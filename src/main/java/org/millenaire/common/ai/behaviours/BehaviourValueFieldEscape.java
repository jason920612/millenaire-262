package org.millenaire.common.ai.behaviours;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.millenaire.common.ai.MillBehaviour;
import org.millenaire.common.ai.nav.MillEdgeCostProvider;
import org.millenaire.common.ai.nav.MillG0Reachability;
import org.millenaire.common.ai.nav.MillRepairPolicy;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

/**
 * Value-field rewrite (Phase 2): the SINGLE escape that replaces the three separate hacks (stuck-detection,
 * the side-waypoint detour, and the pit-escape behaviour). It fires only when the {@link MillG0Reachability}
 * feasibility certificate says the villager is TRAPPED — its normal-move (G0) region is a small enclosure —
 * and then performs the last-resort G1 repair (pillar up / dig out) strictly through {@link MillRepairPolicy}
 * (never griefs buildings, footprint-capped). Gated by {@code ValueFieldNav}, so with the flag off the legacy
 * behaviours still run and nothing changes.
 */
public final class BehaviourValueFieldEscape implements MillBehaviour {
   /** A pit/box is at most this many reachable G0 cells; an open area exceeds it and is "not trapped". */
   private static final int TRAP_PROBE = 64;
   private final MillEdgeCostProvider probeCosts = new MillEdgeCostProvider();
   private MillRepairPolicy policy;
   private BlockPos pillarSpot;

   @Override
   public boolean canRun(MillVillager villager) {
      // Cheap O(1) pre-filter FIRST: only pay for the bounded feasibility BFS when the villager is actually
      // walled in on most sides. In open play canRun is just a few block reads — no per-tick search cost.
      if (!MillConfigValues.ValueFieldNav || !villager.onGround() || !locallyWalled(villager)) {
         return false;
      }
      return !MillG0Reachability.feasible(villager.level(), this.probeCosts, villager.blockPosition(), null, TRAP_PROBE);
   }

   /** O(1): 3+ of the 4 horizontal sides are 2-block-high walls (can't step or jump out) — "maybe trapped". */
   private static boolean locallyWalled(MillVillager villager) {
      Level level = villager.level();
      BlockPos foot = villager.blockPosition();
      int walls = 0;
      int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
      for (int[] d : dirs) {
         if (level.getBlockState(foot.offset(d[0], 0, d[1])).isSolid()
            && level.getBlockState(foot.offset(d[0], 1, d[1])).isSolid()) {
            walls++;
         }
      }
      return walls >= 3;
   }

   @Override
   public int priority(MillVillager villager) {
      return 55; // above combat (can't act from inside a box), below fluid-escape
   }

   @Override
   public boolean tick(MillVillager villager) {
      if (MillG0Reachability.feasible(villager.level(), this.probeCosts, villager.blockPosition(), null, TRAP_PROBE)) {
         if (this.policy != null) {
            this.policy.resetEpisode();
         }
         return false; // freed — normal movement can resume
      }
      if (this.policy == null) {
         this.policy = new MillRepairPolicy(villager);
      }
      if (this.policy.budgetExhausted()) {
         return false; // spent the footprint cap without freeing — give up (no grief, no infinite digging)
      }
      Level level = villager.level();
      BlockPos foot = villager.blockPosition();

      // Dig the block capping the climb overhead — gated by the policy (never a building/unbreakable block).
      BlockPos overhead = foot.above(2);
      if (!level.getBlockState(overhead).isAir() && this.policy.mayBreak(level, overhead)) {
         level.destroyBlock(overhead, false);
         this.policy.noteModification();
         return true;
      }

      // Pillar up: jump, then place a block in the foot space once we've risen above it (policy-gated).
      if (villager.onGround()) {
         villager.getJumpControl().jump();
         this.pillarSpot = foot;
      } else if (this.pillarSpot != null && villager.getY() > this.pillarSpot.getY() + 0.55) {
         if (this.policy.mayPlace(level, this.pillarSpot)) {
            Block block = takePlaceableBlock(villager);
            if (block != null) {
               WorldUtilities.setBlock(level, new Point(this.pillarSpot.getX(), this.pillarSpot.getY(), this.pillarSpot.getZ()), block);
               this.policy.noteModification();
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

   private static Block takePlaceableBlock(MillVillager villager) {
      if (villager.inventory != null) {
         for (Map.Entry<InvItem, Integer> e : villager.inventory.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0 && e.getKey().getItem() instanceof BlockItem bi
               && bi.getBlock().defaultBlockState().isSolid()) {
               villager.takeFromInv(bi.getBlock(), e.getKey().meta, 1);
               return bi.getBlock();
            }
         }
      }
      return Blocks.DIRT; // emergency fallback so a villager carrying nothing can still climb out
   }
}
