package dev.quiqzy.client.gui.engine;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;

/** Kamerafahrt-Aufnahme für das Promo-Edit: setzt jeden Tick die Freecam und speichert einen Screenshot. */
final class EditRec {
   static double cx, cz;
   static int by;
   static int tick = -1;
   static int saved;
   private static boolean registered;
   static final int A = 70, B = 110, C = 60;

   static void start(Minecraft mc) {
      tick = 0;
      if (!registered) {
         registered = true;
         net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(EditRec::tick);
      }
   }

   private static double ease(double t) {
      return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
   }

   private static void look(double x, double y, double z, double tx, double ty, double tz) {
      var f = ModuleManager.FREECAM;
      f.x = x;
      f.y = y;
      f.z = z;
      double dx = tx - x, dy = ty - y, dz = tz - z;
      f.yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
      f.pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
   }

   private static void tick(Minecraft mc) {
      if (tick < 0 || mc.player == null) return;
      double ty = by + 1.5;
      String shot;
      int i;
      if (tick < A) {
         // Schuss A: aus dem Himmel auf die Base zu
         double t = ease(tick / (double) A);
         look(cx - 46 + 34 * t, by + 70 - 58 * t, cz - 46 + 34 * t, cx, ty, cz);
         shot = "a";
         i = tick;
      } else if (tick < A + B) {
         // Schuss B: Orbit um die Base
         double t = (tick - A) / (double) B;
         double ang = Math.toRadians(200 + 230 * t);
         look(cx + Math.cos(ang) * 17, by + 10 - 3 * t, cz + Math.sin(ang) * 17, cx, ty, cz);
         shot = "b";
         i = tick - A;
      } else if (tick < A + B + C) {
         // Schuss C: tief durch das Lager
         double t = ease((tick - A - B) / (double) C);
         look(cx - 7 + 12 * t, by + 3.2, cz - 1.5, cx + 9, by + 0.5, cz - 4);
         shot = "c";
         i = tick - A - B;
      } else {
         tick = -1;
         return;
      }
      String name = String.format("edit_%s_%04d.png", shot, i);
      net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), name, mc.getMainRenderTarget(), 1, msg -> {});
      saved++;
      tick++;
   }
}

/** Zweites Edit: große Base + unterirdische Base mit Schacht, acht Kamerafahrten. */
final class Edit2 {
   static int bigX, bigZ, ugX, ugZ, surf;
   static int tick = -1, saved;
   private static boolean registered;
   private static net.minecraft.server.level.ServerLevel lvl;

   // Länge der Schüsse in Ticks (20 pro Sekunde)
   static final String[] NAMES = {"sky", "shaft", "ugfly", "ugorbit", "approach", "orbit", "hall", "top"};
   static final int[] LEN = {60, 60, 80, 60, 60, 100, 80, 60};

   private static void set(int x, int y, int z, net.minecraft.world.level.block.Block b) {
      lvl.setBlock(new net.minecraft.core.BlockPos(x, y, z), b.defaultBlockState(), 2);
   }

   private static void fill(int x1, int y1, int z1, int x2, int y2, int z2, net.minecraft.world.level.block.Block b) {
      for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
         for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, b);
   }

   static void build(net.minecraft.server.level.ServerLevel level) {
      lvl = level;
      level.setDayTime(13400);
      var Bk = net.minecraft.world.level.block.Blocks.class;
      net.minecraft.world.level.block.Block[] shulkers = {
         net.minecraft.world.level.block.Blocks.ORANGE_SHULKER_BOX, net.minecraft.world.level.block.Blocks.PURPLE_SHULKER_BOX,
         net.minecraft.world.level.block.Blocks.LIGHT_BLUE_SHULKER_BOX, net.minecraft.world.level.block.Blocks.MAGENTA_SHULKER_BOX,
         net.minecraft.world.level.block.Blocks.YELLOW_SHULKER_BOX, net.minecraft.world.level.block.Blocks.LIME_SHULKER_BOX,
         net.minecraft.world.level.block.Blocks.RED_SHULKER_BOX, net.minecraft.world.level.block.Blocks.WHITE_SHULKER_BOX};
      int y = surf;

      // ---------------- Große Base (41x41)
      int x0 = bigX - 20, z0 = bigZ - 20;
      fill(x0 - 3, y, z0 - 3, x0 + 43, y + 30, z0 + 43, net.minecraft.world.level.block.Blocks.AIR);
      fill(x0 - 3, y - 4, z0 - 3, x0 + 43, y - 1, z0 + 43, net.minecraft.world.level.block.Blocks.DEEPSLATE);
      for (int x = 0; x <= 40; x++) for (int z = 0; z <= 40; z++) {
         boolean stripe = x % 5 == 0 || z % 5 == 0;
         set(x0 + x, y - 1, z0 + z, stripe ? net.minecraft.world.level.block.Blocks.SEA_LANTERN
               : ((x + z) % 2 == 0 ? net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE : net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE));
      }
      // Außenmauer mit Säulen
      for (int i = 0; i <= 40; i++) {
         for (int h = 0; h < 12; h++) {
            net.minecraft.world.level.block.Block b = i % 5 == 0 ? net.minecraft.world.level.block.Blocks.QUARTZ_PILLAR
                  : (h == 11 ? net.minecraft.world.level.block.Blocks.ORANGE_STAINED_GLASS : net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_BRICKS);
            if (h >= 3 && h <= 8 && i % 5 != 0) b = net.minecraft.world.level.block.Blocks.TINTED_GLASS;
            set(x0 + i, y + h, z0, b);
            set(x0 + i, y + h, z0 + 40, b);
            set(x0, y + h, z0 + i, b);
            set(x0 + 40, y + h, z0 + i, b);
         }
      }
      // Ecktürme
      int[][] corners = {{0, 0}, {40, 0}, {0, 40}, {40, 40}};
      for (int[] c : corners) {
         for (int h = 0; h < 24; h++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            if (!edge) continue;
            set(x0 + c[0] + dx, y + h, z0 + c[1] + dz, h > 19 ? net.minecraft.world.level.block.Blocks.ORANGE_STAINED_GLASS
                  : (Math.abs(dx) == 2 && Math.abs(dz) == 2 ? net.minecraft.world.level.block.Blocks.QUARTZ_PILLAR : net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ));
         }
         set(x0 + c[0], y + 24, z0 + c[1], net.minecraft.world.level.block.Blocks.BEACON);
         fill(x0 + c[0] - 1, y + 23, z0 + c[1] - 1, x0 + c[0] + 1, y + 23, z0 + c[1] + 1, net.minecraft.world.level.block.Blocks.IRON_BLOCK);
      }
      // Haupthalle in der Mitte
      int hx = x0 + 10, hz = z0 + 10;
      for (int x = 0; x <= 20; x++) for (int z = 0; z <= 20; z++) {
         boolean wall = x == 0 || z == 0 || x == 20 || z == 20;
         for (int h = 0; h < 14; h++) {
            if (wall) set(hx + x, y + h, hz + z, (x % 4 == 0 && z % 4 == 0) || ((x == 0 || x == 20) && z % 4 == 0) || ((z == 0 || z == 20) && x % 4 == 0)
                  ? net.minecraft.world.level.block.Blocks.QUARTZ_PILLAR : (h > 2 && h < 12 ? net.minecraft.world.level.block.Blocks.TINTED_GLASS : net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ));
         }
         set(hx + x, y + 14, hz + z, (x + z) % 3 == 0 ? net.minecraft.world.level.block.Blocks.ORANGE_STAINED_GLASS : net.minecraft.world.level.block.Blocks.GLASS);
      }
      for (int z = 2; z <= 18; z += 4) for (int x = 2; x <= 18; x++) {
         if (x == 10) continue;
         set(hx + x, y, hz + z, shulkers[(x + z) % shulkers.length]);
         set(hx + x, y + 1, hz + z, shulkers[(x + z + 3) % shulkers.length]);
      }
      fill(hx + 8, y, hz + 8, hx + 12, y, hz + 12, net.minecraft.world.level.block.Blocks.IRON_BLOCK);
      fill(hx + 9, y + 1, hz + 9, hx + 11, y + 1, hz + 11, net.minecraft.world.level.block.Blocks.IRON_BLOCK);
      set(hx + 10, y + 2, hz + 10, net.minecraft.world.level.block.Blocks.BEACON);
      // Hof: Truhenreihen + Enchant
      for (int x = 3; x <= 37; x += 2) {
         if (x > 8 && x < 32) continue;
         for (int z = 3; z <= 37; z += 6) set(x0 + x, y, z0 + z, net.minecraft.world.level.block.Blocks.CHEST);
      }
      set(x0 + 5, y, z0 + 20, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE);
      set(x0 + 35, y, z0 + 20, net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR);

      // ---------------- Unterirdische Base (27x27, 10 hoch) mit 3x3-Schacht
      int ux = ugX - 13, uz = ugZ - 13, uy = y - 32;
      fill(ux - 1, uy - 1, uz - 1, ux + 27, uy + 11, uz + 27, net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS);
      fill(ux, uy, uz, ux + 26, uy + 10, uz + 26, net.minecraft.world.level.block.Blocks.AIR);
      for (int x = 0; x <= 26; x++) for (int z = 0; z <= 26; z++) {
         set(ux + x, uy - 1, uz + z, (x % 4 == 0 || z % 4 == 0) ? net.minecraft.world.level.block.Blocks.SEA_LANTERN : net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE);
         set(ux + x, uy + 10, uz + z, (x % 6 == 3 && z % 6 == 3) ? net.minecraft.world.level.block.Blocks.SHROOMLIGHT : net.minecraft.world.level.block.Blocks.CHISELED_DEEPSLATE);
      }
      for (int z = 3; z <= 23; z += 4) for (int x = 2; x <= 24; x++) {
         if (x >= 11 && x <= 15) continue;
         set(ux + x, uy, uz + z, shulkers[(x * 3 + z) % shulkers.length]);
         set(ux + x, uy + 1, uz + z, (x % 2 == 0) ? net.minecraft.world.level.block.Blocks.CHEST : shulkers[(x + z) % shulkers.length]);
      }
      fill(ux + 11, uy, uz + 11, ux + 15, uy, uz + 15, net.minecraft.world.level.block.Blocks.GOLD_BLOCK);
      fill(ux + 12, uy + 1, uz + 12, ux + 14, uy + 1, uz + 14, net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK);
      set(ux + 13, uy + 2, uz + 13, net.minecraft.world.level.block.Blocks.BEACON);
      for (int i = 0; i <= 26; i += 2) {
         set(ux + i, uy + 9, uz, net.minecraft.world.level.block.Blocks.END_ROD);
         set(ux + i, uy + 9, uz + 26, net.minecraft.world.level.block.Blocks.END_ROD);
      }
      // Schacht von der Oberfläche nach unten (über der Ecke des Raums)
      int sx = ux + 3, sz = uz + 13;
      for (int yy = uy + 10; yy <= y + 1; yy++) {
         for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean in = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
            if (in) set(sx + dx, yy, sz + dz, net.minecraft.world.level.block.Blocks.AIR);
            else if (yy < y) set(sx + dx, yy, sz + dz, (yy % 6 == 0) ? net.minecraft.world.level.block.Blocks.SEA_LANTERN : net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE);
         }
      }
      shaftX = sx + 0.5;
      shaftZ = sz + 0.5;
      ugY = uy;
   }

   static double shaftX, shaftZ;
   static int ugY;

   static void start() {
      tick = 0;
      if (!registered) {
         registered = true;
         net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(Edit2::tick);
      }
   }

   private static double ease(double t) {
      return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
   }

   private static void cam(double x, double y, double z, double tx, double ty, double tz) {
      var f = ModuleManager.FREECAM;
      f.x = x;
      f.y = y;
      f.z = z;
      double dx = tx - x, dy = ty - y, dz = tz - z;
      f.yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
      f.pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
   }

   private static void tick(Minecraft mc) {
      if (tick < 0 || mc.player == null) return;
      int t0 = tick, idx = 0;
      while (idx < LEN.length && t0 >= LEN[idx]) {
         t0 -= LEN[idx];
         idx++;
      }
      if (idx >= LEN.length) {
         tick = -1;
         return;
      }
      double t = t0 / (double) LEN[idx], e = ease(t);
      double bx = bigX + 0.5, bz = bigZ + 0.5, ux = ugX + 0.5, uz = ugZ + 0.5, y = surf;
      double uy = ugY;
      switch (NAMES[idx]) {
         case "sky" -> cam(bx - 140 + 90 * t, y + 55 - 15 * t, bz - 60 + 30 * t, bx, y + 8, bz);
         case "shaft" -> {
            // Sturz den Schacht hinunter, Blick nach unten
            var f = ModuleManager.FREECAM;
            f.x = shaftX;
            f.z = shaftZ;
            f.y = y + 14 - (y + 14 - (uy + 4)) * e;
            f.yaw = 0f;
            f.pitch = 89f;
         }
         case "ugfly" -> cam(ux - 12 + 22 * e, uy + 4.5, uz - 9, ux + 12, uy + 1, uz - 4);
         case "ugorbit" -> {
            double a = Math.toRadians(30 + 160 * t);
            cam(ux + Math.cos(a) * 9, uy + 6, uz + Math.sin(a) * 9, ux, uy + 1.5, uz);
         }
         case "approach" -> cam(bx + 90 - 55 * e, y + 30 - 12 * e, bz + 50 - 30 * e, bx, y + 6, bz);
         case "orbit" -> {
            double a = Math.toRadians(-20 + 200 * t);
            cam(bx + Math.cos(a) * 38, y + 28, bz + Math.sin(a) * 38, bx, y + 4, bz);
         }
         case "hall" -> cam(bx - 9 + 18 * e, y + 3.5, bz - 6, bx + 10, y + 1, bz - 2);
         case "top" -> {
            var f = ModuleManager.FREECAM;
            f.x = bx;
            f.z = bz;
            f.y = y + 70 - 20 * e;
            f.yaw = (float) (40 * t);
            f.pitch = 89f;
         }
         default -> {
         }
      }
      String name = String.format("e2_%d_%s_%04d.png", idx, NAMES[idx], t0);
      net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), name, mc.getMainRenderTarget(), 1, msg -> {});
      saved++;
      tick++;
   }
}

/** Selbsttest im Spiel: HUD, Waffen, Treffer-Effekte, Target HUD (mit einem Test-Schwein). */
final class InGameScenario {
   private InGameScenario() {
   }

   /** Feste Maschinen-Position für den "sounds"-Selbsttest (Spieler driftet sonst weg). */
   static int sndFx, sndFy, sndFz;

   private static void log(String what, Object value) {
      QuiqzyClient.LOGGER.info("[Selbsttest] {}: {}", what, value);
   }

   private static Vec3 ahead(Minecraft mc, double d) {
      float yaw = (float) Math.toRadians(mc.player.getYRot());
      return mc.player.position().add(-Math.sin(yaw) * d, 0, Math.cos(yaw) * d);
   }

   /** Setzt Smoker + Ofen an der festen Position, befüllt sie und zündet sie per Reflection. */
   private static void sndPlace(IntegratedServer server, int fx, int fy, int fz) {
      ServerLevel lvl = server.getLevel(Level.OVERWORLD);
      BlockPos smoker = new BlockPos(fx, fy, fz);
      BlockPos ofen = new BlockPos(fx, fy, fz + 2);
      lvl.setBlockAndUpdate(smoker, net.minecraft.world.level.block.Blocks.SMOKER.defaultBlockState());
      lvl.setBlockAndUpdate(ofen, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
      sndIgnite(lvl, smoker);
      sndIgnite(lvl, ofen);
      log("machines placed at", fx + " " + fy + " " + fz);
   }

   /** Befüllt die Ofen-Block-Entity und zündet: litTimeRemaining/litTotalTime, dann LIT-Block-Flip. */
   private static void sndIgnite(ServerLevel lvl, BlockPos pos) {
      if (!(lvl.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity be)) return;
      be.setItem(0, new ItemStack(Items.PORKCHOP, 16));
      be.setItem(1, new ItemStack(Items.COAL, 64));
      try {
         var cls = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class;
         java.lang.reflect.Field f1 = cls.getDeclaredField("litTimeRemaining");
         f1.setAccessible(true);
         f1.setInt(be, 600);
         java.lang.reflect.Field f2 = cls.getDeclaredField("litTotalTime");
         f2.setAccessible(true);
         f2.setInt(be, 600);
      } catch (Exception ignoredE) {
      }
      lvl.setBlockAndUpdate(pos, lvl.getBlockState(pos).setValue(AbstractFurnaceBlock.LIT, true));
   }

   private static LivingEntity pig(Minecraft mc) {
      for (var e : mc.level.entitiesForRendering()) {
         if (e instanceof Pig p && p.isAlive() && p.distanceTo(mc.player) < 10) return p;
      }
      return null;
   }

   private static void lookCam(double x, double y, double z, double tx, double ty, double tz) {
      var f = ModuleManager.FREECAM;
      f.x = x; f.y = y; f.z = z;
      double dx = tx - x, dy = ty - y, dz = tz - z;
      f.yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
      f.pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
   }

   private static void spawnPig(Minecraft mc, float health) {
      IntegratedServer server = mc.getSingleplayerServer();
      if (server == null) return;
      Vec3 at = ahead(mc, 2.6);
      server.execute(() -> {
         ServerLevel lvl = server.getLevel(Level.OVERWORLD);
         Pig p = EntityType.PIG.create(lvl, EntitySpawnReason.COMMAND);
         if (p == null) return;
         p.setPos(at.x, mc.player.getY(), at.z);
         p.setNoAi(true);
         p.setHealth(health);
         lvl.addFreshEntity(p);
      });
   }

   static void add(BiConsumer<String, Consumer<Minecraft>> step) {
      step.accept("ingame_setup@40", mc -> {
         mc.setScreen(null);
         mc.player.setXRot(8f);
         mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), new ItemStack(Items.DIAMOND_SWORD));
         ModuleManager.HUD.setEnabled(true);
         ModuleManager.KEYSTROKES.setEnabled(true);
         ModuleManager.WAYPOINTS.setEnabled(true);
         Vec3 w = ahead(mc, 14);
         if (ModuleManager.WAYPOINTS.points.get().isEmpty()) ModuleManager.WAYPOINTS.points.add(new Setting.Waypoints.Point("Base", (int) w.x, (int) mc.player.getY(), (int) w.z, "overworld"));
         ModuleManager.SUS_CHUNK.setEnabled(true);
         ModuleManager.EMBERS.setEnabled(true);
         ModuleManager.WEAPON_SKINS.setEnabled(true);
         ModuleManager.WEAPON_SKINS.trickOnSwap.set(false);
         ModuleManager.TARGET_HUD.setEnabled(true);
         ModuleManager.HIT_EFFECTS.setEnabled(true);
         ModuleManager.LOW_FIRE.setEnabled(true);
         spawnPig(mc, 10f);
      });
      if ("hudlook".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("hl0@30", mc -> {
            mc.setScreen(null);
            ModuleManager.HUD.setEnabled(true);
            ModuleManager.WATERMARK.setEnabled(true);
            ModuleManager.MODULE_LIST.setEnabled(true);
            ModuleManager.KEYSTROKES.setEnabled(true);
            ModuleManager.TARGET_HUD.setEnabled(true);
            // ein paar Module an, damit die Arraylist Inhalt hat
            ModuleManager.FULLBRIGHT.setEnabled(true);
            ModuleManager.NAMETAGS.setEnabled(true);
            ModuleManager.CROSSHAIR.setEnabled(true);
            ModuleManager.GLOW_WINGS.themeColor.set(true); // Originalfarbe der Textur zeigen
            ModuleManager.GLOW_WINGS.setEnabled(true);
         });
         step.accept("hl1@22", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "hud_look.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("hl2@8", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            mc.player.setYRot(0f);
            mc.player.setYBodyRot(0f);
            mc.player.yBodyRotO = 0f;
            mc.player.setXRot(0f);
            mc.player.setDeltaMovement(0, 0, 0);
         });
         step.accept("hl3@22", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "wings_look.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("hl4@6", mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
         return;
      }
      if ("glassinv".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("gi0@25", mc -> {
            ModuleManager.GLASS_INVENTORY.setEnabled(true);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "give @p diamond_sword");
               cmd.performPrefixedCommand(src, "give @p golden_apple 16");
               cmd.performPrefixedCommand(src, "item replace entity @p armor.chest with diamond_chestplate");
            });
         });
         step.accept("gi1@15", mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
         step.accept("gi2@15", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "glass_inv.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gi3@6", mc -> mc.setScreen(null));
         return;
      }
      if ("rainfx".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("rx0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.RAIN_FX.style.set("Neon");
            ModuleManager.RAIN_FX.amount.set(300);
            ModuleManager.RAIN_FX.range.set(10);
            ModuleManager.RAIN_FX.setEnabled(true);
            IntegratedServer server = mc.getSingleplayerServer();
            int bx = mc.player.getBlockX(), by = mc.player.getBlockY() - 1, bz = mc.player.getBlockZ();
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "fill " + (bx - 10) + " " + by + " " + (bz - 10) + " " + (bx + 10) + " " + by + " " + (bz + 10) + " black_concrete");
               cmd.performPrefixedCommand(src, "time set 16000");
            });
            mc.player.setXRot(35f);
         });
         step.accept("rx1@45", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "rain_1.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("rx2@10", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "rain_2.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("rx3@6", mc -> {});
         return;
      }
      if ("snow".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("sn0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.RAIN_FX.style.set("Snow");
            ModuleManager.RAIN_FX.amount.set(300);
            ModuleManager.RAIN_FX.range.set(10);
            ModuleManager.RAIN_FX.splashes.set(true);
            ModuleManager.RAIN_FX.setEnabled(true);
            IntegratedServer server = mc.getSingleplayerServer();
            int bx = mc.player.getBlockX(), by = mc.player.getBlockY() - 1, bz = mc.player.getBlockZ();
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "fill " + (bx - 10) + " " + by + " " + (bz - 10) + " " + (bx + 10) + " " + by + " " + (bz + 10) + " black_concrete");
               cmd.performPrefixedCommand(src, "time set 16000");
            });
            mc.player.setXRot(25f);
         });
         step.accept("sn1@60", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "snow_1.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sn2@15", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "snow_2.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sn3@6", mc -> {});
         return;
      }
      if ("glossy".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("gf0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.GLOSSY_FLOOR.intensity.set(80);
            ModuleManager.GLOSSY_FLOOR.length.set(220);
            ModuleManager.GLOSSY_FLOOR.setEnabled(true);
            ModuleManager.RAIN_FX.style.set("Water");
            ModuleManager.RAIN_FX.setEnabled(true);
            // hügeliges Gelände (Stufen) bauen, um den Terrain-Bug zu prüfen
            IntegratedServer server = mc.getSingleplayerServer();
            int bx = mc.player.getBlockX(), by = mc.player.getBlockY() - 1, bz = mc.player.getBlockZ();
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               for (int s = 1; s <= 6; s++)
                  cmd.performPrefixedCommand(src, "fill " + (bx + 4 + s * 2) + " " + by + " " + (bz - 12) + " " + (bx + 5 + s * 2) + " " + (by + s) + " " + (bz + 12) + " grass_block");
               cmd.performPrefixedCommand(src, "fill " + (bx - 14) + " " + (by + 2) + " " + (bz - 14) + " " + (bx - 8) + " " + (by + 4) + " " + (bz - 8) + " grass_block");
               cmd.performPrefixedCommand(src, "time set 1000");
            });
            ModuleManager.FREECAM.setEnabled(true);
         });
         step.accept("gf1@25", mc -> {
            Vec3 at = ahead(mc, 6);
            lookCam(mc.player.getX() - 6, mc.player.getY() + 4.5, mc.player.getZ() - 6, at.x, at.y - 1.5, at.z);
         });
         step.accept("gf2@10", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "glossy_1.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gf3@20", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "glossy_2.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gf4@5", mc -> mc.player.setXRot(55f));
         step.accept("gf5@15", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "glossy_down.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gf4@6", mc -> {});
         return;
      }
      if ("guiworld".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("gw0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.GUI_WORLD.scale.set(150);
            ModuleManager.GUI_WORLD.mirror.set(true);
            ModuleManager.GUI_WORLD.setEnabled(true);          // platziert das Panel vor dem Spieler
            mc.setScreen(new dev.quiqzy.client.gui.ZeroGui());  // echte ClickGUI auf -> Capture laeuft
         });
         step.accept("gw1@35", mc -> {});                       // warten, damit die GUI aufgenommen wird
         step.accept("gw2@4", mc -> {
            mc.setScreen(null);                                 // GUI zu -> Quad zeigt die Aufnahme
            ModuleManager.FREECAM.setEnabled(true);
            var g = ModuleManager.GUI_WORLD;
            lookCam(g.ax - 2.6, g.ay + 0.8, g.az - 2.6, g.ax, g.ay, g.az);
         });
         step.accept("gw3@8", mc -> { mc.setScreen(null); net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "guiworld_front.png", mc.getMainRenderTarget(), 1, msg -> {}); });
         step.accept("gw4@12", mc -> {
            mc.setScreen(null);
            var g = ModuleManager.GUI_WORLD;
            lookCam(g.ax + 3.2, g.ay + 1.4, g.az + 3.2, g.ax, g.ay, g.az);
         });
         step.accept("gw5@8", mc -> { mc.setScreen(null); net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "guiworld_back.png", mc.getMainRenderTarget(), 1, msg -> {}); });
         step.accept("gw6@6", mc -> {});
         return;
      }
      if ("f5jump".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("fj0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.CLEAN_F5.setEnabled(true);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
         });
         for (int i = 0; i < 40; i++) {
            final int k = i;
            step.accept("fjt" + i + (i == 0 ? "@40" : "@1"), mc -> {
               if (k == 2 || k == 20) mc.player.jumpFromGround();
               var cam = mc.gameRenderer.getMainCamera();
               dev.quiqzy.client.QuiqzyClient.LOGGER.info("[f5] t={} playerY={} camY={}", k, String.format("%.3f", mc.player.getY()), String.format("%.3f", cam.position().y));
            });
         }
         step.accept("fjz@5", mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); ModuleManager.CLEAN_F5.setEnabled(false); });
         return;
      }
      if ("skychams".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("sk0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.CUSTOM_SKY.preset.set("Planets");
            ModuleManager.CUSTOM_SKY.setEnabled(true);
            ModuleManager.GLOSSY_FLOOR.setEnabled(false);
            mc.player.setXRot(-35f);
            mc.player.setYRot(0f);
            IntegratedServer server = mc.getSingleplayerServer();
            int bx = mc.player.getBlockX() + 120, by = 200, bz = mc.player.getBlockZ() + 120;
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "fill " + (bx - 10) + " " + by + " " + (bz - 10) + " " + (bx + 10) + " " + by + " " + (bz + 10) + " smooth_quartz");
               cmd.performPrefixedCommand(src, "fill " + (bx - 10) + " " + (by + 1) + " " + (bz - 10) + " " + (bx + 10) + " " + (by + 8) + " " + (bz + 10) + " air");
               cmd.performPrefixedCommand(src, "fill " + (bx - 1) + " " + (by + 1) + " " + (bz + 4) + " " + (bx + 1) + " " + (by + 2) + " " + (bz + 4) + " stone");
               server.getPlayerList().getPlayers().get(0).teleportTo(bx + 0.5, by + 1, bz + 0.5);
               cmd.performPrefixedCommand(src, "time set 1000");
               cmd.performPrefixedCommand(src, "kill @e[type=pig]");
               cmd.performPrefixedCommand(src, "summon pig " + (bx + 2.5) + " " + (by + 1) + " " + (bz + 3.0) + " {NoAI:1b,PersistenceRequired:1b}");
               cmd.performPrefixedCommand(src, "summon pig " + (bx + 0.5) + " " + (by + 1) + " " + (bz + 6.5) + " {NoAI:1b,PersistenceRequired:1b}");
            });
         });
         step.accept("sk0b@40", mc -> mc.gui.getChat().clearMessages(false));
         step.accept("sk0c@5", mc -> { mc.player.setYRot(0f); mc.player.setXRot(-35f); });
         step.accept("sk1@10", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_aurora.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sv0a@3", mc -> { ModuleManager.CUSTOM_SKY.preset.set("Planets"); mc.player.setYRot(0f); mc.player.setXRot(-18f); });
         step.accept("sv0b@14", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_planets.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sv1a@3", mc -> { ModuleManager.CUSTOM_SKY.preset.set("Snowy"); mc.player.setYRot(0f); mc.player.setXRot(-18f); });
         step.accept("sv1b@14", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_snowy.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sv2a@3", mc -> { ModuleManager.CUSTOM_SKY.preset.set("Moon"); mc.player.setYRot(0f); mc.player.setXRot(-18f); });
         step.accept("sv2b@14", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_moon.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sv3a@3", mc -> { ModuleManager.CUSTOM_SKY.preset.set("Aurora"); mc.player.setYRot(0f); mc.player.setXRot(-18f); });
         step.accept("sv3b@14", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_aurora.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sv4a@3", mc -> { ModuleManager.CUSTOM_SKY.preset.set("Sunset"); mc.player.setYRot(0f); mc.player.setXRot(-18f); });
         step.accept("sv4b@14", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_sunset.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sv5a@3", mc -> { ModuleManager.CUSTOM_SKY.preset.set("Nebula"); mc.player.setYRot(0f); mc.player.setXRot(-18f); });
         step.accept("sv5b@14", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_nebula.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sv6a@3", mc -> { ModuleManager.CUSTOM_SKY.preset.set("Galaxy"); mc.player.setYRot(0f); mc.player.setXRot(-18f); });
         step.accept("sv6b@14", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_galaxy.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sv7a@3", mc -> { ModuleManager.CUSTOM_SKY.preset.set("Storm"); mc.player.setYRot(0f); mc.player.setXRot(-18f); });
         step.accept("sv7b@14", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_storm.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sk8@3", mc -> {
            ModuleManager.CUSTOM_SKY.preset.set("Sunset");
            ModuleManager.ENTITY_ESP.style.set("Shader");
            ModuleManager.ENTITY_ESP.animals.set(true);
            ModuleManager.ENTITY_ESP.setEnabled(true);
            mc.player.setXRot(20f);
         });
         step.accept("sk8b@10", mc -> { mc.player.setYRot(10f); mc.player.setXRot(28f); mc.player.getInventory().setSelectedSlot(8); });
         step.accept("sk9@3", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "chams.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sk9b@3", mc -> {
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "item replace entity @p armor.chest with diamond_chestplate");
               cmd.performPrefixedCommand(src, "item replace entity @p armor.head with diamond_helmet");
               cmd.performPrefixedCommand(src, "item replace entity @p armor.legs with diamond_leggings");
            });
            mc.player.setYRot(180f);
            mc.player.setXRot(20f);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
         });
         step.accept("sk9c@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "esp_self.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("sk9d@3", mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
         step.accept("ska@5", mc -> {
            ModuleManager.ENTITY_ESP.animals.set(false);
            ModuleManager.ENTITY_ESP.setEnabled(false);
            ModuleManager.CUSTOM_SKY.setEnabled(false);
         });
         return;
      }
      if ("glassfx".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         final dev.quiqzy.client.gui.AuroraGui[] gg = new dev.quiqzy.client.gui.AuroraGui[1];
         step.accept("gx0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.ACCENT.hudStyle.set("Aurora");
            ModuleManager.ACCENT.preset.set("Glass");
            ModuleManager.HAND_SHADER.setEnabled(true);
            ModuleManager.HAND_SHADER.glow.set(true);
            ModuleManager.KILL_AURA.setEnabled(false);
            ModuleManager.KILL_AURA.setKeybind(82);
            ModuleManager.ZOOM.setKeybind(67);
            ModuleManager.GLOSSY_FLOOR.setEnabled(true);
            mc.player.setXRot(12f);
            mc.player.setYRot(0f);
            IntegratedServer server = mc.getSingleplayerServer();
            int bx = mc.player.getBlockX() + 80, by = 200, bz = mc.player.getBlockZ() + 80;
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "fill " + (bx - 10) + " " + by + " " + (bz - 10) + " " + (bx + 10) + " " + by + " " + (bz + 10) + " smooth_quartz");
               cmd.performPrefixedCommand(src, "fill " + (bx - 10) + " " + (by + 1) + " " + (bz - 10) + " " + (bx + 10) + " " + (by + 8) + " " + (bz + 10) + " air");
               cmd.performPrefixedCommand(src, "fill " + (bx - 4) + " " + (by + 1) + " " + (bz + 7) + " " + (bx - 3) + " " + (by + 6) + " " + (bz + 8) + " sea_lantern");
               cmd.performPrefixedCommand(src, "fill " + (bx + 3) + " " + (by + 1) + " " + (bz + 7) + " " + (bx + 4) + " " + (by + 6) + " " + (bz + 8) + " purple_concrete");
               server.getPlayerList().getPlayers().get(0).teleportTo(bx + 0.5, by + 1, bz + 0.5);
               cmd.performPrefixedCommand(src, "item replace entity @p weapon.mainhand with diamond_sword");
               cmd.performPrefixedCommand(src, "effect give @p strength 200 1");
               cmd.performPrefixedCommand(src, "effect give @p speed 90 0");
               cmd.performPrefixedCommand(src, "time set 1000");
            });
         });
         step.accept("gx0b@40", mc -> mc.gui.getChat().clearMessages(false));
         step.accept("gx1@10", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "gx_hand.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gx2@5", mc -> {
            ModuleManager.GLOW_WINGS.setEnabled(true);
            ModuleManager.CLEAN_F5.setEnabled(true);
            mc.player.setYRot(0f);
            mc.player.setXRot(18f);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
         });
         step.accept("gx3@45", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "gx_f5.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gx4@5", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            gg[0] = new dev.quiqzy.client.gui.AuroraGui();
            mc.setScreen(gg[0]);
            gg[0].testTab(dev.quiqzy.client.module.Module.Category.VISUALS.ordinal());
         });
         step.accept("gx5@30", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "gx_gui.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gx6@5", mc -> gg[0].testDetail(ModuleManager.GLOW_WINGS));
         step.accept("gx7@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "gx_detail.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gx7b@3", mc -> { gg[0].testDetail(ModuleManager.BLOCK_ESP); gg[0].testEdit(ModuleManager.BLOCK_ESP.blocks); gg[0].testListQuery("ore"); });
         step.accept("gx7c@20", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "gx_edit_blocks.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gx7d@3", mc -> { gg[0].testDetail(ModuleManager.TPA_ALL); gg[0].testEdit(ModuleManager.TPA_ALL.exclude); });
         step.accept("gx7e@20", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "gx_edit_names.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gx7f@3", mc -> { gg[0].testEdit(null); gg[0].testDetail(null); });
         step.accept("gx8@5", mc -> gg[0].testTab(-1));
         step.accept("gx9@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "gx_themes.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("gxa@5", mc -> {
            mc.setScreen(null);
            ModuleManager.KILL_AURA.setKeybind(-1);
            ModuleManager.ZOOM.setKeybind(-1);
            ModuleManager.GLOW_WINGS.setEnabled(false);
            ModuleManager.CLEAN_F5.setEnabled(false);
            ModuleManager.GLOSSY_FLOOR.setEnabled(false);
         });
         return;
      }
      if ("ring".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("rg0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.ACCENT.hudStyle.set("Aurora");
            ModuleManager.ACCENT.preset.set("Rose");
            ModuleManager.TARGET_MARKER.mobs.set(true);
            ModuleManager.TARGET_MARKER.style.set("Ring");
            ModuleManager.TARGET_MARKER.setEnabled(true);
            ModuleManager.KILL_AURA.setEnabled(false);
            mc.player.setXRot(10f);
            IntegratedServer server = mc.getSingleplayerServer();
            int bx = mc.player.getBlockX() + 60, by = 200, bz = mc.player.getBlockZ() + 60;
            float yaw = mc.player.getYRot();
            Vec3 at = new Vec3(bx + 0.5 - Math.sin(Math.toRadians(yaw)) * 4, by + 1, bz + 0.5 + Math.cos(Math.toRadians(yaw)) * 4);
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "fill " + (bx - 8) + " " + by + " " + (bz - 8) + " " + (bx + 8) + " " + by + " " + (bz + 8) + " smooth_quartz");
               cmd.performPrefixedCommand(src, "fill " + (bx - 8) + " " + (by + 1) + " " + (bz - 8) + " " + (bx + 8) + " " + (by + 6) + " " + (bz + 8) + " air");
               server.getPlayerList().getPlayers().get(0).teleportTo(bx + 0.5, by + 1, bz + 0.5);
               cmd.performPrefixedCommand(src, "time set 1000");
               cmd.performPrefixedCommand(src, "kill @e[type=pig]");
               cmd.performPrefixedCommand(src, String.format(java.util.Locale.ROOT, "summon pig %.2f %.2f %.2f {NoAI:1b,PersistenceRequired:1b}", at.x, at.y, at.z));
            });
         });
         step.accept("rg1@40", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "ring_rose.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("rg2@15", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "ring_rose2.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("rg3@5", mc -> { ModuleManager.ACCENT.preset.set("Violet"); mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getCommands().performPrefixedCommand(mc.getSingleplayerServer().createCommandSourceStack(), "kill @e[type=pig]")); });
         return;
      }
      if ("agui".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         final dev.quiqzy.client.gui.AuroraGui[] ag = new dev.quiqzy.client.gui.AuroraGui[1];
         java.util.function.Consumer<String> shot = n -> net.minecraft.client.Screenshot.grab(new java.io.File(Minecraft.getInstance().gameDirectory.getPath()), n, Minecraft.getInstance().getMainRenderTarget(), 1, msg -> {});
         step.accept("ag0@25", mc -> {
            ModuleManager.ACCENT.hudStyle.set("Aurora");
            ModuleManager.ACCENT.preset.set("Violet");
            ModuleManager.KILL_AURA.setKeybind(82);
            ModuleManager.KILL_AURA.setEnabled(true);
            ModuleManager.AUTO_TOTEM.setEnabled(true);
            ag[0] = new dev.quiqzy.client.gui.AuroraGui();
            mc.setScreen(ag[0]);
            ag[0].testTab(0);
         });
         step.accept("ag1@30", mc -> shot.accept("agui_grid.png"));
         step.accept("ag2@5", mc -> ag[0].testDetail(ModuleManager.KILL_AURA));
         step.accept("ag3@30", mc -> shot.accept("agui_detail.png"));
         step.accept("ag4@5", mc -> { ag[0].testDetail(null); ag[0].testTab(-1); });
         step.accept("ag5@40", mc -> shot.accept("agui_themes.png"));
         step.accept("ag6@5", mc -> { ModuleManager.ACCENT.preset.set("Rose"); ag[0].testSearch("auto"); });
         step.accept("ag7@30", mc -> shot.accept("agui_search_rose.png"));
         step.accept("ag8@5", mc -> { ModuleManager.ACCENT.preset.set("Violet"); ModuleManager.KILL_AURA.setEnabled(false); ModuleManager.KILL_AURA.setKeybind(-1); ag[0].testSearch(""); mc.setScreen(null); });
         return;
      }
      if ("dock".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         final dev.quiqzy.client.gui.DockGui[] dg = new dev.quiqzy.client.gui.DockGui[1];
         step.accept("dk0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.CLICK_GUI.style.set("Dock");
            ModuleManager.CLICK_GUI.bgBlur.set(false);
            ModuleManager.CUSTOM_SKY.setEnabled(false);
            ModuleManager.BACKGROUND_BLUR.setEnabled(true);
            mc.player.setXRot(5f);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 1000"));
         });
         step.accept("dk1@30", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "dof_on.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("dk2@5", mc -> ModuleManager.BACKGROUND_BLUR.setEnabled(false));
         step.accept("dk3@20", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "dof_off.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("dk4@5", mc -> { dg[0] = new dev.quiqzy.client.gui.DockGui(); dg[0].testCategory(dev.quiqzy.client.module.Module.Category.VISUALS); mc.setScreen(dg[0]); });
         step.accept("dk5@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "dock_main.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("dk6@5", mc -> { dg[0].testOpen(ModuleManager.BLOCK_ESP); dg[0].testPopup(ModuleManager.BLOCK_ESP.colorMode); });
         step.accept("dk7@20", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "dock_settings.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("dk8@5", mc -> mc.setScreen(null));
         return;
      }
      if ("skyshaders".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("ss0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.CUSTOM_SKY.setEnabled(true);
            mc.player.setYRot(0f);
            mc.player.setXRot(-35f);
            mc.options.hideGui = true;
            IntegratedServer server = mc.getSingleplayerServer();
            int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "time set 6000");
               cmd.performPrefixedCommand(src, "fill " + (px - 2) + " " + py + " " + (pz + 6) + " " + (px + 6) + " " + (py + 14) + " " + (pz + 6) + " stone");
            });
         });
         String[] st = dev.quiqzy.client.module.visuals.CustomSkyModule.PRESETS;
         for (int i = 0; i < st.length; i++) {
            final String name = st[i];
            step.accept("ss" + i + "a@5", mc -> { ModuleManager.CUSTOM_SKY.preset.set(name); mc.player.setXRot(-35f); });
            step.accept("ss" + i + "b@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "sky_" + name.replace(' ', '_') + ".png", mc.getMainRenderTarget(), 1, msg -> {}));
         }
         step.accept("ssz@5", mc -> mc.options.hideGui = false);
         return;
      }
      if ("panels".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         final dev.quiqzy.client.gui.PanelGui[] pg = new dev.quiqzy.client.gui.PanelGui[1];
         step.accept("pg0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.ACCENT.preset.set("Glass");
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.FULLBRIGHT, ModuleManager.STORAGE_ESP, ModuleManager.FREECAM, ModuleManager.HUD, ModuleManager.AUTO_TOTEM})
               m.setEnabled(true);
            ModuleManager.FREECAM.setEnabled(false);
            ModuleManager.ZOOM.setKeybind(67);
            ModuleManager.CLICK_GUI.bgBlur.set(false);
            ModuleManager.CLICK_GUI.bgDim.set(10);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "time set 1000");
            });
         });
         step.accept("pg1@30", mc -> { pg[0] = new dev.quiqzy.client.gui.PanelGui(); mc.setScreen(pg[0]); });
         step.accept("pg2@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "panels_main.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("pg3@5", mc -> { pg[0].testExpand(ModuleManager.BLOCK_ESP); pg[0].testExpand(ModuleManager.CLICK_GUI); pg[0].testPopup(ModuleManager.BLOCK_ESP.colorMode); });
         step.accept("pg4@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "panels_expand.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("pg5@5", mc -> pg[0].testSearch("esp"));
         step.accept("pg6@20", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "panels_search.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("pg7@5", mc -> mc.setScreen(null));
         return;
      }
      if ("aurora".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("au0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.ACCENT.hudStyle.set("Aurora");
            ModuleManager.ACCENT.preset.set("Violet");
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.HUD, ModuleManager.POTION_HUD, ModuleManager.SPOTIFY, ModuleManager.KEYBINDS_HUD, ModuleManager.SPEED_HUD, ModuleManager.HEALTH_BARS, ModuleManager.NOTIFICATIONS})
               m.setEnabled(true);
            ModuleManager.KILL_AURA.setKeybind(82);
            ModuleManager.TRIGGER_BOT.setKeybind(71);
            ModuleManager.FREECAM.setKeybind(86);
            ModuleManager.ZOOM.setKeybind(67);
            dev.quiqzy.client.util.MediaInfo.injectTest("Midnight Drive", "ZERO Sessions", 61000, 184000);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
               var cmd = server.getCommands(); var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "gamemode survival @a");
               cmd.performPrefixedCommand(src, "effect give @a strength 300 1");
               cmd.performPrefixedCommand(src, "effect give @a speed 90 1");
               cmd.performPrefixedCommand(src, "effect give @a fire_resistance 600 0");
               cmd.performPrefixedCommand(src, "effect give @a absorption 120 1");
               cmd.performPrefixedCommand(src, "time set 18000");
            });
         });
         step.accept("au1@40", mc -> { ModuleManager.KILL_AURA.setEnabled(true); ModuleManager.FREECAM.setKeybind(86); });
         step.accept("au2@6", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "aurora_violet.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("au3@10", mc -> { ModuleManager.KILL_AURA.setEnabled(false); ModuleManager.ACCENT.preset.set("Ember"); });
         step.accept("au4@6", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "aurora_ember.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("au5@10", mc -> { ModuleManager.ACCENT.preset.set("Aqua"); });
         step.accept("au6@6", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "aurora_aqua.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("au7@5", mc -> { ModuleManager.ACCENT.preset.set("Violet"); ModuleManager.KILL_AURA.setKeybind(-1); ModuleManager.TRIGGER_BOT.setKeybind(-1); ModuleManager.ZOOM.setKeybind(-1); });
         return;
      }
      if ("tpaconfirm".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("tc0@25", mc -> {
            mc.setScreen(null);
            IntegratedServer server = mc.getSingleplayerServer();
            var me = mc.player.blockPosition();
            var cp = me.offset(2, 0, 0);
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               lvl.setBlockAndUpdate(cp.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
               lvl.setBlockAndUpdate(cp, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
               if (lvl.getBlockEntity(cp) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity ce) {
                  for (int i = 0; i < 27; i++) ce.setItem(i, new ItemStack(Items.GRAY_STAINED_GLASS_PANE));
                  var no = new ItemStack(Items.RED_STAINED_GLASS);
                  no.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Cancel"));
                  var yes = new ItemStack(Items.LIME_STAINED_GLASS);
                  yes.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Confirm"));
                  ce.setItem(11, no);
                  ce.setItem(13, new ItemStack(Items.PLAYER_HEAD));
                  ce.setItem(15, yes);
               }
            });
            ModuleManager.TPA_CONFIRM.setEnabled(true);
         });
         step.accept("tc1@20", mc -> {
            var cp = mc.player.blockPosition().offset(2, 0, 0);
            ModuleManager.TPA_CONFIRM.onCommand("tpahere Steve");
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(cp), net.minecraft.core.Direction.UP, cp, false));
         });
         step.accept("tc2@30", mc -> {
            var carried = mc.player.containerMenu.getCarried();
            log("Confirm", "status=" + ModuleManager.TPA_CONFIRM.status + " carried=" + carried.getItem() + " " + carried.getHoverName().getString() + " screen=" + mc.screen);
            mc.player.closeContainer();
            ModuleManager.TPA_CONFIRM.setEnabled(false);
         });
         step.accept("tc3@5", mc -> {});
         return;
      }
      if ("tpa".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("tp0@25", mc -> {
            mc.setScreen(null);
            var t = ModuleManager.TPA_ALL;
            t.exclude.set(new java.util.ArrayList<>(java.util.List.of("Steve", "Notch")));
            t.setEnabled(true);
            log("TPA", "enabled=" + t.isEnabled() + " status=" + t.status);
         });
         step.accept("tp1@20", mc -> {
            dev.quiqzy.client.gui.ZeroGui gui = new dev.quiqzy.client.gui.ZeroGui();
            mc.setScreen(gui);
            gui.testShow(dev.quiqzy.client.module.Module.Category.MISC, false, ModuleManager.TPA_ALL);
            gui.testScroll(400f);
            gui.testName("Herobri");
         });
         step.accept("tp2@30", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "tpa_gui.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("tp3@10", mc -> mc.setScreen(null));
         return;
      }
      if ("bonemeal".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("bm0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.KILL_AURA.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return;
            var me = mc.player.blockPosition();
            int x = me.getX(), y = me.getY(), z = me.getZ();
            var bot = ModuleManager.BONE_MEAL_BOT;
            String dim = dev.quiqzy.client.module.world.WaypointsModule.dimensionId(mc.level);
            var spots = new java.util.ArrayList<dev.quiqzy.client.module.Setting.Waypoints.Point>();
            int[][] off = {{2, 0}, {-2, 0}, {0, 2}, {2, 2}};
            for (int[] o : off) spots.add(new dev.quiqzy.client.module.Setting.Waypoints.Point("S", x + o[0], y - 1, z + o[1], dim));
            bot.spots.set(spots);
            var ch = new java.util.ArrayList<dev.quiqzy.client.module.Setting.Waypoints.Point>();
            ch.add(new dev.quiqzy.client.module.Setting.Waypoints.Point("C", x, y + 1, z - 1, dim));
            bot.chest.set(ch);
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               var cmd = server.getCommands();
               var src = server.createCommandSourceStack();
               cmd.performPrefixedCommand(src, "gamemode survival @a");
               cmd.performPrefixedCommand(src, "time set day");
               cmd.performPrefixedCommand(src, "fill " + (x - 6) + " " + y + " " + (z - 6) + " " + (x + 6) + " " + (y + 20) + " " + (z + 6) + " air");
               cmd.performPrefixedCommand(src, "fill " + (x - 6) + " " + (y - 1) + " " + (z - 6) + " " + (x + 6) + " " + (y - 1) + " " + (z + 6) + " stone");
               for (int[] o : off) lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + o[0], y - 1, z + o[1]), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y + 1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
               var cp = new net.minecraft.core.BlockPos(x, y + 1, z - 1);
               lvl.setBlockAndUpdate(cp, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
               if (lvl.getBlockEntity(cp) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity ce) {
                  ce.setItem(0, new ItemStack(Items.BONE_MEAL, 64));
                  ce.setItem(1, new ItemStack(Items.BONE_MEAL, 64));
               }
               var sp = server.getPlayerList().getPlayers().get(0);
               sp.teleportTo(x + 0.5, y + 2, z + 0.5);
               sp.getInventory().clearContent();
               sp.getInventory().setItem(0, new ItemStack(Items.BIRCH_SAPLING, 16));
               sp.getInventory().setItem(20, new ItemStack(Items.NETHERITE_AXE));
            });
         });
         step.accept("bm1@20", mc -> {
            log("Start", "spots=" + ModuleManager.BONE_MEAL_BOT.spots.get().size());
            ModuleManager.BONE_MEAL_BOT.markMode.set(false);
            ModuleManager.BONE_MEAL_BOT.setEnabled(true);
         });
         for (int i = 0; i < 20; i++) {
            int k = i;
            step.accept("bmw" + i + "@40", mc -> {
               log("Bot", ModuleManager.BONE_MEAL_BOT.status);
               if (k == 6) net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "bonemeal_1.png", mc.getMainRenderTarget(), 1, msg -> {});
            });
         }
         step.accept("bm2@5", mc -> {
            int logsItems = 0, bm = 0;
            for (int i = 0; i < 36; i++) {
               var s = mc.player.getInventory().getItem(i);
               if (s.is(net.minecraft.tags.ItemTags.LOGS)) logsItems += s.getCount();
               if (s.is(Items.BONE_MEAL)) bm += s.getCount();
            }
            int left = 0;
            var c0 = mc.player.blockPosition();
            for (int dx = -6; dx <= 6; dx++) for (int dy = -3; dy <= 14; dy++) for (int dz = -6; dz <= 6; dz++)
               if (mc.level.getBlockState(c0.offset(dx, dy, dz)).is(net.minecraft.tags.BlockTags.LOGS)) left++;
            log("Logs noch in der Welt", left);
            log("Ergebnis", "logItems=" + logsItems + " boneMeal=" + bm + " status=" + ModuleManager.BONE_MEAL_BOT.status);
            net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "bonemeal_2.png", mc.getMainRenderTarget(), 1, msg -> {});
            ModuleManager.BONE_MEAL_BOT.setEnabled(false);
         });
         step.accept("bm3@5", mc -> {});
         return;
      }
      if ("storage".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("st0@25", mc -> {
            mc.setScreen(null);
            mc.player.setXRot(8f);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return;
            Vec3 at = ahead(mc, 9);
            int bx = (int) at.x, bz = (int) at.z, by = (int) mc.player.getY();
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               var B = net.minecraft.world.level.block.Blocks.class;
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx, by, bz), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + 2, by, bz + 1), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx - 2, by, bz + 2), net.minecraft.world.level.block.Blocks.SHULKER_BOX.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + 4, by + 1, bz + 3), net.minecraft.world.level.block.Blocks.SHULKER_BOX.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx - 3, by, bz + 5), net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + 1, by, bz + 6), net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + 3, by, bz + 8), net.minecraft.world.level.block.Blocks.ENDER_CHEST.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx - 1, by + 2, bz + 9), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            });
            ModuleManager.STORAGE_ESP.setEnabled(true);
         });
         step.accept("st1@30", mc -> {});
         step.accept("st2@6", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "storage_fp.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("st3@10", mc -> {
            ModuleManager.FREECAM.setEnabled(true);
            Vec3 at = ahead(mc, 6);
            lookCam(mc.player.getX() + 7, mc.player.getY() + 4, mc.player.getZ() - 2, at.x, at.y, at.z + 3);
         });
         step.accept("st4@6", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "storage_side.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("st5@5", mc -> {});
         return;
      }
      if ("krispy2".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("k0@30", mc -> {
            mc.setScreen(null);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
               Vec3 at = ahead(mc, 7); int bx = (int) at.x, bz = (int) at.z, by = (int) mc.player.getY();
               server.execute(() -> { var lvl = server.getLevel(Level.OVERWORLD);
                  lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx, by, bz), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                  lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + 20, by, bz + 3), net.minecraft.world.level.block.Blocks.SHULKER_BOX.defaultBlockState()); });
            }
            ModuleManager.SUS_CHUNK.setEnabled(true);
            ModuleManager.STORAGE_ESP.setEnabled(true);
            ModuleManager.REGION_MAP.setEnabled(true);
         });
         step.accept("k1@5", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "regionmap.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("k2@40", mc -> ModuleManager.CASE_OPENING.setEnabled(true));
         step.accept("k3@110", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "case_roll.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("k4@6", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "case_done.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("k5@4", mc -> mc.setScreen(null));
         return;
      }
      if ("karambit".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("kb0@25", mc -> {
            mc.setScreen(null);
            mc.player.setXRot(10f);
            mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), new ItemStack(Items.DIAMOND_SWORD));
            ModuleManager.WEAPON_SKINS.model.set("Karambit");
            ModuleManager.WEAPON_SKINS.setEnabled(true);
         });
         step.accept("kb1@20", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "karambit_hand.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("kb2@5", mc -> { var sv = mc.getSingleplayerServer(); if (sv != null) sv.execute(() -> sv.getCommands().performPrefixedCommand(sv.createCommandSourceStack(), "kill @e[type=pig]")); mc.player.setXRot(0f); mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), new ItemStack(Items.DIAMOND_SWORD)); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); });
         step.accept("kb3@20", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "karambit_3rd.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("kb3b@4", mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); ModuleManager.FREECAM.setEnabled(true);
            double yr = Math.toRadians(mc.player.getYRot()); double rx = -Math.cos(yr) * 2.4, rz = -Math.sin(yr) * 2.4;
            lookCam(mc.player.getX() + rx, mc.player.getY() + 1.3, mc.player.getZ() + rz, mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ()); });
         step.accept("kb3c@15", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "karambit_side.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("kb3d@3", mc -> ModuleManager.FREECAM.setEnabled(false));
         step.accept("kb4@5", mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); dev.quiqzy.client.gui.CaseOpeningScreen.debugWin = "Karambit"; mc.setScreen(new dev.quiqzy.client.gui.CaseOpeningScreen()); });
         step.accept("kb5@140", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "karambit_case.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("kb6@5", mc -> mc.setScreen(null));
         return;
      }
      if ("dropdown".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("dd0@20", mc -> { ModuleManager.GUI_WORLD.setEnabled(false); ModuleManager.FREECAM.setEnabled(false); var g = new dev.quiqzy.client.gui.ZeroGui(); mc.setScreen(g); });
         step.accept("dd0b@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "dd_open.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("dd0c@3", mc -> dev.quiqzy.client.QuiqzyClient.LOGGER.info("[Selbsttest] screen=" + mc.screen));
         step.accept("dd1@25", mc -> { if (mc.screen instanceof dev.quiqzy.client.gui.ZeroGui g) g.testDropdown(ModuleManager.WEAPON_SKINS, ModuleManager.WEAPON_SKINS.model); });
         step.accept("dd1b@3", mc -> dev.quiqzy.client.QuiqzyClient.LOGGER.info("[Selbsttest] screen2=" + mc.screen));
         step.accept("dd2@25", mc -> net.minecraft.client.Screenshot.grab(new java.io.File(mc.gameDirectory.getPath()), "dd_weapon.png", mc.getMainRenderTarget(), 1, msg -> {}));
         step.accept("dd5@5", mc -> mc.setScreen(null));
         return;
      }
      if ("traj".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("tj0@25", mc -> {
            mc.setScreen(null);
            mc.player.setXRot(-20f);
            mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), new ItemStack(Items.ENDER_PEARL, 16));
            ModuleManager.TRAJECTORIES.setEnabled(true);
         });
         step.accept("tj1@10", mc -> {});
         step.accept("tj2@5", mc -> ModuleManager.TRAJECTORIES.setEnabled(false));
         return;
      }
      if ("sounds".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("snd0@20", mc -> {
            mc.setScreen(null);
            ModuleManager.SUS_CHUNK.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return;
            var b = mc.player.blockPosition();
            Vec3 f = ahead(mc, 7);
            sndFx = (int) Math.floor(f.x);
            sndFy = b.getY();
            sndFz = (int) Math.floor(f.z);
            server.execute(() -> sndPlace(server, sndFx, sndFy, sndFz));
            ModuleManager.SOUND_CHUNK.setEnabled(true);
         });
         step.accept("snd1@3000", mc -> {
         });
         step.accept("snd2@10", mc -> {
            var m = ModuleManager.SOUND_CHUNK;
            int marked = m.marked().size();
            int sus = m.susChunks().size();
            String kinds = "";
            for (var d : m.marked()) kinds += d.reason() + " | ";
            log("sound marked chunks", marked);
            log("sound sus chunks", sus);
            log("sound kinds", kinds.isEmpty() ? "-" : kinds);
         });
         step.accept("snd3@5", mc -> {
         });
         return;
      }
      if ("flamecart".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("fca0@25", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.KILL_AURA, ModuleManager.NAMETAGS, ModuleManager.CART_AURA, ModuleManager.ANCHOR_AURA})
               m.setEnabled(false);
            mc.player.setXRot(0f);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            if (server == null) return;
            var b = mc.player.blockPosition();
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               for (var e : lvl.getAllEntities()) if (e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT || e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) e.discard();
               for (int x = -3; x <= 30; x++) for (int z = -16; z <= 16; z++) {
                  lvl.setBlockAndUpdate(b.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                  for (int y = 0; y < 6; y++) lvl.setBlockAndUpdate(b.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
               }
               var pl = server.getPlayerList().getPlayer(uuid);
               pl.getInventory().clearContent();
               pl.getInventory().setItem(0, new ItemStack(Items.RAIL, 64));
               pl.getInventory().setItem(1, new ItemStack(Items.TNT_MINECART, 16));
            });
            mc.player.getInventory().setItem(0, new ItemStack(Items.RAIL, 64));
            mc.player.getInventory().setItem(1, new ItemStack(Items.TNT_MINECART, 16));
            ModuleManager.FLAME_CART.railHotbar.set(1);
            ModuleManager.FLAME_CART.cartHotbar.set(2);
            ModuleManager.FLAME_CART.anyArrow.set(true);
            ModuleManager.FLAME_CART.refill.set(false);
            ModuleManager.FLAME_CART.hitIgnite.set(false);
            ModuleManager.FLAME_CART.setEnabled(true);
         });
         step.accept("fca1@10", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            float yaw = (float) Math.toRadians(mc.player.getYRot());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            Vec3 eye = mc.player.getEyePosition();
            if (server != null) server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               var pl = server.getPlayerList().getPlayer(uuid);
               var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(lvl, pl, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
               arrow.setPos(eye.x + fx, eye.y - 0.2, eye.z + fz);
               arrow.setDeltaMovement(fx * 0.55, -0.25, fz * 0.55);
               arrow.setOwner(pl);
               arrow.igniteForSeconds(5f);
               lvl.addFreshEntity(arrow);
               log("Pfeil", "gespawnt, onFire=" + arrow.isOnFire() + " v=" + arrow.getDeltaMovement());
            });
         });
         for (int k = 0; k < 16; k++) step.accept("fcw" + k + "@2", mc -> {
            int carts = 0, rails = 0;
            for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT) carts++;
            var b = mc.player.blockPosition();
            for (int x = -3; x <= 30; x++) for (int z = -16; z <= 16; z++) for (int y = -1; y <= 3; y++)
               if (mc.level.getBlockState(b.offset(x, y, z)).is(net.minecraft.tags.BlockTags.RAILS)) rails++;
            log("FlameCart", "rails=" + rails + " carts=" + carts + " inv rail/cart=" + mc.player.getInventory().getItem(0).getCount() + "/" + mc.player.getInventory().getItem(1).getCount());
         });
         step.accept("fct0@10", mc -> {
            mc.player.getInventory().setItem(3, new ItemStack(Items.TOTEM_OF_UNDYING));
            ModuleManager.FLAME_CART.totemSwap.set(true);
            mc.player.getInventory().setSelectedSlot(0);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            Vec3 at = ahead(mc, 2);
            if (server != null) server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule tntExplodes true");
               var cart = new net.minecraft.world.entity.vehicle.minecart.MinecartTNT(net.minecraft.world.entity.EntityType.TNT_MINECART, lvl);
               cart.setPos(at.x, mc.player.getY(), at.z);
               lvl.addFreshEntity(cart);
               cart.primeFuse(lvl.damageSources().generic());
            });
         });
         for (int k = 0; k < 5; k++) step.accept("fctw" + k + "@3", mc -> {
            String cs = "-";
            for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT c) cs = "primed=" + c.isPrimed() + " fuse=" + c.getFuse() + " dist=" + String.format("%.1f", dev.quiqzy.client.util.Targets.distTo(mc.player, c));
            log("TotemSwap", "slot=" + mc.player.getInventory().getSelectedSlot() + " totemInSlot4=" + mc.player.getInventory().getItem(3).getItem() + " cart[" + cs + "]");
         });
         step.accept("fca9@5", mc -> ModuleManager.FLAME_CART.setEnabled(false));
         return;
      }
      if ("cart".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("ca0@25", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.KILL_AURA, ModuleManager.NAMETAGS, ModuleManager.ANCHOR_AURA, ModuleManager.HIT_CRYSTAL, ModuleManager.TRIPLE_TAP})
               m.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            if (server == null) return;
            var b = mc.player.blockPosition();
            server.execute(() -> {
               server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule tntExplodes true");
               var lvl = server.getLevel(Level.OVERWORLD);
               for (var e : lvl.getAllEntities()) if (e instanceof net.minecraft.world.entity.animal.pig.Pig || e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT) e.discard();
               for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                  lvl.setBlockAndUpdate(b.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                  for (int y = 0; y < 4; y++) lvl.setBlockAndUpdate(b.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
               }
               var pl = server.getPlayerList().getPlayer(uuid);
               pl.getInventory().clearContent();
               pl.getInventory().setItem(0, new ItemStack(Items.RAIL, 64));
               pl.getInventory().setItem(1, new ItemStack(Items.TNT_MINECART, 16));
               pl.getInventory().setItem(2, new ItemStack(Items.FLINT_AND_STEEL));
               pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 4000, 4));
            });
            mc.player.getInventory().setItem(0, new ItemStack(Items.RAIL, 64));
            mc.player.getInventory().setItem(1, new ItemStack(Items.TNT_MINECART, 16));
            mc.player.getInventory().setItem(2, new ItemStack(Items.FLINT_AND_STEEL));
         });
         step.accept("ca1@15", mc -> spawnPig(mc, 20f));
         step.accept("ca1b@5", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
               for (var e : server.getLevel(Level.OVERWORLD).getAllEntities())
                  if (e instanceof net.minecraft.world.entity.animal.pig.Pig pg) pg.setHealth(20f);
            });
         });
         step.accept("ca2@10", mc -> {
            ModuleManager.CART_AURA.mobs.set(true);
            ModuleManager.CART_AURA.setEnabled(true);
         });
         for (int k = 0; k < 10; k++) step.accept("cw" + k + "@6", mc -> {
            int carts = 0, primed = 0;
            for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT c) { carts++; if (c.isPrimed()) primed++; }
            LivingEntity pig = pig(mc);
            log("Cart", "carts=" + carts + " primed=" + primed + " pig=" + (pig == null ? "tot" : pig.getHealth()) + " rails/cart im inv=" + mc.player.getInventory().getItem(0).getCount() + "/" + mc.player.getInventory().getItem(1).getCount());
         });
         step.accept("ca3@5", mc -> { ModuleManager.CART_AURA.setEnabled(false); ModuleManager.CART_AURA.mobs.set(false); });
         return;
      }
      if ("triple".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("tt0@25", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.ANCHOR_AURA, ModuleManager.HIT_CRYSTAL, ModuleManager.KILL_AURA, ModuleManager.NAMETAGS})
               m.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            if (server == null) return;
            var b = mc.player.blockPosition();
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               for (var e : lvl.getAllEntities()) {
                  if (e instanceof net.minecraft.world.entity.animal.pig.Pig || e instanceof net.minecraft.world.entity.item.ItemEntity
                        || e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) e.discard();
               }
               for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                  lvl.setBlockAndUpdate(b.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                  for (int y = 0; y < 5; y++) lvl.setBlockAndUpdate(b.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
               }
               var pl = server.getPlayerList().getPlayer(uuid);
               pl.getInventory().clearContent();
               pl.getInventory().setItem(0, new ItemStack(Items.NETHERITE_AXE));
               pl.getInventory().setItem(1, new ItemStack(Items.OBSIDIAN, 64));
               pl.getInventory().setItem(2, new ItemStack(Items.END_CRYSTAL, 64));
               pl.getInventory().setItem(3, new ItemStack(Items.TOTEM_OF_UNDYING));
               pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 2000, 4));
            });
            mc.player.getInventory().setSelectedSlot(0);
            mc.player.getInventory().setItem(0, new ItemStack(Items.NETHERITE_AXE));
            mc.player.getInventory().setItem(1, new ItemStack(Items.OBSIDIAN, 64));
            mc.player.getInventory().setItem(2, new ItemStack(Items.END_CRYSTAL, 64));
            mc.player.getInventory().setItem(3, new ItemStack(Items.TOTEM_OF_UNDYING));
         });
         step.accept("tt1@15", mc -> spawnPig(mc, 10f));
         step.accept("tt1b@5", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
               for (var e : server.getLevel(Level.OVERWORLD).getAllEntities()) {
                  if (e instanceof net.minecraft.world.entity.animal.pig.Pig pg) {
                     pg.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HEALTH_BOOST, 2000, 14));
                     pg.setHealth(pg.getMaxHealth());
                     pg.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 2000, 1));
                  }
               }
            });
         });
         step.accept("tt2@15", mc -> {
            ModuleManager.TRIPLE_TAP.mobs.set(true);
            ModuleManager.TRIPLE_TAP.setEnabled(true);
         });
         for (int k = 0; k < 12; k++) step.accept("ttw" + k + "@3", mc -> {
            LivingEntity p = pig(mc);
            if (p != null) log("Verlauf", "HP=" + p.getHealth() + " luft=" + ModuleManager.TRIPLE_TAP.airHits + " hurt=" + p.hurtTime + " boden=" + p.onGround() + " y=" + String.format("%.2f", p.getY()));
         });
         step.accept("tt4@20", mc -> {
            LivingEntity p = pig(mc);
            log("Triple Tap", "Schwein=" + (p == null ? "tot" : p.getHealth()) + " Kristalle verbraucht=" + (64 - mc.player.getInventory().getItem(2).getCount())
               + " Luft-Axtschläge=" + ModuleManager.TRIPLE_TAP.airHits + " Obsidian verbraucht=" + (64 - mc.player.getInventory().getItem(1).getCount()) + " aktiv=" + ModuleManager.TRIPLE_TAP.isEnabled());
            ModuleManager.TRIPLE_TAP.mobs.set(false);
         });
         return;
      }
      if ("tilt".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("ti0@20", mc -> {
            dev.quiqzy.client.gui.ZeroGui gui = new dev.quiqzy.client.gui.ZeroGui();
            mc.setScreen(gui);
            gui.testShow(dev.quiqzy.client.module.Module.Category.COMBAT, true, null);
            var w = mc.getWindow();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(w.handle(), w.getScreenWidth() * 0.12, w.getScreenHeight() * 0.12);
         });
         step.accept("ti1@25", mc -> {
            var w = mc.getWindow();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(w.handle(), w.getScreenWidth() * 0.88, w.getScreenHeight() * 0.88);
         });
         step.accept("ti2@25", mc -> {
            var w = mc.getWindow();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(w.handle(), w.getScreenWidth() * 0.5, w.getScreenHeight() * 0.5);
         });
         step.accept("ti3@25", mc -> mc.setScreen(null));
         return;
      }
      if ("anchor".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("an0@25", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.HOLE_ESP, ModuleManager.SPOTIFY, ModuleManager.KILL_AURA, ModuleManager.NAMETAGS, ModuleManager.ENTITY_ESP, ModuleManager.AUTO_DOUBLE_HAND})
               m.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            if (server == null) return;
            var b = mc.player.blockPosition();
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               var pl = server.getPlayerList().getPlayer(uuid);
               for (var e : lvl.getAllEntities()) {
                  if (e instanceof net.minecraft.world.entity.animal.pig.Pig || e instanceof net.minecraft.world.entity.item.ItemEntity
                        || e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) e.discard();
               }
               // flache Testfläche
               for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                  lvl.setBlockAndUpdate(b.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                  for (int y = 0; y < 4; y++) lvl.setBlockAndUpdate(b.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
               }
               pl.getInventory().clearContent();
               pl.getInventory().setItem(0, new ItemStack(Items.RESPAWN_ANCHOR, 16));
               pl.getInventory().setItem(1, new ItemStack(Items.GLOWSTONE, 64));
               pl.getInventory().setItem(2, new ItemStack(Items.TOTEM_OF_UNDYING));
               pl.getInventory().setItem(6, new ItemStack(Items.OBSIDIAN, 64));
               pl.getInventory().setItem(4, new ItemStack(Items.END_CRYSTAL, 64));
               pl.setHealth(20f);
               pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 2000, 4));
            });
         });
         step.accept("an1@15", mc -> {
            // Safe-Block-Position für einen Anchor 2 Blöcke östlich
            var anchor = mc.player.blockPosition().east(2);
            log("Schild-Position", dev.quiqzy.client.util.AnchorUtil.shieldPos(mc.player, anchor) + " (Anchor " + anchor + ", ich " + mc.player.blockPosition() + ")");
            spawnPig(mc, 10f);
         });
         step.accept("an2@10", mc -> {
            ModuleManager.ANCHOR_AURA.mobs.set(true);
            ModuleManager.ANCHOR_AURA.setEnabled(true);
            log("Ziele", dev.quiqzy.client.util.Targets.find(mc, 6, 360, true, true, true, false));
         });
         step.accept("an3@40", mc -> {
            var b = mc.player.blockPosition();
            int glow = 0, anchors = 0;
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = 0; y < 2; y++) {
               var st = mc.level.getBlockState(b.offset(x, y, z));
               if (st.is(net.minecraft.world.level.block.Blocks.GLOWSTONE)) glow++;
               if (st.is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR)) anchors++;
            }
            LivingEntity pig = pig(mc);
            log("Anchor Aura", "Schilde gesetzt=" + dev.quiqzy.client.util.AnchorUtil.shieldsPlaced + " Glowstone übrig=" + glow + " Anchors übrig=" + anchors + " Schwein=" + (pig == null ? "tot" : pig.getHealth()) + " meine HP=" + mc.player.getHealth());
            ModuleManager.ANCHOR_AURA.setEnabled(false);
            ModuleManager.ANCHOR_AURA.mobs.set(false);
         });
         step.accept("an4@10", mc -> {
            ModuleManager.HIT_CRYSTAL.setEnabled(true);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            if (server != null) server.execute(() -> {
               var pl = server.getPlayerList().getPlayer(uuid);
               pl.getInventory().setItem(6, new ItemStack(Items.OBSIDIAN, 64));
               pl.getInventory().setItem(4, new ItemStack(Items.END_CRYSTAL, 64));
            });
            mc.player.getInventory().setSelectedSlot(6);
            mc.player.getInventory().setItem(6, new ItemStack(Items.OBSIDIAN, 64));
            mc.player.getInventory().setItem(4, new ItemStack(Items.END_CRYSTAL, 64));
            float[] a = dev.quiqzy.client.util.Targets.angles(mc.player, Vec3.atCenterOf(mc.player.blockPosition().offset(0, -1, -2)).add(0, 0.5, 0));
            mc.player.setYRot(a[0]); mc.player.setXRot(a[1]);
         });
         step.accept("an5@4", mc -> {
            log("Blick", mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult h ? h.getBlockPos() + " " + mc.level.getBlockState(h.getBlockPos()) : String.valueOf(mc.hitResult));
            log("Ziel", mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult h ? h.getBlockPos() + " " + mc.level.getBlockState(h.getBlockPos()) : mc.hitResult);
            ModuleManager.HIT_CRYSTAL.testClick = true;
         });
         step.accept("an6@15", mc -> {
            var t = mc.player.blockPosition().offset(0, 0, -2);
            log("Hand vor Klick war", mc.player.getInventory().getItem(6));
            int crystals = 0;
            for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) crystals++;
            log("Hit Crystal", "Obsidian=" + mc.level.getBlockState(t).is(net.minecraft.world.level.block.Blocks.OBSIDIAN) + " Kristalle übrig=" + crystals + " Slot zurück=" + mc.player.getInventory().getSelectedSlot()
               + " Obsidian im Inventar=" + mc.player.getInventory().getItem(6).getCount() + " Kristalle im Inventar=" + mc.player.getInventory().getItem(4).getCount());
            ModuleManager.HIT_CRYSTAL.setEnabled(false);
         });
         return;
      }
      if ("cpvp".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("cp0@25", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.SPOTIFY, ModuleManager.SPAWNER_BOT, ModuleManager.KILL_AURA, ModuleManager.NAMETAGS, ModuleManager.ENTITY_ESP, ModuleManager.BLOCK_ESP})
               m.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            var b = mc.player.blockPosition();
            if (server == null) return;
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               var pl = server.getPlayerList().getPlayer(uuid);
               // Bedrock-Loch 3 Blöcke vor dir, Obsidian-Loch daneben
               var h1 = b.offset(3, 0, 0); var h2 = b.offset(3, 0, 3);
               for (var h : new net.minecraft.core.BlockPos[]{h1, h2}) {
                  var blk = h == h1 ? net.minecraft.world.level.block.Blocks.BEDROCK : net.minecraft.world.level.block.Blocks.OBSIDIAN;
                  for (int y = 0; y < 3; y++) lvl.setBlockAndUpdate(h.above(y), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                  lvl.setBlockAndUpdate(h.below(), blk.defaultBlockState());
                  for (var d : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST})
                     lvl.setBlockAndUpdate(h.relative(d), blk.defaultBlockState());
               }
               // Kristall auf Obsidian 2 Blöcke vor dir
               var cpos = b.offset(-2, 0, 0);
               lvl.setBlockAndUpdate(cpos.below(), net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState());
               lvl.setBlockAndUpdate(cpos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
               var cr = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(lvl, cpos.getX() + 0.5, cpos.getY(), cpos.getZ() + 0.5);
               cr.setShowBottom(false);
               lvl.addFreshEntity(cr);
               pl.getInventory().setItem(3, new ItemStack(Items.OBSIDIAN, 64));
               pl.getInventory().setItem(7, new ItemStack(Items.TOTEM_OF_UNDYING));
               pl.getInventory().setItem(20, new ItemStack(Items.TOTEM_OF_UNDYING));
               pl.getInventory().setItem(8, ItemStack.EMPTY);
            });
            ModuleManager.HOLE_ESP.setEnabled(true);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
         });
         step.accept("cp1@30", mc -> {
            log("Holes", ModuleManager.HOLE_ESP.holes);
            float[] a = dev.quiqzy.client.util.Targets.angles(mc.player, Vec3.atCenterOf(mc.player.blockPosition().offset(3, -1, 1)));
            mc.player.setYRot(a[0]); mc.player.setXRot(a[1]);
         });
         step.accept("cp2@10", mc -> {
            net.minecraft.world.entity.Entity cr = null;
            for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) cr = e;
            log("Kristall vorher", cr);
            if (cr != null) {
               mc.gameMode.attack(mc.player, cr);
               boolean still = false;
               for (var e : mc.level.entitiesForRendering()) if (e == cr) still = true;
               log("Kristall direkt nach Schlag noch da", still);
            }
         });
         step.accept("cp3@10", mc -> {
            ModuleManager.SURROUND.setEnabled(true);
            mc.player.setXRot(70f);
         });
         step.accept("cp4@20", mc -> {
            var f = mc.player.blockPosition();
            int n = 0;
            for (var t : new net.minecraft.core.BlockPos[]{f.north(), f.south(), f.east(), f.west()}) if (mc.level.getBlockState(t).is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) n++;
            log("Surround Obsidian", n + "/4 enabled=" + ModuleManager.SURROUND.isEnabled());
            ModuleManager.SURROUND.setEnabled(false);
            ModuleManager.AUTO_DOUBLE_HAND.setEnabled(true);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            if (server != null) server.execute(() -> server.getPlayerList().getPlayer(uuid).setHealth(6f));
         });
         step.accept("cp5@20", mc -> {
            log("Double Hand", "slot=" + mc.player.getInventory().getSelectedSlot() + " hand=" + mc.player.getMainHandItem() + " slot9=" + mc.player.getInventory().getItem(8));
            ModuleManager.AUTO_DOUBLE_HAND.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            if (server != null) server.execute(() -> server.getPlayerList().getPlayer(uuid).setHealth(20f));
         });
         return;
      }
      if ("spotify".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("sp0@20", mc -> {
            mc.setScreen(null);
            ModuleManager.SPAWNER_BOT.setEnabled(false);
            ModuleManager.SPOTIFY.spotifyOnly.set(false);
            ModuleManager.SPOTIFY.setEnabled(true);
         });
         step.accept("spl@60", mc -> {
            // Lyrics-Anzeige mit einem bekannten Song prüfen (ohne laufendes Spotify)
            dev.quiqzy.client.util.MediaInfo.injectTest("Blinding Lights", "The Weeknd", 30500, 200000);
         });
         step.accept("spa@100", mc -> {});
         step.accept("spb@12", mc -> {});
         step.accept("sp1@12", mc -> log("Track", dev.quiqzy.client.util.MediaInfo.track + " lyrics=" + dev.quiqzy.client.util.MediaInfo.lyrics.size() + " " + dev.quiqzy.client.util.MediaInfo.lyricsState));
         step.accept("sp2@5", mc -> {
            ModuleManager.SPOTIFY.setEnabled(false);
            ModuleManager.SPOTIFY.spotifyOnly.set(true);
         });
         return;
      }
      if ("automark".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("am0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.SPAWNER_BOT.spawners.set(new java.util.ArrayList<>());
            ModuleManager.SPAWNER_BOT.interval.set(60);
            IntegratedServer server = mc.getSingleplayerServer();
            var b = mc.player.blockPosition();
            if (server != null) server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               lvl.setBlockAndUpdate(b.offset(3, 1, 0), net.minecraft.world.level.block.Blocks.SPAWNER.defaultBlockState());
               lvl.setBlockAndUpdate(b.offset(-2, 2, 2), net.minecraft.world.level.block.Blocks.SPAWNER.defaultBlockState());
               lvl.setBlockAndUpdate(b.offset(0, 3, -4), net.minecraft.world.level.block.Blocks.SPAWNER.defaultBlockState());
               lvl.setBlockAndUpdate(b.offset(12, 0, 0), net.minecraft.world.level.block.Blocks.SPAWNER.defaultBlockState());
            });
            ModuleManager.SPAWNER_BOT.setEnabled(true);
         });
         step.accept("am1@60", mc -> log("Markiert", ModuleManager.SPAWNER_BOT.spawners.get().size() + " " + ModuleManager.SPAWNER_BOT.spawners.get()));
         step.accept("am2@5", mc -> ModuleManager.SPAWNER_BOT.setEnabled(false));
         return;
      }
      if ("fcull".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("fc0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.ENTITY_ESP.setEnabled(false);
            ModuleManager.FREECAM.setEnabled(true);
            ModuleManager.FREECAM.y -= 14;
            ModuleManager.FREECAM.pitch = 0f;
            log("smartCull", mc.smartCull);
         });
         step.accept("fc1@40", mc -> log("smartCull", mc.smartCull));
         step.accept("fc2@5", mc -> {
            ModuleManager.FREECAM.setEnabled(false);
            log("smartCull nach Aus", mc.smartCull);
         });
         return;
      }
      if ("tracer".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("tr0@25", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.TRACERS, ModuleManager.NAMETAGS, ModuleManager.VISUAL_FX, ModuleManager.SPAWNER_BOT})
               m.setEnabled(false);
            ModuleManager.ENTITY_ESP.setEnabled(true);
            ModuleManager.ENTITY_ESP.animals.set(true);
            ModuleManager.ENTITY_ESP.tracers.set(true);
            mc.player.setXRot(10f);
            mc.player.setYRot(mc.player.getYRot() + 120f);
         });
         step.accept("tr1@20", mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
         step.accept("tr2@20", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            ModuleManager.FREECAM.setEnabled(true);
            ModuleManager.FREECAM.y += 6;
            ModuleManager.FREECAM.x += 4;
            ModuleManager.FREECAM.pitch = 50f;
         });
         step.accept("tr3@5", mc -> {
            ModuleManager.FREECAM.setEnabled(false);
            ModuleManager.ENTITY_ESP.tracers.set(false);
         });
         return;
      }
      if ("deposit".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("d0@30", mc -> {
            mc.setScreen(null);
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            var cp = mc.player.blockPosition().above(2);
            Edit2.surf = cp.getY();
            if (server != null) server.execute(() -> {
               var pl = server.getPlayerList().getPlayer(uuid);
               for (int i = 9; i < 30; i++) pl.getInventory().setItem(i, new ItemStack(Items.BONE, 64));
               server.getLevel(Level.OVERWORLD).setBlockAndUpdate(cp, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
               var be = server.getLevel(Level.OVERWORLD).getBlockEntity(cp);
               if (be instanceof net.minecraft.world.Container c) c.clearContent();
            });
            ModuleManager.SPAWNER_BOT.setEnabled(false);
         });
         step.accept("d1@20", mc -> {
            mc.setScreen(null);
            var cp = mc.player.blockPosition().above(2);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(cp), net.minecraft.core.Direction.UP, cp, false));
         });
         step.accept("d2@10", mc -> {
            log("Menü", mc.screen);
            ModuleManager.SPAWNER_BOT.testDeposit();
         });
         for (int i = 0; i < 12; i++) step.accept("dd" + i + "@8", mc -> log("Dbg", ModuleManager.SPAWNER_BOT.debug() + " screen=" + (mc.screen == null ? "-" : mc.screen.getClass().getSimpleName()) + " menu=" + mc.player.containerMenu.containerId));
         step.accept("d4@60", mc -> {
            int bones = 0;
            for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(Items.BONE)) bones += mc.player.getInventory().getItem(i).getCount();
            log("Bones noch im Inventar", bones + " / Screen " + mc.screen + " / " + ModuleManager.SPAWNER_BOT.debug());
            ModuleManager.SPAWNER_BOT.setEnabled(false);
         });
         return;
      }
      if ("bones".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("bn0@30", mc -> {
            mc.setScreen(null);
            ModuleManager.KILL_AURA.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            Vec3 me = mc.player.position();
            log("Start", me);
            if (server != null) server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               double[][] off = {{4, 1}, {-3, 3}, {2, -4}};
               for (double[] o : off) {
                  var ie = new net.minecraft.world.entity.item.ItemEntity(lvl, me.x + o[0], me.y + 0.5, me.z + o[1], new ItemStack(Items.BONE, 16));
                  ie.setDeltaMovement(0, 0, 0);
                  lvl.addFreshEntity(ie);
               }
            });
            ModuleManager.SPAWNER_BOT.spawners.set(new java.util.ArrayList<>());
            ModuleManager.SPAWNER_BOT.order.set(false);
         });
         step.accept("bn1@20", mc -> ModuleManager.SPAWNER_BOT.runNow());
         for (int i = 0; i < 6; i++) step.accept("bnw" + i + "@40", mc -> log("Bot", ModuleManager.SPAWNER_BOT.status + " pos=" + mc.player.position()));
         step.accept("bn2@5", mc -> {
            int bones = 0;
            for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(Items.BONE)) bones += mc.player.getInventory().getItem(i).getCount();
            log("Bones im Inventar", bones);
            log("Ende", mc.player.position());
            ModuleManager.SPAWNER_BOT.order.set(true);
            ModuleManager.SPAWNER_BOT.setEnabled(false);
         });
         return;
      }
      if ("combat".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("c0@25", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.GLASS, ModuleManager.RAIN_FX, ModuleManager.CYBERSPACE, ModuleManager.XRAY})
               m.setEnabled(false);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), new ItemStack(Items.DIAMOND_SWORD));
            IntegratedServer server = mc.getSingleplayerServer();
            var uuid = mc.player.getUUID();
            Vec3 at = ahead(mc, 3);
            var sp = new net.minecraft.core.BlockPos((int) Math.floor(at.x), (int) mc.player.getY(), (int) Math.floor(at.z)).relative(net.minecraft.core.Direction.UP, 0);
            if (server != null) server.execute(() -> {
               var pl = server.getPlayerList().getPlayer(uuid);
               if (pl != null) {
                  pl.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                  pl.getInventory().setItem(20, new ItemStack(Items.DIAMOND_HELMET));
               }
               server.getLevel(Level.OVERWORLD).setBlockAndUpdate(sp.east(2), net.minecraft.world.level.block.Blocks.SPAWNER.defaultBlockState());
            });
            ModuleManager.AUTO_ARMOR.setEnabled(true);
            spawnPig(mc, 10f);
         });
         step.accept("c1@10", mc -> {
            ModuleManager.KILL_AURA.mobs.set(true);
            ModuleManager.KILL_AURA.animals.set(true);
            ModuleManager.KILL_AURA.setEnabled(true);
         });
         step.accept("c2@40", mc -> {
            log("KillAura-Ziel", ModuleManager.KILL_AURA.target);
            if (ModuleManager.KILL_AURA.target != null) log("KA dist/charge/hand", dev.quiqzy.client.util.Targets.distTo(mc.player, ModuleManager.KILL_AURA.target) + " / " + mc.player.getAttackStrengthScale(0.5f) + " / " + mc.player.getMainHandItem());
            LivingEntity p = pig(mc);
            log("Schwein", p == null ? "tot/weg" : p.getHealth());
            log("Helm", mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
            ModuleManager.KILL_AURA.setEnabled(false);
            dev.quiqzy.client.gui.ZeroGui gui = new dev.quiqzy.client.gui.ZeroGui();
            mc.setScreen(gui);
            gui.testShow(dev.quiqzy.client.module.Module.Category.COMBAT, true, null);
         });
         step.accept("c3@10", mc -> {
            mc.setScreen(null);
            Vec3 at = ahead(mc, 3);
            var sp = new net.minecraft.core.BlockPos((int) Math.floor(at.x), (int) mc.player.getY(), (int) Math.floor(at.z)).east(2);
            float[] a = dev.quiqzy.client.util.Targets.angles(mc.player, Vec3.atCenterOf(sp));
            mc.player.setYRot(a[0]);
            mc.player.setXRot(a[1]);
         });
         step.accept("c4@5", mc -> {
            ModuleManager.SPAWNER_BOT.spawners.set(new java.util.ArrayList<>());
            ModuleManager.SPAWNER_BOT.markLooked(mc);
            log("Markiert", ModuleManager.SPAWNER_BOT.spawners.get());
            ModuleManager.SPAWNER_BOT.runNow();
            log("Intervall vor GUI", ModuleManager.SPAWNER_BOT.interval.get());
            log("Bot nach runNow", ModuleManager.SPAWNER_BOT.debug() + " enabled=" + ModuleManager.SPAWNER_BOT.isEnabled());
         });
         for (int i = 0; i < 8; i++) step.accept("cb" + i + "@25", mc -> log("Bot", ModuleManager.SPAWNER_BOT.debug() + " screen=" + mc.screen));
         step.accept("c5@5", mc -> {
            log("SpawnerBot-Status", ModuleManager.SPAWNER_BOT.status);
            dev.quiqzy.client.gui.ZeroGui gui = new dev.quiqzy.client.gui.ZeroGui();
            mc.setScreen(gui);
            gui.testShow(dev.quiqzy.client.module.Module.Category.WORLD, false, ModuleManager.SPAWNER_BOT);
         });
         step.accept("c6@10", mc -> {
            log("Intervall nach GUI", ModuleManager.SPAWNER_BOT.interval.get());
            mc.setScreen(null);
         });
         return;
      }
      if ("rain".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("q0@40", mc -> {
            mc.setScreen(null);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            ModuleManager.GLASS.setEnabled(false);
            ModuleManager.CYBERSPACE.setEnabled(false);
            ModuleManager.RAIN_FX.setEnabled(true);
            ModuleManager.WEAPON_SKINS.setEnabled(true);
            ModuleManager.WEAPON_SKINS.model.set("Bayonet");
            mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), new ItemStack(Items.DIAMOND_SWORD));
            mc.player.setXRot(20f);
         });
         step.accept("q1@3", mc -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
         for (String k : new String[]{"Flip", "Gut", "Bowie"}) {
            step.accept("q_" + k.toLowerCase() + "@30", mc -> {
               mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), new ItemStack(Items.DIAMOND_SWORD));
               mc.player.setXRot(20f);
               ModuleManager.WEAPON_SKINS.model.set(k);
               ModuleManager.RAIN_FX.style.set(k.equals("Gut") ? "Water" : k.equals("Bowie") ? "Neon" : "Embers");
            });
         }
         step.accept("q2@25", mc -> {
            ModuleManager.GLASS.setEnabled(true);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10f);
         });
         return;
      }
      if ("fxpack".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("k0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.AVATAR.setEnabled(false);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.NAMETAGS, ModuleManager.ESP_2D, ModuleManager.TARGET_HUD, ModuleManager.EMBERS})
               m.setEnabled(false);
            ModuleManager.GLASS.setEnabled(true);
            ModuleManager.GLASS.others.set(true);
            ModuleManager.XRAY.setEnabled(true);
            ModuleManager.XRAY.mobs.set(true);
            ModuleManager.XRAY.self.set(true);
            ModuleManager.VISUAL_FX.setEnabled(true);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(15f);
         });
         step.accept("k1@15", mc -> {
            ModuleManager.XRAY.setEnabled(false);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            mc.player.setXRot(35f);
            mc.player.jumpFromGround();
         });
         step.accept("k2@14", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.setXRot(40f);
            spawnPig(mc, 10f);
         });
         step.accept("k3@6", mc -> {
            LivingEntity p = pig(mc);
            if (p != null) mc.gameMode.attack(mc.player, p);
         });
         step.accept("k4@20", mc -> {
            ModuleManager.CYBERSPACE.setEnabled(true);
            mc.player.setXRot(-25f);
         });
         return;
      }
      if ("glass".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("g0@25", mc -> {
            mc.setScreen(null);
            ModuleManager.FREECAM.setEnabled(false);
            ModuleManager.AVATAR.setEnabled(true);
            ModuleManager.AVATAR.style.set("Glass");
            ModuleManager.AVATAR.glassColor.set("Ice");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10f);
         });
         step.accept("g1@15", mc -> ModuleManager.AVATAR.glassColor.set("Ember"));
         step.accept("g2@15", mc -> {
            ModuleManager.AVATAR.glassColor.set("Violet");
            ModuleManager.AVATAR.glassLevel.set("Medium");
         });
         step.accept("g3@15", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), net.minecraft.world.item.ItemStack.EMPTY);
         });
         return;
      }
      if ("edit2".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("x0@10", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.NAMETAGS, ModuleManager.ESP_2D, ModuleManager.VISUAL_FX,
                  ModuleManager.MODULE_LIST, ModuleManager.SESSION_STATS, ModuleManager.ITEM_COUNTER, ModuleManager.ARMOR_HUD,
                  ModuleManager.POTION_HUD, ModuleManager.TARGET_HUD, ModuleManager.SUS_CHUNK, ModuleManager.WAYPOINTS,
                  ModuleManager.KEYSTROKES, ModuleManager.CROSSHAIR, ModuleManager.HUD, ModuleManager.AMBIENCE, ModuleManager.EMBERS})
               m.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return;
            float yaw = (float) Math.toRadians(mc.player.getYRot());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            int py = (int) mc.player.getY();
            // große Base 75 Blöcke voraus, unterirdische Base 22 voraus
            Edit2.bigX = (int) (mc.player.getX() + fx * 75);
            Edit2.bigZ = (int) (mc.player.getZ() + fz * 75);
            Edit2.ugX = (int) (mc.player.getX() + fx * 22);
            Edit2.ugZ = (int) (mc.player.getZ() + fz * 22);
            Edit2.surf = py;
            server.execute(() -> Edit2.build(server.getLevel(Level.OVERWORLD)));
         });
         step.accept("x1@100", mc -> {
            ModuleManager.BASE_FINDER.setEnabled(true);
            ModuleManager.BASE_FINDER.radius.set(16);
            ModuleManager.BLOCK_ESP.setEnabled(true);
            ModuleManager.BLOCK_ESP.preset.set("Storage");
            ModuleManager.BLOCK_ESP.applyPreset();
            ModuleManager.BLOCK_ESP.radius.set(10);
            ModuleManager.STATUS_BAR.setEnabled(true);
            ModuleManager.WATERMARK.setEnabled(true);
            ModuleManager.FREECAM.hands.set(false);
            ModuleManager.FREECAM.speedHud.set(false);
            ModuleManager.FREECAM.setEnabled(true);
            mc.options.renderDistance().set(12);
         });
         step.accept("x2@640", mc -> Edit2.start());
         step.accept("x3@5", mc -> {
            ModuleManager.FREECAM.setEnabled(false);
            log("Edit2-Frames", Edit2.saved);
         });
         return;
      }
      if ("edit".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("e0@10", mc -> {
            mc.setScreen(null);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.NAMETAGS, ModuleManager.ESP_2D, ModuleManager.VISUAL_FX,
                  ModuleManager.MODULE_LIST, ModuleManager.SESSION_STATS, ModuleManager.ITEM_COUNTER, ModuleManager.ARMOR_HUD,
                  ModuleManager.POTION_HUD, ModuleManager.TARGET_HUD, ModuleManager.SUS_CHUNK, ModuleManager.WAYPOINTS,
                  ModuleManager.KEYSTROKES, ModuleManager.CROSSHAIR, ModuleManager.LOW_FIRE, ModuleManager.HUD, ModuleManager.AMBIENCE})
               m.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return;
            Vec3 at = ahead(mc, 34);
            EditRec.cx = Math.floor(at.x) + 0.5;
            EditRec.cz = Math.floor(at.z) + 0.5;
            EditRec.by = (int) mc.player.getY();
            int bx = (int) Math.floor(at.x) - 8, bz = (int) Math.floor(at.z) - 8, by = EditRec.by;
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               lvl.setDayTime(13200);
               var B = new Object() {
                  void set(int x, int y, int z, net.minecraft.world.level.block.Block b) {
                     lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), b.defaultBlockState());
                  }
               };
               var Bl = net.minecraft.world.level.block.Blocks.class;
               // aufräumen + Boden
               for (int x = -2; x < 19; x++) for (int z = -2; z < 19; z++) for (int y = 0; y < 14; y++)
                  B.set(bx + x, by + y, bz + z, net.minecraft.world.level.block.Blocks.AIR);
               for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) {
                  boolean edge = x == 0 || z == 0 || x == 16 || z == 16;
                  boolean ring = x == 2 || z == 2 || x == 14 || z == 14;
                  B.set(bx + x, by - 1, bz + z, edge ? net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN
                        : ring ? net.minecraft.world.level.block.Blocks.SEA_LANTERN
                        : ((x + z) % 2 == 0 ? net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE : net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES));
               }
               // Säulen + Glaswände
               for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) {
                  boolean corner = (x == 0 || x == 16) && (z == 0 || z == 16);
                  boolean wall = x == 0 || z == 0 || x == 16 || z == 16;
                  for (int y = 0; y < 7; y++) {
                     if (corner) B.set(bx + x, by + y, bz + z, net.minecraft.world.level.block.Blocks.QUARTZ_PILLAR);
                     else if (wall) B.set(bx + x, by + y, bz + z, y == 6 ? net.minecraft.world.level.block.Blocks.ORANGE_STAINED_GLASS : net.minecraft.world.level.block.Blocks.TINTED_GLASS);
                  }
               }
               // Lager: zwei Reihen Truhen, Reihe Shulker
               for (int i = 3; i < 14; i++) {
                  B.set(bx + i, by, bz + 4, net.minecraft.world.level.block.Blocks.CHEST);
                  B.set(bx + i, by, bz + 12, net.minecraft.world.level.block.Blocks.BARREL);
               }
               net.minecraft.world.level.block.Block[] sh = {net.minecraft.world.level.block.Blocks.ORANGE_SHULKER_BOX, net.minecraft.world.level.block.Blocks.PURPLE_SHULKER_BOX,
                     net.minecraft.world.level.block.Blocks.LIGHT_BLUE_SHULKER_BOX, net.minecraft.world.level.block.Blocks.MAGENTA_SHULKER_BOX, net.minecraft.world.level.block.Blocks.YELLOW_SHULKER_BOX};
               for (int i = 3; i < 14; i++) B.set(bx + i, by + 1, bz + 4, sh[i % sh.length]);
               // Beacon-Pyramide in der Mitte
               for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) B.set(bx + 8 + x, by, bz + 8 + z, net.minecraft.world.level.block.Blocks.IRON_BLOCK);
               B.set(bx + 8, by + 1, bz + 8, net.minecraft.world.level.block.Blocks.BEACON);
               // Enchant-Ecke
               B.set(bx + 4, by, bz + 8, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE);
               B.set(bx + 12, by, bz + 8, net.minecraft.world.level.block.Blocks.ANVIL);
               B.set(bx + 12, by, bz + 9, net.minecraft.world.level.block.Blocks.ENDER_CHEST);
               B.set(bx + 4, by, bz + 9, net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR);
               for (int z = 6; z <= 10; z++) {
                  B.set(bx + 2, by, bz + z, net.minecraft.world.level.block.Blocks.BOOKSHELF);
                  B.set(bx + 2, by + 1, bz + z, net.minecraft.world.level.block.Blocks.END_ROD);
               }
            });
         });
         step.accept("e1@80", mc -> {
            ModuleManager.BASE_FINDER.setEnabled(true);
            ModuleManager.BLOCK_ESP.setEnabled(true);
            ModuleManager.BLOCK_ESP.preset.set("Storage");
            ModuleManager.BLOCK_ESP.applyPreset();
            ModuleManager.EMBERS.setEnabled(true);
            ModuleManager.EMBERS.style.set("Embers");
            ModuleManager.STATUS_BAR.setEnabled(true);
            ModuleManager.WATERMARK.setEnabled(true);
            ModuleManager.RADAR.setEnabled(true);
            ModuleManager.FREECAM.hands.set(false);
            ModuleManager.FREECAM.speedHud.set(false);
            ModuleManager.FREECAM.setEnabled(true);
         });
         step.accept("e2@250", mc -> EditRec.start(mc));
         step.accept("e3@5", mc -> {
            ModuleManager.FREECAM.setEnabled(false);
            log("Edit-Frames", EditRec.saved);
         });
         return;
      }
      if ("base".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("b0@20", mc -> {
            mc.setScreen(null);
            mc.player.setXRot(10f);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.NAMETAGS, ModuleManager.ESP_2D, ModuleManager.VISUAL_FX}) m.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return;
            Vec3 at = ahead(mc, 40);
            int bx = (int) at.x, bz = (int) at.z, by = (int) mc.player.getY();
            server.execute(() -> {
               var lvl = server.getLevel(Level.OVERWORLD);
               var B = net.minecraft.world.level.block.Blocks.class;
               net.minecraft.world.level.block.Block[] floor = {net.minecraft.world.level.block.Blocks.WHITE_CONCRETE};
               for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
                  lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + x, by - 1, bz + z), net.minecraft.world.level.block.Blocks.WHITE_CONCRETE.defaultBlockState());
                  if (x == 0 || z == 0 || x == 6 || z == 6) for (int y = 0; y < 3; y++)
                     lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + x, by + y, bz + z), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
               }
               var chest = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();
               for (int i = 1; i < 6; i++) lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + i, by, bz + 1), chest);
               for (int i = 1; i < 5; i++) lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + i, by, bz + 5), net.minecraft.world.level.block.Blocks.SHULKER_BOX.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + 3, by, bz + 3), net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + 5, by, bz + 3), net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState());
               lvl.setBlockAndUpdate(new net.minecraft.core.BlockPos(bx + 1, by, bz + 3), net.minecraft.world.level.block.Blocks.ENDER_CHEST.defaultBlockState());
            });
         });
         step.accept("b1@60", mc -> {
            ModuleManager.BASE_FINDER.setEnabled(true);
         });
         step.accept("b2@80", mc -> {
            log("Bases", ModuleManager.BASE_FINDER.bases());
            Vec3 at = ahead(mc, 40);
            var bp = new net.minecraft.core.BlockPos((int) at.x + 2, (int) mc.player.getY(), (int) at.z + 1);
            log("Block an Truhen-Stelle (Client)", mc.level.getBlockState(bp));
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> log("Block an Truhen-Stelle (Server)", server.getLevel(Level.OVERWORLD).getBlockState(bp)));
         });
         return;
      }
      if ("vfx".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("v0@30", mc -> {
            mc.setScreen(null);
            mc.player.setXRot(15f);
            ModuleManager.VISUAL_FX.setEnabled(true);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.NAMETAGS, ModuleManager.ESP_2D, ModuleManager.TARGET_HUD,
                  ModuleManager.HIT_EFFECTS, ModuleManager.WAYPOINTS, ModuleManager.EMBERS}) m.setEnabled(false);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
               server.execute(() -> {
                  var lvl = server.getLevel(Level.OVERWORLD);
                  var sb = server.getScoreboard();
                  var obj = sb.getObjective("zero_test");
                  if (obj != null) sb.removeObjective(obj);
                  for (var e : lvl.getAllEntities()) {
                     if (e instanceof net.minecraft.world.entity.animal.pig.Pig || e instanceof net.minecraft.world.entity.item.ItemEntity) e.discard();
                  }
               });
            }
         });
         step.accept("v0b@10", mc -> {
            spawnPig(mc, 10f);
         });
         step.accept("v1@12", mc -> {
            LivingEntity p = pig(mc);
            if (p != null) mc.gameMode.attack(mc.player, p);
         });
         step.accept("v2@4", mc -> mc.player.jumpFromGround());
         step.accept("v3@20", mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
         step.accept("v4@5", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            LivingEntity p = pig(mc);
            IntegratedServer server = mc.getSingleplayerServer();
            if (p != null && server != null) {
               int id = p.getId();
               server.execute(() -> {
                  var e = server.getLevel(Level.OVERWORLD).getEntity(id);
                  if (e instanceof LivingEntity le) le.setHealth(1f);
               });
            }
         });
         step.accept("v5@12", mc -> {
            LivingEntity p = pig(mc);
            if (p != null) mc.gameMode.attack(mc.player, p);
         });
         return;
      }
      if ("replace".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("r0@30", mc -> {
            mc.setScreen(null);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
               var sb = server.getScoreboard();
               var old = sb.getObjective("zero_test");
               if (old != null) sb.removeObjective(old);
               var obj = sb.addObjective("zero_test", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                     net.minecraft.network.chat.Component.literal("SkyBlock").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD),
                     net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, true, null);
               sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR, obj);
               String[] lines = {"§7Money: §a$1,200", "§7Kills: §c4", "§7Rank: §bMember", "§ewww.server.net"};
               for (int i = 0; i < lines.length; i++) {
                  sb.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(lines[i]), obj).set(lines.length - i);
               }
            });
         });
         step.accept("r1@20", mc -> {
            ModuleManager.TEXT_REPLACE.rules.set(new java.util.ArrayList<>());
            ModuleManager.TEXT_REPLACE.setEnabled(true);
            dev.quiqzy.client.gui.ZeroGui gui = new dev.quiqzy.client.gui.ZeroGui();
            mc.setScreen(gui);
            gui.testShow(dev.quiqzy.client.module.Module.Category.MISC, false, ModuleManager.TEXT_REPLACE);
         });
         step.accept("r2@15", mc -> {
            log("Screen vor Speichern", mc.screen);
            if (mc.screen instanceof dev.quiqzy.client.gui.ZeroGui g) g.testReplaceLine(0, "&7Money: &a$5,000,000", true);
            log("Screen nach Speichern", mc.screen);
         });
         step.accept("r3@15", mc -> {
            if (mc.screen instanceof dev.quiqzy.client.gui.ZeroGui g) g.testReplaceLine(2, "&7Rank: &6MVP&c++", false);
         });
         step.accept("r4@15", mc -> {
            if (mc.screen instanceof dev.quiqzy.client.gui.ZeroGui g) g.testReplaceLine(2, "&7Rank: &6MVP&c++", true);
            mc.setScreen(null);
         });
         return;
      }
      if ("improve".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("i0@30", mc -> {
            mc.setScreen(null);
            ModuleManager.NAMETAGS.setEnabled(true);
            ModuleManager.ESP_2D.setEnabled(true);
            ModuleManager.RADAR.setEnabled(true);
            ModuleManager.HUD.setEnabled(true);
         });
         step.accept("i1@15", mc -> {
            dev.quiqzy.client.gui.ZeroGui gui = new dev.quiqzy.client.gui.ZeroGui();
            mc.setScreen(gui);
            gui.testShow(dev.quiqzy.client.module.Module.Category.HUD, true, null);
         });
         step.accept("i2@15", mc -> {
            if (mc.screen instanceof dev.quiqzy.client.gui.ZeroGui g) {
               g.testShow(dev.quiqzy.client.module.Module.Category.VISUALS, false, ModuleManager.CROSSHAIR);
               g.testColorSetting(ModuleManager.CROSSHAIR.color);
            }
         });
         step.accept("i2b@3", mc -> log("Farbfenster", mc.screen instanceof dev.quiqzy.client.gui.ZeroGui g ? g.testColorState() : String.valueOf(mc.screen)));
         step.accept("i3@15", mc -> mc.setScreen(new dev.quiqzy.client.hud.HudEditorScreen()));
         step.accept("i4@5", mc -> mc.setScreen(null));
         return;
      }
      if ("hud".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("h0@30", mc -> {
            mc.setScreen(null);
            mc.player.setXRot(5f);
            for (var m : new dev.quiqzy.client.module.Module[]{ModuleManager.HUD, ModuleManager.WATERMARK, ModuleManager.MODULE_LIST,
                  ModuleManager.POTION_HUD, ModuleManager.SESSION_STATS, ModuleManager.WAYPOINTS, ModuleManager.RADAR, ModuleManager.STATUS_BAR,
                  ModuleManager.KEYSTROKES, ModuleManager.ARMOR_HUD, ModuleManager.ITEM_COUNTER, ModuleManager.SUS_CHUNK}) m.setEnabled(true);
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
               var uuid = mc.player.getUUID();
               server.execute(() -> {
                  var sp = server.getPlayerList().getPlayer(uuid);
                  if (sp == null) return;
                  sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 2400, 1));
                  sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 900, 0));
                  sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 120, 1));
               });
            }
         });
         step.accept("h1@20", mc -> mc.setScreen(new dev.quiqzy.client.hud.HudEditorScreen()));
         step.accept("h2@20", mc -> {
            ModuleManager.ACCENT.setOffset("info", -260f, 120f);
            ModuleManager.ACCENT.setOffset("watermark", 300f, 0f);
         });
         step.accept("h3@10", mc -> {
            mc.setScreen(null);
            ModuleManager.ACCENT.setOffset("info", 0f, 0f);
            ModuleManager.ACCENT.setOffset("watermark", 0f, 0f);
         });
         return;
      }
      if ("freecam".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("fc0@20", mc -> {
            mc.setScreen(null);
            mc.player.setXRot(10f);
            ModuleManager.FREECAM.setEnabled(true);
         });
         step.accept("fc1@20", mc -> {
            var m = ModuleManager.FREECAM;
            m.x += 3; m.y += 4; m.z += 3;
            m.yaw += 135f; m.pitch = 40f;
            log("Freecam", m.x + " " + m.y + " " + m.z);
         });
         step.accept("fc2@6", mc -> {
            ModuleManager.FREECAM.scroll(1);
            ModuleManager.FREECAM.scroll(1);
            ModuleManager.FREECAM.scroll(1);
            log("Freecam-Tempo", ModuleManager.FREECAM.speed.get());
         });
         step.accept("fc3@20", mc -> {
            log("Spieler-Blick (Treffer)", mc.hitResult == null ? "-" : mc.hitResult.getType() + " " + mc.hitResult.getLocation());
            ModuleManager.FREECAM.setEnabled(false);
         });
         return;
      }
      if ("petals".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         step.accept("p0@20", mc -> {
            mc.setScreen(null);
            mc.player.setXRot(-10f);
            mc.player.setRemainingFireTicks(0);
            ModuleManager.EMBERS.setEnabled(true);
            ModuleManager.EMBERS.style.set("Petals");
         });
         step.accept("p1@40", mc -> log("Petals", "an"));
         step.accept("p2@40", mc -> mc.player.setXRot(40f));
         return;
      }
      if ("weapons".equals(System.getenv("ZERO_SELFTEST_ONLY"))) {
         for (String w : new String[]{"Butterfly", "Katana", "Karambit", "Scythe", "Greatsword", "Butterfly"}) {
            step.accept("w_" + w.toLowerCase() + "@30", mc -> {
               ModuleManager.WEAPON_SKINS.model.set(w);
               log("Modell", w + " / " + mc.player.getMainHandItem());
            });
         }
         return;
      }
      step.accept("hud_full@30", mc -> {
         ModuleManager.WATERMARK.setEnabled(true);
         ModuleManager.MODULE_LIST.setEnabled(true);
         ModuleManager.ARMOR_HUD.setEnabled(true);
         ModuleManager.POTION_HUD.setEnabled(true);
         ModuleManager.NO_HURT_CAM.setEnabled(true);
         ModuleManager.TOGGLE_SPRINT.setEnabled(true);
         IntegratedServer server = mc.getSingleplayerServer();
         if (server != null) {
            var uuid = mc.player.getUUID();
            server.execute(() -> {
               var sp = server.getPlayerList().getPlayer(uuid);
               if (sp == null) return;
               sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
               ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
               chest.setDamageValue(chest.getMaxDamage() / 2);
               sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
               ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS);
               legs.setDamageValue(legs.getMaxDamage() * 9 / 10);
               sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, legs);
               sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
               sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 2400, 1));
               sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 160, 0));
               sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 6000, 0));
            });
         }
      });
      step.accept("esp_tags@30", mc -> {
         ModuleManager.NAMETAGS.setEnabled(true);
         ModuleManager.NAMETAGS.mobs.set(true);
         ModuleManager.ESP_2D.setEnabled(true);
         ModuleManager.ESP_2D.animals.set(true);
         IntegratedServer server = mc.getSingleplayerServer();
         Vec3 at = ahead(mc, 4.0);
         if (server != null) {
            server.execute(() -> {
               ServerLevel lvl = server.getLevel(Level.OVERWORLD);
               var item = new net.minecraft.world.entity.item.ItemEntity(lvl, at.x + 1.2, mc.player.getY() + 0.5, at.z, new ItemStack(Items.DIAMOND, 12));
               item.setPickUpDelay(32767);
               lvl.addFreshEntity(item);
            });
         }
      });
      step.accept("esp_off@3", mc -> {
         ModuleManager.ESP_2D.setEnabled(false);
         ModuleManager.NAMETAGS.setEnabled(false);
      });
      step.accept("fog_on@20", mc -> {
         mc.player.setXRot(-2f);
         mc.options.renderDistance().set(6);
      });
      step.accept("fog_off@20", mc -> ModuleManager.NO_FOG.setEnabled(true));
      step.accept("crosshair_lowhp@25", mc -> {
         ModuleManager.CROSSHAIR.setEnabled(true);
         ModuleManager.LOW_HP.setEnabled(true);
         IntegratedServer server = mc.getSingleplayerServer();
         if (server != null) {
            var uuid = mc.player.getUUID();
            server.execute(() -> {
               var sp = server.getPlayerList().getPlayer(uuid);
               if (sp != null) sp.setHealth(4f);
            });
         }
      });
      step.accept("stats_anim@20", mc -> {
         ModuleManager.SESSION_STATS.setEnabled(true);
         ModuleManager.ANIMATIONS.setEnabled(true);
         ModuleManager.ANIMATIONS.itemSize.set(80);
         ModuleManager.ITEM_COUNTER.setEnabled(true);
         ModuleManager.ITEM_COUNTER.hideZero.set(false);
      });
      step.accept("heal@3", mc -> {
         IntegratedServer server = mc.getSingleplayerServer();
         if (server != null) {
            var uuid = mc.player.getUUID();
            server.execute(() -> {
               var sp = server.getPlayerList().getPlayer(uuid);
               if (sp != null) sp.setHealth(20f);
            });
         }
      });
      step.accept("chat_pop@30", mc -> {
         ModuleManager.CHAT_TWEAKS.setEnabled(true);
         ModuleManager.POP_COUNTER.setEnabled(true);
         mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("ZERO test message"));
         IntegratedServer server = mc.getSingleplayerServer();
         if (server != null) {
            var uuid = mc.player.getUUID();
            server.execute(() -> {
               var sp = server.getPlayerList().getPlayer(uuid);
               if (sp == null) return;
               sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
               sp.setHealth(1f);
               sp.hurtServer(sp.level(), sp.damageSources().generic(), 10f);
            });
         }
      });
      step.accept("pop_check@5", mc -> log("Eigene Pops", ModuleManager.POP_COUNTER.pops(mc.player)));
      step.accept("video_set@40", mc -> {
         ModuleManager.STATUS_BAR.setEnabled(true);
         ModuleManager.RADAR.setEnabled(true);
         ModuleManager.RADAR.mobs.set(true);
         ModuleManager.EMBERS.style.set("Both");
         ModuleManager.KEYSTROKES.keySounds.set(true);
      });
      step.accept("accent_ocean@15", mc -> ModuleManager.ACCENT.preset.set("Ocean"));
      step.accept("accent_gui@15", mc -> {
         dev.quiqzy.client.gui.ZeroGui gui = new dev.quiqzy.client.gui.ZeroGui();
         mc.setScreen(gui);
         gui.testShow(dev.quiqzy.client.module.Module.Category.MISC, true, ModuleManager.ACCENT);
      });
      step.accept("accent_back@5", mc -> {
         mc.setScreen(null);
         ModuleManager.ACCENT.preset.set("Ember");
      });
      step.accept("katana", mc -> log("Hand", mc.player.getMainHandItem()));
      step.accept("hit@3", mc -> {
         LivingEntity p = pig(mc);
         log("Schwein gefunden", p);
         if (p != null) mc.gameMode.attack(mc.player, p);
      });
      step.accept("target_hud@20", mc -> log("Target", ModuleManager.TARGET_HUD.target(mc)));
      step.accept("crit@4", mc -> {
         LivingEntity p = pig(mc);
         if (p != null) {
            mc.player.fallDistance = 1.5;
            mc.player.setOnGround(false);
            mc.gameMode.attack(mc.player, p);
         }
      });
      step.accept("crit_late@9", mc -> log("Crit-Nachlauf", ""));
      step.accept("trick@9", mc -> ModuleManager.WEAPON_SKINS.trick());
      step.accept("karambit@30", mc -> ModuleManager.WEAPON_SKINS.model.set("Karambit"));
      step.accept("scythe@30", mc -> ModuleManager.WEAPON_SKINS.model.set("Scythe"));
      step.accept("greatsword@30", mc -> ModuleManager.WEAPON_SKINS.model.set("Greatsword"));
      step.accept("butterfly@30", mc -> ModuleManager.WEAPON_SKINS.model.set("Butterfly"));
      step.accept("kill_prep@30", mc -> {
         LivingEntity p = pig(mc);
         IntegratedServer server = mc.getSingleplayerServer();
         if (p != null && server != null) {
            int id = p.getId();
            server.execute(() -> {
               var e = server.getLevel(Level.OVERWORLD).getEntity(id);
               if (e instanceof LivingEntity le) le.setHealth(1f);
            });
         }
      });
      step.accept("kill@8", mc -> {
         LivingEntity p = pig(mc);
         log("Kill-Ziel", p);
         if (p != null) mc.gameMode.attack(mc.player, p);
      });
      step.accept("afterglow@25", mc -> log("nach Kill", pig(mc)));
      step.accept("burning@30", mc -> {
         IntegratedServer server = mc.getSingleplayerServer();
         if (server != null) {
            var uuid = mc.player.getUUID();
            server.execute(() -> {
               var sp = server.getPlayerList().getPlayer(uuid);
               if (sp != null) {
                  sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                  sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 400));
                  sp.setRemainingFireTicks(300);
               }
            });
         }
      });
      step.accept("burning_vanilla@3", mc -> ModuleManager.LOW_FIRE.setEnabled(false));
      step.accept("item_skins@30", mc -> {
         ModuleManager.ITEM_SKINS.setEnabled(true);
         mc.player.getInventory().setSelectedSlot(0);
         mc.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
         mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
         log("ItemSkins", "pickaxe=" + ModuleManager.ITEM_SKINS.applies(mc.player.getMainHandItem())
            + " totem=" + ModuleManager.ITEM_SKINS.applies(mc.player.getOffhandItem()));
      });
      step.accept("ingame_gui", mc -> {
         dev.quiqzy.client.gui.ZeroGui gui = new dev.quiqzy.client.gui.ZeroGui();
         mc.setScreen(gui);
         gui.testShow(dev.quiqzy.client.module.Module.Category.VISUALS, true, ModuleManager.HIT_EFFECTS);
      });
   }
}
