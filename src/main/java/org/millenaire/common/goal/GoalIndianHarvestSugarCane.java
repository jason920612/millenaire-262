package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Gather sugar cane from a building with the sugar cane plantation tag.")
public class GoalIndianHarvestSugarCane extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] SUGARCANE;

   public GoalIndianHarvestSugarCane() {
      this.tags.add("tag_agriculture");
      this.icon = InvItem.createInvItem(Items.SUGAR_CANE);
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      List<Point> vp = new ArrayList<>();
      List<Point> buildingp = new ArrayList<>();

      for (Building plantation : villager.getTownHall().getBuildingsWithTag("sugarplantation")) {
         Point p = plantation.getResManager().getSugarCaneHarvestLocation();
         if (p != null) {
            vp.add(p);
            buildingp.add(plantation.getPos());
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
      return SUGARCANE != null ? SUGARCANE : (SUGARCANE = new ItemStack[]{new ItemStack(Items.SUGAR_CANE, 1)});
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestHoeStack();
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      int nbsimultaneous = 0;

      for (MillVillager v : villager.getTownHall().getKnownVillagers()) {
         if (v != villager && this.key.equals(v.goalKey)) {
            nbsimultaneous++;
         }
      }

      if (nbsimultaneous > 2) {
         return false;
      } else {
         boolean delayOver;
         if (!villager.lastGoalTime.containsKey(this)) {
            delayOver = true;
         } else {
            delayOver = villager.level().getOverworldClockTime() > villager.lastGoalTime.get(this) + 2000L;
         }

         for (Building kiln : villager.getTownHall().getBuildingsWithTag("sugarplantation")) {
            int nb = kiln.getResManager().getNbSugarCaneHarvestLocation();
            if (nb > 0 && delayOver) {
               return true;
            }

            if (nb > 4) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   @Override
   public boolean performAction(MillVillager villager) {
      Point cropPoint = villager.getGoalDestPoint().getRelative(0.0, 3.0, 0.0);
      if (villager.getBlock(cropPoint) == Blocks.SUGAR_CANE) {
         villager.setBlockAndMetadata(cropPoint, Blocks.AIR, 0);
         int nbcrop = 1;
         float irrigation = villager.getTownHall().getVillageIrrigation();
         double rand = Math.random();
         if (rand < irrigation / 100.0F) {
            nbcrop++;
         }

         villager.addToInv(Items.SUGAR_CANE, nbcrop);
      }

      cropPoint = villager.getGoalDestPoint().getRelative(0.0, 2.0, 0.0);
      if (villager.getBlock(cropPoint) == Blocks.SUGAR_CANE) {
         villager.setBlockAndMetadata(cropPoint, Blocks.AIR, 0);
         int nbcrop = 1;
         float irrigation = villager.getTownHall().getVillageIrrigation();
         double rand = Math.random();
         if (rand < irrigation / 100.0F) {
            nbcrop++;
         }

         villager.swing(InteractionHand.MAIN_HAND);
         villager.addToInv(Items.SUGAR_CANE, nbcrop);
      }

      return true;
   }

   @Override
   public int priority(MillVillager villager) {
      int p = 200 - villager.getTownHall().nbGoodAvailable(Items.SUGAR_CANE, 0, false, false, false) * 4;

      for (MillVillager v : villager.getTownHall().getKnownVillagers()) {
         if (this.key.equals(v.goalKey)) {
            p /= 2;
         }
      }

      return p;
   }
}
