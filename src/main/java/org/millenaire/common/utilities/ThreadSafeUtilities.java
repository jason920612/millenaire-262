package org.millenaire.common.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Thread-safe (chunk-loaded-checked) world access. Ported to 26.2: chunk checks use
 * {@code Level#hasChunk(cx, cz)} (works for both server and client levels); passability via
 * {@code BlockState#blocksMotion()}.
 */
public class ThreadSafeUtilities {
   public static Block getBlock(Level world, int x, int y, int z) throws ChunkAccessException {
      validateCoords(world, x, z);
      return world.getBlockState(new BlockPos(x, y, z)).getBlock();
   }

   public static BlockState getBlockState(Level world, int x, int y, int z) throws ChunkAccessException {
      validateCoords(world, x, z);
      return world.getBlockState(new BlockPos(x, y, z));
   }

   public static boolean isBlockPassable(Block block, Level world, int x, int y, int z) throws ChunkAccessException {
      validateCoords(world, x, z);
      return !world.getBlockState(new BlockPos(x, y, z)).blocksMotion();
   }

   public static boolean isChunkAtGenerated(Level world, int x, int z) {
      return world.hasChunk(x >> 4, z >> 4);
   }

   public static boolean isChunkAtLoaded(Level world, int x, int z) {
      return world.hasChunk(x >> 4, z >> 4);
   }

   private static void validateCoords(Level world, int x, int z) throws ChunkAccessException {
      if (!world.hasChunk(x >> 4, z >> 4)) {
         throw new ChunkAccessException(
            "Attempting to access a coordinate in an unloaded chunk within a thread at " + x + "/" + z + ".", x, z);
      }
   }

   public static class ChunkAccessException extends Exception {
      private static final long serialVersionUID = -7650231135028039490L;
      public final int x;
      public final int z;

      public ChunkAccessException(String message, int x, int z) {
         super(message);
         this.x = x;
         this.z = z;
      }
   }
}
