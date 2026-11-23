package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Aurora-HUD-Widget: weiche Leben/Hunger-Leisten über der Hotbar, optional eigene Hotbar. */
public final class HealthBarsModule extends Module {
	public final Setting.Bool hotbar = add(new Setting.Bool("hotbar", "Custom Hotbar", true));

	public HealthBarsModule() {
		super("health_bars", "Health Bars", "Smooth health and hunger bars above the hotbar", Category.HUD);
		setEnabled(true);
	}
}
