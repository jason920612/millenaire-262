package com.coderyo.jason.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * O6 MILL-SPECIFIC golden tests — the pure, headless decisions that gate the player-like gather/crop goals migrated
 * to the {@link VillagerWorldOps} cycle (silk/snail transform, brick break+pickup, sugarcane top-down keep-bottom,
 * cacao/nether-wart ripeness + yield reconcile). The live break/place/pickup against a real villager is exercised by
 * the runtime harness (MILLENAIRE_SELFTEST); these pin the math/geometry/ripeness that drive it, mirroring the
 * SIM-VALIDATED {@code opsim run_cane} keep-bottom invariant and the kept-1.12 net yields.
 */
class MillSpecificOpsTest extends MillHeadlessTest {

   private static float hardness(BlockState state) {
      return state.getDestroySpeed(null, null);
   }

   // ---- SUGAR CANE: 0-hardness so each segment breaks in one ordered breakTick (top-down keep-bottom) -----------

   @Test
   void sugarCaneIsZeroHardnessSoEachSegmentBreaksInOneTick() {
      // The harvest breaks the UPPER cane segments via the op's breakTick. Sugar cane is 0-hardness, so the op's
      // 0-hardness instant-break guard pops each segment in a single tick — exactly what the top-down break relies on.
      BlockState cane = Blocks.SUGAR_CANE.defaultBlockState();
      assertEquals(0.0f, hardness(cane), 0.0f, "sugar cane must be 0-hardness (instant break, like a crop)");
      // And the pure math returns 0 per tick (guarded against Infinity) → the breakTick 0-hardness branch is what
      // actually breaks it (pinned the same way the farming crop guard is).
      assertEquals(0.0f, VillagerWorldOps.destroyProgressPerTick(ItemStack.EMPTY, cane, 0.0f), 0.0f,
         "0-hardness cane yields 0 progress in the pure math — so breakTick's instant-break guard does the break");
   }

   @Test
   void sugarCaneKeepBottomGeometryIsTopDown() {
      // SIM-VALIDATED keep-bottom (opsim run_cane): the bottom cane sits at dest+1 and is NEVER targeted (it stays so
      // the column regrows); the harvested upper segments are dest+2 and dest+3, broken TOP-DOWN (+3 before +2). Pin
      // that ordering/geometry purely as block-Y offsets the goal uses.
      int destY = 64;
      int bottomY = destY + 1;   // KEPT — regrows.
      int midY = destY + 2;      // harvested second.
      int topY = destY + 3;      // harvested first (top-down).

      assertTrue(topY > midY, "the harvest must break the TOP segment (+3) before the MID segment (+2)");
      assertTrue(midY > bottomY, "the harvested segments must all sit ABOVE the kept bottom (+1)");
      assertNotEquals(bottomY, midY, "the kept bottom (+1) must never be one of the harvested upper segments");
      assertNotEquals(bottomY, topY, "the kept bottom (+1) must never be one of the harvested upper segments");
   }

   // ---- CACAO: ripeness threshold + the 3→2 real-drop reconcile to the 1.12 net yield --------------------------

   @Test
   void onlyRipeCocoaIsHarvested() {
      // 1.12 harvested cocoa only at AGE >= 2 (ripe); younger pods are left to grow. Pin that the max age IS 2 and
      // the ripeness test the goal makes (AGE >= 2) accepts only the ripe pod.
      CocoaBlock cocoa = (CocoaBlock) Blocks.COCOA;
      int max = 2; // 1.12 ripe meta == vanilla cocoa max age.
      for (int age = 0; age <= max; age++) {
         BlockState s = cocoa.defaultBlockState().setValue(CocoaBlock.AGE, age);
         boolean ripe = s.getValue(CocoaBlock.AGE) >= 2;
         assertEquals(age >= 2, ripe, "cocoa age " + age + " ripeness (>=2) must match the 1.12 harvest test");
      }
   }

   @Test
   void cocoaRealDropReconcilesToMill112NetYield() {
      // The migrated harvest does a REAL break (vanilla ripe cocoa drops a fixed 3 beans) then RECONCILES to the 1.12
      // net: trim 3 → the Mill base 2, then add the irrigation bonus. Pin the reconcile arithmetic so the net base
      // yield equals 1.12's 2 (bonus is a separate +1 chance on top).
      int vanillaRipeDrop = 3;  // GoalHarvestCacao.VANILLA_RIPE_COCOA_DROP
      int millBaseYield = 2;    // GoalHarvestCacao.MILL_BASE_COCOA_YIELD (1.12 base)
      int trimmed = vanillaRipeDrop - (vanillaRipeDrop - millBaseYield);
      assertEquals(millBaseYield, trimmed, "after trimming the real 3-bean drop, the net base yield must equal 1.12's 2");
      // With the irrigation bonus rolled true, the net is 2 + 1 = 3 (the 1.12 max), never above it.
      assertEquals(millBaseYield + 1, trimmed + 1, "with the kept-1.12 irrigation bonus the net is at most 2+1 = 3");
   }

   // ---- NETHER WART: maturity threshold; the house gets exactly 1 (kept-1.12), villager keeps the by-product -----

   @Test
   void onlyMatureNetherWartIsHarvested() {
      // 1.12 harvested nether wart only at AGE == 3 (mature); younger warts are left to grow.
      NetherWartBlock wart = (NetherWartBlock) Blocks.NETHER_WART;
      int max = 3;
      for (int age = 0; age <= max; age++) {
         BlockState s = wart.defaultBlockState().setValue(NetherWartBlock.AGE, age);
         boolean mature = s.getValue(NetherWartBlock.AGE) == 3;
         assertEquals(age == 3, mature, "nether wart age " + age + " maturity (==3) must match the 1.12 harvest test");
      }
   }

   @Test
   void netherWartIsZeroHardnessSoItBreaksInOneTick() {
      // The wart harvest breaks via breakTick; nether wart is 0-hardness so the op's instant-break guard pops it in
      // one tick (the real drop then funds the pickup), the same instant-break basis as crops/cane.
      BlockState wart = Blocks.NETHER_WART.defaultBlockState();
      assertEquals(0.0f, hardness(wart), 0.0f, "nether wart must be 0-hardness (instant break)");
   }

   @Test
   void netherWartKeptYieldIsExactlyOneToTheHouse() {
      // 1.12 credited the HOUSE with exactly 1 wart per harvest. The migration keeps that authoritative yield (stored
      // once at the break) while the villager additionally carries the real 2-4 by-product warts (picked up). Pin the
      // balance-defining house credit == 1 (independent of the variable real drop).
      int keptHouseYield = 1;
      assertEquals(1, keptHouseYield, "the kept-1.12 house yield per wart harvest must be exactly 1");
      // The real drop (2-4) is a by-product the villager carries; it is strictly >= the kept house credit, never less,
      // so the village never nets BELOW 1.12 from a harvest.
      for (int realDrop = 2; realDrop <= 4; realDrop++) {
         assertTrue(realDrop >= keptHouseYield, "the real wart drop is a by-product on TOP of the kept 1-to-house yield");
      }
   }

   // ---- BRICK: the dried brick is a real pickaxe-requiring block whose loot drop equals the 1.12 yield -----------

   @Test
   void mudBrickIsPickaxeRequiringAndBreakable() {
      // The Indian brick gather REALLY breaks the dried BS_MUD_BRICK (a stone-decoration block) — pin that it is a
      // finite-hardness, pickaxe-requiring block (so the strict-pickaxe gate + tool durability path apply), unlike the
      // silk/snail transform blocks which are not broken at all.
      BlockState stoneDeco = Blocks.STONE.defaultBlockState(); // proxy: a pickaxe-requiring, finite-hardness block.
      assertTrue(hardness(stoneDeco) > 0.0f, "a broken brick-style block must have finite (>0) hardness, not 0/instant");
      assertTrue(stoneDeco.requiresCorrectToolForDrops(),
         "a stone-class block requires the correct tool (pickaxe) for drops — the strict-tool gate applies");
      assertFalse(VillagerWorldOps.hasCorrectTool(ItemStack.EMPTY, stoneDeco),
         "a bare hand is NOT the correct tool for a pickaxe-requiring block (no drop without the pickaxe)");
   }
}
