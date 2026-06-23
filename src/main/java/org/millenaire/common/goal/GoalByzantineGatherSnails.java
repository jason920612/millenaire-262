package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.block.BlockSnailSoil;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Gather snails from a snail soil block to make purple dye.")
public class GoalByzantineGatherSnails extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] PURPLE_DYE;

   public GoalByzantineGatherSnails() {
      this.maxSimultaneousInBuilding = 2;
      this.buildingLimit.put(InvItem.createInvItem(Items.DYE.pick(DyeColor.PURPLE)), 128);
      this.townhallLimit.put(InvItem.createInvItem(Items.DYE.pick(DyeColor.PURPLE)), 128);
      this.icon = InvItem.createInvItem(Items.DYE.pick(DyeColor.PURPLE));
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like gather is driven per-tick (reach → swing → 1.12 transform): re-enter every tick (1).
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      List<Point> vp = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building snailFamr : villager.getTownHall().getBuildingsWithTag("snailsfarm")) {
         Point p = snailFamr.getResManager().getSnailSoilHarvestLocation();
         if (p != null) {
            vp.add(p);
            buildingp.add(snailFamr.getPos());
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
      return PURPLE_DYE != null ? PURPLE_DYE : (PURPLE_DYE = new ItemStack[]{new ItemStack(Items.DYE.pick(DyeColor.PURPLE))});
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestShovelStack();
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      boolean delayOver;
      if (!villager.lastGoalTime.containsKey(this)) {
         delayOver = true;
      } else {
         delayOver = villager.level().getOverworldClockTime() > villager.lastGoalTime.get(this) + 2000L;
      }

      for (Building kiln : villager.getTownHall().getBuildingsWithTag("snailsfarm")) {
         int nb = kiln.getResManager().getNbSnailSoilHarvestLocation();
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
    * Player-like snail gather, driven one tick at a time (actionDuration == 1). STATELESS — phase derived from the
    * WORLD (the snail-soil PROGRESS state) each tick, never a per-goal field (this Goal is a shared SINGLETON).
    *
    * <p>1.12 mechanic + yield KEPT EXACTLY: like silk, the snail soil is NOT broken — 1.12 TRANSFORMED the ripe
    * {@code SNAIL_SOIL} (PROGRESS=SNAIL_SOIL_FULL) back to its empty default (so it regrows) and granted one PURPLE
    * dye. The transform drops nothing real, so there is nothing to pick up; the purple dye is the authoritative,
    * KEPT-1.12 yield. The only change vs 1.12: walk within player REACH and SWING before the transform.
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }
      if (WorldUtilities.getBlock(villager.level(), dest) != MillBlocks.SNAIL_SOIL
         || WorldUtilities.getBlockState(villager.level(), dest).getValue(BlockSnailSoil.PROGRESS)
            != BlockSnailSoil.EnumType.SNAIL_SOIL_FULL) {
         return true;
      }

      if (!VillagerWorldOps.withinReach(villager, dest.getBlockPos())) {
         OpState reach = VillagerWorldOps.ensureReach(villager, dest.getBlockPos());
         return reach == OpState.BLOCKED;
      }

      villager.swing(InteractionHand.MAIN_HAND);
      villager.setBlockAndMetadata(dest, MillBlocks.SNAIL_SOIL, 0);
      villager.addToInv(Items.DYE.pick(DyeColor.PURPLE), 1); // KEPT-1.12 yield: transform drops nothing.
      return false;
   }

   @Override
   public int priority(MillVillager villager) {
      int p = 100
         - villager.getTownHall().nbGoodAvailable(InvItem.createInvItem(Items.DYE.pick(DyeColor.PURPLE)), false, false, false) * 2;

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
