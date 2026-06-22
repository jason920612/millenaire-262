package org.millenaire.common.ai.nav;

import net.minecraft.core.BlockPos;

/**
 * A minimal voxel view of the world for the ground-up 3D pathfinder — decoupled from Minecraft so the
 * pathfinder can be unit-tested against synthetic obstacle courses (the in-game harness can't meaningfully
 * test 3D route planning). The real implementation wraps a {@code Level}; the test implementation is a set of
 * solid cells.
 */
public interface Voxel {
   /** Is the block at {@code pos} a full collision block (can't stand inside it; can stand on top of it)? */
   boolean isSolid(int x, int y, int z);

   default boolean isSolid(BlockPos p) {
      return isSolid(p.getX(), p.getY(), p.getZ());
   }

   /** A 2-tall agent can OCCUPY (x,y,z) standing if there is solid ground below and 2 cells of air for body. */
   default boolean standable(int x, int y, int z) {
      return isSolid(x, y - 1, z) && !isSolid(x, y, z) && !isSolid(x, y + 1, z);
   }

   /** Clear (air) for the agent's body at (x,y,z) — feet + head — used for jump/step head-room checks. */
   default boolean clearBody(int x, int y, int z) {
      return !isSolid(x, y, z) && !isSolid(x, y + 1, z);
   }
}
