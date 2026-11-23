package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.FreecamModule;

/** Freecam: Kamera-Position/-Drehung ersetzen, Maus dreht nur die Kamera, Mausrad = Tempo. */
public final class FreecamMixins {
	private FreecamMixins() {
	}

	@Mixin(Camera.class)
	public abstract static class CameraMixin {
		@Shadow
		protected abstract void setPosition(double x, double y, double z);

		@Shadow
		protected abstract void setRotation(float yaw, float pitch);

		@Inject(method = "setup", at = @At("RETURN"), require = 0)
		private void zero$freecam(Level level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
			// Live Preview: Kamera kreist um den Charakter (Maus dreht die Ansicht), damit man Flügel/Effekte live sieht
			if (dev.quiqzy.client.render.PreviewCam.active && !ModuleManager.FREECAM.active()) {
				var mc = net.minecraft.client.Minecraft.getInstance();
				if (mc.player != null) {
					double ex = net.minecraft.util.Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
					double ey = net.minecraft.util.Mth.lerp(partialTick, mc.player.yo, mc.player.getY()) + mc.player.getEyeHeight() * 0.55;
					double ez = net.minecraft.util.Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());
					float yaw = dev.quiqzy.client.render.PreviewCam.yaw, pitch = dev.quiqzy.client.render.PreviewCam.pitch;
					double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
					double dx = -Math.sin(ry) * Math.cos(rp), dy = -Math.sin(rp), dz = Math.cos(ry) * Math.cos(rp);
					double dist = dev.quiqzy.client.render.PreviewCam.dist;
					setRotation(yaw, pitch);
					setPosition(ex - dx * dist, ey - dy * dist, ez - dz * dist);
					return;
				}
			}
			var cf = ModuleManager.CLEAN_F5;
			if (cf != null && cf.isEnabled() && cf.stableHeight.on() && detached && !ModuleManager.FREECAM.active() && entity != null) {
				double y = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY());
				double off = cf.heightOffset(entity, y);
				var pos = ((net.minecraft.client.Camera) (Object) this).position();
				setPosition(pos.x, pos.y + off, pos.z);
			}
			FreecamModule m = ModuleManager.FREECAM;
			if (!m.active()) return;
			setRotation(m.yaw, m.pitch);
			setPosition(m.x, m.y, m.z);
		}

		/** Clean F5: eigener, weich herausfahrender Abstand (optional durch Wände). */
		@Inject(method = "getMaxZoom", at = @At("RETURN"), cancellable = true, require = 0)
		private void zero$cleanF5Zoom(float wanted, CallbackInfoReturnable<Float> cir) {
			var m = ModuleManager.CLEAN_F5;
			if (m != null && m.isEnabled() && !ModuleManager.FREECAM.active()) cir.setReturnValue(m.zoom(cir.getReturnValue()));
		}

		/** Clean F5: Drehung weich nachziehen (nur Third Person). */
		@org.spongepowered.asm.mixin.injection.Redirect(method = "setup", at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0), require = 0)
		private void zero$cleanF5Rot(net.minecraft.client.Camera self, float yaw, float pitch, Level level, Entity entity, boolean detached, boolean mirror, float pt) {
			var m = ModuleManager.CLEAN_F5;
			if (m != null && m.isEnabled() && detached && !mirror && !ModuleManager.FREECAM.active()) {
				float[] r = m.smooth(yaw, pitch);
				setRotation(r[0], r[1]);
			} else {
				setRotation(yaw, pitch);
			}
		}

		/** "Losgelöst" -> dein Spieler wird von außen gezeichnet. */
		@Inject(method = "isDetached", at = @At("RETURN"), cancellable = true, require = 0)
		private void zero$detached(CallbackInfoReturnable<Boolean> cir) {
			if (ModuleManager.FREECAM.active() && ModuleManager.FREECAM.outsideBody()) cir.setReturnValue(true);
		}
	}

	@Mixin(Entity.class)
	public abstract static class TurnMixin {
		@Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
		private void zero$turn(double dx, double dy, CallbackInfo ci) {
			FreecamModule m = ModuleManager.FREECAM;
			if (m.active() && (Object) this == Minecraft.getInstance().player) {
				m.turn(dx, dy);
				ci.cancel();
			}
		}
	}

	@Mixin(MouseHandler.class)
	public abstract static class ScrollMixin {
		@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
		private void zero$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
			FreecamModule m = ModuleManager.FREECAM;
			if (m.active() && Minecraft.getInstance().screen == null && yOffset != 0) {
				m.scroll(yOffset);
				ci.cancel();
			}
		}
	}
}
