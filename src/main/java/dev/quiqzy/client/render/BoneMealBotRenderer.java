package dev.quiqzy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.module.world.BoneMealBotModule;
import dev.quiqzy.client.module.world.WaypointsModule;

/** Zeigt die markierten Setzling-Blöcke (grün), die Knochenmehl-Kiste (orange) und das aktuelle Ziel (weiß, pulsierend). */
public final class BoneMealBotRenderer {
	private BoneMealBotRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		BoneMealBotModule mod = ModuleManager.BONE_MEAL_BOT;
		Minecraft mc = Minecraft.getInstance();
		if (!mod.isEnabled() || mc.player == null || mc.level == null) return;
		String dim = WaypointsModule.dimensionId(mc.level);
		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		PoseStack ms = ctx.matrices();
		ms.pushPose();
		ms.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = ms.last().pose();
		VertexConsumer b = ctx.consumers().getBuffer(RenderTypes.debugFilledBox());
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);

		for (Setting.Waypoints.Point p : mod.spots.get()) {
			if (!p.dim().equals(dim)) continue;
			// nur die Oberseite + Kanten, damit man den Block noch sieht
			float x = p.x(), y = p.y(), z = p.z();
			StorageEspRenderer.box(b, m, x + 0.02f, y + 0.98f, z + 0.02f, x + 0.98f, y + 1.005f, z + 0.98f, StorageEspRenderer.col(0x5EE38A, 0.30f));
			StorageEspRenderer.edges(b, m, x, y, z, x + 1, y + 1, z + 1, 0.02f, StorageEspRenderer.col(0x5EE38A, 0.9f));
		}
		for (Setting.Waypoints.Point p : mod.chest.get()) {
			if (!p.dim().equals(dim)) continue;
			float x = p.x(), y = p.y(), z = p.z();
			StorageEspRenderer.box(b, m, x + 0.03f, y + 0.03f, z + 0.03f, x + 0.97f, y + 0.97f, z + 0.97f, StorageEspRenderer.col(0xFFB347, 0.18f));
			StorageEspRenderer.edges(b, m, x, y, z, x + 1, y + 1, z + 1, 0.025f, StorageEspRenderer.col(0xFFB347, 0.95f));
		}
		BlockPos t = mod.target;
		if (t != null) {
			StorageEspRenderer.edges(b, m, t.getX() - 0.01f, t.getY() - 0.01f, t.getZ() - 0.01f, t.getX() + 1.01f, t.getY() + 1.01f, t.getZ() + 1.01f, 0.03f,
					StorageEspRenderer.col(0xFFFFFF, 0.5f + 0.45f * pulse));
		}
		ms.popPose();
	}
}
