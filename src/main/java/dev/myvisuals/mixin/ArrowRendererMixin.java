package dev.myvisuals.mixin;

import dev.myvisuals.Config;
import dev.myvisuals.RpgRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.client.render.entity.state.ArrowEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Стрела в полёте рисуется как боеголовка, если выбрано в настройках. */
@Mixin(ProjectileEntityRenderer.class)
public abstract class ArrowRendererMixin {
	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/ArrowEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void myvisuals$arrow(ArrowEntityRenderState state, MatrixStack matrices,
								 VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (Config.arrowSkin != Config.ArrowSkin.WARHEAD) return;
		RpgRenderer.drawRocket(matrices, state.yaw, state.pitch);
		ci.cancel();
	}
}
