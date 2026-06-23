package com.coderyo.jason.ops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * Headless-testable surface of the O5 ENTITY-GATHER ops ({@link VillagerWorldOps#shearTick}/{@code milkTick}). The
 * shear/milk interactions themselves require a live {@code Sheep}/{@code Cow} + a {@code ServerLevel} (real loot
 * tables, drop spawning, entity reach) which only exist in the in-game harness (MillSelfTest H6 SHEARCYCLE); so the
 * full real-shear behaviour is asserted there. Here we lock the pure pieces O5 relies on: the STRICT shears tool
 * gate and the bucket→milk_bucket item identities, so a 26.2 API drift fails in CI rather than in-game.
 */
class EntityGatherOpsTest extends MillHeadlessTest {

   private static ItemStack stackOrAssume(Item item) {
      try {
         return new ItemStack(item);
      } catch (Throwable t) {
         assumeTrue(false, "ItemStack data-components unavailable headlessly: " + t);
         return ItemStack.EMPTY; // unreachable
      }
   }

   // ---- STRICT shears gate: shearTick equips/accepts only real shears (else BLOCKED → defer to GoalGetTool) -------

   @Test
   void shearsAreTheOnlyShearTool() {
      // Empty / null never satisfy the SHEARS kind (these need no data-components, so they ALWAYS run): shearTick on a
      // toolless villager returns BLOCKED (strict, no faked wool) rather than shearing.
      assertFalse(VillagerWorldOps.isToolOfKind(ItemStack.EMPTY, VillagerWorldOps.ToolKind.SHEARS));
      assertFalse(VillagerWorldOps.isToolOfKind(null, VillagerWorldOps.ToolKind.SHEARS));

      // Real shears satisfy it; a wrong tool (axe) does not (gated on item data-components being bound headlessly).
      ItemStack shears = stackOrAssume(Items.SHEARS);
      ItemStack axe = stackOrAssume(Items.IRON_AXE);
      assertTrue(VillagerWorldOps.isToolOfKind(shears, VillagerWorldOps.ToolKind.SHEARS));
      assertFalse(VillagerWorldOps.isToolOfKind(axe, VillagerWorldOps.ToolKind.SHEARS));
   }

   // ---- Milk: the bucket→milk_bucket identities the milkTick consumes/produces are the vanilla items --------------

   @Test
   void milkConsumesEmptyBucketProducesMilkBucket() {
      // milkTick consumes minecraft:bucket from stock and produces minecraft:milk_bucket — assert these are distinct
      // vanilla items it references (a registry rename would surface here). No stack needed → ALWAYS runs.
      assertTrue(Items.BUCKET != Items.MILK_BUCKET, "bucket and milk_bucket must be distinct items");

      // Deeper identity checks need data-components bound (gated like the other ops tests).
      ItemStack bucket = stackOrAssume(Items.BUCKET);
      ItemStack milk = stackOrAssume(Items.MILK_BUCKET);
      assertTrue(bucket.is(Items.BUCKET));
      assertTrue(milk.is(Items.MILK_BUCKET));
      // An empty bucket is not already a milk bucket (so milking is a real state change, like the sim's milk_tick).
      assertFalse(bucket.is(Items.MILK_BUCKET));
   }
}
