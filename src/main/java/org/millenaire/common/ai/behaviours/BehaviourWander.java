package org.millenaire.common.ai.behaviours;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.ai.MillBehaviour;
import org.millenaire.common.entity.MillVillager;

/**
 * Idle behaviour (req 8): when the villager has no explicit Mill goal/task point, roam to a RANDOM SAFE spot
 * within ~5 blocks; on arrival pick another. Lowest priority — any real behaviour preempts it. "Safe" uses
 * the same checks the pathfinder values: stand on solid ground, not into/over fluid, and not a long drop.
 */
public final class BehaviourWander implements MillBehaviour {
   private static final int RADIUS = 5;
   private static final double SPEED = 0.4;
   private BlockPos target;

   @Override
   public boolean canRun(MillVillager villager) {
      // Only when there is no explicit destination to drive movement.
      return villager.getPathDestPoint() == null && villager.getGoalDestPoint() == null;
   }

   @Override
   public int priority(MillVillager villager) {
      return 0; // lowest — fallback idle
   }

   @Override
   public boolean tick(MillVillager villager) {
      // Pick a new roam target if we have none or reached the last one.
      if (this.target == null || villager.blockPosition().closerThan(this.target, 1.5)) {
         this.target = pickSafeSpot(villager);
         if (this.target != null) {
            villager.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, SPEED);
         }
      }
      // Idle never "finishes"; it just yields the moment a real behaviour can run (handled by the engine).
      return true;
   }

   @Override
   public void onStop(MillVillager villager) {
      this.target = null;
      villager.getNavigation().stop();
   }

   private static BlockPos pickSafeSpot(MillVillager villager) {
      Level level = villager.level();
      RandomSource rng = villager.getRandom();
      BlockPos origin = villager.blockPosition();
      for (int attempt = 0; attempt < 8; attempt++) {
         int dx = rng.nextInt(RADIUS * 2 + 1) - RADIUS;
         int dz = rng.nextInt(RADIUS * 2 + 1) - RADIUS;
         if (dx == 0 && dz == 0) {
            continue;
         }
         // Snap to the local surface: search a couple of blocks up/down for solid ground.
         for (int dy = 2; dy >= -3; dy--) {
            BlockPos foot = origin.offset(dx, dy, dz);
            if (isStandable(level, foot)) {
               return foot;
            }
         }
      }
      return null;
   }

   /** A foot position is standable if there is solid ground below, headroom above, and no fluid hazard. */
   private static boolean isStandable(Level level, BlockPos foot) {
      BlockState ground = level.getBlockState(foot.below());
      BlockState at = level.getBlockState(foot);
      BlockState head = level.getBlockState(foot.above());
      if (!ground.isSolid()) {
         return false;
      }
      if (!at.getFluidState().isEmpty() || !ground.getFluidState().isEmpty()) {
         return false; // no water/lava (avoid the hazard, matches the safety cost)
      }
      return at.isAir() && head.isAir();
   }
}
