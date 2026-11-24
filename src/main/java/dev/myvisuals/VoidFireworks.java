package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Когда враг падает в бездну, от него взлетает фейерверк и взрывается — чтобы было видно, что он упал. */
public final class VoidFireworks {
	private VoidFireworks() {}

	private static final double MIN_FALL = 10.0;
	private static final int RISE_TICKS = 22;

	private static final class Track {
		double fallStartY;
		boolean falling;
		boolean fired;
	}

	private static final class Rocket {
		double x, y, z;
		int age;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	private static final List<Rocket> ROCKETS = new ArrayList<>();
	private static final Random RND = new Random();

	private static final int[] COLORS = {0xFF3B30, 0xFFD60A, 0x30D158, 0x0A84FF, 0xBF5AF2, 0xFFFFFF};

	public static void tick(MinecraftClient mc) {
		ClientWorld world = mc.world;
		if (world == null || mc.player == null) {
			TRACKS.clear();
			ROCKETS.clear();
			return;
		}

		if (Config.voidFirework) {
			for (PlayerEntity p : world.getPlayers()) {
				if (p == mc.player || p.isSpectator() || !p.isAlive() || mc.player.isTeammate(p)) continue;
				if (p.squaredDistanceTo(mc.player) > 90.0 * 90.0) continue;

				Track t = TRACKS.computeIfAbsent(p.getUuid(), k -> new Track());

				if (p.isOnGround() || p.isTouchingWater() || p.getVelocity().y > -0.1) {
					t.falling = false;
					t.fired = false;
					continue;
				}
				if (t.fired) continue;

				if (!t.falling) {
					t.falling = true;
					t.fallStartY = p.getY();
				}

				if (t.fallStartY - p.getY() >= MIN_FALL && voidBelow(world, p)) {
					t.fired = true;
					launch(world, p.getX(), p.getY(), p.getZ());
				}
			}
		}

		Iterator<Rocket> it = ROCKETS.iterator();
		while (it.hasNext()) {
			Rocket r = it.next();
			r.age++;
			if (r.age <= RISE_TICKS) {
				r.y += 0.95;
				for (int i = 0; i < 3; i++) {
					world.addParticle(ParticleTypes.FIREWORK, r.x + RND.nextGaussian() * 0.05, r.y - i * 0.3,
							r.z + RND.nextGaussian() * 0.05, 0, -0.05, 0);
				}
				world.addParticle(ParticleTypes.FLAME, r.x, r.y - 0.2, r.z, 0, -0.04, 0);
			} else {
				explode(world, r.x, r.y, r.z);
				it.remove();
			}
		}
	}

	/** Под игроком до самого низа мира нет ни одного блока. */
	private static boolean voidBelow(ClientWorld world, PlayerEntity p) {
		int x = (int) Math.floor(p.getX());
		int z = (int) Math.floor(p.getZ());
		if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;

		int top = (int) Math.floor(p.getY());
		int bottom = world.getBottomY();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = top; y >= bottom; y--) {
			pos.set(x, y, z);
			if (!world.getBlockState(pos).isAir()) return false;
		}
		return true;
	}

	private static void launch(ClientWorld world, double x, double y, double z) {
		Rocket r = new Rocket();
		r.x = x;
		r.y = y;
		r.z = z;
		ROCKETS.add(r);
		world.playSound(x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 3.0f, 1.0f, false);
	}

	private static void explode(ClientWorld world, double x, double y, double z) {
		world.playSound(x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 4.0f, 1.0f, false);
		world.playSound(x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 3.0f, 1.0f, false);

		// сферическая вспышка из цветных частиц
		for (int i = 0; i < 160; i++) {
			double u = RND.nextDouble() * 2 - 1;
			double a = RND.nextDouble() * Math.PI * 2;
			double s = Math.sqrt(1 - u * u);
			double dx = s * Math.cos(a), dy = u, dz = s * Math.sin(a);
			double sp = 0.45 + RND.nextDouble() * 0.15;
			int col = COLORS[i % COLORS.length];
			world.addParticle(new DustParticleEffect(col, 2.0f), x, y, z, dx * sp, dy * sp, dz * sp);
			if (i % 2 == 0) world.addParticle(ParticleTypes.FIREWORK, x, y, z, dx * sp * 0.8, dy * sp * 0.8, dz * sp * 0.8);
			if (i % 5 == 0) world.addParticle(ParticleTypes.END_ROD, x, y, z, dx * sp * 0.5, dy * sp * 0.5, dz * sp * 0.5);
		}
	}
}
