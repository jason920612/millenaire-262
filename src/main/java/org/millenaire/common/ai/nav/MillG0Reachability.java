package org.millenaire.common.ai.nav;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The feasibility certificate that decides G0 vs G1: a BOUNDED breadth-first flood over normal (G0) moves from
 * the villager. If the reachable G0 region is fully enclosed within {@code maxNodes} cells, the villager is
 * TRAPPED and the last-resort G1 (place/break) repair is justified; otherwise it can move normally and no
 * terrain modification is allowed. This is what makes place/break a staged-feasibility fallback rather than a
 * cost — and it's cheap (bounded, local), so it can run on the rare stuck trigger without hurting TPS.
 */
public final class MillG0Reachability {
   private MillG0Reachability() {
   }

   /**
    * @return {@code true} if the villager is NOT trapped — it either reaches {@code goal} (when non-null) or
    *         its open G0 region exceeds {@code maxNodes} cells. {@code false} if its reachable G0 region is a
    *         small enclosure (a pit/box) → trapped, G1 repair warranted.
    */
   public static boolean feasible(Level level, EdgeCostProvider costs, BlockPos from, BlockPos goal, int maxNodes) {
      Set<Long> seen = new HashSet<>();
      ArrayDeque<BlockPos> queue = new ArrayDeque<>();
      queue.add(from);
      seen.add(from.asLong());
      int explored = 0;
      while (!queue.isEmpty() && explored < maxNodes) {
         BlockPos cur = queue.poll();
         explored++;
         if (goal != null && cur.closerThan(goal, 1.5)) {
            return true; // a normal route to the goal exists
         }
         for (BlockPos nb : costs.neighbors(level, cur)) {
            if (seen.add(nb.asLong())) {
               queue.add(nb);
            }
         }
      }
      // Hit the cap with cells still to expand ⇒ open region ⇒ not trapped. Queue drained early ⇒ enclosed.
      return explored >= maxNodes;
   }
}
