package dev.quiqzy.client.module.misc;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * TPA Auto Confirm: Nach /tpa oder /tpahere (selbst getippt oder vom TPA All Macro) öffnen viele Server ein
 * Bestätigungs-Menü. Das Modul klickt dort automatisch den grünen Knopf (grünes Glas, grüne Kerze, Wolle,
 * Beton, Smaragd ... oder ein Item mit "Confirm"/"Accept"/"Bestätigen" im Namen). Rote/"Cancel"-Items nie.
 */
public final class TpaAutoConfirmModule extends Module {
	public final Setting.Int delay = add(new Setting.Int("delay", "Click Delay", 4, 1, 40, " t"));
	public final Setting.Int window = add(new Setting.Int("window", "Wait For Menu", 5, 1, 20, " s"));

	private static final String[] YES = {"confirm", "accept", "yes", "bestätig", "bestaetig", "annehmen", "akzept", "ja", "tpa", "teleport", "send", "senden"};
	private static final String[] NO = {"cancel", "deny", "decline", "abbrechen", "ablehnen", "nein", "no", "back", "zurück", "close", "schließen"};

	private long lastCommand;
	private int seenContainer = -1, seenTicks, doneContainer = -1;
	private long lastTick = -1;
	public String status = "Waiting for /tpa";

	public TpaAutoConfirmModule() {
		super("tpa_confirm", "TPA Auto Confirm", "Clicks the green confirm button after /tpa or /tpahere", Category.MISC);
	}

	/** Vom Mixin: jeder Befehl, der rausgeht. */
	public void onCommand(String command) {
		String c = command.trim().toLowerCase(Locale.ROOT);
		if (c.startsWith("/")) c = c.substring(1);
		String first = c.split(" ", 2)[0];
		if (first.equals("tpa") || first.equals("tpahere") || first.equals("tpask") || first.equals("tpaskhere") || first.equals("etpa") || first.equals("etpahere")) {
			lastCommand = System.currentTimeMillis();
			seenContainer = -1;
			seenTicks = 0;
		}
	}

	private static boolean has(String text, String[] words) {
		for (String w : words) {
			// kurze Wörter nur als ganzes Wort (sonst steckt "no" in "Notch")
			if (w.length() <= 3 ? (" " + text + " ").matches(".*[^a-zäöü]" + w + "[^a-zäöü].*") : text.contains(w)) return true;
		}
		return false;
	}

	private static boolean green(ItemStack s) {
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		return id.startsWith("lime_") || id.startsWith("green_") || s.is(Items.EMERALD) || s.is(Items.EMERALD_BLOCK) || s.is(Items.SLIME_BALL);
	}

	private static boolean red(ItemStack s) {
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		return id.startsWith("red_") || s.is(Items.BARRIER) || s.is(Items.REDSTONE_BLOCK);
	}

	private static int score(ItemStack s) {
		if (s.isEmpty() || red(s)) return 0;
		String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
		if (has(name, NO)) return 0;
		int sc = 0;
		if (green(s)) sc += 2;
		if (has(name, YES)) sc += 3;
		return sc;
	}

	/** Wird jeden Tick aufgerufen (von diesem Modul und vom TPA All Macro). */
	public void tickConfirm(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null) return;
		long t = mc.level.getGameTime();
		if (t == lastTick) return; // nicht doppelt pro Tick
		lastTick = t;
		boolean recent = System.currentTimeMillis() - lastCommand < window.get() * 1000L;
		if (!recent) {
			status = "Waiting for /tpa";
			return;
		}
		AbstractContainerMenu m = p.containerMenu;
		if (!(mc.screen instanceof AbstractContainerScreen<?>) || m == p.inventoryMenu || m.containerId == doneContainer) return;
		if (m.containerId != seenContainer) {
			seenContainer = m.containerId;
			seenTicks = 0;
		}
		if (++seenTicks < delay.get()) return; // Inhalt vom Server abwarten
		Slot best = null;
		int bestScore = 0;
		for (Slot sl : m.slots) {
			if (sl.container instanceof Inventory) continue;
			int sc = score(sl.getItem());
			if (sc > bestScore) {
				bestScore = sc;
				best = sl;
			}
		}
		if (best == null) {
			if (seenTicks > delay.get() + 40) {
				status = "No green button found";
				doneContainer = m.containerId;
			}
			return;
		}
		String label = best.getItem().getHoverName().getString();
		mc.gameMode.handleInventoryMouseClick(m.containerId, best.index, 0, ClickType.PICKUP, p);
		doneContainer = m.containerId;
		status = "Confirmed (" + label + ")";
		p.displayClientMessage(Component.literal("§aTPA §7» §fConfirmed"), true);
	}

	@Override
	public void onTick(Minecraft mc) {
		tickConfirm(mc);
	}
}
