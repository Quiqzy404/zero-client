package dev.quiqzy.client.input;

import com.mojang.blaze3d.platform.InputConstants;

/** Pseudo-Tasten fürs Mausrad, damit man Keybinds (z.B. Attack) in den Vanilla-Settings aufs Scrollen legen kann. */
public final class ScrollKeys {
	public static final InputConstants.Key UP = InputConstants.Type.MOUSE.getOrCreate(100);
	public static final InputConstants.Key DOWN = InputConstants.Type.MOUSE.getOrCreate(101);

	private ScrollKeys() {
	}

	public static boolean isScroll(InputConstants.Key k) {
		return k == UP || k == DOWN;
	}
}
