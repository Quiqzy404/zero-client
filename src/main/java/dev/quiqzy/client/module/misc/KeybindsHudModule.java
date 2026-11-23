package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;

/** Aurora-HUD-Widget: Shows your bound modules and their keys. */
public final class KeybindsHudModule extends Module {
	public KeybindsHudModule() {
		super("keybinds_hud", "Keybinds", "Shows your bound modules and their keys", Category.HUD);
		setEnabled(true);
	}
}
