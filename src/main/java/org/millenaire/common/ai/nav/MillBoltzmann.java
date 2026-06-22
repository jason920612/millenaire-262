package org.millenaire.common.ai.nav;

import net.minecraft.util.RandomSource;

/**
 * Boltzmann (softmin) action selection for tactical combat moves: pick action {@code i} with probability
 * proportional to {@code exp(-Q[i]/τ)}. Low-cost moves are favoured but the choice stays RANDOM among
 * near-equal options — so a villager strafes unpredictably (an archer can't lead the shot) instead of the
 * hand-written "flip side every N ticks". Temperature τ tunes greedy (→0: argmin) vs exploratory (large).
 */
public final class MillBoltzmann {
   private MillBoltzmann() {
   }

   /** @return the chosen index into {@code q}, sampled by P(i) ∝ exp(-q[i]/τ). */
   public static int pick(double[] q, double temperature, RandomSource rng) {
      if (q.length == 0) {
         return -1;
      }
      double tau = Math.max(1e-6, temperature);
      double min = Double.POSITIVE_INFINITY;
      for (double v : q) {
         if (v < min) {
            min = v;
         }
      }
      double[] w = new double[q.length];
      double sum = 0.0;
      for (int i = 0; i < q.length; i++) {
         w[i] = Math.exp(-(q[i] - min) / tau); // subtract min for numerical stability
         sum += w[i];
      }
      double r = rng.nextDouble() * sum;
      for (int i = 0; i < q.length; i++) {
         r -= w[i];
         if (r <= 0.0) {
            return i;
         }
      }
      return q.length - 1;
   }
}
