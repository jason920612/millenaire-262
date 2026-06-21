package org.millenaire.common.pathing.atomicstryker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.millenaire.common.block.BlockMillWall;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.BlockStateUtilities;
import org.millenaire.common.utilities.ThreadSafeUtilities;

// Stateless helpers shared by the A* workers: the neighbour-offset tables, distance/heuristic maths,
// and the block-level "can a villager stand/move here" predicates (isPassableBlock / isViable / isLadder).
public class AStarStatic {
   // Relative step offsets explored from a node, each row {dx, dy, dz, stepCost}: the cardinal and
   // step-up/step-down moves a walker can take in one tick.
   static final int[][] candidates = new int[][]{
      {0, 0, -1, 1},
      {0, 0, 1, 1},
      {0, 1, 0, 1},
      {1, 0, 0, 1},
      {-1, 0, 0, 1},
      {1, 1, 0, 2},
      {-1, 1, 0, 2},
      {0, 1, 1, 2},
      {0, 1, -1, 2},
      {1, -1, 0, 1},
      {-1, -1, 0, 1},
      {0, -1, 1, 1},
      {0, -1, -1, 1}
   };
   // Same as candidates but with extra two-block downward moves, used when the search may drop off ledges.
   static final int[][] candidates_allowdrops = new int[][]{
      {0, 0, -1, 1},
      {0, 0, 1, 1},
      {1, 0, 0, 1},
      {-1, 0, 0, 1},
      {1, 1, 0, 2},
      {-1, 1, 0, 2},
      {0, 1, 1, 2},
      {0, 1, -1, 2},
      {1, -1, 0, 1},
      {-1, -1, 0, 1},
      {0, -1, 1, 1},
      {0, -1, -1, 1},
      {1, -2, 0, 1},
      {-1, -2, 0, 1},
      {0, -2, 1, 1},
      {0, -2, -1, 1}
   };

   // Collect every viable standing position in a small box around (posX,posY,posZ), ordered nearest
   // first (cost = horizontal+vertical offset), so callers can try the closest reachable node first.
   public static AStarNode[] getAccessNodesSorted(Level world, int workerX, int workerY, int workerZ, int posX, int posY, int posZ, AStarConfig config) throws ThreadSafeUtilities.ChunkAccessException {
      ArrayList<AStarNode> viableNodes = new ArrayList<>();

      for (int xOffset = -2; xOffset <= 2; xOffset++) {
         for (int zOffset = -2; zOffset <= 2; zOffset++) {
            for (int yOffset = -3; yOffset <= 2; yOffset++) {
               AStarNode candidate = new AStarNode(posX + xOffset, posY + yOffset, posZ + zOffset, Math.abs(xOffset) + Math.abs(yOffset), null);
               if (isViable(world, candidate, 1, config)) {
                  viableNodes.add(candidate);
               }
            }
         }
      }

      Collections.sort(viableNodes);
      int writeIndex = 0;

      // Drain the sorted list (cheapest first) into the result array.
      AStarNode next;
      AStarNode[] sortedNodes;
      for (sortedNodes = new AStarNode[viableNodes.size()]; !viableNodes.isEmpty() && (next = viableNodes.get(0)) != null; writeIndex++) {
         sortedNodes[writeIndex] = next;
         viableNodes.remove(0);
      }

      return sortedNodes;
   }

   public static double getDistanceBetweenCoords(int x, int y, int z, int posX, int posY, int posZ) {
      return Math.sqrt(Math.pow(x - posX, 2.0) + Math.pow(y - posY, 2.0) + Math.pow(z - posZ, 2.0));
   }

   public static double getDistanceBetweenNodes(AStarNode a, AStarNode b) {
      return Math.sqrt(Math.pow(a.x - b.x, 2.0) + Math.pow(a.y - b.y, 2.0) + Math.pow(a.z - b.z, 2.0));
   }

   public static double getEntityLandSpeed(Mob entLiving) {
      Vec3 motion = entLiving.getDeltaMovement();
      return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
   }

   public static int getIntCoordFromDoubleCoord(double input) {
      return Mth.floor(input);
   }

   public static boolean isLadder(Level world, Block b, int x, int y, int z) {
      // 26.2: Forge's Block#isLadder is gone; climbable blocks are identified by the CLIMBABLE block tag.
      return b != null && world.getBlockState(new BlockPos(x, y, z)).is(BlockTags.CLIMBABLE);
   }

   // True if a walker can occupy this block. Blocks fenced/walled positions, optionally water and doors
   // depending on config, and treats decaying leaves as passable when the config allows clearing them.
   public static boolean isPassableBlock(Level world, int ix, int iy, int iz, AStarConfig config) throws ThreadSafeUtilities.ChunkAccessException {
      BlockState blockState = ThreadSafeUtilities.getBlockState(world, ix, iy, iz);
      Block block = blockState.getBlock();
      if (iy > 0) {
         Block blockBelow = ThreadSafeUtilities.getBlock(world, ix, iy - 1, iz);
         if (BlockItemUtilities.isFence(blockBelow)
            || blockBelow == Blocks.IRON_BARS
            || blockBelow == Blocks.NETHER_BRICK_FENCE
            || blockBelow instanceof WallBlock
            || blockBelow instanceof BlockMillWall) {
            return false;
         }
      }

      if (block == null) {
         return true;
      } else if (!config.canSwim && block == Blocks.WATER) {
         return false;
      } else if (!config.canUseDoors || !BlockItemUtilities.isWoodenDoor(block) && !BlockItemUtilities.isFenceGate(block)) {
         if (config.canClearLeaves && block instanceof LeavesBlock) {
            // 26.2: leaves no longer have a "decayable" flag; PERSISTENT==false is the equivalent of the old decayable==true.
            if (!BlockStateUtilities.hasPropertyByName(blockState, "persistent")) {
               return true;
            }

            if (!(Boolean)blockState.getValue(LeavesBlock.PERSISTENT)) {
               return true;
            }
         }

         return ThreadSafeUtilities.isBlockPassable(block, world, ix, iy, iz);
      } else {
         return true;
      }
   }

   public static boolean isViable(Level world, AStarNode target, int yoffset, AStarConfig config) throws ThreadSafeUtilities.ChunkAccessException {
      return isViable(world, target.x, target.y, target.z, yoffset, config);
   }

   // Whether a walker can actually stand at (x,y,z): the block and the one above must be passable, the
   // block below must be solid ground (water only allowed when swimming), plus any extra headroom the
   // yoffset requires. Ladders are always viable as long as the space above is clear.
   public static boolean isViable(Level world, int x, int y, int z, int yoffset, AStarConfig config) throws ThreadSafeUtilities.ChunkAccessException {
      Block block = ThreadSafeUtilities.getBlock(world, x, y, z);
      Block blockBelow = ThreadSafeUtilities.getBlock(world, x, y - 1, z);
      if (block == Blocks.LADDER && isPassableBlock(world, x, y + 1, z, config)) {
         return true;
      } else if (isPassableBlock(world, x, y, z, config) && isPassableBlock(world, x, y + 1, z, config)) {
         if (blockBelow != Blocks.WATER) {
            if (isPassableBlock(world, x, y - 1, z, config)) {
               if (!config.canSwim) {
                  return false;
               }

               if (block != Blocks.WATER) {
                  return false;
               }
            }

            if (yoffset < 0) {
               yoffset *= -1;
            }

            for (int ycheckhigher = 1; ycheckhigher <= yoffset; ycheckhigher++) {
               if (!isPassableBlock(world, x, y + yoffset, z, config)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   // Convert the raw A* node list into a Minecraft path. When diagonal moves are not allowed, each
   // diagonal step is rewritten into an equivalent L-shaped detour through an intermediate node,
   // choosing whichever side is actually walkable. Finally the list is reversed (it was built end->start)
   // into a forward-ordered array of vanilla path Nodes.
   public static AS_PathEntity translateAStarPathtoPathEntity(Level world, List<AStarNode> input, AStarConfig config) throws ThreadSafeUtilities.ChunkAccessException {
      if (!config.canTakeDiagonals) {
         List<AStarNode> diagonalPath = input;
         input = new ArrayList<>();

         for (int i = 0; i < diagonalPath.size() - 1; i++) {
            input.add(diagonalPath.get(i));
            // A same-height step that changes both X and Z is a diagonal; insert a corner node to split it.
            if (diagonalPath.get(i).x != diagonalPath.get(i + 1).x && diagonalPath.get(i).z != diagonalPath.get(i + 1).z && diagonalPath.get(i).y == diagonalPath.get(i + 1).y) {
               if (!isPassableBlock(world, diagonalPath.get(i).x, diagonalPath.get(i).y - 1, diagonalPath.get(i + 1).z, config)
                  && isPassableBlock(world, diagonalPath.get(i).x, diagonalPath.get(i).y, diagonalPath.get(i + 1).z, config)
                  && isPassableBlock(world, diagonalPath.get(i).x, diagonalPath.get(i).y + 1, diagonalPath.get(i + 1).z, config)) {
                  // Route via the corner that keeps the current X and moves in Z first.
                  AStarNode cornerNode = new AStarNode(diagonalPath.get(i).x, diagonalPath.get(i).y, diagonalPath.get(i + 1).z, 0, null);
                  input.add(cornerNode);
               } else {
                  // Otherwise route via the corner that moves in X first.
                  AStarNode cornerNode = new AStarNode(diagonalPath.get(i + 1).x, diagonalPath.get(i).y, diagonalPath.get(i).z, 0, null);
                  input.add(cornerNode);
               }
            }
         }
      }

      // Reverse the node list (stored end-first) into a start-to-end array of vanilla path Nodes.
      Node[] points = new Node[input.size()];
      int writeIndex = 0;

      for (int remaining = input.size(); remaining > 0; writeIndex++) {
         AStarNode node = input.get(remaining - 1);
         points[writeIndex] = new Node(node.x, node.y, node.z);
         input.remove(remaining - 1);
         remaining--;
      }

      return new AS_PathEntity(points);
   }
}
