package dev.quiqzy.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.hud.AuroraHud;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.CardAuraModule;

/**
 * Pokerkarten-Effekte, komplett aus eigener Geometrie (keine Texturen/Assets):
 *  - Card Aura: Karten kreisen um den Spieler (billboard, zur Kamera ausgerichtet, leicht rollend).
 *  - Kill Burst: beim Kill fliegen Karten vom Ziel weg nach oben und blenden aus.
 * Jede Karte = weißer Körper + dunkler Rahmen + farbiges Symbol (Pik/Herz/Karo/Kreuz).
 */
public final class CardFxRenderer {
	private CardFxRenderer() {
	}

	private static final int RED = 0xE0353B;
	private static final int BLACK = 0x17181C;

	private static final class Card {
		double x, y, z, vx, vy, vz;
		float age, life, spin, spinVel, size;
		int suit;
	}

	private static final List<Card> bursts = new ArrayList<>();
	private static long lastNanos;

	// Ebenen-Achsen des aktuellen Frames (nach Roll): ru = Breite, rv = Höhe, f = Kamera vorwärts
	private static float rux, ruy, ruz, rvx, rvy, rvz, bax, bay, baz, bux, buy, buz, fwx, fwy, fwz;

	/** Beim Kill: Karten-Burst an pos auslösen. */
	public static void spawnBurst(Vec3 pos, int count, float power, float sizePct) {
		var rnd = ThreadLocalRandom.current();
		for (int i = 0; i < count; i++) {
			Card c = new Card();
			c.x = pos.x;
			c.y = pos.y + 0.9;
			c.z = pos.z;
			double ang = rnd.nextDouble() * Math.PI * 2, up = 0.5 + rnd.nextDouble() * 0.9;
			double sp = (1.6 + rnd.nextDouble() * 1.8) * power;
			c.vx = Math.cos(ang) * sp * 0.7;
			c.vz = Math.sin(ang) * sp * 0.7;
			c.vy = up * sp;
			c.life = 0.9f + rnd.nextFloat() * 0.6f;
			c.size = (0.10f + rnd.nextFloat() * 0.05f) * sizePct;
			c.spin = rnd.nextFloat() * 6.28f;
			c.spinVel = (rnd.nextFloat() - 0.5f) * 14f;
			c.suit = rnd.nextInt(4);
			bursts.add(c);
		}
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		CardAuraModule aura = ModuleManager.CARD_AURA;
		boolean doAura = aura != null && aura.isEnabled();
		if (!doAura && bursts.isEmpty()) return;

		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : (float) ((now - lastNanos) / 1.0e9);
		if (dt > 0.1f) dt = 0.1f;
		lastNanos = now;

		Camera cam = mc.gameRenderer.getMainCamera();
		Vector3fc up = cam.upVector(), left = cam.leftVector(), fwd = cam.forwardVector();
		bax = -left.x();
		bay = -left.y();
		baz = -left.z(); // right = -left
		bux = up.x();
		buy = up.y();
		buz = up.z();
		fwx = fwd.x();
		fwy = fwd.y();
		fwz = fwd.z();

		Vec3 camPos = ctx.worldState().cameraRenderState.pos;
		PoseStack ps = ctx.matrices();
		ps.pushPose();
		ps.translate(-camPos.x, -camPos.y, -camPos.z);
		Matrix4f mat = ps.last().pose();
		VertexConsumer b = ctx.consumers().getBuffer(RenderTypes.debugFilledBox());

		float t = (float) (now % 1_000_000_000_000L / 1.0e9);
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

		if (doAura) {
			int count = Math.max(1, aura.count.get());
			float radius = aura.radius.get() / 100f;
			float spd = aura.speed.get() / 100f;
			float sz = aura.size.get() / 100f * 0.12f;
			for (Player pl : mc.level.players()) {
				boolean self = pl == mc.player;
				if (!self && !aura.others.on()) continue;
				if (pl.isInvisible() || pl.isSpectator()) continue;
				double px = Mth.lerp(pt, pl.xo, pl.getX());
				double py = Mth.lerp(pt, pl.yo, pl.getY());
				double pz = Mth.lerp(pt, pl.zo, pl.getZ());
				for (int i = 0; i < count; i++) {
					float ang = t * spd * 1.3f + (float) (i * 2 * Math.PI / count);
					double cx = px + Math.cos(ang) * radius;
					double cz = pz + Math.sin(ang) * radius;
					double cy = py + 1.0 + Math.sin(t * 2f + i) * 0.14;
					setRoll(ang + t * 1.5f);
					drawCard(b, mat, cx, cy, cz, sz, i % 4, 1f);
				}
			}
		}

		for (int i = bursts.size() - 1; i >= 0; i--) {
			Card c = bursts.get(i);
			c.age += dt;
			if (c.age >= c.life) {
				bursts.remove(i);
				continue;
			}
			c.vy -= 3.4f * dt;
			float drag = Math.max(0f, 1f - 1.4f * dt);
			c.vx *= drag;
			c.vz *= drag;
			c.x += c.vx * dt;
			c.y += c.vy * dt;
			c.z += c.vz * dt;
			c.spin += c.spinVel * dt;
			float k = c.age / c.life;
			float alpha = k < 0.65f ? 1f : Math.max(0f, 1f - (k - 0.65f) / 0.35f);
			setRoll(c.spin);
			drawCard(b, mat, c.x, c.y, c.z, c.size * (1f - 0.25f * k), c.suit, alpha);
		}
		ps.popPose();
	}

	/** Ebenen-Achsen aus Basis-rechts/-oben um Roll-Winkel a drehen. */
	private static void setRoll(float a) {
		float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
		rux = bax * cos + bux * sin;
		ruy = bay * cos + buy * sin;
		ruz = baz * cos + buz * sin;
		rvx = -bax * sin + bux * cos;
		rvy = -bay * sin + buy * cos;
		rvz = -baz * sin + buz * cos;
	}

	private static void drawCard(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float hw, int suit, float alpha) {
		float hh = hw * 1.42f;
		// Rahmen (hinten), weißer Körper (mitte), Symbol (vorne) – per Tiefenversatz entlang Kamera-vorwärts
		quad(b, m, cx, cy, cz, hw + 0.016f, hh + 0.016f, 0.006f, argb(alpha, BLACK));
		quad(b, m, cx, cy, cz, hw, hh, 0f, argb(alpha, 0xFFFFFF));
		float r = hw * 0.60f;
		boolean red = suit == 0 || suit == 1;
		int col = argb(alpha, red ? RED : BLACK);
		switch (suit) {
			case 0 -> diamond(b, m, cx, cy, cz, r, col);
			case 1 -> heart(b, m, cx, cy, cz, r, col);
			case 2 -> spade(b, m, cx, cy, cz, r, col);
			default -> club(b, m, cx, cy, cz, r, col);
		}
	}

	// ---- Symbole (Ebenen-Koordinaten u rechts, v oben; Mittelpunkt 0,0) ----

	private static void diamond(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float r, int col) {
		emit(b, m, cx, cy, cz, 0.012f, col, 0f, r, r * 0.72f, 0f, 0f, -r, -r * 0.72f, 0f);
	}

	private static void heart(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float r, int col) {
		circle(b, m, cx, cy, cz, -0.42f * r, 0.30f * r, 0.52f * r, col);
		circle(b, m, cx, cy, cz, 0.42f * r, 0.30f * r, 0.52f * r, col);
		tri(b, m, cx, cy, cz, -0.92f * r, 0.26f * r, 0.92f * r, 0.26f * r, 0f, -1.00f * r, col);
	}

	private static void spade(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float r, int col) {
		tri(b, m, cx, cy, cz, 0f, 1.00f * r, -0.86f * r, -0.18f * r, 0.86f * r, -0.18f * r, col);
		circle(b, m, cx, cy, cz, -0.40f * r, -0.18f * r, 0.46f * r, col);
		circle(b, m, cx, cy, cz, 0.40f * r, -0.18f * r, 0.46f * r, col);
		stem(b, m, cx, cy, cz, r, col);
	}

	private static void club(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float r, int col) {
		circle(b, m, cx, cy, cz, 0f, 0.44f * r, 0.44f * r, col);
		circle(b, m, cx, cy, cz, -0.46f * r, -0.06f * r, 0.44f * r, col);
		circle(b, m, cx, cy, cz, 0.46f * r, -0.06f * r, 0.44f * r, col);
		stem(b, m, cx, cy, cz, r, col);
	}

	/** Stiel unten (für Pik/Kreuz): kleines Dreieck nach unten. */
	private static void stem(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float r, int col) {
		emit(b, m, cx, cy, cz, 0.012f, col, -0.22f * r, -0.30f * r, 0.22f * r, -0.30f * r, 0.12f * r, -0.95f * r, -0.12f * r, -0.95f * r);
	}

	private static void circle(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float ou, float ov, float r, int col) {
		int segs = 10;
		for (int i = 0; i < segs; i++) {
			double a0 = i * 2 * Math.PI / segs, a1 = (i + 1) * 2 * Math.PI / segs;
			tri(b, m, cx, cy, cz, ou, ov,
					ou + (float) Math.cos(a0) * r, ov + (float) Math.sin(a0) * r,
					ou + (float) Math.cos(a1) * r, ov + (float) Math.sin(a1) * r, col);
		}
	}

	private static void tri(VertexConsumer b, Matrix4f m, double cx, double cy, double cz,
			float u0, float v0, float u1, float v1, float u2, float v2, int col) {
		emit(b, m, cx, cy, cz, 0.012f, col, u0, v0, u1, v1, u2, v2, u2, v2);
	}

	/** Quad (4 Ebenen-Punkte) beidseitig, mit Tiefenversatz entlang Kamera-vorwärts. */
	private static void quad(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float hw, float hh, float depth, int col) {
		emit(b, m, cx, cy, cz, depth, col, -hw, -hh, hw, -hh, hw, hh, -hw, hh);
	}

	/** 4 Ebenen-Punkte -> Welt (beide Wicklungen, damit ohne Culling sichtbar). */
	private static void emit(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, float depth, int col,
			float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
		double ox = cx - fwx * depth, oy = cy - fwy * depth, oz = cz - fwz * depth;
		p(b, m, ox, oy, oz, u0, v0, col);
		p(b, m, ox, oy, oz, u1, v1, col);
		p(b, m, ox, oy, oz, u2, v2, col);
		p(b, m, ox, oy, oz, u3, v3, col);
		// Rückseite
		p(b, m, ox, oy, oz, u3, v3, col);
		p(b, m, ox, oy, oz, u2, v2, col);
		p(b, m, ox, oy, oz, u1, v1, col);
		p(b, m, ox, oy, oz, u0, v0, col);
	}

	private static void p(VertexConsumer b, Matrix4f m, double ox, double oy, double oz, float u, float v, int col) {
		b.addVertex(m, (float) (ox + rux * u + rvx * v), (float) (oy + ruy * u + rvy * v), (float) (oz + ruz * u + rvz * v)).setColor(col);
	}

	private static int argb(float a, int rgb) {
		return (Math.round(Math.max(0f, Math.min(1f, a)) * 255f) << 24) | (rgb & 0xFFFFFF);
	}
}
