package dev.myvisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class HandsScreen extends Screen {
	private final Screen parent;

	public HandsScreen(Screen parent) {
		super(Text.literal("Руки и анимация меча"));
		this.parent = parent;
	}

	private static Text animLabel() {
		return Text.literal("Анимация меча: " + Config.swordAnim.label);
	}

	private static Text targetLabel() {
		return Text.literal("Таргет-анимация: " + Config.targetFx.label);
	}

	@Override
	protected void init() {
		int colW = 150;
		int cx = this.width / 2;
		int xl = cx - 155;
		int xr = cx + 5;
		int y0 = this.height / 2 - 90;
		int sy = y0 + 30;

		// основная рука
		addDrawableChild(new ConfigSlider(xl, sy, colW, 20, "Размер", 0.3, 2.0, Config.mainScale, v -> Config.mainScale = v));
		addDrawableChild(new ConfigSlider(xl, sy + 24, colW, 20, "Влево/вправо", -0.6, 0.6, Config.mainX, v -> Config.mainX = v));
		addDrawableChild(new ConfigSlider(xl, sy + 48, colW, 20, "Вверх/вниз", -0.6, 0.6, Config.mainY, v -> Config.mainY = v));
		addDrawableChild(new ConfigSlider(xl, sy + 72, colW, 20, "Ближе/дальше", -0.6, 0.6, Config.mainZ, v -> Config.mainZ = v));

		// вторая рука
		addDrawableChild(new ConfigSlider(xr, sy, colW, 20, "Размер", 0.3, 2.0, Config.offScale, v -> Config.offScale = v));
		addDrawableChild(new ConfigSlider(xr, sy + 24, colW, 20, "Влево/вправо", -0.6, 0.6, Config.offX, v -> Config.offX = v));
		addDrawableChild(new ConfigSlider(xr, sy + 48, colW, 20, "Вверх/вниз", -0.6, 0.6, Config.offY, v -> Config.offY = v));
		addDrawableChild(new ConfigSlider(xr, sy + 72, colW, 20, "Ближе/дальше", -0.6, 0.6, Config.offZ, v -> Config.offZ = v));

		int ry = sy + 102;
		addDrawableChild(ButtonWidget.builder(animLabel(), b -> {
			Config.swordAnim = Config.swordAnim.next();
			b.setMessage(animLabel());
		}).dimensions(xl, ry, 205, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить"), b -> {
			Config.resetHands();
			this.clearAndInit();
		}).dimensions(cx + 55, ry, 100, 20).build());

		addDrawableChild(ButtonWidget.builder(targetLabel(), b -> {
			Config.targetFx = Config.targetFx.next();
			b.setMessage(targetLabel());
		}).dimensions(xl, ry + 26, 310, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
				.dimensions(cx - 100, ry + 52, 200, 20).build());
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// без затемнения и размытия, чтобы было видно руку при настройке
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int cx = this.width / 2;
		int y0 = this.height / 2 - 90;
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Руки и анимация меча"), cx, y0, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Основная рука"), cx - 80, y0 + 16, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Вторая рука"), cx + 80, y0 + 16, 0xFFFFFFFF);
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
	public void close() {
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
