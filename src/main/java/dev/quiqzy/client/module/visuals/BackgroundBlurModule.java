package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Background Blur: Tiefenunschärfe – weit Entferntes wird weich, nahes bleibt scharf (Hand bleibt immer scharf). */
public final class BackgroundBlurModule extends Module {
	public final Setting.Int start = add(new Setting.Int("start", "Start Distance", 28, 4, 200, " m"));
	public final Setting.Int fade = add(new Setting.Int("fade", "Fade Length", 40, 2, 200, " m"));
	public final Setting.Int strength = add(new Setting.Int("strength", "Blur Strength", 3, 1, 4));
	public final Setting.Int amount = add(new Setting.Int("amount", "Amount", 85, 10, 100, "%"));
	public final Setting.Bool sky = add(new Setting.Bool("sky", "Blur Sky", true));

	public BackgroundBlurModule() {
		super("background_blur", "Background Blur", "Blurs far away things, keeps close things sharp (depth of field)", Category.VISUALS);
	}
}
