package dev.myvisuals.mixin;

import dev.myvisuals.KneelState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Поза «на одно колено» для клона убитого игрока.
 * Ноги в майнкрафте не сгибаются, поэтому одна нога выставлена вперёд, другая отведена назад,
 * тело опущено, голова наклонена, правая рука лежит на колене, левая опущена.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
	@Shadow @Final public ModelPart head;
	@Shadow @Final public ModelPart body;
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;
	@Shadow @Final public ModelPart rightLeg;
	@Shadow @Final public ModelPart leftLeg;

	@Inject(
			method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
			at = @At("RETURN"),
			require = 0
	)
	private void myvisuals$kneelPose(PlayerEntityRenderState state, CallbackInfo ci) {
		float k = ((KneelState) state).myvisuals$getKneel();
		if (k <= 0f) return;

		float drop = 6.0f * k;
		head.pivotY += drop;
		body.pivotY += drop;
		rightArm.pivotY += drop;
		leftArm.pivotY += drop;
		rightLeg.pivotY += drop;
		leftLeg.pivotY += drop;

		// тело слегка подаётся вперёд, ноги сдвигаем вслед за бёдрами
		body.pitch = 0.15f * k;
		rightLeg.pivotZ += 1.8f * k;
		leftLeg.pivotZ += 1.8f * k;

		head.pitch = lerp(head.pitch, 0.95f, k);

		// правая нога вперёд, левая назад (стоит на колене)
		rightLeg.pitch = lerp(rightLeg.pitch, -1.05f, k);
		leftLeg.pitch = lerp(leftLeg.pitch, 1.05f, k);
		rightLeg.yaw = lerp(rightLeg.yaw, -0.08f, k);
		leftLeg.yaw = lerp(leftLeg.yaw, 0.08f, k);

		// правая рука на колене, левая чуть отведена
		rightArm.pitch = lerp(rightArm.pitch, -1.0f, k);
		rightArm.roll = lerp(rightArm.roll, -0.08f, k);
		leftArm.pitch = lerp(leftArm.pitch, 0.35f, k);
		leftArm.roll = lerp(leftArm.roll, 0.15f, k);
	}

	private static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}
}
