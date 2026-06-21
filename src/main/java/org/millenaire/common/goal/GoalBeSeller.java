package org.millenaire.common.goal;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.MillLog;

@DocumentedElement.Documentation("Go and sell to the player.")
public class GoalBeSeller extends Goal {
   public static final int sellingRadius = 7;
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] PURSE;
   private static ItemStack[] DENIER;

   private static ItemStack[] purse() {
      if (PURSE == null) {
         PURSE = new ItemStack[]{new ItemStack(MillItems.PURSE, 1)};
      }
      return PURSE;
   }

   private static ItemStack[] denier() {
      if (DENIER == null) {
         DENIER = new ItemStack[]{new ItemStack(MillItems.DENIER, 1)};
      }
      return DENIER;
   }

   public GoalBeSeller() {
      this.icon = InvItem.createInvItem(MillItems.PURSE);
      this.floatingIcon = this.icon;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      return this.packDest(villager.getTownHall().sellingPlace);
   }

   @Override
   public ItemStack[] getHeldItemsDestination(MillVillager villager) throws Exception {
      return denier();
   }

   @Override
   public ItemStack[] getHeldItemsOffHandDestination(MillVillager villager) throws Exception {
      return purse();
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      return false;
   }

   @Override
   public boolean isStillValidSpecific(MillVillager villager) throws Exception {
      if (villager.getTownHall().sellingPlace == null) {
         return false;
      } else {
         Player player = villager.level()
            .getNearestPlayer(
               villager.getTownHall().sellingPlace.getiX(),
               villager.getTownHall().sellingPlace.getiY(),
               villager.getTownHall().sellingPlace.getiZ(),
               7.0,
               false
            );
         boolean valid = player != null && villager.getTownHall().sellingPlace.distanceTo(player) < 7.0;
         if (!valid && MillConfigValues.LogWifeAI >= 1) {
            MillLog.major(this, "Selling goal no longer valid.");
         }

         return valid;
      }
   }

   @Override
   public boolean lookAtPlayer() {
      return true;
   }

   @Override
   public void onAccept(MillVillager villager) {
      Player player = villager.level()
         .getNearestPlayer(
            villager.getTownHall().sellingPlace.getiX(), villager.getTownHall().sellingPlace.getiY(), villager.getTownHall().sellingPlace.getiZ(), 7.0, false
         );
      ServerSender.sendTranslatedSentence(player, 'f', "ui.sellercoming", villager.getVillagerName());
   }

   @Override
   public void onComplete(MillVillager villager) {
      Player player = villager.level()
         .getNearestPlayer(
            villager.getTownHall().getResManager().getSellingPos().getiX(),
            villager.getTownHall().getResManager().getSellingPos().getiY(),
            villager.getTownHall().getResManager().getSellingPos().getiZ(),
            17.0,
            false
         );
      ServerSender.sendTranslatedSentence(player, 'f', "ui.tradecomplete", villager.getVillagerName());
      villager.getTownHall().seller = null;
      villager.getTownHall().sellingPlace = null;
   }

   @Override
   public boolean performAction(MillVillager villager) {
      if (villager.getTownHall().sellingPlace == null) {
         MillLog.error(this, "villager.townHall.sellingPlace is null.");
         return true;
      } else {
         return false;
      }
   }

   @Override
   public int priority(MillVillager villager) {
      return 0;
   }

   @Override
   public int range(MillVillager villager) {
      return 2;
   }
}
