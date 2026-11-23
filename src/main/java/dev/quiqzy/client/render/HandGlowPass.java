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
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.HandShaderModule;

/**
 * Hand Glow als Screen-Space-Effekt: läuft direkt nach dem Hand-Pass. Der Tiefenpuffer enthält
 * da nur die Hand (vanilla leert ihn davor), daraus macht der Shader eine Maske und blutet sie
 * weich nach außen aus. Ein Vollbild-Dreieck, keine zusätzlichen Render-Targets.
 */
public final class HandGlowPass {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/hand_glow_pass"))
					.withVertexShader("core/screenquad")
					.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_hand_glow"))
					.withSampler("DepthSampler")
					.withSampler("GlowSampler")
					.withUniform("ZeroGlow", UniformType.UNIFORM_BUFFER)
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthWrite(false)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.build());

	/** Maske aus dem Tiefenpuffer (1 = Hand). */
	private static final RenderPipeline MASK = RenderPipelines.register(
			RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/hand_glow_mask"))
					.withVertexShader("core/screenquad")
					.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_glow_mask"))
					.withSampler("DepthSampler")
					.withoutBlend()
					.withDepthWrite(false)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.build());

	private static final int MAX_LEVELS = 6;
	private static final com.mojang.blaze3d.textures.GpuTexture[] TEX = new com.mojang.blaze3d.textures.GpuTexture[MAX_LEVELS];
	private static final GpuTextureView[] VIEW = new GpuTextureView[MAX_LEVELS];
	private static int texW, texH;

	private static final int UBO_SIZE = 32;
	private static GpuBuffer ubo;
	private static boolean failed;

	private HandGlowPass() {
	}

	public static void init() {
	}

	public static void render() {
		HandShaderModule m = ModuleManager.HAND_SHADER;
		if (failed || m == null || !m.isEnabled() || !m.glow.on()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
		RenderTarget main = mc.getMainRenderTarget();
		if (main == null || main.getDepthTextureView() == null || main.getColorTextureView() == null) return;
		try {
			draw(m, main);
		} catch (Throwable t) {
			failed = true; // nie das Spiel crashen, nur den Effekt abschalten
			QuiqzyClient.LOGGER.warn("ZERO hand glow disabled: {}", t.toString());
		}
	}

	private static void ensure(int w, int h) {
		if (TEX[0] != null && w == texW && h == texH) return;
		for (int i = 0; i < MAX_LEVELS; i++) {
			if (VIEW[i] != null) VIEW[i].close();
			if (TEX[i] != null) TEX[i].close();
		}
		texW = w;
		texH = h;
		var dev = RenderSystem.getDevice();
		for (int i = 0; i < MAX_LEVELS; i++) {
			int tw = Math.max(1, w >> i), th = Math.max(1, h >> i);
			TEX[i] = dev.createTexture("zero hand glow " + i, 12, com.mojang.blaze3d.textures.TextureFormat.RGBA8, tw, th, 1, 1);
			VIEW[i] = dev.createTextureView(TEX[i]);
		}
	}

	private static void blit(CommandEncoder enc, RenderPipeline pipe, String sampler, GpuTextureView from, GpuTextureView to, GpuSampler smp) {
		try (RenderPass pass = enc.createRenderPass(() -> "zero hand glow blur", to, OptionalInt.empty())) {
			pass.setPipeline(pipe);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture(sampler, from, smp);
			pass.draw(0, 3);
		}
	}

	private static void draw(HandShaderModule m, RenderTarget main) {
		int rgb = m.glowColor.get() & 0xFFFFFF;
		if (dev.quiqzy.client.util.Theme.glass() && m.themeColor.on()) rgb = 0xFFFFFF;
		float opacity = m.glowOpacity.get() / 100f;
		int size = m.glowWidth.get();
		int levels = Math.max(2, Math.min(MAX_LEVELS - 1, 2 + size / 15)); // Glow-Größe = Anzahl Blur-Stufen
		float boost = 1.6f + size / 40f;
		ensure(main.width, main.height);
		CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
		if (ubo == null) {
			ubo = RenderSystem.getDevice().createBuffer(() -> "zero hand glow ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
					.putVec4((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, opacity)
					.putVec4(boost, m.glowInner.get() / 100f, 0f, 0f)
					.get();
			enc.writeToBuffer(ubo.slice(), data);
		}
		GpuTextureView depth = main.getDepthTextureView();
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		// 1) Maske
		blit(enc, MASK, "DepthSampler", depth, VIEW[0], nearest);
		// 2) Dual-Kawase: runter, dann wieder hoch bis halbe Auflösung
		for (int i = 1; i <= levels; i++) blit(enc, dev.quiqzy.client.gui.engine.ZeroPipelines.BLUR_DOWN, "InSampler", VIEW[i - 1], VIEW[i], linear);
		for (int i = levels - 1; i >= 1; i--) blit(enc, dev.quiqzy.client.gui.engine.ZeroPipelines.BLUR_UP, "InSampler", VIEW[i + 1], VIEW[i], linear);
		// 3) Auftragen
		try (RenderPass pass = enc.createRenderPass(() -> "zero hand glow", main.getColorTextureView(), OptionalInt.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("ZeroGlow", ubo);
			pass.bindTexture("DepthSampler", depth, nearest);
			pass.bindTexture("GlowSampler", VIEW[1], linear);
			pass.draw(0, 3);
		}
	}
}
