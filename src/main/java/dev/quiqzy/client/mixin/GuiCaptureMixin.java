package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.render.GuiCapture;

/**
 * Greift den fertigen Frame (Welt + GUI) NACH dem GUI-Render ab, solange die ClickGUI offen ist.
 * Nur so enthält der Framebuffer die echte GUI - im Welt-Render-Pass ist sie noch nicht gezeichnet.
 */
@Mixin(GameRenderer.class)
public abstract class GuiCaptureMixin {
	@Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"), require = 0)
	private void zero$captureGui(DeltaTracker dt, boolean renderLevel, CallbackInfo ci) {
		try {
			if (!ModuleManager.GUI_WORLD.isEnabled() || !ModuleManager.GUI_WORLD.mirror.on()) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.screen != null && mc.screen.getClass().getSimpleName().equals("ZeroGui")) {
				var main = mc.getMainRenderTarget();
				GuiCapture.capture(0, 0, main.width, main.height);
			}
		} catch (Throwable ignored) {
		}
	}
}
