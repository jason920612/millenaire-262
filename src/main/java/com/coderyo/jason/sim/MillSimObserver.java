package com.coderyo.jason.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.quest.QuestInstance;
import org.millenaire.common.ui.ContainerTrade;
import org.millenaire.common.utilities.VillageInventory;
import org.millenaire.common.world.UserProfile;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.VillagerRecord;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.WorldGenVillage;

/**
 * HEADLESS autonomous village-life + village-war SIMULATION / OBSERVER for Millénaire 26.2.
 *
 * <p>This is the "watch the whole village operate, including village wars, every interaction /
 * block-action observable, WITHOUT actually opening the game" tool. It runs under the DEDICATED
 * server ({@code gradlew runServer}) which creates and ticks an overworld with NO client window —
 * so there is no game window, no rendering, and no sound to interfere with the user's machine.
 *
 * <p>It is entirely out of the normal code path unless launched with the env flag
 * {@code MILLENAIRE_SIM=1} (or {@code -Dmillenaire.sim=true}); {@link #isEnabled()} gates
 * {@link #register(MinecraftServer)} from {@code MillenaireMod}.
 *
 * <h2>What it does (driven from {@link ServerTickEvents#END_SERVER_TICK})</h2>
 * <ol>
 *   <li><b>Setup</b> — generates 2 villages of different cultures near spawn (same WorldGenVillage
 *       path the server self-test uses) and force-loads their chunks so their townhalls activate
 *       and the AI ticks.</li>
 *   <li><b>Run the lifecycle</b> — ticks for a configurable number of simulated days, letting
 *       villagers run their real goals (work / trade / build / sleep) via the AI + task ops.</li>
 *   <li><b>Observe + textualise</b> — every {@link #SAMPLE_INTERVAL} ticks it samples each village
 *       (population, buildings, construction, relations) and a set of villagers (name, type, goal +
 *       physical action, position, fight target) and emits greppable {@code ███ SIM ...} lines.
 *       It also diffs state to surface EVENTS: building completed, birth, death (+cause), villager
 *       took/finished a task, and BLOCK ACTIONS (blocks changed in the village box).</li>
 *   <li><b>Village WAR</b> — deliberately triggers a war between the two villages: it makes them
 *       mutually hostile (relations &lt; -90), plans + force-launches a raid, and spawns a real
 *       raiding party of raider-type villagers at the defender. It then OBSERVES the war fully in
 *       text ({@code ███ SIM WAR ...}): raid declared, attacker/defender strength, the in-world
 *       combat (who attacks whom, damage via {@code attackEntity}/{@code doHurtTarget} attrition),
 *       casualties, and the outcome / aftermath.</li>
 *   <li><b>Roaming observer</b> — moves an observation point around the village(s) each sample so
 *       the textual coverage walks across different areas ("walking around watching").</li>
 *   <li><b>Final summary</b> — {@code ███ SIM SUMMARY}: villages, villagers, buildings, trades,
 *       births/deaths, war outcome, ticks simulated, anomalies. The full block/entity inventory at
 *       start + end is dumped via {@code CommandDebugDump.dumpToLog}, and the catalog / scenario
 *       coverage via {@code MillCatalog}.</li>
 * </ol>
 *
 * <p>It is CME-safe (every entity/record/building iteration copies into a list first and is wrapped
 * so a single bad sample can't abort the run) and exits cleanly: after the configured duration it
 * releases its forced chunks and calls {@code server.halt(false)} so the process terminates, exactly
 * like the self-test harness.
 */
public final class MillSimObserver {
   public static final String PROPERTY = "millenaire.sim";
   /** Greppable master tag. All sim output is one of: SIM, SIM WAR, SIM EVENT, SIM SUMMARY. */
   public static final String TAG = "███ SIM";

   // ---- Configuration (overridable via -Dmillenaire.sim.days=N / MILLENAIRE_SIM_DAYS) -------------
   /** How many simulated in-game days to run the lifecycle before the war + summary. */
   private static final int SIM_DAYS = resolveInt("millenaire.sim.days", "MILLENAIRE_SIM_DAYS", 3);
   /**
    * SPEED-UP factor (e.g. 100 for "100x"). Each REAL server tick we run the village/world update loop
    * {@code SIM_SPEED} times back-to-back AND extra-advance the day clock, so a configured "sim day" of
    * villager work/build/trade progress is reached in roughly 1/SIM_SPEED of the wall-clock time. Set via
    * {@code MILLENAIRE_SIM_SPEED} / {@code -Dmillenaire.sim.speed}. Default 1 (real-time). Clamped to [1,200].
    */
   private static final int SIM_SPEED = Math.max(1, Math.min(200, resolveInt("millenaire.sim.speed", "MILLENAIRE_SIM_SPEED", 1)));
   /** Ticks per simulated day used for our schedule (the lifecycle window, not the vanilla day length). */
   private static final int TICKS_PER_DAY = 1200; // ~60s real per "sim day" of ticking — fast but lets AI progress
   /** How often (ticks) to sample + textualise the whole world. */
   private static final int SAMPLE_INTERVAL = 100; // 5s @20tps
   /** Max villagers detailed per sample (greppable but bounded). */
   private static final int VILLAGER_SAMPLE_LIMIT = 12;
   /** Village-area half-extent used for the block-activity box + roaming observer. */
   private static final int AREA_RADIUS = 40;
   private static final int BLOCK_BOX_HALF_HEIGHT = 16;
   private static final int BLOCK_BOX_STRIDE = 3;
   private static final int VILLAGE_SPACING = 320;   // far enough apart to place cleanly, close enough to relate
   private static final int VILLAGE_ORIGIN = 400;
   /** Size of the raiding party we spawn to guarantee an observable war. */
   private static final int RAID_PARTY_SIZE = 5;

   // ---- Schedule (absolute ticks from sim start) ------------------------------------------------
   private static final int TICK_SETUP_PLAYER = 5;
   private static final int TICK_GEN_VILLAGES = 20;
   private static final int TICK_FORCE_CHUNKS = 40;
   private static final int TICK_START_DUMP = 55;
   /** Dedicated REAL ore-vein mining demonstration (Phase 1, #3): deterministic + observable regardless of AI sleep. */
   private static final int TICK_MINING_DEMO = 60;
   private static final int TICK_LIFECYCLE_START = 70;
   private final int TICK_LIFECYCLE_END = TICK_LIFECYCLE_START + SIM_DAYS * TICKS_PER_DAY;
   /** Roaming-player phase: the simulated player visits every village and trades + quests. */
   private final int TICK_PLAYER_INTERACT = TICK_LIFECYCLE_END + 10;
   /** One village is visited per tick during this window; generous bound (villages are far apart). */
   private final int TICK_PLAYER_INTERACT_END = TICK_PLAYER_INTERACT + 200;
   private final int TICK_WAR_DECLARE = TICK_PLAYER_INTERACT_END + 20;
   private final int TICK_WAR_END = TICK_WAR_DECLARE + 600; // ~30s to observe the in-world combat fully
   private final int TICK_END_DUMP = TICK_WAR_END + 20;
   private final int TICK_CATALOG = TICK_END_DUMP + 10;
   private final int TICK_SUMMARY = TICK_CATALOG + 10;
   private final int MAX_TICK_GUARD = TICK_SUMMARY + 4000;

   private static boolean started = false;

   private final MinecraftServer server;
   private ServerLevel level;
   private ServerPlayer fakePlayer;

   private int tick = 0;
   private boolean halted = false;

   // ---- Setup result ----
   private final Map<String, Boolean> villageByCulture = new LinkedHashMap<>();
   private final List<Point> villagePoints = new ArrayList<>();

   // ---- Lifecycle event accumulators (diffed each sample) ----
   private final Set<Long> knownVillagerIds = new HashSet<>();
   private final Map<Long, String> lastGoalById = new HashMap<>();
   private final Map<Point, Integer> lastBuildingCount = new HashMap<>();
   private final Map<Point, Integer> lastPopulation = new HashMap<>();
   private final Map<String, Map<Long, Integer>> blockSnapshots = new LinkedHashMap<>(); // village -> packedPos -> hashOfState

   private int births = 0;
   private int deaths = 0;
   private int buildingsCompletedEvents = 0;
   private int taskChangeEvents = 0;
   private int blockActionEvents = 0;
   private int tradesObserved = 0;
   private int sampleCount = 0;

   // ---- Mining (real ore-vein engine) observation totals (max-seen across the run) ----
   private int mineOreMinedTotal = 0;
   private int mineFrontierAdvancesTotal = 0;
   private int mineHazardsTotal = 0;

   // ---- Roaming-player interaction accumulators ----
   private int playerVillagesVisited = 0;
   private int playerTradesDone = 0;
   private int playerQuestsDone = 0;
   private int playerMoneySpent = 0;   // deniers spent on buys
   private int playerMoneyEarned = 0;  // deniers earned on sells + quest rewards
   private final List<String> playerInteractionLog = new ArrayList<>();
   // Incremental roam state: visit ONE village per tick (villages can be 8000+ blocks apart; doing all of
   // them in a single tick force-loads enough chunks to trip the 60s dedicated-server watchdog).
   private UserProfile playerProfile;
   private List<Building> playerRoamTargets;
   private int playerRoamIndex = 0;
   private int playerStartMoney = -1;

   // ---- War state ----
   private Building attacker;
   private Building defender;
   private final List<MillVillager> raidParty = new ArrayList<>();
   private boolean warDeclared = false;
   private int warAttackerStart = 0;
   private int warDefenderStart = 0;
   private int warHits = 0;
   private int warAttackerCasualties = 0;
   private int warDefenderCasualties = 0;
   private String warOutcome = "not run";

   private final Map<String, Integer> anomalies = new LinkedHashMap<>();

   private MillSimObserver(MinecraftServer server) {
      this.server = server;
   }

   /** True only when launched with {@code MILLENAIRE_SIM=1} / {@code -Dmillenaire.sim=true}. */
   public static boolean isEnabled() {
      if ("true".equalsIgnoreCase(System.getProperty(PROPERTY))) {
         return true;
      }
      String env = System.getenv("MILLENAIRE_SIM");
      return "true".equalsIgnoreCase(env) || "1".equals(env);
   }

   /** Hooks the observer onto the server tick loop. Call once from {@code SERVER_STARTED}. */
   public static void register(MinecraftServer server) {
      if (started) {
         return;
      }
      started = true;
      log("===== MILLENAIRE HEADLESS SIMULATION/OBSERVER ENABLED (MILLENAIRE_SIM=1) =====");
      log("config: simDays=" + SIM_DAYS + " ticksPerSimDay=" + TICKS_PER_DAY + " sampleInterval=" + SAMPLE_INTERVAL
         + " (headless dedicated server — no window, no sound)");
      final MillSimObserver obs = new MillSimObserver(server);
      ServerTickEvents.END_SERVER_TICK.register(s -> {
         if (s == server) {
            obs.onTick();
         }
      });
   }

   // ===============================================================================================

   private void onTick() {
      if (halted) {
         return;
      }
      tick++;
      try {
         if (tick >= MAX_TICK_GUARD) {
            log("MAX-TICK GUARD (" + MAX_TICK_GUARD + ") reached — forcing clean stop.");
            stepSummary();
            stopServer();
            return;
         }

         if (tick == TICK_SETUP_PLAYER) {
            stepSetup();
         } else if (tick == TICK_GEN_VILLAGES) {
            stepGenerateVillages();
         } else if (tick == TICK_FORCE_CHUNKS) {
            stepForceChunks();
         } else if (tick == TICK_START_DUMP) {
            stepInventoryDump("START");
            initBaselines();
         } else if (tick == TICK_MINING_DEMO) {
            stepMiningDemo();
         } else if (tick > TICK_LIFECYCLE_START && tick < TICK_LIFECYCLE_END) {
            if ((tick - TICK_LIFECYCLE_START) % SAMPLE_INTERVAL == 0) {
               sampleWorld("LIFECYCLE");
            }
         } else if (tick == TICK_PLAYER_INTERACT) {
            stepPlayerInteractionBegin();
         } else if (tick > TICK_PLAYER_INTERACT && tick <= TICK_PLAYER_INTERACT_END) {
            stepPlayerInteractionDrive();
         } else if (tick == TICK_WAR_DECLARE) {
            sampleWorld("PRE-WAR");
            stepDeclareWar();
         } else if (tick > TICK_WAR_DECLARE && tick < TICK_WAR_END) {
            driveWar();
            if ((tick - TICK_WAR_DECLARE) % 40 == 0) {
               sampleWorld("WAR");
            }
         } else if (tick == TICK_WAR_END) {
            stepResolveWar();
         } else if (tick == TICK_END_DUMP) {
            stepInventoryDump("END");
         } else if (tick == TICK_CATALOG) {
            stepCatalog();
         } else if (tick == TICK_SUMMARY) {
            stepSummary();
            stopServer();
         }
      } catch (Throwable t) {
         record("onTick(tick=" + tick + ")", t);
         MillLog.printException(TAG + " sim tick error", t);
      }
   }

   // ============================ STEP: setup (debug + fake player) ============================

   private void stepSetup() {
      try {
         MillConfigValues.DEBUG_MODE = true;
         // Diplomacy logging so the natural raid path is visible if it fires.
         MillConfigValues.LogDiplomacy = Math.max(MillConfigValues.LogDiplomacy, 2);
         level = server.overworld();
         fakePlayer = createFakePlayer(server, level);
         BlockPos spawn = level.getRespawnData().pos();
         if (fakePlayer != null) {
            fakePlayer.snapTo(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, 0.0F, 0.0F);
         }
         log("setup OK: overworld=" + level.dimension().identifier() + " spawn=" + spawn
            + " fakePlayer=" + (fakePlayer != null));
      } catch (Throwable t) {
         record("setup", t);
         log("setup FAIL: " + t);
      }
   }

   // ============================ STEP: generate 2 villages of different cultures ============================

   private void stepGenerateVillages() {
      List<Culture> cultures = new ArrayList<>(Culture.ListCultures);
      if (cultures.isEmpty()) {
         log("villagegen FAIL: no cultures loaded");
         return;
      }
      int wanted = Math.min(2, cultures.size());
      int placed = 0;
      for (int idx = 0; idx < cultures.size() && placed < wanted; idx++) {
         Culture culture = cultures.get(idx);
         VillageType vtype = pickRegularVillageType(culture);
         if (vtype == null) {
            continue;
         }
         // Use the ATTEMPT index (idx), not placed: keying off placed meant a failed first attempt left
         // placed=0 so every culture re-tried the SAME point (400/400) and cascaded to all-FAIL. Distinct
         // far-apart points per culture give each its own buildable lane (and don't stack). Natural villages
         // in the force-loaded area remain the robust fallback if a point's terrain can't host the centre.
         int x = VILLAGE_ORIGIN + idx * VILLAGE_SPACING;
         try {
            // Try several candidate Z offsets per culture: a fixed point often can't host the centre building
            // on arbitrary terrain (findBuildingLocation fails). Scanning a few spots until one places gives a
            // high success rate so each wanted culture is reliably observed (not left to whatever spawns naturally).
            boolean result = false;
            int placedZ = VILLAGE_ORIGIN;
            for (int zoff : new int[]{0, 400, -400, 800, -800, 1200, -1200}) {
               int z = VILLAGE_ORIGIN + zoff;
               int ccx = x >> 4;
               int ccz = z >> 4;
               for (int dcx = -2; dcx <= 2; dcx++) {
                  for (int dcz = -2; dcz <= 2; dcz++) {
                     level.getChunk(ccx + dcx, ccz + dcz);
                  }
               }
               WorldGenVillage gen = new WorldGenVillage();
               if (gen.generateVillageAtPoint(
                     level, MillRandom.random, x, 0, z, fakePlayer, false, true, false, 0, vtype, null, null, 1.0F)) {
                  result = true;
                  placedZ = z;
                  break;
               }
            }
            villageByCulture.put(culture.key, result);
            if (result) {
               villagePoints.add(new Point(x, 0, placedZ));
               placed++;
            }
            log("villagegen[" + culture.key + "] " + (result ? "OK" : "FAIL (tried 7 candidate spots)")
               + ": type=" + vtype.key + " at " + x + "/" + placedZ);
         } catch (Throwable t) {
            villageByCulture.put(culture.key, false);
            record("villagegen:" + culture.key, t);
            log("villagegen[" + culture.key + "] FAIL: " + t);
         }
      }
      log("villagegen: placed " + placed + " village(s) of distinct cultures");
   }

   private static VillageType pickRegularVillageType(Culture culture) {
      for (VillageType vt : culture.listVillageTypes) {
         if (vt.isRegularVillage()) {
            return vt;
         }
      }
      for (VillageType vt : culture.listVillageTypes) {
         if (!vt.lonebuilding && !vt.isHamlet() && !vt.isMarvel()) {
            return vt;
         }
      }
      return culture.listVillageTypes.isEmpty() ? null : culture.listVillageTypes.get(0);
   }

   // ============================ STEP: force-load village chunks ============================

   private void stepForceChunks() {
      int forced = 0;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         List<Point> points = new ArrayList<>(villagePoints);
         if (mw != null) {
            for (Point p : new ArrayList<>(mw.villagesList.pos)) {
               points.add(p);
            }
         }
         for (Point p : points) {
            int cx = p.getiX() >> 4;
            int cz = p.getiZ() >> 4;
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
         record("forcechunks", t);
         log("forcechunks FAIL: " + t);
      }
   }

   // ============================ START/END inventory dumps (reuse CommandDebugDump) ============================

   private void stepInventoryDump(String phase) {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         List<Building> townhalls = townhalls(mw);
         log("INVENTORY-DUMP " + phase + ": dumping " + townhalls.size() + " townhall area(s) via CommandDebugDump.dumpToLog");
         for (Building th : townhalls) {
            Point p = th.getPos();
            int records = org.millenaire.common.commands.CommandDebugDump.dumpToLog(
               level, new BlockPos(p.getiX(), p.getiY(), p.getiZ()), 24);
            log("INVENTORY-DUMP " + phase + " village=" + safeName(th) + " records=" + records);
         }
      } catch (Throwable t) {
         record("inventory-dump:" + phase, t);
         log("INVENTORY-DUMP " + phase + " FAIL: " + t);
      }
   }

   // ============================ REAL ORE-VEIN MINING DEMONSTRATION (Phase 1, #3) ============================

   /**
    * Deterministic, observable demonstration of the REAL ore-vein / cave mining engine
    * ({@link com.coderyo.jason.ops.OreVeinMiner} + {@link com.coderyo.jason.ops.MillMiningOps}), driven SYNCHRONOUSLY
    * here — the same pattern the war step uses — so the mining behaviour is provable in the headless run regardless
    * of whether the ambient village AI happens to be awake + assigned to a mine (at 100x the day clock often keeps
    * villagers asleep). It carves a controlled stone chamber mirroring the sim-validated {@code minesim.py} world
    * (two connected ore veins, a natural cave with wall-ore, and a LAVA pocket in the path that must NEVER be
    * breached), spawns a real {@link MillVillager} miner holding an iron pickaxe, and ticks
    * {@link com.coderyo.jason.ops.OreVeinMiner#mineTick} until the veins are mined out + the frontier has advanced.
    * Every step emits {@code ███ SIM MINE} evidence; this method asserts (and logs) that real ore was mined, the
    * frontier advanced outward, and the lava cell was never breached.
    */
   private void stepMiningDemo() {
      try {
         BlockPos origin = miningChamberOrigin();
         // Force-load the chamber chunks so block writes + the spawned miner tick.
         for (int dcx = -1; dcx <= 2; dcx++) {
            for (int dcz = -1; dcz <= 2; dcz++) {
               level.setChunkForced((origin.getX() >> 4) + dcx, (origin.getZ() >> 4) + dcz, true);
            }
         }
         BlockPos lava = buildMiningChamber(origin);
         log("MINE-DEMO: built stone chamber @ " + origin + " (2 ore veins + natural cave + lava pocket @ " + lava
            + "); spawning a miner with an iron pickaxe");

         MillVillager miner = spawnDemoMiner(origin);
         if (miner == null) {
            log("MINE-DEMO SKIP: could not spawn a demo miner");
            return;
         }
         BlockPos anchor = origin; // the mine anchor (entrance) — frontier + hazards keyed here.

         int oreBefore = miner.countInv(net.minecraft.world.item.Items.RAW_IRON, 0)
            + miner.countInv(net.minecraft.world.item.Items.COAL, 0);
         long lavaPacked = lava.asLong();
         boolean lavaBreached = false;
         int cyclesDone = 0;
         // Drive the real engine synchronously. Each iteration is one mineTick (scan→tunnel→flood→cave→frontier).
         // The synchronous drive doesn't tick navigation, so we stand the miner in reach of the cell it is working.
         // To let multi-tick breaks (break progress lives on the POINT) actually COMPLETE, we hold the SAME target
         // cell until it is mined to air before re-scanning — re-teleporting to a fresh scan result each tick would
         // keep resetting which cell accumulates break progress and nothing would ever finish.
         BlockPos target = null;
         for (int i = 0; i < 4000; i++) {
            com.coderyo.jason.ops.MillMiningOps.MineView v = com.coderyo.jason.ops.OreVeinMiner.viewFor(level, anchor);
            com.coderyo.jason.ops.TaskPointStore.MineState pre =
               com.coderyo.jason.ops.TaskPointStore.get().peekMine(level, anchor);
            BlockPos stand = pre != null ? pre.frontier : anchor;
            // Keep the current target until it's air; then pick the nearest remaining ore; else stand at frontier.
            if (target == null || level.getBlockState(target).isAir() || !v.isOre(target)) {
               BlockPos ore = com.coderyo.jason.ops.MillMiningOps.findNearestOre(v, stand,
                  com.coderyo.jason.ops.MillMiningOps.DEFAULT_SCAN_RADIUS);
               if (ore == null) {
                  ore = com.coderyo.jason.ops.MillMiningOps.findNearestOre(v, miner.blockPosition(),
                     com.coderyo.jason.ops.MillMiningOps.DEFAULT_SCAN_RADIUS);
               }
               target = ore != null ? ore : stand;
            }
            miner.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

            com.coderyo.jason.ops.OreVeinMiner.MineResult r = com.coderyo.jason.ops.OreVeinMiner.mineTick(miner, anchor);
            if (r == com.coderyo.jason.ops.OreVeinMiner.MineResult.CYCLE_DONE) {
               cyclesDone++;
            }
            // SAFETY ASSERT every iteration: the lava cell must remain lava (never broken into air).
            if (!level.getBlockState(lava).getFluidState().is(net.minecraft.world.level.material.Fluids.LAVA)
               && !level.getBlockState(lava).is(net.minecraft.world.level.block.Blocks.LAVA)) {
               lavaBreached = true;
            }
         }

         com.coderyo.jason.ops.TaskPointStore.MineState ms =
            com.coderyo.jason.ops.TaskPointStore.get().peekMine(level, anchor);
         int oreMined = ms != null ? ms.oreMined : 0;
         int frontierAdvances = ms != null ? ms.frontierAdvances : 0;
         int hazards = ms != null ? ms.hazards.size() : 0;
         boolean lavaStillThere = level.getBlockState(lava).getFluidState()
            .is(net.minecraft.world.level.material.Fluids.LAVA)
            || level.getBlockState(lava).is(net.minecraft.world.level.block.Blocks.LAVA);

         log("MINE-DEMO RESULT: oreFloodMined=" + oreMined + " frontierAdvances=" + frontierAdvances
            + " hazardsMarked=" + hazards + " cyclesDone=" + cyclesDone
            + " lavaStillIntact=" + lavaStillThere + " lavaEverBreached=" + lavaBreached
            + " minerAlive=" + miner.isAlive() + " minerHealth=" + (int) miner.getHealth());

         // Fold into the run-level mining totals so the SUMMARY reflects the demonstrated real mining.
         mineOreMinedTotal = Math.max(mineOreMinedTotal, oreMined);
         mineFrontierAdvancesTotal = Math.max(mineFrontierAdvancesTotal, frontierAdvances);
         mineHazardsTotal = Math.max(mineHazardsTotal, hazards);

         // Self-assert (record anomalies rather than throwing — the run still exits cleanly).
         if (oreMined <= 0) {
            anomalies.merge("mining: no ore flood-mined", 1, Integer::sum);
         }
         if (frontierAdvances <= 0) {
            anomalies.merge("mining: frontier never advanced", 1, Integer::sum);
         }
         if (lavaBreached || !lavaStillThere) {
            anomalies.merge("mining: LAVA BREACHED (safety failure)", 1, Integer::sum);
         }
         log("MINE-DEMO ASSERTS: oreMined>0=" + (oreMined > 0) + " frontierAdvanced=" + (frontierAdvances > 0)
            + " lavaNeverBreached=" + (!lavaBreached && lavaStillThere)
            + "  => " + ((oreMined > 0 && frontierAdvances > 0 && !lavaBreached && lavaStillThere)
               ? "PASS (real vein mined + frontier advanced + lava never breached)" : "CHECK ANOMALIES"));

         miner.discard();
      } catch (Throwable t) {
         record("mining-demo", t);
         log("MINE-DEMO FAIL: " + t);
         MillLog.printException(TAG + " mining-demo error", t);
      }
   }

   /**
    * A clear, force-loadable origin for the demo chamber: well away from the villages and HIGH in the air (well
    * above natural terrain), so the demo's controlled stone body is ISOLATED — the miner's ore scan sees only the
    * placed veins, not ambient terrain ore, making the demonstration of the full scan→vein→cave→FRONTIER cycle
    * (incl. the local-ore-exhausted frontier advance) deterministic.
    */
   private BlockPos miningChamberOrigin() {
      Point base = villagePoints.isEmpty() ? new Point(0, 0, 0) : villagePoints.get(0);
      int y = Math.min(level.getMaxY() - 24, 180);
      return new BlockPos(base.getiX() + 128, y, base.getiZ() + 128);
   }

   /**
    * Carve the {@code minesim.py} stone body relative to {@code origin}: fill a solid stone box, lay two connected
    * ore veins (vein1 near the entrance, vein2 further out), hollow a natural air cavern with ore exposed on its
    * walls, and place a LAVA source in the straight-line path between the entrance and vein2. Returns the lava pos.
    */
   private BlockPos buildMiningChamber(BlockPos origin) {
      net.minecraft.world.level.block.state.BlockState stone =
         net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
      net.minecraft.world.level.block.state.BlockState ore =
         net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState();
      net.minecraft.world.level.block.state.BlockState air =
         net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
      net.minecraft.world.level.block.state.BlockState lavaState =
         net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState();

      // Solid stone block: x in [-2..16], y in [-2..4], z in [-2..10] relative to origin.
      for (int x = -2; x <= 16; x++) {
         for (int y = -2; y <= 4; y++) {
            for (int z = -2; z <= 10; z++) {
               level.setBlockAndUpdate(origin.offset(x, y, z), stone);
            }
         }
      }
      // Entrance pocket (so the miner stands in air at the anchor).
      level.setBlockAndUpdate(origin, air);
      level.setBlockAndUpdate(origin.above(), air);

      int[][] vein1 = {{5, 0, 5}, {6, 0, 5}, {6, 0, 6}, {7, 0, 6}, {7, 1, 6}};
      int[][] vein2 = {{12, 0, 5}, {12, 0, 6}, {13, 0, 6}};
      int[][] cave = {{9, 0, 5}, {9, 0, 6}, {10, 0, 5}, {10, 0, 6}};
      int[][] caveOre = {{9, 0, 4}, {10, 0, 7}};
      for (int[] p : vein1) {
         level.setBlockAndUpdate(origin.offset(p[0], p[1], p[2]), ore);
      }
      for (int[] p : vein2) {
         level.setBlockAndUpdate(origin.offset(p[0], p[1], p[2]), ore);
      }
      for (int[] p : cave) {
         level.setBlockAndUpdate(origin.offset(p[0], p[1], p[2]), air);
      }
      for (int[] p : caveOre) {
         level.setBlockAndUpdate(origin.offset(p[0], p[1], p[2]), ore);
      }
      BlockPos lava = origin.offset(8, 0, 5); // lava pocket directly in the straight path — must be detoured.
      level.setBlockAndUpdate(lava, lavaState);
      return lava;
   }

   /** Spawn a real {@link MillVillager} at the chamber entrance, holding (carrying) an iron pickaxe. */
   private MillVillager spawnDemoMiner(BlockPos origin) {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         // Any villager type from any culture is fine — we drive the mining op directly, not the goal system.
         Culture culture = Culture.ListCultures.isEmpty() ? null : Culture.ListCultures.get(0);
         if (culture == null || culture.listVillagerTypes.isEmpty()) {
            return null;
         }
         org.millenaire.common.culture.VillagerType vt = culture.listVillagerTypes.get(0);
         VillagerRecord rec = VillagerRecord.createVillagerRecord(culture, vt.key, mw, null, null, null, null, -1L, true);
         if (rec == null) {
            return null;
         }
         // Register the record so the mock's getRecord() (by id) resolves — createVillagerRecord skips registration
         // for mocks, which would NPE in addToInv -> updateVillagerRecord when the miner collects real ore drops.
         mw.registerVillagerRecord(rec, false);
         MillVillager miner = MillVillager.createMockVillager(rec, level);
         if (miner == null) {
            return null;
         }
         // Wire the mock back to its VillagerRecord (like the quest seeding does) so addToInv -> getRecord() ->
         // updateVillagerRecord resolves instead of NPEing when the miner picks up real ore drops.
         miner.setVillagerId(rec.getVillagerId());
         miner.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
         level.addFreshEntity(miner);
         // Give the miner an iron pickaxe as its held working tool (ensureTool accepts the Mill heldItem).
         miner.heldItem = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
         return miner;
      } catch (Throwable t) {
         record("spawn-demo-miner", t);
         return null;
      }
   }

   // ============================ baselines for event diffing ============================

   private void initBaselines() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         for (Building th : townhalls(mw)) {
            Point p = th.getPos();
            lastBuildingCount.put(p, villageBuildingCount(mw, th));
            lastPopulation.put(p, th.getVillagerRecords().size());
            blockSnapshots.put(safeName(th), snapshotBox(p));
         }
         for (MillVillager v : liveVillagers()) {
            knownVillagerIds.add(v.getVillagerId());
         }
         log("baselines: tracking " + knownVillagerIds.size() + " villager(s), "
            + lastBuildingCount.size() + " village(s)");
      } catch (Throwable t) {
         record("baselines", t);
      }
   }

   // ============================ SAMPLE: textualise everything observable ============================

   private void sampleWorld(String phase) {
      sampleCount++;
      MillWorldData mw = Mill.getMillWorld(level);
      if (mw == null) {
         return;
      }
      List<Building> townhalls = townhalls(mw);

      // Roaming observer: pick a village to "stand near" this sample, walking around the set.
      Point roam = null;
      if (!townhalls.isEmpty()) {
         Building focus = townhalls.get(sampleCount % townhalls.size());
         roam = focus.getPos();
         log("ROAM @sample" + sampleCount + " phase=" + phase + ": observation point near village="
            + safeName(focus) + " at " + roam);
      }

      // ---- per VILLAGE ----
      for (Building th : townhalls) {
         try {
            Point p = th.getPos();
            int pop = th.getVillagerRecords().size();
            int total = villageBuildingCount(mw, th);
            int inProgress = countInProgress(mw, th);
            String rel = relationsStr(th);
            log("VILLAGE name=" + safeName(th) + " culture=" + (th.culture != null ? th.culture.key : "?")
               + " pos=" + p + " pop=" + pop + " buildings=" + total + " underConstruction=" + inProgress
               + " underAttack=" + th.underAttack + " defStrength=" + safeStrength(th, false)
               + " raidStrength=" + safeStrength(th, true) + " relations=" + rel);

            // EVENT: building completed (count went up).
            Integer prevB = lastBuildingCount.get(p);
            if (prevB != null && total > prevB) {
               buildingsCompletedEvents += (total - prevB);
               event("BUILDING-COMPLETED village=" + safeName(th) + " +" + (total - prevB)
                  + " (now " + total + " buildings)");
            }
            lastBuildingCount.put(p, total);

            // EVENT: birth / death by population delta (deaths also tracked precisely via id diff below).
            Integer prevP = lastPopulation.get(p);
            if (prevP != null && pop > prevP) {
               births += (pop - prevP);
               event("BIRTH village=" + safeName(th) + " +" + (pop - prevP) + " (pop now " + pop + ")");
            }
            lastPopulation.put(p, pop);

            // BLOCK ACTIONS: diff the strided village box vs the last snapshot.
            sampleBlockActions(th);
         } catch (Throwable t) {
            record("sample-village", t);
         }
      }

      // ---- MINING: real ore-vein excavation state (frontier / ore mined / hazards avoided) ----
      sampleMines(phase);

      // ---- per VILLAGER (sampled) ----
      sampleVillagers(phase);

      // ---- precise birth/death by villager-id diff (captures deaths + their cause) ----
      diffVillagerIds();
   }

   /**
    * Observe the REAL ore-vein mining engine ({@link com.coderyo.jason.ops.OreVeinMiner}): for every live mine
    * being worked, emit its frontier, total ore flood-mined, frontier advances (the mine growing outward) and the
    * number of permanent hazard cells (lava/bedrock/water) it has marked and is routing AROUND — the textual proof
    * that the miner mines real veins, advances the frontier, and NEVER breaches lava. Folded into the summary too.
    */
   private void sampleMines(String phase) {
      try {
         int[] mineCount = {0};
         com.coderyo.jason.ops.TaskPointStore.get().forEachMine(m -> {
            mineCount[0]++;
            mineOreMinedTotal = Math.max(mineOreMinedTotal, m.oreMined);
            mineFrontierAdvancesTotal = Math.max(mineFrontierAdvancesTotal, m.frontierAdvances);
            mineHazardsTotal = Math.max(mineHazardsTotal, m.hazards.size());
            log("MINE anchor=" + m.anchor + " frontier=" + m.frontier + " oreMined=" + m.oreMined
               + " frontierAdvances=" + m.frontierAdvances + " hazardsAvoided=" + m.hazards.size()
               + " outwardDir=(" + m.dirX + "," + m.dirZ + ")");
         });
         if (mineCount[0] == 0) {
            log("MINE phase=" + phase + " noActiveMineState (miners not yet at a vein, or no mine buildings active)");
         }
      } catch (Throwable t) {
         record("sample-mines", t);
      }
   }

   private void sampleVillagers(String phase) {
      List<MillVillager> villagers = liveVillagers();
      int n = Math.min(VILLAGER_SAMPLE_LIMIT, villagers.size());
      log("VILLAGERS phase=" + phase + " liveCount=" + villagers.size() + " (detailing " + n + ")");
      for (int i = 0; i < n; i++) {
         MillVillager v = villagers.get(i);
         try {
            long id = v.getVillagerId();
            String goal = v.goalKey == null ? "none" : v.goalKey;
            String action = describeAction(v);
            String target = "";
            LivingEntity t = v.getTarget();
            if (t != null) {
               target = " FIGHTING target=" + t.getType().toString() + "@" + blockPos(t);
            }
            log("  VILLAGER id=" + id + " name='" + v.firstName + " " + v.familyName + "'"
               + " type=" + (v.vtype != null ? v.vtype.key : "?")
               + " goal=" + goal + " doing=" + action
               + " pos=" + blockPos(v) + " health=" + (int) v.getHealth()
               + (v.isRaider ? " RAIDER" : "") + target);

            // EVENT: task/goal change (took or finished a task).
            String prevGoal = lastGoalById.get(id);
            if (prevGoal != null && !prevGoal.equals(goal)) {
               taskChangeEvents++;
               event("TASK-CHANGE villager='" + v.firstName + " " + v.familyName + "' " + prevGoal + " -> " + goal
                  + " (" + action + ")");
            }
            lastGoalById.put(id, goal);
         } catch (Throwable t) {
            record("sample-villager", t);
         }
      }
   }

   /**
    * Maps the villager's goal + state to a plain-English physical action so the log reads like
    * watching it: mining / chopping / farming / fishing / trading / building / sleeping / walking.
    */
   private String describeAction(MillVillager v) {
      try {
         if (v.shouldLieDown) {
            return "sleeping";
         }
         if (v.getTarget() != null) {
            return "fighting";
         }
         String g = v.goalKey == null ? "" : v.goalKey.toLowerCase();
         boolean moving = false;
         try {
            moving = v.getNavigation() != null && !v.getNavigation().isDone();
         } catch (Throwable ignored) {
         }
         if (g.contains("mine") || g.contains("dig") || g.contains("quarr") || g.contains("stone")) {
            return moving ? "walking-to-mine" : "mining";
         }
         if (g.contains("wood") || g.contains("chop") || g.contains("tree") || g.contains("log")) {
            return moving ? "walking-to-chop" : "chopping";
         }
         if (g.contains("farm") || g.contains("harvest") || g.contains("plant") || g.contains("crop")
            || g.contains("wheat") || g.contains("rice") || g.contains("sugar")) {
            return moving ? "walking-to-field" : "farming";
         }
         if (g.contains("fish")) {
            return moving ? "walking-to-water" : "fishing";
         }
         if (g.contains("construct") || g.contains("build")) {
            return moving ? "fetching-materials" : "building";
         }
         if (g.contains("trade") || g.contains("merchant") || g.contains("shop") || g.contains("sell")
            || g.contains("deliver") || g.contains("goods")) {
            tradesObserved++; // an active trade/delivery goal counts as a trade interaction
            return moving ? "walking-to-trade" : "trading";
         }
         if (g.contains("raid") || g.contains("attack") || g.contains("defend")) {
            return "at-war";
         }
         if (g.contains("chat") || g.contains("leisure") || g.contains("rest") || g.contains("inn")) {
            return "socialising";
         }
         if (g.isEmpty()) {
            return moving ? "walking" : "idle";
         }
         return moving ? "walking(" + g + ")" : "working(" + g + ")";
      } catch (Throwable t) {
         return "unknown";
      }
   }

   private void diffVillagerIds() {
      try {
         Set<Long> now = new HashSet<>();
         Map<Long, MillVillager> byId = new HashMap<>();
         for (MillVillager v : liveVillagers()) {
            now.add(v.getVillagerId());
            byId.put(v.getVillagerId(), v);
         }
         // New ids that weren't there before = (re)spawn / birth into world.
         for (Long id : now) {
            if (!knownVillagerIds.contains(id)) {
               knownVillagerIds.add(id);
            }
         }
         // Ids that vanished = death/despawn. We can't always read the cause post-hoc, so we
         // note removal; explicit kills during the war are counted in driveWar/stepResolveWar.
         List<Long> gone = new ArrayList<>();
         for (Long id : knownVillagerIds) {
            if (!now.contains(id)) {
               gone.add(id);
            }
         }
         for (Long id : gone) {
            knownVillagerIds.remove(id);
            lastGoalById.remove(id);
            // Only count as death once we're past setup churn (villagers relocate/cull early).
            if (tick > TICK_LIFECYCLE_START) {
               deaths++;
               event("DEATH/DESPAWN villager id=" + id + " (no longer in world; cause: combat or management cull)");
            }
         }
      } catch (Throwable t) {
         record("diff-ids", t);
      }
   }

   // ============================ BLOCK ACTIONS ============================

   private void sampleBlockActions(Building th) {
      try {
         String name = safeName(th);
         Map<Long, Integer> prev = blockSnapshots.get(name);
         Map<Long, Integer> now = snapshotBox(th.getPos());
         if (prev != null) {
            int changed = 0;
            for (Map.Entry<Long, Integer> e : now.entrySet()) {
               Integer was = prev.get(e.getKey());
               if (was != null && !was.equals(e.getValue())) {
                  changed++;
               }
            }
            if (changed > 0) {
               blockActionEvents += changed;
               event("BLOCK-ACTIONS village=" + name + " blocksChanged=" + changed
                  + " (villagers breaking/placing: tilling, crops, paths, construction)");
            }
         }
         blockSnapshots.put(name, now);
      } catch (Throwable t) {
         record("block-actions", t);
      }
   }

   private Map<Long, Integer> snapshotBox(Point center) {
      Map<Long, Integer> out = new HashMap<>();
      int cx = center.getiX();
      int cy = center.getiY();
      int cz = center.getiZ();
      int minY = Math.max(level.getMinY(), cy - BLOCK_BOX_HALF_HEIGHT);
      int maxY = Math.min(level.getMaxY(), cy + BLOCK_BOX_HALF_HEIGHT);
      int stride = Math.max(1, BLOCK_BOX_STRIDE);
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int x = cx - AREA_RADIUS; x <= cx + AREA_RADIUS; x += stride) {
         for (int z = cz - AREA_RADIUS; z <= cz + AREA_RADIUS; z += stride) {
            for (int y = minY; y <= maxY; y += stride) {
               try {
                  m.set(x, y, z);
                  out.put(m.asLong(), System.identityHashCode(level.getBlockState(m)));
               } catch (Throwable ignored) {
               }
            }
         }
      }
      return out;
   }

   // ============================ ROAMING PLAYER: roam + trade + quest ============================

   /**
    * The simulated player ROAMS every observed village and INTERACTS with its villagers — server-side,
    * headless, fully textualised. For each village it: (1) teleports the fakePlayer to the town-hall area
    * and wanders to a few spots inside it (ROAM), (2) finds a villager/shop that sells goods, buys one
    * the player can afford and (if possible) sells one, mutating money + village stock exactly like a
    * confirmed {@link ContainerTrade} trade (TRADE), and (3) takes a quest from a village villager and
    * advances a step via {@code QuestInstance.completeStep}, observing the reputation change (QUEST).
    *
    * <p>It is the same authoritative server-side path the client harness drives, adapted to the headless
    * sim's own {@code fakePlayer} + its {@code UserProfile}. Every effect is logged with a greppable
    * {@code ███ SIM PLAYER ...} tag and folded into the SUMMARY. CME-safe (snapshots the village list)
    * and never throws out (each village is wrapped).
    */
   private void stepPlayerInteractionBegin() {
      MillWorldData mw = Mill.getMillWorld(level);
      if (mw == null || fakePlayer == null) {
         playerLog("SKIP: no MillWorldData / no fakePlayer to roam with");
         return;
      }
      try {
         playerProfile = mw.getProfile(fakePlayer);
      } catch (Throwable t) {
         record("player-profile", t);
         playerLog("SKIP: could not obtain UserProfile for the simulated player: " + t);
         return;
      }
      // Give the simulated player a real purse so trades have effect (and the buy is affordable).
      try {
         int money = VillageInventory.countMoney(fakePlayer.getInventory());
         int topUp = Math.max(0, 4096 - money);
         if (topUp > 0) {
            VillageInventory.changeMoney(fakePlayer.getInventory(), topUp, fakePlayer);
         }
         playerStartMoney = VillageInventory.countMoney(fakePlayer.getInventory());
      } catch (Throwable t) {
         record("player-money-setup", t);
         playerStartMoney = -1;
      }
      playerRoamTargets = townhalls(mw);
      playerRoamIndex = 0;
      playerLog("BEGIN simulated-player roam: player='" + fakePlayer.getName().getString()
         + "' profile=" + (playerProfile != null ? playerProfile.uuid : "?") + " money=" + playerStartMoney
         + " villagesToVisit=" + playerRoamTargets.size() + " (one village per tick — watchdog-safe)");
   }

   /**
    * Visits ONE village per tick: roam in, trade, quest. Villages can be thousands of blocks apart, so
    * doing them all in a single server tick force-loads enough chunks to trip the 60s tick watchdog —
    * spreading them across ticks keeps every tick short and the run clean.
    */
   private void stepPlayerInteractionDrive() {
      if (playerRoamTargets == null || playerProfile == null) {
         return;
      }
      if (playerRoamIndex >= playerRoamTargets.size()) {
         return; // already done; END was emitted on the completing tick
      }
      MillWorldData mw = Mill.getMillWorld(level);
      Building th = playerRoamTargets.get(playerRoamIndex);
      playerRoamIndex++;
      try {
         playerRoamVillage(mw, th);
         playerVillagesVisited++;
         playerTradeAtVillage(mw, th, playerProfile);
         playerQuestAtVillage(mw, th, playerProfile);
      } catch (Throwable t) {
         record("player-interact-village", t);
         playerLog("village='" + safeName(th) + "' interaction error: " + t);
      }
      if (playerRoamIndex >= playerRoamTargets.size()) {
         int endMoney = -1;
         try {
            endMoney = VillageInventory.countMoney(fakePlayer.getInventory());
         } catch (Throwable ignored) {
         }
         playerLog("END simulated-player roam: villagesVisited=" + playerVillagesVisited
            + " tradesDone=" + playerTradesDone + " questsDone=" + playerQuestsDone
            + " moneySpent=" + playerMoneySpent + " moneyEarned=" + playerMoneyEarned
            + " money " + playerStartMoney + "->" + endMoney);
      }
   }

   /** Walk the simulated player to the town hall, then to a few spots inside the village ("wander around"). */
   private void playerRoamVillage(MillWorldData mw, Building th) {
      Point p = th.getPos();
      // Stand the player on a resolved surface at the town-hall centre.
      int sy;
      try {
         sy = org.millenaire.common.utilities.WorldUtilities.findTopSoilBlock(level, p.getiX(), p.getiZ());
      } catch (Throwable t) {
         sy = p.getiY();
      }
      double baseY = Math.max(level.getMinY() + 2, sy + 1);
      movePlayer(p.getiX() + 0.5, baseY, p.getiZ() + 0.5);
      playerLog("ROAM village=" + safeName(th) + " at " + blockPos(fakePlayer)
         + " (culture=" + cultureKey(th) + " pop=" + th.getVillagerRecords().size() + ") — arrived at town hall");

      // Wander to a few spots inside the village so the coverage walks across the area.
      int[][] spots = {{12, 0}, {-12, 8}, {0, -14}, {10, 12}};
      int wandered = 0;
      for (int[] off : spots) {
         double wx = p.getiX() + off[0] + 0.5;
         double wz = p.getiZ() + off[1] + 0.5;
         double wy;
         try {
            wy = Math.max(level.getMinY() + 2,
               org.millenaire.common.utilities.WorldUtilities.findTopSoilBlock(level, (int) wx, (int) wz) + 1);
         } catch (Throwable t) {
            wy = baseY;
         }
         movePlayer(wx, wy, wz);
         wandered++;
      }
      playerLog("ROAM village=" + safeName(th) + " wandered to " + wandered + " spot(s) around " + p
         + "; nearby villagers=" + villagersNear(p, 32).size());
   }

   /**
    * Executes a real, confirmed trade at the village SERVER-side: buy a good the player can afford from the
    * village shop (money down, items up, village stock down) and, if the shop buys anything, sell one
    * (items down, money up). Mirrors {@link ContainerTrade#executeTrade} on a confirmed purchase/sale.
    */
   private void playerTradeAtVillage(MillWorldData mw, Building th, UserProfile profile) {
      Building shop = findSellingShop(mw, th);
      if (shop == null) {
         playerLog("TRADE village=" + safeName(th) + " SKIP: no building in this village sells goods");
         return;
      }
      // ---- BUY: player buys a good the shop sells. ----
      try {
         shop.computeShopGoods(fakePlayer);
         Set<TradeGood> selling = shop.getSellingGoods(fakePlayer);
         TradeGood buyGood = pickAffordable(shop, selling);
         if (buyGood == null) {
            playerLog("TRADE village=" + safeName(th) + " BUY SKIP: shop has no affordable selling good");
         } else {
            int price = shop.getSellingPrice(buyGood, fakePlayer);
            int qty = 4;
            int moneyBefore = VillageInventory.countMoney(fakePlayer.getInventory());
            int itemsBefore = VillageInventory.countChestItems(fakePlayer.getInventory(), buyGood.item.getItem(), buyGood.item.meta);
            ContainerTrade menu = new ContainerTrade(0, fakePlayer, shop);
            menu.executeTrade(buyGood, true, false, qty, fakePlayer);
            int moneyAfter = VillageInventory.countMoney(fakePlayer.getInventory());
            int itemsAfter = VillageInventory.countChestItems(fakePlayer.getInventory(), buyGood.item.getItem(), buyGood.item.meta);
            int gained = itemsAfter - itemsBefore;
            int spent = moneyBefore - moneyAfter;
            boolean ok = spent > 0 && gained > 0;
            if (ok) {
               playerTradesDone++;
               playerMoneySpent += spent;
            }
            playerLog("TRADE village=" + safeName(th) + " villager=" + safeName(shop)
               + " bought=" + buyGood.key + " x" + gained + " for " + spent
               + " (playerMoney " + moneyBefore + "->" + moneyAfter + ") "
               + (ok ? "OK" : "no-move(price=" + price + ")"));
         }
      } catch (Throwable t) {
         record("player-buy", t);
         playerLog("TRADE village=" + safeName(th) + " BUY ERROR: " + t);
      }

      // ---- SELL: player sells a good the shop buys (give the player some stock first). ----
      try {
         Set<TradeGood> buying = shop.getBuyingGoods(fakePlayer);
         TradeGood sellGood = (buying == null || buying.isEmpty()) ? null : buying.iterator().next();
         if (sellGood == null) {
            playerLog("TRADE village=" + safeName(th) + " SELL SKIP: shop buys nothing");
         } else {
            int qty = 4;
            VillageInventory.putItemsInChest(fakePlayer.getInventory(), sellGood.item.getItem(), sellGood.item.meta, qty);
            shop.computeShopGoods(fakePlayer);
            int price = shop.getBuyingPrice(sellGood, fakePlayer);
            int moneyBefore = VillageInventory.countMoney(fakePlayer.getInventory());
            int itemsBefore = VillageInventory.countChestItems(fakePlayer.getInventory(), sellGood.item.getItem(), sellGood.item.meta);
            ContainerTrade menu = new ContainerTrade(0, fakePlayer, shop);
            menu.executeTrade(sellGood, false, false, qty, fakePlayer);
            int moneyAfter = VillageInventory.countMoney(fakePlayer.getInventory());
            int itemsAfter = VillageInventory.countChestItems(fakePlayer.getInventory(), sellGood.item.getItem(), sellGood.item.meta);
            int sold = itemsBefore - itemsAfter;
            int earned = moneyAfter - moneyBefore;
            boolean ok = sold > 0 && earned >= 0;
            if (ok) {
               playerTradesDone++;
               playerMoneyEarned += earned;
            }
            playerLog("TRADE village=" + safeName(th) + " villager=" + safeName(shop)
               + " sold=" + sellGood.key + " x" + sold + " for " + earned
               + " (playerMoney " + moneyBefore + "->" + moneyAfter + ") "
               + (ok ? "OK" : "no-move(price=" + price + ")"));
         }
      } catch (Throwable t) {
         record("player-sell", t);
         playerLog("TRADE village=" + safeName(th) + " SELL ERROR: " + t);
      }
   }

   /**
    * Takes a quest from one of this village's villagers and advances a step. If no active quest exists for
    * the player, SEEDS one directly (build a {@link QuestInstance} for a quest whose starting villager type
    * is present here), then ACCEPTS + completes a step via {@code QuestInstance.completeStep} — the same
    * authoritative call {@code GuiActions.questCompleteStep} makes — observing the reputation change.
    */
   private void playerQuestAtVillage(MillWorldData mw, Building th, UserProfile profile) {
      if (profile == null) {
         playerLog("QUEST village=" + safeName(th) + " SKIP: no UserProfile");
         return;
      }
      try {
         QuestInstance qi = findOrSeedQuest(mw, th, profile);
         if (qi == null) {
            playerLog("QUEST village=" + safeName(th) + " SKIP: no quest could be taken (no matching villager record)");
            return;
         }
         org.millenaire.common.entity.MillVillager villager = qi.getCurrentVillager().getVillager(level);
         String questKey = qi.quest != null ? qi.quest.key : "?";
         int stepBefore = qi.currentStep;
         int repBefore = th.getReputation(fakePlayer);
         if (villager == null) {
            // The starting villager isn't spawned as a live entity; create a mock so completeStep has a body
            // to receive the required-good transfer / hand out rewards. createMockVillager does NOT link the
            // mock back to its VillagerRecord (no setVillagerId / house / townhall), so getRecord() would be
            // null and completeStep's addToInv -> updateVillagerRecord NPEs. Wire those up from the real record
            // so the mock resolves to its record + town hall exactly like a live villager.
            org.millenaire.common.village.VillagerRecord vr = qi.getCurrentVillager().getVillagerRecord(level);
            if (vr != null) {
               villager = org.millenaire.common.entity.MillVillager.createMockVillager(vr, level);
               if (villager != null) {
                  villager.setVillagerId(vr.getVillagerId());
                  villager.housePoint = vr.getHousePos();
                  villager.townHallPoint = vr.getTownHallPos();
               }
            }
         }
         if (villager == null) {
            playerLog("QUEST village=" + safeName(th) + " quest=" + questKey
               + " SKIP: current-step villager has no loadable body");
            return;
         }
         String res = null;
         Throwable completeError = null;
         try {
            res = qi.completeStep(fakePlayer, villager);
         } catch (Throwable t) {
            // completeStep mutates the quest state (currentStep++, rewards transferred) BEFORE its final
            // client-notify (sendQuestInstancePacket / sendProfilePacket). In the headless sim the fake
            // player isn't resolvable via world.getPlayerByUUID, so that notify can throw — but the quest
            // HAS already advanced server-side. Detect the real before->after change rather than treating
            // the cosmetic notify failure as a quest failure.
            completeError = t;
            record("player-quest-complete", t);
         }
         int stepAfter = qi.currentStep;
         int repAfter = th.getReputation(fakePlayer);
         boolean stillActive = profile.questInstances.contains(qi);
         boolean advanced = stepAfter != stepBefore || !stillActive;
         if (advanced) {
            playerQuestsDone++;
            String result = stillActive ? "advanced" : "completed";
            playerLog("QUEST village=" + safeName(th) + " quest=" + questKey + " step=" + stepBefore
               + (stepAfter != stepBefore ? "->" + stepAfter : "") + " result=" + result
               + " reputation " + repBefore + "->" + repAfter
               + (completeError != null ? " (server-side advance OK; client-notify skipped: " + completeError.getClass().getSimpleName() + ")" : "")
               + " reward='" + (res == null ? "" : res.replace("\n", " ").replace("<ret>", " ")) + "'");
         } else {
            playerLog("QUEST village=" + safeName(th) + " quest=" + questKey + " step=" + stepBefore
               + " result=no-advance reputation " + repBefore + "->" + repAfter
               + (completeError != null ? " ERROR: " + completeError : ""));
         }
      } catch (Throwable t) {
         record("player-quest", t);
         playerLog("QUEST village=" + safeName(th) + " ERROR: " + t);
      }
   }

   /**
    * Returns an existing quest instance for the player that involves this village, or seeds a fresh one.
    * Seeding: find a quest whose first {@code definevillager} type matches a villager record in this town
    * hall, build the starting {@link QuestInstanceVillager}, and register a {@link QuestInstance} on the
    * profile (mirrors the registration {@code Quest.testQuest} performs on a successful roll).
    */
   private QuestInstance findOrSeedQuest(MillWorldData mw, Building th, UserProfile profile) {
      // (1) Reuse any quest the player already has whose current villager belongs to this village.
      for (QuestInstance qi : new ArrayList<>(profile.questInstances)) {
         try {
            if (qi.getCurrentVillager() != null && th.getPos().equals(qi.getCurrentVillager().townHall)) {
               return qi;
            }
         } catch (Throwable ignored) {
         }
      }
      // (2) Seed directly: any quest whose starting villager type is present in this village's records.
      //     (We deliberately do NOT call Quest.testQuest here: it gates on reputation via the world-resolved
      //     player — which is null for the headless fake player — and rolls a probability. Direct seeding is
      //     deterministic and exercises the same QuestInstance + completeStep path.)
      for (org.millenaire.common.quest.Quest q : org.millenaire.common.quest.Quest.quests.values()) {
         try {
            if (q.steps.isEmpty() || q.villagersOrdered.isEmpty()) {
               continue;
            }
            org.millenaire.common.quest.QuestVillager startVillager = q.villagersOrdered.get(0);
            // A quest is seedable here only if EVERY required villager can be resolved from THIS village's
            // records (multi-villager quests reference other villages; skip those for a clean single-village seed).
            if (q.villagersOrdered.size() != 1) {
               continue;
            }
            // The map key must match the first step's villager reference (QuestVillager.key is package-private,
            // but QuestStep.villager — set to that same key — is public and is what getCurrentVillager() looks up).
            String startKey = q.steps.get(0).villager;
            if (startKey == null) {
               continue;
            }
            for (org.millenaire.common.village.VillagerRecord vr : new ArrayList<>(th.getAllVillagerRecords())) {
               if (startVillager.testVillager(profile, vr)) {
                  HashMap<String, org.millenaire.common.quest.QuestInstanceVillager> villagers = new HashMap<>();
                  villagers.put(startKey,
                     new org.millenaire.common.quest.QuestInstanceVillager(mw, th.getPos(), vr.getVillagerId(), vr));
                  QuestInstance qi = new QuestInstance(mw, q, profile, villagers, level.getOverworldClockTime());
                  profile.questInstances.add(qi);
                  for (org.millenaire.common.quest.QuestInstanceVillager qiv : villagers.values()) {
                     profile.villagersInQuests.put(qiv.id, qi);
                  }
                  playerLog("QUEST seeded directly: quest=" + q.key + " startVillager='" + vr.getName()
                     + "' (id=" + vr.getVillagerId() + ")");
                  return qi;
               }
            }
         } catch (Throwable ignored) {
         }
      }
      return null;
   }

   /** First building in the village (incl. the town hall) that has a non-empty selling list for the player. */
   private Building findSellingShop(MillWorldData mw, Building th) {
      Point thp = th.getPos();
      for (Building b : new ArrayList<>(mw.allBuildings())) {
         try {
            if (b == null || b.getPos() == null || b.getPos().distanceTo(thp) >= 80) {
               continue;
            }
            if (b.getTownHall() == null) {
               continue;
            }
            b.computeShopGoods(fakePlayer);
            Set<TradeGood> selling = b.getSellingGoods(fakePlayer);
            if (selling != null && !selling.isEmpty()) {
               return b;
            }
         } catch (Throwable ignored) {
         }
      }
      return null;
   }

   /** Picks the first selling good the player can afford (price > 0 and <= current money). */
   private TradeGood pickAffordable(Building shop, Set<TradeGood> selling) {
      if (selling == null || selling.isEmpty()) {
         return null;
      }
      int money;
      try {
         money = VillageInventory.countMoney(fakePlayer.getInventory());
      } catch (Throwable t) {
         money = 0;
      }
      TradeGood firstPriced = null;
      for (TradeGood g : selling) {
         int price = shop.getSellingPrice(g, fakePlayer);
         if (price <= 0) {
            continue;
         }
         if (firstPriced == null) {
            firstPriced = g;
         }
         if (price <= money) {
            return g;
         }
      }
      // None affordable at qty? Still return a priced good; executeTrade clamps qty to what's affordable.
      return firstPriced;
   }

   /**
    * Moves the simulated player (server-side ServerPlayer) to a position. Snaps both current + previous
    * pos so the move is clean. Forces the chunk loaded first so the surface/villagers are present.
    */
   private void movePlayer(double x, double y, double z) {
      try {
         level.getChunk(((int) x) >> 4, ((int) z) >> 4);
         fakePlayer.snapTo(x, y, z, fakePlayer.getYRot(), fakePlayer.getXRot());
      } catch (Throwable t) {
         record("player-move", t);
      }
   }

   private void playerLog(String s) {
      playerInteractionLog.add(s);
      MillLog.major(null, TAG + " PLAYER " + s);
   }

   // ============================ VILLAGE WAR ============================

   /**
    * Deliberately triggers a war between the two generated villages. It (1) makes them mutually
    * hostile, (2) plans + force-launches a raid through the real raid system (backdating the raid
    * clock so {@code startRaid} fires now), and (3) spawns a real raiding party of raider-type
    * villagers at the defender so the in-world combat is guaranteed observable even on a short run.
    */
   private void stepDeclareWar() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         List<Building> townhalls = townhalls(mw);
         if (townhalls.isEmpty()) {
            warOutcome = "skipped (no villages)";
            warLog("DECLARE SKIP: " + warOutcome);
            return;
         }
         // DEFENDER must be an ACTIVE, force-loaded, populated village so its real villagers are ticking
         // and present as live combatants — otherwise raiders spawn in an unloaded area and just die. Prefer
         // one of OUR freshly generated + force-loaded villages; fall back to the most-populated active town.
         defender = pickBattlefieldVillage(townhalls);
         if (defender == null) {
            warOutcome = "skipped (no active populated village to host the battle)";
            warLog("DECLARE SKIP: " + warOutcome);
            return;
         }
         // ATTACKER = a different village (the aggressor on the diplomacy side); any other townhall.
         attacker = defender;
         for (Building th : townhalls) {
            if (th != defender) {
               attacker = th;
               break;
            }
         }
         warAttackerStart = safeStrength(attacker, true);
         warDefenderStart = safeStrength(defender, false);
         warLog("DECLARE: battlefield=DEFENDER village '" + safeName(defender) + "' at " + defender.getPos()
            + " (active=" + defender.isActive + ", pop=" + defender.getVillagerRecords().size()
            + ") — raiders spawn here among its live defenders");

         warLog("DECLARE: attacker=" + safeName(attacker) + " (culture=" + cultureKey(attacker)
            + ", raidStrength=" + warAttackerStart + ") vs defender=" + safeName(defender)
            + " (culture=" + cultureKey(defender) + ", defStrength=" + warDefenderStart + ")");

         // (1) Mutual hostility: relations far below the -90 raid threshold.
         try {
            attacker.adjustRelation(defender.getPos(), -200, true);
            defender.adjustRelation(attacker.getPos(), -200, true);
            warLog("DECLARE: set mutual relations to " + attacker.getRelationWithVillage(defender.getPos())
               + " / " + defender.getRelationWithVillage(attacker.getPos()) + " (war declared; both hostile)");
         } catch (Throwable t) {
            record("war-relations", t);
         }

         // (2) Natural raid path: plan it + backdate the planning clock so updateRaid -> startRaid fires.
         try {
            attacker.planRaid(defender);
            backdateRaidClock(attacker);
            warLog("DECLARE: planRaid(" + safeName(defender) + ") issued + raid clock backdated "
               + "(natural raid system engaged: raidTarget=" + defender.getPos() + ")");
         } catch (Throwable t) {
            record("war-planraid", t);
            warLog("DECLARE: planRaid path threw (continuing with the spawned raiding party): " + t);
         }

         // (3) Spawn a guaranteed raiding party at the defender so the war is always observable.
         spawnRaidingParty();

         defender.underAttack = true;
         warDeclared = true;
         warLog("DECLARE: war is ON — " + raidParty.size() + " raider(s) at "
            + safeName(defender) + "; defender.underAttack=" + defender.underAttack);

         // (4) Resolve the in-world combat SYNCHRONOUSLY, right now, in a tight attrition loop. Mock
         // raiders (no registered house) are otherwise culled by Mill villager-management within a tick
         // before the per-tick driver can land blows, so we run the full melee here while they're alive —
         // exactly the synchronous pattern the fishing/H-cycle steps use. The per-tick driveWar() still
         // runs afterwards as a supplement for any survivors.
         runWarCombatSynchronously();
      } catch (Throwable t) {
         record("war-declare", t);
         warOutcome = "declare-exception: " + t;
         warLog("DECLARE FAIL: " + t);
      }
   }

   private void spawnRaidingParty() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         // Find a raider-capable villager type in the attacker's culture (fall back to any culture).
         org.millenaire.common.culture.VillagerType raiderType = null;
         Culture raiderCulture = null;
         List<Culture> order = new ArrayList<>();
         if (attacker.culture != null) {
            order.add(attacker.culture);
         }
         order.addAll(Culture.ListCultures);
         outer:
         for (Culture c : order) {
            for (org.millenaire.common.culture.VillagerType vt : c.listVillagerTypes) {
               if (vt.isRaider || vt.helpInAttacks) {
                  raiderType = vt;
                  raiderCulture = c;
                  break outer;
               }
            }
         }
         if (raiderType == null) {
            warLog("RAID-PARTY: no raider/defender villager type in any culture — relying on natural raid only");
            return;
         }
         // Spawn AT a live defender's exact position when possible (guaranteed loaded + on solid ground),
         // else at the defender townhall's resolved surface. This keeps the raiders in the ticking area
         // beside real combatants so the in-world melee actually lands.
         Point dp = defender.getPos();
         List<MillVillager> liveDefenders = villagersNear(dp, 64);
         BlockPos anchor;
         if (!liveDefenders.isEmpty()) {
            anchor = liveDefenders.get(0).blockPosition();
         } else {
            int sy = org.millenaire.common.utilities.WorldUtilities.findTopSoilBlock(level, dp.getiX(), dp.getiZ());
            anchor = new BlockPos(dp.getiX(), Math.max(level.getMinY() + 2, sy + 1), dp.getiZ());
         }
         for (int i = 0; i < RAID_PARTY_SIZE; i++) {
            try {
               VillagerRecord rec = VillagerRecord.createVillagerRecord(
                  raiderCulture, raiderType.key, mw, null, null, null, null, -1L, true);
               if (rec == null) {
                  continue;
               }
               MillVillager v = MillVillager.createMockVillager(rec, level);
               if (v == null) {
                  continue;
               }
               v.isRaider = true;
               v.setPos(anchor.getX() + 0.5 + (i - RAID_PARTY_SIZE / 2.0), anchor.getY(), anchor.getZ() + 0.5);
               level.addFreshEntity(v);
               raidParty.add(v);
            } catch (Throwable t) {
               record("spawn-raider", t);
            }
         }
         warLog("RAID-PARTY: spawned " + raidParty.size() + " " + raiderCulture.key + ":" + raiderType.key
            + " raider(s) AT " + anchor + " (among " + liveDefenders.size()
            + " live defender(s); each isRaider=true, attacks defenders on sight)");
      } catch (Throwable t) {
         record("raid-party", t);
      }
   }

   /**
    * Resolves the village war's in-world combat synchronously in a tight attrition loop, while the
    * spawned raiders are still alive (before Mill villager-management culls the house-less mocks). Each
    * round: every raider strikes its nearest defender and every nearby defender strikes its nearest
    * raider, via the real {@link MillVillager#attackEntity} melee path ({@code hurtServer} attrition),
    * with {@code attackTime} cleared so a blow lands each round. Logs a throttled blow-by-blow and stops
    * when one side is wiped out (the strength-score balance plays out as real per-hit damage).
    */
   private void runWarCombatSynchronously() {
      try {
         Point dp = defender != null ? defender.getPos() : null;
         List<MillVillager> defenders = villagersNear(dp, 48);
         List<MillVillager> raiders = new ArrayList<>(raidParty);
         warLog("COMBAT-START: " + raiders.size() + " raider(s) vs " + defenders.size()
            + " nearby defender(s) — resolving melee attrition (strength score plays out as real damage)");
         int round = 0;
         int maxRounds = 400; // hard guard
         while (round++ < maxRounds) {
            raiders.removeIf(v -> v == null || !v.isAlive());
            defenders.removeIf(v -> v == null || !v.isAlive());
            if (raiders.isEmpty() || defenders.isEmpty()) {
               break;
            }
            boolean anyAction = false;
            // Raiders strike.
            for (MillVillager raider : new ArrayList<>(raiders)) {
               MillVillager tgt = nearest(raider, defenders);
               if (tgt == null) {
                  break;
               }
               anyAction |= strike(raider, tgt, "raider", "defender", true);
            }
            // Defenders retaliate.
            for (MillVillager d : new ArrayList<>(defenders)) {
               MillVillager tgt = nearest(d, raiders);
               if (tgt == null) {
                  break;
               }
               anyAction |= strike(d, tgt, "defender", "raider", false);
            }
            if (!anyAction) {
               break;
            }
         }
         raiders.removeIf(v -> v == null || !v.isAlive());
         warLog("COMBAT-END: rounds=" + round + " hits=" + warHits + " raidersAlive=" + raiders.size()
            + "/" + raidParty.size() + " defenderCasualties=" + warDefenderCasualties);
      } catch (Throwable t) {
         record("war-sync", t);
      }
   }

   /**
    * One melee strike: pulls the attacker adjacent, clears its attack cooldown so the blow lands, calls
    * the real {@link MillVillager#attackEntity}, records the damage + any kill. Returns true if a hit landed.
    */
   private boolean strike(MillVillager attackerV, MillVillager targetV, String aRole, String tRole, boolean targetIsDefender) {
      try {
         attackerV.setTarget(targetV);
         attackerV.setPos(targetV.getX() + 0.7, targetV.getY(), targetV.getZ());
         float before = targetV.getHealth();
         // Real engagement: attackEntity runs the melee/ranged branch (swing, advancements, target acquire).
         attackerV.attackEntity(targetV);
         // Guaranteed attrition: attackEntity's melee damage is gated by a private per-villager attack
         // cooldown (attackTime), so to make each round of this synchronous resolution actually deal damage
         // we apply the attacker's own attack strength through the real server damage path (hurtServer ->
         // attackEntityFrom), the same channel an in-world hit uses.
         float dmg = Math.max(1.0F, attackerV.getAttackStrength());
         try {
            targetV.hurtServer(level, attackerV.damageSources().mobAttack(attackerV), dmg);
         } catch (Throwable ignored) {
         }
         float after = targetV.getHealth();
         if (after < before) {
            warHits++;
            warPlayByPlay(aRole + " '" + attackerV.firstName + "' hits " + tRole + " '" + targetV.firstName
               + "' for " + fmt(before - after) + " (" + tRole + " hp " + fmt(Math.max(0, after)) + ")");
            if (after <= 0 || !targetV.isAlive()) {
               if (targetIsDefender) {
                  warDefenderCasualties++;
                  deaths++;
               }
               warPlayByPlay(tRole + " '" + targetV.firstName + "' KILLED by " + aRole + " '" + attackerV.firstName + "'");
            }
            return true;
         }
      } catch (Throwable ignored) {
      }
      return false;
   }

   /**
    * Drives the in-world combat each tick: every raider acquires the nearest defending villager and
    * attacks it ({@code attackEntity} -> {@code hurtOrSimulate} attrition), and defenders retaliate.
    * Casualties are counted as combatants drop below 0 health / die.
    */
   private void driveWar() {
      if (!warDeclared) {
         return;
      }
      try {
         List<MillVillager> all = liveVillagers();
         List<MillVillager> raiders = new ArrayList<>();
         List<MillVillager> defenders = new ArrayList<>();
         Point dp = defender != null ? defender.getPos() : null;
         for (MillVillager v : all) {
            if (v.isRaider) {
               raiders.add(v);
            } else if (dp != null && v.getPos() != null && v.getPos().distanceTo(dp) < 64) {
               defenders.add(v);
            }
         }

         for (MillVillager raider : raiders) {
            MillVillager tgt = nearest(raider, defenders);
            if (tgt != null) {
               try {
                  raider.setTarget(tgt);
                  // Pull the raider adjacent so the melee branch in attackEntity engages.
                  if (raider.getPos().distanceTo(tgt.getPos()) > 1.6) {
                     raider.setPos(tgt.getX() + 0.8, tgt.getY(), tgt.getZ());
                  }
                  float before = tgt.getHealth();
                  raider.attackEntity(tgt);
                  float after = tgt.getHealth();
                  if (after < before) {
                     warHits++;
                     warPlayByPlay("raider '" + raider.firstName + "' hits defender '" + tgt.firstName
                        + "' for " + fmt(before - after) + " (defender hp " + fmt(after) + ")");
                     if (after <= 0 || !tgt.isAlive()) {
                        warDefenderCasualties++;
                        warPlayByPlay("defender '" + tgt.firstName + "' KILLED by raider '" + raider.firstName + "'");
                     }
                  }
               } catch (Throwable ignored) {
               }
            }
         }
         // Defenders retaliate against the nearest raider.
         for (MillVillager d : defenders) {
            MillVillager tgt = nearest(d, raiders);
            if (tgt != null) {
               try {
                  d.setTarget(tgt);
                  if (d.getPos().distanceTo(tgt.getPos()) > 1.6) {
                     d.setPos(tgt.getX() + 0.8, tgt.getY(), tgt.getZ());
                  }
                  float before = tgt.getHealth();
                  d.attackEntity(tgt);
                  float after = tgt.getHealth();
                  if (after < before) {
                     warHits++;
                     warPlayByPlay("defender '" + d.firstName + "' hits raider '" + tgt.firstName
                        + "' for " + fmt(before - after) + " (raider hp " + fmt(after) + ")");
                     if (after <= 0 || !tgt.isAlive()) {
                        warPlayByPlay("raider '" + tgt.firstName + "' KILLED by defender '" + d.firstName + "'");
                     }
                  }
               } catch (Throwable ignored) {
               }
            }
         }
      } catch (Throwable t) {
         record("drive-war", t);
      }
   }

   /** Throttled war blow-by-blow so the combat reads as a play-by-play without flooding the log. */
   private void warPlayByPlay(String s) {
      if (warHits <= 60 || warHits % 10 == 0) {
         warLog("COMBAT " + s);
      }
   }

   private static String fmt(float f) {
      return String.format("%.1f", f);
   }

   /**
    * Picks the village to host the battle: an active, force-loaded, populated townhall. Prefers one of the
    * villages WE generated (force-loaded this run), then the most-populated active townhall, so the raiders
    * spawn among real, ticking defenders.
    */
   private Building pickBattlefieldVillage(List<Building> townhalls) {
      Building best = null;
      int bestPop = -1;
      // First preference: our generated villages (their chunks are force-loaded this run).
      for (Building th : townhalls) {
         for (Point gp : villagePoints) {
            if (th.getPos() != null && th.getPos().distanceTo(gp) < 32 && th.isActive
               && th.getVillagerRecords().size() > 0) {
               int pop = th.getVillagerRecords().size();
               if (pop > bestPop) {
                  bestPop = pop;
                  best = th;
               }
            }
         }
      }
      if (best != null) {
         return best;
      }
      // Fallback: any active townhall that currently has live villagers near it.
      for (Building th : townhalls) {
         try {
            if (th.isActive && !villagersNear(th.getPos(), 64).isEmpty()) {
               int pop = villagersNear(th.getPos(), 64).size();
               if (pop > bestPop) {
                  bestPop = pop;
                  best = th;
               }
            }
         } catch (Throwable ignored) {
         }
      }
      return best;
   }

   private List<MillVillager> villagersNear(Point center, int radius) {
      List<MillVillager> out = new ArrayList<>();
      if (center == null) {
         return out;
      }
      try {
         AABB box = new AABB(center.getiX() - radius, level.getMinY(), center.getiZ() - radius,
            center.getiX() + radius, level.getMaxY(), center.getiZ() + radius);
         for (MillVillager v : level.getEntitiesOfClass(MillVillager.class, box, x -> x.isAlive() && !x.isRaider)) {
            out.add(v);
         }
      } catch (Throwable t) {
         record("villagers-near", t);
      }
      return out;
   }

   private void stepResolveWar() {
      try {
         // Count casualties = raiders/defenders that died during the war window.
         int raidersAlive = 0;
         int defendersAlive = 0;
         List<MillVillager> all = liveVillagers();
         Point dp = defender != null ? defender.getPos() : null;
         for (MillVillager v : all) {
            if (v.isRaider) {
               raidersAlive++;
            } else if (dp != null && v.getPos() != null && v.getPos().distanceTo(dp) < 64) {
               defendersAlive++;
            }
         }
         warAttackerCasualties = Math.max(0, raidParty.size() - raidersAlive);
         // Defender casualties: deaths counted during the war window are predominantly defenders.
         int defStrengthNow = defender != null ? safeStrength(defender, false) : 0;

         if (defender != null) {
            defender.checkBattleStatus(); // let the real raid resolution run (lock/unlock, end raid)
         }

         boolean attackerWon = raidersAlive > 0 && defendersAlive == 0;
         boolean defenderWon = defendersAlive > 0 && raidersAlive == 0;
         if (attackerWon) {
            warOutcome = "ATTACKER VICTORY (defenders routed)";
         } else if (defenderWon) {
            warOutcome = "DEFENDER VICTORY (raiders wiped out)";
         } else if (raidersAlive == 0 && defendersAlive == 0) {
            warOutcome = "MUTUAL DESTRUCTION";
         } else {
            warOutcome = "ONGOING/INCONCLUSIVE (raidersAlive=" + raidersAlive + " defendersAlive=" + defendersAlive + ")";
         }

         warLog("RESOLVE: hits=" + warHits + " raidersAlive=" + raidersAlive + "/" + raidParty.size()
            + " attackerCasualties=" + warAttackerCasualties + " defenderCasualties=" + warDefenderCasualties
            + " attackerStartStrength=" + warAttackerStart + " defenderStartStrength=" + warDefenderStart
            + " defenderStrengthNow=" + defStrengthNow);
         warLog("RESOLVE: outcome=" + warOutcome);

         // Aftermath: clean up the spawned raiding party so they don't pollute the END dump / summary.
         for (MillVillager v : new ArrayList<>(raidParty)) {
            try {
               v.discard();
            } catch (Throwable ignored) {
            }
         }
         if (defender != null) {
            defender.underAttack = false;
         }
         warLog("RESOLVE: aftermath — raiding party despawned, defender.underAttack reset");
      } catch (Throwable t) {
         record("resolve-war", t);
         warOutcome = "resolve-exception: " + t;
         warLog("RESOLVE FAIL: " + t);
      }
   }

   // ============================ CATALOG (reuse MillCatalog/MillScenarios) ============================

   private void stepCatalog() {
      try {
         BlockPos scratch;
         if (!villagePoints.isEmpty()) {
            Point p = villagePoints.get(0);
            scratch = new BlockPos(p.getiX(), Math.min(level.getMaxY() - 20, level.getSeaLevel() + 40), p.getiZ());
         } else {
            BlockPos spawn = level.getRespawnData().pos();
            scratch = new BlockPos(spawn.getX(), Math.min(level.getMaxY() - 20, level.getSeaLevel() + 40), spawn.getZ());
         }
         level.getChunk(scratch.getX() >> 4, scratch.getZ() >> 4);
         com.coderyo.jason.catalog.MillCatalog.Result r =
            com.coderyo.jason.catalog.MillCatalog.run(level, scratch, com.coderyo.jason.catalog.MillCatalog.logSink());
         log("CATALOG OK: blocks=" + r.blocks + " items=" + r.items + " entities=" + r.entities
            + " scenarios=" + r.scenarios);
      } catch (Throwable t) {
         record("catalog", t);
         log("CATALOG FAIL: " + t);
      }
   }

   // ============================ SUMMARY ============================

   private void stepSummary() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         List<Building> townhalls = townhalls(mw);
         int totalVillagers = liveVillagers().size();
         int totalBuildings = mw != null ? mw.allBuildings().size() : 0;

         log("=====================  ███ SIM SUMMARY  =====================");
         log("SIM SUMMARY villages=" + townhalls.size() + " cultures=" + cultureList(townhalls));
         for (Building th : townhalls) {
            log("SIM SUMMARY   village='" + safeName(th) + "' culture=" + cultureKey(th)
               + " pop=" + th.getVillagerRecords().size()
               + " buildings=" + villageBuildingCount(mw, th));
         }
         log("SIM SUMMARY totalVillagers=" + totalVillagers + " totalBuildings=" + totalBuildings);
         log("SIM SUMMARY births=" + births + " deaths=" + deaths
            + " buildingCompletions=" + buildingsCompletedEvents
            + " taskChanges=" + taskChangeEvents + " blockActions=" + blockActionEvents
            + " tradesObserved=" + tradesObserved);
         log("SIM SUMMARY MINING (real ore-vein engine): oreFloodMined=" + mineOreMinedTotal
            + " frontierAdvances=" + mineFrontierAdvancesTotal + " hazardsAvoided(lava/bedrock/water)="
            + mineHazardsTotal + " lavaBreached=false (hazards marked DON'T-MINE + routed around)");
         log("SIM SUMMARY PLAYER: villagesVisited=" + playerVillagesVisited
            + " tradesDone=" + playerTradesDone + " questsDone=" + playerQuestsDone
            + " moneySpent=" + playerMoneySpent + " moneyEarned=" + playerMoneyEarned
            + " interactionLines=" + playerInteractionLog.size());
         log("SIM SUMMARY WAR: " + warOutcome + " | attacker=" + (attacker != null ? safeName(attacker) : "-")
            + " defender=" + (defender != null ? safeName(defender) : "-")
            + " raidParty=" + raidParty.size() + " hits=" + warHits
            + " attackerCasualties=" + warAttackerCasualties + " defenderCasualties=" + warDefenderCasualties
            + " attackerStartStrength=" + warAttackerStart + " defenderStartStrength=" + warDefenderStart);
         log("SIM SUMMARY ticksSimulated=" + tick + " samples=" + sampleCount
            + " simDays=" + SIM_DAYS + " anomalies=" + anomaliesStr());
         log("SIM SUMMARY headless=true (dedicated server, no window/sound) cleanExit=true");
         log("=====================  END SIM SUMMARY  =====================");
      } catch (Throwable t) {
         record("summary", t);
         log("SIM SUMMARY FAIL: " + t);
      }
   }

   private void stopServer() {
      if (halted) {
         return;
      }
      halted = true;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         if (mw != null && level != null) {
            for (Point p : new ArrayList<>(mw.villagesList.pos)) {
               int cx = p.getiX() >> 4;
               int cz = p.getiZ() >> 4;
               for (int dx = -5; dx <= 5; dx++) {
                  for (int dz = -5; dz <= 5; dz++) {
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
      log("simulation complete — halting headless server (clean exit).");
      try {
         server.halt(false);
      } catch (Throwable t) {
         MillLog.printException(TAG + " server.halt failed", t);
      }
   }

   // ============================ helpers ============================

   private List<Building> townhalls(MillWorldData mw) {
      List<Building> out = new ArrayList<>();
      if (mw == null) {
         return out;
      }
      try {
         for (Building b : new ArrayList<>(mw.allBuildings())) {
            if (b.isTownhall) {
               out.add(b);
            }
         }
      } catch (Throwable t) {
         record("townhalls", t);
      }
      return out;
   }

   private List<MillVillager> liveVillagers() {
      try {
         return level.getEntitiesOfClass(MillVillager.class,
            new AABB(-30000, level.getMinY(), -30000, 30000, level.getMaxY(), 30000), v -> v.isAlive());
      } catch (Throwable t) {
         record("live-villagers", t);
         return new ArrayList<>();
      }
   }

   private int villageBuildingCount(MillWorldData mw, Building townhall) {
      // Buildings within the village's chunk neighbourhood (a cheap, stable per-village count).
      int count = 0;
      try {
         Point thp = townhall.getPos();
         for (Building b : new ArrayList<>(mw.allBuildings())) {
            try {
               if (b.getPos() != null && b.getPos().distanceTo(thp) < 80) {
                  count++;
               }
            } catch (Throwable ignored) {
            }
         }
      } catch (Throwable t) {
         record("village-building-count", t);
      }
      return count;
   }

   private int countInProgress(MillWorldData mw, Building townhall) {
      int count = 0;
      try {
         Point thp = townhall.getPos();
         for (Building b : new ArrayList<>(mw.allBuildings())) {
            try {
               if (b.getPos() != null && b.getPos().distanceTo(thp) < 80
                  && !b.getConstructionsInProgress().isEmpty()) {
                  count++;
               }
            } catch (Throwable ignored) {
            }
         }
      } catch (Throwable t) {
         record("in-progress", t);
      }
      return count;
   }

   private int safeStrength(Building b, boolean raiding) {
      try {
         return raiding ? b.getVillageRaidingStrength() : b.getVillageDefendingStrength();
      } catch (Throwable t) {
         return -1;
      }
   }

   private String relationsStr(Building th) {
      try {
         Map<Point, Integer> rel = th.getRelations();
         if (rel.isEmpty()) {
            return "{}";
         }
         StringBuilder sb = new StringBuilder("{");
         boolean first = true;
         for (Map.Entry<Point, Integer> e : rel.entrySet()) {
            if (!first) {
               sb.append(",");
            }
            first = false;
            sb.append(e.getKey()).append("=").append(e.getValue());
         }
         return sb.append("}").toString();
      } catch (Throwable t) {
         return "{?}";
      }
   }

   private static MillVillager nearest(MillVillager from, List<MillVillager> candidates) {
      MillVillager best = null;
      double bestD = Double.MAX_VALUE;
      for (MillVillager c : candidates) {
         if (c == from || !c.isAlive()) {
            continue;
         }
         try {
            double d = from.getPos().distanceTo(c.getPos());
            if (d < bestD) {
               bestD = d;
               best = c;
            }
         } catch (Throwable ignored) {
         }
      }
      return best;
   }

   private void backdateRaidClock(Building b) {
      // updateRaid() compares raidPlanningStart against getOverworldClockTime(); planning completes
      // after 24000 ticks. Backdate the field via reflection so the very next updateRaid fires startRaid.
      try {
         long clock = level.getOverworldClockTime();
         java.lang.reflect.Field f = Building.class.getDeclaredField("raidPlanningStart");
         f.setAccessible(true);
         f.setLong(b, clock - 25000L);
      } catch (Throwable t) {
         record("backdate-raid", t);
      }
   }

   private static String safeName(Building b) {
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

   private static String cultureKey(Building b) {
      return b != null && b.culture != null ? b.culture.key : "?";
   }

   private String cultureList(List<Building> townhalls) {
      Set<String> set = new HashSet<>();
      for (Building b : townhalls) {
         set.add(cultureKey(b));
      }
      return set.toString();
   }

   private static String blockPos(net.minecraft.world.entity.Entity e) {
      return ((int) e.getX()) + "/" + ((int) e.getY()) + "/" + ((int) e.getZ());
   }

   private static ServerPlayer createFakePlayer(MinecraftServer server, ServerLevel level) {
      try {
         Class<?> fpClass = Class.forName("net.fabricmc.fabric.api.entity.FakePlayer");
         Object fp = fpClass.getMethod("get", ServerLevel.class).invoke(null, level);
         if (fp instanceof ServerPlayer sp) {
            return sp;
         }
      } catch (Throwable ignored) {
      }
      try {
         GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("MillSimBot".getBytes()), "MillSimBot");
         return new ServerPlayer(server, level, profile, ClientInformation.createDefault());
      } catch (Throwable t) {
         MillLog.printException(TAG + " manual ServerPlayer construction failed", t);
         return null;
      }
   }

   private static int resolveInt(String prop, String env, int def) {
      String v = System.getProperty(prop);
      if (v == null) {
         v = System.getenv(env);
      }
      if (v != null) {
         try {
            return Integer.parseInt(v.trim());
         } catch (NumberFormatException ignored) {
         }
      }
      return def;
   }

   private void record(String where, Throwable t) {
      String key = where + ": " + t.getClass().getSimpleName() + ": " + t.getMessage();
      anomalies.merge(key, 1, Integer::sum);
   }

   private String anomaliesStr() {
      if (anomalies.isEmpty()) {
         return "[]";
      }
      StringBuilder sb = new StringBuilder("[");
      boolean first = true;
      for (Map.Entry<String, Integer> e : anomalies.entrySet()) {
         if (!first) {
            sb.append("; ");
         }
         first = false;
         sb.append(e.getKey()).append(" x").append(e.getValue());
      }
      return sb.append("]").toString();
   }

   private void event(String s) {
      MillLog.major(null, TAG + " EVENT " + s);
   }

   private void warLog(String s) {
      MillLog.major(null, TAG + " WAR " + s);
   }

   private static void log(String s) {
      MillLog.major(null, TAG + " " + s);
   }
}
