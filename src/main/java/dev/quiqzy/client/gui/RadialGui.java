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
import net.minecraft.world.item.Items;

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
 * Radial-GUI: in der Mitte dein Kopf (schaut zur Maus), darum ein Ring aus Glas-Segmenten – eins pro Bereich
 * (Mace, Crystal, Combat, Base Finding, ...) mit dem echten Item als Icon. Klick auf ein Segment öffnet daneben
 * ein Glas-Panel mit den Modulen (links = an/aus, rechts = Einstellungen darunter).
 */
public final class RadialGui extends Screen {
	private static final float ROW = 14.5f, SROW = 13f, INNER = 62f, OUTER = 138f, PANEL_W = 176f;
	private static final Set<Module> EXPANDED = new HashSet<>();
	private static int selected = -1;

	private record Group(String name, net.minecraft.world.item.ItemStack icon, List<Module> mods) {
	}

	private final List<Group> groups = new ArrayList<>();
	private final Screen parent;
	private float k, vw, vh, mx, my, dt, open, time;
	private long last = System.nanoTime();
	private final Map<Object, Float> anim = new HashMap<>();
	private final List<Region> regions = new ArrayList<>();
	private float pScroll, pScrollTarget, pContentH, pViewH;
	private float[] panelBounds;
	private Setting.Int dragSlider;
	private float sliderX1, sliderX2;
	private Module listening;
	private Setting<?> openPopup;
	private String search = "";
	private String hoverDesc;

	private record Region(float x1, float y1, float x2, float y2, Runnable left, Runnable right) {
	}

	public RadialGui() {
		this(null);
	}

	public RadialGui(Screen parent) {
		super(Component.literal("ZERO"));
		this.parent = parent;
		buildGroups();
	}

	private static ClickGuiModule cfg() {
		return ModuleManager.CLICK_GUI;
	}

	/** Bereiche: feste Zuordnung für Mace/Crystal/Base Finding, der Rest nach Kategorie. */
	private void buildGroups() {
		Set<String> mace = Set.of("stun_slam", "auto_ely_mace", "instant_launch", "auto_backstep", "hit_flick", "shield_breaker", "auto_drain");
		Set<String> crystal = Set.of("crystal_aura", "crystal_optimizer", "hit_crystal", "anchor", "anchor_aura", "surround", "hole_esp",
				"auto_double_hand", "auto_totem", "auto_inv_totem");
		Set<String> bases = Set.of("sus_chunk", "base_finder", "base_radar", "base_research", "block_esp", "sound_chunks", "spawner_noti",
				"spawner_bot", "xray", "xray_research", "activity_inspector", "storage_esp", "spawner_nametags", "region_map", "rtp_hunter",
				"waypoints", "death_waypoint");
		String[] names = {"Mace", "Crystal", "Combat", "Base Finding", "Movement", "Visuals", "HUD", "Utility", "Client"};
		net.minecraft.world.item.Item[] icons = {Items.MACE, Items.END_CRYSTAL, Items.NETHERITE_SWORD, Items.SPYGLASS, Items.FEATHER,
				Items.ENDER_EYE, Items.CLOCK, Items.DIAMOND_PICKAXE, Items.COMPARATOR};
		List<List<Module>> lists = new ArrayList<>();
		for (int i = 0; i < names.length; i++) lists.add(new ArrayList<>());
		for (Module m : ModuleManager.all()) {
			int gi;
			if (mace.contains(m.id())) gi = 0;
			else if (crystal.contains(m.id())) gi = 1;
			else if (bases.contains(m.id()) || m.category() == Module.Category.TIERSMC) gi = 3;
			else gi = switch (m.category()) {
				case COMBAT -> 2;
				case MOVEMENT -> 4;
				case VISUALS -> 5;
				case HUD -> 6;
				case CLIENT -> 8;
				default -> 7;
			};
			lists.get(gi).add(m);
		}
		for (int i = 0; i < names.length; i++)
			if (!lists.get(i).isEmpty()) groups.add(new Group(names[i], new net.minecraft.world.item.ItemStack(icons[i]), lists.get(i)));
		if (selected >= groups.size()) selected = -1;
	}

	private void scale() {
		Window w = this.minecraft.getWindow();
		float pixels = Math.max(1f, w.getHeight() / 540f) * (cfg().scale.get() / 100f);
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

	private void item(GuiGraphics g, net.minecraft.world.item.ItemStack st, float cx, float cy, float size) {
		g.pose().pushMatrix();
		g.pose().translate(cx - size / 2f, cy - size / 2f);
		g.pose().scale(size / 16f, size / 16f);
		g.renderItem(st, 0, 0);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ Render

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - last) / 1.0e9f);
		last = now;
		open = Draw.approach(open, 1f, 8f, dt);
		time += dt;
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

		// Ring links von der Mitte, wenn ein Panel offen ist (damit beides Platz hat)
		float targetCx = selected >= 0 ? vw / 2f - (PANEL_W + 30f) / 2f : vw / 2f;
		// leichte Bewegung: schwebt sanft + folgt der Maus ein kleines Stück (Parallaxe)
		float px = an("px", (mx - vw / 2f) * 0.025f, 6f), py = an("py", (my - vh / 2f) * 0.025f, 6f);
		float cx = an("cx", targetCx, 10f) + px, cy = vh / 2f + py + (float) Math.sin(time * 1.3f) * 2.2f;
		ZeroRender.noTint = true;
		try {
			ring(g, cx, cy, ease);
			centre(g, cx, cy, ease);
			if (selected >= 0) panel(g, groups.get(selected), cx + OUTER + 30f, cy, ease);
			footer(g, ease);
		} finally {
			ZeroRender.noTint = false;
		}
		g.pose().popMatrix();
		head(g, cx / k, cy / k, mouseX, mouseY);
	}

	private int hoveredSegment(float cx, float cy) {
		float dx = mx - cx, dy = my - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
		if (d < INNER || d > OUTER + 6f) return -1;
		float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
		float rel = ((ang + 90f + 360f + 180f / groups.size()) % 360f);
		return Math.min(groups.size() - 1, (int) (rel / (360f / groups.size())));
	}

	private void ring(GuiGraphics g, float cx, float cy, float al) {
		int n = groups.size();
		float sweep = 360f / n;
		int hv = hoveredSegment(cx, cy);
		float op = cfg().panelOpacity.get() / 100f;
		ZeroRender.glow(g, cx - OUTER, cy - OUTER + 6f, cx + OUTER, cy + OUTER + 8f, OUTER, 24f, a(0xFF000000, 0.18f * al));
		for (int i = 0; i < n; i++) {
			Group gr = groups.get(i);
			float start = -90f - sweep / 2f + i * sweep;
			float hA = an("seg" + i, i == hv ? 1f : 0f, 14f), sA = an("sel" + i, i == selected ? 1f : 0f, 12f);
			float outer = OUTER + 14f * hA + 4f * sA, inner = INNER - 2f * hA + 2f * sA;
			float spin = (1f - al) * 30f;
			int tint = ZeroRender.lerp(a(0xFF14161C, 0.30f + 0.5f * op - 0.12f * hA), a(0xFFFFFFFF, cfg().activeGlass.get() / 100f), sA);
			ZeroRender.sector(g, cx, cy, inner, outer, start - spin, sweep, 5f, 9f, tint, a(0xFFFFFFFF, 0.10f + 0.10f * hA), al);
			// Item-Icon in der Segmentmitte
			double mid = Math.toRadians(start - spin + sweep / 2f);
			float rr = (inner + outer) / 2f;
			float ix = cx + (float) Math.cos(mid) * rr, iy = cy + (float) Math.sin(mid) * rr;
			float size = 20f + 9f * hA + 2f * sA;
			item(g, gr.icon(), ix, iy - 4f, size);
			int txt = ZeroRender.lerp(0xFFFFFFFF, 0xFF141418, sA);
			ZeroRender.textCentered(g, ZeroFont.BOLD, gr.name(), ix, iy + size / 2f - 1f, 5.4f, a(txt, (0.65f + 0.35f * hA) * al));
			final int idx = i;
			if (i == hv) click(cx - OUTER - 8f, cy - OUTER - 8f, cx + OUTER + 8f, cy + OUTER + 8f, () -> {
				selected = selected == idx ? -1 : idx;
				pScroll = pScrollTarget = 0f;
				openPopup = null;
			}, null);
		}
	}

	/** Mitte: Glas-Kreis hinter dem Kopf + Name des Bereichs darunter. */
	private void centre(GuiGraphics g, float cx, float cy, float al) {
		float r = INNER - 9f;
		ZeroRender.panel(g, cx - r, cy - r, cx + r, cy + r, r, a(0xFF14161C, 0.35f), a(0xFFFFFFFF, 0.10f), al);
		int hv = hoveredSegment(cx, cy);
		String label = hv >= 0 ? groups.get(hv).name() : selected >= 0 ? groups.get(selected).name() : "ZERO";
		float lw = w(ZeroFont.BOLD, label, 6.4f) + 16f;
		ZeroRender.panel(g, cx - lw / 2f, cy + OUTER + 16f, cx + lw / 2f, cy + OUTER + 31f, 7.5f, a(0xFF14161C, 0.55f), 0x0AFFFFFF, al);
		ZeroRender.textCentered(g, ZeroFont.BOLD, label, cx, cy + OUTER + 20f, 6.4f, a(0xFFFFFFFF, al));
	}

	/** 3D-Kopf in der Mitte, dreht sich zur Maus (Screen-Koordinaten). */
	private void head(GuiGraphics g, float cx, float cy, int mouseX, int mouseY) {
		if (this.minecraft.player == null) return;
		float r = (INNER - 12f) / k;
		int s = Math.max(10, Math.round(r * 2.3f));
		try {
			net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
					g, Math.round(cx - r), Math.round(cy - r), Math.round(cx + r), Math.round(cy + r),
					s, 0.75f, mouseX, mouseY, this.minecraft.player);
		} catch (Throwable ignored) {
		}
	}

	/** Glas-Panel mit den Modulen des gewählten Bereichs. */
	private void panel(GuiGraphics g, Group gr, float x1, float cy, float al) {
		float x2 = x1 + PANEL_W;
		float h = Math.min(OUTER * 2f + 20f, vh - 40f);
		float y1 = cy - h / 2f, y2 = cy + h / 2f;
		float r = cfg().roundness.get() + 4f;
		float op = cfg().panelOpacity.get() / 100f;
		ZeroRender.glow(g, x1, y1 + 4f, x2, y2 + 6f, r, 18f, a(0xFF000000, 0.18f * al));
		ZeroRender.panel(g, x1, y1, x2, y2, r, a(0xFF14161C, 0.18f + 0.6f * op), a(0xFFFFFFFF, 0.10f), al);
		item(g, gr.icon(), x1 + 15f, y1 + 13f, 13f);
		ZeroRender.text(g, ZeroFont.BOLD, gr.name(), x1 + 27f, y1 + 8.5f, 7.2f, a(0xFFFFFFFF, al));
		ZeroRender.textRight(g, ZeroFont.REGULAR, gr.mods().size() + "", x2 - 10f, y1 + 10f, 5.6f, a(0xFFFFFFFF, 0.45f * al));
		float by1 = y1 + 26f, by2 = y2 - 6f;
		pViewH = by2 - by1;
		panelBounds = new float[] {x1, by1, x2, by2};
		pScrollTarget = Math.max(0f, Math.min(pScrollTarget, Math.max(0f, pContentH - pViewH)));
		pScroll = Draw.approach(pScroll, pScrollTarget, 16f, dt);
		g.enableScissor(Math.round(x1), Math.round(by1), Math.round(x2), Math.round(by2));
		float y = by1 - pScroll;
		boolean inBody = hot(x1, by1, x2, by2);
		for (Module m : gr.mods()) y = moduleRow(g, m, x1 + 5f, y, x2 - 5f, by1, by2, inBody, al);
		g.disableScissor();
		pContentH = y + pScroll - by1 + 2f;
		if (pContentH > pViewH + 1f) {
			float maxS = pContentH - pViewH, t = pScroll / maxS, bh = Math.max(16f, pViewH * pViewH / pContentH);
			float bY = by1 + (pViewH - bh) * t;
			ZeroRender.rect(g, x2 - 3f, bY, x2 - 1.6f, bY + bh, 0.7f, a(0xFFFFFFFF, 0.25f * al));
		}
	}

	private void footer(GuiGraphics g, float al) {
		String line = hoverDesc != null && cfg().descriptions.on() ? hoverDesc
				: selected < 0 ? "Click a segment" : "Module: left = on/off  ·  right = settings";
		float tw = w(ZeroFont.REGULAR, line, 6f) + 20f;
		float cx = vw / 2f, y = vh - 22f;
		ZeroRender.panel(g, cx - tw / 2f, y, cx + tw / 2f, y + 15f, 7.5f, a(0xFF14161C, 0.55f), 0x0AFFFFFF, al);
		ZeroRender.textCentered(g, ZeroFont.REGULAR, line, cx, y + 4.4f, 6f, a(0xFFFFFFFF, 0.8f * al));
	}

	private float moduleRow(GuiGraphics g, Module m, float x1, float y, float x2, float clipTop, float clipBot, boolean inBody, float al) {
		boolean settingsOnly = m == ModuleManager.CLICK_GUI;
		boolean on = !settingsOnly && m.isEnabled();
		boolean hv = inBody && hot(x1, y, x2, y + ROW);
		float onA = an(m, on ? 1f : 0f, 14f);
		float rr = cfg().rowRoundness.get();
		boolean visible = y + ROW > clipTop && y < clipBot;
		if (visible) {
			if (onA > 0.01f) {
				// aktives Modul = weißes Milchglas: Hintergrund schimmert noch durch
				int fill = cfg().accentRows.on() ? accent() : 0xFFFFFFFF;
				float wh = cfg().activeGlass.get() / 100f;
				ZeroRender.panel(g, x1, y + 0.8f, x2, y + ROW - 0.8f, rr, a(fill, wh), a(0xFFFFFFFF, 0.12f), onA * al);
			} else if (hv) {
				ZeroRender.rect(g, x1, y + 0.8f, x2, y + ROW - 0.8f, rr, a(0xFFFFFFFF, 0.08f * al));
			}
			int txt = ZeroRender.lerp(a(0xFFFFFFFF, 0.78f), cfg().accentRows.on() ? 0xFFFFFFFF : 0xFF141418, onA);
			String name = listening == m ? "Press a key…" : m.name();
			ZeroRender.text(g, on ? ZeroFont.BOLD : ZeroFont.REGULAR, fit(ZeroFont.REGULAR, name, 6.2f, x2 - x1 - 16f), x1 + 5f, y + 4.1f, 6.2f, a(txt, al));
			if (!m.settings().isEmpty() || settingsOnly) {
				boolean ex = EXPANDED.contains(m);
				if (hv || ex) ZeroRender.icon(g, ex ? ZeroFont.Icon.CHEVRON_DOWN : ZeroFont.Icon.CHEVRON_RIGHT, x2 - 6f, y + ROW / 2f, 5f, a(txt, 0.7f * al));
			}
			if (m.keybind() > 0 && !hv) {
				String kb = keyName(m.keybind());
				ZeroRender.textRight(g, ZeroFont.REGULAR, kb, x2 - 5f, y + 4.6f, 5.2f, a(txt, 0.45f * al));
			}
			if (hv && cfg().descriptions.on()) hoverDesc = m.description();
			float c1 = Math.max(y, clipTop), c2 = Math.min(y + ROW, clipBot);
			if (c2 > c1) click(x1, c1, x2, c2, () -> {
				if (settingsOnly) {
					toggleExpand(m);
					return;
				}
				m.setEnabled(!m.isEnabled());
				QuiqzyConfig.save();
			}, () -> toggleExpand(m));
		}
		y += ROW;
		boolean ex = EXPANDED.contains(m);
		float exA = an("ex" + m.id(), ex ? 1f : 0f, 16f);
		if (exA < 0.01f) return y;
		// Einstellungen
		float sy = y + 1f;
		float startY = sy;
		ZeroRender.noTint = false;
		for (Setting<?> s : m.settings()) {
			if (s instanceof Setting.ColorMap) continue;
			sy = setting(g, m, s, x1 + 3f, sy, x2, clipTop, clipBot, inBody, al * exA);
		}
		sy = keybindRow(g, m, x1 + 3f, sy, x2, clipTop, clipBot, inBody, al * exA);
		ZeroRender.noTint = true;
		float full = sy - startY + 3f;
		// Trennlinie links (zeigt Zugehörigkeit)
		float shown = full * exA;
		if (y + shown > clipTop && y < clipBot)
			ZeroRender.rect(g, x1 + 1f, y + 2f, x1 + 1.8f, y + Math.max(2f, shown - 2f), 0.4f, a(0xFFFFFFFF, 0.18f * al * exA));
		return y + shown;
	}

	private void toggleExpand(Module m) {
		if (!EXPANDED.remove(m)) EXPANDED.add(m);
		openPopup = null;
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
		pScrollTarget -= (float) v * 26f;
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
			if (selected >= 0) { selected = -1; openPopup = null; return true; }
			onClose();
			return true;
		}
		if (QuiqzyClient.OPEN_GUI.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
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
	public void testSelect(int i) {
		selected = i;
	}

	public void testExpand(Module m) {
		EXPANDED.add(m);
	}
}
