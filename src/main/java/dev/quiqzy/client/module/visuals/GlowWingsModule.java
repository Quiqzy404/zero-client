package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Glow Wings: leuchtende, durchscheinende Flügel am Rücken (sichtbar in F5). Eigene Geometrie, kein Modell/Asset. */
public final class GlowWingsModule extends Module {
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "Angel", "Angel", "Butterfly", "Blade"));
	public final Setting.Bool themeColor = add(new Setting.Bool("theme_color", "Theme Colour", true));
	public final Setting.Color color = add(new Setting.Color("color", "Colour", 0xF0A6FF));
	public final Setting.Int size = add(new Setting.Int("size", "Size", 100, 50, 180, " %"));
	public final Setting.Int glow = add(new Setting.Int("glow", "Glow", 70, 0, 100, " %"));
	public final Setting.Int flap = add(new Setting.Int("flap", "Flap Speed", 100, 0, 300, " %"));
	public final Setting.Bool others = add(new Setting.Bool("others", "On Other Players", false));

	public GlowWingsModule() {
		super("glow_wings", "Glow Wings", "Glowing glass wings on your back (F5)", Category.VISUALS);
	}
}
