package dev.quiqzy.client.module.misc;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * RTP Hunter: spammt /rtp, klickt im Menü die gewählte Dimension, wartet, scannt auf Spieler/Beacon-Beam.
 * Fund -> /tpahere <Name>, bei Spieler /invsee und Inventar nach Keyword ("STAR") prüfen,
 * dann Screenshot + Discord-Webhook (@everyone, Spielername, Tool). Alles per Setting konfigurierbar.
 */
public final class RtpHunterModule extends Module {
	public final Setting.Choice menuPick = add(new Setting.Choice("menu_pick", "Menu Pick", "Leftmost", "Leftmost", "Middle", "Rightmost"));
	public final Setting.Int range = add(new Setting.Int("range", "Detect Range", 64, 8, 128, " m"));
	public final Setting.Int wait = add(new Setting.Int("wait", "Wait After TP", 45, 10, 120, " t"));
	public final Setting.Bool scanPlayers = add(new Setting.Bool("players", "Find Players", true));
	public final Setting.Bool scanBeacons = add(new Setting.Bool("beacons", "Find Beacon Beams", true));
	public final Setting.Bool ping = add(new Setting.Bool("ping", "Ping @everyone", true));
	public final Setting.Bool sendAll = add(new Setting.Bool("send_all", "Webhook on every find", false));
	public final Setting.Bool shareChunk = add(new Setting.Bool("share_chunk", "Share chunk to ZERO users", true));
	public final Setting.Strings tpaName = add(new Setting.Strings("tpa_name", "TPA Here Name"));
	public final Setting.Strings keyword = add(new Setting.Strings("keyword", "Loot Keyword"));
	public final Setting.Strings webhook = add(new Setting.Strings("webhook", "Discord Webhook"));
	public final Setting.Strings rtpCmd = add(new Setting.Strings("rtp_cmd", "RTP Command"));
	public final Setting.Strings invseeCmd = add(new Setting.Strings("invsee_cmd", "Invsee Command"));
	public final Setting.Strings tpahereCmd = add(new Setting.Strings("tpahere_cmd", "TPA Command"));

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	private enum State { IDLE, WAIT_MENU, WAIT_TP, SCAN, WAIT_INVSEE, WAIT_TEAM }

	private State state = State.IDLE;
	private int timer;
	private String targetName = "";
	private int targetX, targetY, targetZ;
	private volatile boolean invseeHandled; // Inventar schon aus dem Paket gelesen+gesendet?
	// Queue: nichts im selben Tick raushauen (Server flaggt Burst/Spam), ein Eintrag alle paar Ticks.
	private record Queued(String text, boolean chat) {
	}

	private final java.util.ArrayDeque<Queued> cmdQueue = new java.util.ArrayDeque<>();
	private int cmdCooldown;

	private void queueCmd(String cmd) {
		if (cmd != null && !cmd.isBlank()) cmdQueue.add(new Queued(cmd, false));
	}

	private void queueChat(String msg) {
		if (msg != null && !msg.isBlank()) cmdQueue.add(new Queued(msg, true));
	}

	private void sendChatRaw(Minecraft mc, String msg) {
		// nie zu lange Nachrichten senden -> sonst EncoderException + Disconnect
		if (mc.player != null && mc.player.connection != null && msg != null && !msg.isBlank()
				&& dev.quiqzy.client.render.MarkerShare.fits(msg)) {
			mc.player.connection.sendChat(msg);
		}
	}

	public RtpHunterModule() {
		super("rtp_hunter", "RTP Hunter", "Auto /rtp, scans for players/beacons, invsee + Discord webhook", Category.MISC);
		if (keyword.get().isEmpty()) keyword.get().add("STAR");
		if (rtpCmd.get().isEmpty()) rtpCmd.get().add("/rtp");
		if (invseeCmd.get().isEmpty()) invseeCmd.get().add("/invsee %s");
		if (tpahereCmd.get().isEmpty()) tpahereCmd.get().add("/tpahere %s");
	}

	@Override
	protected void onEnable() {
		state = State.IDLE;
		timer = 0;
	}

	@Override
	protected void onDisable() {
		state = State.IDLE;
		cmdQueue.clear();
		cmdCooldown = 0;
	}

	private static String first(Setting.Strings s, String def) {
		return s.get().isEmpty() ? def : s.get().get(0).trim();
	}

	private static void say(Minecraft mc, String msg) {
		if (mc != null && mc.player != null) {
			mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6RTP §7» §f" + msg), false);
		}
	}

	private void sendCmd(Minecraft mc, String cmd) {
		if (mc.player == null || mc.player.connection == null || cmd == null || cmd.isBlank()) return;
		String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
		mc.player.connection.sendCommand(c);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (!isEnabled() || mc.player == null || mc.level == null) return;
		timer++;
		// Queue abarbeiten: ein Eintrag alle 6 Ticks (~0,3s), damit der Server nichts als Spam dropt
		if (cmdCooldown > 0) cmdCooldown--;
		if (cmdCooldown == 0 && !cmdQueue.isEmpty()) {
			Queued q = cmdQueue.poll();
			if (q.chat()) sendChatRaw(mc, q.text());
			else sendCmd(mc, q.text());
			cmdCooldown = 6;
		}
		switch (state) {
			case IDLE -> {
				sendCmd(mc, first(rtpCmd, "/rtp"));
				state = State.WAIT_MENU;
				timer = 0;
			}
			case WAIT_MENU -> {
				if (mc.screen instanceof AbstractContainerScreen<?> cs) {
					clickDimension(mc, cs);
					state = State.WAIT_TP;
					timer = 0;
				} else if (timer > 80) {
					state = State.IDLE;
				}
			}
			case WAIT_TP -> {
				if (timer >= wait.get()) state = State.SCAN;
			}
			case SCAN -> {
				Player p = scanPlayers.on() ? nearestPlayer(mc) : null;
				if (p != null) {
					targetName = p.getName().getString();
					targetX = p.getBlockX();
					targetY = p.getBlockY();
					targetZ = p.getBlockZ();
					say(mc, "Spieler gefunden: §e" + targetName + " §7@ " + targetX + " " + targetY + " " + targetZ);
					// Chunk des Gegners markieren (5 Min, rot, verläuft weg) + an alle ZERO-Clients teilen
					String dim = dev.quiqzy.client.render.MarkerStore.dim(mc.level);
					dev.quiqzy.client.render.MarkerStore.addChunk(targetX >> 4, targetZ >> 4, targetY, dim, targetName, 0xFF4040, 300_000L);
					// WICHTIG: erst die Commands (tpahere/invsee), DANN der getarnte Chunk-Broadcast -
					// sonst flaggt hugosmp die komische Nachricht als Spam und blockt die Commands danach.
					tpahere(mc);
					queueCmd(String.format(first(invseeCmd, "/invsee %s"), targetName));
					if (shareChunk.on()) shareChunkTo(targetX >> 4, targetZ >> 4, targetY, dim, targetName, 0xFF4040);
					invseeHandled = false;
					state = State.WAIT_INVSEE;
					timer = 0;
				} else if (scanBeacons.on() && nearestBeacon(mc) != null) {
					BlockPos bp = nearestBeacon(mc);
					say(mc, "Beacon gefunden §7@ " + bp.getX() + " " + bp.getY() + " " + bp.getZ());
					// Beacon-Chunk markieren (5 Min, gold) + teilen
					String dim = dev.quiqzy.client.render.MarkerStore.dim(mc.level);
					dev.quiqzy.client.render.MarkerStore.addChunk(bp.getX() >> 4, bp.getZ() >> 4, bp.getY(), dim, "Beacon", 0xFFC24A, 300_000L);
					tpahere(mc);
					if (shareChunk.on()) shareChunkTo(bp.getX() >> 4, bp.getZ() >> 4, bp.getY(), dim, "Beacon", 0xFFC24A);
					shotAndSend(mc, "Beacon beam gefunden bei " + bp.getX() + " " + bp.getY() + " " + bp.getZ());
					state = State.WAIT_TEAM;
					timer = 0;
				} else {
					state = State.IDLE; // niemand da -> weiter rtp
				}
			}
			case WAIT_INVSEE -> {
				// Inventar wird primär aus dem Paket gelesen (onContainerContent). Wenn das schon lief -> weiter.
				if (invseeHandled) {
					state = State.WAIT_TEAM;
					timer = 0;
				} else if (!cmdQueue.isEmpty()) {
					// erst alle Queue-Commands (tpahere + invsee) rauslassen
					timer = 0;
				} else if (timer >= 30) {
					// Fallback: Paket kam nicht (gedroppt) -> aus der GUI lesen, falls doch eine da ist
					if (mc.screen instanceof AbstractContainerScreen<?> cs) {
						String loot = findLoot(cs);
						String where = " @ " + targetX + " " + targetY + " " + targetZ + " (Chunk " + (targetX >> 4) + ", " + (targetZ >> 4) + ")";
						if (loot != null) {
							say(mc, "§aSTAR-Loot: §f" + loot + " §7-> Webhook");
							shotAndSend(mc, targetName + " hat: " + loot + where);
						} else if (sendAll.on()) {
							say(mc, "kein STAR, sende trotzdem (Webhook on every find)");
							shotAndSend(mc, targetName + " (kein STAR-Tool)" + where);
						} else {
							say(mc, "§7" + targetName + " hat kein STAR-Tool -> kein Webhook");
						}
						mc.setScreen(null); // GUI schließen -> du hast Kontrolle
					} else {
						say(mc, "§ckein /invsee-Inventar geladen (Command/Name prüfen)");
					}
					state = State.WAIT_TEAM; // auf den /tpahere-Kollegen warten
					timer = 0;
				}
			}
			case WAIT_TEAM -> {
				// warten bis der Teammate (tpahere-Name) da ist, sonst nach 20s weiter hunten
				if (teammateHere(mc) || timer >= 400) {
					state = State.IDLE;
				}
			}
		}
	}

	/** Empfänger für geteilte Chunk-Marker: Team-Namen + die Freunde aus dem Marker-Ping-Modul. */
	private List<String> shareRecipients() {
		List<String> out = new ArrayList<>(teamNames());
		try {
			if (dev.quiqzy.client.module.ModuleManager.PING != null) {
				for (String n : dev.quiqzy.client.module.ModuleManager.PING.friendNames()) {
					boolean dup = false;
					for (String e : out) if (e.equalsIgnoreCase(n)) dup = true;
					if (!dup) out.add(n);
				}
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	/**
	 * Teilt einen Chunk-Marker zuverlässig: privat an jeden Empfänger flüstern (/msg, überlebt Filter),
	 * plus der getarnte Broadcast (falls der Server unsichtbare Zeichen durchlässt). Alles über die Queue.
	 */
	private void shareChunkTo(int cx, int cz, double baseY, String dim, String label, int rgb) {
		List<String> rec = shareRecipients();
		String tmpl = "/msg %s %m";
		try {
			if (dev.quiqzy.client.module.ModuleManager.PING != null && !dev.quiqzy.client.module.ModuleManager.PING.msgCmd.get().isEmpty()) {
				tmpl = dev.quiqzy.client.module.ModuleManager.PING.msgCmd.get().get(0).trim();
			}
		} catch (Throwable ignored) {
		}
		String payload = dev.quiqzy.client.render.MarkerShare.chunkWhisper(cx, cz, baseY, dim, label, rgb);
		for (String name : rec) {
			queueCmd(dev.quiqzy.client.render.MarkerShare.msgCommand(tmpl, name, payload));
		}
		// zusätzlich getarnter Broadcast (Chat-Zeile, bei anderen ZERO-Clients versteckt)
		queueChat(dev.quiqzy.client.render.MarkerShare.chunkPayload(cx, cz, baseY, dim, label, rgb));
		// Weg B: Cloud-Relay -> alle ZERO-Clients (auch Public/Viewer) sehen den Chunk ohne Namen.
		dev.quiqzy.client.render.MarkerRelay.publish(dev.quiqzy.client.render.MarkerShare.chunkPayloadRaw(cx, cz, baseY, dim, label, rgb));
	}

	/** Alle eingetragenen Teammate-Namen (getrimmt, leere raus). */
	private List<String> teamNames() {
		List<String> out = new ArrayList<>();
		for (String s : tpaName.get()) {
			String n = s == null ? "" : s.trim();
			if (!n.isEmpty()) out.add(n);
		}
		return out;
	}

	private void tpahere(Minecraft mc) {
		List<String> names = teamNames();
		if (names.isEmpty()) {
			say(mc, "§ckein TPA Here Name gesetzt -> kein /tpahere (im Modul eintragen)");
			return;
		}
		String cmd = first(tpahereCmd, "/tpahere %s");
		for (String name : names) {
			// %s fehlt? -> Name hinten anhängen, damit das Ziel nicht fehlt
			String full = cmd.contains("%s") ? String.format(cmd, name) : cmd + " " + name;
			queueCmd(full); // in die Queue (mit Abstand), nicht als Burst
			say(mc, "§e-> " + (full.startsWith("/") ? full : "/" + full));
		}
	}

	/** Im RTP-Menü die gewählte Dimension (links/mitte/rechts) klicken – anhand der echten Item-Slots. */
	private void clickDimension(Minecraft mc, AbstractContainerScreen<?> cs) {
		var menu = cs.getMenu();
		List<Integer> items = new ArrayList<>();
		for (Slot s : menu.slots) {
			if (s.container == mc.player.getInventory()) continue; // keine Spieler-Inventar-Slots
			ItemStack it = s.getItem();
			if (it.isEmpty()) continue;
			String n = it.getItem().toString().toLowerCase();
			if (n.contains("glass_pane") || n.contains("stained_glass")) continue; // Deko überspringen
			items.add(s.index);
		}
		if (items.isEmpty()) return;
		int idx = switch (menuPick.get()) {
			case "Rightmost" -> items.get(items.size() - 1);
			case "Middle" -> items.get(items.size() / 2);
			default -> items.get(0); // Leftmost
		};
		mc.gameMode.handleInventoryMouseClick(menu.containerId, idx, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
	}

	/** Ist der /tpahere-Kollege in der Nähe (angekommen)? */
	private boolean teammateHere(Minecraft mc) {
		List<String> team = teamNames();
		if (team.isEmpty()) return false;
		for (Player p : mc.level.players()) {
			if (p == mc.player) continue;
			if (p.distanceToSqr(mc.player) >= 24 * 24) continue;
			for (String t : team) if (p.getName().getString().equalsIgnoreCase(t)) return true;
		}
		return false;
	}

	private Player nearestPlayer(Minecraft mc) {
		Player best = null;
		List<String> team = teamNames();
		double bd = range.get() * range.get();
		for (Player p : mc.level.players()) {
			if (p == mc.player || p.isSpectator()) continue;
			boolean mate = false;
			for (String t : team) if (p.getName().getString().equalsIgnoreCase(t)) mate = true;
			if (mate) continue; // Teammates sind keine Ziele
			double d = p.distanceToSqr(mc.player);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	private BlockPos nearestBeacon(Minecraft mc) {
		int r = Math.max(1, (range.get() >> 4) + 1);
		int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
		BlockPos best = null;
		double bd = (double) range.get() * range.get();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				LevelChunk ch = mc.level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
				if (ch == null) continue;
				for (Map.Entry<BlockPos, BlockEntity> e : ch.getBlockEntities().entrySet()) {
					if (!(e.getValue() instanceof BeaconBlockEntity be)) continue;
					if (be.getBeamSections().isEmpty()) continue; // nur aktive Beams
					double d = mc.player.distanceToSqr(e.getKey().getX() + 0.5, e.getKey().getY(), e.getKey().getZ() + 0.5);
					if (d < bd) {
						bd = d;
						best = e.getKey();
					}
				}
			}
		}
		return best;
	}

	/**
	 * Wird vom ClientPacketListener-Mixin aufgerufen, sobald ein Container-Inhalt ankommt.
	 * Liest das /invsee-Inventar DIREKT aus dem Paket (zuverlässig, auch wenn der Server danach kickt)
	 * und schickt die ganze Item-Liste als Text an den Webhook.
	 */
	public void onContainerContent(int containerId, java.util.List<ItemStack> items) {
		if (!isEnabled() || state != State.WAIT_INVSEE || containerId == 0 || invseeHandled) return;
		Minecraft mc = Minecraft.getInstance();
		if (!mc.isSameThread()) return; // nur auf dem Haupt-Thread verarbeiten
		invseeHandled = true;

		List<String> keys = keyword.get().isEmpty() ? List.of("STAR") : keyword.get();
		List<InvEntry> entries = new ArrayList<>();
		String star = null;
		int count = 0;
		for (ItemStack it : items) {
			if (it == null || it.isEmpty()) continue;
			String name = it.getHoverName().getString();
			int c = it.getCount();
			count++;
			String up = name.toUpperCase();
			boolean isStar = false;
			for (String k : keys) if (!k.isBlank() && up.contains(k.toUpperCase())) { star = name; isStar = true; }
			int col = 0xE6E9EF;
			try {
				var tc = it.getHoverName().getStyle().getColor();
				if (tc != null) col = tc.getValue() & 0xFFFFFF;
			} catch (Throwable ignored) {
			}
			if (isStar) col = 0xFF5A5A;
			entries.add(new InvEntry(name, c, col, isStar));
		}

		String where = " @ " + targetX + " " + targetY + " " + targetZ + " (Chunk " + (targetX >> 4) + ", " + (targetZ >> 4) + ")";
		boolean hasStar = star != null;
		String wh = first(webhook, "");
		if (wh.isBlank()) {
			say(mc, "§ckein Webhook gesetzt! (Ctrl+V im Modul)");
		} else if (hasStar || sendAll.on()) {
			String head = (hasStar && ping.on() ? "@everyone " : "") + targetName + (hasStar ? " STAR: " + star : "") + where + " (" + count + " items)";
			byte[] img = renderInventoryPng(entries, targetName, where.trim(), count);
			// Falls das Bild mal nicht erzeugt werden kann: Text-Liste als Fallback anhängen.
			String content = head;
			if (img == null) {
				StringBuilder sb = new StringBuilder(head).append(":");
				for (InvEntry e : entries) if (sb.length() < 1800) sb.append("\n- ").append(e.name).append(e.count > 1 ? " x" + e.count : "");
				content = sb.toString();
			}
			final String fContent = content;
			Thread t = new Thread(() -> postImage(wh, fContent, img, "inventory.png"), "ZERO-InvWebhook");
			t.setDaemon(true);
			t.start();
			say(mc, hasStar ? "§aSTAR: §f" + star + " §7-> Webhook (Bild)" : "§aInventar (" + count + ") §7-> Webhook (Bild)");
		} else {
			say(mc, "§7kein STAR -> kein Webhook (sendAll aus)");
		}

		if (mc.screen instanceof AbstractContainerScreen<?>) mc.setScreen(null);
		state = State.WAIT_TEAM;
		timer = 0;
	}

	/** Container nach Keyword-Item durchsuchen -> Name des Tools oder null. */
	private String findLoot(AbstractContainerScreen<?> cs) {
		List<String> keys = keyword.get().isEmpty() ? List.of("STAR") : keyword.get();
		for (Slot s : cs.getMenu().slots) {
			ItemStack it = s.getItem();
			if (it.isEmpty()) continue;
			String name = it.getHoverName().getString();
			String up = name.toUpperCase();
			for (String k : keys) {
				if (!k.isBlank() && up.contains(k.toUpperCase())) return name;
			}
		}
		return null;
	}

	/** Screenshot machen und per Webhook senden (mit @everyone + Text). */
	private void shotAndSend(Minecraft mc, String text) {
		String wh = first(webhook, "");
		if (wh.isBlank()) {
			say(mc, "§ckein Webhook gesetzt! Im Modul mit Ctrl+V einfügen.");
			return;
		}
		String content = (ping.on() ? "@everyone " : "") + text;
		String file = "zero_rtp_" + (System.currentTimeMillis() % 100000) + ".png";
		Screenshot.grab(new File(mc.gameDirectory.getPath()), file, mc.getMainRenderTarget(), 1, msg -> {
			Thread t = new Thread(() -> post(wh, content, mc.gameDirectory.toPath().resolve("screenshots").resolve(file)), "ZERO-RtpWebhook");
			t.setDaemon(true);
			t.start();
		});
	}

	/** Ein Item fürs Inventar-Bild. */
	private record InvEntry(String name, int count, int rgb, boolean star) {
	}

	/**
	 * Zeichnet das Inventar als sauberes PNG (dunkles Grid, Item-Name in Seltenheitsfarbe, Anzahl-Pill,
	 * STAR-Items rot umrandet). So landet im Discord ein echtes Bild statt nur Text.
	 */
	private static byte[] renderInventoryPng(List<InvEntry> items, String who, String where, int count) {
		try {
			final int cols = 3;
			final int margin = 18, gap = 8, cellW = 244, cellH = 36, headerH = 60;
			int n = Math.max(items.size(), 1);
			int rows = (n + cols - 1) / cols;
			int gridW = cols * cellW + (cols - 1) * gap;
			int width = margin * 2 + gridW;
			int height = headerH + margin + rows * cellH + (rows - 1) * gap + margin;

			BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// Hintergrund
			g.setColor(new Color(0x16181d));
			g.fillRect(0, 0, width, height);
			// Header-Balken
			g.setColor(new Color(0x20232b));
			g.fillRoundRect(margin, margin - 6, width - margin * 2, headerH - 12, 14, 14);
			g.setColor(new Color(0xFF5A5A));
			g.fillRoundRect(margin, margin - 6, 5, headerH - 12, 5, 5); // Akzent-Strich
			Font title = new Font("SansSerif", Font.BOLD, 20);
			Font small = new Font("SansSerif", Font.PLAIN, 12);
			g.setFont(title);
			g.setColor(Color.WHITE);
			g.drawString(who, margin + 16, margin + 16);
			g.setFont(small);
			g.setColor(new Color(0x9aa0ad));
			g.drawString(count + " items  ·  " + where, margin + 16, margin + 34);
			g.setColor(new Color(0x5a6070));
			String tag = "ZERO";
			int tw = g.getFontMetrics().stringWidth(tag);
			g.drawString(tag, width - margin - tw - 6, margin + 34);

			// Grid
			int gy0 = headerH + margin;
			g.setFont(small);
			for (int i = 0; i < items.size(); i++) {
				InvEntry e = items.get(i);
				int cx = margin + (i % cols) * (cellW + gap);
				int cy = gy0 + (i / cols) * (cellH + gap);
				g.setColor(new Color(0x272b33));
				g.fillRoundRect(cx, cy, cellW, cellH, 10, 10);
				if (e.star) {
					g.setColor(new Color(0xFF5A5A));
					g.drawRoundRect(cx, cy, cellW - 1, cellH - 1, 10, 10);
				}
				// Name (gekürzt), in Seltenheitsfarbe
				g.setColor(new Color(e.rgb));
				String name = fit(g, e.name, cellW - 58);
				g.drawString(name, cx + 12, cy + 23);
				// Anzahl-Pill
				if (e.count > 1) {
					String cstr = "x" + e.count;
					int pw = g.getFontMetrics().stringWidth(cstr) + 14;
					g.setColor(new Color(0x15171c));
					g.fillRoundRect(cx + cellW - pw - 8, cy + 8, pw, cellH - 16, 9, 9);
					g.setColor(new Color(0xd7dbe2));
					g.drawString(cstr, cx + cellW - pw - 8 + 7, cy + 23);
				}
			}
			g.dispose();
			var baos = new java.io.ByteArrayOutputStream();
			javax.imageio.ImageIO.write(img, "png", baos);
			return baos.toByteArray();
		} catch (Throwable t) {
			QuiqzyClient.LOGGER.warn("RTP Hunter: Inventar-Bild fehlgeschlagen: {}", t.toString());
			return null;
		}
	}

	/** Text so kürzen, dass er in maxW Pixel passt (… am Ende). */
	private static String fit(Graphics2D g, String s, int maxW) {
		var fm = g.getFontMetrics();
		if (fm.stringWidth(s) <= maxW) return s;
		String dots = "…";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (fm.stringWidth(sb.toString() + s.charAt(i) + dots) > maxW) break;
			sb.append(s.charAt(i));
		}
		return sb + dots;
	}

	/** Webhook mit optionalem Bild (byte[]) als Anhang. */
	private static void postImage(String url, String content, byte[] img, String filename) {
		try {
			if (url == null || url.isBlank()) return;
			String boundary = "----zeroInv" + System.nanoTime();
			String json = "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
			var out = new java.io.ByteArrayOutputStream();
			out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n" + json + "\r\n").getBytes(StandardCharsets.UTF_8));
			if (img != null && img.length > 0) {
				out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\"" + filename + "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
				out.write(img);
				out.write("\r\n".getBytes(StandardCharsets.UTF_8));
			}
			out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
					.header("Content-Type", "multipart/form-data; boundary=" + boundary)
					.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			int code = res.statusCode();
			QuiqzyClient.LOGGER.info("RTP Hunter inv-webhook -> {}", code);
			Minecraft m = Minecraft.getInstance();
			m.execute(() -> say(m, code >= 200 && code < 300 ? "§aWebhook gesendet ✓ (" + code + ")" : "§cWebhook Fehler: HTTP " + code));
		} catch (Throwable t) {
			QuiqzyClient.LOGGER.warn("RTP Hunter inv-webhook failed: {}", t.toString());
			Minecraft m = Minecraft.getInstance();
			m.execute(() -> say(m, "§cWebhook Fehler: " + t.getClass().getSimpleName()));
		}
	}

	private static void post(String url, String content, Path png) {
		try {
			if (url == null || url.isBlank()) {
				QuiqzyClient.LOGGER.info("RTP Hunter: kein Webhook gesetzt.");
				return;
			}
			byte[] img = (png != null && Files.exists(png)) ? Files.readAllBytes(png) : new byte[0];
			String boundary = "----zeroRtp" + System.nanoTime();
			String json = "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
			var out = new java.io.ByteArrayOutputStream();
			var w = new java.io.ByteArrayOutputStream();
			w.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n" + json + "\r\n").getBytes(StandardCharsets.UTF_8));
			if (img.length > 0) {
				w.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\"inv.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
				w.write(img);
				w.write("\r\n".getBytes(StandardCharsets.UTF_8));
			}
			w.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			out.write(w.toByteArray());
			HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
					.header("Content-Type", "multipart/form-data; boundary=" + boundary)
					.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			QuiqzyClient.LOGGER.info("RTP Hunter webhook -> {}", res.statusCode());
			int code = res.statusCode();
			Minecraft m = Minecraft.getInstance();
			m.execute(() -> say(m, code >= 200 && code < 300 ? "§aWebhook gesendet ✓ (" + code + ")" : "§cWebhook Fehler: HTTP " + code));
		} catch (Throwable t) {
			QuiqzyClient.LOGGER.warn("RTP Hunter webhook failed: {}", t.toString());
			Minecraft m = Minecraft.getInstance();
			m.execute(() -> say(m, "§cWebhook Fehler: " + t.getClass().getSimpleName()));
		}
	}
}
