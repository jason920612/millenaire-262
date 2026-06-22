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
 * Extreme-terrain traversal derivations for the ground-up 3D pathfinder. Because {@link Mill3DPathfinder}
 * runs against a synthetic {@link Voxel} it is pure math — no Minecraft — so we can simulate nasty geometry
 * (consecutive voids, lava strips, tall pillars, long detours, spiral towers, one-wide bridges, sealed goals)
 * deterministically and fast, the cases the in-game movement-sampling harness can never reproduce reliably.
 */
class Mill3DPathfinderExtremeTest {

   private static final int BUDGET = 60000;

   /** World from an explicit set of solid cells (everything else is air). */
   private static Voxel of(Set<Long> solid) {
      return (x, y, z) -> solid.contains(BlockPos.asLong(x, y, z));
   }

   /** Add a solid floor rectangle at y=0 over [x0..x1]×[z0..z1]. */
   private static void floor(Set<Long> s, int x0, int x1, int z0, int z1) {
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            s.add(BlockPos.asLong(x, 0, z));
         }
      }
   }

   @Test
   void jumpsAFieldOfConsecutiveGaps() {
      // Floor row x=0..8 with the floor removed under x=2,4,6 → three 1-block voids to jump in succession.
      Set<Long> s = new HashSet<>();
      floor(s, 0, 8, 0, 0);
      s.remove(BlockPos.asLong(2, 0, 0));
      s.remove(BlockPos.asLong(4, 0, 0));
      s.remove(BlockPos.asLong(6, 0, 0));
      List<BlockPos> path = Mill3DPathfinder.findPath(of(s), new BlockPos(0, 1, 0), new BlockPos(8, 1, 0), BUDGET);
      assertNotNull(path, "crosses a field of gaps");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(8, 1, 0)), "reaches the far side");
      for (int gx : new int[]{2, 4, 6}) {
         assertFalse(path.contains(new BlockPos(gx, 1, 0)), "never stands in a void at x=" + gx);
      }
   }

   /** World with explicit solid cells AND tall (fence/wall, 1.5-high) cells. */
   private static Voxel withTall(Set<Long> solid, Set<Long> tall) {
      return new Voxel() {
         public boolean isSolid(int x, int y, int z) {
            return solid.contains(BlockPos.asLong(x, y, z));
         }

         public boolean tall(int x, int y, int z) {
            return tall.contains(BlockPos.asLong(x, y, z));
         }
      };
   }

   @Test
   void goesAroundAFenceInsteadOfHoppingIt() {
      // A fence line (1.5-high, un-jumpable) at x=3, z=0..2, open at z=-1. The villager must route around it,
      // never trying to step onto it — the real "stuck trying to hop a 1.5-high fence" bug.
      Set<Long> solid = new HashSet<>();
      Set<Long> tall = new HashSet<>();
      floor(solid, 0, 6, -1, 3);
      for (int z = 0; z <= 2; z++) {
         long k = BlockPos.asLong(3, 1, z);
         solid.add(k);
         tall.add(k);
      }
      List<BlockPos> path = Mill3DPathfinder.findPath(withTall(solid, tall), new BlockPos(0, 1, 0), new BlockPos(6, 1, 0), BUDGET);
      assertNotNull(path, "found a route around the fence");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(6, 1, 0)), "reaches the goal past the fence");
      assertTrue(path.stream().anyMatch(p -> p.getZ() <= -1), "detours around the fence's open end");
      for (int z = 0; z <= 2; z++) {
         assertFalse(path.contains(new BlockPos(3, 2, z)), "never hops ONTO the fence top at z=" + z);
      }
   }

   @Test
   void runningJumpsA2WideGap() {
      // Floor x=0..6 with x=2,3 removed → a 2-WIDE void (stream / wide path) only the running jump can clear.
      Set<Long> s = new HashSet<>();
      floor(s, 0, 6, 0, 0);
      s.remove(BlockPos.asLong(2, 0, 0));
      s.remove(BlockPos.asLong(3, 0, 0));
      List<BlockPos> path = Mill3DPathfinder.findPath(of(s), new BlockPos(0, 1, 0), new BlockPos(6, 1, 0), BUDGET);
      assertNotNull(path, "running-jumps the 2-wide gap");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(6, 1, 0)), "reaches the far side");
      assertFalse(path.contains(new BlockPos(2, 1, 0)) || path.contains(new BlockPos(3, 1, 0)), "never stands in the 2-wide void");
   }

   @Test
   void routesAroundALavaCellViaDangerCost() {
      // Solid floor 0..4 × 0..2. A single "lava" cell at (2,1,0) modelled as a huge danger cost — the path
      // should detour one row over (z=1) instead of stepping on it, even though it is physically walkable.
      Set<Long> s = new HashSet<>();
      floor(s, 0, 4, 0, 2);
      Mill3DPathfinder.CostField lava = (x, y, z) -> (x == 2 && z == 0) ? 1000.0 : 0.0;
      List<BlockPos> path = Mill3DPathfinder.findPath(of(s), new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), BUDGET, lava);
      assertNotNull(path, "found a safe route");
      assertFalse(path.contains(new BlockPos(2, 1, 0)), "detours around the lava cell instead of crossing it");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(4, 1, 0)), "still reaches the goal");
   }

   @Test
   void dropsOffATallPillarToReachGround() {
      // A 5-high pillar at (0,*,0); the agent stands on top at y=5. Ground floor lies around it. It must step
      // off the ledge (a multi-block drop) and walk to the goal — vertical awareness, not a frozen perch.
      Set<Long> s = new HashSet<>();
      floor(s, -1, 4, -1, 1);
      for (int y = 0; y <= 4; y++) {
         s.add(BlockPos.asLong(0, y, 0)); // the pillar column
      }
      List<BlockPos> path = Mill3DPathfinder.findPath(of(s), new BlockPos(0, 5, 0), new BlockPos(4, 1, 0), BUDGET);
      assertNotNull(path, "steps off the pillar");
      assertTrue(path.get(0).equals(new BlockPos(0, 5, 0)), "starts on the pillar top");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(4, 1, 0)), "reaches the ground goal");
      assertTrue(path.stream().anyMatch(p -> p.getY() == 1), "descends to ground level");
   }

   @Test
   void detoursAroundALongWallToTheOnlyOpening() {
      // A long 2-high wall along x=3 from z=-4..4, open only past z=4. The agent must travel the length of the
      // wall to round its end — a big, non-greedy detour (the straight line is fully blocked).
      Set<Long> s = new HashSet<>();
      floor(s, -1, 6, -5, 6);
      for (int z = -4; z <= 4; z++) {
         s.add(BlockPos.asLong(3, 1, z));
         s.add(BlockPos.asLong(3, 2, z));
      }
      List<BlockPos> path = Mill3DPathfinder.findPath(of(s), new BlockPos(0, 1, 0), new BlockPos(6, 1, 0), BUDGET);
      assertNotNull(path, "found the way around the long wall");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(6, 1, 0)), "reaches the goal beyond the wall");
      assertTrue(path.stream().anyMatch(p -> p.getX() == 3 && p.getZ() >= 5), "rounds the wall's open end");
      for (int z = -4; z <= 4; z++) {
         assertFalse(path.contains(new BlockPos(3, 1, z)), "never walks through the wall body at z=" + z);
      }
   }

   @Test
   void climbsASpiralStaircaseTower() {
      // A spiral of single step-up blocks winding upward; each step is reachable by a step-up-one from the last.
      Set<Long> s = new HashSet<>();
      floor(s, -1, 3, -1, 3);
      // steps: support blocks whose tops form a rising spiral (1,1)->(1,2)->(2,3)->... around a 2x2 core.
      int[][] spiral = {{1, 0, 1}, {2, 0, 1}, {2, 1, 1}, {2, 0, 2}, {2, 1, 2}, {2, 2, 2}, {1, 0, 2}, {1, 1, 2}, {1, 2, 2}, {1, 3, 2}};
      for (int[] c : spiral) {
         s.add(BlockPos.asLong(c[0], c[1], c[2]));
      }
      // Top standable cell is on top of the tallest support: (1, 4, 2).
      List<BlockPos> path = Mill3DPathfinder.findPath(of(s), new BlockPos(0, 1, 0), new BlockPos(1, 4, 2), BUDGET);
      assertNotNull(path, "climbs the spiral tower");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(1, 4, 2)), "reaches the top of the spiral");
      assertTrue(path.stream().mapToInt(BlockPos::getY).max().orElse(0) >= 4, "actually ascended");
   }

   @Test
   void staysOnAOneWideBridgeOverAVoid() {
      // A single-block-wide bridge at z=0 over a void (no other floor). The only route is along the bridge.
      Set<Long> s = new HashSet<>();
      for (int x = 0; x <= 6; x++) {
         s.add(BlockPos.asLong(x, 0, 0)); // the bridge, nothing either side
      }
      List<BlockPos> path = Mill3DPathfinder.findPath(of(s), new BlockPos(0, 1, 0), new BlockPos(6, 1, 0), BUDGET);
      assertNotNull(path, "crosses the bridge");
      assertTrue(path.stream().allMatch(p -> p.getZ() == 0 && p.getY() == 1), "stays on the bridge, never steps into the void");
      assertTrue(path.get(path.size() - 1).equals(new BlockPos(6, 1, 0)), "reaches the far end");
   }

   @Test
   void bestEffortTowardASealedGoalDoesNotCrashOrTeleport() {
      // The goal sits inside a sealed 1-block air pocket fully surrounded by solid — unreachable. The pathfinder
      // must return a best-effort/partial result (or null) WITHOUT throwing and WITHOUT jumping to the goal.
      Set<Long> s = new HashSet<>();
      floor(s, -1, 6, -1, 1);
      // wall off a pocket around (5,1,0): solid ring at x=4 and x=6 and the cell above, sealing it.
      for (int y = 1; y <= 2; y++) {
         s.add(BlockPos.asLong(4, y, 0));
         s.add(BlockPos.asLong(6, y, 0));
         s.add(BlockPos.asLong(5, y, -1));
         s.add(BlockPos.asLong(5, y, 1));
      }
      s.add(BlockPos.asLong(5, 2, 0)); // ceiling over the pocket
      List<BlockPos> path = Mill3DPathfinder.findPath(of(s), new BlockPos(0, 1, 0), new BlockPos(5, 1, 0), BUDGET);
      // Either a partial path toward the wall, or null — but NEVER a path that contains the sealed goal cell.
      if (path != null) {
         assertFalse(path.contains(new BlockPos(5, 1, 0)), "never reaches/teleports into the sealed goal");
         assertTrue(path.get(0).equals(new BlockPos(0, 1, 0)), "starts at the start");
      }
   }
}
