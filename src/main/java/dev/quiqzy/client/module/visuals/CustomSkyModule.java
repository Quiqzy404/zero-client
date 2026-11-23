package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Custom Sky: eigene, prozedurale Himmel (Shader zero_sky). Nur in der Oberwelt. */
public final class CustomSkyModule extends Module {
	public static final String[] PRESETS = {"Planets", "Snowy", "Moon", "Aurora", "Sunset", "Nebula", "Galaxy", "Storm", "Theme"};
	public final Setting.Choice preset = add(new Setting.Choice("preset_v2", "Sky", "Planets", PRESETS));
	public final Setting.Int brightness = add(new Setting.Int("brightness", "Brightness", 100, 30, 200, " %"));
	public final Setting.Int stars = add(new Setting.Int("stars", "Stars", 100, 0, 300, " %"));
	public final Setting.Int speed = add(new Setting.Int("speed", "Animation Speed", 100, 0, 400, " %"));
	public final Setting.Bool snowFall = add(new Setting.Bool("snow_fall", "Falling Snow (Snowy)", true));
	public final Setting.Int snowAmount = add(new Setting.Int("snow_amount", "Snow Amount", 60, 10, 100, " %"));
	public final Setting.Bool matchFog = add(new Setting.Bool("match_fog", "Match Fog Colour", true));
	public final Setting.Bool overworldOnly = add(new Setting.Bool("overworld_only", "Overworld Only", true));

	public CustomSkyModule() {
		super("custom_sky", "Custom Sky", "Your own animated skies: aurora, sunset, nebula, galaxy, storm", Category.VISUALS);
	}

	public int presetIndex() {
		String p = preset.get();
		for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i].equals(p)) return i;
		return 0;
	}

	/** Horizont-Farbe des Presets (für den Nebel, damit die Kante verschwindet). */
	public float[] horizon(int themeRgb) {
		float b = brightness.get() / 100f;
		float tr = (themeRgb >> 16 & 255) / 255f, tg = (themeRgb >> 8 & 255) / 255f, tb = (themeRgb & 255) / 255f;
		float[] c = switch (presetIndex()) {
			case 0 -> new float[]{0.03f, 0.03f, 0.06f};      // Planets
			case 1 -> new float[]{0.85f, 0.88f, 0.92f};      // Snowy
			case 2 -> new float[]{0.07f, 0.10f, 0.22f};      // Moon
			case 3 -> new float[]{0.04f, 0.09f, 0.12f};      // Aurora
			case 4 -> new float[]{1.0f, 0.55f, 0.32f};       // Sunset
			case 5 -> new float[]{0.05f, 0.025f, 0.08f};     // Nebula
			case 6 -> new float[]{0.015f, 0.015f, 0.03f};    // Galaxy
			case 7 -> new float[]{0.20f, 0.22f, 0.27f};      // Storm
			default -> new float[]{0.03f + tr * 0.05f, 0.07f + tg * 0.05f, 0.11f + tb * 0.05f}; // Theme
		};
		return new float[]{Math.min(1f, c[0] * b), Math.min(1f, c[1] * b), Math.min(1f, c[2] * b)};
	}
}
