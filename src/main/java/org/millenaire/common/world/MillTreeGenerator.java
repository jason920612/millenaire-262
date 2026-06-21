package org.millenaire.common.world;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared base for the Millénaire custom fruit-tree generators (apple/cherry/olive/pistachio/sakura).
 *
 * <p>1.12 extended {@code net.minecraft.world.gen.feature.WorldGenAbstractTree}, which is gone in
 * 26.2 (worldgen is data-driven). These Mill trees are small and bespoke, so — as authorised by the
 * port brief — they are placed imperatively via {@link Level#setBlock} rather than as full
 * {@code Feature}s. This base provides the obstacle/ground/replaceable helpers the original
 * {@code WorldGenAbstractTree} supplied, all verified against the 26.2 API.</p>
 */
public abstract class MillTreeGenerator {

   /** Replaces {@code WorldGenAbstractTree.func_175903_a}: place a block silently during worldgen. */
   protected void setBlock(Level world, BlockPos pos, BlockState state) {
      // Flag 4 = no neighbour-notification / no rendering events, matching worldgen-style placement.
      world.setBlock(pos, state, 4);
   }

   /** Air or leaves (the original allowed growth through leaves). */
   protected boolean isAirOrLeaves(Level world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      return state.isAir() || state.is(BlockTags.LEAVES);
   }

   /**
    * Replaces {@code WorldGenAbstractTree.isReplaceable}: the column the tree occupies must be air,
    * leaves or sapling-replaceable (1.12 used {@code block.isReplaceable} / air / leaves).
    */
   protected boolean isReplaceable(Level world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      return state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced();
   }

   /**
    * Replaces the 1.12 {@code canSustainPlant(...sapling)} ground check: the block below must be
    * dirt/grass-like so a sapling could grow there.
    */
   protected boolean canPlaceOn(Level world, BlockPos position) {
      BlockState below = world.getBlockState(position.below());
      return below.is(BlockTags.DIRT) || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.FARMLAND);
   }

   /**
    * Replaces the 1.12 obstacle scan: ensure the bounding column for the tree (widening near the
    * top) is clear of solid obstacles.
    */
   protected boolean spaceAvailable(Level world, BlockPos position, int treeHeight) {
      int baseY = position.getY();
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      for (int j = baseY; j <= baseY + 1 + treeHeight; j++) {
         int radius = 1;
         if (j == baseY) {
            radius = 0;
         }
         if (j >= baseY + 1 + treeHeight - 2) {
            radius = 2;
         }

         for (int l = position.getX() - radius; l <= position.getX() + radius; l++) {
            for (int i1 = position.getZ() - radius; i1 <= position.getZ() + radius; i1++) {
               if (j < world.getMinY() || j > world.getMaxY()) {
                  return false;
               }
               cursor.set(l, j, i1);
               if (!this.isReplaceable(world, cursor)) {
                  return false;
               }
            }
         }
      }
      return true;
   }

   /** Generate the tree at {@code position}; returns true if anything was placed. */
   public abstract boolean generate(Level worldIn, Random rand, BlockPos position);
}
