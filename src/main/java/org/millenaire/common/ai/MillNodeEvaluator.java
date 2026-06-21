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
   private MillInfluenceGrid influence = MillInfluenceGrid.empty();
   private float dangerWeight = 1.0F;
   private float jumpPenalty = 2.0F;

   /** Set the per-village danger field + tuning weights for the next path computation. */
   public void configure(MillInfluenceGrid influence, float dangerWeight, float jumpPenalty) {
      this.influence = influence == null ? MillInfluenceGrid.empty() : influence;
      this.dangerWeight = dangerWeight;
      this.jumpPenalty = jumpPenalty;
   }

   @Override
   public int getNeighbors(Node[] neighbors, Node node) {
      int count = super.getNeighbors(neighbors, node);
      for (int i = 0; i < count; i++) {
         Node nb = neighbors[i];
         // req 4 — danger field penalty (stay away from hostiles/hazards).
         float danger = this.influence.dangerAt(nb.x, nb.z);
         if (danger > 0.0F) {
            nb.costMalus += this.dangerWeight * danger;
         }
         // req 9 — gently discourage DROPS only (a step down is a small fall + harder to reverse). Do NOT
         // penalise climbing UP: vanilla already gates jump reachability, and penalising every step-up made
         // hills expensive so the A* spent its node budget on flat routes and never found the climb →
         // villagers got stuck going uphill. Climbing is now free; only downward steps cost a little.
         if (nb.y < node.y) {
            nb.costMalus += this.jumpPenalty;
         }
      }
      return count;
   }
}
