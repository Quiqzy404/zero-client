package dev.quiqzy.client.module.visuals;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Clean F5: cineastische Third-Person-Kamera.
 * - fährt beim Umschalten weich heraus statt zu springen
 * - folgt Drehungen mit leichter, geschmeidiger Verzögerung
 * - wird nicht von Wänden ins Gesicht gedrückt (optional)
 * - eigener Abstand, Front-Ansicht wird übersprungen
 */
public final class CleanF5Module extends Module {
	public final Setting.Int distance = add(new Setting.Int("distance", "Distance", 5, 2, 14, " m"));
	public final Setting.Int lag = add(new Setting.Int("lag", "Camera Smoothness", 35, 0, 90, " %"));
	public final Setting.Bool zoomIn = add(new Setting.Bool("zoom_in", "Smooth Zoom", true));
	public final Setting.Bool noClip = add(new Setting.Bool("no_clip", "Ignore Walls", true));
	public final Setting.Bool skipFront = add(new Setting.Bool("skip_front", "Skip Front View", true));
	/** Kamera bleibt beim Springen auf Boden-Höhe (folgt Treppen weich, Fallen sofort). */
	public final Setting.Bool stableHeight = add(new Setting.Bool("stable_height", "Stable Height (Jumps)", true));

	private double groundY = Double.NaN, camY = Double.NaN;
	private long lastY;

	private float dist = 0f;
	private long last;
	private float yaw = Float.NaN, pitch;
	private long lastRot;

	public CleanF5Module() {
		super("clean_f5", "Clean F5", "Smooth cinematic third-person camera", Category.VISUALS);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (skipFront.on() && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(CameraType.FIRST_PERSON);
		if (mc.options.getCameraType().isFirstPerson()) {
			dist = 0.6f;
			yaw = Float.NaN;
			camY = Double.NaN;
		}
	}

	/** Kamera-Abstand (statt vanilla 4 m). vanilla = von Wänden gekürzter Wert. */
	public float zoom(float vanillaClamped) {
		long now = System.nanoTime();
		float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		float target = distance.get();
		if (zoomIn.on()) dist += (target - dist) * (1f - (float) Math.exp(-dt * 6.5f));
		else dist = target;
		float d = dist;
		if (!noClip.on()) d = Math.min(d, vanillaClamped);
		return d;
	}

	/**
	 * Kamera-Höhe ohne Sprünge: in der Luft bleibt sie auf der letzten Boden-Höhe; neue Höhe nach der
	 * Landung (Block hoch) wird weich angefahren, nach unten folgt sie sofort. Rückgabe = Versatz zur echten Höhe.
	 */
	public double heightOffset(net.minecraft.world.entity.Entity e, double y) {
		long now = System.nanoTime();
		float dt = lastY == 0 ? 0.016f : Math.min(0.1f, (now - lastY) / 1e9f);
		lastY = now;
		boolean free = !(e instanceof net.minecraft.world.entity.LivingEntity le) || le.isFallFlying() || le.isInWater() || le.onClimbable()
				|| (e instanceof net.minecraft.world.entity.player.Player pl && pl.getAbilities().flying) || e.isPassenger();
		if (Double.isNaN(groundY) || Double.isNaN(camY) || Math.abs(y - camY) > 8) {
			groundY = y;
			camY = y;
		}
		if (e.onGround() || free) groundY = y;
		double target = groundY;
		if (y < groundY) target = y;                      // fällt -> sofort mit
		if (y > groundY + 2.2) target = y - 1.2;          // deutlich höher (z. B. Knockback) -> mitgehen
		double speed = target < camY ? 30.0 : 9.0;        // runter schnell, hoch weich
		camY += (target - camY) * (1.0 - Math.exp(-dt * speed));
		return camY - y;
	}

	/** Weich nachgezogene Drehung der F5-Kamera. */
	public float[] smooth(float targetYaw, float targetPitch) {
		long now = System.nanoTime();
		float dt = lastRot == 0 ? 0.016f : Math.min(0.1f, (now - lastRot) / 1e9f);
		lastRot = now;
		if (Float.isNaN(yaw) || lag.get() <= 0) {
			yaw = targetYaw;
			pitch = targetPitch;
			return new float[]{yaw, pitch};
		}
		float speed = 4f + (100 - lag.get()) * 0.35f; // 35 % -> ~26/s
		float k = 1f - (float) Math.exp(-dt * speed);
		yaw += Mth.wrapDegrees(targetYaw - yaw) * k;
		pitch += (targetPitch - pitch) * k;
		return new float[]{yaw, pitch};
	}
}
