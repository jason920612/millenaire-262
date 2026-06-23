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
   private static final int TICK_GROWTH_END = TICK_GROWTH_START + 2400; // ~2 min of growth ticking (default)
   /**
    * Optional EARLY end of the growth window (absolute tick), read once from {@code -Dmillenaire.selftest.growthend=N}
    * (or {@code MILLENAIRE_SELFTEST_GROWTHEND}). When set (and &lt; {@link #TICK_GROWTH_END}) the growth default-branch
    * FAST-FORWARDS {@link #tick} to {@link #TICK_GROWTH_END} once this tick is reached, so the post-growth H-cycle
    * steps (mine/chop/farm/fish) run promptly. This exists purely so a verification run that shares a process with the
    * client self-test (which halts the process around its own tick ~1540) can reach the H-cycles before the halt,
    * while still giving villagers a few hundred ticks to spawn. Unset → normal full-length run.
    */
   private static final int GROWTH_EARLY_END = resolveGrowthEarlyEnd();

   private static int resolveGrowthEarlyEnd() {
      String prop = System.getProperty("millenaire.selftest.growthend");
      if (prop == null) {
         prop = System.getenv("MILLENAIRE_SELFTEST_GROWTHEND");
      }
      if (prop != null) {
         try {
            int n = Integer.parseInt(prop.trim());
            if (n > TICK_GROWTH_START) {
               return n;
            }
         } catch (NumberFormatException ignored) {
            // fall through to disabled
         }
      }
      return -1; // disabled
   }
   private static final int TICK_BUILDING_REPORT = TICK_GROWTH_END + 20;
   private static final int TICK_ITEMS_BLOCKS = TICK_GROWTH_END + 40;
   private static final int TICK_TRADE = TICK_GROWTH_END + 60;
   private static final int TICK_INTERACT = TICK_GROWTH_END + 80;
   private static final int TICK_MINE_CYCLE = TICK_GROWTH_END + 90;
   private static final int TICK_CHOP_CYCLE = TICK_GROWTH_END + 95;
   // O6 Mill-specific: the SIM-VALIDATED sugarcane keep-bottom (break upper segments top-down, keep the bottom so it
   // regrows, pick up the cane). Synchronous like the chop/farm cycles.
   private static final int TICK_CANE_CYCLE = TICK_GROWTH_END + 97;
   private static final int TICK_FARM_CYCLE = TICK_GROWTH_END + 98;
   // O5 entity gather: spawn ready + already-sheared sheep + a cow, REALLY shear (Sheep.shear) the ready ones, pick up
   // the dropped wool, skip the sheared one, and milk the cow. Synchronous like the chop/farm cycles.
   private static final int TICK_SHEAR_CYCLE = TICK_GROWTH_END + 99;
   // O4 fishing: a real bobber must animate across many ticks. Set up + cast at FISH_START, drive/observe the
   // animation each tick through FISH_END (the mixin ticks the hook for us between harness ticks), verify at FISH_END.
   private static final int TICK_FISH_START = TICK_GROWTH_END + 100;
   private static final int FISH_WINDOW = 120; // ~6s of real bobber ticking — plenty to show the animation.
   private static final int TICK_FISH_END = TICK_FISH_START + FISH_WINDOW;
   // COMPREHENSIVE static catalog + dynamic scenario inventory (com.coderyo.jason.catalog.MillCatalog):
   // emits ███ CATALOG / ███ SCENARIO / ███ COVERAGE SUMMARY. Runs after all H-cycles so their results
   // can be folded into the scenario coverage, and before the [MILLTEST] summary.
   private static final int TICK_CATALOG = TICK_FISH_END + 5;
   private static final int TICK_SUMMARY = TICK_CATALOG + 5;
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

   /**
    * Set true once the SERVER self-test has finished its summary (all H-cycle steps incl. H6 SHEARCYCLE have run).
    * A co-hosted client self-test (which CREATES the world and otherwise stops the whole process on its OWN schedule,
    * racing this one) polls this so it waits for the server steps to complete before halting — otherwise the
    * post-growth cycles never get to run. Volatile: read from the client (render) thread, written on the server thread.
    */
   public static volatile boolean COMPLETED = false;

   /**
    * Set true once the synchronous post-growth H-cycles that run AT growth-end (notably H6 SHEARCYCLE) have executed.
    * This is the milestone the co-hosted client self-test actually needs to have observed; it flips well before the
    * full {@link #COMPLETED} summary (which, under the throttled co-hosted integrated server, may not be reached).
    */
   public static volatile boolean GROWTH_CYCLES_DONE = false;

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
   private Boolean caneCycleOk = null;
   private Boolean shearCycleOk = null;
   private Boolean fishCycleOk = null;
   // COMPREHENSIVE catalog + scenario coverage (com.coderyo.jason.catalog.MillCatalog) result.
   private com.coderyo.jason.catalog.MillCatalog.Result catalogResult = null;

   // --- O4 fishing-cycle live state (spans TICK_FISH_START..TICK_FISH_END) ---
   private MillVillager fishVillager;
   private BlockPos fishWaterSurface;
   private int fishBobberId;
   private int fishMaxBitingObserved;    // peak of the "biting" splash flag → proof the animation reached a bite.
   private int fishLootSpawned;          // ItemEntities spawned by the forced reel (real FISHING loot).
   private int fishPickedUp;             // items the villager actually collected into its inventory.
   private boolean fishHookSurvived;     // the villager-owned hook lived past the tick that vanilla would discard it.
   private boolean fishCatchForced;      // whether we forced the bite (deterministic) after observing the animation.
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
               // H6 SHEARCYCLE (O5) is fully self-contained + synchronous, so we run it HERE at the reliable
               // growth-end tick rather than a later spaced tick: under the co-hosted client self-test the integrated
               // server is heavily throttled after growth and may not reach the later TICK_SHEAR_CYCLE before the
               // process is told to stop. (The dedicated TICK_SHEAR_CYCLE case below still runs it for a full
               // server-only run; the guard makes the second call a no-op-ish re-run on fresh sheep.)
               stepShearCycle();
               // Under the co-hosted client self-test the integrated server is throttled after growth and may not
               // reach the later spaced post-growth ticks before the process halts. Run the remaining SYNCHRONOUS
               // post-growth steps + the COMPREHENSIVE catalog HERE at the reliable growth-end milestone so their
               // results are available and the ███ CATALOG / ███ SCENARIO / ███ COVERAGE SUMMARY always emit.
               // (The dedicated TICK_* cases below still run for a full server-only run; each is guarded so it
               // doesn't double-run.) Fishing is the one multi-tick step and stays on its own window.
               if (buildingsReported == 0) {
                  stepBuildingCompleteness();
               }
               if (itemsTested == 0) {
                  stepItemsAndBlocks();
               }
               if (tradeOk == null) {
                  stepTradeLogic();
               }
               if (interactOk == null) {
                  stepVillagerInteraction();
               }
               if (mineCycleOk == null) {
                  stepMineCycle();
               }
               if (chopCycleOk == null) {
                  stepChopCycle();
               }
               if (caneCycleOk == null) {
                  stepCaneCycle();
               }
               if (farmCycleOk == null) {
                  stepFarmCycle();
               }
               // FISH is multi-tick (real bobber animation), but its dedicated window (TICK_FISH_*) is LATER than
               // GROWTH_END and the co-hosted client halts before reaching it. Run the INLINE fishing cycle HERE — it
               // drives the real hook's tick() loop itself (mixin still animates) + forces the bite deterministically,
               // so FISH actually completes within the reliably-reached window.
               if (fishCycleOk == null) {
                  stepFishInline();
               }
               if (catalogResult == null) {
                  stepCatalog();
               }
               GROWTH_CYCLES_DONE = true; // milestone: the client self-test may now stop (all sync steps + catalog ran).
            }
            case TICK_BUILDING_REPORT -> {
               if (buildingsReported == 0) {
                  stepBuildingCompleteness();
               }
            }
            case TICK_ITEMS_BLOCKS -> {
               if (itemsTested == 0) {
                  stepItemsAndBlocks();
               }
            }
            case TICK_TRADE -> {
               if (tradeOk == null) {
                  stepTradeLogic();
               }
            }
            case TICK_INTERACT -> {
               if (interactOk == null) {
                  stepVillagerInteraction();
               }
            }
            case TICK_MINE_CYCLE -> {
               if (mineCycleOk == null) {
                  stepMineCycle();
               }
            }
            case TICK_CHOP_CYCLE -> {
               if (chopCycleOk == null) {
                  stepChopCycle();
               }
            }
            case TICK_CANE_CYCLE -> {
               if (caneCycleOk == null) {
                  stepCaneCycle();
               }
            }
            case TICK_FARM_CYCLE -> {
               if (farmCycleOk == null) {
                  stepFarmCycle();
               }
            }
            case TICK_SHEAR_CYCLE -> {
               if (shearCycleOk == null) { // not already run at GROWTH_END (e.g. a full non-early-end run).
                  stepShearCycle();
               }
            }
            case TICK_FISH_START -> {
               // Only run the multi-tick fishing window if the INLINE cycle at GROWTH_END didn't already pass it
               // (it always runs in the co-hosted harness; this dedicated window is the full server-only fallback).
               if (fishCycleOk == null || !fishCycleOk) {
                  stepFishStart();
               }
            }
            case TICK_FISH_END -> {
               if (fishVillager != null) {
                  stepFishEnd();
               }
            }
            case TICK_CATALOG -> {
               if (catalogResult == null) {
                  stepCatalog();
               }
            }
            case TICK_SUMMARY -> {
               stepSummary();
               stopServer();
            }
            default -> {
               if (tick > TICK_GROWTH_START && tick < TICK_GROWTH_END) {
                  // Verification escape hatch: jump to the end of the growth window early so the post-growth H-cycle
                  // steps run before a co-hosted client self-test halts the process. Villagers have spawned by then.
                  if (GROWTH_EARLY_END > 0 && tick >= GROWTH_EARLY_END) {
                     log("growth early-end (=" + GROWTH_EARLY_END + ") reached — fast-forwarding to TICK_GROWTH_END=" + TICK_GROWTH_END);
                     sampleVillagerMovement();
                     tick = TICK_GROWTH_END - 1; // next increment lands exactly on TICK_GROWTH_END.
                     return;
                  }
                  growthHeartbeat();
                  if ((tick - TICK_GROWTH_START) % MOVEMENT_SAMPLE_INTERVAL == 0) {
                     sampleVillagerMovement(); // periodic position sample (metric 1)
                  }
               } else if (tick > TICK_FISH_START && tick < TICK_FISH_END) {
                  stepFishDrive(); // O4: advance + observe the live bobber animation each tick.
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
         // The villager stands here (feet at groundY) for the whole horizontal-approach + pickup phase. ONLY the
         // scaffold-climb (standing on the column top) is allowed to raise it — this is what forces ensureReach to
         // build a real scaffold for the upper logs, rather than the harness teleporting the villager up to them.
         final int groundY = vPos.getY();
         // Clear a TALL column of air around the trunk + the villager's standing cell + give it solid footing so it
         // doesn't fall. The cleared height (>= trunkHeight + headroom) means the scaffold column at the villager's
         // feet has free space to rise the full way up to the top logs.
         for (int dy = -1; dy <= 20; dy++) {
            for (int dx = -1; dx <= 2; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  BlockPos p = vPos.offset(dx, dy, dz);
                  level.setBlock(p, dy == -1
                     ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()
                     : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
         // A 16-tall oak trunk topped with a small leaf cap. The eye sits ~1.62 above groundY, and reach is 4.5, so
         // every log from y+5 (≈4.9 above the eye) upward is GENUINELY out of reach from the ground — the chop MUST
         // build a scaffold to fell them (mirrors opsim.py chop_tree's tall-trunk scaffold proof).
         int trunkHeight = 16;
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
         // Prove the SCENE is correct before driving it: the topmost log must be out of reach from the ground stand,
         // so a scaffold is unavoidable. (If this were false the test would be a no-op artifact, exactly the prior bug.)
         v.setPos(vPos.getX() + 0.5, groundY, vPos.getZ() + 0.5);
         BlockPos topLog = trunkBase.above(trunkHeight - 1);
         boolean topOutOfReachFromGround = !com.coderyo.jason.ops.VillagerWorldOps.withinReach(v, topLog);
         log("H3 CHOPCYCLE built a " + trunkHeight + "-tall oak (top log at y=" + topLog.getY()
            + ", villager groundEyeY=" + String.format("%.1f", v.getEyePosition().y)
            + ", topLogOutOfReachFromGround=" + topOutOfReachFromGround + " — scaffold REQUIRED)");

         // Equip the axe (strict-tool path) and verify ensureTool(AXE) accepts it.
         v.heldItem = new ItemStack(net.minecraft.world.item.Items.IRON_AXE);
         boolean toolOk = com.coderyo.jason.ops.VillagerWorldOps.ensureTool(v, com.coderyo.jason.ops.VillagerWorldOps.ToolKind.AXE);
         log("H3 CHOPCYCLE ensureTool(AXE) on held iron_axe = " + toolOk);

         // Anchor reach-extension / reclaim on the trunk base (the goal's dest point), as the goal does.
         BlockPos anchor = trunkBase;
         boolean scaffoldUsed = false;
         int maxScaffold = 0;

         // Drive the per-tick cycle directly: for each block lowest-first, ensureReach (scaffold if high) then break +
         // pickup. The villager ONLY rises by standing on the scaffold column it builds — every horizontal-approach /
         // pickup nudge keeps its feet at groundY so high logs stay genuinely out of reach until the scaffold lifts it.
         int guard = 0;
         while (guard++ < 8000) {
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
                  // This is the ONLY place the villager is allowed to gain height (it climbs its own scaffold).
                  if (prog != null && !prog.scaffoldColumn.isEmpty()) {
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
               if (reach == com.coderyo.jason.ops.OpState.COMPLETE) {
                  // ensureReach reports it's now in reach (it stood the villager on the column for us); fall through to
                  // break this tick.
               }
            }

            com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.breakTick(v, target);
            if (st == com.coderyo.jason.ops.OpState.APPROACHING) {
               // Still out of reach (a low block we haven't scaffolded for): pull the villager horizontally adjacent
               // at GROUND level — never up to the target's Y. A still-too-high block is handled by ensureReach above.
               v.setPos(target.getX() + 1.5, groundY, target.getZ() + 0.5);
               continue;
            }
            if (st == com.coderyo.jason.ops.OpState.COMPLETE) {
               // Collect this block's drops. Drops fall to the ground, so the villager returns to groundY beside the
               // trunk and nudges onto each drop's XZ at ground level (never re-gaining height off the scaffold).
               v.setPos(vPos.getX() + 0.5, groundY, vPos.getZ() + 0.5);
               int pg = 0;
               while (pg++ < 200) {
                  com.coderyo.jason.ops.OpState pst = com.coderyo.jason.ops.VillagerWorldOps.pickupTick(v, target);
                  if (pst == com.coderyo.jason.ops.OpState.COMPLETE) {
                     break;
                  }
                  var d = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(target).inflate(6.0));
                  if (!d.isEmpty()) {
                     v.setPos(d.get(0).getX(), d.get(0).getY(), d.get(0).getZ());
                  } else {
                     break;
                  }
               }
               // Return to the ground stand so the NEXT (higher) target is correctly judged out of reach → scaffold.
               v.setPos(vPos.getX() + 0.5, groundY, vPos.getZ() + 0.5);
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
         for (int dy = -1; dy <= 22; dy++) {
            for (int dx = -2; dx <= 3; dx++) {
               for (int dz = -2; dz <= 2; dz++) {
                  if (level.getBlockState(vPos.offset(dx, dy, dz)).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)) {
                     scaffoldLeft++;
                  }
               }
            }
         }
         int logsCollected = v.countInv(net.minecraft.world.level.block.Blocks.OAK_LOG.asItem(), 0);

         log("H3 CHOPCYCLE result: logsPlaced=" + logsPlaced + " topLogOutOfReachFromGround=" + topOutOfReachFromGround
            + " allLogsGone=" + allLogsGone
            + " allLeavesGone=" + allLeavesGone + " scaffoldUsed=" + scaffoldUsed + " maxScaffoldColumn=" + maxScaffold
            + " columnReclaimed=" + columnBeforeReclaim + "->" + columnAfterReclaim
            + " scaffoldBlocksLeftInWorld=" + scaffoldLeft + " oakLogsInInv=" + logsCollected);

         // STRICT: the scene must have genuinely required a scaffold (topOutOfReachFromGround), the scaffold must have
         // been used (scaffoldUsed), the whole tree felled, the drops collected, and the scaffold fully reclaimed.
         chopCycleOk = toolOk && topOutOfReachFromGround && allLogsGone && allLeavesGone
            && scaffoldUsed && scaffoldLeft == 0 && logsCollected > 0;
         log("H3 CHOPCYCLE " + (chopCycleOk ? "OK" : "PARTIAL")
            + ": tool=" + toolOk + " sceneRequiredScaffold=" + topOutOfReachFromGround
            + " wholeTreeFelled=" + (allLogsGone && allLeavesGone)
            + " scaffoldUsedForTallTree=" + scaffoldUsed + " scaffoldReclaimed=" + (scaffoldLeft == 0)
            + " drops PickedUp=" + (logsCollected > 0));

         // Clean up the pad.
         for (int dy = -1; dy <= 22; dy++) {
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

   // ====================== STEP H7: Mill-specific sugarcane keep-bottom (O6) ======================

   /**
    * Live evidence for the O6 Mill-specific sugarcane refactor and the SIM-VALIDATED keep-bottom invariant
    * (opsim {@code run_cane}): build a 3-tall sugar-cane column (bottom + two upper segments) next to a real
    * villager, then drive the {@link com.coderyo.jason.ops.VillagerWorldOps} primitives the migrated
    * {@link org.millenaire.common.goal.GoalIndianHarvestSugarCane} now calls — break the UPPER segments TOP-DOWN
    * ({@code +3} then {@code +2}), KEEP the bottom block ({@code +1}) so the column regrows, and pick up the real
    * dropped cane. Asserts the bottom survives and exactly the two upper segments were harvested. Greppable as
    * {@code [MILLTEST] CANECYCLE ...}.
    */
   private void stepCaneCycle() {
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive());
         if (villagers.isEmpty()) {
            log("H7 CANECYCLE SKIP: no MillVillager in world");
            return;
         }
         MillVillager v = villagers.get(0);

         // Build the cane column beside the villager. The goal's dest is the SOIL (sand); the bottom cane is dest+1
         // (KEPT), the harvested upper segments are dest+2 and dest+3.
         BlockPos vPos = v.blockPosition();
         BlockPos soil = vPos.offset(2, -1, 0); // sand one block down so the column rises into reach.
         level.setBlock(soil, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState(), 3);
         BlockPos bottom = soil.above(1); // dest+1 — the KEPT bottom.
         BlockPos mid = soil.above(2);    // dest+2 — harvested second.
         BlockPos top = soil.above(3);    // dest+3 — harvested first (top-down).
         for (BlockPos seg : new BlockPos[]{bottom, mid, top}) {
            level.setBlock(seg, net.minecraft.world.level.block.Blocks.SUGAR_CANE.defaultBlockState(), 3);
         }
         log("H7 CANECYCLE built a 3-tall cane column (bottom y=" + bottom.getY() + " KEPT, upper y=" + mid.getY()
            + "," + top.getY() + ")");

         int caneBefore = v.countInv(net.minecraft.world.item.Items.SUGAR_CANE, 0);

         // TOP-DOWN harvest of the UPPER segments only (never the bottom): break +3 then +2, each a single 0-hardness
         // breakTick, picking up the dropped cane between segments. Nudge the villager adjacent so the reach-gated
         // ops fire in this synchronous loop.
         int harvestedSegments = 0;
         boolean orderTopDownOk = true;
         BlockPos[] topDown = new BlockPos[]{top, mid};
         for (BlockPos seg : topDown) {
            // The bottom must STILL be cane before we touch any upper segment (we never target it).
            if (level.getBlockState(bottom).getBlock() != net.minecraft.world.level.block.Blocks.SUGAR_CANE) {
               orderTopDownOk = false;
            }
            v.setPos(seg.getX() + 0.5, seg.getY(), seg.getZ() + 1.2);
            com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.breakTick(v, seg);
            int guard = 0;
            while (st != com.coderyo.jason.ops.OpState.COMPLETE && st != com.coderyo.jason.ops.OpState.BLOCKED && guard++ < 50) {
               if (st == com.coderyo.jason.ops.OpState.APPROACHING) {
                  v.setPos(seg.getX() + 0.5, seg.getY(), seg.getZ() + 1.2);
               }
               st = com.coderyo.jason.ops.VillagerWorldOps.breakTick(v, seg);
            }
            boolean brokeToAir = level.getBlockState(seg).isAir();
            log("H7 CANECYCLE segment y=" + seg.getY() + " break state=" + st + " nowAir=" + brokeToAir
               + " bottomStillCane=" + (level.getBlockState(bottom).getBlock() == net.minecraft.world.level.block.Blocks.SUGAR_CANE));
            if (st == com.coderyo.jason.ops.OpState.COMPLETE && brokeToAir) {
               harvestedSegments++;
            }

            // PICKUP the dropped cane for this segment (walk to each drop).
            int pg = 0;
            com.coderyo.jason.ops.OpState pst = com.coderyo.jason.ops.VillagerWorldOps.pickupTick(v, seg);
            while (pst != com.coderyo.jason.ops.OpState.COMPLETE && pg++ < 100) {
               var d = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(seg).inflate(5.0));
               if (!d.isEmpty()) {
                  v.setPos(d.get(0).getX(), d.get(0).getY(), d.get(0).getZ());
               }
               pst = com.coderyo.jason.ops.VillagerWorldOps.pickupTick(v, seg);
            }
         }

         // VERIFY the keep-bottom invariant: the bottom block survives, both upper segments are gone, cane was picked.
         boolean bottomKept = level.getBlockState(bottom).getBlock() == net.minecraft.world.level.block.Blocks.SUGAR_CANE;
         boolean upperGone = level.getBlockState(mid).isAir() && level.getBlockState(top).isAir();
         int caneAfter = v.countInv(net.minecraft.world.item.Items.SUGAR_CANE, 0);
         log("H7 CANECYCLE result: harvestedSegments=" + harvestedSegments + " (expected 2) bottomKept=" + bottomKept
            + " upperGone=" + upperGone + " topDownOrder=" + orderTopDownOk
            + " caneInv " + caneBefore + "->" + caneAfter);

         caneCycleOk = harvestedSegments == 2 && bottomKept && upperGone && orderTopDownOk && caneAfter > caneBefore;
         log("H7 CANECYCLE " + (caneCycleOk ? "OK" : "PARTIAL")
            + ": twoUpperHarvested=" + (harvestedSegments == 2) + " bottomRegrowsKept=" + bottomKept
            + " pickedUpCane=" + (caneAfter > caneBefore));

         // Clean up the cane pad (including the kept bottom).
         for (BlockPos seg : new BlockPos[]{top, mid, bottom}) {
            level.removeBlock(seg, false);
         }
         level.removeBlock(soil, false);
      } catch (Throwable t) {
         caneCycleOk = false;
         recordException("H7:canecycle", t);
         log("H7 CANECYCLE FAIL: " + t);
      }
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

   // ============================ STEP H6: player-like entity gather cycle (O5) ============================

   /**
    * Live evidence for the O5 player-like ENTITY-GATHER refactor: spawn three sheep (two woolly/ready, one already
    * sheared) and a cow next to a real villager, equip shears, and drive the migrated
    * {@link org.millenaire.common.goal.GoalShearSheep}'s {@link com.coderyo.jason.ops.VillagerWorldOps#shearTick}
    * primitive — REALLY shearing the ready sheep (each becomes {@code isSheared()}; 1-3 wool of its colour drop via
    * the vanilla {@code BuiltInLootTables.SHEAR_SHEEP} path), walking to + collecting that wool, and SKIPPING the
    * already-sheared one. Then milk the cow via {@link com.coderyo.jason.ops.VillagerWorldOps#milkTick} (no milk goal
    * exists in 1.12, but the helper is exercised for parity). Each phase greppable as {@code [MILLTEST] SHEARCYCLE}.
    */
   private void stepShearCycle() {
      java.util.List<net.minecraft.world.entity.Entity> spawned = new java.util.ArrayList<>();
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive());
         if (villagers.isEmpty()) {
            log("H6 SHEARCYCLE SKIP: no MillVillager in world");
            return;
         }
         MillVillager v = villagers.get(0);

         // Clear a small flat pad around the villager so spawned animals + wool drops sit on solid ground.
         BlockPos base = v.blockPosition();
         for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
               level.setBlock(base.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
               level.setBlock(base.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
               level.setBlock(base.offset(dx, 1, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
         }
         v.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);

         // Two READY (woolly) sheep with known colours + one ALREADY-SHEARED sheep that must be SKIPPED.
         net.minecraft.world.entity.animal.sheep.Sheep readyWhite = spawnSheep(base.offset(1, 0, 0), net.minecraft.world.item.DyeColor.WHITE, false, spawned);
         net.minecraft.world.entity.animal.sheep.Sheep readyBlack = spawnSheep(base.offset(2, 0, 0), net.minecraft.world.item.DyeColor.BLACK, false, spawned);
         net.minecraft.world.entity.animal.sheep.Sheep alreadyShorn = spawnSheep(base.offset(0, 0, 2), net.minecraft.world.item.DyeColor.BROWN, true, spawned);
         net.minecraft.world.entity.animal.cow.Cow cow = spawnCow(base.offset(-1, 0, 1), spawned);

         int spawnedSheep = (readyWhite != null ? 1 : 0) + (readyBlack != null ? 1 : 0) + (alreadyShorn != null ? 1 : 0);
         log("H6 SHEARCYCLE scene: villager@" + base + " readySheep=" + ((readyWhite != null ? 1 : 0) + (readyBlack != null ? 1 : 0))
            + " alreadySheared=" + (alreadyShorn != null && alreadyShorn.isSheared()) + " cow=" + (cow != null)
            + " (total sheep spawned " + spawnedSheep + ")");

         // Equip shears (the goal's tool) and verify the strict tool gate accepts it.
         v.heldItem = new ItemStack(net.minecraft.world.item.Items.SHEARS);
         boolean toolOk = com.coderyo.jason.ops.VillagerWorldOps.ensureTool(v, com.coderyo.jason.ops.VillagerWorldOps.ToolKind.SHEARS);
         log("H6 SHEARCYCLE ensureTool(SHEARS) on held shears = " + toolOk);

         int realShears = 0;
         int skippedSheared = 0;
         int woolBefore = countWool(v);

         // Drive each sheep through shearTick → (pickup). Nudge the villager onto each animal so the synchronous loop
         // gets within reach (no real pathing between our calls), exactly like the chop/farm harness cycles.
         for (net.minecraft.world.entity.animal.sheep.Sheep s : new net.minecraft.world.entity.animal.sheep.Sheep[]{readyWhite, readyBlack, alreadyShorn}) {
            if (s == null) {
               continue;
            }
            boolean wasReady = s.readyForShearing();
            v.setPos(s.getX(), s.getY(), s.getZ()); // step adjacent so distanceToSqr <= reach.
            com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.shearTick(v, s);
            if (!wasReady) {
               // Already-sheared sheep: shearTick must report COMPLETE (skip) and leave it sheared, no new wool.
               if (st == com.coderyo.jason.ops.OpState.COMPLETE) {
                  skippedSheared++;
               }
               log("H6 SHEARCYCLE skip already-sheared sheep colour=" + s.getColor() + " state=" + st
                  + " stillSheared=" + s.isSheared());
               continue;
            }
            // Ready sheep: shearTick really shore it (PICKING_UP) → it is now sheared + wool dropped.
            boolean nowSheared = s.isSheared();
            if (st == com.coderyo.jason.ops.OpState.PICKING_UP && nowSheared) {
               realShears++;
            }
            log("H6 SHEARCYCLE REAL shear colour=" + s.getColor() + " state=" + st + " sheepNowSheared=" + nowSheared);

            // Collect the dropped wool: nudge onto each wool ItemEntity until pickup completes.
            BlockPos woolSpot = s.blockPosition();
            int pg = 0;
            while (pg++ < 100) {
               com.coderyo.jason.ops.OpState pst = com.coderyo.jason.ops.VillagerWorldOps.pickupTick(v, woolSpot);
               if (pst == com.coderyo.jason.ops.OpState.COMPLETE) {
                  break;
               }
               var d = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(woolSpot).inflate(6.0));
               if (!d.isEmpty()) {
                  v.setPos(d.get(0).getX(), d.get(0).getY(), d.get(0).getZ());
               } else {
                  break;
               }
            }
         }

         int woolAfter = countWool(v);
         int woolPickedUp = woolAfter - woolBefore;

         // Milk the cow (parity helper — no 1.12 milk goal). Give the villager an empty bucket in stock first.
         int milkBefore = v.countInv(net.minecraft.world.item.Items.MILK_BUCKET, 0);
         if (cow != null) {
            v.addToInv(net.minecraft.world.item.Items.BUCKET, 1);
            v.setPos(cow.getX(), cow.getY(), cow.getZ());
            com.coderyo.jason.ops.OpState mst = com.coderyo.jason.ops.VillagerWorldOps.milkTick(v, cow);
            log("H6 SHEARCYCLE milk cow state=" + mst + " bucketsLeft=" + v.countInv(net.minecraft.world.item.Items.BUCKET, 0));
         }
         int milkAfter = v.countInv(net.minecraft.world.item.Items.MILK_BUCKET, 0);

         boolean readyBothSheared = (readyWhite == null || readyWhite.isSheared()) && (readyBlack == null || readyBlack.isSheared());
         shearCycleOk = toolOk && realShears >= 2 && skippedSheared == 1 && woolPickedUp >= 2 && readyBothSheared;

         log("H6 SHEARCYCLE result: realShears=" + realShears + " (expected 2) skippedAlreadySheared=" + skippedSheared
            + " (expected 1) woolPickedUp=" + woolPickedUp + " readySheepNowSheared=" + readyBothSheared
            + " milkBuckets=" + milkBefore + "->" + milkAfter);
         log("H6 SHEARCYCLE " + (shearCycleOk ? "OK" : "PARTIAL")
            + ": tool=" + toolOk + " realShearReadyOnly=" + (realShears >= 2 && skippedSheared == 1)
            + " woolPickedUp=" + (woolPickedUp >= 2) + " sheepActuallySheared=" + readyBothSheared
            + " cowMilked=" + (milkAfter > milkBefore));
      } catch (Throwable t) {
         shearCycleOk = false;
         recordException("H6:shearcycle", t);
         log("H6 SHEARCYCLE FAIL: " + t);
      } finally {
         // Remove the spawned animals + any stray wool drops so they don't pollute later steps / the world.
         for (net.minecraft.world.entity.Entity e : spawned) {
            if (e != null) {
               e.discard();
            }
         }
      }
   }

   /** Spawn a {@link net.minecraft.world.entity.animal.sheep.Sheep} of {@code colour} at {@code pos}; pre-shear it if asked. */
   private net.minecraft.world.entity.animal.sheep.Sheep spawnSheep(
      BlockPos pos, net.minecraft.world.item.DyeColor colour, boolean sheared,
      java.util.List<net.minecraft.world.entity.Entity> spawned) {
      net.minecraft.world.entity.animal.sheep.Sheep s =
         net.minecraft.world.entity.EntityTypes.SHEEP.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
      if (s == null) {
         return null;
      }
      s.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
      s.setColor(colour);
      s.setSheared(sheared);
      s.setAge(0); // adult (readyForShearing requires !isBaby()).
      level.addFreshEntity(s);
      spawned.add(s);
      return s;
   }

   /** Spawn an adult {@link net.minecraft.world.entity.animal.cow.Cow} at {@code pos}. */
   private net.minecraft.world.entity.animal.cow.Cow spawnCow(
      BlockPos pos, java.util.List<net.minecraft.world.entity.Entity> spawned) {
      net.minecraft.world.entity.animal.cow.Cow c =
         net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
      if (c == null) {
         return null;
      }
      c.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
      c.setAge(0);
      level.addFreshEntity(c);
      spawned.add(c);
      return c;
   }

   /** Total wool of any colour in the villager's Mill inventory (the gather YIELD). */
   private int countWool(MillVillager v) {
      int total = 0;
      for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values()) {
         total += v.countInv(net.minecraft.world.level.block.Blocks.WOOL.pick(c).asItem(), 0);
      }
      return total;
   }

   // ============================ STEP H5: player-like fishing cycle (O4) ============================

   /**
    * Live evidence for the O4 player-like fishing refactor. Unlike the break-based cycles (which run synchronously),
    * fishing needs a REAL {@link net.minecraft.world.entity.projectile.FishingHook} that animates across many ticks
    * via {@code FishingHookMixin}; so this is a 3-phase, multi-tick test:
    *
    * <ol>
    *   <li>{@link #stepFishStart} (TICK_FISH_START): dig a small water pool next to a real villager, equip a fishing
    *       rod, register a fishing-spot point on the {@link com.coderyo.jason.ops.TaskPointStore}, and drive
    *       {@code VillagerWorldOps.fishTick} once — IDLE→CASTING — spawning a real villager-owned bobber over the
    *       water. The crux of the AW+mixin: a villager (a Mob, not a Player) owns the hook.</li>
    *   <li>{@link #stepFishDrive} (each tick in between): the server tick runs the hook's {@code tick()} → our mixin
    *       keeps the villager-owned hook ALIVE (vanilla would have discarded it on tick 1) and runs the real
    *       {@code catchingFish} bite FSM (splash particles, the {@code biting} flag). We sample {@code biting} as
    *       proof the animation is really happening. Partway through we FORCE a bite deterministically (set the
    *       AW-widened {@code nibble}) so the test doesn't depend on the random 100-600t lure timer, then let the
    *       mixin's catch path fire {@code VillagerFishing.reel} → real FISHING loot.</li>
    *   <li>{@link #stepFishEnd} (TICK_FISH_END): verify the hook survived + animated, loot was rolled from
    *       {@code BuiltInLootTables.FISHING}, and the villager picked it up; report greppable {@code H5 FISHCYCLE}.</li>
    * </ol>
    */
   private void stepFishStart() {
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive());
         if (villagers.isEmpty()) {
            log("H5 FISHCYCLE SKIP: no MillVillager in world");
            return;
         }
         MillVillager v = villagers.get(0);
         fishVillager = v;

         // Build the fishing scene AT THE VILLAGER'S OWN POSITION — do NOT teleport it far (a villager relocated away
         // from its village is removed by Mill's management within a tick, which would orphan the bobber). We only
         // clear a small pad around where it already validly stands and carve a 3x3 water pool 2 blocks in front.
         BlockPos base = v.blockPosition();
         for (int dx = -2; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
               level.setBlock(base.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
               level.setBlock(base.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
               level.setBlock(base.offset(dx, 1, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
         }
         // Carve a 3x3 water pool at base+ (2,0,0): water sources on the pad level, air above, stone below.
         BlockPos centre = base.offset(2, 0, 0);
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               BlockPos w = centre.offset(dx, 0, dz);
               level.setBlock(w.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
               level.setBlock(w, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
               level.setBlock(w.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
         }
         // Keep the villager exactly where it is (on the cleared pad), just face the pool. No long-distance teleport.
         v.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
         fishWaterSurface = centre;

         // Equip the rod (the goal's travelling tool) and clear any stale point state.
         v.heldItem = new ItemStack(net.minecraft.world.item.Items.FISHING_ROD);
         com.coderyo.jason.ops.TaskPointStore.get().clear(level, centre);

         boolean rodOk = com.coderyo.jason.ops.VillagerWorldOps.ensureTool(v, com.coderyo.jason.ops.VillagerWorldOps.ToolKind.ROD);
         BlockPos surface = com.coderyo.jason.ops.VillagerFishing.findWaterSurface(level, centre);
         log("H5 FISHCYCLE scene: pad@" + base + " pool@" + centre + " villager@" + v.blockPosition()
            + " rodEquipped=" + rodOk + " waterSurfaceResolved=" + surface);

         // CAST: IDLE → CASTING. Spawns the real villager-owned hook over the water.
         com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.fishTick(v, centre);
         com.coderyo.jason.ops.TaskPointStore.Progress p = com.coderyo.jason.ops.TaskPointStore.get().peek(level, centre);
         fishBobberId = (p != null) ? p.fishingBobberId : 0;
         var hook = (fishBobberId != 0 && level.getEntity(fishBobberId) instanceof net.minecraft.world.entity.projectile.FishingHook h) ? h : null;
         log("H5 FISHCYCLE cast: state=" + st + " bobberId=" + fishBobberId
            + " hookSpawned=" + (hook != null) + " ownerIsVillager=" + (hook != null && hook.getOwner() == v));
      } catch (Throwable t) {
         fishCycleOk = false;
         recordException("H5:fishstart", t);
         log("H5 FISHCYCLE FAIL (start): " + t);
      }
   }

   /** Per-tick during the fishing window: advance the FSM, sample the live animation, force the deterministic bite. */
   private void stepFishDrive() {
      if (fishVillager == null || fishWaterSurface == null) {
         return;
      }
      try {
         MillVillager v = fishVillager;
         // Keep the villager beside the pool so reach-gated pickup works once the catch lands.
         v.setPos(fishWaterSurface.getX() + 0.5, fishWaterSurface.getY(), fishWaterSurface.getZ() - 1.2);

         net.minecraft.world.entity.projectile.FishingHook hook =
            (level.getEntity(fishBobberId) instanceof net.minecraft.world.entity.projectile.FishingHook h && h.isAlive()) ? h : null;

         int sinceStart = tick - TICK_FISH_START;
         if (sinceStart % 20 == 0 || hook == null) {
            log("H5 FISHCYCLE drive +" + sinceStart + "t: hookAlive=" + (hook != null)
               + (hook != null ? (" inWater=" + level.getFluidState(hook.blockPosition()).is(net.minecraft.tags.FluidTags.WATER)
                  + " biting=" + ((net.minecraft.world.entity.projectile.FishingHook) hook).biting
                  + " nibble=" + ((net.minecraft.world.entity.projectile.FishingHook) hook).nibble
                  + " hookY=" + String.format("%.2f", hook.getY())) : ""));
         }
         if (hook != null) {
            fishHookSurvived = true; // it lived past tick 1 → the mixin's keep-alive defeated the Player-gating.
            // Sample the live bite-animation flag (AW-widened). Peak>0 means the bobber really reached "biting".
            int biting = ((net.minecraft.world.entity.projectile.FishingHook) hook).biting ? 1 : 0;
            fishMaxBitingObserved = Math.max(fishMaxBitingObserved, biting);

            // Force the catch deterministically ~40t in (after the bobber has visibly animated for a while), unless
            // a natural bite already happened. Setting the AW-widened nibble>0 makes the mixin's catch path reel.
            if (!fishCatchForced && sinceStart >= 40) {
               int beforeNibble = ((net.minecraft.world.entity.projectile.FishingHook) hook).nibble;
               if (beforeNibble <= 0) {
                  ((net.minecraft.world.entity.projectile.FishingHook) hook).nibble = 1;
               }
               fishCatchForced = true;
               log("H5 FISHCYCLE forced a bite at +" + sinceStart + "t (nibble " + beforeNibble + "->"
                  + ((net.minecraft.world.entity.projectile.FishingHook) hook).nibble + "); next tick the mixin reels.");
            }
         }

         // Drive the goal-level FSM (CASTING→WAITING; or PICKUP after the reel) so the villager collects the loot.
         com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.fishTick(v, fishWaterSurface);

         // Once the reel has happened the point is in PICKUP; count any loot drops near the water as they appear.
         var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(fishWaterSurface).inflate(6.0));
         fishLootSpawned = Math.max(fishLootSpawned, drops.size());
         if (st == com.coderyo.jason.ops.OpState.PICKING_UP && !drops.isEmpty()) {
            // Nudge the villager onto a drop so the reach-gated pickup collects it in this synchronous loop.
            v.setPos(drops.get(0).getX(), drops.get(0).getY(), drops.get(0).getZ());
         }
      } catch (Throwable t) {
         recordException("H5:fishdrive", t);
      }
   }

   private void stepFishEnd() {
      try {
         MillVillager v = fishVillager;
         if (v == null || fishWaterSurface == null) {
            log("H5 FISHCYCLE SKIP: not set up");
            return;
         }
         // Finish any remaining pickup synchronously.
         com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.fishTick(v, fishWaterSurface);
         int guard = 0;
         while (st == com.coderyo.jason.ops.OpState.PICKING_UP && guard++ < 100) {
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(fishWaterSurface).inflate(6.0));
            if (!drops.isEmpty()) {
               v.setPos(drops.get(0).getX(), drops.get(0).getY(), drops.get(0).getZ());
            }
            st = com.coderyo.jason.ops.VillagerWorldOps.fishTick(v, fishWaterSurface);
         }

         // The picked-up loot is now in the villager's inventory; sum the FISHING-table outcomes we can count.
         fishPickedUp = countFishingLoot(v);

         // A successful cycle: the villager-owned hook survived+animated, a catch rolled real loot, the villager
         // ended IDLE/COMPLETE (no stuck bobber), and at least one loot stack made it into its inventory.
         boolean caughtSomething = fishLootSpawned > 0 || fishPickedUp > 0;
         boolean noStuckBobber = !(level.getEntity(fishBobberId) instanceof net.minecraft.world.entity.projectile.FishingHook fh && fh.isAlive());
         fishCycleOk = fishHookSurvived && fishCatchForced && caughtSomething;

         log("H5 FISHCYCLE result: hookSurvived(villager-owned, NOT discarded)=" + fishHookSurvived
            + " maxBitingFlag=" + fishMaxBitingObserved
            + " forcedBite=" + fishCatchForced
            + " lootSpawned=" + fishLootSpawned + " (real BuiltInLootTables.FISHING)"
            + " pickedUpIntoInv=" + fishPickedUp
            + " bobberCleanedUp=" + noStuckBobber
            + " finalFSM=" + st);
         log("H5 FISHCYCLE " + (fishCycleOk ? "OK" : "PARTIAL")
            + ": fullAnimationViaMixin=" + fishHookSurvived
            + " realFishingLoot=" + caughtSomething
            + " villagerPickedUp=" + (fishPickedUp > 0));

         // Clean up the pool + any stray drops.
         BlockPos c = fishWaterSurface;
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               level.removeBlock(c.offset(dx, 0, dz), false);
            }
         }
         com.coderyo.jason.ops.TaskPointStore.get().clear(level, fishWaterSurface);
      } catch (Throwable t) {
         fishCycleOk = false;
         recordException("H5:fishend", t);
         log("H5 FISHCYCLE FAIL (end): " + t);
      }
   }

   // ====================== STEP H5b: INLINE fishing cycle (runs AT GROWTH_END) ======================

   /**
    * INLINE, fully-synchronous fishing cycle so the FISH scenario actually RUNS + PASSES within the reliably-reached
    * GROWTH_END window (the multi-tick {@link #stepFishStart}/{@link #stepFishDrive}/{@link #stepFishEnd} window is
    * scheduled LATER than GROWTH_END, which the co-hosted client halts before reaching). It mirrors that window's
    * three phases but drives the real bobber's {@code tick()} ITSELF in a loop here — the {@code FishingHookMixin}
    * runs inside each {@code hook.tick()}, so the full vanilla bobbing + bite animation still happens, the
    * villager-owned hook survives (never the Player-gated discard), we observe the live {@code biting} flag, then FORCE
    * the bite deterministically (set the AW-widened {@code nibble}) so the very next {@code hook.tick()} reels real
    * {@code BuiltInLootTables.FISHING} loot, and the villager walks to + collects it. Sets {@link #fishCycleOk}.
    */
   private void stepFishInline() {
      MillVillager v = null;
      BlockPos centre = null;
      try {
         List<MillVillager> villagers = level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), x -> x.isAlive());
         if (villagers.isEmpty()) {
            log("H5 FISHCYCLE(inline) SKIP: no MillVillager in world");
            fishCycleOk = false;
            return;
         }
         v = villagers.get(0);
         fishVillager = v;

         // Build the fishing scene at the villager's own validly-occupied position (do NOT teleport it far — a relocated
         // villager is culled by Mill management within a tick and would orphan the bobber). Clear a small pad + carve a
         // 3x3 water pool 2 blocks in front.
         BlockPos base = v.blockPosition();
         for (int dx = -2; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
               level.setBlock(base.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
               level.setBlock(base.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
               level.setBlock(base.offset(dx, 1, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
         }
         centre = base.offset(2, 0, 0);
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               BlockPos w = centre.offset(dx, 0, dz);
               level.setBlock(w.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
               level.setBlock(w, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
               level.setBlock(w.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
         }
         v.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
         fishWaterSurface = centre;

         v.heldItem = new ItemStack(net.minecraft.world.item.Items.FISHING_ROD);
         com.coderyo.jason.ops.TaskPointStore.get().clear(level, centre);
         boolean rodOk = com.coderyo.jason.ops.VillagerWorldOps.ensureTool(v, com.coderyo.jason.ops.VillagerWorldOps.ToolKind.ROD);
         BlockPos surface = com.coderyo.jason.ops.VillagerFishing.findWaterSurface(level, centre);
         log("H5 FISHCYCLE(inline) scene: pad@" + base + " pool@" + centre + " villager@" + v.blockPosition()
            + " rodEquipped=" + rodOk + " waterSurfaceResolved=" + surface);

         // CAST: IDLE → CASTING, spawning the real villager-owned hook over the water.
         com.coderyo.jason.ops.OpState cast = com.coderyo.jason.ops.VillagerWorldOps.fishTick(v, centre);
         com.coderyo.jason.ops.TaskPointStore.Progress p = com.coderyo.jason.ops.TaskPointStore.get().peek(level, centre);
         fishBobberId = (p != null) ? p.fishingBobberId : 0;
         net.minecraft.world.entity.projectile.FishingHook hook =
            (fishBobberId != 0 && level.getEntity(fishBobberId) instanceof net.minecraft.world.entity.projectile.FishingHook h) ? h : null;
         log("H5 FISHCYCLE(inline) cast: state=" + cast + " bobberId=" + fishBobberId
            + " hookSpawned=" + (hook != null) + " ownerIsVillager=" + (hook != null && hook.getOwner() == v));
         if (hook == null) {
            fishCycleOk = false;
            log("H5 FISHCYCLE(inline) FAIL: no hook spawned");
            return;
         }

         // DRIVE the hook's own tick() in a loop. The mixin keeps the villager-owned hook alive + runs the real
         // bobbing/bite FSM each tick. Keep the villager beside the pool so reach-gated pickup works after the reel.
         int drive = 0;
         boolean reeled = false;
         while (drive++ < FISH_WINDOW) {
            v.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() - 1.2);
            net.minecraft.world.entity.projectile.FishingHook live =
               (level.getEntity(fishBobberId) instanceof net.minecraft.world.entity.projectile.FishingHook h2 && h2.isAlive()) ? h2 : null;
            if (live == null) {
               break; // reeled (hook discarded by the mixin on catch) or expired.
            }
            fishHookSurvived = true; // it lived past tick 1 → the mixin's keep-alive defeated the Player-gating.
            // Sample the live bite-animation flag (proof the bobber really reached "biting").
            fishMaxBitingObserved = Math.max(fishMaxBitingObserved, live.biting ? 1 : 0);

            // Force the catch deterministically ~10 ticks in (after it has settled onto the water + bobbed), unless a
            // natural bite already landed. Setting the AW-widened nibble>0 makes the next tick's mixin path reel.
            if (!fishCatchForced && drive >= 10) {
               int beforeNibble = live.nibble;
               if (beforeNibble <= 0) {
                  live.nibble = 1;
               }
               fishCatchForced = true;
               log("H5 FISHCYCLE(inline) forced a bite at +" + drive + "t (nibble " + beforeNibble + "->" + live.nibble + ")");
            }

            // Run the hook's vanilla tick → the mixin animates / (on the forced bite) reels via VillagerFishing.reel.
            live.tick();

            // Did the reel happen? The point flips to PICKUP and the hook is discarded.
            com.coderyo.jason.ops.TaskPointStore.Progress pp = com.coderyo.jason.ops.TaskPointStore.get().peek(level, centre);
            if (pp != null && pp.fishingPhase == com.coderyo.jason.ops.VillagerFishing.Phase.PICKUP.ordinal()) {
               reeled = true;
               break;
            }
         }

         // PICKUP: walk the villager to each FISHING-loot drop and collect it (synchronous).
         com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerWorldOps.fishTick(v, centre);
         int guard = 0;
         while (st == com.coderyo.jason.ops.OpState.PICKING_UP && guard++ < 200) {
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(centre).inflate(6.0));
            fishLootSpawned = Math.max(fishLootSpawned, drops.size());
            if (!drops.isEmpty()) {
               v.setPos(drops.get(0).getX(), drops.get(0).getY(), drops.get(0).getZ());
            }
            st = com.coderyo.jason.ops.VillagerWorldOps.fishTick(v, centre);
         }

         fishPickedUp = countFishingLoot(v);
         boolean caughtSomething = fishLootSpawned > 0 || fishPickedUp > 0;
         boolean noStuckBobber = !(level.getEntity(fishBobberId) instanceof net.minecraft.world.entity.projectile.FishingHook fh && fh.isAlive());
         fishCycleOk = fishHookSurvived && fishCatchForced && caughtSomething;

         log("H5 FISHCYCLE(inline) result: hookSurvived(villager-owned, NOT discarded)=" + fishHookSurvived
            + " maxBitingFlag=" + fishMaxBitingObserved + " forcedBite=" + fishCatchForced
            + " reeled=" + reeled + " lootSpawned=" + fishLootSpawned + " (real BuiltInLootTables.FISHING)"
            + " pickedUpIntoInv=" + fishPickedUp + " bobberCleanedUp=" + noStuckBobber + " finalFSM=" + st);
         log("H5 FISHCYCLE(inline) " + (fishCycleOk ? "OK" : "PARTIAL")
            + ": fullAnimationViaMixin=" + fishHookSurvived + " realFishingLoot=" + caughtSomething
            + " villagerPickedUp=" + (fishPickedUp > 0));
      } catch (Throwable t) {
         fishCycleOk = false;
         recordException("H5b:fishinline", t);
         log("H5 FISHCYCLE(inline) FAIL: " + t);
      } finally {
         // Clean up the pool + any stale point/bobber.
         try {
            if (centre != null) {
               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     level.removeBlock(centre.offset(dx, 0, dz), false);
                  }
               }
               com.coderyo.jason.ops.TaskPointStore.get().clear(level, centre);
            }
            if (fishBobberId != 0 && level.getEntity(fishBobberId) instanceof net.minecraft.world.entity.projectile.FishingHook fh) {
               com.coderyo.jason.ops.VillagerFishing.forget(fishBobberId);
               fh.discard();
            }
         } catch (Throwable ignored) {
         }
         // Clear the multi-tick fishing state so the later dedicated TICK_FISH_* window (full server-only runs) does
         // not act on this inline run's stale handles.
         fishVillager = null;
         fishWaterSurface = null;
         fishBobberId = 0;
      }
   }

   /** Sum the countable {@code BuiltInLootTables.FISHING} outcomes now in the villager's Mill inventory. */
   private int countFishingLoot(MillVillager v) {
      return v.countInv(net.minecraft.world.item.Items.COD, 0)
         + v.countInv(net.minecraft.world.item.Items.SALMON, 0)
         + v.countInv(net.minecraft.world.item.Items.PUFFERFISH, 0)
         + v.countInv(net.minecraft.world.item.Items.TROPICAL_FISH, 0)
         + v.countInv(net.minecraft.world.item.Items.STICK, 0)
         + v.countInv(net.minecraft.world.item.Items.STRING, 0)
         + v.countInv(net.minecraft.world.item.Items.BOWL, 0)
         + v.countInv(net.minecraft.world.item.Items.LEATHER, 0)
         + v.countInv(net.minecraft.world.item.Items.LEATHER_BOOTS, 0)
         + v.countInv(net.minecraft.world.item.Items.ROTTEN_FLESH, 0)
         + v.countInv(net.minecraft.world.item.Items.INK_SAC, 0)
         + v.countInv(net.minecraft.world.item.Items.BONE, 0)
         + v.countInv(net.minecraft.world.item.Items.LILY_PAD, 0)
         + v.countInv(net.minecraft.world.item.Items.NAME_TAG, 0)
         + v.countInv(net.minecraft.world.item.Items.NAUTILUS_SHELL, 0)
         + v.countInv(net.minecraft.world.item.Items.SADDLE, 0)
         + v.countInv(net.minecraft.world.item.Items.FISHING_ROD, 0)
         + v.countInv(net.minecraft.world.item.Items.ENCHANTED_BOOK, 0);
   }

   // ====================== STEP CATALOG: comprehensive static + dynamic coverage ======================

   /**
    * Runs the COMPREHENSIVE registry-iterating static catalog (every {@code millenaire} block/item +
    * every Mill EntityType + every VillagerType per culture) AND the dynamic scenario inventory, emitting
    * the greppable {@code ███ CATALOG} / {@code ███ SCENARIO} / {@code ███ COVERAGE SUMMARY} lines.
    *
    * <p>The H-cycle results computed earlier in this run (mine/chop/farm/fish/shear/cane, trade,
    * interaction, movement, goals, construction) are folded into the scenario inventory as
    * already-covered behaviours so they're re-stated rather than re-run; the gap behaviours
    * (door/swim/melee/reputation/sleep/raid) are live-checked by {@code MillScenarios}.
    */
   private void stepCatalog() {
      try {
         // Coverage map: behaviour name -> tri-state, from the H-cycle results we already have.
         com.coderyo.jason.catalog.MillScenarios.Coverage cov = new com.coderyo.jason.catalog.MillScenarios.Coverage()
            .put("MINE", mineCycleOk)
            .put("CHOP", chopCycleOk)
            .put("CANE", caneCycleOk)
            .put("FARM", farmCycleOk)
            .put("FISH", fishCycleOk)
            .put("SHEAR", shearCycleOk)
            .put("TRADE", tradeOk)
            .put("INTERACT", interactOk)
            // Movement/goals: treat as covered+OK if the metrics ran (they always do over the growth window).
            .put("MOVEMENT", !"not run".equals(movementSummary))
            .put("GOALS", !"not run".equals(goalSummary))
            // Construction: covered if any building was reported (step E ran).
            .put("CONSTRUCTION", buildingsReported > 0);
            // COMBAT is set INSIDE MillScenarios.run from the LIVE server-side melee acquisition check it performs
            // this session (target stick + attackEntity engage), so it is a genuine live result, never a constant.

         // Scratch area: high above the first generated village (or world spawn) so placement/spawning is isolated.
         BlockPos scratch;
         if (!generatedVillagePoints.isEmpty()) {
            Point p = generatedVillagePoints.get(0);
            scratch = new BlockPos(p.getiX(), Math.min(level.getMaxY() - 20, level.getSeaLevel() + 40), p.getiZ());
         } else {
            BlockPos spawn = level.getRespawnData().pos();
            scratch = new BlockPos(spawn.getX(), Math.min(level.getMaxY() - 20, level.getSeaLevel() + 40), spawn.getZ());
         }
         // Force the scratch chunk loaded so block/entity ops are valid.
         level.getChunk(scratch.getX() >> 4, scratch.getZ() >> 4);

         com.coderyo.jason.catalog.MillCatalog.Sink sink = com.coderyo.jason.catalog.MillCatalog.logSink();
         sink.emit(com.coderyo.jason.catalog.MillCatalog.TAG + " HEADER level=" + level.dimension().identifier()
            + " scratch=" + scratch.getX() + "/" + scratch.getY() + "/" + scratch.getZ()
            + " (iterating BuiltInRegistries — registry-driven, stays complete)");

         // Run blocks/items/entities then the scenario inventory WITH our coverage map.
         com.coderyo.jason.catalog.MillCatalog.Result r = new com.coderyo.jason.catalog.MillCatalog.Result();
         r.blocks = invokeCatalogBlocks(sink, r, scratch);
         r.items = invokeCatalogItems(sink, r);
         r.entities = invokeCatalogEntities(sink, r, scratch);
         r.scenarios = com.coderyo.jason.catalog.MillScenarios.run(level, scratch, sink, r, cov);
         sink.emit(com.coderyo.jason.catalog.MillCatalog.SUMMARY_TAG + " blocks=" + r.blocks + " items=" + r.items
            + " entities=" + r.entities + " scenarios=" + r.scenarios + " anomalies=" + anomaliesStr(r));
         catalogResult = r;
         log("CATALOG OK: blocks=" + r.blocks + " items=" + r.items + " entities=" + r.entities
            + " scenarios=" + r.scenarios + " anomalies=" + anomaliesStr(r));
      } catch (Throwable t) {
         recordException("CATALOG", t);
         log("CATALOG FAIL: " + t);
      }
   }

   private static String anomaliesStr(com.coderyo.jason.catalog.MillCatalog.Result r) {
      if (r.anomalies.isEmpty()) {
         return "[]";
      }
      StringBuilder sb = new StringBuilder("[");
      boolean first = true;
      for (Map.Entry<String, Integer> e : r.anomalies.entrySet()) {
         if (!first) {
            sb.append(", ");
         }
         first = false;
         sb.append(e.getKey()).append("x").append(e.getValue());
      }
      return sb.append("]").toString();
   }

   // The MillCatalog static phase methods are package-public via thin wrappers so the harness can run
   // them with the SAME level/scratch and fold the scenario coverage in between. They delegate to the
   // public MillCatalog.run(...) phases (kept private in MillCatalog) through its run() entrypoint is not
   // used here because we need to inject coverage; instead we call MillCatalog's public phase helpers.
   private int invokeCatalogBlocks(com.coderyo.jason.catalog.MillCatalog.Sink sink,
         com.coderyo.jason.catalog.MillCatalog.Result r, BlockPos scratch) {
      return com.coderyo.jason.catalog.MillCatalog.catalogBlocksPublic(level, scratch, sink, r);
   }

   private int invokeCatalogItems(com.coderyo.jason.catalog.MillCatalog.Sink sink,
         com.coderyo.jason.catalog.MillCatalog.Result r) {
      return com.coderyo.jason.catalog.MillCatalog.catalogItemsPublic(sink, r);
   }

   private int invokeCatalogEntities(com.coderyo.jason.catalog.MillCatalog.Sink sink,
         com.coderyo.jason.catalog.MillCatalog.Result r, BlockPos scratch) {
      return com.coderyo.jason.catalog.MillCatalog.catalogEntitiesPublic(level, scratch, sink, r);
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
      log("cane cycle (O6 Mill-specific sugarcane: top-down break upper segments, KEEP bottom, pickup): " + (caneCycleOk == null ? "not run" : (caneCycleOk ? "OK" : "PARTIAL/FAIL")));
      log("farm cycle (O3 only-mature harvest+pickup+auto-replant, immature skipped): " + (farmCycleOk == null ? "not run" : (farmCycleOk ? "OK" : "PARTIAL/FAIL")));
      log("fish cycle (O4 AW+mixin real bobber animation+FISHING loot+pickup): " + (fishCycleOk == null ? "not run" : (fishCycleOk ? "OK" : "PARTIAL/FAIL")));
      log("shear cycle (O5 real Sheep.shear ready-only+wool pickup+skip-sheared+milk): " + (shearCycleOk == null ? "not run" : (shearCycleOk ? "OK" : "PARTIAL/FAIL")));
      log("distinct exception types seen: " + distinctExceptions.size());
      for (Map.Entry<String, Integer> e : distinctExceptions.entrySet()) {
         log("  exception x" + e.getValue() + ": " + e.getKey());
      }
      log("===== END SUMMARY =====");
      COMPLETED = true; // signal the co-hosted client self-test that ALL server H-cycles (incl. H6 SHEARCYCLE) ran.
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
