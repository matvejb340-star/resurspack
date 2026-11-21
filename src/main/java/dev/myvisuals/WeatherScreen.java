package dev.myvisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/** Окно «Погода и небо»: две кнопки, каждая переключает свои режимы по кругу. */
public class WeatherScreen extends Screen {
	private static final int W = 260;
	private static final int H = 150;

	private final Screen parent;
	private int px, py;

	public WeatherScreen(Screen parent) {
		super(Text.literal("Погода и небо"));
		this.parent = parent;
	}

	private static Text weatherLabel() {
		return Text.literal("Погода: " + Config.weather.label);
	}

	private static Text skyLabel() {
		return Text.literal("Небо: " + Config.skyMode.label);
	}

	@Override
	protected void init() {
		px = (this.width - W) / 2;
		py = (this.height - H) / 2;

		int bx = px + 20;
		int bw = W - 40;

		addDrawableChild(ButtonWidget.builder(weatherLabel(), b -> {
			Config.weather = Config.weather.next();
			b.setMessage(weatherLabel());
		}).dimensions(bx, py + 42, bw, 20).build());

		addDrawableChild(ButtonWidget.builder(skyLabel(), b -> {
			Config.skyMode = Config.skyMode.next();
			b.setMessage(skyLabel());
		}).dimensions(bx, py + 70, bw, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
				.dimensions(bx, py + 108, bw, 20).build());
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
		super.renderBackground(c, mouseX, mouseY, delta);

		c.fill(px - 2, py - 2, px + W + 2, py + H + 2, 0xFF1A1A1A);
		c.fill(px, py, px + W, py + H, 0xFFC6C6C6);
		c.fill(px, py, px + W, py + 3, 0xFFFFFFFF);
		c.fill(px, py, px + 3, py + H, 0xFFFFFFFF);
		c.fill(px, py + H - 3, px + W, py + H, 0xFF555555);
		c.fill(px + W - 3, py, px + W, py + H, 0xFF555555);

		Text title = Text.literal("Погода и небо").formatted(Formatting.GOLD, Formatting.BOLD);
		MatrixStack ms = c.getMatrices();
		ms.push();
		ms.translate(this.width / 2f, py + 12, 0);
		ms.scale(1.5f, 1.5f, 1f);
		c.drawTextWithShadow(this.textRenderer, title, -this.textRenderer.getWidth(title) / 2, 0, 0xFFFFAA00);
		ms.pop();
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
		if (this.client != null) this.client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
