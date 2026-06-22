package org.millenaire.common.ai.nav;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/**
 * Validates the ground-up 3D pathfinder on synthetic obstacle courses — the kind of 3D route planning the
 * in-game movement-sampling harness can't meaningfully test: gap jumps, stair climbs, going around walls.
 */
class Mill3DPathfinderTest {

   /** Voxel = a flat floor at y=0 over [0..n]² (minus removed cells) plus extra solid blocks. Agent walks y=1+. */
   private static Voxel world(int n, Set<Long> extraSolid, Set<Long> removedFloor) {
      return (x, y, z) -> {
         long key = BlockPos.asLong(x, y, z);
         if (extraSolid.contains(key)) {
            return true;
         }
         return y == 0 && x >= -1 && x <= n + 1 && z >= -1 && z <= n + 1 && !removedFloor.contains(key);
      };
   }

   @Test
   void jumpsAcrossA1BlockGap() {
      // Floor row with the floor under x=2 removed → a 1-block gap the agent must jump (can't stand in it).
      Voxel v = world(4, Set.of(), Set.of(BlockPos.asLong(2, 0, 0)));
      List<BlockPos> path = Mill3DPathfinder.findPath(v, new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), 20000);
      assertNotNull(path, "found a path across the gap");
      assertFalse(path.contains(new BlockPos(2, 1, 0)), "never stands in the gap");
      assertTrue(path.contains(new BlockPos(3, 1, 0)), "lands on the far side of the jump");
   }

   @Test
   void climbsAStaircase() {
      // Steps: stacks of solids so standable tops rise (1,2,0),(2,3,0),(3,4,0). Agent climbs from the floor.
      Set<Long> solids = new HashSet<>();
      solids.add(BlockPos.asLong(1, 1, 0));
      solids.add(BlockPos.asLong(2, 1, 0));
      solids.add(BlockPos.asLong(2, 2, 0));
      solids.add(BlockPos.asLong(3, 1, 0));
      solids.add(BlockPos.asLong(3, 2, 0));
      solids.add(BlockPos.asLong(3, 3, 0));
      Voxel v = world(4, solids, Set.of());
      List<BlockPos> path = Mill3DPathfinder.findPath(v, new BlockPos(0, 1, 0), new BlockPos(3, 4, 0), 20000);
      assertNotNull(path, "found a path up the stairs");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(3, 4, 0)), "reaches the top step");
      assertTrue(path.contains(new BlockPos(1, 2, 0)) && path.contains(new BlockPos(2, 3, 0)), "climbs each step");
   }

   @Test
   void goesAroundA2BlockWall() {
      // A 2-high wall across z=0 at x=2 (can't step over 2) with a 1-wide gap at z=2 → must detour around.
      Set<Long> wall = new HashSet<>();
      for (int z = 0; z <= 1; z++) {
         wall.add(BlockPos.asLong(2, 1, z));
         wall.add(BlockPos.asLong(2, 2, z));
      }
      Voxel v = world(4, wall, Set.of());
      List<BlockPos> path = Mill3DPathfinder.findPath(v, new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), 20000);
      assertNotNull(path, "found a route around the wall");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(4, 1, 0)), "reaches the goal past the wall");
      // must cross x=2 around an END of the wall (z outside the walled 0..1), in either direction.
      assertTrue(path.stream().anyMatch(p -> p.getX() == 2 && (p.getZ() >= 2 || p.getZ() <= -1)),
         "crosses the wall line only around its end");
      assertFalse(path.contains(new BlockPos(2, 1, 0)) || path.contains(new BlockPos(2, 1, 1)),
         "never walks through the wall");
   }
}
