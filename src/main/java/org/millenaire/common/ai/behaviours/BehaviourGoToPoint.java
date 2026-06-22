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
   private net.minecraft.core.BlockPos detour; // side waypoint to re-route around a stuck spot
   private int detourTicks;
   private int detourSide = 1;

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
      // Re-routing: if we're escaping a stuck spot, go to the side waypoint first, then resume direct pathing.
      // We KEEP the Mill task and just plan a NEW ROUTE — we never abandon/override the goal.
      if (this.detour != null) {
         if (--this.detourTicks <= 0 || villager.blockPosition().closerThan(this.detour, 2.0)) {
            this.detour = null;
            villager.getNavigation().stop();
         } else {
            if (villager.getNavigation().isDone()) {
               villager.getNavigation().moveTo(this.detour.getX() + 0.5, this.detour.getY(), this.detour.getZ() + 0.5, SPEED);
            }
            return true;
         }
      }
      double dist = villager.getPos().distanceTo(d);
      if (dist <= ARRIVE) {
         villager.getNavigation().stop();
         return false; // arrived → done
      }
      // (Re)issue movement only when idle. Use the navigation's OWN A* (MillPathNavigation + the danger-aware
      // MillNodeEvaluator, which extends vanilla WalkNodeEvaluator): it has full 3D awareness (step-ups,
      // in-reach jumps, climbs, going around obstacles) and explores all routes for the optimal one. The
      // flow-field cell-by-cell follow was cruder (±1 Y horizontal neighbours only) so it got blocked easily;
      // the field stays as infrastructure (escape/long-range) but normal movement goes through the real A*.
      if (villager.getNavigation().isDone()) {
         boolean pathed = villager.getNavigation().moveTo(d.getiX() + 0.5, d.getiY(), d.getiZ() + 0.5, SPEED);
         if (!pathed) {
            startDetour(villager, d); // genuinely can't path there → plan a new route around the blockage
            return true;
         }
      }
      // Stuck detection: no progress → drop the stuck path and re-pathfind; if still stuck, RE-ROUTE via a
      // side waypoint to get around the blockage (keeping the goal — never clearGoal).
      double mx = villager.getX() - this.lastX;
      double mz = villager.getZ() - this.lastZ;
      if (!Double.isNaN(this.lastX) && mx * mx + mz * mz < 0.0025) {
         this.stuckTicks++;
         if (this.stuckTicks == 60) {
            villager.getNavigation().stop();
         } else if (this.stuckTicks > 100) {
            this.stuckTicks = 0;
            startDetour(villager, d);
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
      this.detour = null;
   }

   /**
    * Plan a NEW ROUTE around a blockage: aim for a reachable side waypoint ~5 blocks perpendicular to the
    * goal direction (alternating sides each attempt) so the villager breaks out of the local dead-end, then
    * resumes pathing to the SAME goal. The Mill task/goal is preserved — we only change the route.
    */
   private void startDetour(MillVillager villager, Point goal) {
      double dx = goal.getiX() - villager.getX();
      double dz = goal.getiZ() - villager.getZ();
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 0.01) {
         return;
      }
      this.detourSide = -this.detourSide; // alternate left/right between attempts
      double px = -dz / len * this.detourSide * 5.0;
      double pz = dx / len * this.detourSide * 5.0;
      this.detour = net.minecraft.core.BlockPos.containing(villager.getX() + px, villager.getY(), villager.getZ() + pz);
      this.detourTicks = 80; // ~4s to reach the side waypoint before resuming direct pathing
      villager.getNavigation().moveTo(this.detour.getX() + 0.5, this.detour.getY(), this.detour.getZ() + 0.5, SPEED);
   }

   private static Point dest(MillVillager villager) {
      Point p = villager.getPathDestPoint();
      return p != null ? p : villager.getGoalDestPoint();
   }
}
