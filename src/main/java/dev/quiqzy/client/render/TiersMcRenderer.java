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
import dev.quiqzy.client.module.tiersmc.DLModule;

/**
 * Zeichnet die TiersMC/DonutLarp-Module wie im Meteor-Addon: flache Chunk-Platten auf Y-Level mit
 * einstellbarer Höhe (Shape Mode Both/Sides/Lines), durch Wände sichtbar.
 */
public final class TiersMcRenderer {
	private static final float EDGE = 0.04f;

	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/tiersmc_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	private TiersMcRenderer() {
	}

	public static void render(WorldRenderContext context) {
		boolean chunk = ModuleManager.DL_CHUNK_FINDER.isEnabled();
		boolean sus = ModuleManager.DL_SUS_FINDER.isEnabled();
		boolean act = ModuleManager.DL_ACTIVITY.isEnabled();
		if (!chunk && !sus && !act) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;
		int pcx = player.getBlockX() >> 4, pcz = player.getBlockZ() >> 4;

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.last().pose();

		BufferBuilder b = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());
		int faces = 0;
		int budget = 4000; // Obergrenze an Kästen pro Frame (Schutz gegen riesige Server-Listen)

		if (sus) {
			DLModule mod = ModuleManager.DL_SUS_FINDER;
			int r = ModuleManager.DL_SUS_FINDER.radius.get();
			for (ServerBaseNet.Base base : ServerBaseNet.bases()) {
				if (budget-- <= 0) break;
				int[] c = ModuleManager.DL_SUS_FINDER.center(base);
				if (!inRange(c[0], c[1], pcx, pcz, mod)) continue;
				faces += slab(b, m, mod, c[0] - r, c[1] - r, c[0] + r, c[1] + r);
			}
		}
		if (chunk) {
			DLModule mod = ModuleManager.DL_CHUNK_FINDER;
			for (ServerBaseNet.Base base : ServerBaseNet.bases()) {
				if (budget-- <= 0) break;
				if (!inRange(base.chunkX(), base.chunkZ(), pcx, pcz, mod)) continue;
				faces += slab(b, m, mod, base.chunkX(), base.chunkZ(), base.chunkX(), base.chunkZ());
			}
		}
		if (act) {
			DLModule mod = ModuleManager.DL_ACTIVITY;
			for (ServerBaseNet.Activity a : ServerBaseNet.activity()) {
				if (budget-- <= 0) break;
				if (!inRange(a.chunkX(), a.chunkZ(), pcx, pcz, mod)) continue;
				faces += slab(b, m, mod, a.chunkX(), a.chunkZ(), a.chunkX(), a.chunkZ());
			}
		}

		matrices.popPose();
		if (faces > 0) drawThroughWalls(mc, b);
		else b.build();
	}

	private static boolean inRange(int cx, int cz, int pcx, int pcz, DLModule mod) {
		int d = mod.maxDist.get();
		return Math.abs(cx - pcx) <= d && Math.abs(cz - pcz) <= d;
	}

	/** Platte über die Chunks cx0..cx1 / cz0..cz1 auf Y-Level mit Höhe. */
	private static int slab(VertexConsumer b, Matrix4f m, DLModule mod, int cx0, int cz0, int cx1, int cz1) {
		float x0 = cx0 * 16f, z0 = cz0 * 16f, x1 = cx1 * 16f + 16f, z1 = cz1 * 16f + 16f;
		float y0 = mod.yLevel.get(), y1 = y0 + mod.height.get();
		int faces = 0;
		if (mod.sides()) {
			box(b, m, x0, y0, z0, x1, y1, z1, (mod.sideAlpha.get() << 24) | (mod.sideColor.get() & 0xFFFFFF));
			faces += 6;
		}
		if (mod.lines()) faces += edgesOf(b, m, x0, y0, z0, x1, y1, z1, 0xFF000000 | (mod.lineColor.get() & 0xFFFFFF));
		return faces;
	}

	/** 12 Kanten eines Kastens als dünne Balken. */
	private static int edgesOf(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
		float e = EDGE;
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
		return 12 * 6;
	}

	/** Gefüllte Box (6 Flächen à 4 Ecken). */
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

	// ---------------- Zeichnen ohne Tiefentest (wie im Fabric-Docs-Beispiel 1.21.11)

	private static void drawThroughWalls(Minecraft mc, BufferBuilder buffer) {
		MeshData mesh = buffer.buildOrThrow();
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " tiersmc", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
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
				() -> QuiqzyClient.MOD_ID + " tiersmc pass",
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
