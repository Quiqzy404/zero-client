package dev.quiqzy.client.module.visuals;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Targets;

/**
 * Target Marker: setzt ein rotierendes Viereck (nur die 4 Eck-Klammern) auf den Bauch des aktuellen Gegners.
 * Zielwahl: das Kampfziel vom Target HUD, sonst der nächste Gegner in Reichweite.
 */
public final class TargetMarkerModule extends Module {
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "Ring", "Ring", "Brackets"));
	public final Setting.Bool themeColor = add(new Setting.Bool("theme_color", "Theme Colour", true));
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 18, 3, 64, " m"));
	public final Setting.Int size = add(new Setting.Int("size", "Size", 60, 20, 160, " %"));
	public final Setting.Int speed = add(new Setting.Int("speed", "Spin Speed", 5, 0, 20));
	public final Setting.Int corner = add(new Setting.Int("corner", "Corner Length", 32, 5, 50, " %"));
	public final Setting.Int thickness = add(new Setting.Int("thickness", "Thickness", 2, 1, 6));
	public final Setting.Bool rainbow = add(new Setting.Bool("rainbow", "Rainbow", false));
	public final Setting.Color color = add(new Setting.Color("color", "Color", 0xFF5A6E));
	public final Setting.Bool pulse = add(new Setting.Bool("pulse", "Pulse", true));

	private LivingEntity shown;

	public TargetMarkerModule() {
		super("target_marker", "Target Marker", "Spinning corner brackets on your target", Category.VISUALS);
	}

	@Override
	protected void onEnable() {
		shown = null;
	}

	/** Aktuelles Ziel: Kampfziel vom Target HUD, sonst der naechste Gegner in Reichweite. */
	public LivingEntity target(Minecraft mc) {
		LivingEntity t = ModuleManager.TARGET_HUD.target(mc);
		if (t != null && !t.isRemoved() && t.isAlive() && t.distanceTo(mc.player) <= range.get()) {
			shown = t;
			return t;
		}
		List<LivingEntity> ts = Targets.find(mc, range.get(), 360, players.on(), mobs.on(), mobs.on(), false);
		shown = ts.isEmpty() ? null : ts.get(0);
		return shown;
	}
}
