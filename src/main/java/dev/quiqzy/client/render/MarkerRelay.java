package dev.quiqzy.client.render;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

/**
 * Cloud-Relay für geteilte Marker ("Weg B") – unabhängig vom Minecraft-Chat.
 * Der volle Client POSTet geteilte Marker; ALLE Editionen (full + viewer/public) pollen sie
 * und rendern sie automatisch. Dadurch sehen alle ZERO-Nutzer geteilte Chunks/Pings,
 * ohne dass Freund-Namen eingetragen sein müssen.
 *
 * Die Relay-URL kommt aus {@code dist/relay.json} ({"url":"...","key":"..."}), damit sie ohne
 * Client-Rebuild änderbar ist. Fehlt sie oder ist leer -> Relay still deaktiviert (nur Chat-Weg).
 */
public final class MarkerRelay {
	private static final String CONFIG_URL =
			"https://raw.githubusercontent.com/Quiqzy404/zero-config-server/main/dist/relay.json";
	private static final long POLL_MS = 8000L;
	private static final long PING_TTL = 60_000L;
	private static final HttpClient HTTP =
			HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private static volatile String baseUrl = null; // ohne Slash am Ende
	private static volatile String key = "";
	private static volatile boolean started = false;
	// schon verarbeitete Marker (payload|ts) -> nicht doppelt ingesten
	private static final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

	private MarkerRelay() {
	}

	public static void start() {
		if (started) return;
		started = true;
		Thread t = new Thread(MarkerRelay::run, "ZERO-MarkerRelay");
		t.setDaemon(true);
		t.start();
	}

	private static void run() {
		loadConfig();
		while (true) {
			try {
				if (baseUrl != null) poll();
			} catch (Throwable ignored) {
			}
			try {
				Thread.sleep(POLL_MS);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static void loadConfig() {
		try {
			HttpResponse<String> res = HTTP.send(
					HttpRequest.newBuilder(URI.create(CONFIG_URL)).timeout(Duration.ofSeconds(10)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) return;
			JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
			String u = j.has("url") ? j.get("url").getAsString().trim() : "";
			if (u.isEmpty()) {
				System.out.println("[ZERO Relay] keine URL in relay.json – Relay aus (nur Chat-Weg).");
				return;
			}
			while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
			baseUrl = u;
			key = j.has("key") ? j.get("key").getAsString().trim() : "";
			System.out.println("[ZERO Relay] aktiv: " + baseUrl);
		} catch (Throwable t) {
			System.err.println("[ZERO Relay] Config-Fehler: " + t);
		}
	}

	/** Vollständige Marker-Payload ("P ..." / "C ...") in die Cloud posten (fire & forget). */
	public static void publish(String payload) {
		final String url = baseUrl;
		if (url == null || payload == null || payload.isBlank()) return;
		try {
			HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + "/pub"))
					.timeout(Duration.ofSeconds(10))
					.header("Content-Type", "text/plain; charset=utf-8");
			if (!key.isEmpty()) b.header("X-ZK", key);
			HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
			HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
		} catch (Throwable ignored) {
		}
	}

	private static void poll() throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/sub"))
				.timeout(Duration.ofSeconds(10)).GET();
		if (!key.isEmpty()) b.header("X-ZK", key);
		HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) return;
		JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		for (int i = 0; i < arr.size(); i++) {
			JsonObject m = arr.get(i).getAsJsonObject();
			String p = m.has("p") ? m.get("p").getAsString() : null;
			long ts = m.has("t") ? m.get("t").getAsLong() : now;
			if (p == null) continue;
			String id = p + "|" + ts;
			if (seen.putIfAbsent(id, now) != null) continue; // schon verarbeitet
			// Ingest auf dem Client-Thread (MarkerStore ist nicht thread-safe)
			mc.execute(() -> MarkerShare.ingestPayload(p, PING_TTL));
		}
		if (seen.size() > 512) seen.entrySet().removeIf(e -> now - e.getValue() > 600_000L);
	}
}
