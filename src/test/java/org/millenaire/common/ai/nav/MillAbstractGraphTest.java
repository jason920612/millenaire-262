package org.millenaire.common.ai.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/** Phase 4: the HPA* high-level abstract graph must return the lexicographic-shortest waypoint route. */
class MillAbstractGraphTest {

   @Test
   void picksLowerCostRoute() {
      BlockPos a = new BlockPos(0, 0, 0);
      BlockPos b = new BlockPos(1, 0, 0);
      BlockPos c = new BlockPos(0, 0, 1);
      BlockPos d = new BlockPos(1, 0, 1);
      MillAbstractGraph g = new MillAbstractGraph();
      g.addBidirectional(a, b, LexCost.normal(1)); // A-B-D = 2
      g.addBidirectional(b, d, LexCost.normal(1));
      g.addBidirectional(a, c, LexCost.normal(1)); // A-C-D = 6
      g.addBidirectional(c, d, LexCost.normal(5));
      assertEquals(List.of(a, b, d), g.shortestPath(a, d));
   }

   @Test
   void nullWhenDisconnected() {
      MillAbstractGraph g = new MillAbstractGraph();
      g.addEdge(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), LexCost.normal(1));
      assertNull(g.shortestPath(new BlockPos(0, 0, 0), new BlockPos(9, 0, 9)));
   }

   @Test
   void trivialSameNode() {
      MillAbstractGraph g = new MillAbstractGraph();
      BlockPos a = new BlockPos(3, 0, 3);
      assertEquals(List.of(a), g.shortestPath(a, a));
   }
}
