package org.millenaire.common.ai;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Multi-objective node evaluator for the Mill pathfinder. Extends the vanilla ground evaluator (which
 * already encodes terrain passability + step/jump reachability — req 2) and ADDS, per neighbour, the extra
 * cost that makes Mill paths SAFE and EASY rather than merely shortest:
 *
 * <ul>
 *   <li><b>Danger (req 4):</b> + {@code dangerWeight × influenceGrid.dangerAt(cell)} — routes detour away
 *       from hostiles / hazards captured in the village {@link MillInfluenceGrid}.</li>
 *   <li><b>Traversal difficulty (req 9):</b> a flat penalty per vertical move (jump up / drop down) so a
 *       flatter, fewer-jumps route wins ties.</li>
 * </ul>
 *
 * The vanilla A* ({@code PathFinder}) sums {@code g + distance + node.costMalus}, so adding to each
 * neighbour's {@code costMalus} is exactly how its cost function is extended.
 */
public class MillNodeEvaluator extends WalkNodeEvaluator {
   /** The single cost authority (Phase 1 of the value-field rewrite): all "what a move costs" logic — danger
    *  field + drop penalty, priced in walk-blocks — now lives in one EdgeCostProvider instead of inline here. */
   private final org.millenaire.common.ai.nav.MillEdgeCostProvider costs = new org.millenaire.common.ai.nav.MillEdgeCostProvider();

   /** Set the per-village danger field + tuning weights for the next path computation. */
   public void configure(MillInfluenceGrid influence, float dangerWeight, float jumpPenalty) {
      this.costs.configure(influence, dangerWeight, jumpPenalty);
   }

   @Override
   public int getNeighbors(Node[] neighbors, Node node) {
      int count = super.getNeighbors(neighbors, node);
      for (int i = 0; i < count; i++) {
         Node nb = neighbors[i];
         // Danger + drop malus now comes from the one EdgeCostProvider (behaviour identical to before).
         nb.costMalus += this.costs.malus(nb.x, nb.z, nb.y - node.y);
      }
      return count;
   }
}
