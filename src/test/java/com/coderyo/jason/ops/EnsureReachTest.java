package com.coderyo.jason.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;

/**
 * Golden tests for the O2 scaffold-first reach-extension planning ({@link VillagerWorldOps#ensureReach} core) and
 * the strict AXE tool selection used by the migrated lumberman chop goal. The planning math
 * ({@link VillagerWorldOps#plannedColumnHeight}) is pure (no level/villager) so it is asserted directly; the
 * point-owned reclaim list is asserted through the same {@link TaskPointStore.Progress#scaffoldColumn} tracking the
 * live op uses.
 */
class EnsureReachTest extends MillHeadlessTest {

   /** A villager eye sits ~1.62 above its feet; the planning helper takes the eye explicitly so this is exact. */
   private static final double EYE_OFFSET = 1.62;

   private static Vec3 eyeAt(BlockPos feet) {
      return new Vec3(feet.getX() + 0.5, feet.getY() + EYE_OFFSET, feet.getZ() + 0.5);
   }

   // ---- plannedColumnHeight: target above → needs N scaffolds -------------------------------------

   @Test
   void targetAlreadyInReachNeedsNoColumn() {
      // Feet at y=64; a log two blocks up in the same column is well within 4.5 → height 0 (no scaffold needed).
      BlockPos feet = new BlockPos(0, 64, 0);
      BlockPos log = new BlockPos(0, 66, 0);
      assertEquals(0, VillagerWorldOps.plannedColumnHeight(eyeAt(feet), feet, log));
   }

   @Test
   void tallTrunkNeedsAColumnAndItBringsTheTargetIntoReach() {
      // A tall-tree upper log far above the feet → needs a positive column height. Verify (a) it's > 0, and
      // (b) standing on a column of exactly that height actually puts the target within reach (the contract).
      BlockPos feet = new BlockPos(0, 64, 0);
      BlockPos highLog = new BlockPos(0, 78, 0); // 14 blocks up — out of the 4.5 reach from the ground.

      assertFalse(VillagerWorldOps.withinReach(eyeAt(feet), highLog), "14 blocks up must start out of reach");

      int needed = VillagerWorldOps.plannedColumnHeight(eyeAt(feet), feet, highLog);
      assertTrue(needed > 0, "a tall trunk must need a scaffold column, got " + needed);

      // Standing on a column of `needed` blocks: feet rise by `needed`, eye rides EYE_OFFSET above → now in reach.
      BlockPos standFeet = feet.above(needed);
      assertTrue(VillagerWorldOps.withinReach(eyeAt(standFeet), highLog),
         "after climbing the planned column the target must be in reach");

      // And one block shorter must NOT yet be enough (the plan is minimal, not padded).
      if (needed >= 1) {
         BlockPos oneShort = feet.above(needed - 1);
         assertFalse(VillagerWorldOps.withinReach(eyeAt(oneShort), highLog),
            "the planned column height must be the minimal sufficient height");
      }
   }

   @Test
   void plannedHeightGrowsWithTargetHeight() {
      BlockPos feet = new BlockPos(0, 64, 0);
      int hLow = VillagerWorldOps.plannedColumnHeight(eyeAt(feet), feet, new BlockPos(0, 72, 0));
      int hHigh = VillagerWorldOps.plannedColumnHeight(eyeAt(feet), feet, new BlockPos(0, 80, 0));
      assertTrue(hHigh > hLow, "a higher log needs a taller column (" + hLow + " vs " + hHigh + ")");
   }

   @Test
   void targetFarToTheSideCannotBeReachedByAVerticalColumn() {
      // A target 10 blocks horizontally away, at feet level: no vertical column ever brings it within 4.5 → -1.
      BlockPos feet = new BlockPos(0, 64, 0);
      BlockPos sideways = new BlockPos(10, 64, 0);
      assertEquals(-1, VillagerWorldOps.plannedColumnHeight(eyeAt(feet), feet, sideways),
         "a sideways-out-of-reach target can't be fixed by stacking straight up");
   }

   // ---- column positions: lowest-first, one block per level --------------------------------------

   @Test
   void columnPositionsStackStraightUpFromFeet() {
      BlockPos feet = new BlockPos(5, 64, -3);
      assertEquals(new BlockPos(5, 64, -3), VillagerWorldOps.columnPosForLevel(feet, 0));
      assertEquals(new BlockPos(5, 65, -3), VillagerWorldOps.columnPosForLevel(feet, 1));
      assertEquals(new BlockPos(5, 67, -3), VillagerWorldOps.columnPosForLevel(feet, 3));
   }

   // ---- reclaim list correctness (point-owned scaffold tracking) ----------------------------------

   @Test
   void scaffoldTrackingIsOrderedDedupedAndTopDownReclaimable() {
      TaskPointStore.Progress p = new TaskPointStore.Progress();
      assertTrue(p.scaffoldColumn.isEmpty(), "a fresh point has no scaffold column");

      BlockPos feet = new BlockPos(0, 64, 0);
      // Track a 3-block column, lowest-first, as the live op does (place at feet, climb, place at new feet…).
      p.trackScaffold(VillagerWorldOps.columnPosForLevel(feet, 0)); // y64
      p.trackScaffold(VillagerWorldOps.columnPosForLevel(feet, 1)); // y65
      p.trackScaffold(VillagerWorldOps.columnPosForLevel(feet, 2)); // y66
      p.trackScaffold(VillagerWorldOps.columnPosForLevel(feet, 1)); // duplicate — must be ignored.

      assertEquals(3, p.scaffoldColumn.size(), "duplicates must not grow the column");

      // The reclaim order is top-down (highest Y first) so a column never collapses onto a lower block mid-reclaim.
      List<Long> reclaimOrder = new ArrayList<>(p.scaffoldColumn);
      reclaimOrder.sort((a, b) -> Integer.compare(BlockPos.of(b).getY(), BlockPos.of(a).getY()));
      assertEquals(66, BlockPos.of(reclaimOrder.get(0)).getY(), "reclaim starts at the TOP of the column");
      assertEquals(65, BlockPos.of(reclaimOrder.get(1)).getY());
      assertEquals(64, BlockPos.of(reclaimOrder.get(2)).getY(), "reclaim ends at the column base");
   }

   // ---- AXE tool selection (strict) ---------------------------------------------------------------

   private static ItemStack stackOrAssume(Item item) {
      try {
         return new ItemStack(item);
      } catch (Throwable t) {
         assumeTrue(false, "ItemStack data-components unavailable headlessly: " + t);
         return ItemStack.EMPTY;
      }
   }

   @Test
   void axeIsAxeAndNothingElseIsChosenForChop() {
      ItemStack axe = stackOrAssume(Items.IRON_AXE);
      ItemStack pick = stackOrAssume(Items.IRON_PICKAXE);
      ItemStack hoe = stackOrAssume(Items.IRON_HOE);

      // The chop goal asks ensureTool(AXE); the strict gate accepts only an axe.
      assertTrue(VillagerWorldOps.isToolOfKind(axe, VillagerWorldOps.ToolKind.AXE), "an axe satisfies AXE");
      assertFalse(VillagerWorldOps.isToolOfKind(pick, VillagerWorldOps.ToolKind.AXE), "a pickaxe is not an axe");
      assertFalse(VillagerWorldOps.isToolOfKind(hoe, VillagerWorldOps.ToolKind.AXE), "a hoe is not an axe");
      assertFalse(VillagerWorldOps.isToolOfKind(ItemStack.EMPTY, VillagerWorldOps.ToolKind.AXE), "bare hand is not an axe");
   }
}
