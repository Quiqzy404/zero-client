package dev.quiqzy.client.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.world.level.Level;

import dev.quiqzy.client.module.world.WaypointsModule;

/**
 * Gemeinsamer Speicher für Welt-Markierungen:
 * - CHUNK: ein ganzer Chunk wird markiert (RTP Hunter: "hier ist/war der Gegner"), 5 Min, verläuft weg.
 * - PING: ein Punkt-Ping (wie bei Fortnite), per Doppel-Mausrad gesetzt, optional an alle Client-Nutzer geteilt.
 *
 * Thread-safe (CopyOnWriteArrayList), weil Pings auch vom Netzwerk-Thread (Chat) ankommen können.
 */
public final class MarkerStore {

	public enum Kind { CHUNK, PING }

	public static final class Mark {
		public final Kind kind;
		public final double x, y, z;   // Welt-Punkt (PING) bzw. Bezugs-Y (CHUNK)
		public final int cx, cz;       // Chunk-Koordinaten (CHUNK)
		public final String dim;       // "overworld"/"nether"/"end"
		public final String label;     // Name (Spieler / Absender)
		public final int rgb;          // Grundfarbe ohne Alpha (0xRRGGBB)
		public final long created;
		public final long ttl;         // Lebensdauer in ms

		Mark(Kind kind, double x, double y, double z, int cx, int cz, String dim, String label, int rgb, long ttl) {
			this.kind = kind;
			this.x = x;
			this.y = y;
			this.z = z;
			this.cx = cx;
			this.cz = cz;
			this.dim = dim;
			this.label = label;
			this.rgb = rgb & 0xFFFFFF;
			this.created = System.currentTimeMillis();
			this.ttl = ttl;
		}

		public float age01() {
			return Math.min(1f, (System.currentTimeMillis() - created) / (float) ttl);
		}

		public boolean dead() {
			return System.currentTimeMillis() - created >= ttl;
		}

		/** Weiche Alpha-Hüllkurve: kurz einblenden, am Ende sanft ausblenden (verlaufen). */
		public float envelope() {
			long elapsed = System.currentTimeMillis() - created;
			float in = Math.min(1f, elapsed / 350f);
			float t = age01();
			float out = t > 0.80f ? 1f - (t - 0.80f) / 0.20f : 1f;
			return Math.max(0f, Math.min(in, out));
		}
	}

	private static final CopyOnWriteArrayList<Mark> MARKS = new CopyOnWriteArrayList<>();

	private MarkerStore() {
	}

	/** Dimensionskürzel wie in WaypointsModule ("overworld"/"nether"/"end"). */
	public static String dim(Level level) {
		return WaypointsModule.dimensionId(level);
	}

	/** Chunk markieren (RTP Hunter). baseY = Y des Gegners, dient als Höhe der Box. */
	public static void addChunk(int chunkX, int chunkZ, double baseY, String dim, String label, int rgb, long ttlMs) {
		// gleicher Chunk schon markiert? -> auffrischen statt doppeln
		MARKS.removeIf(m -> m.kind == Kind.CHUNK && m.cx == chunkX && m.cz == chunkZ && m.dim.equals(dim));
		MARKS.add(new Mark(Kind.CHUNK, chunkX * 16 + 8, baseY, chunkZ * 16 + 8, chunkX, chunkZ, dim, label, rgb, ttlMs));
	}

	/** Punkt-Ping setzen. */
	public static void addPing(double x, double y, double z, String dim, String label, int rgb, long ttlMs) {
		MARKS.add(new Mark(Kind.PING, x, y, z, (int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, dim, label, rgb, ttlMs));
	}

	/** Abgelaufene entfernen (jeden Frame aufgerufen). */
	public static void purge() {
		if (!MARKS.isEmpty()) MARKS.removeIf(Mark::dead);
	}

	public static void clear() {
		MARKS.clear();
	}

	public static List<Mark> all() {
		return MARKS;
	}

	public static boolean isEmpty() {
		return MARKS.isEmpty();
	}
}
