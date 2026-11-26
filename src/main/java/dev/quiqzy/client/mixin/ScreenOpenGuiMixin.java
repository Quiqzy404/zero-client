package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.gui.AuroraGui;

/** ZERO-GUI auf JEDEM Screen mit der Bind-Taste öffnen (Server-Auswahl, Hauptmenü, Pause ...). */
@Mixin(Screen.class)
public abstract class ScreenOpenGuiMixin {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$openGui(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		Screen self = (Screen) (Object) this;
		if (dev.quiqzy.client.module.misc.ClickGuiModule.isMenu(self)) return; // eigene Tasten selbst behandeln
		if (!QuiqzyClient.OPEN_GUI.matches(event)) return;
		if (self.getFocused() instanceof EditBox) return; // nicht beim Tippen in Textfeldern
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		mc.setScreen(dev.quiqzy.client.module.ModuleManager.CLICK_GUI.create(self));
		cir.setReturnValue(true);
	}
}
