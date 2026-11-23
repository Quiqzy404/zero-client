package dev.quiqzy.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;

/**
 * Chams (Entity ESP): das Modell wird ein zweites Mal mit dem eigenen Shader "zero_chams" gezeichnet –
 * Skin eingefärbt in der ESP-Farbe + leuchtender Rand. Zwei Pipelines: mit Tiefentest und durch Wände.
 */
public final class ChamsRenderer {
	private static RenderPipeline pipeline(String name, boolean walls) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/" + name))
				.withVertexShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_chams"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_chams"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
				.withCull(true)
				.withDepthWrite(false)
				.withDepthTestFunction(walls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
				.build());
	}

	private static final RenderPipeline NORMAL = pipeline("chams", false);
	private static final RenderPipeline WALLS = pipeline("chams_walls", true);
	private static final Map<Identifier, RenderType> CACHE = new HashMap<>();
	private static final Map<Identifier, RenderType> CACHE_WALLS = new HashMap<>();

	private ChamsRenderer() {
	}

	public static void init() {
	}

	public static RenderType type(Identifier texture, boolean walls) {
		Map<Identifier, RenderType> c = walls ? CACHE_WALLS : CACHE;
		return c.computeIfAbsent(texture, t -> RenderType.create("zero_chams" + (walls ? "_walls" : ""),
				RenderSetup.builder(walls ? WALLS : NORMAL).withTexture("Sampler0", t).createRenderSetup()));
	}

	/** Parameter als "Licht" verpackt (UV2): Block = Rand-Glow 0..15, Himmel = Skin-Details 0/15. */
	public static int packedParams() {
		var esp = ModuleManager.ENTITY_ESP;
		int glow = Math.round(esp.chamsGlow.get() / 100f * 15f);
		int skin = esp.chamsSkin.on() ? 15 : 0;
		return glow << 4 | skin << 20;
	}

	/** ARGB mit Chams-Deckkraft. */
	public static int color(int rgb) {
		int a = Math.round(ModuleManager.ENTITY_ESP.chamsOpacity.get() / 100f * 255f);
		return a << 24 | (rgb & 0xFFFFFF);
	}
}
