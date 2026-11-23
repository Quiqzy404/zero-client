package dev.quiqzy.client.render;

import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.GuiWorldModule;

/** Zeichnet ein echtes 3D-Panel (Quad + Text) an einer festen Weltposition - man kann drumherum laufen. */
public final class GuiWorldRenderer {
	private static final int LIGHT = 15728880; // volle Helligkeit
	private static final float UNIT = 0.03f;    // Textpixel -> Weltgröße

	private GuiWorldRenderer() {
	}

	public static void init() {
	}

	public static void render(WorldRenderContext ctx) {
		GuiWorldModule m = ModuleManager.GUI_WORLD;
		if (!m.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		Font font = mc.font;

		Module.Category cat;
		try {
			cat = Module.Category.valueOf(m.cat.get().toUpperCase(Locale.ROOT));
		} catch (Exception e) {
			cat = Module.Category.COMBAT;
		}
		List<Module> mods = ModuleManager.byCategory(cat);

		float W = 150f;
		float H = 34f + mods.size() * 11f + 8f;

		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		PoseStack ms = ctx.matrices();
		ms.pushPose();
		ms.translate(m.ax - cam.x, m.ay - cam.y, m.az - cam.z);
		ms.mulPose(Axis.YP.rotationDegrees(-m.yaw));
		float s = UNIT * (m.scale.get() / 100f);
		ms.scale(s, -s, s);             // Text-Y zeigt nach unten -> umdrehen
		ms.translate(-W / 2f, -H / 2f, 0f); // auf den Anker zentrieren
		Matrix4f mat = ms.last().pose();

		// Experimentell: echte GUI aufnehmen, SOLANGE die ClickGUI offen ist (Quad hält die echten GUI-Pixel).
		if (m.mirror.on() && GuiCapture.has) {
			float tw = W;
			float th = W * (GuiCapture.capH / (float) Math.max(1, GuiCapture.capW));
			float cx0 = W / 2f - tw / 2f, cy0 = H / 2f - th / 2f;
			VertexConsumer tex = ctx.consumers().getBuffer(RenderTypes.entityCutoutNoCull(GuiCapture.ID));
			texQuad(tex, mat, cx0, cy0, cx0 + tw, cy0 + th); // noCull = beidseitig sichtbar
			ms.popPose();
			return;
		}

		// Fallback (noch keine Aufnahme): einfacher 3D-Text
		// 1) ALLE Quads zuerst (ein Puffer) - Text würde den Puffer sonst beenden ("Not building")
		VertexConsumer vb = ctx.consumers().getBuffer(RenderTypes.debugFilledBox());
		quad(vb, mat, 0, 0, W, H, 0.6f, 0xE60A0A14);        // Vorderseite
		quad(vb, mat, W, 0, 0, H, 0.6f, 0xE60A0A14);        // Rückseite
		quad(vb, mat, 0, 0, W, 2.5f, 0.55f, 0xFFF0782C);    // Akzentleiste oben
		float y = 36f;
		for (Module mod : mods) {
			quad(vb, mat, 8f, y + 1.5f, 13f, y + 6.5f, 0.5f, mod.isEnabled() ? 0xFF6FE89A : 0x66FFFFFF);
			y += 11f;
		}

		// 2) ALLER Text danach
		MultiBufferSource consumers = ctx.consumers();
		font.drawInBatch("ZERO", 8f, 8f, 0xFFFFFFFF, false, mat, consumers, Font.DisplayMode.NORMAL, 0, LIGHT);
		font.drawInBatch(cat.name(), 8f, 20f, 0xFFF0782C, false, mat, consumers, Font.DisplayMode.NORMAL, 0, LIGHT);
		y = 36f;
		for (Module mod : mods) {
			font.drawInBatch(mod.name(), 18f, y, mod.isEnabled() ? 0xFF6FE89A : 0xFFB6B6C2, false, mat, consumers, Font.DisplayMode.NORMAL, 0, LIGHT);
			y += 11f;
		}

		ms.popPose();
	}

	/** Texturiertes Quad (entity-Format: Farbe, UV, Overlay, Light, Normal). */
	private static void texQuad(VertexConsumer b, Matrix4f m, float x1, float y1, float x2, float y2) {
		int L = 15728880;
		int O = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
		// nur V gespiegelt (Framebuffer-Y-Ursprung unten links), U normal
		b.addVertex(m, x1, y1, 0f).setColor(255, 255, 255, 255).setUv(0f, 1f).setOverlay(O).setLight(L).setNormal(0f, 0f, 1f);
		b.addVertex(m, x1, y2, 0f).setColor(255, 255, 255, 255).setUv(0f, 0f).setOverlay(O).setLight(L).setNormal(0f, 0f, 1f);
		b.addVertex(m, x2, y2, 0f).setColor(255, 255, 255, 255).setUv(1f, 0f).setOverlay(O).setLight(L).setNormal(0f, 0f, 1f);
		b.addVertex(m, x2, y1, 0f).setColor(255, 255, 255, 255).setUv(1f, 1f).setOverlay(O).setLight(L).setNormal(0f, 0f, 1f);
	}

	/** Flaches Rechteck in der lokalen XY-Ebene (z als Tiefe hinter dem Text). */
	private static void quad(VertexConsumer b, Matrix4f m, float x1, float y1, float x2, float y2, float z, int c) {
		b.addVertex(m, x1, y1, z).setColor(c);
		b.addVertex(m, x1, y2, z).setColor(c);
		b.addVertex(m, x2, y2, z).setColor(c);
		b.addVertex(m, x2, y1, z).setColor(c);
	}
}
