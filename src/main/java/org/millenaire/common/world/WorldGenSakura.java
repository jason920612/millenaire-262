package org.millenaire.common.world;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.utilities.MillCommonUtilities;

/**
 * Millénaire sakura-tree generator, re-implemented for MC 26.2 (see {@link MillTreeGenerator}).
 * Mirrors the 1.12 algorithm: a spruce trunk, a rounded sakura-leaf canopy and a few branches.
 */
public class WorldGenSakura extends MillTreeGenerator {
   private static final int MIN_TREE_HEIGHT = 5;
   private static final BlockState WOOD_BS = Blocks.SPRUCE_LOG.defaultBlockState();
   private static final BlockState LEAVES_BS = MillBlocks.SAKURA_LEAVES.defaultBlockState()
      .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);

   public WorldGenSakura(boolean notify) {
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

      int baseY = position.getY();

      for (int yPos = baseY + 2; yPos <= baseY + treeHeight + 1; yPos++) {
         int leavesRadius = 3;
         if (yPos < baseY + 4) {
            leavesRadius -= baseY + 4 - yPos;
         } else if (yPos > baseY + treeHeight - 2) {
            leavesRadius -= yPos - (baseY + treeHeight - 2);
         }

         for (int xPos = position.getX() - leavesRadius; xPos <= position.getX() + leavesRadius; xPos++) {
            int dX = xPos - position.getX();
            for (int zPos = position.getZ() - leavesRadius; zPos <= position.getZ() + leavesRadius; zPos++) {
               int dZ = zPos - position.getZ();
               int chanceOn100 = 95;
               if (Math.abs(dX) == leavesRadius && Math.abs(dZ) == leavesRadius) {
                  chanceOn100 = 0;
               } else if (Math.abs(dX) == leavesRadius || Math.abs(dZ) == leavesRadius) {
                  chanceOn100 = 80;
               }

               if (MillCommonUtilities.randomInt(100) < chanceOn100) {
                  BlockPos pos = new BlockPos(xPos, yPos, zPos);
                  if (this.isAirOrLeaves(worldIn, pos)) {
                     this.setBlock(worldIn, pos, LEAVES_BS);
                  }
               }
            }
         }
      }

      for (int j3 = 0; j3 < treeHeight; j3++) {
         BlockPos upN = position.above(j3);
         if (this.isAirOrLeaves(worldIn, upN)) {
            this.setBlock(worldIn, upN, WOOD_BS);
         }
      }

      for (Direction direction : Direction.Plane.HORIZONTAL) {
         if (MillCommonUtilities.randomInt(100) < 60) {
            int branchMaxY = treeHeight - rand.nextInt(2);
            int branchMinY = 3 + rand.nextInt(2);
            int horizontalOffset = 2 - rand.nextInt(2);
            int xPos = position.getX();
            int zPos = position.getZ();

            for (int yPos = 0; yPos < branchMaxY; yPos++) {
               int y = baseY + yPos;
               if (yPos >= branchMinY && horizontalOffset > 0) {
                  xPos += direction.getStepX();
                  zPos += direction.getStepZ();
                  horizontalOffset--;
               }

               BlockPos pos = new BlockPos(xPos, y, zPos);
               if (this.isAirOrLeaves(worldIn, pos)) {
                  this.setBlock(worldIn, pos, WOOD_BS);
               }
            }
         }
      }

      return true;
   }
}
