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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.module.visuals.EntityEspModule;
import dev.quiqzy.client.module.visuals.TracersModule;
import dev.quiqzy.client.module.world.BlockEspModule;
import dev.quiqzy.client.module.world.WaypointsModule;

/**
 * Zeichnet Entity ESP, Tracers und Waypoint-Säulen.
 * Gleiche Technik wie BlockEspRenderer: eigene Pipeline ohne Tiefentest für "durch Wände",
 * sonst der normale debugFilledBox-Puffer. Linien sind dünne Flächen (Quads), damit es mit derselben Pipeline geht.
 */
public final class EspRenderer {
	private static final int MAX_ENTITIES = 400;
	private static final int MAX_TRACERS = 400;

	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/esp_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	private EspRenderer() {
	}

	/** Lädt die Klasse früh, damit die Pipeline beim Start registriert wird. */
	public static void init() {
	}

	public static void render(WorldRenderContext context) {
		EntityEspModule esp = ModuleManager.ENTITY_ESP;
		TracersModule tracers = ModuleManager.TRACERS;
		WaypointsModule waypoints = ModuleManager.WAYPOINTS;
		if (!esp.isEnabled() && !tracers.isEnabled() && !waypoints.isEnabled() && !ModuleManager.ESP_2D.isEnabled() && !ModuleManager.BLOCK_ESP.isEnabled()) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;

		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.last().pose();

		BufferBuilder walls = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());
		int wallCount = 0;

		// ---------- Entity ESP
		if (esp.isEnabled()) {
			VertexConsumer target = esp.throughWalls.on() ? walls : context.consumers().getBuffer(RenderTypes.debugFilledBox());
			float fillA = esp.fillOpacity.get() / 100f;
			float lineA = esp.lineOpacity.get() / 100f;
			float edge = esp.lineWidth.get() * 0.01f;
			float pad = esp.boxSize.get() / 100f;
			int n = 0;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (n >= MAX_ENTITIES) break;
				int rgb = esp.colorFor(mc, e);
				if (rgb < 0) continue;
				if ((esp.chams() || esp.shader()) && e instanceof net.minecraft.world.entity.LivingEntity) continue; // Chams färben das Modell selbst
				AABB box = lerpedBox(e, pt);
				if (pad > 0) box = box.inflate(box.getXsize() * pad, box.getYsize() * pad * 0.5, box.getZsize() * pad);
				float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
				float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
				float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
				if (esp.drawFill() && fillA > 0) box(target, m, x0, y0, z0, x1, y1, z1, ARGB.colorFromFloat(fillA, r, g, b));
				if (lineA > 0) {
					int c = ARGB.colorFromFloat(lineA, r, g, b);
					if (esp.drawCorners()) corners(target, m, x0, y0, z0, x1, y1, z1, edge, c);
					else if (esp.drawOutline()) edges(target, m, x0, y0, z0, x1, y1, z1, edge, c);
				}
				if (target == walls) wallCount++;
				n++;
			}
		}

		// ---------- Tracers (immer durch Wände) – eigenes Modul + Schalter in Entity ESP / 2D ESP / Block ESP
		var esp2d = ModuleManager.ESP_2D;
		boolean espTr = esp.isEnabled() && esp.tracers.on();
		boolean e2dTr = esp2d.isEnabled() && esp2d.tracers.on();
		if (tracers.isEnabled() || espTr || e2dTr) {
			Vec3 start = tracerStart(mc, tracers, cam, pt);
			Vec3 fwd = camForward(mc);
			float alpha = tracers.opacity.get() / 100f;
			// Screen-Space-Breite: halbe Winkelbreite pro Einheit Distanz -> gleiche Pixelstaerke
			double width = tracers.width.get() * 0.0006;
			int n = 0;
			java.util.Set<Integer> done = new java.util.HashSet<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (n >= MAX_TRACERS) break;
				if (e == mc.player) continue;
				int rgb = tracers.isEnabled() ? tracers.colorFor(mc, e) : -1;
				if (rgb < 0 && espTr) rgb = esp.colorFor(mc, e);
				if (rgb < 0 && e2dTr) rgb = esp2dColor(mc, esp2d, e);
				if (rgb < 0 || !done.add(e.getId())) continue;
				AABB box = lerpedBox(e, pt);
				double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
				double y = switch (tracers.end.get()) {
					case "Head" -> box.maxY - 0.1;
					case "Feet" -> box.minY;
					default -> (box.minY + box.maxY) / 2;
				};
				line(walls, m, cam, fwd, start, new Vec3(cx, y, cz), width, color(alpha, rgb));
				wallCount++;
				n++;
			}
		}

		// ---------- Waypoints: leuchtende Säule (durch Wände)
		if (waypoints.isEnabled() && waypoints.beam.on()) {
			String dim = WaypointsModule.dimensionId(mc.level);
			for (Setting.Waypoints.Point p : waypoints.points.get()) {
				if (!p.dim().equals(dim)) continue;
				int rgb = WaypointsModule.colorOf(p.name());
				float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
				float cx = p.x() + 0.5f, cz = p.z() + 0.5f;
				float y0 = p.y(), y1 = p.y() + 48f;
				box(walls, m, cx - 0.2f, y0, cz - 0.2f, cx + 0.2f, y1, cz + 0.2f, ARGB.colorFromFloat(0.35f, r, g, b));
				box(walls, m, p.x() + 0.02f, p.y() + 0.02f, p.z() + 0.02f, p.x() + 0.98f, p.y() + 0.98f, p.z() + 0.98f, ARGB.colorFromFloat(0.3f, r, g, b));
				wallCount++;
			}
		}

		matrices.popPose();
		if (wallCount > 0) drawThroughWalls(mc, walls);
		else {
			MeshData unused = walls.build();
			if (unused != null) unused.close();
		}
	}

	private static int color(float alpha, int rgb) {
		return ARGB.colorFromFloat(alpha, ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
	}

	/** Farbe wie im 2D ESP (Spieler Akzent, feindlich rot, sonst grün), -1 = nicht anzeigen. */
	private static int esp2dColor(Minecraft mc, dev.quiqzy.client.module.visuals.Esp2DModule m, Entity e) {
		if (!(e instanceof net.minecraft.world.entity.LivingEntity le) || !le.isAlive()) return -1;
		if (e.distanceToSqr(mc.player) > (double) m.range.get() * m.range.get()) return -1;
		boolean isPlayer = e instanceof net.minecraft.world.entity.player.Player;
		boolean hostile = e instanceof net.minecraft.world.entity.monster.Enemy;
		if (isPlayer ? !m.players.on() : hostile ? !m.hostiles.on() : !m.animals.on()) return -1;
		return (isPlayer ? ModuleManager.HIT_EFFECTS.palette()[1] : hostile ? 0xFF5A5A : 0x7BE07B) & 0xFFFFFF;
	}

	/**
	 * Startpunkt der Tracer. Body/Eyes/Feet sind echte Punkte an deinem Spieler (3D – in F5 und Freecam sieht man die
	 * Linien aus dir herauskommen). Crosshair/Bottom liegen vor der echten Kamera (auch richtig in Freecam).
	 */
	public static Vec3 tracerStart(Minecraft mc, TracersModule t, Vec3 cam, float pt) {
		Vec3 look = camForward(mc);
		double px = mc.player.getX(pt);
		double py = mc.player.getY(pt);
		double pz = mc.player.getZ(pt);
		boolean freecam = ModuleManager.FREECAM.active();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson() && !freecam;
		switch (t.start.get()) {
			case "Body", "Eyes", "Feet" -> {
				double h = switch (t.start.get()) {
					case "Eyes" -> mc.player.getEyeHeight();
					case "Feet" -> 0.05;
					default -> mc.player.getBbHeight() * 0.55;
				};
				// In Ich-Perspektive aus dem Fadenkreuz (Körper unsichtbar)
				if (firstPerson) return cam.add(look.scale(0.5));
				// F5 / Freecam: echter Punkt am Spielerkörper (nicht an der fliegenden Kamera)
				return new Vec3(px, py + h, pz);
			}
			case "Bottom of Screen" -> {
				Vec3 right = look.cross(new Vec3(0, 1, 0));
				if (right.lengthSqr() < 1e-8) return cam.add(look.scale(0.5));
				Vec3 up = right.normalize().cross(look).normalize();
				return cam.add(look.scale(0.5)).subtract(up.scale(0.45));
			}
			default -> { // Crosshair
				return cam.add(look.scale(0.5));
			}
		}
	}

	/** Blickrichtung der echten Kamera (auch in F5/Freecam korrekt). */
	public static Vec3 camForward(Minecraft mc) {
		var camera = mc.gameRenderer.getMainCamera();
		double yaw = Math.toRadians(camera.yRot());
		double pitch = Math.toRadians(camera.xRot());
		return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
	}

private static AABB lerpedBox(Entity e, float pt) {
		Vec3 lerp = e.getPosition(pt);
		Vec3 now = e.position();
		return e.getBoundingBox().move(lerp.x - now.x, lerp.y - now.y, lerp.z - now.z);
	}

	/** Sehr duenne Linie mit konstanter Bildschirmbreite. Clippt an die Near-Plane (F5/Freecam: Start hinter der Kamera). */
	private static void line(VertexConsumer buf, Matrix4f m, Vec3 cam, Vec3 fwd, Vec3 a, Vec3 b, double halfAngle, int c) {
		// Near-Plane-Clip: Abschnitte hinter der Kamera erzeugen sonst Riesenkeile
		double near = 0.05;
		double d0 = a.subtract(cam).dot(fwd);
		double d1 = b.subtract(cam).dot(fwd);
		if (d0 < near && d1 < near) return;
		if (d0 < near) {
			double t = (near - d0) / (d1 - d0);
			a = a.lerp(b, t);
		} else if (d1 < near) {
			double t = (near - d1) / (d0 - d1);
			b = b.lerp(a, t);
		}

		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1e-8) return;

		// Seitenrichtung pro Ende zur Kamera ausrichten (vorher aus der Mitte -> in F5/Freecam
		// verdrehte, flackernde Bänder bei schräg weglaufenden Linien)
		Vec3 sideA = side(dir, a.subtract(cam)), sideB = side(dir, b.subtract(cam));
		if (sideA == null || sideB == null) return;
		if (sideA.dot(sideB) < 0) sideB = sideB.scale(-1);

		// Halbe Breite am Start-/Endpunkt aus dessen Kamerasdistanz ( Screen-Space )
		double halfA = Math.max(0.0005, a.distanceTo(cam) * halfAngle);
		double halfB = Math.max(0.0005, b.distanceTo(cam) * halfAngle);
		Vec3 a1 = a.add(sideA.scale(halfA)), a2 = a.subtract(sideA.scale(halfA));
		Vec3 b1 = b.add(sideB.scale(halfB)), b2 = b.subtract(sideB.scale(halfB));

		quad(buf, m, a1, a2, b2, b1, c);
		quad(buf, m, b1, b2, a2, a1, c);
	}

	static Vec3 side(Vec3 dir, Vec3 toPoint) {
		Vec3 sd = dir.cross(toPoint);
		if (sd.lengthSqr() < 1e-12) sd = dir.cross(new Vec3(0, 1, 0));
		if (sd.lengthSqr() < 1e-12) sd = dir.cross(new Vec3(1, 0, 0));
		if (sd.lengthSqr() < 1e-12) return null;
		return sd.normalize();
	}

private static void quad(VertexConsumer b, Matrix4f m, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int c) {
		b.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(c);
		b.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(c);
		b.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(c);
		b.addVertex(m, (float) p4.x, (float) p4.y, (float) p4.z).setColor(c);
	}

	/** Nur die Ecken einer Box (je ein Viertel jeder Kante). */
	private static void corners(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float e, int c) {
		float lx = (x1 - x0) * 0.25f, ly = (y1 - y0) * 0.25f, lz = (z1 - z0) * 0.25f;
		float[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				for (int k = 0; k < 2; k++) {
					float x = xs[i], y = ys[j], z = zs[k];
					float dx = i == 0 ? lx : -lx, dy = j == 0 ? ly : -ly, dz = k == 0 ? lz : -lz;
					float ex = i == 0 ? e : -e, ey = j == 0 ? e : -e, ez = k == 0 ? e : -e;
					box(b, m, Math.min(x, x + dx), Math.min(y, y + ey), Math.min(z, z + ez), Math.max(x, x + dx), Math.max(y, y + ey), Math.max(z, z + ez), c);
					box(b, m, Math.min(x, x + ex), Math.min(y, y + dy), Math.min(z, z + ez), Math.max(x, x + ex), Math.max(y, y + dy), Math.max(z, z + ez), c);
					box(b, m, Math.min(x, x + ex), Math.min(y, y + ey), Math.min(z, z + dz), Math.max(x, x + ex), Math.max(y, y + ey), Math.max(z, z + dz), c);
				}
			}
		}
	}

	private static void edges(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float e, int c) {
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
		MeshData mesh = buffer.build();
		if (mesh == null) return;
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " esp", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
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
				() -> QuiqzyClient.MOD_ID + " esp pass",
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
