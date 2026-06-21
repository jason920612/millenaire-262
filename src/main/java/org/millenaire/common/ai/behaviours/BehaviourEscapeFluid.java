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
         villager.getNavigation().stop();
         return false; // safely out — done
      }
      // Swim/float upward while submerged (helps clear deep water/lava walls before pathing kicks in).
      villager.setDeltaMovement(villager.getDeltaMovement().add(0.0, 0.06, 0.0));

      // Re-pick the nearest dry shore if we don't have one or arrived at it.
      if (this.shore == null || villager.blockPosition().closerThan(this.shore, 1.5)) {
         this.shore = nearestShore(villager);
      }
      if (this.shore != null) {
         villager.getNavigation().moveTo(this.shore.getX() + 0.5, this.shore.getY(), this.shore.getZ() + 0.5, SPEED);
      } else {
         // No shore found nearby — head toward the lowest-danger open air above us as a fallback.
         villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY() + 1.0, villager.getZ(), SPEED);
      }
      return true;
   }

   @Override
   public void onStop(MillVillager villager) {
      this.shore = null;
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
