package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Einstellungen der Panel-GUI (Spalten wie im Video): Größe, Glas, Blur, Rundungen, Icons.
 * Ist kein "an/aus"-Modul – Linksklick klappt nur die Einstellungen auf.
 */
public final class ClickGuiModule extends Module {

	public final Setting.Choice style = add(new Setting.Choice("style_v3", "GUI Style", "Panels", "Panels", "Classic"));
	public final Setting.Int scale = add(new Setting.Int("scale", "GUI Scale", 100, 60, 160, "%"));
	public final Setting.Int panelOpacity = add(new Setting.Int("panel_opacity", "Panel Opacity", 45, 0, 100, "%"));
	public final Setting.Int blurStrength = add(new Setting.Int("blur_strength", "Glass Blur", 2, 1, 4));
	public final Setting.Int activeGlass = add(new Setting.Int("active_glass", "Active Row Whiteness", 55, 15, 95, "%"));
	public final Setting.Bool bgBlur = add(new Setting.Bool("bg_blur", "Background Blur", false));
	public final Setting.Int bgDim = add(new Setting.Int("bg_dim", "Background Dim", 10, 0, 90, "%"));
	public final Setting.Int roundness = add(new Setting.Int("roundness", "Corner Roundness", 9, 0, 16));
	public final Setting.Int rowRoundness = add(new Setting.Int("row_roundness", "Row Roundness", 5, 0, 8));
	public final Setting.Bool icons = add(new Setting.Bool("icons", "Category Icons", true));
	public final Setting.Bool accentRows = add(new Setting.Bool("accent_rows", "Accent Enabled Rows", false));
	public final Setting.Bool descriptions = add(new Setting.Bool("descriptions", "Show Descriptions", true));

	public ClickGuiModule() {
		super("click_gui", "Click GUI", "Look of the menu: size, glass, blur, roundness", Category.CLIENT);
	}

	public boolean panels() {
		return !style.get().equals("Classic");
	}

	/** Das Menü im gewählten Stil öffnen (parent = Screen, zu dem beim Schließen zurückgegangen wird). */
	public net.minecraft.client.gui.screens.Screen create(net.minecraft.client.gui.screens.Screen parent) {
		return switch (style.get()) {
			case "Classic" -> new dev.quiqzy.client.gui.AuroraGui(parent);
			default -> new dev.quiqzy.client.gui.PanelGui(parent);
		};
	}

	public static boolean isMenu(net.minecraft.client.gui.screens.Screen s) {
		return s instanceof dev.quiqzy.client.gui.PanelGui || s instanceof dev.quiqzy.client.gui.AuroraGui;
	}
}
