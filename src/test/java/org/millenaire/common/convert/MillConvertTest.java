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

   // ---- goal-data flower flattening (red_flower / yellow_flower / double_plant) ------------------

   private static BlockState goal(String spec) {
      return MillConvert.goalBlockState(spec);
   }

   private static BlockState vanilla(String id) {
      return net.minecraft.core.registries.BuiltInRegistries.BLOCK
         .getValue(net.minecraft.resources.Identifier.parse(id)).defaultBlockState();
   }

   @Test
   void redFlowerPoppyFlattens() {
      assertEquals(vanilla("minecraft:poppy"), goal("red_flower;type=poppy"));
   }

   @Test
   void redFlowerHoustoniaIsAzureBluet() {
      assertEquals(vanilla("minecraft:azure_bluet"), goal("red_flower;type=houstonia"));
   }

   @Test
   void redFlowerVariantsFlatten() {
      assertEquals(vanilla("minecraft:red_tulip"), goal("red_flower;type=red_tulip"));
      assertEquals(vanilla("minecraft:orange_tulip"), goal("red_flower;type=orange_tulip"));
      assertEquals(vanilla("minecraft:white_tulip"), goal("red_flower;type=white_tulip"));
      assertEquals(vanilla("minecraft:pink_tulip"), goal("red_flower;type=pink_tulip"));
      assertEquals(vanilla("minecraft:blue_orchid"), goal("red_flower;type=blue_orchid"));
      assertEquals(vanilla("minecraft:allium"), goal("red_flower;type=allium"));
      assertEquals(vanilla("minecraft:oxeye_daisy"), goal("red_flower;type=oxeye_daisy"));
   }

   @Test
   void yellowFlowerIsDandelion() {
      assertEquals(vanilla("minecraft:dandelion"), goal("yellow_flower;type=dandelion"));
      assertEquals(vanilla("minecraft:dandelion"), goal("yellow_flower"));
   }

   @Test
   void doublePlantRoseKeepsLowerHalf() {
      BlockState expected = vanilla("minecraft:rose_bush")
         .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
      assertEquals(expected, goal("double_plant;facing=north,half=lower,variant=double_rose"));
   }

   @Test
   void doublePlantVariantsFlatten() {
      assertEquals(vanilla("minecraft:sunflower").getBlock(), goal("double_plant;variant=sunflower,half=lower").getBlock());
      assertEquals(vanilla("minecraft:lilac").getBlock(), goal("double_plant;variant=syringa,half=lower").getBlock());
      assertEquals(vanilla("minecraft:tall_grass").getBlock(), goal("double_plant;variant=double_grass,half=lower").getBlock());
      assertEquals(vanilla("minecraft:large_fern").getBlock(), goal("double_plant;variant=double_fern,half=lower").getBlock());
      assertEquals(vanilla("minecraft:peony").getBlock(), goal("double_plant;variant=paeonia,half=lower").getBlock());
   }

   @Test
   void flowersNeverResolveToAir() {
      assertEquals(false, goal("red_flower;type=poppy").isAir());
      assertEquals(false, goal("yellow_flower").isAir());
      assertEquals(false, goal("double_plant;variant=double_rose,half=lower").isAir());
   }

   @Test
   void unknownFlowerVariantFailsFast() {
      assertThrows(RuntimeException.class, () -> goal("red_flower;type=bogus"));
      assertThrows(RuntimeException.class, () -> goal("double_plant;variant=bogus,half=lower"));
      assertThrows(RuntimeException.class, () -> goal("yellow_flower;type=bogus"));
   }

   // ---- mining-goal legacy ids (stone variant, snow_layer) the routing also now flattens -----------

   @Test
   void stoneVariantDioriteFlattens() {
      assertEquals(vanilla("minecraft:diorite"), goal("minecraft:stone;variant=diorite"));
   }

   @Test
   void plainStonePassesThrough() {
      assertEquals(vanilla("minecraft:stone"), goal("minecraft:stone"));
   }

   @Test
   void snowLayerIsSnow() {
      // 1.12 snow_layer -> 26.2 snow (the layer block, not snow_block).
      assertEquals(vanilla("minecraft:snow").getBlock(), goal("minecraft:snow_layer").getBlock());
      assertEquals(false, goal("minecraft:snow_layer").isAir());
   }

   @Test
   void plainVanillaSourceBlocksUnaffected() {
      assertEquals(vanilla("minecraft:sand"), goal("minecraft:sand"));
      assertEquals(vanilla("minecraft:gravel"), goal("minecraft:gravel"));
      assertEquals(vanilla("minecraft:ice"), goal("minecraft:ice"));
   }
}
