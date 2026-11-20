package dev.myvisuals.mixin;

import dev.myvisuals.KneelState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements KneelState {
	@Unique
	private float myvisuals$kneel = 0f;

	@Override
	public float myvisuals$getKneel() {
		return myvisuals$kneel;
	}

	@Override
	public void myvisuals$setKneel(float value) {
		myvisuals$kneel = value;
	}
}
