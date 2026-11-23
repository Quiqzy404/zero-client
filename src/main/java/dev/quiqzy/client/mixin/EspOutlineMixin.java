package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;

import dev.quiqzy.client.render.EspShaderRenderer;

/**
 * Shader-ESP: Den Vanilla-Glow-Shader (entity_outline Post-Chain) überspringen und beim Auftragen
 * des Outline-Puffers unseren eigenen Pass benutzen (Füllung + Umriss + Glow).
 */
@Mixin(LevelRenderer.class)
public abstract class EspOutlineMixin {
	@Shadow
	private RenderTarget entityOutlineTarget;

	@Shadow
	protected abstract boolean shouldShowEntityOutlines();

	@Redirect(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/PostChain;addToFrame(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;IILnet/minecraft/client/renderer/PostChain$TargetBundle;)V",
			ordinal = 0), require = 0)
	private void zero$skipVanillaOutline(PostChain chain, FrameGraphBuilder builder, int w, int h, PostChain.TargetBundle targets) {
		if (EspShaderRenderer.active()) return;
		chain.addToFrame(builder, w, h, targets);
	}

	@Inject(method = "doEntityOutline", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$espComposite(CallbackInfo ci) {
		if (!EspShaderRenderer.active()) return;
		if (shouldShowEntityOutlines()) EspShaderRenderer.composite(entityOutlineTarget);
		ci.cancel();
	}
}
