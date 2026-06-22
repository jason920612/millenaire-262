package org.millenaire.common.ai.nav;

/**
 * The ONE objective of the value-field navigation rewrite: a lexicographic, multi-cost path value. Compared
 * tier by tier (lower is better) so that, e.g., "never use a repair edge while a normal path exists" and
 * "lethal risk dominates distance" are expressed as ORDERING, not as huge magic cost magnitudes.
 *
 * <p>Tiers, most significant first:
 * <ol>
 *   <li>{@link #illegal} — count of illegal edges (forbidden zones); any legal path beats any illegal one.</li>
 *   <li>{@link #repairUsed} — count of place/break (G1) edges; a pure-G0 path (0) always wins. This is what
 *       keeps block modification a LAST RESORT — it is a tier above distance, not a big number.</li>
 *   <li>{@link #hardRisk} — count of lethal-risk edges crossed (lava/deadly drop); fewer first.</li>
 *   <li>{@link #scalarCost} — the soft blend, all in "equivalent walk-blocks":
 *       {@code τ + α·danger + β·fallRisk + γ·effort + δ·congestion}.</li>
 * </ol>
 *
 * <p>All tiers are non-negative and additive, so a path's cost is the component-wise sum of its edges and the
 * lexicographic order is consistent — i.e. standard (vector-cost) Dijkstra/A* stays correct.
 */
public final class LexCost implements Comparable<LexCost> {
   public static final LexCost ZERO = new LexCost(0, 0, 0, 0.0);
   /** An unusable edge (kept as a value so callers can compare rather than special-case null). */
   public static final LexCost INFINITE = new LexCost(Integer.MAX_VALUE, 0, 0, Double.POSITIVE_INFINITY);

   public final int illegal;
   public final int repairUsed;
   public final int hardRisk;
   public final double scalarCost;

   public LexCost(int illegal, int repairUsed, int hardRisk, double scalarCost) {
      this.illegal = illegal;
      this.repairUsed = repairUsed;
      this.hardRisk = hardRisk;
      this.scalarCost = scalarCost;
   }

   /** A normal (G0) edge: just a scalar walk-block cost, no illegal/repair/lethal flags. */
   public static LexCost normal(double scalarCost) {
      return new LexCost(0, 0, 0, scalarCost);
   }

   /** A repair (G1) edge: counts as one block modification, plus its scalar cost. */
   public static LexCost repair(double scalarCost) {
      return new LexCost(0, 1, 0, scalarCost);
   }

   /** Component-wise sum — accumulate this edge onto a running path cost. */
   public LexCost plus(LexCost edge) {
      if (this == INFINITE || edge == INFINITE) {
         return INFINITE;
      }
      return new LexCost(
         this.illegal + edge.illegal,
         this.repairUsed + edge.repairUsed,
         this.hardRisk + edge.hardRisk,
         this.scalarCost + edge.scalarCost);
   }

   @Override
   public int compareTo(LexCost o) {
      if (this.illegal != o.illegal) {
         return Integer.compare(this.illegal, o.illegal);
      }
      if (this.repairUsed != o.repairUsed) {
         return Integer.compare(this.repairUsed, o.repairUsed);
      }
      if (this.hardRisk != o.hardRisk) {
         return Integer.compare(this.hardRisk, o.hardRisk);
      }
      return Double.compare(this.scalarCost, o.scalarCost);
   }

   @Override
   public String toString() {
      return "Lex(illegal=" + illegal + ", repair=" + repairUsed + ", risk=" + hardRisk
         + ", cost=" + String.format("%.2f", scalarCost) + ")";
   }
}
