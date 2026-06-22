package org.millenaire.common.ai.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;

import org.junit.jupiter.api.Test;

/** Phase 5: Boltzmann action selection — greedy at low τ, favours low cost, random among equals. */
class MillBoltzmannTest {

   @Test
   void lowTemperaturePicksArgmin() {
      double[] q = {5.0, 1.0, 3.0}; // cheapest = index 1
      RandomSource rng = RandomSource.create(42);
      for (int i = 0; i < 20; i++) {
         assertEquals(1, MillBoltzmann.pick(q, 0.001, rng));
      }
   }

   @Test
   void favoursLowerCostOverManySamples() {
      double[] q = {0.0, 10.0};
      RandomSource rng = RandomSource.create(7);
      int count0 = 0;
      for (int i = 0; i < 1000; i++) {
         if (MillBoltzmann.pick(q, 1.0, rng) == 0) {
            count0++;
         }
      }
      assertTrue(count0 > 900, "the much cheaper action dominates, count0=" + count0);
   }

   @Test
   void roughlyUniformWhenEqual() {
      double[] q = {2.0, 2.0, 2.0};
      RandomSource rng = RandomSource.create(3);
      int[] counts = new int[3];
      for (int i = 0; i < 3000; i++) {
         counts[MillBoltzmann.pick(q, 1.0, rng)]++;
      }
      for (int c : counts) {
         assertTrue(c > 700 && c < 1300, "equal costs → roughly uniform, got " + c);
      }
   }

   @Test
   void emptyReturnsMinusOne() {
      assertEquals(-1, MillBoltzmann.pick(new double[0], 1.0, RandomSource.create(1)));
   }
}
