package dev.quiqzy.client.render;

import net.minecraft.client.Minecraft;

/**
 * Teilt Welt-Markierungen getarnt zwischen ZERO-Clients über den Chat.
 * Die Nutzdaten werden in unsichtbare Zero-Width-Zeichen kodiert, sodass Spieler ohne Client
 * nur eine leere Chat-Zeile sehen. Zwei Typen:
 *   "P x y z dim name"             -> Punkt-Ping (wie Fortnite)
 *   "C cx cz baseY dim label rgb"  -> ganzer Chunk markiert (RTP Hunter: Gegner-Chunk)
 */
public final class MarkerShare {

	private static final char MARKER = '⁤';
	// Unsichtbare "Tag"-Zeichen (U+E00xx): 1 Zeichen pro ASCII-Byte -> Nachricht bleibt kurz,
	// damit Minecrafts Chat-Encoder (max 256 Zeichen) nicht crasht.
	private static final int TAG_BASE = 0xE0000;
	private static final int MAX_CHAT = 250; // Sicherheitsgrenze unter 256

	// Sichtbarer Token für den ZUVERLÄSSIGEN Weg: private Flüster-Nachricht (/msg Freund <token...>).
	// Viele Server (z.B. hugosmp) strippen unsichtbare Zeichen -> der getarnte Broadcast kommt nie an.
	// Ein Flüstern sieht aber nur der Empfänger, dessen Client die Zeile sofort versteckt -> trotzdem getarnt
	// und es überlebt jeden Chat-Filter. Payload ist normaler Text hinter diesem Token.
	public static final String VTOKEN = "zM~";

	private static final long CHUNK_TTL = 300_000L; // 5 Min

	private MarkerShare() {
	}

	// ---------------------------------------------------------------- senden (Broadcast, getarnt)

	public static void broadcastPing(Minecraft mc, double x, double y, double z, String dim, String name) {
		send(mc, "P " + Math.round(x) + " " + Math.round(y) + " " + Math.round(z) + " " + dim + " " + name);
	}

	public static void broadcastChunk(Minecraft mc, int cx, int cz, double baseY, String dim, String label, int rgb) {
		send(mc, chunkPayload(cx, cz, baseY, dim, label, rgb));
	}

	/** Fertig kodierte Chunk-Nachricht (zum selber senden, z.B. verzögert über eine Queue). */
	public static String chunkPayload(int cx, int cz, double baseY, String dim, String label, int rgb) {
		return encode("C " + cx + " " + cz + " " + Math.round(baseY) + " " + dim + " " + label + " " + (rgb & 0xFFFFFF));
	}

	// ---------------------------------------------- senden (Whisper, zuverlässig + privat)

	/** Baut den fertigen /msg-Command. Template kann %s (Name) und %m (Nachricht) enthalten. */
	public static String msgCommand(String template, String recipient, String payload) {
		String base = (template == null || template.isBlank()) ? "/msg %s %m" : template.trim();
		base = base.contains("%s") ? base.replace("%s", recipient) : base + " " + recipient;
		base = base.contains("%m") ? base.replace("%m", payload) : base + " " + payload;
		return base;
	}

	/** Chunk-Nutzdaten als sichtbarer (aber privat geflüsterter) Text. */
	public static String chunkWhisper(int cx, int cz, double baseY, String dim, String label, int rgb) {
		return VTOKEN + "C " + cx + " " + cz + " " + Math.round(baseY) + " " + dim + " " + sanitizeName(label) + " " + (rgb & 0xFFFFFF);
	}

	/** Ping-Nutzdaten als sichtbarer (aber privat geflüsterter) Text. */
	public static String pingWhisper(double x, double y, double z, String dim, String name) {
		return VTOKEN + "P " + Math.round(x) + " " + Math.round(y) + " " + Math.round(z) + " " + dim + " " + sanitizeName(name);
	}

	/** Rohe Ping-Nutzlast "P x y z dim name" (ohne Token/Encoding) – fürs Cloud-Relay. */
	public static String pingPayloadRaw(double x, double y, double z, String dim, String name) {
		return "P " + Math.round(x) + " " + Math.round(y) + " " + Math.round(z) + " " + dim + " " + sanitizeName(name);
	}

	/** Rohe Chunk-Nutzlast "C cx cz baseY dim label rgb" (ohne Token/Encoding) – fürs Cloud-Relay. */
	public static String chunkPayloadRaw(int cx, int cz, double baseY, String dim, String label, int rgb) {
		return "C " + cx + " " + cz + " " + Math.round(baseY) + " " + dim + " " + sanitizeName(label) + " " + (rgb & 0xFFFFFF);
	}

	private static String sanitizeName(String n) {
		if (n == null) return "?";
		String s = n.replaceAll("\\s+", "_");
		return s.isEmpty() ? "?" : s;
	}

	private static void send(Minecraft mc, String payload) {
		if (mc != null && mc.player != null && mc.player.connection != null) {
			String enc = encode(payload);
			if (enc.length() <= MAX_CHAT) mc.player.connection.sendChat(enc);
		}
	}

	/** Kodiert in unsichtbare Tag-Zeichen (1 Zeichen pro ASCII-Byte). */
	private static String encode(String payload) {
		StringBuilder sb = new StringBuilder();
		sb.append(MARKER);
		for (int i = 0; i < payload.length(); i++) {
			char c = payload.charAt(i);
			if (c < 0x20 || c > 0x7E) c = '?'; // nur druckbares ASCII
			sb.appendCodePoint(TAG_BASE + c);
		}
		return sb.toString();
	}

	/** True, wenn die kodierte Nachricht sicher sendbar ist (unter dem Chat-Limit). */
	public static boolean fits(String encoded) {
		return encoded != null && encoded.length() <= MAX_CHAT;
	}

	// ---------------------------------------------------------------- empfangen

	/**
	 * Verarbeitet eine eingehende Chat-Zeile. Ist es ein geteilter Marker, wird er gesetzt
	 * und true zurückgegeben (damit die Zeile versteckt wird). pingTtlMs = Lebensdauer für Pings.
	 */
	public static boolean handle(String raw, long pingTtlMs) {
		if (raw == null) return false;
		String payload;
		// 1) Zuverlässiger Weg: sichtbarer Token aus einem /msg-Flüstern (überlebt Filter).
		int vi = raw.indexOf(VTOKEN);
		if (vi >= 0) {
			payload = raw.substring(vi + VTOKEN.length());
		} else {
			// 2) Getarnter Broadcast: unsichtbare Tag-Zeichen dekodieren.
			int idx = raw.indexOf(MARKER);
			if (idx < 0) return false;
			StringBuilder payloadSb = new StringBuilder();
			int i = idx + 1;
			while (i < raw.length()) {
				int cp = raw.codePointAt(i);
				if (cp >= TAG_BASE + 0x20 && cp <= TAG_BASE + 0x7E) {
					payloadSb.append((char) (cp - TAG_BASE));
					i += Character.charCount(cp);
				} else {
					break;
				}
			}
			if (payloadSb.length() == 0) return false;
			payload = payloadSb.toString();
		}
		return ingestPayload(payload, pingTtlMs);
	}

	/**
	 * Verarbeitet eine rohe Marker-Nutzlast ("P ..." oder "C ...") und setzt sie im MarkerStore.
	 * Wird sowohl vom Chat-Empfang als auch vom Cloud-Relay ([[MarkerRelay]]) benutzt.
	 * @return true, wenn es ein gültiger Marker war.
	 */
	public static boolean ingestPayload(String payload, long pingTtlMs) {
		if (payload == null) return false;
		String[] t = payload.trim().split("\\s+");
		if (t.length < 2) return false;
		try {
			Minecraft mc = Minecraft.getInstance();
			if ("P".equals(t[0]) && t.length >= 6) {
				double x = Double.parseDouble(t[1]);
				double y = Double.parseDouble(t[2]);
				double z = Double.parseDouble(t[3]);
				String dim = t[4];
				String name = t[5];
				// eigenen Ping (Server-Echo) nur verstecken, nicht doppelt setzen
				if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) return true;
				MarkerStore.addPing(x, y, z, dim, name, dev.quiqzy.client.module.world.WaypointsModule.colorOf(name), pingTtlMs);
				return true;
			}
			if ("C".equals(t[0]) && t.length >= 7) {
				int cx = Integer.parseInt(t[1]);
				int cz = Integer.parseInt(t[2]);
				double baseY = Double.parseDouble(t[3]);
				String dim = t[4];
				String label = t[5];
				int rgb = Integer.parseInt(t[6]);
				// addChunk ersetzt denselben Chunk -> eigenes Echo ist harmlos (frischt nur auf)
				MarkerStore.addChunk(cx, cz, baseY, dim, label, rgb, CHUNK_TTL);
				return true;
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}
}
