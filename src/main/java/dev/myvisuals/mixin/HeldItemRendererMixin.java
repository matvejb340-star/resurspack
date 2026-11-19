package dev.myvisuals.mixin;

import dev.myvisuals.HandTransforms;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
	private void myvisuals$pre(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
							   float swingProgress, ItemStack item, float equipProgress,
							   MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
							   CallbackInfo ci) {
		matrices.push();
		if (!item.isEmpty()) {
			HandTransforms.apply(player, hand, tickDelta, item, matrices);
		}
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void myvisuals$post(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
								float swingProgress, ItemStack item, float equipProgress,
								MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
								CallbackInfo ci) {
		matrices.pop();
	}

	@ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, index = 5)
	private float myvisuals$swing(float swingProgress) {
		return HandTransforms.suppressVanillaSwing(swingProgress);
	}
}
