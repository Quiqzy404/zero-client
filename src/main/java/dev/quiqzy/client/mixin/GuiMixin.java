package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

import dev.quiqzy.client.module.ModuleManager;

/** Custom Crosshair: Vanilla-Fadenkreuz ausblenden, wenn unseres an ist. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	/** Potion HUD an -> die großen Vanilla-Effekt-Icons oben rechts ausblenden (sonst doppelt). */
	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$effects(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (ModuleManager.POTION_HUD.isEnabled()) ci.cancel();
	}

	/** Aurora Health Bars: Vanilla-Herzen/Hunger/Rüstung/Luft ausblenden (eigene Leisten). */
	@Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$health(GuiGraphics g, CallbackInfo ci) {
		if (ModuleManager.HEALTH_BARS.isEnabled() && dev.quiqzy.client.hud.AuroraHud.active()) ci.cancel();
	}

	@org.spongepowered.asm.mixin.Shadow
	private void renderSelectedItemName(GuiGraphics g) {
	}

	/** Aurora: eigene Hotbar + XP + Leisten statt der Vanilla-Leiste; Item-Name bleibt. */
	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$hotbar(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (dev.quiqzy.client.hud.AuroraHud.customHotbar()) {
			try {
				renderSelectedItemName(g);
			} catch (Throwable ignored) {
			}
			ci.cancel();
		}
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$crosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (ModuleManager.CROSSHAIR.isEnabled()) ci.cancel();
	}
}
