package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Помощь в строительстве: пока ты держишь блок и зажата правая кнопка мыши,
 * мод сам ставит блок под ноги, если под тобой пусто. Идёшь назад и не падаешь.
 */
public final class BridgeAssist {
	private BridgeAssist() {}

	public static void tick(MinecraftClient mc) {
		if (!Config.bridge) return;
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.world == null || mc.interactionManager == null) return;
		if (mc.currentScreen != null) return;
		if (!mc.options.useKey.isPressed()) return;
		if (!player.isOnGround()) return;
		if (!(player.getMainHandStack().getItem() instanceof BlockItem)) return;

		BlockPos below = BlockPos.ofFloored(player.getX(), player.getY() - 0.5, player.getZ());
		if (!mc.world.getBlockState(below).isReplaceable()) return;

		BlockPos bestNeighbor = null;
		Direction bestDir = null;
		double bestDist = Double.MAX_VALUE;

		for (Direction d : Direction.Type.HORIZONTAL) {
			BlockPos n = below.offset(d);
			if (mc.world.getBlockState(n).isReplaceable()) continue;
			double dist = Vec3d.ofCenter(n).squaredDistanceTo(player.getPos());
			if (dist < bestDist) {
				bestDist = dist;
				bestNeighbor = n;
				bestDir = d;
			}
		}
		if (bestNeighbor == null) return;

		Direction face = bestDir.getOpposite();
		Vec3d hitPos = Vec3d.ofCenter(bestNeighbor).add(
				face.getOffsetX() * 0.5,
				face.getOffsetY() * 0.5,
				face.getOffsetZ() * 0.5);

		BlockHitResult hit = new BlockHitResult(hitPos, face, bestNeighbor, false);
		ActionResult result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
		if (result.isAccepted()) {
			player.swingHand(Hand.MAIN_HAND);
		}
	}
}
