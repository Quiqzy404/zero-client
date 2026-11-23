package dev.quiqzy.client.config;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.QuiqzyClient;

/**
 * Config-Cloud: Configs auf den ZERO Config Server hochladen, auflisten und herunterladen.
 * Alle Netzwerk-Aufrufe laufen im Hintergrund; Callbacks werden auf dem Main-Thread (mc.execute) ausgefuehrt.
 */
public final class ConfigCloud {
	public static volatile String SERVER = "https://zero-config-server.onrender.com";

	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "ZERO-ConfigCloud");
		t.setDaemon(true);
		return t;
	});

	public record Entry(String id, String name, String author, String code, int downloads, long created) {
	}

	// Tokens der eigenen Uploads (id -> token), damit man sie wieder loeschen kann
	private static final java.nio.file.Path TOKENS = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("quiqzy-cloud.json");
	private static final java.util.Map<String, String> OWNED = loadTokens();

	private ConfigCloud() {
	}

	@SuppressWarnings("unchecked")
	private static java.util.Map<String, String> loadTokens() {
		try {
			if (java.nio.file.Files.exists(TOKENS)) {
				String s = java.nio.file.Files.readString(TOKENS);
				java.util.Map<String, String> m = GSON.fromJson(s, java.util.Map.class);
				if (m != null) return new java.util.concurrent.ConcurrentHashMap<>(m);
			}
		} catch (Exception ignored) {
		}
		return new java.util.concurrent.ConcurrentHashMap<>();
	}

	private static void saveTokens() {
		try {
			java.nio.file.Files.createDirectories(TOKENS.getParent());
			java.nio.file.Files.writeString(TOKENS, GSON.toJson(OWNED));
		} catch (Exception ignored) {
		}
	}

	/** Gehoert dieser Upload mir (habe ich den Loesch-Token)? */
	public static boolean owns(String id) {
		return id != null && OWNED.containsKey(id);
	}

	private static void main(Runnable r) {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null) mc.execute(r);
		else r.run();
	}

	private static String base() {
		String s = SERVER == null ? "" : SERVER.trim();
		return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
	}

	/** Liste der oeffentlichen Configs. sort = "new" oder "top". */
	public static void list(String query, String sort, Consumer<List<Entry>> ok, Consumer<String> err) {
		POOL.execute(() -> {
			try {
				String q = query == null ? "" : URLEncoder.encode(query, StandardCharsets.UTF_8);
				URI uri = URI.create(base() + "/api/configs?sort=" + (sort == null ? "new" : sort) + "&limit=150&q=" + q);
				HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build();
				HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + res.statusCode());
				JsonArray arr = GSON.fromJson(res.body(), JsonArray.class);
				List<Entry> out = new ArrayList<>();
				for (var el : arr) {
					JsonObject o = el.getAsJsonObject();
					out.add(new Entry(str(o, "id"), str(o, "name"), str(o, "author"), str(o, "code"),
							o.has("downloads") ? o.get("downloads").getAsInt() : 0,
							o.has("created") ? o.get("created").getAsLong() : 0));
				}
				main(() -> ok.accept(out));
			} catch (Exception e) {
				QuiqzyClient.LOGGER.warn("Config list failed", e);
				main(() -> err.accept(reason(e)));
			}
		});
	}

	/** Eine Config hochladen. Gibt bei Erfolg den 5-stelligen Code zurueck. */
	public static void upload(String name, String author, JsonObject data, Consumer<String> ok, Consumer<String> err) {
		POOL.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("name", name);
				body.addProperty("author", author);
				body.add("data", data);
				HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/configs"))
						.timeout(Duration.ofSeconds(60))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8)).build();
				HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				JsonObject o = GSON.fromJson(res.body(), JsonObject.class);
				if (res.statusCode() / 100 != 2) throw new RuntimeException(o != null && o.has("error") ? o.get("error").getAsString() : "HTTP " + res.statusCode());
				String id = str(o, "id"), token = str(o, "token");
				if (!id.isEmpty() && !token.isEmpty()) {
					OWNED.put(id, token);
					saveTokens();
				}
				main(() -> ok.accept(str(o, "code")));
			} catch (Exception e) {
				QuiqzyClient.LOGGER.warn("Config upload failed", e);
				main(() -> err.accept(reason(e)));
			}
		});
	}

	/** Eine Config per Code oder ID holen (liefert das komplette data-Objekt). */
	public static void fetch(String idOrCode, Consumer<JsonObject> ok, Consumer<String> err) {
		POOL.execute(() -> {
			try {
				String key = URLEncoder.encode(idOrCode.trim(), StandardCharsets.UTF_8);
				HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/configs/" + key))
						.timeout(Duration.ofSeconds(60)).GET().build();
				HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 404) throw new RuntimeException("not found");
				if (res.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + res.statusCode());
				JsonObject o = GSON.fromJson(res.body(), JsonObject.class);
				main(() -> ok.accept(o));
			} catch (Exception e) {
				QuiqzyClient.LOGGER.warn("Config fetch failed", e);
				main(() -> err.accept(reason(e)));
			}
		});
	}

	/** Eigenen Upload wieder loeschen (braucht den gespeicherten Token). */
	public static void delete(String id, Runnable ok, Consumer<String> err) {
		String token = OWNED.get(id);
		if (token == null) {
			main(() -> err.accept("not your config"));
			return;
		}
		POOL.execute(() -> {
			try {
				String tk = URLEncoder.encode(token, StandardCharsets.UTF_8);
				HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/configs/" + id + "?token=" + tk))
						.timeout(Duration.ofSeconds(60)).DELETE().build();
				HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + res.statusCode());
				OWNED.remove(id);
				saveTokens();
				main(ok);
			} catch (Exception e) {
				QuiqzyClient.LOGGER.warn("Config delete failed", e);
				main(() -> err.accept(reason(e)));
			}
		});
	}

	private static String str(JsonObject o, String k) {
		return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	private static String reason(Exception e) {
		String m = e.getMessage();
		if (m == null || m.isBlank()) m = e.getClass().getSimpleName();
		if (m.contains("ConnectException") || m.contains("timed out") || m.contains("HttpTimeout")) return "Server not reachable";
		return m;
	}
}
