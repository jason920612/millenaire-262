package org.millenaire.common.ai.behaviours;

import org.millenaire.common.ai.MillBehaviour;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.utilities.Point;

/**
 * Core movement behaviour (req 7): drive the villager toward its OWN Mill goal/task point (the destination
 * set by the existing Mill goal system). Uses the navigation (terrain + safety + jump cost via
 * {@link org.millenaire.common.ai.MillNodeEvaluator}); on an unreachable target it FAILS gracefully — never
 * teleports (req 1) — so the decision/goal layer can re-plan or abandon.
 */
public final class BehaviourGoToPoint implements MillBehaviour {
   private static final double SPEED = 0.5;
   /** How close (blocks) counts as "arrived". */
   private static final double ARRIVE = 1.6;

   @Override
   public boolean canRun(MillVillager villager) {
      return dest(villager) != null;
   }

   @Override
   public int priority(MillVillager villager) {
      return 10; // above idle wander, below combat
   }

   @Override
   public boolean tick(MillVillager villager) {
      Point d = dest(villager);
      if (d == null) {
         return false;
      }
      double dist = villager.getPos().distanceTo(d);
      if (dist <= ARRIVE) {
         villager.getNavigation().stop();
         return false; // arrived → done
      }
      // (Re)issue a path only when idle; if the planner can't reach it, fail gracefully (no warp).
      if (villager.getNavigation().isDone()) {
         boolean pathed = villager.getNavigation().moveTo(d.getiX() + 0.5, d.getiY(), d.getiZ() + 0.5, SPEED);
         if (!pathed) {
            return false; // unreachable — abandon, the goal layer decides what next
         }
      }
      return true;
   }

   @Override
   public void onStop(MillVillager villager) {
      villager.getNavigation().stop();
   }

   private static Point dest(MillVillager villager) {
      Point p = villager.getPathDestPoint();
      return p != null ? p : villager.getGoalDestPoint();
   }
}
