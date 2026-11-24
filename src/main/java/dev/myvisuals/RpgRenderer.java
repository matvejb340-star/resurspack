package dev.myvisuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** РПГ вместо лука в первом лице: труба с раструбом, прицел, рукоятки и зелёная боеголовка. */
public final class RpgRenderer {
	private RpgRenderer() {}

	public static boolean active(ItemStack stack) {
		return Config.bowSkin == Config.BowSkin.RPG && stack != null && stack.isOf(Items.BOW);
	}

	public static void draw(MatrixStack ms, AbstractClientPlayerEntity player, float tickDelta, float equip) {
		ms.push();

		// на плече справа, дулом вперёд; при доставании выезжает снизу
		ms.translate(0.46f, -0.34f - 0.55f * equip, -0.30f);

		// натяжение: РПГ поднимается к прицелу
		float aim = 0f;
		if (player.isUsingItem() && player.getActiveItem().isOf(Items.BOW)) {
			aim = Math.min(1f, (player.getItemUseTime() + tickDelta) / 8f);
			aim = aim * aim * (3f - 2f * aim);
		}
		ms.translate(-0.24f * aim, 0.12f * aim, 0.08f * aim);

		ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(4f - 4f * aim));
		ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f)); // ось модели Y -> вперёд
		ms.translate(0f, -0.30f, 0f);

		Matrix4f m = ms.peek().getPositionMatrix();

		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();

		BufferBuilder b = Tessellator.getInstance()
				.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		build(b, m);
		BufferRenderer.drawWithGlobalProgram(b.end());

		RenderSystem.enableCull();
		ms.pop();
	}

	private static void build(BufferBuilder b, Matrix4f m) {
		int[] olive = {92, 100, 78};
		int[] oliveDark = {62, 70, 54};
		int[] steel = {120, 126, 136};
		int[] black = {38, 40, 44};
		int[] grn = {96, 175, 58};
		int[] grnDark = {58, 120, 36};
		int[] red = {190, 60, 40};

		// тыльный раструб
		float[][] venturi = {
				{-0.14f, 0.070f}, {-0.135f, 0.074f}, {-0.06f, 0.056f}, {0.00f, 0.046f}
		};
		ModelKit.lathe(b, m, venturi, 22, 0f, 0f, steel, 0.8f);

		// труба
		float[][] tube = {
				{0.000f, 0.046f}, {0.012f, 0.050f}, {0.030f, 0.050f}, {0.036f, 0.046f},
				{0.200f, 0.046f}, {0.206f, 0.050f}, {0.230f, 0.050f}, {0.236f, 0.046f},
				{0.500f, 0.046f}, {0.506f, 0.052f}, {0.540f, 0.052f}, {0.548f, 0.040f}
		};
		ModelKit.lathe(b, m, tube, 22, 0f, 0f, olive, 0.55f);

		// тёмные бандажи
		float[][] band1 = {{0.100f, 0.046f}, {0.103f, 0.0495f}, {0.117f, 0.0495f}, {0.120f, 0.046f}};
		float[][] band2 = {{0.350f, 0.046f}, {0.353f, 0.0495f}, {0.367f, 0.0495f}, {0.370f, 0.046f}};
		ModelKit.lathe(b, m, band1, 22, 0f, 0f, oliveDark, 0.4f);
		ModelKit.lathe(b, m, band2, 22, 0f, 0f, oliveDark, 0.4f);

		// боеголовка: хвостовик, зелёная овальная часть и острый нос
		float[][] stem = {
				{0.540f, 0.020f}, {0.560f, 0.024f}, {0.600f, 0.030f}
		};
		ModelKit.lathe(b, m, stem, 16, 0f, 0f, black, 0.7f);
		float[][] warhead = {
				{0.590f, 0.030f}, {0.610f, 0.050f}, {0.640f, 0.066f}, {0.680f, 0.074f},
				{0.730f, 0.072f}, {0.775f, 0.060f}, {0.815f, 0.042f}, {0.850f, 0.022f},
				{0.872f, 0.000f}
		};
		ModelKit.lathe(b, m, warhead, 24, 0f, 0f, grn, 0.75f);
		// тёмно-зелёный пояс и красная полоса
		float[][] belt = {{0.672f, 0.0735f}, {0.676f, 0.0775f}, {0.698f, 0.0775f}, {0.702f, 0.0735f}};
		ModelKit.lathe(b, m, belt, 24, 0f, 0f, grnDark, 0.5f);
		float[][] stripe = {{0.724f, 0.0725f}, {0.727f, 0.0755f}, {0.740f, 0.0755f}, {0.743f, 0.0715f}};
		ModelKit.lathe(b, m, stripe, 24, 0f, 0f, red, 0.5f);

		// пистолетная рукоять и передняя ручка (вниз = -Z)
		ModelKit.box(b, m, -0.014f, 0.180f, -0.130f, 0.014f, 0.230f, -0.040f, black, 0.35f);
		ModelKit.box(b, m, -0.016f, 0.262f, -0.100f, 0.016f, 0.290f, -0.040f, black, 0.35f);
		ModelKit.box(b, m, -0.020f, 0.170f, -0.058f, 0.020f, 0.300f, -0.046f, steel, 0.8f);

		// прицел сверху (+Z)
		ModelKit.box(b, m, -0.008f, 0.300f, 0.044f, 0.008f, 0.380f, 0.070f, black, 0.5f);
		ModelKit.box(b, m, -0.020f, 0.372f, 0.060f, 0.020f, 0.384f, 0.100f, steel, 0.8f);
		ModelKit.box(b, m, -0.004f, 0.470f, 0.046f, 0.004f, 0.476f, 0.074f, steel, 0.8f);

		// ремешок-петля
		ModelKit.torusZ(b, m, 0f, 0.060f, 0f, 0.012f, 0.004f, 10, 6, black, 0.3f);
	}
}
