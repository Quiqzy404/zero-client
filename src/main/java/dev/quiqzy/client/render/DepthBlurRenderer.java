package dev.quiqzy.client.render;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.gui.engine.ZeroBlur;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.BackgroundBlurModule;

/**
 * Background Blur (Tiefenunschärfe), läuft am Ende der Welt (vor der Hand -> Hand bleibt scharf):
 * 1) Szene weichzeichnen (ZeroBlur), 2) Tiefe kopieren, 3) Vollbild-Pass mischt je nach Entfernung die
 * weiche Version über das Bild.
 */
public final class DepthBlurRenderer {
	private static final int UBO_SIZE = 16;
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/depth_blur"))
					.withVertexShader("core/screenquad")
					.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_dof"))
					.withUniform("ZeroDof", UniformType.UNIFORM_BUFFER)
					.withSampler("Sampler0")
					.withSampler("Sampler1")
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthWrite(false)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.build());

	private static GpuBuffer ubo;
	private static GpuTexture depthTex;
	private static GpuTextureView depthView;
	private static int w, h;
	private static boolean failed;

	private DepthBlurRenderer() {
	}

	public static void init() {
	}

	public static void render() {
		BackgroundBlurModule m = ModuleManager.BACKGROUND_BLUR;
		if (failed || m == null || !m.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		RenderTarget main = mc.getMainRenderTarget();
		if (main == null || main.getColorTextureView() == null || main.getDepthTexture() == null) return;
		try {
			draw(main, m);
		} catch (Throwable t) {
			failed = true;
			QuiqzyClient.LOGGER.warn("ZERO background blur disabled: {}", t.toString());
		}
	}

	private static void draw(RenderTarget main, BackgroundBlurModule m) {
		GpuTexture srcDepth = main.getDepthTexture();
		if (depthTex == null || w != main.width || h != main.height) {
			if (depthView != null) depthView.close();
			if (depthTex != null) depthTex.close();
			w = main.width;
			h = main.height;
			depthTex = RenderSystem.getDevice().createTexture("zero dof depth",
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, srcDepth.getFormat(), w, h, 1, 1);
			depthView = RenderSystem.getDevice().createTextureView(depthTex);
		}
		CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
		enc.copyTextureToTexture(srcDepth, depthTex, 0, 0, 0, 0, 0, w, h);
		ZeroBlur.capture(m.strength.get());
		GpuTextureView blurred = ZeroBlur.result();
		if (blurred == null) return;

		if (ubo == null) ubo = RenderSystem.getDevice().createBuffer(() -> "zero dof ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
		float start = m.start.get();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
					.putVec4(start, start + m.fade.get(), m.amount.get() / 100f, m.sky.on() ? 1f : 0f).get();
			enc.writeToBuffer(ubo.slice(), data);
		}
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (RenderPass pass = enc.createRenderPass(() -> "zero background blur", main.getColorTextureView(), OptionalInt.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setUniform("ZeroDof", ubo);
			pass.bindTexture("Sampler0", blurred, linear);
			pass.bindTexture("Sampler1", depthView, nearest);
			pass.draw(0, 3);
		}
	}
}
