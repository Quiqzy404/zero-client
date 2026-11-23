package dev.quiqzy.client.util;

import java.util.concurrent.ConcurrentHashMap;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Farbton-Tausch für die Akzentfarbe & CatLean Grey Theme:
 * - Wenn CatLean gewählt ist: verwandelt warme/orange Akzente und bräunliche Hintergründe
 *   vollständig in minimalistisches, cleanes CatLean-Dunkelgrau / Slate / Silber-Weiß.
 * - Sonst: klassischer Farbton-Tausch auf die gewählte Akzentfarbe.
 */
public final class Theme {
	private static final ConcurrentHashMap<Integer, Integer> CACHE = new ConcurrentHashMap<>();
	private static float cachedHue = Float.NaN;
	private static boolean cachedCatLean = false;

	private Theme() {
	}

	/** Themen-Farbton 0..1 (Ember/Original = Orange). */
	public static float hue() {
		if (ModuleManager.ACCENT == null) return 0.74f;
		float h = ModuleManager.ACCENT.hue();
		return h < 0f ? 0.075f : h;
	}

	/** Farbloses Liquid-Glass-Theme aktiv? */
	public static boolean glass() {
		return ModuleManager.ACCENT != null && ModuleManager.ACCENT.isGlass();
	}

	/** HSV -> ARGB, Farbton wird umgebrochen. Im Glass-Theme farblos (außer unter noTint). */
	public static int hsv(float h, float s, float v, float a) {
		if (!dev.quiqzy.client.gui.engine.ZeroRender.noTint && glass()) s = 0f;
		h = h - (float) Math.floor(h);
		int rgb = net.minecraft.util.Mth.hsvToRgb(h, Math.max(0f, Math.min(1f, s)), Math.max(0f, Math.min(1f, v))) & 0xFFFFFF;
		return (Math.round(Math.max(0f, Math.min(1f, a)) * 255f) << 24) | rgb;
	}

	public static int tint(int argb) {
		if (ModuleManager.ACCENT == null) return argb;
		boolean cat = ModuleManager.ACCENT.isCatLean() || ModuleManager.ACCENT.isGlass();
		float target = ModuleManager.ACCENT.hue();
		if (cat != cachedCatLean || target != cachedHue) {
			CACHE.clear();
			cachedCatLean = cat;
			cachedHue = target;
		}
		Integer c = CACHE.get(argb);
		if (c != null) return c;
		int out = ModuleManager.ACCENT.isGlass() ? toGrey(argb) : cat ? toCatLeanGrey(argb) : (target >= 0f ? shift(argb, target) : argb);
		if (CACHE.size() < 4096) CACHE.put(argb, out);
		return out;
	}

	/** Glass: alles farblos. Dunkle Töne -> neutrales Dunkelgrau, helle -> Weiß/Silber (Helligkeit bleibt). */
	private static int toGrey(int argb) {
		int a = argb >>> 24;
		float r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float l = 0.2126f * r + 0.7152f * g + 0.0722f * b;
		float v = max < 0.3f ? Math.min(0.16f, max * 0.9f) : Math.max(l, max * 0.92f);
		int c = Math.round(Math.min(1f, v) * 255f);
		return (a << 24) | (c << 16) | (c << 8) | c;
	}

	/** Verwandelt Farben in den minimalistischen CatLean Dunkelgrau-Look. */
	private static int toCatLeanGrey(int argb) {
		int a = argb >>> 24;
		float r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;

		// 1. Warme/Bräunliche dunkle Hintergründe -> Clean Charcoal/Dark Grey
		if (max < 0.22f && r >= g && r >= b) {
			int val = Math.round(max * 255f * 0.95f);
			val = Math.max(14, Math.min(32, val));
			return (a << 24) | (val << 16) | (val << 8) | (val + 3);
		}

		// 2. Nicht gesättigt (bereits neutrales Grau/Schwarz/Weiß) -> belassen
		if (max <= 0f || d / max < 0.22f) return argb;

		float h = max == r ? ((g - b) / d) % 6f : max == g ? (b - r) / d + 2f : (r - g) / d + 4f;
		h /= 6f;
		if (h < 0f) h += 1f;
		float deg = h * 360f;

		// 3. Orange / Warme Akzente (Client-Orange) -> Elegantes CatLean Slate/Silber-Weiß
		if (deg >= 5f && deg <= 55f) {
			int greyVal = Math.round(180f + max * 65f); // 180..245 heller Silber-Look
			greyVal = Math.min(245, greyVal);
			return (a << 24) | (greyVal << 16) | (greyVal << 8) | (greyVal + 4);
		}

		return argb;
	}

	private static int shift(int argb, float target) {
		int a = argb >>> 24;
		float r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		if (max <= 0f || d / max < 0.28f) return argb;
		float h = max == r ? ((g - b) / d) % 6f : max == g ? (b - r) / d + 2f : (r - g) / d + 4f;
		h /= 6f;
		if (h < 0f) h += 1f;
		float deg = h * 360f;
		if (deg < 6f || deg > 48f) return argb;
		float s = d / max, v = max;
		int rgb = net.minecraft.util.Mth.hsvToRgb(target, s, v) & 0xFFFFFF;
		return a << 24 | rgb;
	}
}
