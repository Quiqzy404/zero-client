package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Sky Shaders: animierte Shader-Himmel (Shader zero_sky, Presets 9-14):
 * Liquid Gold / Liquid Silver / Rose Gold = flüssiges Metall mit glänzenden Schlieren,
 * Glass Rain = Tropfen auf Glas vor hellem Himmel, Night = Sternennacht, Rain Night = dunkle Regennacht.
 */
public final class CustomSkyModule extends Module {
	public static final String[] PRESETS = {"Liquid Gold", "Liquid Silver", "Rose Gold", "Glass Rain", "Night", "Rain Night"};
	public final Setting.Choice preset = add(new Setting.Choice("style_v3", "Style", "Liquid Gold", PRESETS));
	public final Setting.Int speed = add(new Setting.Int("speed_v3", "Speed", 7, 0, 100).decimals(2));
	public final Setting.Int scale = add(new Setting.Int("pattern_scale", "Pattern scale", 119, 30, 300).decimals(2));
	public final Setting.Int intensity = add(new Setting.Int("intensity", "Intensity", 94, 10, 150));
	public final Setting.Bool matchFog = add(new Setting.Bool("match_fog", "Match Fog Colour", true));
	public final Setting.Bool overworldOnly = add(new Setting.Bool("overworld_only", "Overworld Only", true));

	public CustomSkyModule() {
		super("custom_sky", "Sky Shaders", "Animated shader skies: liquid gold, liquid silver, rose gold, glass rain, night", Category.VISUALS);
	}

	/** Shader-Preset (9..14). */
	public int presetIndex() {
		String p = preset.get();
		for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i].equals(p)) return 9 + i;
		return 9;
	}

	/** Horizont-Farbe des Stils (für den Nebel, damit die Kante verschwindet). */
	public float[] horizon(int themeRgb) {
		float b = intensity.get() / 100f;
		float[] c = switch (presetIndex()) {
			case 9 -> new float[]{0.16f, 0.10f, 0.03f};   // Liquid Gold
			case 10 -> new float[]{0.14f, 0.15f, 0.17f};  // Liquid Silver
			case 11 -> new float[]{0.18f, 0.07f, 0.08f};  // Rose Gold
			case 12 -> new float[]{0.62f, 0.72f, 0.84f};  // Glass Rain
			case 13 -> new float[]{0.05f, 0.07f, 0.16f};  // Night
			default -> new float[]{0.06f, 0.07f, 0.10f};  // Rain Night
		};
		return new float[]{Math.min(1f, c[0] * b), Math.min(1f, c[1] * b), Math.min(1f, c[2] * b)};
	}
}
