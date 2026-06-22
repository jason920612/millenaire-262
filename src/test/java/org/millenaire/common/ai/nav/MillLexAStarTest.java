package org.millenaire.common.ai.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

/** Phase 3: the bounded LexCost A* must return the shortest G0 path and null when the goal is unreachable. */
class MillLexAStarTest {

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
   void findsShortestStraightPath() {
      List<BlockPos> path = MillLexAStar.findPath(null, grid(5), new BlockPos(0, 0, 0), new BlockPos(5, 0, 0), 5000);
      assertTrue(path != null, "path found");
      assertEquals(new BlockPos(0, 0, 0), path.get(0), "starts at start");
      assertTrue(path.get(path.size() - 1).closerThan(new BlockPos(5, 0, 0), 1.0), "ends at goal");
      assertEquals(6, path.size(), "shortest path along a row of 6 cells");
   }

   @Test
   void nullWhenGoalUnreachable() {
      assertNull(MillLexAStar.findPath(null, grid(5), new BlockPos(0, 0, 0), new BlockPos(99, 0, 99), 5000));
   }
}
