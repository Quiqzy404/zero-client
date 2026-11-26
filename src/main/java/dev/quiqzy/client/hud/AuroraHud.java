package dev.quiqzy.client.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import dev.quiqzy.client.gui.Draw;
import dev.quiqzy.client.gui.engine.ZeroFont;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.util.Theme;

/**
 * "Aurora" HUD-Stil: alles in einer Themenfarbe getönt (Glas, Kanten, Schein, Hervorhebungen).
 * Widgets: Info-Pille + Koordinaten (oben Mitte), Musik-Karte + Lyrics-Pille, Keybinds, Potions,
 * Speed mit Verlauf, Toggle-Benachrichtigungen, eigene Leben/Hunger-Leisten über der Hotbar.
 * Eigene Gestaltung (angelehnt an gängige Client-HUDs, kein fremder Code).
 */
public final class AuroraHud {
	private static final int TEXT = 0xFFF5F2FA;
	private static final Map<Object, Float> ANIM = new HashMap<>();
	private static float dt, time;

	private AuroraHud() {
	}

	public static boolean active() {
		var a = ModuleManager.ACCENT;
		return a != null && "Aurora".equals(a.hudStyle.get());
	}

	// ------------------------------------------------------------------ Farben

	public static int accent() {
		if (Theme.glass()) return 0xFFFFFFFF;
		return Theme.hsv(Theme.hue(), 0.58f, 1f, 1f);
	}

	public static int accent2() {
		if (Theme.glass()) return 0xFFE9ECF2;
		return Theme.hsv(Theme.hue() + 0.035f, 0.38f, 1f, 1f);
	}

	private static int muted() {
		if (Theme.glass()) return 0xB8FFFFFF;
		return Theme.hsv(Theme.hue(), 0.16f, 0.80f, 1f);
	}

	private static int faint() {
		if (Theme.glass()) return 0x73FFFFFF;
		return Theme.hsv(Theme.hue(), 0.22f, 0.58f, 1f);
	}

	private static int a(int c, float f) {
		return ZeroRender.alpha(c, f);
	}

	private static long blurAt;

	/** Glass-Theme: einmal pro Frame das Spielbild weichzeichnen, damit HUD-Glas echte Unschärfe dahinter hat. */
	public static void captureBlur() {
		var acc = ModuleManager.ACCENT;
		if (acc == null || !active() || !acc.hudBlur.on()) return;
		long now = System.nanoTime();
		if (now - blurAt < 2_000_000L && dev.quiqzy.client.gui.engine.ZeroBlur.result() != null) return;
		blurAt = now;
		try {
			dev.quiqzy.client.gui.engine.ZeroBlur.capture(ModuleManager.CLICK_GUI.blurStrength.get());
		} catch (Throwable ignored) {
		}
	}

	/**
	 * HUD-Panel. Glass-Theme: Liquid Glass (Unschärfe + Brechung am Rand aus dem Shader, rauchige Tönung,
	 * heller Rand oben links, schwaches Gegenlicht unten). Sonst: getöntes Glas in der Themenfarbe.
	 */
	public static void surface(GuiGraphics g, float x1, float y1, float x2, float y2, float r, float alpha) {
		if (alpha <= 0.01f) return;
		// neuer Stil (wie die Panel-GUI): immer Glas mit Blur + Farbaufnahme, kein Rand
		if (true) {
			glassSurface(g, x1, y1, x2, y2, r, alpha);
			return;
		}
		float h = Theme.hue();
		int acc = accent();
		ZeroRender.glow(g, x1, y1 + 2f, x2, y2 + 3f, r, 10f, a(0xFF000000, 0.42f * alpha));
		ZeroRender.glow(g, x1, y1, x2, y2, r, 9f, a(acc, 0.13f * alpha));
		ZeroRender.gradient(g, x1, y1, x2, y2, r, Theme.hsv(h, 0.55f, 0.20f, 0.80f * alpha), Theme.hsv(h + 0.02f, 0.62f, 0.10f, 0.74f * alpha), 90f);
		float sheen = Math.min(14f, (y2 - y1) * 0.5f);
		ZeroRender.gradient(g, x1, y1, x2, y1 + sheen, r, a(0xFFFFFFFF, 0.07f * alpha), 0x00FFFFFF, 90f);
		ZeroRender.strokeGradient(g, x1, y1, x2, y2, r, 0.7f, a(acc, 0.55f * alpha), a(acc, 0.12f * alpha), 90f);
	}

	/** Glass-Theme HUD: nur graues, durchsichtiges Milchglas mit Blur. Kein Rand, kein Schatten, kein Glanz. */
	public static void glassSurface(GuiGraphics g, float x1, float y1, float x2, float y2, float r, float alpha) {
		ZeroRender.panel(g, x1, y1, x2, y2, r, a(0xFF14161C, 0.50f), a(0xFFFFFFFF, 0.08f), alpha);
	}

	/** Heller Glas-"Tropfen" für Auswahl/aktive Elemente (statt farbiger Füllung). */
	public static void lens(GuiGraphics g, float x1, float y1, float x2, float y2, float r, float alpha) {
		if (alpha <= 0.01f) return;
		// aktiv/ausgewählt = weißes Milchglas (wie aktive Module in der Panel-GUI)
		if (true) {
			ZeroRender.panel(g, x1, y1, x2, y2, r, a(0xFFFFFFFF, 0.40f), a(0xFFFFFFFF, 0.10f), alpha);
			return;
		}
		if (!Theme.glass()) {
			ZeroRender.glow(g, x1, y1, x2, y2, r, 5f, a(accent(), 0.40f * alpha));
			ZeroRender.gradient(g, x1, y1, x2, y2, r, a(accent(), 0.55f * alpha), a(accent2(), 0.25f * alpha), 90f);
			ZeroRender.stroke(g, x1, y1, x2, y2, r, 0.6f, a(accent2(), 0.9f * alpha));
			return;
		}
		ZeroRender.rect(g, x1, y1, x2, y2, r, a(0xFFFFFFFF, 0.16f * alpha));
	}

	private static float anim(Object key, float target, float speed) {
		float v = ANIM.getOrDefault(key, target);
		v = Draw.approach(v, target, speed, dt);
		ANIM.put(key, v);
		return v;
	}

	private static float w(ZeroFont f, String s, float size) {
		return f.width(s, size, 0f);
	}

	private static String clip(ZeroFont f, String s, float size, float max) {
		if (w(f, s, size) <= max) return s;
		String t = s;
		while (t.length() > 1 && w(f, t + "…", size) > max) t = t.substring(0, t.length() - 1);
		return t + "…";
	}

	// ------------------------------------------------------------------ Einstieg

	/** Oben Mitte + Seiten-Widgets. Gibt nichts zurück; Positionen sind fest, per HUD-Editor verschiebbar. */
	public static void render(GuiGraphics g, Minecraft mc, LocalPlayer p, float vw, float vh, float kk, float leftY, float frameDt, float t) {
		dt = frameDt;
		time = t;
		sampleSpeed(p);
		float y = 6f;
		boolean infoOn = ModuleManager.HUD.isEnabled() || ModuleManager.WATERMARK.isEnabled() || ModuleManager.STATUS_BAR.isEnabled();
		if (infoOn && ModuleManager.HUD.coords.on()) { ZeroHud.beginExt(g, "a_coords"); y = coords(g, p, vw, y); ZeroHud.endExt(g); }
		if (infoOn) { ZeroHud.beginExt(g, "a_info"); y = info(g, mc, p, vw, y); ZeroHud.endExt(g); }
		if (ModuleManager.SPOTIFY.isEnabled()) { ZeroHud.beginExt(g, "a_music"); y = music(g, mc, vw, y); ZeroHud.endExt(g); }

		float right = vw - 8f;
		float ry = vh * 0.24f;
		if (ModuleManager.POTION_HUD.isEnabled()) { ZeroHud.beginExt(g, "a_potions"); ry = potions(g, p, right, ry); ZeroHud.endExt(g); }
		if (ModuleManager.SPEED_HUD.isEnabled()) { ZeroHud.beginExt(g, "a_speed"); ry = speed(g, right, ry + 6f); ZeroHud.endExt(g); }
		if (ModuleManager.KEYBINDS_HUD.isEnabled()) { ZeroHud.beginExt(g, "a_keybinds"); keybinds(g, 8f, Math.max(leftY + 6f, vh * 0.34f)); ZeroHud.endExt(g); }
		if (ModuleManager.HEALTH_BARS.isEnabled()) { ZeroHud.beginExt(g, "a_bars"); bars(g, mc, p, vw, vh, kk); ZeroHud.endExt(g); }
		if (ModuleManager.NOTIFICATIONS.isEnabled()) notifications(g, vw, vh, kk);
	}

	// ------------------------------------------------------------------ Oben Mitte

	private static float coords(GuiGraphics g, LocalPlayer p, float vw, float y) {
		String xs = String.valueOf(p.getBlockX()), ys = String.valueOf(p.getBlockY()), zs = String.valueOf(p.getBlockZ());
		float s = 6.2f, h = 15f;
		String[][] parts = {{"X", xs}, {"Y", ys}, {"Z", zs}};
		float tw = 12f;
		for (String[] pt : parts) tw += w(ZeroFont.BOLD, pt[0], 5f) + 3f + w(ZeroFont.BOLD, pt[1], s) + 8f;
		tw -= 8f;
		float x1 = vw / 2f - tw / 2f - 8f, x2 = vw / 2f + tw / 2f + 8f, y2 = y + h;
		surface(g, x1, y, x2, y2, h / 2f, 1f);
		ZeroHud.markExt(x1, y, x2, y2);
		float x = x1 + 8f, cy = y + h / 2f;
		ZeroRender.icon(g, ZeroFont.Icon.NAVIGATION, x + 3.5f, cy, 6.5f, muted());
		x += 12f;
		for (String[] pt : parts) {
			ZeroRender.text(g, ZeroFont.BOLD, pt[0], x, cy - 2.6f, 5f, faint());
			x += w(ZeroFont.BOLD, pt[0], 5f) + 3f;
			ZeroRender.text(g, ZeroFont.BOLD, pt[1], x, cy - 3.3f, s, TEXT);
			x += w(ZeroFont.BOLD, pt[1], s) + 8f;
		}
		return y2 + 4f;
	}

	private static float info(GuiGraphics g, Minecraft mc, LocalPlayer p, float vw, float y) {
		String name = p.getGameProfile().name();
		String fps = String.valueOf(mc.getFps());
		String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : mc.hasSingleplayerServer() ? "Singleplayer" : "-";
		if (server.length() > 22) server = server.substring(0, 22);
		String clock = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
		String ping = null;
		if (mc.getConnection() != null && mc.getCurrentServer() != null) {
			var pi = mc.getConnection().getPlayerInfo(p.getUUID());
			if (pi != null) ping = String.valueOf(pi.getLatency());
		}
		float s = 7f, h = 20f;
		// Segmente: {icon, wert, einheit, id} – im HUD-Editor einzeln per × ausblendbar
		List<Object[]> segs = new ArrayList<>();
		segs.add(new Object[]{null, name, null, "name"});
		segs.add(new Object[]{ZeroFont.Icon.MONITOR, fps, "fps", "fps"});
		if (ping != null) segs.add(new Object[]{ZeroFont.Icon.WIFI, ping, "ms", "ping"});
		segs.add(new Object[]{ZeroFont.Icon.CLOUD, server, null, "server"});
		segs.add(new Object[]{ZeroFont.Icon.CLOCK, clock, null, "clock"});
		segs.removeIf(sg -> ZeroHud.partHidden("a_info:" + sg[3]));
		if (segs.isEmpty()) return y;
		float gap = 15f;
		float total = 0f;
		for (Object[] sg : segs) total += segW(sg, s) + gap;
		total -= gap;
		float x1 = vw / 2f - total / 2f - 10f, x2 = vw / 2f + total / 2f + 10f, y2 = y + h;
		// passt die Leiste (mit Verschiebung) nicht mehr ins Bild -> hochkant, Teile untereinander
		float off = ModuleManager.ACCENT.offset("a_info")[0];
		if (x1 + off < 3f || x2 + off > vw - 3f) return infoVertical(g, p, segs, vw, y, s, off);
		surface(g, x1, y, x2, y2, h / 2f, 1f);
		ZeroHud.markExt(x1, y, x2, y2);
		float x = x1 + 10f, cy = y + h / 2f;
		for (int i = 0; i < segs.size(); i++) {
			Object[] sg = segs.get(i);
			float sx = x;
			x = drawSeg(g, p, sg, x, cy, s);
			ZeroHud.markPart((String) sg[3], sx - 3f, y + 2f, x + 3f, y2 - 2f);
			if (i < segs.size() - 1) {
				ZeroRender.circle(g, x + gap / 2f, cy, 0.9f, a(0xFFFFFFFF, 0.28f));
				x += gap;
			}
		}
		return y2 + 5f;
	}

	/** Hochkant-Variante: jede Info in einer eigenen Zeile (wenn die Leiste am Rand nicht mehr passt). */
	private static float infoVertical(GuiGraphics g, LocalPlayer p, List<Object[]> segs, float vw, float y, float s, float off) {
		float rowH = 16f, maxW = 0f;
		for (Object[] sg : segs) maxW = Math.max(maxW, segW(sg, s));
		float wdt = maxW + 20f, hgt = segs.size() * rowH + 6f;
		float x1 = vw / 2f - wdt / 2f;
		// im Bild halten
		if (x1 + off < 3f) x1 = 3f - off;
		if (x1 + wdt + off > vw - 3f) x1 = vw - 3f - off - wdt;
		float x2 = x1 + wdt, y2 = y + hgt;
		surface(g, x1, y, x2, y2, 9f, 1f);
		ZeroHud.markExt(x1, y, x2, y2);
		float ry = y + 3f;
		for (int i = 0; i < segs.size(); i++) {
			Object[] sg = segs.get(i);
			drawSeg(g, p, sg, x1 + 10f, ry + rowH / 2f, s);
			ZeroHud.markPart((String) sg[3], x1 + 3f, ry, x2 - 3f, ry + rowH);
			if (i < segs.size() - 1) ZeroRender.rect(g, x1 + 9f, ry + rowH - 0.3f, x2 - 9f, ry + rowH + 0.3f, 0f, a(0xFFFFFFFF, 0.10f));
			ry += rowH;
		}
		return y2 + 5f;
	}

	/** Ein Info-Teil (Icon/Kopf + Wert + Einheit); gibt die x-Position dahinter zurück. */
	private static float drawSeg(GuiGraphics g, LocalPlayer p, Object[] sg, float x, float cy, float s) {
		String txt = (String) sg[1];
		if (sg[0] == null) {
			try {
				net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, p.getSkin(), Math.round(x), Math.round(cy - 5.5f), 11);
			} catch (Throwable ignored) {
			}
			x += 15f;
		} else {
			ZeroRender.icon(g, (Character) sg[0], x + 4f, cy, 7f, muted());
			x += 11f;
		}
		ZeroRender.text(g, ZeroFont.BOLD, txt, x, cy - 3.6f, s, TEXT);
		x += w(ZeroFont.BOLD, txt, s);
		if (sg[2] != null) {
			x += 1.5f;
			ZeroRender.text(g, ZeroFont.REGULAR, (String) sg[2], x, cy - 2.2f, 5.2f, faint());
			x += w(ZeroFont.REGULAR, (String) sg[2], 5.2f);
		}
		return x;
	}

	private static float segW(Object[] sg, float s) {
		float wv = (sg[0] == null ? 15f : 11f) + w(ZeroFont.BOLD, (String) sg[1], s);
		if (sg[2] != null) wv += 1.5f + w(ZeroFont.REGULAR, (String) sg[2], 5.2f);
		return wv;
	}

	private static float music(GuiGraphics g, Minecraft mc, float vw, float y) {
		var m = ModuleManager.SPOTIFY;
		var t = dev.quiqzy.client.util.MediaInfo.track;
		float fade = anim("a:music", t != null && !(m.hidePaused.on() && !t.playing()) ? 1f : 0f, 6f);
		if (fade < 0.01f || t == null) return y;
		if (m.cover.on()) ZeroHud.loadCover(mc, t.cover());
		long pos = t.position();
		float cw = 150f, h = 38f;
		float x1 = vw / 2f - cw / 2f, x2 = x1 + cw, y2 = y + h;
		surface(g, x1, y, x2, y2, 9f, fade);
		ZeroHud.markExt(x1, y, x2, y2);
		float cs = h - 10f, cx0 = x1 + 5f, cy0 = y + 5f;
		if (m.cover.on() && ZeroHud.coverOk) {
			g.pose().pushMatrix();
			g.pose().translate(cx0, cy0);
			g.pose().scale(cs / 64f, cs / 64f);
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ZeroHud.COVER_ID, 0, 0, 0f, 0f, 64, 64, 64, 64);
			g.pose().popMatrix();
			ZeroRender.stroke(g, cx0 - 1f, cy0 - 1f, cx0 + cs + 1f, cy0 + cs + 1f, 6f, 2f, Theme.hsv(Theme.hue(), 0.6f, 0.13f, fade));
		} else {
			ZeroRender.gradient(g, cx0, cy0, cx0 + cs, cy0 + cs, 6f, a(accent(), fade), Theme.hsv(Theme.hue(), 0.7f, 0.45f, fade), 45f);
			ZeroRender.icon(g, ZeroFont.Icon.MUSIC, cx0 + cs / 2f, cy0 + cs / 2f, 11f, a(TEXT, fade));
		}
		float tx = cx0 + cs + 7f, maxW = x2 - 8f - tx;
		ZeroRender.text(g, ZeroFont.BOLD, clip(ZeroFont.BOLD, t.title(), 7.4f, maxW), tx, y + 5.5f, 7.4f, a(TEXT, fade));
		ZeroRender.text(g, ZeroFont.REGULAR, clip(ZeroFont.REGULAR, t.artist(), 5.8f, maxW), tx, y + 14.5f, 5.8f, a(muted(), fade));
		// Steuerung (nur Anzeige)
		float mid = tx + maxW / 2f, cyc = y + 25.5f;
		ZeroRender.icon(g, ZeroFont.Icon.SKIP_BACK, mid - 13f, cyc, 6f, a(muted(), fade));
		ZeroRender.circle(g, mid, cyc, 5f, a(accent(), 0.25f * fade));
		ZeroRender.icon(g, t.playing() ? ZeroFont.Icon.PAUSE : ZeroFont.Icon.PLAY, mid, cyc, 6f, a(TEXT, fade));
		ZeroRender.icon(g, ZeroFont.Icon.SKIP_FORWARD, mid + 13f, cyc, 6f, a(muted(), fade));
		float frac = t.durMs() > 0 ? Math.min(1f, pos / (float) t.durMs()) : 0f;
		float bx1 = tx, bx2 = x2 - 8f, by = y2 - 5f;
		ZeroRender.rect(g, bx1, by, bx2, by + 1.8f, 0.9f, a(0xFFFFFFFF, 0.12f * fade));
		ZeroRender.gradient(g, bx1, by, bx1 + (bx2 - bx1) * frac, by + 1.8f, 0.9f, a(accent(), fade), a(accent2(), fade), 0f);
		y = y2 + 4f;

		if (m.lyrics.on()) {
			var lines = dev.quiqzy.client.util.MediaInfo.lyrics;
			int idx = lines.isEmpty() ? -1 : dev.quiqzy.client.util.MediaInfo.currentLine(pos + m.offset.get());
			String line = idx >= 0 ? lines.get(idx).text() : null;
			float lf = anim("a:lyric", line != null && !line.isBlank() ? 1f : 0f, 6f) * fade;
			if (lf > 0.01f && idx >= 0) {
				float s = 7.2f;
				String txt = clip(ZeroFont.BOLD, lines.get(idx).text(), s, vw * 0.5f);
				float lw = w(ZeroFont.BOLD, txt, s) + 26f;
				float lx1 = vw / 2f - lw / 2f, lx2 = vw / 2f + lw / 2f, ly2 = y + 16f;
				surface(g, lx1, y, lx2, ly2, 8f, lf);
				ZeroHud.markExt(lx1, y, lx2, ly2);
				// kleiner Equalizer
				for (int b = 0; b < 3; b++) {
					float amp = t.playing() ? 0.5f + 0.5f * (float) Math.sin(time * (5.0 + b) + b * 1.9) : 0.2f;
					float bh = 1.5f + 5f * amp, bx = lx1 + 7f + b * 2.6f;
					ZeroRender.rect(g, bx, y + 11.5f - bh, bx + 1.5f, y + 11.5f, 0.7f, a(accent(), lf));
				}
				ZeroRender.text(g, ZeroFont.BOLD, txt, lx1 + 18f, y + 4.2f, s, a(TEXT, lf));
				y = ly2 + 4f;
			}
		}
		return y;
	}

	// ------------------------------------------------------------------ Seiten-Widgets

	private static float header(GuiGraphics g, char icon, String title, String right, float x1, float y, float x2) {
		ZeroRender.icon(g, icon, x1 + 12f, y + 10f, 7f, muted());
		ZeroRender.text(g, ZeroFont.BOLD, title, x1 + 19f, y + 6.4f, 7f, TEXT);
		if (right != null) ZeroRender.textRight(g, ZeroFont.BOLD, right, x2 - 9f, y + 7f, 5.8f, faint());
		return y + 23f;
	}

	private static void keybinds(GuiGraphics g, float x, float y) {
		List<Module> list = new ArrayList<>();
		for (Module m : ModuleManager.all()) if (m.keybind() > 0) list.add(m);
		if (list.isEmpty()) return;
		float s = 6.6f, w = 112f, rh = 13.5f;
		int on = 0;
		for (Module m : list) {
			w = Math.max(w, w(ZeroFont.BOLD, m.name(), s) + 52f);
			if (m.isEnabled()) on++;
		}
		float h = 27f + list.size() * rh;
		float x2 = x + w, y2 = y + h;
		surface(g, x, y, x2, y2, 11f, 1f);
		ZeroHud.markExt(x, y, x2, y2);
		float ry = header(g, ZeroFont.Icon.KEYBOARD, "Keybinds", on + "/" + list.size(), x, y, x2);
		for (Module m : list) {
			float o = anim("a:kb:" + m.id(), m.isEnabled() ? 1f : 0f, 10f);
			float cy = ry + rh / 2f - 0.5f;
			if (o > 0.01f) ZeroRender.rect(g, x + 4f, ry - 0.5f, x2 - 4f, ry + rh - 1.5f, 5f, a(0xFFFFFFFF, 0.07f * o));
			ZeroRender.circle(g, x + 11f, cy, 2.1f, a(0xFFFFFFFF, 0.18f));
			if (o > 0.01f) {
				ZeroRender.glow(g, x + 8f, cy - 3f, x + 14f, cy + 3f, 3f, 3f, a(accent(), 0.6f * o));
				ZeroRender.circle(g, x + 11f, cy, 2.1f, a(accent(), o));
			}
			ZeroRender.text(g, ZeroFont.BOLD, m.name(), x + 18f, cy - 3.3f, s, ZeroRender.lerp(muted(), TEXT, o));
			String key = keyName(m.keybind());
			float kw = Math.max(13f, w(ZeroFont.BOLD, key, 5.2f) + 8f);
			float kx2 = x2 - 8f, kx1 = kx2 - kw;
			ZeroRender.rect(g, kx1, cy - 4.5f, kx2, cy + 4.5f, 4.5f, a(0xFFFFFFFF, 0.08f + 0.06f * o));
			ZeroRender.textCentered(g, ZeroFont.BOLD, key, (kx1 + kx2) / 2f, cy - 2.6f, 5.2f, ZeroRender.lerp(muted(), TEXT, o));
			ry += rh;
		}
	}

	private static String keyName(int key) {
		try {
			String n = com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
			return n.length() > 5 ? n.substring(0, 5) : n.toUpperCase(java.util.Locale.ROOT);
		} catch (Throwable t) {
			return "?";
		}
	}

	private static float potions(GuiGraphics g, LocalPlayer p, float right, float y) {
		List<MobEffectInstance> effects = new ArrayList<>(p.getActiveEffects());
		if (effects.isEmpty()) return y;
		effects.sort((x, z) -> Integer.compare(z.getDuration(), x.getDuration()));
		float s = 6.6f, w = 118f, rh = 19f;
		for (var e : effects) w = Math.max(w, w(ZeroFont.BOLD, effectTitle(e), s) + w(ZeroFont.BOLD, effectTime(e), 6f) + 40f);
		float x1 = right - w, h = 27f + effects.size() * rh - 2f, y2 = y + h;
		surface(g, x1, y, right, y2, 11f, 1f);
		ZeroHud.markExt(x1, y, right, y2);
		float ry = header(g, ZeroFont.Icon.FLASK, "Effects", String.valueOf(effects.size()), x1, y, right);
		for (var e : effects) {
			int col = 0xFF000000 | e.getEffect().value().getColor();
			ZeroRender.rect(g, x1 + 7f, ry, x1 + 21f, ry + 14f, 4.5f, a(0xFFFFFFFF, 0.08f));
			g.pose().pushMatrix();
			g.pose().translate(x1 + 9f, ry + 2f);
			g.pose().scale(10f / 18f, 10f / 18f);
			try {
				g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Gui.getMobEffectSprite(e.getEffect()), 0, 0, 18, 18);
			} catch (Throwable ignored) {
			}
			g.pose().popMatrix();
			boolean low = !e.isInfiniteDuration() && e.getDuration() < 200;
			float blink = low ? 0.55f + 0.45f * (float) Math.sin(time * 8.0) : 1f;
			ZeroRender.text(g, ZeroFont.BOLD, effectTitle(e), x1 + 26f, ry + 1.2f, s, a(TEXT, blink));
			ZeroRender.textRight(g, ZeroFont.BOLD, effectTime(e), right - 9f, ry + 1.6f, 6f, a(muted(), blink));
			float frac = e.isInfiniteDuration() ? 1f : Math.min(1f, e.getDuration() / 1200f);
			float lx1 = x1 + 26f, lx2 = right - 9f, ly = ry + 10.5f;
			ZeroRender.rect(g, lx1, ly, lx2, ly + 1.6f, 0.8f, a(0xFFFFFFFF, 0.12f));
			int fill = Theme.glass() ? 0xFFFFFFFF : col;
			ZeroRender.noTint = true;
			ZeroRender.rect(g, lx1, ly, lx1 + (lx2 - lx1) * frac, ly + 1.6f, 0.8f, a(fill, blink));
			ZeroRender.noTint = false;
			ry += rh;
		}
		return y2 + 5f;
	}

	private static String effectTitle(MobEffectInstance e) {
		String n = e.getEffect().value().getDisplayName().getString();
		if (e.getAmplifier() > 0) n += " " + roman(e.getAmplifier() + 1);
		return n;
	}

	private static String effectTime(MobEffectInstance e) {
		if (e.isInfiniteDuration()) return "inf";
		int sec = e.getDuration() / 20;
		return (sec / 60) + ":" + String.format("%02d", sec % 60);
	}

	private static String roman(int n) {
		return switch (n) {
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(n);
		};
	}

	// --- Speed (Blöcke pro Sekunde) mit kleinem Verlauf
	private static final float[] SPEED = new float[48];
	private static int speedIdx;
	private static long speedAt;
	private static double lastX = Double.NaN, lastZ;

	private static void sampleSpeed(LocalPlayer p) {
		long now = System.currentTimeMillis();
		if (now - speedAt < 100) return;
		float secs = speedAt == 0 ? 0.1f : (now - speedAt) / 1000f;
		speedAt = now;
		if (!Double.isNaN(lastX)) {
			double dx = p.getX() - lastX, dz = p.getZ() - lastZ;
			float bps = (float) Math.sqrt(dx * dx + dz * dz) / Math.max(0.05f, secs);
			if (bps > 80f) bps = 0f; // Teleport
			SPEED[speedIdx] = bps;
			speedIdx = (speedIdx + 1) % SPEED.length;
		}
		lastX = p.getX();
		lastZ = p.getZ();
	}

	private static float speed(GuiGraphics g, float right, float y) {
		float w = 104f, h = 36f, x1 = right - w, y2 = y + h;
		surface(g, x1, y, right, y2, 11f, 1f);
		ZeroHud.markExt(x1, y, right, y2);
		float cur = SPEED[(speedIdx + SPEED.length - 1) % SPEED.length];
		float shown = anim("a:bps", cur, 8f);
		String v = String.format(java.util.Locale.ROOT, "%.1f", shown);
		ZeroRender.icon(g, ZeroFont.Icon.GAUGE, x1 + 12f, y + 10f, 7f, muted());
		ZeroRender.text(g, ZeroFont.BOLD, v, x1 + 19f, y + 5.4f, 8.4f, TEXT);
		ZeroRender.text(g, ZeroFont.REGULAR, "b/s", x1 + 21f + w(ZeroFont.BOLD, v, 8.4f), y + 7.8f, 5.4f, faint());
		float max = 6f;
		for (float sv : SPEED) max = Math.max(max, sv);
		float gx1 = x1 + 9f, gx2 = right - 9f, gy1 = y + 19f, gy2 = y2 - 6f;
		ZeroRender.rect(g, gx1, gy2 + 0.6f, gx2, gy2 + 1f, 0f, a(0xFFFFFFFF, 0.08f));
		int n = SPEED.length;
		float step = (gx2 - gx1) / (n - 1);
		float px = 0, py = 0;
		for (int i = 0; i < n; i++) {
			float sv = SPEED[(speedIdx + i) % n];
			float xx = gx1 + i * step, yy = gy2 - (gy2 - gy1) * Math.min(1f, sv / max);
			float fade = (float) i / n;
			ZeroRender.gradient(g, xx - step / 2f, yy, xx + step / 2f, gy2, 0f, a(accent(), 0.16f * fade), a(accent(), 0.0f), 90f);
			if (i > 0) line(g, px, py, xx, yy, 1f, a(accent2(), 0.25f + 0.75f * fade));
			px = xx;
			py = yy;
		}
		ZeroRender.glow(g, px - 2.5f, py - 2.5f, px + 2.5f, py + 2.5f, 2.5f, 3f, a(accent(), 0.6f));
		ZeroRender.circle(g, px, py, 1.6f, accent());
		return y2 + 5f;
	}

	private static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float wdt, int col) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.01f) return;
		g.pose().pushMatrix();
		g.pose().translate(x1, y1);
		g.pose().rotate((float) Math.atan2(dy, dx));
		ZeroRender.rect(g, 0f, -wdt / 2f, len, wdt / 2f, wdt / 2f, col);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ Benachrichtigungen

	private record Note(String name, boolean on, long at) {
	}

	private static final List<Note> NOTES = new ArrayList<>();

	/** Aus Module.setEnabled: zeigt "X enabled/disabled" unten in der Mitte. */
	public static void toggled(Module m, boolean on) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null || ModuleManager.NOTIFICATIONS == null || m == ModuleManager.NOTIFICATIONS) return;
		synchronized (NOTES) {
			NOTES.removeIf(n -> n.name().equals(m.name()));
			NOTES.add(new Note(m.name(), on, System.currentTimeMillis()));
			while (NOTES.size() > 4) NOTES.remove(0);
		}
	}

	/** iOS-artiger Schalter. Glass: helle Spur + weißer Knopf mit Schatten, sonst Themenfarbe. */
	public static void toggleSwitch(GuiGraphics g, float x1, float cy, float tw, float th, float on, float al) {
		int track = Theme.glass() ? ZeroRender.lerp(0x2EFFFFFF, 0x9EFFFFFF, on) : ZeroRender.lerp(0x2EFFFFFF, accent(), on);
		ZeroRender.rect(g, x1, cy - th / 2f, x1 + tw, cy + th / 2f, th / 2f, a(track, al));
		if (!Theme.glass() && on > 0.05f) ZeroRender.glow(g, x1, cy - th / 2f, x1 + tw, cy + th / 2f, th / 2f, 5f, a(accent(), 0.4f * on * al));
		float kr = th / 2f - th * 0.12f;
		float kx = x1 + th / 2f + (tw - th) * on;
		ZeroRender.glow(g, kx - kr, cy - kr + 0.6f, kx + kr, cy + kr + 0.9f, kr, 2.5f, a(0xFF000000, 0.35f * al));
		ZeroRender.circle(g, kx, cy, kr, a(0xFFFFFFFF, al));
	}

	private static void notifications(GuiGraphics g, float vw, float vh, float kk) {
		long now = System.currentTimeMillis();
		List<Note> list;
		synchronized (NOTES) {
			NOTES.removeIf(n -> now - n.at() > 2600);
			list = new ArrayList<>(NOTES);
		}
		float y = vh - 52f * kk - 21f;
		for (int i = list.size() - 1; i >= 0; i--) {
			Note n = list.get(i);
			float age = (now - n.at()) / 1000f;
			float in = Math.min(1f, age / 0.25f), out = Math.min(1f, Math.max(0f, (2.6f - age) / 0.35f));
			float f = Math.min(1f - (1f - in) * (1f - in) * (1f - in), out);
			float s = 6.8f, h = 19f;
			String state = n.on() ? "Enabled" : "Disabled";
			float tw = 22f + Math.max(w(ZeroFont.BOLD, n.name(), s), w(ZeroFont.REGULAR, state, 5.4f)) + 34f;
			// "Insel" wächst aus der Mitte
			float grow = 0.6f + 0.4f * f;
			float x1 = vw / 2f - tw / 2f * grow, x2 = vw / 2f + tw / 2f * grow;
			float yy = y + (1f - f) * 6f;
			surface(g, x1, yy, x2, yy + h, h / 2f, f);
			if (f > 0.5f) {
				float tf = (f - 0.5f) / 0.5f;
				float cx = x1 + 11f, cy = yy + h / 2f;
				ZeroRender.circle(g, cx, cy, 6.2f, a(0xFFFFFFFF, (n.on() ? 0.20f : 0.08f) * tf));
				ZeroRender.icon(g, ZeroFont.Icon.POWER, cx, cy, 6.4f, a(n.on() ? TEXT : muted(), tf));
				ZeroRender.text(g, ZeroFont.BOLD, n.name(), x1 + 21f, yy + 3.2f, s, a(TEXT, tf));
				ZeroRender.text(g, ZeroFont.REGULAR, state, x1 + 21f, yy + 11.2f, 5.4f, a(muted(), tf));
				float knob = n.on() ? Math.min(1f, age / 0.3f) : 1f - Math.min(1f, age / 0.3f);
				knob = knob * knob * (3f - 2f * knob);
				toggleSwitch(g, x2 - 25f, cy, 17f, 9.5f, knob, tf);
			}
			y -= (h + 4f) * f;
		}
	}

	// ------------------------------------------------------------------ Leben / Hunger über der Hotbar

	private static void bars(GuiGraphics g, Minecraft mc, LocalPlayer p, float vw, float vh, float kk) {
		if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
		ZeroRender.noTint = true;
		try {
			barsRaw(g, p, vw, vh, kk);
		} finally {
			ZeroRender.noTint = false;
		}
	}

	private static void barsRaw(GuiGraphics g, LocalPlayer p, float vw, float vh, float kk) {
		float cx = vw / 2f;
		float bh = 5.6f * kk;
		float y1 = vh - 36.5f * kk, y2 = y1 + bh;
		float lx1 = cx - 91f * kk, lx2 = cx - 3f * kk, rx1 = cx + 3f * kk, rx2 = cx + 91f * kk;
		float r = bh / 2f - 0.3f;
		float ts = 3.7f * kk;
		ZeroHud.markExt(lx1, y1 - 6f * kk, rx2, y2);

		// Leben
		float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth()), abs = p.getAbsorptionAmount();
		float shown = anim("a:hp", hp / max, 7f);
		float lag = anim("a:hplag", hp / max, 1.6f);
		bar(g, lx1, y1, lx2, y2, r);
		if (lag > shown) ZeroRender.rect(g, lx1, y1, lx1 + (lx2 - lx1) * lag, y2, r, 0x66FFFFFF);
		boolean low = hp / max < 0.3f;
		float pulse = low ? 0.7f + 0.3f * (float) Math.sin(time * 9.0) : 1f;
		ZeroRender.gradient(g, lx1, y1, lx1 + Math.max(bh, (lx2 - lx1) * shown), y2, r, a(0xFFFF3F5A, pulse), a(0xFFFF8E8E, pulse), 0f);
		ZeroRender.glow(g, lx1, y1, lx1 + (lx2 - lx1) * shown, y2, r, 4f * kk, a(0xFFFF3F5A, 0.35f));
		String ht = (int) Math.ceil(hp) + "/" + (int) max;
		ZeroRender.textCentered(g, ZeroFont.BOLD, ht, (lx1 + lx2) / 2f, y1 + bh / 2f - ts * 0.52f, ts, TEXT);
		if (abs > 0f) {
			float af = Math.min(1f, abs / max);
			ZeroRender.rect(g, lx1, y1 - 3.4f * kk, lx2, y1 - 1.4f * kk, 0.8f * kk, 0x30FFFFFF);
			ZeroRender.gradient(g, lx1, y1 - 3.4f * kk, lx1 + (lx2 - lx1) * af, y1 - 1.4f * kk, 0.8f * kk, 0xFFFFC94D, 0xFFFFE89A, 0f);
		}
		// Rüstung (dünn, über der Lebensleiste)
		int armor = p.getArmorValue();
		if (armor > 0 && abs <= 0f) {
			ZeroRender.rect(g, lx1, y1 - 3.4f * kk, lx2, y1 - 1.4f * kk, 0.8f * kk, 0x30FFFFFF);
			ZeroRender.gradient(g, lx1, y1 - 3.4f * kk, lx1 + (lx2 - lx1) * Math.min(1f, armor / 20f), y1 - 1.4f * kk, 0.8f * kk, a(accent2(), 1f), 0xFFE6E9F2, 0f);
		}

		// Hunger
		var food = p.getFoodData();
		float fs = anim("a:food", food.getFoodLevel() / 20f, 7f);
		bar(g, rx1, y1, rx2, y2, r);
		ZeroRender.gradient(g, rx2 - Math.max(bh, (rx2 - rx1) * fs), y1, rx2, y2, r, 0xFFFFB061, 0xFFFF8A2B, 0f);
		ZeroRender.textCentered(g, ZeroFont.BOLD, food.getFoodLevel() + "/20", (rx1 + rx2) / 2f, y1 + bh / 2f - ts * 0.52f, ts, TEXT);
		float sat = Math.min(1f, food.getSaturationLevel() / 20f);
		if (sat > 0f) {
			ZeroRender.rect(g, rx1, y1 - 3.4f * kk, rx2, y1 - 1.4f * kk, 0.8f * kk, 0x30FFFFFF);
			ZeroRender.gradient(g, rx2 - (rx2 - rx1) * sat, y1 - 3.4f * kk, rx2, y1 - 1.4f * kk, 0.8f * kk, 0xFFFFE0A3, 0xFFFFC56B, 0f);
		}
		// Luft unter Wasser
		int air = p.getAirSupply(), maxAir = p.getMaxAirSupply();
		if (air < maxAir && maxAir > 0) {
			float yA = y1 - 7.6f * kk;
			ZeroRender.rect(g, rx1, yA, rx2, yA + 2.6f * kk, 1.1f * kk, 0x30FFFFFF);
			ZeroRender.gradient(g, rx2 - (rx2 - rx1) * Math.max(0f, air / (float) maxAir), yA, rx2, yA + 2.6f * kk, 1.1f * kk, 0xFF7FD3FF, 0xFF3FA9F5, 0f);
		}
	}

	private static void bar(GuiGraphics g, float x1, float y1, float x2, float y2, float r) {
		ZeroRender.glow(g, x1, y1 + 1f, x2, y2 + 1f, r, 4f, 0x66000000);
		ZeroRender.rect(g, x1, y1, x2, y2, r, Theme.glass() ? 0xC71C1D21 : Theme.hsv(Theme.hue(), 0.5f, 0.12f, 0.78f));
		ZeroRender.stroke(g, x1, y1, x2, y2, r, 0.5f, a(accent(), Theme.glass() ? 0.12f : 0.25f));
	}

	// ------------------------------------------------------------------ Welt-Färbung + Ziel-Ring

	public static void worldTint(GuiGraphics g, float vw, float vh) {
		float t = ModuleManager.ACCENT.worldTint.get() / 100f;
		if (t <= 0.001f) return;
		float h = Theme.hue();
		if (Theme.glass()) {
			// farblos: nur eine weiche Vignette
			ZeroRender.radial(g, -vw * 0.25f, -vh * 0.35f, vw * 1.25f, vh * 1.35f, vh * 0.9f, 0x00000000, a(0xFF000000, Math.min(0.55f, t * 1.8f)));
			return;
		}
		ZeroRender.gradient(g, 0, 0, vw, vh, 0f, Theme.hsv(h, 0.75f, 0.75f, t * 0.85f), Theme.hsv(h, 0.7f, 0.35f, t * 0.35f), 90f);
		ZeroRender.radial(g, -vw * 0.25f, -vh * 0.35f, vw * 1.25f, vh * 1.35f, vh * 0.9f, 0x00000000, Theme.hsv(h + 0.02f, 0.8f, 0.12f, Math.min(0.7f, t * 2.2f)));
	}

	/** Leuchtender Ring um das Ziel, der langsam an ihm hoch- und runterläuft (+ zweiter, schwächerer Ring). */
	public static void targetRing(GuiGraphics g, net.minecraft.world.entity.LivingEntity e, float pt, int color, float size, int speed, int thick) {
		var pos = e.getPosition(pt);
		float hgt = e.getBbHeight();
		double r = e.getBbWidth() * 0.85 * Math.max(0.4f, size * 1.6f);
		double tt = System.currentTimeMillis() / 1000.0 * (0.4 + speed * 0.12);
		for (int ring = 0; ring < 2; ring++) {
			double phase = tt + ring * Math.PI;
			double yy = pos.y + hgt * (0.5 + 0.45 * Math.sin(phase));
			float alpha = ring == 0 ? 1f : 0.45f;
			int seg = 48;
			float[] prev = null;
			for (int i = 0; i <= seg; i++) {
				double ang = i * Math.PI * 2 / seg + tt * 0.8;
				float[] sp = HitFx.screen(pos.x + Math.cos(ang) * r, yy, pos.z + Math.sin(ang) * r);
				if (sp != null && prev != null) {
					HitFx.line(g, prev[0], prev[1], sp[0], sp[1], thick * 6f, a(color, 0.12f * alpha));
					HitFx.line(g, prev[0], prev[1], sp[0], sp[1], thick * 2.6f, a(color, 0.45f * alpha));
					HitFx.line(g, prev[0], prev[1], sp[0], sp[1], thick * 1.1f, a(ZeroRender.lerp(color, 0xFFFFFFFF, 0.45f), alpha));
				}
				prev = sp;
			}
		}
	}

	// ------------------------------------------------------------------ Hotbar (GUI-Pixel)

	public static boolean customHotbar() {
		var mc = Minecraft.getInstance();
		return active() && ModuleManager.HEALTH_BARS.isEnabled() && ModuleManager.HEALTH_BARS.hotbar.on()
				&& mc.player != null && !mc.player.isSpectator() && !mc.options.hideGui;
	}

	/** Hotbar wie vor dem Glass-Update, aber schlicht grau: graues Milchglas, Auswahl = hellerer grauer Block. */
	public static void hotbar(GuiGraphics g, Minecraft mc, LocalPlayer p) {
		captureBlur();
		var inv = p.getInventory();
		int sel = inv.getSelectedSlot();
		float cx = g.guiWidth() / 2f, y2 = g.guiHeight() - 2f, y1 = y2 - 20f;
		float x1 = cx - 91f, x2 = cx + 91f;
		greySurface(g, x1, y1, x2, y2, 6f);
		// gleitende Auswahl (nur grau)
		float selX = anim("a:hot", x1 + 1f + sel * 20f, 22f);
		ZeroRender.noTint = true;
		ZeroRender.rect(g, selX + 0.5f, y1 + 1f, selX + 19.5f, y2 - 1f, 5f, 0x40FFFFFF);
		ZeroRender.noTint = false;
		for (int i = 0; i < 9; i++) {
			var st = inv.getItem(i);
			if (st.isEmpty()) continue;
			int ix = Math.round(x1 + 3f + i * 20f), iy = Math.round(y1 + 2f);
			g.renderItem(p, st, ix, iy, i + 1);
			g.renderItemDecorations(mc.font, st, ix, iy);
		}
		// Offhand
		var off = p.getOffhandItem();
		if (!off.isEmpty()) {
			float ox2 = x1 - 4f, ox1 = ox2 - 20f;
			greySurface(g, ox1, y1, ox2, y2, 6f);
			int ix = Math.round(ox1 + 2f), iy = Math.round(y1 + 2f);
			g.renderItem(p, off, ix, iy, 10);
			g.renderItemDecorations(mc.font, off, ix, iy);
		}
		// XP: dünne Leiste + Level (grau/weiß)
		if (mc.gameMode != null && mc.gameMode.hasExperience()) {
			float xp = p.experienceProgress;
			float by = y1 - 4f;
			ZeroRender.noTint = true;
			ZeroRender.rect(g, x1 + 2f, by, x2 - 2f, by + 1.6f, 0.8f, 0x26FFFFFF);
			ZeroRender.rect(g, x1 + 2f, by, x1 + 2f + (x2 - x1 - 4f) * xp, by + 1.6f, 0.8f, 0xE6FFFFFF);
			ZeroRender.noTint = false;
			if (p.experienceLevel > 0) ZeroRender.textCentered(g, ZeroFont.BOLD, String.valueOf(p.experienceLevel), cx, by - 6.2f, 5.4f, TEXT);
		}
	}

	/** Glas wie überall (neuer Stil). */
	private static void greySurface(GuiGraphics g, float x1, float y1, float x2, float y2, float r) {
		boolean prev = ZeroRender.noTint;
		ZeroRender.noTint = true;
		ZeroRender.panel(g, x1, y1, x2, y2, r, 0x8014161C, 0x14FFFFFF, 1f);
		ZeroRender.noTint = prev;
	}
}
