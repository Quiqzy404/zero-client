package dev.quiqzy.client.render;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;

/**
 * Shader-ESP (eigene Umsetzung): ESP-Ziele laufen über Minecrafts Glow-Mechanik in den Entity-Outline-Puffer
 * (dort landet der komplette Körper inkl. Rüstung, Kopf, gehaltener Items – in der ESP-Farbe).
 * Statt des Vanilla-Glow-Shaders macht unser Pass daraus Füllung + gleichmäßigen Umriss + weichen Glow.
 */
public final class EspShaderRenderer {
	private static final RenderPipeline COMPOSITE = RenderPipelines.register(RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/esp_composite"))
			.withVertexShader("core/screenquad")
			.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_esp_composite"))
			.withSampler("MaskSampler")
			.withUniform("ZeroEsp", UniformType.UNIFORM_BUFFER)
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.build());

	private static GpuBuffer ubo;
	private static boolean failed;

	private EspShaderRenderer() {
	}

	public static void init() {
	}

	public static boolean active() {
		var esp = ModuleManager.ENTITY_ESP;
		return !failed && esp != null && esp.isEnabled() && esp.shader();
	}

	/** Ersetzt LevelRenderer.doEntityOutline: Outline-Puffer -> Füllung + Umriss + Glow auf das Bild. */
	public static void composite(RenderTarget outline) {
		if (outline == null || outline.getColorTextureView() == null) return;
		try {
			var esp = ModuleManager.ENTITY_ESP;
			Minecraft mc = Minecraft.getInstance();
			RenderTarget main = mc.getMainRenderTarget();
			float scale = Math.max(1f, main.height / 1080f);
			float width = Math.max(1f, Math.min(6f, (0.5f + esp.lineWidth.get() * 0.5f) * scale));
			float glow = esp.shaderGlow.on() ? (2f + esp.glowSize.get() / 100f * 14f) * scale : 0f;
			CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
			if (ubo == null) ubo = RenderSystem.getDevice().createBuffer(() -> "zero esp ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				ByteBuffer data = Std140Builder.onStack(stack, 16)
						.putVec4(esp.shaderFill.get() / 100f, esp.lineOpacity.get() / 100f, width, glow)
						.get();
				enc.writeToBuffer(ubo.slice(), data);
			}
			try (RenderPass pass = enc.createRenderPass(() -> "zero esp composite", main.getColorTextureView(), OptionalInt.empty())) {
				pass.setPipeline(COMPOSITE);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("ZeroEsp", ubo);
				pass.bindTexture("MaskSampler", outline.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.draw(0, 3);
			}
		} catch (Throwable e) {
			failed = true;
			QuiqzyClient.LOGGER.warn("ZERO shader esp disabled: {}", e.toString());
		}
	}
}
