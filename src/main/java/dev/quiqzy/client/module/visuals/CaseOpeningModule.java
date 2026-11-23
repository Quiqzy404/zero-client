package dev.quiqzy.client.module.visuals;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.gui.CaseOpeningScreen;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Open Case (CSGO-Style, ZERO-eigene Skins): Einschalten oeffnet die Kisten-Rolle, die auslaeuft und einen
 * ZERO-Knife enthuellt. Der Gewinner wird direkt als Weapon-Skin-Modell gesetzt. Schaltet sich danach selbst aus.
 */
public final class CaseOpeningModule extends Module {
	public final Setting.Int duration = add(new Setting.Int("duration", "Spin Time", 6, 3, 12, " s"));
	public final Setting.Bool equip = add(new Setting.Bool("equip", "Equip Winner", true));
	public final Setting.Bool sounds = add(new Setting.Bool("sounds", "Tick Sounds", true));

	public CaseOpeningModule() {
		super("case_opening", "Open Case", "Spin a ZERO case and win a knife skin", Category.VISUALS);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.screen == null || !(mc.screen instanceof CaseOpeningScreen)) mc.setScreen(new CaseOpeningScreen());
			setEnabled(false); // Toggle = Aktion, kein Dauerzustand
		});
	}
}
