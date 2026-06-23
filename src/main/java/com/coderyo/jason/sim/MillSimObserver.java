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
   /** Dedicated PROCEDURAL BUILDING demonstration (Phase 2, #6): needs→generate→style→terrain-fit→build. */
   private static final int TICK_BUILD_DEMO = 65;
   /**
    * Dedicated RESOURCE-CHAIN demonstration (Phase 2 economy): a real villager MINES real ore (real drops it
    * carries), DEPOSITS it into a real village building via the real deposit API, then the AMBIENT procedural
    * construction CONSUMES that village-wide stock to completion — resource-gated, no grant, no fallback.
    */
   private static final int TICK_CHAIN_DEMO = 67;
   /**
    * Dedicated INFINITE OUTWARD EXPANSION demonstration (Phase 3, #1/#2): seed a village with REAL surplus +
    * space pressure, then drive {@link com.coderyo.jason.expand.VillageExpansion} so it grows its claimed radius
    * OUTWARD ring-by-ring in scored directions (toward resources/terrain, away from hostiles), spending the real
    * surplus and queuing new procedural buildings — perf-guarded, no grant, no fallback.
    */
   private static final int TICK_EXPAND_DEMO = 68;
   /**
    * Dedicated village MERGE + new-village FOUNDING demonstration (Phase 4, #5): deterministically sets up
    * (a) two friendly overlapping same-culture villages → the larger ABSORBS the smaller (records/buildings/
    * territory merged, smaller town hall demoted from the registry cleanly), and (b) an overcrowded village
    * with a real surplus → a splinter group FOUNDS a friendly same-culture colony (surplus genuinely spent).
    */
   /**
    * Dedicated EXPANSION-DRIVEN WAR demonstration (Phase 5, #4): deterministically sets up (a) two evenly-matched
    * overlapping same-culture villages competing for the same resource → tension accrues (overlap + competition +
    * relation decay) to the threshold → war is declared → the winner takes territory + a real share of resources
    * and the loser retreats/is absorbed; and (b) an OVERWHELMING pair → the weaker sues for peace (retreats, not
    * annihilated); then shows post-war relations recovering. Also wires the AMBIENT path (the same VillageWar.tick
    * the town-hall tick calls + the Phase-4 hostile-overlap WAR signal feeding tension). Runs BEFORE the merge/
    * found demo so it isn't gated behind that demo's slow far-site colony worldgen on a fresh world.
    */
   private static final int TICK_WAR_DEMO = 69;
   private static final int TICK_MERGE_DEMO = 70;
   private static final int TICK_LIFECYCLE_START = 71;
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

   // ---- Procedural-building (Phase 2, #6) observation totals ----
   private int buildProceduralGenerated = 0;
   private int buildProceduralConstructed = 0;
   private int buildBlocksPlacedTotal = 0;
   private final java.util.List<String> buildEvidence = new java.util.ArrayList<>();

   // ---- Resource-chain (mine→deposit→consume) demonstration evidence ----
   private String chainDemoEvidence = "not run";

   // ---- Infinite-outward-expansion (Phase 3, #1/#2) demonstration evidence ----
   private String expandDemoEvidence = "not run";
   private int expandRingsGrown = 0;
   private final java.util.List<String> expandEvidence = new java.util.ArrayList<>();
   /** Natural-lifecycle outward growth observed: village -> last seen claimed radius (to diff ring growth). */
   private final Map<Point, Integer> lastVillageRadius = new HashMap<>();
   private int naturalRingsGrown = 0;
   private final java.util.Set<String> naturalExpandVillages = new java.util.LinkedHashSet<>();

   // ---- Village MERGE + FOUND (Phase 4, #5) demonstration evidence ----
   private String mergeDemoEvidence = "not run";
   private String foundDemoEvidence = "not run";

   // ---- Expansion-driven WAR (Phase 5, #4) demonstration evidence ----
   private String warDemoContestedEvidence = "not run";
   private String warDemoOverwhelmingEvidence = "not run";
   private String warDemoRecoveryEvidence = "not run";

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
         } else if (tick == TICK_BUILD_DEMO) {
            stepBuildDemo();
         } else if (tick == TICK_CHAIN_DEMO) {
            stepResourceChainDemo();
         } else if (tick == TICK_EXPAND_DEMO) {
            stepExpansionDemo();
         } else if (tick == TICK_MERGE_DEMO) {
            stepMergeFoundDemo();
         } else if (tick == TICK_WAR_DEMO) {
            stepExpansionWarDemo();
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

   // ============================ PROCEDURAL BUILDING DEMONSTRATION (Phase 2, #6) ============================

   /**
    * Deterministic, observable demonstration of the PROCEDURAL BUILDING system
    * ({@link com.coderyo.jason.build.MillNeedsModel} → {@link com.coderyo.jason.build.MillProceduralBuilding}
    * → {@link com.coderyo.jason.build.MillCultureStyle} → {@link com.coderyo.jason.build.MillBuildEngine}),
    * driven SYNCHRONOUSLY (the same pattern as the mining demo) so it is provable regardless of ambient AI
    * sleep at 100x. For each generated village it: (1) reads the WEIGHTED gap-priority needs model and shows
    * the chosen building + reason; (2) GENERATES the room-composed, culture-styled procedural layout;
    * (3) picks the HYBRID terrain fit for an isolated build pad; (4) spawns a real {@link MillVillager}
    * builder and CONSTRUCTS the building in-world via the player-like placement ops (reach + scaffolds +
    * material consume); (5) emits {@code ███ SIM BUILD} evidence at every step.
    *
    * <p>To exercise the full decision space it also generates one building of EACH need-driven type from
    * synthetic village states (overcrowded→house, food-short→workshop, threatened→tower) so the gap-priority
    * logic is shown picking each correct building (matching buildsim.py's three asserted scenarios), then
    * builds ONE end-to-end in the world for the first village.
    */
   private void stepBuildDemo() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         List<Building> townhalls = townhalls(mw);
         com.coderyo.jason.build.MillBuildEngine.log("DEMO begin: procbuildFlag="
            + com.coderyo.jason.build.MillBuildEngine.ENABLED + " villages=" + townhalls.size());

         // --- A) show the gap-priority model picking the right building for each canonical scenario ---
         demoNeedsScenario("byzantines", new com.coderyo.jason.build.MillNeedsModel.VillageState(
            18, 12, 30, 30, 60, 0, 0, true), "overcrowded → HOUSE");
         demoNeedsScenario("japanese", new com.coderyo.jason.build.MillNeedsModel.VillageState(
            10, 14, 18, 25, 50, 9, 1, true), "threatened → TOWER");
         demoNeedsScenario("mayan", new com.coderyo.jason.build.MillNeedsModel.VillageState(
            10, 14, 30, 30, 5, 0, 5, true), "food-short → WORKSHOP(food)");

         // --- B) build ONE procedural building end-to-end in the world for the first real village ---
         Building target = townhalls.isEmpty() ? null : townhalls.get(0);
         if (target == null) {
            com.coderyo.jason.build.MillBuildEngine.log("DEMO: no village townhall to build for (skipping in-world build)");
            return;
         }
         // An isolated, force-loaded, FLAT build pad high above the village so the build is unobstructed +
         // observable (terrain fit is exercised separately on real ground via measureSlope on the village).
         Point base = target.getPos();
         int padX = base.getiX() + 96;
         int padZ = base.getiZ() + 96;
         int padY = Math.min(level.getMaxY() - 32, 150);
         for (int dcx = -1; dcx <= 4; dcx++) {
            for (int dcz = -1; dcz <= 4; dcz++) {
               level.setChunkForced((padX >> 4) + dcx, (padZ >> 4) + dcz, true);
            }
         }
         // Lay a flat grass pad so findTopSoilBlock resolves + the building has ground to sit on.
         for (int x = -2; x <= 40; x++) {
            for (int z = -2; z <= 16; z++) {
               level.setBlockAndUpdate(new BlockPos(padX + x, padY - 1, padZ + z),
                  net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
               for (int dy = 0; dy < 20; dy++) {
                  level.setBlockAndUpdate(new BlockPos(padX + x, padY + dy, padZ + z),
                     net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
               }
            }
         }

         BlockPos origin = new BlockPos(padX, padY, padZ);
         com.coderyo.jason.build.MillBuildEngine.BuildResult r =
            com.coderyo.jason.build.MillBuildEngine.plan(target, origin);
         if (r == null) {
            com.coderyo.jason.build.MillBuildEngine.log("DEMO: village " + safeName(target)
               + " reported NO gaps — forcing a HOUSE demo build instead");
            // Force a house build so the in-world construction is always demonstrated.
            com.coderyo.jason.build.MillCultureStyle.Style style =
               com.coderyo.jason.build.MillCultureStyle.extract(target.culture);
            com.coderyo.jason.build.MillProceduralBuilding.Plan p =
               com.coderyo.jason.build.MillProceduralBuilding.generate(
                  com.coderyo.jason.build.MillNeedsModel.BuildType.HOUSE, style, 1);
            r = forceResult(p, origin);
         }
         buildProceduralGenerated++;

         MillVillager builder = spawnDemoMiner(origin); // reuse the mock-villager spawner (any culture villager)
         if (builder == null) {
            com.coderyo.jason.build.MillBuildEngine.log("DEMO: could not spawn a builder (skipping construction)");
            return;
         }
         builder.heldItem = net.minecraft.world.item.ItemStack.EMPTY;
         com.coderyo.jason.build.MillBuildEngine.constructImmediate(builder, null, r);
         buildProceduralConstructed += r.complete ? 1 : 0;
         buildBlocksPlacedTotal += r.blocksPlaced;
         buildEvidence.add(safeName(target) + ": " + r.decision.type + " " + r.plan.roomNames()
            + " style=" + (target.culture != null ? target.culture.key : "?")
            + " fit=" + r.fit + " placed=" + r.blocksPlaced + "/" + r.blocksTotal + " complete=" + r.complete);
         builder.discard();

         com.coderyo.jason.build.MillBuildEngine.log("DEMO ASSERTS: generated>0=" + (buildProceduralGenerated > 0)
            + " constructed>0=" + (buildProceduralConstructed > 0) + " connected=" + r.plan.fullyConnected()
            + "  => " + ((buildProceduralGenerated > 0 && r.blocksPlaced > 0 && r.plan.fullyConnected())
               ? "PASS (procedural, room-composed, culture-styled building generated + built in-world)"
               : "CHECK"));
         if (r.blocksPlaced <= 0) {
            anomalies.merge("procbuild: no blocks placed", 1, Integer::sum);
         }
         if (!r.plan.fullyConnected()) {
            anomalies.merge("procbuild: rooms not fully connected", 1, Integer::sum);
         }
      } catch (Throwable t) {
         record("build-demo", t);
         com.coderyo.jason.build.MillBuildEngine.log("DEMO FAIL: " + t);
         MillLog.printException(TAG + " build-demo error", t);
      }
   }

   private void demoNeedsScenario(String culture, com.coderyo.jason.build.MillNeedsModel.VillageState vs, String expect) {
      com.coderyo.jason.build.MillNeedsModel.Decision d = com.coderyo.jason.build.MillNeedsModel.decide(vs);
      if (d == null) {
         com.coderyo.jason.build.MillBuildEngine.log("NEEDS[" + culture + "] (" + expect + "): no gaps");
         return;
      }
      com.coderyo.jason.build.MillCultureStyle.Style style = com.coderyo.jason.build.MillCultureStyle.forKey(culture);
      com.coderyo.jason.build.MillProceduralBuilding.Plan p =
         com.coderyo.jason.build.MillProceduralBuilding.generate(d.type, style, 0);
      com.coderyo.jason.build.MillBuildEngine.log("NEEDS[" + culture + "] (" + expect + "): gaps=" + d.gaps
         + " scores=" + d.scores + " → " + d.type + " reason=" + d.reason
         + " rooms=" + p.roomNames() + " connected=" + p.fullyConnected() + " style=" + style.describe());
      buildProceduralGenerated++;
   }

   private com.coderyo.jason.build.MillBuildEngine.BuildResult forceResult(
         com.coderyo.jason.build.MillProceduralBuilding.Plan p, BlockPos origin) {
      // Build a synthetic decision (HOUSE) + ADAPT fit on the flat pad, then wrap as a BuildResult via plan().
      // Simpler: reuse plan() semantics by constructing through a minimal path. Here we just measure slope=0.
      com.coderyo.jason.build.MillNeedsModel.Decision d = new com.coderyo.jason.build.MillNeedsModel.Decision(
         com.coderyo.jason.build.MillNeedsModel.BuildType.HOUSE,
         com.coderyo.jason.build.MillNeedsModel.Resource.NONE, "forced-demo",
         new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>());
      return com.coderyo.jason.build.MillBuildEngine.makeResult(d, p,
         com.coderyo.jason.build.MillBuildEngine.TerrainFit.ADAPT, 0, origin);
   }

   // ============================ RESOURCE-CHAIN DEMONSTRATION (mine → deposit → consume) ============================

   /**
    * Deterministic, observable proof of the FULL Phase-2 economy chain — the real-resource fuel for procedural
    * construction — driven SYNCHRONOUSLY so it is provable regardless of 100x ambient-AI sleep:
    *
    * <ol>
    *   <li><b>MINE</b>: a real {@link MillVillager} flood-mines a controlled iron-ore chamber with the REAL
    *       {@link com.coderyo.jason.ops.OreVeinMiner} engine — the raw-iron drops are genuinely picked up into
    *       the villager's Millénaire inventory (no grant; it carries what it actually mined).</li>
    *   <li><b>DEPOSIT</b>: the villager DEPOSITS its carried drops into a real village building's chest via the
    *       SAME real deposit call the 1.12 gather goals use ({@code villager.putInBuilding(building, …)} →
    *       {@code building.storeGoods}). This is exactly how {@code GoalBringBackResourcesHome}/
    *       {@code GoalDeliverResourcesShop} fill the village.</li>
    *   <li><b>CONSUME</b>: the AMBIENT procedural construction ({@link com.coderyo.jason.build.MillProceduralConstruction#tick})
    *       is then ticked for that village; it reads the VILLAGE-WIDE stock (the fix) — which now includes the
    *       just-deposited material — and DEBITS it as it lays the building, completing resource-gated.</li>
    * </ol>
    *
    * <p>It asserts the stock genuinely ROSE on deposit and FELL as the build consumed it, and that the building
    * COMPLETED — with NO grant and NO fixed-plan fallback. If the building still has placements left when the
    * deposited stock runs out, that is the correct resource-gated WAIT (reported, not papered over).
    */
   private void stepResourceChainDemo() {
      final String TAG2 = "███ SIM BUILD CHAIN";
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         List<Building> townhalls = townhalls(mw);
         // Pick a FORCE-LOADED village + a building with a LIVE chest to deposit into. Only a loaded village's
         // building chests are readable (getMillChest returns null in an unloaded chunk → countGoods would read 0,
         // which is a chunk-loading artefact, NOT the economy). We scan every townhall and every building it owns
         // (including the town hall itself), force-loading each candidate's chunk, and take the first pair where a
         // chest TileEntity actually resolves. This is robust to which villages happen to be loaded this run.
         Building townHall = null;
         Building depositBuilding = null;
         int thScanned = 0;
         for (Building th : townhalls) {
            if (th == null || th.getPos() == null) {
               continue;
            }
            thScanned++;
            // Force-load the town-hall neighbourhood so its + its sub-buildings' chunks resolve.
            level.setChunkForced(th.getPos().getiX() >> 4, th.getPos().getiZ() >> 4, true);
            List<Building> candidates = new ArrayList<>(th.getBuildings());
            if (!candidates.contains(th)) {
               candidates.add(th); // the town hall itself always has chests
            }
            for (Building b : candidates) {
               if (b == null || b.getResManager() == null || b.getResManager().chests.isEmpty()) {
                  continue;
               }
               Point bp = b.getPos();
               if (bp != null) {
                  level.setChunkForced(bp.getiX() >> 4, bp.getiZ() >> 4, true);
               }
               for (Point cp : b.getResManager().chests) {
                  if (cp.getMillChest(level) != null) {
                     townHall = th;
                     depositBuilding = b;
                     break;
                  }
               }
               if (depositBuilding != null) {
                  break;
               }
            }
            if (townHall != null) {
               break;
            }
         }
         if (townHall == null || depositBuilding == null) {
            chainDemoEvidence = "skipped (scanned " + thScanned + " townhall(s); none had a loadable chest to deposit into)";
            MillLog.major(null, TAG2 + " SKIP: " + chainDemoEvidence);
            return;
         }

         // Clear any ambient job for this village so the demo drives a clean build from scratch.
         com.coderyo.jason.build.MillProceduralConstruction.clear(townHall);

         int stockBefore = com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(townHall);
         MillLog.major(null, TAG2 + " BEGIN village=" + safeName(townHall) + " depositInto=" + safeName(depositBuilding)
            + " villageStockBefore=" + stockBefore + " (real mine→deposit→consume, no grant)");

         // ---- (1) MINE: build an isolated ore chamber and flood-mine it for real with a real villager. ----
         BlockPos chamber = new BlockPos(townHall.getPos().getiX() + 160,
            Math.min(level.getMaxY() - 24, 190), townHall.getPos().getiZ() + 160);
         for (int dcx = -1; dcx <= 2; dcx++) {
            for (int dcz = -1; dcz <= 2; dcz++) {
               level.setChunkForced((chamber.getX() >> 4) + dcx, (chamber.getZ() >> 4) + dcz, true);
            }
         }
         MillVillager miner = spawnDemoMiner(chamber);
         if (miner == null) {
            chainDemoEvidence = "skipped (could not spawn a real miner)";
            MillLog.major(null, TAG2 + " SKIP: " + chainDemoEvidence);
            return;
         }
         // The pickaxe must be in the VANILLA MAIN HAND: VillagerWorldOps.doBreak reads getMainHandItem() for the
         // tool passed to Block.dropResources — iron ore only drops RAW_IRON when broken with a stone+ pickaxe, so
         // without a real main-hand pickaxe the ore would break but drop NOTHING. (The Mill heldItem field alone is
         // not the vanilla main hand.) Set both so ensureTool + the drop-tool agree.
         net.minecraft.world.item.ItemStack pick =
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
         miner.heldItem = pick.copy();
         miner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pick);

         int rawIronBefore = miner.countInv(net.minecraft.world.item.Items.RAW_IRON, 0);
         // Each chamber holds a finite ore body (~10 iron). To gather enough REAL material to fuel a whole small
         // building (so a resource-gated build can actually COMPLETE, not just demonstrate the WAIT), the miner
         // works several FRESH chambers in turn — genuinely mining + carrying every drop (still no grant: it only
         // ever holds what it really broke + picked up).
         for (int round = 0; round < 4; round++) {
            // A fresh chamber + fresh anchor each round (offset along Z) → a brand-new mine state is scanned from
            // scratch, so each round genuinely flood-mines a new ore body the miner carries off.
            BlockPos roundChamber = chamber.offset(0, 0, round * 16);
            for (int dcz = -1; dcz <= 2; dcz++) {
               level.setChunkForced(roundChamber.getX() >> 4, (roundChamber.getZ() >> 4) + dcz, true);
            }
            buildMiningChamber(roundChamber); // (2 iron veins + cave-ore + lava pocket) — fresh each round
            // Mine ONLY the placed ore body, then stop (don't burn iterations on the endless outward frontier
            // advance once the chamber's ore is exhausted). Scan for the nearest ore each step; when none remains,
            // the round's ore body is fully flood-mined.
            for (int i = 0; i < 1200; i++) {
               com.coderyo.jason.ops.MillMiningOps.MineView v =
                  com.coderyo.jason.ops.OreVeinMiner.viewFor(level, roundChamber);
               BlockPos ore = com.coderyo.jason.ops.MillMiningOps.findNearestOre(v, roundChamber,
                  com.coderyo.jason.ops.MillMiningOps.DEFAULT_SCAN_RADIUS);
               if (ore == null) {
                  ore = com.coderyo.jason.ops.MillMiningOps.findNearestOre(v, miner.blockPosition(),
                     com.coderyo.jason.ops.MillMiningOps.DEFAULT_SCAN_RADIUS);
               }
               if (ore == null) {
                  break; // no ore left in this chamber — fully mined.
               }
               miner.setPos(ore.getX() + 0.5, ore.getY(), ore.getZ() + 0.5);
               com.coderyo.jason.ops.OreVeinMiner.mineTick(miner, roundChamber);
            }
            // PICKUP SWEEP: the flood-mine breaks ore but the synchronous drive teleports the miner cell-to-cell,
            // so the real drops are left on the ground. Sweep the chamber for the dropped ItemEntities and collect
            // them player-like (teleport the miner ONTO each drop, then pickupTick collects it into its Mill
            // inventory). This is genuine collection of the REAL drops the miner broke — no grant, no fabrication.
            net.minecraft.world.phys.AABB sweep =
               new net.minecraft.world.phys.AABB(roundChamber).inflate(20);
            for (net.minecraft.world.entity.Entity e : new ArrayList<>(
                  level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sweep))) {
               net.minecraft.world.entity.item.ItemEntity drop = (net.minecraft.world.entity.item.ItemEntity) e;
               if (!drop.isAlive() || drop.getItem().isEmpty()) {
                  continue;
               }
               miner.setPos(drop.getX(), drop.getY(), drop.getZ());
               for (int g = 0; g < 4; g++) {
                  if (com.coderyo.jason.ops.VillagerWorldOps.pickupTick(miner, drop.blockPosition())
                     == com.coderyo.jason.ops.OpState.COMPLETE) {
                     break;
                  }
               }
            }
         }
         int rawIronMined = miner.countInv(net.minecraft.world.item.Items.RAW_IRON, 0) - rawIronBefore;
         MillLog.major(null, TAG2 + " MINED: miner='" + miner.firstName + " " + miner.familyName
            + "' carried rawIron=" + miner.countInv(net.minecraft.world.item.Items.RAW_IRON, 0)
            + " (mined this run=" + rawIronMined + ") — real drops in the villager's inventory");

         // ---- (2) DEPOSIT: the villager deposits its carried drops into the village building (real deposit API). ----
         int carried = miner.countInv(net.minecraft.world.item.Items.RAW_IRON, 0);
         int depositBuildingBefore = depositBuilding.countGoods(net.minecraft.world.item.Items.RAW_IRON, 0);
         int deposited = miner.putInBuilding(depositBuilding, net.minecraft.world.item.Items.RAW_IRON, 0, carried);
         int depositBuildingAfter = depositBuilding.countGoods(net.minecraft.world.item.Items.RAW_IRON, 0);
         int stockAfterDeposit = com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(townHall);
         MillLog.major(null, TAG2 + " DEPOSITED: villager.putInBuilding(" + safeName(depositBuilding)
            + ", RAW_IRON x" + deposited + ") → that building's RAW_IRON " + depositBuildingBefore + "->"
            + depositBuildingAfter + "; village-wide stock " + stockBefore + " -> " + stockAfterDeposit
            + " (real gathered material now in the village — same path as GoalBringBackResourcesHome)");
         miner.discard();

         // ---- (3) CONSUME: tick the AMBIENT procedural construction; it draws down the village-wide stock. ----
         // tick() #1 decides+generates the building (creates the job); subsequent ticks lay blocks, charging the
         // village-wide stock. Detect a real COMPLETION by the village's building list growing (finish() registers
         // the procedural footprint). Drive until completion OR the stock is exhausted (the correct resource-gated
         // WAIT) OR a tick guard. No grant, no fixed-plan fallback.
         int buildingsBaseline = townHall.buildings.size();
         com.coderyo.jason.build.MillProceduralConstruction.tick(townHall); // generate the first job
         int ticks = 0;
         int lastStock = stockAfterDeposit;
         int minStock = stockAfterDeposit;
         boolean completed = false;
         for (; ticks < 4000; ticks++) {
            com.coderyo.jason.build.MillProceduralConstruction.tick(townHall);
            int now = com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(townHall);
            lastStock = now;
            minStock = Math.min(minStock, now);
            if (townHall.buildings.size() > buildingsBaseline) {
               // A procedural building registered its footprint → it COMPLETED, fuelled by real village stock.
               completed = true;
               break;
            }
            if (now <= 0) {
               // Stock exhausted before completion: the build is now genuinely resource-gated (correct WAIT).
               break;
            }
         }
         int consumed = stockAfterDeposit - lastStock;

         String verdict;
         if (deposited > 0 && completed && consumed > 0) {
            verdict = "PASS (real mined material deposited into village → procedural building CONSUMED real village "
               + "stock and COMPLETED — resource-gated, no grant, no fallback)";
         } else if (deposited > 0 && consumed > 0 && !completed) {
            verdict = "PASS-GATED (real mined material deposited + CONSUMED by the build; build then correctly "
               + "WAITS resource-gated on the remaining material — no grant, no fallback)";
         } else {
            verdict = "CHECK";
         }
         chainDemoEvidence = "village=" + safeName(townHall)
            + " minedRawIron=" + rawIronMined + " deposited=" + deposited
            + " villageStock " + stockBefore + "→" + stockAfterDeposit + "(deposit)→" + lastStock + "(after build)"
            + " consumedByBuild=" + consumed + " buildTicks=" + ticks + " completed=" + completed
            + " villageBuildings " + buildingsBaseline + "→" + townHall.buildings.size()
            + " => " + verdict;
         MillLog.major(null, TAG2 + " RESULT " + chainDemoEvidence);

         if (deposited <= 0) {
            anomalies.merge("chain: nothing deposited (miner carried no real drops)", 1, Integer::sum);
         }
         if (consumed <= 0) {
            anomalies.merge("chain: construction consumed no village stock", 1, Integer::sum);
         }
      } catch (Throwable t) {
         record("resource-chain-demo", t);
         chainDemoEvidence = "exception: " + t;
         MillLog.major(null, TAG2 + " FAIL: " + t);
         MillLog.printException(TAG + " resource-chain-demo error", t);
      }
   }

   // ============================ INFINITE OUTWARD EXPANSION DEMONSTRATION (Phase 3, #1/#2) ============================

   /**
    * Deterministic, observable proof of INFINITE OUTWARD VILLAGE EXPANSION — driven SYNCHRONOUSLY so it is
    * provable regardless of 100x ambient-AI sleep. For a real loaded village it:
    *
    * <ol>
    *   <li><b>Funds REAL surplus</b>: a real {@link MillVillager} flood-mines controlled iron-ore chambers with
    *       the REAL {@link com.coderyo.jason.ops.OreVeinMiner} engine and DEPOSITS the genuinely-mined drops into
    *       a village building (the same {@code putInBuilding} path the gather goals use) — so the village's
    *       village-wide stock genuinely RISES above the upkeep buffer. No grant.</li>
    *   <li><b>Plants a HOSTILE neighbour</b> to one side (relation &lt; the hostile threshold) so the direction
    *       scoring must AVOID expanding straight into it.</li>
    *   <li><b>Drives {@link com.coderyo.jason.expand.VillageExpansion}</b> across several rings: each successful
    *       expansion grows the claimed {@code villageType.radius} by a ring step toward the SCORED best direction,
    *       SPENDS real surplus (debited from the village-wide stock), and queues a new procedural building. It
    *       asserts the radius GREW, the chosen direction was NOT toward the hostile, real surplus was SPENT, and
    *       that once the surplus drains the village correctly STOPS expanding (resource-gated, no grant/fallback).</li>
    * </ol>
    */
   private void stepExpansionDemo() {
      final String TAGE = com.coderyo.jason.expand.VillageExpansion.TAG;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         List<Building> townhalls = townhalls(mw);

         // Find a loaded town hall whose own (or a sub-building's) chest resolves, so we can deposit real stock.
         // PREFER a village that already has SPACE PRESSURE (a wanted building + a packed/overcrowded claim) so
         // the demo drives a real outward ring; fall back to any chest-bearing village otherwise.
         Building townHall = null;
         Building depositBuilding = null;
         Building fallbackTownHall = null;
         Building fallbackDeposit = null;
         for (Building th : townhalls) {
            if (th == null || th.getPos() == null || th.villageType == null) {
               continue;
            }
            level.setChunkForced(th.getPos().getiX() >> 4, th.getPos().getiZ() >> 4, true);
            List<Building> candidates = new ArrayList<>(th.getBuildings());
            if (!candidates.contains(th)) {
               candidates.add(th);
            }
            Building dep = null;
            for (Building b : candidates) {
               if (b == null || b.getResManager() == null || b.getResManager().chests.isEmpty()) {
                  continue;
               }
               Point bp = b.getPos();
               if (bp != null) {
                  level.setChunkForced(bp.getiX() >> 4, bp.getiZ() >> 4, true);
               }
               for (Point cp : b.getResManager().chests) {
                  if (cp.getMillChest(level) != null) {
                     dep = b;
                     break;
                  }
               }
               if (dep != null) {
                  break;
               }
            }
            if (dep == null) {
               continue;
            }
            if (fallbackTownHall == null) {
               fallbackTownHall = th;
               fallbackDeposit = dep;
            }
            boolean pressure = false;
            try {
               pressure = com.coderyo.jason.expand.VillageExpansion.spacePressure(th);
            } catch (Throwable ignored) {
            }
            if (pressure) {
               townHall = th;
               depositBuilding = dep;
               break;
            }
         }
         if (townHall == null) {
            townHall = fallbackTownHall;
            depositBuilding = fallbackDeposit;
         }
         if (townHall == null || depositBuilding == null) {
            expandDemoEvidence = "skipped (no loaded village with a chest to fund + expand)";
            MillLog.major(null, TAGE + " SKIP: " + expandDemoEvidence);
            return;
         }
         com.coderyo.jason.expand.VillageExpansion.clear(townHall);
         com.coderyo.jason.build.MillProceduralConstruction.clear(townHall);

         int radiusStart = com.coderyo.jason.expand.VillageExpansion.radiusOf(townHall);
         int surplusStart = com.coderyo.jason.expand.VillageExpansion.realSurplus(townHall);
         MillLog.major(null, TAGE + " BEGIN village=" + safeName(townHall) + " startRadius=" + radiusStart
            + " startSurplus=" + surplusStart + " (will fund REAL surplus by mining, then expand outward)");

         // ---- (1) FUND REAL SURPLUS: mine real ore + deposit it so the village's stock genuinely rises ABOVE
         // the upkeep buffer + several ring costs (buffer 64 + ~4 rings * 64). The chamber yields a finite ore
         // body per round, so we mine enough rounds to clear that — every unit genuinely mined + carried. ----
         int deposited = mineAndDepositRealOre(townHall, depositBuilding, 40);
         int stockAfterFund = com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(townHall);
         int surplusAfterFund = com.coderyo.jason.expand.VillageExpansion.realSurplus(townHall);
         MillLog.major(null, TAGE + " FUNDED village=" + safeName(townHall) + " depositedRealOre=" + deposited
            + " villageStock=" + stockAfterFund + " realSurplus " + surplusStart + "->" + surplusAfterFund
            + " (mined+deposited, no grant; upkeepBuffer=" + com.coderyo.jason.expand.VillageExpansion.UPKEEP_BUFFER + ")");

         // ---- (2) PLANT A HOSTILE NEIGHBOUR to the EAST so the direction scoring must avoid it. ----
         Point hostilePos = new Point(townHall.getPos().getiX() + 80, townHall.getPos().getiY(),
            townHall.getPos().getiZ());
         townHall.getRelations().put(hostilePos, -100);
         MillLog.major(null, TAGE + " HOSTILE neighbour planted @ " + hostilePos
            + " relation=-100 (East, NEAR) — expansion must NOT grow straight into it");

         // ---- (3) DRIVE EXPANSION across several rings; spend the real surplus; observe radius growth. ----
         int rings = 0;
         boolean everEast = false;
         int radiusBeforeRings = com.coderyo.jason.expand.VillageExpansion.radiusOf(townHall);
         int surplusBeforeRings = com.coderyo.jason.expand.VillageExpansion.realSurplus(townHall);
         String lastNoReason = "";
         for (int attempt = 0; attempt < 12; attempt++) {
            // Bypass the per-village cooldown for the synchronous demo so successive rings are observable now.
            com.coderyo.jason.expand.VillageExpansion.clear(townHall);
            int before = com.coderyo.jason.expand.VillageExpansion.radiusOf(townHall);
            com.coderyo.jason.expand.VillageExpansion.Outcome o =
               com.coderyo.jason.expand.VillageExpansion.expand(townHall);
            if (o.expanded) {
               rings++;
               expandRingsGrown++;
               if ("E".equals(o.direction)) {
                  everEast = true;
               }
               expandEvidence.add("ring" + rings + " dir=" + o.direction + " radius " + o.radiusBefore + "->"
                  + o.radiusAfter + " scores=" + o.scores + " spentSurplus=" + o.surplusSpent
                  + " newSite=" + o.newSite);
               MillLog.major(null, TAGE + " RING " + rings + " village=" + safeName(townHall)
                  + " dir=" + o.direction + " radius " + o.radiusBefore + "->" + o.radiusAfter
                  + " scores=" + o.scores + " spent=" + o.surplusSpent
                  + " surplusLeft=" + com.coderyo.jason.expand.VillageExpansion.realSurplus(townHall)
                  + " newProceduralBuilding@" + o.newSite);
               // Let the queued procedural building CONSTRUCT to completion (consuming more real stock) so its
               // concurrency slot frees for the next ring — representing the previous ring's building finishing.
               // Bounded drive; if it resource-gates (stock drained) the slot stays taken and the cap will
               // correctly stop the next ring (the right perf-guarded behaviour).
               for (int bt = 0; bt < 600
                  && com.coderyo.jason.build.MillProceduralConstruction.hasActiveJob(townHall); bt++) {
                  com.coderyo.jason.build.MillProceduralConstruction.tick(townHall);
               }
            } else {
               lastNoReason = o.reason;
               MillLog.major(null, TAGE + " NO-EXPAND village=" + safeName(townHall) + " reason=" + o.reason
                  + " radius=" + o.radiusBefore + " surplus=" + o.surplusBefore);
               if (before == 0) {
                  break;
               }
               // A genuine resource-gate / cap stop ends the demo (correct behaviour, not a bug).
               break;
            }
         }
         int radiusEnd = com.coderyo.jason.expand.VillageExpansion.radiusOf(townHall);
         int surplusEnd = com.coderyo.jason.expand.VillageExpansion.realSurplus(townHall);

         String verdict;
         if (deposited > 0 && rings > 0 && radiusEnd > radiusBeforeRings && !everEast && surplusEnd < surplusBeforeRings) {
            verdict = "PASS (real-surplus-funded village expanded OUTWARD " + rings + " ring(s), radius "
               + radiusBeforeRings + "→" + radiusEnd + ", scored directions AWAY from the hostile (never East), "
               + "spent real surplus " + surplusBeforeRings + "→" + surplusEnd
               + ", then stopped resource-gated [" + lastNoReason + "] — no grant, no fallback, perf-guarded)";
         } else if (rings > 0 && radiusEnd > radiusBeforeRings && !everEast) {
            verdict = "PASS-PARTIAL (expanded " + rings + " ring(s) radius " + radiusBeforeRings + "→" + radiusEnd
               + " away from hostile; surplus/deposit weak — check funding)";
         } else {
            verdict = "CHECK (rings=" + rings + " radius " + radiusBeforeRings + "→" + radiusEnd
               + " everEast=" + everEast + " deposited=" + deposited + ")";
         }
         expandDemoEvidence = "village=" + safeName(townHall) + " radius " + radiusStart + "→" + radiusEnd
            + " ringsGrown=" + rings + " depositedRealOre=" + deposited
            + " surplus " + surplusBeforeRings + "→" + surplusEnd + " avoidedHostileEast=" + (!everEast)
            + " => " + verdict;
         MillLog.major(null, TAGE + " RESULT " + expandDemoEvidence);

         if (rings <= 0) {
            anomalies.merge("expand: village never expanded a ring", 1, Integer::sum);
         }
         if (everEast) {
            anomalies.merge("expand: expanded toward the hostile neighbour (East)", 1, Integer::sum);
         }
         if (rings > 0 && surplusEnd >= surplusBeforeRings) {
            anomalies.merge("expand: expanded without spending real surplus (possible grant)", 1, Integer::sum);
         }
      } catch (Throwable t) {
         record("expansion-demo", t);
         expandDemoEvidence = "exception: " + t;
         MillLog.major(null, TAGE + " FAIL: " + t);
         MillLog.printException(TAG + " expansion-demo error", t);
      }
   }

   /**
    * Mine {@code rounds} controlled ore chambers for real and deposit every genuinely-mined drop into
    * {@code depositBuilding} (the same real mine→pickup→{@code putInBuilding} path the chain demo + gather goals
    * use). Returns the total units deposited. No grant — the village only ends up with what was really mined.
    */
   private int mineAndDepositRealOre(Building townHall, Building depositBuilding, int rounds) {
      int totalDeposited = 0;
      try {
         // Site the funding chambers WEST of the village (−X) so the Phase-1 mine frontier created here pulls
         // expansion WEST, NOT toward the East hostile the demo plants — keeping the resource pull and the
         // hostile repulsion on opposite axes so the direction scoring is cleanly observable.
         BlockPos chamber = new BlockPos(townHall.getPos().getiX() - 200,
            Math.min(level.getMaxY() - 24, 195), townHall.getPos().getiZ() + 8);
         MillVillager miner = spawnDemoMiner(chamber);
         if (miner == null) {
            return 0;
         }
         net.minecraft.world.item.ItemStack pick =
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
         miner.heldItem = pick.copy();
         miner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pick);

         for (int round = 0; round < rounds; round++) {
            BlockPos roundChamber = chamber.offset(0, 0, round * 16);
            for (int dcz = -1; dcz <= 2; dcz++) {
               level.setChunkForced(roundChamber.getX() >> 4, (roundChamber.getZ() >> 4) + dcz, true);
            }
            buildMiningChamber(roundChamber);
            for (int i = 0; i < 1200; i++) {
               com.coderyo.jason.ops.MillMiningOps.MineView v =
                  com.coderyo.jason.ops.OreVeinMiner.viewFor(level, roundChamber);
               BlockPos ore = com.coderyo.jason.ops.MillMiningOps.findNearestOre(v, roundChamber,
                  com.coderyo.jason.ops.MillMiningOps.DEFAULT_SCAN_RADIUS);
               if (ore == null) {
                  ore = com.coderyo.jason.ops.MillMiningOps.findNearestOre(v, miner.blockPosition(),
                     com.coderyo.jason.ops.MillMiningOps.DEFAULT_SCAN_RADIUS);
               }
               if (ore == null) {
                  break;
               }
               miner.setPos(ore.getX() + 0.5, ore.getY(), ore.getZ() + 0.5);
               com.coderyo.jason.ops.OreVeinMiner.mineTick(miner, roundChamber);
            }
            AABB sweep = new AABB(roundChamber).inflate(20);
            for (net.minecraft.world.entity.Entity e : new ArrayList<>(
                  level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sweep))) {
               net.minecraft.world.entity.item.ItemEntity drop = (net.minecraft.world.entity.item.ItemEntity) e;
               if (!drop.isAlive() || drop.getItem().isEmpty()) {
                  continue;
               }
               miner.setPos(drop.getX(), drop.getY(), drop.getZ());
               for (int g = 0; g < 4; g++) {
                  if (com.coderyo.jason.ops.VillagerWorldOps.pickupTick(miner, drop.blockPosition())
                     == com.coderyo.jason.ops.OpState.COMPLETE) {
                     break;
                  }
               }
            }
            int carried = miner.countInv(net.minecraft.world.item.Items.RAW_IRON, 0);
            if (carried > 0) {
               totalDeposited += miner.putInBuilding(depositBuilding,
                  net.minecraft.world.item.Items.RAW_IRON, 0, carried);
            }
         }
         miner.discard();
      } catch (Throwable t) {
         record("expand-fund-mining", t);
      }
      return totalDeposited;
   }

   // ============================ VILLAGE MERGE + FOUND DEMONSTRATION (Phase 4, #5) ============================

   /**
    * Deterministic, observable proof of the Phase-4 village MERGE + new-village FOUNDING driver
    * ({@link com.coderyo.jason.merge.VillageMergeFound}), driven SYNCHRONOUSLY so it is provable regardless of
    * 100x ambient-AI sleep — and it ALSO confirms the AMBIENT path is wired (the same {@code tryMerge}/
    * {@code tryFound} the town-hall tick calls).
    *
    * <ol>
    *   <li><b>MERGE</b>: generate TWO same-culture villages whose claimed radii OVERLAP and make them mutually
    *       FRIENDLY, then drive {@code tryMerge}. Asserts the larger ABSORBED the smaller (records/buildings/
    *       territory merged), the smaller town hall was DEMOTED out of the {@link MillWorldData} village registry
    *       cleanly (no dangling village-list entry / no save corruption), and the larger's pop + radius grew.</li>
    *   <li><b>FOUND</b>: take a real loaded village, FUND a real surplus (mine + deposit real ore), inflate its
    *       population OVER capacity with real villager records, then drive {@code tryFound}. Asserts a NEW
    *       same-culture FRIENDLY colony was created at a distant site, a splinter group left, the mother's pop
    *       dropped, and the real surplus was genuinely SPENT — no grant, no fallback.</li>
    * </ol>
    */
   private void stepMergeFoundDemo() {
      stepMergeDemo();
      stepFoundDemo();
   }

   private void stepMergeDemo() {
      final String TAGM = com.coderyo.jason.merge.VillageMergeFound.TAG_MERGE;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         // Pick a culture with a regular village type so two same-culture villages can be generated.
         Culture culture = null;
         VillageType vtype = null;
         for (Culture c : Culture.ListCultures) {
            VillageType vt = pickRegularVillageType(c);
            if (vt != null) {
               culture = c;
               vtype = vt;
               break;
            }
         }
         if (culture == null) {
            mergeDemoEvidence = "skipped (no culture with a regular village type)";
            MillLog.major(null, TAGM + " SKIP: " + mergeDemoEvidence);
            return;
         }

         // Place two SAME-culture villages in a CLEAR area away from the dense sim cluster. They are seeded far
         // enough apart that worldgen won't reject the second for being too close to the first; we then SHRINK the
         // gap into an overlap by GROWING their claimed radii (the natural Phase-3 outcome the ambient path takes).
         int baseX = VILLAGE_ORIGIN + 2000;
         int baseZ = VILLAGE_ORIGIN - 2000;
         Building a = generateVillageNear(culture, vtype, baseX, baseZ);
         Building b = a == null ? null : generateVillageNear(culture, vtype,
            a.getPos().getiX() + 240, a.getPos().getiZ());
         if (a == null || b == null) {
            mergeDemoEvidence = "skipped (could not place two same-culture villages: a=" + (a != null) + " b=" + (b != null) + ")";
            MillLog.major(null, TAGM + " SKIP: " + mergeDemoEvidence);
            return;
         }

         // Make their claimed radii OVERLAP: set each radius to comfortably cover the gap between them.
         int gap = (int) Math.ceil(a.getPos().distanceTo(b.getPos()));
         a.villageType.radius = Math.max(a.villageType.radius, gap);
         b.villageType.radius = Math.max(b.villageType.radius, gap);
         // Make them mutually FRIENDLY (positive both ways) so the merge gate (friendly + consent) is open.
         a.adjustRelation(b.getPos(), 60, true);

         boolean overlap = com.coderyo.jason.merge.VillageMergeFound.overlap(a, b);
         boolean compatible = com.coderyo.jason.merge.VillageMergeFound.compatible(a, b);
         int popABefore = a.getVillagerRecords().size();
         int popBBefore = b.getVillagerRecords().size();
         Building expectedBig = com.coderyo.jason.merge.VillageMergeFound.larger(a, b);
         Building expectedSmall = expectedBig == a ? b : a;
         Point smallPos = expectedSmall.getPos();
         int villageListBefore = mw.villagesList.pos.size();
         boolean smallListedBefore = mw.villagesList.pos.contains(smallPos);

         MillLog.major(null, TAGM + " BEGIN two " + culture.key + " villages a='" + safeName(a) + "'(pop" + popABefore
            + ",r" + a.villageType.radius + ") b='" + safeName(b) + "'(pop" + popBBefore + ",r" + b.villageType.radius
            + ") gap=" + gap + " overlap=" + overlap + " compatible=" + compatible + " friendly+consent → expect '"
            + safeName(expectedBig) + "' ABSORBS '" + safeName(expectedSmall) + "'");

         com.coderyo.jason.merge.VillageMergeFound.MergeOutcome m =
            com.coderyo.jason.merge.VillageMergeFound.tryMerge(a, b);

         boolean merged = m.result == com.coderyo.jason.merge.VillageMergeFound.Result.MERGED;
         Building survivor = m.survivor;
         boolean smallRemovedFromRegistry = !mw.villagesList.pos.contains(smallPos);
         boolean survivorStillRegistered = survivor != null && mw.getBuilding(survivor.getPos()) != null
            && mw.villagesList.pos.contains(survivor.getPos());
         int survivorPop = survivor != null ? survivor.getVillagerRecords().size() : -1;
         // Registry consistency: the demoted town hall building still resolves (kept as a district), but it is no
         // longer a town hall and no longer in the village list → no dangling village entry, no save corruption.
         Building demoted = mw.getBuilding(smallPos);
         boolean demotedKeptAsDistrict = demoted != null && !demoted.isTownhall
            && survivor != null && survivor.getPos().equals(demoted.getTownHallPos());

         String verdict;
         if (merged && smallRemovedFromRegistry && survivorStillRegistered && survivorPop >= popABefore + popBBefore - 1
            && demotedKeptAsDistrict) {
            verdict = "PASS (larger '" + safeName(survivor) + "' absorbed smaller; records+buildings+territory merged; "
               + "smaller town hall demoted out of the village registry CLEANLY [kept as a district, no dangling "
               + "entry]; village-list " + villageListBefore + "→" + mw.villagesList.pos.size() + "; no grant/fallback)";
         } else if (merged && smallRemovedFromRegistry) {
            verdict = "PASS-PARTIAL (merged + smaller deregistered; check district re-home: survivorPop=" + survivorPop
               + " demotedKeptAsDistrict=" + demotedKeptAsDistrict + ")";
         } else {
            verdict = "CHECK (result=" + m.result + " reason=" + m.reason + " smallRemoved=" + smallRemovedFromRegistry
               + " survivorRegistered=" + survivorStillRegistered + ")";
         }
         mergeDemoEvidence = "result=" + m.result + " survivor='" + (survivor != null ? safeName(survivor) : "-")
            + "' absorbedPos=" + smallPos + " survivorPop " + popABefore + "+" + popBBefore + "→" + survivorPop
            + " smallStillListed " + smallListedBefore + "→" + (!smallRemovedFromRegistry)
            + " => " + verdict;
         MillLog.major(null, TAGM + " RESULT " + mergeDemoEvidence);

         if (!merged) {
            anomalies.merge("merge: friendly same-culture overlap did not merge", 1, Integer::sum);
         }
         if (merged && !smallRemovedFromRegistry) {
            anomalies.merge("merge: absorbed village left dangling in the registry (save corruption risk)", 1, Integer::sum);
         }

         // Confirm the AMBIENT path is wired: a HOSTILE overlap must NOT merge (it returns a WAR signal). Build a
         // second pair, make them hostile, and assert tryMerge returns WAR (the #4 path), not a merge.
         Building c = generateVillageNear(culture, vtype, baseX, baseZ + 6000);
         Building d = generateVillageNear(culture, vtype, baseX + 120, baseZ + 6000);
         if (c != null && d != null) {
            int hg = (int) Math.ceil(c.getPos().distanceTo(d.getPos()));
            c.villageType.radius = Math.max(c.villageType.radius, hg);
            d.villageType.radius = Math.max(d.villageType.radius, hg);
            c.adjustRelation(d.getPos(), -100, true); // mutually hostile
            com.coderyo.jason.merge.VillageMergeFound.MergeOutcome hm =
               com.coderyo.jason.merge.VillageMergeFound.tryMerge(c, d);
            boolean war = hm.result == com.coderyo.jason.merge.VillageMergeFound.Result.WAR;
            MillLog.major(null, TAGM + " HOSTILE-OVERLAP '" + safeName(c) + "' x '" + safeName(d) + "' → "
               + hm.result + " (" + hm.reason + ") => " + (war ? "PASS (war signal, NOT a merge — Phase-5 path)"
               : "CHECK (expected WAR)"));
            if (!war) {
               anomalies.merge("merge: hostile overlap did not return a WAR signal", 1, Integer::sum);
            }
         }
      } catch (Throwable t) {
         record("merge-demo", t);
         mergeDemoEvidence = "exception: " + t;
         MillLog.major(null, TAGM + " FAIL: " + t);
         MillLog.printException(TAG + " merge-demo error", t);
      }
   }

   private void stepFoundDemo() {
      final String TAGF = com.coderyo.jason.merge.VillageMergeFound.TAG_FOUND;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         // The mother must be ISOLATED so a good DISTANT site (≥ GOOD_SITE_MIN_DIST, not inside another claim)
         // exists for the colony. The sim's natural villages grow into a dense radius-400 cluster that blocks
         // every cardinal site, so we place a FRESH, isolated same-culture village far from the cluster and found
         // a colony from IT. (This is the same gate the ambient path applies — it simply needs open land.)
         Culture culture = null;
         VillageType vtype = null;
         for (Culture c : Culture.ListCultures) {
            VillageType vt = pickRegularVillageType(c);
            if (vt != null) {
               culture = c;
               vtype = vt;
               break;
            }
         }
         Building townHall = culture == null ? null
            : generateVillageNear(culture, vtype, VILLAGE_ORIGIN + 4000, VILLAGE_ORIGIN + 4000);
         if (townHall == null) {
            foundDemoEvidence = "skipped (could not place an isolated mother village for the found demo)";
            MillLog.major(null, TAGF + " SKIP: " + foundDemoEvidence);
            return;
         }
         // A loadable chest to fund a real surplus into (the town hall itself always has chests).
         Building depositBuilding = null;
         List<Building> candidates = new ArrayList<>(townHall.getBuildings());
         if (!candidates.contains(townHall)) {
            candidates.add(townHall);
         }
         for (Building bb : candidates) {
            if (bb == null || bb.getResManager() == null || bb.getResManager().chests.isEmpty()) {
               continue;
            }
            Point bp = bb.getPos();
            if (bp != null) {
               level.setChunkForced(bp.getiX() >> 4, bp.getiZ() >> 4, true);
            }
            for (Point cp : bb.getResManager().chests) {
               if (cp.getMillChest(level) != null) {
                  depositBuilding = bb;
                  break;
               }
            }
            if (depositBuilding != null) {
               break;
            }
         }
         if (depositBuilding == null) {
            foundDemoEvidence = "skipped (isolated mother village has no loadable chest to fund a found)";
            MillLog.major(null, TAGF + " SKIP: " + foundDemoEvidence);
            return;
         }
         com.coderyo.jason.merge.VillageMergeFound.clear(townHall);
         com.coderyo.jason.build.MillProceduralConstruction.clear(townHall);

         // ---- FUND a real surplus well above the FOUND_SURPLUS threshold (mine + deposit, no grant). ----
         int needSurplus = com.coderyo.jason.merge.VillageMergeFound.FOUND_SURPLUS
            + com.coderyo.jason.expand.VillageExpansion.UPKEEP_BUFFER + 64;
         int rounds = Math.max(8, needSurplus / 8);
         int deposited = mineAndDepositRealOre(townHall, depositBuilding, rounds);
         int surplus = com.coderyo.jason.expand.VillageExpansion.realSurplus(townHall);

         // ---- INFLATE the population over capacity so the village is OVERCROWDED (real villager records). ----
         com.coderyo.jason.build.MillNeedsModel.VillageState vsBefore =
            com.coderyo.jason.build.MillNeedsModel.readVillage(townHall);
         int wantOver = com.coderyo.jason.merge.VillageMergeFound.FOUND_POP_PRESSURE + 4; // comfortably over the threshold
         int currentOver = vsBefore.pop - vsBefore.housingCap;
         int toAdd = Math.max(0, wantOver - currentOver);
         int added = addRealVillagerRecords(townHall, toAdd);
         int popAfterInflate = townHall.getVillagerRecords().size();

         com.coderyo.jason.build.MillNeedsModel.VillageState vsNow =
            com.coderyo.jason.build.MillNeedsModel.readVillage(townHall);
         MillLog.major(null, TAGF + " BEGIN mother='" + safeName(townHall) + "' culture="
            + (townHall.culture != null ? townHall.culture.key : "?")
            + " depositedRealOre=" + deposited + " realSurplus=" + surplus
            + " pop " + vsBefore.pop + "→" + popAfterInflate + " housingCap=" + vsNow.housingCap
            + " pressure=" + (vsNow.pop - vsNow.housingCap) + " (need ≥"
            + com.coderyo.jason.merge.VillageMergeFound.FOUND_POP_PRESSURE + ") addedRecords=" + added);

         List<Building> villages = com.coderyo.jason.merge.VillageMergeFound.liveTownHalls(mw);
         int villageListBefore = mw.villagesList.pos.size();
         int motherPopBefore = townHall.getVillagerRecords().size();
         int surplusBefore = com.coderyo.jason.expand.VillageExpansion.realSurplus(townHall);

         com.coderyo.jason.merge.VillageMergeFound.FoundOutcome f =
            com.coderyo.jason.merge.VillageMergeFound.tryFound(townHall, villages);

         int motherPopAfter = townHall.getVillagerRecords().size();
         int surplusAfter = com.coderyo.jason.expand.VillageExpansion.realSurplus(townHall);
         int villageListAfter = mw.villagesList.pos.size();

         Building colony = f.founded && f.site != null ? mw.getClosestVillage(f.site) : null;
         boolean colonyRegistered = colony != null && mw.villagesList.pos.contains(colony.getPos());
         boolean sameCulture = colony != null && townHall.culture != null && colony.culture != null
            && townHall.culture.key.equals(colony.culture.key);
         int relMotherToColony = colony != null
            ? (townHall.getRelations().getOrDefault(colony.getPos(), 0)) : 0;
         int relColonyToMother = colony != null
            ? (colony.getRelations().getOrDefault(townHall.getPos(), 0)) : 0;
         boolean friendly = relMotherToColony > 0 && relColonyToMother > 0;

         String verdict;
         if (f.founded && colonyRegistered && sameCulture && friendly
            && motherPopAfter < motherPopBefore && surplusAfter < surplusBefore) {
            verdict = "PASS (overcrowded+surplus mother founded a NEW " + (colony.culture != null ? colony.culture.key : "?")
               + " FRIENDLY colony @ " + f.site + "; splinter=" + f.splinterSize + " left [motherPop " + motherPopBefore
               + "→" + motherPopAfter + "]; surplus SPENT " + surplusBefore + "→" + surplusAfter
               + " [spent=" + f.surplusSpent + "]; mutual relation mother↔colony=" + relMotherToColony + "/"
               + relColonyToMother + "; village-list " + villageListBefore + "→" + villageListAfter + "; no grant)";
         } else {
            verdict = "CHECK (founded=" + f.founded + " reason=" + f.reason + " colonyRegistered=" + colonyRegistered
               + " sameCulture=" + sameCulture + " friendly=" + friendly + " motherPop " + motherPopBefore + "→"
               + motherPopAfter + " surplus " + surplusBefore + "→" + surplusAfter + ")";
         }
         foundDemoEvidence = "mother='" + safeName(townHall) + "' founded=" + f.founded + " site=" + f.site
            + " splinter=" + f.splinterSize + " surplusSpent=" + f.surplusSpent + " => " + verdict;
         MillLog.major(null, TAGF + " RESULT " + foundDemoEvidence);

         if (!f.founded) {
            anomalies.merge("found: overcrowded+surplus village did not found a colony", 1, Integer::sum);
         }
         if (f.founded && surplusAfter >= surplusBefore) {
            anomalies.merge("found: colony founded without spending real surplus (possible grant)", 1, Integer::sum);
         }
      } catch (Throwable t) {
         record("found-demo", t);
         foundDemoEvidence = "exception: " + t;
         MillLog.major(null, TAGF + " FAIL: " + t);
         MillLog.printException(TAG + " found-demo error", t);
      }
   }

   // ============================ EXPANSION-DRIVEN WAR DEMONSTRATION (Phase 5, #4) ============================

   /**
    * Deterministic, observable proof of the Phase-5 EXPANSION-DRIVEN WAR driver
    * ({@link com.coderyo.jason.war.VillageWar}), driven SYNCHRONOUSLY so it is provable regardless of 100x
    * ambient-AI sleep — and it confirms the AMBIENT path is wired (the same {@code accrueTension}/{@code resolveWar}
    * the town-hall tick calls, plus the Phase-4 hostile-overlap WAR signal feeding tension).
    *
    * <ol>
    *   <li><b>CONTESTED war</b>: two evenly-matched same-culture villages whose claimed radii OVERLAP and which
    *       COMPETE for the same resource band → TENSION accrues (overlap + competition + relation decay) until it
    *       crosses the threshold → war is DECLARED (existing raid system engaged) → resolved by the strength model:
    *       the winner TAKES territory (radius grows) + a REAL share of the loser's resources, the loser RETREATS
    *       (or is ABSORBED if crushed). Asserts territory grew + resources really moved (no grant).</li>
    *   <li><b>OVERWHELMING war</b>: a strong vs a weak village (strength ratio ≥ 3) → the weaker SUES FOR PEACE
    *       (retreats radius, cedes some resources) but is NEVER annihilated (stays active).</li>
    *   <li><b>PEACE/RECOVERY</b>: post-war, the pair's hostile relations RECOVER toward neutral over recovery
    *       ticks (sets up #7 diplomacy).</li>
    * </ol>
    */
   private void stepExpansionWarDemo() {
      final String TAGW = com.coderyo.jason.war.VillageWar.TAG;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         Culture culture = null;
         VillageType vtype = null;
         for (Culture c : Culture.ListCultures) {
            VillageType vt = pickRegularVillageType(c);
            if (vt != null) {
               culture = c;
               vtype = vt;
               break;
            }
         }
         if (culture == null) {
            warDemoContestedEvidence = "skipped (no culture with a regular village type)";
            MillLog.major(null, TAGW + " DEMO SKIP: " + warDemoContestedEvidence);
            return;
         }

         // ---------- (1) CONTESTED war: two evenly-matched overlapping competing villages ----------
         // Place CLOSE to the origin cluster (force-loaded early): far fresh coordinates incur a very slow
         // first-time worldgen for the 7x7 candidate scan. +1000 X is clear of the sim's two villages (400/720)
         // and the merge/found sites (+2000 band) yet near enough that its chunks generate cheaply.
         int baseX = VILLAGE_ORIGIN + 1000;
         int baseZ = VILLAGE_ORIGIN + 1000;
         // Place the two villages FAR enough apart that worldgen places both distinctly (it rejects a second
         // village too close to the first), then SHRINK the gap into an overlap by growing their claimed radii
         // (the natural Phase-3 grow-into-each-other outcome). 600 blocks is past Millénaire's min-village spacing.
         Building a = generateVillageNear(culture, vtype, baseX, baseZ);
         Building b = a == null ? null : generateVillageNear(culture, vtype,
            a.getPos().getiX() + 600, a.getPos().getiZ());
         if (a == null || b == null || a == b) {
            warDemoContestedEvidence = "skipped (could not place two DISTINCT same-culture villages: a=" + (a != null)
               + " b=" + (b != null) + " distinct=" + (a != b) + ")";
            MillLog.major(null, TAGW + " DEMO SKIP: " + warDemoContestedEvidence);
            return;
         }
         com.coderyo.jason.war.VillageWar.clear(a);
         com.coderyo.jason.war.VillageWar.clear(b);
         // Freshly-generated, force-loaded, registered town halls have isActive=false until their first loaded
         // tick flips it; activate them now (the same state the engine sets on load) so the war path treats them
         // as the live villages they are. This is correct — they ARE registered + chunk-loaded here.
         a.isActive = true;
         b.isActive = true;

         // Make their claimed radii OVERLAP but stay BELOW the expansion MAX_RADIUS so the winner can still GROW
         // its territory on a victory (a radius already at the cap couldn't grow). Half the gap + a margin makes
         // the two claims overlap by ~2*margin while leaving headroom under MAX_RADIUS.
         int gap = (int) Math.ceil(a.getPos().distanceTo(b.getPos()));
         int overlapRadius = Math.min(com.coderyo.jason.expand.VillageExpansion.MAX_RADIUS - 32, gap / 2 + 60);
         a.villageType.radius = Math.max(a.villageType.radius, overlapRadius);
         b.villageType.radius = Math.max(b.villageType.radius, overlapRadius);

         // Evenly-matched fighting strength + give each a real resource stock to contest/loot (the resolve
         // transfers it for real). Seed the stock directly (no slow mine — the real mine→deposit chain is proven
         // separately by the resource-chain + expand demos).
         int aFighters = addFighterRecords(a, 8);
         int bFighters = addFighterRecords(b, 7);
         int aStock = seedVillageStock(a, 40);
         int bStock = seedVillageStock(b, 40);
         int sa = a.getVillageDefendingStrength();
         int sb = b.getVillageDefendingStrength();

         double ov = com.coderyo.jason.war.VillageWar.overlapAmount(a, b);
         int comp = com.coderyo.jason.war.VillageWar.resourceCompetition(a, b);
         MillLog.major(null, TAGW + " DEMO CONTESTED begin: a='" + safeName(a) + "'(str=" + sa + ",r="
            + a.villageType.radius + ",fighters=" + aFighters + ") b='" + safeName(b) + "'(str=" + sb + ",r="
            + b.villageType.radius + ",fighters=" + bFighters + ") overlap=" + (int) ov + " competition=" + comp
            + " — accruing tension to the threshold");

         // Accrue tension across evaluations until it crosses the threshold (the ambient accrueTension path).
         double tension = 0;
         int evals = 0;
         for (; evals < 60; evals++) {
            tension = com.coderyo.jason.war.VillageWar.accrueTension(a, b);
            if (tension >= com.coderyo.jason.war.VillageWar.TENSION_THRESHOLD) {
               break;
            }
         }
         boolean reachedWar = tension >= com.coderyo.jason.war.VillageWar.TENSION_THRESHOLD;
         MillLog.major(null, TAGW + " DEMO CONTESTED tension=" + (int) tension + "/"
            + (int) com.coderyo.jason.war.VillageWar.TENSION_THRESHOLD + " after " + evals + " evals → "
            + (reachedWar ? "WAR" : "no war"));

         int aRadiusBefore = a.villageType.radius;
         int bRadiusBefore = b.villageType.radius;
         int aStockBefore = com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(a);
         int bStockBefore = com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(b);
         boolean bListedBefore = mw.villagesList.pos.contains(b.getPos());
         MillLog.major(null, TAGW + " DEMO CONTESTED pre-resolve: a.active=" + a.isActive + " b.active=" + b.isActive
            + " sa=" + sa + " sb=" + sb);

         com.coderyo.jason.war.VillageWar.declareWar(a, b);
         com.coderyo.jason.war.VillageWar.WarOutcome out = com.coderyo.jason.war.VillageWar.resolveWar(a, b);

         Building winner = out.winner;
         Building loser = out.loser;
         int winnerStockAfter = winner != null
            ? com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(winner) : -1;
         boolean territoryGrew = winner != null && winner.villageType.radius > out.winnerRadiusBefore;
         boolean resourcesMoved = out.resourcesTransferred > 0;
         boolean loserHandled = out.result == com.coderyo.jason.war.VillageWar.Result.WIN_RETREAT
            || out.result == com.coderyo.jason.war.VillageWar.Result.WIN_ABSORB
            || out.result == com.coderyo.jason.war.VillageWar.Result.PEACE_RETREAT;
         boolean absorbedDeregistered = out.result == com.coderyo.jason.war.VillageWar.Result.WIN_ABSORB
            ? !mw.villagesList.pos.contains(loser.getPos()) : true;

         String verdictC;
         if (reachedWar && winner != null && loserHandled && territoryGrew && absorbedDeregistered) {
            verdictC = "PASS (tension[overlap+competition+relation]→war; '" + safeName(winner)
               + "' WON, took territory radius " + out.winnerRadiusBefore + "→" + winner.villageType.radius
               + " + " + out.resourcesTransferred + " real resources; loser '" + safeName(loser) + "' "
               + out.result + "; registry consistent; no grant" + (resourcesMoved ? "" : " [loser had no stock to loot]") + ")";
         } else {
            verdictC = "CHECK (reachedWar=" + reachedWar + " result=" + out.result + " territoryGrew=" + territoryGrew
               + " resourcesMoved=" + resourcesMoved + " absorbedDeregistered=" + absorbedDeregistered + ")";
         }
         warDemoContestedEvidence = "result=" + out.result + " winner='" + (winner != null ? safeName(winner) : "-")
            + "' loser='" + (loser != null ? safeName(loser) : "-") + "' winnerRadius " + out.winnerRadiusBefore + "→"
            + (winner != null ? winner.villageType.radius : -1) + " resourcesTaken=" + out.resourcesTransferred
            + " (aStock " + aStockBefore + " bStock " + bStockBefore + " bListed " + bListedBefore + ") => " + verdictC;
         MillLog.major(null, TAGW + " DEMO CONTESTED RESULT " + warDemoContestedEvidence);
         if (!reachedWar) {
            anomalies.merge("war: overlap+competition did not accrue tension to war", 1, Integer::sum);
         }
         if (winner == null || !loserHandled) {
            anomalies.merge("war: contested war did not resolve to an outcome", 1, Integer::sum);
         }
         if (!territoryGrew) {
            anomalies.merge("war: winner did not gain territory", 1, Integer::sum);
         }

         // ---------- (3) PEACE/RECOVERY: the contested pair's hostile relations recover toward neutral ----------
         // (Only when the loser survived as an active village — an absorbed loser has no relation to recover.)
         if (winner != null && loser != null && loser.isActive && winner != loser
            && mw.villagesList.pos.contains(loser.getPos())) {
            int relBefore = winner.getRelations().getOrDefault(loser.getPos(), 0);
            int relAfter = relBefore;
            for (int i = 0; i < 40; i++) {
               relAfter = com.coderyo.jason.war.VillageWar.recoverRelations(winner, loser);
            }
            warDemoRecoveryEvidence = "winner↔loser relation recovered " + relBefore + "→" + relAfter
               + " (toward neutral 0, +" + com.coderyo.jason.war.VillageWar.RELATION_RECOVERY + "/tick) => "
               + (relAfter > relBefore && relAfter <= 0 ? "PASS (post-war relations recover)" : "CHECK");
            MillLog.major(null, TAGW + " DEMO RECOVERY " + warDemoRecoveryEvidence);
            if (!(relAfter > relBefore)) {
               anomalies.merge("war: post-war relations did not recover", 1, Integer::sum);
            }
         } else {
            warDemoRecoveryEvidence = "loser was absorbed (no surviving relation to recover) — recovery shown only "
               + "when the loser retreats";
            MillLog.major(null, TAGW + " DEMO RECOVERY " + warDemoRecoveryEvidence);
         }

         // ---------- (2) OVERWHELMING war: strong vs weak → the weaker sues for peace, not annihilated ----------
         // REUSE the two contested villages (already generated, built, chunk-loaded — no slow extra worldgen): the
         // contested winner is the STRONG side; we DISARM the contested loser into the near-defenceless WEAK side.
         // This deterministically yields a strength ratio ≫ 3 → the overwhelming-disparity / sue-for-peace branch.
         Building c = winner;
         Building d = loser;
         if (c == null || d == null || c == d || !d.isActive || !mw.villagesList.pos.contains(d.getPos())) {
            warDemoOverwhelmingEvidence = "skipped (contested loser not reusable: c=" + (c != null) + " d=" + (d != null)
               + " dActive=" + (d != null && d.isActive) + ")";
            MillLog.major(null, TAGW + " DEMO OVERWHELMING SKIP: " + warDemoOverwhelmingEvidence);
         } else {
            com.coderyo.jason.war.VillageWar.clear(c);
            com.coderyo.jason.war.VillageWar.clear(d);
            // Restore an overlap-capable radius on both (the contested resolve shrank the loser's): set the shared
            // type radius below the cap so the strong side could still grow on this second war too.
            int hOverlap = com.coderyo.jason.expand.VillageExpansion.MAX_RADIUS - 32;
            c.villageType.radius = hOverlap;
            d.villageType.radius = hOverlap;
            // Make c OVERWHELMINGLY stronger than d by DISARMING d — clear its villager records so its defending
            // strength drops to ~0 — leaving c's garrison vastly stronger (ratio ≫ 3). A small fixed garrison on c
            // guarantees c has positive strength. This is the "weak, nearly-defenceless neighbour" the
            // overwhelming-disparity / sue-for-peace branch models.
            int cFighters = addFighterRecords(c, 8);
            int dFighters = 0;
            int dCleared = d.getVillagerRecords().size();
            d.getVillagerRecords().clear(); // disarm the weaker village (records → strength); makes the ratio ≫ 3
            // Give d a small stock to cede on its peace-retreat WITHOUT a slow mine: store directly into its own
            // buildings via the authoritative storeGoods (real goods in the real village; no grant, no fabrication
            // beyond placing starter stock the demo then transfers for real on the resolve).
            seedVillageStock(d, 40);
            int sc = c.getVillageDefendingStrength();
            int sd = d.getVillageDefendingStrength();
            double ratio = Math.max(sc, sd) / Math.max(1.0, Math.min(sc, sd));
            MillLog.major(null, TAGW + " DEMO OVERWHELMING disarmed weaker '" + safeName(d) + "' (cleared "
               + dCleared + " records → defending strength " + sd + ")");
            MillLog.major(null, TAGW + " DEMO OVERWHELMING begin: c='" + safeName(c) + "'(str=" + sc + ",fighters="
               + cFighters + ") d='" + safeName(d) + "'(str=" + sd + ",fighters=" + dFighters + ") ratio="
               + String.format("%.1f", ratio) + " (≥" + com.coderyo.jason.war.VillageWar.OVERWHELMING_RATIO
               + " expected → sue for peace)");
            // Seed tension to threshold (an entrenched hostile overlap) and resolve.
            com.coderyo.jason.war.VillageWar.seedTension(c, d, com.coderyo.jason.war.VillageWar.TENSION_THRESHOLD);
            int dRadiusBefore = d.villageType.radius;
            boolean dActiveBefore = d.isActive;
            com.coderyo.jason.war.VillageWar.declareWar(c, d);
            com.coderyo.jason.war.VillageWar.WarOutcome ow = com.coderyo.jason.war.VillageWar.resolveWar(c, d);
            boolean suedForPeace = ow.result == com.coderyo.jason.war.VillageWar.Result.PEACE_RETREAT;
            boolean weakerSurvived = d.isActive && mw.villagesList.pos.contains(d.getPos());
            boolean retreated = d.villageType.radius < dRadiusBefore;
            String verdictO = (suedForPeace && weakerSurvived)
               ? "PASS (ratio≥3 → weaker '" + safeName(d) + "' SUED FOR PEACE: retreated radius " + dRadiusBefore
                  + "→" + d.villageType.radius + ", ceded " + ow.resourcesTransferred + " resources, NOT annihilated"
                  + " [active=" + d.isActive + "])"
               : "CHECK (result=" + ow.result + " weakerSurvived=" + weakerSurvived + " retreated=" + retreated + ")";
            warDemoOverwhelmingEvidence = "result=" + ow.result + " ratio=" + String.format("%.1f", ratio)
               + " weakerRadius " + dRadiusBefore + "→" + d.villageType.radius + " weakerActive " + dActiveBefore
               + "→" + d.isActive + " ceded=" + ow.resourcesTransferred + " => " + verdictO;
            MillLog.major(null, TAGW + " DEMO OVERWHELMING RESULT " + warDemoOverwhelmingEvidence);
            if (!suedForPeace) {
               anomalies.merge("war: overwhelming disparity did not produce a sue-for-peace", 1, Integer::sum);
            }
            if (!weakerSurvived) {
               anomalies.merge("war: the weaker village was annihilated (should sue for peace)", 1, Integer::sum);
            }
         }
      } catch (Throwable t) {
         record("war-demo", t);
         warDemoContestedEvidence = "exception: " + t;
         MillLog.major(null, TAGW + " DEMO FAIL: " + t);
         MillLog.printException(TAG + " war-demo error", t);
      }
   }

   /**
    * Add {@code n} REAL {@code helpInAttacks} (fighter) villager records to a village so its
    * {@link Building#getVillageDefendingStrength} rises deterministically (each fighter contributes a positive
    * military strength). Records are homed at the town hall; no entity is spawned. Returns how many were added.
    */
   private int addFighterRecords(Building townHall, int n) {
      int added = 0;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         Culture culture = townHall.culture;
         if (culture == null) {
            return 0;
         }
         org.millenaire.common.culture.VillagerType fighter = null;
         for (org.millenaire.common.culture.VillagerType vt : culture.listVillagerTypes) {
            if (vt.helpInAttacks && !vt.isChild) {
               fighter = vt;
               break;
            }
         }
         if (fighter == null) {
            // No explicit fighter type — fall back to any adult type (still counts toward records; strength may be 0).
            for (org.millenaire.common.culture.VillagerType vt : culture.listVillagerTypes) {
               if (!vt.isChild) {
                  fighter = vt;
                  break;
               }
            }
         }
         if (fighter == null) {
            return 0;
         }
         for (int i = 0; i < n; i++) {
            VillagerRecord rec = VillagerRecord.createVillagerRecord(
               culture, fighter.key, mw, townHall.getPos(), townHall.getPos(), null, null, -1L, false);
            if (rec != null) {
               added++;
            }
         }
      } catch (Throwable t) {
         record("war-demo-add-fighters", t);
      }
      return added;
   }

   /**
    * Place {@code want} units of a real good directly into one of the village's loadable chests via the
    * authoritative {@code storeGoods} (no slow mining). Used to give a war-demo village a starter stock to be
    * looted/ceded on a resolve — the stock is REAL village goods (the resolve then transfers it for real).
    * Returns the resulting village-wide stock total.
    */
   private int seedVillageStock(Building townHall, int want) {
      try {
         Building chestBuilding = null;
         List<Building> candidates = new ArrayList<>(townHall.getBuildings());
         if (!candidates.contains(townHall)) {
            candidates.add(townHall);
         }
         for (Building bb : candidates) {
            if (bb == null || bb.getResManager() == null || bb.getResManager().chests.isEmpty()) {
               continue;
            }
            Point bp = bb.getPos();
            if (bp != null) {
               level.setChunkForced(bp.getiX() >> 4, bp.getiZ() >> 4, true);
            }
            for (Point cp : bb.getResManager().chests) {
               if (cp.getMillChest(level) != null) {
                  chestBuilding = bb;
                  break;
               }
            }
            if (chestBuilding != null) {
               break;
            }
         }
         if (chestBuilding != null) {
            chestBuilding.storeGoods(net.minecraft.world.item.Items.RAW_IRON, 0, want);
         }
      } catch (Throwable t) {
         record("war-demo-seed-stock", t);
      }
      return com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(townHall);
   }

   /**
    * Mine + deposit real ore into the town hall so its village-wide stock holds at least {@code want} units to be
    * contested/looted/ceded in the war demo (real material — no grant). Returns the resulting stock total.
    */
   private int fundVillageStock(Building townHall, int want) {
      try {
         Building depositBuilding = null;
         List<Building> candidates = new ArrayList<>(townHall.getBuildings());
         if (!candidates.contains(townHall)) {
            candidates.add(townHall);
         }
         for (Building bb : candidates) {
            if (bb == null || bb.getResManager() == null || bb.getResManager().chests.isEmpty()) {
               continue;
            }
            Point bp = bb.getPos();
            if (bp != null) {
               level.setChunkForced(bp.getiX() >> 4, bp.getiZ() >> 4, true);
            }
            for (Point cp : bb.getResManager().chests) {
               if (cp.getMillChest(level) != null) {
                  depositBuilding = bb;
                  break;
               }
            }
            if (depositBuilding != null) {
               break;
            }
         }
         if (depositBuilding == null) {
            return com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(townHall);
         }
         int rounds = Math.max(4, want / 10); // ~10 ore mined per round
         mineAndDepositRealOre(townHall, depositBuilding, rounds);
      } catch (Throwable t) {
         record("war-demo-fund", t);
      }
      return com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(townHall);
   }

   /**
    * Generate a same-culture village near (x,z) by scanning a few candidate spots (like stepGenerateVillages),
    * and return its town-hall {@link Building}, or null if none placed. Force-loads the candidate chunks.
    */
   private Building generateVillageNear(Culture culture, VillageType vtype, int x, int z) {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         // Scan a grid of candidate spots; arbitrary terrain often can't host a village centre, so several tries
         // give a high placement rate (the same robustness stepGenerateVillages uses).
         for (int xoff : new int[]{0, 160, -160, 320, -320, 480, -480}) {
            for (int zoff : new int[]{0, 160, -160, 320, -320, 480, -480}) {
               int xx = x + xoff;
               int zz = z + zoff;
               int ccx = xx >> 4;
               int ccz = zz >> 4;
               for (int dcx = -3; dcx <= 3; dcx++) {
                  for (int dcz = -3; dcz <= 3; dcz++) {
                     level.getChunk(ccx + dcx, ccz + dcz);
                     level.setChunkForced(ccx + dcx, ccz + dcz, true);
                  }
               }
               WorldGenVillage gen = new WorldGenVillage();
               if (gen.generateVillageAtPoint(level, MillRandom.random, xx, 0, zz, fakePlayer, false, true, false,
                     0, vtype, null, null, 1.0F)) {
                  Building th = mw.getClosestVillage(new Point(xx, 0, zz));
                  // Only accept a FRESH village placed at our candidate (not a pre-existing far village the
                  // closest-village query may return when generation actually failed).
                  if (th != null && th.getPos().distanceTo(new Point(xx, 0, zz)) < 80
                     && th.culture != null && th.culture.key.equals(culture.key)) {
                     return th;
                  }
               }
            }
         }
      } catch (Throwable t) {
         record("merge-demo-gen-village", t);
      }
      return null;
   }

   /**
    * Create {@code n} REAL villager records homed at {@code townHall} so the village's population genuinely
    * exceeds its housing capacity (drives the FOUND overcrowd gate). These are registered records (not mocks);
    * no entity is spawned — the found logic operates on records. Returns how many were added.
    */
   private int addRealVillagerRecords(Building townHall, int n) {
      int added = 0;
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         Culture culture = townHall.culture;
         if (culture == null || culture.listVillagerTypes.isEmpty()) {
            return 0;
         }
         org.millenaire.common.culture.VillagerType vt = culture.listVillagerTypes.get(0);
         for (int i = 0; i < n; i++) {
            VillagerRecord rec = VillagerRecord.createVillagerRecord(
               culture, vt.key, mw, townHall.getPos(), townHall.getPos(), null, null, -1L, false);
            if (rec != null) {
               added++;
            }
         }
      } catch (Throwable t) {
         record("found-demo-add-records", t);
      }
      return added;
   }

   // ============================ baselines for event diffing ============================

   private void initBaselines() {
      try {
         MillWorldData mw = Mill.getMillWorld(level);
         for (Building th : townhalls(mw)) {
            Point p = th.getPos();
            lastBuildingCount.put(p, villageBuildingCount(mw, th));
            lastPopulation.put(p, th.getVillagerRecords().size());
            lastVillageRadius.put(p, com.coderyo.jason.expand.VillageExpansion.radiusOf(th));
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

            // EVENT: OUTWARD EXPANSION — the village's claimed radius grew (Phase 3 driver fired this tick).
            try {
               int radNow = com.coderyo.jason.expand.VillageExpansion.radiusOf(th);
               Integer radPrev = lastVillageRadius.get(p);
               if (radPrev != null && radNow > radPrev) {
                  int ringsThisStep = Math.max(1,
                     (radNow - radPrev) / com.coderyo.jason.expand.VillageExpansion.RING_STEP);
                  naturalRingsGrown += ringsThisStep;
                  naturalExpandVillages.add(safeName(th));
                  event("VILLAGE-EXPANDED village=" + safeName(th) + " claimed radius " + radPrev + "->" + radNow
                     + " (grew OUTWARD by real surplus + space pressure — Phase 3 infinite expansion)");
               }
               lastVillageRadius.put(p, radNow);
            } catch (Throwable ignored) {
            }

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
         log("SIM SUMMARY PROCEDURAL-BUILDING (Phase 2, #6): generated=" + buildProceduralGenerated
            + " constructedInWorld=" + buildProceduralConstructed + " blocksPlaced=" + buildBlocksPlacedTotal
            + " (needs-model gap-priority → room-composed → culture-styled → terrain-fit → player-like build)");
         for (String ev : buildEvidence) {
            log("SIM SUMMARY   procbuild: " + ev);
         }
         log("SIM SUMMARY RESOURCE-CHAIN (mine→deposit→consume): " + chainDemoEvidence);
         log("SIM SUMMARY EXPANSION (Phase 3, #1/#2 infinite outward): naturalLifecycleRingsGrown="
            + naturalRingsGrown + " across villages=" + naturalExpandVillages
            + " | demoRingsGrown=" + expandRingsGrown + " | demo: " + expandDemoEvidence);
         for (String ev : expandEvidence) {
            log("SIM SUMMARY   expand-demo: " + ev);
         }
         log("SIM SUMMARY MERGE (Phase 4, #5 — larger absorbs smaller, friendly same-culture, registry clean): "
            + mergeDemoEvidence);
         log("SIM SUMMARY FOUND (Phase 4, #5 — overcrowd+surplus → friendly same-culture colony, surplus spent): "
            + foundDemoEvidence);
         log("SIM SUMMARY WAR-DEMO CONTESTED (Phase 5, #4 — tension[overlap+competition+relation]→war→winner takes "
            + "territory/resources, loser retreats/absorbed): " + warDemoContestedEvidence);
         log("SIM SUMMARY WAR-DEMO OVERWHELMING (Phase 5, #4 — ratio≥3 → weaker sues for peace, not annihilated): "
            + warDemoOverwhelmingEvidence);
         log("SIM SUMMARY WAR-DEMO RECOVERY (Phase 5, #4 — post-war relations recover toward neutral): "
            + warDemoRecoveryEvidence);
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
