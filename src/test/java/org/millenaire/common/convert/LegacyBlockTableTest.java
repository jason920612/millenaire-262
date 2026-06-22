package org.millenaire.common.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * M3 golden tests for the declarative legacy 1.12 (name, meta) -> 26.2 {@link BlockState} conversion
 * table that replaced {@code WorldUtilities.legacyMetaToBlockState}.
 *
 * <p>Expected states are DERIVED from the decompiled 1.12 vanilla {@code Block.getStateFromMeta(meta)}
 * bit-packing (cited per family below) and constructed directly from 26.2 vanilla blocks/properties, so
 * a regression in the table OR the dot-spec parser fails here rather than placing wrong blocks in-world.
 * The 1.12 meta values are exactly those {@code WorldUtilities.blockStateToLegacyMeta} (validated in
 * earlier milestones) reproduces, i.e. behaviour-identical to pre-M3.</p>
 */
class LegacyBlockTableTest extends MillHeadlessTest {

   private static BlockState state(String name, int meta) {
      return MillConvert.blockState(name, meta);
   }

   // ---- Logs / pillars: 1.12 BlockLog meta&12 = axis (Y=0, X=4, Z=8) -----------------------------

   @Test
   void logAxis() {
      // 1.12 evidence: blockStateToLegacyMeta maps AXIS X->4, Z->8, Y->0; getStateFromMeta is its inverse.
      assertEquals(Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y),
         state("spruce_log", 0));
      assertEquals(Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X),
         state("spruce_log", 4));
      assertEquals(Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z),
         state("oak_log", 8));
   }

   // ---- Slab: 1.12 BlockSlab top-half bit = 8 ----------------------------------------------------

   @Test
   void slabType() {
      // 1.12 evidence: blockStateToLegacyMeta maps SLAB_TYPE TOP->8, BOTTOM->0.
      assertEquals(Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM),
         state("oak_slab", 0));
      assertEquals(Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP),
         state("stone_slab", 8));
   }

   // ---- Stairs: 1.12 meta = 4*(half==TOP) | (5 - facing.get3DDataValue()) -------------------------

   @Test
   void stairsFacingHalf() {
      // 1.12 evidence: EnumFacing 3D ids DOWN0 UP1 NORTH2 SOUTH3 WEST4 EAST5; so 5-id gives
      // EAST=0 WEST=1 SOUTH=2 NORTH=3; |4 sets the TOP half. blockStateToLegacyMeta reproduces this.
      assertEquals(Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
            .setValue(BlockStateProperties.HALF, Half.BOTTOM),
         state("oak_stairs", 0));
      assertEquals(Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            .setValue(BlockStateProperties.HALF, Half.BOTTOM),
         state("oak_stairs", 2));
      assertEquals(Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .setValue(BlockStateProperties.HALF, Half.TOP),
         state("oak_stairs", 7));
   }

   // ---- Ladder: 1.12 BlockLadder meta = facing.get3DDataValue() ----------------------------------

   @Test
   void ladderFacing() {
      // 1.12 evidence: NORTH=2 SOUTH=3 WEST=4 EAST=5 (3D ids); blockStateToLegacyMeta: facing.get3DDataValue().
      assertEquals(Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
         state("ladder", 2));
      assertEquals(Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
         state("ladder", 5));
   }

   // ---- Torch: 1.12 standing=5; wall EAST=1 WEST=2 SOUTH=3 NORTH=4 --------------------------------

   @Test
   void torchSplit() {
      // 1.12 evidence: the single "torch" block decoded by meta into a standing torch (5) or a wall torch
      // facing E/W/S/N (1..4). 26.2 split these into TORCH (no facing) and WALL_TORCH (facing).
      assertEquals(Blocks.TORCH.defaultBlockState(), state("torch", 5));
      assertEquals(Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
         state("torch", 1));
      assertEquals(Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
         state("torch", 4));
   }

   // ---- Bed: 1.12 BlockBed meta = facing.get2DDataValue() | (head? 8) ----------------------------

   @Test
   void bedFacingPart() {
      // 1.12 evidence: 2D ids SOUTH0 WEST1 NORTH2 EAST3; HEAD sets bit 8. blockStateToLegacyMeta reproduces.
      // 26.2 beds are a ColorCollection (no Blocks.RED_BED constant), so derive the golden block from the
      // registry — the same approach MillConvertTest.noProperties uses for wool.
      BlockState redBed = net.minecraft.core.registries.BuiltInRegistries.BLOCK
         .getValue(net.minecraft.resources.Identifier.parse("red_bed")).defaultBlockState();
      assertEquals(redBed
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            .setValue(BlockStateProperties.BED_PART, BedPart.FOOT),
         state("red_bed", 0));
      assertEquals(redBed
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
            .setValue(BlockStateProperties.BED_PART, BedPart.HEAD),
         state("red_bed", 11));
   }

   // ---- Unbounded numeric families (NOT enumerated by name; applied property-driven) -------------

   @Test
   void cropAge7() {
      // 1.12 evidence: BlockCrops meta = AGE (0..7), clamped. WorldUtilities.legacyMetaToBlockState used AGE_7.
      assertEquals(Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 5),
         state("wheat", 5));
      // meta beyond max clamps to 7 (Math.min(meta,7)), matching the former WorldUtilities behaviour.
      assertEquals(Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7),
         state("wheat", 9));
   }

   @Test
   void netherWartAge3() {
      // 1.12 evidence: BlockNetherWart meta = AGE (0..3). Former code used AGE_3 with Math.min(meta,3).
      assertEquals(Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 2),
         state("nether_wart", 2));
   }

   @Test
   void farmlandMoisture() {
      // 1.12 evidence: BlockFarmland meta = MOISTURE (0..7); former code used (meta & 7) clamped to 7.
      assertEquals(Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 6),
         state("farmland", 6));
   }

   @Test
   void zeroMetaIsDefault() {
      // 1.12 fast path: meta <= 0 keeps the default state.
      assertEquals(Blocks.WHEAT.defaultBlockState(), state("wheat", 0));
   }

   @Test
   void blockOverload() {
      // The (Block, meta) overload WorldUtilities.setBlockAndMetadata uses must match the (name, meta) one.
      assertEquals(state("spruce_log", 4), MillConvert.blockState(Blocks.SPRUCE_LOG, 4));
      assertEquals(state("wheat", 5), MillConvert.blockState(Blocks.WHEAT, 5));
   }

   // ---- Fail-fast --------------------------------------------------------------------------------

   @Test
   void unknownLegacyBlockFails() {
      // A legacy name with no modern equivalent and no numeric property must crash, not place AIR.
      assertThrows(RuntimeException.class, () -> state("definitely_not_a_block", 1));
   }
}
