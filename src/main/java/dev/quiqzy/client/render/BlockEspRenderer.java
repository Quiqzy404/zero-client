package dev.quiqzy.client.render;

import java.util.List;
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
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.world.BlockEspModule;
import dev.quiqzy.client.module.world.BlockEspModule.Hit;

/**
 * Zeichnet die Block-ESP-Boxen. Gleiche Technik wie SusChunkRenderer:
 * eigene Pipeline ohne Tiefentest für "Through Blocks", sonst normaler debugFilledBox-Puffer.
 */
public final class BlockEspRenderer {
	/** Mehr Boxen werden nicht gezeichnet, damit es nicht laggt. */
	private static final int MAX_BOXES = 2500;

	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/block_esp_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	private BlockEspRenderer() {
	}

	/** Lädt die Klasse früh, damit die Pipeline beim Start registriert wird. */
	public static void init() {
	}

	public static void render(WorldRenderContext context) {
		BlockEspModule module = ModuleManager.BLOCK_ESP;
		if (!module.isEnabled()) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		BlockEspModule.Snapshot snap = module.snapshot();
		if (snap.hits().isEmpty()) return;

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.last().pose();

		BufferBuilder walls = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());
		int wallQuads = 0;
		VertexConsumer faces = module.throughWalls.on() ? walls : context.consumers().getBuffer(RenderTypes.debugFilledBox());
		int n = emitFaces(faces, m, module, snap, cam);
		if (module.throughWalls.on()) wallQuads += n;

		boolean tracers = module.tracers.on() || (ModuleManager.TRACERS.isEnabled() && ModuleManager.TRACERS.blocks.on());
		if (tracers) wallQuads += emitTracers(walls, m, mc, module, snap, cam);

		matrices.popPose();
		if (wallQuads > 0) drawThroughWalls(mc, walls);
		else {
			MeshData unused = walls.build();
			if (unused != null) unused.close();
		}
	}

	/**
	 * Nur Farbe, keine Outline: pro Block werden nur die Seiten gezeichnet, an denen KEIN gleichfarbiger
	 * Treffer liegt -> eine Ader ist eine saubere Form ohne innere Linien. Nächste Blöcke zuerst (Limit).
	 */
	private static int emitFaces(VertexConsumer b, Matrix4f m, BlockEspModule module, BlockEspModule.Snapshot snap, Vec3 cam) {
		List<Hit> hits = snap.hits();
		Integer[] order = new Integer[hits.size()];
		for (int i = 0; i < order.length; i++) order[i] = i;
		if (order.length > MAX_BOXES) {
			double[] d = new double[order.length];
			for (int i = 0; i < d.length; i++) d[i] = hits.get(i).pos().distToCenterSqr(cam);
			java.util.Arrays.sort(order, (x, y) -> Double.compare(d[x], d[y]));
		}
		float alpha = module.fillOpacity.get() / 100f;
		var rgbAt = snap.rgbAt();
		int quads = 0;
		final float o = 0.002f; // minimal nach außen, damit es nicht mit der Blockfläche flimmert
		for (int k = 0; k < order.length && k < MAX_BOXES; k++) {
			int i = order[k];
			Hit hit = hits.get(i);
			BlockPos p = hit.pos();
			int rgb = hit.rgb();
			int c = ARGB.color(Math.round(alpha * 255f), module.drawColor(hit, snap.veinOf()[i]));
			int x = p.getX(), y = p.getY(), z = p.getZ();
			float x0 = x - o, y0 = y - o, z0 = z - o, x1 = x + 1 + o, y1 = y + 1 + o, z1 = z + 1 + o;
			if (!same(rgbAt, x, y + 1, z, rgb)) { quad(b, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, c); quads++; }
			if (!same(rgbAt, x, y - 1, z, rgb)) { quad(b, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c); quads++; }
			if (!same(rgbAt, x, y, z + 1, rgb)) { quad(b, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c); quads++; }
			if (!same(rgbAt, x, y, z - 1, rgb)) { quad(b, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, c); quads++; }
			if (!same(rgbAt, x - 1, y, z, rgb)) { quad(b, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c); quads++; }
			if (!same(rgbAt, x + 1, y, z, rgb)) { quad(b, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, c); quads++; }
		}
		return quads;
	}

	private static boolean same(java.util.Map<Long, Integer> rgbAt, int x, int y, int z, int rgb) {
		Integer c = rgbAt.get(BlockPos.asLong(x, y, z));
		return c != null && c == rgb;
	}

	/**
	 * Ein Tracer pro Ader (zur Ader-Mitte), die nächsten zuerst bis zum Limit -> kein Linien-Wirrwarr,
	 * und jede nahe Ader bekommt sicher einen. Die Linie blendet vom Fadenkreuz aus ein (kein Stern in der Mitte).
	 */
	private static int emitTracers(VertexConsumer b, Matrix4f m, Minecraft mc, BlockEspModule module, BlockEspModule.Snapshot snap, Vec3 cam) {
		List<BlockEspModule.Vein> veins = new java.util.ArrayList<>(snap.veins());
		veins.sort(java.util.Comparator.comparingDouble(v -> cam.distanceToSqr(v.x(), v.y(), v.z())));
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 start = EspRenderer.tracerStart(mc, ModuleManager.TRACERS, cam, pt);
		Vec3 fwd = EspRenderer.camForward(mc);
		double width = ModuleManager.TRACERS.width.get() * 0.0005;
		float alpha = module.tracerOpacity.get() / 100f;
		int limit = module.tracerLimit.get(), quads = 0;
		for (int i = 0; i < veins.size() && i < limit; i++) {
			BlockEspModule.Vein v = veins.get(i);
			int rgb = module.drawColor(new Hit(BlockPos.ZERO, v.rgb()), v.index()) & 0xFFFFFF;
			quads += fadeLine(b, m, cam, fwd, start, new Vec3(v.x(), v.y(), v.z()), width, rgb, alpha);
		}
		return quads;
	}

	/** Dünne Linie mit konstanter Bildschirmbreite, Alpha von 0 (Start) bis alpha (Ziel). */
	private static int fadeLine(VertexConsumer buf, Matrix4f m, Vec3 cam, Vec3 fwd, Vec3 a, Vec3 b, double halfAngle, int rgb, float alpha) {
		double near = 0.05;
		double d0 = a.subtract(cam).dot(fwd), d1 = b.subtract(cam).dot(fwd);
		if (d0 < near && d1 < near) return 0;
		float fa = 0f, fb = 1f;
		if (d0 < near) {
			double t = (near - d0) / (d1 - d0);
			a = a.lerp(b, t);
			fa = (float) t;
		} else if (d1 < near) {
			double t = (near - d1) / (d0 - d1);
			b = b.lerp(a, t);
			fb = (float) (1 - t);
		}
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1e-8) return 0;
		Vec3 sideA = EspRenderer.side(dir, a.subtract(cam)), sideB = EspRenderer.side(dir, b.subtract(cam));
		if (sideA == null || sideB == null) return 0;
		if (sideA.dot(sideB) < 0) sideB = sideB.scale(-1);
		double halfA = Math.max(0.0005, a.distanceTo(cam) * halfAngle), halfB = Math.max(0.0005, b.distanceTo(cam) * halfAngle);
		Vec3 a1 = a.add(sideA.scale(halfA)), a2 = a.subtract(sideA.scale(halfA));
		Vec3 b1 = b.add(sideB.scale(halfB)), b2 = b.subtract(sideB.scale(halfB));
		int ca = ARGB.color(Math.round(alpha * fa * fa * 255f), rgb); // quadratisch: Start bleibt lange durchsichtig
		int cb = ARGB.color(Math.round(alpha * fb * 255f), rgb);
		v(buf, m, a1, ca); v(buf, m, a2, ca); v(buf, m, b2, cb); v(buf, m, b1, cb);
		v(buf, m, b1, cb); v(buf, m, b2, cb); v(buf, m, a2, ca); v(buf, m, a1, ca);
		return 2;
	}

	private static void v(VertexConsumer b, Matrix4f m, Vec3 p, int c) {
		b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(c);
	}

	private static void quad(VertexConsumer b, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, int c) {
		b.addVertex(m, ax, ay, az).setColor(c);
		b.addVertex(m, bx, by, bz).setColor(c);
		b.addVertex(m, cx, cy, cz).setColor(c);
		b.addVertex(m, dx, dy, dz).setColor(c);
	}

	private static void box(VertexConsumer b, Matrix4f m, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int c) {
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

	private static void drawThroughWalls(Minecraft mc, BufferBuilder buffer) {
		MeshData mesh = buffer.buildOrThrow();
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " block esp", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
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
				() -> QuiqzyClient.MOD_ID + " block esp pass",
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
