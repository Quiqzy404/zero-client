package dev.quiqzy.client.module.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Stun Slam – zwei Modi (umschaltbar):
 *  - "Mace Combo": Schild brechen, nach der Verzögerung Mace.
 *  - "Shield Drain": solange du eine schildende Person anschlägst, viele schnelle Schläge pro Tick aufs Schild.
 *
 * Trigger (was du in der Hand halten musst, damit es auslöst) und das Schild-Brech-Item sind getrennt:
 *  - "Any Item" an: jeder Schlag mit jedem Item löst aus.
 *  - sonst: nur mit einem der "Shield Break Items" (leere Liste = jedes Item).
 * Zum Brechen des Schilds wechselt das Modul automatisch auf ein Shield-Break-Item aus der Hotbar
 * (eins der gewählten, sonst irgendeine Axt – nur eine Axt bricht in Minecraft den Schild), dann Mace.
 *
 * Läuft am ANFANG des Ticks, damit der Klick abgefangen wird, bevor Minecraft ihn selbst verarbeitet.
 */
public final class StunSlamModule extends Module {
	public final Setting.Choice mode = add(new Setting.Choice("mode", "Mode", "Mace Combo", "Mace Combo", "Shield Drain"));
	/** Keybind: Toggle = Taste schaltet an/aus, Hold = Modul nur an, solange die Taste gehalten wird. */
	public final Setting.Choice bindMode = add(new Setting.Choice("bind_mode", "Bind Mode", "Toggle", "Toggle", "Hold"));
	/** Nur auslösen, wenn das Ziel horizontal so nah ist (Blöcke). */
	public final Setting.Int targetDist = add(new Setting.Int("target_dist", "Horizontal Target Dist", 6, 1, 6, " b"));
	/** Nur auf den Mace wechseln, wenn du mindestens so tief gefallen bist (Blöcke). 0 = auch am Boden. */
	public final Setting.Int minFall = add(new Setting.Int("min_fall", "Min Fall Distance", 0, 0, 50, " b"));
	/** Aus = nur Mace-Swap, kein Schildbrechen mit der Axt. */
	public final Setting.Bool stunSlam = add(new Setting.Bool("stun_slam", "Stun Slam", true));
	/** Chance = zufälliger Wurf pro Stun Slam, Hold Key = nur solange der Keybind gehalten wird. */
	public final Setting.Choice stunMode = add(new Setting.Choice("stun_mode", "Stun Slam Mode", "Chance", "Chance", "Hold Key"));
	public final Setting.Int chance = add(new Setting.Int("chance", "Chance", 100, 0, 100, "%"));
	/** Chance mit der Fallhöhe skalieren: große Drops lösen öfter aus. */
	public final Setting.Bool scaleWithFall = add(new Setting.Bool("scale_with_fall", "Scale With Fall", false));
	/** Stun Slam erst ab dieser Fallhöhe (Blöcke). 0 = Modul-Minimum (Min Fall Distance). */
	public final Setting.Int stunMinFall = add(new Setting.Int("stun_min_fall", "Stun Min Fall", 0, 0, 50, " b"));
	/** Bevorzugt einen Mace mit Breach-Verzauberung. */
	public final Setting.Bool breachCheck = add(new Setting.Bool("breach_check", "Breach Check", false));
	/** An = egal welches Item in der Hand löst aus (ignoriert die Item-Liste). */
	public final Setting.Bool anyItem = add(new Setting.Bool("any_item", "Any Item", false));
	/** Items, die zum Auslösen in der Hand liegen müssen UND zum Schildbrechen genommen werden. */
	public final Setting.Items breakers = add(new Setting.Items("breakers", "Shield Break Items",
			"minecraft:netherite_axe", "minecraft:diamond_axe", "minecraft:iron_axe",
			"minecraft:golden_axe", "minecraft:stone_axe", "minecraft:wooden_axe"));
	/** Shield Drain: Schläge pro Tick (20 Ticks/s -> 10 = ~200 CPS). */
	public final Setting.Int hitsPerTick = add(new Setting.Int("hits_per_tick", "Drain Hits / Tick", 10, 1, 20));
	/** Shield Drain: an = Angriff gedrückt halten, aus = draint automatisch beim Anvisieren. */
	public final Setting.Bool holdToDrain = add(new Setting.Bool("hold_to_drain", "Hold to Drain", true));
	public final Setting.Int minDelay = add(new Setting.Int("min_delay", "Min. Delay", 1, 0, 40, " t"));
	public final Setting.Int maxDelay = add(new Setting.Int("max_delay", "Max. Delay", 1, 0, 40, " t"));
	/** Merkt sich das zuletzt angegriffene Ziel; schlägst du ins Leere, wird der Schlag auf dieses Ziel umgelenkt. */
	public final Setting.Bool aimTarget = add(new Setting.Bool("aim_target", "Hit Last Target", false));
	/** Max. Entfernung (Blöcke), bis zu der ein Luft-Schlag aufs gemerkte Ziel umgelenkt wird. */
	public final Setting.Int aimReach = add(new Setting.Int("aim_reach", "Last Target Reach", 3, 3, 6, " b"));
	/** Wie lange (ms) das Ziel nach dem letzten Anvisieren gemerkt bleibt. */
	public final Setting.Int memoryMs = add(new Setting.Int("memory_ms", "Target Memory", 2000, 200, 8000, " ms"));
	/** Auto Hit: löst den Mace-Combo automatisch aus (ohne Klick), sobald ein Ziel in Reichweite ist (z.B. beim Ely-Mace). */
	public final Setting.Bool autoHit = add(new Setting.Bool("auto_hit", "Auto Hit", false));
	/** Abstand zwischen zwei Auto-Hit-Combos (Ticks). */
	public final Setting.Int autoHitDelay = add(new Setting.Int("auto_hit_delay", "Auto Hit Delay", 10, 0, 40, " t"));
	/**
	 * Aim Mode für den umgelenkten Schlag (Hit Last Target):
	 *  - "Visible": schaut den Gegner sichtbar an (nur in First Person; in F5/3rd-Person wird NICHT gedreht),
	 *    dazu immer ein Server-Paket, damit der Treffer sofort landet. Nur im Schlag-Moment -> du kannst dich
	 *    in First Person ganz normal weiterbewegen.
	 *  - "Silent": dreht NUR server-seitig (Kamera bleibt komplett, in jeder Ansicht).
	 */
	public final Setting.Choice aimMode = add(new Setting.Choice("aim_mode", "Aim Mode", "Visible", "Visible", "Silent"));
	/** Wie lange (Ticks) der Kopf im Visible-Modus nach dem Treffer sichtbar zum Gegner schaut (0 = nur kurz antippen). */
	public final Setting.Int lookHold = add(new Setting.Int("look_hold", "Look Hold", 6, 0, 20, " t"));
	/** Nur auslösen, wenn KEINE Elytra angezogen ist (z.B. erst nach dem Elytra-Swap beim Ely-Mace). */
	public final Setting.Bool noElytra = add(new Setting.Bool("no_elytra", "Only When Elytra Off", true));

	/** Ticks bis zum Mace-Schlag, -1 = nichts geplant. */
	private int ticksUntilMaceHit = -1;
	/** Zuletzt angegriffenes/anvisiertes Ziel (für "Hit Last Target"). */
	private Entity lastTarget;
	private long lastTargetTime;
	/** Explizites Ziel des aktuellen Combos (überschreibt das Fadenkreuz, z.B. beim Umlenken). */
	private Entity attackTarget;
	/** Cooldown für Auto Hit. */
	private int autoCooldown;
	/** Visible-Look: sichtbar zum Gegner schauen (nach Treffer, First Person) für ein paar Ticks. */
	private Entity visTarget;
	private int visTicks;

	private static final Predicate<ItemStack> MACE = stack -> stack.is(Items.MACE);
	private static final Predicate<ItemStack> AXE = stack -> stack.is(ItemTags.AXES);

	/** Die gewählten Shield-Break-Items, neu gebaut wenn sich die Auswahl ändert. */
	private Set<Item> breakerItems = new HashSet<>();
	private int breakersVersion = -1;

	public StunSlamModule() {
		super("stun_slam", "Stun Slam", "Shield break + mace, or shield drain", Category.COMBAT);
	}

	/**
	 * Keybind-Druck kommt als toggle() vom ModuleManager. Hier abfangen:
	 *  - Stun Slam Mode "Hold Key": Taste löst nur den Stun aus, Modul bleibt an.
	 *  - Bind Mode "Hold": Taste gedrückt = an, losgelassen = aus (siehe onStartTick).
	 * Klicks im GUI (Taste nicht gedrückt) schalten ganz normal um.
	 */
	@Override
	public void toggle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.getWindow() != null && mc.screen == null && isBindHeld(mc)) {
			if (bindUsedForStun()) return;
			if (bindMode.get().equals("Hold")) {
				if (!isEnabled()) super.toggle();
				return;
			}
		}
		super.toggle();
	}

	@Override
	protected void onEnable() {
		ticksUntilMaceHit = -1;
		lastTarget = null;
		attackTarget = null;
		autoCooldown = 0;
		visTarget = null;
		visTicks = 0;
	}

	@Override
	protected void onDisable() {
		ticksUntilMaceHit = -1;
		lastTarget = null;
		attackTarget = null;
		autoCooldown = 0;
		visTarget = null;
		visTicks = 0;
	}

	@Override
	public void onStartTick(Minecraft mc) {
		// Bind Mode Hold: Taste losgelassen -> Modul aus
		if (bindMode.get().equals("Hold") && !bindUsedForStun() && keybind() >= 0 && !isBindHeld(mc)) {
			setEnabled(false);
			return;
		}
		if (mc.player == null || mc.gameMode == null) {
			ticksUntilMaceHit = -1;
			return;
		}
		if (mc.screen != null) return;

		// Ziel merken: wen du gerade anvisierst (für "Hit Last Target")
		updateTargetMemory(mc);
		if (autoCooldown > 0) autoCooldown--;

		// Visible-Look: Kopf nach dem Treffer sichtbar + smooth zum Gegner drehen (nur First Person).
		if (visTicks > 0) {
			if (aimMode.get().equals("Visible") && firstPerson(mc) && visTarget != null && visTarget.isAlive()
					&& mc.player instanceof LocalPlayer lp) {
				visibleLook(lp, visTarget);
			} else {
				visTicks = 0;
			}
			visTicks--;
		}

		// Shield-Drain-Modus
		if (mode.get().equals("Shield Drain")) {
			shieldDrain(mc);
			return;
		}

		// Mace-Schlag ist schon geplant -> runterzählen, Klicks solange verschlucken
		if (ticksUntilMaceHit >= 0) {
			// nicht dauer-drehen (sonst sperrt es die Bewegung) – gezielt wird im Schlag-Moment (leftClick)
			if (ticksUntilMaceHit-- == 0) maceHit(mc);
			if (mc.hitResult instanceof EntityHitResult) {
				while (mc.options.keyAttack.consumeClick()) {
				}
			}
			return;
		}

		// Trigger: Any Item an -> alles; sonst nur mit einem gewählten Item (leer = jedes).
		if (!canTrigger(mc.player.getMainHandItem())) return;
		// Nur wenn keine Elytra an ist (mit Elytra fliegst du -> kein Shield-Break/Combo)
		if (noElytra.on() && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;

		// Ziel bestimmen: unter dem Fadenkreuz, sonst (Hit Last Target) das gemerkte Ziel in Reichweite.
		Entity target;
		boolean redirect = false;
		if (mc.hitResult instanceof EntityHitResult hit) {
			target = hit.getEntity();
			if (!inHorizontalRange(mc, target)) return; // zu weit weg -> normaler Schlag, nichts abfangen
		} else {
			target = redirectTarget(mc); // Luft/Block -> evtl. aufs gemerkte Ziel umlenken
			if (target == null) return;
			redirect = true;
		}

		double fall = mc.player.fallDistance;
		if (fall < minFall.get()) return;

		// Auslöser: ein Klick ODER Auto Hit (ohne Klick, nach Cooldown). Klick erst jetzt
		// konsumieren – wenn kein gültiges Ziel da war, bleibt der Klick für Minecraft erhalten.
		boolean clicked = mc.options.keyAttack.consumeClick();
		while (mc.options.keyAttack.consumeClick()) {
		}
		boolean auto = autoHit.on() && autoCooldown <= 0;
		if (!clicked && !auto) return;

		attackTarget = redirect ? target : null;
		// Gezielt wird erst im Schlag-Moment in leftClick (Server-Paket immer; sichtbar nur First Person).
		if (autoHit.on()) autoCooldown = Math.max(1, autoHitDelay.get());

		if (!(target instanceof LivingEntity living) || !living.isBlocking() || !shouldStun(mc, fall)) {
			// Kein Schild oben (oder Stun Slam diesmal nicht) -> direkt Mace
			maceHit(mc);
			return;
		}

		beginShieldBreak(mc);
	}

	/** Schild oben -> auf ein Schild-Brech-Item wechseln (bricht den Schild), dann nach Delay Mace. */
	private void beginShieldBreak(Minecraft mc) {
		Inventory inv = mc.player.getInventory();
		int slot = findSlot(inv, this::isBreakWeapon); // eins der gewählten Items
		if (slot == -1) slot = findSlot(inv, AXE);     // sonst irgendeine Axt
		if (slot != -1) inv.setSelectedSlot(slot);
		leftClick(mc);

		int min = Math.min(minDelay.get(), maxDelay.get());
		int max = Math.max(minDelay.get(), maxDelay.get());
		int delay = min + mc.player.getRandom().nextInt(max - min + 1);
		if (delay == 0) maceHit(mc);
		else ticksUntilMaceHit = delay - 1;
	}

	/** Merkt sich die gerade anvisierte lebende Entität. */
	private void updateTargetMemory(Minecraft mc) {
		if (!aimTarget.on()) return;
		if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le != mc.player) {
			lastTarget = le;
			lastTargetTime = System.currentTimeMillis();
		}
	}

	/** Das gemerkte Ziel, falls aktiv, noch am Leben, frisch genug und in Reichweite – sonst null. */
	private Entity redirectTarget(Minecraft mc) {
		if (!aimTarget.on()) return null;
		Entity t = lastTarget;
		if (t == null || !t.isAlive() || t.isRemoved() || t == mc.player) return null;
		if (System.currentTimeMillis() - lastTargetTime > memoryMs.get()) return null;
		double d = distanceToBox(mc.player.getEyePosition(), t.getBoundingBox());
		if (d > aimReach.get()) return null;
		return t;
	}

	/** Blick smooth Richtung Ziel drehen (sichtbar) – pro Tick ein Stück, damit der Kopf erkennbar hindreht. */
	private void visibleLook(LocalPlayer viewer, Entity tgt) {
		Vec3 eye = viewer.getEyePosition();
		Vec3 c = tgt.getBoundingBox().getCenter();
		double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) Math.max(-90.0, Math.min(90.0, -Math.toDegrees(Math.atan2(dy, h))));
		float dyaw = Mth.wrapDegrees(yaw - viewer.getYRot());
		float dpitch = pitch - viewer.getXRot();
		float k = 0.55f; // Glättung: pro Tick 55% der Restdrehung -> schnell aber sichtbar, nicht hart
		viewer.setYRot(viewer.getYRot() + dyaw * k);
		viewer.setXRot(Mth.clamp(viewer.getXRot() + dpitch * k, -90f, 90f));
		// Kopf-Rotation mitführen, damit es auch im Modell sauber zum Gegner zeigt
		viewer.setYHeadRot(viewer.getYRot());
	}

	private static double distanceToBox(Vec3 p, AABB box) {
		double x = Math.max(box.minX, Math.min(p.x, box.maxX));
		double y = Math.max(box.minY, Math.min(p.y, box.maxY));
		double z = Math.max(box.minZ, Math.min(p.z, box.maxZ));
		return p.distanceTo(new Vec3(x, y, z));
	}

	/** Shield Drain: schnelle Schläge aufs Schild. */
	private void shieldDrain(Minecraft mc) {
		if (!(mc.hitResult instanceof EntityHitResult hit)) return;
		if (!(hit.getEntity() instanceof LivingEntity living) || !living.isBlocking()) return;
		if (!inHorizontalRange(mc, living)) return;
		if (!canTrigger(mc.player.getMainHandItem())) return;
		if (holdToDrain.on() && !InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) return;

		// Zum Draining ein Schild-Brech-Item/Axt nehmen, falls du gerade was anderes hältst
		Inventory inv = mc.player.getInventory();
		if (!AXE.test(mc.player.getMainHandItem()) && !isBreakWeapon(mc.player.getMainHandItem())) {
			int slot = findSlot(inv, this::isBreakWeapon);
			if (slot == -1) slot = findSlot(inv, AXE);
			if (slot != -1) inv.setSelectedSlot(slot);
		}

		int n = hitsPerTick.get();
		for (int i = 0; i < n; i++) {
			mc.gameMode.attack(mc.player, living);
		}
		mc.player.swing(InteractionHand.MAIN_HAND);
		while (mc.options.keyAttack.consumeClick()) {
		}
	}

	/** Ziel horizontal (ohne Höhe) innerhalb von "Horizontal Target Dist"? */
	private boolean inHorizontalRange(Minecraft mc, Entity target) {
		double dx = target.getX() - mc.player.getX();
		double dz = target.getZ() - mc.player.getZ();
		return Math.sqrt(dx * dx + dz * dz) <= targetDist.get();
	}

	/** Soll dieser Schlag ein Stun Slam (Axt -> Mace) werden? */
	private boolean shouldStun(Minecraft mc, double fall) {
		if (!stunSlam.on()) return false;
		if (stunMinFall.get() > 0 && fall < stunMinFall.get()) return false;
		if (stunMode.get().equals("Hold Key")) return isBindHeld(mc);
		double c = chance.get();
		// Scale With Fall: volle Chance ab 10 Blöcken über dem Minimum, darunter anteilig
		if (scaleWithFall.on()) {
			double base = Math.max(minFall.get(), stunMinFall.get());
			c *= Math.min(1.0, Math.max(0.1, (fall - base) / 10.0));
		}
		return mc.player.getRandom().nextDouble() * 100.0 < c;
	}

	/** Ist der Keybind dieses Moduls gerade physisch gedrückt? */
	public boolean isBindHeld(Minecraft mc) {
		int key = keybind();
		if (key < 0) return false;
		return GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
	}

	/** Keybind schaltet das Modul nicht um, wenn er für "Hold Key" gebraucht wird. */
	public boolean bindUsedForStun() {
		return stunSlam.on() && stunMode.get().equals("Hold Key");
	}

	private void rebuildBreakers() {
		int v = breakers.version();
		if (v != breakersVersion) {
			Set<Item> set = new HashSet<>();
			for (String id : breakers.get()) {
				Identifier key = Identifier.tryParse(id);
				if (key != null) BuiltInRegistries.ITEM.getOptional(key).ifPresent(set::add);
			}
			breakerItems = set;
			breakersVersion = v;
		}
	}

	/** Darf der Combo mit diesem Item in der Hand auslösen? */
	private boolean canTrigger(ItemStack stack) {
		if (anyItem.on()) return true;          // egal welches Item
		rebuildBreakers();
		if (breakerItems.isEmpty()) return true; // nichts gewählt -> jedes Item
		return breakerItems.contains(stack.getItem());
	}

	/** Ist das ein gewähltes Schild-Brech-Item? (für den Hotbar-Wechsel) */
	private boolean isBreakWeapon(ItemStack stack) {
		rebuildBreakers();
		return breakerItems.contains(stack.getItem());
	}

	private void maceHit(Minecraft mc) {
		// Umgelenktes Ziel tot/weg -> Combo abbrechen
		if (attackTarget != null && (!attackTarget.isAlive() || attackTarget.isRemoved())) {
			attackTarget = null;
			return;
		}
		Inventory inv = mc.player.getInventory();
		int mace = -1;
		if (breachCheck.on()) mace = findSlot(inv, s -> MACE.test(s) && hasBreach(s));
		if (mace == -1) mace = findSlot(inv, MACE);
		if (mace == -1) return;
		inv.setSelectedSlot(mace);
		leftClick(mc);
		attackTarget = null; // Combo fertig
	}

	private static boolean hasBreach(ItemStack stack) {
		ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (Holder<Enchantment> h : ench.keySet()) {
			if (h.is(Enchantments.BREACH)) return true;
		}
		return false;
	}

	/** Erster passender Slot in der Hotbar (0–8), sonst -1. */
	private static int findSlot(Inventory inv, Predicate<ItemStack> test) {
		for (int i = 0; i < 9; i++) {
			if (test.test(inv.getItem(i))) return i;
		}
		return -1;
	}

	private void leftClick(Minecraft mc) {
		Entity t = attackTarget;
		if (t == null && mc.hitResult instanceof EntityHitResult hit) t = hit.getEntity();
		if (t != null) {
			// Nur beim UMGELENKTEN Schlag drehen (beim normalen Fadenkreuz-Treffer schaust du eh schon hin).
			if (attackTarget != null && mc.player instanceof LocalPlayer lp) {
				// Server-Paket IMMER direkt vor dem Angriff -> Treffer landet sofort, auch in F5.
				aimServer(lp, t);
				// Sichtbar nur im Visible-Modus UND First Person. In F5 (3rd Person) wird NICHT gedreht.
				// Startet ein kurzes, smoothes "Anschauen" (hält lookHold Ticks) -> Kopf dreht sichtbar zum Gegner.
				if (aimMode.get().equals("Visible") && firstPerson(mc)) {
					visTarget = t;
					visTicks = Math.max(1, lookHold.get());
					visibleLook(lp, t); // sofort anfangen zu drehen
				}
			}
			mc.gameMode.attack(mc.player, t);
			if (aimTarget.on() && t instanceof LivingEntity) {
				lastTarget = t;
				lastTargetTime = System.currentTimeMillis();
			}
		}
		mc.player.swing(InteractionHand.MAIN_HAND);
	}

	/** Ist der Spieler gerade in der First-Person-Ansicht (nicht F5/3rd-Person)? */
	private static boolean firstPerson(Minecraft mc) {
		return mc.options != null && mc.options.getCameraType() != null && mc.options.getCameraType().isFirstPerson();
	}

	/**
	 * Dreht NUR server-seitig zum Ziel: ein Rotations-Paket mit der Ziel-Richtung geht raus, direkt bevor
	 * der Schlag gesendet wird – der Server validiert den Treffer damit. Die echte Spieler-Rotation (und
	 * damit die Kamera) bleibt unverändert; der nächste normale Bewegungs-Tick sendet eh wieder deine
	 * echte Blickrichtung, also "dreht es sofort wie davor zurück".
	 */
	private void aimServer(LocalPlayer player, Entity tgt) {
		Vec3 eye = player.getEyePosition();
		Vec3 c = tgt.getBoundingBox().getCenter();
		double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) Math.max(-90.0, Math.min(90.0, -Math.toDegrees(Math.atan2(dy, h))));
		player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
	}
}
