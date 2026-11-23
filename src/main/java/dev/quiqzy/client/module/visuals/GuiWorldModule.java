package dev.quiqzy.client.module.visuals;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * GUI In World: zeigt ein echtes 3D-Panel an einer festen Stelle in der Welt (kein Bildschirm-Overlay).
 * Beim Aktivieren wird es vor dir platziert - du bist NICHT in einem Screen, kannst also drumherum laufen.
 * Erste Version: Anzeige der Combat-Module (Name + An/Aus). Klicken per Blick kommt als Nächstes.
 */
public final class GuiWorldModule extends Module {
	public final Setting.Int scale = add(new Setting.Int("scale", "Size", 100, 30, 300, " %"));
	public final Setting.Int distance = add(new Setting.Int("distance", "Place Distance", 3, 1, 8, " b"));
	public final Setting.Choice cat = add(new Setting.Choice("category", "Category", "Combat", "Combat", "Movement", "Visuals", "HUD", "World", "Misc"));
	public final Setting.Bool mirror = add(new Setting.Bool("mirror", "Mirror Real GUI (experimental)", false));

	public double ax, ay, az;
	public float yaw;
	private boolean wasOpen;

	public GuiWorldModule() {
		super("gui_world", "GUI In World", "A real 3D menu panel placed in the world you can walk around", Category.VISUALS);
	}

	@Override
	protected void onEnable() {
		place(Minecraft.getInstance());
		wasOpen = false;
	}

	@Override
	public void onTick(Minecraft mc) {
		// Mit "Mirror": jedes Mal wenn die ClickGUI aufgeht, das Panel frisch vor dich platzieren.
		boolean open = mc.screen != null && mc.screen.getClass().getSimpleName().equals("ZeroGui");
		if (mirror.on() && open && !wasOpen) place(mc);
		wasOpen = open;
	}

	/** Panel vor den Spieler legen (feste Weltposition + Blickrichtung). */
	public void place(Minecraft mc) {
		if (mc.player == null) return;
		float y = mc.player.getYRot();
		double rad = Math.toRadians(y);
		double d = distance.get();
		ax = mc.player.getX() - Math.sin(rad) * d;
		az = mc.player.getZ() + Math.cos(rad) * d;
		ay = mc.player.getEyeY() - 0.3;
		yaw = y;
	}
}
