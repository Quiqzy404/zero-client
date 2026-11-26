package dev.quiqzy.client.render;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.hud.AuroraHud;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.CustomSkyModule;

/**
 * Custom Sky: ein Vollbild-Dreieck nach dem Terrain (BEFORE_TRANSLUCENT). Der Shader schreibt
 * gl_FragDepth = 1 und testet LEQUAL -> malt nur Himmels-Pixel (Tiefe 1), Blöcke bleiben unberührt.
 */
public final class SkyRenderer {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/custom_sky"))
					.withVertexShader("core/screenquad")
					.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_sky"))
					.withUniform("ZeroSky", UniformType.UNIFORM_BUFFER)
					.withoutBlend()
					.withDepthWrite(false)
					.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.build());

	/** Fallender Schnee (Snowy): über alles, transparent. */
	private static final RenderPipeline SNOW = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/custom_sky_snow"))
					.withVertexShader("core/screenquad")
					.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_snow"))
					.withUniform("ZeroSky", UniformType.UNIFORM_BUFFER)
					.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
					.withDepthWrite(false)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.build());

	private static final int UBO_SIZE = 32;
	private static GpuBuffer ubo;
	private static boolean failed;
	private static final long START = System.nanoTime();
	private static double animTime;
	private static long lastNs;

	private SkyRenderer() {
	}

	public static void init() {
	}

	public static boolean active() {
		CustomSkyModule m = ModuleManager.CUSTOM_SKY;
		Minecraft mc = Minecraft.getInstance();
		if (failed || m == null || !m.isEnabled() || mc.level == null) return false;
		return !m.overworldOnly.on() || mc.level.dimension() == Level.OVERWORLD;
	}

	public static void render(WorldRenderContext ctx) {
		if (!active()) return;
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = mc.getMainRenderTarget();
		if (main == null || main.getColorTextureView() == null || main.getDepthTextureView() == null) return;
		try {
			draw(main);
		} catch (Throwable t) {
			failed = true;
			QuiqzyClient.LOGGER.warn("ZERO custom sky disabled: {}", t.toString());
		}
	}

	private static void draw(RenderTarget main) {
		CustomSkyModule m = ModuleManager.CUSTOM_SKY;
		long now = System.nanoTime();
		if (lastNs != 0) animTime += (now - lastNs) / 1e9 * (m.speed.asFloat() * 4.0);
		lastNs = now;
		int theme = AuroraHud.accent() & 0xFFFFFF;
		CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
		if (ubo == null) ubo = RenderSystem.getDevice().createBuffer(() -> "zero sky ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
					.putVec4((float) (animTime % 10000.0), m.presetIndex(), m.intensity.get() / 100f, m.scale.asFloat())
					.putVec4((theme >> 16 & 255) / 255f, (theme >> 8 & 255) / 255f, (theme & 255) / 255f, 0f)
					.get();
			enc.writeToBuffer(ubo.slice(), data);
		}
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
		try (RenderPass pass = enc.createRenderPass(() -> "zero custom sky", main.getColorTextureView(), OptionalInt.empty(),
				main.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setUniform("ZeroSky", ubo);
			pass.draw(0, 3);
		}
	}
}
