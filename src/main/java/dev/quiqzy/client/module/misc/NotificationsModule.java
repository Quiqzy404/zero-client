package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;

/** Aurora-HUD-Widget: Shows when a module gets turned on or off. */
public final class NotificationsModule extends Module {
	public NotificationsModule() {
		super("notifications", "Notifications", "Shows when a module gets turned on or off", Category.HUD);
		setEnabled(true);
	}
}
