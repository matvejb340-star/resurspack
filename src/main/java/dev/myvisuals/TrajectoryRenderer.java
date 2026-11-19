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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoryRenderer {
	private TrajectoryRenderer() {}

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

		List<Vec3d> points = new ArrayList<>();
		points.add(pos);
		Vec3d landing = null;

		for (int i = 0; i < 300; i++) {
			Vec3d next = pos.add(vel);
			BlockHitResult hit = world.raycast(new RaycastContext(
					pos, next,
					RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE,
					player));
			if (hit.getType() != HitResult.Type.MISS) {
				landing = hit.getPos();
				points.add(landing);
				break;
			}
			points.add(next);
			pos = next;

			boolean inWater = world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
			vel = vel.multiply(inWater ? 0.6 : params.drag()).subtract(0, params.gravity(), 0);

			if (pos.y < world.getBottomY() - 10) break;
		}

		if (points.size() < 2) return;

		Vec3d cam = ctx.camera().getPos();
		Matrix4f m = matrices.peek().getPositionMatrix();

		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		RenderSystem.disableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.lineWidth(3.0f);

		BufferBuilder line = Tessellator.getInstance()
				.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
		for (Vec3d pt : points) {
			line.vertex(m, (float) (pt.x - cam.x), (float) (pt.y - cam.y), (float) (pt.z - cam.z))
					.color(80, 255, 120, 255);
		}
		BufferRenderer.drawWithGlobalProgram(line.end());

		if (landing != null) {
			float s = 0.3f;
			float cx = (float) (landing.x - cam.x);
			float cy = (float) (landing.y - cam.y);
			float cz = (float) (landing.z - cam.z);
			BufferBuilder cross = Tessellator.getInstance()
					.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
			cross.vertex(m, cx - s, cy, cz).color(255, 80, 80, 255);
			cross.vertex(m, cx + s, cy, cz).color(255, 80, 80, 255);
			cross.vertex(m, cx, cy - s, cz).color(255, 80, 80, 255);
			cross.vertex(m, cx, cy + s, cz).color(255, 80, 80, 255);
			cross.vertex(m, cx, cy, cz - s).color(255, 80, 80, 255);
			cross.vertex(m, cx, cy, cz + s).color(255, 80, 80, 255);
			BufferRenderer.drawWithGlobalProgram(cross.end());
		}

		RenderSystem.lineWidth(1.0f);
		RenderSystem.enableDepthTest();
		RenderSystem.disableBlend();
	}
}
