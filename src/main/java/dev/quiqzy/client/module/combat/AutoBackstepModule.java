package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/**
 * Auto Backstep (Mace): Nach einem Mace-Treffer läufst du automatisch ein paar Ticks rückwärts,
 * um Abstand zu gewinnen (kein Gegenschlag, Platz für den nächsten Smash). Danach übernimmt wieder deine Tastatur.
 */
public final class AutoBackstepModule extends Module {

	public final Setting.Bool maceOnly = add(new Setting.Bool("mace_only", "Mace Only", true));
	public final Setting.Bool smashOnly = add(new Setting.Bool("smash_only", "Only Smash Hits", false));
	public final Setting.Int delay = add(new Setting.Int("delay", "Delay", 0, 0, 10, " t"));
	public final Setting.Int duration = add(new Setting.Int("duration", "Duration", 5, 1, 20, " t"));
	public final Setting.Bool stopSprint = add(new Setting.Bool("stop_sprint", "Stop Sprint", true));

	private int delayLeft = -1;
	private int stepLeft;
	private boolean needRelease;

	public AutoBackstepModule() {
		super("auto_backstep", "Auto Backstep", "Walks back for a moment after a mace hit", Category.COMBAT);
	}

	/** Aus MultiPlayerGameModeMixin (attack HEAD). */
	public void onAttack(LocalPlayer p, Entity target) {
		if (!isEnabled() || !(target instanceof LivingEntity)) return;
		if (maceOnly.on() && !p.getMainHandItem().is(Items.MACE)) return;
		if (smashOnly.on() && p.fallDistance < 1.5) return;
		delayLeft = delay.get();
		stepLeft = 0;
	}

	@Override
	protected void onDisable() {
		if (stepLeft > 0 || needRelease) release(Minecraft.getInstance());
		needRelease = false;
		delayLeft = -1;
		stepLeft = 0;
	}

	@Override
	public void onStartTick(Minecraft mc) {
		if (mc.player == null) return;
		if (delayLeft >= 0 && delayLeft-- == 0) stepLeft = Math.max(1, duration.get());
		if (stepLeft > 0) {
			mc.options.keyDown.setDown(true);
			mc.options.keyUp.setDown(false);
			if (stopSprint.on()) {
				mc.options.keySprint.setDown(false);
				mc.player.setSprinting(false);
			}
			if (--stepLeft == 0) needRelease = true;
			return;
		}
		if (needRelease) {
			needRelease = false;
			release(mc);
		}
	}

	/** Tasten wieder auf den echten (physischen) Zustand setzen. */
	private static void release(Minecraft mc) {
		mc.options.keyDown.setDown(InputUtil.isPhysicallyDown(mc, mc.options.keyDown));
		mc.options.keyUp.setDown(InputUtil.isPhysicallyDown(mc, mc.options.keyUp));
		mc.options.keySprint.setDown(InputUtil.isPhysicallyDown(mc, mc.options.keySprint));
	}
}
