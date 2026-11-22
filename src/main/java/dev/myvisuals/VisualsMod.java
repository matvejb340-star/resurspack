package dev.myvisuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class VisualsMod implements ClientModInitializer {
	public static KeyBinding menuKey;
	public static KeyBinding triggerKey;

	@Override
	public void onInitializeClient() {
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.myvisuals.menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_ALT,
				"category.myvisuals"
		));

		triggerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.myvisuals.trigger",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_0,
				"category.myvisuals"
		));

		KillAnimations.register();
		TargetFx.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new VisualsScreen());
				}
			}
			BridgeAssist.tick(client);
			AutoApple.tick(client);
			FallingItems.tick(client);
			KillAnimations.tick(client);
			Radar.tick(client);
			TargetFx.tick(client);
			KnifeRenderer.tick(client);
		});

		HudRenderCallback.EVENT.register((context, tickCounter) -> Radar.render(context));

		WorldRenderEvents.AFTER_TRANSLUCENT.register(SkyFx::render);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(TrajectoryRenderer::render);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(KillAnimations::render);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(TargetFx::render);
	}
}
