package org.millenaire.common.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

/**
 * Simplified ground navigation used by some Mill mobs.
 *
 * <p>1.12 this extended the raw {@code PathNavigate} and overrode a lot of
 * internal helpers ({@code getEntityPosition}, {@code getPathablePosY},
 * {@code getPathFinder}, {@code pathFollow}, {@code isDirectPathBetweenPoints},
 * {@code isSafeToStandAt}) — most of which duplicated vanilla
 * {@code PathNavigateGround}. In 26.2 the navigation/pathfinder internals are no
 * longer overridable the same way ({@code WalkNodeProcessor}→{@code WalkNodeEvaluator},
 * {@code PathPoint}→{@code Node}, {@code Path.getCurrentPathLength}/{@code getPathPriority}
 * removed/renamed). The one behaviour Mill actually wanted — being able to path
 * through doors and swim — is now expressed through flags on
 * {@link GroundPathNavigation}.
 *
 * <p>NOTE: the 1.12 class's overrides (the "direct path between points" line-of-sight check etc.) were
 * just copied vanilla {@code PathNavigateGround} internals, all of which {@link GroundPathNavigation}
 * already provides on 26.2. The only Mill-specific intent — pathing through doors and swimming — is set
 * via the flags in the constructor, so this thin subclass is the faithful and complete equivalent.
 */
public class PathNavigateSimple extends GroundPathNavigation {

   public PathNavigateSimple(Mob entity, Level level) {
      super(entity, level);
      this.setCanOpenDoors(true);
      this.setCanFloat(true);
   }
}
