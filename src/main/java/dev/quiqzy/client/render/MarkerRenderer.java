package dev.quiqzy.client.render;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.render.MarkerStore.Mark;

/**
 * Zeichnet die Welt-Markierungen aus {@link MarkerStore}:
 * - CHUNK: ganzer 16×16-Chunk als sauberer Kasten mit nach oben verlaufender (transparenter) Füllung,
 *   doppelter Glow-Kante und einem weichen Strahl nach oben. (Gegner-Markierung)
 * - PING: schlanker Strahl + schwebender, pulsierender Diamant (wie ein Fortnite-Ping).
 *
 * Immer durch Wände sichtbar (eigene Pipeline ohne Tiefentest, nach dem Fabric-1.21.11-Beispiel).
 */
public final class MarkerRenderer {

	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/markers_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	private MarkerRenderer() {
	}

	public static void init() {
	}

	public static void render(WorldRenderContext context) {
		MarkerStore.purge();
		if (MarkerStore.isEmpty()) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		String dim = MarkerStore.dim(mc.level);

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.last().pose();

		BufferBuilder buffer = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());

		long now = System.currentTimeMillis();
		float pulse = 0.5f + 0.5f * (float) Math.sin(now / 420.0);
		float worldMin = mc.level.getMinY();
		float worldMax = mc.level.getMaxY();
		boolean any = false;

		for (Mark mk : MarkerStore.all()) {
			if (!mk.dim.equals(dim)) continue;
			float env = mk.envelope();
			if (env <= 0.01f) continue;
			any = true;
			if (mk.kind == MarkerStore.Kind.CHUNK) {
				chunk(buffer, m, mk, env, pulse, now, worldMin, worldMax);
			} else {
				ping(buffer, m, mk, env, pulse, now);
			}
		}

		matrices.popPose();
		if (any) draw(mc, buffer);
		else buffer.build();
	}

	// ---------------------------------------------------------------- CHUNK

	private static void chunk(BufferBuilder b, Matrix4f m, Mark mk, float env, float pulse, long now, float worldMin, float worldMax) {
		float x0 = mk.cx * 16f;
		float z0 = mk.cz * 16f;
		float x1 = x0 + 16f;
		float z1 = z0 + 16f;
		// GANZER Chunk: volle Welthöhe
		float y0 = worldMin;
		float y1 = worldMax;
		float h = y1 - y0;
		float fy = (float) mk.y; // Höhe des Gegners (Hervorhebung)

		int rgb = mk.rgb;
		int bright = mixWhite(rgb, 0.45f);

		// dezenter vertikaler Verlauf über die ganze Höhe, am Gegner-Level am hellsten
		float midY = Math.max(y0 + 1f, Math.min(y1 - 1f, fy));
		int fillEdge = argb(0.05f * env, rgb);
		int fillGlow = argb(0.16f * env, mixWhite(rgb, 0.25f));
		gradColumn(b, m, x0, z0, x1, z1, y0, midY, fillEdge, fillGlow);
		gradColumn(b, m, x0, z0, x1, z1, midY, y1, fillGlow, fillEdge);

		// leuchtende Platte auf Gegner-Höhe (zeigt die echte Y-Ebene)
		int plate = argb((0.22f + 0.14f * pulse) * env, bright);
		quadY(b, m, x0, z0, x1, z1, fy + 0.02f, plate);

		// Kanten über die ganze Höhe: weicher Saum + heller pulsierender Kern
		int soft = argb((0.14f + 0.08f * pulse) * env, rgb);
		int core = argb((0.60f + 0.30f * pulse) * env, bright);
		edges(b, m, x0, y0, z0, x1, y1, z1, soft, 0.16f);
		edges(b, m, x0, y0, z0, x1, y1, z1, core, 0.06f);

		// kräftige Eck-Pfeiler über die volle Höhe
		int pillar = argb((0.75f + 0.20f * pulse) * env, mixWhite(rgb, 0.5f));
		float e = 0.16f;
		box(b, m, x0, y0, z0, x0 + e, y1, z0 + e, pillar);
		box(b, m, x1 - e, y0, z0, x1, y1, z0 + e, pillar);
		box(b, m, x0, y0, z1 - e, x0 + e, y1, z1, pillar);
		box(b, m, x1 - e, y0, z1 - e, x1, y1, z1, pillar);

		// aufsteigende Scan-Linie über die ganze Höhe
		float period = 3200f;
		float phase = (now % (long) period) / period;
		float scanY = y0 + h * phase;
		float scanFade = (float) Math.sin(phase * Math.PI);
		int scan = argb(0.35f * scanFade * env, bright);
		if (scanY + 0.6f < y1) {
			gradColumn(b, m, x0, z0, x1, z1, scanY, scanY + 0.6f, scan, scan);
		}

		// weicher Strahl nach oben aus der Chunk-Mitte (über Welthöhe hinaus)
		float bx = x0 + 8f, bz = z0 + 8f;
		int beamBottom = argb((0.35f + 0.22f * pulse) * env, mixWhite(rgb, 0.3f));
		int beamTop = argb(0.0f, rgb);
		gradColumn(b, m, bx - 0.5f, bz - 0.5f, bx + 0.5f, bz + 0.5f, y1, y1 + 46f, beamBottom, beamTop);
	}

	// ---------------------------------------------------------------- PING

	private static void ping(BufferBuilder b, Matrix4f m, Mark mk, float env, float pulse, long now) {
		float cx = (float) mk.x;
		float cy = (float) mk.y;
		float cz = (float) mk.z;
		int rgb = mk.rgb;

		// Strahl: vom Boden-nahen Punkt nach oben, verlaufend
		int beamBottom = argb((0.55f + 0.25f * pulse) * env, mixWhite(rgb, 0.25f));
		int beamTop = argb(0.0f, rgb);
		gradColumn(b, m, cx - 0.14f, cz - 0.14f, cx + 0.14f, cz + 0.14f, cy - 1.0f, cy + 28f, beamBottom, beamTop);

		// schwebender, pulsierender Diamant am Punkt
		float bob = (float) Math.sin(now / 500.0) * 0.18f;
		float r = 0.42f + 0.06f * pulse;
		int solid = argb((0.85f) * env, mixWhite(rgb, 0.15f));
		int glow = argb((0.30f) * env, rgb);
		diamond(b, m, cx, cy + 1.6f + bob, cz, r * 1.35f, glow);
		diamond(b, m, cx, cy + 1.6f + bob, cz, r, solid);
	}

	// ---------------------------------------------------------------- Geometrie

	/** 4 Seitenflächen einer Säule mit vertikalem Farbverlauf (unten cB, oben cT). */
	private static void gradColumn(BufferBuilder b, Matrix4f m, float x0, float z0, float x1, float z1, float y0, float y1, int cB, int cT) {
		// +Z
		b.addVertex(m, x0, y0, z1).setColor(cB);
		b.addVertex(m, x1, y0, z1).setColor(cB);
		b.addVertex(m, x1, y1, z1).setColor(cT);
		b.addVertex(m, x0, y1, z1).setColor(cT);
		// -Z
		b.addVertex(m, x1, y0, z0).setColor(cB);
		b.addVertex(m, x0, y0, z0).setColor(cB);
		b.addVertex(m, x0, y1, z0).setColor(cT);
		b.addVertex(m, x1, y1, z0).setColor(cT);
		// -X
		b.addVertex(m, x0, y0, z0).setColor(cB);
		b.addVertex(m, x0, y0, z1).setColor(cB);
		b.addVertex(m, x0, y1, z1).setColor(cT);
		b.addVertex(m, x0, y1, z0).setColor(cT);
		// +X
		b.addVertex(m, x1, y0, z1).setColor(cB);
		b.addVertex(m, x1, y0, z0).setColor(cB);
		b.addVertex(m, x1, y1, z0).setColor(cT);
		b.addVertex(m, x1, y1, z1).setColor(cT);
	}

	/** Waagrechte Fläche auf Höhe y (beide Seiten sichtbar). */
	private static void quadY(BufferBuilder b, Matrix4f m, float x0, float z0, float x1, float z1, float y, int c) {
		b.addVertex(m, x0, y, z0).setColor(c);
		b.addVertex(m, x1, y, z0).setColor(c);
		b.addVertex(m, x1, y, z1).setColor(c);
		b.addVertex(m, x0, y, z1).setColor(c);
		b.addVertex(m, x0, y, z1).setColor(c);
		b.addVertex(m, x1, y, z1).setColor(c);
		b.addVertex(m, x1, y, z0).setColor(c);
		b.addVertex(m, x0, y, z0).setColor(c);
	}

	/** 12 Kanten eines Kastens als dünne Balken. */
	private static void edges(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c, float e) {
		box(b, m, x0, y0, z0, x1, y0 + e, z0 + e, c);
		box(b, m, x0, y0, z1 - e, x1, y0 + e, z1, c);
		box(b, m, x0, y0, z0, x0 + e, y0 + e, z1, c);
		box(b, m, x1 - e, y0, z0, x1, y0 + e, z1, c);
		box(b, m, x0, y1 - e, z0, x1, y1, z0 + e, c);
		box(b, m, x0, y1 - e, z1 - e, x1, y1, z1, c);
		box(b, m, x0, y1 - e, z0, x0 + e, y1, z1, c);
		box(b, m, x1 - e, y1 - e, z0, x1, y1, z1, c);
		box(b, m, x0, y0, z0, x0 + e, y1, z0 + e, c);
		box(b, m, x1 - e, y0, z0, x1, y1, z0 + e, c);
		box(b, m, x0, y0, z1 - e, x0 + e, y1, z1, c);
		box(b, m, x1 - e, y0, z1 - e, x1, y1, z1, c);
	}

	/** Diamant (Oktaeder) als 8 Dreiecke, im QUADS-Modus mit doppeltem letzten Vertex. */
	private static void diamond(BufferBuilder b, Matrix4f m, float cx, float cy, float cz, float r, int c) {
		float top = cy + r, bot = cy - r;
		float[][] eq = {{cx + r, cz}, {cx, cz + r}, {cx - r, cz}, {cx, cz - r}};
		for (int i = 0; i < 4; i++) {
			float[] a = eq[i];
			float[] d = eq[(i + 1) % 4];
			tri(b, m, cx, top, cz, a[0], cy, a[1], d[0], cy, d[1], c);     // obere Kappe
			tri(b, m, cx, bot, cz, d[0], cy, d[1], a[0], cy, a[1], c);     // untere Kappe
		}
	}

	private static void tri(BufferBuilder b, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, int c) {
		b.addVertex(m, ax, ay, az).setColor(c);
		b.addVertex(m, bx, by, bz).setColor(c);
		b.addVertex(m, cx, cy, cz).setColor(c);
		b.addVertex(m, cx, cy, cz).setColor(c); // QUAD -> Dreieck durch doppelten Vertex
	}

	/** Gefüllte Box (6 Flächen). */
	private static void box(BufferBuilder b, Matrix4f m, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int c) {
		b.addVertex(m, minX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, minX, minY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, minX, minY, maxZ).setColor(c);
	}

	// ---------------------------------------------------------------- Farbe

	private static int argb(float a, int rgb) {
		int ai = Math.max(0, Math.min(255, (int) (a * 255f)));
		return (ai << 24) | (rgb & 0xFFFFFF);
	}

	/** rgb Richtung Weiß ziehen (0 = original, 1 = weiß). */
	private static int mixWhite(int rgb, float t) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
		r = (int) (r + (255 - r) * t);
		g = (int) (g + (255 - g) * t);
		bl = (int) (bl + (255 - bl) * t);
		return (r << 16) | (g << 8) | bl;
	}

	// ---------------------------------------------------------------- Zeichnen ohne Tiefentest

	private static void draw(Minecraft mc, BufferBuilder buffer) {
		MeshData mesh = buffer.buildOrThrow();
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " markers", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
		}
		GpuBuffer vertices = vertexBuffer.currentBuffer();

		GpuBuffer indices;
		VertexFormat.IndexType indexType;
		if (THROUGH_WALLS.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			mesh.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
			indices = THROUGH_WALLS.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
			indexType = mesh.drawState().indexType();
		} else {
			RenderSystem.AutoStorageIndexBuffer shapes = RenderSystem.getSequentialBuffer(THROUGH_WALLS.getVertexFormatMode());
			indices = shapes.getBuffer(state.indexCount());
			indexType = shapes.type();
		}

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> QuiqzyClient.MOD_ID + " markers pass",
				mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
				mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(THROUGH_WALLS);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, state.indexCount(), 1);
		}

		mesh.close();
		vertexBuffer.rotate();
	}
}
