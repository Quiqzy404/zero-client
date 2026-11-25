package dev.quiqzy.client.module.tiersmc;

import java.util.List;

import dev.quiqzy.client.render.ServerBaseNet;

/** DL Chunk Finder: goldener Chunk über jeder Base, die der Server schickt. */
public final class DLChunkFinderModule extends DLModule {

	public DLChunkFinderModule() {
		super("dl_chunk_finder", "DL Chunk Finder", "Marks a golden chunk above every base on DonutLarp",
				true, 32, 64, 0xFFC400, 70, 0xFFD700);
	}

	public void onNewBases(List<ServerBaseNet.Base> added) {
		if (!isEnabled()) return;
		if (added.size() > 3) {
			announce("Base Chunks Found!", added.size() + " golden chunks received.", challenge(), 1.2f);
			return;
		}
		for (ServerBaseNet.Base b : added) {
			announce("Base Chunk Found!", "Golden chunk found at " + (b.chunkX() * 16 + 8) + ", " + (b.chunkZ() * 16 + 8) + ".",
					challenge(), 1.2f);
		}
	}

	public int known() {
		return ServerBaseNet.bases().size();
	}
}
