package dev.quiqzy.client.render;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;

import dev.quiqzy.client.QuiqzyClient;

/**
 * Echtes 3D-Mesh als Item ("minecraft:special" mit Typ "quiqzy:mesh"): liest assets/quiqzy/models/mesh/&lt;name&gt;.zmesh
 * (Dreiecke mit Position/UV/Normale, im 0..1-Item-Wuerfel) und zeichnet sie mit der echten Textur -
 * glatte Kurven statt Pixel-Sprite. Haltung kommt aus dem "base"-Modell (handheld).
 */
public final class MeshSpecialRenderer implements NoDataSpecialModelRenderer {
	/** Ein Teil: Textur + Vertexdaten (je 8 floats: x y z u v nx ny nz, 3 pro Dreieck). */
	private record Part(Identifier texture, float[] data) {
	}

	/** Thread-sicher: Minecraft backt Item-Modelle parallel auf mehreren Worker-Threads. */
	private static final Map<String, List<Part>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	private final List<Part> parts;

	private MeshSpecialRenderer(List<Part> parts) {
		this.parts = parts;
	}

	private static List<Part> load(String name) {
		List<Part> cached = CACHE.get(name);
		if (cached != null && !cached.isEmpty()) return cached;
		List<Part> fresh = read(name);
		if (!fresh.isEmpty()) CACHE.put(name, fresh);
		return fresh;
	}

	private static List<Part> read(String n) {
		{
			List<Part> out = new ArrayList<>();
			Identifier id = Identifier.fromNamespaceAndPath("quiqzy", "models/mesh/" + n + ".zmesh");
			try (InputStream raw = Minecraft.getInstance().getResourceManager().open(id);
					DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw, 1 << 16))) {
				byte[] magic = new byte[4];
				in.readFully(magic);
				int count = in.readInt();
				for (int i = 0; i < count; i++) {
					byte[] nb = new byte[in.readShort()];
					in.readFully(nb);
					int tris = in.readInt();
					float[] d = new float[tris * 3 * 8];
					for (int k = 0; k < d.length; k++) d[k] = in.readFloat();
					out.add(new Part(Identifier.fromNamespaceAndPath("quiqzy", "textures/mesh/" + new String(nb) + ".png"), d));
				}
			} catch (Exception e) {
				QuiqzyClient.LOGGER.error("ZERO mesh '{}' konnte nicht geladen werden", n, e);
			}
			return out;
		}
	}

	@Override
	public void submit(ItemDisplayContext ctx, PoseStack ms, SubmitNodeCollector nodes, int light, int overlay, boolean glint, int outline) {
		for (Part p : parts) {
			float[] d = p.data();
			boolean glow = p.texture().getPath().contains("_glow");
			nodes.submitCustomGeometry(ms, glow ? RenderTypes.entityTranslucentEmissive(p.texture()) : RenderTypes.entityCutoutNoCull(p.texture()),
					(pose, vc) -> emit(pose, vc, d, glow ? 15728880 : light, overlay));
		}
	}

	/** Dreiecke als (degenerierte) Quads ausgeben - die Entity-RenderTypes arbeiten mit QUADS. */
	private static void emit(PoseStack.Pose pose, VertexConsumer vc, float[] d, int light, int overlay) {
		for (int t = 0; t < d.length; t += 24) {
			vert(pose, vc, d, t, light, overlay);
			vert(pose, vc, d, t + 8, light, overlay);
			vert(pose, vc, d, t + 16, light, overlay);
			vert(pose, vc, d, t + 16, light, overlay);
		}
	}

	private static void vert(PoseStack.Pose pose, VertexConsumer vc, float[] d, int o, int light, int overlay) {
		vc.addVertex(pose, d[o], d[o + 1], d[o + 2])
				.setColor(255, 255, 255, 255)
				.setUv(d[o + 3], d[o + 4])
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, d[o + 5], d[o + 6], d[o + 7]);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> out) {
		for (int i = 0; i < 8; i++) out.accept(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
	}

	public record Unbaked(String mesh) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.fieldOf("mesh").forGetter(Unbaked::mesh)).apply(i, Unbaked::new));

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext ctx) {
			return new MeshSpecialRenderer(load(mesh));
		}
	}
}
