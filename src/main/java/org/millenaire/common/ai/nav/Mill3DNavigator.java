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
   private static final int MAX_NODES = 4000;
   private static final double SPEED = 1.0;
   private static final int REPLAN_TICKS = 40;

   private List<BlockPos> path;
   private int index;
   private int replanTimer;
   private BlockPos pathGoal;

   /** Drive {@code villager} toward {@code goal} for one tick. @return false when arrived or unreachable. */
   public boolean navigateTo(MillVillager villager, BlockPos goal) {
      BlockPos here = villager.blockPosition();
      if (here.closerThan(goal, 1.6)) {
         return false; // arrived
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

      villager.getMoveControl().setWantedPosition(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, SPEED);
      // Jump when the next node climbs, or is a horizontal hop over a gap (>1.5 blocks away, not lower).
      double dx = target.getX() + 0.5 - villager.getX();
      double dz = target.getZ() + 0.5 - villager.getZ();
      double horiz = Math.sqrt(dx * dx + dz * dz);
      boolean climbs = target.getY() > here.getY();
      boolean gapHop = horiz > 1.5 && target.getY() >= here.getY();
      if ((climbs || gapHop) && villager.onGround()) {
         villager.getJumpControl().jump();
      }
      return true;
   }

   public void reset() {
      this.path = null;
      this.pathGoal = null;
      this.index = 0;
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
      // Don't let the legacy navigation fight the MoveControl while we drive the 3D path directly.
      villager.getNavigation().stop();
   }
}
