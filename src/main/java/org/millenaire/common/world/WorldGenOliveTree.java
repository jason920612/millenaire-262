package org.millenaire.common.world;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.utilities.MillCommonUtilities;

/**
 * Millénaire olive-tree generator, re-implemented for MC 26.2 (see {@link MillTreeGenerator}).
 * Mirrors the 1.12 algorithm: a short acacia trunk with four spreading branches and olive leaves.
 */
public class WorldGenOliveTree extends MillTreeGenerator {
   private static final int MIN_TREE_HEIGHT = 5;
   private static final BlockState WOOD_BS = Blocks.ACACIA_LOG.defaultBlockState();
   private static final BlockState LEAVES_BS = MillBlocks.LEAVES_OLIVETREE.defaultBlockState()
      .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);

   public WorldGenOliveTree(boolean notify) {
   }

   @Override
   public boolean generate(Level worldIn, Random rand, BlockPos position) {
      int treeHeight = rand.nextInt(2) + MIN_TREE_HEIGHT;
      if (position.getY() < 1 || position.getY() + treeHeight + 1 > worldIn.getMaxY()) {
         return false;
      }
      if (!this.spaceAvailable(worldIn, position, treeHeight)) {
         return false;
      }
      if (!this.canPlaceOn(worldIn, position) || position.getY() >= worldIn.getMaxY() - treeHeight - 1) {
         return false;
      }

      for (int yPos = 0; yPos < 5; yPos++) {
         BlockPos upN = position.above(yPos);
         if (this.isAirOrLeaves(worldIn, upN)) {
            this.setBlock(worldIn, upN, WOOD_BS);
         }
      }

      for (Direction direction : Direction.Plane.HORIZONTAL) {
         int branchStartY = 3 + rand.nextInt(1);
         int horizontalSize = 3 - rand.nextInt(2);
         int xPos = position.getX();
         int zPos = position.getZ();
         int yPosx = position.getY() + branchStartY;
         int curve = Math.random() < 0.5 ? 1 : -1;

         for (int hPos = 0; hPos < horizontalSize; hPos++) {
            if (yPosx < position.getY() + treeHeight && Math.random() < 0.7) {
               yPosx++;
            }

            if (direction.getStepX() != 0) {
               xPos += direction.getStepX();
               if (Math.random() < 0.15) {
                  zPos += curve;
               }
            } else {
               zPos += direction.getStepZ();
               if (Math.random() < 0.15) {
                  xPos += curve;
               }
            }

            BlockPos branchPos = new BlockPos(xPos, yPosx, zPos);
            if (this.isAirOrLeaves(worldIn, branchPos)) {
               this.setBlock(worldIn, branchPos, WOOD_BS.setValue(RotatedPillarBlock.AXIS, direction.getAxis()));

               for (int dx = -1; dx < 2; dx++) {
                  for (int dz = -1; dz < 2; dz++) {
                     for (int dy = -1; dy < 2; dy++) {
                        BlockPos leavePos = branchPos.offset(dx, dy, dz);
                        if (worldIn.getBlockState(leavePos).isAir() && MillCommonUtilities.randomInt(100) < 50) {
                           this.setBlock(worldIn, leavePos, LEAVES_BS);
                        }
                     }
                  }
               }
            }
         }
      }

      return true;
   }
}
