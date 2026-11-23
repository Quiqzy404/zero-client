package dev.quiqzy.client.module.visuals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.render.CardFxRenderer;
import dev.quiqzy.client.util.UiSounds;

/** Kill Effects: beim Kill fliegt ein Schwung Pokerkarten vom Ziel weg nach oben. */
public final class KillEffectsModule extends Module {
	public final Setting.Int count = add(new Setting.Int("count", "Cards", 14, 4, 40));
	public final Setting.Int power = add(new Setting.Int("power", "Burst", 100, 40, 200, " %"));
	public final Setting.Int size = add(new Setting.Int("size", "Size", 100, 50, 200, " %"));
	public final Setting.Bool sound = add(new Setting.Bool("sound", "Sound", true));

	private record Tracked(Entity entity, long until) {
	}

	private final List<Tracked> tracked = new ArrayList<>();

	public KillEffectsModule() {
		super("kill_effects", "Kill Effects", "Poker cards burst out when you get a kill", Category.VISUALS);
	}

	@Override
	protected void onDisable() {
		tracked.clear();
	}

	/** Vom MultiPlayerGameModeMixin: du hast gerade etwas angegriffen. */
	public void onAttack(LocalPlayer player, Entity target) {
		if (!isEnabled() || target == null) return;
		if (target instanceof LivingEntity) {
			tracked.removeIf(t -> t.entity() == target);
			tracked.add(new Tracked(target, System.currentTimeMillis() + 3000));
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		if (tracked.isEmpty()) return;
		long now = System.currentTimeMillis();
		Iterator<Tracked> it = tracked.iterator();
		while (it.hasNext()) {
			Tracked t = it.next();
			Entity e = t.entity();
			boolean dead = e instanceof LivingEntity le && (le.isDeadOrDying() || (e.isRemoved() && le.getHealth() <= 0f));
			if (dead) {
				CardFxRenderer.spawnBurst(e.getBoundingBox().getCenter(), count.get(), power.get() / 100f, size.get() / 100f);
				if (sound.on()) UiSounds.kill();
				it.remove();
			} else if (now > t.until()) {
				it.remove();
			}
		}
	}
}
