package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Радар в углу экрана и предупреждение «враг движется к вам». */
public final class Radar {
	private Radar() {}

	private static final double RANGE = 50.0;
	private static final double WARN_DIST = 10.0;
	private static final long WARN_MS = 1500;
	private static final int SIZE = 96;
	private static final int MARGIN = 6;

	private static final Set<UUID> NEAR = new HashSet<>();
	private static long warnUntil = 0;

	private static boolean valid(MinecraftClient mc, PlayerEntity p) {
		if (p == mc.player || p.isSpectator() || !p.isAlive()) return false;
		ClientPlayNetworkHandler h = mc.getNetworkHandler();
		// игроки без записи в списке игроков — это NPC, их на радаре не показываем
		return h != null && h.getPlayerListEntry(p.getUuid()) != null;
	}

	public static void tick(MinecraftClient mc) {
		if (!Config.radar || mc.player == null || mc.world == null) {
			NEAR.clear();
			return;
		}

		Set<UUID> now = new HashSet<>();
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (!valid(mc, p)) continue;
			if (mc.player.distanceTo(p) <= WARN_DIST) {
				now.add(p.getUuid());
				if (!NEAR.contains(p.getUuid())) {
					warnUntil = System.currentTimeMillis() + WARN_MS;
				}
			}
		}
		NEAR.clear();
		NEAR.addAll(now);
	}

	public static void render(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!Config.radar || mc.player == null || mc.world == null || mc.options.hudHidden) return;

		int x = mc.getWindow().getScaledWidth() - SIZE - MARGIN;
		int y = MARGIN;
		int cx = x + SIZE / 2;
		int cy = y + SIZE / 2;
		double radius = SIZE / 2.0 - 4;

		// фон и рамка
		c.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFF000000);
		c.fill(x, y, x + SIZE, y + SIZE, 0xAA101820);

		// перекрестие и кольцо на 25 блоках
		c.fill(cx, y, cx + 1, y + SIZE, 0x40FFFFFF);
		c.fill(x, cy, x + SIZE, cy + 1, 0x40FFFFFF);
		for (int i = 0; i < 48; i++) {
			double a = Math.PI * 2 * i / 48.0;
			int rx = cx + (int) Math.round(Math.cos(a) * radius / 2);
			int ry = cy + (int) Math.round(Math.sin(a) * radius / 2);
			c.fill(rx, ry, rx + 1, ry + 1, 0x50FFFFFF);
		}

		// радар повёрнут по взгляду: верх экрана = куда ты смотришь
		double yaw = Math.toRadians(mc.player.getYaw());
		double cs = Math.cos(yaw);
		double sn = Math.sin(yaw);

		int count = 0;
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (!valid(mc, p)) continue;

			double dx = p.getX() - mc.player.getX();
			double dz = p.getZ() - mc.player.getZ();
			if (Math.hypot(dx, dz) > RANGE) continue;

			double right = -dx * cs - dz * sn;
			double forward = -dx * sn + dz * cs;
			int sx = cx + (int) Math.round(right / RANGE * radius);
			int sy = cy - (int) Math.round(forward / RANGE * radius);

			boolean close = mc.player.distanceTo(p) <= WARN_DIST;
			c.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF000000);
			c.fill(sx - 1, sy - 1, sx + 2, sy + 2, close ? 0xFFFF2020 : 0xFFFF7070);
			count++;
		}

		// ты в центре
		c.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF000000);
		c.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF55FF55);

		c.drawTextWithShadow(mc.textRenderer, "Игроков: " + count, x, y + SIZE + 4, 0xFFFFFFFF);

		// предупреждение в центре экрана
		long left = warnUntil - System.currentTimeMillis();
		if (left > 0) {
			float fade = Math.min(1f, left / 400f);
			int alpha = Math.max(8, (int) (255 * fade));
			Text t = Text.literal("ВРАГ ДВИЖЕТСЯ К ВАМ!").styled(s -> s.withBold(true));
			float scale = 2.2f;
			int w = mc.textRenderer.getWidth(t);
			var ms = c.getMatrices();
			ms.push();
			ms.translate(mc.getWindow().getScaledWidth() / 2f, mc.getWindow().getScaledHeight() / 2f - 36, 0);
			ms.scale(scale, scale, 1f);
			c.drawTextWithShadow(mc.textRenderer, t, -w / 2, 0, (alpha << 24) | 0xFF2020);
			ms.pop();
		}
	}
}
