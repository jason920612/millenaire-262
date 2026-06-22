package org.millenaire.common.ai.behaviours;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.ai.MillBehaviour;
import org.millenaire.common.entity.MillVillager;

/**
 * Survival behaviour (req 11): if a villager accidentally ends up IN a fluid (water or lava) it must get out
 * fast — head for the nearest dry standable shore and climb/swim up — rather than wander or fight. Highest
 * priority (above combat), so escaping lava/drowning preempts everything. Finishes the moment the villager
 * is back on dry land. Never warps (req 1): it just paths to the nearest reachable shore and swims upward.
 */
public final class BehaviourEscapeFluid implements MillBehaviour {
   private static final int SEARCH = 8; // how far to look for shore
   private static final double SPEED = 0.9; // hurry
   private BlockPos shore;
   private int rangedCd; // shared with combat: lets us shoot while swimming to shore

   @Override
   public boolean canRun(MillVillager villager) {
      return villager.isInWater() || villager.isInLava();
   }

   @Override
   public int priority(MillVillager villager) {
      // Above combat (50) and everything else — getting out of lava/water is survival-first.
      return villager.isInLava() ? 100 : 60;
   }

   @Override
   public boolean tick(MillVillager villager) {
      if (!villager.isInWater() && !villager.isInLava()) {
         villager.setSprinting(false);
         villager.getNavigation().stop();
         return false; // safely out — done
      }
      // The 3D pathfinder needs solid ground that deep water lacks (water isn't a standable node), so it can't
      // route us out — SWIM DIRECTLY toward the nearest shore via the (amphibious) MoveControl instead of pathing.
      villager.getNavigation().stop();
      if (this.shore == null || villager.blockPosition().closerThan(this.shore, 1.5)) {
         this.shore = nearestShore(villager);
      }
      // PLAYER-LIKE swim: sprinting while submerged makes vanilla updateSwimming() set the SWIMMING pose +
      // animation (same as a swimming player), and MillAmphibiousMoveControl then pitches the body and thrusts
      // in real 3D toward the target — so it actually swims up/forward to the shore, not just bobs.
      if (villager.isInWater()) {
         villager.setSprinting(true);
      }
      double sx = this.shore != null ? this.shore.getX() + 0.5 : villager.getX();
      double sy = this.shore != null ? this.shore.getY() + 0.5 : villager.getY() + 2.0; // aim up if no shore found
      double sz = this.shore != null ? this.shore.getZ() + 0.5 : villager.getZ();
      villager.getMoveControl().setWantedPosition(sx, sy, sz, SPEED);

      // In WATER (not suicidal lava), still FIGHT when able: swim to shore AND melee/shoot a reachable enemy,
      // rather than be a helpless floating target. (Lava: pure escape — trading blows in lava is death.)
      if (villager.isInWater() && villager.getTarget() != null) {
         this.rangedCd = BehaviourCombat.attackIfAble(villager, this.rangedCd);
      }
      return true;
   }

   @Override
   public void onStop(MillVillager villager) {
      this.shore = null;
      villager.setSprinting(false);
   }

   /** Nearest dry, standable, fluid-free cell (preferring the closest) — the shore to climb out onto. */
   private static BlockPos nearestShore(MillVillager villager) {
      Level level = villager.level();
      BlockPos origin = villager.blockPosition();
      BlockPos best = null;
      double bestDist = Double.MAX_VALUE;
      for (int dx = -SEARCH; dx <= SEARCH; dx++) {
         for (int dz = -SEARCH; dz <= SEARCH; dz++) {
            for (int dy = 3; dy >= -2; dy--) {
               BlockPos foot = origin.offset(dx, dy, dz);
               if (isDryStandable(level, foot)) {
                  double dist = origin.distSqr(foot);
                  if (dist < bestDist) {
                     bestDist = dist;
                     best = foot;
                  }
                  break;
               }
            }
         }
      }
      return best;
   }

   private static boolean isDryStandable(Level level, BlockPos foot) {
      BlockState ground = level.getBlockState(foot.below());
      BlockState at = level.getBlockState(foot);
      BlockState head = level.getBlockState(foot.above());
      // solid dry ground, no fluid in the ground/standing/head space, room to stand.
      return ground.isSolid() && ground.getFluidState().isEmpty()
         && at.getFluidState().isEmpty() && head.getFluidState().isEmpty()
         && at.isAir() && head.isAir();
   }
}
