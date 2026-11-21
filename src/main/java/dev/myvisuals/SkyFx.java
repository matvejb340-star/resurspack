package dev.myvisuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Красивое небо: тёмный купол со звёздами и колышущееся северное сияние (зелёное или фиолетовое).
 * Рисуется поверх обычного неба, а земля и постройки закрывают его как обычно.
 */
public final class SkyFx {
	private SkyFx() {}

	private static final double DOME_R = 220.0;
	private static final double AURORA_R = 150.0;
	private static final int STAR_COUNT = 420;
	private static final double[][] STARS = new double[STAR_COUNT][5]; // x, y, z, яркость, фаза

	static {
		Random r = new Random(7777);
		for (int i = 0; i < STAR_COUNT; i++) {
			double y = 0.06 + 0.94 * r.nextDouble();
			double phi = Math.PI * 2 * r.nextDouble();
			double rad = Math.sqrt(1 - y * y);
			STARS[i][0] = rad * Math.cos(phi);
			STARS[i][1] = y;
			STARS[i][2] = rad * Math.sin(phi);
			double q = r.nextDouble();
			STARS[i][3] = 0.35 + 0.65 * q * q;
			STARS[i][4] = Math.PI * 2 * r.nextDouble();
		}
	}

	private static double nightFactor(long timeOfDay) {
		long t = timeOfDay % 24000L;
		if (t >= 13000 && t <= 23000) return 1.0;
		if (t > 12000 && t < 13000) return (t - 12000) / 1000.0;
		if (t > 23000) return (24000 - t) / 1000.0;
		return 0.0;
	}

	public static void render(WorldRenderContext ctx) {
		Config.SkyMode mode = Config.skyMode;
		if (mode == Config.SkyMode.VANILLA) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
		MatrixStack ms = ctx.matrixStack();
		if (world == null || ms == null || !world.getDimension().hasSkyLight()) return;

		double night = nightFactor(world.getTimeOfDay());
		int domeAlpha = (int) (125 + 85 * (1.0 - night));
		double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
		Matrix4f m = ms.peek().getPositionMatrix();

		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);

		BufferBuilder b = Tessellator.getInstance()
				.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		drawDome(b, m, domeAlpha);
		drawStars(b, m, t);
		drawAurora(b, m, t, mode == Config.SkyMode.AURORA_PURPLE);

		BufferRenderer.drawWithGlobalProgram(b.end());

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	// ------------------------------------------------------------------- купол

	private static void drawDome(BufferBuilder b, Matrix4f m, int baseAlpha) {
		double[] lat = {-12, 0, 12, 26, 42, 60, 76, 90};
		final int lon = 36;
		for (int i = 0; i < lat.length - 1; i++) {
			for (int j = 0; j < lon; j++) {
				double a0 = Math.PI * 2 * j / lon;
				double a1 = Math.PI * 2 * (j + 1) / lon;
				domeVertex(b, m, lat[i], a0, baseAlpha);
				domeVertex(b, m, lat[i], a1, baseAlpha);
				domeVertex(b, m, lat[i + 1], a1, baseAlpha);
				domeVertex(b, m, lat[i + 1], a0, baseAlpha);
			}
		}
	}

	private static void domeVertex(BufferBuilder b, Matrix4f m, double latDeg, double az, int baseAlpha) {
		double la = Math.toRadians(latDeg);
		float x = (float) (Math.cos(la) * Math.cos(az) * DOME_R);
		float y = (float) (Math.sin(la) * DOME_R);
		float z = (float) (Math.cos(la) * Math.sin(az) * DOME_R);

		double k = Math.sqrt(Math.max(0.0, Math.min(1.0, latDeg / 90.0)));
		int r = (int) (28 + (3 - 28) * k);
		int g = (int) (45 + (5 - 45) * k);
		int bl = (int) (92 + (18 - 92) * k);

		double fade;
		if (latDeg <= -12) fade = 0.0;
		else if (latDeg < 0) fade = (latDeg + 12) / 12.0 * 0.9;
		else if (latDeg < 12) fade = 0.9 + 0.1 * latDeg / 12.0;
		else fade = 1.0;

		b.vertex(m, x, y, z).color(r, g, bl, (int) (baseAlpha * fade));
	}

	// ------------------------------------------------------------------ звёзды

	private static void drawStars(BufferBuilder b, Matrix4f m, double t) {
		for (double[] s : STARS) {
			double dx = s[0], dy = s[1], dz = s[2];
			double twinkle = 0.6 + 0.4 * Math.sin(t * 1.6 + s[4]);
			int alpha = (int) (235 * s[3] * twinkle);
			double size = 0.9 + s[3] * 1.3;

			// два вектора, лежащих в плоскости, перпендикулярной направлению на звезду
			double ux = dz, uy = 0, uz = -dx;
			double ul = Math.sqrt(ux * ux + uz * uz);
			if (ul < 1.0E-4) continue;
			ux /= ul;
			uz /= ul;
			double vx = dy * uz - dz * uy;
			double vy = dz * ux - dx * uz;
			double vz = dx * uy - dy * ux;

			double cx = dx * DOME_R, cy = dy * DOME_R, cz = dz * DOME_R;
			quadStar(b, m, cx - ux * size - vx * size, cy - uy * size - vy * size, cz - uz * size - vz * size,
					cx + ux * size - vx * size, cy + uy * size - vy * size, cz + uz * size - vz * size,
					cx + ux * size + vx * size, cy + uy * size + vy * size, cz + uz * size + vz * size,
					cx - ux * size + vx * size, cy - uy * size + vy * size, cz - uz * size + vz * size,
					alpha);
		}
	}

	private static void quadStar(BufferBuilder b, Matrix4f m,
								 double ax, double ay, double az, double bx, double by, double bz,
								 double cx, double cy, double cz, double dx, double dy, double dz, int alpha) {
		b.vertex(m, (float) ax, (float) ay, (float) az).color(225, 235, 255, alpha);
		b.vertex(m, (float) bx, (float) by, (float) bz).color(225, 235, 255, alpha);
		b.vertex(m, (float) cx, (float) cy, (float) cz).color(225, 235, 255, alpha);
		b.vertex(m, (float) dx, (float) dy, (float) dz).color(225, 235, 255, alpha);
	}

	// ----------------------------------------------------------------- сияние

	private static double[] column(int k, double u, double t, double a0, double width) {
		double a = a0 + width * u + 0.10 * Math.sin(t * 0.11 + k * 2.0);
		double r = AURORA_R + 14 * Math.sin(a * 2.7 + t * 0.25 + k);
		double baseY = 34 + 9 * k + 8 * Math.sin(a * 3.1 + t * 0.3 + k * 1.7);
		double h = 55 + 32 * (0.5 + 0.5 * Math.sin(a * 4.3 - t * 0.45 + k));

		// вертикальные «лучи» сияния
		double ray = 0.35 + 0.65 * Math.pow(0.5 + 0.5 * Math.sin(a * 55 + t * 1.7 + k * 3 + 2 * Math.sin(a * 9 + t * 0.6)), 2);
		double endFade = Math.pow(Math.sin(Math.PI * u), 0.6);

		double ca = Math.cos(a);
		double sa = Math.sin(a);
		double sway = 7 * Math.sin(a * 8 + t * 0.8 + k);

		double bx = ca * r, bz = sa * r, by = baseY;
		double mx = ca * r - sa * sway * 0.4, mz = sa * r + ca * sway * 0.4, my = baseY + h * 0.35;
		double tx = ca * r * 0.985 - sa * sway, tz = sa * r * 0.985 + ca * sway, ty = baseY + h;

		return new double[]{bx, by, bz, mx, my, mz, tx, ty, tz, 170 * ray * endFade, 95 * ray * endFade};
	}

	private static void auroraVertex(BufferBuilder b, Matrix4f m, double x, double y, double z, int[] c, double alpha) {
		b.vertex(m, (float) x, (float) y, (float) z).color(c[0], c[1], c[2], (int) Math.max(0, Math.min(255, alpha)));
	}

	private static void drawAurora(BufferBuilder b, Matrix4f m, double t, boolean purple) {
		int[] cBot = purple ? new int[]{205, 165, 255} : new int[]{90, 255, 150};
		int[] cMid = purple ? new int[]{160, 120, 255} : new int[]{70, 225, 175};
		int[] cTop = purple ? new int[]{110, 80, 235} : new int[]{70, 140, 235};

		double[] centers = {0.6, 2.7, 4.6};
		double[] widths = {1.9, 1.5, 2.2};
		final int segs = 72;

		for (int k = 0; k < centers.length; k++) {
			double a0 = centers[k] - widths[k] / 2;
			double[] prev = column(k, 0.0, t, a0, widths[k]);
			for (int i = 1; i <= segs; i++) {
				double[] cur = column(k, i / (double) segs, t, a0, widths[k]);

				// нижняя часть: яркая у основания
				auroraVertex(b, m, prev[0], prev[1], prev[2], cBot, prev[9]);
				auroraVertex(b, m, cur[0], cur[1], cur[2], cBot, cur[9]);
				auroraVertex(b, m, cur[3], cur[4], cur[5], cMid, cur[10]);
				auroraVertex(b, m, prev[3], prev[4], prev[5], cMid, prev[10]);

				// верхняя часть: плавно тает в небе
				auroraVertex(b, m, prev[3], prev[4], prev[5], cMid, prev[10]);
				auroraVertex(b, m, cur[3], cur[4], cur[5], cMid, cur[10]);
				auroraVertex(b, m, cur[6], cur[7], cur[8], cTop, 0);
				auroraVertex(b, m, prev[6], prev[7], prev[8], cTop, 0);

				prev = cur;
			}
		}
	}
}
