package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.block.BlockSilkWorm;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Gather ripe silk from a silk block.")
public class GoalByzantineGatherSilk extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] SHEARS;
   private static ItemStack[] SILK;

   public GoalByzantineGatherSilk() {
      this.maxSimultaneousInBuilding = 2;
      this.buildingLimit.put(InvItem.createInvItem(MillItems.SILK), 128);
      this.townhallLimit.put(InvItem.createInvItem(MillItems.SILK), 128);
      this.icon = InvItem.createInvItem(MillItems.SILK);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like gather is driven per-tick (reach → swing → 1.12 transform): re-enter every tick (1) instead of
      // the fixed 20-tick countdown that instantly transformed the block + teleported the yield in.
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      List<Point> vp = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building silkFarm : villager.getTownHall().getBuildingsWithTag("silkwormfarm")) {
         Point p = silkFarm.getResManager().getSilkwormHarvestLocation();
         if (p != null) {
            vp.add(p);
            buildingp.add(silkFarm.getPos());
         }
      }

      if (vp.isEmpty()) {
         return null;
      } else {
         Point p = vp.get(0);
         Point buildingP = buildingp.get(0);

         for (int i = 1; i < vp.size(); i++) {
            if (vp.get(i).horizontalDistanceToSquared(villager) < p.horizontalDistanceToSquared(villager)) {
               p = vp.get(i);
               buildingP = buildingp.get(i);
            }
         }

         return this.packDest(p, buildingP);
      }
   }

   @Override
   public ItemStack[] getHeldItemsOffHandDestination(MillVillager villager) {
      return SILK != null ? SILK : (SILK = new ItemStack[]{new ItemStack(MillItems.SILK, 1)});
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return SHEARS != null ? SHEARS : (SHEARS = new ItemStack[]{new ItemStack(Items.SHEARS, 1)});
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      boolean delayOver;
      if (!villager.lastGoalTime.containsKey(this)) {
         delayOver = true;
      } else {
         delayOver = villager.level().getOverworldClockTime() > villager.lastGoalTime.get(this) + 2000L;
      }

      for (Building kiln : villager.getTownHall().getBuildingsWithTag("silkwormfarm")) {
         int nb = kiln.getResManager().getNbSilkWormHarvestLocation();
         if (nb > 0 && delayOver) {
            return true;
         }

         if (nb > 4) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   /**
    * Player-like silk gather, driven one tick at a time (actionDuration == 1). STATELESS — this Goal is a SINGLETON
    * shared across villagers, so the phase is derived from the WORLD each tick (the silk block's PROGRESS state),
    * never from a per-goal field (mirrors the migrated mine/chop/harvest goals).
    *
    * <p>1.12 mechanic + yield KEPT EXACTLY: the silk source is NOT a breakable block — 1.12 TRANSFORMED the ripe
    * {@code SILK_WORM} (PROGRESS=SILKWORMFULL) back to its empty default state (so it regrows) and granted one
    * {@link MillItems#SILK}. The transform spawns NO real {@link net.minecraft.world.entity.item.ItemEntity} (the
    * block stays in place, just reset), so there is nothing to pick up — the {@code SILK} yield is the authoritative
    * Mill economy item, kept as the 1.12-fixed yield. The change vs 1.12 is ONLY that the villager must be within
    * player REACH (walk closer / scaffold if not) and SWINGS before the transform — a genuine player-like interact.
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }
      // Phase from the world: only act on a still-ripe silk block (PROGRESS == SILKWORMFULL). Anything else (already
      // harvested this cycle / not silk) → finish so the goal re-picks.
      if (WorldUtilities.getBlock(villager.level(), dest) != MillBlocks.SILK_WORM
         || WorldUtilities.getBlockState(villager.level(), dest).getValue(BlockSilkWorm.PROGRESS)
            != BlockSilkWorm.EnumType.SILKWORMFULL) {
         return true;
      }

      // Reach-gate: walk within player reach (scaffold-extend if needed) before interacting.
      if (!VillagerWorldOps.withinReach(villager, dest.getBlockPos())) {
         OpState reach = VillagerWorldOps.ensureReach(villager, dest.getBlockPos());
         return reach == OpState.BLOCKED; // BLOCKED → abandon (true); else keep approaching (false).
      }

      // Real player-like interact: swing, then perform the 1.12 transform (ripe → empty default) + the 1.12 yield.
      villager.swing(InteractionHand.MAIN_HAND);
      villager.setBlockAndMetadata(dest, MillBlocks.SILK_WORM, 0);
      villager.addToInv(MillItems.SILK, 0, 1); // KEPT-1.12 yield: the transform drops nothing, so grant the Mill item.
      return false;
   }

   @Override
   public int priority(MillVillager villager) {
      int p = 100 - villager.getTownHall().nbGoodAvailable(InvItem.createInvItem(MillItems.SILK, 1), false, false, false) * 2;

      for (MillVillager v : villager.getTownHall().getKnownVillagers()) {
         if (this.key.equals(v.goalKey)) {
            p /= 2;
         }
      }

      return p;
   }

   @Override
   public boolean unreachableDestination(MillVillager villager) {
      return false;
   }
}
