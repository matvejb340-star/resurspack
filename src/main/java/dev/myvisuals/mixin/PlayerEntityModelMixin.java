package dev.myvisuals.mixin;

import dev.myvisuals.KneelState;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Поза «на одно колено» для клона убитого игрока.
 * Ноги в майнкрафте не сгибаются, поэтому одна нога выставлена вперёд, другая отведена назад,
 * тело опущено, голова наклонена, правая рука лежит на колене, левая опущена.
 *
 * Части модели берём обычным обращением к полям (без @Shadow), чтобы не было проблем при запуске.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
	@Inject(
			method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
			at = @At("RETURN"),
			require = 0
	)
	private void myvisuals$kneelPose(PlayerEntityRenderState state, CallbackInfo ci) {
		float k = ((KneelState) state).myvisuals$getKneel();
		if (k <= 0f) return;

		BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;

		float drop = 6.0f * k;
		m.head.pivotY += drop;
		m.body.pivotY += drop;
		m.rightArm.pivotY += drop;
		m.leftArm.pivotY += drop;
		m.rightLeg.pivotY += drop;
		m.leftLeg.pivotY += drop;

		// тело слегка подаётся вперёд, ноги сдвигаем вслед за бёдрами
		m.body.pitch = 0.15f * k;
		m.rightLeg.pivotZ += 1.8f * k;
		m.leftLeg.pivotZ += 1.8f * k;

		m.head.pitch = lerp(m.head.pitch, 0.95f, k);

		// правая нога вперёд, левая назад (стоит на колене)
		m.rightLeg.pitch = lerp(m.rightLeg.pitch, -1.05f, k);
		m.leftLeg.pitch = lerp(m.leftLeg.pitch, 1.05f, k);
		m.rightLeg.yaw = lerp(m.rightLeg.yaw, -0.08f, k);
		m.leftLeg.yaw = lerp(m.leftLeg.yaw, 0.08f, k);

		// правая рука на колене, левая чуть отведена
		m.rightArm.pitch = lerp(m.rightArm.pitch, -1.0f, k);
		m.rightArm.roll = lerp(m.rightArm.roll, -0.08f, k);
		m.leftArm.pitch = lerp(m.leftArm.pitch, 0.35f, k);
		m.leftArm.roll = lerp(m.leftArm.roll, 0.15f, k);
	}

	private static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}
}
