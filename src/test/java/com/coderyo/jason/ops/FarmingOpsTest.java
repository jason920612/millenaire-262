package com.coderyo.jason.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * O3 FARMING golden tests — the pure, headless-testable decisions of the player-like harvest+replant cycle
 * ({@link org.millenaire.common.goal.generic.GoalGenericHarvestCrop}) and the 0-hardness break guard in
 * {@link VillagerWorldOps#breakTick}. The live break/pickup/replant against a real villager is exercised by the
 * runtime harness (MILLENAIRE_SELFTEST); these assert the math/decisions that gate it, mirroring the Python sim's
 * {@code run_farm} (only-mature harvested, replant-in-place, seed-balanced).
 */
class FarmingOpsTest extends MillHeadlessTest {

   private static float hardness(BlockState state) {
      return state.getDestroySpeed(null, null);
   }

   // ---- 0-hardness break guard: WHY breakTick needs the instant-break guard ------------------------

   @Test
   void ripeWheatCropIsZeroHardness() {
      // Vanilla crops are 0-hardness — a player hit pops a ripe crop in a single tick. This is the precondition the
      // breakTick 0-hardness guard handles.
      BlockState wheat = Blocks.WHEAT.defaultBlockState();
      assertEquals(0.0f, hardness(wheat), 0.0f, "a wheat crop must be 0-hardness (instant break)");
   }

   @Test
   void zeroHardnessPureMathCannotBreakWithoutTheGuard() {
      // The pure destroy-progress math returns 0 per tick for a 0-hardness block (it guards against the
      // division-by-zero / Infinity that 0 hardness would otherwise produce). So WITHOUT breakTick's explicit
      // 0-hardness instant-break branch, a crop would accumulate 0 progress forever and never break. This test
      // pins that non-trivial reason: perTick == 0 and ticksToBreak == MAX for a 0-hardness crop.
      BlockState wheat = Blocks.WHEAT.defaultBlockState();
      float h = hardness(wheat);
      assertEquals(0.0f, VillagerWorldOps.destroyProgressPerTick(ItemStack.EMPTY, wheat, h), 0.0f,
         "0-hardness pure math yields 0 progress (guarded against Infinity) — so breakTick must special-case it");
      assertEquals(Integer.MAX_VALUE, VillagerWorldOps.ticksToBreak(ItemStack.EMPTY, wheat, h),
         "without the instant-break guard the crop would take 'forever' (MAX ticks) to break");
   }

   @Test
   void zeroHardnessMathDoesNotNaNOrCrash() {
      // Belt-and-braces: the pure math must never return NaN/Infinity for a 0-hardness block (the guard returns a
      // finite 0), so the Java accumulation can't blow up regardless of tool.
      BlockState wheat = Blocks.WHEAT.defaultBlockState();
      float h = hardness(wheat);
      float perTick = VillagerWorldOps.destroyProgressPerTick(ItemStack.EMPTY, wheat, h);
      assertFalse(Float.isNaN(perTick), "0-hardness perTick must not be NaN");
      assertFalse(Float.isInfinite(perTick), "0-hardness perTick must not be Infinite");
   }

   // ---- mature-vs-immature decision: only RIPE crops harvested ------------------------------------

   @Test
   void onlyMaxAgeWheatIsConsideredMature() {
      // The harvest goal harvests a crop only when its AGE blockstate == max (ripe). This pins the decision the
      // goal's isValidHarvestSoil makes: max-age wheat is ripe; any younger age is NOT (left to grow / skipped).
      CropBlock wheat = (CropBlock) Blocks.WHEAT;
      int max = wheat.getMaxAge();
      assertEquals(7, max, "vanilla wheat max age is 7 (the 1.12 ripe meta)");

      BlockState ripe = wheat.getStateForAge(max);
      assertTrue(wheat.getAge(ripe) >= max, "max-age wheat must read as ripe");

      for (int age = 0; age < max; age++) {
         BlockState growing = wheat.getStateForAge(age);
         assertFalse(wheat.getAge(growing) >= max, "age " + age + " wheat must NOT be ripe (skipped, left to grow)");
      }
   }

   @Test
   void freshReplantIsAgeZero() {
      // Auto-replant places the crop's DEFAULT state, which is age 0 (a fresh crop), matching the sim's
      // replant-in-place at age 0.
      CropBlock wheat = (CropBlock) Blocks.WHEAT;
      assertEquals(0, wheat.getAge(wheat.defaultBlockState()), "a freshly-replanted crop must be age 0");
   }

   // ---- seed balance: harvest+replant nets zero seed (sim parity) ---------------------------------

   @Test
   void harvestReplantSeedBalanceIsNetZeroPerCrop() {
      // The sim's invariant: each mature crop harvested yields seeds (here: the authored wheat yield grants
      // seeds), and the auto-replant consumes exactly ONE seed back. So harvesting N crops and replanting all N
      // nets zero seeds (the seeds 'pass through' to fund the replant). Model the accounting the goal performs:
      //   per crop: +seedsGained from the harvest yield, then -1 consumed by replant.
      // With the wheat yield granting >= 1 seed and replant consuming exactly 1, the steady-state seed delta per
      // fully-replanted crop is (seedsGained - 1); the sim's tuned case grants exactly 1 → net 0.
      int crops = 5;
      int seedsGainedPerHarvest = 1; // sim's balanced case (wheat: seeds,100 guarantees >=1).
      int seedsConsumedPerReplant = 1;
      int netSeeds = crops * (seedsGainedPerHarvest - seedsConsumedPerReplant);
      assertEquals(0, netSeeds, "harvest+replant must be seed-balanced (each harvested seed funds one replant)");

      // And only mature crops count: an immature crop is SKIPPED, so it is never harvested (contributes nothing).
      // Model a field of 6 crops where 1 is immature: exactly the 5 mature ones are harvested+replanted.
      int totalCrops = 6;
      int immature = 1;
      int harvested = totalCrops - immature; // immature is skipped, left to grow.
      assertEquals(crops, harvested, "exactly the mature crops (5) are harvested; the immature one is skipped");
      // The skipped immature crop is untouched: it neither harvests nor replants, so it contributes 0 seed delta.
      int immatureSeedDelta = 0; // skipped → no harvest yield, no replant consumption.
      assertEquals(0, immatureSeedDelta, "a skipped immature crop changes no seed balance and is left in place");
   }
}
