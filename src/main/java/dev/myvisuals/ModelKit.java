package dev.myvisuals;

import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Небольшой набор для рисования блестящих «металлических» моделей: освещение считается по нормалям. */
public final class ModelKit {
	private ModelKit() {}

	private static final Vector3f LIGHT = new Vector3f(-0.45f, 0.65f, 0.62f).normalize();
	private static final Vector3f HALF = new Vector3f(LIGHT).add(0f, 0f, 1f).normalize();

	/** Вершина с освещением. gloss — сила блика (0 — матовый, 1 — зеркальный металл). */
	public static void vtx(BufferBuilder b, Matrix4f m, float x, float y, float z,
						   float nx, float ny, float nz, int[] c, float gloss) {
		Vector3f n = m.transformDirection(new Vector3f(nx, ny, nz));
		if (n.lengthSquared() < 1.0E-8f) n.set(0f, 0f, 1f);
		n.normalize();

		float diff = Math.max(0f, n.dot(LIGHT));
		float hemi = n.y * 0.5f + 0.5f;
		float spec = (float) Math.pow(Math.max(0f, n.dot(HALF)), 26) * gloss;
		float rim = (float) Math.pow(1f - Math.min(1f, Math.abs(n.z)), 2) * 0.22f * gloss;
		float k = 0.30f + 0.52f * diff + 0.22f * hemi;

		int r = clamp(c[0] * k + 255f * spec * 0.85f + 70f * rim);
		int g = clamp(c[1] * k + 255f * spec * 0.85f + 75f * rim);
		int bl = clamp(c[2] * k + 255f * spec * 0.85f + 90f * rim);
		b.vertex(m, x, y, z).color(r, g, bl, 255);
	}

	private static int clamp(float v) {
		return v < 0 ? 0 : Math.min(255, (int) v);
	}

	/** Четырёхугольник с плоской нормалью, направленной от точки ctr. */
	public static void quadFlat(BufferBuilder b, Matrix4f m, float[] a, float[] bb, float[] c, float[] d,
								float[] ctr, int[] col, float gloss) {
		float e1x = bb[0] - a[0], e1y = bb[1] - a[1], e1z = bb[2] - a[2];
		float e2x = d[0] - a[0], e2y = d[1] - a[1], e2z = d[2] - a[2];
		float nx = e1y * e2z - e1z * e2y;
		float ny = e1z * e2x - e1x * e2z;
		float nz = e1x * e2y - e1y * e2x;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1.0E-9f) {
			// вырожденная грань (остриё) — рисуем с любой нормалью
			nx = 0; ny = 0; nz = 1; len = 1;
		}
		nx /= len; ny /= len; nz /= len;
		float mx = (a[0] + bb[0] + c[0] + d[0]) / 4f - ctr[0];
		float my = (a[1] + bb[1] + c[1] + d[1]) / 4f - ctr[1];
		float mz = (a[2] + bb[2] + c[2] + d[2]) / 4f - ctr[2];
		if (nx * mx + ny * my + nz * mz < 0) { nx = -nx; ny = -ny; nz = -nz; }
		vtx(b, m, a[0], a[1], a[2], nx, ny, nz, col, gloss);
		vtx(b, m, bb[0], bb[1], bb[2], nx, ny, nz, col, gloss);
		vtx(b, m, c[0], c[1], c[2], nx, ny, nz, col, gloss);
		vtx(b, m, d[0], d[1], d[2], nx, ny, nz, col, gloss);
	}

	private static void face(BufferBuilder b, Matrix4f m, float nx, float ny, float nz, int[] c, float gloss,
							 float... p) {
		for (int i = 0; i < 12; i += 3) vtx(b, m, p[i], p[i + 1], p[i + 2], nx, ny, nz, c, gloss);
	}

	public static void box(BufferBuilder b, Matrix4f m, float x0, float y0, float z0,
						   float x1, float y1, float z1, int[] c, float gloss) {
		face(b, m, 0, 1, 0, c, gloss, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
		face(b, m, 0, -1, 0, c, gloss, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
		face(b, m, 0, 0, 1, c, gloss, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		face(b, m, 0, 0, -1, c, gloss, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		face(b, m, 1, 0, 0, c, gloss, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		face(b, m, -1, 0, 0, c, gloss, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
	}

	/**
	 * Тело вращения вокруг оси Y. prof — точки {y, радиус} снизу вверх (внешняя сторона).
	 * Нормали считаются по каждому участку профиля, вокруг оси — плавно.
	 */
	public static void lathe(BufferBuilder b, Matrix4f m, float[][] prof, int seg, float cx, float cz,
							 int[] c, float gloss) {
		for (int i = 0; i < prof.length - 1; i++) {
			float y0 = prof[i][0], r0 = prof[i][1];
			float y1 = prof[i + 1][0], r1 = prof[i + 1][1];
			float nr = y1 - y0;
			float ny = -(r1 - r0);
			float len = (float) Math.sqrt(nr * nr + ny * ny);
			if (len < 1.0E-9f) continue;
			nr /= len; ny /= len;
			for (int j = 0; j < seg; j++) {
				double a0 = Math.PI * 2 * j / seg;
				double a1 = Math.PI * 2 * (j + 1) / seg;
				float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
				float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
				vtx(b, m, cx + r0 * c0, y0, cz + r0 * s0, nr * c0, ny, nr * s0, c, gloss);
				vtx(b, m, cx + r0 * c1, y0, cz + r0 * s1, nr * c1, ny, nr * s1, c, gloss);
				vtx(b, m, cx + r1 * c1, y1, cz + r1 * s1, nr * c1, ny, nr * s1, c, gloss);
				vtx(b, m, cx + r1 * c0, y1, cz + r1 * s0, nr * c0, ny, nr * s0, c, gloss);
			}
		}
	}

	/** Кольцо (тор), лежащее в плоскости XY, ось — Z. */
	public static void torusZ(BufferBuilder b, Matrix4f m, float cx, float cy, float cz,
							  float big, float small, int na, int nb, int[] c, float gloss) {
		for (int i = 0; i < na; i++) {
			double a0 = Math.PI * 2 * i / na, a1 = Math.PI * 2 * (i + 1) / na;
			for (int j = 0; j < nb; j++) {
				double t0 = Math.PI * 2 * j / nb, t1 = Math.PI * 2 * (j + 1) / nb;
				torusV(b, m, cx, cy, cz, big, small, a0, t0, c, gloss);
				torusV(b, m, cx, cy, cz, big, small, a1, t0, c, gloss);
				torusV(b, m, cx, cy, cz, big, small, a1, t1, c, gloss);
				torusV(b, m, cx, cy, cz, big, small, a0, t1, c, gloss);
			}
		}
	}

	private static void torusV(BufferBuilder b, Matrix4f m, float cx, float cy, float cz,
							   float big, float small, double a, double t, int[] c, float gloss) {
		float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
		float ct = (float) Math.cos(t), st = (float) Math.sin(t);
		float rr = big + small * ct;
		vtx(b, m, cx + rr * ca, cy + rr * sa, cz + small * st, ct * ca, ct * sa, st, c, gloss);
	}
}
