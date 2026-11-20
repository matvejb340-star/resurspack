package dev.myvisuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Таргет-анимации: появляются на существе, которое ты бьёшь, и пропадают,
 * если ты перестал его бить (через 1,5 секунды).
 *  — Круг с крестом
 *  — Зелёные полупрозрачные волны вокруг врага
 *  — Пузырьки
 */
public final class TargetFx {
	private TargetFx() {}

	private static final int SHOW_TICKS = 30;
	private static final Random RNG = new Random();

	private static final class Bubble {
		final double x, y, z;
		final float size, rise, phase;
		final int born, life;

		Bubble(double x, double y, double z, float size, float rise, float phase, int born, int life) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.size = size;
			this.rise = rise;
			this.phase = phase;
			this.born = born;
			this.life = life;
		}
	}

	private static final List<Bubble> BUBBLES = new ArrayList<>();
	private static LivingEntity target = null;
	private static int hitTick = 0;
	private static int tick = 0;

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient && entity instanceof LivingEntity le && entity != player) {
				target = le;
				hitTick = tick;
			}
			return ActionResult.PASS;
		});
	}

	public static void tick(MinecraftClient mc) {
		tick++;
		if (mc.world == null) {
			target = null;
			BUBBLES.clear();
			return;
		}

		if (target != null && (target.isRemoved() || !target.isAlive() || tick - hitTick > SHOW_TICKS)) {
			target = null;
		}
		BUBBLES.removeIf(b -> tick - b.born >= b.life);

		if (target != null && Config.targetFx == Config.TargetFx.BUBBLES && BUBBLES.size() < 80) {
			Box bb = target.getBoundingBox();
			double spread = Math.max(bb.getLengthX(), bb.getLengthZ()) + 0.5;
			for (int i = 0; i < 2; i++) {
				double x = bb.getCenter().x + (RNG.nextDouble() - 0.5) * spread;
				double y = bb.minY + RNG.nextDouble() * bb.getLengthY();
				double z = bb.getCenter().z + (RNG.nextDouble() - 0.5) * spread;
				BUBBLES.add(new Bubble(x, y, z,
						0.03f + RNG.nextFloat() * 0.06f,
						0.02f + RNG.nextFloat() * 0.025f,
						RNG.nextFloat() * 6.28f,
						tick, 18 + RNG.nextInt(22)));
			}
		}
	}

	// ------------------------------------------------------------- отрисовка

	public static void render(WorldRenderContext ctx) {
		Config.TargetFx mode = Config.targetFx;
		if (mode == Config.TargetFx.OFF) return;

		boolean haveTarget = target != null && (mode == Config.TargetFx.CROSS || mode == Config.TargetFx.WAVE);
		boolean haveBubbles = mode == Config.TargetFx.BUBBLES && !BUBBLES.isEmpty();
		if (!haveTarget && !haveBubbles) return;

		MatrixStack ms = ctx.matrixStack();
		if (ms == null) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		float td = mc.getRenderTickCounter().getTickDelta(true);
		double now = tick + td;
		Vec3d cam = ctx.camera().getPos();
		Matrix4f m = ms.peek().getPositionMatrix();

		// оси камеры, чтобы кружки и пузырьки всегда были повёрнуты к тебе
		double yaw = Math.toRadians(ctx.camera().getYaw());
		double pitch = Math.toRadians(ctx.camera().getPitch());
		Vec3d fwd = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3d up = right.crossProduct(fwd);

		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);

		BufferBuilder b = Tessellator.getInstance()
				.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		if (mode == Config.TargetFx.CROSS) drawCross(b, m, cam, right, up, now);
		else if (mode == Config.TargetFx.WAVE) drawWave(b, m, cam, now);
		else drawBubbles(b, m, cam, right, up, now);

		BufferRenderer.drawWithGlobalProgram(b.end());

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static double fadeOut(double now) {
		double left = SHOW_TICKS - (now - hitTick);
		return Math.max(0.0, Math.min(1.0, left / 6.0));
	}

	// 1) круг с красным крестом
	private static void drawCross(BufferBuilder b, Matrix4f m, Vec3d cam, Vec3d right, Vec3d up, double now) {
		Vec3d center = target.getBoundingBox().getCenter().subtract(cam);
		// чуть сдвигаем к камере, чтобы не прятался внутри модели
		Vec3d c = center.add(center.normalize().multiply(-0.5));

		double pulse = 1.0 + 0.5 * Math.max(0.0, 1.0 - (now - hitTick) / 6.0);
		double fade = fadeOut(now);
		double s = pulse;

		disc(b, m, c, right, up, 0.36 * s, 24, 0, 0, 0, (int) (80 * fade));
		ring(b, m, c, right, up, 0.42 * s, 0.35 * s, 24, 255, 255, 255, (int) (210 * fade));
		bar(b, m, c, right, up, Math.PI / 4, 0.26 * s, 0.035 * s, 255, 40, 40, (int) (240 * fade));
		bar(b, m, c, right, up, -Math.PI / 4, 0.26 * s, 0.035 * s, 255, 40, 40, (int) (240 * fade));
	}

	// 2) зелёные волны вокруг врага
	private static void drawWave(BufferBuilder b, Matrix4f m, Vec3d cam, double now) {
		Box bb = target.getBoundingBox();
		Vec3d ctr = bb.getCenter();
		double halfW = Math.max(bb.getLengthX(), bb.getLengthZ()) / 2.0 + 0.12;
		double h = bb.getLengthY();
		double minY = bb.minY;
		double t0 = now / 20.0;
		double fade = fadeOut(now);

		final int n = 36;
		for (int k = 0; k < 5; k++) {
			Vec3d[] pts = new Vec3d[n + 1];
			for (int i = 0; i <= n; i++) {
				double u = i / (double) n;
				double phi;
				double y;
				switch (k) {
					case 0 -> { // диагональная спираль вверх
						phi = 6.283 * (u * 1.25) + t0 * 2.2;
						y = minY + h * u;
					}
					case 1 -> { // волнистая вертикальная линия
						phi = 4.0 + 0.9 * Math.sin(u * 6.283 * 2 + t0 * 3.0) + t0 * 0.8;
						y = minY + h * u;
					}
					case 2 -> { // пояс-волна вокруг тела
						phi = 6.283 * u + t0 * 1.6;
						y = minY + h * (0.5 + 0.18 * Math.sin(phi * 2 + t0 * 2));
					}
					case 3 -> { // диагональ в другую сторону
						phi = -6.283 * u - t0 * 2.6 + 2.0;
						y = minY + h * (1.0 - u);
					}
					default -> { // плавный изгиб
						phi = 2.0 + 1.2 * Math.sin(u * 6.283 - t0 * 2.4);
						y = minY + h * (0.5 + 0.45 * Math.sin(u * 6.283 + t0));
					}
				}
				double rad = halfW + 0.04 * Math.sin(phi * 3 + t0);
				pts[i] = new Vec3d(ctr.x + Math.cos(phi) * rad, y, ctr.z + Math.sin(phi) * rad).subtract(cam);
			}

			Vec3d[] side = new Vec3d[n + 1];
			for (int i = 0; i <= n; i++) {
				Vec3d d = pts[Math.min(n, i + 1)].subtract(pts[Math.max(0, i - 1)]);
				Vec3d sd = d.crossProduct(pts[i]);
				if (sd.lengthSquared() < 1.0E-8) sd = new Vec3d(0, 1, 0);
				side[i] = sd.normalize().multiply(0.035);
			}

			for (int i = 0; i < n; i++) {
				int a0 = (int) (125 * fade * Math.sin(Math.PI * i / n));
				int a1 = (int) (125 * fade * Math.sin(Math.PI * (i + 1) / n));
				quadC(b, m,
						pts[i].add(side[i]), a0,
						pts[i].subtract(side[i]), a0,
						pts[i + 1].subtract(side[i + 1]), a1,
						pts[i + 1].add(side[i + 1]), a1,
						70, 255, 110);
			}
		}
	}

	// 3) пузырьки
	private static void drawBubbles(BufferBuilder b, Matrix4f m, Vec3d cam, Vec3d right, Vec3d up, double now) {
		for (Bubble bu : BUBBLES) {
			double age = now - bu.born;
			if (age < 0) age = 0;

			double x = bu.x + 0.04 * Math.sin(age * 0.3 + bu.phase);
			double y = bu.y + bu.rise * age;
			double z = bu.z + 0.04 * Math.cos(age * 0.3 + bu.phase);

			double fadeIn = Math.min(1.0, age / 3.0);
			double fadeOut = Math.min(1.0, (bu.life - age) / 4.0);
			double alpha = Math.max(0.0, Math.min(fadeIn, fadeOut));
			double size = bu.size * (1.0 + 0.2 * age / bu.life) * (bu.life - age < 2 ? 1.35 : 1.0);

			Vec3d c = new Vec3d(x, y, z).subtract(cam);

			disc(b, m, c, right, up, size, 10, 140, 200, 255, (int) (45 * alpha));
			ring(b, m, c, right, up, size, size * 0.7, 10, 175, 225, 255, (int) (175 * alpha));
			Vec3d hl = c.add(right.multiply(-size * 0.35)).add(up.multiply(size * 0.35));
			disc(b, m, hl, right, up, size * 0.22, 6, 255, 255, 255, (int) (160 * alpha));
		}
	}

	// ----------------------------------------------------------------- фигуры

	private static void vtx(BufferBuilder b, Matrix4f m, Vec3d p, int r, int g, int bl, int a) {
		b.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, bl, Math.max(0, Math.min(255, a)));
	}

	private static void quadC(BufferBuilder b, Matrix4f m,
							  Vec3d p1, int a1, Vec3d p2, int a2, Vec3d p3, int a3, Vec3d p4, int a4,
							  int r, int g, int bl) {
		vtx(b, m, p1, r, g, bl, a1);
		vtx(b, m, p2, r, g, bl, a2);
		vtx(b, m, p3, r, g, bl, a3);
		vtx(b, m, p4, r, g, bl, a4);
	}

	private static Vec3d on(Vec3d c, Vec3d right, Vec3d up, double rad, double angle) {
		return c.add(right.multiply(Math.cos(angle) * rad)).add(up.multiply(Math.sin(angle) * rad));
	}

	private static void ring(BufferBuilder b, Matrix4f m, Vec3d c, Vec3d right, Vec3d up,
							 double rOut, double rIn, int segs, int r, int g, int bl, int a) {
		for (int i = 0; i < segs; i++) {
			double a0 = Math.PI * 2 * i / segs;
			double a1 = Math.PI * 2 * (i + 1) / segs;
			vtx(b, m, on(c, right, up, rOut, a0), r, g, bl, a);
			vtx(b, m, on(c, right, up, rOut, a1), r, g, bl, a);
			vtx(b, m, on(c, right, up, rIn, a1), r, g, bl, a);
			vtx(b, m, on(c, right, up, rIn, a0), r, g, bl, a);
		}
	}

	private static void disc(BufferBuilder b, Matrix4f m, Vec3d c, Vec3d right, Vec3d up,
							 double rad, int segs, int r, int g, int bl, int a) {
		for (int i = 0; i < segs; i++) {
			double a0 = Math.PI * 2 * i / segs;
			double a1 = Math.PI * 2 * (i + 1) / segs;
			vtx(b, m, c, r, g, bl, a);
			vtx(b, m, on(c, right, up, rad, a0), r, g, bl, a);
			vtx(b, m, on(c, right, up, rad, a1), r, g, bl, a);
			vtx(b, m, on(c, right, up, rad, a1), r, g, bl, a);
		}
	}

	/** Прямая планка, повёрнутая на angle относительно «вправо» экрана. */
	private static void bar(BufferBuilder b, Matrix4f m, Vec3d c, Vec3d right, Vec3d up,
							double angle, double halfLen, double halfW, int r, int g, int bl, int a) {
		Vec3d d = right.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle)));
		Vec3d n = right.multiply(-Math.sin(angle)).add(up.multiply(Math.cos(angle)));
		vtx(b, m, c.add(d.multiply(halfLen)).add(n.multiply(halfW)), r, g, bl, a);
		vtx(b, m, c.add(d.multiply(halfLen)).subtract(n.multiply(halfW)), r, g, bl, a);
		vtx(b, m, c.subtract(d.multiply(halfLen)).subtract(n.multiply(halfW)), r, g, bl, a);
		vtx(b, m, c.subtract(d.multiply(halfLen)).add(n.multiply(halfW)), r, g, bl, a);
	}
}
