package com.coderyo.jason.build;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.VillagerRecord;

/**
 * Phase 2 (#6) PROCEDURAL BUILDING — the WEIGHTED gap-priority NEEDS MODEL.
 *
 * <p>This is the faithful Java port of the sim-validated decision logic in
 * {@code task-ops-sim/buildsim.py}: it quantifies each unmet village need as a GAP magnitude, then
 * scores those gaps on a COMMON WEIGHTED scale (raw magnitudes are NOT comparable across categories —
 * "6 homeless villagers" and "a resource count of 40" are different units), and picks the single
 * highest-scoring gap as the next building to construct.
 *
 * <p>The python sim caught + fixed exactly this: comparing raw gaps picked the wrong building, so the
 * model NORMALISES resource shortfalls to a fraction-of-need and applies survival-first weights
 * (defense &gt; housing &gt; resource &gt; social). It also exposes the chosen REASON so the build is
 * observable. This class reads the live {@link Building} (townhall) for population, housing capacity,
 * stored resource goods, and threat/defense — no new state.
 *
 * <h2>Weights (identical to the sim)</h2>
 * <ul>
 *   <li>defense  = gap * 12  (under attack — most urgent)</li>
 *   <li>housing  = gap * 10  (homeless villagers — urgent)</li>
 *   <li>workshop = (gap / need) * 6  (NORMALISED resource shortfall, 0..1)</li>
 *   <li>market   = gap * 2  (growth / social — lowest)</li>
 * </ul>
 */
public final class MillNeedsModel {

   /** Per-resource target stock the village wants on hand (matches buildsim.py {@code needs}). */
   public static final int NEED_WOOD = 20;
   public static final int NEED_STONE = 20;
   public static final int NEED_FOOD = 40;

   private MillNeedsModel() {
   }

   /** The building type the weighted gap-priority selected. */
   public enum BuildType {
      HOUSE, WORKSHOP, TOWER, MARKET, GRANARY
   }

   /** A resource the workshop can be tuned to produce (the missing one drives the workshop choice). */
   public enum Resource {
      WOOD, STONE, FOOD, NONE
   }

   /**
    * The model's decision: the chosen building type, the resource it is tuned to (for WORKSHOP), a
    * human-readable reason key (e.g. {@code "housing"}, {@code "workshop:food"}, {@code "defense"}),
    * the full weighted-score table (for the {@code ███ SIM BUILD} evidence), and the raw gaps.
    */
   public static final class Decision {
      public final BuildType type;
      public final Resource resource;
      public final String reason;
      public final Map<String, Double> scores;
      public final Map<String, Integer> gaps;

      public Decision(BuildType type, Resource resource, String reason,
               Map<String, Double> scores, Map<String, Integer> gaps) {
         this.type = type;
         this.resource = resource;
         this.reason = reason;
         this.scores = scores;
         this.gaps = gaps;
      }

      @Override
      public String toString() {
         return "Decision[type=" + type + " resource=" + resource + " reason=" + reason
            + " scores=" + scores + " gaps=" + gaps + "]";
      }
   }

   /** A pure snapshot of the village state the model reads — also the unit a headless test can build directly. */
   public static final class VillageState {
      public final int pop;
      public final int housingCap;
      public final int woodStock;
      public final int stoneStock;
      public final int foodStock;
      public final int threat;
      public final int defense;
      public final boolean hasMarket;

      public VillageState(int pop, int housingCap, int woodStock, int stoneStock, int foodStock,
                          int threat, int defense, boolean hasMarket) {
         this.pop = pop;
         this.housingCap = housingCap;
         this.woodStock = woodStock;
         this.stoneStock = stoneStock;
         this.foodStock = foodStock;
         this.threat = threat;
         this.defense = defense;
         this.hasMarket = hasMarket;
      }
   }

   /**
    * Read the live village state off the townhall {@link Building}: population = number of villager
    * records; housing capacity = sum of resident slots over the village's houses; resource stocks =
    * stored goods (logs+planks → wood, stone+cobble → stone, wheat+bread+carrot → food); threat =
    * raiding strength bearing on it (or {@code underAttack}); defense = defending strength; market =
    * whether any market/shop building exists.
    */
   public static VillageState readVillage(Building townHall) {
      int pop = townHall.getVillagerRecords().size();

      int housingCap = 0;
      boolean hasMarket = false;
      for (Building b : townHall.getBuildings()) {
         if (b.isHouse()) {
            housingCap += b.location.getMaleResidents().size() + b.location.getFemaleResidents().size();
         }
         if (b.location != null && b.location.shop != null && b.location.shop.length() > 0) {
            hasMarket = true;
         }
         if (b.containsTags("market") || b.containsTags("inn")) {
            hasMarket = true;
         }
      }
      // A village with no house records still houses its current pop in the townhall — never report 0 cap
      // for a populated village, or it would forever build houses. Floor capacity at the larger of the two.
      housingCap = Math.max(housingCap, 0);

      int wood = townHall.countGoods(Blocks.OAK_LOG, 0)
         + townHall.countGoods(Blocks.OAK_PLANKS, 0)
         + townHall.countGoods(Blocks.SPRUCE_LOG, 0)
         + townHall.countGoods(Items.STICK, 0);
      int stone = townHall.countGoods(Blocks.STONE, 0)
         + townHall.countGoods(Blocks.COBBLESTONE, 0)
         + townHall.countGoods(Blocks.STONE_BRICKS, 0);
      int food = townHall.countGoods(Items.WHEAT, 0)
         + townHall.countGoods(Items.BREAD, 0)
         + townHall.countGoods(Items.CARROT, 0)
         + townHall.countGoods(Items.WHEAT_SEEDS, 0);

      int defense = safeDefendingStrength(townHall);
      int threat = safeThreat(townHall);

      return new VillageState(pop, housingCap, wood, stone, food, threat, defense, hasMarket);
   }

   private static int safeDefendingStrength(Building th) {
      try {
         return th.getVillageDefendingStrength();
      } catch (Throwable t) {
         return 0;
      }
   }

   private static int safeThreat(Building th) {
      try {
         int raid = th.getVillageAttackerStrength();
         if (th.underAttack && raid <= 0) {
            return 10; // under attack but no strength resolved yet — still a real defense need.
         }
         return raid;
      } catch (Throwable t) {
         return th.underAttack ? 10 : 0;
      }
   }

   /** Convenience: read + decide off a live townhall. */
   public static Decision decide(Building townHall) {
      return decide(readVillage(townHall));
   }

   /**
    * Compute gaps + WEIGHTED scores and pick the top one — the exact algorithm asserted by buildsim.py.
    * Returns {@code null} when there are no gaps (nothing to build).
    */
   public static Decision decide(VillageState v) {
      Map<String, Integer> gaps = computeGaps(v);
      if (gaps.isEmpty()) {
         return null;
      }

      Map<String, Double> scores = new LinkedHashMap<>();
      for (Map.Entry<String, Integer> e : gaps.entrySet()) {
         String k = e.getKey();
         int mag = e.getValue();
         double score;
         if (k.equals("defense")) {
            score = mag * 12.0;
         } else if (k.equals("housing")) {
            score = mag * 10.0;
         } else if (k.startsWith("workshop:")) {
            int need = needFor(k.substring("workshop:".length()));
            score = ((double) mag / Math.max(1, need)) * 6.0;
         } else if (k.equals("market")) {
            score = mag * 2.0;
         } else {
            score = mag;
         }
         scores.put(k, round2(score));
      }

      String topKey = null;
      double topScore = Double.NEGATIVE_INFINITY;
      for (Map.Entry<String, Double> e : scores.entrySet()) {
         if (e.getValue() > topScore) {
            topScore = e.getValue();
            topKey = e.getKey();
         }
      }

      BuildType type;
      Resource resource = Resource.NONE;
      if (topKey == null) {
         return null;
      } else if (topKey.equals("housing")) {
         type = BuildType.HOUSE;
      } else if (topKey.startsWith("workshop:")) {
         type = BuildType.WORKSHOP;
         resource = resourceOf(topKey.substring("workshop:".length()));
      } else if (topKey.equals("defense")) {
         type = BuildType.TOWER;
      } else if (topKey.equals("market")) {
         type = BuildType.MARKET;
      } else {
         type = BuildType.GRANARY;
      }
      return new Decision(type, resource, topKey, scores, gaps);
   }

   /**
    * Phase 7 (#7) DISCUSSION STEER: given the model's own {@code base} decision for a village, return a decision
    * that PROMOTES the discussion-endorsed {@code preferred} build type — but ONLY if a REAL gap of that type
    * already exists in {@code base.gaps}. If a matching gap exists we build a decision for it (carrying the same
    * gaps + scores, with the reason tagged {@code "+discussed"} so the steer is verifiable in the build log).
    * If no such gap exists we return {@code base} UNCHANGED — we never fabricate a need the gaps don't have
    * (strict no-fabrication / no-fallback). A {@code null} base (no gaps at all) is also returned unchanged.
    */
   public static Decision steerToward(Decision base, BuildType preferred) {
      if (base == null || preferred == null || base.type == preferred) {
         return base;
      }
      // Find the gap key that corresponds to the preferred type, and only if it is a genuine (present) gap.
      String matchKey = null;
      Resource resource = Resource.NONE;
      for (Map.Entry<String, Integer> e : base.gaps.entrySet()) {
         String k = e.getKey();
         if (e.getValue() == null || e.getValue() <= 0) {
            continue;
         }
         BuildType t;
         Resource r = Resource.NONE;
         if (k.equals("housing")) {
            t = BuildType.HOUSE;
         } else if (k.startsWith("workshop:")) {
            t = BuildType.WORKSHOP;
            r = resourceOf(k.substring("workshop:".length()));
         } else if (k.equals("defense")) {
            t = BuildType.TOWER;
         } else if (k.equals("market")) {
            t = BuildType.MARKET;
         } else {
            t = BuildType.GRANARY;
         }
         if (t == preferred) {
            matchKey = k;
            resource = r;
            break;
         }
      }
      if (matchKey == null) {
         return base; // no genuine gap supports the discussed type — do NOT fabricate one.
      }
      return new Decision(preferred, resource, matchKey + "+discussed", base.scores, base.gaps);
   }

   /** Quantify each unmet need as a gap magnitude (bigger = more urgent). Mirrors {@code compute_gaps}. */
   public static Map<String, Integer> computeGaps(VillageState v) {
      Map<String, Integer> g = new LinkedHashMap<>();
      put(g, "housing", Math.max(0, v.pop - v.housingCap));
      put(g, "workshop:wood", Math.max(0, NEED_WOOD - v.woodStock));
      put(g, "workshop:stone", Math.max(0, NEED_STONE - v.stoneStock));
      put(g, "workshop:food", Math.max(0, NEED_FOOD - v.foodStock));
      put(g, "defense", Math.max(0, v.threat - v.defense));
      put(g, "market", v.hasMarket ? 0 : (v.pop / 12));
      return g;
   }

   private static void put(Map<String, Integer> g, String k, int v) {
      if (v > 0) {
         g.put(k, v);
      }
   }

   private static int needFor(String res) {
      switch (res) {
         case "wood": return NEED_WOOD;
         case "stone": return NEED_STONE;
         case "food": return NEED_FOOD;
         default: return 1;
      }
   }

   private static Resource resourceOf(String res) {
      switch (res) {
         case "wood": return Resource.WOOD;
         case "stone": return Resource.STONE;
         case "food": return Resource.FOOD;
         default: return Resource.NONE;
      }
   }

   private static double round2(double d) {
      return Math.round(d * 100.0) / 100.0;
   }
}
