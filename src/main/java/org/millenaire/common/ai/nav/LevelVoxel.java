package org.millenaire.common.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The real-world {@link Voxel}: a block is "solid" (blocks the body / can be stood on) if it has any collision
 * shape — so full blocks, slabs, stairs, paths, farmland count, while air, tall grass, crops, flowers and
 * fluids are passable. This is exactly the standability rule the value-field cost provider settled on, now
 * shared by the ground-up 3D pathfinder against the live world. (Danger/safety is layered on as cost, not
 * here.) A short per-tick lookup cache keeps repeated A* queries cheap.
 */
public final class LevelVoxel implements Voxel {
   private final Level level;
   private final java.util.HashMap<Long, Boolean> cache = new java.util.HashMap<>();
   private final java.util.HashMap<Long, Boolean> tallCache = new java.util.HashMap<>();

   public LevelVoxel(Level level) {
      this.level = level;
   }

   @Override
   public boolean isSolid(int x, int y, int z) {
      long key = BlockPos.asLong(x, y, z);
      Boolean cached = this.cache.get(key);
      if (cached != null) {
         return cached;
      }
      BlockPos p = new BlockPos(x, y, z);
      BlockState s = this.level.getBlockState(p);
      boolean solid = !s.getCollisionShape(this.level, p).isEmpty();
      this.cache.put(key, solid);
      return solid;
   }

   @Override
   public boolean tall(int x, int y, int z) {
      long key = BlockPos.asLong(x, y, z);
      Boolean cached = this.tallCache.get(key);
      if (cached != null) {
         return cached;
      }
      BlockPos p = new BlockPos(x, y, z);
      net.minecraft.world.phys.shapes.VoxelShape shape = this.level.getBlockState(p).getCollisionShape(this.level, p);
      // Collision top above one full block (fence/wall/gate = 1.5) → can't be jumped onto/over.
      boolean tall = !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) > 1.0;
      this.tallCache.put(key, tall);
      return tall;
   }
}
