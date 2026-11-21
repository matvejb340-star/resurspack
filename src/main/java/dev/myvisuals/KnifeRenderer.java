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
	private static final int INSPECT_LEN = 80;

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

		// осмотр: нож поднимается, долго показывает обе стороны в середине и опускается
		if (inspectTicks < INSPECT_LEN) {
			float t = Math.min(1f, (inspectTicks + tickDelta) / INSPECT_LEN);
			float lift;
			if (t < 0.18f) lift = smooth(t / 0.18f);
			else if (t > 0.82f) lift = smooth((1f - t) / 0.18f);
			else lift = 1f;
			float h = Math.max(0f, Math.min(1f, (t - 0.18f) / 0.64f)); // середина
			float spin = h * h * (3f - 2f * h);
			ms.translate(-0.30f * lift, 0.10f * lift, 0.22f * lift);
			ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(360f * spin));
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(30f * lift + 25f * (float) Math.sin(2 * Math.PI * h) * lift));
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-20f * lift));
		}

		// удар: рубящий взмах по диагонали сверху-справа вниз-влево, как мечом
		if (swing > 0f) {
			float p = Math.min(1f, swing);
			float u = 1f - (1f - p) * (1f - p);          // быстрый старт, мягкий конец
			float d = u * 0.9f - 0.1f;                    // от середины экрана вниз по диагонали
			float s = (float) Math.sin(Math.PI * p);
			ms.translate(-0.70f * d, -0.60f * d, -0.18f * s);
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(75f * d));
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22f * s));
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

	/** Нож: ручка по оси Y снизу вверх, затем гарда и клинок. Размеры в блоках. */
	private static void buildKnife(BufferBuilder b, Matrix4f m, boolean gold) {
		int[] steel = gold ? new int[]{246, 205, 70} : new int[]{196, 202, 214};
		int[] steelDark = gold ? new int[]{205, 155, 35} : new int[]{120, 128, 142};
		int[] steelEdge = gold ? new int[]{255, 236, 150} : new int[]{235, 240, 248};
		int[] grip = gold ? new int[]{70, 45, 20} : new int[]{34, 34, 40};
		int[] gripLine = gold ? new int[]{110, 75, 30} : new int[]{58, 58, 66};
		int[] metal = gold ? new int[]{225, 175, 55} : new int[]{88, 92, 104};

		float w = 0.030f; // полутолщина ручки

		// набалдашник и ручка с рифлением
		box(b, m, -w - 0.006f, -0.020f, -w - 0.004f, w + 0.006f, 0.000f, w + 0.004f, metal);
		box(b, m, -w, 0.000f, -w, w, 0.250f, w, grip);
		for (int i = 0; i < 7; i++) {
			float y = 0.025f + i * 0.032f;
			box(b, m, -w - 0.003f, y, -w - 0.003f, w + 0.003f, y + 0.011f, w + 0.003f, gripLine);
		}
		// кольцо у основания (как у штык-ножа)
		box(b, m, -w - 0.004f, 0.236f, -w - 0.004f, w + 0.004f, 0.256f, w + 0.004f, metal);

		// гарда с кольцом под ствол
		box(b, m, -0.085f, 0.256f, -0.020f, 0.040f, 0.282f, 0.020f, metal);
		box(b, m, -0.088f, 0.250f, -0.026f, -0.060f, 0.292f, 0.026f, metal);

		// клинок: ступенчатое сужение + заточенный скос (clip point)
		float y0 = 0.282f;
		float len = 0.500f;
		int steps = 18;
		for (int i = 0; i < steps; i++) {
			float t0 = i / (float) steps;
			float t1 = (i + 1) / (float) steps;
			float ya = y0 + len * t0;
			float yb = y0 + len * t1;

			// ширина клинка (по X): постоянная, затем острый скос к концу
			float half = t0 < 0.58f ? 0.036f : 0.036f * (1f - (t0 - 0.58f) / 0.42f);
			half = Math.max(half, 0.004f);
			float spine = 0.0095f - 0.0035f * t0; // толщина (по Z)

			// обух слева (X-), лезвие справа (X+)
			box(b, m, -half, ya, -spine, half, yb, spine, steel);
			// заточенная кромка справа
			box(b, m, half * 0.35f, ya, -spine * 0.55f, half, yb, spine * 0.55f, steelEdge);
			// канавка (долы) по центру
			if (t0 > 0.05f && t0 < 0.62f) {
				box(b, m, -half * 0.45f, ya, spine * 0.98f, half * 0.10f, yb, spine * 1.05f, steelDark);
				box(b, m, -half * 0.45f, ya, -spine * 1.05f, half * 0.10f, yb, -spine * 0.98f, steelDark);
			}
		}
		// «зубья» на обухе у основания
		for (int i = 0; i < 4; i++) {
			float y = y0 + 0.06f + i * 0.035f;
			box(b, m, -0.042f, y, -0.006f, -0.034f, y + 0.014f, 0.006f, steelDark);
		}
	}

	/** Коробка с имитацией освещения: у каждой грани своя яркость. */
	private static void box(BufferBuilder b, Matrix4f m,
							float x0, float y0, float z0, float x1, float y1, float z1, int[] c) {
		// верх
		quad(b, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c, 1.00f);
		// низ
		quad(b, m, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, c, 0.55f);
		// передняя (+Z)
		quad(b, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c, 0.92f);
		// задняя (-Z)
		quad(b, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, c, 0.70f);
		// правая (+X)
		quad(b, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, c, 0.82f);
		// левая (-X)
		quad(b, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c, 0.62f);
	}

	private static void quad(BufferBuilder b, Matrix4f m,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz,
							 int[] c, float shade) {
		int r = Math.min(255, (int) (c[0] * shade));
		int g = Math.min(255, (int) (c[1] * shade));
		int bl = Math.min(255, (int) (c[2] * shade));
		b.vertex(m, ax, ay, az).color(r, g, bl, 255);
		b.vertex(m, bx, by, bz).color(r, g, bl, 255);
		b.vertex(m, cx, cy, cz).color(r, g, bl, 255);
		b.vertex(m, dx, dy, dz).color(r, g, bl, 255);
	}
}
