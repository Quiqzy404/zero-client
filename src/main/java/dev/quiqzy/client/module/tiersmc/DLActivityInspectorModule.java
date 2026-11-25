package dev.quiqzy.client.module.tiersmc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.quiqzy.client.render.ServerBaseNet;

/** DL Activity Inspector: roter Chunk, wo laut Server ein Spieler unter der Erde ist. */
public final class DLActivityInspectorModule extends DLModule {

	private Set<Long> known = new HashSet<>();

	public DLActivityInspectorModule() {
		super("dl_activity_inspector", "DL Activity Inspector", "Marks a red chunk where a player is underground on DonutLarp",
				false, 32, 64, 0xFF1E1E, 70, 0xFF2828);
	}

	public void onActivity(List<ServerBaseNet.Activity> list) {
		Set<Long> now = new HashSet<>();
		int fresh = 0;
		ServerBaseNet.Activity first = null;
		for (ServerBaseNet.Activity a : list) {
			long k = ((long) a.chunkX() << 32) ^ (a.chunkZ() & 0xFFFFFFFFL);
			now.add(k);
			if (!known.contains(k)) {
				fresh++;
				if (first == null) first = a;
			}
		}
		known = now;
		if (!isEnabled() || fresh == 0) return;
		if (fresh == 1) {
			announce("Activity", "New activity at " + (first.chunkX() * 16 + 8) + ", " + (first.chunkZ() * 16 + 8)
					+ " (" + first.players() + " player" + (first.players() == 1 ? "" : "s") + ").", ding(), 1.4f);
		} else {
			announce("Activity", fresh + " new activity chunks.", ding(), 1.4f);
		}
	}
}
