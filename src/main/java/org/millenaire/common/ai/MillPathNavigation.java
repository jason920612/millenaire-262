package org.millenaire.common.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * Execution layer of the Mill-AI rewrite. A ground navigation that follows a planned path with the
 * multi-objective {@link MillNodeEvaluator} (terrain + danger + jump cost) and NEVER teleports — the old
 * "stuck → warp to destination" recovery is simply not present here; an unreachable target just fails (the
 * decision layer re-plans, waits, or abandons the goal). Vanilla {@code GroundPathNavigation} already moves
 * the mob smoothly toward each node and stops cleanly when it can't progress, which is the no-warp behaviour
 * we want; we only swap in our evaluator and expose per-village cost tuning.
 *
 * <p>Scoped to Mill entities only (req 7): created by the Mill villager's {@code createNavigation()}; nothing
 * global is touched.
 */
public class MillPathNavigation extends net.minecraft.world.entity.ai.navigation.GroundPathNavigation {
   private MillNodeEvaluator millEvaluator;

   public MillPathNavigation(Mob mob, Level level) {
      super(mob, level);
   }

   /** Node budget for the planner — extended well past the vanilla follow-range cap so villagers can path
    *  the long distances a Mill village needs. The multi-objective cost (terrain+danger+jump) keeps the
    *  search sane. (A hierarchical region-graph is the future perf optimisation for very large villages.) */
   private static final int LONG_RANGE_NODES = 4096;

   @Override
   protected PathFinder createPathFinder(int maxVisitedNodes) {
      this.millEvaluator = new MillNodeEvaluator();
      this.nodeEvaluator = this.millEvaluator;
      return new PathFinder(this.nodeEvaluator, Math.max(maxVisitedNodes, LONG_RANGE_NODES));
   }

   /**
    * Feed the current village danger field + cost weights to the evaluator before issuing a path. Call this
    * from the decision tick whenever the village's influence grid is rebuilt.
    */
   public void configureCost(MillInfluenceGrid influence, float dangerWeight, float jumpPenalty) {
      if (this.millEvaluator != null) {
         this.millEvaluator.configure(influence, dangerWeight, jumpPenalty);
      }
   }
}
