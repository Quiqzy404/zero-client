package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Card Aura: Pokerkarten kreisen um dich herum (eigene Geometrie, kein Asset). Sichtbar in F5/bei anderen. */
public final class CardAuraModule extends Module {
	public final Setting.Bool themeColor = add(new Setting.Bool("theme_color", "Theme Colour", true));
	public final Setting.Color color = add(new Setting.Color("color", "Colour", 0xF0A6FF));
	public final Setting.Int count = add(new Setting.Int("count", "Cards", 5, 1, 12));
	public final Setting.Int radius = add(new Setting.Int("radius", "Radius", 90, 40, 180, " %"));
	public final Setting.Int size = add(new Setting.Int("size", "Size", 100, 50, 200, " %"));
	public final Setting.Int speed = add(new Setting.Int("speed", "Speed", 100, 0, 300, " %"));
	public final Setting.Bool others = add(new Setting.Bool("others", "On Other Players", false));

	public CardAuraModule() {
		super("card_aura", "Card Aura", "Poker cards circling around you", Category.VISUALS);
	}
}
