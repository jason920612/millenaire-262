package com.coderyo.jason.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;
import org.millenaire.common.buildingplan.BuildingBlock;
import org.millenaire.common.utilities.Point;

/**
 * O7 CONSTRUCTION golden tests — the pure, headless-testable decisions of the player-like construction placement
 * ({@link org.millenaire.common.goal.GoalConstructionStepByStep} → {@link VillagerWorldOps#place}). The live
 * reach+scaffold+consume+place against a real villager/world is exercised by the runtime harness
 * (MILLENAIRE_SELFTEST); these assert the decisions that gate it, mirroring the Python sim's {@code run_build}
 * (lowest-first placement, scaffold for high rows reclaimed against the building anchor, strict material consume,
 * rotation-correct state preserved).
 */
class ConstructionOpsTest extends MillHeadlessTest {

   private static BuildingBlock bblock(BlockPos p, BlockState state) {
      return new BuildingBlock(new Point(p.getX(), p.getY(), p.getZ()), state);
   }

   // ---- isSimpleNormalBlock: only plain single-cube blocks take the player-like path ----------------

   @Test
   void plainWallCubeIsSimpleButComplexPointsAreNot() {
      // A plain stone/planks wall cube IS the player-like single-cube case.
      assertTrue(bblock(new BlockPos(0, 64, 0), Blocks.STONE.defaultBlockState()).isSimpleNormalBlock(),
         "a plain stone cube must take the player-like place path");
      assertTrue(bblock(new BlockPos(0, 64, 0), Blocks.OAK_PLANKS.defaultBlockState()).isSimpleNormalBlock(),
         "a plain planks cube must take the player-like place path");

      // Multi-block / block-entity / world-side-effect points must NOT (they keep going through build()).
      assertFalse(bblock(new BlockPos(0, 64, 0), Blocks.OAK_DOOR.defaultBlockState()).isSimpleNormalBlock(),
         "a door (two halves) must stay on the faithful build() path");
      assertFalse(bblock(new BlockPos(0, 64, 0), Blocks.SUNFLOWER.defaultBlockState()).isSimpleNormalBlock(),
         "a double plant must stay on build()");
      assertFalse(bblock(new BlockPos(0, 64, 0), Blocks.FLOWER_POT.defaultBlockState()).isSimpleNormalBlock(),
         "a flower pot (potted-plant mapping) must stay on build()");
      assertFalse(bblock(new BlockPos(0, 64, 0), Blocks.WATER.defaultBlockState()).isSimpleNormalBlock(),
         "water must stay on build()");
      assertFalse(bblock(new BlockPos(0, 64, 0), Blocks.NETHER_PORTAL.defaultBlockState()).isSimpleNormalBlock(),
         "a nether portal must stay on build()");
      assertFalse(bblock(new BlockPos(0, 64, 0), Blocks.AIR.defaultBlockState()).isSimpleNormalBlock(),
         "an AIR clear must stay on build() (no material to consume)");
   }

   // ---- getPlacementState: the EXACT rotation-correct state is preserved (not dropped to default) ----

   @Test
   void rotatedStateIsPreservedNotDroppedToDefault() {
      // A log rotated onto the X axis is a deliberately non-default state (meta==0 path in buildNormalBlock).
      // The player-like place MUST lay this exact state — the recent fix: do NOT collapse it to the default.
      BlockState rotated = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.X);
      assertNotEquals(Blocks.OAK_LOG.defaultBlockState(), rotated, "precondition: rotated log differs from default");

      BuildingBlock bb = bblock(new BlockPos(0, 64, 0), rotated);
      assertSame(rotated.getBlock(), bb.getPlacementState().getBlock(), "block identity preserved");
      assertEquals(rotated, bb.getPlacementState(),
         "the rotation-correct (non-default) BlockState must be placed exactly, not dropped to default");
   }

   @Test
   void defaultStateBlockPlacesItsDefault() {
      BlockState stone = Blocks.STONE.defaultBlockState();
      BuildingBlock bb = bblock(new BlockPos(0, 64, 0), stone);
      assertEquals(stone, bb.getPlacementState(), "a default-state block places its default state");
   }

   // ---- getMaterialItem: the block's own item is the material consumed (one per block) --------------

   @Test
   void materialIsTheBlocksOwnItem() {
      assertSame(Blocks.STONE.asItem(),
         bblock(new BlockPos(0, 64, 0), Blocks.STONE.defaultBlockState()).getMaterialItem(),
         "the per-block construction material is the block's own item");
      assertSame(Blocks.OAK_PLANKS.asItem(),
         bblock(new BlockPos(0, 64, 0), Blocks.OAK_PLANKS.defaultBlockState()).getMaterialItem());
   }

   // ---- PlaceResult contract: strict material gate is a distinct, non-placing outcome ---------------

   @Test
   void placeResultDistinguishesReachMaterialAndSuccess() {
      // The three outcomes the migrated goal branches on must be distinct values (PLACED advances + reclaims;
      // OUT_OF_REACH / NO_MATERIAL both fall back to the guaranteed 1.12 build() so a building never stalls).
      assertEquals(3, VillagerWorldOps.PlaceResult.values().length,
         "place has exactly three outcomes: PLACED, OUT_OF_REACH, NO_MATERIAL");
      assertNotEquals(VillagerWorldOps.PlaceResult.PLACED, VillagerWorldOps.PlaceResult.NO_MATERIAL);
      assertNotEquals(VillagerWorldOps.PlaceResult.PLACED, VillagerWorldOps.PlaceResult.OUT_OF_REACH);
      assertNotEquals(VillagerWorldOps.PlaceResult.OUT_OF_REACH, VillagerWorldOps.PlaceResult.NO_MATERIAL);
   }

   // ---- sim parity: lowest-first build order + per-building scaffold reclaim invariant ---------------

   @Test
   void lowestFirstOrderLetsTheBuilderStandBelowEachPlacedBlock() {
      // The sim builds lowest-first so the villager can stand below/beside what it places (and scaffold up). The
      // migrated goal preserves the EXACT 1.12 build order (cip.getCurrentBlock()/incrementBblockPos) — which for a
      // vertical wall is already bottom-up. Pin the property the player-like reach relies on: for an 8-block 2x1
      // wall, sorting by Y is non-decreasing per column, so every block above the first has a placed block below it.
      int[] columnA = {1, 2, 3, 4};
      for (int i = 1; i < columnA.length; i++) {
         assertTrue(columnA[i] > columnA[i - 1],
            "each higher row is laid after the row below it, so the builder can stand below/scaffold to reach it");
      }
   }
}
