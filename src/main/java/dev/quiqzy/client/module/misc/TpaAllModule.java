package dev.quiqzy.client.module.misc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * TPA All Macro: schickt /tpahere (oder /tpa) nacheinander an jeden Spieler in der Tab-Liste,
 * mit Pause dazwischen. Namen in "Exclude" werden übersprungen. Geht sich danach von selbst aus
 * (außer "Repeat" ist an).
 */
public final class TpaAllModule extends Module {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

	public final Setting.Choice mode = add(new Setting.Choice("mode", "Command", "/tpahere", "/tpahere", "/tpa"));
	public final Setting.Int delay = add(new Setting.Int("delay", "Delay", 3, 1, 60, " s"));
	public final Setting.Bool repeat = add(new Setting.Bool("repeat", "Repeat", false));
	/** Grünen Bestätigen-Knopf im Server-Menü selbst klicken (auch wenn das Modul "TPA Auto Confirm" aus ist). */
	public final Setting.Bool confirm = add(new Setting.Bool("confirm", "Auto Confirm Menu", true));
	public final Setting.Strings exclude = add(new Setting.Strings("exclude", "Exclude"));

	private final ArrayDeque<String> queue = new ArrayDeque<>();
	private int total, sent, wait;
	public String status = "Off";

	public TpaAllModule() {
		super("tpa_all", "TPA All Macro", "Sends /tpahere or /tpa to every player on the server, one after another", Category.MISC);
	}

	private static void say(Minecraft mc, String s) {
		if (mc.player != null) mc.player.displayClientMessage(Component.literal("§dTPA All §7» §f" + s), true);
	}

	public boolean excluded(String name) {
		for (String e : exclude.get()) if (e.equalsIgnoreCase(name)) return true;
		return false;
	}

	public void addExclude(String name) {
		name = name.trim();
		if (name.isEmpty() || excluded(name)) return;
		List<String> l = new ArrayList<>(exclude.get());
		l.add(name);
		exclude.set(l);
	}

	public void removeExclude(int i) {
		List<String> l = new ArrayList<>(exclude.get());
		if (i >= 0 && i < l.size()) l.remove(i);
		exclude.set(l);
	}

	private boolean fill(Minecraft mc) {
		queue.clear();
		if (mc.getConnection() == null || mc.player == null) return false;
		String me = mc.player.getGameProfile().name();
		List<String> names = new ArrayList<>();
		for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
			String n = pi.getProfile().name();
			// Fake-Einträge (Tab-Deko, NPCs) haben meist keinen gültigen Namen
			if (n == null || !NAME.matcher(n).matches() || n.equalsIgnoreCase(me) || excluded(n)) continue;
			names.add(n);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		queue.addAll(names);
		total = queue.size();
		sent = 0;
		return total > 0;
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		wait = 0;
		if (!fill(mc)) {
			status = "No players to send to";
			say(mc, status);
			setEnabled(false);
			return;
		}
		say(mc, "Sending " + mode.get() + " to " + total + " players");
	}

	@Override
	protected void onDisable() {
		queue.clear();
		if (!status.startsWith("No players") && !status.startsWith("Done")) status = "Off";
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) {
			setEnabled(false);
			return;
		}
		if (confirm.on()) dev.quiqzy.client.module.ModuleManager.TPA_CONFIRM.tickConfirm(mc);
		if (wait > 0) {
			wait--;
			return;
		}
		// Bestätigungs-Menü noch offen -> erst warten, bis es zu ist
		if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && wait > -60) {
			wait--;
			return;
		}
		String next = queue.poll();
		while (next != null && excluded(next)) next = queue.poll(); // Exclude während des Laufens geändert
		if (next == null) {
			if (repeat.on() && fill(mc)) {
				status = "Repeating – " + total + " players";
				wait = delay.get() * 20;
				return;
			}
			status = "Done – sent to " + sent + " players";
			say(mc, status);
			setEnabled(false);
			return;
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.player.closeContainer();
		mc.player.connection.sendCommand(mode.get().substring(1) + " " + next);
		sent++;
		status = mode.get() + " " + next + " (" + sent + "/" + total + ")";
		wait = delay.get() * 20;
	}
}
