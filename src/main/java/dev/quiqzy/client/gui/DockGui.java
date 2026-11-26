package dev.quiqzy.client.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.gui.engine.ZeroBlur;
import dev.quiqzy.client.gui.engine.ZeroFont;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.hud.AuroraHud;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.module.misc.ClickGuiModule;

/**
 * Dock-GUI (neuer Stil, Glas): unten ein schwebendes Dock mit den Kategorien, darüber ein Glas-Blatt
 * mit den Modulen als Kacheln (Name, Beschreibung, Schalter). Linksklick = an/aus, Rechtsklick = Einstellungen
 * (das Blatt klappt auf die Einstellungen um, mit Zurück-Pfeil). Tippen = Suche über alle Module.
 */
public final class DockGui extends Screen {
	private static final float W = 600f, H = 330f, HEAD = 34f, TILE_H = 40f, GAP = 7f, SROW = 14f;
	private static Module.Category cat = Module.Category.COMBAT;
	private static Module openMod;

	private final Screen parent;
	private float k, vw, vh, mx, my, dt, open;
	private long last = System.nanoTime();
	private final Map<Object, Float> anim = new HashMap<>();
	private final List<Region> regions = new ArrayList<>();
	private float scroll, scrollTarget, contentH, viewH;
	private Setting.Int dragSlider;
	private float sliderX1, sliderX2;
	private Module listening;
	private Setting<?> openPopup;
	private String search = "";
	private String hoverDesc;

	private record Region(float x1, float y1, float x2, float y2, Runnable left, Runnable right) {
	}

	public DockGui() {
		this(null);
	}

	public DockGui(Screen parent) {
		super(Component.literal("ZERO"));
		this.parent = parent;
	}

	private static ClickGuiModule cfg() {
		return ModuleManager.CLICK_GUI;
	}

	private void scale() {
		Window w = this.minecraft.getWindow();
		float pixels = Math.max(1f, w.getHeight() / 540f) * (cfg().scale.get() / 100f);
		pixels = Math.min(pixels, Math.min(w.getWidth() / (W + 40f), w.getHeight() / (H + 110f)));
		this.k = (float) w.getGuiScale() / pixels;
		this.vw = this.width * k;
		this.vh = this.height * k;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
	}

	private float an(Object key, float target, float speed) {
		float v = Draw.approach(anim.getOrDefault(key, target), target, speed, dt);
		anim.put(key, v);
		return v;
	}

	private boolean hot(float x1, float y1, float x2, float y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}

	private void click(float x1, float y1, float x2, float y2, Runnable left, Runnable right) {
		regions.add(new Region(x1, y1, x2, y2, left, right));
	}

	private static int a(int c, float f) {
		return ZeroRender.alpha(c, f);
	}

	private static float w(ZeroFont f, String s, float size) {
		return f.width(s, size, 0f);
	}

	private static String fit(ZeroFont f, String s, float size, float max) {
		if (w(f, s, size) <= max) return s;
		while (s.length() > 1 && w(f, s + "…", size) > max) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	private static int accent() {
		return AuroraHud.accent() | 0xFF000000;
	}

	private static List<Module> modulesOf(Module.Category c) {
		List<Module> out = new ArrayList<>();
		for (Module m : ModuleManager.all()) if (m.category() == c) out.add(m);
		return out;
	}

	private boolean matches(Module m) {
		if (search.isEmpty()) return true;
		String q = search.toLowerCase(Locale.ROOT);
		return m.name().toLowerCase(Locale.ROOT).contains(q) || m.id().contains(q);
	}

	/** Standard-Anordnung: alle Spalten nebeneinander, Breite passt sich dem Bildschirm an. */

	// ------------------------------------------------------------------ Render

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - last) / 1.0e9f);
		last = now;
		open = Draw.approach(open, 1f, 9f, dt);
		scale();
		mx = mouseX * k;
		my = mouseY * k;
		regions.clear();
		hoverDesc = null;
		float ease = 1f - (1f - open) * (1f - open) * (1f - open);

		ZeroBlur.capture(cfg().blurStrength.get());
		g.pose().pushMatrix();
		g.pose().scale(1f / k);
		if (cfg().bgBlur.on()) ZeroRender.frost(g, 0, 0, vw, vh, 0f, 0x40101114, ease);
		ZeroRender.rect(g, 0, 0, vw, vh, 0, a(0xFF050508, cfg().bgDim.get() / 100f * ease));

		float x1 = (vw - W) / 2f, y1 = (vh - H) / 2f - 26f + (1f - ease) * 16f, x2 = x1 + W, y2 = y1 + H;
		ZeroRender.noTint = true;
		try {
			sheet(g, x1, y1, x2, y2, ease);
			dock(g, vw / 2f, y2 + 14f, ease);
		} finally {
			ZeroRender.noTint = false;
		}
		g.pose().popMatrix();
	}

	private void sheet(GuiGraphics g, float x1, float y1, float x2, float y2, float al) {
		float r = cfg().roundness.get() + 8f;
		ZeroRender.glow(g, x1, y1 + 8f, x2, y2 + 12f, r, 26f, a(0xFF000000, 0.22f * al));
		float op = cfg().panelOpacity.get() / 100f;
		ZeroRender.panel(g, x1, y1, x2, y2, r, a(0xFF14161C, 0.18f + 0.6f * op), a(0xFFFFFFFF, 0.10f), al);

		boolean searching = !search.isEmpty();
		if (openMod != null && !searching) {
			boolean bh = hot(x1 + 12f, y1 + 9f, x1 + 30f, y1 + 27f);
			ZeroRender.panel(g, x1 + 12f, y1 + 9f, x1 + 30f, y1 + 27f, 9f, a(0xFFFFFFFF, bh ? 0.30f : 0.16f), 0x14FFFFFF, al);
			ZeroRender.textCentered(g, ZeroFont.BOLD, "<", x1 + 21f, y1 + 13.6f, 7f, a(0xFFFFFFFF, al));
			click(x1 + 10f, y1 + 6f, x1 + 32f, y1 + 30f, () -> { openMod = null; openPopup = null; scrollTarget = 0f; }, null);
			ZeroRender.text(g, ZeroFont.BOLD, openMod.name(), x1 + 38f, y1 + 9.5f, 9f, a(0xFFFFFFFF, al));
			ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, openMod.description(), 5.8f, W - 230f), x1 + 38f, y1 + 21.5f, 5.8f, a(0xFFFFFFFF, 0.55f * al));
		} else {
			String title = searching ? "Search" : cat.label;
			ZeroRender.icon(g, searching ? ZeroFont.Icon.SEARCH : icon(cat), x1 + 22f, y1 + 18f, 9f, a(0xFFFFFFFF, al));
			ZeroRender.text(g, ZeroFont.BOLD, title, x1 + 34f, y1 + 11f, 9.5f, a(0xFFFFFFFF, al));
			int n = searching ? results().size() : modulesOf(cat).size();
			ZeroRender.text(g, ZeroFont.REGULAR, n + " modules", x1 + 40f + w(ZeroFont.BOLD, title, 9.5f), y1 + 14f, 6f, a(0xFFFFFFFF, 0.45f * al));
		}
		float sx2 = x2 - 12f, sx1 = sx2 - 150f, sy1 = y1 + 9f, sy2 = y1 + 27f;
		ZeroRender.panel(g, sx1, sy1, sx2, sy2, 9f, a(0xFF14161C, 0.45f), 0x0AFFFFFF, al);
		ZeroRender.icon(g, ZeroFont.Icon.SEARCH, sx1 + 10f, sy1 + 9f, 6f, a(0xFFFFFFFF, 0.6f * al));
		String sq = searching ? search + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : "Type to search";
		ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, sq, 6.2f, 120f), sx1 + 19f, sy1 + 5.2f, 6.2f, a(0xFFFFFFFF, (searching ? 0.95f : 0.4f) * al));

		float cx1 = x1 + 12f, cx2 = x2 - 12f, cy1 = y1 + HEAD + 4f, cy2 = y2 - 12f;
		viewH = cy2 - cy1;
		scrollTarget = Math.max(0f, Math.min(scrollTarget, Math.max(0f, contentH - viewH)));
		scroll = Draw.approach(scroll, scrollTarget, 16f, dt);
		g.enableScissor(Math.round(cx1 - 4f), Math.round(cy1), Math.round(cx2 + 4f), Math.round(cy2));
		float y = cy1 - scroll;
		boolean inView = hot(cx1 - 4f, cy1, cx2 + 4f, cy2);
		if (openMod != null && !searching) y = settingsView(g, openMod, cx1, y, cx2, cy1, cy2, inView, al);
		else y = tiles(g, searching ? results() : modulesOf(cat), cx1, y, cx2, cy1, cy2, inView, al);
		g.disableScissor();
		contentH = y + scroll - cy1 + 4f;
		if (contentH > viewH + 1f) {
			float maxS = contentH - viewH, t = scroll / maxS, bh = Math.max(18f, viewH * viewH / contentH);
			float by = cy1 + (viewH - bh) * t;
			ZeroRender.rect(g, x2 - 6f, by, x2 - 4.6f, by + bh, 0.7f, a(0xFFFFFFFF, 0.25f * al));
		}
		if (hoverDesc != null && cfg().descriptions.on()) {
			float tw = w(ZeroFont.REGULAR, hoverDesc, 6f) + 18f;
			ZeroRender.panel(g, vw / 2f - tw / 2f, y1 - 24f, vw / 2f + tw / 2f, y1 - 8f, 8f, a(0xFF14161C, 0.6f), 0x0AFFFFFF, al);
			ZeroRender.textCentered(g, ZeroFont.REGULAR, hoverDesc, vw / 2f, y1 - 19.5f, 6f, a(0xFFFFFFFF, 0.9f * al));
		}
	}

	private List<Module> results() {
		List<Module> out = new ArrayList<>();
		for (Module m : ModuleManager.all()) if (matches(m)) out.add(m);
		return out;
	}

	/** Module als Kacheln (3 Spalten). */
	private float tiles(GuiGraphics g, List<Module> mods, float x1, float y, float x2, float clipTop, float clipBot, boolean inView, float al) {
		int cols = 3;
		float tw = (x2 - x1 - GAP * (cols - 1)) / cols;
		float rr = cfg().rowRoundness.get() + 6f;
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			float tx = x1 + (i % cols) * (tw + GAP), ty = y + (i / cols) * (TILE_H + GAP);
			if (ty + TILE_H < clipTop || ty > clipBot) continue;
			boolean settingsOnly = m == ModuleManager.CLICK_GUI;
			boolean on = !settingsOnly && m.isEnabled();
			boolean hv = inView && hot(tx, ty, tx + tw, ty + TILE_H);
			float onA = an(m, on ? 1f : 0f, 14f), hA = an("h" + m.id(), hv ? 1f : 0f, 16f);
			// aus = dunkles Glas, an = weißes Milchglas
			ZeroRender.panel(g, tx, ty, tx + tw, ty + TILE_H, rr,
					ZeroRender.lerp(a(0xFF14161C, 0.46f - 0.10f * hA), a(0xFFFFFFFF, cfg().activeGlass.get() / 100f), onA), a(0xFFFFFFFF, 0.08f), al);
			int txt = ZeroRender.lerp(0xFFFFFFFF, 0xFF141418, onA);
			String name = listening == m ? "Press a key..." : m.name();
			ZeroRender.text(g, ZeroFont.BOLD, fit(ZeroFont.BOLD, name, 6.8f, tw - 40f), tx + 10f, ty + 8f, 6.8f, a(txt, al));
			ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, m.description(), 5.2f, tw - 20f), tx + 10f, ty + 21f, 5.2f, a(txt, 0.55f * al));
			if (m.keybind() > 0) ZeroRender.textRight(g, ZeroFont.REGULAR, keyName(m.keybind()), tx + tw - 9f, ty + 29f, 5f, a(txt, 0.5f * al));
			if (!settingsOnly) {
				float sw = 15f, sh = 7.6f, sx = tx + tw - 9f - sw, sy = ty + 8f;
				ZeroRender.rect(g, sx, sy, sx + sw, sy + sh, sh / 2f, ZeroRender.lerp(a(0xFFFFFFFF, 0.18f * al), a(0xFF141418, 0.85f * al), onA));
				ZeroRender.circle(g, sx + sh / 2f + (sw - sh) * onA, sy + sh / 2f, sh / 2f - 1.1f, a(0xFFFFFFFF, al));
			} else {
				ZeroRender.icon(g, ZeroFont.Icon.SETTINGS, tx + tw - 14f, ty + 12f, 6.5f, a(txt, 0.8f * al));
			}
			if (hv && !m.settings().isEmpty()) hoverDesc = "Left-click on/off  ·  Right-click settings";
			float c1 = Math.max(ty, clipTop), c2 = Math.min(ty + TILE_H, clipBot);
			if (inView && c2 > c1) click(tx, c1, tx + tw, c2, () -> {
				if (settingsOnly) { openSettings(m); return; }
				m.setEnabled(!m.isEnabled());
				QuiqzyConfig.save();
			}, () -> openSettings(m));
		}
		int rows = (mods.size() + cols - 1) / cols;
		return y + rows * (TILE_H + GAP);
	}

	private void openSettings(Module m) {
		search = "";
		openMod = m;
		openPopup = null;
		scroll = scrollTarget = 0f;
	}

	/** Einstellungen eines Moduls in zwei Spalten. */
	private float settingsView(GuiGraphics g, Module m, float x1, float y, float x2, float clipTop, float clipBot, boolean inView, float al) {
		float colW = (x2 - x1 - 16f) / 2f;
		float ly = y + 2f, ry = y + 2f;
		boolean left = true;
		for (Setting<?> s : m.settings()) {
			if (s instanceof Setting.ColorMap) continue;
			if (left) ly = setting(g, m, s, x1, ly, x1 + colW, clipTop, clipBot, inView, al) + 2f;
			else ry = setting(g, m, s, x1 + colW + 16f, ry, x2, clipTop, clipBot, inView, al) + 2f;
			left = ly <= ry;
		}
		if (left) ly = keybindRow(g, m, x1, ly, x1 + colW, clipTop, clipBot, inView, al);
		else ry = keybindRow(g, m, x1 + colW + 16f, ry, x2, clipTop, clipBot, inView, al);
		return Math.max(ly, ry);
	}

	/** Schwebendes Kategorie-Dock unten. */
	private void dock(GuiGraphics g, float cx, float y, float al) {
		Module.Category[] cats = Module.Category.values();
		float bw = 34f, h = 34f, pad = 6f;
		float total = cats.length * bw + pad * 2;
		float x = cx - total / 2f;
		ZeroRender.glow(g, x, y + 4f, x + total, y + h + 6f, h / 2f, 18f, a(0xFF000000, 0.20f * al));
		ZeroRender.panel(g, x, y, x + total, y + h, h / 2f, a(0xFF14161C, 0.45f), a(0xFFFFFFFF, 0.10f), al);
		for (int i = 0; i < cats.length; i++) {
			Module.Category c = cats[i];
			float bx = x + pad + i * bw, bcx = bx + bw / 2f, bcy = y + h / 2f;
			boolean sel = c == cat && openMod == null && search.isEmpty();
			boolean hv = hot(bx, y, bx + bw, y + h);
			float sA = an("dock" + c, sel ? 1f : 0f, 14f), hA = an("dockh" + c, hv ? 1f : 0f, 16f);
			float rad = 12f + 1.5f * hA;
			if (sA > 0.01f || hA > 0.01f)
				ZeroRender.panel(g, bcx - rad, bcy - rad, bcx + rad, bcy + rad, rad, a(0xFFFFFFFF, 0.12f * hA + (cfg().activeGlass.get() / 100f) * sA), 0x10FFFFFF, al);
			ZeroRender.icon(g, icon(c), bcx, bcy, 8.5f + 1f * hA, a(ZeroRender.lerp(0xFFFFFFFF, 0xFF141418, sA), al));
			if (hv) {
				float lw = w(ZeroFont.BOLD, c.label, 6f) + 14f;
				ZeroRender.panel(g, bcx - lw / 2f, y - 18f, bcx + lw / 2f, y - 5f, 6.5f, a(0xFF14161C, 0.65f), 0x0AFFFFFF, al);
				ZeroRender.textCentered(g, ZeroFont.BOLD, c.label, bcx, y - 14.2f, 6f, a(0xFFFFFFFF, al));
			}
			click(bx, y, bx + bw, y + h, () -> {
				cat = c;
				openMod = null;
				openPopup = null;
				search = "";
				scroll = scrollTarget = 0f;
			}, null);
		}
	}

	// ------------------------------------------------------------------ Einstellungen

	private float setting(GuiGraphics g, Module m, Setting<?> s, float x1, float y, float x2, float clipTop, float clipBot, boolean inBody, float al) {
		float c1 = Math.max(y, clipTop), lx = x1 + 3f, rx = x2 - 4f;
		boolean vis = y + 22f > clipTop && y < clipBot;
		int label = a(0xFFFFFFFF, 0.62f * al), value = a(0xFFFFFFFF, 0.85f * al);
		String name = fit(ZeroFont.REGULAR, s.name(), 5.6f, (x2 - x1) * 0.62f);

		if (s instanceof Setting.Bool b) {
			float on = an(b, b.on() ? 1f : 0f, 16f);
			if (vis) {
				ZeroRender.text(g, ZeroFont.REGULAR, name, lx, y + 3.6f, 5.6f, label);
				float tw = 13f, th = 6.6f, tx = rx - tw, ty = y + (SROW - th) / 2f;
				ZeroRender.rect(g, tx, ty, tx + tw, ty + th, th / 2f, ZeroRender.lerp(a(0xFFFFFFFF, 0.16f * al), a(accent(), al), on));
				ZeroRender.circle(g, tx + th / 2f + (tw - th) * on, ty + th / 2f, th / 2f - 1f, a(0xFFFFFFFF, al));
				clickIn(x1, y, x2, y + SROW, clipTop, clipBot, () -> {
					b.toggle();
					QuiqzyConfig.save();
				}, null);
			}
			return y + SROW;
		}
		if (s instanceof Setting.Int in) {
			float h = 19f;
			if (vis) {
				ZeroRender.text(g, ZeroFont.REGULAR, name, lx, y + 2.6f, 5.6f, label);
				ZeroRender.textRight(g, ZeroFont.REGULAR, in.display(), rx, y + 2.6f, 5.6f, value);
				float sx1 = lx, sx2 = rx, sy = y + 13.5f;
				float pr = an(in, in.progress(), 20f);
				ZeroRender.rect(g, sx1, sy - 0.9f, sx2, sy + 0.9f, 0.9f, a(0xFFFFFFFF, 0.16f * al));
				ZeroRender.rect(g, sx1, sy - 0.9f, sx1 + (sx2 - sx1) * pr, sy + 0.9f, 0.9f, a(accent(), al));
				ZeroRender.circle(g, sx1 + (sx2 - sx1) * pr, sy, 2.4f, a(0xFFFFFFFF, al));
				clickIn(x1, y, x2, y + h, clipTop, clipBot, () -> {
					dragSlider = in;
					sliderX1 = sx1;
					sliderX2 = sx2;
					in.setProgress((mx - sx1) / (sx2 - sx1));
				}, () -> {
					in.reset();
					QuiqzyConfig.save();
				});
			}
			return y + h;
		}
		if (s instanceof Setting.Choice ch) {
			boolean opened = openPopup == ch;
			if (vis) {
				ZeroRender.text(g, ZeroFont.REGULAR, name, lx, y + 3.6f, 5.6f, label);
				String v = fit(ZeroFont.REGULAR, ch.get(), 5.6f, (x2 - x1) * 0.42f);
				ZeroRender.textRight(g, ZeroFont.REGULAR, v, rx - 7f, y + 3.6f, 5.6f, value);
				ZeroRender.icon(g, opened ? ZeroFont.Icon.CHEVRON_DOWN : ZeroFont.Icon.CHEVRON_RIGHT, rx - 2f, y + SROW / 2f, 4.6f, a(0xFFFFFFFF, 0.6f * al));
				clickIn(x1, y, x2, y + SROW, clipTop, clipBot, () -> {
					openPopup = opened ? null : ch;
				}, () -> {
					ch.cycle();
					QuiqzyConfig.save();
				});
			}
			y += SROW;
			if (opened) {
				float oy1 = y;
				List<String> opts = ch.options();
				float oh = opts.size() * 11.5f + 4f;
				if (y + oh > clipTop && y < clipBot) ZeroRender.rect(g, x1, y, x2 - 1f, y + oh, 4f, a(0xFF0C0C10, 0.75f * al));
				y += 2f;
				for (String o : opts) {
					boolean sel = o.equals(ch.get());
					boolean ohv = inBody && hot(x1, y, x2, y + 11.5f);
					if (y + 11.5f > clipTop && y < clipBot) {
						if (ohv) ZeroRender.rect(g, x1 + 2f, y, x2 - 3f, y + 11.5f, 3f, a(0xFFFFFFFF, 0.08f * al));
						if (sel) ZeroRender.rect(g, x1 + 2f, y + 2.5f, x1 + 3.2f, y + 9f, 0.6f, a(accent(), al));
						ZeroRender.text(g, sel ? ZeroFont.BOLD : ZeroFont.REGULAR, fit(ZeroFont.REGULAR, o, 5.5f, x2 - x1 - 18f), x1 + 6f, y + 3f, 5.5f,
								a(0xFFFFFFFF, (sel ? 0.95f : 0.7f) * al));
						if (sel) ZeroRender.icon(g, ZeroFont.Icon.CHECK, x2 - 8f, y + 5.75f, 5f, a(0xFFFFFFFF, 0.8f * al));
						final String opt = o;
						clickIn(x1, y, x2, y + 11.5f, clipTop, clipBot, () -> {
							ch.set(opt);
							openPopup = null;
							QuiqzyConfig.save();
						}, null);
					}
					y += 11.5f;
				}
				y = oy1 + oh + 1f;
			}
			return y;
		}
		if (s instanceof Setting.Color col) {
			boolean opened = openPopup == col;
			if (vis) {
				ZeroRender.text(g, ZeroFont.REGULAR, name, lx, y + 3.6f, 5.6f, label);
				ZeroRender.rect(g, rx - 11f, y + 3f, rx, y + SROW - 3f, 2f, a(0xFF000000 | col.get(), al));
				ZeroRender.stroke(g, rx - 11f, y + 3f, rx, y + SROW - 3f, 2f, 0.5f, a(0xFFFFFFFF, 0.3f * al));
				clickIn(x1, y, x2, y + SROW, clipTop, clipBot, () -> {
					openPopup = opened ? null : col;
				}, null);
			}
			y += SROW;
			if (opened) {
				int[] sw = {0xFFFFFF, 0xB8BCC6, 0x5B6070, 0x101014, 0xFF3B30, 0xFF8A1F, 0xFFD23D, 0x7CFF4D, 0x2EE59D, 0x4DE8FF, 0x3B82F6, 0x8B5CF6, 0xD946EF, 0xF472B6};
				int cols = 7;
				float cell = (x2 - x1 - 6f) / cols;
				float hy = y + 2f;
				// Farbverlauf-Leiste
				if (hy + 8f > clipTop && hy < clipBot) {
					int segs = 28;
					for (int i = 0; i < segs; i++) {
						float s1 = x1 + 2f + (x2 - x1 - 6f) * i / segs, s2 = x1 + 2f + (x2 - x1 - 6f) * (i + 1) / segs + 0.4f;
						ZeroRender.rect(g, s1, hy, s2, hy + 7f, 0f, a(0xFF000000 | net.minecraft.util.Mth.hsvToRgb(i / (float) segs, 0.8f, 1f), al));
					}
					final float bx1 = x1 + 2f, bx2 = x2 - 4f;
					clickIn(bx1, hy, bx2, hy + 7f, clipTop, clipBot, () -> {
						float t = Math.max(0f, Math.min(0.999f, (mx - bx1) / (bx2 - bx1)));
						col.set(net.minecraft.util.Mth.hsvToRgb(t, 0.8f, 1f) & 0xFFFFFF);
						QuiqzyConfig.save();
					}, null);
				}
				float gy = hy + 10f;
				for (int i = 0; i < sw.length; i++) {
					float cx = x1 + 2f + (i % cols) * cell + cell / 2f, cy = gy + (i / cols) * cell + cell / 2f;
					if (cy + cell / 2f > clipTop && cy - cell / 2f < clipBot) {
						if (col.get() == sw[i]) ZeroRender.circle(g, cx, cy, cell * 0.36f, a(0xFFFFFFFF, al));
						ZeroRender.circle(g, cx, cy, cell * 0.28f, a(0xFF000000 | sw[i], al));
						final int rgb = sw[i];
						clickIn(cx - cell / 2f, cy - cell / 2f, cx + cell / 2f, cy + cell / 2f, clipTop, clipBot, () -> {
							col.set(rgb);
							QuiqzyConfig.save();
						}, null);
					}
				}
				y = gy + ((sw.length + cols - 1) / cols) * cell + 2f;
			}
			return y;
		}
		// Listen -> Listen-Editor der großen GUI
		String sub = s instanceof Setting.Blocks b ? b.get().size() + " blocks" : s instanceof Setting.Items it ? it.get().size() + " items"
				: s instanceof Setting.Waypoints wp ? wp.get().size() + "" : s instanceof Setting.Strings st ? st.get().size() + "" : "";
		if (vis) {
			ZeroRender.text(g, ZeroFont.REGULAR, name, lx, y + 3.6f, 5.6f, label);
			ZeroRender.textRight(g, ZeroFont.REGULAR, sub + "  Edit", rx, y + 3.6f, 5.6f, value);
			clickIn(x1, y, x2, y + SROW, clipTop, clipBot, () -> {
				this.minecraft.setScreen(AuroraGui.editor(this, m, s));
			}, null);
		}
		return y + SROW;
	}

	private float keybindRow(GuiGraphics g, Module m, float x1, float y, float x2, float clipTop, float clipBot, boolean inBody, float al) {
		if (m == ModuleManager.CLICK_GUI) return y;
		if (y + SROW > clipTop && y < clipBot) {
			ZeroRender.text(g, ZeroFont.REGULAR, "Keybind", x1 + 3f, y + 3.6f, 5.6f, a(0xFFFFFFFF, 0.62f * al));
			String kb = listening == m ? "…" : keyName(m.keybind());
			float kw = w(ZeroFont.REGULAR, kb, 5.4f) + 8f;
			ZeroRender.rect(g, x2 - 4f - kw, y + 2.5f, x2 - 4f, y + SROW - 2.5f, 3f, a(0xFFFFFFFF, 0.12f * al));
			ZeroRender.textCentered(g, ZeroFont.REGULAR, kb, x2 - 4f - kw / 2f, y + 3.9f, 5.4f, a(0xFFFFFFFF, 0.85f * al));
			clickIn(x1, y, x2, y + SROW, clipTop, clipBot, () -> {
				listening = listening == m ? null : m;
			}, () -> {
				m.setKeybind(-1);
				listening = null;
				QuiqzyConfig.save();
			});
		}
		return y + SROW;
	}

	private void clickIn(float x1, float y1, float x2, float y2, float clipTop, float clipBot, Runnable left, Runnable right) {
		float c1 = Math.max(y1, clipTop), c2 = Math.min(y2, clipBot);
		if (c2 > c1) click(x1, c1, x2, c2, left, right);
	}

	private void footerUnused(GuiGraphics g, float al) {
		String line;
		if (!search.isEmpty()) line = "Search: " + search;
		else if (hoverDesc != null && !hoverDesc.isEmpty()) line = hoverDesc;
		else line = "Module: left = on/off, right = settings  ·  Header: left = fold, right-drag = move  ·  Type to search";
		float tw = w(ZeroFont.REGULAR, line, 6f) + 20f;
		float cx = vw / 2f, y = vh - 22f;
		ZeroRender.frost(g, cx - tw / 2f, y, cx + tw / 2f, y + 15f, 7.5f, a(0xFF1A1B20, 0.85f), al);
		ZeroRender.textCentered(g, ZeroFont.REGULAR, line, cx, y + 4.4f, 6f, a(0xFFFFFFFF, (search.isEmpty() ? 0.7f : 1f) * al));
	}

	private static char icon(Module.Category c) {
		return switch (c) {
			case COMBAT -> ZeroFont.Icon.SWORDS;
			case MOVEMENT -> ZeroFont.Icon.FOOTPRINTS;
			case VISUALS -> ZeroFont.Icon.EYE;
			case HUD -> ZeroFont.Icon.GRID;
			case PLAYER -> ZeroFont.Icon.USER;
			case WORLD -> ZeroFont.Icon.GLOBE;
			case MISC -> ZeroFont.Icon.SLIDERS;
			case TIERSMC -> ZeroFont.Icon.MAP_PIN;
			case CLIENT -> ZeroFont.Icon.SETTINGS;
		};
	}

	private static String keyName(int key) {
		if (key <= 0) return "None";
		try {
			return Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase(Locale.ROOT);
		} catch (Exception e) {
			return "KEY";
		}
	}


	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
		mx = (float) event.x() * k;
		my = (float) event.y() * k;
		boolean left = event.button() == 0, right = event.button() == 1;
		if (!left && !right) return super.mouseClicked(event, dbl);
		for (int i = regions.size() - 1; i >= 0; i--) {
			Region r = regions.get(i);
			if (mx >= r.x1 && mx < r.x2 && my >= r.y1 && my < r.y2) {
				Runnable act = left ? r.left : r.right;
				if (act != null) act.run();
				return true;
			}
		}
		openPopup = null;
		return super.mouseClicked(event, dbl);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		mx = (float) event.x() * k;
		my = (float) event.y() * k;
		if (dragSlider != null) {
			dragSlider.setProgress((mx - sliderX1) / (sliderX2 - sliderX1));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragSlider != null) {
			dragSlider = null;
			QuiqzyConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		scrollTarget -= (float) v * 30f;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (listening != null) {
			listening.setKeybind(key == 256 || key == 259 || key == 261 ? -1 : key);
			listening = null;
			QuiqzyConfig.save();
			return true;
		}
		if (key == 256) {
			if (!search.isEmpty()) { search = ""; return true; }
			if (openMod != null) { openMod = null; openPopup = null; return true; }
			onClose();
			return true;
		}
		if (key == 259 && !search.isEmpty()) {
			search = search.substring(0, search.length() - 1);
			return true;
		}
		if (search.isEmpty() && QuiqzyClient.OPEN_GUI.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (listening != null) return true;
		String c = event.codepointAsString();
		if (c.isEmpty() || Character.isISOControl(c.charAt(0))) return false;
		if (search.length() < 24) search += c;
		scroll = scrollTarget = 0f;
		return true;
	}

	@Override
	public void onClose() {
		if (parent != null && this.minecraft != null) this.minecraft.setScreen(parent);
		else super.onClose();
	}

	@Override
	public void removed() {
		ZeroBlur.release();
		QuiqzyConfig.save();
		super.removed();
	}

	// Selbsttest
	public void testCategory(Module.Category c) {
		cat = c;
		openMod = null;
	}

	public void testOpen(Module m) {
		openSettings(m);
	}

	public void testPopup(Setting<?> s) {
		openPopup = s;
	}
}
