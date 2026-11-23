package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.LivingEntity;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.render.ChamsRenderer;
import dev.quiqzy.client.render.ChamsState;

/** Entity ESP "Chams": Modell zusätzlich mit eigenem Shader einfärben (statt Box). */
public final class ChamsMixins {
	private ChamsMixins() {
	}

	@Mixin(EntityRenderState.class)
	public abstract static class StateMixin implements ChamsState {
		@Unique
		private int zero$chamsColor = -1;

		@Override
		public int zero$chams() {
			return zero$chamsColor;
		}

		@Override
		public void zero$setChams(int rgb) {
			zero$chamsColor = rgb;
		}
	}

	@Mixin(LivingEntityRenderer.class)
	public abstract static class RendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
		@org.spongepowered.asm.mixin.Shadow
		public abstract net.minecraft.resources.Identifier getTextureLocation(S state);

		@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
				at = @At("RETURN"), require = 0)
		private void zero$extract(LivingEntity entity, LivingEntityRenderState state, float pt, CallbackInfo ci) {
			var esp = ModuleManager.ENTITY_ESP;
			int rgb = -1;
			Minecraft mc = Minecraft.getInstance();
			if (esp.isEnabled() && esp.chams() && entity != mc.player) rgb = esp.colorFor(mc, entity);
			((ChamsState) state).zero$setChams(rgb);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		@Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
				at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
				require = 0)
		private void zero$chams(SubmitNodeCollector collector, Model model, Object state, PoseStack pose, RenderType type, int light, int overlay,
				int color, TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
			collector.submitModel(model, state, pose, type, light, overlay, color, sprite, outline, crumbling);
			if (!(state instanceof ChamsState cs)) return;
			if (cs.zero$chams() < 0) return;
			try {
				boolean walls = ModuleManager.ENTITY_ESP.throughWalls.on();
				RenderType chams = ChamsRenderer.type(getTextureLocation((S) state), walls);
				collector.submitModel(model, state, pose, chams, ChamsRenderer.packedParams(), overlay,
						ChamsRenderer.color(cs.zero$chams()), null, 0, null);
			} catch (Throwable ignored) {
			}
		}
	}
}
