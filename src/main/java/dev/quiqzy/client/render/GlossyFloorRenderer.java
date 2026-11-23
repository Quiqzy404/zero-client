package dev.quiqzy.client.render;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormatElement.Type;
import com.mojang.blaze3d.vertex.VertexFormatElement.Usage;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.mixin.BufferBuilderAccessor;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.GlossyFloorModule;

/**
 * Glossy Floor mit echter Screen-Space-Reflexion:
 * Der Renderer kopiert pro Frame die Szenen-Farbtextur (vor allen Overlays) und zeichnet
 * Boden-Quads. Im Fragment-Shader wird der Blickstrahl an der Boden-Ebene gespiegelt und
 * durch die Szenen-Kopie gemittelt - Gebaeude/Lampen spiegeln sich real im nassen Boden
 * (wie im Referenz-Clip).
 */
public final class GlossyFloorRenderer {
	// Vertex: Position + Color + Params(x = Intensitaet, y = Reflextionslaenge in m)
	private static final VertexFormatElement FLOOR_PARAMS = element(Type.FLOAT, Usage.GENERIC, 2);
	private static final VertexFormat FLOOR_FORMAT = VertexFormat.builder()
			.add("Position", VertexFormatElement.POSITION)
			.add("Color", VertexFormatElement.COLOR)
			.add("Params", FLOOR_PARAMS)
			.build();

	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/glossy_floor"))
					.withVertexShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_glossy"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_glossy"))
					.withBlend(BlendFunction.TRANSLUCENT)
					.withVertexFormat(FLOOR_FORMAT, VertexFormat.Mode.QUADS)
					.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false) // Quads sind im Uhrzeigersinn -> sonst als Rückseite weggeschnitten (war der Bug)
					.withSampler("Sampler0")
					.withSampler("Sampler1")
					.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	// Szenen-Kopie (Reflexions-Quelle)
	private static GpuTexture capTex;
	private static GpuTextureView capView;
	private static GpuTexture capDepth;
	private static GpuTextureView capDepthView;
	private static int capW, capH;

	// Boden-Hoehen-Cache (neu nur bei Block-/Hoehen-/Weltenwechsel)
	private static short[] heights;
	private static int hRange, hStep, hBaseX, hBaseZ, hBaseY;
	private static Level hLevel;
	private static final short NONE = Short.MIN_VALUE;

	private static boolean warned;

	// Vollbild-SSR (reflektiert jede nach oben zeigende Fläche – Gras/Boden/Stufen)
	private static final int UBO_SIZE = 16; // vec4 GParams
	private static GpuBuffer gUbo;
	private static final RenderPipeline FULL = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/glossy_full"))
					.withVertexShader("core/screenquad")
					.withFragmentShader(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "core/zero_glossy_full"))
					.withUniform("ZeroGloss", UniformType.UNIFORM_BUFFER)
					.withSampler("Sampler0")
					.withSampler("Sampler1")
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthWrite(false)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
					.build());

	private GlossyFloorRenderer() {
	}

	/** Lädt die Klasse früh, damit die Pipeline beim Start registriert wird. */
	public static void init() {
	}

	public static void render(WorldRenderContext ctx) {
		GlossyFloorModule m = ModuleManager.GLOSSY_FLOOR;
		if (!m.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!captureScene(mc)) return;
		try {
			drawFull(mc, m);
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				QuiqzyClient.LOGGER.warn("ZERO glossy full-screen failed: {}", t.toString());
			}
		}
	}

	/** Vollbild-SSR: ein Dreieck über das ganze Bild; der Shader spiegelt jede nach oben zeigende Fläche. */
	private static void drawFull(Minecraft mc, GlossyFloorModule m) {
		RenderTarget main = mc.getMainRenderTarget();
		if (main == null || main.getColorTextureView() == null || main.getDepthTextureView() == null) return;
		CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
		if (gUbo == null) gUbo = RenderSystem.getDevice().createBuffer(() -> "zero glossy ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
		float intensity = m.intensity.get() / 100f;
		float maxLen = 3f + 14f * (m.length.get() / 100f);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE).putVec4(intensity, maxLen, 0f, 0f).get();
			enc.writeToBuffer(gUbo.slice(), data);
		}
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (RenderPass pass = enc.createRenderPass(() -> QuiqzyClient.MOD_ID + " glossy full",
				main.getColorTextureView(), OptionalInt.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(FULL);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setUniform("ZeroGloss", gUbo);
			pass.bindTexture("Sampler0", capView, linear);
			pass.bindTexture("Sampler1", capDepthView, nearest);
			pass.draw(0, 3);
		}
	}

	private static void unusedScanPath(Minecraft mc, GlossyFloorModule m, WorldRenderContext ctx) {
		scan(mc, m);
		if (heights == null) return;

		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		// Vertices kamerarelativ (vorher fehlte das: die Quads lagen weit neben der Kamera)
		Matrix4f pose = ctx.matrices().last().pose();
		float intensity = m.intensity.get() / 100f;
		float len = 3f + 14f * (m.length.get() / 100f);

		BufferBuilder buf = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
		int n = hStep > 0 ? (hRange * 2) / hStep + 1 : 0;
		int quads = 0;
		for (int i = 0; i < n; i++) {
			int x = hBaseX - hRange + i * hStep;
			for (int j = 0; j < n; j++) {
				short sy = heights[i * n + j];
				if (sy == NONE) continue;
				int z = hBaseZ - hRange + j * hStep;
				float y = (float) (sy + 1.012 - cam.y); // leicht ueber der Oberflaeche -> kein Z-Fighting
				float x1 = (float) (x - cam.x), x2 = (float) (x + hStep - cam.x), z1 = (float) (z - cam.z), z2 = (float) (z + hStep - cam.z);
				quad(buf, pose, x1, y, z1, x2, y, z1, x2, y, z2, x1, y, z2, intensity, len);
				quads++;
			}
		}
		if (quads == 0) return;

		draw(mc, buf, cam);
	}

	/**
	 * Boden-Scan über Minecrafts Heightmap: pro Spalte die echte Oberfläche – folgt dem Gelände lückenlos
	 * (vorher nur ein Fenster um die Spielerhöhe -> auf Hügeln einzelne schwebende Quads).
	 * Ergebnis wird pro Spieler-Block gecacht.
	 */
	private static void scan(Minecraft mc, GlossyFloorModule m) {
		int range = Math.max(4, m.range.get());
		int step = Math.max(1, Math.round(range / 24f)); // fein, aber Anzahl der Quads gedeckelt
		BlockPos pp = mc.player.blockPosition();
		int bx = Math.floorDiv(pp.getX(), step) * step;
		int bz = Math.floorDiv(pp.getZ(), step) * step;
		int by = pp.getY();
		if (heights != null && mc.level == hLevel && range == hRange && step == hStep
				&& bx == hBaseX && bz == hBaseZ && by == hBaseY) return;

		int n = (range * 2) / step + 1;
		short[] hs = new short[n * n];
		int minY = mc.level.getMinY();
		for (int i = 0; i < n; i++) {
			int x = bx - range + i * step;
			for (int j = 0; j < n; j++) {
				int z = bz - range + j * step;
				// Oberkante der obersten festen Oberfläche (MOTION_BLOCKING_NO_LEAVES = Boden ohne Blätter)
				int top = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				hs[i * n + j] = top <= minY ? NONE : (short) (top - 1); // y des festen Blocks (seine Oberkante)
			}
		}
		heights = hs;
		hRange = range;
		hStep = step;
		hBaseX = bx;
		hBaseZ = bz;
		hBaseY = by;
		hLevel = mc.level;
	}

	/** Kopiert die komplette Szenen-Farbtextur (vor allen Overlays) in die eigene Textur. */
	private static boolean captureScene(Minecraft mc) {
		RenderTarget main = mc.getMainRenderTarget();
		GpuTexture src = main == null ? null : main.getColorTexture();
		GpuTexture srcDepth = main == null ? null : main.getDepthTexture();
		if (src == null || srcDepth == null || main.width <= 0 || main.height <= 0) return false;
		try {
			if (capTex == null || capW != main.width || capH != main.height) {
				if (capView != null) capView.close();
				if (capTex != null) capTex.close();
				if (capDepthView != null) capDepthView.close();
				if (capDepth != null) capDepth.close();
				capW = main.width;
				capH = main.height;
				capTex = RenderSystem.getDevice().createTexture("zero glossy scene",
						GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
						TextureFormat.RGBA8, capW, capH, 1, 1);
				capView = RenderSystem.getDevice().createTextureView(capTex);
				capDepth = RenderSystem.getDevice().createTexture("zero glossy depth",
						GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
						srcDepth.getFormat(), capW, capH, 1, 1);
				capDepthView = RenderSystem.getDevice().createTextureView(capDepth);
			}
			CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
			enc.copyTextureToTexture(src, capTex, 0, 0, 0, 0, 0, capW, capH);
			enc.copyTextureToTexture(srcDepth, capDepth, 0, 0, 0, 0, 0, capW, capH);
			return true;
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				QuiqzyClient.LOGGER.warn("ZERO glossy floor capture failed: {}", t.toString());
			}
			return false;
		}
	}

	private static void quad(BufferBuilder b, Matrix4f pose, float x1, float y1, float z1, float x2, float y2, float z2,
			float x3, float y3, float z3, float x4, float y4, float z4, float intensity, float len) {
		vertex(b, pose, x1, y1, z1, intensity, len);
		vertex(b, pose, x2, y2, z2, intensity, len);
		vertex(b, pose, x3, y3, z3, intensity, len);
		vertex(b, pose, x4, y4, z4, intensity, len);
	}

	private static void vertex(BufferBuilder b, Matrix4f pose, float x, float y, float z, float intensity, float len) {
		b.addVertex(pose, x, y, z).setColor(0xFFFFFFFF);
		long p = beginElement(b, FLOOR_PARAMS);
		MemoryUtil.memPutFloat(p, intensity);
		MemoryUtil.memPutFloat(p + 4L, len);
	}

	/** Params-Element schreiben: Accessor-Mixin, sonst Reflection (wie in ZeroRender). */
	private static java.lang.reflect.Method BEGIN;

	private static long beginElement(BufferBuilder b, VertexFormatElement element) {
		if (b instanceof BufferBuilderAccessor acc) return acc.zero$beginElement(element);
		try {
			if (BEGIN == null) {
				for (Class<?> c = b.getClass(); c != null && BEGIN == null; c = c.getSuperclass()) {
					for (java.lang.reflect.Method meth : c.getDeclaredMethods()) {
						Class<?>[] p = meth.getParameterTypes();
						if (meth.getReturnType() == long.class && p.length == 1 && p[0] == VertexFormatElement.class) {
							meth.setAccessible(true);
							BEGIN = meth;
							break;
						}
					}
				}
			}
			return (long) BEGIN.invoke(b, element);
		} catch (Throwable t) {
			throw new RuntimeException("glossy begin-element failed", t);
		}
	}

	private static void draw(Minecraft mc, BufferBuilder buf, Vec3 cam) {
		MeshData mesh = buf.build();
		if (mesh == null) return;
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " glossy", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
		}
		GpuBuffer vertices = vertexBuffer.currentBuffer();

		mesh.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
		GpuBuffer indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
		VertexFormat.IndexType indexType = state.indexType();

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		try (RenderPass pass = encoder.createRenderPass(() -> QuiqzyClient.MOD_ID + " glossy pass",
				mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
				mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler0", capView, linear);
			pass.bindTexture("Sampler1", capDepthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, state.indexCount(), 1);
		}

		mesh.close();
		vertexBuffer.rotate();
	}

	private static VertexFormatElement element(Type type, Usage usage, int count) {
		for (int id = 31; id > 0; id--) {
			if (VertexFormatElement.byId(id) == null) {
				return VertexFormatElement.register(id, 0, type, usage, count);
			}
		}
		throw new IllegalStateException("Keine freie Vertex-Element-ID");
	}
}
