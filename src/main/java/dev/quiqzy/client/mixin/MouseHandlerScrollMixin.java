package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import dev.quiqzy.client.input.ScrollKeys;

/** Scroll-Wheel-Keybinds: im Controls-Screen aufs Scrollen binden, im Spiel das gebundene Keybind auslösen. */
@Mixin(MouseHandler.class)
public class MouseHandlerScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (yOffset == 0) return;
		Minecraft mc = Minecraft.getInstance();
		InputConstants.Key key = yOffset > 0 ? ScrollKeys.UP : ScrollKeys.DOWN;

		// 1) In den Controls-Settings: gerade ausgewähltes Keybind aufs Scrollen legen
		if (mc.screen instanceof KeyBindsScreen kbs && kbs.selectedKey != null) {
			kbs.selectedKey.setKey(key);
			kbs.selectedKey = null;
			KeyMapping.resetMapping();
			if (mc.options != null) mc.options.save();
			ci.cancel();
			return;
		}

		// 2) Im Spiel: wenn ein Keybind aufs Scrollen gelegt ist -> auslösen (und Hotbar-Scroll unterdrücken)
		if (mc.screen == null && mc.options != null) {
			boolean bound = false;
			for (KeyMapping km : mc.options.keyMappings) {
				if (((KeyMappingAccessor) (Object) km).zero$key() == key) {
					bound = true;
					break;
				}
			}
			if (bound) {
				KeyMapping.click(key);
				ci.cancel();
			}
		}
	}
}
