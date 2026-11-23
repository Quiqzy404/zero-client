package dev.quiqzy.client.module.player;

import dev.quiqzy.client.module.Module;

/** Fast Use: benutzt Items (Essen, Werfen, Pearls ...) ohne Rechtsklick-Verzögerung. */
public final class FastUseModule extends Module {
	public FastUseModule() {
		super("fast_use", "Fast Use", "Uses items with no right-click delay", Category.PLAYER);
	}
}
