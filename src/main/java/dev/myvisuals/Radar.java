package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Радар в правом верхнем углу и предупреждение «К вам бежит враг».
 *
 * Игрок появляется на радаре, когда он ближе 60 блоков.
 * Предупреждение приходит, когда игрок подбежал ближе 35 блоков. Для этого же игрока
 * оно сработает снова только после того, как он отойдёт дальше 45 блоков и снова подбежит.
 */
public final class Radar {
	private Radar() {}

	private static final double RANGE = 60.0;
	private static final double WARN_NEAR = 35.0;
	private static final double REARM = 45.0;
	private static final long WARN_MS = 1500;
	private static final int SIZE = 96;
	private static final int MARGIN = 6;

	private static final Map<UUID, Boolean> ARMED = new HashMap<>();
	private static final Map<UUID, double[]> LAST = new HashMap<>();
	private static final Map<UUID, double[]> MOVE = new HashMap<>();
	private static long warnUntil = 0;

	private static boolean valid(MinecraftClient mc, PlayerEntity p) {
		if (p == mc.player || p.isSpectator() || !p.isAlive()) return false;
		ClientPlayNetworkHandler h = mc.getNetworkHandler();
		// игроки без записи в списке игроков — это NPC, их на радаре нет
		return h != null && h.getPlayerListEntry(p.getUuid()) != null;
	}

	private static void clearAll() {
		ARMED.clear();
		LAST.clear();
		MOVE.clear();
	}

	public static void tick(MinecraftClient mc) {
		if (!Config.radar || mc.player == null || mc.world == null) {
			clearAll();
			return;
		}

		Set<UUID> seen = new HashSet<>();
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (!valid(mc, p)) continue;
			UUID id = p.getUuid();
			seen.add(id);

			double d = mc.player.distanceTo(p);
			boolean armed = ARMED.getOrDefault(id, d > WARN_NEAR);
			if (d > REARM) {
				armed = true;
			} else if (armed && d <= WARN_NEAR) {
				armed = false;
				warnUntil = System.currentTimeMillis() + WARN_MS;
			}
			ARMED.put(id, armed);

			// куда двигается игрок (сглаженный сдвиг за тик)
			double[] last = LAST.get(id);
			double[] mv = MOVE.computeIfAbsent(id, k -> new double[2]);
			if (last != null) {
				mv[0] = mv[0] * 0.6 + (p.getX() - last[0]) * 0.4;
				mv[1] = mv[1] * 0.6 + (p.getZ() - last[1]) * 0.4;
			}
			LAST.put(id, new double[]{p.getX(), p.getZ()});
		}

		ARMED.keySet().retainAll(seen);
		LAST.keySet().retainAll(seen);
		MOVE.keySet().retainAll(seen);
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

		// перекрестие и кольцо на половине дальности
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

			// короткая линия: куда игрок сейчас двигается
			double[] mv = MOVE.get(p.getUuid());
			if (mv != null) {
				double speed = Math.hypot(mv[0], mv[1]);
				if (speed > 0.03) {
					double mr = (-mv[0] * cs - mv[1] * sn) / speed;
					double mf = (-mv[0] * sn + mv[1] * cs) / speed;
					for (int s = 3; s <= 9; s++) {
						int lx = sx + (int) Math.round(mr * s);
						int ly = sy - (int) Math.round(mf * s);
						c.fill(lx, ly, lx + 1, ly + 1, 0xFFFFD0D0);
					}
				}
			}

			boolean close = mc.player.distanceTo(p) <= WARN_NEAR;
			c.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF000000);
			c.fill(sx - 1, sy - 1, sx + 2, sy + 2, close ? 0xFFFF2020 : 0xFFFF7070);
			count++;
		}

		// ты в центре
		c.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF000000);
		c.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF55FF55);

		c.drawTextWithShadow(mc.textRenderer, "Игроков: " + count, x, y + SIZE + 4, 0xFFFFFFFF);

		// предупреждение сверху экрана
		long left = warnUntil - System.currentTimeMillis();
		if (left > 0) {
			float fade = Math.min(1f, left / 400f);
			int alpha = Math.max(8, (int) (255 * fade));
			Text t = Text.literal("К ВАМ БЕЖИТ ВРАГ!").styled(s -> s.withBold(true));
			float scale = 2.2f;
			int w = mc.textRenderer.getWidth(t);
			var ms = c.getMatrices();
			ms.push();
			ms.translate(mc.getWindow().getScaledWidth() / 2f, 26, 0);
			ms.scale(scale, scale, 1f);
			c.drawTextWithShadow(mc.textRenderer, t, -w / 2, 0, (alpha << 24) | 0xFF2020);
			ms.pop();
		}
	}
}
