package dev.quiqzy.client.module.tiersmc;

import java.util.List;

import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.render.ServerBaseNet;

/** DL Sus Finder: grobes Gebiet um jede Base (Mitte leicht verschoben, wie im Addon). */
public final class DLSusFinderModule extends DLModule {

	public final Setting.Int radius = add(new Setting.Int("area_radius", "Area Radius", 2, 0, 8, " chunks"));
	public final Setting.Int fuzz = add(new Setting.Int("fuzz", "Fuzz", 2, 0, 8, " chunks"));

	public DLSusFinderModule() {
		super("dl_sus_finder", "DL Sus Finder", "Marks the rough area where a base is on DonutLarp",
				true, 40, 63, 0xFF3C3C, 35, 0xFF4646);
	}

	/** Mitte des Sus-Gebiets in Chunks – gleiche deterministische Verschiebung wie im Addon (pro Base-ID). */
	public int[] center(ServerBaseNet.Base b) {
		int f = fuzz.get();
		long h = b.id() * -7046029254386353131L;
		int dx = f == 0 ? 0 : (int) Math.floorMod(h >>> 7, 2L * f + 1) - f;
		int dz = f == 0 ? 0 : (int) Math.floorMod(h >>> 29, 2L * f + 1) - f;
		return new int[] { b.chunkX() + dx, b.chunkZ() + dz };
	}

	public void onNewBases(List<ServerBaseNet.Base> added) {
		if (!isEnabled()) return;
		if (added.size() > 3) {
			announce("Sus Areas Detected", added.size() + " sus areas received.", ding(), 0.8f);
			return;
		}
		for (ServerBaseNet.Base b : added) {
			int[] c = center(b);
			announce("Sus Area Detected", "Sus area around " + (c[0] * 16 + 8) + ", " + (c[1] * 16 + 8) + ".", ding(), 0.8f);
		}
	}
}
