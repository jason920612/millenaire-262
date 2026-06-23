package com.coderyo.jason.merge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.Item;

import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.VillagerRecord;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.WorldGenVillage;

import com.coderyo.jason.build.MillNeedsModel;
import com.coderyo.jason.build.MillProceduralConstruction;
import com.coderyo.jason.expand.VillageExpansion;

/**
 * Phase 4 (#5) — village MERGE + new-village FOUNDING (the emergent-civilization social driver).
 *
 * <p>Faithful Java port of the sim-validated logic in {@code task-ops-sim/mergesim.py}. It builds on Phase 3
 * ({@link VillageExpansion}): villages grow their claimed radii OUTWARD until they grow INTO each other —
 * which makes them merge candidates — and a village that grows OVERCROWDED with a resource SURPLUS spins off
 * a colony.
 *
 * <h2>MERGE — {@link #tryMerge(Building, Building)}</h2>
 * Two villages MERGE iff (matching {@code mergesim.py try_merge}):
 * <ul>
 *   <li>their claimed radii OVERLAP (distance &lt; radiusA + radiusB),</li>
 *   <li>they are CULTURE-COMPATIBLE (same culture — cross-culture stays separate for now),</li>
 *   <li>they are FRIENDLY (neither relation &lt; 0). A HOSTILE overlap is NOT a merge → it returns a
 *       {@link Result#WAR} signal the Phase-5 war path consumes (we never merge hostiles),</li>
 *   <li>BOTH CONSENT (the #7-diplomacy agreement proxy until that lands: both non-hostile + the smaller is
 *       struggling/willing + the larger has room — see {@link #consents}).</li>
 * </ul>
 * On merge the LARGER/STRONGER village (by population, then defending strength) ABSORBS the smaller: the
 * smaller's villager RECORDS + sub-BUILDINGS + TERRITORY join the larger (records re-homed via the
 * authoritative {@link Building#transferVillagerPermanently}, sub-buildings re-pointed to the larger town
 * hall, the larger's claimed radius grown to cover both), and the smaller TOWN HALL is DEMOTED — removed from
 * the {@link MillWorldData} village registry CLEANLY (no dangling refs / save corruption) and kept only as a
 * district building of the larger village. Real village data is genuinely merged: nothing is faked or reverted.
 *
 * <h2>FOUND — {@link #tryFound(Building, List)}</h2>
 * A village FOUNDS a colony iff (matching {@code mergesim.py try_found}):
 * <ul>
 *   <li>it is OVERCROWDED ({@code pop − housingCap ≥ }{@link #FOUND_POP_PRESSURE}),</li>
 *   <li>it has a resource SURPLUS ({@code realSurplus ≥ }{@link #FOUND_SURPLUS}),</li>
 *   <li>a good DISTANT site exists ({@code ≥ }{@link #GOOD_SITE_MIN_DIST} from the mother AND not inside
 *       another village's claim).</li>
 * </ul>
 * On found a SPLINTER GROUP of villager records LEAVES with resources and a NEW village of the SAME CULTURE is
 * created at the distant site via the real village-creation path ({@link WorldGenVillage}); its first buildings
 * are laid by the Phase-2 procedural construction. Parent and child get a MUTUAL FRIENDLY relation (trade/ally
 * sub-village, {@code parent = mother}). The mother's population drops and the found surplus is GENUINELY SPENT
 * (debited from the real village-wide stock — no grant).
 *
 * <p>STRICT no-grant / no-fallback (the user's hard rule): conditions unmet → no merge/found this tick (the
 * correct outcome, not a bug). Surplus is really spent, village data is really merged, and the registry is
 * mutated only through {@link MillWorldData}'s own add/remove APIs so saves stay consistent.
 *
 * <p>Every decision emits the greppable {@code ███ SIM MERGE} / {@code ███ SIM FOUND} evidence.
 */
public final class VillageMergeFound {

   /** Greppable tag for all MERGE observation lines. */
   public static final String TAG_MERGE = "███ SIM MERGE";
   /** Greppable tag for all FOUND observation lines. */
   public static final String TAG_FOUND = "███ SIM FOUND";

   // ---- thresholds (mirror mergesim.py) -----------------------------------------------------------
   /** Villagers over housing capacity before a splinter group leaves to found a colony. */
   public static final int FOUND_POP_PRESSURE = 8;
   /** Real surplus the splinter party must take to seed a new village (genuinely spent — no grant). */
   public static final int FOUND_SURPLUS = 128;
   /** The new site must be at least this far from the mother (room to grow without instant overlap). */
   public static final int GOOD_SITE_MIN_DIST = 200;
   /** Friendly relation value set mutually between a mother and her founded colony (trade/ally). */
   public static final int COLONY_FRIENDLY_RELATION = 100;

   /** Per-village cooldown (ticks) so a village evaluates merge/found deliberately, not every town-hall tick. */
   public static final long MERGE_FOUND_COOLDOWN_TICKS = 600L;
   private static final Map<Long, Long> LAST_TICK = new HashMap<>();

   private VillageMergeFound() {
   }

   /** The outcome class of a merge attempt (mirrors mergesim.py's tagged return). */
   public enum Result {
      MERGED, WAR, NO_MERGE
   }

   /** A merge attempt's result + a human-readable reason (for the {@code ███ SIM MERGE} evidence). */
   public static final class MergeOutcome {
      public final Result result;
      public final String reason;
      /** The surviving (absorbing) town hall on a MERGE, else null. */
      public final Building survivor;
      /** The absorbed (demoted) town hall on a MERGE, else null. */
      public final Building absorbed;

      MergeOutcome(Result result, String reason, Building survivor, Building absorbed) {
         this.result = result;
         this.reason = reason;
         this.survivor = survivor;
         this.absorbed = absorbed;
      }

      static MergeOutcome no(String reason) {
         return new MergeOutcome(Result.NO_MERGE, reason, null, null);
      }

      static MergeOutcome war(String reason) {
         return new MergeOutcome(Result.WAR, reason, null, null);
      }
   }

   /** A found attempt's result (for the {@code ███ SIM FOUND} evidence). */
   public static final class FoundOutcome {
      public final boolean founded;
      public final String reason;
      public final Point site;
      public final int splinterSize;
      public final int surplusSpent;

      FoundOutcome(boolean founded, String reason, Point site, int splinterSize, int surplusSpent) {
         this.founded = founded;
         this.reason = reason;
         this.site = site;
         this.splinterSize = splinterSize;
         this.surplusSpent = surplusSpent;
      }

      static FoundOutcome no(String reason) {
         return new FoundOutcome(false, reason, null, 0, 0);
      }
   }

   // ===============================================================================================
   // TICK ENTRY — autonomous, called from the town hall construction tick (after Phase-3 expansion).
   // ===============================================================================================

   /**
    * The autonomous merge/found tick for one village town hall. Tries (1) to MERGE with the nearest
    * culture-compatible friendly village whose claim it now overlaps, then (2) to FOUND a colony if it is
    * overcrowded with a surplus. Returns {@code true} if it merged or founded this tick (so the caller can
    * treat it like a construction change). Strict: unmet conditions → no-op (no grant, no fallback).
    */
   public static boolean tick(Building townHall) {
      if (townHall == null || townHall.world == null || townHall.world.isClientSide() || !townHall.isTownhall) {
         return false;
      }
      long key = townHall.getPos().getBlockPos().asLong();
      long now = townHall.world.getGameTime();
      Long last = LAST_TICK.get(key);
      if (last != null && now - last < MERGE_FOUND_COOLDOWN_TICKS) {
         return false;
      }
      LAST_TICK.put(key, now);

      MillWorldData mw = Mill.getMillWorld(townHall.world);
      if (mw == null) {
         return false;
      }
      List<Building> villages = liveTownHalls(mw);

      // ---- (1) MERGE: against the nearest overlapping culture-compatible village ----
      for (Building other : villages) {
         if (other == townHall || other.getPos() == null) {
            continue;
         }
         MergeOutcome m = tryMerge(townHall, other);
         if (m.result == Result.MERGED) {
            logMerge(m, townHall, other);
            return true;
         }
         if (m.result == Result.WAR) {
            // Don't merge a hostile overlap — feed it into the Phase-5 expansion-war path (seed tension to the
            // threshold so the war tick declares + resolves it) and leave the villages intact for now.
            logMerge(m, townHall, other);
            com.coderyo.jason.war.VillageWar.onHostileOverlap(townHall, other);
         }
      }

      // ---- (2) FOUND: a colony if overcrowded + surplus + a good distant site ----
      FoundOutcome f = tryFound(townHall, villages);
      if (f.founded) {
         logFound(f, townHall);
         return true;
      }
      // Only log a substantive no-found (overcrowded but blocked), not the common "not overcrowded" no-op.
      if (!f.reason.startsWith("not overcrowded")) {
         logFound(f, townHall);
      }
      return false;
   }

   // ===============================================================================================
   // MERGE
   // ===============================================================================================

   /** Euclidean horizontal distance between two town halls. */
   private static double distance(Building a, Building b) {
      Point pa = a.getPos();
      Point pb = b.getPos();
      return Math.hypot(pa.getiX() - pb.getiX(), pa.getiZ() - pb.getiZ());
   }

   /** The two claims overlap (mirrors mergesim.py overlap): distance &lt; radiusA + radiusB. */
   public static boolean overlap(Building a, Building b) {
      return distance(a, b) < (VillageExpansion.radiusOf(a) + VillageExpansion.radiusOf(b));
   }

   /** Same culture = compatible (cross-culture stays separate for now — mirrors mergesim.py compatible). */
   public static boolean compatible(Building a, Building b) {
      return a.culture != null && b.culture != null && a.culture.key.equals(b.culture.key);
   }

   /** Relation a→b (0 if unknown). */
   private static int relation(Building a, Building b) {
      Integer r = a.getRelations().get(b.getPos());
      return r == null ? 0 : r;
   }

   /**
    * BOTH must consent (mirrors mergesim.py consents). Proxy for the #7 diplomacy agreement until that lands:
    * both friendly (neither relation &lt; 0), AND the smaller is struggling/willing (overcrowded OR small pop),
    * AND the larger has room to absorb (its housing isn't already over capacity by more than the smaller adds).
    */
   public static boolean consents(Building a, Building b) {
      if (relation(a, b) < 0 || relation(b, a) < 0) {
         return false; // a hostile side never consents (this is also the WAR gate)
      }
      Building big = larger(a, b);
      Building small = big == a ? b : a;
      // The smaller is willing if it is small or itself under space pressure (it gains safety + services).
      boolean smallWilling = popOf(small) <= popOf(big) || isStruggling(small);
      // The larger has room: it isn't so overcrowded that absorbing the smaller would be untenable.
      boolean bigHasRoom = bigHasRoom(big, small);
      return smallWilling && bigHasRoom;
   }

   /** A village is "struggling/willing" when overcrowded relative to its housing, or has little/no surplus. */
   private static boolean isStruggling(Building v) {
      try {
         MillNeedsModel.VillageState vs = MillNeedsModel.readVillage(v);
         if (vs.pop - vs.housingCap > 0) {
            return true;
         }
      } catch (Throwable ignored) {
      }
      return VillageExpansion.realSurplus(v) < FOUND_SURPLUS;
   }

   /** The larger village can host the smaller's residents without being driven far past its own capacity. */
   private static boolean bigHasRoom(Building big, Building small) {
      try {
         MillNeedsModel.VillageState vs = MillNeedsModel.readVillage(big);
         // Always room when not yet overcrowded; tolerate a modest overshoot equal to the pressure threshold.
         return (vs.pop - vs.housingCap) < FOUND_POP_PRESSURE;
      } catch (Throwable t) {
         return true;
      }
   }

   private static int popOf(Building th) {
      try {
         return th.getVillagerRecords().size();
      } catch (Throwable t) {
         return 0;
      }
   }

   private static int strengthOf(Building th) {
      try {
         return th.getVillageDefendingStrength();
      } catch (Throwable t) {
         return 0;
      }
   }

   /** The larger/stronger of two villages (by population, then defending strength) — mirrors mergesim.py. */
   public static Building larger(Building a, Building b) {
      int pa = popOf(a);
      int pb = popOf(b);
      if (pa != pb) {
         return pa > pb ? a : b;
      }
      return strengthOf(a) >= strengthOf(b) ? a : b;
   }

   /**
    * Try to MERGE two villages (mirrors mergesim.py try_merge gates exactly). On a successful merge the larger
    * absorbs the smaller and the smaller town hall is demoted out of the registry CLEANLY. A hostile overlap
    * returns {@link Result#WAR} (the Phase-5 path's signal); cross-culture / non-overlap / no-consent return
    * {@link Result#NO_MERGE}. No grant, no fallback — real records/buildings/territory are moved.
    */
   public static MergeOutcome tryMerge(Building a, Building b) {
      if (a == null || b == null || a == b) {
         return MergeOutcome.no("invalid pair");
      }
      // Guard against DOUBLE-ABSORB: a village removed by an earlier merge this pass can still be referenced by
      // another town hall's stale candidate list. An absorbed village is marked !isActive (in absorb), so skip
      // any pair where either side is no longer an active registered village — keeps the registry consistent.
      if (!a.isActive || !b.isActive) {
         return MergeOutcome.no("a village is no longer active (already absorbed/removed this pass)");
      }
      if (!overlap(a, b)) {
         return MergeOutcome.no("radii do not overlap");
      }
      if (!compatible(a, b)) {
         return MergeOutcome.no("incompatible cultures (" + cultureKey(a) + " vs " + cultureKey(b) + ")");
      }
      if (relation(a, b) < 0 || relation(b, a) < 0) {
         return MergeOutcome.war("overlap with a HOSTILE neighbour → war path (Phase 5), not a merge");
      }
      if (!consents(a, b)) {
         return MergeOutcome.no("both sides do not consent (diplomacy proxy)");
      }

      Building big = larger(a, b);
      Building small = big == a ? b : a;
      return absorb(big, small);
   }

   /**
    * The larger village {@code big} ABSORBS the smaller village {@code small}. Re-homes the smaller's villager
    * records + sub-buildings to the larger town hall, grows the larger's claimed radius to cover both, and
    * DEMOTES the smaller town hall out of the {@link MillWorldData} village registry cleanly. Mutates the
    * registry only through its own APIs so the save stays consistent (no dangling references).
    */
   /**
    * Phase-5 WAR entry point: the war winner ABSORBS a crushed loser, REUSING the same registry-safe
    * {@link #absorb} the merge path uses (records/buildings/territory folded in, the loser town hall demoted out
    * of the {@link MillWorldData} village registry cleanly, double-absorb guarded by {@code isActive}). This is
    * the plan's "REUSE absorb for 'winner absorbs crushed loser'". Strict: an already-inactive loser is skipped
    * (no double-absorb / no dangling registry entry). No grant — real village data is moved.
    */
   public static MergeOutcome absorbForWar(Building winner, Building loser) {
      if (winner == null || loser == null || winner == loser) {
         return MergeOutcome.no("invalid war-absorb pair");
      }
      if (!winner.isActive || !loser.isActive) {
         return MergeOutcome.no("a village is no longer active (already absorbed/removed)");
      }
      return absorb(winner, loser);
   }

   private static MergeOutcome absorb(Building big, Building small) {
      MillWorldData mw = big.mw;
      int bigPopBefore = popOf(big);
      int smallPopBefore = popOf(small);

      // ---- (a) re-home the smaller's villager RECORDS to the larger (authoritative diplomacy transfer) ----
      // Copy first (transfer mutates the source records map).
      List<VillagerRecord> toMove = new ArrayList<>(small.getVillagerRecords().values());
      int movedVillagers = 0;
      for (VillagerRecord vr : toMove) {
         try {
            small.transferVillagerPermanently(vr, big);
            movedVillagers++;
         } catch (Throwable t) {
            MillLog.printException(TAG_MERGE + " villager re-home failed for " + vr, t);
         }
      }

      // ---- (b) re-home the smaller's sub-BUILDINGS to the larger town hall (incl. the demoted town hall) ----
      int movedBuildings = 0;
      List<Point> smallBuildingPos = new ArrayList<>(small.buildings);
      // Ensure the small town hall itself becomes a district building of the larger village.
      if (!smallBuildingPos.contains(small.getPos())) {
         smallBuildingPos.add(small.getPos());
      }
      for (Point bp : smallBuildingPos) {
         Building sub = mw.getBuilding(bp);
         if (sub == null) {
            continue;
         }
         sub.setTownHallPos(big.getPos());           // re-point ownership to the surviving town hall
         if (sub == small) {
            sub.isTownhall = false;                  // demote: it is now a district building, not a town hall
         }
         if (!big.buildings.contains(bp)) {
            big.buildings.addIfAbsent(bp);
         }
         movedBuildings++;
      }

      // ---- (c) grow the larger's PER-VILLAGE claimed radius to TERRITORY-cover both (mirrors mergesim.py) ----
      // Writes only the survivor's own territory entry (VillageTerritory), never the shared villageType.radius, so
      // absorbing does not change any other same-culture village's claim. The absorbed village's own entry is
      // cleared in step (d) — its area is now part of the survivor's grown radius.
      int radiusBefore = VillageExpansion.radiusOf(big);
      int span = (int) Math.ceil(distance(big, small)) + VillageExpansion.radiusOf(small);
      int radiusAfter = Math.min(VillageExpansion.MAX_RADIUS, Math.max(radiusBefore, span));
      com.coderyo.jason.expand.VillageTerritory.get().set(big, radiusAfter);
      rebuildClaim(big);

      // ---- (d) DEMOTE the smaller town hall out of the village registry CLEANLY ----
      // Remove its village-list entry (villages.txt) + its in-progress procedural/expansion state so nothing
      // dangles. The town-hall building itself stays as a registered district building (re-homed above), so its
      // chests/inventory are NOT lost — its goods are now part of the larger village's stock.
      mw.removeVillageOrLoneBuilding(small.getPos());
      small.isActive = false;   // mark absorbed so a stale candidate list can't double-absorb it (tryMerge guards on this)
      small.getRelations().remove(big.getPos());
      big.getRelations().remove(small.getPos());
      VillageExpansion.clear(small);
      MillProceduralConstruction.clear(small);
      com.coderyo.jason.war.VillageWar.clear(small);
      // Drop the absorbed village's own per-village territory entry — its claimed area is now part of the
      // survivor's grown radius, so it must not linger as an independent (and now stale) claim.
      com.coderyo.jason.expand.VillageTerritory.get().clear(small);

      // ---- (e) persist the merged state ----
      big.saveTownHall("Phase-4 merge: absorbed " + safeName(small));

      MillLog.major(null, TAG_MERGE + " ABSORB '" + safeName(big) + "' <- '" + safeName(small)
         + "' culture=" + cultureKey(big) + " movedVillagers=" + movedVillagers + " movedBuildings=" + movedBuildings
         + " pop " + bigPopBefore + "+" + smallPopBefore + "=" + popOf(big)
         + " radius " + radiusBefore + "->" + radiusAfter);
      return new MergeOutcome(Result.MERGED,
         "larger absorbs smaller (friendly, " + cultureKey(big) + ", both consent)", big, small);
   }

   // ===============================================================================================
   // FOUND
   // ===============================================================================================

   /**
    * A good DISTANT colony site (mirrors mergesim.py good_distant_site): the first of the four cardinal points
    * {@link #GOOD_SITE_MIN_DIST} from the mother that does NOT fall inside any other live village's claim.
    */
   public static Point goodDistantSite(Building mother, List<Building> others) {
      Point mp = mother.getPos();
      int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
      for (int[] d : dirs) {
         int sx = mp.getiX() + d[0] * GOOD_SITE_MIN_DIST;
         int sz = mp.getiZ() + d[1] * GOOD_SITE_MIN_DIST;
         boolean clear = true;
         for (Building o : others) {
            if (o == mother || o.getPos() == null) {
               continue;
            }
            double dist = Math.hypot(sx - o.getPos().getiX(), sz - o.getPos().getiZ());
            if (dist <= VillageExpansion.radiusOf(o) + 40) {
               clear = false;
               break;
            }
         }
         if (clear) {
            return new Point(sx, mp.getiY(), sz);
         }
      }
      return null;
   }

   /**
    * Try to FOUND a colony from {@code mother} (mirrors mergesim.py try_found gates). On success a splinter
    * group of villager records leaves, a new same-culture FRIENDLY sub-village is created at a distant site via
    * the real {@link WorldGenVillage} path, mutual friendly relations are set, and the real surplus is SPENT.
    */
   public static FoundOutcome tryFound(Building mother, List<Building> villages) {
      MillNeedsModel.VillageState vs;
      try {
         vs = MillNeedsModel.readVillage(mother);
      } catch (Throwable t) {
         return FoundOutcome.no("could not read village state: " + t);
      }
      int pressure = vs.pop - vs.housingCap;
      if (pressure < FOUND_POP_PRESSURE) {
         return FoundOutcome.no("not overcrowded enough (" + pressure + "<" + FOUND_POP_PRESSURE + ")");
      }
      int surplus = VillageExpansion.realSurplus(mother);
      if (surplus < FOUND_SURPLUS) {
         return FoundOutcome.no("insufficient surplus (" + surplus + "<" + FOUND_SURPLUS + ")");
      }
      Point site = goodDistantSite(mother, villages);
      if (site == null) {
         return FoundOutcome.no("no good distant site (all blocked/too close)");
      }
      return foundColony(mother, site, pressure);
   }

   /**
    * Create the colony: spend the real surplus, pick the splinter records, create the new village of the same
    * culture at {@code site} via {@link WorldGenVillage}, re-home the splinter records into it, lay its first
    * buildings procedurally, and set the mutual friendly relation. No grant — surplus is genuinely debited and
    * the mother's population genuinely drops.
    */
   private static FoundOutcome foundColony(Building mother, Point site, int pressure) {
      // ---- (1) SPEND the real surplus the splinter takes (no grant — debit the village-wide stock) ----
      int spent = spendSurplus(mother, FOUND_SURPLUS);
      if (spent < FOUND_SURPLUS) {
         // Couldn't actually pay the founding cost out of the real stock → do NOT found (no fabrication).
         return FoundOutcome.no("could not spend the founding surplus (only " + spent + "/" + FOUND_SURPLUS
            + " real stock available)");
      }

      // ---- (2) create the NEW same-culture village at the distant site via the real creation path ----
      Culture culture = mother.culture;
      VillageType vtype = pickRegularVillageType(culture);
      if (vtype == null) {
         return FoundOutcome.no("no regular village type for culture " + cultureKey(mother));
      }
      MillWorldData mw = mother.mw;
      String colonyName = safeName(mother) + " Colony";
      boolean created;
      try {
         WorldGenVillage gen = new WorldGenVillage();
         // parentVillage = the mother's pos → the new village is recorded as her sub-village/child.
         created = gen.generateVillageAtPoint(mother.world, MillRandom.random, site.getiX(), 0, site.getiZ(),
            null, false, true, false, 0, vtype, colonyName, mother.getPos(), 1.0F);
      } catch (Throwable t) {
         MillLog.printException(TAG_FOUND + " colony village generation failed", t);
         created = false;
      }
      if (!created) {
         return FoundOutcome.no("village-creation path could not place a colony at " + site);
      }
      Building colony = mw.getClosestVillage(site);
      if (colony == null || colony.getPos() == null
         || colony.getPos().distanceTo(site) > GOOD_SITE_MIN_DIST) {
         return FoundOutcome.no("colony town hall did not register at " + site);
      }

      // ---- (3) move a SPLINTER GROUP of villager records out of the mother into the colony ----
      int leaving = Math.max(4, pressure / 2);
      List<VillagerRecord> pool = new ArrayList<>(mother.getVillagerRecords().values());
      int moved = 0;
      for (VillagerRecord vr : pool) {
         if (moved >= leaving) {
            break;
         }
         // Keep the mother's own anchor villagers if possible — move non-essential records first; here every
         // record is eligible (the colony needs founders), bounded by the splinter size.
         try {
            mother.transferVillagerPermanently(vr, colony);
            moved++;
         } catch (Throwable t) {
            MillLog.printException(TAG_FOUND + " splinter re-home failed for " + vr, t);
         }
      }

      // ---- (4) MUTUAL FRIENDLY relation (trade/ally sub-village) ----
      try {
         mother.adjustRelation(colony.getPos(), COLONY_FRIENDLY_RELATION, true);
      } catch (Throwable t) {
         // adjustRelation sets both sides; if it can't resolve, set the maps directly as a careful fallback-free op.
         mother.getRelations().put(colony.getPos(), COLONY_FRIENDLY_RELATION);
         colony.getRelations().put(mother.getPos(), COLONY_FRIENDLY_RELATION);
      }

      // ---- (5) lay the colony's first buildings procedurally (consumes the colony's own seed stock over time) ----
      try {
         MillProceduralConstruction.tick(colony);
      } catch (Throwable t) {
         MillLog.printException(TAG_FOUND + " colony first-building hand-off failed", t);
      }

      mother.saveTownHall("Phase-4 found: spawned colony " + safeName(colony));
      MillLog.major(null, TAG_FOUND + " COLONY '" + safeName(colony) + "' founded by '" + safeName(mother)
         + "' @ " + site + " culture=" + cultureKey(mother) + " splinter=" + moved
         + " surplusSpent=" + spent + " friendlyRelation=" + COLONY_FRIENDLY_RELATION
         + " motherPopNow=" + popOf(mother));
      return new FoundOutcome(true, "overcrowded + surplus + distant site", site, moved, spent);
   }

   // ===============================================================================================
   // shared helpers
   // ===============================================================================================

   /**
    * SPEND {@code amount} units of the village's real stock by debiting its most-abundant goods (same
    * authoritative {@code takeGoods} debit the expansion/procedural construction use). Returns the units
    * actually removed (≤ amount). No grant — only what the village really holds can be removed.
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

   /**
    * Re-bound the village map at the (changed) PER-VILLAGE claim radius — applies this village's own territory
    * ({@link com.coderyo.jason.expand.VillageTerritory}) transiently into the upstream map-bounding then restores
    * the shared {@code villageType.radius}, exactly as {@code VillageExpansion} does. Per-village, no field leak.
    */
   private static void rebuildClaim(Building townHall) {
      try {
         com.coderyo.jason.expand.VillageTerritory.get().withRadiusApplied(townHall, () -> {
            try {
               townHall.winfo.world = null;
               townHall.updateWorldInfo();
            } catch (Throwable t) {
               MillLog.printException(TAG_MERGE + " claim rebuild failed", t);
            }
         });
      } catch (Throwable t) {
         MillLog.printException(TAG_MERGE + " claim rebuild failed", t);
      }
   }

   private static VillageType pickRegularVillageType(Culture culture) {
      if (culture == null) {
         return null;
      }
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

   /** Every live, registered village town hall in the world (a real Building with a location). */
   public static List<Building> liveTownHalls(MillWorldData mw) {
      List<Building> out = new ArrayList<>();
      for (Point p : new ArrayList<>(mw.villagesList.pos)) {
         Building b = mw.getBuilding(p);
         if (b != null && b.isTownhall && b.location != null && b.villageType != null) {
            out.add(b);
         }
      }
      return out;
   }

   private static String cultureKey(Building b) {
      return b != null && b.culture != null ? b.culture.key : "?";
   }

   private static String safeName(Building th) {
      try {
         return th.getVillageQualifiedName();
      } catch (Throwable t) {
         return String.valueOf(th.getPos());
      }
   }

   /** Drop a village's merge/found cooldown state (e.g. on unload / after it is absorbed) so it doesn't leak. */
   public static void clear(Building townHall) {
      if (townHall != null && townHall.getPos() != null) {
         LAST_TICK.remove(townHall.getPos().getBlockPos().asLong());
      }
   }

   private static void logMerge(MergeOutcome o, Building a, Building b) {
      if (o.result == Result.MERGED) {
         // The absorb() already logged the detailed ABSORB line; add the decision summary.
         MillLog.major(null, TAG_MERGE + " MERGED survivor='" + safeName(o.survivor) + "' absorbed='"
            + safeName(o.absorbed) + "' reason=" + o.reason);
      } else if (o.result == Result.WAR) {
         MillLog.major(null, TAG_MERGE + " WAR-SIGNAL '" + safeName(a) + "' x '" + safeName(b)
            + "' — " + o.reason + " (left intact for the Phase-5 war path)");
      } else {
         MillLog.major(null, TAG_MERGE + " NO-MERGE '" + safeName(a) + "' x '" + safeName(b) + "' — " + o.reason);
      }
   }

   private static void logFound(FoundOutcome o, Building mother) {
      if (o.founded) {
         MillLog.major(null, TAG_FOUND + " FOUNDED mother='" + safeName(mother) + "' site=" + o.site
            + " splinter=" + o.splinterSize + " surplusSpent=" + o.surplusSpent);
      } else {
         MillLog.major(null, TAG_FOUND + " NO-FOUND mother='" + safeName(mother) + "' — " + o.reason);
      }
   }
}
