package dev.myvisuals.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.myvisuals.Config;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeMixin {
	@Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
	private void myvisuals$forcePrecipitation(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
		// Только для клиента (рендер-поток), чтобы не трогать логику сервера.
		if (!RenderSystem.isOnRenderThread()) return;
		switch (Config.weather) {
			case RAIN, THUNDER -> cir.setReturnValue(Biome.Precipitation.RAIN);
			case SNOW -> cir.setReturnValue(Biome.Precipitation.SNOW);
			default -> {}
		}
	}
}
