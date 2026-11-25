package dev.quiqzy.client.module.combat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/**
 * Auto Backstep: Hält der Gegner sein Schild hoch und du stehst VOR ihm, läufst du automatisch seitlich um ihn herum
 * hinter seinen Rücken (ein Schild blockt nur von vorne) und schlägst ihn dort – der Treffer geht durchs Schild.
 * Während des Backsteps steuert das Modul W/A/S/D, danach gelten wieder deine echten Tasten.
 */
public final class AutoBackstepModule extends Module {

	public final Setting.Choice trigger = add(new Setting.Choice("trigger", "Trigger", "On Click", "On Click", "Automatic"));
	public final Setting.Bool maceOnly = add(new Setting.Bool("mace_only", "Mace Only", false));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 4, 2, 6, " b"));
	public final Setting.Int maxTime = add(new Setting.Int("max_time", "Max Time", 20, 5, 60, " t"));
	public final Setting.Bool autoHit = add(new Setting.Bool("auto_hit", "Hit When Behind", true));
	public final Setting.Choice aimMode = add(new Setting.Choice("aim_mode", "Aim Mode", "Silent", "Silent", "Visible"));

	private LivingEntity target;
	private int ticks;
	private int side; // +1 = rechts herum, -1 = links herum

	public AutoBackstepModule() {
		super("auto_backstep", "Auto Backstep", "Steps behind a shielding enemy and hits him in the back", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		stop(Minecraft.getInstance());
	}

	@Override
	public void onStartTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) {
			if (target != null) stop(mc);
			return;
		}

		if (target == null) {
			if (maceOnly.on() && !p.getMainHandItem().is(Items.MACE)) return;
			LivingEntity t = mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le ? le : null;
			if (t == null || !t.isBlocking() || !inFront(p, t) || dist(p, t) > range.get()) return;
			if (trigger.get().equals("On Click")) {
				// nur wenn du zuschlagen willst: Klick abfangen (sonst prallt er am Schild ab)
				if (!mc.options.keyAttack.consumeClick()) return;
				while (mc.options.keyAttack.consumeClick()) {
				}
			}
			start(p, t);
		}

		// läuft
		while (trigger.get().equals("On Click") && mc.options.keyAttack.consumeClick()) {
		}
		LivingEntity t = target;
		if (!t.isAlive() || t.isRemoved() || ++ticks > maxTime.get() || dist(p, t) > range.get() + 3) {
			stop(mc);
			return;
		}
		if (!t.isBlocking() || !inFront(p, t)) {
			// hinter ihm (oder Schild unten) -> zuschlagen
			if (autoHit.on() && reach(p, t) && p.getAttackStrengthScale(0.5f) >= 0.9f) {
				hit(mc, p, t);
				stop(mc);
				return;
			}
			if (!t.isBlocking()) {
				stop(mc);
				return;
			}
		}
		walkTo(mc, p, waypoint(p, t));
	}

	private void start(LocalPlayer p, LivingEntity t) {
		target = t;
		ticks = 0;
		// kürzere Seite wählen: auf welcher Seite seines Blicks stehst du schon?
		Vec3 look = flat(t.getViewVector(1f));
		Vec3 right = new Vec3(-look.z, 0, look.x);
		Vec3 toMe = flat(p.position().subtract(t.position()));
		side = toMe.dot(right) >= 0 ? 1 : -1;
	}

	private void stop(Minecraft mc) {
		if (target != null && mc.options != null) {
			release(mc, mc.options.keyUp);
			release(mc, mc.options.keyDown);
			release(mc, mc.options.keyLeft);
			release(mc, mc.options.keyRight);
		}
		target = null;
		ticks = 0;
	}

	/** Zwischenziel: erst seitlich neben ihn, dann hinter seinen Rücken. */
	private Vec3 waypoint(LocalPlayer p, LivingEntity t) {
		Vec3 look = flat(t.getViewVector(1f));
		Vec3 right = new Vec3(-look.z, 0, look.x).scale(side);
		Vec3 toMe = flat(p.position().subtract(t.position()));
		double front = toMe.dot(look);
		if (front > 0.25) return t.position().add(right.scale(1.6)).add(look.scale(-0.2));
		return t.position().add(look.scale(-1.3)).add(right.scale(0.3));
	}

	/** W/A/S/D so drücken, dass du (relativ zu deiner Blickrichtung) zum Punkt läufst. Kamera bleibt. */
	private static void walkTo(Minecraft mc, LocalPlayer p, Vec3 goal) {
		Vec3 d = flat(goal.subtract(p.position()));
		double yaw = Math.toRadians(p.getYRot());
		double fw = d.x * -Math.sin(yaw) + d.z * Math.cos(yaw);
		double lf = d.x * Math.cos(yaw) + d.z * Math.sin(yaw);
		double th = 0.35;
		mc.options.keyUp.setDown(fw > th);
		mc.options.keyDown.setDown(fw < -th);
		mc.options.keyLeft.setDown(lf > th);
		mc.options.keyRight.setDown(lf < -th);
	}

	private void hit(Minecraft mc, LocalPlayer p, LivingEntity t) {
		float[] r = rotTo(p, t);
		if (aimMode.get().equals("Visible")) {
			p.setYRot(r[0]);
			p.setXRot(r[1]);
			p.setYHeadRot(r[0]);
		} else {
			p.connection.send(new ServerboundMovePlayerPacket.Rot(r[0], r[1], p.onGround(), p.horizontalCollision));
		}
		mc.gameMode.attack(p, t);
		p.swing(InteractionHand.MAIN_HAND);
	}

	/** Stehst du in der vorderen Hälfte seines Blicks (da blockt das Schild)? */
	private static boolean inFront(LocalPlayer p, LivingEntity t) {
		Vec3 toMe = flat(p.position().subtract(t.position()));
		return toMe.dot(flat(t.getViewVector(1f))) > -0.05;
	}

	private static float[] rotTo(LocalPlayer p, Entity t) {
		Vec3 eye = p.getEyePosition();
		Vec3 c = t.getBoundingBox().getCenter();
		double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		return new float[] { (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0), (float) Mth.clamp(-Math.toDegrees(Math.atan2(dy, h)), -90.0, 90.0) };
	}

	private static boolean reach(LocalPlayer p, Entity t) {
		Vec3 e = p.getEyePosition();
		AABB b = t.getBoundingBox();
		return e.distanceTo(new Vec3(Mth.clamp(e.x, b.minX, b.maxX), Mth.clamp(e.y, b.minY, b.maxY), Mth.clamp(e.z, b.minZ, b.maxZ)))
				<= p.entityInteractionRange();
	}

	private static double dist(LocalPlayer p, Entity t) {
		return p.position().distanceTo(t.position());
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0, v.z);
		return f.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static void release(Minecraft mc, KeyMapping k) {
		k.setDown(InputUtil.isPhysicallyDown(mc, k));
	}
}
