package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Sounds: Klänge für Module an/aus, GUI auf/zu, Einstellungen und Waypoints. Abgespielt über util/UiSounds. */
public final class SoundsModule extends Module {
	public final Setting.Choice style = add(new Setting.Choice("style_v3", "Style", "Minimal", "Minimal", "Glass", "Soft", "Pling", "Click"));
	public final Setting.Int volume = add(new Setting.Int("volume", "Volume", 70, 0, 100, " %"));
	public final Setting.Bool toggles = add(new Setting.Bool("toggles", "Module On/Off", true));
	public final Setting.Bool gui = add(new Setting.Bool("gui", "GUI Open/Close", true));
	public final Setting.Bool clicks = add(new Setting.Bool("clicks", "Settings Clicks", true));

	public SoundsModule() {
		super("sounds", "Sounds", "Sounds for module toggles and the GUI", Category.CLIENT);
		setEnabled(true);
	}
}
