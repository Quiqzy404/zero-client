package dev.quiqzy.client.module.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.module.world.WaypointsModule;
import dev.quiqzy.client.render.MarkerShare;
import dev.quiqzy.client.render.MarkerStore;
import dev.quiqzy.client.util.UiSounds;

/**
 * Marker Ping (wie Fortnite): Mausrad 2x reindrücken (Doppel-Mittelklick) setzt dort, wo du hinschaust,
 * eine Markierung. Mit "Share" wird sie per Chat an alle ZERO-Nutzer geschickt, die sie dann auch sehen
 * (die Chat-Zeile wird bei ihnen versteckt). Jeder Ping verläuft nach seiner Lebensdauer weg.
 */
public final class PingModule extends Module {

	public final Setting.Bool share = add(new Setting.Bool("share", "Share with ZERO users", true));
	public final Setting.Strings friends = add(new Setting.Strings("friends", "Friend Names (whisper)"));
	public final Setting.Strings msgCmd = add(new Setting.Strings("msg_cmd", "Whisper Command"));
	public final Setting.Int ttlSec = add(new Setting.Int("ttl", "Ping Lifetime", 45, 5, 300, " s"));
	public final Setting.Int reach = add(new Setting.Int("reach", "Place Distance", 48, 8, 160, " m"));

	private boolean prevMiddle;
	private long lastMiddleDown;

	public PingModule() {
		super("marker_ping", "Marker Ping", "Double-click the scroll wheel to drop a shared marker (like Fortnite)", Category.MISC);
		if (msgCmd.get().isEmpty()) msgCmd.get().add("/msg %s %m");
	}

	/** Die Namen, an die Marker privat geflüstert werden (zuverlässig, filter-sicher). */
	public java.util.List<String> friendNames() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String s : friends.get()) {
			String n = s == null ? "" : s.trim();
			if (!n.isEmpty()) out.add(n);
		}
		return out;
	}

	/** Flüstert eine fertige Payload an alle Freunde (jeweils als /msg-Command). */
	public void whisperToFriends(Minecraft mc, String payload) {
		if (mc.player == null || mc.player.connection == null) return;
		String tmpl = msgCmd.get().isEmpty() ? "/msg %s %m" : msgCmd.get().get(0).trim();
		for (String name : friendNames()) {
			String cmd = MarkerShare.msgCommand(tmpl, name, payload);
			mc.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
		}
	}

	@Override
	public void onFrame(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.screen != null) {
			prevMiddle = mc.mouseHandler != null && mc.mouseHandler.isMiddlePressed();
			return;
		}
		boolean mid = mc.mouseHandler.isMiddlePressed();
		if (mid && !prevMiddle) {
			long now = System.currentTimeMillis();
			if (now - lastMiddleDown < 400L) {
				placePing(mc);
				lastMiddleDown = 0L;
			} else {
				lastMiddleDown = now;
			}
		}
		prevMiddle = mid;
	}

	private void placePing(Minecraft mc) {
		Vec3 loc;
		if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
			loc = mc.hitResult.getLocation();
		} else {
			Vec3 eye = mc.player.getEyePosition(1f);
			Vec3 look = mc.player.getViewVector(1f);
			loc = eye.add(look.scale(reach.get()));
		}
		String dim = MarkerStore.dim(mc.level);
		String name = mc.player.getName().getString();
		int rgb = WaypointsModule.colorOf(name);
		MarkerStore.addPing(loc.x, loc.y, loc.z, dim, name, rgb, ttlSec.get() * 1000L);
		UiSounds.added();

		if (share.on()) {
			// Zuverlässig: privat an die Freunde flüstern (überlebt Chat-Filter, nur sie sehen es).
			whisperToFriends(mc, MarkerShare.pingWhisper(loc.x, loc.y, loc.z, dim, name));
			// Zusätzlich der getarnte Broadcast (falls der Server unsichtbare Zeichen durchlässt).
			MarkerShare.broadcastPing(mc, loc.x, loc.y, loc.z, dim, name);
			// Weg B: Cloud-Relay -> alle ZERO-Clients sehen den Ping, ohne Freund-Namen.
			dev.quiqzy.client.render.MarkerRelay.publish(MarkerShare.pingPayloadRaw(loc.x, loc.y, loc.z, dim, name));
			if (friendNames().isEmpty()) {
				mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
						"§ePing §7» §fkeine Freund-Namen gesetzt – trag sie im Modul ein, damit sie den Marker sehen"), true);
			}
		}
	}

	/**
	 * Von {@link dev.quiqzy.client.mixin.PingChatMixin} für jede eingehende Chat-Zeile aufgerufen.
	 * Behandelt geteilte Pings UND geteilte Chunk-Marker (RTP Hunter).
	 * @return true, wenn es ein geteilter ZERO-Marker war (dann wird die Chat-Zeile versteckt).
	 */
	public static boolean handleIncoming(String raw) {
		long ttl = ModuleManager.PING != null ? ModuleManager.PING.ttlSec.get() * 1000L : 45_000L;
		return MarkerShare.handle(raw, ttl);
	}
}
