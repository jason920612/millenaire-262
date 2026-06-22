package org.millenaire.common.ai.behaviours;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.ai.MillBehaviour;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

/**
 * Self-rescue when a villager is TRAPPED in a pit/hole it cannot path out of (e.g. it fell in). It pillars UP
 * — jump and place a block from its own inventory under its feet — and DIGS the block overhead if the climb
 * is capped, until it reaches the surrounding ground level. Emergency-only (high priority, only while
 * genuinely walled in), so it doesn't mess with terrain in normal play.
 *
 * <p>Anti-grief (user design = "medium"): it only breaks NON-STRUCTURAL blocks — never anything inside a Mill
 * building (mw.getBuilding != null) and never unbreakable blocks — so village builds are safe. Placed blocks
 * come from the villager's inventory (a dirt fallback only if it carries nothing, so it can't die stuck).
 */
public final class BehaviourEscapePit implements MillBehaviour {
   private BlockPos pillarSpot;

   @Override
   public boolean canRun(MillVillager villager) {
      // Under ValueFieldNav the unified BehaviourValueFieldEscape (feasibility-gated G1 repair) supersedes
      // this legacy pit-escape; don't run both.
      return !org.millenaire.common.config.MillConfigValues.ValueFieldNav && villager.onGround() && isInPit(villager);
   }

   @Override
   public int priority(MillVillager villager) {
      return 55; // above combat (can't fight from the bottom of a hole), below fluid-escape
   }

   @Override
   public boolean tick(MillVillager villager) {
      if (!isInPit(villager)) {
         return false; // climbed out → done
      }
      Level level = villager.level();
      BlockPos foot = villager.blockPosition();

      // 1. If a block caps the climb directly overhead (above the villager's head), dig it — but only if it's
      //    breakable (not a Mill building block).
      BlockPos overhead = foot.above(2);
      if (!level.getBlockState(overhead).isAir() && isBreakable(villager, overhead)) {
         level.destroyBlock(overhead, false);
         return true;
      }

      // 2. Pillar up: jump, then place a block in the foot space once we've risen above it.
      if (villager.onGround()) {
         villager.getJumpControl().jump();
         this.pillarSpot = foot;
      } else if (this.pillarSpot != null && villager.getY() > this.pillarSpot.getY() + 0.55) {
         if (level.getBlockState(this.pillarSpot).isAir()) {
            Block block = takePlaceableBlock(villager);
            if (block != null) {
               WorldUtilities.setBlock(level, new Point(this.pillarSpot.getX(), this.pillarSpot.getY(), this.pillarSpot.getZ()), block);
            }
         }
         this.pillarSpot = null;
      }
      return true;
   }

   @Override
   public void onStop(MillVillager villager) {
      this.pillarSpot = null;
   }

   /**
    * Trapped = standing on the ground with at least 3 of the 4 horizontal sides walled 2+ blocks high (a wall
    * a villager can neither step nor jump over). A 1-block lip is not a pit (it just walks/jumps out).
    */
   private static boolean isInPit(MillVillager villager) {
      Level level = villager.level();
      BlockPos foot = villager.blockPosition();
      int walls = 0;
      int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
      for (int[] d : dirs) {
         boolean atFoot = level.getBlockState(foot.offset(d[0], 0, d[1])).isSolid();
         boolean atHead = level.getBlockState(foot.offset(d[0], 1, d[1])).isSolid();
         if (atFoot && atHead) {
            walls++;
         }
      }
      return walls >= 3;
   }

   /** Breakable under the "medium" rule: not air, not unbreakable, and NOT part of a Mill building. */
   private static boolean isBreakable(MillVillager villager, BlockPos pos) {
      Level level = villager.level();
      BlockState state = level.getBlockState(pos);
      if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
         return false; // air or unbreakable (bedrock)
      }
      Point p = new Point(pos.getX(), pos.getY(), pos.getZ());
      return villager.mw == null || villager.mw.getBuilding(p) == null; // protect village buildings
   }

   /** Take one full block from the villager's inventory to place; dirt as an emergency fallback so a villager
    *  carrying nothing can still climb out instead of dying in the hole. */
   private static Block takePlaceableBlock(MillVillager villager) {
      if (villager.inventory != null) {
         for (Map.Entry<InvItem, Integer> e : villager.inventory.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0 && e.getKey().getItem() instanceof BlockItem bi) {
               Block block = bi.getBlock();
               if (block.defaultBlockState().isSolid()) {
                  villager.takeFromInv(block, e.getKey().meta, 1);
                  return block;
               }
            }
         }
      }
      return Blocks.DIRT; // emergency fallback (carries nothing) — minimal, never griefs structures
   }
}
