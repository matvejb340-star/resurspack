package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

/** Авто-удар: бьёт, когда прицел наведён на игрока, до которого можно дотянуться. */
public final class TriggerBot {
	private TriggerBot() {}

	private static boolean lastKey = false;
	private static long toastUntil = 0;

	public static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) return;

		// клавиша 0 (обычная или на цифровой панели) читается напрямую
		long win = mc.getWindow().getHandle();
		boolean down = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_0) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(win, GLFW.GLFW_KEY_KP_0) == GLFW.GLFW_PRESS;
		if (down && !lastKey && mc.currentScreen == null) {
			Config.triggerBot = !Config.triggerBot;
			toastUntil = System.currentTimeMillis() + 500;
		}
		lastKey = down;

		if (!Config.triggerBot || mc.interactionManager == null || mc.currentScreen != null) return;
		if (!mc.player.isAlive() || mc.player.isSpectator()) return;

		// crosshairTarget уже ограничен дальностью атаки, дальних игроков он не вернёт
		if (!(mc.crosshairTarget instanceof EntityHitResult hit)
				|| mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

		Entity target = hit.getEntity();
		if (!(target instanceof PlayerEntity p) || p == mc.player || !p.isAlive() || p.isSpectator()) return;

		if (mc.player.getAttackCooldownProgress(0.0f) < 0.9f) return;

		mc.interactionManager.attackEntity(mc.player, target);
		mc.player.swingHand(Hand.MAIN_HAND);
	}

	/** Плашка на 0.5 сек при переключении + маленькая метка, пока режим включён. */
	public static void render(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) return;

		long now = System.currentTimeMillis();
		if (now < toastUntil) {
			boolean on = Config.triggerBot;
			Text t = Text.literal("АВТО-УДАР: " + (on ? "ВКЛ" : "ВЫКЛ"));
			int w = mc.textRenderer.getWidth(t);
			MatrixStack ms = c.getMatrices();
			ms.push();
			ms.translate(c.getScaledWindowWidth() / 2f, c.getScaledWindowHeight() / 2f - 40, 0);
			ms.scale(2f, 2f, 1f);
			c.fill(-w / 2 - 4, -3, w / 2 + 4, 11, 0xAA000000);
			c.drawTextWithShadow(mc.textRenderer, t, -w / 2, 0, on ? 0xFF55FF55 : 0xFFFF5555);
			ms.pop();
		}

		if (Config.triggerBot) {
			c.drawTextWithShadow(mc.textRenderer, Text.literal("Авто-удар"), 4, 4, 0xFF55FF55);
		}
	}
}
