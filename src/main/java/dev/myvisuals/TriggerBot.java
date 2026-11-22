package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/** Авто-удар: бьёт, когда прицел наведён на игрока, до которого можно дотянуться. */
public final class TriggerBot {
	private TriggerBot() {}

	public static void tick(MinecraftClient mc) {
		if (!Config.triggerBot || mc.player == null || mc.world == null
				|| mc.interactionManager == null || mc.currentScreen != null) return;
		if (!mc.player.isAlive() || mc.player.isSpectator()) return;

		// crosshairTarget уже ограничен дальностью атаки, так что дальних игроков он не вернёт
		if (!(mc.crosshairTarget instanceof EntityHitResult hit)
				|| mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

		Entity target = hit.getEntity();
		if (!(target instanceof PlayerEntity p) || p == mc.player || !p.isAlive() || p.isSpectator()) return;

		// бьём только когда оружие перезарядилось, как при честной атаке
		if (mc.player.getAttackCooldownProgress(0.0f) < 0.95f) return;

		mc.interactionManager.attackEntity(mc.player, target);
		mc.player.swingHand(Hand.MAIN_HAND);
	}
}
