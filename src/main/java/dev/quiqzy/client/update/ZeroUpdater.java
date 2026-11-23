package dev.quiqzy.client.update;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import dev.quiqzy.client.QuiqzyClient;

/**
 * ZERO Loader / Selbst-Updater: prüft beim Start ein GitHub-Repo. Ist dort ein höherer Build,
 * wird die neue Jar heruntergeladen und – weil die laufende Jar unter Windows gesperrt ist –
 * beim Spiel-Schließen per kleinem Hintergrund-Skript ausgetauscht (beim nächsten Start aktiv).
 * So muss nie wieder manuell eine Jar kopiert werden.
 */
public final class ZeroUpdater {
	// version.json: {"build": <int>, "url": "<jar-url>", "name": "<anzeige>"}
	// Full-Edition zieht dist/version.json, Viewer-Edition dist/viewer/version.json (eigener Kanal).
	private static final String VERSION_URL_FULL = "https://raw.githubusercontent.com/Quiqzy404/zero-config-server/main/dist/version.json";
	private static final String VERSION_URL_VIEWER = "https://raw.githubusercontent.com/Quiqzy404/zero-config-server/main/dist/viewer/version.json";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

	private static String readEdition() {
		try (InputStream in = ZeroUpdater.class.getResourceAsStream("/zero_edition.txt")) {
			if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
		} catch (Throwable ignored) {
		}
		return "full";
	}

	private static String versionUrl() {
		return "viewer".equalsIgnoreCase(readEdition()) ? VERSION_URL_VIEWER : VERSION_URL_FULL;
	}

	public static volatile boolean updateReady;
	public static volatile String updateName = "";

	private ZeroUpdater() {
	}

	public static int localBuild() {
		try (InputStream in = ZeroUpdater.class.getResourceAsStream("/zero_build.txt")) {
			if (in == null) return 0;
			return Integer.parseInt(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
		} catch (Throwable t) {
			return 0;
		}
	}

	/** Async anstoßen (blockiert den Start nicht). */
	public static void checkAsync() {
		Thread t = new Thread(ZeroUpdater::check, "ZERO-Updater");
		t.setDaemon(true);
		t.start();
	}

	private static void check() {
		try {
			int local = localBuild();
			String versionUrl = versionUrl();
			HttpResponse<String> res = HTTP.send(
					HttpRequest.newBuilder(URI.create(versionUrl)).timeout(Duration.ofSeconds(20)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) {
				QuiqzyClient.LOGGER.info("ZERO updater: version check {} ({})", res.statusCode(), versionUrl);
				return;
			}
			JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
			int remote = j.get("build").getAsInt();
			String url = j.get("url").getAsString();
			String name = j.has("name") ? j.get("name").getAsString() : ("build " + remote);
			if (remote <= local) {
				QuiqzyClient.LOGGER.info("ZERO updater: up to date (build {})", local);
				return;
			}
			Path target = currentJar();
			if (target == null) {
				QuiqzyClient.LOGGER.warn("ZERO updater: own jar path unknown, skipping");
				return;
			}
			Path staging = target.resolveSibling(target.getFileName() + ".update");
			QuiqzyClient.LOGGER.info("ZERO updater: downloading build {} -> {}", remote, staging);
			HttpResponse<Path> dl = HTTP.send(
					HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).GET().build(),
					HttpResponse.BodyHandlers.ofFile(staging, StandardOpenOptionWrite()));
			if (dl.statusCode() != 200 || !Files.exists(staging) || Files.size(staging) < 100_000) {
				QuiqzyClient.LOGGER.warn("ZERO updater: download failed ({}), cleaning", dl.statusCode());
				try {
					Files.deleteIfExists(staging);
				} catch (Throwable ignored) {
				}
				return;
			}
			scheduleSwap(staging, target);
			updateReady = true;
			updateName = name;
			QuiqzyClient.LOGGER.info("ZERO updater: build {} staged, will apply on exit", remote);
			Minecraft mc = Minecraft.getInstance();
			if (mc != null) mc.execute(ZeroUpdater::toast);
		} catch (Throwable t) {
			QuiqzyClient.LOGGER.info("ZERO updater: skipped ({})", t.toString());
		}
	}

	private static java.nio.file.OpenOption[] StandardOpenOptionWrite() {
		return new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE};
	}

	private static Path currentJar() {
		return FabricLoader.getInstance().getModContainer("quiqzy")
				.flatMap(c -> c.getOrigin().getPaths().stream().filter(p -> p.getFileName().toString().endsWith(".jar")).findFirst())
				.orElse(null);
	}

	/** Beim Spiel-Schließen: Hintergrund-Skript, das die Jar austauscht sobald sie entsperrt ist. */
	private static void scheduleSwap(Path staging, Path target) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				String os = System.getProperty("os.name", "").toLowerCase();
				if (os.contains("win")) {
					Path bat = Files.createTempFile("zero_update", ".bat");
					String s = "@echo off\r\n"
							+ "set /a n=0\r\n"
							+ ":loop\r\n"
							+ "move /Y \"" + staging + "\" \"" + target + "\" >nul 2>&1\r\n"
							+ "if not errorlevel 1 goto done\r\n"
							+ "set /a n+=1\r\n"
							+ "if %n% GEQ 120 goto done\r\n"
							+ "ping -n 2 127.0.0.1 >nul\r\n"
							+ "goto loop\r\n"
							+ ":done\r\n"
							+ "del \"%~f0\"\r\n";
					Files.writeString(bat, s);
					new ProcessBuilder("cmd", "/c", "start", "/min", "", "cmd", "/c", bat.toString())
							.start();
				} else {
					// Linux/Mac: Jar ist meist nicht gesperrt -> direkt tauschen
					Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (Throwable ignored) {
			}
		}, "ZERO-Updater-Swap"));
	}

	private static void toast() {
		try {
			Minecraft mc = Minecraft.getInstance();
			net.minecraft.client.gui.components.toasts.SystemToast.add(
					mc.getToastManager(), net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					net.minecraft.network.chat.Component.literal("ZERO aktualisiert"),
					net.minecraft.network.chat.Component.literal(updateName + " – beim Neustart aktiv"));
		} catch (Throwable ignored) {
		}
	}
}
