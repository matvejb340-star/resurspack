package dev.myvisuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Анимации убийства (видны только тебе):
 *  1) На колено: враг опускает голову, садится и пропадает.
 *  2) Туалет с ракетой: туалет прискакивает, враг садится на него, ракета уносит вверх.
 *  3) Душа: прозрачная душа поднимается вверх.
 *
 * Убийством считается смерть существа, которое ты недавно ударил (в ближнем бою).
 */
public final class KillAnimations {
	private KillAnimations() {}

	private static final Random RNG = new Random();
	private static final double SOUL_TIME = 2.6;

	private static final class Anim {
		final Config.KillAnim kind;
		final int start;
		final double x, y, z;
		final float yaw;
		LivingEntity clone;

		Anim(Config.KillAnim kind, int start, double x, double y, double z, float yaw) {
			this.kind = kind;
			this.start = start;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
		}
	}

	private record TState(double x, double y, double z, double lift, boolean rocket, boolean flame, boolean done) {}

	private static final List<Anim> ANIMS = new ArrayList<>();
	private static int tick = 0;
	private static int nextId = -2_000_000;
	private static ClientWorld lastWorld = null;

	private static LivingEntity target = null;
	private static int hitTick = 0;
	private static float lastHp = 20f;

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient && entity instanceof LivingEntity le) {
				target = le;
				hitTick = tick;
				lastHp = le.getHealth();
			}
			return ActionResult.PASS;
		});
	}

	// ------------------------------------------------------------------ логика

	public static void tick(MinecraftClient mc) {
		tick++;
		ClientWorld world = mc.world;
		if (world != lastWorld) {
			ANIMS.clear();
			target = null;
			lastWorld = world;
		}
		if (world == null) return;

		if (target != null) {
			if (tick - hitTick > 100) {
				target = null;
			} else {
				boolean removed = target.isRemoved();
				boolean dead = target.isDead() || target.getHealth() <= 0;
				if (!dead && !removed) lastHp = target.getHealth();
				boolean vanishedAfterHit = removed && !dead && lastHp <= 12f && tick - hitTick <= 15;
				if (dead || vanishedAfterHit) {
					start(mc, target);
					target = null;
				}
			}
		}

		Iterator<Anim> it = ANIMS.iterator();
		while (it.hasNext()) {
			Anim a = it.next();
			int age = tick - a.start;
			boolean done = switch (a.kind) {
				case KNEEL -> tickKneel(world, a, age);
				case TOILET -> tickToilet(world, a, age);
				case SOUL -> tickSoul(world, a, age);
				default -> true;
			};
			if (done) {
				if (a.clone != null) a.clone.discard();
				it.remove();
			}
		}
	}

	private static void start(MinecraftClient mc, LivingEntity e) {
		Config.KillAnim kind = Config.killAnim;
		if (kind == Config.KillAnim.OFF || mc.world == null) return;

		Anim a = new Anim(kind, tick, e.getX(), e.getY(), e.getZ(), e.getBodyYaw());
		if (!e.isRemoved()) e.setInvisible(true);
		if (kind != Config.KillAnim.SOUL) a.clone = makeClone(mc.world, e, a);
		ANIMS.add(a);
	}

	private static LivingEntity makeClone(ClientWorld w, LivingEntity src, Anim a) {
		Entity c;
		if (src instanceof PlayerEntity pe) {
			c = new OtherClientPlayerEntity(w, pe.getGameProfile());
		} else {
			c = src.getType().create(w, SpawnReason.LOAD);
		}
		if (!(c instanceof LivingEntity le)) return null;

		le.refreshPositionAndAngles(a.x, a.y, a.z, a.yaw, 0f);
		le.setHeadYaw(a.yaw);
		le.setBodyYaw(a.yaw);
		le.setNoGravity(true);
		le.setVelocity(Vec3d.ZERO);
		le.setId(nextId--);
		w.addEntity(le);
		return le;
	}

	// 1) на колено
	private static boolean tickKneel(ClientWorld w, Anim a, int age) {
		LivingEntity c = a.clone;
		if (c != null) {
			c.setPose(EntityPose.CROUCHING);
			c.setPitch(Math.min(75f, age * 9f));
			c.setHeadYaw(a.yaw);
			c.setBodyYaw(a.yaw);
		}
		if (age >= 36) {
			poof(w, a.x, a.y + 0.9, a.z);
			return true;
		}
		return false;
	}

	// 2) туалет с ракетой
	private static TState toilet(Anim a, double t) {
		double dx = -Math.sin(Math.toRadians(a.yaw));
		double dz = Math.cos(Math.toRadians(a.yaw));

		if (t < 1.0) {
			double dist = 6.0 * (1.0 - t);
			double hop = Math.abs(Math.sin(t * Math.PI * 4)) * 0.35;
			return new TState(a.x - dx * dist, a.y + hop, a.z - dz * dist, 0, t >= 0.8, false, false);
		}
		if (t < 1.4) {
			double u = (t - 1.0) / 0.4;
			double smooth = u * u * (3 - 2 * u);
			return new TState(a.x, a.y + 0.9 * Math.sin(Math.PI * u), a.z, 0.5 * smooth, true, false, false);
		}
		double s = t - 1.4;
		double h = 3.5 * s * s;
		return new TState(a.x, a.y + h, a.z, 0.5, true, true, s > 2.0);
	}

	private static boolean tickToilet(ClientWorld w, Anim a, int age) {
		double t = age / 20.0;
		TState s = toilet(a, t);

		LivingEntity c = a.clone;
		if (c != null) {
			c.setPose(t < 1.0 ? EntityPose.STANDING : EntityPose.CROUCHING);
			c.setPosition(s.x(), s.y() + s.lift(), s.z());
			c.setHeadYaw(a.yaw);
			c.setBodyYaw(a.yaw);
		}

		if (s.flame()) {
			for (int i = 0; i < 3; i++) {
				w.addParticle(ParticleTypes.FLAME,
						s.x() + (RNG.nextDouble() - 0.5) * 0.12, s.y() - 0.5, s.z() + (RNG.nextDouble() - 0.5) * 0.12,
						0, -0.15, 0);
			}
			w.addParticle(ParticleTypes.LARGE_SMOKE, s.x(), s.y() - 0.6, s.z(), 0, -0.05, 0);
		}

		if (s.done()) {
			w.addParticle(ParticleTypes.EXPLOSION, s.x(), s.y() + 0.5, s.z(), 0, 0, 0);
			for (int i = 0; i < 30; i++) {
				w.addParticle(ParticleTypes.FIREWORK, s.x(), s.y() + 0.6, s.z(),
						(RNG.nextDouble() - 0.5) * 0.5, (RNG.nextDouble() - 0.5) * 0.5, (RNG.nextDouble() - 0.5) * 0.5);
			}
			return true;
		}
		return false;
	}

	// 3) душа
	private static double soulHeight(double t) {
		return 0.3 * t + 1.8 * t * t;
	}

	private static boolean tickSoul(ClientWorld w, Anim a, int age) {
		double t = age / 20.0;
		double y = a.y + soulHeight(t);
		for (int i = 0; i < 2; i++) {
			w.addParticle(ParticleTypes.SOUL,
					a.x + (RNG.nextDouble() - 0.5) * 0.6, y + 0.3 + RNG.nextDouble() * 1.4, a.z + (RNG.nextDouble() - 0.5) * 0.6,
					0, 0.04, 0);
		}
		if (age % 2 == 0) {
			w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, a.x, y, a.z, 0, 0.02, 0);
		}
		if (t >= SOUL_TIME) {
			poof(w, a.x, y + 1.0, a.z);
			return true;
		}
		return false;
	}

	private static void poof(ClientWorld w, double x, double y, double z) {
		for (int i = 0; i < 20; i++) {
			w.addParticle(ParticleTypes.POOF,
					x + (RNG.nextDouble() - 0.5) * 0.7, y + (RNG.nextDouble() - 0.5) * 0.9, z + (RNG.nextDouble() - 0.5) * 0.7,
					0, 0.02, 0);
		}
	}

	// --------------------------------------------------------------- отрисовка

	public static void render(WorldRenderContext ctx) {
		if (ANIMS.isEmpty()) return;

		boolean need = false;
		for (Anim a : ANIMS) {
			if (a.kind == Config.KillAnim.TOILET || a.kind == Config.KillAnim.SOUL) {
				need = true;
				break;
			}
		}
		if (!need) return;

		MatrixStack matrices = ctx.matrixStack();
		if (matrices == null) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		float td = mc.getRenderTickCounter().getTickDelta(true);
		Vec3d cam = ctx.camera().getPos();
		Matrix4f base = matrices.peek().getPositionMatrix();

		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);

		BufferBuilder b = Tessellator.getInstance()
				.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

		for (Anim a : ANIMS) {
			// сущность-клон интерполируется с отставанием в 1 тик, подстраиваемся под неё
			double t = Math.max(0.0, (tick - a.start - 1 + td) / 20.0);
			if (a.kind == Config.KillAnim.TOILET) drawToilet(b, base, cam, a, t);
			else if (a.kind == Config.KillAnim.SOUL) drawSoul(b, base, cam, a, t);
		}

		BufferRenderer.drawWithGlobalProgram(b.end());

		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void drawToilet(BufferBuilder b, Matrix4f base, Vec3d cam, Anim a, double t) {
		TState s = toilet(a, t);
		Matrix4f m = new Matrix4f(base)
				.translate((float) (s.x() - cam.x), (float) (s.y() - cam.y), (float) (s.z() - cam.z))
				.rotateY((float) -Math.toRadians(a.yaw));

		// унитаз смотрит в +z, бачок сзади
		box(b, m, -0.17f, 0.00f, -0.15f, 0.17f, 0.35f, 0.20f, 240, 240, 245, 255); // подставка
		box(b, m, -0.25f, 0.35f, -0.20f, 0.25f, 0.50f, 0.42f, 245, 245, 250, 255); // чаша
		box(b, m, -0.21f, 0.50f, -0.16f, 0.21f, 0.54f, 0.38f, 205, 210, 225, 255); // сиденье
		box(b, m, -0.22f, 0.50f, -0.32f, 0.22f, 0.98f, -0.14f, 240, 240, 245, 255); // бачок
		box(b, m, -0.24f, 0.98f, -0.34f, 0.24f, 1.03f, -0.12f, 220, 225, 235, 255); // крышка бачка
		box(b, m, -0.04f, 1.03f, -0.26f, 0.04f, 1.08f, -0.20f, 170, 175, 185, 255); // кнопка смыва

		if (s.rocket()) {
			box(b, m, -0.09f, -0.50f, -0.09f, 0.09f, -0.02f, 0.09f, 215, 55, 55, 255); // корпус
			box(b, m, -0.12f, -0.50f, -0.01f, 0.12f, -0.30f, 0.01f, 60, 60, 70, 255); // стабилизатор
			box(b, m, -0.01f, -0.50f, -0.12f, 0.01f, -0.30f, 0.12f, 60, 60, 70, 255);
			if (s.flame()) {
				float len = 0.35f + 0.15f * (float) Math.sin(t * 60);
				box(b, m, -0.07f, -0.50f - len, -0.07f, 0.07f, -0.50f, 0.07f, 255, 150, 30, 220);
				box(b, m, -0.035f, -0.50f - len * 1.5f, -0.035f, 0.035f, -0.50f, 0.035f, 255, 240, 120, 230);
			}
		}
	}

	private static void drawSoul(BufferBuilder b, Matrix4f base, Vec3d cam, Anim a, double t) {
		double y = a.y + soulHeight(t);
		double fade = 1.0 - Math.min(1.0, t / SOUL_TIME);
		int al = (int) (125 * (0.3 + 0.7 * fade));
		int eye = Math.min(255, al * 2);
		double sway = Math.sin(t * 5) * 0.15;

		Matrix4f m = new Matrix4f(base)
				.translate((float) (a.x - cam.x + sway), (float) (y - cam.y), (float) (a.z - cam.z))
				.rotateY((float) -Math.toRadians(a.yaw));

		int r = 175, g = 235, bl = 255;
		box(b, m, -0.24f, 0.00f, -0.12f, -0.01f, 0.68f, 0.12f, r, g, bl, al); // ноги
		box(b, m, 0.01f, 0.00f, -0.12f, 0.24f, 0.68f, 0.12f, r, g, bl, al);
		box(b, m, -0.25f, 0.68f, -0.13f, 0.25f, 1.38f, 0.13f, r, g, bl, al); // тело
		box(b, m, -0.50f, 0.68f, -0.12f, -0.25f, 1.38f, 0.12f, r, g, bl, al); // руки
		box(b, m, 0.25f, 0.68f, -0.12f, 0.50f, 1.38f, 0.12f, r, g, bl, al);
		box(b, m, -0.25f, 1.38f, -0.25f, 0.25f, 1.88f, 0.25f, r, g, bl, al); // голова
		box(b, m, -0.15f, 1.58f, 0.25f, -0.05f, 1.68f, 0.26f, 20, 30, 60, eye); // глаза
		box(b, m, 0.05f, 1.58f, 0.25f, 0.15f, 1.68f, 0.26f, 20, 30, 60, eye);
	}

	private static void box(BufferBuilder b, Matrix4f m,
							float x0, float y0, float z0, float x1, float y1, float z1,
							int r, int g, int bl, int a) {
		face(b, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, bl, a, 1.00f); // верх
		face(b, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, a, 0.50f); // низ
		face(b, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, bl, a, 0.80f);
		face(b, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, a, 0.80f);
		face(b, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, bl, a, 0.65f);
		face(b, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, bl, a, 0.65f);
	}

	private static void face(BufferBuilder b, Matrix4f m,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz,
							 int r, int g, int bl, int a, float shade) {
		int rr = (int) (r * shade);
		int gg = (int) (g * shade);
		int bb = (int) (bl * shade);
		b.vertex(m, ax, ay, az).color(rr, gg, bb, a);
		b.vertex(m, bx, by, bz).color(rr, gg, bb, a);
		b.vertex(m, cx, cy, cz).color(rr, gg, bb, a);
		b.vertex(m, dx, dy, dz).color(rr, gg, bb, a);
	}
}
