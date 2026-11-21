package dev.myvisuals.mixin;

import dev.myvisuals.HandTransforms;
import dev.myvisuals.KnifeRenderer;
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
	/** true, если мы сделали push и должны сделать pop в конце метода */
	private static boolean myvisuals$pushed = false;

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void myvisuals$pre(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
							   float swingProgress, ItemStack item, float equipProgress,
							   MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
							   CallbackInfo ci) {
		myvisuals$pushed = false;

		// нож M9 вместо меча
		if (hand == Hand.MAIN_HAND && KnifeRenderer.active(item)) {
			KnifeRenderer.draw(matrices, tickDelta, swingProgress);
			ci.cancel();
			return;
		}

		matrices.push();
		myvisuals$pushed = true;
		if (!item.isEmpty()) {
			HandTransforms.apply(player, hand, tickDelta, item, matrices);
		}
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void myvisuals$post(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
								float swingProgress, ItemStack item, float equipProgress,
								MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
								CallbackInfo ci) {
		if (myvisuals$pushed) {
			myvisuals$pushed = false;
			matrices.pop();
		}
	}

	@ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, index = 5)
	private float myvisuals$swing(float swingProgress) {
		return HandTransforms.suppressVanillaSwing(swingProgress);
	}
}
