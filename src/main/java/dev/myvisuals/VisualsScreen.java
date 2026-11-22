package dev.myvisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Меню «Визуал чушпана» в виде инвентаря: каждая функция лежит в своём слоте. */
public class VisualsScreen extends Screen {
	private static final int COLS = 6;
	private static final int ROWS = 4;
	private static final int SLOT = 28;
	private static final int GAP = 4;
	private static final int PAD = 12;

	/**
	 * on   — null, если слот просто открывает окно (тогда зелёного свечения нет);
	 * mode — текст текущего режима для слотов, которые переключаются по кругу.
	 */
	private record Entry(int slot, ItemStack stack, String name, BooleanSupplier on,
						 Supplier<String> mode, Runnable click, String hint) {}

	private final List<Entry> entries = new ArrayList<>();
	private int px, py, panelW, panelH;

	public VisualsScreen() {
		super(Text.literal("Визуал чушпана"));
	}

	@Override
	protected void init() {
		entries.clear();

		entries.add(new Entry(0, new ItemStack(Items.DIAMOND_SWORD), "Руки и анимация меча",
				null, null,
				() -> {
					if (this.client != null) this.client.setScreen(new HandsScreen(this));
				},
				"Нажми, чтобы открыть"));

		entries.add(new Entry(3, new ItemStack(Items.BOW), "Траектория",
				() -> Config.trajectory, null,
				() -> Config.trajectory = !Config.trajectory,
				"Нажми, чтобы включить или выключить"));

		entries.add(new Entry(8, new ItemStack(Items.COBBLESTONE), "Помощь в строительстве",
				() -> Config.bridge, null,
				() -> Config.bridge = !Config.bridge,
				"Нажми, чтобы включить или выключить"));

		entries.add(new Entry(10, new ItemStack(Items.GOLDEN_APPLE), "Авто-яблоко",
				() -> Config.autoApple, null,
				() -> Config.autoApple = !Config.autoApple,
				"Нажми, чтобы включить или выключить"));

		entries.add(new Entry(13, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), "Авто-яблоко +++",
				() -> Config.autoApplePlus, null,
				() -> Config.autoApplePlus = !Config.autoApplePlus,
				"Нажми, чтобы включить или выключить"));

		entries.add(new Entry(15, new ItemStack(Items.WATER_BUCKET), "Погода",
				null,
				() -> "Сейчас: " + Config.weather.label + ", небо: " + Config.skyMode.label,
				() -> {
					if (this.client != null) this.client.setScreen(new WeatherScreen(this));
				},
				"Нажми, чтобы открыть"));

		entries.add(new Entry(20, new ItemStack(Items.SKELETON_SKULL), "Анимация убийства",
				() -> Config.killAnim != Config.KillAnim.OFF,
				() -> "Режим: " + Config.killAnim.label,
				() -> Config.killAnim = Config.killAnim.next(),
				"Нажми, чтобы сменить анимацию"));

		entries.add(new Entry(11, new ItemStack(Items.COMPASS), "Радар и предупреждение",
				() -> Config.radar, null,
				() -> Config.radar = !Config.radar,
				"Нажми, чтобы включить или выключить"));

		entries.add(new Entry(16, new ItemStack(Items.NETHERITE_SWORD), "Авто-удар (цифра 0)",
				() -> Config.triggerBot, null,
				() -> Config.triggerBot = !Config.triggerBot,
				"Бьёт сам, когда прицел на игроке в зоне досягаемости. Клавиша 0 включает и выключает"));

		panelW = COLS * (SLOT + GAP) - GAP + PAD * 2;
		panelH = ROWS * (SLOT + GAP) - GAP + PAD * 2;
		px = (this.width - panelW) / 2;
		py = (this.height - panelH) / 2 + 16;
	}

	private int slotX(int slot) {
		return px + PAD + (slot % COLS) * (SLOT + GAP);
	}

	private int slotY(int slot) {
		return py + PAD + (slot / COLS) * (SLOT + GAP);
	}

	private Entry entryAt(double mx, double my) {
		for (Entry e : entries) {
			int sx = slotX(e.slot());
			int sy = slotY(e.slot());
			if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) return e;
		}
		return null;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		drawTitle(context);
		drawPanel(context);

		Entry hovered = null;

		for (int i = 0; i < COLS * ROWS; i++) {
			drawSlotBackground(context, slotX(i), slotY(i));
		}

		for (Entry e : entries) {
			int sx = slotX(e.slot());
			int sy = slotY(e.slot());
			boolean isHover = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
			if (isHover) hovered = e;

			if (e.on() != null && e.on().getAsBoolean()) {
				int pulse = (int) (80 + 30 * Math.sin(System.currentTimeMillis() / 300.0));
				int glow = (pulse << 24) | 0x55FF55;
				context.fill(sx, sy, sx + SLOT, sy + SLOT, glow);
				int line = 0xFF55FF55;
				context.fill(sx - 2, sy - 2, sx + SLOT + 2, sy, line);
				context.fill(sx - 2, sy + SLOT, sx + SLOT + 2, sy + SLOT + 2, line);
				context.fill(sx - 2, sy, sx, sy + SLOT, line);
				context.fill(sx + SLOT, sy, sx + SLOT + 2, sy + SLOT, line);
			}

			if (isHover) {
				context.fill(sx, sy, sx + SLOT, sy + SLOT, 0x60FFFFFF);
			}

			MatrixStack ms = context.getMatrices();
			ms.push();
			ms.translate(sx + 4, sy + 4, 0);
			ms.scale(1.25f, 1.25f, 1f);
			context.drawItem(e.stack(), 0, 0);
			ms.pop();
		}

		if (hovered != null) {
			List<Text> lines = new ArrayList<>();
			lines.add(Text.literal(hovered.name()).formatted(Formatting.WHITE));
			if (hovered.mode() != null) {
				lines.add(Text.literal(hovered.mode().get()).formatted(Formatting.YELLOW));
			}
			if (hovered.on() != null) {
				if (hovered.on().getAsBoolean()) {
					lines.add(Text.literal("Включено").formatted(Formatting.GREEN));
				} else {
					lines.add(Text.literal("Выключено").formatted(Formatting.GRAY));
				}
			}
			lines.add(Text.literal(hovered.hint()).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
			context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
		}
	}

	private void drawTitle(DrawContext context) {
		Text title = Text.literal("Визуал чушпана").formatted(Formatting.GOLD, Formatting.BOLD);
		float scale = 2.2f;
		int cx = this.width / 2;
		int ty = py - 38;

		MatrixStack ms = context.getMatrices();
		ms.push();
		ms.translate(cx, ty, 0);
		ms.scale(scale, scale, 1f);
		int tw = this.textRenderer.getWidth(title);
		context.drawTextWithShadow(this.textRenderer, title, -tw / 2, 0, 0xFFFFAA00);
		ms.pop();

		int half = (int) (tw * scale / 2) + 22;
		sparkle(context, cx - half, ty + 9);
		sparkle(context, cx + half, ty + 9);
	}

	/** Маленькая золотая «звёздочка» по бокам заголовка. */
	private void sparkle(DrawContext c, int x, int y) {
		int gold = 0xFFFFD84A;
		int soft = 0x99FFD84A;
		c.fill(x - 1, y - 9, x + 1, y + 9, gold);
		c.fill(x - 9, y - 1, x + 9, y + 1, gold);
		c.fill(x - 3, y - 3, x + 3, y + 3, soft);
		c.fill(x - 5, y - 5, x - 3, y - 3, soft);
		c.fill(x + 3, y - 5, x + 5, y - 3, soft);
		c.fill(x - 5, y + 3, x - 3, y + 5, soft);
		c.fill(x + 3, y + 3, x + 5, y + 5, soft);
	}

	private void drawPanel(DrawContext c) {
		c.fill(px - 2, py - 2, px + panelW + 2, py + panelH + 2, 0xFF1A1A1A);
		c.fill(px, py, px + panelW, py + panelH, 0xFFC6C6C6);
		c.fill(px, py, px + panelW, py + 3, 0xFFFFFFFF);
		c.fill(px, py, px + 3, py + panelH, 0xFFFFFFFF);
		c.fill(px, py + panelH - 3, px + panelW, py + panelH, 0xFF555555);
		c.fill(px + panelW - 3, py, px + panelW, py + panelH, 0xFF555555);
	}

	private void drawSlotBackground(DrawContext c, int sx, int sy) {
		c.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF8B8B8B);
		c.fill(sx, sy, sx + SLOT, sy + 2, 0xFF373737);
		c.fill(sx, sy, sx + 2, sy + SLOT, 0xFF373737);
		c.fill(sx, sy + SLOT - 2, sx + SLOT, sy + SLOT, 0xFFFFFFFF);
		c.fill(sx + SLOT - 2, sy, sx + SLOT, sy + SLOT, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			Entry e = entryAt(mouseX, mouseY);
			if (e != null) {
				if (this.client != null) {
					this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
				}
				e.click().run();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
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
