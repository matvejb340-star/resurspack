package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Авто-яблоко: если в хотбаре (9 слотов) лежит золотое яблоко и здоровья стало
 * половина или меньше, мод переключает слот на яблоко и один раз его съедает.
 *
 * Обычный режим: после еды возвращается прежний слот.
 * Режим +++: рука остаётся на слоте с яблоком.
 *
 * Снова сработает, только когда здоровье поднимется выше половины.
 */
public final class AutoApple {
	private AutoApple() {}

	private static boolean eating = false;
	private static boolean armed = true;
	private static boolean started = false;
	private static boolean keepSlot = false;
	private static int prevSlot = -1;
	private static int appleSlot = -1;
	private static int ticks = 0;

	private static void stop(MinecraftClient mc) {
		mc.options.useKey.setPressed(false);
		ClientPlayerEntity p = mc.player;
		if (!keepSlot && p != null && prevSlot >= 0 && p.getInventory().selectedSlot == appleSlot) {
			p.getInventory().selectedSlot = prevSlot;
		}
		eating = false;
		started = false;
		prevSlot = -1;
		appleSlot = -1;
		ticks = 0;
	}

	public static void tick(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		boolean enabled = Config.autoApple || Config.autoApplePlus;

		if (p == null || mc.interactionManager == null || !enabled) {
			if (eating) stop(mc);
			armed = true;
			return;
		}

		float max = p.getMaxHealth();
		float hp = p.getHealth();
		boolean low = hp > 0 && hp <= max / 2.0f;
		if (!low) armed = true;

		if (eating) {
			ticks++;
			if (mc.currentScreen != null || !p.isAlive()) {
				stop(mc);
				return;
			}
			if (p.isUsingItem()) started = true;
			if ((started && !p.isUsingItem()) || (!started && ticks > 20)) {
				stop(mc);
				return;
			}
			mc.options.useKey.setPressed(true);
			return;
		}

		if (!armed || !low || mc.currentScreen != null) return;

		int slot = -1;
		for (int i = 0; i < 9; i++) {
			if (p.getInventory().getStack(i).isOf(Items.GOLDEN_APPLE)) {
				slot = i;
				break;
			}
		}
		if (slot < 0) return;

		keepSlot = Config.autoApplePlus;
		prevSlot = p.getInventory().selectedSlot;
		appleSlot = slot;
		p.getInventory().selectedSlot = slot;
		armed = false;
		eating = true;
		ticks = 0;

		mc.options.useKey.setPressed(true);
		mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
		started = p.isUsingItem();
	}
}
