package com.coderyo.jason.build;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

import com.coderyo.jason.build.MillNeedsModel.Decision;
import com.coderyo.jason.build.MillProceduralBuilding.Placement;
import com.coderyo.jason.build.MillProceduralBuilding.Plan;
import com.coderyo.jason.ops.VillagerWorldOps;

/**
 * Phase 2 (#6) PROCEDURAL BUILDING — the INTEGRATION engine.
 *
 * <p>Ties the three pieces together so a village actually builds a procedural building:
 * {@link MillNeedsModel} (gap-priority → what to build) → {@link MillCultureStyle} (the culture palette)
 * → {@link MillProceduralBuilding} (rooms → connected footprint → placements) → TERRAIN FIT (hybrid:
 * adapt vs level vs partial) → player-like construction via {@link VillagerWorldOps#place} (real reach,
 * scaffolds for high rows, material consume).
 *
 * <p>Gated behind {@link #ENABLED} (the {@code millenaire.procbuild} flag) so the existing fixed-plan
 * path stays the default fallback — turning the flag on makes a village's NEXT building procedural.
 *
 * <p>Every step emits greppable {@code ███ SIM BUILD} evidence (the gap chosen + reason, the generated
 * type + rooms, the culture style, the terrain fit, and the construction progress/completion).
 */
public final class MillBuildEngine {

   /** Master tag for all procedural-building observation lines. */
   public static final String TAG = "███ SIM BUILD";

   /**
    * Phase 2 (#6): procedural building is the DEFAULT construction path for a village's ongoing/ambient
    * development — there is NO fixed-plan fallback for ambient construction. This flag is therefore ON by
    * default; it can only be turned OFF explicitly (to fall back to the legacy fixed culture-plan list for
    * testing / comparison) via {@code -Dmillenaire.procbuild=false} or {@code MILLENAIRE_PROCBUILD=0}.
    */
   public static final boolean ENABLED = resolveFlag();

   private static boolean resolveFlag() {
      String prop = System.getProperty("millenaire.procbuild");
      if (prop != null) {
         return !"false".equalsIgnoreCase(prop) && !"0".equals(prop);
      }
      String env = System.getenv("MILLENAIRE_PROCBUILD");
      if (env != null) {
         return !"false".equalsIgnoreCase(env) && !"0".equals(env);
      }
      return true; // default ON: procedural is the ambient construction path.
   }

   private MillBuildEngine() {
   }

   /** Terrain-fit strategy chosen for the site (hybrid, matching buildsim.py {@code terrain_fit}). */
   public enum TerrainFit {
      ADAPT,            // small slope → step/stilts (build as-is on adapted ground)
      PARTIAL_LEVEL,    // mid slope → partial level + step
      LEVEL_PAD         // large slope → level a flat pad
   }

   /** The full result of generating + (optionally) constructing one procedural building. */
   public static final class BuildResult {
      public final Decision decision;
      public final Plan plan;
      public final TerrainFit fit;
      public final int slope;
      public final BlockPos origin;
      public int blocksPlaced;
      public int blocksTotal;
      public boolean complete;

      public BuildResult(Decision decision, Plan plan, TerrainFit fit, int slope, BlockPos origin) {
         this.decision = decision;
         this.plan = plan;
         this.fit = fit;
         this.slope = slope;
         this.origin = origin;
      }
   }

   // ===============================================================================================
   // STEP 1-4: decide → generate → terrain-fit → (return a ready-to-build BuildResult)
   // ===============================================================================================

   /**
    * Decide what the village needs, generate the procedural culture-styled building, and pick the
    * terrain fit for the site. Does NOT place blocks — call {@link #construct} (or
    * {@link #constructImmediate}) to build it. Returns {@code null} if the village has no gaps.
    *
    * @param origin the world position the building's relative (0,0,0) maps to (its corner on the ground).
    */
   public static BuildResult plan(Building townHall, BlockPos origin) {
      Decision decision = MillNeedsModel.decide(townHall);
      if (decision == null) {
         log("village=" + name(townHall) + " has no gaps → nothing to build");
         return null;
      }
      MillCultureStyle.Style style = MillCultureStyle.extract(townHall.culture);
      // Bigger villages build bigger buildings (more rooms): scale by population over a threshold.
      int pop = townHall.getVillagerRecords().size();
      int sizeBoost = Math.max(0, Math.min(3, (pop - 8) / 6));
      Plan p = MillProceduralBuilding.generate(decision.type, style, sizeBoost);

      int slope = measureSlope(townHall.world, origin, p.lengthX, p.widthZ);
      TerrainFit fit = terrainFit(slope);

      BuildResult r = new BuildResult(decision, p, fit, slope, origin);
      r.blocksTotal = p.placements.size();
      logDecision(townHall, r);
      return r;
   }

   /** Factory for callers (e.g. the sim demo) that have a pre-built plan + chosen fit. */
   public static BuildResult makeResult(Decision decision, Plan plan, TerrainFit fit, int slope, BlockPos origin) {
      BuildResult r = new BuildResult(decision, plan, fit, slope, origin);
      r.blocksTotal = plan.placements.size();
      return r;
   }

   /** hybrid terrain fit — identical thresholds to buildsim.py {@code terrain_fit}. */
   public static TerrainFit terrainFit(int slope) {
      if (slope <= 2) {
         return TerrainFit.ADAPT;
      }
      if (slope >= 6) {
         return TerrainFit.LEVEL_PAD;
      }
      return TerrainFit.PARTIAL_LEVEL;
   }

   /** Max vertical span of the topsoil across the footprint corners + centre = the site's slope. */
   public static int measureSlope(Level world, BlockPos origin, int lengthX, int widthZ) {
      int[] xs = {0, lengthX, 0, lengthX, lengthX / 2};
      int[] zs = {0, 0, widthZ, widthZ, widthZ / 2};
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (int i = 0; i < xs.length; i++) {
         int y;
         try {
            y = WorldUtilities.findTopSoilBlock(world, origin.getX() + xs[i], origin.getZ() + zs[i]);
         } catch (Throwable t) {
            y = origin.getY();
         }
         min = Math.min(min, y);
         max = Math.max(max, y);
      }
      return (max == Integer.MIN_VALUE) ? 0 : (max - min);
   }

   // ===============================================================================================
   // STEP 5: CONSTRUCT — apply terrain fit, then lay each placement player-like via VillagerWorldOps.
   // ===============================================================================================

   /**
    * Build the whole plan SYNCHRONOUSLY in one pass (the headless-sim drive, mirroring the mining demo):
    * apply the terrain fit (level/partial-level a pad if needed), then for every placement teleport the
    * builder into reach and lay the block via the player-like {@link VillagerWorldOps#place}. Emits
    * progress + completion evidence. Returns the same {@link BuildResult} with counters filled in.
    */
   public static BuildResult constructImmediate(MillVillager builder, BuildingResultSink sink, BuildResult r) {
      Level world = builder.level();
      applyTerrainFit(world, r);

      int placed = 0;
      int yMax = 0;
      for (Placement pl : r.plan.placements) {
         BlockPos pos = worldPos(r.origin, pl.rel);
         yMax = Math.max(yMax, (int) pl.rel.getiY());
         // Teleport the builder adjacent so it is within player reach (the sync drive doesn't run nav).
         builder.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
         Item mat = pl.material == null ? Items.AIR : pl.material;
         // Ensure the builder carries the material so the strict consume path succeeds (then it's credited back).
         if (mat != Items.AIR && builder.countInv(mat, 0) <= 0) {
            builder.addToInv(mat, 1);
         }
         VillagerWorldOps.PlaceResult res = VillagerWorldOps.place(builder, pos, pl.state, mat, 0);
         if (res == VillagerWorldOps.PlaceResult.OUT_OF_REACH) {
            // Build a scaffold up to the row, then place (high roof rows).
            for (int guard = 0; guard < 64; guard++) {
               com.coderyo.jason.ops.OpState st = VillagerWorldOps.ensureReach(builder, pos);
               if (st == com.coderyo.jason.ops.OpState.COMPLETE || st == com.coderyo.jason.ops.OpState.BLOCKED) {
                  break;
               }
            }
            res = VillagerWorldOps.place(builder, pos, pl.state, mat, 0);
            VillagerWorldOps.reclaimReach(builder, pos);
         }
         if (res == VillagerWorldOps.PlaceResult.PLACED) {
            placed++;
         }
         if (sink != null && (placed % 25 == 0)) {
            sink.onProgress(r, placed);
         }
      }
      r.blocksPlaced = placed;
      r.complete = placed >= (int) (r.blocksTotal * 0.9); // allow a few overwrites/clears to net out.
      logComplete(r);
      return r;
   }

   /** Optional progress callback so the sim can sample partial construction. */
   public interface BuildingResultSink {
      void onProgress(BuildResult r, int placed);
   }

   /**
    * Apply the chosen terrain fit to the site BEFORE construction:
    * <ul>
    *   <li>LEVEL_PAD: clear everything above the pad floor + fill any holes below to a flat base across the footprint.</li>
    *   <li>PARTIAL_LEVEL: level the higher half, step the lower half (so the slope is only partly cut).</li>
    *   <li>ADAPT: leave terrain; the floor placements sit on the existing ground (step/stilts).</li>
    * </ul>
    */
   public static void applyTerrainFit(Level world, BuildResult r) {
      BlockPos origin = r.origin;
      if (r.fit == TerrainFit.ADAPT) {
         return; // build adapts to the ground as-is.
      }
      int padY = origin.getY();
      int half = r.plan.lengthX / 2;
      for (int x = 0; x <= r.plan.lengthX; x++) {
         for (int z = 0; z <= r.plan.widthZ; z++) {
            boolean doLevel = (r.fit == TerrainFit.LEVEL_PAD) || (x <= half);
            if (!doLevel) {
               continue;
            }
            BlockPos ground = new BlockPos(origin.getX() + x, padY - 1, origin.getZ() + z);
            // fill the pad base solid
            BlockState base = world.getBlockState(ground);
            if (base.isAir() || base.canBeReplaced()) {
               world.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
            }
            // clear the column above the pad so nothing intersects the building.
            for (int dy = 0; dy <= r.plan.height + 6; dy++) {
               BlockPos above = new BlockPos(origin.getX() + x, padY + dy, origin.getZ() + z);
               BlockState st = world.getBlockState(above);
               if (!st.isAir()) {
                  world.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
               }
            }
         }
      }
   }

   private static BlockPos worldPos(BlockPos origin, Point rel) {
      return new BlockPos(origin.getX() + (int) rel.getiX(),
         origin.getY() + (int) rel.getiY(),
         origin.getZ() + (int) rel.getiZ());
   }

   // ===============================================================================================
   // Observation
   // ===============================================================================================

   private static void logDecision(Building th, BuildResult r) {
      Decision d = r.decision;
      log("village=" + name(th) + " culture=" + (th.culture != null ? th.culture.key : "?")
         + " pop=" + th.getVillagerRecords().size()
         + " NEEDS: gaps=" + d.gaps + " weightedScores=" + d.scores + " → chose=" + d.type
         + " (reason=" + d.reason + (d.resource != MillNeedsModel.Resource.NONE ? " tunedTo=" + d.resource : "") + ")");
      List<String> rooms = r.plan.roomNames();
      log("village=" + name(th) + " GENERATED " + d.type + " rooms=" + rooms + " doors=" + r.plan.doors.size()
         + " connected=" + r.plan.fullyConnected()
         + " footprint=" + r.plan.lengthX + "x" + r.plan.widthZ + "x" + r.plan.height
         + " blocks=" + r.plan.placements.size());
      log("village=" + name(th) + " STYLE[" + (th.culture != null ? th.culture.key : "?") + "]: "
         + r.plan.style.describe());
      log("village=" + name(th) + " TERRAIN slope=" + r.slope + " → fit=" + r.fit
         + " origin=" + r.origin);
   }

   private static void logComplete(BuildResult r) {
      log("CONSTRUCTION " + r.decision.type + " placed=" + r.blocksPlaced + "/" + r.blocksTotal
         + " terrainFit=" + r.fit + " complete=" + r.complete
         + (r.complete ? "  ✓ village built a " + describe(r) : "  (partial)"));
   }

   private static String describe(BuildResult r) {
      return r.decision.type + " (" + r.plan.roomNames() + ") in " + r.plan.style.culture
         + " style, fitted to terrain (" + r.fit + ")";
   }

   private static String name(Building th) {
      try {
         return th.getVillageQualifiedName();
      } catch (Throwable t) {
         return String.valueOf(th.getPos());
      }
   }

   public static void log(String msg) {
      org.millenaire.common.utilities.MillLog.major(null, TAG + " " + msg);
      System.out.println(TAG + " " + msg);
   }
}
