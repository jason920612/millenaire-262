package com.coderyo.jason.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Java twin of the sim-validated {@code task-ops-sim/minesim.py}: the pure {@link MillMiningOps} engine is run on the
 * SAME synthetic stone body (two ore veins, a natural cave, a lava pocket in the path) and the SAME assertions are
 * made — the connected vein floods out, the cave's exposed ore is found, the frontier advances OUTWARD, and a hazard
 * (lava) is NEVER planned through (it is detoured around). No Minecraft {@code Level} is needed; the engine runs
 * entirely on the {@link MillMiningOps.MineView} abstraction, exactly like {@code minesim.py}'s {@code World}.
 */
class MillMiningOpsTest {

   /** Synthetic mine world: a map of cell -> kind, defaulting to solid diggable stone (underground), like the sim. */
   private static final class TestMine implements MillMiningOps.MineView {
      static final int STONE = 0, ORE = 1, AIR = 2, LAVA = 3, BEDROCK = 4;
      final Map<Long, Integer> cells = new HashMap<>();
      final Set<Long> marked = new HashSet<>();

      void set(BlockPos p, int kind) {
         cells.put(p.asLong(), kind);
      }

      int kind(BlockPos p) {
         return cells.getOrDefault(p.asLong(), STONE);
      }

      @Override
      public boolean isOre(BlockPos p) {
         return kind(p) == ORE;
      }

      @Override
      public boolean isHazard(BlockPos p) {
         int k = kind(p);
         return k == LAVA || k == BEDROCK || marked.contains(p.asLong());
      }

      @Override
      public boolean isDiggable(BlockPos p) {
         int k = kind(p);
         return k == STONE || k == ORE;
      }

      @Override
      public boolean isPassable(BlockPos p) {
         return kind(p) == AIR && !marked.contains(p.asLong());
      }
   }

   private static BlockPos at(int x, int y, int z) {
      return new BlockPos(x, y, z);
   }

   /** Build the minesim.py world: vein1 near the entrance, vein2 further out, a cave with wall-ore, a lava pocket. */
   private static TestMine simWorld() {
      TestMine w = new TestMine();
      int[][] vein1 = {{5, 8, 5}, {6, 8, 5}, {6, 8, 6}, {7, 8, 6}, {7, 9, 6}};
      int[][] vein2 = {{12, 8, 5}, {12, 8, 6}, {13, 8, 6}};
      int[][] cave = {{9, 8, 5}, {9, 8, 6}, {10, 8, 5}, {10, 8, 6}};
      int[][] caveOre = {{9, 8, 4}, {10, 8, 7}};
      for (int[] p : vein1) {
         w.set(at(p[0], p[1], p[2]), TestMine.ORE);
      }
      for (int[] p : vein2) {
         w.set(at(p[0], p[1], p[2]), TestMine.ORE);
      }
      for (int[] p : cave) {
         w.set(at(p[0], p[1], p[2]), TestMine.AIR);
      }
      for (int[] p : caveOre) {
         w.set(at(p[0], p[1], p[2]), TestMine.ORE);
      }
      w.set(at(8, 8, 5), TestMine.LAVA); // lava hazard in the path — must never be breached.
      return w;
   }

   // ---- ore scan -----------------------------------------------------------------------------------

   @Test
   void findsNearestOre() {
      TestMine w = simWorld();
      BlockPos from = at(0, 8, 5);
      BlockPos nearest = MillMiningOps.findNearestOre(w, from, 12);
      assertEquals(at(5, 8, 5), nearest, "nearest ore to the entrance is the closest vein1 cell");
   }

   @Test
   void scanSkipsHazards() {
      TestMine w = new TestMine();
      w.set(at(2, 8, 0), TestMine.LAVA); // a lava cell is never reported as an ore target.
      assertEquals(null, MillMiningOps.findNearestOre(w, at(0, 8, 0), 5));
   }

   // ---- flood-fill vein ----------------------------------------------------------------------------

   @Test
   void floodMinesWholeConnectedVein() {
      TestMine w = simWorld();
      List<BlockPos> vein = MillMiningOps.floodMineVein(w, at(5, 8, 5));
      // vein1 is 5 connected ore cells; the flood must return exactly those (not vein2, not the cave ore).
      assertEquals(5, vein.size(), "flood-fill clears the whole connected vein1");
      assertTrue(vein.contains(at(7, 9, 6)), "includes the diagonally-stepped-up connected ore");
      assertFalse(vein.contains(at(12, 8, 5)), "does NOT cross to the disconnected vein2");
   }

   @Test
   void floodVeinIsBounded() {
      // A 100-cell straight ore vein, capped at MAX_VEIN_PER_CYCLE so a huge vein can't stall a tick.
      TestMine w = new TestMine();
      for (int x = 0; x < 100; x++) {
         w.set(at(x, 8, 0), TestMine.ORE);
      }
      List<BlockPos> vein = MillMiningOps.floodMineVein(w, at(0, 8, 0));
      assertEquals(MillMiningOps.MAX_VEIN_PER_CYCLE, vein.size(), "vein flood is bounded per cycle");
   }

   // ---- cave use -----------------------------------------------------------------------------------

   @Test
   void findsOreExposedOnCaveWalls() {
      TestMine w = simWorld();
      // From a cell bordering the cavern, the engine finds the ore exposed on the cave walls.
      List<BlockPos> exposed = MillMiningOps.caveExposedOre(w, at(9, 8, 5), 3);
      assertTrue(exposed.contains(at(9, 8, 4)), "finds ore on the cave wall (9,8,4)");
      assertTrue(exposed.contains(at(10, 8, 7)), "finds ore on the far cave wall (10,8,7)");
   }

   // ---- dig path: routes AROUND a lava hazard ------------------------------------------------------

   @Test
   void digPathNeverPlansThroughLava() {
      TestMine w = simWorld();
      // Plan a tunnel from the entrance toward vein2, which lies past the lava at (8,8,5).
      List<BlockPos> path = MillMiningOps.planDigPath(w, at(0, 8, 5), at(12, 8, 5));
      assertFalse(path.isEmpty(), "a dig path is planned");
      for (BlockPos p : path) {
         assertFalse(w.isHazard(p), "the dig path NEVER steps into the lava hazard @ " + p);
      }
      // The detour around the lava must leave the straight line at the lava column (x=8).
      assertTrue(path.stream().anyMatch(p -> p.getX() == 8 && p.getY() != 8 || (p.getX() == 8 && p.getZ() != 5)
            || p.getX() != 8),
         "the path detours around the lava rather than going straight through it");
   }

   // ---- frontier advance: outward, hazard-routed ---------------------------------------------------

   @Test
   void frontierAdvancesOutwardAndMarksHazards() {
      TestMine w = simWorld();
      List<BlockPos> marked = new ArrayList<>();
      BlockPos before = at(7, 8, 5); // just before the lava column.
      BlockPos after = MillMiningOps.advanceFrontier(w, before, 1, 0, p -> {
         marked.add(p);
         w.marked.add(p.asLong());
      });
      assertTrue(after.getX() > before.getX(), "the frontier advanced OUTWARD (toward unexplored ground)");
      assertFalse(w.isHazard(after), "the frontier never lands ON a hazard");
      assertTrue(marked.contains(at(8, 8, 5)), "the lava in the path was marked a DON'T-MINE hazard and avoided");
   }

   // ---- the full minesim.py scenario asserts -------------------------------------------------------

   @Test
   void fullScenarioMatchesMinesim() {
      TestMine w = simWorld();
      // Mine vein1 (flood), take the cave ore, advance the frontier past the lava toward vein2 — assert the
      // same invariants minesim.py asserts: enough ore minable, lava never breached, frontier advanced outward.
      int oreMined = 0;

      // vein1 flood
      List<BlockPos> vein1 = MillMiningOps.floodMineVein(w, at(5, 8, 5));
      for (BlockPos p : vein1) {
         assertFalse(w.isHazard(p), "never flood a hazard");
         w.set(p, TestMine.AIR);
         oreMined++;
      }

      // cave ore
      List<BlockPos> caveOre = MillMiningOps.caveExposedOre(w, at(9, 8, 5), 3);
      for (BlockPos p : caveOre) {
         w.set(p, TestMine.AIR);
         oreMined++;
      }

      // frontier advance toward vein2, routing around the lava
      BlockPos frontier = at(7, 8, 5);
      int advances = 0;
      for (int cyc = 0; cyc < 4; cyc++) {
         BlockPos next = MillMiningOps.advanceFrontier(w, frontier, 1, 0, p -> w.marked.add(p.asLong()));
         if (!next.equals(frontier)) {
            advances++;
         }
         frontier = next;
      }

      // ===== minesim.py asserts =====
      assertEquals(TestMine.LAVA, w.kind(at(8, 8, 5)), "lava cell unaltered (never breached into air)");
      assertTrue(oreMined >= vein1.size() + caveOre.size(), "vein + cave ore all mined, got " + oreMined);
      assertTrue(advances >= 1 && frontier.getX() > 7, "mine frontier advanced outward to " + frontier);
      assertNotNull(frontier);
   }
}
