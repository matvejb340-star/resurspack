package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Дождь из тотемов / мечей: на клиенте создаются «выброшенные предметы»,
 * которые медленно падают сверху и исчезают, как только коснулись земли.
 * Они есть только на твоём экране, сервер о них ничего не знает.
 */
public final class FallingItems {
	private FallingItems() {}

	private record Piece(ItemEntity entity, int born) {}

	private static final List<Piece> PIECES = new ArrayList<>();
	private static final Random RNG = new Random();
	private static final Item[] SWORDS = {
			Items.WOODEN_SWORD, Items.STONE_SWORD, Items.GOLDEN_SWORD,
			Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD
	};
	private static final int MAX_PIECES = 70;
	private static final double FALL_SPEED = -0.14;

	private static int tickCounter = 0;
	private static int nextId = -1_000_000;
	private static ClientWorld lastWorld = null;

	public static void tick(MinecraftClient mc) {
		tickCounter++;
		ClientWorld world = mc.world;
		ClientPlayerEntity player = mc.player;

		if (world != lastWorld) {
			PIECES.clear();
			lastWorld = world;
		}

		boolean active = world != null && player != null
				&& (Config.weather == Config.Weather.TOTEMS || Config.weather == Config.Weather.SWORDS);

		Iterator<Piece> it = PIECES.iterator();
		while (it.hasNext()) {
			Piece p = it.next();
			ItemEntity e = p.entity();
			boolean dead = !active || e.isRemoved() || e.isOnGround() || tickCounter - p.born() > 110;
			if (dead) {
				if (!e.isRemoved()) e.discard();
				it.remove();
			} else {
				e.setVelocity(e.getVelocity().x * 0.9, FALL_SPEED, e.getVelocity().z * 0.9);
			}
		}

		if (!active) return;
		if (RNG.nextDouble() > 0.85 || PIECES.size() >= MAX_PIECES) return;

		double x = player.getX() + (RNG.nextDouble() - 0.5) * 18;
		double z = player.getZ() + (RNG.nextDouble() - 0.5) * 18;
		double y = player.getY() + 7 + RNG.nextDouble() * 4;
		BlockPos bp = BlockPos.ofFloored(x, y, z);
		if (!world.getBlockState(bp).isAir() || !world.isSkyVisible(bp)) return;

		ItemStack stack = Config.weather == Config.Weather.TOTEMS
				? new ItemStack(Items.TOTEM_OF_UNDYING)
				: new ItemStack(SWORDS[RNG.nextInt(SWORDS.length)]);

		ItemEntity entity = new ItemEntity(world, x, y, z, stack);
		entity.setId(nextId--);
		entity.setNoGravity(true);
		entity.setPickupDelay(32767);
		entity.setVelocity((RNG.nextDouble() - 0.5) * 0.02, FALL_SPEED, (RNG.nextDouble() - 0.5) * 0.02);
		world.addEntity(entity);
		PIECES.add(new Piece(entity, tickCounter));
	}
}
