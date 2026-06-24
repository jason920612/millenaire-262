package com.coderyo.jason.build;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.common.buildingplan.BuildingPlan;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.buildingplan.PointType;
import org.millenaire.common.culture.Culture;

/**
 * Phase 2 (#6) PROCEDURAL BUILDING — per-culture STYLE EXTRACTION.
 *
 * <p>The procedural layout is culture-AGNOSTIC (rooms + footprint); this class supplies the PALETTE
 * (wall material / roof material+shape / accent / floor) so the generated building stays recognisably
 * japanese / byzantine / mayan / norman / …, matching the sim's {@code STYLE} table.
 *
 * <p><b>It is genuinely EXTRACTED</b>, not hard-coded: {@link #extract(Culture)} ANALYSES the culture's
 * already-loaded building plans ({@link Culture#ListPlanSets} → {@link BuildingPlan#plan} block grids)
 * and counts block frequency to find the culture's dominant solid WALL block, its dominant STAIR/SLAB
 * ROOF block, and a distinctive ACCENT — exactly "derive the per-culture style table by analysing the
 * culture's existing building plans + blocklist". A curated fallback table (keyed by {@code culture.key},
 * identical to buildsim.py) is used only when a culture has no analysable plans loaded (e.g. headless
 * tests), so the system always produces a sensible, culture-recognisable palette.
 */
public final class MillCultureStyle {

   /** A roof's geometric form, chosen per culture (the sim's {@code roofShape}). */
   public enum RoofShape {
      GABLE, HIP, FLAT, DOME
   }

   /** The extracted palette applied to a procedural layout. */
   public static final class Style {
      public final String culture;
      public final BlockState wall;
      public final BlockState roof;        // a stair/slab/solid used for the roof skin
      public final BlockState accent;      // trim / corners
      public final BlockState floor;
      public final RoofShape roofShape;
      public final String source;          // "extracted" or "curated-fallback"

      Style(String culture, BlockState wall, BlockState roof, BlockState accent, BlockState floor,
            RoofShape roofShape, String source) {
         this.culture = culture;
         this.wall = wall;
         this.roof = roof;
         this.accent = accent;
         this.floor = floor;
         this.roofShape = roofShape;
         this.source = source;
      }

      public String describe() {
         return "wall=" + blockName(wall) + " roof=" + blockName(roof) + "(" + roofShape.name().toLowerCase() + ")"
            + " accent=" + blockName(accent) + " floor=" + blockName(floor) + " [" + source + "]";
      }
   }

   private MillCultureStyle() {
   }

   private static final Map<String, Style> CACHE = new HashMap<>();

   /** Extract (and cache) the culture's style. Falls back to the curated table if no plans are analysable. */
   public static Style extract(Culture culture) {
      String key = culture == null ? "norman" : culture.key;
      Style cached = CACHE.get(key);
      if (cached != null) {
         return cached;
      }
      Style s = analyse(culture, key);
      if (s == null) {
         s = curated(key);
      }
      CACHE.put(key, s);
      return s;
   }

   /** Style for a culture key alone (curated table) — used by headless tests + cultures with no plans. */
   public static Style forKey(String key) {
      Style cached = CACHE.get(key);
      if (cached != null) {
         return cached;
      }
      Style s = curated(key);
      CACHE.put(key, s);
      return s;
   }

   // -------------------------------------------------------------------------------------------------
   // EXTRACTION — analyse the culture's actual building-plan block grids for its dominant materials.
   // -------------------------------------------------------------------------------------------------

   private static Style analyse(Culture culture, String key) {
      if (culture == null || culture.ListPlanSets == null || culture.ListPlanSets.isEmpty()) {
         return null;
      }
      Map<Block, Integer> solidCounts = new HashMap<>();   // candidate walls (full opaque cubes)
      Map<Block, Integer> roofCounts = new HashMap<>();    // candidate roofs (stairs/slabs)
      int plansScanned = 0;

      for (BuildingPlanSet set : culture.ListPlanSets) {
         if (set == null || set.plans == null) {
            continue;
         }
         for (BuildingPlan[] variation : set.plans) {
            if (variation == null) {
               continue;
            }
            for (BuildingPlan plan : variation) {
               if (plan == null || plan.plan == null) {
                  continue;
               }
               if (plansScanned >= 40) {
                  break; // bounded scan — 40 plans is more than enough to find the dominant palette.
               }
               plansScanned++;
               countPlanBlocks(plan, solidCounts, roofCounts);
            }
         }
      }
      if (plansScanned == 0 || solidCounts.isEmpty()) {
         return null;
      }

      Block wallBlock = topBlock(solidCounts);
      Block roofBlock = roofCounts.isEmpty() ? null : topBlock(roofCounts);
      if (wallBlock == null) {
         return null;
      }
      BlockState wall = wallBlock.defaultBlockState();
      // Roof: prefer the extracted dominant stair/slab; else a curated roof for the culture.
      BlockState roof = roofBlock != null ? roofBlock.defaultBlockState() : curated(key).roof;
      // Accent: second-most-common solid that differs from the wall (the trim the culture uses).
      Block accentBlock = secondBlock(solidCounts, wallBlock);
      BlockState accent = accentBlock != null ? accentBlock.defaultBlockState() : curated(key).accent;
      BlockState floor = curated(key).floor;
      RoofShape shape = curated(key).roofShape; // shape is a stylistic CHOICE per culture, kept curated.

      return new Style(key, wall, roof, accent, floor, shape, "extracted(" + plansScanned + " plans)");
   }

   private static void countPlanBlocks(BuildingPlan plan, Map<Block, Integer> solid, Map<Block, Integer> roof) {
      PointType[][][] grid = plan.plan;
      for (PointType[][] layer : grid) {
         if (layer == null) {
            continue;
         }
         for (PointType[] row : layer) {
            if (row == null) {
               continue;
            }
            for (PointType pt : row) {
               if (pt == null) {
                  continue;
               }
               Block b;
               try {
                  b = pt.getBlock();
               } catch (Throwable t) {
                  continue;
               }
               if (b == null || b == Blocks.AIR) {
                  continue;
               }
               if (b instanceof StairBlock || b instanceof SlabBlock) {
                  roof.merge(b, 1, Integer::sum);
               } else if (isWallCandidate(b)) {
                  solid.merge(b, 1, Integer::sum);
               }
            }
         }
      }
   }

   /** A block usable as a structural wall: a full, buildable, non-decorative material. */
   private static boolean isWallCandidate(Block b) {
      BlockState st = b.defaultBlockState();
      // Exclude ground/decoration/utility blocks so the dominant WALL material surfaces.
      if (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.WATER || b == Blocks.LAVA
         || b == Blocks.FARMLAND || b == Blocks.GRAVEL || b == Blocks.SAND) {
         return false;
      }
      try {
         return st.isSolidRender() && b.asItem() != net.minecraft.world.item.Items.AIR;
      } catch (Throwable t) {
         return false;
      }
   }

   private static Block topBlock(Map<Block, Integer> counts) {
      Block best = null;
      int bestN = -1;
      for (Map.Entry<Block, Integer> e : counts.entrySet()) {
         if (e.getValue() > bestN) {
            bestN = e.getValue();
            best = e.getKey();
         }
      }
      return best;
   }

   private static Block secondBlock(Map<Block, Integer> counts, Block exclude) {
      Block best = null;
      int bestN = -1;
      for (Map.Entry<Block, Integer> e : counts.entrySet()) {
         if (e.getKey() == exclude) {
            continue;
         }
         if (e.getValue() > bestN) {
            bestN = e.getValue();
            best = e.getKey();
         }
      }
      return best;
   }

   // -------------------------------------------------------------------------------------------------
   // CURATED FALLBACK — keyed by culture.key, identical materials/shape choices to buildsim.py STYLE.
   // -------------------------------------------------------------------------------------------------

   private static final Map<String, Style> CURATED = buildCurated();

   private static Style curated(String key) {
      Style s = CURATED.get(key);
      if (s != null) {
         return s;
      }
      return CURATED.get("norman");
   }

   private static Map<String, Style> buildCurated() {
      Map<String, Style> m = new LinkedHashMap<>();
      // norman: oak planks walls, stone-brick gable roof, cobble accent.
      m.put("norman", new Style("norman",
         Blocks.OAK_PLANKS.defaultBlockState(),
         Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
         Blocks.COBBLESTONE.defaultBlockState(),
         Blocks.OAK_PLANKS.defaultBlockState(),
         RoofShape.GABLE, "curated-fallback"));
      // japanese: spruce walls, dark-oak hip roof, white-wool accent.
      m.put("japanese", new Style("japanese",
         Blocks.SPRUCE_PLANKS.defaultBlockState(),
         Blocks.DARK_OAK_STAIRS.defaultBlockState(),
         Blocks.DIORITE.defaultBlockState(),
         Blocks.SPRUCE_PLANKS.defaultBlockState(),
         RoofShape.HIP, "curated-fallback"));
      // mayan: smooth sandstone walls, sandstone-slab flat roof, chiseled-sandstone accent.
      m.put("mayan", new Style("mayan",
         Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
         Blocks.SANDSTONE_SLAB.defaultBlockState(),
         Blocks.CHISELED_SANDSTONE.defaultBlockState(),
         Blocks.SANDSTONE.defaultBlockState(),
         RoofShape.FLAT, "curated-fallback"));
      // byzantines: stone-brick walls, brick-stair dome roof, gold accent.
      m.put("byzantines", new Style("byzantines",
         Blocks.STONE_BRICKS.defaultBlockState(),
         Blocks.BRICK_STAIRS.defaultBlockState(),
         Blocks.GOLD_BLOCK.defaultBlockState(),
         Blocks.STONE_BRICKS.defaultBlockState(),
         RoofShape.DOME, "curated-fallback"));
      // indian: a warm sandstone/brick palette (gives the remaining Mill culture a recognisable look).
      m.put("indian", new Style("indian",
         Blocks.BRICKS.defaultBlockState(),
         Blocks.BRICK_STAIRS.defaultBlockState(),
         Blocks.RED_SANDSTONE.defaultBlockState(),
         Blocks.BRICKS.defaultBlockState(),
         RoofShape.HIP, "curated-fallback"));
      // inuit: snow/ice/spruce.
      m.put("inuit", new Style("inuit",
         Blocks.PACKED_ICE.defaultBlockState(),
         Blocks.SNOW_BLOCK.defaultBlockState(),
         Blocks.SPRUCE_PLANKS.defaultBlockState(),
         Blocks.PACKED_ICE.defaultBlockState(),
         RoofShape.HIP, "curated-fallback"));
      return m;
   }

   private static String blockName(BlockState s) {
      try {
         return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
      } catch (Throwable t) {
         return String.valueOf(s.getBlock());
      }
   }
}
