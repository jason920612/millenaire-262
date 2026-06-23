package com.coderyo.jason.war;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

import com.coderyo.jason.expand.VillageExpansion;
import com.coderyo.jason.merge.VillageMergeFound;
import com.coderyo.jason.ops.TaskPointStore;

/**
 * Phase 5 (#4) — EXPANSION-DRIVEN WAR (the emergent-civilization conflict driver).
 *
 * <p>Faithful Java port of the sim-validated logic in {@code task-ops-sim/warsim.py}. It builds on Phase 3
 * ({@link VillageExpansion}, villages grow their claimed radii OUTWARD until they grow INTO each other) and
 * Phase 4 ({@link VillageMergeFound}, whose HOSTILE-overlap path returns a {@code WAR} signal this phase
 * consumes, and whose {@code absorb()} mechanic this phase REUSES for "winner absorbs a crushed loser").
 * War here is NOT a scripted raid — it EMERGES from territorial + resource overlap between neighbours.
 *
 * <h2>TENSION — {@link #accrueTension(Building, Building)}</h2>
 * Per ORDERED neighbour pair, tension accrues each evaluation from (matching {@code warsim.py accrue_tension}):
 * <ul>
 *   <li>territorial OVERLAP amount — {@code overlapAmount(a,b) * }{@link #W_OVERLAP} (how far the two claimed
 *       radii overlap; this is the Phase-3 outward-growth-into-each-other signal),</li>
 *   <li>shared-RESOURCE competition — {@code resourceCompetition(a,b) * }{@link #W_COMPETITION} (both villages'
 *       Phase-1 mine reach / claimed resources overlapping the same nearby ground),</li>
 *   <li>RELATION decay — {@code max(0,-relation) * }{@link #W_RELATION} (a poor relation feeds the grievance).</li>
 * </ul>
 * Overlap/competition also DECAYS the relation by {@link #RELATION_DECAY_STEP} (the same authoritative
 * {@link Building#adjustRelation} the diplomacy uses), so chronic crowding sours relations over time. When the
 * accrued tension reaches {@link #TENSION_THRESHOLD} a war is DECLARED.
 *
 * <h2>DECLARE — {@link #declareWar(Building, Building)}</h2>
 * Sets mutually HOSTILE relations (≤ {@link VillageExpansion#HOSTILE_RELATION}) and engages the EXISTING
 * raid/war system between the two villages ({@link Building#planRaid} — {@code GoalRaidVillage}/
 * {@code GoalDefendVillage} then run the in-world combat). Phase 4's hostile-overlap {@code WAR} signal feeds
 * straight into {@link #onHostileOverlap(Building, Building)} which seeds the tension to threshold.
 *
 * <h2>RESOLUTION + OUTCOME — {@link #resolveWar(Building, Building)}</h2>
 * Reuses the abstract STRENGTH model ({@link Building#getVillageDefendingStrength}, fed by in-world attrition —
 * casualties lower the loser's strength) to pick the winner. Then (matching {@code warsim.py resolve_war}):
 * <ul>
 *   <li><b>OVERWHELMING disparity</b> (strength ratio ≥ {@link #OVERWHELMING_RATIO}): the weaker SUES FOR PEACE
 *       — it RETREATS (radius shrinks by {@link #RETREAT_STEP}) and cedes a SHARE of resources, but is NEVER
 *       annihilated. ({@code PEACE_RETREAT})</li>
 *   <li><b>Contested</b>: the winner TAKES territory (radius grows by {@link #WIN_GROW_STEP}) + a SHARE of the
 *       loser's resources (a REAL transfer — debited from the loser, credited into the winner). The loser is
 *       ABSORBED via {@link VillageMergeFound} (REUSING its registry-safe {@code absorb}) if CRUSHED
 *       (tiny pop or {@code loserStrength*2 < winnerStrength}; {@code WIN_ABSORB}), else it RETREATS
 *       ({@code WIN_RETREAT}).</li>
 * </ul>
 * No grant, no fallback: territory + resources move for REAL through the village data, the registry is mutated
 * only through {@link MillWorldData}'s own APIs (the Phase-4 absorb guards double-absorb), and an unmet
 * condition is simply the correct outcome.
 *
 * <h2>PEACE / RECOVERY — {@link #recoverRelations(Building, Building)}</h2>
 * Post-war (no active war between the pair), relations RECOVER slowly toward neutral
 * ({@link #RELATION_RECOVERY} points per recovery tick, capped at 0) — setting up #7 diplomacy.
 *
 * <p>Every decision emits the greppable {@code ███ SIM WAR} evidence: tension building (why — overlap/
 * competition/relation), war declared, resolution (strengths), outcome (territory/resources taken, absorbed/
 * retreated/sued-for-peace), and relations recovering.
 */
public final class VillageWar {

   /** Greppable master tag for all WAR observation lines (matches the sim observer's grep target). */
   public static final String TAG = "███ SIM WAR";

   // ---- thresholds (mirror warsim.py) -----------------------------------------------------------
   /** Accrued tension at which a war is declared between a neighbour pair (warsim.py TENSION_THRESHOLD). */
   public static final double TENSION_THRESHOLD = 100.0;
   /** Relation points recovered per recovery tick post-war, capped at neutral 0 (warsim.py RELATION_RECOVERY). */
   public static final int RELATION_RECOVERY = 2;

   // tension weights (warsim.py accrue_tension): ov*0.5 + competition*1.0 + max(0,-rel)*0.2
   public static final double W_OVERLAP = 0.5;
   public static final double W_COMPETITION = 1.0;
   public static final double W_RELATION = 0.2;
   /** Overlap/competition decays the relation by this many points each accrual (warsim.py -3). */
   public static final int RELATION_DECAY_STEP = 3;

   /** Strength ratio at/above which the disparity is OVERWHELMING → the weaker sues for peace (warsim.py 3.0). */
   public static final double OVERWHELMING_RATIO = 3.0;
   /** Winner's claimed radius growth on a contested victory (warsim.py +16). */
   public static final int WIN_GROW_STEP = 16;
   /** Loser's claimed radius shrink on a retreat / peace-retreat (warsim.py -16), floored at the min radius. */
   public static final int RETREAT_STEP = 16;
   /** Minimum radius a retreating village keeps (warsim.py max(16, ...)) — never shrunk below this. */
   public static final int MIN_RADIUS = 16;
   /** A loser with pop ≤ this is CRUSHED (absorbed rather than allowed to retreat) (warsim.py pop<=4). */
   public static final int CRUSH_POP = 4;

   /** Share of the loser's resources the winner takes on a contested victory (warsim.py 0.5). */
   public static final double WIN_RESOURCE_SHARE = 0.5;
   /** Share of resources the weaker cedes when it sues for peace under overwhelming disparity (warsim.py 0.3). */
   public static final double PEACE_RESOURCE_CEDE = 0.3;

   /** Per-village cooldown (ticks) so tension is accrued deliberately, not every town-hall tick. */
   public static final long WAR_COOLDOWN_TICKS = 400L;

   private VillageWar() {
   }

   // ---- tension state: unordered pair key "lo:hi" -> accrued tension ----------------------------
   private static final Map<String, Double> TENSION = new HashMap<>();
   private static final Map<Long, Long> LAST_TICK = new HashMap<>();
   /** Pairs with a war currently declared/active (so we accrue→declare once, then recover after). */
   private static final java.util.Set<String> AT_WAR = new java.util.HashSet<>();

   /** The tagged outcome of resolving a war (mirrors warsim.py's tagged return). */
   public enum Result {
      WIN_ABSORB, WIN_RETREAT, PEACE_RETREAT, NO_WAR
   }

   /** A war resolution's result + the territory/resource outcome (for the {@code ███ SIM WAR} evidence). */
   public static final class WarOutcome {
      public final Result result;
      public final Building winner;
      public final Building loser;
      public final String reason;
      public final int winnerRadiusBefore;
      public final int winnerRadiusAfter;
      public final int loserRadiusBefore;
      public final int loserRadiusAfter;
      public final int resourcesTransferred;

      WarOutcome(Result result, Building winner, Building loser, String reason,
                 int winnerRadiusBefore, int winnerRadiusAfter,
                 int loserRadiusBefore, int loserRadiusAfter, int resourcesTransferred) {
         this.result = result;
         this.winner = winner;
         this.loser = loser;
         this.reason = reason;
         this.winnerRadiusBefore = winnerRadiusBefore;
         this.winnerRadiusAfter = winnerRadiusAfter;
         this.loserRadiusBefore = loserRadiusBefore;
         this.loserRadiusAfter = loserRadiusAfter;
         this.resourcesTransferred = resourcesTransferred;
      }

      static WarOutcome none(String reason) {
         return new WarOutcome(Result.NO_WAR, null, null, reason, 0, 0, 0, 0, 0);
      }
   }

   // ===============================================================================================
   // TICK ENTRY — autonomous, called from the town hall construction tick (after Phase-4 merge/found).
   // ===============================================================================================

   /**
    * The autonomous war tick for one village town hall. For each ACTIVE neighbour whose claim it overlaps it
    * (1) accrues tension from overlap + resource competition + relation decay, declaring + resolving a war when
    * tension crosses the threshold; and (2) lets a post-war pair's relations RECOVER toward neutral. Returns
    * {@code true} if a war resolved this tick (so the caller treats it like a construction change). Strict:
    * unmet conditions → no-op (no grant, no fallback).
    */
   public static boolean tick(Building townHall) {
      if (townHall == null || townHall.world == null || townHall.world.isClientSide() || !townHall.isTownhall
         || !townHall.isActive) {
         return false;
      }
      long key = townHall.getPos().getBlockPos().asLong();
      long now = townHall.world.getGameTime();
      Long last = LAST_TICK.get(key);
      if (last != null && now - last < WAR_COOLDOWN_TICKS) {
         return false;
      }
      LAST_TICK.put(key, now);

      MillWorldData mw = Mill.getMillWorld(townHall.world);
      if (mw == null) {
         return false;
      }
      List<Building> villages = VillageMergeFound.liveTownHalls(mw);
      boolean resolvedAWar = false;

      for (Building other : villages) {
         // If THIS town hall was absorbed by a war it lost earlier in this same loop, stop — it's gone.
         if (!townHall.isActive) {
            break;
         }
         if (other == townHall || other == null || other.getPos() == null || !other.isActive) {
            continue;
         }
         // Only evaluate each unordered pair from the lower-keyed town hall so tension isn't double-stepped.
         if (other.getPos().getBlockPos().asLong() < key) {
            continue;
         }
         String pair = pairKey(townHall, other);

         // ---- POST-WAR RECOVERY: a pair no longer at war recovers its relation toward neutral. ----
         if (!AT_WAR.contains(pair)) {
            // Recover only when there is a negative relation to heal (a past war / soured neighbourhood).
            if (relation(townHall, other) < 0 || relation(other, townHall) < 0) {
               recoverRelations(townHall, other);
            }
         }

         // ---- TENSION: only neighbours whose claims overlap (or already grievance-laden) accrue tension. ----
         double tension = accrueTension(townHall, other);
         if (tension >= TENSION_THRESHOLD && !AT_WAR.contains(pair)
            && townHall.isActive && other.isActive) {
            declareWar(townHall, other);
            WarOutcome o = resolveWar(townHall, other);
            if (o.result != Result.NO_WAR) {
               resolvedAWar = true;
            }
            // War concluded this evaluation (abstract resolution + the in-world raid attrition feed strength):
            // reset tension and move the pair to the recovery phase.
            TENSION.put(pair, 0.0);
            AT_WAR.remove(pair);
         }
      }
      return resolvedAWar;
   }

   // ===============================================================================================
   // TENSION
   // ===============================================================================================

   /** Euclidean horizontal distance between two town halls. */
   private static double distance(Building a, Building b) {
      Point pa = a.getPos();
      Point pb = b.getPos();
      return Math.hypot(pa.getiX() - pb.getiX(), pa.getiZ() - pb.getiZ());
   }

   /** How far the two claimed radii overlap (warsim.py overlap_amount): {@code max(0, rA+rB - dist)}. */
   public static double overlapAmount(Building a, Building b) {
      double d = distance(a, b);
      return Math.max(0.0, (VillageExpansion.radiusOf(a) + VillageExpansion.radiusOf(b)) - d);
   }

   /** Relation a→b (0 if unknown). */
   private static int relation(Building a, Building b) {
      Integer r = a.getRelations().get(b.getPos());
      return r == null ? 0 : r;
   }

   /**
    * Shared-RESOURCE competition between two villages (the warsim.py {@code resourceCompetition} input). Both
    * villages mine/gather the same nearby ground when their Phase-1 mine FRONTIERS fall inside the OTHER
    * village's claimed radius (each is digging toward ore the other also claims). We count mine frontiers of
    * EITHER village that lie within BOTH claims — the more shared veins, the higher the competition. Falls back
    * to a baseline derived from the claim overlap (two villages whose territory overlaps are by definition
    * contesting the resources in the shared band) so competition is never under-counted when mines aren't loaded.
    */
   public static int resourceCompetition(Building a, Building b) {
      int shared = 0;
      try {
         final Point pa = a.getPos();
         final Point pb = b.getPos();
         final int ra = VillageExpansion.radiusOf(a);
         final int rb = VillageExpansion.radiusOf(b);
         final int[] count = {0};
         TaskPointStore.get().forEachMine(m -> {
            BlockPos f = m.frontier != null ? m.frontier : m.anchor;
            double da = Math.hypot(f.getX() - pa.getiX(), f.getZ() - pa.getiZ());
            double db = Math.hypot(f.getX() - pb.getiX(), f.getZ() - pb.getiZ());
            if (da <= ra && db <= rb) {
               count[0]++; // a vein both villages' claims reach = directly contested
            }
         });
         shared = count[0];
      } catch (Throwable ignored) {
      }
      // Baseline competition from the overlap band: a deep claim overlap means the villages are necessarily
      // contesting the shared resources there even before a mine frontier is observed (e.g. unloaded chunks at
      // 100x). Scale modestly so a real shared vein still dominates. (No grant — this only feeds the abstract
      // tension number, it does not move any resource.)
      double ov = overlapAmount(a, b);
      int baseline = ov > 0 ? (int) Math.min(20, Math.ceil(ov / 8.0)) : 0;
      return Math.max(shared * 10, baseline);
   }

   /**
    * Accrue tension for the ordered pair (a,b) (mirrors warsim.py accrue_tension). Tension grows from territorial
    * overlap + shared-resource competition + relation decay; overlap/competition also decays the relation.
    * Returns the pair's new accrued tension. No tension accrues for a non-overlapping, non-grievance pair.
    */
   public static double accrueTension(Building a, Building b) {
      double ov = overlapAmount(a, b);
      int competition = resourceCompetition(a, b);
      int rel = relation(a, b);
      // Nothing to accrue when the villages neither overlap nor compete nor hold a grievance — the common case.
      if (ov <= 0 && competition <= 0 && rel >= 0) {
         return TENSION.getOrDefault(pairKey(a, b), 0.0);
      }

      double inc = ov * W_OVERLAP + competition * W_COMPETITION + Math.max(0, -rel) * W_RELATION;
      String key = pairKey(a, b);
      double newTension = TENSION.getOrDefault(key, 0.0) + inc;
      TENSION.put(key, newTension);

      // Overlap/competition also DECAYS the relation (the authoritative two-sided adjust).
      if (ov > 0 || competition > 0) {
         try {
            a.adjustRelation(b.getPos(), -RELATION_DECAY_STEP, false);
         } catch (Throwable t) {
            MillLog.printException(TAG + " relation decay failed", t);
         }
      }

      MillLog.major(null, TAG + " TENSION '" + safeName(a) + "' x '" + safeName(b) + "' +" + round1(inc)
         + " → " + round1(newTension) + "/" + (int) TENSION_THRESHOLD
         + " (overlap=" + (int) ov + " competition=" + competition + " relation=" + relation(a, b) + ")");
      return newTension;
   }

   // ===============================================================================================
   // DECLARE
   // ===============================================================================================

   /**
    * The Phase-4 hostile-overlap WAR signal feeds into the tension model: a hostile overlap is an immediate
    * grievance, so we seed the pair's tension to the threshold (and ensure the relation is hostile) so the next
    * {@link #tick} declares + resolves the war. This wires {@code VillageMergeFound}'s {@code Result.WAR} into the
    * expansion-driven war path as the plan requires.
    */
   public static void onHostileOverlap(Building a, Building b) {
      if (a == null || b == null || a.getPos() == null || b.getPos() == null) {
         return;
      }
      String key = pairKey(a, b);
      TENSION.put(key, Math.max(TENSION.getOrDefault(key, 0.0), TENSION_THRESHOLD));
      MillLog.major(null, TAG + " HOSTILE-OVERLAP signal '" + safeName(a) + "' x '" + safeName(b)
         + "' → tension seeded to threshold (Phase-4 WAR signal feeds the expansion-war path)");
   }

   /**
    * DECLARE war between two villages: set mutually HOSTILE relations and ENGAGE the existing raid/war system
    * ({@link Building#planRaid} → {@code GoalRaidVillage}/{@code GoalDefendVillage} drive the in-world combat).
    * The aggressor is the STRONGER village (it presses its advantage). No new combat is built — we reuse the
    * village-war machinery; this only sets the diplomatic state + plans the raid.
    */
   public static void declareWar(Building a, Building b) {
      int sa = strengthOf(a);
      int sb = strengthOf(b);
      Building aggressor = sa >= sb ? a : b;
      Building target = aggressor == a ? b : a;
      AT_WAR.add(pairKey(a, b));

      // Mutually hostile (below the raid threshold the existing system uses).
      try {
         aggressor.adjustRelation(target.getPos(), VillageExpansion.HOSTILE_RELATION - 10, true);
      } catch (Throwable t) {
         MillLog.printException(TAG + " set-hostile failed", t);
      }
      // Engage the existing raid system: the aggressor plans a raid on the target.
      try {
         aggressor.planRaid(target);
      } catch (Throwable t) {
         MillLog.printException(TAG + " planRaid failed", t);
      }
      target.underAttack = true;

      MillLog.major(null, TAG + " DECLARE '" + safeName(aggressor) + "' (str=" + sa(aggressor)
         + ") raids '" + safeName(target) + "' (str=" + sa(target) + ") — tension≥" + (int) TENSION_THRESHOLD
         + ", mutual relations now hostile, existing raid system engaged (planRaid)");
   }

   // ===============================================================================================
   // RESOLUTION + OUTCOME
   // ===============================================================================================

   /** A village's abstract defending strength (the existing strength model, fed by in-world attrition). */
   private static int strengthOf(Building b) {
      try {
         return Math.max(0, b.getVillageDefendingStrength());
      } catch (Throwable t) {
         return 0;
      }
   }

   private static int sa(Building b) {
      return strengthOf(b);
   }

   private static int popOf(Building b) {
      try {
         return b.getVillagerRecords().size();
      } catch (Throwable t) {
         return 0;
      }
   }

   /**
    * Resolve a war between two villages (mirrors warsim.py resolve_war). The winner is decided by the abstract
    * STRENGTH model (which the in-world raid attrition has already lowered for the side that took casualties).
    * Applies the OUTCOME: an overwhelming disparity makes the weaker SUE FOR PEACE (retreat, cede some
    * resources, never annihilated); otherwise the winner TAKES territory + a share of resources and the loser is
    * ABSORBED if crushed else RETREATS. All transfers are REAL (no grant); absorb reuses the Phase-4 registry-safe
    * path. Returns the tagged {@link WarOutcome}.
    */
   public static WarOutcome resolveWar(Building a, Building b) {
      if (a == null || b == null || a == b || !a.isActive || !b.isActive) {
         return WarOutcome.none("invalid / inactive pair");
      }
      int sa = strengthOf(a);
      int sb = strengthOf(b);
      double ratio = Math.max(sa, sb) / Math.max(1.0, Math.min(sa, sb));
      Building winner = sa >= sb ? a : b;
      Building loser = winner == a ? b : a;
      int winnerRadiusBefore = VillageExpansion.radiusOf(winner);
      int loserRadiusBefore = VillageExpansion.radiusOf(loser);

      // ---- OVERWHELMING disparity → the weaker SUES FOR PEACE (retreats, cedes some resources, NOT wiped out) ----
      if (ratio >= OVERWHELMING_RATIO) {
         int loserRadiusAfter = Math.max(MIN_RADIUS, loserRadiusBefore - RETREAT_STEP);
         setRadius(loser, loserRadiusAfter);
         int ceded = transferResources(loser, winner, PEACE_RESOURCE_CEDE);
         // Hostile but not annihilated: heavy on the loser's side, lighter on the winner's (it showed mercy).
         setRelation(loser, winner, -100);
         setRelation(winner, loser, -50);
         endHostilities(winner, loser); // the war is over — clear the under-attack flag on both survivors
         MillLog.major(null, TAG + " RESOLVE '" + safeName(a) + "' vs '" + safeName(b) + "': strength " + sa
            + " vs " + sb + " (ratio " + round1(ratio) + " ≥ " + OVERWHELMING_RATIO + ") → '" + safeName(loser)
            + "' SUES FOR PEACE: retreats radius " + loserRadiusBefore + "→" + loserRadiusAfter
            + ", cedes " + ceded + " resources to '" + safeName(winner) + "'; NOT annihilated (alive="
            + loser.isActive + ")");
         return new WarOutcome(Result.PEACE_RETREAT, winner, loser, "overwhelming disparity → sue for peace",
            winnerRadiusBefore, winnerRadiusBefore, loserRadiusBefore, loserRadiusAfter, ceded);
      }

      // ---- CONTESTED: winner TAKES territory + a share of resources; loser absorbed if crushed else retreats ----
      int winnerRadiusAfter = Math.min(VillageExpansion.MAX_RADIUS, winnerRadiusBefore + WIN_GROW_STEP);
      setRadius(winner, winnerRadiusAfter);
      int taken = transferResources(loser, winner, WIN_RESOURCE_SHARE);

      boolean crushed = popOf(loser) <= CRUSH_POP || loser.getVillagerRecords().size() == 0
         || sa(loser) * 2 < sa(winner);
      if (crushed) {
         // REUSE the Phase-4 registry-safe absorb: the crushed loser is folded into the winner (records,
         // buildings, territory; the loser town hall demoted out of the registry cleanly, double-absorb guarded).
         VillageMergeFound.MergeOutcome m = VillageMergeFound.absorbForWar(winner, loser);
         MillLog.major(null, TAG + " RESOLVE '" + safeName(a) + "' vs '" + safeName(b) + "': strength " + sa
            + " vs " + sb + " → '" + safeName(winner) + "' WINS, '" + safeName(loser)
            + "' CRUSHED → ABSORBED (territory radius " + winnerRadiusBefore + "→" + winnerRadiusAfter
            + ", took " + taken + " resources; absorb=" + (m != null ? m.result : "?") + ")");
         return new WarOutcome(Result.WIN_ABSORB, winner, loser, "winner crushes + absorbs loser",
            winnerRadiusBefore, winnerRadiusAfter, loserRadiusBefore, 0, taken);
      }

      int loserRadiusAfter = Math.max(MIN_RADIUS, loserRadiusBefore - RETREAT_STEP);
      setRadius(loser, loserRadiusAfter);
      setRelation(loser, winner, -80);
      setRelation(winner, loser, -60);
      endHostilities(winner, loser); // the war is over — clear the under-attack flag on both survivors
      MillLog.major(null, TAG + " RESOLVE '" + safeName(a) + "' vs '" + safeName(b) + "': strength " + sa
         + " vs " + sb + " → '" + safeName(winner) + "' WINS, '" + safeName(loser)
         + "' RETREATS (winner radius " + winnerRadiusBefore + "→" + winnerRadiusAfter
         + ", loser radius " + loserRadiusBefore + "→" + loserRadiusAfter + ", took " + taken + " resources)");
      return new WarOutcome(Result.WIN_RETREAT, winner, loser, "winner takes territory/resources, loser retreats",
         winnerRadiusBefore, winnerRadiusAfter, loserRadiusBefore, loserRadiusAfter, taken);
   }

   /**
    * Transfer a {@code share} (0..1) of the loser's REAL stock to the winner — debited from the loser's
    * buildings (authoritative {@code takeGoods}) and credited into the winner (authoritative {@code storeGoods}).
    * No grant: only what the loser actually holds moves, and it really lands in the winner's stock. Returns the
    * number of units transferred.
    */
   public static int transferResources(Building loser, Building winner, double share) {
      int total = villageStock(loser);
      int want = (int) Math.floor(total * share);
      if (want <= 0) {
         return 0;
      }
      Building winnerSink = pickSink(winner);
      int moved = 0;
      int guard = 0;
      while (moved < want && guard++ < want * 4 + 32) {
         InvItem best = null;
         int bestCount = 0;
         Building bestBuilding = null;
         for (Building lb : loser.getBuildings()) {
            if (lb == null) {
               continue;
            }
            for (Map.Entry<InvItem, Integer> e : lb.getInventoryGoods().entrySet()) {
               if (e.getValue() != null && e.getValue() > bestCount) {
                  bestCount = e.getValue();
                  best = e.getKey();
                  bestBuilding = lb;
               }
            }
         }
         if (best == null || bestBuilding == null || bestCount <= 0) {
            break; // nothing left to take
         }
         Item item = best.getItem();
         int take = Math.min(want - moved, bestCount);
         int removed = bestBuilding.takeGoods(item, best.meta, take);
         if (removed <= 0) {
            break;
         }
         if (winnerSink != null) {
            try {
               winnerSink.storeGoods(item, best.meta, removed); // REAL credit into the winner (no grant)
            } catch (Throwable t) {
               MillLog.printException(TAG + " resource credit to winner failed", t);
            }
         }
         moved += removed;
      }
      return moved;
   }

   /** The winner's building best able to receive looted goods (a building with a chest; else the town hall). */
   private static Building pickSink(Building winner) {
      try {
         for (Building b : winner.getBuildings()) {
            if (b != null && b.getResManager() != null && !b.getResManager().chests.isEmpty()) {
               return b;
            }
         }
      } catch (Throwable ignored) {
      }
      return winner;
   }

   private static int villageStock(Building townHall) {
      try {
         return com.coderyo.jason.build.MillProceduralConstruction.villageStockTotal(townHall);
      } catch (Throwable t) {
         return 0;
      }
   }

   /**
    * Set a village's PER-VILLAGE claimed radius ({@link com.coderyo.jason.expand.VillageTerritory}) + re-bound its
    * village map at the new claim. Writes the village's OWN territory entry — NOT the shared per-culture
    * {@code villageType.radius} — so a winner growing or a loser retreating only changes ITS OWN radius and never
    * clobbers a same-culture neighbour's (the war territory-tracking bug this fix targets). The map re-bound
    * applies the new per-village radius transiently into the upstream map-bounding then restores the shared field.
    */
   private static void setRadius(Building townHall, int radius) {
      try {
         com.coderyo.jason.expand.VillageTerritory.get().set(townHall, radius);
         com.coderyo.jason.expand.VillageTerritory.get().withRadiusApplied(townHall, () -> {
            try {
               townHall.winfo.world = null;
               townHall.updateWorldInfo();
            } catch (Throwable t) {
               MillLog.printException(TAG + " radius/claim rebuild failed", t);
            }
         });
      } catch (Throwable t) {
         MillLog.printException(TAG + " radius/claim rebuild failed", t);
      }
   }

   /** The war has concluded — clear the {@code underAttack} flag on both surviving villages (raid system reset). */
   private static void endHostilities(Building winner, Building loser) {
      try {
         if (winner != null && winner.isActive) {
            winner.underAttack = false;
         }
         if (loser != null && loser.isActive) {
            loser.underAttack = false;
         }
      } catch (Throwable ignored) {
      }
   }

   /** Force a one-sided relation value (the war outcome dictates each side's stance independently). */
   private static void setRelation(Building from, Building to, int value) {
      try {
         from.getRelations().put(to.getPos(), value);
      } catch (Throwable t) {
         MillLog.printException(TAG + " set-relation failed", t);
      }
   }

   // ===============================================================================================
   // PEACE / RECOVERY
   // ===============================================================================================

   /**
    * Recover the pair's relations toward NEUTRAL (mirrors warsim.py recover_relations): each recovery tick adds
    * {@link #RELATION_RECOVERY} to each side, capped at 0 (never past neutral). Sets up #7 diplomacy. Returns the
    * recovered a→b relation.
    */
   public static int recoverRelations(Building a, Building b) {
      int ra = Math.min(0, relation(a, b) + RELATION_RECOVERY);
      int rb = Math.min(0, relation(b, a) + RELATION_RECOVERY);
      setRelation(a, b, ra);
      setRelation(b, a, rb);
      MillLog.major(null, TAG + " RECOVER '" + safeName(a) + "' x '" + safeName(b)
         + "' relations recovering toward neutral → " + ra + " / " + rb + " (post-war peace, +" + RELATION_RECOVERY + ")");
      return ra;
   }

   // ===============================================================================================
   // helpers
   // ===============================================================================================

   /** Unordered pair key (commutative) "lo:hi" from two town-hall block positions. */
   private static String pairKey(Building a, Building b) {
      long ka = a.getPos().getBlockPos().asLong();
      long kb = b.getPos().getBlockPos().asLong();
      long lo = Math.min(ka, kb);
      long hi = Math.max(ka, kb);
      return lo + ":" + hi;
   }

   private static double round1(double d) {
      return Math.round(d * 10.0) / 10.0;
   }

   private static String safeName(Building th) {
      try {
         return th.getVillageQualifiedName();
      } catch (Throwable t) {
         return String.valueOf(th.getPos());
      }
   }

   /** Current accrued tension for a pair (0 if none) — for the demo/observer. */
   public static double tensionOf(Building a, Building b) {
      return TENSION.getOrDefault(pairKey(a, b), 0.0);
   }

   /** Force a pair's accrued tension (the demo seeds this to drive a deterministic war). */
   public static void seedTension(Building a, Building b, double value) {
      TENSION.put(pairKey(a, b), value);
   }

   /** Whether the pair is currently flagged at war. */
   public static boolean atWar(Building a, Building b) {
      return AT_WAR.contains(pairKey(a, b));
   }

   /** Drop a village's war/tension state (e.g. on unload / after it is absorbed) so it doesn't leak. */
   public static void clear(Building townHall) {
      if (townHall == null || townHall.getPos() == null) {
         return;
      }
      long k = townHall.getPos().getBlockPos().asLong();
      LAST_TICK.remove(k);
      // Drop any pair keys ("lo:hi") whose lo or hi member is this town hall.
      String member = String.valueOf(k);
      TENSION.keySet().removeIf(pk -> involves(pk, member));
      AT_WAR.removeIf(pk -> involves(pk, member));
   }

   private static boolean involves(String pairKey, String memberKey) {
      int i = pairKey.indexOf(':');
      if (i < 0) {
         return false;
      }
      return pairKey.substring(0, i).equals(memberKey) || pairKey.substring(i + 1).equals(memberKey);
   }
}
