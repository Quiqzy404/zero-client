package dev.quiqzy.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.platform.Window;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
import dev.quiqzy.client.util.Theme;
import dev.quiqzy.client.util.UiSounds;

/**
 * Neues ZERO-Menü (Aurora): Icon-Tabs oben, Module als Karten, Klick öffnet eine Detail-Karte von rechts,
 * eigene Themes-Seite. Alles in der Themenfarbe getönt. Komplexe Listen (Blöcke, Items, Wegpunkte ...)
 * öffnen den klassischen Editor.
 */
public final class AuroraGui extends Screen {
	private static final float W = 600f, H = 384f;
	private static final int TAB_THEMES = -1;
	private static final int TAB_CONFIGS = -2;
	private static final String[] PRESETS = {"Glass", "Violet", "CatLean", "Rose", "Crimson", "Ember", "Gold", "Mint", "Aqua", "Ocean", "Custom"};

	private static int tab = 0; // Index in Category.values(), -1 = Themes
	private static String search = "";
	private static final Map<Integer, Float> SCROLL = new HashMap<>();

	private float k, vw, vh, mx, my, dt, time, open, x0, y0;
	private long last = System.nanoTime();
	private final Map<Object, Float> anim = new HashMap<>();
	private final List<Region> regions = new ArrayList<>();
	private float scroll, scrollTarget, contentH, viewH;
	private float tabFade = 1f;

	private Module detail, closing;
	private float detailA, dScroll, dScrollTarget, dContentH;
	private Module listening;
	private Setting.Int dragSlider;
	private float sliderX1, sliderX2;
	private boolean searchFocus;
	private boolean modal;
	// Live Preview: bei Visuals-Modul-Settings Charakter in 3rd-Person zeigen
	private boolean previewing;
	private net.minecraft.client.CameraType savedCam;
	// Configs-Tab
	private String cfgName = "", cfgCode = "";
	private int cfgFocus; // 0 = keins, 1 = Name, 2 = Code
	private String cfgStatus = "";
	private boolean cfgLoading;
	private java.util.List<dev.quiqzy.client.config.ConfigCloud.Entry> publicCfgs;
	private float cScroll, cScrollTarget;

	private record Region(float x1, float y1, float x2, float y2, Runnable left, Runnable right) {
	}

	/** Eltern-Screen (z.B. Server-Auswahl), zu dem beim Schließen zurückgekehrt wird; null = ins Spiel. */
	private final Screen parent;

	public AuroraGui() {
		this(null);
	}

	public AuroraGui(Screen parent) {
		super(Component.literal("ZERO"));
		this.parent = parent;
	}

	/** Die neue GUI ist jetzt immer das Menü (alte GUI entfernt). */
	public static boolean enabled() {
		return true;
	}

	// ------------------------------------------------------------------ Basis

	private void scale() {
		Window w = this.minecraft.getWindow();
		float pixels = Math.max(1f, w.getHeight() / 540f);
		pixels = Math.min(pixels, Math.min(w.getWidth() / (W + 30f), w.getHeight() / (H + 30f)));
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

	private float anFrom(Object key, float start, float target, float speed) {
		float v = Draw.approach(anim.getOrDefault(key, start), target, speed, dt);
		anim.put(key, v);
		return v;
	}

	private boolean hot(float x1, float y1, float x2, float y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}

	private boolean hotFree(float x1, float y1, float x2, float y2) {
		return !modal && hot(x1, y1, x2, y2);
	}

	private void click(float x1, float y1, float x2, float y2, Runnable left) {
		regions.add(new Region(x1, y1, x2, y2, left, null));
	}

	private void click(float x1, float y1, float x2, float y2, Runnable left, Runnable right) {
		regions.add(new Region(x1, y1, x2, y2, left, right));
	}

	private static int a(int c, float f) {
		return ZeroRender.alpha(c, f);
	}

	private static float hue() {
		return Theme.hue();
	}

	private static int acc() {
		return AuroraHud.accent();
	}

	private static int acc2() {
		return AuroraHud.accent2();
	}

	private static int text() {
		return 0xFFF6F3FB;
	}

	private static int muted() {
		return Theme.hsv(hue(), 0.15f, 0.78f, 1f);
	}

	private static int faint() {
		return Theme.hsv(hue(), 0.2f, 0.55f, 1f);
	}

	private static float w(ZeroFont f, String s, float size) {
		return f.width(s, size, 0f);
	}

	private static String fit(ZeroFont f, String s, float size, float max) {
		if (w(f, s, size) <= max) return s;
		String t = s;
		while (t.length() > 1 && w(f, t + "…", size) > max) t = t.substring(0, t.length() - 1);
		return t.trim() + "…";
	}

	private static List<String> wrap(ZeroFont f, String s, float size, float max, int lines) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : s.split(" ")) {
			String test = cur.isEmpty() ? word : cur + " " + word;
			if (w(f, test, size) > max && !cur.isEmpty()) {
				out.add(cur.toString());
				cur = new StringBuilder(word);
				if (out.size() == lines) break;
			} else {
				cur = new StringBuilder(test);
			}
		}
		if (out.size() < lines && !cur.isEmpty()) out.add(cur.toString());
		if (out.size() == lines && out.size() > 0) {
			String joined = String.join(" ", out);
			if (joined.length() < s.length()) out.set(lines - 1, fit(f, out.get(lines - 1) + "…", size, max));
		}
		return out;
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

	// ------------------------------------------------------------------ Render

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - last) / 1.0e9f);
		last = now;
		time += dt;
		open = Draw.approach(open, 1f, 9f, dt);
		tabFade = Draw.approach(tabFade, 1f, 7f, dt);
		scale();
		mx = mouseX * k;
		my = mouseY * k;
		regions.clear();
		if (QuiqzyConfig.guiBlur) ZeroBlur.capture();
		float ease = 1f - (1f - open) * (1f - open) * (1f - open);

		// Live Preview: Settings eines Visuals-Moduls offen -> Charakter live in 3rd-Person zeigen
		boolean wantPreview = ModuleManager.ACCENT.livePreview.on() && closing != null && detailA > 0.4f
				&& closing.category() == Module.Category.VISUALS && editing == null;
		updatePreview(wantPreview);

		// Hintergrund: bei Preview mittel abdunkeln, damit das 3D-Modell links klar steht
		float dim = previewing ? 0.42f : (Theme.glass() ? 0.28f : 0.55f);
		ZeroRender.rect(g, 0, 0, width, height, 0, a(0xFF05040A, dim * ease));
		g.pose().pushMatrix();
		g.pose().scale(1f / k);
		if (!previewing) ZeroRender.radial(g, vw / 2f - 520f, vh / 2f - 380f, vw / 2f + 520f, vh / 2f + 380f, 380f, Theme.hsv(hue(), 0.7f, 0.45f, 0.20f * ease), 0x00000000);

		// bei Preview Fenster nach rechts, damit der Charakter links sichtbar ist
		// Live Preview: Fenster schmaler + rechtsbündig, damit der Charakter links frei sichtbar ist
		float winW = an("winW", previewing ? 430f : W, 12f);
		float targetX0 = previewing ? vw - winW - 18f : (vw - winW) / 2f;
		x0 = an("winx", targetX0, 12f);
		y0 = (vh - H) / 2f + (1f - ease) * 14f;
		float x1 = x0 + winW, y1 = y0 + H;
		g.pose().pushMatrix();
		float pop = 0.97f + 0.03f * ease;
		g.pose().translate(vw / 2f, vh / 2f);
		g.pose().scale(pop);
		g.pose().translate(-vw / 2f, -vh / 2f);

		modal = detail != null;
		ZeroRender.noStroke = Theme.glass(); // Glass: keine Kanten im Menü
		try {
		window(g, x0, y0, x1, y1, ease);
		topBar(g, x0, y0, x1, ease);
		// Settings-Seite statt Overlay: Raster blendet aus, Seite blendet ein
		detailA = Draw.approach(detailA, detail != null ? 1f : 0f, 11f, dt);
		if (detail != null) closing = detail;
		if (detail == null) editing = null;
		if (closing != null && detailA < 0.01f && detail == null) closing = null;
		float dA = closing == null ? 0f : 1f - (1f - detailA) * (1f - detailA) * (1f - detailA);
		if (tab == TAB_THEMES && search.isEmpty() && closing == null) themes(g, x0, y0 + 46f, x1, y1 - 26f, ease);
		else if (tab == TAB_CONFIGS && search.isEmpty() && closing == null) configs(g, x0, y0 + 46f, x1, y1 - 26f, ease);
		else if (dA < 0.99f) modules(g, x0, y0 + 46f, x1, y1 - 26f, ease * (1f - dA));
		if (closing != null) detailPage(g, closing, x0, y0 + 46f, x1, y1 - 26f, ease * dA);
		footer(g, x0, y1, x1, ease);
		} finally {
			ZeroRender.noStroke = false;
		}

		g.pose().popMatrix();
		g.pose().popMatrix();

		renderPreviewModel(g, mouseX, mouseY);
	}

	private void window(GuiGraphics g, float x1, float y1, float x2, float y2, float al) {
		if (Theme.glass()) {
			ZeroRender.glow(g, x1, y1 + 6f, x2, y2 + 10f, 22f, 30f, a(0xFF000000, 0.30f * al));
			ZeroRender.frost(g, x1, y1, x2, y2, 22f, 0xC81C1D21, al);
			ZeroRender.strokeGradient(g, x1, y1, x2, y2, 22f, 1f, a(0xFFFFFFFF, 0.45f * al), a(0xFFFFFFFF, 0.05f * al), 60f);
			ZeroRender.strokeGradient(g, x1, y1, x2, y2, 22f, 0.7f, 0x00FFFFFF, a(0xFFFFFFFF, 0.14f * al), 60f);
			return;
		}
		float h = hue();
		ZeroRender.glow(g, x1, y1 + 6f, x2, y2 + 10f, 18f, 30f, a(0xFF000000, 0.55f * al));
		ZeroRender.glow(g, x1, y1, x2, y2, 18f, 22f, Theme.hsv(h, 0.7f, 0.9f, 0.10f * al));
		ZeroRender.glass(g, x1, y1, x2, y2, 18f, Theme.hsv(h, 0.55f, 0.17f, 0.88f), Theme.hsv(h + 0.03f, 0.6f, 0.07f, 0.90f), 75f, al);
		// weiche Farbwolke oben links
		ZeroRender.radial(g, x1 - 120f, y1 - 140f, x1 + 260f, y1 + 180f, 190f, Theme.hsv(h, 0.75f, 0.85f, 0.13f * al), 0x00000000);
		ZeroRender.strokeGradient(g, x1, y1, x2, y2, 18f, 0.8f, a(acc(), 0.55f * al), a(acc(), 0.08f * al), 90f);
	}

	private List<Integer> tabs() {
		List<Integer> t = new ArrayList<>();
		for (int i = 0; i < Module.Category.values().length; i++) t.add(i);
		t.add(TAB_THEMES);
		t.add(TAB_CONFIGS);
		return t;
	}

	private static String tabLabel(int t) {
		return t == TAB_THEMES ? "Themes" : t == TAB_CONFIGS ? "Configs" : Module.Category.values()[t].label;
	}

	private static char tabIcon(int t) {
		return t == TAB_THEMES ? ZeroFont.Icon.PALETTE : t == TAB_CONFIGS ? ZeroFont.Icon.PACKAGE : icon(Module.Category.values()[t]);
	}

	private void topBar(GuiGraphics g, float x1, float y, float x2, float al) {
		// Logo
		ZeroRender.text(g, ZeroFont.LOGO, "ZERO", x1 + 18f, y + 12f, 17f, a(text(), al));
		ZeroRender.circle(g, x1 + 18f + w(ZeroFont.LOGO, "ZERO", 17f) + 4f, y + 26f, 1.8f, a(acc(), al));

		// Tabs (Pillen, aktive mit Text)
		List<Integer> list = tabs();
		float s = 7.2f, gap = 4f;
		float[] ws = new float[list.size()];
		float total = 0f;
		for (int i = 0; i < list.size(); i++) {
			int t = list.get(i);
			boolean on = t == tab && search.isEmpty();
			String label = tabLabel(t);
			float target = on ? 26f + w(ZeroFont.BOLD, label, s) + 8f : 24f;
			ws[i] = an("tabw" + t, target, 16f);
			total += ws[i] + gap;
		}
		total -= gap;
		float tx = (x1 + x2) / 2f - total / 2f - 30f, ty = y + 11f, th = 23f;
		ZeroRender.rect(g, tx - 4f, ty - 4f, tx + total + 4f, ty + th + 4f, 15f, a(0xFF000000, 0.22f * al));
		ZeroRender.stroke(g, tx - 4f, ty - 4f, tx + total + 4f, ty + th + 4f, 15f, 0.5f, a(0xFFFFFFFF, 0.06f * al));
		for (int i = 0; i < list.size(); i++) {
			int t = list.get(i);
			boolean on = t == tab && search.isEmpty();
			float bx = tx, bw = ws[i];
			boolean hv = hotFree(bx, ty, bx + bw, ty + th);
			float onA = an("tabon" + t, on ? 1f : 0f, 12f);
			float hvA = an("tabhv" + t, hv ? 1f : 0f, 16f);
			if (onA > 0.01f && Theme.glass()) {
				AuroraHud.lens(g, bx, ty, bx + bw, ty + th, 11.5f, onA * al);
			} else if (onA > 0.01f) {
				ZeroRender.glow(g, bx, ty, bx + bw, ty + th, 11.5f, 8f, a(acc(), 0.45f * onA * al));
				ZeroRender.gradient(g, bx, ty, bx + bw, ty + th, 11.5f, a(acc(), onA * al), a(Theme.hsv(hue() + 0.03f, 0.75f, 0.75f, 1f), onA * al), 0f);
			}
			if (hvA > 0.01f && !on) ZeroRender.rect(g, bx, ty, bx + bw, ty + th, 11.5f, a(0xFFFFFFFF, 0.08f * hvA * al));
			char ic = tabIcon(t);
			ZeroRender.icon(g, ic, bx + 12f, ty + th / 2f, 9f, a(ZeroRender.lerp(muted(), text(), Math.max(onA, hvA)), al));
			if (onA > 0.3f) {
				String label = tabLabel(t);
				ZeroRender.text(g, ZeroFont.BOLD, label, bx + 23f, ty + 7.6f, s, a(text(), (onA - 0.3f) / 0.7f * al));
			}
			click(bx, ty, bx + bw, ty + th, () -> {
				if (tab != t || !search.isEmpty()) {
					tab = t;
					search = "";
					searchFocus = false;
					scroll = scrollTarget = SCROLL.getOrDefault(t, 0f);
					tabFade = 0f;
					UiSounds.click();
				}
			});
			tx += bw + gap;
		}

		// Suche
		float sx2 = x2 - 16f, sx1 = sx2 - 132f, sy1 = y + 12f, sy2 = sy1 + 21f;
		boolean hv = hotFree(sx1, sy1, sx2, sy2);
		float fA = an("sfocus", searchFocus ? 1f : 0f, 14f);
		ZeroRender.rect(g, sx1, sy1, sx2, sy2, 10.5f, a(0xFF000000, (0.25f + 0.1f * fA) * al));
		ZeroRender.stroke(g, sx1, sy1, sx2, sy2, 10.5f, 0.6f, a(ZeroRender.lerp(a(0xFFFFFFFF, hv ? 0.16f : 0.08f), acc(), fA), al));
		ZeroRender.icon(g, ZeroFont.Icon.SEARCH, sx1 + 11f, (sy1 + sy2) / 2f, 8f, a(searchFocus ? acc() : muted(), al));
		String shown = search.isEmpty() && !searchFocus ? "Search modules" : search + (searchFocus && (time % 1f) < 0.55f ? "|" : "");
		ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, shown, 7f, sx2 - sx1 - 30f), sx1 + 21f, sy1 + 6.6f, 7f,
				a(search.isEmpty() && !searchFocus ? faint() : text(), al));
		click(sx1, sy1, sx2, sy2, () -> searchFocus = true);
		ZeroRender.rect(g, x1 + 16f, y + 44f, x2 - 16f, y + 44.5f, 0f, a(0xFFFFFFFF, 0.06f * al));
	}

	private void footer(GuiGraphics g, float x1, float y2, float x2, float al) {
		float y = y2 - 19f;
		String hint = editing != null ? "Click to add / remove  ·  Type to search  ·  Esc to go back"
				: closing != null ? "Esc to go back  ·  Drag sliders  ·  Click a key chip to rebind"
				: "Click to toggle  ·  Right-click for settings  ·  Esc to close";
		ZeroRender.text(g, ZeroFont.REGULAR, hint, x1 + 18f, y, 6.2f, a(faint(), al));
	}

	// ------------------------------------------------------------------ Modul-Karten

	private List<Module> visibleModules() {
		List<Module> out = new ArrayList<>();
		String q = search.trim().toLowerCase(Locale.ROOT);
		for (Module m : ModuleManager.all()) {
			if (!q.isEmpty()) {
				if (m.name().toLowerCase(Locale.ROOT).contains(q) || m.description().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
			} else if (tab >= 0 && m.category() == Module.Category.values()[tab]) {
				out.add(m);
			}
		}
		return out;
	}

	private void modules(GuiGraphics g, float x1, float y1, float x2, float y2, float al) {
		List<Module> list = visibleModules();
		String title = !search.isEmpty() ? "Search" : Module.Category.values()[Math.max(0, tab)].label;
		int on = 0;
		for (Module m : list) if (m.isEnabled()) on++;
		ZeroRender.text(g, ZeroFont.BOLD, title, x1 + 18f, y1 + 6f, 13f, a(text(), al));
		ZeroRender.text(g, ZeroFont.REGULAR, list.size() + " modules  ·  " + on + " enabled", x1 + 18f, y1 + 22f, 6.6f, a(muted(), al));

		float gy1 = y1 + 36f, gx1 = x1 + 16f, gx2 = x2 - 16f;
		int cols = 3;
		float gap = 9f, cw = (gx2 - gx1 - gap * (cols - 1)) / cols, ch = 66f;
		int rows = (list.size() + cols - 1) / cols;
		contentH = rows * (ch + gap);
		viewH = y2 - gy1;
		scrollTarget = Math.max(0f, Math.min(scrollTarget, Math.max(0f, contentH - viewH + 4f)));
		scroll = Draw.approach(scroll, scrollTarget, 16f, dt);
		SCROLL.put(search.isEmpty() ? tab : 99, scrollTarget);
		if (list.isEmpty()) {
			ZeroRender.textCentered(g, ZeroFont.REGULAR, "Nothing found", (x1 + x2) / 2f, gy1 + 40f, 8f, a(faint(), al));
			return;
		}
		g.enableScissor(Math.round(x1), Math.round(gy1 - 4f), Math.round(x2), Math.round(y2));
		for (int i = 0; i < list.size(); i++) {
			Module m = list.get(i);
			int col = i % cols, row = i / cols;
			float stagger = Math.max(0f, Math.min(1f, tabFade * 1.6f - Math.min(i, 12) * 0.05f));
			float cx = gx1 + col * (cw + gap), cy = gy1 + row * (ch + gap) - scroll + (1f - stagger) * 10f;
			if (cy > y2 || cy + ch < gy1 - 4f) continue;
			card(g, m, cx, cy, cx + cw, cy + ch, al * stagger, gy1 - 4f, y2);
		}
		g.disableScissor();
		// Scrollbalken
		if (contentH > viewH) {
			float frac = viewH / contentH, pos = scroll / (contentH - viewH);
			float th = Math.max(20f, viewH * frac), ty = gy1 + (viewH - th) * pos;
			ZeroRender.rect(g, x2 - 9f, ty, x2 - 7f, ty + th, 1f, a(acc(), 0.5f * al));
		}
	}

	private void card(GuiGraphics g, Module m, float x1, float y1, float x2, float y2, float al, float clipTop, float clipBottom) {
		boolean inView = my >= clipTop && my < clipBottom;
		boolean hv = inView && hotFree(x1, y1, x2, y2);
		float hA = an("chv" + m.id(), hv ? 1f : 0f, 14f);
		float oA = an("con" + m.id(), m.isEnabled() ? 1f : 0f, 10f);
		float lift = hA * 1.5f;
		y1 -= lift;
		y2 -= lift;
		if (oA > 0.01f) ZeroRender.glow(g, x1, y1, x2, y2, 11f, 10f, a(acc(), 0.22f * oA * al));
		ZeroRender.rect(g, x1, y1, x2, y2, 11f, a(0xFFFFFFFF, (0.035f + 0.04f * hA) * al));
		if (oA > 0.01f) ZeroRender.gradient(g, x1, y1, x2, y2, 11f, a(acc(), 0.20f * oA * al), a(acc(), 0.03f * oA * al), 120f);
		ZeroRender.strokeGradient(g, x1, y1, x2, y2, 11f, 0.7f,
				a(ZeroRender.lerp(a(0xFFFFFFFF, 0.10f + 0.08f * hA), acc(), oA * 0.85f), al), a(0xFFFFFFFF, 0.03f * al), 90f);
		// Icon-Chip
		ZeroRender.rect(g, x1 + 9f, y1 + 9f, x1 + 27f, y1 + 27f, 6f, a(ZeroRender.lerp(0x18FFFFFF, a(acc(), 0.3f), oA), al));
		ZeroRender.icon(g, icon(m.category()), x1 + 18f, y1 + 18f, 9f, a(ZeroRender.lerp(muted(), acc2(), oA), al));
		// Schalter
		float sx2 = x2 - 10f, sx1 = sx2 - 21f, sy = y1 + 18f;
		toggle(g, sx1, sy, oA, al);
		// Text
		ZeroRender.text(g, ZeroFont.BOLD, fit(ZeroFont.BOLD, m.name(), 8.2f, x2 - x1 - 18f), x1 + 10f, y1 + 33f, 8.2f, a(text(), al));
		List<String> d = wrap(ZeroFont.REGULAR, m.description(), 6f, x2 - x1 - 20f, 2);
		for (int i = 0; i < d.size(); i++) ZeroRender.text(g, ZeroFont.REGULAR, d.get(i), x1 + 10f, y1 + 45f + i * 8f, 6f, a(muted(), al));
		if (m.keybind() > 0) {
			String kn = keyName(m.keybind());
			float kw = w(ZeroFont.BOLD, kn, 5.4f) + 8f;
			ZeroRender.rect(g, x2 - 30f - kw, y1 + 12.5f, x2 - 30f, y1 + 23.5f, 3.5f, a(0xFFFFFFFF, 0.09f * al));
			ZeroRender.textCentered(g, ZeroFont.BOLD, kn, x2 - 30f - kw / 2f, y1 + 15.4f, 5.4f, a(muted(), al));
		}
		if (!inView || al < 0.3f) return;
		float cy1 = Math.max(y1, clipTop), cy2 = Math.min(y2, clipBottom);
		if (cy2 <= cy1) return;
		Runnable flip = () -> {
			m.toggle();
			QuiqzyConfig.save();
		};
		click(sx1 - 4f, Math.max(sy - 9f, cy1), sx2 + 4f, Math.min(sy + 9f, cy2), flip, () -> openDetail(m));
		click(x1, cy1, x2, cy2, flip, () -> openDetail(m));
	}

	private void toggle(GuiGraphics g, float x1, float cy, float on, float al) {
		if (Theme.glass()) {
			AuroraHud.toggleSwitch(g, x1, cy, 21f, 11.5f, on, al);
			return;
		}
		float tw = 21f, th = 11.5f;
		ZeroRender.rect(g, x1, cy - th / 2f, x1 + tw, cy + th / 2f, th / 2f, a(ZeroRender.lerp(0x2EFFFFFF, acc(), on), al));
		if (on > 0.05f) ZeroRender.glow(g, x1, cy - th / 2f, x1 + tw, cy + th / 2f, th / 2f, 5f, a(acc(), 0.4f * on * al));
		float kx = x1 + th / 2f + (tw - th) * on;
		ZeroRender.circle(g, kx, cy, th / 2f - 1.6f, a(0xFFFFFFFF, al));
	}

	private void openDetail(Module m) {
		detail = m;
		dScroll = dScrollTarget = 0f;
		listening = null;
		UiSounds.click();
	}

	// ------------------------------------------------------------------ Detail-Karte

	/**
	 * Settings-Seite eines Moduls: ersetzt das Karten-Raster (weiche Überblendung), kein Overlay.
	 * Kopf: Zurück, Icon, Name, Beschreibung, rechts Reset / Keybind / Schalter. Darunter Settings in zwei Spalten.
	 */
	private void detailPage(GuiGraphics g, Module m, float x1, float y1, float x2, float y2, float e) {
		float oy = (1f - e) * 12f;
		y1 += oy;
		float oA = an("con" + m.id(), m.isEnabled() ? 1f : 0f, 10f);
		boolean prevModal = modal;
		modal = false;

		// Zurück
		String back = editing != null ? m.name() : m.category().label;
		float bw = w(ZeroFont.BOLD, back, 6.6f) + 18f;
		boolean bh = hot(x1 + 14f, y1 + 2f, x1 + 14f + bw, y1 + 16f);
		ZeroRender.icon(g, ZeroFont.Icon.CHEVRON_LEFT, x1 + 20f, y1 + 9f, 7f, a(bh ? text() : muted(), e));
		ZeroRender.text(g, ZeroFont.BOLD, back, x1 + 27f, y1 + 5.6f, 6.6f, a(bh ? text() : muted(), e));
		click(x1 + 14f, y1 + 2f, x1 + 14f + bw, y1 + 16f, () -> {
			if (editing != null) editing = null;
			else detail = null;
			UiSounds.click();
		});

		// Kopf
		float hy = y1 + 22f;
		ZeroRender.rect(g, x1 + 18f, hy, x1 + 48f, hy + 30f, 9f, a(0xFFFFFFFF, 0.08f + 0.06f * oA * e));
		ZeroRender.icon(g, icon(m.category()), x1 + 33f, hy + 15f, 13f, a(ZeroRender.lerp(muted(), text(), oA), e));
		float rightW = 160f;
		ZeroRender.text(g, ZeroFont.BOLD, fit(ZeroFont.BOLD, m.name(), 14f, x2 - x1 - 90f - rightW), x1 + 58f, hy + 2f, 14f, a(text(), e));
		ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, m.description(), 6.6f, x2 - x1 - 90f - rightW), x1 + 58f, hy + 20f, 6.6f, a(muted(), e));

		// rechts: Schalter, Keybind, Reset
		float cy = hy + 15f;
		float tx = x2 - 18f - 21f;
		toggle(g, tx, cy, oA, e);
		click(tx - 4f, cy - 9f, tx + 25f, cy + 9f, () -> {
			m.toggle();
			QuiqzyConfig.save();
		});
		boolean lis = listening == m;
		String kn = lis ? "Press a key" : (m.keybind() > 0 ? keyName(m.keybind()) : "Bind");
		float kw = w(ZeroFont.BOLD, kn, 6.2f) + 22f;
		float kx2 = tx - 10f, kx1 = kx2 - kw;
		boolean kh = hot(kx1, cy - 8f, kx2, cy + 8f);
		ZeroRender.rect(g, kx1, cy - 8f, kx2, cy + 8f, 8f, a(0xFFFFFFFF, (lis ? 0.22f : kh ? 0.12f : 0.07f) * e));
		ZeroRender.icon(g, ZeroFont.Icon.KEYBOARD, kx1 + 9f, cy, 6.5f, a(muted(), e));
		ZeroRender.text(g, ZeroFont.BOLD, kn, kx1 + 16f, cy - 3.2f, 6.2f, a(text(), e));
		click(kx1, cy - 8f, kx2, cy + 8f, () -> {
			listening = listening == m ? null : m;
			UiSounds.click();
		});
		float rx2 = kx1 - 6f, rx1 = rx2 - 16f;
		boolean rh = hot(rx1, cy - 8f, rx2, cy + 8f);
		ZeroRender.rect(g, rx1, cy - 8f, rx2, cy + 8f, 8f, a(0xFFFFFFFF, (rh ? 0.12f : 0.07f) * e));
		ZeroRender.icon(g, ZeroFont.Icon.RESET, (rx1 + rx2) / 2f, cy, 6.5f, a(rh ? text() : muted(), e));
		click(rx1, cy - 8f, rx2, cy + 8f, () -> {
			for (Setting<?> s : m.settings()) s.reset();
			QuiqzyConfig.save();
			UiSounds.removed();
		});

		// Settings in zwei Spalten (oder Listen-Editor)
		float top = hy + 42f, bottom = y2;
		if (editing != null) {
			listPage(g, m, editing, x1, top, x2, bottom, e);
			modal = prevModal;
			return;
		}
		float gx1 = x1 + 16f, gx2 = x2 - 16f, gap = 10f;
		int cols = (x2 - x1) < 500f ? 1 : 2; // schmales Preview-Fenster -> eine Spalte
		float colW = cols == 1 ? gx2 - gx1 : (gx2 - gx1 - gap) / 2f;
		dScrollTarget = Math.max(0f, Math.min(dScrollTarget, Math.max(0f, dContentH - (bottom - top))));
		dScroll = Draw.approach(dScroll, dScrollTarget, 16f, dt);
		g.enableScissor(Math.round(x1), Math.round(top - 2f), Math.round(x2), Math.round(bottom));
		float[] cyCol = {top - dScroll, top - dScroll};
		if (m.settings().isEmpty()) {
			ZeroRender.textCentered(g, ZeroFont.REGULAR, "This module has no settings", (x1 + x2) / 2f, top + 30f, 7f, a(faint(), e));
		}
		for (Setting<?> s : m.settings()) {
			if (s instanceof Setting.ColorMap) continue;
			int c = cols == 1 ? 0 : (cyCol[0] <= cyCol[1] ? 0 : 1);
			float cx1 = gx1 + c * (colW + gap);
			cyCol[c] = setting(g, m, s, cx1, cyCol[c], cx1 + colW, e, top - 2f, bottom) + 4f;
		}
		dContentH = Math.max(cyCol[0], cyCol[1]) + dScroll - top + 4f;
		g.disableScissor();
		// Scrollbalken
		if (dContentH > bottom - top) {
			float vh2 = bottom - top, frac = vh2 / dContentH, pos = dScroll / (dContentH - vh2);
			float th = Math.max(20f, vh2 * frac), ty = top + (vh2 - th) * pos;
			ZeroRender.rect(g, x2 - 9f, ty, x2 - 7f, ty + th, 1f, a(0xFFFFFFFF, 0.25f * e));
		}
		modal = prevModal;
	}

	// ------------------------------------------------------------------ Listen-Editoren (ersetzen den alten Editor)

	private Setting<?> editing;
	private String listQuery = "";
	private float lScroll, lScrollTarget, lContentH;

	private record Entry(String id, String name, net.minecraft.world.item.ItemStack stack) {
	}

	private static List<Entry> BLOCK_ENTRIES, ITEM_ENTRIES;

	private static List<Entry> entries(boolean blocks) {
		if (blocks && BLOCK_ENTRIES != null) return BLOCK_ENTRIES;
		if (!blocks && ITEM_ENTRIES != null) return ITEM_ENTRIES;
		List<Entry> out = new ArrayList<>();
		if (blocks) {
			for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
				var item = block.asItem();
				if (item == net.minecraft.world.item.Items.AIR) continue;
				out.add(new Entry(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString(),
						new net.minecraft.world.item.ItemStack(item)));
			}
			BLOCK_ENTRIES = out;
		} else {
			for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
				if (item == net.minecraft.world.item.Items.AIR) continue;
				var st = new net.minecraft.world.item.ItemStack(item);
				out.add(new Entry(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(), st.getHoverName().getString(), st));
			}
			ITEM_ENTRIES = out;
		}
		return out;
	}

	private void openEditor(Setting<?> s) {
		editing = s;
		listQuery = "";
		lScroll = lScrollTarget = 0f;
		UiSounds.click();
	}

	private void item(GuiGraphics g, net.minecraft.world.item.ItemStack st, float x, float y, float size) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 16f, size / 16f);
		g.renderItem(st, 0, 0);
		g.pose().popMatrix();
	}

	private void pill(GuiGraphics g, String label, char ic, float x2, float y, float al, Runnable act) {
		float bw = w(ZeroFont.BOLD, label, 6.2f) + 24f, x1 = x2 - bw;
		boolean hv = hot(x1, y, x2, y + 16f);
		ZeroRender.rect(g, x1, y, x2, y + 16f, 8f, a(0xFFFFFFFF, (hv ? 0.16f : 0.08f) * al));
		ZeroRender.icon(g, ic, x1 + 10f, y + 8f, 6.5f, a(hv ? text() : muted(), al));
		ZeroRender.text(g, ZeroFont.BOLD, label, x1 + 17f, y + 4.8f, 6.2f, a(text(), al));
		click(x1, y, x2, y + 16f, act);
	}

	/** Gibt die linke Kante der Knopf-Reihe zurück (für Platz). */
	private float waypointButtons(GuiGraphics g, Setting.Waypoints wp, float x2, float y, float al) {
		var bmb = ModuleManager.BONE_MEAL_BOT;
		var sb = ModuleManager.SPAWNER_BOT;
		float x = x2;
		if (wp == bmb.spots) {
			pill(g, "Mark block", ZeroFont.Icon.CROSSHAIR, x, y, al, () -> { bmb.markLooked(this.minecraft); QuiqzyConfig.save(); UiSounds.added(); });
			x -= w(ZeroFont.BOLD, "Mark block", 6.2f) + 30f;
			pill(g, "Start", ZeroFont.Icon.PLAY, x, y, al, () -> { bmb.start(); UiSounds.click(); });
			x -= w(ZeroFont.BOLD, "Start", 6.2f) + 30f;
		} else if (wp == bmb.chest) {
			pill(g, "Set chest", ZeroFont.Icon.CROSSHAIR, x, y, al, () -> { bmb.setChestLooked(this.minecraft); QuiqzyConfig.save(); UiSounds.added(); });
			x -= w(ZeroFont.BOLD, "Set chest", 6.2f) + 30f;
		} else if (wp == sb.spawners) {
			pill(g, "Mark spawner", ZeroFont.Icon.CROSSHAIR, x, y, al, () -> { sb.markLooked(this.minecraft); QuiqzyConfig.save(); UiSounds.added(); });
			x -= w(ZeroFont.BOLD, "Mark spawner", 6.2f) + 30f;
			pill(g, "Run now", ZeroFont.Icon.PLAY, x, y, al, () -> { sb.runNow(); UiSounds.click(); });
			x -= w(ZeroFont.BOLD, "Run now", 6.2f) + 30f;
			pill(g, "Mark all", ZeroFont.Icon.PLUS, x, y, al, () -> { sb.markAll(this.minecraft, true); QuiqzyConfig.save(); UiSounds.added(); });
			x -= w(ZeroFont.BOLD, "Mark all", 6.2f) + 30f;
		} else {
			pill(g, "Add here", ZeroFont.Icon.MAP_PIN, x, y, al, () -> { ModuleManager.WAYPOINTS.addHere(this.minecraft, ""); QuiqzyConfig.save(); UiSounds.added(); });
			x -= w(ZeroFont.BOLD, "Add here", 6.2f) + 30f;
		}
		return x;
	}

	/** Editor-Inhalt unter dem Modul-Kopf. */
	private void listPage(GuiGraphics g, Module m, Setting<?> s, float x1, float top, float x2, float bottom, float al) {
		boolean blocks = s instanceof Setting.Blocks, items = s instanceof Setting.Items;
		boolean strings = s instanceof Setting.Strings;
		float gx1 = x1 + 16f, gx2 = x2 - 16f;
		// Titelzeile: Name der Liste + Eingabe / Knöpfe
		ZeroRender.text(g, ZeroFont.BOLD, s.name(), gx1, top + 3f, 9f, a(text(), al));
		float fieldX2 = gx2;
		if (s instanceof Setting.Waypoints wp) {
			String st = wp == ModuleManager.SPAWNER_BOT.spawners ? ModuleManager.SPAWNER_BOT.status
					: wp == ModuleManager.BONE_MEAL_BOT.spots ? ModuleManager.BONE_MEAL_BOT.status : wp.get().size() + " entries";
			ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, st, 6.2f, 200f), gx1 + w(ZeroFont.BOLD, s.name(), 9f) + 8f, top + 5f, 6.2f, a(muted(), al));
			waypointButtons(g, wp, gx2, top, al);
		} else {
			// Suchfeld / Eingabe (einfach tippen)
			float fx1 = gx2 - 180f;
			ZeroRender.rect(g, fx1, top, fieldX2, top + 16f, 8f, a(0xFFFFFFFF, 0.08f * al));
			ZeroRender.icon(g, strings ? ZeroFont.Icon.PLUS : ZeroFont.Icon.SEARCH, fx1 + 10f, top + 8f, 6.5f, a(muted(), al));
			String ph = strings ? "Type or paste (Ctrl+V), Enter to add" : "Type to search";
			String tail = listQuery.length() > 36 ? "…" + listQuery.substring(listQuery.length() - 36) : listQuery;
			String shown = listQuery.isEmpty() ? ph : tail + ((time % 1f) < 0.55f ? "|" : "");
			ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, shown, 6.4f, 150f), fx1 + 18f, top + 4.6f, 6.4f,
					a(listQuery.isEmpty() ? faint() : text(), al));
		}

		float ctop = top + 24f;
		lScrollTarget = Math.max(0f, Math.min(lScrollTarget, Math.max(0f, lContentH - (bottom - ctop))));
		lScroll = Draw.approach(lScroll, lScrollTarget, 16f, dt);
		g.enableScissor(Math.round(x1), Math.round(ctop - 2f), Math.round(x2), Math.round(bottom));
		float y = ctop - lScroll;
		boolean inView = my >= ctop && my < bottom;

		if (blocks || items) {
			List<String> sel = blocks ? ((Setting.Blocks) s).get() : ((Setting.Items) s).get();
			List<Entry> all = entries(blocks);
			float tile = 26f, gap = 5f;
			int cols = Math.max(1, (int) ((gx2 - gx1 + gap) / (tile + gap)));
			// Ausgewählte
			ZeroRender.text(g, ZeroFont.BOLD, "SELECTED  " + sel.size(), gx1, y, 5.8f, a(faint(), al));
			y += 11f;
			int i = 0;
			for (String id : new ArrayList<>(sel)) {
				Entry en = null;
				for (Entry e2 : all) if (e2.id().equals(id)) { en = e2; break; }
				float tx = gx1 + (i % cols) * (tile + gap), ty = y + (i / cols) * (tile + gap);
				boolean hv = inView && hot(tx, ty, tx + tile, ty + tile);
				ZeroRender.rect(g, tx, ty, tx + tile, ty + tile, 7f, a(0xFFFFFFFF, (hv ? 0.20f : 0.12f) * al));
				if (en != null) item(g, en.stack(), tx + 5f, ty + 5f, 16f);
				if (hv) {
					ZeroRender.rect(g, tx, ty, tx + tile, ty + tile, 7f, a(0xFF000000, 0.45f * al));
					ZeroRender.icon(g, ZeroFont.Icon.X, tx + tile / 2f, ty + tile / 2f, 9f, a(text(), al));
				}
				if (inView) click(tx, ty, tx + tile, ty + tile, () -> {
					if (blocks) ((Setting.Blocks) s).remove(id); else ((Setting.Items) s).remove(id);
					QuiqzyConfig.save();
					UiSounds.removed();
				});
				i++;
			}
			if (sel.isEmpty()) {
				ZeroRender.text(g, ZeroFont.REGULAR, "Nothing selected yet - click something below to add it", gx1, y + 2f, 6.4f, a(faint(), al));
				y += 16f;
			} else {
				y += ((sel.size() + cols - 1) / cols) * (tile + gap) + 4f;
			}
			// Hinzufügen
			String q = listQuery.trim().toLowerCase(Locale.ROOT);
			List<Entry> matches = new ArrayList<>();
			for (Entry en : all) {
				if (sel.contains(en.id())) continue;
				if (!q.isEmpty() && !en.name().toLowerCase(Locale.ROOT).contains(q) && !en.id().contains(q)) continue;
				matches.add(en);
				if (matches.size() >= 240) break;
			}
			ZeroRender.text(g, ZeroFont.BOLD, q.isEmpty() ? "ADD" : "RESULTS", gx1, y + 4f, 5.8f, a(faint(), al));
			y += 15f;
			for (int k = 0; k < matches.size(); k++) {
				Entry en = matches.get(k);
				float tx = gx1 + (k % cols) * (tile + gap), ty = y + (k / cols) * (tile + gap);
				if (ty > bottom || ty + tile < ctop - 2f) continue;
				boolean hv = inView && hot(tx, ty, tx + tile, ty + tile);
				ZeroRender.rect(g, tx, ty, tx + tile, ty + tile, 7f, a(0xFFFFFFFF, (hv ? 0.14f : 0.05f) * al));
				item(g, en.stack(), tx + 5f, ty + 5f, 16f);
				if (hv) {
					String nm = en.name();
					float nw = w(ZeroFont.BOLD, nm, 6f) + 12f;
					float nx = Math.min(tx, gx2 - nw);
					ZeroRender.rect(g, nx, ty - 14f, nx + nw, ty - 2f, 6f, a(0xF0202125, al));
					ZeroRender.text(g, ZeroFont.BOLD, nm, nx + 6f, ty - 11f, 6f, a(text(), al));
				}
				if (inView) click(tx, ty, tx + tile, ty + tile, () -> {
					if (blocks) ((Setting.Blocks) s).add(en.id()); else ((Setting.Items) s).add(en.id());
					QuiqzyConfig.save();
					UiSounds.added();
				});
			}
			y += ((matches.size() + cols - 1) / cols) * (tile + gap);
		} else if (strings) {
			Setting.Strings st = (Setting.Strings) s;
			List<String> list = st.get();
			if (list.isEmpty()) {
				ZeroRender.text(g, ZeroFont.REGULAR, "No entries yet - type a name and press Enter", gx1, y + 4f, 6.6f, a(faint(), al));
				y += 18f;
			}
			for (int i = 0; i < list.size(); i++) {
				String v = list.get(i);
				int idx = i;
				boolean hv = inView && hot(gx1, y, gx2, y + 20f);
				ZeroRender.rect(g, gx1, y, gx2, y + 18f, 8f, a(0xFFFFFFFF, (hv ? 0.09f : 0.05f) * al));
				ZeroRender.text(g, ZeroFont.BOLD, v, gx1 + 10f, y + 5.5f, 7f, a(text(), al));
				boolean th = inView && hot(gx2 - 20f, y, gx2, y + 18f);
				ZeroRender.icon(g, ZeroFont.Icon.TRASH, gx2 - 10f, y + 9f, 7.5f, a(th ? 0xFFFF6B6B : muted(), al));
				if (inView) click(gx2 - 20f, y, gx2, y + 18f, () -> {
					List<String> l = new ArrayList<>(st.get());
					if (idx < l.size()) l.remove(idx);
					st.set(l);
					QuiqzyConfig.save();
					UiSounds.removed();
				});
				y += 21f;
			}
		} else if (s instanceof Setting.Waypoints wp) {
			List<Setting.Waypoints.Point> pts = wp.get();
			String here = this.minecraft.level != null ? dev.quiqzy.client.module.world.WaypointsModule.dimensionId(this.minecraft.level) : "";
			if (pts.isEmpty()) {
				ZeroRender.text(g, ZeroFont.REGULAR, "No entries yet - use the buttons above", gx1, y + 4f, 6.6f, a(faint(), al));
				y += 18f;
			}
			for (int i = 0; i < pts.size(); i++) {
				var p = pts.get(i);
				int idx = i;
				int rgb = dev.quiqzy.client.module.world.WaypointsModule.colorOf(p.name());
				boolean hv = inView && hot(gx1, y, gx2, y + 20f);
				ZeroRender.rect(g, gx1, y, gx2, y + 18f, 8f, a(0xFFFFFFFF, (hv ? 0.09f : 0.05f) * al));
				ZeroRender.noTint = true;
				ZeroRender.circle(g, gx1 + 10f, y + 9f, 3f, a(0xFF000000 | rgb, al));
				ZeroRender.noTint = false;
				ZeroRender.text(g, ZeroFont.BOLD, fit(ZeroFont.BOLD, p.name(), 7f, (gx2 - gx1) * 0.4f), gx1 + 19f, y + 5.5f, 7f, a(text(), al));
				String info;
				if (this.minecraft.player != null && p.dim().equals(here)) {
					double dx = p.x() + 0.5 - this.minecraft.player.getX(), dy = p.y() - this.minecraft.player.getY(), dz = p.z() + 0.5 - this.minecraft.player.getZ();
					info = (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + " m  ·  " + p.x() + ", " + p.y() + ", " + p.z();
				} else {
					info = p.dim() + "  ·  " + p.x() + ", " + p.y() + ", " + p.z();
				}
				ZeroRender.textRight(g, ZeroFont.REGULAR, info, gx2 - 26f, y + 5.8f, 6.4f, a(muted(), al));
				boolean th = inView && hot(gx2 - 20f, y, gx2, y + 18f);
				ZeroRender.icon(g, ZeroFont.Icon.TRASH, gx2 - 10f, y + 9f, 7.5f, a(th ? 0xFFFF6B6B : muted(), al));
				if (inView) click(gx2 - 20f, y, gx2, y + 18f, () -> {
					wp.remove(idx);
					QuiqzyConfig.save();
					UiSounds.removed();
				});
				y += 21f;
			}
		}
		lContentH = y + lScroll - ctop + 6f;
		g.disableScissor();
	}

	private interface Draw2 {
		void draw(float y);
	}

	private float row(GuiGraphics g, float x1, float y, float x2, float h, float al, float top, float bottom, String label, String sub, Draw2 right, Runnable act) {
		boolean vis = y + h > top && y < bottom;
		if (vis) {
			boolean hv = my >= top && my < bottom && hot(x1, y, x2, y + h);
			float hA = an("row" + label + System.identityHashCode(right.getClass()), hv ? 1f : 0f, 16f);
			ZeroRender.rect(g, x1, y, x2, y + h - 3f, 8f, a(0xFFFFFFFF, (0.035f + 0.035f * hA) * al));
			ZeroRender.text(g, ZeroFont.BOLD, label, x1 + 9f, y + (sub == null ? 8f : 5f), 7.2f, a(text(), al));
			if (sub != null) ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, sub, 5.8f, x2 - x1 - 110f), x1 + 9f, y + 14.5f, 5.8f, a(faint(), al));
			right.draw(y);
			if (act != null) {
				float c1 = Math.max(y, top), c2 = Math.min(y + h - 3f, bottom);
				if (c2 > c1) click(x1, c1, x2, c2, act);
			}
		}
		return y + h;
	}

	private float setting(GuiGraphics g, Module m, Setting<?> s, float x1, float y, float x2, float al, float top, float bottom) {
		if (s instanceof Setting.Bool b) {
			float on = an("sb" + m.id() + b.id(), b.on() ? 1f : 0f, 12f);
			return row(g, x1, y, x2, 24f, al, top, bottom, b.name(), null, (yy) -> toggle(g, x2 - 30f, yy + 10.5f, on, al), () -> {
				b.toggle();
				QuiqzyConfig.save();
				UiSounds.toggle(b.on());
			});
		}
		if (s instanceof Setting.Int in) {
			float h = 32f;
			if (y + h > top && y < bottom) {
				ZeroRender.rect(g, x1, y, x2, y + h - 3f, 8f, a(0xFFFFFFFF, 0.035f * al));
				ZeroRender.text(g, ZeroFont.BOLD, in.name(), x1 + 9f, y + 5f, 7.2f, a(text(), al));
				String val = in.get() + in.unit();
				float vw2 = w(ZeroFont.BOLD, val, 6.4f) + 12f;
				ZeroRender.rect(g, x2 - 8f - vw2, y + 3.5f, x2 - 8f, y + 14.5f, 5f, a(acc(), 0.22f * al));
				ZeroRender.textCentered(g, ZeroFont.BOLD, val, x2 - 8f - vw2 / 2f, y + 6.2f, 6.4f, a(text(), al));
				float sx1 = x1 + 10f, sx2 = x2 - 10f, sy = y + 21.5f;
				float p = an("si" + m.id() + in.id(), in.progress(), 18f);
				ZeroRender.rect(g, sx1, sy - 1.5f, sx2, sy + 1.5f, 1.5f, a(0xFFFFFFFF, 0.10f * al));
				ZeroRender.gradient(g, sx1, sy - 1.5f, sx1 + (sx2 - sx1) * p, sy + 1.5f, 1.5f, a(acc(), al), a(acc2(), al), 0f);
				float kx = sx1 + (sx2 - sx1) * p;
				boolean dragging = dragSlider == in;
				ZeroRender.glow(g, kx - 5f, sy - 5f, kx + 5f, sy + 5f, 5f, 5f, a(acc(), (dragging ? 0.7f : 0.35f) * al));
				ZeroRender.circle(g, kx, sy, dragging ? 4.4f : 3.8f, a(0xFFFFFFFF, al));
				float c1 = Math.max(sy - 7f, top), c2 = Math.min(sy + 7f, bottom);
				if (c2 > c1) click(sx1 - 4f, c1, sx2 + 4f, c2, () -> {
					dragSlider = in;
					sliderX1 = sx1;
					sliderX2 = sx2;
					in.setProgress((mx - sx1) / (sx2 - sx1));
				});
			}
			return y + h;
		}
		if (s instanceof Setting.Choice c) {
			List<String> opts = c.options();
			float pillsW = 0f;
			for (String o : opts) pillsW += w(ZeroFont.BOLD, o, 6.2f) + 14f + 4f;
			boolean pills = opts.size() <= 6;
			// Zeilen für Pillen berechnen
			float avail = x2 - x1 - 18f;
			int lines = 1;
			float lx = 0f;
			for (String o : opts) {
				float ow = w(ZeroFont.BOLD, o, 6.2f) + 14f;
				if (lx + ow > avail && lx > 0) {
					lines++;
					lx = 0;
				}
				lx += ow + 4f;
			}
			if (lines > 2) pills = false;
			float h = pills ? 22f + lines * 15f : 26f;
			if (y + h > top && y < bottom) {
				ZeroRender.rect(g, x1, y, x2, y + h - 3f, 8f, a(0xFFFFFFFF, 0.035f * al));
				ZeroRender.text(g, ZeroFont.BOLD, c.name(), x1 + 9f, y + (pills ? 5f : 8f), 7.2f, a(text(), al));
				if (pills) {
					float px = x1 + 9f, py = y + 17f;
					for (String o : opts) {
						float ow = w(ZeroFont.BOLD, o, 6.2f) + 14f;
						if (px + ow > x1 + 9f + avail && px > x1 + 9f) {
							px = x1 + 9f;
							py += 15f;
						}
						boolean sel = o.equals(c.get());
						float sA = an("ch" + m.id() + c.id() + o, sel ? 1f : 0f, 14f);
						boolean hv = my >= top && my < bottom && hot(px, py, px + ow, py + 12f);
						if (Theme.glass()) {
							ZeroRender.rect(g, px, py, px + ow, py + 12f, 6f, a(0xFFFFFFFF, (hv ? 0.11f : 0.06f) * al));
							AuroraHud.lens(g, px, py, px + ow, py + 12f, 6f, sA * al);
						} else {
							ZeroRender.rect(g, px, py, px + ow, py + 12f, 6f, a(ZeroRender.lerp(a(0xFFFFFFFF, hv ? 0.11f : 0.06f), acc(), sA), al));
						}
						ZeroRender.textCentered(g, ZeroFont.BOLD, o, px + ow / 2f, py + 2.8f, 6.2f, a(ZeroRender.lerp(muted(), 0xFFFFFFFF, Math.max(sA, hv ? 0.5f : 0f)), al));
						float c1 = Math.max(py, top), c2 = Math.min(py + 12f, bottom);
						if (c2 > c1) click(px, c1, px + ow, c2, () -> {
							c.set(o);
							QuiqzyConfig.save();
							UiSounds.click();
						});
						px += ow + 4f;
					}
				} else {
					String v = c.get();
					float vw2 = w(ZeroFont.BOLD, v, 6.4f) + 24f;
					float bx1 = x2 - 8f - vw2, bx2 = x2 - 8f;
					boolean hv = my >= top && my < bottom && hot(bx1, y + 5f, bx2, y + 19f);
					ZeroRender.rect(g, bx1, y + 5f, bx2, y + 19f, 7f, a(acc(), (hv ? 0.4f : 0.25f) * al));
					ZeroRender.text(g, ZeroFont.BOLD, v, bx1 + 8f, y + 8.6f, 6.4f, a(text(), al));
					ZeroRender.icon(g, ZeroFont.Icon.CHEVRON_RIGHT, bx2 - 7f, y + 12f, 6f, a(text(), al));
					float c1 = Math.max(y, top), c2 = Math.min(y + h - 3f, bottom);
					if (c2 > c1) click(x1, c1, x2, c2, () -> {
						c.cycle();
						QuiqzyConfig.save();
						UiSounds.click();
					}, () -> {
						int i = opts.indexOf(c.get());
						c.set(opts.get((i - 1 + opts.size()) % opts.size()));
						QuiqzyConfig.save();
						UiSounds.click();
					});
				}
			}
			return y + h;
		}
		if (s instanceof Setting.Color col) {
			int[] sw = {0xFFFFFF, 0xC4B5FD, 0xA78BFA, 0xF472B6, 0xFB7185, 0xF97316, 0xFACC15, 0x4ADE80, 0x22D3EE, 0x60A5FA};
			return row(g, x1, y, x2, 26f, al, top, bottom, col.name(), col.hex().startsWith("#") ? col.hex() : "#" + col.hex(), (yy) -> {
				float cx = x2 - 14f;
				for (int i = sw.length - 1; i >= 0; i--) {
					int rgb = sw[i];
					boolean sel = col.get() == rgb;
					if (sel) ZeroRender.rect(g, cx - 5.5f, yy + 6f, cx + 5.5f, yy + 17f, 5.5f, a(0x59FFFFFF, al));
					if (sel) ZeroRender.stroke(g, cx - 5.5f, yy + 6f, cx + 5.5f, yy + 17f, 5.5f, 0.8f, a(0xFFFFFFFF, al));
					ZeroRender.noTint = true; // Farbfelder immer in echter Farbe
					ZeroRender.circle(g, cx, yy + 11.5f, 3.8f, a(0xFF000000 | rgb, al));
					ZeroRender.noTint = false;
					float c1 = Math.max(yy + 5f, top), c2 = Math.min(yy + 18f, bottom);
					if (c2 > c1) click(cx - 5.5f, c1, cx + 5.5f, c2, () -> {
						col.set(rgb);
						QuiqzyConfig.save();
						UiSounds.click();
					});
					cx -= 12f;
				}
			}, null);
		}
		// Listen & Spezialfälle -> klassischer Editor
		String what = s instanceof Setting.Blocks b ? b.get().size() + " blocks" : s instanceof Setting.Items it ? it.get().size() + " items"
				: s instanceof Setting.Waypoints wp ? wp.get().size() + " entries" : s instanceof Setting.Strings st ? st.get().size() + " entries" : null;
		if (s instanceof Setting.ColorMap) return y;
		String sub = what != null ? what : "Advanced editor";
		return row(g, x1, y, x2, 26f, al, top, bottom, s.name(), sub, (yy) -> {
			float bw = w(ZeroFont.BOLD, "Edit", 6.4f) + 22f;
			ZeroRender.rect(g, x2 - 8f - bw, yy + 6.5f, x2 - 8f, yy + 19.5f, 6.5f, a(acc(), 0.25f * al));
			ZeroRender.text(g, ZeroFont.BOLD, "Edit", x2 - 8f - bw + 8f, yy + 9.8f, 6.4f, a(text(), al));
			ZeroRender.icon(g, ZeroFont.Icon.CHEVRON_RIGHT, x2 - 15f, yy + 13f, 6f, a(text(), al));
		}, () -> {
			openEditor(s);
		});
	}

	// ------------------------------------------------------------------ Themes

	// ------------------------------------------------------------------ Configs (lokal + Cloud)

	private String cfgAuthor() {
		String n = this.minecraft.getUser() != null ? this.minecraft.getUser().getName() : "player";
		n = n.replaceAll("[^A-Za-z0-9_]", "");
		if (n.isEmpty()) n = "player";
		return n.length() > 16 ? n.substring(0, 16) : n;
	}

	private void cfgField(GuiGraphics g, float x1, float y, float x2, int which, String text, String placeholder, float al) {
		boolean focus = cfgFocus == which;
		boolean hv = hotFree(x1, y, x2, y + 16f);
		ZeroRender.rect(g, x1, y, x2, y + 16f, 8f, a(0xFF000000, (focus ? 0.32f : 0.22f) * al));
		ZeroRender.stroke(g, x1, y, x2, y + 16f, 8f, 0.6f, a(focus ? acc() : a(0xFFFFFFFF, hv ? 0.16f : 0.08f), al));
		String shown = text.isEmpty() && !focus ? placeholder : text + (focus && (time % 1f) < 0.55f ? "|" : "");
		ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, shown, 6.6f, x2 - x1 - 14f), x1 + 8f, y + 4.8f, 6.6f,
				a(text.isEmpty() && !focus ? faint() : text(), al));
		click(x1, y, x2, y + 16f, () -> cfgFocus = which);
	}

	private void loadPublic() {
		cfgLoading = true;
		cfgStatus = "Loading…";
		dev.quiqzy.client.config.ConfigCloud.list("", "new", list -> {
			publicCfgs = list;
			cfgLoading = false;
			cfgStatus = list.isEmpty() ? "No public configs yet – upload one!" : list.size() + " public configs";
		}, err -> {
			cfgLoading = false;
			publicCfgs = new ArrayList<>();
			cfgStatus = "Server: " + err;
		});
	}

	private void getByKey(String key) {
		if (key == null || key.isBlank()) return;
		cfgStatus = "Fetching…";
		dev.quiqzy.client.config.ConfigCloud.fetch(key.trim(), o -> {
			try {
				com.google.gson.JsonObject data = o.getAsJsonObject("data");
				String nm = o.has("name") ? o.get("name").getAsString() : "Downloaded";
				QuiqzyConfig.saveNamedFromJson(nm, data);
				QuiqzyConfig.applyJsonPublic(data);
				cfgStatus = "Loaded & applied: " + nm;
				UiSounds.added();
			} catch (Exception e) {
				cfgStatus = "Bad config data";
			}
		}, err -> cfgStatus = "Not found / " + err);
	}

	private void configs(GuiGraphics g, float x1, float y1, float x2, float y2, float al) {
		if (publicCfgs == null && !cfgLoading) loadPublic();
		ZeroRender.text(g, ZeroFont.BOLD, "Configs", x1 + 18f, y1 + 6f, 13f, a(text(), al));
		ZeroRender.text(g, ZeroFont.REGULAR, "Save setups, share them, or load configs from others", x1 + 18f, y1 + 22f, 6.6f, a(muted(), al));

		float gx1 = x1 + 16f, gx2 = x2 - 16f, colW = (gx2 - gx1 - 14f) / 2f;
		float lx2 = gx1 + colW, rx1 = gx2 - colW;
		float top = y1 + 38f;

		// ---------------- Meine Configs (links)
		ZeroRender.icon(g, ZeroFont.Icon.PACKAGE, gx1 + 4f, top + 4f, 7f, a(acc2(), al));
		ZeroRender.text(g, ZeroFont.BOLD, "MY CONFIGS", gx1 + 12f, top + 1f, 6f, a(acc2(), al));
		float ly = top + 14f;
		cfgField(g, gx1, ly, lx2 - 46f, 1, cfgName, "Name", al);
		pill(g, "Save", ZeroFont.Icon.DOWNLOAD, lx2, ly, al, () -> {
			if (!cfgName.isBlank()) {
				QuiqzyConfig.saveNamed(cfgName);
				cfgStatus = "Saved: " + cfgName;
				cfgName = "";
				cfgFocus = 0;
				UiSounds.added();
			}
		});
		ly += 22f;
		java.util.List<String> local = QuiqzyConfig.listNamed();
		if (local.isEmpty()) {
			ZeroRender.text(g, ZeroFont.REGULAR, "No saved configs yet", gx1, ly + 2f, 6.4f, a(faint(), al));
		}
		for (String name : local) {
			boolean hv = hotFree(gx1, ly, lx2, ly + 17f);
			ZeroRender.rect(g, gx1, ly, lx2, ly + 17f, 7f, a(0xFFFFFFFF, (hv ? 0.09f : 0.05f) * al));
			ZeroRender.text(g, ZeroFont.BOLD, fit(ZeroFont.BOLD, name, 7f, colW - 56f), gx1 + 8f, ly + 5f, 7f, a(text(), al));
			float ix = lx2 - 10f;
			boolean dh = hotFree(ix - 6f, ly, ix + 6f, ly + 17f);
			ZeroRender.icon(g, ZeroFont.Icon.TRASH, ix, ly + 8.5f, 7f, a(dh ? 0xFFFF6B6B : muted(), al));
			click(ix - 7f, ly, ix + 7f, ly + 17f, () -> { QuiqzyConfig.deleteNamed(name); UiSounds.removed(); });
			ix -= 18f;
			boolean uh = hotFree(ix - 6f, ly, ix + 7f, ly + 17f);
			ZeroRender.icon(g, ZeroFont.Icon.UPLOAD, ix, ly + 8.5f, 7f, a(uh ? text() : muted(), al));
			click(ix - 7f, ly, ix + 8f, ly + 17f, () -> {
				var data = QuiqzyConfig.namedJson(name);
				if (data == null) { cfgStatus = "Config missing"; return; }
				cfgStatus = "Uploading…";
				dev.quiqzy.client.config.ConfigCloud.upload(name, cfgAuthor(), data,
						code -> { cfgStatus = "Uploaded! Share code: " + code; publicCfgs = null; UiSounds.added(); },
						err -> cfgStatus = "Upload failed: " + err);
			});
			ix -= 18f;
			ZeroRender.text(g, ZeroFont.REGULAR, "load", ix - w(ZeroFont.REGULAR, "load", 5.8f), ly + 5.6f, 5.8f, a(hotFree(ix - 24f, ly, ix, ly + 17f) ? acc2() : faint(), al));
			click(gx1, ly, ix - 2f, ly + 17f, () -> { QuiqzyConfig.loadNamed(name); cfgStatus = "Loaded: " + name; UiSounds.click(); });
			ly += 20f;
		}

		// ---------------- Public Configs (rechts)
		ZeroRender.icon(g, ZeroFont.Icon.GLOBE, rx1 + 4f, top + 4f, 7f, a(acc2(), al));
		ZeroRender.text(g, ZeroFont.BOLD, "PUBLIC CONFIGS", rx1 + 12f, top + 1f, 6f, a(acc2(), al));
		boolean rh = hotFree(gx2 - 14f, top - 2f, gx2, top + 12f);
		ZeroRender.icon(g, ZeroFont.Icon.RESET, gx2 - 7f, top + 4f, 6.5f, a(rh ? text() : muted(), al));
		click(gx2 - 14f, top - 2f, gx2, top + 12f, () -> { publicCfgs = null; UiSounds.click(); });
		float ry = top + 14f;
		cfgField(g, rx1, ry, gx2 - 42f, 2, cfgCode, "Code", al);
		pill(g, "Get", ZeroFont.Icon.DOWNLOAD, gx2, ry, al, () -> { getByKey(cfgCode); cfgCode = ""; cfgFocus = 0; });
		ry += 22f;
		float listTop = ry, listBot = y2 - 14f;
		cScrollTarget = Math.max(0f, cScrollTarget);
		cScroll = Draw.approach(cScroll, cScrollTarget, 16f, dt);
		g.enableScissor(Math.round(rx1), Math.round(listTop - 2f), Math.round(gx2), Math.round(listBot));
		float yy = ry - cScroll;
		java.util.List<dev.quiqzy.client.config.ConfigCloud.Entry> pub = publicCfgs;
		if (pub != null) {
			for (var e : pub) {
				if (yy + 22f > listTop - 2f && yy < listBot) {
					boolean hv = my >= listTop && my < listBot && hotFree(rx1, yy, gx2, yy + 22f);
					ZeroRender.rect(g, rx1, yy, gx2, yy + 22f, 7f, a(0xFFFFFFFF, (hv ? 0.09f : 0.05f) * al));
					ZeroRender.text(g, ZeroFont.BOLD, fit(ZeroFont.BOLD, e.name(), 7f, colW - 52f), rx1 + 8f, yy + 3.5f, 7f, a(text(), al));
					ZeroRender.text(g, ZeroFont.REGULAR, "by " + e.author() + "  ·  " + e.downloads() + " ", rx1 + 8f, yy + 12.5f, 5.6f, a(muted(), al));
					boolean gh = my >= listTop && my < listBot && hotFree(gx2 - 34f, yy, gx2 - 6f, yy + 22f);
					ZeroRender.rect(g, gx2 - 36f, yy + 5f, gx2 - 6f, yy + 17f, 6f, a(acc(), (gh ? 0.4f : 0.22f) * al));
					ZeroRender.textCentered(g, ZeroFont.BOLD, "Get", gx2 - 21f, yy + 7.6f, 6.2f, a(text(), al));
					if (my >= listTop && my < listBot) click(gx2 - 36f, yy + 5f, gx2 - 6f, yy + 17f, () -> getByKey(e.code()));
					// eigenen Upload loeschen
					if (dev.quiqzy.client.config.ConfigCloud.owns(e.id())) {
						float dx = gx2 - 44f;
						boolean dh = my >= listTop && my < listBot && hotFree(dx - 7f, yy, dx + 7f, yy + 22f);
						ZeroRender.icon(g, ZeroFont.Icon.TRASH, dx, yy + 11f, 7f, a(dh ? 0xFFFF6B6B : muted(), al));
						if (my >= listTop && my < listBot) click(dx - 7f, yy, dx + 7f, yy + 22f, () -> {
							cfgStatus = "Deleting…";
							dev.quiqzy.client.config.ConfigCloud.delete(e.id(), () -> { cfgStatus = "Deleted"; publicCfgs = null; UiSounds.removed(); }, err -> cfgStatus = "Delete failed: " + err);
						});
					}
				}
				yy += 24f;
			}
			cScrollTarget = Math.min(cScrollTarget, Math.max(0f, (pub.size() * 24f) - (listBot - listTop)));
		} else {
			ZeroRender.text(g, ZeroFont.REGULAR, "Loading…", rx1, ry + 4f, 6.6f, a(faint(), al));
		}
		g.disableScissor();

		// Status unten
		if (!cfgStatus.isEmpty()) ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, cfgStatus, 6f, x2 - x1 - 36f), gx1, y2 - 9f, 6f, a(acc2(), al));
	}

	private void themes(GuiGraphics g, float x1, float y1, float x2, float y2, float al) {
		ZeroRender.text(g, ZeroFont.BOLD, "Themes", x1 + 18f, y1 + 6f, 13f, a(text(), al));
		ZeroRender.text(g, ZeroFont.REGULAR, "Glass = colourless Liquid Glass, or pick one colour for menu, HUD and effects", x1 + 18f, y1 + 22f, 6.6f, a(muted(), al));
		var acc = ModuleManager.ACCENT;
		float gx1 = x1 + 16f, gx2 = x2 - 16f, gy = y1 + 38f;
		int cols = 6;
		float gap = 8f, cw = (gx2 - gx1 - gap * (cols - 1)) / cols, ch = 64f;
		for (int i = 0; i < PRESETS.length; i++) {
			String p = PRESETS[i];
			int col = i % cols, row = i / cols;
			float stagger = Math.max(0f, Math.min(1f, tabFade * 1.6f - Math.min(i, 12) * 0.05f));
			float cx1 = gx1 + col * (cw + gap), cy1 = gy + row * (ch + gap) + (1f - stagger) * 8f, cx2 = cx1 + cw, cy2 = cy1 + ch;
			float h = acc.hueFor(p);
			if (h < 0f) h = 0.075f;
			boolean gl = p.equals("Glass");
			boolean cat = p.equals("CatLean") || gl;
			boolean sel = acc.isEnabled() && acc.preset.get().equals(p);
			boolean hv = hotFree(cx1, cy1, cx2, cy2);
			float hA = an("th" + p, hv ? 1f : 0f, 14f), sA = an("ts" + p, sel ? 1f : 0f, 12f);
			float a2 = al * stagger;
			ZeroRender.noTint = true;
			int base = gl ? 0xFFFFFFFF : cat ? 0xFF9AA0AE : Theme.hsv(h, 0.58f, 1f, 1f);
			if (sA > 0.01f) ZeroRender.glow(g, cx1, cy1, cx2, cy2, 11f, 10f, a(base, 0.35f * sA * a2));
			// Mini-Vorschau
			if (gl) {
				ZeroRender.gradient(g, cx1, cy1, cx2, cy2, 11f, a(0xFF3A3D45, a2), a(0xFF15161A, a2), 90f);
				ZeroRender.gradient(g, cx1 + 6f, cy1 + 6f, cx2 - 6f, cy1 + 36f, 8f, a(0xFFFFFFFF, 0.22f * a2), a(0xFFFFFFFF, 0.06f * a2), 90f);
				ZeroRender.strokeGradient(g, cx1 + 6f, cy1 + 6f, cx2 - 6f, cy1 + 36f, 8f, 0.7f, a(0xFFFFFFFF, 0.8f * a2), a(0xFFFFFFFF, 0.1f * a2), 60f);
			} else
			ZeroRender.gradient(g, cx1, cy1, cx2, cy2, 11f, cat ? a(0xFF2A2C33, a2) : Theme.hsv(h, 0.55f, 0.22f, a2), cat ? a(0xFF141519, a2) : Theme.hsv(h + 0.03f, 0.6f, 0.08f, a2), 90f);
			if (!gl) ZeroRender.radial(g, cx1 - 30f, cy1 - 30f, cx1 + 60f, cy1 + 50f, 45f, a(base, 0.30f * a2), 0x00000000);
			ZeroRender.rect(g, cx1 + 9f, cy1 + 10f, cx1 + 9f + cw * 0.45f, cy1 + 13f, 1.5f, a(0xFFFFFFFF, 0.55f * a2));
			ZeroRender.rect(g, cx1 + 9f, cy1 + 17f, cx1 + 9f + cw * 0.3f, cy1 + 19.5f, 1.2f, a(0xFFFFFFFF, 0.22f * a2));
			ZeroRender.rect(g, cx2 - 22f, cy1 + 9f, cx2 - 9f, cy1 + 16f, 3.5f, a(base, a2));
			ZeroRender.circle(g, cx2 - 12.5f, cy1 + 12.5f, 2.4f, a(0xFFFFFFFF, a2));
			ZeroRender.gradient(g, cx1 + 9f, cy1 + 26f, cx2 - 9f, cy1 + 30f, 2f, a(base, a2), cat ? a(0xFFE6E8EE, a2) : Theme.hsv(h + 0.04f, 0.35f, 1f, a2), 0f);
			ZeroRender.strokeGradient(g, cx1, cy1, cx2, cy2, 11f, sel ? 1f : 0.6f, a(ZeroRender.lerp(a(0xFFFFFFFF, 0.12f + 0.12f * hA), base, sA), a2), a(0xFFFFFFFF, 0.04f * a2), 90f);
			ZeroRender.text(g, ZeroFont.BOLD, p, cx1 + 9f, cy2 - 17f, 7.4f, a(text(), a2));
			if (sel) {
				ZeroRender.circle(g, cx2 - 13f, cy2 - 12.5f, 5f, a(base, a2));
				ZeroRender.icon(g, ZeroFont.Icon.CHECK, cx2 - 13f, cy2 - 12.5f, 6f, a(0xFF101014, a2));
			}
			ZeroRender.noTint = false;
			click(cx1, cy1, cx2, cy2, () -> {
				if (!acc.isEnabled()) acc.setEnabled(true);
				acc.preset.set(p);
				QuiqzyConfig.save();
				UiSounds.click();
			});
		}
		// Darunter: HUD-Stil, Animation, Größe, eigene Farbe
		float ry = gy + 2 * (ch + gap) + 6f;
		float rx1 = gx1, rx2 = gx2;
		boolean prev = modal;
		Module m = acc;
		float colW = (rx2 - rx1 - 10f) / 2f;
		float ly = ry, ry2 = ry;
		ly = setting(g, m, acc.hudStyle, rx1, ly, rx1 + colW, al, y1, y2 + 30f);
		ly = setting(g, m, acc.hudScale, rx1, ly, rx1 + colW, al, y1, y2 + 30f);
		ry2 = setting(g, m, acc.animated, rx1 + colW + 10f, ry2, rx2, al, y1, y2 + 30f);
		ry2 = setting(g, m, acc.custom, rx1 + colW + 10f, ry2, rx2, al, y1, y2 + 30f);
		ry2 = setting(g, m, acc.hudBlur, rx1 + colW + 10f, ry2, rx2, al, y1, y2 + 30f);
		ly = setting(g, m, acc.worldTint, rx1, ly, rx1 + colW, al, y1, y2 + 30f);
		float by = Math.max(ly, ry2);
		// Blur-Schalter (GUI-Option)
		float on = an("blur", QuiqzyConfig.guiBlur ? 1f : 0f, 12f);
		row(g, rx1 + colW + 10f, by - 1f, rx2, 24f, al, y1, y2 + 30f, "Background Blur", null, (yy) -> toggle(g, rx2 - 30f, yy + 10.5f, on, al), () -> {
			QuiqzyConfig.guiBlur = !QuiqzyConfig.guiBlur;
			QuiqzyConfig.save();
			UiSounds.toggle(QuiqzyConfig.guiBlur);
		});
		modal = prev;
	}

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
		mx = (float) event.x() * k;
		my = (float) event.y() * k;
		boolean left = event.button() == 0, right = event.button() == 1;
		if (!left && !right) return super.mouseClicked(event, dbl);
		boolean wasSearch = searchFocus;
		searchFocus = false;
		for (int i = regions.size() - 1; i >= 0; i--) {
			Region r = regions.get(i);
			if (mx >= r.x1 && mx < r.x2 && my >= r.y1 && my < r.y2) {
				Runnable act = left ? r.left : r.right;
				if (act != null) {
					act.run();
					return true;
				}
				if (!left) return true;
			}
		}
		if (wasSearch && !search.isEmpty()) searchFocus = true;
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
			UiSounds.click();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		if (editing != null) lScrollTarget -= (float) v * 30f;
		else if (detail != null) dScrollTarget -= (float) v * 28f;
		else if (tab == TAB_CONFIGS) cScrollTarget = Math.max(0f, cScrollTarget - (float) v * 30f);
		else scrollTarget -= (float) v * 36f;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (listening != null) {
			listening.setKeybind(key == 256 || key == 259 || key == 261 ? -1 : key);
			listening = null;
			QuiqzyConfig.save();
			UiSounds.click();
			return true;
		}
		if (event.isPaste()) {
			String clip = this.minecraft != null ? this.minecraft.keyboardHandler.getClipboard() : null;
			if (clip != null && !clip.isEmpty()) {
				if (editing instanceof Setting.Strings) {
					String v = clip.replaceAll("[\\r\\n\\t]", " ").trim();
					listQuery = (listQuery + v);
					if (listQuery.length() > 256) listQuery = listQuery.substring(0, 256);
					UiSounds.click();
					return true;
				}
				if (editing != null && !(editing instanceof Setting.Waypoints)) {
					listQuery += clip.replaceAll("\\s+", " ").trim();
					if (listQuery.length() > 256) listQuery = listQuery.substring(0, 256);
					lScrollTarget = 0f;
					return true;
				}
				if (tab == TAB_CONFIGS && cfgFocus == 1) { cfgName = (cfgName + clip.replaceAll("[^A-Za-z0-9 _-]", "")); if (cfgName.length() > 32) cfgName = cfgName.substring(0, 32); return true; }
				if (tab == TAB_CONFIGS && cfgFocus == 2) { cfgCode = (cfgCode + clip.replaceAll("[^A-Za-z0-9]", "")); if (cfgCode.length() > 12) cfgCode = cfgCode.substring(0, 12); return true; }
				if (detail == null && listening == null) { search += clip.replaceAll("\\s+", " ").trim(); if (search.length() > 24) search = search.substring(0, 24); searchFocus = true; scrollTarget = 0f; return true; }
			}
			return true;
		}
		if (editing != null) {
			boolean strings = editing instanceof Setting.Strings;
			if (key == 256) {
				editing = null;
				UiSounds.click();
				return true;
			}
			if (key == 259 && !listQuery.isEmpty()) {
				listQuery = listQuery.substring(0, listQuery.length() - 1);
				lScrollTarget = 0f;
				return true;
			}
			if ((key == 257 || key == 335) && strings && !listQuery.isBlank()) {
				Setting.Strings st = (Setting.Strings) editing;
				if (st == ModuleManager.TPA_ALL.exclude) ModuleManager.TPA_ALL.addExclude(listQuery);
				else {
					List<String> l = new ArrayList<>(st.get());
					if (!l.contains(listQuery.trim())) l.add(listQuery.trim());
					st.set(l);
				}
				listQuery = "";
				QuiqzyConfig.save();
				UiSounds.added();
				return true;
			}
		}
		if (tab == TAB_CONFIGS && cfgFocus != 0 && closing == null) {
			if (key == 256) {
				cfgFocus = 0;
				return true;
			}
			if (key == 259) {
				if (cfgFocus == 1 && !cfgName.isEmpty()) cfgName = cfgName.substring(0, cfgName.length() - 1);
				if (cfgFocus == 2 && !cfgCode.isEmpty()) cfgCode = cfgCode.substring(0, cfgCode.length() - 1);
				return true;
			}
			if (key == 257 || key == 335) {
				if (cfgFocus == 1 && !cfgName.isBlank()) { QuiqzyConfig.saveNamed(cfgName); cfgStatus = "Saved: " + cfgName; cfgName = ""; UiSounds.added(); }
				if (cfgFocus == 2) { getByKey(cfgCode); cfgCode = ""; }
				cfgFocus = 0;
				return true;
			}
		}
		if (key == 256) {
			if (detail != null) {
				detail = null;
				UiSounds.click();
				return true;
			}
			if (searchFocus || !search.isEmpty()) {
				search = "";
				searchFocus = false;
				return true;
			}
			onClose();
			return true;
		}
		if (detail == null && key == 259 && !search.isEmpty()) {
			search = search.substring(0, search.length() - 1);
			scrollTarget = 0f;
			return true;
		}
		if (key == KeyBindingHelper.getBoundKeyOf(QuiqzyClient.OPEN_GUI).getValue() && !searchFocus) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (editing != null && listening == null && event.isAllowedChatCharacter()) {
			String c = event.codepointAsString();
			if (editing instanceof Setting.Strings) {
				boolean printable = c.length() == 1 && c.charAt(0) >= ' ' && c.charAt(0) < 127;
				boolean leadingSpace = listQuery.isEmpty() && c.equals(" ");
				if (printable && !leadingSpace && listQuery.length() < 256) listQuery += c;
			} else if (!(editing instanceof Setting.Waypoints)) {
				if (!(listQuery.isEmpty() && c.equals(" ")) && listQuery.length() < 32) listQuery += c;
				lScrollTarget = 0f;
			}
			return true;
		}
		if (tab == TAB_CONFIGS && cfgFocus != 0 && listening == null && closing == null && event.isAllowedChatCharacter()) {
			String c = event.codepointAsString();
			if (cfgFocus == 1 && c.matches("[A-Za-z0-9 _-]") && cfgName.length() < 32) cfgName += c;
			if (cfgFocus == 2 && c.matches("[A-Za-z0-9]") && cfgCode.length() < 12) cfgCode += c;
			return true;
		}
		if (detail == null && listening == null && event.isAllowedChatCharacter()) {
			String c = event.codepointAsString();
			if (search.isEmpty() && c.equals(" ")) return true;
			if (search.length() < 24) search += c;
			searchFocus = true;
			scrollTarget = 0f;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		UiSounds.gui(false);
		if (parent != null && this.minecraft != null) this.minecraft.setScreen(parent);
		else super.onClose();
	}

	/** 3rd-Person-Preview an/aus schalten und Kamera merken/wiederherstellen. */
	private void updatePreview(boolean want) {
		previewing = want; // stabil: kein Kamera-Orbit (der ließ die ganze Welt mitschwenken)
		dev.quiqzy.client.render.PreviewCam.active = false;
	}

	/** 3D-Spielermodell links ins Menü zeichnen, schaut zum Cursor (Screen-Pixel, nach dem virtuellen Scale). */
	private void renderPreviewModel(GuiGraphics g, int mouseX, int mouseY) {
		if (!previewing || this.minecraft.player == null) return;
		float px0 = x0 / k; // linke Fensterkante in Screen-Pixeln
		float cx = px0 * 0.5f, cy = height * 0.60f;
		float scale = Math.max(38f, Math.min(90f, px0 * 0.20f)); // kleiner, damit es nicht nervt
		float hw = scale * 1.0f, hh = scale * 1.7f;
		int by1 = Math.round(cy - hh), by2 = Math.round(cy + hh * 0.55f);
		try {
			// mouseY fest auf Box-Mitte -> Körper neigt sich NICHT hoch/runter, dreht nur zum Cursor (horizontal)
			float fixedY = (by1 + by2) / 2f;
			net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
					g, Math.round(cx - hw), by1, Math.round(cx + hw), by2,
					Math.round(scale), 0.0625f, mouseX, fixedY, this.minecraft.player);
		} catch (Throwable ignored) {
		}
	}

	@Override
	public void removed() {
		previewing = false;
		dev.quiqzy.client.render.PreviewCam.active = false;
		ZeroBlur.release();
		QuiqzyConfig.save();
		super.removed();
	}

	// Selbsttest
	public void testTab(int t) {
		tab = t;
		search = "";
		tabFade = 1f;
	}

	public void testDetail(Module m) {
		detail = m;
		detailA = 1f;
	}

	public void testEdit(Setting<?> s) {
		editing = s;
		listQuery = "";
	}

	public void testListQuery(String q) {
		listQuery = q;
	}

	public void testSearch(String s) {
		search = s;
	}
}
