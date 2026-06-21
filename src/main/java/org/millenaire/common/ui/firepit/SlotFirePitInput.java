package org.millenaire.common.ui.firepit;

import org.jspecify.annotations.NonNull;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.entity.TileEntityFirePit;

public class SlotFirePitInput extends Slot {
   public SlotFirePitInput(Container container, int index, int xPosition, int yPosition) {
      super(container, index, xPosition, yPosition);
   }

   @Override
   public boolean mayPlace(@NonNull ItemStack stack) {
      return TileEntityFirePit.isFirePitBurnable(stack);
   }
}
