package org.millenaire.common.goal;

import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;

@DocumentedElement.Documentation("Goal that harvests ripe cacao.")
public class GoalHarvestCacao extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] CACAO;

   public GoalHarvestCacao() {
      this.icon = InvItem.createInvItem(Items.COCOA_BEANS);
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      Point p = villager.getHouse().getResManager().getCocoaHarvestLocation();
      return this.packDest(p, villager.getHouse());
   }

   @Override
   public ItemStack[] getHeldItemsOffHandDestination(MillVillager villager) {
      return CACAO != null ? CACAO : (CACAO = new ItemStack[]{new ItemStack(Items.COCOA_BEANS, 1)});
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return villager.getBestHoeStack();
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      return this.getDestination(villager).getDest() != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   @Override
   public boolean performAction(MillVillager villager) {
      Point cropPoint = villager.getGoalDestPoint();
      if (cropPoint.getBlock(villager.level()) == Blocks.COCOA) {
         BlockState bs = cropPoint.getBlockActualState(villager.level());
         if ((Integer)bs.getValue(CocoaBlock.AGE) >= 2) {
            villager.setBlockAndMetadata(cropPoint, Blocks.AIR, 0);
            int nbcrop = 2;
            float irrigation = villager.getTownHall().getVillageIrrigation();
            double rand = Math.random();
            if (rand < irrigation / 100.0F) {
               nbcrop++;
            }

            villager.addToInv(Items.COCOA_BEANS, nbcrop);
            villager.swing(InteractionHand.MAIN_HAND);
         }
      }

      return true;
   }

   @Override
   public int priority(MillVillager villager) {
      return 100;
   }
}
