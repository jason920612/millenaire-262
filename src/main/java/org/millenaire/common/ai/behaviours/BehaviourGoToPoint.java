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
   private double lastX = Double.NaN;
   private double lastZ;
   private int stuckTicks;

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
      // Stuck detection: when a large obstacle / dead-end path stops progress, force a FRESH re-path (the
      // navigation otherwise keeps following its stuck path); if still stuck after a while, give up so the
      // goal layer re-plans instead of standing forever.
      double mx = villager.getX() - this.lastX;
      double mz = villager.getZ() - this.lastZ;
      if (!Double.isNaN(this.lastX) && mx * mx + mz * mz < 0.0025) {
         this.stuckTicks++;
         if (this.stuckTicks == 60) {
            villager.getNavigation().stop(); // drop the stuck path → re-pathfind next tick (now finds climbs)
         } else if (this.stuckTicks > 140) {
            this.stuckTicks = 0;
            return false; // genuinely stuck ~7s → abandon, let the goal layer re-plan
         }
      } else {
         this.stuckTicks = 0;
      }
      this.lastX = villager.getX();
      this.lastZ = villager.getZ();
      return true;
   }

   @Override
   public void onStop(MillVillager villager) {
      villager.getNavigation().stop();
      this.lastX = Double.NaN;
      this.stuckTicks = 0;
   }

   private static Point dest(MillVillager villager) {
      Point p = villager.getPathDestPoint();
      return p != null ? p : villager.getGoalDestPoint();
   }
}
