package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.renderer.fog.FogRenderer;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.NoFogModule;

/**
 * NoFog: setzt die Nebel-Distanzen kurz vor dem Hochladen in den GPU-Puffer weit nach hinten.
 * Args von updateBuffer: 0 Buffer, 1 Index, 2 Farbe, 3 envStart, 4 envEnd, 5 renderStart, 6 renderEnd, 7 skyEnd, 8 cloudEnd.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@ModifyArgs(method = "setupFog", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
	private void zero$noFog(Args args) {
		// Custom Sky: Nebelfarbe = Horizont des Himmels -> keine harte Kante am Ende der Sichtweite
		var sky = ModuleManager.CUSTOM_SKY;
		if (dev.quiqzy.client.render.SkyRenderer.active() && sky.matchFog.on()) {
			org.joml.Vector4f c = args.get(2);
			float[] h = sky.horizon(dev.quiqzy.client.hud.AuroraHud.accent() & 0xFFFFFF);
			args.set(2, new org.joml.Vector4f(h[0], h[1], h[2], c.w));
		}
		NoFogModule m = ModuleManager.NO_FOG;
		if (!m.isEnabled()) return;
		float far = 100000f;
		float envEnd = args.get(4);
		boolean thick = envEnd < 60f; // Wasser/Lava/Puderschnee/Blindheit haben sehr kurzen Umwelt-Nebel
		if (!thick || m.liquids.on()) {
			args.set(3, far);
			args.set(4, far + 1f);
		}
		if (m.distance.on()) {
			args.set(5, far);
			args.set(6, far + 1f);
		}
	}
}
