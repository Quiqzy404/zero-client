package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Hit Flick: Klickst du einen Gegner an, dreht sich die Kamera ZUERST in die eingestellte Richtung
 * (Standard: 180° = nach hinten), bleibt dort "Flick Time" Ticks, dreht dann zurück auf den Gegner und schlägt.
 * Die Richtung ist relativ zu deinem Blick (nur seitlich, kein Pitch): 180 = nach hinten, 90 = rechts, -90 = links.
 * Danach geht es EXAKT auf deinen Blick vom Klick zurück und schlägt.
 */
public final class HitFlickModule extends Module {

	public final Setting.Int flickYaw = add(new Setting.Int("flick_yaw", "Flick Direction", 180, -180, 180, "°"));
	public final Setting.Int flickTicks = add(new Setting.Int("flick_ticks", "Flick Time", 2, 1, 10, " t"));
	public final Setting.Choice returnMode = add(new Setting.Choice("return_mode", "Return", "Snap", "Snap", "Smooth"));
	public final Setting.Bool weaponsOnly = add(new Setting.Bool("weapons_only", "Weapons Only", true));

	private Entity target;
	/** Dein Blick beim Klick – danach geht es exakt hierhin zurück. */
	private float origYaw, origPitch;
	private int flickLeft;
	private int returnLeft;

	public HitFlickModule() {
		super("hit_flick", "Hit Flick", "Flicks to a set direction first, then turns back and hits the target", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		target = null;
		flickLeft = returnLeft = 0;
	}

	@Override
	public void onStartTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) return;

		// läuft gerade ein Flick -> weitere Klicks schlucken, Ablauf weiterführen
		if (target != null) {
			while (mc.options.keyAttack.consumeClick()) {
			}
			if (!target.isAlive() || target.isRemoved()) {
				target = null;
				return;
			}
			if (flickLeft > 0) {
				flickLeft--;
				applyFlick(p);
				if (flickLeft == 0) returnLeft = returnMode.get().equals("Smooth") ? 2 : 0;
				if (flickLeft > 0 || returnLeft > 0) return;
			}
			if (returnLeft > 0) {
				returnLeft--;
				back(p, 0.6f);
				if (returnLeft > 0) return;
			}
			back(p, 1f);
			if (inReach(p, target)) {
				mc.gameMode.attack(p, target);
			}
			p.swing(InteractionHand.MAIN_HAND);
			target = null;
			return;
		}

		// neuer Klick auf einen Gegner? (nur dann den Klick abfangen – Blöcke/Luft bleiben normal)
		if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity le)) return;
		if (weaponsOnly.on() && !isWeapon(p.getMainHandItem())) return;
		if (!mc.options.keyAttack.consumeClick()) return;
		while (mc.options.keyAttack.consumeClick()) {
		}
		target = le;
		origYaw = p.getYRot();
		origPitch = p.getXRot();
		flickLeft = Math.max(1, flickTicks.get());
		applyFlick(p);
	}

	/** Nur seitlich drehen (Yaw relativ zu deinem Blick), Pitch bleibt wie er war. */
	private void applyFlick(LocalPlayer p) {
		p.setYRot(origYaw + flickYaw.get());
		p.setXRot(origPitch);
		p.setYHeadRot(p.getYRot());
	}

	/** Zurück auf den ursprünglichen Blick (k=1 -> exakt). */
	private void back(LocalPlayer p, float k) {
		float dy = Mth.wrapDegrees(origYaw - p.getYRot());
		p.setYRot(k >= 1f ? origYaw : p.getYRot() + dy * k);
		p.setXRot(origPitch);
		p.setYHeadRot(p.getYRot());
	}

	private static boolean inReach(LocalPlayer p, Entity t) {
		Vec3 e = p.getEyePosition();
		AABB b = t.getBoundingBox();
		double x = Mth.clamp(e.x, b.minX, b.maxX), y = Mth.clamp(e.y, b.minY, b.maxY), z = Mth.clamp(e.z, b.minZ, b.maxZ);
		return e.distanceTo(new Vec3(x, y, z)) <= p.entityInteractionRange();
	}

	private static boolean isWeapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT) || s.is(ItemTags.SPEARS);
	}
}
