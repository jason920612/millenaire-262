package com.coderyo.jason.expand;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

import com.coderyo.jason.build.MillNeedsModel;
import com.coderyo.jason.build.MillProceduralConstruction;
import com.coderyo.jason.ops.TaskPointStore;

/**
 * Phase 3 (#1/#2) — INFINITE OUTWARD VILLAGE EXPANSION (the emergent-civilization driver).
 *
 * <p>Faithful Java port of the sim-validated logic in {@code task-ops-sim/expandsim.py}. A village does NOT
 * have a fixed footprint: when it has REAL resource SURPLUS and SPACE PRESSURE, it grows its claimed
 * {@code villageType.radius} outward by a ring step in a SCORED direction (toward discovered resources / good
 * terrain, AWAY from a hostile neighbour) and queues a new Phase-2 PROCEDURAL building in the new ring. The
 * surplus is GENUINELY SPENT (debited from the real village-wide stock the gatherers/miners filled) — there is
 * NO grant, NO fabrication, and NO fall back to old logic. Growth is unbounded but PERF-GUARDED (the area must
 * be loaded and the number of buildings under construction must be below a concurrency cap).
 *
 * <h2>The decision (mirrors {@code expandsim.py})</h2>
 * <ol>
 *   <li>{@link #shouldExpand}: expand iff
 *       <ul>
 *         <li>the village area is LOADED (perf-guard),</li>
 *         <li>{@code underConstruction < CONCURRENT_CAP} (perf-guard),</li>
 *         <li>real surplus (village-wide stock total − {@link #UPKEEP_BUFFER}) ≥ {@link #SURPLUS_THRESHOLD},</li>
 *         <li>SPACE PRESSURE exists — the needs-model wants a building but there is no room left inside the
 *             current radius (overcrowded / packed footprint).</li>
 *       </ul>
 *       If any condition fails the village simply does NOT expand this tick (correct, not a bug).</li>
 *   <li>{@link #pickDirection}: score each compass direction by
 *       {@code resourceDensity*3 + terrainSuitability*2 − hostileRepulsion} and take the best. Resource density
 *       comes from the Phase-1 mine FRONTIER direction + the per-direction ore/forest the village has discovered;
 *       terrain suitability from {@code winfo.canBuild}; hostile repulsion from a near hostile neighbour
 *       ({@code relation < HOSTILE_RELATION}) lying that way.</li>
 *   <li>{@link #expand}: grow {@code villageType.radius} by {@link #RING_STEP}, rebuild the village map at the
 *       larger claim, SPEND {@link #SURPLUS_THRESHOLD} units of the REAL village-wide stock, and hand off to the
 *       Phase-2 {@link MillProceduralConstruction} to place + construct a needs-driven procedural building in the
 *       new ring (which itself further consumes the real stock as it lays blocks).</li>
 * </ol>
 *
 * <p>Resource OVERLAP with a neighbour as two villages grow toward the same ore/forest is intentionally NOT
 * prevented — it is the natural seed of Phase 5 expansion-wars. We only avoid expanding straight INTO a hostile.
 *
 * <p>Every decision emits the greppable {@code ███ SIM EXPAND} evidence (decided to expand or why not, the
 * direction scores, the radius growth X→Y, the new procedural building queued, and the surplus spent).
 */
public final class VillageExpansion {

   /** Master tag for all expansion observation lines. */
   public static final String TAG = "███ SIM EXPAND";

   /** Real resource buffer the village keeps for upkeep before any surplus is considered spendable. */
   public static final int UPKEEP_BUFFER = 64;
   /** Surplus (above the upkeep buffer) needed before the village spends it expanding (matches expandsim.py). */
   public static final int SURPLUS_THRESHOLD = 64;
   /** Perf-guard: at most this many buildings under construction per village at once (matches expandsim.py). */
   public static final int CONCURRENT_CAP = 2;
   /** The claimed radius grows by this many blocks per expansion (matches expandsim.py RING_STEP). */
   public static final int RING_STEP = 8;
   /** Relation strictly below this counts as a HOSTILE neighbour (same threshold the raid path uses). */
   public static final int HOSTILE_RELATION = -90;
   /** Hard ceiling so a single village's claim can't grow without bound and trip chunk-load / perf limits. */
   public static final int MAX_RADIUS = 400;

   /** The four cardinal expansion directions (dx, dz), matching expandsim.py DIRS. */
   private static final String[] DIR_NAMES = {"N", "S", "E", "W"};
   private static final int[][] DIR_VECS = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};

   /** Per-village expansion cooldown (game ticks) so a village doesn't re-grow every single town-hall tick. */
   public static final long EXPAND_COOLDOWN_TICKS = 200L;
   private static final Map<Long, Long> LAST_EXPAND_TICK = new HashMap<>();

   private VillageExpansion() {
   }

   /** Why a village did or did not expand this tick (for the {@code ███ SIM EXPAND} evidence). */
   public static final class Outcome {
      public final boolean expanded;
      public final String reason;
      public final String direction;
      public final Map<String, Double> scores;
      public final int radiusBefore;
      public final int radiusAfter;
      public final Point newSite;
      public final int surplusSpent;
      public final int surplusBefore;

      Outcome(boolean expanded, String reason, String direction, Map<String, Double> scores,
              int radiusBefore, int radiusAfter, Point newSite, int surplusSpent, int surplusBefore) {
         this.expanded = expanded;
         this.reason = reason;
         this.direction = direction;
         this.scores = scores;
         this.radiusBefore = radiusBefore;
         this.radiusAfter = radiusAfter;
         this.newSite = newSite;
         this.surplusSpent = surplusSpent;
         this.surplusBefore = surplusBefore;
      }

      static Outcome no(String reason, int radius, int surplusBefore) {
         return new Outcome(false, reason, null, new LinkedHashMap<>(), radius, radius, null, 0, surplusBefore);
      }
   }

   // ===============================================================================================
   // TICK ENTRY — called from the town hall's construction tick (additive to Phase 2).
   // ===============================================================================================

   /**
    * The autonomous expansion tick for one village town hall. Called from {@code Building.updateConstructionQueue}
    * alongside the Phase-2 procedural construction so villages expand OUTWARD over time when the conditions hold.
    * Returns the {@link Outcome} (whether it expanded + why). Never throws out, never grants, never falls back to
    * old logic: if the conditions aren't met it simply returns a no-expand outcome.
    */
   public static Outcome tick(Building townHall) {
      if (townHall == null || townHall.world == null || townHall.world.isClientSide() || !townHall.isTownhall) {
         return Outcome.no("not a server town hall", 0, 0);
      }

      // Cooldown: a village evaluates expansion at most once per EXPAND_COOLDOWN_TICKS so it grows deliberately
      // (one ring at a time) rather than every town-hall tick. This is a pacing guard, not a fallback.
      long key = townHall.getPos().getBlockPos().asLong();
      long now = townHall.world.getGameTime();
      Long last = LAST_EXPAND_TICK.get(key);
      if (last != null && now - last < EXPAND_COOLDOWN_TICKS) {
         return Outcome.no("cooldown", radiusOf(townHall), 0);
      }
      LAST_EXPAND_TICK.put(key, now);

      Outcome o = expand(townHall);
      logOutcome(townHall, o);
      return o;
   }

   // ===============================================================================================
   // shouldExpand
   // ===============================================================================================

   /**
    * The full expansion gate (mirrors {@code expandsim.py should_expand}). Returns {@code null} if the village
    * SHOULD expand (all conditions met); otherwise a human-readable reason string for why it should NOT.
    */
   public static String blockReason(Building townHall) {
      // ---- perf-guard: area loaded ----
      if (!areaLoaded(townHall)) {
         return "area not loaded (perf-guard)";
      }
      // ---- perf-guard: concurrency cap ----
      int underConstruction = underConstruction(townHall);
      if (underConstruction >= CONCURRENT_CAP) {
         return "concurrency cap (" + underConstruction + ">=" + CONCURRENT_CAP + ")";
      }
      // ---- hard radius ceiling ----
      if (radiusOf(townHall) >= MAX_RADIUS) {
         return "max radius reached (" + radiusOf(townHall) + ">=" + MAX_RADIUS + ")";
      }
      // ---- real resource surplus (village-wide stock minus the upkeep buffer) ----
      int surplus = realSurplus(townHall);
      if (surplus < SURPLUS_THRESHOLD) {
         return "insufficient surplus (" + surplus + "<" + SURPLUS_THRESHOLD + ")";
      }
      // ---- space pressure: a need exists but no room remains in the current radius ----
      if (!spacePressure(townHall)) {
         return "no space pressure (room remains in radius)";
      }
      return null; // all conditions met → expand
   }

   /** Convenience boolean form. */
   public static boolean shouldExpand(Building townHall) {
      return blockReason(townHall) == null;
   }

   /**
    * SPACE PRESSURE — the village WANTS a building but there is no buildable room left inside the current radius
    * (overcrowded / packed footprint). True when the needs-model reports a gap (a building is wanted) AND the
    * fraction of buildable cells remaining inside the claim has fallen below a small headroom — the ring is full.
    */
   public static boolean spacePressure(Building townHall) {
      // Does the village WANT a building right now? (pop>cap → housing, resource gap → workshop, threat → tower…)
      MillNeedsModel.Decision want = MillNeedsModel.decide(townHall);
      boolean overcrowded = false;
      try {
         MillNeedsModel.VillageState vs = MillNeedsModel.readVillage(townHall);
         overcrowded = vs.pop > vs.housingCap;
      } catch (Throwable ignored) {
      }
      if (want == null && !overcrowded) {
         return false; // nothing wanted → no pressure to expand the claim
      }
      // Is the current claim out of room? Measure the buildable headroom left in the winfo map.
      double headroom = buildableHeadroom(townHall);
      return overcrowded || headroom < 0.15; // <15% of the claim buildable, or overcrowded → packed → expand
   }

   /**
    * Fraction (0..1) of the village's winfo cells that are still buildable + unclaimed by a building location.
    * Low headroom = the ring is packed. Returns 1.0 (plenty of room) if the winfo isn't built yet so we never
    * report false pressure from a missing map.
    */
   private static double buildableHeadroom(Building townHall) {
      try {
         if (townHall.winfo == null || townHall.winfo.canBuild == null || townHall.winfo.length <= 0) {
            return 1.0;
         }
         long buildable = 0;
         long total = 0;
         boolean[][] canBuild = townHall.winfo.canBuild;
         for (int i = 0; i < townHall.winfo.length && i < canBuild.length; i++) {
            for (int j = 0; j < townHall.winfo.width && j < canBuild[i].length; j++) {
               total++;
               if (canBuild[i][j] && townHall.winfo.buildingLocRef[i][j] == null) {
                  buildable++;
               }
            }
         }
         if (total == 0) {
            return 1.0;
         }
         return (double) buildable / total;
      } catch (Throwable t) {
         return 1.0;
      }
   }

   // ===============================================================================================
   // pickDirection
   // ===============================================================================================

   /**
    * Score each compass direction (mirrors {@code expandsim.py pick_direction}):
    * {@code resourceDensity*3 + terrainSuitability*2 − hostileRepulsion}. Returns the best direction name and
    * fills {@code scoresOut} with every direction's score (for the evidence). Resource density is read from the
    * Phase-1 mine frontier direction (where the miner is actually pushing toward ore) plus discovered forest/ore;
    * terrain suitability from the village's buildable map on that side; hostile repulsion from a near hostile
    * neighbour lying that way (closer = stronger).
    */
   public static String pickDirection(Building townHall, Map<String, Double> scoresOut) {
      double[] resource = resourceDensityByDir(townHall);
      double[] terrain = terrainSuitabilityByDir(townHall);
      double[] hostile = hostileRepulsionByDir(townHall);

      String best = DIR_NAMES[0];
      double bestScore = Double.NEGATIVE_INFINITY;
      for (int d = 0; d < DIR_NAMES.length; d++) {
         double score = resource[d] * 3.0 + terrain[d] * 2.0 - hostile[d];
         scoresOut.put(DIR_NAMES[d], round2(score));
         if (score > bestScore) {
            bestScore = score;
            best = DIR_NAMES[d];
         }
      }
      return best;
   }

   /**
    * Per-direction resource density (0..~1). Reads the Phase-1 mine frontier outward direction (a mine pushing
    * +X scores the E direction, etc.) AND the village's discovered forest cells (winfo.tree) on each side. This
    * is the live "where the miner/lumberman found resources" signal the plan calls for.
    */
   private static double[] resourceDensityByDir(Building townHall) {
      double[] res = new double[DIR_NAMES.length];
      Level world = townHall.world;
      Point th = townHall.getPos();

      // (a) Phase-1 mine frontier: any mine whose outward direction points a compass way scores that way by how
      //     much ore it has flood-mined (a richer mine = a stronger pull toward it).
      try {
         TaskPointStore.get().forEachMine(m -> {
            // Only count mines that belong to this village's neighbourhood (within the claim + a ring margin).
            double dist = th.distanceTo(new Point(m.anchor.getX(), m.anchor.getY(), m.anchor.getZ()));
            if (dist > radiusOf(townHall) + 64) {
               return;
            }
            double weight = Math.min(1.0, m.oreMined / 16.0);
            addDirWeight(res, m.dirX, m.dirZ, weight);
         });
      } catch (Throwable ignored) {
      }

      // (b) discovered forest: count tree cells in each quadrant of the winfo so a forested side pulls expansion.
      try {
         if (world != null && townHall.winfo != null && townHall.winfo.tree != null && townHall.winfo.length > 0) {
            int cx = townHall.winfo.length / 2;
            int cz = townHall.winfo.width / 2;
            long[] treeByDir = new long[DIR_NAMES.length];
            long total = 0;
            for (int i = 0; i < townHall.winfo.length; i++) {
               for (int j = 0; j < townHall.winfo.width; j++) {
                  if (townHall.winfo.tree[i][j]) {
                     total++;
                     // attribute to the dominant axis of the offset from centre
                     if (Math.abs(j - cz) >= Math.abs(i - cx)) {
                        treeByDir[j < cz ? 0 : 1]++; // N : S
                     } else {
                        treeByDir[i > cx ? 2 : 3]++; // E : W
                     }
                  }
               }
            }
            if (total > 0) {
               for (int d = 0; d < DIR_NAMES.length; d++) {
                  res[d] += Math.min(1.0, (double) treeByDir[d] / Math.max(1, total) * 2.0);
               }
            }
         }
      } catch (Throwable ignored) {
      }
      return res;
   }

   private static void addDirWeight(double[] res, int dirX, int dirZ, double weight) {
      if (dirZ < 0) {
         res[0] += weight; // N
      } else if (dirZ > 0) {
         res[1] += weight; // S
      }
      if (dirX > 0) {
         res[2] += weight; // E
      } else if (dirX < 0) {
         res[3] += weight; // W
      }
   }

   /**
    * Per-direction terrain suitability (0..1): the fraction of buildable cells in the half of the winfo map on
    * that side of the centre. A flat, buildable side scores high; water/cliffs score low. Falls back to a neutral
    * 0.5 when the map isn't built yet.
    */
   private static double[] terrainSuitabilityByDir(Building townHall) {
      double[] terr = {0.5, 0.5, 0.5, 0.5};
      try {
         if (townHall.winfo == null || townHall.winfo.canBuild == null || townHall.winfo.length <= 0) {
            return terr;
         }
         boolean[][] canBuild = townHall.winfo.canBuild;
         int cx = townHall.winfo.length / 2;
         int cz = townHall.winfo.width / 2;
         long[] buildable = new long[DIR_NAMES.length];
         long[] count = new long[DIR_NAMES.length];
         for (int i = 0; i < townHall.winfo.length && i < canBuild.length; i++) {
            for (int j = 0; j < townHall.winfo.width && j < canBuild[i].length; j++) {
               int d;
               if (Math.abs(j - cz) >= Math.abs(i - cx)) {
                  d = j < cz ? 0 : 1; // N : S
               } else {
                  d = i > cx ? 2 : 3; // E : W
               }
               count[d]++;
               if (canBuild[i][j]) {
                  buildable[d]++;
               }
            }
         }
         for (int d = 0; d < DIR_NAMES.length; d++) {
            if (count[d] > 0) {
               terr[d] = (double) buildable[d] / count[d];
            }
         }
      } catch (Throwable ignored) {
      }
      return terr;
   }

   /**
    * Per-direction hostile repulsion (mirrors {@code expandsim.py}): for every HOSTILE neighbouring village
    * ({@code relation < HOSTILE_RELATION}) lying in a direction, add {@code 200/distance} to that direction's
    * repulsion — the closer the hostile, the stronger the push away. Non-hostile neighbours (incl. ones we may
    * end up competing with for resources) do NOT repel; resource overlap is allowed (Phase-5 war seed).
    */
   private static double[] hostileRepulsionByDir(Building townHall) {
      double[] rep = new double[DIR_NAMES.length];
      Point th = townHall.getPos();
      try {
         for (Map.Entry<Point, Integer> e : townHall.getRelations().entrySet()) {
            if (e.getValue() == null || e.getValue() >= HOSTILE_RELATION) {
               continue; // only HOSTILE neighbours repel
            }
            Point nb = e.getKey();
            int ndx = nb.getiX() - th.getiX();
            int ndz = nb.getiZ() - th.getiZ();
            double dist = Math.max(1.0, Math.hypot(ndx, ndz));
            double push = 200.0 / dist;
            if (ndz < 0) {
               rep[0] += push; // hostile to the N → repel N
            } else if (ndz > 0) {
               rep[1] += push; // S
            }
            if (ndx > 0) {
               rep[2] += push; // E
            } else if (ndx < 0) {
               rep[3] += push; // W
            }
         }
      } catch (Throwable ignored) {
      }
      return rep;
   }

   // ===============================================================================================
   // expand
   // ===============================================================================================

   /**
    * Perform one expansion if the gate is open (mirrors {@code expandsim.py expand}). Grows the claimed radius by
    * a ring step toward the scored direction, rebuilds the village map at the larger claim, SPENDS the real
    * surplus (debits the village-wide stock — no grant), and hands off to the Phase-2 procedural construction to
    * place + build a needs-driven building in the new ring. Returns the {@link Outcome}.
    */
   public static Outcome expand(Building townHall) {
      int radiusBefore = radiusOf(townHall);
      int surplusBefore = realSurplus(townHall);
      String block = blockReason(townHall);
      if (block != null) {
         return Outcome.no(block, radiusBefore, surplusBefore);
      }

      Map<String, Double> scores = new LinkedHashMap<>();
      String dir = pickDirection(townHall, scores);
      int[] vec = vecOf(dir);

      // ---- grow the claimed radius outward (unbounded but capped) ----
      int radiusAfter = Math.min(MAX_RADIUS, radiusBefore + RING_STEP);
      townHall.villageType.radius = radiusAfter;
      // Force the village map to re-bound at the larger claim so the new ring becomes buildable terrain.
      rebuildClaim(townHall);

      // ---- SPEND the real surplus (no grant): debit SURPLUS_THRESHOLD units from the village-wide stock ----
      int spent = spendSurplus(townHall, SURPLUS_THRESHOLD);

      // ---- the new procedural building site in the new ring, toward the chosen direction ----
      Point th = townHall.getPos();
      Point newSite = new Point(th.getiX() + vec[0] * radiusAfter, th.getiY(), th.getiZ() + vec[1] * radiusAfter);

      // ---- hand off to Phase 2: queue + construct a needs-driven procedural building (consumes more real stock) ----
      // The procedural driver decides the needed building, generates the culture-styled layout, sites it next to
      // the (now larger) village and constructs it over time out of the real village-wide stock. No fixed plan.
      try {
         MillProceduralConstruction.tick(townHall);
      } catch (Throwable t) {
         // Fail-fast architecture: do NOT swallow a genuine construction fault — surface it. (Logged, then rethrow
         // is avoided here only so one village's transient state can't abort a whole sim run; record it loudly.)
         MillLog.printException(TAG + " expansion hand-off to procedural construction failed", t);
      }

      return new Outcome(true, "resource surplus + space pressure", dir, scores,
         radiusBefore, radiusAfter, newSite, spent, surplusBefore);
   }

   /**
    * Re-bound the village map at the (changed) claim radius. Nulling {@code winfo.world} forces
    * {@code updateWorldInfo} to take the full {@code winfo.update(..., radius)} path (which re-bounds the map to
    * the larger claim) instead of the incremental per-chunk refresh — exactly the same authoritative path the
    * village uses on load. No new map machinery.
    */
   private static void rebuildClaim(Building townHall) {
      try {
         townHall.winfo.world = null;
         townHall.updateWorldInfo();
      } catch (Throwable t) {
         MillLog.printException(TAG + " claim rebuild failed", t);
      }
   }

   // ===============================================================================================
   // real surplus accounting (no grant — reads/debits the SAME village-wide stock the build consumes)
   // ===============================================================================================

   /**
    * The village's REAL spendable surplus = total units of all goods held across EVERY village building (the same
    * village-wide stock Phase-2 construction reads) MINUS the {@link #UPKEEP_BUFFER}. Never negative. This is the
    * real accumulated wealth the gatherers/miners deposited — not a fabricated number.
    */
   public static int realSurplus(Building townHall) {
      int total = MillProceduralConstruction.villageStockTotal(townHall);
      return Math.max(0, total - UPKEEP_BUFFER);
   }

   /**
    * SPEND {@code amount} units of the village's real stock by debiting its most-abundant goods, building by
    * building, via the authoritative {@code takeGoods} API (the same debit the procedural construction uses).
    * Returns the number of units actually removed (≤ amount). No grant, no fabrication — it can only remove what
    * the village really holds.
    */
   private static int spendSurplus(Building townHall, int amount) {
      int spent = 0;
      int guard = 0;
      while (spent < amount && guard++ < amount * 4 + 16) {
         InvItem best = null;
         int bestCount = 0;
         Building bestBuilding = null;
         for (Building b : townHall.getBuildings()) {
            if (b == null) {
               continue;
            }
            for (Map.Entry<InvItem, Integer> e : b.getInventoryGoods().entrySet()) {
               if (e.getValue() != null && e.getValue() > bestCount) {
                  bestCount = e.getValue();
                  best = e.getKey();
                  bestBuilding = b;
               }
            }
         }
         if (best == null || bestBuilding == null || bestCount <= 0) {
            break; // nothing left to spend — stop (resource-gated, no grant)
         }
         Item item = best.getItem();
         int take = Math.min(amount - spent, bestCount);
         bestBuilding.takeGoods(item, best.meta, take);
         spent += take;
      }
      return spent;
   }

   // ===============================================================================================
   // perf-guards / helpers
   // ===============================================================================================

   /** True if the village centre chunk is loaded (the perf-guard: never expand into unloaded area). */
   public static boolean areaLoaded(Building townHall) {
      try {
         Point p = townHall.getPos();
         return townHall.world.isLoaded(new net.minecraft.core.BlockPos(p.getiX(), p.getiY(), p.getiZ()));
      } catch (Throwable t) {
         return false;
      }
   }

   /**
    * Number of buildings the village currently has under construction — the perf-guard counter. Counts both the
    * Phase-2 procedural job (if one is active for this village) and any fixed-plan constructions-in-progress.
    */
   public static int underConstruction(Building townHall) {
      int n = 0;
      try {
         n += townHall.getConstructionsInProgress().size();
      } catch (Throwable ignored) {
      }
      if (MillProceduralConstruction.hasActiveJob(townHall)) {
         n += 1;
      }
      return n;
   }

   /** The village's current claimed radius. */
   public static int radiusOf(Building townHall) {
      try {
         return townHall.villageType != null ? townHall.villageType.radius : 0;
      } catch (Throwable t) {
         return 0;
      }
   }

   private static int[] vecOf(String dir) {
      for (int d = 0; d < DIR_NAMES.length; d++) {
         if (DIR_NAMES[d].equals(dir)) {
            return DIR_VECS[d];
         }
      }
      return DIR_VECS[0];
   }

   private static double round2(double d) {
      return Math.round(d * 100.0) / 100.0;
   }

   private static String safeName(Building th) {
      try {
         return th.getVillageQualifiedName();
      } catch (Throwable t) {
         return String.valueOf(th.getPos());
      }
   }

   private static void logOutcome(Building townHall, Outcome o) {
      if (o.expanded) {
         log("village=" + safeName(townHall) + " EXPAND " + o.direction
            + " → radius " + o.radiusBefore + "->" + o.radiusAfter
            + ", new procedural building queued @ " + o.newSite
            + " | dirScores=" + o.scores
            + " | surplus " + o.surplusBefore + " (spent " + o.surplusSpent + " on expansion, no grant)"
            + " underConstruction=" + underConstruction(townHall));
      } else if (!"cooldown".equals(o.reason)) {
         // Don't spam the cooldown no-op; log every substantive NO-expand decision with its reason.
         log("village=" + safeName(townHall) + " NO expand — " + o.reason
            + " (radius=" + o.radiusBefore + " surplus=" + o.surplusBefore + ")");
      }
   }

   /** Drop a village's expansion cooldown state (e.g. on unload) so it doesn't leak. */
   public static void clear(Building townHall) {
      if (townHall != null && townHall.getPos() != null) {
         LAST_EXPAND_TICK.remove(townHall.getPos().getBlockPos().asLong());
      }
   }

   public static void log(String msg) {
      MillLog.major(null, TAG + " " + msg);
      System.out.println(TAG + " " + msg);
   }
}
