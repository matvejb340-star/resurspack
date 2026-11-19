package dev.myvisuals.mixin;

import dev.myvisuals.Config;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
	@Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
	private void myvisuals$rain(float delta, CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof ClientWorld)) return;
		switch (Config.weather) {
			case CLEAR -> cir.setReturnValue(0.0f);
			case RAIN, SNOW, THUNDER -> cir.setReturnValue(1.0f);
			default -> {}
		}
	}

	@Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
	private void myvisuals$thunder(float delta, CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof ClientWorld)) return;
		switch (Config.weather) {
			case CLEAR, RAIN, SNOW -> cir.setReturnValue(0.0f);
			case THUNDER -> cir.setReturnValue(1.0f);
			default -> {}
		}
	}
}
