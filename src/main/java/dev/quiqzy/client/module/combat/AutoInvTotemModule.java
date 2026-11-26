package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Auto Inv Totem: Sobald du dein Inventar öffnest, legt es einen Totem in die Offhand
 * (und auf Wunsch einen in den gewählten Hotbar-Slot). Arbeitet NUR bei offenem Inventar.
 */
public final class AutoInvTotemModule extends Module {
	private static final int OFFHAND_BUTTON = 40;

	public final Setting.Int delay = add(new Setting.Int("delay", "Delay", 2, 0, 20, " t"));
	public final Setting.Bool offhand = add(new Setting.Bool("offhand", "Offhand Totem", true));
	public final Setting.Bool hotbar = add(new Setting.Bool("hotbar", "Hotbar Totem", true));
	public final Setting.Int hotbarSlot = add(new Setting.Int("hotbar_slot", "Hotbar Slot", 9, 1, 9));
	public final Setting.Bool replace = add(new Setting.Bool("replace", "Replace Other Items", true));

	private int wait = -1;

	public AutoInvTotemModule() {
		super("auto_inv_totem", "Auto Inv Totem", "Puts a totem in your offhand/hotbar when you open your inventory", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return;
		if (!(mc.screen instanceof InventoryScreen)) {
			wait = -1;
			return;
		}
		if (wait < 0) wait = delay.get(); // gerade geöffnet
		if (wait > 0) {
			wait--;
			return;
		}
		Inventory inv = p.getInventory();
		// 1) Offhand
		if (offhand.on() && !p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && (p.getOffhandItem().isEmpty() || replace.on())) {
			int slot = find(inv, -1);
			if (slot >= 0) {
				mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot(slot), OFFHAND_BUTTON, ClickType.SWAP, p);
				wait = Math.max(1, delay.get());
				return;
			}
		}
		// 2) Hotbar-Slot
		int hs = hotbarSlot.get() - 1;
		if (hotbar.on() && !inv.getItem(hs).is(Items.TOTEM_OF_UNDYING) && (inv.getItem(hs).isEmpty() || replace.on())) {
			int slot = find(inv, hs);
			if (slot >= 9) { // nur aus dem Hauptinventar holen, nicht aus anderen Hotbar-Slots
				mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, hs, ClickType.SWAP, p);
				wait = Math.max(1, delay.get());
			}
		}
	}

	/** Totem im Inventar suchen – erst Hauptinventar (9-35), dann Hotbar; skip = Slot, der ignoriert wird. */
	private static int find(Inventory inv, int skip) {
		for (int i = 9; i < 36; i++) if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
		for (int i = 0; i < 9; i++) if (i != skip && inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
		return -1;
	}

	/** Inventar-Index -> Slot im Inventar-Menü (Hotbar liegt dort bei 36-44). */
	private static int menuSlot(int invIndex) {
		return invIndex < 9 ? 36 + invIndex : invIndex;
	}
}
