package dev.quiqzy.client.module.visuals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Storage ESP (Krispy-Look): alle Container in Reichweite als leuchtend gefüllte Boxen + ein dünner Tracer-Fächer
 * aus der Hand zu jeder Box. Scannt Block-Entities (schnell, erwischt Truhen/Shulker/Fässer/Ender-Truhen/Trichter).
 * Farbe je Typ: Truhe orange, Shulker lila, Fass braun, Ender-Truhe türkis, Trichter grau.
 */
public final class StorageEspModule extends Module {
	public final Setting.Bool chests = add(new Setting.Bool("chests", "Chests", true));
	public final Setting.Bool shulkers = add(new Setting.Bool("shulkers", "Shulkers", true));
	public final Setting.Bool barrels = add(new Setting.Bool("barrels", "Barrels", true));
	public final Setting.Bool enderChests = add(new Setting.Bool("ender_chests", "Ender Chests", true));
	public final Setting.Bool hoppers = add(new Setting.Bool("hoppers", "Hoppers", false));
	public final Setting.Int range = add(new Setting.Int("range", "Radius", 6, 1, 16, " Ch"));
	public final Setting.Int fill = add(new Setting.Int("fill", "Fill Opacity", 22, 0, 100, " %"));
	public final Setting.Bool glow = add(new Setting.Bool("glow", "Glow Edges", true));
	public final Setting.Bool tracers = add(new Setting.Bool("tracers", "Tracer Fan", true));
	public final Setting.Int tracerAlpha = add(new Setting.Int("tracer_alpha", "Tracer Opacity", 40, 5, 100, " %"));
	public final Setting.Int tracerWidth = add(new Setting.Int("tracer_width", "Tracer Width", 1, 1, 5));
	public final Setting.Int maxTracers = add(new Setting.Int("max_tracers", "Max Tracers", 250, 10, 1000));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));

	/** Ein Container: Position + Farbe (RGB). */
	public record Hit(BlockPos pos, int rgb) {
	}

	private volatile List<Hit> hits = Collections.emptyList();
	private int timer;

	public StorageEspModule() {
		super("storage_esp", "Storage ESP", "Glowing boxes + tracer fan to every chest, shulker and barrel", Category.VISUALS);
	}

	@Override
	protected void onEnable() {
		hits = Collections.emptyList();
		timer = 0;
	}

	@Override
	protected void onDisable() {
		hits = Collections.emptyList();
	}

	public List<Hit> hits() {
		return hits;
	}

	/** Farbe je Block-Entity-Typ, -1 = nicht anzeigen. */
	private int colorOf(BlockEntityType<?> t) {
		if (t == BlockEntityType.CHEST || t == BlockEntityType.TRAPPED_CHEST) return chests.on() ? 0xFFB347 : -1;
		if (t == BlockEntityType.SHULKER_BOX) return shulkers.on() ? 0xB36BFF : -1;
		if (t == BlockEntityType.BARREL) return barrels.on() ? 0xC8955A : -1;
		if (t == BlockEntityType.ENDER_CHEST) return enderChests.on() ? 0x4DE8D0 : -1;
		if (t == BlockEntityType.HOPPER) return hoppers.on() ? 0x9AA0B0 : -1;
		return -1;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		if (timer-- > 0) return;
		timer = 10; // alle 0,5 s neu scannen
		int r = range.get();
		int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
		List<Hit> out = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				LevelChunk chunk = mc.level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
				if (chunk == null) continue;
				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					int c = colorOf(e.getValue().getType());
					if (c >= 0) out.add(new Hit(e.getKey().immutable(), c));
				}
			}
		}
		hits = out;
	}
}
