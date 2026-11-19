package dev.myvisuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoryRenderer {
	private TrajectoryRenderer() {}

	private static final int SUBSTEPS = 4;
	private static final double RIBBON_HALF_WIDTH = 0.09;

	private record Params(double speed, double gravity, double drag, float pitchOffset) {}

	private static Params paramsFor(ClientPlayerEntity p) {
		for (Hand hand : Hand.values()) {
			ItemStack stack = p.getStackInHand(hand);
			Item item = stack.getItem();

			if (item instanceof BowItem) {
				if (!p.isUsingItem() || p.getActiveHand() != hand) continue;
				float pull = BowItem.getPullProgress(p.getItemUseTime());
				if (pull < 0.1f) continue;
				return new Params(pull * 3.0, 0.05, 0.99, 0f);
			}
			if (item instanceof CrossbowItem) {
				if (CrossbowItem.isCharged(stack)) return new Params(3.15, 0.05, 0.99, 0f);
				continue;
			}
			if (item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG) {
				return new Params(1.5, 0.03, 0.99, 0f);
			}
			if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
				return new Params(0.5, 0.05, 0.99, -20f);
			}
		}
		return null;
	}

	public static void render(WorldRenderContext ctx) {
		if (!Config.trajectory) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		ClientWorld world = mc.world;
		MatrixStack matrices = ctx.matrixStack();
		if (player == null || world == null || matrices == null) return;

		Params params = paramsFor(player);
		if (params == null) return;

		float tickDelta = mc.getRenderTickCounter().getTickDelta(true);

		Vec3d pos = player.getCameraPosVec(tickDelta).subtract(0, 0.1, 0);
		Vec3d vel = Vec3d.fromPolar(player.getPitch(tickDelta) + params.pitchOffset(), player.getYaw(tickDelta))
				.multiply(params.speed());

		// --- симуляция полёта мелкими шагами, чтобы кривая была плавной ---
		final double dt = 1.0 / SUBSTEPS;
		List<Vec3d> points = new ArrayList<>();
		points.add(pos);
		BlockHitResult landing = null;

		for (int i = 0; i < 120 * SUBSTEPS; i++) {
			Vec3d next = pos.add(vel.multiply(dt));
			BlockHitResult hit = world.raycast(new RaycastContext(
					pos, next,
					RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE,
					player));
			if (hit.getType() != HitResult.Type.MISS) {
				landing = hit;
				points.add(hit.getPos());
				break;
			}
			points.add(next);
			pos = next;

			boolean inWater = world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
			double drag = Math.pow(inWater ? 0.6 : params.drag(), dt);
			vel = vel.multiply(drag).subtract(0, params.gravity() * dt, 0);

			if (pos.y < world.getBottomY() - 10) break;
		}

		Vec3d cam = ctx.camera().getPos();

		// точки относительно камеры; начало у самого лица пропускаем
		List<Vec3d> rel = new ArrayList<>();
		for (Vec3d pt : points) {
			Vec3d r = pt.subtract(cam);
			if (!rel.isEmpty() || r.lengthSquared() > 1.5 * 1.5) rel.add(r);
		}
		if (rel.size() < 2) return;

		Matrix4f m = matrices.peek().getPositionMatrix();

		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);

		// --- полупрозрачная зелёная лента ---
		BufferBuilder strip = Tessellator.getInstance()
				.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < rel.size(); i++) {
			Vec3d r = rel.get(i);
			Vec3d dir;
			if (i == 0) dir = rel.get(1).subtract(r);
			else if (i == rel.size() - 1) dir = r.subtract(rel.get(i - 1));
			else dir = rel.get(i + 1).subtract(rel.get(i - 1));

			Vec3d side = dir.crossProduct(r);
			if (side.lengthSquared() < 1.0E-8) side = new Vec3d(0, 1, 0);
			side = side.normalize().multiply(RIBBON_HALF_WIDTH);

			int alpha = (int) Math.min(125, 35 + 22 * Math.max(0, r.length() - 1.5));
			strip.vertex(m, (float) (r.x + side.x), (float) (r.y + side.y), (float) (r.z + side.z))
					.color(70, 255, 110, alpha);
			strip.vertex(m, (float) (r.x - side.x), (float) (r.y - side.y), (float) (r.z - side.z))
					.color(70, 255, 110, alpha);
		}
		BufferRenderer.drawWithGlobalProgram(strip.end());

		// --- чёрный кружок в месте падения ---
		if (landing != null) {
			Direction face = landing.getSide();
			Vec3d n = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
			Vec3d c = landing.getPos().subtract(cam).add(n.multiply(0.02));
			Vec3d ref = Math.abs(n.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
			Vec3d u = n.crossProduct(ref).normalize();
			Vec3d v = n.crossProduct(u);

			final int seg = 28;

			BufferBuilder disc = Tessellator.getInstance()
					.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			disc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(0, 0, 0, 200);
			for (int i = 0; i <= seg; i++) {
				double a = 2 * Math.PI * i / seg;
				double r = 0.33;
				disc.vertex(m,
						(float) (c.x + (u.x * Math.cos(a) + v.x * Math.sin(a)) * r),
						(float) (c.y + (u.y * Math.cos(a) + v.y * Math.sin(a)) * r),
						(float) (c.z + (u.z * Math.cos(a) + v.z * Math.sin(a)) * r))
						.color(0, 0, 0, 200);
			}
			BufferRenderer.drawWithGlobalProgram(disc.end());

			BufferBuilder ring = Tessellator.getInstance()
					.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
			for (int i = 0; i <= seg; i++) {
				double a = 2 * Math.PI * i / seg;
				double cs = Math.cos(a);
				double sn = Math.sin(a);
				double r1 = 0.36;
				double r2 = 0.43;
				ring.vertex(m,
						(float) (c.x + (u.x * cs + v.x * sn) * r1),
						(float) (c.y + (u.y * cs + v.y * sn) * r1),
						(float) (c.z + (u.z * cs + v.z * sn) * r1))
						.color(70, 255, 110, 170);
				ring.vertex(m,
						(float) (c.x + (u.x * cs + v.x * sn) * r2),
						(float) (c.y + (u.y * cs + v.y * sn) * r2),
						(float) (c.z + (u.z * cs + v.z * sn) * r2))
						.color(70, 255, 110, 170);
			}
			BufferRenderer.drawWithGlobalProgram(ring.end());
		}

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}
}
