package org.millenaire.common.ai.nav;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.ai.MillInfluenceGrid;

/**
 * Phase 1 concrete {@link EdgeCostProvider}: the ONE place "what a move costs" lives, priced in equivalent
 * walk-blocks. Cost = base move (≈1 cardinal / √2 diagonal) + danger (from the village {@link MillInfluenceGrid})
 * + a small drop penalty (a downward step is a minor fall and harder to reverse — climbing UP is free so
 * villagers don't avoid hills). This reproduces today's MillNodeEvaluator cost exactly, so wiring it in is a
 * pure refactor (regression-validated), and later phases reuse the very same cost for our own search.
 */
public final class MillEdgeCostProvider implements EdgeCostProvider {
   private MillInfluenceGrid influence = MillInfluenceGrid.empty();
   private float dangerWeight = 1.0F;
   private float dropPenalty = 0.3F;

   public void configure(MillInfluenceGrid influence, float dangerWeight, float dropPenalty) {
      this.influence = influence == null ? MillInfluenceGrid.empty() : influence;
      this.dangerWeight = dangerWeight;
      this.dropPenalty = dropPenalty;
   }

   /** The cost-malus ADD-ON (danger + drop) beyond the base move — what the vanilla NodeEvaluator's costMalus
    *  wants in Phase 1 (vanilla already charges the base distance via g+distance). */
   public float malus(int x, int z, int dy) {
      float m = 0.0F;
      float danger = this.influence.dangerAt(x, z);
      if (danger > 0.0F) {
         m += this.dangerWeight * danger;
      }
      if (dy < 0) {
         m += this.dropPenalty * (-dy);
      }
      return m;
   }

   @Override
   public LexCost edgeCost(Level level, BlockPos from, BlockPos to) {
      double dx = to.getX() - from.getX();
      double dz = to.getZ() - from.getZ();
      double base = Math.sqrt(dx * dx + dz * dz);
      if (base < 0.01) {
         base = 1.0; // pure vertical move
      }
      return LexCost.normal(base + malus(to.getX(), to.getZ(), to.getY() - from.getY()));
   }

   @Override
   public List<BlockPos> neighbors(Level level, BlockPos from) {
      // G0 ground neighbours: 8 horizontal directions, each tried at the same Y and ±1 Y (step up / drop one),
      // keeping only standable cells. (A richer G0 model — multi-block drops, jumps, ladders, swim — lands in
      // a later phase; this is enough for Phase 1's cost-centralisation + the local controller groundwork.)
      List<BlockPos> out = new ArrayList<>(8);
      int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
      for (int[] d : dirs) {
         for (int dy = 1; dy >= -1; dy--) {
            BlockPos c = from.offset(d[0], dy, d[1]);
            if (standable(level, c)) {
               out.add(c);
               break; // first standable Y in this column
            }
         }
      }
      return out;
   }

   private static boolean standable(Level level, BlockPos foot) {
      BlockState ground = level.getBlockState(foot.below());
      BlockState at = level.getBlockState(foot);
      BlockState head = level.getBlockState(foot.above());
      return ground.isSolid() && at.getFluidState().isEmpty() && at.isAir() && head.isAir();
   }
}
