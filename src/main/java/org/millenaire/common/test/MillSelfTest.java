package org.millenaire.common.test;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.entity.MillEntities;
import org.millenaire.common.entity.MillBlockEntities;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.ui.ContainerTrade;
import org.millenaire.common.utilities.VillageInventory;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.WorldGenVillage;

/**
 * Server-side automated self-test harness for the Millénaire 26.2 Fabric port.
 *
 * <p>It exercises everything testable without a real client (registration, village world-gen for
 * every culture, villager spawning, building construction, item/block use, the server trade path,
 * and villager interaction), dumps a greppable {@code [MILLTEST]} report to the log, and then
 * cleanly stops the server so the process exits.
 *
 * <p>It runs ONLY when the JVM is launched with {@code -Dmillenaire.selftest=true}. With the
 * property absent, {@link #isEnabled()} returns false and {@link #register(MinecraftServer)} is
 * never called from {@code MillenaireMod} — the harness is entirely out of the normal code path.
 *
 * <p>Everything is driven from {@link ServerTickEvents#END_SERVER_TICK} so the world actually
 * ticks between steps; a hard max-tick guard force-stops the server even if a step stalls.
 */
public final class MillSelfTest {
   public static final String PROPERTY = "millenaire.selftest";
   private static final String TAG = "[MILLTEST]";

   /** Per-step tick schedule (relative to harness start). */
   private static final int TICK_SPAWN_PLAYER = 5;
   private static final int TICK_REGISTRATION = 10;
   private static final int TICK_GEN_VILLAGES = 20;
   private static final int TICK_FORCE_CHUNKS = 40;
   private static final int TICK_GROWTH_START = 60;
   private static final int TICK_GROWTH_END = TICK_GROWTH_START + 2400; // ~2 min of growth ticking
   private static final int TICK_BUILDING_REPORT = TICK_GROWTH_END + 20;
   private static final int TICK_ITEMS_BLOCKS = TICK_GROWTH_END + 40;
   private static final int TICK_TRADE = TICK_GROWTH_END + 60;
   private static final int TICK_INTERACT = TICK_GROWTH_END + 80;
   private static final int TICK_MINE_CYCLE = TICK_GROWTH_END + 90;
   private static final int TICK_CHOP_CYCLE = TICK_GROWTH_END + 95;
   private static final int TICK_FARM_CYCLE = TICK_GROWTH_END + 98;
   private static final int TICK_SUMMARY = TICK_GROWTH_END + 100;
   private static final int MAX_TICK_GUARD = 6000 + TICK_GROWTH_END; // absolute safety net

   /** Distance between consecutive culture village placements (blocks). */
   private static final int VILLAGE_SPACING = 1500;
   private static final int VILLAGE_ORIGIN = 600;

   // --- Villager-activity metric tuning -------------------------------------------------------
   /** Sample every villager's position every this many ticks during the growth window. */
   private static final int MOVEMENT_SAMPLE_INTERVAL = 100; // 100t = 5s @20tps
   /** Hard cap on distinct villagers tracked for movement (memory guard). */
   private static final int MOVEMENT_MAX_VILLAGERS = 200;
   /** Total path distance (blocks) above which a villager counts as having MOVED. */
   private static final double MOVEMENT_MOVED_THRESHOLD = 2.0;
   /** Village-area half-extent (blocks) used both for the area-AABB villager count and block diff. */
   private static final int VILLAGE_AREA_RADIUS = 48;
   /** Vertical half-extent of the block-activity snapshot box. */
   private static final int BLOCK_BOX_HALF_HEIGHT = 24;
   /** Sampling stride for the block-activity box (>=1; 1 = every block). Keeps memory bounded. */
   private static final int BLOCK_BOX_STRIDE = 2;
   /** Hard cap on snapshotted blocks PER village (memory guard). */
   private static final int BLOCK_BOX_MAX_SAMPLES = 60000;
   /** How many villagers to sample for the cheap goal/AI report. */
   private static final int GOAL_SAMPLE_LIMIT = 200;

   private static boolean started = false;

   private final MinecraftServer server;
   private ServerLevel level;
   private ServerPlayer fakePlayer;

   private int tick = 0;
   private boolean growthLogged600 = false;
   private boolean growthLogged1200 = false;
   private boolean growthLogged1800 = false;
   private boolean halted = false;

   // --- Result accumulators (for the summary block) ---
   private final Map<String, Boolean> villageByCulture = new LinkedHashMap<>();
   private final Map<String, String> villageDetailByCulture = new LinkedHashMap<>();
   private final List<Point> generatedVillagePoints = new ArrayList<>();
   private int itemsTested = 0;
   private int itemsFailed = 0;
   private int blocksTested = 0;
   private int blocksFailed = 0;
   private int buildingsReported = 0;
   private int highFailureBuildings = 0;
   private Boolean tradeOk = null;
   private String tradeDetail = "not run";
   private Boolean interactOk = null;
   private String interactDetail = "not run";
   private Boolean mineCycleOk = null;
   private Boolean chopCycleOk = null;
   private Boolean farmCycleOk = null;
   private final Map<String, Integer> distinctExceptions = new HashMap<>();

   // --- Movement tracking (per villager, keyed by stable getVillagerId) ---
   /** Last sampled position per villager id. */
   private final Map<Long, double[]> lastSamplePos = new HashMap<>();
   /** Accumulated path distance per villager id (sum of segment lengths between samples). */
   private final Map<Long, Double> accumulatedDistance = new HashMap<>();
   /** First sample tick recorded, for blocks/second over the actual sampled window. */
   private int firstMovementSampleTick = -1;
   private int lastMovementSampleTick = -1;
   private int movementSampleCount = 0;
   /** Cached movement summary line, computed once at growth end. */
   private String movementSummary = "not run";

   // --- Block-activity tracking (per village townhall, keyed by name) ---
   /** Start-of-window block snapshot: village name -> (packed BlockPos.asLong -> BlockState). */
   private final Map<String, Map<Long, BlockState>> blockSnapshots = new LinkedHashMap<>();
   /** Computed at growth end: village name -> blocksChanged. */
   private final Map<String, Integer> blockActivityByVillage = new LinkedHashMap<>();

   // --- Goal/AI sanity (computed at growth end) ---
   private String goalSummary = "not run";

   private MillSelfTest(MinecraftServer server) {
      this.server = server;
   }

   /** True only when launched with {@code -Dmillenaire.selftest=true}. Cheap; safe to call always. */
   public static boolean isEnabled() {
      // System property (-Dmillenaire.selftest=true) OR env var MILLENAIRE_SELFTEST=true/1 — the env var
      // is reliably inherited by Loom's forked runServer JVM where -D may not forward.
      if ("true".equalsIgnoreCase(System.getProperty(PROPERTY))) {
         return true;
      }
      String env = System.getenv("MILLENAIRE_SELFTEST");
      return "true".equalsIgnoreCase(env) || "1".equals(env);
   }

   /**
    * Hooks the harness onto the server tick loop. Call once from
    * {@code ServerLifecycleEvents.SERVER_STARTED}, guarded by {@link #isEnabled()}.
    */
   public static void register(MinecraftServer server) {
      if (started) {
         return;
      }
      started = true;
      log("===== MILLENAIRE SELF-TEST ENABLED (-D" + PROPERTY + "=true) =====");
      final MillSelfTest harness = new MillSelfTest(server);
      ServerTickEvents.END_SERVER_TICK.register(s -> {
         if (s == server) {
            harness.onTick();
         }
      });
   }

   // ----------------------------------------------------------------------------------------------

   private void onTick() {
      if (halted) {
         return;
      }
      tick++;
      try {
         // Absolute safety net: never let the server hang.
         if (tick >= MAX_TICK_GUARD) {
            log("MAX-TICK GUARD (" + MAX_TICK_GUARD + ") reached — forcing stop.");
            stopServer();
            return;
         }

         switch (tick) {
            case TICK_SPAWN_PLAYER -> stepSpawnPlayerAndDebug();
            case TICK_REGISTRATION -> stepRegistrationSanity();
            case TICK_GEN_VILLAGES -> stepGenerateVillages();
            case TICK_FORCE_CHUNKS -> stepForceLoadVillageChunks();
            case TICK_GROWTH_START -> {
               log("step GROWTH: ticking world ~" + (TICK_GROWTH_END - TICK_GROWTH_START) + " ticks to let villagers spawn / buildings build…");
               snapshotVillageBlocks();   // start-of-window block snapshot (metric 2)
               sampleVillagerMovement();  // baseline position sample (metric 1)
            }
            case TICK_GROWTH_END -> {
               sampleVillagerMovement();      // final position sample
               computeMovementSummary();      // metric 1: MOVEMENT line
               computeBlockActivitySummary(); // metric 2: BLOCKACTIVITY lines
               computeGoalSummary();          // metric 3: GOALS line
            }
            case TICK_BUILDING_REPORT -> stepBuildingCompleteness();
            case TICK_ITEMS_BLOCKS -> stepItemsAndBlocks();
            case TICK_TRADE -> stepTradeLogic();
            case TICK_INTERACT -> stepVillagerInteraction();
            case TICK_MINE_CYCLE -> stepMineCycle();
            case TICK_CHOP_CYCLE -> stepChopCycle();
            case TICK_FARM_CYCLE -> stepFarmCycle();
            case TICK_SUMMARY -> {
               stepSummary();
               stopServer();
            }
            default -> {
               if (tick > TICK_GROWTH_START && tick < TICK_GROWTH_END) {
                  growthHeartbeat();
                  if ((tick - TICK_GROWTH_START) % MOVEMENT_SAMPLE_INTERVAL == 0) {
                     sampleVillagerMovement(); // periodic position sample (metric 1)
                  }
               }
            }
         }
      } catch (Throwable t) {
         // The tick loop itself must never die. Record and keep going toward the summary/stop.
         recordException("onTick(tick=" + tick + ")", t);
         MillLog.printException(TAG + " self-test tick error", t);
      }
   }

   private void growthHeartbeat() {
      int elapsed = tick - TICK_GROWTH_START;
      if (elapsed >= 600 && !growthLogged600) {
         growthLogged600 = true;
         logGrowthStatus(600);
      } else if (elapsed >= 1200 && !growthLogged1200) {
         growthLogged1200 = true;
         logGrowthStatus(1200);
      } else if (elapsed >= 1800 && !growthLogged1800) {
         growthLogged1800 = true;
         logGrowthStatus(1800);
      }
   }

   private void logGrowthStatus(int elapsed) {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         int villages = mw != null ? mw.villagesList.pos.size() : 0;
         int buildings = mw != null ? mw.allBuildings().size() : 0;
         int villagers = countVillagersInWorld();
         log("GROWTH @" + elapsed + " ticks: villages=" + villages + " buildings=" + buildings
            + " villagersInWorld=" + villagers + " exceptionsSeen=" + distinctExceptions.size());
      } catch (Throwable t) {
         recordException("growthHeartbeat", t);
      }
   }

   // ============================ METRIC 1: villager movement-per-time ============================

   /**
    * Samples every (capped) MillVillager's position and accumulates path distance between
    * consecutive samples, keyed by the stable {@link MillVillager#getVillagerId()}. Robust: never
    * throws out to the tick loop. Also logs the two villager counts (global lookup vs. per-village
    * area AABB) so we can see whether villagers exist but aren't tracked/ticking.
    */
   private void sampleVillagerMovement() {
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive()
         );
         int globalCount = villagers.size();
         int areaCount = countVillagersInVillageAreas();

         int sampled = 0;
         for (MillVillager v : villagers) {
            if (sampled >= MOVEMENT_MAX_VILLAGERS) {
               break;
            }
            try {
               long id = v.getVillagerId();
               double x = v.getX();
               double y = v.getY();
               double z = v.getZ();
               double[] prev = lastSamplePos.get(id);
               if (prev != null) {
                  double dx = x - prev[0];
                  double dy = y - prev[1];
                  double dz = z - prev[2];
                  double seg = Math.sqrt(dx * dx + dy * dy + dz * dz);
                  accumulatedDistance.merge(id, seg, Double::sum);
               } else {
                  accumulatedDistance.putIfAbsent(id, 0.0);
               }
               lastSamplePos.put(id, new double[] {x, y, z});
               sampled++;
            } catch (Throwable ignored) {
            }
         }
         if (firstMovementSampleTick < 0) {
            firstMovementSampleTick = tick;
         }
         lastMovementSampleTick = tick;
         movementSampleCount++;
         log("MOVEMENT-SAMPLE #" + movementSampleCount + " @tick" + tick + ": globalCount=" + globalCount
            + " areaAABBCount=" + areaCount + " trackedIds=" + accumulatedDistance.size()
            + (globalCount != areaCount ? "  (MISMATCH: villagers exist outside ticking/tracked set?)" : ""));
      } catch (Throwable t) {
         recordException("movement-sample", t);
      }
   }

   /** Computes the final MOVEMENT summary line over the whole sampled window. */
   private void computeMovementSummary() {
      try {
         int sampled = accumulatedDistance.size();
         if (sampled == 0) {
            movementSummary = "sampled=0 (no villagers ever sampled)";
            log("MOVEMENT: " + movementSummary);
            return;
         }
         int moved = 0;
         int stuck = 0;
         double total = 0.0;
         double max = 0.0;
         for (double d : accumulatedDistance.values()) {
            total += d;
            if (d > max) {
               max = d;
            }
            if (d > MOVEMENT_MOVED_THRESHOLD) {
               moved++;
            } else {
               stuck++;
            }
         }
         double avgBlocks = total / sampled;
         int windowTicks = Math.max(1, lastMovementSampleTick - firstMovementSampleTick);
         double windowSeconds = windowTicks / 20.0;
         double avgBlocksPerSec = windowSeconds > 0 ? avgBlocks / windowSeconds : 0.0;
         movementSummary = String.format(
            "sampled=%d moved=%d stuck=%d avgBlocks=%.2f maxBlocks=%.2f avgBlocksPerSec=%.3f (window=%dt/%.1fs samples=%d)",
            sampled, moved, stuck, avgBlocks, max, avgBlocksPerSec, windowTicks, windowSeconds, movementSampleCount);
         log("MOVEMENT: " + movementSummary);
         if (stuck > moved) {
            log("MOVEMENT: WARNING — most sampled villagers are STUCK (" + stuck + "/" + sampled
               + "). This is the 'villagers stand still / don't work' bug signal.");
         }
      } catch (Throwable t) {
         recordException("movement-summary", t);
         movementSummary = "exception: " + t;
         log("MOVEMENT: FAIL " + movementSummary);
      }
   }

   /** Counts villagers found within the per-village-area AABB(s) around each townhall. */
   private int countVillagersInVillageAreas() {
      int count = 0;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         if (mw == null) {
            return 0;
         }
         for (Building b : mw.allBuildings()) {
            try {
               if (!b.isTownhall) {
                  continue;
               }
               Point p = b.getPos();
               if (p == null) {
                  continue;
               }
               int r = clampRadius(b);
               AABB area = new AABB(
                  p.getiX() - r, level.getMinY(), p.getiZ() - r,
                  p.getiX() + r, level.getMaxY(), p.getiZ() + r);
               count += level.getEntitiesOfClass(MillVillager.class, area, v -> v.isAlive()).size();
            } catch (Throwable ignored) {
            }
         }
      } catch (Throwable t) {
         recordException("area-villager-count", t);
      }
      return count;
   }

   // ============================ METRIC 2: villager block interaction / world activity ============================

   /** Snapshots the block states in a clamped, strided box around each village townhall. */
   private void snapshotVillageBlocks() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         if (mw == null) {
            log("BLOCKACTIVITY: SKIP (no MillWorldData at snapshot time)");
            return;
         }
         for (Building b : mw.allBuildings()) {
            try {
               if (!b.isTownhall) {
                  continue;
               }
               Point p = b.getPos();
               if (p == null) {
                  continue;
               }
               String name = safeVillageName(b);
               int r = clampRadius(b);
               Map<Long, BlockState> snap = sampleBox(p, r);
               blockSnapshots.put(name, snap);
               log("BLOCKACTIVITY: snapshot village=" + name + " sampledBlocks=" + snap.size()
                  + " (radius=" + r + " stride=" + BLOCK_BOX_STRIDE + ")");
            } catch (Throwable t) {
               recordException("block-snapshot", t);
            }
         }
      } catch (Throwable t) {
         recordException("block-snapshot-all", t);
      }
   }

   /** Re-samples each village box and counts how many sampled blocks changed since the snapshot. */
   private void computeBlockActivitySummary() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         int windowTicks = TICK_GROWTH_END - TICK_GROWTH_START;
         if (blockSnapshots.isEmpty()) {
            log("BLOCKACTIVITY: no snapshots taken (no townhall villages?)");
            return;
         }
         for (Map.Entry<String, Map<Long, BlockState>> e : blockSnapshots.entrySet()) {
            String name = e.getKey();
            int changed = 0;
            try {
               for (Map.Entry<Long, BlockState> cell : e.getValue().entrySet()) {
                  BlockPos pos = BlockPos.of(cell.getKey());
                  BlockState now = level.getBlockState(pos);
                  if (now != cell.getValue()) {
                     changed++;
                  }
               }
            } catch (Throwable t) {
               recordException("block-diff", t);
            }
            blockActivityByVillage.put(name, changed);
            log("BLOCKACTIVITY: village=" + name + " blocksChanged=" + changed + " over " + windowTicks + "t");
         }
         int totalChanged = 0;
         for (int v : blockActivityByVillage.values()) {
            totalChanged += v;
         }
         if (totalChanged == 0 && !blockActivityByVillage.isEmpty()) {
            log("BLOCKACTIVITY: WARNING — ZERO block changes across all villages over the growth window."
               + " Villagers aren't doing jobs (no crops/tilling/paths/construction progress).");
         }
      } catch (Throwable t) {
         recordException("block-activity-summary", t);
      }
   }

   /** Samples block states in a strided cube around {@code center}, capped at {@link #BLOCK_BOX_MAX_SAMPLES}. */
   private Map<Long, BlockState> sampleBox(Point center, int r) {
      Map<Long, BlockState> out = new HashMap<>();
      int cx = center.getiX();
      int cy = center.getiY();
      int cz = center.getiZ();
      int minY = Math.max(level.getMinY(), cy - BLOCK_BOX_HALF_HEIGHT);
      int maxY = Math.min(level.getMaxY(), cy + BLOCK_BOX_HALF_HEIGHT);
      int stride = Math.max(1, BLOCK_BOX_STRIDE);
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      outer:
      for (int x = cx - r; x <= cx + r; x += stride) {
         for (int z = cz - r; z <= cz + r; z += stride) {
            for (int y = minY; y <= maxY; y += stride) {
               try {
                  m.set(x, y, z);
                  out.put(m.asLong(), level.getBlockState(m));
                  if (out.size() >= BLOCK_BOX_MAX_SAMPLES) {
                     break outer;
                  }
               } catch (Throwable ignored) {
               }
            }
         }
      }
      return out;
   }

   /** Village radius clamped to a sane range for the activity box. */
   private static int clampRadius(Building townhall) {
      int r = VILLAGE_AREA_RADIUS;
      try {
         if (townhall.villageType != null && townhall.villageType.radius > 0) {
            r = townhall.villageType.radius;
         }
      } catch (Throwable ignored) {
      }
      return Math.max(8, Math.min(VILLAGE_AREA_RADIUS, r));
   }

   private static String safeVillageName(Building b) {
      try {
         String n = b.getVillageQualifiedName();
         if (n != null && !n.isEmpty()) {
            return n;
         }
      } catch (Throwable ignored) {
      }
      try {
         return "village@" + b.getPos();
      } catch (Throwable ignored) {
         return "village?";
      }
   }

   // ============================ METRIC 3: goal / AI sanity ============================

   /** Cheap goal/nav sanity over a sample of villagers: withGoal/noGoal and navigating/idle. */
   private void computeGoalSummary() {
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive()
         );
         int withGoal = 0;
         int noGoal = 0;
         int navigating = 0;
         int idle = 0;
         int examined = 0;
         for (MillVillager v : villagers) {
            if (examined >= GOAL_SAMPLE_LIMIT) {
               break;
            }
            examined++;
            try {
               if (v.goalKey != null && !v.goalKey.isEmpty()) {
                  withGoal++;
               } else {
                  noGoal++;
               }
            } catch (Throwable ignored) {
               noGoal++;
            }
            try {
               if (v.getNavigation() != null && !v.getNavigation().isDone()) {
                  navigating++;
               } else {
                  idle++;
               }
            } catch (Throwable ignored) {
               idle++;
            }
         }
         goalSummary = "withGoal=" + withGoal + " noGoal=" + noGoal + " navigating=" + navigating
            + " idle=" + idle + " (examined=" + examined + "/" + villagers.size() + ")";
         log("GOALS: " + goalSummary);
         if (withGoal == 0 && examined > 0) {
            log("GOALS: WARNING — NO sampled villager has a goal assigned. The village isn't handing out work.");
         }
      } catch (Throwable t) {
         recordException("goal-summary", t);
         goalSummary = "exception: " + t;
         log("GOALS: FAIL " + goalSummary);
      }
   }

   // ============================ STEP A: debug mode + fake player ============================

   private void stepSpawnPlayerAndDebug() {
      // A. Enable Mill debug so [MILLDEBUG] context is captured during the run.
      try {
         MillConfigValues.DEBUG_MODE = true;
         MillConfigValues.LogBuildingPlan = 3;
         log("A debug-mode OK: MillConfigValues.DEBUG_MODE=true, LogBuildingPlan=3");
      } catch (Throwable t) {
         recordException("A:debug", t);
         log("A debug-mode FAIL: " + t);
      }

      // FakePlayer creation (used by items/blocks/trade/interaction steps).
      try {
         level = server.overworld();
         fakePlayer = createFakePlayer(server, level);
         if (fakePlayer != null) {
            BlockPos spawn = level.getRespawnData().pos();
            fakePlayer.snapTo(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, 0.0F, 0.0F);
            log("A fakeplayer OK: " + fakePlayer.getName().getString() + " at " + spawn + " (class=" + fakePlayer.getClass().getName() + ")");
         } else {
            log("A fakeplayer FAIL: could not create a fake/server player (item, block, trade and interaction steps will be skipped)");
         }
      } catch (Throwable t) {
         recordException("A:fakeplayer", t);
         log("A fakeplayer FAIL: " + t);
      }
   }

   /**
    * Tries Fabric API's {@code net.fabricmc.fabric.api.entity.FakePlayer.get(ServerLevel)} via
    * reflection (so the harness compiles even if that transitive submodule were absent); falls back
    * to a minimal {@link ServerPlayer} built with the verified 26.2 constructor.
    */
   private static ServerPlayer createFakePlayer(MinecraftServer server, ServerLevel level) {
      try {
         Class<?> fpClass = Class.forName("net.fabricmc.fabric.api.entity.FakePlayer");
         Object fp = fpClass.getMethod("get", ServerLevel.class).invoke(null, level);
         if (fp instanceof ServerPlayer sp) {
            return sp;
         }
      } catch (Throwable ignored) {
         // FakePlayer not on classpath / failed — fall through to the manual path below.
      }
      try {
         GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("MillTestBot".getBytes()), "MillTestBot");
         ServerPlayer sp = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
         return sp;
      } catch (Throwable t) {
         MillLog.printException(TAG + " manual ServerPlayer construction failed", t);
         return null;
      }
   }

   // ============================ STEP B: registration sanity ============================

   private void stepRegistrationSanity() {
      try {
         int items = MillRegistry.REGISTERED_ITEMS.size();
         int blocks = MillRegistry.REGISTERED_BLOCKS.size();
         int entities = MillEntities.REGISTERED.size();
         int blockEntities = MillBlockEntities.REGISTERED.size();
         int cultures = Culture.ListCultures.size();
         boolean ok = items > 0 && blocks > 0 && entities > 0 && blockEntities > 0 && cultures > 0;
         log("B registration " + (ok ? "OK" : "FAIL") + ": items=" + items + " blocks=" + blocks
            + " entities=" + entities + " blockEntities=" + blockEntities + " cultures=" + cultures);
         if (!ok) {
            recordException("B:registration", new IllegalStateException("a registration count was 0"));
         }
      } catch (Throwable t) {
         recordException("B:registration", t);
         log("B registration FAIL: " + t);
      }
   }

   // ============================ STEP C: village generation per culture ============================

   private void stepGenerateVillages() {
      List<Culture> cultures = new ArrayList<>(Culture.ListCultures);
      if (cultures.isEmpty()) {
         log("C villagegen FAIL: no cultures loaded");
         return;
      }
      int idx = 0;
      for (Culture culture : cultures) {
         String cKey = culture.key;
         try {
            VillageType vtype = pickRegularVillageType(culture);
            if (vtype == null) {
               villageByCulture.put(cKey, false);
               villageDetailByCulture.put(cKey, "no regular village type");
               log("C villagegen[" + cKey + "] FAIL: no regular VillageType available");
               idx++;
               continue;
            }
            int x = VILLAGE_ORIGIN + idx * VILLAGE_SPACING;
            int z = VILLAGE_ORIGIN;
            // Force-generate the target chunks FIRST. generateVillage calls findTopSoilBlock before its
            // own neighbour force-load loop, so on ungenerated chunks it returns world min-Y (-63) and
            // fails with no_place_for_central_building. Load a 5x5 of real chunks so a surface exists.
            int ccx = x >> 4;
            int ccz = z >> 4;
            for (int dcx = -2; dcx <= 2; dcx++) {
               for (int dcz = -2; dcz <= 2; dcz++) {
                  level.getChunk(ccx + dcx, ccz + dcz);
               }
            }
            int surfaceY = org.millenaire.common.utilities.WorldUtilities.findTopSoilBlock(level, x, z);
            log("C villagegen[" + cKey + "] target " + x + "/" + z + " surfaceY=" + surfaceY + " (after chunk load)");
            WorldGenVillage gen = new WorldGenVillage();
            // Same entry point CommandSpawnVillage uses: full completion so buildings are placed.
            boolean result = gen.generateVillageAtPoint(
               level, MillRandom.random, x, 0, z, fakePlayer, false, true, false, 0, vtype, null, null, 1.0F
            );
            villageByCulture.put(cKey, result);
            villageDetailByCulture.put(cKey, "type=" + vtype.key + " at " + x + "/" + z + " -> " + result);
            if (result) {
               generatedVillagePoints.add(new Point(x, 0, z));
            }
            log("C villagegen[" + cKey + "] " + (result ? "OK" : "FAIL") + ": type=" + vtype.key + " at " + x + "/" + z);
         } catch (Throwable t) {
            villageByCulture.put(cKey, false);
            villageDetailByCulture.put(cKey, "exception: " + t);
            recordException("C:villagegen:" + cKey, t);
            log("C villagegen[" + cKey + "] FAIL: " + t);
         }
         idx++;
      }
   }

   private static VillageType pickRegularVillageType(Culture culture) {
      for (VillageType vt : culture.listVillageTypes) {
         if (vt.isRegularVillage()) {
            return vt;
         }
      }
      // Fall back to any non-lone, non-hamlet, non-marvel type, then to whatever exists.
      for (VillageType vt : culture.listVillageTypes) {
         if (!vt.lonebuilding && !vt.isHamlet() && !vt.isMarvel()) {
            return vt;
         }
      }
      return culture.listVillageTypes.isEmpty() ? null : culture.listVillageTypes.get(0);
   }

   // ============================ STEP (force-load village chunks) ============================

   private void stepForceLoadVillageChunks() {
      int forced = 0;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         List<Point> points = new ArrayList<>(generatedVillagePoints);
         if (mw != null) {
            for (Point p : mw.villagesList.pos) {
               points.add(p);
            }
         }
         for (Point p : points) {
            int cx = p.getiX() >> 4;
            int cz = p.getiZ() >> 4;
            // Force an 11x11 chunk square. A townhall only becomes isActive when isVillageChunksLoaded()
            // finds EVERY chunk of its winfo area loaded; a village can span ~±5 chunks, so a too-small
            // square leaves edge chunks unloaded -> townhall never activates -> villagers skip all AI
            // (the "stand still" symptom). ±5 covers a full village so the AI actually runs in the test.
            for (int dx = -5; dx <= 5; dx++) {
               for (int dz = -5; dz <= 5; dz++) {
                  try {
                     level.setChunkForced(cx + dx, cz + dz, true);
                     forced++;
                  } catch (Throwable ignored) {
                  }
               }
            }
         }
         log("forcechunks OK: forced " + forced + " chunks around " + points.size() + " village point(s)");
      } catch (Throwable t) {
         recordException("forcechunks", t);
         log("forcechunks FAIL: " + t);
      }
   }

   // ============================ STEP E: building completeness ============================

   private void stepBuildingCompleteness() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         if (mw == null) {
            log("E buildings FAIL: no MillWorldData");
            return;
         }
         Collection<Building> buildings = mw.allBuildings();
         buildingsReported = buildings.size();
         log("E buildings OK: " + buildingsReported + " building(s) in world (per-building placed/failed counts above"
            + " in [MILLDEBUG] BuildingPlan lines when LogBuildingPlan>0).");
         // We cannot universally read per-block placed/failed counters without coupling to internals,
         // so we flag obviously-incomplete buildings: those still mid-construction after the growth window.
         int incomplete = 0;
         for (Building b : buildings) {
            try {
               if (!b.getConstructionsInProgress().isEmpty()) {
                  incomplete++;
               }
            } catch (Throwable ignored) {
            }
         }
         highFailureBuildings = incomplete;
         log("E buildings: still-incomplete after growth window = " + incomplete + "/" + buildingsReported);
      } catch (Throwable t) {
         recordException("E:buildings", t);
         log("E buildings FAIL: " + t);
      }
   }

   // ============================ STEP F: items + blocks ============================

   private void stepItemsAndBlocks() {
      if (fakePlayer == null) {
         log("F items/blocks SKIP: no fake player");
         return;
      }
      BlockPos base;
      try {
         base = level.getRespawnData().pos().above(2);
      } catch (Throwable t) {
         base = new BlockPos(0, level.getSeaLevel() + 2, 0);
      }

      // --- Items ---
      for (Item item : new ArrayList<>(MillRegistry.REGISTERED_ITEMS)) {
         itemsTested++;
         try {
            ItemStack stack = new ItemStack(item, 64);
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
            // use(Level,Player,Hand)
            item.use(level, fakePlayer, InteractionHand.MAIN_HAND);
            // useOn(UseOnContext): aim at the block just below the test position.
            BlockPos target = base.below(3);
            Vec3 hitVec = Vec3.atCenterOf(target);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, target, false);
            UseOnContext ctx = new UseOnContext(level, fakePlayer, InteractionHand.MAIN_HAND, stack, hit);
            item.useOn(ctx);
         } catch (Throwable t) {
            itemsFailed++;
            recordException("F:item", t);
            log("F item FAIL [" + safeId(item) + "]: " + t);
         }
      }
      log("F items OK: tested=" + itemsTested + " failed=" + itemsFailed);

      // --- Blocks ---
      int i = 0;
      for (Block block : new ArrayList<>(MillRegistry.REGISTERED_BLOCKS)) {
         blocksTested++;
         BlockPos pos = base.offset((i % 16) - 8, 0, (i / 16));
         i++;
         try {
            BlockState state = block.defaultBlockState();
            level.setBlock(pos, state, 3);
            BlockState read = level.getBlockState(pos);
            if (read.getBlock() != block) {
               throw new IllegalStateException("read-back mismatch: expected " + block + " got " + read.getBlock());
            }
            // Clean up so the test pad doesn't accumulate state.
            level.removeBlock(pos, false);
         } catch (Throwable t) {
            blocksFailed++;
            recordException("F:block", t);
            log("F block FAIL [" + safeBlockId(block) + "]: " + t);
         }
      }
      log("F blocks OK: tested=" + blocksTested + " failed=" + blocksFailed);
   }

   // ============================ STEP G: trade logic ============================

   private void stepTradeLogic() {
      if (fakePlayer == null) {
         tradeOk = false;
         tradeDetail = "no fake player";
         log("G trade SKIP: no fake player");
         return;
      }
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         Building shop = findShopWithGoods(mw);
         if (shop == null) {
            tradeOk = false;
            tradeDetail = "no shop with goods found";
            log("G trade SKIP: no shop building with goods found in any generated village");
            return;
         }

         // Give the bot a stack of money so a buy can actually execute.
         try {
            VillageInventory.changeMoney(fakePlayer.getInventory(), 100000, fakePlayer);
         } catch (Throwable ignored) {
         }

         shop.computeShopGoods(fakePlayer);
         Set<TradeGood> selling = shop.getSellingGoods(fakePlayer);
         Set<TradeGood> buying = shop.getBuyingGoods(fakePlayer);

         ContainerTrade container = new ContainerTrade(0, fakePlayer, shop);

         StringBuilder detail = new StringBuilder("shop=").append(shop.getPos())
            .append(" selling=").append(selling == null ? 0 : selling.size())
            .append(" buying=").append(buying == null ? 0 : buying.size());

         // BUY: player buys a selling-good from the shop.
         if (selling != null && !selling.isEmpty()) {
            TradeGood g = selling.iterator().next();
            int moneyBefore = VillageInventory.countMoney(fakePlayer.getInventory());
            container.executeTrade(g, true, false, 1, fakePlayer);
            int moneyAfter = VillageInventory.countMoney(fakePlayer.getInventory());
            detail.append(" | BUY good=").append(g.key)
               .append(" moneyDelta=").append(moneyAfter - moneyBefore);
            log("G trade BUY OK: good=" + g.key + " money " + moneyBefore + "->" + moneyAfter);
         } else {
            detail.append(" | BUY skipped (no selling goods)");
         }

         // SELL: player sells a buying-good to the shop (only if the bot has any in inventory).
         if (buying != null && !buying.isEmpty()) {
            TradeGood g = buying.iterator().next();
            // Give the bot some of that item so the sell can run.
            try {
               ItemStack give = new ItemStack(g.item.getItem(), 16);
               fakePlayer.getInventory().add(give);
            } catch (Throwable ignored) {
            }
            int moneyBefore = VillageInventory.countMoney(fakePlayer.getInventory());
            container.executeTrade(g, false, false, 1, fakePlayer);
            int moneyAfter = VillageInventory.countMoney(fakePlayer.getInventory());
            detail.append(" | SELL good=").append(g.key)
               .append(" moneyDelta=").append(moneyAfter - moneyBefore);
            log("G trade SELL OK: good=" + g.key + " money " + moneyBefore + "->" + moneyAfter);
         } else {
            detail.append(" | SELL skipped (no buying goods)");
         }

         tradeOk = true;
         tradeDetail = detail.toString();
         log("G trade OK: " + tradeDetail);
      } catch (Throwable t) {
         tradeOk = false;
         tradeDetail = "exception: " + t;
         recordException("G:trade", t);
         log("G trade FAIL: " + t);
      }
   }

   private Building findShopWithGoods(MillWorldData mw) {
      if (mw == null) {
         return null;
      }
      for (Building b : mw.allBuildings()) {
         try {
            if (b.isTownhall) {
               for (Building shop : b.getShops()) {
                  if (shop != null) {
                     return shop;
                  }
               }
            }
         } catch (Throwable ignored) {
         }
      }
      // Fallback: any building exposing a shop location.
      for (Building b : mw.allBuildings()) {
         try {
            if (b.location != null && b.location.shop != null && b.location.shop.length() > 0) {
               return b;
            }
         } catch (Throwable ignored) {
         }
      }
      return null;
   }

   // ============================ STEP H: villager interaction ============================

   private void stepVillagerInteraction() {
      if (fakePlayer == null) {
         interactOk = false;
         interactDetail = "no fake player";
         log("H interact SKIP: no fake player");
         return;
      }
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive()
         );
         if (villagers.isEmpty()) {
            interactOk = false;
            interactDetail = "no villagers spawned in world";
            log("H interact SKIP: no MillVillager entities in world");
            return;
         }
         MillVillager v = villagers.get(0);
         boolean handled = v.processInteract(fakePlayer, InteractionHand.MAIN_HAND);
         interactOk = true;
         interactDetail = "villager id=" + v.getVillagerId() + " name='" + v.getVillagerName() + "' processInteract=" + handled;
         log("H interact OK: " + interactDetail);
      } catch (Throwable t) {
         interactOk = false;
         interactDetail = "exception: " + t;
         recordException("H:interact", t);
         log("H interact FAIL: " + t);
      }
   }

   // ============================ STEP H2: player-like mine cycle (O1) ============================

   /**
    * Live evidence for the O1 player-like mine refactor: drive {@link com.coderyo.jason.ops.VillagerWorldOps}'s
    * break → pickup cycle on a REAL villager + a REAL stone block, then the point-owned regrow. Logs each phase so
    * the cycle is greppable in the harness output ({@code [MILLTEST] MINECYCLE ...}). This exercises the actual
    * primitives the migrated {@link org.millenaire.common.goal.GoalMinerMineResource} now calls.
    */
   private void stepMineCycle() {
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive());
         if (villagers.isEmpty()) {
            log("H2 MINECYCLE SKIP: no MillVillager in world");
            return;
         }
         MillVillager v = villagers.get(0);

         // A clear pad next to the villager: source block one across, villager standing adjacent (in reach).
         BlockPos vPos = v.blockPosition();
         BlockPos source = vPos.offset(1, 0, 0);
         level.setBlock(source.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
         level.setBlock(source, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);

         // Equip the correct tool (strict-tool path) and verify ensureTool accepts it.
         v.heldItem = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
         boolean toolOk = com.coderyo.jason.ops.VillagerWorldOps.ensureTool(v, com.coderyo.jason.ops.VillagerWorldOps.ToolKind.PICKAXE);
         log("H2 MINECYCLE ensureTool(PICKAXE) on held iron_pickaxe = " + toolOk);

         BlockState sourceState = level.getBlockState(source);

         // --- BREAK over time: call breakTick until COMPLETE, logging the crack progression. ---
         com.coderyo.jason.ops.OpState st = null;
         int guard = 0;
         int lastStage = -1;
         while (guard++ < 2000) {
            st = com.coderyo.jason.ops.VillagerWorldOps.breakTick(v, source);
            if (st == com.coderyo.jason.ops.OpState.IN_PROGRESS) {
               var prog = com.coderyo.jason.ops.TaskPointStore.get().peek(level, source);
               int stage = prog != null ? prog.crackStage() : -1;
               if (stage != lastStage) {
                  log("H2 MINECYCLE breaking… crackStage=" + stage);
                  lastStage = stage;
               }
            } else {
               break;
            }
         }
         log("H2 MINECYCLE break finished after " + guard + " ticks, state=" + st
            + ", blockNowAir=" + level.getBlockState(source).isAir());

         // --- DROPS: real ItemEntities should now be on the ground near the source. ---
         List<net.minecraft.world.entity.item.ItemEntity> drops = level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class, new AABB(source).inflate(5.0));
         log("H2 MINECYCLE drops on ground = " + drops.size()
            + (drops.isEmpty() ? "" : " first=" + safeId(drops.get(0).getItem().getItem())));

         // --- PICKUP: walk-to-each-drop until COMPLETE (drive several ticks; teleport-collect is distance-gated). ---
         int beforeCobble = v.countInv(net.minecraft.world.level.block.Blocks.COBBLESTONE.asItem());
         com.coderyo.jason.ops.OpState pst = null;
         guard = 0;
         while (guard++ < 200) {
            pst = com.coderyo.jason.ops.VillagerWorldOps.pickupTick(v, source);
            if (pst == com.coderyo.jason.ops.OpState.COMPLETE) {
               break;
            }
            // Nudge the villager onto the nearest drop so the distance-gated collect fires in the headless harness
            // (no real pathing tick between our calls here).
            if (!drops.isEmpty()) {
               var d = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(source).inflate(5.0));
               if (!d.isEmpty()) {
                  v.setPos(d.get(0).getX(), d.get(0).getY(), d.get(0).getZ());
               }
            }
         }
         int afterCobble = v.countInv(net.minecraft.world.level.block.Blocks.COBBLESTONE.asItem());
         log("H2 MINECYCLE pickup state=" + pst + " cobblestoneInInv " + beforeCobble + " -> " + afterCobble);

         // --- REGROW: schedule on the point, then force it due and tick it back. ---
         long now = level.getGameTime();
         com.coderyo.jason.ops.TaskPointStore.get().scheduleRegrow(level, source, sourceState, now, 0);
         int regrown = com.coderyo.jason.ops.TaskPointStore.get().tickRegrow(level, now);
         boolean back = level.getBlockState(source).getBlock() == sourceState.getBlock();
         log("H2 MINECYCLE regrow scheduled+ticked: regrown=" + regrown + " sourceRestored=" + back);

         mineCycleOk = toolOk && st == com.coderyo.jason.ops.OpState.COMPLETE && afterCobble > beforeCobble && back;
         log("H2 MINECYCLE " + (mineCycleOk ? "OK" : "PARTIAL")
            + ": tool=" + toolOk + " broke=" + (st == com.coderyo.jason.ops.OpState.COMPLETE)
            + " collected=" + (afterCobble > beforeCobble) + " regrew=" + back);

         // Clean up the test pad.
         level.removeBlock(source, false);
         level.removeBlock(source.below(), false);
      } catch (Throwable t) {
         mineCycleOk = false;
         recordException("H2:minecycle", t);
         log("H2 MINECYCLE FAIL: " + t);
      }
   }

   // ============================ STEP H3: player-like chop cycle (O2) ============================

   /**
    * Live evidence for the O2 player-like chop refactor: build a TALL tree (so upper logs are out of reach) next to
    * a real villager, equip an axe, and drive the {@link com.coderyo.jason.ops.VillagerWorldOps} reach-extension +
    * break + pickup primitives the migrated {@link org.millenaire.common.goal.GoalLumbermanChopTrees} now calls —
    * the WHOLE trunk felled, the leaves cleared, drops collected, and the temporary scaffold reclaimed. Logs each
    * phase greppable as {@code [MILLTEST] CHOPCYCLE ...}.
    */
   private void stepChopCycle() {
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive());
         if (villagers.isEmpty()) {
            log("H3 CHOPCYCLE SKIP: no MillVillager in world");
            return;
         }
         MillVillager v = villagers.get(0);

         // Build a clear pad: villager on solid ground, a tall trunk one block across so its top is OUT of reach.
         BlockPos vPos = v.blockPosition();
         BlockPos trunkBase = vPos.offset(1, 0, 0);
         // Clear a column of air around the trunk + give the villager solid footing so it doesn't fall.
         for (int dy = -1; dy <= 14; dy++) {
            for (int dx = -1; dx <= 2; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  BlockPos p = vPos.offset(dx, dy, dz);
                  level.setBlock(p, dy == -1
                     ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()
                     : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
         // A 12-tall oak trunk topped with a small leaf cap (upper logs at y+8..+11 are beyond the 4.5 reach).
         int trunkHeight = 12;
         for (int i = 0; i < trunkHeight; i++) {
            level.setBlock(trunkBase.above(i), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 3);
         }
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               level.setBlock(trunkBase.above(trunkHeight).offset(dx, 0, dz),
                  net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState(), 3);
            }
         }
         int logsPlaced = trunkHeight;
         log("H3 CHOPCYCLE built a " + trunkHeight + "-tall oak (top log at y=" + trunkBase.above(trunkHeight - 1).getY()
            + ", villager eyeY=" + String.format("%.1f", v.getEyePosition().y) + ")");

         // Equip the axe (strict-tool path) and verify ensureTool(AXE) accepts it.
         v.heldItem = new ItemStack(net.minecraft.world.item.Items.IRON_AXE);
         boolean toolOk = com.coderyo.jason.ops.VillagerWorldOps.ensureTool(v, com.coderyo.jason.ops.VillagerWorldOps.ToolKind.AXE);
         log("H3 CHOPCYCLE ensureTool(AXE) on held iron_axe = " + toolOk);

         // Anchor reach-extension / reclaim on the trunk base (the goal's dest point), as the goal does.
         BlockPos anchor = trunkBase;
         boolean scaffoldUsed = false;
         int maxScaffold = 0;

         // Drive the per-tick cycle directly: for each log lowest-first, ensureReach (scaffold if high) then break +
         // pickup; then clear leaves. We nudge the villager (no real pathing between our synchronous calls) onto the
         // climb column / drops so the distance-gated steps fire headlessly.
         int guard = 0;
         while (guard++ < 6000) {
            // Re-enumerate remaining logs/leaves from the world (broken ones are now air).
            BlockPos nextLog = lowestBlock(level, trunkBase, true);
            BlockPos nextLeaf = nextLog == null ? lowestBlock(level, trunkBase, false) : null;
            BlockPos target = nextLog != null ? nextLog : nextLeaf;
            if (target == null) {
               break; // whole tree (logs + leaves) gone.
            }

            if (!com.coderyo.jason.ops.VillagerWorldOps.withinReach(v, target)) {
               com.coderyo.jason.ops.OpState reach = com.coderyo.jason.ops.VillagerWorldOps.ensureReach(v, target, anchor);
               if (reach == com.coderyo.jason.ops.OpState.EXTENDING_REACH) {
                  scaffoldUsed = true;
                  var prog = com.coderyo.jason.ops.TaskPointStore.get().peek(level, anchor);
                  int col = prog != null ? prog.scaffoldColumn.size() : 0;
                  maxScaffold = Math.max(maxScaffold, col);
                  // Stand the villager on top of the column it has built so the next reach test/placement advances.
                  if (prog != null && !prog.scaffoldColumn.isEmpty()) {
                     BlockPos base = trunkBase.below(); // column rises from the villager's feet (vPos level)
                     // Highest tracked scaffold block:
                     long topPacked = prog.scaffoldColumn.get(prog.scaffoldColumn.size() - 1);
                     BlockPos top = BlockPos.of(topPacked);
                     v.setPos(top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5);
                  }
                  continue;
               }
               if (reach == com.coderyo.jason.ops.OpState.BLOCKED) {
                  log("H3 CHOPCYCLE reach BLOCKED on " + target + " — abandoning that block");
                  break;
               }
            }

            com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.breakTick(v, target);
            if (st == com.coderyo.jason.ops.OpState.APPROACHING) {
               // Pull the villager adjacent to the target so the synchronous loop makes progress.
               v.setPos(target.getX() + 1.5, target.getY(), target.getZ() + 0.5);
               continue;
            }
            if (st == com.coderyo.jason.ops.OpState.COMPLETE) {
               // Collect this block's drops (nudge onto them, like the mine-cycle harness).
               int pg = 0;
               while (pg++ < 100) {
                  com.coderyo.jason.ops.OpState pst = com.coderyo.jason.ops.VillagerWorldOps.pickupTick(v, target);
                  if (pst == com.coderyo.jason.ops.OpState.COMPLETE) {
                     break;
                  }
                  var d = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(target).inflate(5.0));
                  if (!d.isEmpty()) {
                     v.setPos(d.get(0).getX(), d.get(0).getY(), d.get(0).getZ());
                  }
               }
            }
         }

         // Reclaim the climb column.
         var progBefore = com.coderyo.jason.ops.TaskPointStore.get().peek(level, anchor);
         int columnBeforeReclaim = progBefore != null ? progBefore.scaffoldColumn.size() : 0;
         com.coderyo.jason.ops.VillagerWorldOps.reclaimReach(v, anchor);
         var progAfter = com.coderyo.jason.ops.TaskPointStore.get().peek(level, anchor);
         int columnAfterReclaim = progAfter != null ? progAfter.scaffoldColumn.size() : 0;

         // Verify the whole tree is gone and no scaffolding remains.
         boolean allLogsGone = lowestBlock(level, trunkBase, true) == null;
         boolean allLeavesGone = lowestBlock(level, trunkBase, false) == null;
         int scaffoldLeft = 0;
         for (int dy = -1; dy <= 16; dy++) {
            for (int dx = -2; dx <= 3; dx++) {
               for (int dz = -2; dz <= 2; dz++) {
                  if (level.getBlockState(vPos.offset(dx, dy, dz)).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)) {
                     scaffoldLeft++;
                  }
               }
            }
         }
         int logsCollected = v.countInv(net.minecraft.world.level.block.Blocks.OAK_LOG.asItem(), 0);

         log("H3 CHOPCYCLE result: logsPlaced=" + logsPlaced + " allLogsGone=" + allLogsGone
            + " allLeavesGone=" + allLeavesGone + " scaffoldUsed=" + scaffoldUsed + " maxScaffoldColumn=" + maxScaffold
            + " columnReclaimed=" + columnBeforeReclaim + "->" + columnAfterReclaim
            + " scaffoldBlocksLeftInWorld=" + scaffoldLeft + " oakLogsInInv=" + logsCollected);

         chopCycleOk = toolOk && allLogsGone && allLeavesGone && scaffoldUsed && scaffoldLeft == 0;
         log("H3 CHOPCYCLE " + (chopCycleOk ? "OK" : "PARTIAL")
            + ": tool=" + toolOk + " wholeTreeFelled=" + (allLogsGone && allLeavesGone)
            + " scaffoldUsedForTallTree=" + scaffoldUsed + " scaffoldReclaimed=" + (scaffoldLeft == 0));

         // Clean up the pad.
         for (int dy = -1; dy <= 16; dy++) {
            for (int dx = -2; dx <= 3; dx++) {
               for (int dz = -2; dz <= 2; dz++) {
                  level.removeBlock(vPos.offset(dx, dy, dz), false);
               }
            }
         }
      } catch (Throwable t) {
         chopCycleOk = false;
         recordException("H3:chopcycle", t);
         log("H3 CHOPCYCLE FAIL: " + t);
      }
   }

   /**
    * Lowest remaining log (or leaf) of the test trunk, scanning the 1.12-style box around {@code base}. Returns
    * null when none remain. Mirrors the goal's lowest-first felling order.
    */
   private BlockPos lowestBlock(ServerLevel level, BlockPos base, boolean wantLog) {
      BlockPos best = null;
      for (int dy = -2; dy <= 16; dy++) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               BlockPos p = base.offset(dx, dy, dz);
               Block b = level.getBlockState(p).getBlock();
               boolean match = wantLog
                  ? b == net.minecraft.world.level.block.Blocks.OAK_LOG
                  : b == net.minecraft.world.level.block.Blocks.OAK_LEAVES;
               if (match && (best == null || p.getY() < best.getY())) {
                  best = p;
               }
            }
         }
      }
      return best;
   }

   // ============================ STEP H4: player-like farm cycle (O3) ============================

   /**
    * Live evidence for the O3 player-like farm refactor: lay a row of wheat crops next to a real villager — most
    * RIPE (age 7), one IMMATURE (age 2) — equip a hoe, and drive the {@link com.coderyo.jason.ops.VillagerWorldOps}
    * primitives the migrated {@link org.millenaire.common.goal.generic.GoalGenericHarvestCrop} now calls: for each
    * MATURE crop, {@code breakTick} (0-hardness → instant break this tick, real wheat+seed drops) → {@code pickupTick}
    * (walk to each drop) → AUTO-REPLANT a fresh age-0 crop in place. The IMMATURE crop must be SKIPPED (left to grow).
    * Mirrors the Python sim's {@code run_farm}. Each phase greppable as {@code [MILLTEST] FARMCYCLE ...}.
    */
   private void stepFarmCycle() {
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive());
         if (villagers.isEmpty()) {
            log("H4 FARMCYCLE SKIP: no MillVillager in world");
            return;
         }
         MillVillager v = villagers.get(0);

         net.minecraft.world.level.block.CropBlock wheat = (net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT;
         int ripeAge = wheat.getMaxAge();        // 7
         int immatureAge = 2;

         // Build a flat farm pad: farmland row at vPos.y-1, crops at vPos.y. 5 crops; index 0 is immature.
         BlockPos vPos = v.blockPosition();
         int row = 5;
         BlockPos[] cropPos = new BlockPos[row];
         for (int i = 0; i < row; i++) {
            BlockPos crop = vPos.offset(2, 0, i - 2); // a row beside the villager, within reach.
            cropPos[i] = crop;
            // Clear above, place farmland below, plant the crop.
            level.setBlock(crop.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(crop.below(), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(), 3);
            int age = (i == 0) ? immatureAge : ripeAge;
            level.setBlock(crop, wheat.getStateForAge(age), 3);
         }
         log("H4 FARMCYCLE laid " + row + " wheat crops (1 immature age=" + immatureAge + ", " + (row - 1) + " ripe age=" + ripeAge + ")");

         // Equip a hoe (the harvest goal's travelling tool) and seed the villager's stock so the replant can consume.
         v.heldItem = new ItemStack(net.minecraft.world.item.Items.IRON_HOE);
         v.addToInv(net.minecraft.world.item.Items.WHEAT_SEEDS, 16);
         int seedsBefore = v.countInv(net.minecraft.world.item.Items.WHEAT_SEEDS, 0);
         int wheatBefore = v.countInv(net.minecraft.world.item.Items.WHEAT, 0);

         int harvested = 0;
         int replanted = 0;
         int skippedImmature = 0;

         for (int i = 0; i < row; i++) {
            BlockPos crop = cropPos[i];
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(crop);
            boolean ripe = state.getBlock() == wheat && wheat.getAge(state) >= ripeAge;
            if (!ripe) {
               skippedImmature++;
               continue; // MATURE-only: immature crop is left to grow (sim: skip age < max).
            }

            // Nudge the villager adjacent so the reach-gated break/pickup fire in the synchronous harness loop.
            v.setPos(crop.getX() + 0.5, crop.getY(), crop.getZ() + 1.2);

            // BREAK: a 0-hardness ripe crop must break in a SINGLE breakTick (the 0-hardness guard).
            com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.breakTick(v, crop);
            int guard = 0;
            while (st != com.coderyo.jason.ops.OpState.COMPLETE && st != com.coderyo.jason.ops.OpState.BLOCKED && guard++ < 50) {
               if (st == com.coderyo.jason.ops.OpState.APPROACHING) {
                  v.setPos(crop.getX() + 0.5, crop.getY(), crop.getZ() + 1.2);
               }
               st = com.coderyo.jason.ops.VillagerWorldOps.breakTick(v, crop);
            }
            boolean brokeToAir = level.getBlockState(crop).isAir();
            log("H4 FARMCYCLE crop[" + i + "] break state=" + st + " after " + guard + " extra ticks, nowAir=" + brokeToAir);
            if (st != com.coderyo.jason.ops.OpState.COMPLETE) {
               continue;
            }
            harvested++;

            // PICKUP: walk to each real wheat/seed drop and collect it.
            int pg = 0;
            com.coderyo.jason.ops.OpState pst = com.coderyo.jason.ops.VillagerWorldOps.pickupTick(v, crop);
            while (pst != com.coderyo.jason.ops.OpState.COMPLETE && pg++ < 100) {
               var d = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(crop).inflate(5.0));
               if (!d.isEmpty()) {
                  v.setPos(d.get(0).getX(), d.get(0).getY(), d.get(0).getZ());
               }
               pst = com.coderyo.jason.ops.VillagerWorldOps.pickupTick(v, crop);
            }

            // AUTO-REPLANT: place a fresh age-0 crop, consuming one seed (mirrors the goal's replant()).
            int taken = v.takeFromInv(net.minecraft.world.item.Items.WHEAT_SEEDS, 0, 1);
            if (taken > 0 && level.getBlockState(crop).isAir()) {
               v.setBlockstate(new Point(crop), wheat.defaultBlockState());
               replanted++;
            }
         }

         // Verify: only mature harvested; all harvested plots now hold a fresh age-0 crop; immature untouched.
         int freshReplants = 0;
         for (int i = 1; i < row; i++) { // index 0 is the immature one.
            net.minecraft.world.level.block.state.BlockState s = level.getBlockState(cropPos[i]);
            if (s.getBlock() == wheat && wheat.getAge(s) == 0) {
               freshReplants++;
            }
         }
         net.minecraft.world.level.block.state.BlockState immatureState = level.getBlockState(cropPos[0]);
         boolean immatureUntouched = immatureState.getBlock() == wheat && wheat.getAge(immatureState) == immatureAge;
         int seedsAfter = v.countInv(net.minecraft.world.item.Items.WHEAT_SEEDS, 0);
         int wheatAfter = v.countInv(net.minecraft.world.item.Items.WHEAT, 0);

         log("H4 FARMCYCLE result: harvested=" + harvested + " (expected " + (row - 1) + ")"
            + " replanted=" + replanted + " freshAge0Crops=" + freshReplants
            + " skippedImmature=" + skippedImmature + " immatureUntouched=" + immatureUntouched
            + " seeds " + seedsBefore + "->" + seedsAfter + " (net " + (seedsAfter - seedsBefore)
            + ", picked-up + replant) wheatInv " + wheatBefore + "->" + wheatAfter);

         farmCycleOk = harvested == (row - 1)
            && freshReplants == (row - 1)
            && skippedImmature == 1
            && immatureUntouched;
         log("H4 FARMCYCLE " + (farmCycleOk ? "OK" : "PARTIAL")
            + ": onlyMatureHarvested=" + (harvested == (row - 1) && skippedImmature == 1)
            + " autoReplantedInPlace=" + (freshReplants == (row - 1))
            + " immatureLeftAlone=" + immatureUntouched);

         // Clean up the farm pad.
         for (int i = 0; i < row; i++) {
            level.removeBlock(cropPos[i], false);
            level.removeBlock(cropPos[i].below(), false);
         }
      } catch (Throwable t) {
         farmCycleOk = false;
         recordException("H4:farmcycle", t);
         log("H4 FARMCYCLE FAIL: " + t);
      }
   }

   // ============================ STEP I: summary + stop ============================

   private void stepSummary() {
      int villagesCreated = 0;
      for (Boolean ok : villageByCulture.values()) {
         if (Boolean.TRUE.equals(ok)) {
            villagesCreated++;
         }
      }
      int totalVillagers = countVillagersInWorld();
      MillWorldData mw = Mill.getMillWorld(level);
      int totalBuildings = mw != null ? mw.allBuildings().size() : buildingsReported;

      log("===== SUMMARY =====");
      log("villages created: " + villagesCreated + "/" + villageByCulture.size() + " cultures");
      for (Map.Entry<String, Boolean> e : villageByCulture.entrySet()) {
         log("  culture " + e.getKey() + ": " + (Boolean.TRUE.equals(e.getValue()) ? "OK" : "FAIL")
            + " (" + villageDetailByCulture.getOrDefault(e.getKey(), "") + ")");
      }
      log("total villagers in world: " + totalVillagers);
      log("MOVEMENT: " + movementSummary);
      if (blockActivityByVillage.isEmpty()) {
         log("BLOCKACTIVITY: none recorded");
      } else {
         int totalChanged = 0;
         for (Map.Entry<String, Integer> e : blockActivityByVillage.entrySet()) {
            totalChanged += e.getValue();
            log("BLOCKACTIVITY: village=" + e.getKey() + " blocksChanged=" + e.getValue());
         }
         log("BLOCKACTIVITY: totalBlocksChanged=" + totalChanged + " across " + blockActivityByVillage.size() + " village(s)");
      }
      log("GOALS: " + goalSummary);
      log("buildings built: " + totalBuildings + " (incomplete after growth: " + highFailureBuildings + ")");
      log("items tested: " + itemsTested + " / failed: " + itemsFailed);
      log("blocks tested: " + blocksTested + " / failed: " + blocksFailed);
      log("trade: " + (tradeOk == null ? "not run" : (tradeOk ? "OK" : "FAIL")) + " (" + tradeDetail + ")");
      log("interaction: " + (interactOk == null ? "not run" : (interactOk ? "OK" : "FAIL")) + " (" + interactDetail + ")");
      log("mine cycle (O1 break+pickup+regrow): " + (mineCycleOk == null ? "not run" : (mineCycleOk ? "OK" : "PARTIAL/FAIL")));
      log("chop cycle (O2 whole-tree+leaves+scaffold+pickup+reclaim): " + (chopCycleOk == null ? "not run" : (chopCycleOk ? "OK" : "PARTIAL/FAIL")));
      log("farm cycle (O3 only-mature harvest+pickup+auto-replant, immature skipped): " + (farmCycleOk == null ? "not run" : (farmCycleOk ? "OK" : "PARTIAL/FAIL")));
      log("distinct exception types seen: " + distinctExceptions.size());
      for (Map.Entry<String, Integer> e : distinctExceptions.entrySet()) {
         log("  exception x" + e.getValue() + ": " + e.getKey());
      }
      log("===== END SUMMARY =====");
   }

   private void stopServer() {
      if (halted) {
         return;
      }
      halted = true;
      try {
         // Release any forced chunks before stopping (tidy shutdown).
         MillWorldData mw = Mill.getMillWorld(level);
         if (mw != null && level != null) {
            for (Point p : new ArrayList<>(mw.villagesList.pos)) {
               int cx = p.getiX() >> 4;
               int cz = p.getiZ() >> 4;
               for (int dx = -2; dx <= 2; dx++) {
                  for (int dz = -2; dz <= 2; dz++) {
                     try {
                        level.setChunkForced(cx + dx, cz + dz, false);
                     } catch (Throwable ignored) {
                     }
                  }
               }
            }
         }
      } catch (Throwable ignored) {
      }
      log("self-test complete — halting server.");
      try {
         server.halt(false);
      } catch (Throwable t) {
         MillLog.printException(TAG + " server.halt failed", t);
      }
   }

   // ============================ helpers ============================

   private int countVillagersInWorld() {
      try {
         return level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive()
         ).size();
      } catch (Throwable t) {
         return -1;
      }
   }

   private void recordException(String where, Throwable t) {
      String key = t.getClass().getName() + ": " + String.valueOf(t.getMessage());
      distinctExceptions.merge(key, 1, Integer::sum);
   }

   private static String safeId(Item item) {
      try {
         return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
      } catch (Throwable t) {
         return String.valueOf(item);
      }
   }

   private static String safeBlockId(Block block) {
      try {
         return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
      } catch (Throwable t) {
         return String.valueOf(block);
      }
   }

   private static void log(String s) {
      MillLog.major(null, TAG + " " + s);
   }
}
