package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Glass Inventory: Inventar & Kisten im selben grauen Glas-Look wie das HUD (statt der Vanilla-Textur). */
public final class GlassInventoryModule extends Module {
	public final Setting.Bool allScreens = add(new Setting.Bool("all_screens", "All Containers", true));
	public final Setting.Bool cells = add(new Setting.Bool("cells", "Slot Cells", true));
	public final Setting.Bool titles = add(new Setting.Bool("titles", "Keep Titles", false));

	public GlassInventoryModule() {
		super("glass_inventory", "Glass Inventory", "Inventory and chests in the clean glass look", Category.MISC);
		setEnabled(true);
	}
}
