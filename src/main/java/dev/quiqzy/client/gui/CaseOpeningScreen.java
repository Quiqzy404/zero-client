package dev.quiqzy.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.gui.engine.ZeroFont;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.util.UiSounds;

/**
 * ZERO Case (CSGO-Style Kisten-Rolle) mit ZEROs EIGENEN Knife-Modellen (keine fremden Assets):
 * Karten rollen durch, bremsen weich ab, der Gewinner landet unter dem gelben Marker und wird enthuellt.
 */
public final class CaseOpeningScreen extends Screen {
	private record Skin(String name, String rarity, int color, int weight) {
	}

	private static final Skin[] POOL = {
			new Skin("Bowie", "Mil-Spec", 0x4B69FF, 30),
			new Skin("Gut", "Mil-Spec", 0x4B69FF, 30),
			new Skin("Flip", "Restricted", 0x8847FF, 22),
			new Skin("Bayonet", "Restricted", 0x8847FF, 22),
			new Skin("Scythe", "Classified", 0xD32CE6, 13),
			new Skin("Greatsword", "Classified", 0xD32CE6, 13),
			new Skin("Katana", "Covert", 0xEB4B4B, 8),
			new Skin("Butterfly", "★ Gold", 0xFFD700, 4),
			new Skin("Karambit", "★ Gold", 0xFFD700, 4),
	};
	private static final int REEL = 48, WIN = 40;
	private static final float CW = 108f, GAP = 8f, CH = 118f;

	/** Nur fuer Selbsttests: erzwingt den Gewinner (null = echter Zufall). */
	public static String debugWin;
	private final List<Skin> reel = new ArrayList<>();
	private final Random rnd = new Random();
	private long start;
	private float durMs;
	private float landJitter;
	private int lastIdx = -1;
	private boolean done, equipped;
	private float[] bClose = {}, bAgain = {};

	public CaseOpeningScreen() {
		super(Component.literal("ZERO Case"));
		spin();
	}

	private Skin weighted() {
		int total = 0;
		for (Skin s : POOL) total += s.weight;
		int r = rnd.nextInt(total);
		for (Skin s : POOL) {
			r -= s.weight;
			if (r < 0) return s;
		}
		return POOL[0];
	}

	private void spin() {
		reel.clear();
		for (int i = 0; i < REEL; i++) reel.add(weighted());
		if (debugWin != null) for (Skin k : POOL) if (k.name().equals(debugWin)) reel.set(WIN, k);
		start = System.currentTimeMillis();
		durMs = ModuleManager.CASE_OPENING.duration.get() * 1000f;
		landJitter = (rnd.nextFloat() - 0.5f) * CW * 0.55f; // landet nicht exakt mittig = echter
		lastIdx = -1;
		done = false;
		equipped = false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, this.width, this.height, 0xCC05050A);
	}

	private boolean hit(float[] r, double x, double y) {
		return r.length == 4 && x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		float W = this.width, H = this.height;
		float cx = W / 2f, cy = H / 2f;

		// Fortschritt (weiches Ausrollen)
		float t = System.currentTimeMillis() - start;
		float p = Math.max(0f, Math.min(1f, t / durMs));
		float e = 1f - (float) Math.pow(1f - p, 3.2);
		float step = CW + GAP;
		float target = WIN * step + CW / 2f + landJitter;   // Mitte der Gewinner-Karte
		float offset = e * target;                            // Welt-x, das gerade unter dem Marker liegt
		if (p >= 1f && !done) {
			done = true;
			UiSounds.found();
			if (ModuleManager.CASE_OPENING.equip.on()) {
				Skin w = reel.get(WIN);
				ModuleManager.WEAPON_SKINS.model.set(w.name());
				if (!ModuleManager.WEAPON_SKINS.isEnabled()) ModuleManager.WEAPON_SKINS.setEnabled(true);
				QuiqzyConfig.save();
				equipped = true;
			}
		}

		// Kopf
		ZeroRender.textCentered(g, ZeroFont.BOLD, "ZERO CASE", cx, 26f, 15f, 0xFFFFFFFF);
		ZeroRender.textCentered(g, ZeroFont.REGULAR, done ? "You unlocked" : "Unlocking ZERO Case", cx, 46f, 8.5f, 0xFFB8B8C4);

		// Rolle
		float ry = cy - CH / 2f;
		ZeroRender.rect(g, 0, ry - 10f, W, ry + CH + 10f, 0f, 0x55000000);
		int idxUnder = -1;
		for (int i = 0; i < reel.size(); i++) {
			float cardCenter = i * step + CW / 2f;
			float x = cx + (cardCenter - offset) - CW / 2f;
			if (x + CW < -20 || x > W + 20) continue;
			Skin s = reel.get(i);
			float dist = Math.abs(x + CW / 2f - cx);
			if (dist < CW / 2f) idxUnder = i;
			float fade = Math.max(0.35f, 1f - dist / (W * 0.6f));
			card(g, x, ry, s, fade, done && i == WIN);
		}
		if (!done && idxUnder != lastIdx) {
			lastIdx = idxUnder;
			if (ModuleManager.CASE_OPENING.sounds.on()) UiSounds.click();
		}
		// Marker
		ZeroRender.rect(g, cx - 1.1f, ry - 14f, cx + 1.1f, ry + CH + 14f, 1f, 0xFFF6D14A);
		ZeroRender.rect(g, cx - 5f, ry - 18f, cx + 5f, ry - 12f, 2f, 0xFFF6D14A);
		ZeroRender.rect(g, cx - 5f, ry + CH + 12f, cx + 5f, ry + CH + 18f, 2f, 0xFFF6D14A);

		// Enthuellung
		if (done) {
			Skin w = reel.get(WIN);
			float by = ry + CH + 34f;
			ZeroRender.textCentered(g, ZeroFont.BOLD, w.name(), cx, by, 14f, w.color());
			ZeroRender.textCentered(g, ZeroFont.REGULAR, w.rarity() + (equipped ? "  ·  equipped as Weapon Skin" : ""), cx, by + 18f, 7.5f, 0xFFD0D0DA);
		}

		// Buttons unten
		float bw = 92f, bh = 20f, bby = H - 40f;
		bAgain = new float[]{cx - bw - 8f, bby, cx - 8f, bby + bh};
		bClose = new float[]{cx + 8f, bby, cx + 8f + bw, bby + bh};
		button(g, bAgain, "SPIN AGAIN", hit(bAgain, mouseX, mouseY), done);
		button(g, bClose, "CLOSE", hit(bClose, mouseX, mouseY), true);
	}

	private void card(GuiGraphics g, float x, float y, Skin s, float fade, boolean winner) {
		int bg = ZeroRender.alpha(0xFF15151E, fade);
		ZeroRender.rect(g, x, y, x + CW, y + CH, 8f, bg);
		ZeroRender.stroke(g, x, y, x + CW, y + CH, 8f, winner ? 1.6f : 0.6f, ZeroRender.alpha(winner ? 0xFFF6D14A : 0x40FFFFFF, fade));
		// Seltenheits-Balken unten
		ZeroRender.rect(g, x + 6f, y + CH - 9f, x + CW - 6f, y + CH - 5f, 2f, ZeroRender.alpha(s.color(), fade));
		// Echtes Modell-Bild, wenn vorhanden (Karambit aus dem 3D-Modell gerendert)
		if (s.name().equals("Karambit")) {
			int sz = 84;
			int ix = Math.round(x + CW / 2f - sz / 2f), iy = Math.round(y + 6f);
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
					net.minecraft.resources.Identifier.fromNamespaceAndPath("quiqzy", "textures/gui/case_karambit.png"),
					ix, iy, 0f, 0f, sz, sz, sz, sz, ZeroRender.alpha(0xFFFFFFFF, fade));
			ZeroRender.textCentered(g, ZeroFont.BOLD, s.name(), x + CW / 2f, y + CH - 30f, 7.6f, ZeroRender.alpha(0xFFFFFFFF, fade));
			ZeroRender.textCentered(g, ZeroFont.REGULAR, s.rarity(), x + CW / 2f, y + CH - 20f, 5.8f, ZeroRender.alpha(s.color(), fade));
			return;
		}
		// Stilisierter Knife (schraege Klinge + Griff) in Seltenheitsfarbe
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + CW / 2f, y + CH / 2f - 8f);
		pose.rotate((float) Math.toRadians(-32));
		ZeroRender.rect(g, -34f, -4.5f, 14f, 4.5f, 4f, ZeroRender.alpha(ZeroRender.lerp(0xFFE8E8F0, s.color(), 0.35f), fade));
		ZeroRender.rect(g, -36f, -2.2f, 10f, 2.2f, 2f, ZeroRender.alpha(0xFFFFFFFF, fade * 0.55f));
		ZeroRender.rect(g, 12f, -6f, 36f, 6f, 3f, ZeroRender.alpha(0xFF2A2A36, fade));
		ZeroRender.rect(g, 10f, -7.5f, 15f, 7.5f, 2f, ZeroRender.alpha(s.color(), fade));
		pose.popMatrix();
		ZeroRender.textCentered(g, ZeroFont.BOLD, s.name(), x + CW / 2f, y + CH - 30f, 7.6f, ZeroRender.alpha(0xFFFFFFFF, fade));
		ZeroRender.textCentered(g, ZeroFont.REGULAR, s.rarity(), x + CW / 2f, y + CH - 20f, 5.8f, ZeroRender.alpha(s.color(), fade));
	}

	private void button(GuiGraphics g, float[] r, String label, boolean hot, boolean enabled) {
		float a = enabled ? 1f : 0.35f;
		ZeroRender.rect(g, r[0], r[1], r[2], r[3], 6f, ZeroRender.alpha(hot && enabled ? 0x33FFFFFF : 0x18FFFFFF, a));
		ZeroRender.stroke(g, r[0], r[1], r[2], r[3], 6f, 0.7f, ZeroRender.alpha(0x80FFFFFF, a));
		ZeroRender.textCentered(g, ZeroFont.BOLD, label, (r[0] + r[2]) / 2f, r[1] + 5.5f, 7.5f, ZeroRender.alpha(0xFFFFFFFF, a));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (hit(bClose, e.x(), e.y())) { onClose(); return true; }
		if (done && hit(bAgain, e.x(), e.y())) { spin(); UiSounds.click(); return true; }
		return super.mouseClicked(e, dbl);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == 256) { onClose(); return true; }
		return super.keyPressed(e);
	}
}
