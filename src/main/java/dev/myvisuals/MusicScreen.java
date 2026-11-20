package dev.myvisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/** Окно музыки: список песен со звёздочками «избранного» и кнопки управления. */
public class MusicScreen extends Screen {
	private static final int W = 320;
	private static final int H = 236;
	private static final int ROW = 20;
	private static final int LIST_H = 100;

	private final Screen parent;
	private int px, py;
	private int scroll = 0;
	private List<Path> songs = List.of();

	private ButtonWidget pauseBtn;
	private ButtonWidget favBtn;

	public MusicScreen(Screen parent) {
		super(Text.literal("Музыка"));
		this.parent = parent;
	}

	private int listX() {
		return px + 10;
	}

	private int listY() {
		return py + 42;
	}

	private int listW() {
		return W - 20;
	}

	private static Text pauseLabel() {
		return Text.literal(MusicPlayer.isPlaying() ? "Пауза" : "Играть");
	}

	private static Text favLabel() {
		return Text.literal("Только избранные: " + (MusicPlayer.favoritesOnly ? "ВКЛ" : "ВЫКЛ"));
	}

	@Override
	protected void init() {
		MusicPlayer.refresh();
		songs = MusicPlayer.songs();

		px = (this.width - W) / 2;
		py = (this.height - H) / 2;

		int bx = px + 10;
		int row1 = py + 42 + LIST_H + 36;
		int row2 = row1 + 26;

		addDrawableChild(ButtonWidget.builder(Text.literal("<<"), b -> MusicPlayer.prev())
				.dimensions(bx, row1, 40, 20).build());

		pauseBtn = ButtonWidget.builder(pauseLabel(), b -> MusicPlayer.togglePause())
				.dimensions(bx + 44, row1, 80, 20).build();
		addDrawableChild(pauseBtn);

		addDrawableChild(ButtonWidget.builder(Text.literal(">>"), b -> MusicPlayer.next())
				.dimensions(bx + 128, row1, 40, 20).build());

		favBtn = ButtonWidget.builder(favLabel(), b -> {
			MusicPlayer.favoritesOnly = !MusicPlayer.favoritesOnly;
			b.setMessage(favLabel());
		}).dimensions(bx + 172, row1, 128, 20).build();
		addDrawableChild(favBtn);

		addDrawableChild(new ConfigSlider(bx, row2, 150, 20, "Громкость", 0.0, 1.0,
				MusicPlayer.volume, v -> MusicPlayer.volume = (float) v));

		addDrawableChild(ButtonWidget.builder(Text.literal("Открыть папку"), b -> {
			try {
				Util.getOperatingSystem().open(MusicPlayer.dir().toFile());
			} catch (Throwable ignored) {
			}
		}).dimensions(bx + 154, row2, 90, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
				.dimensions(bx + 248, row2, 52, 20).build());
	}

	private int maxScroll() {
		return Math.max(0, songs.size() * ROW - LIST_H);
	}

	private static String fmt(double sec) {
		int s = (int) Math.max(0, sec);
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);

		pauseBtn.setMessage(pauseLabel());

		// панель
		c.fill(px - 2, py - 2, px + W + 2, py + H + 2, 0xFF1A1A1A);
		c.fill(px, py, px + W, py + H, 0xFFC6C6C6);
		c.fill(px, py, px + W, py + 3, 0xFFFFFFFF);
		c.fill(px, py, px + 3, py + H, 0xFFFFFFFF);
		c.fill(px, py + H - 3, px + W, py + H, 0xFF555555);
		c.fill(px + W - 3, py, px + W, py + H, 0xFF555555);

		// заголовок
		Text title = Text.literal("Музыка").formatted(Formatting.GOLD, Formatting.BOLD);
		MatrixStack ms = c.getMatrices();
		ms.push();
		ms.translate(this.width / 2f, py + 8, 0);
		ms.scale(1.5f, 1.5f, 1f);
		c.drawTextWithShadow(this.textRenderer, title, -this.textRenderer.getWidth(title) / 2, 0, 0xFFFFAA00);
		ms.pop();

		String path = "Папка: " + MusicPlayer.dir();
		c.drawText(this.textRenderer, this.textRenderer.trimToWidth(path, W - 20), px + 10, py + 28, 0xFF404040, false);

		// список
		int lx = listX();
		int ly = listY();
		int lw = listW();
		c.fill(lx - 1, ly - 1, lx + lw + 1, ly + LIST_H + 1, 0xFF373737);
		c.fill(lx, ly, lx + lw, ly + LIST_H, 0xFF1E1E1E);

		if (songs.isEmpty()) {
			c.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Здесь пока нет песен"),
					px + W / 2, ly + 30, 0xFFFFFFFF);
			c.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Положи mp3 или wav в папку и открой окно заново"),
					px + W / 2, ly + 46, 0xFFAAAAAA);
		} else {
			c.enableScissor(lx, ly, lx + lw, ly + LIST_H);
			Path cur = MusicPlayer.current();
			for (int i = 0; i < songs.size(); i++) {
				int y = ly + i * ROW - scroll;
				if (y + ROW < ly || y > ly + LIST_H) continue;

				Path s = songs.get(i);
				boolean isCur = s.equals(cur);
				boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= y && mouseY < y + ROW
						&& mouseY >= ly && mouseY < ly + LIST_H;

				if (isCur) c.fill(lx, y, lx + lw, y + ROW, 0x4055FF55);
				else if (hover) c.fill(lx, y, lx + lw, y + ROW, 0x30FFFFFF);

				boolean fav = MusicPlayer.isFavorite(s);
				c.drawTextWithShadow(this.textRenderer, fav ? "★" : "☆", lx + 6, y + 6,
						fav ? 0xFFFFD84A : 0xFF888888);

				String name = this.textRenderer.trimToWidth(MusicPlayer.displayName(s), lw - 40);
				c.drawTextWithShadow(this.textRenderer, name, lx + 24, y + 6, isCur ? 0xFF55FF55 : 0xFFFFFFFF);
			}
			c.disableScissor();

			int max = maxScroll();
			if (max > 0) {
				int thumbH = Math.max(12, LIST_H * LIST_H / (songs.size() * ROW));
				int thumbY = ly + (int) ((LIST_H - thumbH) * (scroll / (double) max));
				c.fill(lx + lw - 3, thumbY, lx + lw, thumbY + thumbH, 0xFF8B8B8B);
			}
		}

		// что играет
		int iy = ly + LIST_H + 6;
		Path cur = MusicPlayer.current();
		String err = MusicPlayer.error();
		if (err != null) {
			c.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(err, lw), lx, iy, 0xFFFF5555);
		} else if (cur == null) {
			c.drawText(this.textRenderer, "Ничего не играет", lx, iy, 0xFF404040, false);
		} else {
			String state = MusicPlayer.isPaused() ? "Пауза: " : (MusicPlayer.isPlaying() ? "Играет: " : "Остановлено: ");
			String time = fmt(MusicPlayer.positionSec()) + " / " + fmt(MusicPlayer.lengthSec());
			int tw = this.textRenderer.getWidth(time);
			String line = this.textRenderer.trimToWidth(state + MusicPlayer.displayName(cur), lw - tw - 8);
			c.drawText(this.textRenderer, line, lx, iy, 0xFF202020, false);
			c.drawText(this.textRenderer, time, lx + lw - tw, iy, 0xFF202020, false);
		}

		int by = iy + 13;
		c.fill(lx, by, lx + lw, by + 4, 0xFF373737);
		double len = MusicPlayer.lengthSec();
		if (cur != null && len > 0) {
			int w = (int) (lw * Math.min(1.0, MusicPlayer.positionSec() / len));
			c.fill(lx, by, lx + w, by + 4, 0xFF55FF55);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		int lx = listX();
		int ly = listY();
		int lw = listW();
		if (button == 0 && mx >= lx && mx < lx + lw && my >= ly && my < ly + LIST_H) {
			int idx = (int) ((my - ly + scroll) / ROW);
			if (idx >= 0 && idx < songs.size()) {
				Path s = songs.get(idx);
				if (mx < lx + 22) {
					MusicPlayer.toggleFavorite(s);
				} else {
					MusicPlayer.play(s);
				}
			}
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - vertical * ROW));
		return true;
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
