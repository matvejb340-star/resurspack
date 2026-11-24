package dev.myvisuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Нож M9 (штык-нож) вместо модельки меча в первом лице.
 * Нож рисуется из коробок прямо в коде, поэтому отдельная модель не нужна.
 * Анимации: доставание при взятии в руку и «осмотр» по правой кнопке мыши.
 */
public final class KnifeRenderer {
	private KnifeRenderer() {}

	private static final int DRAW_LEN = 16;
	private static final int INSPECT_LEN = 90;

	private static int drawTicks = DRAW_LEN;
	private static int inspectTicks = INSPECT_LEN;
	private static boolean lastActive = false;
	private static int lastSlot = -1;
	private static boolean lastUse = false;

	public static boolean active(ItemStack stack) {
		return Config.swordSkin != Config.SwordSkin.VANILLA
				&& stack != null && !stack.isEmpty() && stack.isIn(ItemTags.SWORDS);
	}

	public static void tick(MinecraftClient mc) {
		if (mc.player == null) {
			lastActive = false;
			return;
		}
		ItemStack main = mc.player.getMainHandStack();
		boolean act = active(main);
		int slot = mc.player.getInventory().selectedSlot;

		if (act && (!lastActive || slot != lastSlot)) {
			drawTicks = 0;
			inspectTicks = INSPECT_LEN;
		}
		lastActive = act;
		lastSlot = slot;

		if (drawTicks < DRAW_LEN) drawTicks++;
		if (inspectTicks < INSPECT_LEN) inspectTicks++;

		boolean use = mc.options.useKey.isPressed();
		if (act && use && !lastUse && mc.currentScreen == null
				&& drawTicks >= DRAW_LEN && inspectTicks >= INSPECT_LEN) {
			inspectTicks = 0;
		}
		lastUse = use;
	}

	private static float smooth(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3f - 2f * x);
	}

	// ------------------------------------------------------------------ рисование

	public static void draw(MatrixStack ms, float tickDelta, float swing) {
		ms.push();

		// базовая позиция в правой руке
		ms.translate(0.50f, -0.40f, -0.72f);
		ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-62f));
		ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-8f));
		ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-12f));

		// доставание: нож вылетает снизу с вращением
		if (drawTicks < DRAW_LEN) {
			float t = Math.min(1f, (drawTicks + tickDelta) / DRAW_LEN);
			float e = 1f - (1f - t) * (1f - t) * (1f - t);
			float inv = 1f - e;
			ms.translate(0.0f, -0.55f * inv, 0.12f * inv);
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(70f * inv));
			ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(360f * inv));
		}

		// осмотр: прежняя плавная анимация, чуть растянутая по времени
		if (inspectTicks < INSPECT_LEN) {
			float t = Math.min(1f, (inspectTicks + tickDelta) / INSPECT_LEN);
			// время течёт медленнее в середине (но не останавливается), быстрее по краям
			float k = t + 0.8f * (float) Math.sin(2 * Math.PI * t) / (float) (2 * Math.PI);
			float lift = (float) Math.sin(Math.PI * k);
			float ease = k * k * (3f - 2f * k);
			ms.translate(-0.30f * lift, 0.10f * lift, 0.22f * lift);
			ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(360f * ease));
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(55f * (float) Math.sin(2 * Math.PI * k)));
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-25f * lift));
		}

		// удар: из руки к центру экрана, оттуда рубящий взмах по диагонали вправо-вниз и назад на место
		if (swing > 0f) {
			float p = Math.min(1f, swing);
			float a;
			if (p < 0.35f) {
				float x = p / 0.35f;
				a = x * x * (3f - 2f * x);                       // замах к центру
			} else {
				float x = (p - 0.35f) / 0.65f;
				a = 1f - x * x;                                  // быстрый удар обратно
			}
			float s = (float) Math.sin(Math.PI * p);
			ms.translate(-0.42f * a, 0.34f * a, -0.12f * s);
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(28f * a));
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-25f * s));
		}

		ms.translate(0f, -0.22f, 0f); // центр хвата
		Matrix4f m = ms.peek().getPositionMatrix();

		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();

		BufferBuilder b = Tessellator.getInstance()
				.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		buildKnife(b, m, Config.swordSkin == Config.SwordSkin.M9_GOLD);
		BufferRenderer.drawWithGlobalProgram(b.end());

		RenderSystem.enableCull();
		ms.pop();
	}

	// ----------------------------------------------------------------- геометрия

	private static float smoothstep(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3f - 2f * x);
	}

	/** Штык-нож M9: клинок с долом и скосом, рифлёная ручка, кольцо и гарда. Ось ножа — Y. */
	private static void buildKnife(BufferBuilder b, Matrix4f m, boolean gold) {
		int[] steel = gold ? new int[]{232, 184, 58} : new int[]{150, 156, 166};
		int[] steelEdge = gold ? new int[]{255, 232, 140} : new int[]{205, 210, 220};
		int[] steelDark = gold ? new int[]{170, 120, 28} : new int[]{86, 92, 102};
		int[] grip = gold ? new int[]{92, 62, 22} : new int[]{44, 46, 52};
		int[] gripHi = gold ? new int[]{150, 105, 40} : new int[]{74, 77, 86};
		int[] metal = gold ? new int[]{214, 160, 44} : new int[]{66, 70, 80};

		// ручка: колпачок, четыре рифлёных секции, воротник
		float[][] handle = {
				{-0.036f, 0.000f}, {-0.036f, 0.029f}, {-0.031f, 0.034f}, {-0.004f, 0.034f},
				{0.000f, 0.030f}, {0.008f, 0.024f}
		};
		lathe(b, m, handle, 18, metal, 0.9f);
		for (int k = 0; k < 4; k++) {
			float y = 0.016f + k * 0.054f;
			float[][] ring = {
					{y, 0.024f}, {y + 0.004f, 0.029f}, {y + 0.010f, 0.0315f}, {y + 0.038f, 0.0315f},
					{y + 0.044f, 0.029f}, {y + 0.048f, 0.024f}
			};
			lathe(b, m, ring, 18, (k % 2 == 0) ? grip : gripHi, 0.45f);
		}
		float[][] collar = {
				{0.232f, 0.024f}, {0.236f, 0.031f}, {0.262f, 0.031f}, {0.268f, 0.026f}, {0.274f, 0.000f}
		};
		lathe(b, m, collar, 18, metal, 0.9f);

		// гарда и кольцо под ствол
		box(b, m, -0.072f, 0.256f, -0.017f, 0.034f, 0.276f, 0.017f, metal, 0.9f);
		box(b, m, 0.028f, 0.226f, -0.020f, 0.044f, 0.304f, 0.020f, metal, 0.9f);
		ModelKit.torusZ(b, m, -0.088f, 0.262f, 0.0f, 0.026f, 0.0065f, 20, 8, metal, 0.9f);

		blade(b, m, 0.276f, 0.500f, steel, steelEdge, steelDark);
	}

	private static void lathe(BufferBuilder b, Matrix4f m, float[][] prof, int seg, int[] c, float gloss) {
		ModelKit.lathe(b, m, prof, seg, 0f, 0f, c, gloss);
	}

	private static void box(BufferBuilder b, Matrix4f m, float x0, float y0, float z0,
							float x1, float y1, float z1, int[] c, float gloss) {
		ModelKit.box(b, m, x0, y0, z0, x1, y1, z1, c, gloss);
	}

	/** Клинок: сечение с долами и фасками, скос обуха (clip point) и «пила» на обухе. */
	private static void blade(BufferBuilder b, Matrix4f m, float y0, float len,
							  int[] steel, int[] edgeC, int[] dark) {
		final int N = 46;
		final float hw = 0.040f;      // полуширина
		final float t0 = 0.0105f;     // полутолщина
		final float tipX = 0.35f;     // положение кончика (доля ширины)

		// доли ширины и множители толщины точек сечения: A F1 F2 F3 F4 S1 E
		final float[] F = {0f, 0.10f, 0.18f, 0.44f, 0.52f, 0.66f, 1f};
		final float[] T = {1f, 1f, 0f, 0f, 1f, 0.78f, 0f}; // F2, F3 зависят от глубины дола

		float[][][] pts = new float[N + 1][13][3];
		float[] ctrY = new float[N + 1];
		float[] ctrX = new float[N + 1];

		for (int i = 0; i <= N; i++) {
			float u = i / (float) N;
			float y = y0 + len * u;

			// обух (xs) и лезвие (xe)
			float v = Math.max(0f, (u - 0.58f) / 0.42f);
			float tip = (tipX * 2f - 1f) * hw;
			float xs = -hw + (tip + hw) * (float) Math.pow(v, 2.2);
			float xe = hw + (tip - hw) * (float) Math.pow(v, 3.0);
			float ts = Math.max(0.10f, 1f - 0.85f * v * v);

			float depth = 0.42f * smoothstep(Math.min(u - 0.03f, 0.64f - u) / 0.09f);

			for (int j = 0; j < 13; j++) {
				int src = j <= 6 ? j : 12 - j;
				float sign = j <= 6 ? 1f : -1f;
				float th = T[src];
				if (src == 2 || src == 3) th = 1f - depth;
				float x = xs + (xe - xs) * F[src];
				float z = sign * t0 * ts * th;
				pts[i][j][0] = x;
				pts[i][j][1] = y;
				pts[i][j][2] = z;
			}
			ctrX[i] = (xs + xe) / 2f;
			ctrY[i] = y;
		}

		for (int i = 0; i < N; i++) {
			float[] ctr = {(ctrX[i] + ctrX[i + 1]) / 2f, (ctrY[i] + ctrY[i + 1]) / 2f, 0f};
			for (int j = 0; j < 13; j++) {
				int j2 = (j + 1) % 13;
				int[] col;
				if (j == 5 || j == 6) col = edgeC;           // заточенные фаски
				else if (j == 12) col = dark;                // обух
				else if (j == 1 || j == 2 || j == 3 || j == 8 || j == 9 || j == 10) col = dark;  // дол
				else col = steel;
				ModelKit.quadFlat(b, m, pts[i][j], pts[i][j2], pts[i + 1][j2], pts[i + 1][j],
						ctr, col, 1.0f);
			}
		}

		// пила на обухе
		for (int k = 0; k < 6; k++) {
			float y = y0 + 0.035f + k * 0.036f;
			box(b, m, -hw - 0.011f, y, -0.0075f, -hw + 0.001f, y + 0.017f, 0.0075f, dark, 0.8f);
		}
		// клеймо у основания
		box(b, m, -hw * 0.55f, y0, t0 * 1.02f, hw * 0.4f, y0 + 0.07f, t0 * 1.02f + 0.0008f, edgeC, 0.6f);
	}
}
