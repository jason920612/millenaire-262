package org.millenaire.common.ai.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Phase 0: the lexicographic objective must order tiers correctly and accumulate component-wise. */
class LexCostTest {

   @Test
   void normalBeatsRepairRegardlessOfScalar() {
      // A long pure-walking path must beat a short path that places/breaks a block — repair is a LAST resort
      // (a tier above distance), not merely "expensive".
      assertTrue(LexCost.normal(100.0).compareTo(LexCost.repair(1.0)) < 0);
   }

   @Test
   void illegalDominatesEverything() {
      assertTrue(LexCost.normal(1000.0).compareTo(new LexCost(1, 0, 0, 0.0)) < 0);
   }

   @Test
   void lethalRiskBeatsDistance() {
      LexCost safeFar = new LexCost(0, 0, 0, 50.0);
      LexCost riskyNear = new LexCost(0, 0, 1, 1.0);
      assertTrue(safeFar.compareTo(riskyNear) < 0);
   }

   @Test
   void scalarBreaksTiesLast() {
      assertTrue(LexCost.normal(1.0).compareTo(LexCost.normal(2.0)) < 0);
      assertEquals(0, LexCost.normal(5.0).compareTo(LexCost.normal(5.0)));
   }

   @Test
   void plusAccumulatesComponentwise() {
      LexCost sum = new LexCost(0, 1, 0, 2.0).plus(new LexCost(0, 0, 1, 3.0));
      assertEquals(0, sum.illegal);
      assertEquals(1, sum.repairUsed);
      assertEquals(1, sum.hardRisk);
      assertEquals(5.0, sum.scalarCost, 1e-9);
   }

   @Test
   void infiniteAbsorbs() {
      assertEquals(LexCost.INFINITE, LexCost.INFINITE.plus(LexCost.normal(1.0)));
      assertTrue(LexCost.normal(1e9).compareTo(LexCost.INFINITE) < 0);
   }
}
