package dev.myvisuals.mixin;

import dev.myvisuals.KillAnimations;
import dev.myvisuals.KneelState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
	@Inject(
			method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
			at = @At("RETURN"),
			require = 0
	)
	private void myvisuals$kneelState(AbstractClientPlayerEntity entity, PlayerEntityRenderState state,
									  float tickDelta, CallbackInfo ci) {
		((KneelState) state).myvisuals$setKneel(KillAnimations.kneelProgress(entity, tickDelta));
	}
}
