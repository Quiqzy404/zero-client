package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Glossy Floor: echte Screen-Space-Reflexion. Der Renderer kopiert die Szenen-Farbtextur und faehrt
 * pro Bodenpixel die reflektierte Blickstrahlrichtung durch das Bild (mittelt die Szenenfarbe) -
 * dadurch spiegeln sich Gebaeude/Lampen real im nassen Boden (wie im Referenz-Clip).
 */
public final class GlossyFloorModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Range", 24, 4, 64, " m"));
	public final Setting.Int intensity = add(new Setting.Int("intensity", "Intensity", 65, 5, 100, " %"));
	public final Setting.Int length = add(new Setting.Int("length", "Reflection Length", 180, 40, 400, " %"));

	public GlossyFloorModule() {
		super("glossy_floor", "Glossy Floor", "Wet reflective floor - the real scene mirrors in the ground", Category.VISUALS);
	}
}
