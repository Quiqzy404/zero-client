package dev.quiqzy.client.module.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Targets;

/**
 * Bone Meal Bot – Baumfarm ohne Hinlaufen:
 *  1. Blöcke markieren (Erde/Gras), auf die Setzlinge kommen ("Mark block" oder Mark Mode: Mittelklick).
 *  2. Kiste mit Knochenmehl markieren ("Set chest" oder Mark Mode: Shift + Mittelklick).
 *  3. An: Setzlinge auf alle freien Blöcke setzen -> alle mit Knochenmehl wachsen lassen -> Stämme mit
 *     der Netherite-Axt abbauen -> von vorne. Ist das Knochenmehl alle, holt er neues aus der Kiste.
 * Alles muss in Reichweite sein (du stehst in der Mitte, der Bot läuft nicht).
 */
public final class BoneMealBotModule extends Module {
	public final Setting.Bool markMode = add(new Setting.Bool("mark_mode", "Mark Mode (Middle Click)", false));
	public final Setting.Int delay = add(new Setting.Int("delay", "Action Delay", 3, 1, 20, " t"));
	public final Setting.Int stacks = add(new Setting.Int("stacks", "Bone Meal Stacks", 4, 1, 27));
	public final Setting.Bool onlyNetherite = add(new Setting.Bool("only_netherite", "Only Netherite Axe", true));
	public final Setting.Int minDurability = add(new Setting.Int("min_durability", "Keep Axe Above", 20, 0, 500));
	public final Setting.Int swapSlot = add(new Setting.Int("swap_slot", "Hotbar Swap Slot", 9, 1, 9));
	public final Setting.Bool look = add(new Setting.Bool("look", "Look At Target", true));
	public final Setting.Waypoints chest = add(new Setting.Waypoints("chest", "Bone Meal Chest"));
	public final Setting.Waypoints spots = add(new Setting.Waypoints("spots", "Sapling Blocks"));

	private enum Step { PLAN, CHOP, OPEN_CHEST, TAKE, CLOSE }

	private Step step = Step.PLAN;
	private int wait, timeout, lastContainer = -1;
	private final ArrayDeque<BlockPos> logs = new ArrayDeque<>();
	private BlockPos breaking;
	/** Setzlinge, die trotz Knochenmehl nicht wachsen (kein Platz/Licht) -> überspringen. */
	private final java.util.Map<Long, Integer> tries = new java.util.HashMap<>();
	private int taken, lastCount, sameCount;
	public BlockPos target;
	public String status = "Off";
	private int trees, unreachable;

	public BoneMealBotModule() {
		super("bone_meal_bot", "Bone Meal Bot", "Plants saplings on marked blocks, bone meals them and chops the trees", Category.WORLD);
	}

	@Override
	protected void onEnable() {
		step = Step.PLAN;
		wait = 0;
		tries.clear();
		trees = 0;
		status = markMode.on() ? "Mark mode – middle click blocks" : "Starting";
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameMode != null && breaking != null) mc.gameMode.stopDestroyBlock();
		if (mc.player != null && step != Step.PLAN && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
		breaking = null;
		target = null;
		logs.clear();
		step = Step.PLAN;
		status = "Off";
	}

	private static void say(Minecraft mc, String s) {
		if (mc.player != null) mc.player.displayClientMessage(Component.literal("§aBone Meal Bot §7» §f" + s), true);
	}

	// ------------------------------------------------------------------ Markieren

	private static boolean same(Setting.Waypoints.Point p, BlockPos b, String dim) {
		return p.x() == b.getX() && p.y() == b.getY() && p.z() == b.getZ() && p.dim().equals(dim);
	}

	/** Angesehenen Block als Setzling-Platz markieren – nochmal = Markierung entfernen. */
	public void markLooked(Minecraft mc) {
		if (!(mc.hitResult instanceof BlockHitResult bhr) || mc.level == null || mc.level.getBlockState(bhr.getBlockPos()).isAir()) {
			say(mc, "Look at a block first");
			return;
		}
		BlockPos b = bhr.getBlockPos();
		BlockState st = mc.level.getBlockState(b);
		// auf Setzling/Stamm geschaut -> den Boden darunter meinen
		while ((st.is(BlockTags.SAPLINGS) || st.is(BlockTags.LOGS)) && b.getY() > mc.level.getMinY()) {
			b = b.below();
			st = mc.level.getBlockState(b);
		}
		String dim = WaypointsModule.dimensionId(mc.level);
		List<Setting.Waypoints.Point> l = spots.get();
		for (int i = 0; i < l.size(); i++) {
			if (same(l.get(i), b, dim)) {
				spots.remove(i);
				QuiqzyConfig.save();
				say(mc, "Block unmarked (" + spots.get().size() + ")");
				return;
			}
		}
		spots.add(new Setting.Waypoints.Point("Sapling " + (l.size() + 1), b.getX(), b.getY(), b.getZ(), dim));
		QuiqzyConfig.save();
		say(mc, "Block marked (" + spots.get().size() + ")");
	}

	/** Angesehene Kiste/Fass/Shulker als Knochenmehl-Lager setzen. */
	public void setChestLooked(Minecraft mc) {
		if (!(mc.hitResult instanceof BlockHitResult bhr) || mc.level == null
				|| !(mc.level.getBlockEntity(bhr.getBlockPos()) instanceof Container)) {
			say(mc, "Look at a chest first");
			return;
		}
		BlockPos b = bhr.getBlockPos();
		List<Setting.Waypoints.Point> l = new ArrayList<>();
		l.add(new Setting.Waypoints.Point("Bone Meal Chest", b.getX(), b.getY(), b.getZ(), WaypointsModule.dimensionId(mc.level)));
		chest.set(l);
		QuiqzyConfig.save();
		say(mc, "Chest set");
	}

	public void start() {
		markMode.set(false);
		if (isEnabled()) onEnable();
		else setEnabled(true);
	}

	// ------------------------------------------------------------------ Inventar

	private static int count(LocalPlayer p, Predicate<ItemStack> f) {
		int n = 0;
		Inventory inv = p.getInventory();
		for (int i = 0; i < 36; i++) if (f.test(inv.getItem(i))) n += inv.getItem(i).getCount();
		return n;
	}

	private static boolean isSapling(ItemStack s) {
		return s.is(ItemTags.SAPLINGS) && s.getItem() instanceof BlockItem;
	}

	private static boolean isBoneMeal(ItemStack s) {
		return s.is(Items.BONE_MEAL);
	}

	private boolean isAxe(ItemStack s) {
		if (onlyNetherite.on() ? !s.is(Items.NETHERITE_AXE) : !s.is(ItemTags.AXES)) return false;
		return !s.isDamageableItem() || s.getMaxDamage() - s.getDamageValue() > minDurability.get();
	}

	/** Passendes Item in die Hand nehmen (Hotbar wählen oder aus dem Inventar in den Swap-Slot tauschen). */
	private static boolean hold(Minecraft mc, LocalPlayer p, Predicate<ItemStack> f, int swap) {
		Inventory inv = p.getInventory();
		if (f.test(inv.getItem(inv.getSelectedSlot()))) return true;
		for (int i = 0; i < 9; i++) {
			if (f.test(inv.getItem(i))) {
				inv.setSelectedSlot(i);
				return true;
			}
		}
		for (int i = 9; i < 36; i++) {
			if (f.test(inv.getItem(i))) {
				// Inventar-Menü: Slots 9..35 = Hauptinventar, SWAP mit Hotbar-Taste
				mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, i, swap, ClickType.SWAP, p);
				inv.setSelectedSlot(swap);
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ Ablauf

	private boolean inReach(LocalPlayer p, BlockPos b) {
		return p.getEyePosition().distanceTo(Vec3.atCenterOf(b)) <= p.blockInteractionRange();
	}

	private void face(LocalPlayer p, Vec3 v) {
		if (!look.on()) return;
		float[] a = Targets.angles(p, v);
		p.setYRot(a[0]);
		p.setXRot(a[1]);
	}

	private void use(Minecraft mc, LocalPlayer p, BlockPos pos, Direction side) {
		Vec3 hit = Vec3.atCenterOf(pos).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
		face(p, hit);
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, side, pos, false));
		p.swing(InteractionHand.MAIN_HAND);
	}

	private List<BlockPos> mySpots(Minecraft mc) {
		String dim = WaypointsModule.dimensionId(mc.level);
		List<BlockPos> out = new ArrayList<>();
		for (Setting.Waypoints.Point pt : spots.get()) if (pt.dim().equals(dim)) out.add(new BlockPos(pt.x(), pt.y(), pt.z()));
		return out;
	}

	private BlockPos chestPos(Minecraft mc) {
		String dim = WaypointsModule.dimensionId(mc.level);
		for (Setting.Waypoints.Point pt : chest.get()) if (pt.dim().equals(dim)) return new BlockPos(pt.x(), pt.y(), pt.z());
		return null;
	}

	/** Alle zusammenhängenden Stämme ab dem Stammfuß (nur erreichbare), unten zuerst. */
	private void collectTrunk(Minecraft mc, LocalPlayer p, BlockPos base) {
		logs.clear();
		unreachable = 0;
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		q.add(base);
		seen.add(base.asLong());
		List<BlockPos> found = new ArrayList<>();
		while (!q.isEmpty() && found.size() < 64) {
			BlockPos b = q.poll();
			if (!mc.level.getBlockState(b).is(BlockTags.LOGS)) continue;
			if (inReach(p, b)) found.add(b);
			else unreachable++;
			for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				BlockPos n = b.offset(dx, dy, dz);
				if (Math.abs(n.getX() - base.getX()) > 4 || Math.abs(n.getZ() - base.getZ()) > 4 || n.getY() - base.getY() > 24) continue;
				if (seen.add(n.asLong())) q.add(n);
			}
		}
		found.sort(Comparator.comparingInt(BlockPos::getY));
		logs.addAll(found);
	}

	@Override
	public void onStartTick(Minecraft mc) {
		// Mark Mode: Mittelklick = Block (de)markieren, Shift + Mittelklick = Kiste setzen
		if (!markMode.on() || mc.screen != null || mc.player == null) return;
		while (mc.options.keyPickItem.consumeClick()) {
			if (mc.player.isShiftKeyDown()) setChestLooked(mc);
			else markLooked(mc);
			dev.quiqzy.client.util.UiSounds.added();
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null) return;
		if (markMode.on()) {
			status = "Mark mode – " + mySpots(mc).size() + " blocks" + (chestPos(mc) != null ? ", chest set" : ", no chest");
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		switch (step) {
			case PLAN -> plan(mc, p);
			case CHOP -> chop(mc, p);
			case OPEN_CHEST -> openChest(mc, p);
			case TAKE -> take(mc, p);
			case CLOSE -> {
				if (p.containerMenu != p.inventoryMenu) p.closeContainer();
				step = Step.PLAN;
				wait = delay.get() + 2;
			}
		}
	}

	private void plan(Minecraft mc, LocalPlayer p) {
		if (mc.screen != null) {
			status = "Paused (menu open)";
			return;
		}
		List<BlockPos> list = mySpots(mc);
		if (list.isEmpty()) {
			status = "Mark blocks first";
			return;
		}
		// 1) gewachsene Bäume abbauen
		for (BlockPos s : list) {
			BlockPos up = s.above();
			if (mc.level.getBlockState(up).is(BlockTags.LOGS) && inReach(p, up)) {
				if (!hold(mc, p, this::isAxe, swapSlot.get() - 1)) {
					status = onlyNetherite.on() ? "Need a Netherite Axe" : "Need an axe";
					say(mc, status + " – stopped");
					setEnabled(false);
					return;
				}
				collectTrunk(mc, p, up);
				target = up;
				step = Step.CHOP;
				trees++;
				return;
			}
		}
		// 2) Setzlinge knochenmehlen
		for (BlockPos s : list) {
			BlockPos up = s.above();
			if (!mc.level.getBlockState(up).is(BlockTags.SAPLINGS) || !inReach(p, up)) continue;
			int t = tries.getOrDefault(up.asLong(), 0);
			if (t >= 40) continue;
			if (count(p, BoneMealBotModule::isBoneMeal) == 0) {
				refill(mc, p, "Out of bone meal");
				return;
			}
			hold(mc, p, BoneMealBotModule::isBoneMeal, swapSlot.get() - 1);
			target = up;
			status = "Bone mealing " + up.toShortString();
			use(mc, p, up, Direction.UP);
			tries.put(up.asLong(), t + 1);
			wait = delay.get();
			return;
		}
		// 3) Setzlinge pflanzen
		for (BlockPos s : list) {
			BlockPos up = s.above();
			if (!inReach(p, up) || !mc.level.getBlockState(up).canBeReplaced()) continue;
			ItemStack sap = findSapling(p);
			if (sap == null) {
				refill(mc, p, "Out of saplings");
				return;
			}
			BlockState plant = ((BlockItem) sap.getItem()).getBlock().defaultBlockState();
			if (!plant.canSurvive(mc.level, up)) continue;
			hold(mc, p, st -> st.is(sap.getItem()), swapSlot.get() - 1);
			target = up;
			tries.remove(up.asLong());
			status = "Planting on " + s.toShortString();
			use(mc, p, s, Direction.UP);
			wait = delay.get();
			return;
		}
		target = null;
		boolean stuck = false;
		for (BlockPos s : list) if (mc.level.getBlockState(s.above()).is(BlockTags.SAPLINGS) && tries.getOrDefault(s.above().asLong(), 0) >= 40) stuck = true;
		status = stuck ? "Some saplings can't grow (space/light?)" : "Nothing to do – stand closer?";
		if (stuck && ++idle > 100) {
			tries.clear(); // nach 5 s nochmal probieren
			idle = 0;
		}
	}

	private int idle;

	private ItemStack findSapling(LocalPlayer p) {
		Inventory inv = p.getInventory();
		if (isSapling(inv.getItem(inv.getSelectedSlot()))) return inv.getItem(inv.getSelectedSlot());
		for (int i = 0; i < 36; i++) if (isSapling(inv.getItem(i))) return inv.getItem(i);
		return null;
	}

	private boolean needSaplings;

	private void refill(Minecraft mc, LocalPlayer p, String why) {
		needSaplings = why.contains("sapling");
		BlockPos c = chestPos(mc);
		if (c == null) {
			status = why + " – no chest set";
			say(mc, status);
			setEnabled(false);
			return;
		}
		if (!inReach(p, c)) {
			status = why + " – chest out of reach";
			say(mc, status);
			setEnabled(false);
			return;
		}
		status = why + " – refilling";
		target = c;
		lastContainer = p.containerMenu.containerId;
		use(mc, p, c, Direction.UP);
		step = Step.OPEN_CHEST;
		timeout = 60;
		wait = 2;
	}

	private void openChest(Minecraft mc, LocalPlayer p) {
		var m = p.containerMenu;
		if (m == p.inventoryMenu || m.containerId == lastContainer || m.getStateId() == 0) {
			if (--timeout <= 0) {
				status = "Chest didn't open";
				say(mc, status);
				step = Step.CLOSE;
			}
			return;
		}
		taken = 0;
		sameCount = 0;
		lastCount = -1;
		step = Step.TAKE;
		wait = delay.get();
	}

	private void take(Minecraft mc, LocalPlayer p) {
		var m = p.containerMenu;
		if (m == p.inventoryMenu) {
			step = Step.PLAN;
			return;
		}
		int bm = count(p, BoneMealBotModule::isBoneMeal);
		boolean wantSaplings = count(p, BoneMealBotModule::isSapling) < 16;
		Slot next = null;
		for (Slot sl : m.slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack s = sl.getItem();
			if ((isBoneMeal(s) && taken < stacks.get()) || (wantSaplings && isSapling(s))) {
				next = sl;
				break;
			}
		}
		// nichts mehr da oder Inventar voll (Anzahl ändert sich nicht)
		if (lastCount == bm) sameCount++;
		else sameCount = 0;
		lastCount = bm;
		if (next == null || sameCount >= 3) {
			if (bm == 0 || (needSaplings && count(p, BoneMealBotModule::isSapling) == 0)) {
				status = bm == 0 ? "Chest has no bone meal – stopped" : "No saplings in inventory or chest – stopped";
				say(mc, status);
				p.closeContainer();
				setEnabled(false);
				return;
			}
			step = Step.CLOSE;
			wait = delay.get();
			return;
		}
		if (isBoneMeal(next.getItem())) taken++;
		mc.gameMode.handleInventoryMouseClick(m.containerId, next.index, 0, ClickType.QUICK_MOVE, p);
		status = "Taking bone meal (" + bm + ")";
		wait = Math.max(1, delay.get() / 2);
	}

	private void chop(Minecraft mc, LocalPlayer p) {
		if (breaking != null && !mc.level.getBlockState(breaking).is(BlockTags.LOGS)) {
			breaking = null;
			wait = 1;
		}
		if (breaking == null) {
			while (!logs.isEmpty() && !mc.level.getBlockState(logs.peek()).is(BlockTags.LOGS)) logs.poll();
			breaking = logs.poll();
			timeout = 120;
			if (breaking == null) {
				mc.gameMode.stopDestroyBlock();
				status = "Tree " + trees + " chopped";
				if (unreachable > 0) say(mc, unreachable + " log(s) out of reach – stand 2 blocks higher, saplings 1-2 blocks away");
				step = Step.PLAN;
				wait = delay.get();
				return;
			}
		}
		if (!hold(mc, p, this::isAxe, swapSlot.get() - 1)) {
			mc.gameMode.stopDestroyBlock();
			status = "Axe gone or too damaged – stopped";
			say(mc, status);
			setEnabled(false);
			return;
		}
		if (--timeout <= 0 || !inReach(p, breaking)) {
			mc.gameMode.stopDestroyBlock();
			breaking = null;
			return;
		}
		target = breaking;
		status = "Chopping tree " + trees + " (" + (logs.size() + 1) + " logs left)";
		Direction side = Direction.getApproximateNearest(p.getEyePosition().subtract(Vec3.atCenterOf(breaking)));
		face(p, Vec3.atCenterOf(breaking));
		mc.gameMode.continueDestroyBlock(breaking, side);
		p.swing(InteractionHand.MAIN_HAND);
	}
}
