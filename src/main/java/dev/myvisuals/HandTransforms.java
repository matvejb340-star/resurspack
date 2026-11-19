package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/** Размер, положение рук и свои анимации удара мечом. */
public final class HandTransforms {
	private HandTransforms() {}

	// кадры: { время 0..1, rotX, rotY, rotZ (градусы), смещение X, Y, Z (блоки) }
	private static final float[][] CHOP = {
			{0.00f, 0, 0, 0, 0, 0.00f, 0.00f},
			{0.25f, 55, 0, 0, 0, 0.10f, 0.05f},
			{0.55f, -45, 0, 0, 0, -0.12f, -0.15f},
			{1.00f, 0, 0, 0, 0, 0.00f, 0.00f}
	};
	private static final float[][] STAB = {
			{0.00f, 0, 0, 0, 0, 0.00f, 0.00f},
			{0.25f, 8, 0, 0, 0, 0.02f, 0.12f},
			{0.55f, -35, 0, 0, 0, 0.04f, -0.45f},
			{1.00f, 0, 0, 0, 0, 0.00f, 0.00f}
	};
	private static final float[][] SLASH = {
			{0.00f, 0, 0, 0, 0.00f, 0.00f, 0.00f},
			{0.30f, -15, -20, 10, 0.10f, 0.00f, -0.20f},
			{0.70f, 25, 25, -30, -0.25f, 0.05f, 0.35f},
			{1.00f, 0, 0, 0, 0.00f, 0.00f, 0.00f}
	};

	public static void apply(AbstractClientPlayerEntity player, Hand hand, float tickDelta,
							 ItemStack item, MatrixStack m) {
		boolean main = hand == Hand.MAIN_HAND;
		Arm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == Arm.RIGHT ? 1f : -1f;

		double scale = main ? Config.mainScale : Config.offScale;
		double ox = main ? Config.mainX : Config.offX;
		double oy = main ? Config.mainY : Config.offY;
		double oz = main ? Config.mainZ : Config.offZ;

		m.translate(ox, oy, oz);

		// точка, где обычно находится рука; вокруг неё масштабируем и вращаем
		double px = side * 0.56;
		double py = -0.52;
		double pz = -0.72;
		m.translate(px, py, pz);

		if (scale != 1.0) {
			m.scale((float) scale, (float) scale, (float) scale);
		}

		if (main && Config.swordAnim != Config.SwordAnim.VANILLA && item.isIn(ItemTags.SWORDS)) {
			float p = player.getHandSwingProgress(tickDelta);
			if (p > 0f && p < 1f) {
				animate(m, side, p);
			}
		}

		m.translate(-px, -py, -pz);
	}

	private static void animate(MatrixStack m, float side, float p) {
		float[][] keys = switch (Config.swordAnim) {
			case CHOP -> CHOP;
			case STAB -> STAB;
			case SLASH -> SLASH;
			default -> null;
		};
		if (keys == null) return;

		float[] v = new float[6];
		for (int i = 0; i < keys.length - 1; i++) {
			float[] a = keys[i];
			float[] b = keys[i + 1];
			if (p >= a[0] && p <= b[0]) {
				float u = (p - a[0]) / (b[0] - a[0]);
				u = u * u * (3 - 2 * u);
				for (int k = 0; k < 6; k++) {
					v[k] = a[k + 1] + (b[k + 1] - a[k + 1]) * u;
				}
				break;
			}
		}

		m.translate(v[3] * side, v[4], v[5]);
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(v[0]));
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(v[1] * side));
		m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(v[2] * side));
	}

	/** Если включена своя анимация меча, обычный взмах отключаем. */
	public static float suppressVanillaSwing(float value) {
		if (value <= 0f || Config.swordAnim == Config.SwordAnim.VANILLA) return value;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) return 0f;
		return value;
	}
}
