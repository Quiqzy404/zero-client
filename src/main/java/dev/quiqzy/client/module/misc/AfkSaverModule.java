package dev.quiqzy.client.module.misc;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * AFK Saver – schützt vor GPU-Überlast-Crashes (Bugcheck 0x116 / VIDEO_TDR_FAILURE), wie sie beim
 * Multi-Boxing (mehrere Minecraft-Instanzen gleichzeitig) auftreten. Begrenzt die FPS hart und senkt
 * FPS + Render-Distanz zusätzlich stark, sobald das Fenster NICHT im Fokus ist (also AFK im Hintergrund).
 * So rendert jede Instanz im Hintergrund nur noch minimal und die GPU läuft nicht mehr voll.
 *
 * Setzt nur die Laufzeit-Werte der Vanilla-Optionen (framerateLimit, renderDistance) – nichts wird
 * dauerhaft in die options.txt geschrieben; beim Ausschalten werden die Original-Werte wiederhergestellt.
 */
public final class AfkSaverModule extends Module {

	public final Setting.Int activeFps = add(new Setting.Int("active_fps", "Max FPS (im Fokus)", 60, 10, 260, " fps"));
	public final Setting.Int afkFps = add(new Setting.Int("afk_fps", "Max FPS (unfokussiert)", 10, 1, 120, " fps"));
	public final Setting.Bool cutRender = add(new Setting.Bool("cut_render", "Render-Distanz senken (unfokussiert)", true));
	public final Setting.Int afkRender = add(new Setting.Int("afk_render", "Render-Distanz (unfokussiert)", 4, 2, 16, " ch"));

	private int savedFps = -1;
	private int savedRender = -1;
	private boolean captured = false;
	private int lastFpsApplied = Integer.MIN_VALUE;
	private int lastRenderApplied = Integer.MIN_VALUE;

	public AfkSaverModule() {
		super("afk_saver", "AFK Saver",
				"Begrenzt FPS & Render-Distanz (vor allem wenn unfokussiert), um GPU-Crashes (0x116) beim Multi-Boxing zu vermeiden",
				Category.MISC);
	}

	@Override
	protected void onEnable() {
		captured = false; // beim nächsten Tick die aktuellen Original-Werte merken
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) return;
		try {
			if (savedFps > 0) mc.options.framerateLimit().set(savedFps);
			if (savedRender > 0) mc.options.renderDistance().set(savedRender);
		} catch (Throwable ignored) {
		}
		lastFpsApplied = Integer.MIN_VALUE;
		lastRenderApplied = Integer.MIN_VALUE;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.options == null) return;
		try {
			if (!captured) {
				savedFps = mc.options.framerateLimit().get();
				savedRender = mc.options.renderDistance().get();
				captured = true;
			}

			boolean focused = mc.isWindowActive();

			int fps = focused ? activeFps.get() : afkFps.get();
			if (fps != lastFpsApplied) {
				mc.options.framerateLimit().set(fps);
				lastFpsApplied = fps;
			}

			int rd = (cutRender.on() && !focused) ? afkRender.get() : savedRender;
			if (rd > 0 && rd != lastRenderApplied) {
				mc.options.renderDistance().set(rd);
				lastRenderApplied = rd;
			}
		} catch (Throwable ignored) {
		}
	}
}
