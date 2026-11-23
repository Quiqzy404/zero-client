package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Region Map (Krispy-Look): Chunk-Raster um dich herum als Glas-Karte. Dein Chunk ist markiert,
 * Sus-Chunks und Base-Finder-Treffer leuchten auf, optional mit Chunk-Koordinaten in den Zellen.
 */
public final class RegionMapModule extends Module {
	public final Setting.Int size = add(new Setting.Int("size", "Size", 130, 70, 260, " px"));
	public final Setting.Int radius = add(new Setting.Int("radius", "Radius", 4, 2, 8, " Ch"));
	public final Setting.Bool numbers = add(new Setting.Bool("numbers", "Chunk Coords", true));
	public final Setting.Bool sus = add(new Setting.Bool("sus", "Highlight Sus Chunks", true));
	public final Setting.Bool bases = add(new Setting.Bool("bases", "Highlight Bases", true));
	public final Setting.Bool storage = add(new Setting.Bool("storage", "Highlight Storage", true));

	public RegionMapModule() {
		super("region_map", "Region Map", "Chunk grid around you - sus, base and storage chunks light up", Category.HUD);
	}
}
