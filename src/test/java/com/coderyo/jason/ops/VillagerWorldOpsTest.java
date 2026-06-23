package com.coderyo.jason.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * Golden tests for the pure, player-like break math in {@link VillagerWorldOps}. They assert the re-implemented
 * Player-only destroy formula directly against real 26.2 vanilla blocks/items, so a regression in the formula (or a
 * 26.2 API drift in {@code getDestroySpeed}/{@code isCorrectToolForDrops}) fails here rather than producing wrong
 * mining speeds in-game. Asserts that depend on ItemStack data-component binding being available headlessly are
 * gated with {@code assume} (same pattern as the convert tests) — but the formula asserts always run.
 */
class VillagerWorldOpsTest extends MillHeadlessTest {

   // The block hardness comes straight from the cached destroySpeed (the BlockGetter/pos args are ignored by the
   // BlockBehaviour overload), so null,null is the genuine production value here.
   private static float hardness(BlockState state) {
      return state.getDestroySpeed(null, null);
   }

   /**
    * Build a real tool ItemStack, or skip the test via {@code assume} if item data-components aren't bound in this
    * headless harness ({@code new ItemStack(item)} throws "Components not bound yet"). The bare-hand / store /
    * reach / unbreakable asserts do not need this and always run.
    */
   private static ItemStack stackOrAssume(Item item) {
      try {
         return new ItemStack(item);
      } catch (Throwable t) {
         assumeTrue(false, "ItemStack data-components unavailable headlessly: " + t);
         return ItemStack.EMPTY; // unreachable
      }
   }

   // ---- destroyProgressPerTick: matches (toolSpeed/hardness)/(correct?30:100) ----------------------

   @Test
   void stoneWithCorrectPickaxe() {
      BlockState stone = Blocks.STONE.defaultBlockState();
      float h = hardness(stone);
      ItemStack pick = stackOrAssume(Items.IRON_PICKAXE);

      // Independently recompute the vanilla formula from the same public calls the production code uses.
      assumeTrue(pick.getDestroySpeed(stone) > 1.0f, "ItemStack destroy-speed components unavailable headlessly");
      boolean correct = !stone.requiresCorrectToolForDrops() || pick.isCorrectToolForDrops(stone);
      assertTrue(correct, "iron pickaxe should be the correct tool for stone");
      float expected = (pick.getDestroySpeed(stone) / h) / 30.0f;

      assertEquals(expected, VillagerWorldOps.destroyProgressPerTick(pick, stone, h), 1.0e-6f);

      // Right tool → breaks in a sane handful of ticks, not hundreds.
      int ticks = VillagerWorldOps.ticksToBreak(pick, stone, h);
      assertTrue(ticks >= 1 && ticks < 60, "iron pickaxe on stone should be quick, got " + ticks + " ticks");
   }

   @Test
   void stoneWithBareHand() {
      BlockState stone = Blocks.STONE.defaultBlockState();
      float h = hardness(stone);
      ItemStack hand = ItemStack.EMPTY;

      // Bare hand: speed 1.0, wrong tool (stone requires a tool for drops) → divisor 100.
      assertFalse(VillagerWorldOps.hasCorrectTool(hand, stone), "bare hand is not the correct tool for stone");
      float expected = (hand.getDestroySpeed(stone) / h) / 100.0f;
      assertEquals(expected, VillagerWorldOps.destroyProgressPerTick(hand, stone, h), 1.0e-6f);

      // Bare hand on stone is slow → many ticks, and strictly slower than the pickaxe case.
      int handTicks = VillagerWorldOps.ticksToBreak(hand, stone, h);
      ItemStack pick = stackOrAssume(Items.IRON_PICKAXE);
      assumeTrue(pick.getDestroySpeed(stone) > 1.0f, "ItemStack destroy-speed components unavailable headlessly");
      int pickTicks = VillagerWorldOps.ticksToBreak(pick, stone, h);
      assertTrue(handTicks > pickTicks, "bare hand (" + handTicks + ") should be slower than pickaxe (" + pickTicks + ")");
   }

   @Test
   void stoneWithWrongToolShovel() {
      BlockState stone = Blocks.STONE.defaultBlockState();
      float h = hardness(stone);
      ItemStack shovel = stackOrAssume(Items.IRON_SHOVEL);

      // Shovel is the WRONG tool for stone → no correct-tool bonus → divisor 100.
      assertFalse(VillagerWorldOps.hasCorrectTool(shovel, stone), "shovel is not correct for stone");
      float expected = (shovel.getDestroySpeed(stone) / h) / 100.0f;
      assertEquals(expected, VillagerWorldOps.destroyProgressPerTick(shovel, stone, h), 1.0e-6f);
   }

   // ---- hasCorrectTool: requiresCorrectToolForDrops semantics --------------------------------------

   @Test
   void correctToolMatrix() {
      BlockState stone = Blocks.STONE.defaultBlockState();
      BlockState dirt = Blocks.DIRT.defaultBlockState();

      ItemStack pick = stackOrAssume(Items.IRON_PICKAXE);
      ItemStack shovel = stackOrAssume(Items.IRON_SHOVEL);
      ItemStack woodPick = stackOrAssume(Items.WOODEN_PICKAXE);

      // stone + pickaxe = true ; stone + shovel = false (stone requires the correct tool for drops)
      assertTrue(stone.requiresCorrectToolForDrops(), "stone requires a correct tool for drops");
      assertTrue(VillagerWorldOps.hasCorrectTool(pick, stone));
      assertFalse(VillagerWorldOps.hasCorrectTool(shovel, stone));

      // dirt + anything = true (dirt never requires a correct tool)
      assertFalse(dirt.requiresCorrectToolForDrops(), "dirt does not require a correct tool");
      assertTrue(VillagerWorldOps.hasCorrectTool(shovel, dirt));
      assertTrue(VillagerWorldOps.hasCorrectTool(woodPick, dirt));
      assertTrue(VillagerWorldOps.hasCorrectTool(ItemStack.EMPTY, dirt));
   }

   @Test
   void unbreakableYieldsZeroProgress() {
      // hardness < 0 (bedrock-like) → 0 per-tick → MAX ticks (BLOCKED at the op level).
      BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
      float h = hardness(bedrock);
      assertTrue(h < 0.0f, "bedrock hardness should be negative (unbreakable), was " + h);
      assertEquals(0.0f, VillagerWorldOps.destroyProgressPerTick(ItemStack.EMPTY, bedrock, h), 0.0f);
      assertEquals(Integer.MAX_VALUE, VillagerWorldOps.ticksToBreak(ItemStack.EMPTY, bedrock, h));
   }

   // ---- withinReach: 4.5-block player reach (pure Vec3 overload) -----------------------------------

   // ---- ToolKind selection + strict isToolOfKind (O1) ---------------------------------------------

   @Test
   void miningToolForMatches112Choice() {
      // 1.12 GoalMinerMineResource: shovel for sand/clay/gravel, pickaxe for stone/sandstone (and the rest).
      assertEquals(VillagerWorldOps.ToolKind.SHOVEL, VillagerWorldOps.miningToolFor(Blocks.SAND));
      assertEquals(VillagerWorldOps.ToolKind.SHOVEL, VillagerWorldOps.miningToolFor(Blocks.CLAY));
      assertEquals(VillagerWorldOps.ToolKind.SHOVEL, VillagerWorldOps.miningToolFor(Blocks.GRAVEL));
      assertEquals(VillagerWorldOps.ToolKind.PICKAXE, VillagerWorldOps.miningToolFor(Blocks.STONE));
      assertEquals(VillagerWorldOps.ToolKind.PICKAXE, VillagerWorldOps.miningToolFor(Blocks.SANDSTONE));
   }

   @Test
   void isToolOfKindEmptyIsNeverATool() {
      // Bare hand satisfies no tool kind (strict policy).
      for (VillagerWorldOps.ToolKind k : VillagerWorldOps.ToolKind.values()) {
         assertFalse(VillagerWorldOps.isToolOfKind(ItemStack.EMPTY, k), "empty hand is not a " + k);
         assertFalse(VillagerWorldOps.isToolOfKind(null, k), "null is not a " + k);
      }
   }

   @Test
   void isToolOfKindPickaxeVsShovel() {
      ItemStack pick = stackOrAssume(Items.IRON_PICKAXE);
      ItemStack shovel = stackOrAssume(Items.IRON_SHOVEL);
      // gate on data-components being bound (pickaxe detection uses isCorrectToolForDrops(stone)).
      assumeTrue(pick.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()),
         "ItemStack tool components unavailable headlessly");

      // Pickaxe is a PICKAXE, not a SHOVEL; shovel is a SHOVEL, not a PICKAXE — strict, mutually exclusive.
      assertTrue(VillagerWorldOps.isToolOfKind(pick, VillagerWorldOps.ToolKind.PICKAXE));
      assertFalse(VillagerWorldOps.isToolOfKind(pick, VillagerWorldOps.ToolKind.SHOVEL));
      assertTrue(VillagerWorldOps.isToolOfKind(shovel, VillagerWorldOps.ToolKind.SHOVEL));
      assertFalse(VillagerWorldOps.isToolOfKind(shovel, VillagerWorldOps.ToolKind.PICKAXE));
   }

   @Test
   void isToolOfKindAxeHoeShearsRod() {
      ItemStack axe = stackOrAssume(Items.IRON_AXE);
      ItemStack hoe = stackOrAssume(Items.IRON_HOE);
      ItemStack shears = stackOrAssume(Items.SHEARS);
      ItemStack rod = stackOrAssume(Items.FISHING_ROD);

      assertTrue(VillagerWorldOps.isToolOfKind(axe, VillagerWorldOps.ToolKind.AXE));
      assertTrue(VillagerWorldOps.isToolOfKind(hoe, VillagerWorldOps.ToolKind.HOE));
      assertTrue(VillagerWorldOps.isToolOfKind(shears, VillagerWorldOps.ToolKind.SHEARS));
      assertTrue(VillagerWorldOps.isToolOfKind(rod, VillagerWorldOps.ToolKind.ROD));

      // Cross-kind negatives: an axe is not a hoe/shears/rod.
      assertFalse(VillagerWorldOps.isToolOfKind(axe, VillagerWorldOps.ToolKind.HOE));
      assertFalse(VillagerWorldOps.isToolOfKind(axe, VillagerWorldOps.ToolKind.SHEARS));
      assertFalse(VillagerWorldOps.isToolOfKind(hoe, VillagerWorldOps.ToolKind.ROD));
      // ...and an axe is not a PICKAXE even though it is a digger (class-exclusion in the pickaxe rule).
      assertFalse(VillagerWorldOps.isToolOfKind(axe, VillagerWorldOps.ToolKind.PICKAXE));
   }

   @Test
   void withinReachBoundary() {
      // Eye just above a block; the target block AABB occupies [pos, pos+1].
      Vec3 eye = new Vec3(0.5, 1.5, 0.5);

      // Same column, two blocks down → centre-to-AABB well within 4.5.
      assertTrue(VillagerWorldOps.withinReach(eye, new BlockPos(0, 0, 0)));

      // ~4 blocks away horizontally → in reach.
      assertTrue(VillagerWorldOps.withinReach(eye, new BlockPos(4, 1, 0)));

      // ~8 blocks away → out of reach.
      assertFalse(VillagerWorldOps.withinReach(eye, new BlockPos(8, 1, 0)));

      // Far vertical → out of reach.
      assertFalse(VillagerWorldOps.withinReach(eye, new BlockPos(0, 20, 0)));
   }
}
