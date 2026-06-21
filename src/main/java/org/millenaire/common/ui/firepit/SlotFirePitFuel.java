package org.millenaire.common.ui.firepit;

import org.jspecify.annotations.NonNull;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.FurnaceFuelSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.entity.TileEntityFirePit;

public class SlotFirePitFuel extends Slot {
   public SlotFirePitFuel(Container container, int index, int xPosition, int yPosition) {
      super(container, index, xPosition, yPosition);
   }

   @Override
   public int getMaxStackSize(@NonNull ItemStack stack) {
      return FurnaceFuelSlot.isBucket(stack) ? 1 : super.getMaxStackSize(stack);
   }

   @Override
   public boolean mayPlace(@NonNull ItemStack stack) {
      // 1.12 accepted buckets (for emptying) + any item TileEntityFurnace.isItemFuel reported as fuel.
      // On 26.2 the fuel check lives on the level (FuelValues): the slot's container is the fire-pit
      // block-entity, so resolve its level to mirror AbstractFurnaceMenu.isFuel exactly.
      if (FurnaceFuelSlot.isBucket(stack)) {
         return true;
      }
      if (this.container instanceof TileEntityFirePit firePit && firePit.getLevel() != null) {
         return firePit.getLevel().fuelValues().isFuel(stack);
      }
      return this.container.canPlaceItem(this.getContainerSlot(), stack);
   }
}
