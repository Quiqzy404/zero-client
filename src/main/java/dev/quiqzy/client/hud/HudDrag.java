package dev.quiqzy.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.gui.engine.ZeroFont;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.util.UiSounds;

/**
 * HUD per Drag & Drop verschieben – geteilte Logik (HUD-Editor UND Chat).
 * Koordinaten kommen in GUI-Pixeln rein und werden in die virtuellen HUD-Koordinaten umgerechnet.
 */
public final class HudDrag {
	private static String dragging;
	private static float grabX, grabY, startDx, startDy;
	public static float guideX = Float.NaN, guideY = Float.NaN;
	private static final float SNAP = 6f;

	private HudDrag() {
	}

	public static boolean dragging() {
		return dragging != null;
	}

	/** GUI-Pixel -> virtuelle HUD-Koordinate. */
	public static float v(double gui) {
		return (float) gui * ZeroHud.lastK / ZeroHud.lastScale;
	}

	/** Klick auf ein HUD-Element: links = ziehen starten, rechts = zurücksetzen. true = verbraucht. */
	public static boolean click(double guiX, double guiY, int button) {
		float mx = v(guiX), my = v(guiY);
		// × an einem Teil (z.B. FPS in der Info-Leiste) -> diesen Teil ausblenden
		if (button == 0) {
			String part = hoveredPart(mx, my);
			if (part != null) {
				float[] b = ZeroHud.PARTS.get(part);
				float bx = b[2] - 1f, by = b[1] - 1f;
				if ((mx - bx) * (mx - bx) + (my - by) * (my - by) <= 5.5f * 5.5f) {
					ModuleManager.HUD.hiddenParts.get().add(part);
					QuiqzyConfig.save();
					return true;
				}
			}
		}
		for (Map.Entry<String, float[]> e : ZeroHud.BOUNDS.entrySet()) {
			float[] b = e.getValue();
			if (mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3]) {
				if (button == 1) {
					ModuleManager.ACCENT.setOffset(e.getKey(), 0f, 0f);
					// ausgeblendete Teile dieses Elements zurückholen
					String pre = e.getKey() + ":";
					ModuleManager.HUD.hiddenParts.get().removeIf(x -> x.startsWith(pre));
					QuiqzyConfig.save();
					UiSounds.removed();
					return true;
				}
				if (button == 0) {
					dragging = e.getKey();
					float[] off = ModuleManager.ACCENT.offset(dragging);
					startDx = off[0];
					startDy = off[1];
					grabX = mx;
					grabY = my;
					UiSounds.click();
					return true;
				}
			}
		}
		return false;
	}

	/** Während des Ziehens: neue Position mit Andocken an Kanten/andere Elemente. */
	public static void drag(double guiX, double guiY) {
		if (dragging == null) return;
		float nx = startDx + v(guiX) - grabX;
		float ny = startDy + v(guiY) - grabY;
		float[] cur = ZeroHud.BOUNDS.get(dragging);
		guideX = Float.NaN;
		guideY = Float.NaN;
		if (cur != null) {
			float[] off = ModuleManager.ACCENT.offset(dragging);
			float bx1 = cur[0] - off[0] + nx, bx2 = cur[2] - off[0] + nx;
			float by1 = cur[1] - off[1] + ny, by2 = cur[3] - off[1] + ny;
			Minecraft mc = Minecraft.getInstance();
			float vw = v(mc.getWindow().getGuiScaledWidth()), vh = v(mc.getWindow().getGuiScaledHeight());
			List<Float> xs = new ArrayList<>(List.of(8f, vw / 2f, vw - 8f));
			List<Float> ys = new ArrayList<>(List.of(8f, vh / 2f, vh - 8f));
			for (Map.Entry<String, float[]> e : ZeroHud.BOUNDS.entrySet()) {
				if (e.getKey().equals(dragging)) continue;
				float[] b = e.getValue();
				xs.add(b[0]);
				xs.add(b[2]);
				ys.add(b[1]);
				ys.add(b[3]);
			}
			float bestX = SNAP, bestY = SNAP, shiftX = 0f, shiftY = 0f;
			float[] myX = {bx1, (bx1 + bx2) / 2f, bx2}, myY = {by1, (by1 + by2) / 2f, by2};
			for (float t : xs) for (float mm : myX) if (Math.abs(t - mm) < bestX) { bestX = Math.abs(t - mm); shiftX = t - mm; guideX = t; }
			for (float t : ys) for (float mm : myY) if (Math.abs(t - mm) < bestY) { bestY = Math.abs(t - mm); shiftY = t - mm; guideY = t; }
			nx += shiftX;
			ny += shiftY;
		}
		ModuleManager.ACCENT.setOffset(dragging, nx, ny);
	}

	/** Teil unter der Maus (oder knapp daneben, damit man das × oben rechts trifft). */
	private static String hoveredPart(float mx, float my) {
		for (Map.Entry<String, float[]> e : ZeroHud.PARTS.entrySet()) {
			float[] b = e.getValue();
			if (mx >= b[0] && mx <= b[2] + 6f && my >= b[1] - 6f && my <= b[3]) return e.getKey();
		}
		return null;
	}

	/** Ziehen beenden (speichert). */
	public static void release() {
		if (dragging != null) {
			dragging = null;
			guideX = Float.NaN;
			guideY = Float.NaN;
			QuiqzyConfig.save();
		}
	}

	/** Umrisse aller HUD-Elemente + Hilfslinien zeichnen (virtueller Raum, Aufrufer skaliert bereits). */
	public static void outlines(GuiGraphics g, float mxGui, float myGui, boolean hint) {
		float mx = v(mxGui), my = v(myGui);
		for (Map.Entry<String, float[]> e : ZeroHud.BOUNDS.entrySet()) {
			float[] b = e.getValue();
			boolean hot = dragging != null ? dragging.equals(e.getKey()) : (mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3]);
			int c = hot ? 0xFFFFFFFF : 0x66FFFFFF;
			ZeroRender.rect(g, b[0] - 2f, b[1] - 2f, b[2] + 2f, b[3] + 2f, 4f, hot ? 0x33FFFFFF : 0x0CFFFFFF);
			ZeroRender.stroke(g, b[0] - 2f, b[1] - 2f, b[2] + 2f, b[3] + 2f, 4f, hot ? 1f : 0.5f, c);
			if (hot) ZeroRender.text(g, ZeroFont.BOLD, e.getKey().toUpperCase(Locale.ROOT), b[0], b[1] - 9f, 5.6f, 0xFFFFFFFF);
		}
		// × am Teil unter der Maus (nicht beim Ziehen)
		String part = dragging == null ? hoveredPart(mx, my) : null;
		if (part != null) {
			float[] b = ZeroHud.PARTS.get(part);
			ZeroRender.stroke(g, b[0], b[1], b[2], b[3], 3f, 0.6f, 0x99FFFFFF);
			float bx = b[2] - 1f, by = b[1] - 1f;
			boolean overX = (mx - bx) * (mx - bx) + (my - by) * (my - by) <= 5.5f * 5.5f;
			ZeroRender.circle(g, bx, by, 4.6f, overX ? 0xFFFF4A4A : 0xF0202126);
			ZeroRender.icon(g, ZeroFont.Icon.X, bx, by, 4.4f, 0xFFFFFFFF);
		}
		Minecraft mc = Minecraft.getInstance();
		float vw = v(mc.getWindow().getGuiScaledWidth()), vh = v(mc.getWindow().getGuiScaledHeight());
		if (!Float.isNaN(guideX)) ZeroRender.rect(g, guideX - 0.4f, 0, guideX + 0.4f, vh, 0f, 0xC0FFFFFF);
		if (!Float.isNaN(guideY)) ZeroRender.rect(g, 0, guideY - 0.4f, vw, guideY + 0.4f, 0f, 0xC0FFFFFF);
		if (hint) {
			String tip = "Drag HUD to move  ·  × removes a part  ·  Right-click = reset";
			float tw = ZeroFont.BOLD.width(tip, 6.4f, 0f);
			ZeroRender.noTint = true;
			ZeroRender.panel(g, vw / 2f - tw / 2f - 10f, 6f, vw / 2f + tw / 2f + 10f, 21f, 7.5f, 0x9E14161C, 0x14FFFFFF, 1f);
			ZeroRender.textCentered(g, ZeroFont.BOLD, tip, vw / 2f, 9.6f, 6.4f, 0xFFF4EEE8);
			ZeroRender.noTint = false;
		}
	}
}
