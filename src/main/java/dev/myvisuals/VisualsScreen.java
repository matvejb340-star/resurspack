package dev.myvisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class VisualsScreen extends Screen {
	public VisualsScreen() {
		super(Text.literal("My Visuals"));
	}

	private static Text trajectoryLabel() {
		return Text.literal("Траектория: " + (Config.trajectory ? "ВКЛ" : "ВЫКЛ"));
	}

	private static Text bridgeLabel() {
		return Text.literal("Помощь в строительстве: " + (Config.bridge ? "ВКЛ" : "ВЫКЛ"));
	}

	private static Text appleLabel() {
		return Text.literal("Авто-яблоко: " + (Config.autoApple ? "ВКЛ" : "ВЫКЛ"));
	}

	private static Text weatherLabel() {
		return Text.literal("Погода: " + Config.weather.label);
	}

	@Override
	protected void init() {
		int w = 220;
		int h = 20;
		int x = this.width / 2 - w / 2;
		int y = this.height / 2 - 58;

		addDrawableChild(ButtonWidget.builder(trajectoryLabel(), b -> {
			Config.trajectory = !Config.trajectory;
			b.setMessage(trajectoryLabel());
		}).dimensions(x, y, w, h).build());

		addDrawableChild(ButtonWidget.builder(bridgeLabel(), b -> {
			Config.bridge = !Config.bridge;
			b.setMessage(bridgeLabel());
		}).dimensions(x, y + 26, w, h).build());

		addDrawableChild(ButtonWidget.builder(appleLabel(), b -> {
			Config.autoApple = !Config.autoApple;
			b.setMessage(appleLabel());
		}).dimensions(x, y + 52, w, h).build());

		addDrawableChild(ButtonWidget.builder(weatherLabel(), b -> {
			Config.weather = Config.weather.next();
			b.setMessage(weatherLabel());
		}).dimensions(x, y + 78, w, h).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
				.dimensions(x, y + 110, w, h).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("My Visuals"),
				this.width / 2, this.height / 2 - 82, 0xFFFFFFFF);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_RIGHT_ALT) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
