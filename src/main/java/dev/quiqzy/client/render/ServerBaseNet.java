package dev.quiqzy.client.render;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Empfang der Base-Daten, die der DonutLarp-Server SELBST schickt (Kanäle donutlarp:bases + donutlarp:activity,
 * gleiches Format wie das offizielle DonutLarp-Addon). ZERO schickt nichts zurück – nur lesen + anzeigen.
 *
 * Sicherheit (Server-Daten = nicht vertrauenswürdig):
 * - Paket > MAX_BYTES wird verworfen (Bytes werden übersprungen, kein Disconnect, kein Speicher-Flooding).
 * - Anzahl-Felder begrenzt, negative/zu große Werte -> ganzes Paket verworfen.
 * - Koordinaten müssen in der Minecraft-Welt liegen, Größen plausibel.
 * - Texte (Spawner-Typ) werden auf [a-z0-9_:.-] gekürzt -> keine Formatcodes/Links/Commands im Chat.
 * - Gesamtspeicher gedeckelt (MAX_BASES/MAX_SPAWNERS/MAX_ACTIVITY), alles try/catch -> kann den Client nie crashen.
 * - Verarbeitung nur auf dem Haupt-Thread; nichts wird ausgeführt, geöffnet oder gesendet.
 */
public final class ServerBaseNet {

	public record Base(int id, int x, int y, int z, int sx, int sy, int sz) {
		public int chunkX() { return (x + sx / 2) >> 4; }
		public int chunkZ() { return (z + sz / 2) >> 4; }
	}

	public record Spawner(int x, int y, int z, int stack, String type) {
	}

	public record Activity(int chunkX, int chunkZ, int players) {
	}

	private static final int MAX_BYTES = 256 * 1024;
	private static final int MAX_PER_PACKET = 8192;
	private static final int MAX_BASES = 20_000;
	private static final int MAX_SPAWNERS = 20_000;
	private static final int MAX_ACTIVITY = 4096;
	private static final int WORLD_XZ = 30_000_000;
	private static final int WORLD_Y = 4096;
	private static final int MAX_SIZE = 4096;

	private static final Map<Integer, Base> BASES = new ConcurrentHashMap<>();
	private static final Map<Long, Spawner> SPAWNERS = new ConcurrentHashMap<>();
	private static volatile List<Activity> activity = List.of();
	/** Erhöht sich bei jeder Änderung -> Modul synchronisiert die Marker nur bei Bedarf. */
	private static volatile int version;

	private static boolean registered;

	private ServerBaseNet() {
	}

	// ------------------------------------------------------------------ Payloads

	public record BasesPayload(byte[] data) implements CustomPacketPayload {
		public static final Type<BasesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("donutlarp", "bases"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BasesPayload> CODEC =
				StreamCodec.of((buf, p) -> buf.writeBytes(p.data), buf -> new BasesPayload(readCapped(buf)));

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record ActivityPayload(byte[] data) implements CustomPacketPayload {
		public static final Type<ActivityPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("donutlarp", "activity"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActivityPayload> CODEC =
				StreamCodec.of((buf, p) -> buf.writeBytes(p.data), buf -> new ActivityPayload(readCapped(buf)));

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Liest den Rest des Pakets – zu große Pakete werden übersprungen und als leer (null) markiert. */
	private static byte[] readCapped(FriendlyByteBuf buf) {
		int n = buf.readableBytes();
		if (n > MAX_BYTES) {
			buf.skipBytes(n);
			return null;
		}
		byte[] out = new byte[n];
		buf.readBytes(out);
		return out;
	}

	// ------------------------------------------------------------------ Registrierung

	public static void register() {
		if (registered) return;
		registered = true;
		try {
			PayloadTypeRegistry.playS2C().register(BasesPayload.TYPE, BasesPayload.CODEC);
			PayloadTypeRegistry.playS2C().register(ActivityPayload.TYPE, ActivityPayload.CODEC);
			ClientPlayNetworking.registerGlobalReceiver(BasesPayload.TYPE,
					(p, ctx) -> ctx.client().execute(() -> handleBases(p.data())));
			ClientPlayNetworking.registerGlobalReceiver(ActivityPayload.TYPE,
					(p, ctx) -> ctx.client().execute(() -> handleActivity(p.data())));
			ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(ServerBaseNet::clear));
		} catch (Throwable t) {
			// z.B. wenn das DonutLarp-Addon parallel installiert ist (Kanal schon belegt) -> ZERO-Empfang aus
			System.out.println("[ZERO] Server Bases: Kanal nicht registriert (" + t + ")");
		}
	}

	// ------------------------------------------------------------------ Parsen

	private static void handleBases(byte[] data) {
		if (data == null || data.length == 0) return;
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			int op = in.readByte();
			if (op == 0) {
				clear();
				return;
			}
			if (op == 2) return;
			if (op == 3) {
				int n = in.readInt();
				if (n < 0 || n > MAX_PER_PACKET) return;
				List<Spawner> fresh = new ArrayList<>(Math.min(n, 256));
				for (int i = 0; i < n; i++) {
					int x = in.readInt(), y = in.readInt(), z = in.readInt(), stack = in.readInt();
					String type = cleanType(in.readUTF());
					if (!validXZ(x) || !validXZ(z) || !validY(y)) return;
					fresh.add(new Spawner(x, y, z, Math.max(0, Math.min(stack, 1_000_000)), type));
				}
				for (Spawner s : fresh) {
					if (SPAWNERS.size() >= MAX_SPAWNERS) break;
					SPAWNERS.put(key(s.x, s.y, s.z), s);
				}
				version++;
				return;
			}
			int n = in.readInt();
			if (n < 0 || n > MAX_PER_PACKET) return;
			List<Base> fresh = new ArrayList<>(Math.min(n, 256));
			for (int i = 0; i < n; i++) {
				Base b = new Base(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
				if (!validXZ(b.x) || !validXZ(b.z) || !validY(b.y)) return;
				if (b.sx < 0 || b.sy < 0 || b.sz < 0 || b.sx > MAX_SIZE || b.sy > MAX_SIZE || b.sz > MAX_SIZE) return;
				fresh.add(b);
			}
			List<Base> added = new ArrayList<>();
			for (Base b : fresh) {
				if (BASES.size() >= MAX_BASES) break;
				if (BASES.putIfAbsent(b.id, b) == null) added.add(b);
			}
			version++;
			if (!added.isEmpty()) {
				ModuleManager.DL_CHUNK_FINDER.onNewBases(added);
				ModuleManager.DL_SUS_FINDER.onNewBases(added);
			}
		} catch (Throwable ignored) {
			// kaputtes/abgeschnittenes Paket -> ignorieren
		}
	}

	private static void handleActivity(byte[] data) {
		if (data == null || data.length == 0) return;
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			int n = in.readInt();
			if (n < 0 || n > MAX_ACTIVITY) return;
			List<Activity> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				int cx = in.readInt(), cz = in.readInt(), players = in.readInt();
				if (Math.abs(cx) > WORLD_XZ >> 4 || Math.abs(cz) > WORLD_XZ >> 4) return;
				list.add(new Activity(cx, cz, Math.max(0, Math.min(players, 1000))));
			}
			activity = List.copyOf(list);
			version++;
			ModuleManager.DL_ACTIVITY.onActivity(activity);
		} catch (Throwable ignored) {
		}
	}

	private static boolean validXZ(int v) { return v > -WORLD_XZ && v < WORLD_XZ; }

	private static boolean validY(int v) { return v > -WORLD_Y && v < WORLD_Y; }

	/** Spawner-Typ nur als harmloser Bezeichner (z.B. "minecraft:zombie"), max 48 Zeichen. */
	private static String cleanType(String s) {
		if (s == null) return "?";
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length() && b.length() < 48; i++) {
			char c = Character.toLowerCase(s.charAt(i));
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == ':' || c == '.' || c == '-') b.append(c);
		}
		return b.length() == 0 ? "?" : b.toString();
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	// ------------------------------------------------------------------ Zugriff

	public static void clear() {
		BASES.clear();
		SPAWNERS.clear();
		activity = List.of();
		version++;
	}

	public static Collection<Base> bases() { return BASES.values(); }

	public static Collection<Spawner> spawners() { return SPAWNERS.values(); }

	public static List<Activity> activity() { return activity; }

	public static int version() { return version; }
}
