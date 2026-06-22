package org.millenaire.common.goal;

import net.minecraft.world.item.ItemStack;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;

@DocumentedElement.Documentation("For market merchants, hold their stalls so the player can trade with them.")
public class GoalForeignMerchantKeepStall extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] PURSE;
   private static ItemStack[] DENIER_ARGENT;

   public GoalForeignMerchantKeepStall() {
      this.icon = InvItem.createInvItem(MillItems.PURSE);
   }

   @Override
   public int actionDuration(MillVillager villager) throws Exception {
      return 1200;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      return villager.foreignMerchantStallId >= villager.getHouse().getResManager().stalls.size()
         ? null
         : this.packDest(villager.getHouse().getResManager().stalls.get(villager.foreignMerchantStallId), villager.getHouse());
   }

   @Override
   public ItemStack[] getHeldItemsDestination(MillVillager villager) throws Exception {
      return DENIER_ARGENT != null ? DENIER_ARGENT : (DENIER_ARGENT = new ItemStack[]{new ItemStack(MillItems.DENIER_ARGENT, 1)});
   }

   @Override
   public ItemStack[] getHeldItemsOffHandDestination(MillVillager villager) throws Exception {
      return PURSE != null ? PURSE : (PURSE = new ItemStack[]{new ItemStack(MillItems.PURSE, 1)});
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) throws Exception {
      return true;
   }

   @Override
   public boolean lookAtPlayer() {
      return true;
   }

   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      return MillRandom.chanceOn(600);
   }

   @Override
   public int priority(MillVillager villager) throws Exception {
      return MillRandom.randomInt(50);
   }
}
