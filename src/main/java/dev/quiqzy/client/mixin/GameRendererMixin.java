package dev.quiqzy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.CustomFovModule;

/** NoHurtCam (kein Wackeln bei Schaden) und Custom FOV. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$noHurtCam(PoseStack pose, float partialTick, CallbackInfo ci) {
		if (ModuleManager.NO_HURT_CAM.isEnabled()) ci.cancel();
	}

	/**
	 * Hand Glow: die Hand wird in 1.21.11 erst beim renderAllFeatures() in renderLevel gezeichnet
	 * (renderItemInHand reicht sie nur ein). Direkt nach dem endBatch dort enthält der Tiefenpuffer nur die Hand.
	 */
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V", ordinal = 0, shift = At.Shift.AFTER), require = 0)
	private void zero$handGlow(net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
		dev.quiqzy.client.render.HandGlowPass.render();
	}

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void zero$customFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		CustomFovModule m = ModuleManager.CUSTOM_FOV;
		if (!m.isEnabled() || !useFovSetting) return;
		float base = Minecraft.getInstance().options.fov().get();
		float now = cir.getReturnValue();
		if (m.staticFov.on()) {
			cir.setReturnValue((float) m.fov.get());
		} else if (base > 0f) {
			cir.setReturnValue(now / base * m.fov.get());
		}
	}
}
