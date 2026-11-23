package dev.quiqzy.client.module.player;

import dev.quiqzy.client.module.Module;

/** Fast Place: platziert Blöcke ohne Rechtsklick-Verzögerung. */
public final class FastPlaceModule extends Module {
	public FastPlaceModule() {
		super("fast_place", "Fast Place", "Places blocks with no right-click delay", Category.PLAYER);
	}
}
