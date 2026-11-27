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
 * Listen-Editor im neuen Glas-Stil (Blöcke, Items, Namen, Wegpunkte) – öffnet sich aus Radial-/Panel-GUI.
 * Schwebendes Glas-Blatt mit Zurück-Knopf; Logik (Suche, Auswahl, Block-Farbe per Rechtsklick, Wegpunkte) wie bisher.
 */
public final class ListEditorGui extends Screen {
	private static final float W = 560f, H = 360f;

	private final Screen parent;
	private final Module module;
	private float k, vw, vh, mx, my, dt, time, open;
	private long last = System.nanoTime();
	private final Map<Object, Float> anim = new HashMap<>();
	private final List<Region> regions = new ArrayList<>();
	private boolean modal;

	private record Region(float x1, float y1, float x2, float y2, Runnable left, Runnable right) {
	}

	public ListEditorGui(Screen parent, Module module, Setting<?> setting) {
		super(Component.literal("ZERO"));
		this.parent = parent;
		this.module = module;
		this.editing = setting;
	}

	private void scale() {
		Window w = this.minecraft.getWindow();
		float pixels = Math.max(1f, w.getHeight() / 540f) * (ModuleManager.CLICK_GUI.scale.get() / 100f);
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


	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - last) / 1.0e9f);
		last = now;
		time += dt;
		open = Draw.approach(open, 1f, 9f, dt);
		scale();
		mx = mouseX * k;
		my = mouseY * k;
		regions.clear();
		float ease = 1f - (1f - open) * (1f - open) * (1f - open);
		var cfg = ModuleManager.CLICK_GUI;
		ZeroBlur.capture(cfg.blurStrength.get());
		g.pose().pushMatrix();
		g.pose().scale(1f / k);
		if (cfg.bgBlur.on()) ZeroRender.frost(g, 0, 0, vw, vh, 0f, 0x40101114, ease);
		ZeroRender.rect(g, 0, 0, vw, vh, 0, a(0xFF050508, cfg.bgDim.get() / 100f * ease));
		float x1 = (vw - W) / 2f, y1 = (vh - H) / 2f + (1f - ease) * 12f, x2 = x1 + W, y2 = y1 + H;
		float r = cfg.roundness.get() + 8f;
		ZeroRender.noTint = true;
		try {
			ZeroRender.glow(g, x1, y1 + 8f, x2, y2 + 12f, r, 26f, a(0xFF000000, 0.22f * ease));
			ZeroRender.panel(g, x1, y1, x2, y2, r, a(0xFF14161C, 0.18f + 0.6f * cfg.panelOpacity.get() / 100f), a(0xFFFFFFFF, 0.10f), ease);
			// Kopf: Zurück + Modulname
			boolean bh = hot(x1 + 12f, y1 + 10f, x1 + 30f, y1 + 28f);
			ZeroRender.panel(g, x1 + 12f, y1 + 10f, x1 + 30f, y1 + 28f, 9f, a(0xFFFFFFFF, bh ? 0.30f : 0.16f), 0x14FFFFFF, ease);
			ZeroRender.textCentered(g, ZeroFont.BOLD, "<", x1 + 21f, y1 + 14.6f, 7f, a(0xFFFFFFFF, ease));
			click(x1 + 10f, y1 + 8f, x1 + 32f, y1 + 30f, this::onClose);
			ZeroRender.text(g, ZeroFont.BOLD, module.name(), x1 + 38f, y1 + 10.5f, 8.5f, a(0xFFFFFFFF, ease));
			ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, module.description(), 5.6f, W - 80f), x1 + 38f, y1 + 22f, 5.6f, a(0xFFFFFFFF, 0.5f * ease));
			listPage(g, module, editing, x1, y1 + 38f, x2, y2 - 12f, ease);
		} finally {
			ZeroRender.noTint = false;
		}
		g.pose().popMatrix();
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

	/** Block ESP: per Rechtsklick gewählter Block -> eigene Farbe (Farbverlauf + feste Farben + Reset). */
	private String colorTarget;

	private float blockColorPanel(GuiGraphics g, String id, Entry en, float x1, float y, float x2, boolean inView, float al) {
		var esp = ModuleManager.BLOCK_ESP;
		int cur = esp.colorFor(id);
		float h = 66f;
		ZeroRender.rect(g, x1, y, x2, y + h, 9f, a(0xFFFFFFFF, 0.06f * al));
		if (en != null) item(g, en.stack(), x1 + 8f, y + 6f, 16f);
		String nm = en != null ? en.name() : id;
		ZeroRender.text(g, ZeroFont.BOLD, nm, x1 + 30f, y + 7f, 7f, a(text(), al));
		ZeroRender.noTint = true;
		ZeroRender.rect(g, x1 + 30f + w(ZeroFont.BOLD, nm, 7f) + 8f, y + 7f, x1 + 30f + w(ZeroFont.BOLD, nm, 7f) + 22f, y + 16f, 3f, a(0xFF000000 | cur, al));
		ZeroRender.noTint = false;
		// Reset / Fertig
		boolean custom = esp.blockColors.color(id) != null;
		float bx = x2 - 8f;
		ZeroRender.icon(g, ZeroFont.Icon.X, bx - 6f, y + 11.5f, 7f, a(muted(), al));
		if (inView) click(bx - 13f, y + 4f, bx + 1f, y + 19f, () -> { colorTarget = null; UiSounds.click(); });
		bx -= 22f;
		if (custom) {
			float rw = w(ZeroFont.REGULAR, "Reset", 6.2f);
			ZeroRender.text(g, ZeroFont.REGULAR, "Reset", bx - rw, y + 8f, 6.2f, a(muted(), al));
			if (inView) click(bx - rw - 4f, y + 4f, bx + 4f, y + 19f, () -> {
				esp.blockColors.remove(id);
				QuiqzyConfig.save();
				UiSounds.click();
			});
		}
		// Farbverlauf (Klick = Farbton)
		float hy = y + 26f, hx1 = x1 + 8f, hx2 = x2 - 8f;
		int segs = 48;
		ZeroRender.noTint = true;
		for (int k = 0; k < segs; k++) {
			float sx1 = hx1 + (hx2 - hx1) * k / segs, sx2 = hx1 + (hx2 - hx1) * (k + 1) / segs + 0.5f;
			ZeroRender.rect(g, sx1, hy, sx2, hy + 12f, 0f, a(0xFF000000 | net.minecraft.util.Mth.hsvToRgb(k / (float) segs, 0.78f, 1f), al));
		}
		ZeroRender.noTint = false;
		if (inView) click(hx1, hy, hx2, hy + 12f, () -> {
			float t = Math.max(0f, Math.min(0.999f, (mx - hx1) / (hx2 - hx1)));
			esp.blockColors.put(id, net.minecraft.util.Mth.hsvToRgb(t, 0.78f, 1f) & 0xFFFFFF);
			QuiqzyConfig.save();
			UiSounds.click();
		});
		// feste Farben
		int[] sw = {0xFFFFFF, 0xB8BCC6, 0x5B6070, 0xFF3B30, 0xFF8A1F, 0xFFD23D, 0x7CFF4D, 0x2EE59D, 0x4DE8FF, 0x3B82F6, 0x8B5CF6, 0xF472B6};
		float cx = x1 + 14f, cy = y + 52f;
		ZeroRender.noTint = true;
		for (int rgb : sw) {
			boolean selc = cur == rgb;
			if (selc) ZeroRender.circle(g, cx, cy, 6f, a(0xFFFFFFFF, al));
			ZeroRender.circle(g, cx, cy, 4.6f, a(0xFF000000 | rgb, al));
			final int c = rgb;
			if (inView) click(cx - 6f, cy - 6f, cx + 6f, cy + 6f, () -> {
				esp.blockColors.put(id, c);
				QuiqzyConfig.save();
				UiSounds.click();
			});
			cx += 15f;
		}
		ZeroRender.noTint = false;
		return y + h;
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
				boolean espColors = blocks && s == ModuleManager.BLOCK_ESP.blocks;
				if (espColors) {
					// Farbpunkt unten rechts = aktuelle ESP-Farbe dieses Blocks
					ZeroRender.noTint = true;
					ZeroRender.circle(g, tx + tile - 5f, ty + tile - 5f, 3.6f, a(0xFF101114, al));
					ZeroRender.circle(g, tx + tile - 5f, ty + tile - 5f, 2.7f, a(0xFF000000 | ModuleManager.BLOCK_ESP.colorFor(id), al));
					ZeroRender.noTint = false;
					if (id.equals(colorTarget)) ZeroRender.stroke(g, tx, ty, tx + tile, ty + tile, 7f, 1f, a(0xFFFFFFFF, 0.8f * al));
				}
				if (hv) {
					ZeroRender.rect(g, tx, ty, tx + tile, ty + tile, 7f, a(0xFF000000, 0.45f * al));
					ZeroRender.icon(g, ZeroFont.Icon.X, tx + tile / 2f, ty + tile / 2f, 9f, a(text(), al));
					if (espColors) {
						String tip = "Right-click: color";
						float tw = w(ZeroFont.REGULAR, tip, 5.8f) + 12f, tipx = Math.min(tx, gx2 - tw);
						ZeroRender.rect(g, tipx, ty - 14f, tipx + tw, ty - 2f, 6f, a(0xF0202125, al));
						ZeroRender.text(g, ZeroFont.REGULAR, tip, tipx + 6f, ty - 11f, 5.8f, a(text(), al));
					}
				}
				if (inView) click(tx, ty, tx + tile, ty + tile, () -> {
					if (blocks) ((Setting.Blocks) s).remove(id); else ((Setting.Items) s).remove(id);
					if (id.equals(colorTarget)) colorTarget = null;
					QuiqzyConfig.save();
					UiSounds.removed();
				}, espColors ? () -> {
					colorTarget = id.equals(colorTarget) ? null : id;
					UiSounds.click();
				} : null);
				i++;
			}
			if (sel.isEmpty()) {
				ZeroRender.text(g, ZeroFont.REGULAR, "Nothing selected yet - click something below to add it", gx1, y + 2f, 6.4f, a(faint(), al));
				y += 16f;
			} else {
				y += ((sel.size() + cols - 1) / cols) * (tile + gap) + 4f;
			}
			if (blocks && s == ModuleManager.BLOCK_ESP.blocks && colorTarget != null && sel.contains(colorTarget)) {
				Entry ce = null;
				for (Entry e2 : all) if (e2.id().equals(colorTarget)) { ce = e2; break; }
				y = blockColorPanel(g, colorTarget, ce, gx1, y + 2f, gx2, inView, al) + 6f;
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

	// ------------------------------------------------------------------ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
		mx = (float) event.x() * k;
		my = (float) event.y() * k;
		boolean left = event.button() == 0, right = event.button() == 1;
		if (!left && !right) return super.mouseClicked(event, dbl);
		for (int i = regions.size() - 1; i >= 0; i--) {
			Region rg = regions.get(i);
			if (mx >= rg.x1 && mx < rg.x2 && my >= rg.y1 && my < rg.y2) {
				Runnable act = left ? rg.left : rg.right;
				if (act != null) act.run();
				return true;
			}
		}
		return super.mouseClicked(event, dbl);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		lScrollTarget -= (float) v * 30f;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (event.isPaste()) {
			String clip = this.minecraft != null ? this.minecraft.keyboardHandler.getClipboard() : null;
			if (clip != null && !clip.isEmpty()) {
				if (editing instanceof Setting.Strings) {
					listQuery = listQuery + clip.replaceAll("[\\r\\n\\t]", " ").trim();
				} else if (!(editing instanceof Setting.Waypoints)) {
					listQuery += clip.replaceAll("\\s+", " ").trim();
					lScrollTarget = 0f;
				}
				if (listQuery.length() > 256) listQuery = listQuery.substring(0, 256);
			}
			return true;
		}
		if (key == 256) {
			onClose();
			return true;
		}
		if (key == 259 && !listQuery.isEmpty()) {
			listQuery = listQuery.substring(0, listQuery.length() - 1);
			lScrollTarget = 0f;
			return true;
		}
		if ((key == 257 || key == 335) && editing instanceof Setting.Strings st && !listQuery.isBlank()) {
			if (st == ModuleManager.TPA_ALL.exclude) ModuleManager.TPA_ALL.addExclude(listQuery);
			else {
				List<String> l = new ArrayList<>(st.get());
				if (!l.contains(listQuery.trim())) l.add(listQuery.trim());
				st.set(l);
			}
			listQuery = "";
			QuiqzyConfig.save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!event.isAllowedChatCharacter()) return super.charTyped(event);
		String c = event.codepointAsString();
		if (editing instanceof Setting.Strings) {
			boolean printable = c.length() == 1 && c.charAt(0) >= ' ' && c.charAt(0) < 127;
			if (printable && !(listQuery.isEmpty() && c.equals(" ")) && listQuery.length() < 256) listQuery += c;
		} else if (!(editing instanceof Setting.Waypoints)) {
			if (!(listQuery.isEmpty() && c.equals(" ")) && listQuery.length() < 32) listQuery += c;
			lScrollTarget = 0f;
		}
		return true;
	}

	@Override
	public void onClose() {
		QuiqzyConfig.save();
		if (this.minecraft != null) this.minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		ZeroBlur.release();
		super.removed();
	}
}
