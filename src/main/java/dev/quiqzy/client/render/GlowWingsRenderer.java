package dev.quiqzy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.hud.AuroraHud;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.GlowWingsModule;

/**
 * Glow Wings: leuchtende Flügel als eigene, glatte Textur (textures/wing.png) auf zwei gespiegelten Quads
 * am Rücken – weiche Federn/Glow statt eckiger Geometrie. Emissiv (immer hell), getönt nach Akzent/Farbe.
 */
public final class GlowWingsRenderer {
	private static final Identifier WING_TEX = Identifier.fromNamespaceAndPath("quiqzy", "textures/wing.png");
	private static final int FULL_LIGHT = 15728880;

	private GlowWingsRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		GlowWingsModule m = ModuleManager.GLOW_WINGS;
		if (m == null || !m.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		PoseStack ps = ctx.matrices();
		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f mat = ps.last().pose();
		VertexConsumer buf = ctx.consumers().getBuffer(RenderTypes.entityTranslucentEmissive(WING_TEX));
		int rgb = (m.themeColor.on() ? AuroraHud.accent() : m.color.get()) & 0xFFFFFF;
		for (Player p : mc.level.players()) {
			boolean self = p == mc.player;
			if (!self && !m.others.on()) continue;
			if (self && mc.options.getCameraType().isFirstPerson() && !ModuleManager.FREECAM.active()) continue;
			if (p.isInvisible() || p.isSpectator()) continue;
			wings(buf, mat, p, pt, m, rgb);
		}
		ps.popPose();
	}

	private static void wings(VertexConsumer b, Matrix4f mat, Player p, float pt, GlowWingsModule m, int rgb) {
		double px = Mth.lerp(pt, p.xo, p.getX()), py = Mth.lerp(pt, p.yo, p.getY()), pz = Mth.lerp(pt, p.zo, p.getZ());
		float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
		double yr = Math.toRadians(bodyYaw);
		double fx = -Math.sin(yr), fz = Math.cos(yr);   // vorwärts
		double rx = Math.cos(yr), rz = Math.sin(yr);    // rechts (horizontal)

		float scale = m.size.get() / 100f;
		float glow = m.glow.get() / 100f;
		boolean crouch = p.isCrouching();
		double baseY = py + (crouch ? 0.70 : 0.95);     // Schulter-/Rückenhöhe (Textur-Fuß)
		double ax = px - fx * 0.18, az = pz - fz * 0.18; // hinter dem Rücken
		if (crouch) {
			ax -= fx * 0.12;
			az -= fz * 0.12;
		}

		// sanfter Flügelschlag
		float t = (float) (System.nanoTime() % 1_000_000_000_000L / 1.0e9);
		boolean fast = p.isFallFlying() || p.getAbilities().flying || p.isSprinting();
		float speed = m.flap.get() / 100f * (fast ? 2.2f : 1f);
		float flap = (float) Math.sin(t * 2.0f * speed + p.getId()) * (fast ? 0.14f : 0.07f);

		// Bild = beide Flügel. ZWEI Flächen, je eine Hälfte, nach hinten-außen abgewinkelt (3D-V),
		// damit es wie echte Flügel aussieht und nicht wie ein flaches Bild.
		float wingW = 2.00f * scale;                 // Länge eines Flügels (größer)
		float wingH = 2.75f * scale;                 // Höhe (Textur ist jetzt höher + Glow-Rand)
		double cy = py + 1.10 + Math.sin(System.nanoTime() % 1_000_000_000_000L / 1.0e9 * 1.6) * 0.03;
		double tilt = 0.22;                          // leicht nach hinten geneigt
		double upx = -fx * tilt, upy = 1.0, upz = -fz * tilt;
		double ul = Math.sqrt(upx * upx + upy * upy + upz * upz);
		upx /= ul;
		upy /= ul;
		upz /= ul;
		// Flügelschlag öffnet/schließt den Öffnungswinkel leicht
		double theta = Math.toRadians(24) + flap * 0.6;
		double ct = Math.cos(theta), st = Math.sin(theta);
		double bx = px - fx * 0.08, bz = pz - fz * 0.08; // Basis am Rücken (Wirbelsäule)

		int a = (int) (255 * Math.max(0.3f, Math.min(1f, 0.70f + 0.30f * glow)));
		int cr = 255, cg = 255, cb = 255; // Originalfarben der Textur (violett)
		if (!m.themeColor.on()) {
			cr = rgb >> 16 & 255;
			cg = rgb >> 8 & 255;
			cb = rgb & 255;
		}
		// rechter Flügel: nach rechts + hinten; UV innen 0.5 -> außen 1.0
		double drx = rx * ct - fx * st, drz = rz * ct - fz * st;
		wingQuad(b, mat, bx, cy, bz, drx, drz, upx, upy, upz, wingW, wingH, 0.5f, 1.0f, cr, cg, cb, a);
		// linker Flügel: nach links + hinten; UV innen 0.5 -> außen 0.0 (gespiegelt)
		double dlx = -rx * ct - fx * st, dlz = -rz * ct - fz * st;
		wingQuad(b, mat, bx, cy, bz, dlx, dlz, upx, upy, upz, wingW, wingH, 0.5f, 0.0f, cr, cg, cb, a);
	}

	/**
	 * Ein Flügel: Innenkante an der Wirbelsäule (bx,cy,bz), Außenrichtung (dx,dz) nach außen-hinten,
	 * up-Achse (upx..), Länge W, Höhe H. UV: Innenkante uInner, Außenkante uOuter, oben v=0 / unten v=1.
	 */
	private static void wingQuad(VertexConsumer b, Matrix4f m, double bx, double cy, double bz,
			double dx, double dz, double upx, double upy, double upz, float w, float h,
			float uInner, float uOuter, int cr, int cg, int cb, int a) {
		double hh = h / 2.0;
		double utx = upx * hh, uty = upy * hh, utz = upz * hh;
		double ox = bx + dx * w, oz = bz + dz * w; // Außenkante
		for (int s = 0; s < 2; s++) {
			if (s == 0) {
				vert(b, m, bx + utx, cy + uty, bz + utz, uInner, 0f, cr, cg, cb, a);
				vert(b, m, ox + utx, cy + uty, oz + utz, uOuter, 0f, cr, cg, cb, a);
				vert(b, m, ox - utx, cy - uty, oz - utz, uOuter, 1f, cr, cg, cb, a);
				vert(b, m, bx - utx, cy - uty, bz - utz, uInner, 1f, cr, cg, cb, a);
			} else {
				vert(b, m, bx - utx, cy - uty, bz - utz, uInner, 1f, cr, cg, cb, a);
				vert(b, m, ox - utx, cy - uty, oz - utz, uOuter, 1f, cr, cg, cb, a);
				vert(b, m, ox + utx, cy + uty, oz + utz, uOuter, 0f, cr, cg, cb, a);
				vert(b, m, bx + utx, cy + uty, bz + utz, uInner, 0f, cr, cg, cb, a);
			}
		}
	}

	private static void vert(VertexConsumer b, Matrix4f m, double x, double y, double z, float u, float v,
			int cr, int cg, int cb, int a) {
		b.addVertex(m, (float) x, (float) y, (float) z).setColor(cr, cg, cb, a).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_LIGHT).setNormal(0f, 1f, 0f);
	}
}
