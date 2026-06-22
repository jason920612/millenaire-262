package org.millenaire.common.ai.nav;

import java.util.List;

import net.minecraft.core.BlockPos;

import org.millenaire.common.entity.MillVillager;

/**
 * Executes a {@link Mill3DPathfinder} path on a villager (one instance per villager). It walks the path
 * node-by-node via the MoveControl and JUMPS when an edge climbs or crosses a gap — which vanilla navigation
 * can't do (it won't path across a 1-block gap), so this is the custom executor that makes the ground-up 3D
 * routes actually traversed. Re-plans on a timer, when off-path, or when the goal moves.
 */
public final class Mill3DNavigator {
   private static final int MAX_NODES = 8000; // bigger budget for medium-range 3D routes (long range → HPA*)
   private static final int REPLAN_TICKS = 40;

   private List<BlockPos> path;
   private int index;
   private int replanTimer;
   private BlockPos pathGoal;
   private int noRouteStreak;
   private double lastX = Double.NaN;
   private double lastZ;
   private int stuckTicks;

   /**
    * Drive {@code villager} toward {@code goal} for one tick at {@code speed} (the MOVEMENT_SPEED multiplier —
    * SAME scale as PathNavigation.moveTo's speedModifier and MoveControl.setWantedPosition's speed, so 0.5 is
    * the normal Mill walking speed; passing it through means task movement matches normal walking, and combat
    * can ask for its own faster approach). @return false when arrived or unreachable.
    */
   public boolean navigateTo(MillVillager villager, BlockPos goal, double speed) {
      BlockPos here = villager.blockPosition();
      if (here.closerThan(goal, 1.6)) {
         return false; // arrived
      }
      // No forward progress for ~1s → the current path is unwalkable from here; force a fresh plan.
      double mv = Double.isNaN(this.lastX) ? 1.0
         : (villager.getX() - this.lastX) * (villager.getX() - this.lastX) + (villager.getZ() - this.lastZ) * (villager.getZ() - this.lastZ);
      this.lastX = villager.getX();
      this.lastZ = villager.getZ();
      if (mv < 0.0025) {
         if (++this.stuckTicks > 20) {
            this.stuckTicks = 0;
            this.path = null;
         }
      } else {
         this.stuckTicks = 0;
      }
      boolean stale = this.path == null || this.pathGoal == null || !this.pathGoal.equals(goal)
         || --this.replanTimer <= 0 || this.index >= this.path.size()
         || farFromPath(here);
      if (stale) {
         replan(villager, goal);
      }
      if (this.path == null || this.path.size() < 2) {
         return false; // no route
      }
      // Advance past nodes we've effectively reached.
      while (this.index < this.path.size() - 1 && here.closerThan(this.path.get(this.index), 1.2)) {
         this.index++;
      }
      BlockPos target = this.path.get(Math.min(this.index, this.path.size() - 1));

      villager.getMoveControl().setWantedPosition(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
      // Jump ONLY when actually needed: climbing UP MORE than the villager can auto-step, or an actual air GAP
      // (a hole — no floor) one step toward the target. Do NOT jump just because the next node is far (that
      // caused pointless in-place jumping on flat ground). A wall ahead is a climb (handled by 'climbs'),
      // not a gap.
      // Use the ACTUAL height difference (villager.getY() is a double, so it accounts for already standing
      // part-way up on a slab/path/snow at e.g. y+0.5) against the entity's step height: anything <= maxUpStep
      // is auto-stepped by the vanilla collision code (Entity.maxUpStep), so jumping there is the spurious
      // jump-on-a-half-block the navigator used to do.
      boolean climbs = target.getY() - villager.getY() > villager.maxUpStep();
      boolean gapAhead = false;
      int sdx = Integer.signum(target.getX() - here.getX());
      int sdz = Integer.signum(target.getZ() - here.getZ());
      if ((sdx != 0 || sdz != 0) && target.getY() >= here.getY()) {
         Voxel v = new LevelVoxel(villager.level());
         int ax = here.getX() + sdx;
         int az = here.getZ() + sdz;
         gapAhead = !v.isSolid(ax, here.getY(), az) && !v.isSolid(ax, here.getY() - 1, az); // passable + no floor = hole
      }
      if ((climbs || gapAhead) && villager.onGround()) {
         villager.getJumpControl().jump();
      }
      return true;
   }

   public void reset() {
      this.path = null;
      this.pathGoal = null;
      this.index = 0;
   }

   /**
    * The path node the navigator is currently steering toward (the next waypoint), or {@code null} if there is
    * no active path. The door-open driver uses this (plus {@link #upcomingNode}) to find a wooden door / fence
    * gate the villager is about to walk through and open it ahead of time — the new-nav replacement for the old
    * pathEntity current/next-target-point lookups.
    */
   public BlockPos currentNode() {
      if (this.path == null || this.path.isEmpty()) {
         return null;
      }
      return this.path.get(Math.min(this.index, this.path.size() - 1));
   }

   /** The node one step beyond {@link #currentNode} (look-ahead for opening a door before reaching it). */
   public BlockPos upcomingNode() {
      if (this.path == null || this.path.isEmpty()) {
         return null;
      }
      int i = Math.min(this.index + 1, this.path.size() - 1);
      return this.path.get(i);
   }

   private boolean farFromPath(BlockPos here) {
      if (this.path == null || this.index >= this.path.size()) {
         return false;
      }
      return !here.closerThan(this.path.get(this.index), 3.0); // wandered off the planned node
   }

   private void replan(MillVillager villager, BlockPos goal) {
      this.pathGoal = goal;
      this.replanTimer = REPLAN_TICKS;
      this.index = 0;
      // Bias the 3D route away from the village danger field (hostiles/hazards) — safe AND 3D.
      org.millenaire.common.ai.MillInfluenceGrid danger = villager.getAiInfluence();
      float w = (float) org.millenaire.common.config.MillConfigValues.VFNavDangerWeight;
      Mill3DPathfinder.CostField field = (x, y, z) -> w * danger.dangerAt(x, z);
      this.path = Mill3DPathfinder.findPath(new LevelVoxel(villager.level()), villager.blockPosition(), goal, MAX_NODES, field);
      // Fail LOUD only on a GENUINE no-route (path == null). A size-1 path means "already adjacent to a
      // non-standable goal" — i.e. the villager is at the worksite — which is success, not a failure.
      if (this.path == null) {
         if (this.noRouteStreak++ % 60 == 0) {
            org.millenaire.common.utilities.MillLog.printException(
               "███ 3D-NAV: NO route ███ " + villager.blockPosition() + " → " + goal + " for " + villager,
               new IllegalStateException("3D pathfinder found no route"));
         }
      } else {
         this.noRouteStreak = 0;
      }
      // Don't let the legacy navigation fight the MoveControl while we drive the 3D path directly.
      villager.getNavigation().stop();
   }
}
