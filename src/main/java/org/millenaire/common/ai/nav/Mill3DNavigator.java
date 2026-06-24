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

   /** No-forward-progress ticks before we force a fresh plan — FAST (well under the old 60) so a stuck villager
    *  recovers in ~0.6s instead of seizing up for seconds. Mirrors how vanilla GroundPathNavigation drops a path
    *  it can't follow (tooFarFromPath / no movement) rather than re-aiming at a dead node forever. */
   private static final int STUCK_REPLAN_TICKS = 12;
   /** After this many CONSECUTIVE failed replans (same goal, still no progress) we are genuinely blocked on the
    *  current route: escalate to a real ALTERNATIVE — replan while AVOIDING the cell we keep getting stuck against —
    *  instead of recomputing the identical dead-end (the "never finds another way" symptom). */
   private static final int BLOCKED_REPLAN_ATTEMPTS = 3;

   /** GLOBAL-PROGRESS guard: ticks of NO net closing on the goal (best horizontal distance not improved) before we
    *  declare an ORBIT/PACE limit-cycle. The velocity guard above only catches a STOPPED villager; a villager that
    *  keeps MOVING around a pillar / pacing between two points has mv well above the velocity threshold every tick,
    *  so it never trips — yet it makes zero NET progress. ~70 ticks (~3.5s) lets a normal route settle/round a
    *  corner (which briefly stalls best-distance) without false-firing, then recovers an orbit. */
   private static final int NO_PROGRESS_TICKS = 70;
   /** A reduction in horizontal distance-to-goal smaller than this does NOT count as progress (so micro-jitter and
    *  the orbit's own oscillation can't keep resetting the counter). */
   private static final double PROGRESS_EPSILON = 0.5;

   private List<BlockPos> path;
   private int index;
   private int replanTimer;
   private BlockPos pathGoal;
   private int noRouteStreak;
   private double lastX = Double.NaN;
   private double lastZ;
   private int stuckTicks;
   private int blockedReplans; // consecutive replans that didn't restore progress
   private BlockPos avoidCell; // a cell to route AROUND on the next replan (the spot we keep jamming against)
   private double bestDistToGoal = Double.NaN; // closest (min) horizontal dist-to-goal seen on this leg
   private int noProgressTicks; // ticks since bestDistToGoal last improved (orbit/pace detector)

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
      // A genuinely NEW goal starts a fresh leg → re-baseline the global-progress guard so the previous leg's
      // best-distance doesn't make the new (farther) goal look like instant "no progress".
      if (this.pathGoal == null || !this.pathGoal.equals(goal)) {
         this.bestDistToGoal = Double.NaN;
         this.noProgressTicks = 0;
      }
      // No forward progress → the current path is unwalkable from HERE. Recover FAST (mirrors vanilla
      // GroundPathNavigation dropping a path it can't follow) rather than re-aiming at a dead node for seconds.
      double mv = Double.isNaN(this.lastX) ? 1.0
         : (villager.getX() - this.lastX) * (villager.getX() - this.lastX) + (villager.getZ() - this.lastZ) * (villager.getZ() - this.lastZ);
      this.lastX = villager.getX();
      this.lastZ = villager.getZ();
      boolean forceReplan = false;
      if (mv < 0.0025) {
         if (++this.stuckTicks > STUCK_REPLAN_TICKS) {
            this.stuckTicks = 0;
            // Remember the cell we keep jamming against (the next path node) so the escalated replan routes AROUND
            // it instead of recomputing the identical dead-end.
            if (this.path != null && this.index < this.path.size()) {
               this.avoidCell = this.path.get(Math.min(this.index, this.path.size() - 1));
            }
            this.path = null;        // drop the stuck path
            forceReplan = true;
            this.blockedReplans++;   // escalate the avoidance on repeated failure
         }
      } else {
         this.stuckTicks = 0;
         this.blockedReplans = 0;
         this.avoidCell = null;     // making progress → no longer blocked by anything
      }
      // ---- GLOBAL-PROGRESS guard (ORBIT / PACE) ----
      // The velocity guard above only fires when the villager STOPS. A villager that keeps MOVING — circling a
      // pillar/corner, or pacing between two points — passes the velocity check every tick (mv >> 0.0025) yet makes
      // zero NET progress toward the goal. Detect that by tracking the best (minimum) horizontal distance-to-goal
      // seen this leg: if the current distance fails to beat that best by PROGRESS_EPSILON for NO_PROGRESS_TICKS,
      // it's a limit cycle → run the SAME escalation as the velocity guard (avoid the jammed cell, force a replan,
      // bump blockedReplans so A* is pushed onto a genuinely different route / BehaviourGoToPoint re-routes).
      //
      // MOVING-TARGET EXCLUSION: a villager chasing a wandering animal (getGoalDestEntity — checkGoals republishes
      // the live entity pos into the path dest each tick) or a combat target (getTarget) LEGITIMATELY sees the
      // distance change without "closing" on a fixed point; firing the guard there would break shear/milk/combat.
      // Only run the guard for a FIXED goal.
      boolean movingTarget = villager.getGoalDestEntity() != null || villager.getTarget() != null;
      if (!movingTarget) {
         double goalDist = Math.sqrt(distSqHoriz(villager.getX(), villager.getZ(), goal.getX() + 0.5, goal.getZ() + 0.5));
         if (Double.isNaN(this.bestDistToGoal) || goalDist < this.bestDistToGoal - PROGRESS_EPSILON) {
            this.bestDistToGoal = goalDist; // genuine closing → record new best, reset the orbit counter
            this.noProgressTicks = 0;
         } else if (++this.noProgressTicks > NO_PROGRESS_TICKS) {
            // Moving but never closing → an orbit/pace. Recover via the EXISTING stuck escalation.
            this.noProgressTicks = 0;
            this.bestDistToGoal = goalDist; // re-baseline so we re-measure progress off the new route
            this.stuckTicks = 0;
            if (this.path != null && this.index < this.path.size()) {
               this.avoidCell = this.path.get(Math.min(this.index, this.path.size() - 1));
            }
            this.path = null;
            forceReplan = true;
            this.blockedReplans++;
         }
      } else {
         // Chasing a moving target: keep the orbit detector quiescent (no false positive when it later stops).
         this.bestDistToGoal = Double.NaN;
         this.noProgressTicks = 0;
      }
      boolean stale = forceReplan || this.path == null || this.pathGoal == null || !this.pathGoal.equals(goal)
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
      // The node AFTER the immediate target — used to recognise a JUMP edge (a step where the path skips over
      // air to land 2-3 cells out) and to keep momentum aimed past the gap, not at its near lip.
      BlockPos next = this.index + 1 < this.path.size() ? this.path.get(this.index + 1) : null;

      villager.getMoveControl().setWantedPosition(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);

      // ---- JUMP DECISION (faithful: jump ONLY when the path edge genuinely needs it) ----
      // (a) CLIMB: the target is more than one auto-step above us (vanilla auto-steps <= STEP_HEIGHT 0.6; a full
      //     1-block ledge needs a jump). Use the live Y (a slab/path leaves us at y+0.5) vs maxUpStep.
      boolean climbs = target.getY() - villager.getY() > villager.maxUpStep();
      // (b) GAP: this path EDGE crosses air — the target (or the node beyond it) is >=2 cells away horizontally
      //     with no walkable floor in the intervening cell. The pathfinder emitted a jump/running-jump edge here;
      //     we must launch across it. Detect it from the EDGE (here→target / target→next), not a single probe.
      Voxel vox = new LevelVoxel(villager.level());
      boolean gapAhead = edgeIsGap(vox, here, target) || (next != null && edgeIsGap(vox, here, next));
      // Landing node for a running jump = the far side we must reach; aim momentum at IT so we clear the gap.
      BlockPos landing = gapAhead ? (edgeIsGap(vox, here, target) ? target : next) : target;

      if ((climbs || gapAhead) && villager.onGround()) {
         villager.getJumpControl().jump();
         // CRITICAL FORWARD MOMENTUM: vanilla jumpFromGround only adds horizontal launch when isSprinting()
         // (LivingEntity.jumpFromGround) — a villager is never sprinting, so the bare JumpControl.jump() is a pure
         // VERTICAL hop: it rises and falls straight back into the gap / against the ledge (exactly the "can't jump
         // the gap / stuck on the step" bug). So we add the sprint-style horizontal impulse OURSELVES, toward the
         // landing cell, scaled up for a multi-block running jump. This is the faithful analogue of sprint-jumping.
         double tx = (landing.getX() + 0.5) - villager.getX();
         double tz = (landing.getZ() + 0.5) - villager.getZ();
         double len = Math.sqrt(tx * tx + tz * tz);
         if (len > 1.0E-4) {
            double horizDist = Math.max(Math.abs(landing.getX() - here.getX()), Math.abs(landing.getZ() - here.getZ()));
            // 0.36 ~= the 0.2 sprint impulse + extra so a 2-3 block running jump actually lands; a plain step-up
            // (horizDist<=1) gets a gentle nudge so it settles onto the ledge instead of overshooting.
            double impulse = gapAhead ? (horizDist >= 3 ? 0.46 : 0.36) : 0.18;
            net.minecraft.world.phys.Vec3 dm = villager.getDeltaMovement();
            villager.setDeltaMovement(dm.x + tx / len * impulse, dm.y, dm.z + tz / len * impulse);
         }
      }
      return true;
   }

   /**
    * Does the straight edge {@code from→to} cross an AIR GAP that requires a jump? True when {@code to} is at
    * least 2 cells away (cardinally) and at the same or higher level, AND the cell one step toward it has no
    * walkable floor (a hole / trench / ravine lip) — i.e. you can't simply walk it, you must leap. Mirrors the
    * pathfinder's jump-gap / running-jump edges so the executor jumps exactly where the planner planned a jump.
    */
   private static boolean edgeIsGap(Voxel v, BlockPos from, BlockPos to) {
      if (to.getY() < from.getY()) {
         return false; // dropping down is handled by walking off + falling, not a jump
      }
      int dx = to.getX() - from.getX();
      int dz = to.getZ() - from.getZ();
      int horiz = Math.max(Math.abs(dx), Math.abs(dz));
      if (horiz < 2) {
         return false; // adjacent edge — a step-up at most, not a gap
      }
      int sx = Integer.signum(dx);
      int sz = Integer.signum(dz);
      // The first cell toward the target: if there's no floor under it (and it's open), it's a real gap to jump.
      int ax = from.getX() + sx;
      int az = from.getZ() + sz;
      return !v.isSolid(ax, from.getY() - 1, az) && !v.isSolid(ax, from.getY(), az);
   }

   public void reset() {
      this.path = null;
      this.pathGoal = null;
      this.index = 0;
      this.stuckTicks = 0;
      this.blockedReplans = 0;
      this.avoidCell = null;
      this.lastX = Double.NaN;
      this.bestDistToGoal = Double.NaN;
      this.noProgressTicks = 0;
   }

   /** Squared horizontal (XZ) distance between two world-space points. */
   private static double distSqHoriz(double ax, double az, double bx, double bz) {
      double dx = ax - bx;
      double dz = az - bz;
      return dx * dx + dz * dz;
   }

   /** Consecutive replans that didn't restore progress — exposed for the E2E orbit/pace regression test, which
    *  asserts the global-progress guard actually ESCALATED (engaged the avoid-cell / blocked-replan recovery)
    *  rather than orbiting forever. Not used by gameplay code. */
   public int blockedReplansForTest() {
      return this.blockedReplans;
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
      // ALTERNATIVE-ROUTE escalation: once we've failed to make progress on the SAME route a few times, the
      // planner keeps returning the identical dead-end (e.g. a node tucked behind an obstacle we can't actually
      // traverse). Layer a heavy avoidance cost around the cell we keep jamming against so A* is forced to find a
      // genuinely DIFFERENT way around — this is the "try another route" recovery (never spin on the same node).
      final BlockPos avoid = this.blockedReplans >= BLOCKED_REPLAN_ATTEMPTS ? this.avoidCell : null;
      Mill3DPathfinder.CostField field = (x, y, z) -> {
         double extra = w * danger.dangerAt(x, z);
         if (avoid != null) {
            int adx = x - avoid.getX();
            int adz = z - avoid.getZ();
            if (adx * adx + adz * adz <= 4) { // within ~2 cells of the stuck spot → make A* route around it
               extra += 1000.0;
            }
         }
         return extra;
      };
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
