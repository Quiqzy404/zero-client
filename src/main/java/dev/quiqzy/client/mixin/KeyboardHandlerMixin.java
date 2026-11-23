package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

import dev.quiqzy.client.module.ModuleManager;

/** Merkt sich jeden Tastendruck - auch Tipper kuerzer als ein Tick gehen so nicht verloren. */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void zero$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action == 1 && Minecraft.getInstance().screen == null) ModuleManager.PRESSED.add(event.key());
	}
}
