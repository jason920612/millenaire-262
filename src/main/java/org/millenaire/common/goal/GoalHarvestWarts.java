package org.millenaire.common.goal;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;

@DocumentedElement.Documentation("Harvest grown nether warts froom home.")
public class GoalHarvestWarts extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] WARTS;

   public GoalHarvestWarts() {
      this.icon = InvItem.createInvItem(Items.NETHER_WART);
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      return this.packDest(villager.getHouse().getResManager().getNetherWartsHarvestLocation(), villager.getHouse());
   }

   @Override
   public ItemStack[] getHeldItemsOffHandDestination(MillVillager villager) {
      return WARTS != null ? WARTS : (WARTS = new ItemStack[]{new ItemStack(Items.NETHER_WART, 1)});
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
      Point cropPoint = villager.getGoalDestPoint().getAbove();
      // 1.12 checked legacy meta == 3 (ripe). In 26.2 metadata is gone; read the AGE_3 property.
      BlockState wartState = cropPoint.getBlockActualState(villager.level());
      if (villager.getBlock(cropPoint) == Blocks.NETHER_WART && wartState.getValue(NetherWartBlock.AGE) == 3) {
         villager.setBlockAndMetadata(cropPoint, Blocks.AIR, 0);
         villager.getHouse().storeGoods(Items.NETHER_WART, 1);
         villager.swing(InteractionHand.MAIN_HAND);
      }

      return true;
   }

   @Override
   public int priority(MillVillager villager) {
      return 100 - villager.getHouse().countGoods(Items.NETHER_WART) * 4;
   }
}
