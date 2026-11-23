package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Rain FX: eigener leuchtender Regen, der auf dem Boden aufschlägt und spritzt (nur bei dir sichtbar). */
public final class RainFxModule extends Module {
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "Embers", "Embers", "Water", "Neon", "Snow"));
	public final Setting.Int amount = add(new Setting.Int("amount", "Amount", 140, 20, 400));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 14, 6, 32, " b"));
	public final Setting.Bool splashes = add(new Setting.Bool("splashes", "Ground Splashes", true));

	public RainFxModule() {
		super("rain_fx", "Rain FX", "Glowing rain that splashes on the ground", Category.VISUALS);
	}

	/** True wenn der Snow-Modus aktiv (langsam fallende, schwebende Flocken). */
	public boolean snow() {
		return "Snow".equals(style.get());
	}

	/** {Kopf, Schweif} je nach Stil. */
	public int[] colors() {
		return switch (style.get()) {
			case "Water" -> new int[]{0xDDEFFF, 0x7AA8FF};
			case "Neon" -> new int[]{0xF2D6FF, 0xA05CFF};
			case "Snow" -> new int[]{0xFFFFFF, 0xBFD4F5};
			default -> new int[]{0xFFE2B0, 0xFF7A2C};
		};
	}
}
