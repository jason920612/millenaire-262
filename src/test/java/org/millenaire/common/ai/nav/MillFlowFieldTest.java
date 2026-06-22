package org.millenaire.common.ai.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

/** Phase 3: the reverse-Dijkstra flow field must give cost-to-go = distance on an open grid and a gradient
 *  that always steps closer to the goal — validated against a synthetic grid, no Minecraft world needed. */
class MillFlowFieldTest {

   /** A flat open grid [0..n]×[0..n] at y=0, 4-cardinal, uniform unit cost. Ignores the (null) Level. */
   private static EdgeCostProvider grid(int n) {
      return new EdgeCostProvider() {
         @Override
         public List<BlockPos> neighbors(Level level, BlockPos p) {
            List<BlockPos> out = new ArrayList<>(4);
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dd : d) {
               int x = p.getX() + dd[0];
               int z = p.getZ() + dd[1];
               if (x >= 0 && x <= n && z >= 0 && z <= n) {
                  out.add(new BlockPos(x, 0, z));
               }
            }
            return out;
         }

         @Override
         public LexCost edgeCost(Level level, BlockPos from, BlockPos to) {
            return LexCost.normal(1.0);
         }
      };
   }

   @Test
   void costToGoEqualsManhattanDistanceOnOpenGrid() {
      MillFlowField f = MillFlowField.build(null, grid(4), new BlockPos(0, 0, 0), 1000);
      assertEquals(0.0, f.costToGo(new BlockPos(0, 0, 0)).scalarCost, 1e-9);
      assertEquals(3.0, f.costToGo(new BlockPos(3, 0, 0)).scalarCost, 1e-9);
      assertEquals(4.0, f.costToGo(new BlockPos(2, 0, 2)).scalarCost, 1e-9);
   }

   @Test
   void nextStepAlwaysDescendsTowardGoal() {
      MillFlowField f = MillFlowField.build(null, grid(4), new BlockPos(0, 0, 0), 1000);
      BlockPos here = new BlockPos(4, 0, 4);
      for (int i = 0; i < 12 && !here.equals(new BlockPos(0, 0, 0)); i++) {
         BlockPos next = f.nextStep(here);
         assertTrue(next != null, "every covered non-goal cell has a next step");
         assertTrue(f.costToGo(next).scalarCost < f.costToGo(here).scalarCost, "each step strictly descends");
         here = next;
      }
      assertEquals(new BlockPos(0, 0, 0), here, "gradient descent reaches the goal");
   }

   @Test
   void unreachableCellIsInfinite() {
      MillFlowField f = MillFlowField.build(null, grid(4), new BlockPos(0, 0, 0), 1000);
      assertEquals(LexCost.INFINITE, f.costToGo(new BlockPos(99, 0, 99)));
   }
}
