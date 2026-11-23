package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;

/** Aurora-HUD-Widget: Your speed in blocks per second with a small graph. */
public final class SpeedHudModule extends Module {
	public SpeedHudModule() {
		super("speed_hud", "Speed", "Your speed in blocks per second with a small graph", Category.HUD);
		setEnabled(true);
	}
}
