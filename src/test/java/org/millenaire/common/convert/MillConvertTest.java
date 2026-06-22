package org.millenaire.common.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * Golden tests for the M0 dot-notation block-state parser ({@link MillConvert#dotSpecToBlockState}).
 * Expected states are derived from real 26.2 vanilla blocks/properties (the same headless registry the
 * nav / LexCost tests use), so a regression in the parser fails here rather than producing wrong blocks
 * in the building system later.
 */
class MillConvertTest extends MillHeadlessTest {

   private static BlockState parse(String spec) {
      return MillConvert.dotSpecToBlockState(new DotSpec(spec));
   }

   // ---- happy path -------------------------------------------------------------------------------

   @Test
   void enumPropertiesStairs() {
      BlockState expected = Blocks.OAK_STAIRS.defaultBlockState()
         .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH)
         .setValue(BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
      assertEquals(expected, parse("oak_stairs.facing(south).half(top)"));
   }

   @Test
   void wallTorchFacing() {
      BlockState expected = Blocks.WALL_TORCH.defaultBlockState()
         .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST);
      assertEquals(expected, parse("wall_torch.facing(east)"));
   }

   @Test
   void slabType() {
      BlockState expected = Blocks.OAK_SLAB.defaultBlockState()
         .setValue(BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
      assertEquals(expected, parse("oak_slab.type(top)"));
   }

   @Test
   void logAxis() {
      BlockState expected = Blocks.OAK_LOG.defaultBlockState()
         .setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y);
      assertEquals(expected, parse("oak_log.axis(y)"));
   }

   @Test
   void integerPropertyMaxKeyword() {
      BlockState expected = Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7);
      assertEquals(expected, parse("wheat.age(max)"));
   }

   @Test
   void integerPropertyValueOverMax() {
      BlockState expected = Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 2);
      assertEquals(expected, parse("wheat.age(2/7)"));
   }

   @Test
   void integerPropertyMinKeyword() {
      BlockState expected = Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 0);
      assertEquals(expected, parse("water.level(min)"));
   }

   @Test
   void noProperties() {
      // red_wool has no field on Blocks in 26.2 (wool is a ColorCollection), so derive the golden state
      // from the same registry the parser uses — this still exercises the "block, no properties" path.
      BlockState expected = net.minecraft.core.registries.BuiltInRegistries.BLOCK
         .getValue(net.minecraft.resources.Identifier.parse("red_wool")).defaultBlockState();
      assertEquals(expected, parse("red_wool"));
   }

   // ---- fail-fast --------------------------------------------------------------------------------

   @Test
   void unknownPropertyFails() {
      // standing torch has no 'facing' property
      assertThrows(RuntimeException.class, () -> parse("torch.facing(east)"));
   }

   @Test
   void integerOutOfRangeFails() {
      assertThrows(RuntimeException.class, () -> parse("wheat.age(8/7)"));
   }

   @Test
   void integerWrongDeclaredMaxFails() {
      assertThrows(RuntimeException.class, () -> parse("wheat.age(2/15)"));
   }

   @Test
   void integerBareNumberFails() {
      assertThrows(RuntimeException.class, () -> parse("wheat.age(2)"));
   }
}
