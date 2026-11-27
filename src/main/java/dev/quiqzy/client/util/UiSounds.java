package dev.quiqzy.client.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.misc.SoundsModule;

/**
 * Eigene Client-Klänge, komplett in Java synthetisiert (keine Minecraft-/fremden Samples).
 * Styles:
 *  - Glass: Glas-Obertöne (unharmonische Teiltöne wie ein angeschlagenes Glas), feiner Tap, Luft-Whoosh
 *  - Soft:  holzig/weich wie eine Marimba, eine Oktave tiefer
 *  - Pling: die alten Sinus/Dreieck-Töne
 *  - Click: nur kurze, trockene Ticks
 * Alles läuft durch einen kleinen Stereo-Hall und wird auf eine feste Lautheit normalisiert.
 */
public final class UiSounds {
	private static final float RATE = 44100f;
	private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 2, true, false);
	private static final ExecutorService EXEC = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "ZERO-Sounds");
		t.setDaemon(true);
		return t;
	});
	/** Fertig berechnete Töne (Stereo, interleaved, bei Lautstärke 1.0). */
	private static final Map<String, short[]> CACHE = new HashMap<>();
	private static volatile boolean broken;

	private UiSounds() {
	}

	/** Modul an/aus. */
	public static void toggle(boolean on) {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.toggles.on()) return;
		route(on ? "on" : "off");
	}

	/** GUI geöffnet / geschlossen. */
	public static void gui(boolean open) {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.gui.on()) return;
		route(open ? "open" : "close");
	}

	/** Kleiner Klick. */
	public static void click() {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.clicks.on()) return;
		route("click");
	}

	/** Etwas hinzugefügt. */
	public static void added() {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.clicks.on()) return;
		route("add");
	}

	/** Etwas entfernt. */
	public static void removed() {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.clicks.on()) return;
		route("remove");
	}

	/** Creamy Keys: mechanischer Tastenklang (eigene Lautstärke, unabhängig vom Sounds-Modul). */
	public static void key(boolean mouse) {
		var k = ModuleManager.KEYSTROKES;
		if (!k.isEnabled() || !k.keySounds.on() || broken || !ModuleManager.SOUNDS.isEnabled()) return;
		float vol = k.keyVolume.get() / 100f;
		if (vol <= 0f) return;
		String name = mouse ? "mouse" : "key" + (int) (Math.random() * 3);
		EXEC.execute(() -> play("Keys:" + name, vol));
	}

	/** Treffer (Hit Effects). */
	public static void hit(boolean crit) {
		route(crit ? "crit" : "hit");
	}

	/** Kill (Hit Effects). */
	public static void kill() {
		route("kill");
	}

	/** Neuer Fund (z. B. Sus Chunk). */
	public static void found() {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled()) return;
		route("found");
	}

	/** Vorschau im GUI (Style gewechselt). */
	public static void preview() {
		route("on");
	}

	private static void route(String name) {
		if (!ModuleManager.SOUNDS.isEnabled()) return; // Hauptschalter: aus = keine ZERO-Sounds (auch Treffer/Kill)
		float vol = ModuleManager.SOUNDS.volume.get() / 100f;
		if (vol <= 0f || broken) return;
		String key = ModuleManager.SOUNDS.style.get() + ":" + name;
		EXEC.execute(() -> play(key, vol));
	}

	private static void play(String key, float vol) {
		short[] base;
		synchronized (CACHE) {
			base = CACHE.computeIfAbsent(key, UiSounds::render);
		}
		if (base == null || base.length == 0) return;
		float g = vol * vol * 0.85f + vol * 0.15f;
		byte[] pcm = new byte[base.length * 2];
		for (int i = 0; i < base.length; i++) {
			int v = Math.round(base[i] * g);
			pcm[i * 2] = (byte) v;
			pcm[i * 2 + 1] = (byte) (v >> 8);
		}
		try {
			Clip clip = AudioSystem.getClip();
			clip.addLineListener(e -> {
				if (e.getType() == LineEvent.Type.STOP) clip.close();
			});
			clip.open(FORMAT, pcm, 0, pcm.length);
			clip.start();
		} catch (Throwable t) {
			broken = true; // kein Audiogerät o. ä. -> nicht weiter versuchen
			QuiqzyClient.LOGGER.warn("ZERO sounds disabled: {}", t.toString());
		}
	}

	// ================================================================== Synth

	/** Mono-Puffer, in den die Bausteine hineinmischen. */
	private static final class Buf {
		final float[] d = new float[(int) (RATE * 1.2f)];
		final Random rnd;

		Buf(String seed) {
			rnd = new Random(seed.hashCode());
		}

		void add(int i, float v) {
			if (i >= 0 && i < d.length) d[i] += v;
		}

		/**
		 * Angeschlagenes Glas: unharmonische Teiltöne (Verhältnisse einer frei schwingenden Glasglocke),
		 * höhere klingen schneller ab; dazu eine leicht verstimmte Kopie für feines Schweben.
		 */
		void glass(float at, float freq, float gain, float decay) {
			bell(at, freq, gain, decay, new float[]{1f, 2.756f, 5.404f, 8.933f}, new float[]{1f, 0.34f, 0.13f, 0.05f}, 0.0025f);
			bell(at, freq * 1.0035f, gain * 0.28f, decay * 1.1f, new float[]{1f}, new float[]{1f}, 0.0025f);
		}

		/** Holz/Marimba: fast harmonisch (1 : 4 : 10), Obertöne sehr kurz. */
		void wood(float at, float freq, float gain, float decay) {
			bell(at, freq, gain, decay, new float[]{1f, 3.93f, 9.2f}, new float[]{1f, 0.22f, 0.06f}, 0.0015f);
		}

		private void bell(float at, float freq, float gain, float decay, float[] ratios, float[] gains, float attack) {
			int start = (int) (at * RATE);
			for (int p = 0; p < ratios.length; p++) {
				float f = freq * ratios[p];
				if (f > 17000f) continue;
				float k = decay * (1f + p * 0.9f);
				int len = (int) (Math.min(1.1f, 7.5f / k) * RATE);
				double w = 2 * Math.PI * f / RATE;
				double ph = rnd.nextDouble() * 0.2;
				for (int i = 0; i < len; i++) {
					float t = i / RATE;
					float env = (float) Math.exp(-k * t) * Math.min(1f, t / attack);
					add(start + i, (float) Math.sin(ph + w * i) * gain * gains[p] * env);
				}
			}
		}

		/** Kurzer Tap: Rauschen durch einen resonanten Bandpass, sehr schnell abklingend. */
		void tap(float at, float dur, float gain, float center, float q) {
			int start = (int) (at * RATE), len = (int) (dur * RATE);
			Biquad bp = Biquad.bandpass(center, q);
			for (int i = 0; i < len + 400; i++) {
				float t = i / (float) len;
				float n = i < len ? (rnd.nextFloat() * 2f - 1f) * (float) Math.exp(-5.5 * t) : 0f;
				add(start + i, bp.process(n) * gain);
			}
		}

		/** Luftzug: Rauschen durch einen Tiefpass, dessen Grenzfrequenz von f0 nach f1 gleitet. */
		void whoosh(float at, float dur, float gain, float f0, float f1) {
			int start = (int) (at * RATE), len = (int) (dur * RATE);
			Biquad lp = Biquad.lowpass(f0, 0.9f);
			for (int i = 0; i < len; i++) {
				float t = i / (float) len;
				if ((i & 31) == 0) lp.setLowpass(f0 * (float) Math.pow(f1 / f0, t), 0.9f);
				float env = (float) Math.pow(Math.sin(Math.PI * Math.min(1f, t * 1.15f)), 1.6);
				add(start + i, lp.process(rnd.nextFloat() * 2f - 1f) * gain * env);
			}
		}

		/** Weicher, tiefer Puls (Treffer-Gefühl): Sinus mit Pitch-Fall. */
		void thump(float at, float dur, float gain, float f0, float f1) {
			int start = (int) (at * RATE), len = (int) (dur * RATE);
			double ph = 0;
			for (int i = 0; i < len; i++) {
				float t = i / (float) len;
				double f = f0 * Math.pow(f1 / f0, t);
				ph += 2 * Math.PI * f / RATE;
				float env = (float) Math.exp(-4.0 * t) * Math.min(1f, i / (RATE * 0.002f));
				add(start + i, (float) Math.sin(ph) * gain * env);
			}
		}

		/** Alter Ton (Pling-Style): Frequenz gleitet, Wellenform 0 Sinus, 1 Dreieck, 2 weich. */
		void note(float at, float freq, float to, float dur, float gain, int wave) {
			int start = (int) (at * RATE), count = (int) (dur * RATE);
			double phase = 0;
			for (int i = 0; i < count; i++) {
				float t = i / (float) count;
				double f = freq * Math.pow(to / freq, t);
				phase += 2 * Math.PI * f / RATE;
				double w = switch (wave) {
					case 1 -> 2.0 / Math.PI * Math.asin(Math.sin(phase));
					case 2 -> Math.sin(phase) * 0.7 + Math.sin(phase * 2.0) * 0.3;
					default -> Math.sin(phase);
				};
				float attack = Math.min(1f, i / (RATE * 0.006f));
				add(start + i, (float) (w * gain * attack * Math.exp(-4.2 * t)));
			}
		}
	}

	/** RBJ-Biquad. */
	private static final class Biquad {
		float b0, b1, b2, a1, a2, x1, x2, y1, y2;

		static Biquad bandpass(float f, float q) {
			Biquad b = new Biquad();
			double w = 2 * Math.PI * f / RATE, al = Math.sin(w) / (2 * q), a0 = 1 + al;
			b.b0 = (float) (al / a0);
			b.b1 = 0f;
			b.b2 = (float) (-al / a0);
			b.a1 = (float) (-2 * Math.cos(w) / a0);
			b.a2 = (float) ((1 - al) / a0);
			return b;
		}

		static Biquad lowpass(float f, float q) {
			Biquad b = new Biquad();
			b.setLowpass(f, q);
			return b;
		}

		void setLowpass(float f, float q) {
			f = Math.min(f, RATE * 0.45f);
			double w = 2 * Math.PI * f / RATE, al = Math.sin(w) / (2 * q), c = Math.cos(w), a0 = 1 + al;
			b0 = (float) ((1 - c) / 2 / a0);
			b1 = (float) ((1 - c) / a0);
			b2 = b0;
			a1 = (float) (-2 * c / a0);
			a2 = (float) ((1 - al) / a0);
		}

		float process(float x) {
			float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
			x2 = x1;
			x1 = x;
			y2 = y1;
			y1 = y;
			return y;
		}
	}

	/** Baut einen Klang und gibt Stereo-PCM zurück. key = "Style:name". */
	private static short[] render(String key) {
		int c = key.indexOf(':');
		String style = key.substring(0, c), name = key.substring(c + 1);
		Buf b = new Buf(key);
		float wet;
		float peak;
		if (style.equals("Keys")) {
			keys(b, name);
			wet = 0.05f;
			peak = name.equals("mouse") ? 0.32f : 0.45f;
		} else {
			String st = switch (style) {
				case "Glass", "Soft", "Pling", "Click" -> style;
				default -> "Minimal";
			};
			boolean ok = switch (st) {
				case "Soft" -> soft(b, name);
				case "Pling" -> pling(b, name);
				case "Click" -> click(b, name);
				case "Glass" -> glass(b, name);
				default -> minimal(b, name);
			};
			if (!ok) return null;
			wet = switch (st) {
				case "Minimal" -> 0.02f;
				case "Glass" -> 0.26f;
				case "Soft" -> 0.16f;
				case "Pling" -> 0.14f;
				default -> 0.06f;
			};
			boolean min = st.equals("Minimal");
			peak = min ? switch (name) {
				case "click" -> 0.16f;
				case "add", "remove" -> 0.22f;
				case "open", "close" -> 0.24f;
				case "hit" -> 0.50f;
				case "crit" -> 0.55f;
				case "kill" -> 0.62f;
				default -> 0.26f;
			} : switch (name) {
				case "click" -> 0.30f;
				case "add", "remove" -> 0.40f;
				case "open", "close" -> 0.44f;
				case "hit" -> 0.55f;
				case "crit" -> 0.62f;
				case "kill" -> 0.70f;
				default -> 0.48f;
			};
		}
		return finish(b.d, wet, peak);
	}

	// ------------------------------------------------------------------ Styles

	/**
	 * Minimal (Standard): ganz dezente, kurze, weiche UI-Klänge wie Handy-/Mac-Klicks.
	 * Keine Melodie, keine Glocken, kein Hall – nur ein weiches "Tock" und feine Ticks.
	 */
	private static boolean minimal(Buf b, String n) {
		switch (n) {
			case "click" -> {
				b.tap(0f, 0.004f, 0.5f, 2400f, 0.9f);
				b.thump(0f, 0.018f, 0.35f, 900f, 700f);
			}
			case "on" -> { // etwas höher = an
				b.thump(0f, 0.05f, 0.8f, 680f, 560f);
				b.tap(0f, 0.004f, 0.25f, 3000f, 1f);
			}
			case "off" -> { // etwas tiefer = aus
				b.thump(0f, 0.05f, 0.8f, 520f, 420f);
				b.tap(0f, 0.004f, 0.2f, 2400f, 1f);
			}
			case "add" -> b.thump(0f, 0.04f, 0.7f, 760f, 640f);
			case "remove" -> b.thump(0f, 0.04f, 0.7f, 460f, 380f);
			case "open" -> {
				b.whoosh(0f, 0.12f, 0.18f, 250f, 1600f);
				b.thump(0.03f, 0.05f, 0.6f, 600f, 500f);
			}
			case "close" -> {
				b.whoosh(0f, 0.10f, 0.16f, 1400f, 250f);
				b.thump(0.02f, 0.05f, 0.6f, 480f, 400f);
			}
			case "found" -> {
				b.thump(0f, 0.05f, 0.7f, 600f, 520f);
				b.thump(0.07f, 0.05f, 0.7f, 800f, 700f);
			}
			default -> {
				return click(b, n); // Treffer/Kill: trockene Varianten
			}
		}
		return true;
	}

	private static boolean glass(Buf b, String n) {
		switch (n) {
			case "on" -> { // zwei helle Glas-Pings, aufsteigend (G6 -> D7)
				b.tap(0f, 0.010f, 0.25f, 5200f, 2.5f);
				b.glass(0f, 1568f, 0.55f, 13f);
				b.glass(0.055f, 2349f, 0.45f, 13f);
			}
			case "off" -> { // dieselben, absteigend und dunkler (D6 -> A5)
				b.tap(0f, 0.010f, 0.20f, 3800f, 2.5f);
				b.glass(0f, 1175f, 0.50f, 14f);
				b.glass(0.050f, 880f, 0.42f, 14f);
			}
			case "click" -> { // feiner Tick wie ein Fingernagel auf Glas
				b.tap(0f, 0.008f, 0.55f, 4200f, 3.5f);
				b.glass(0f, 3136f, 0.10f, 45f);
			}
			case "add" -> {
				b.glass(0f, 1319f, 0.45f, 16f);
				b.glass(0.045f, 1976f, 0.40f, 16f);
			}
			case "remove" -> {
				b.glass(0f, 1319f, 0.42f, 17f);
				b.glass(0.045f, 988f, 0.38f, 17f);
			}
			case "open" -> { // Luft zieht auf + weicher Glas-Akkord
				b.whoosh(0f, 0.18f, 0.30f, 350f, 4200f);
				b.glass(0.06f, 1175f, 0.32f, 7f);
				b.glass(0.09f, 1760f, 0.28f, 7.5f);
				b.glass(0.12f, 2637f, 0.16f, 8f);
			}
			case "close" -> {
				b.whoosh(0f, 0.14f, 0.26f, 3600f, 400f);
				b.glass(0.03f, 1319f, 0.30f, 10f);
				b.glass(0.07f, 880f, 0.28f, 10f);
			}
			case "found" -> {
				b.glass(0f, 1319f, 0.40f, 7f);
				b.glass(0.08f, 1760f, 0.38f, 7f);
				b.glass(0.16f, 2637f, 0.34f, 6f);
			}
			case "hit" -> {
				b.thump(0f, 0.08f, 0.70f, 170f, 70f);
				b.tap(0f, 0.014f, 0.30f, 2600f, 1.8f);
				b.glass(0f, 2093f, 0.12f, 28f);
			}
			case "crit" -> {
				b.thump(0f, 0.10f, 0.70f, 190f, 65f);
				b.tap(0f, 0.012f, 0.30f, 3400f, 2f);
				b.glass(0.01f, 2637f, 0.36f, 12f);
				b.glass(0.05f, 3520f, 0.26f, 12f);
			}
			case "kill" -> {
				b.thump(0f, 0.26f, 0.75f, 120f, 48f);
				b.glass(0.03f, 1047f, 0.32f, 6f);
				b.glass(0.09f, 1568f, 0.30f, 6f);
				b.glass(0.16f, 2093f, 0.30f, 5f);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static boolean soft(Buf b, String n) {
		switch (n) {
			case "on" -> {
				b.wood(0f, 784f, 0.6f, 16f);
				b.wood(0.06f, 1175f, 0.5f, 16f);
			}
			case "off" -> {
				b.wood(0f, 587f, 0.6f, 17f);
				b.wood(0.055f, 440f, 0.5f, 17f);
			}
			case "click" -> b.wood(0f, 1568f, 0.35f, 60f);
			case "add" -> {
				b.wood(0f, 659f, 0.5f, 20f);
				b.wood(0.045f, 988f, 0.45f, 20f);
			}
			case "remove" -> {
				b.wood(0f, 659f, 0.5f, 20f);
				b.wood(0.045f, 494f, 0.45f, 20f);
			}
			case "open" -> {
				b.whoosh(0f, 0.15f, 0.14f, 300f, 1800f);
				b.wood(0.04f, 587f, 0.45f, 10f);
				b.wood(0.08f, 880f, 0.40f, 10f);
				b.wood(0.12f, 1175f, 0.30f, 11f);
			}
			case "close" -> {
				b.wood(0f, 880f, 0.45f, 12f);
				b.wood(0.06f, 587f, 0.42f, 12f);
			}
			case "found" -> {
				b.wood(0f, 659f, 0.5f, 10f);
				b.wood(0.08f, 880f, 0.5f, 10f);
				b.wood(0.16f, 1319f, 0.45f, 9f);
			}
			case "hit" -> {
				b.thump(0f, 0.08f, 0.75f, 160f, 70f);
				b.wood(0f, 1047f, 0.15f, 35f);
			}
			case "crit" -> {
				b.thump(0f, 0.10f, 0.75f, 180f, 65f);
				b.wood(0.01f, 1319f, 0.4f, 14f);
				b.wood(0.05f, 1760f, 0.3f, 14f);
			}
			case "kill" -> {
				b.thump(0f, 0.26f, 0.8f, 120f, 48f);
				b.wood(0.03f, 523f, 0.4f, 8f);
				b.wood(0.09f, 784f, 0.4f, 8f);
				b.wood(0.16f, 1047f, 0.4f, 7f);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static boolean click(Buf b, String n) {
		switch (n) {
			case "on" -> {
				b.tap(0f, 0.008f, 0.7f, 3600f, 3f);
				b.tap(0.035f, 0.008f, 0.6f, 4800f, 3f);
			}
			case "off" -> {
				b.tap(0f, 0.008f, 0.7f, 3200f, 3f);
				b.tap(0.035f, 0.008f, 0.6f, 2200f, 3f);
			}
			case "click", "add", "remove" -> b.tap(0f, 0.007f, 0.6f, n.equals("remove") ? 2600f : 4000f, 3f);
			case "open" -> b.whoosh(0f, 0.13f, 0.30f, 500f, 4000f);
			case "close" -> b.whoosh(0f, 0.11f, 0.28f, 3600f, 500f);
			case "found" -> {
				b.tap(0f, 0.008f, 0.6f, 3000f, 4f);
				b.tap(0.06f, 0.008f, 0.6f, 4000f, 4f);
				b.tap(0.12f, 0.008f, 0.6f, 5200f, 4f);
			}
			case "hit", "crit" -> {
				b.thump(0f, 0.07f, 0.7f, 170f, 70f);
				b.tap(0f, 0.010f, 0.4f, n.equals("crit") ? 4200f : 2800f, 2f);
			}
			case "kill" -> {
				b.thump(0f, 0.22f, 0.8f, 120f, 48f);
				b.tap(0.03f, 0.010f, 0.5f, 3600f, 3f);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Die alten Töne, jetzt mit Stereo-Hall. */
	private static boolean pling(Buf b, String n) {
		final int SINE = 0, TRI = 1, SOFT = 2;
		switch (n) {
			case "on" -> {
				b.note(0f, 659f, 659f, 0.11f, 0.42f, TRI);
				b.note(0.06f, 988f, 988f, 0.16f, 0.36f, SINE);
			}
			case "off" -> b.note(0f, 587f, 392f, 0.16f, 0.40f, TRI);
			case "click" -> b.note(0f, 2100f, 1500f, 0.035f, 0.22f, SOFT);
			case "add" -> b.note(0f, 784f, 1175f, 0.10f, 0.36f, TRI);
			case "remove" -> b.note(0f, 523f, 311f, 0.11f, 0.34f, SINE);
			case "open" -> {
				b.note(0f, 392f, 392f, 0.18f, 0.28f, SINE);
				b.note(0.06f, 587f, 587f, 0.20f, 0.26f, SINE);
				b.note(0.12f, 784f, 784f, 0.24f, 0.18f, SINE);
			}
			case "close" -> b.note(0f, 698f, 392f, 0.18f, 0.28f, SINE);
			case "hit" -> {
				b.note(0f, 180f, 90f, 0.07f, 0.55f, SINE);
				b.note(0f, 2400f, 1800f, 0.03f, 0.12f, SOFT);
			}
			case "crit" -> {
				b.note(0f, 200f, 80f, 0.09f, 0.6f, SINE);
				b.note(0.01f, 1568f, 1568f, 0.10f, 0.22f, TRI);
				b.note(0.05f, 2349f, 2349f, 0.16f, 0.16f, SINE);
			}
			case "kill" -> {
				b.note(0f, 130f, 60f, 0.22f, 0.6f, SINE);
				b.note(0.04f, 784f, 784f, 0.18f, 0.26f, TRI);
				b.note(0.12f, 1175f, 1175f, 0.20f, 0.24f, TRI);
				b.note(0.20f, 1568f, 1568f, 0.34f, 0.22f, SINE);
			}
			case "found" -> {
				b.note(0f, 784f, 784f, 0.14f, 0.34f, TRI);
				b.note(0.09f, 988f, 988f, 0.14f, 0.32f, TRI);
				b.note(0.18f, 1319f, 1319f, 0.26f, 0.30f, SINE);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Creamy Keys: dumpfer "Thock" + kurzer Anschlag, drei Varianten. */
	private static void keys(Buf b, String n) {
		if (n.equals("mouse")) {
			b.tap(0f, 0.006f, 0.5f, 3000f, 2f);
			b.thump(0f, 0.03f, 0.35f, 220f, 120f);
			return;
		}
		float base = n.equals("key0") ? 150f : n.equals("key1") ? 165f : 138f;
		b.thump(0f, 0.05f, 0.7f, base, 85f);
		b.tap(0f, 0.006f, 0.35f, base * 20f, 1.6f);
		b.tap(0.001f, 0.012f, 0.20f, 900f, 1.2f);
	}

	// ------------------------------------------------------------------ Hall + Mastering

	private static final int[] COMBS = {1116, 1188, 1277, 1356, 1422, 1491};
	private static final int[] APS = {556, 441, 341};

	/** Kleiner Freeverb-artiger Stereo-Hall, Normalisierung auf "peak", Soft-Clip, Fade-Out. */
	private static short[] finish(float[] dry, float wet, float peak) {
		int end = dry.length;
		while (end > 0 && Math.abs(dry[end - 1]) < 1e-4f) end--;
		if (end == 0) return new short[0];
		int len = Math.min(dry.length, end + (int) (RATE * (0.10f + wet * 1.2f)));
		float[] l = new float[len], r = new float[len];
		for (int ch = 0; ch < 2; ch++) {
			float[] out = ch == 0 ? l : r;
			int spread = ch == 0 ? 0 : 23;
			for (int cIdx = 0; cIdx < COMBS.length; cIdx++) {
				int dl = (int) ((COMBS[cIdx] + spread) * 0.62f);
				float[] buf = new float[dl];
				int pos = 0;
				float store = 0f;
				for (int i = 0; i < len; i++) {
					float o = buf[pos];
					store = o * 0.72f + store * 0.28f; // Dämpfung
					buf[pos] = (i < end ? dry[i] : 0f) + store * 0.74f;
					out[i] += o;
					if (++pos >= dl) pos = 0;
				}
			}
			for (int a = 0; a < APS.length; a++) {
				int dl = (int) ((APS[a] + spread) * 0.62f);
				float[] buf = new float[dl];
				int pos = 0;
				for (int i = 0; i < len; i++) {
					float bo = buf[pos];
					float in = out[i];
					out[i] = -in + bo;
					buf[pos] = in + bo * 0.5f;
					if (++pos >= dl) pos = 0;
				}
			}
		}
		float max = 1e-6f;
		float[] ml = new float[len], mr = new float[len];
		float wetG = wet * 0.15f;
		for (int i = 0; i < len; i++) {
			float d = i < end ? dry[i] : 0f;
			ml[i] = d + l[i] * wetG;
			mr[i] = d + r[i] * wetG;
			max = Math.max(max, Math.max(Math.abs(ml[i]), Math.abs(mr[i])));
		}
		float norm = peak / max;
		int fadeIn = (int) (RATE * 0.0008f), fadeOut = (int) (RATE * 0.03f);
		short[] pcm = new short[len * 2];
		for (int i = 0; i < len; i++) {
			float f = 1f;
			if (i < fadeIn) f = i / (float) fadeIn;
			if (i > len - fadeOut) f *= (len - i) / (float) fadeOut;
			pcm[i * 2] = (short) Math.round(Math.tanh(ml[i] * norm * f * 1.05) * 32000.0);
			pcm[i * 2 + 1] = (short) Math.round(Math.tanh(mr[i] * norm * f * 1.05) * 32000.0);
		}
		return pcm;
	}
}
