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
import net.minecraft.client.player.LocalPlayer;
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
import dev.quiqzy.client.module.visuals.StorageEspModule;
import dev.quiqzy.client.module.visuals.StorageEspModule.Hit;

/**
 * Storage ESP Renderer (Krispy-Look): je Container eine weich gefüllte Box mit leuchtender Doppelkante,
 * dazu ein Fächer aus dünnen Tracern, die an der Hand zusammenlaufen. Optional ohne Tiefentest (durch Wände).
 */
public final class StorageEspRenderer {
	private static final float INSET = 0.02f;
	private static final float EDGE = 0.025f;
	private static final float HALO = 0.065f;

	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/storage_esp_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());
	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	private StorageEspRenderer() {
	}

	public static void init() {
	}

	public static void render(WorldRenderContext ctx) {
		StorageEspModule mod = ModuleManager.STORAGE_ESP;
		if (!mod.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) return;
		List<Hit> hits = mod.hits();
		if (hits.isEmpty()) return;

		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		PoseStack ms = ctx.matrices();
		ms.pushPose();
		ms.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = ms.last().pose();

		boolean tw = mod.throughWalls.on();
		BufferBuilder walls = tw ? new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat()) : null;
		VertexConsumer b = tw ? walls : ctx.consumers().getBuffer(RenderTypes.debugFilledBox());

		float fillA = mod.fill.get() / 100f;
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 500.0);
		boolean glow = mod.glow.on();

		// Boxen
		for (Hit h : hits) {
			BlockPos pos = h.pos();
			float x0 = pos.getX() + INSET, y0 = pos.getY() + INSET, z0 = pos.getZ() + INSET;
			float x1 = pos.getX() + 1 - INSET, y1 = pos.getY() + 1 - INSET, z1 = pos.getZ() + 1 - INSET;
			int rgb = h.rgb();
			if (fillA > 0) box(b, m, x0, y0, z0, x1, y1, z1, col(rgb, fillA * (0.8f + 0.2f * pulse)));
			if (glow) edges(b, m, x0, y0, z0, x1, y1, z1, HALO, col(rgb, 0.22f + 0.1f * pulse));
			edges(b, m, x0, y0, z0, x1, y1, z1, EDGE, col(rgb, 0.95f));
		}

		// Tracer-Fächer aus der Hand
		if (mod.tracers.on()) {
			Vec3 eye = p.getEyePosition(pt);
			Vec3 fwd = p.getViewVector(pt);
			Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
			Vec3 start = eye.add(fwd.scale(0.85)).add(right.scale(0.32)).subtract(0, 0.30, 0);
			double width = mod.tracerWidth.get() * 0.00045;
			float ta = mod.tracerAlpha.get() / 100f;
			int n = 0, max = mod.maxTracers.get();
			for (Hit h : hits) {
				if (n++ >= max) break;
				BlockPos pos = h.pos();
				Vec3 end = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				line(b, m, cam, start, end, width, col(h.rgb(), ta), col(h.rgb(), ta * 0.28f));
			}
		}

		ms.popPose();
		if (tw) drawThroughWalls(mc, walls);
	}

	static int col(int rgb, float a) {
		return ARGB.colorFromFloat(Math.max(0f, Math.min(1f, a)), ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
	}

	/** Dünne Billboard-Linie (Ausrichtung vom Mittelpunkt zur Kamera = stabil) mit weichem Saum. */
	private static void line(VertexConsumer buf, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, double width, int core, int halo) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-6) return;
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 side = dir.cross(cam.subtract(mid));
		if (side.lengthSqr() < 1.0E-8) side = dir.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-8) return;
		side = side.normalize();
		double wa = width * Math.max(1.0, a.distanceTo(cam));
		double wb = width * Math.max(1.0, b.distanceTo(cam));
		band(buf, m, a, b, side, wa * 2.2, wb * 2.2, halo);
		band(buf, m, a, b, side, wa, wb, core);
	}

	private static void band(VertexConsumer buf, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, double wa, double wb, int c) {
		Vec3 a1 = a.add(side.scale(wa)), a2 = a.subtract(side.scale(wa));
		Vec3 b1 = b.add(side.scale(wb)), b2 = b.subtract(side.scale(wb));
		quad(buf, m, a1, a2, b2, b1, c);
		quad(buf, m, b1, b2, a2, a1, c);
	}

	private static void quad(VertexConsumer buf, Matrix4f m, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int c) {
		buf.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(c);
		buf.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(c);
		buf.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(c);
		buf.addVertex(m, (float) p4.x, (float) p4.y, (float) p4.z).setColor(c);
	}

	static void edges(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float e, int c) {
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

	static void box(VertexConsumer b, Matrix4f m, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int c) {
		b.addVertex(m, minX, minY, maxZ).setColor(c); b.addVertex(m, maxX, minY, maxZ).setColor(c); b.addVertex(m, maxX, maxY, maxZ).setColor(c); b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c); b.addVertex(m, minX, minY, minZ).setColor(c); b.addVertex(m, minX, maxY, minZ).setColor(c); b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c); b.addVertex(m, minX, minY, maxZ).setColor(c); b.addVertex(m, minX, maxY, maxZ).setColor(c); b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c); b.addVertex(m, maxX, minY, minZ).setColor(c); b.addVertex(m, maxX, maxY, minZ).setColor(c); b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c); b.addVertex(m, maxX, maxY, maxZ).setColor(c); b.addVertex(m, maxX, maxY, minZ).setColor(c); b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c); b.addVertex(m, maxX, minY, minZ).setColor(c); b.addVertex(m, maxX, minY, maxZ).setColor(c); b.addVertex(m, minX, minY, maxZ).setColor(c);
	}

	private static void drawThroughWalls(Minecraft mc, BufferBuilder buffer) {
		MeshData mesh = buffer.build();
		if (mesh == null) return;
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();
		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " storage esp", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
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
				() -> QuiqzyClient.MOD_ID + " storage esp pass",
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
