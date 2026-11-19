package dev.myvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Помощь в строительстве: держишь блок + зажата правая кнопка мыши + идёшь назад,
 * блок ставится под ноги сам.
 *
 * Чтобы сервер не откатывал блок, на момент установки отправляем ему
 * направление взгляда на нужную грань (на твоём экране камера не двигается).
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

		BlockPos target = null;
		Direction face = null;
		double best = Double.MAX_VALUE;

		for (Direction d : Direction.Type.HORIZONTAL) {
			BlockPos n = below.offset(d);
			if (mc.world.getBlockState(n).isReplaceable()) continue;
			double dist = Vec3d.ofCenter(n).squaredDistanceTo(player.getPos());
			if (dist < best) {
				best = dist;
				target = n;
				face = d.getOpposite();
			}
		}
		if (target == null) {
			BlockPos n = below.down();
			if (!mc.world.getBlockState(n).isReplaceable()) {
				target = n;
				face = Direction.UP;
			}
		}
		if (target == null) return;

		Vec3d aim = Vec3d.ofCenter(target).add(
				face.getOffsetX() * 0.5,
				face.getOffsetY() * 0.5,
				face.getOffsetZ() * 0.5);

		Vec3d diff = aim.subtract(player.getEyePos());
		double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
		float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
		float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(diff.y, horiz)), -90.0, 90.0);

		boolean onGround = player.isOnGround();
		boolean collided = player.horizontalCollision;

		player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, onGround, collided));

		BlockHitResult hit = new BlockHitResult(aim, face, target, false);
		ActionResult result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
		if (result.isAccepted()) {
			player.swingHand(Hand.MAIN_HAND);
		}

		player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
				player.getYaw(), player.getPitch(), onGround, collided));
	}
}
