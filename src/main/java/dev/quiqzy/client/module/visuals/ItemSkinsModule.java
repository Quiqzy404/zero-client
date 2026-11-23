package dev.quiqzy.client.module.visuals;

import java.util.Locale;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Item Skins: Weihnachts-Totem (nur bei dir, nur beim Zeichnen).
 * Modelle: assets/quiqzy/items/christmas_*.json + textures/item/christmas_*.png.
 */
public final class ItemSkinsModule extends Module {
	public final Setting.Bool totem = add(new Setting.Bool("totem", "Christmas Totem", true));

	public ItemSkinsModule() {
		super("item_skins", "Item Skins", "Christmas totem skin (client-side)", Category.VISUALS);
	}

	/** Soll dieses Item ersetzt werden? */
	public boolean applies(ItemStack stack) {
		if (!isEnabled() || stack.isEmpty()) return false;
		return totem.on() && stack.is(Items.TOTEM_OF_UNDYING);
	}

	/** Kopie des Items mit unserem Weihnachts-Modell (echtes Item bleibt unverändert). */
	public ItemStack skin(ItemStack stack) {
		if (!applies(stack)) return stack;
		ItemStack copy = stack.copy();
		String model = "christmas_totem";
		copy.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("quiqzy", model));
		return copy;
	}

	/** Nur für die Selftest-Anzeige: Modell-Prüfung. */
	public boolean isSkinned(ItemStack stack) {
		Identifier id = stack.get(DataComponents.ITEM_MODEL);
		return id != null && id.getNamespace().equals("quiqzy") && id.getPath().startsWith("christmas_");
	}
}
