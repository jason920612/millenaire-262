package org.millenaire.common.ui.firepit;

import org.jspecify.annotations.NonNull;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.ui.MillMenus;

/**
 * Mill fire pit menu. Slot layout in the underlying {@link TileEntityFirePit} Container:
 * inputs 0-2, fuel 3, outputs 4-6.
 *
 * <p>26.2: the cook/burn progress bars used the removed 1.12 {@code IContainerListener}/
 * {@code updateProgressBar} flow; they are ported to a {@link ContainerData} (added via
 * {@code addDataSlots}) exposing the fire pit's cook/burn times, which sync to the client automatically
 * through {@code broadcastChanges()}.
 */
public class ContainerFirePit extends AbstractContainerMenu {
   private static final int[][] INPUT_POSITIONS = new int[][]{{56, 8}, {44, 28}, {56, 48}};
   private static final int[][] OUTPUT_POSITIONS = new int[][]{{104, 8}, {116, 28}, {104, 48}};
   private static final int[] FUEL_POSITION = new int[]{80, 70};
   private static final int[] INV_POSITION = new int[]{8, 93};
   private final int inputStart;
   private final int inputEnd;
   private final int fuelStart;
   private final int fuelEnd;
   private final int outputStart;
   private final int outputEnd;
   private final int inventoryStart;
   private final int inventoryEnd;
   private final int hotbarStart;
   private final int hotbarEnd;
   private final TileEntityFirePit firePit;

   private static boolean inRange(int index, int start, int end) {
      return start <= index && index < end;
   }

   public ContainerFirePit(int containerId, Player player, TileEntityFirePit firePit) {
      super(MillMenus.FIRE_PIT, containerId);
      this.firePit = firePit;
      Inventory playerInventory = player.getInventory();
      this.inputStart = this.slots.size();

      for (int i = 0; i < 3; i++) {
         this.addSlot(new SlotFirePitInput(firePit, i, INPUT_POSITIONS[i][0], INPUT_POSITIONS[i][1]));
      }

      this.inputEnd = this.slots.size();
      this.fuelStart = this.slots.size();
      this.addSlot(new SlotFirePitFuel(firePit, 3, FUEL_POSITION[0], FUEL_POSITION[1]));
      this.fuelEnd = this.slots.size();
      this.outputStart = this.slots.size();

      for (int i = 0; i < 3; i++) {
         this.addSlot(new SlotFirePitOutput(player, firePit, 4 + i, OUTPUT_POSITIONS[i][0], OUTPUT_POSITIONS[i][1]));
      }

      this.outputEnd = this.slots.size();
      this.inventoryStart = this.slots.size();

      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column + row * 9 + 9, INV_POSITION[0] + column * 18, INV_POSITION[1] + row * 18));
         }
      }

      this.inventoryEnd = this.slots.size();
      this.hotbarStart = this.slots.size();

      for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
         this.addSlot(new Slot(playerInventory, hotbarIndex, INV_POSITION[0] + hotbarIndex * 18, INV_POSITION[1] + 54 + 4));
      }

      this.hotbarEnd = this.slots.size();

      // Sync the 5 progress values (3 cook times + burn + total) to the client.
      this.addDataSlots(new ContainerData() {
         @Override
         public int get(int id) {
            switch (id) {
               case 0:
               case 1:
               case 2:
                  return firePit.getCookTime(id);
               case 3:
                  return firePit.getBurnTime();
               case 4:
                  return firePit.getTotalBurnTime();
               default:
                  return 0;
            }
         }

         @Override
         public void set(int id, int value) {
            switch (id) {
               case 0:
               case 1:
               case 2:
                  firePit.setCookTime(id, value);
                  break;
               case 3:
                  firePit.setBurnTime(value);
                  break;
               case 4:
                  firePit.setTotalBurnTime(value);
                  break;
               default:
            }
         }

         @Override
         public int getCount() {
            return 5;
         }
      });
   }

   @Override
   public boolean stillValid(@NonNull Player playerIn) {
      return this.firePit.stillValid(playerIn);
   }

   @Override
   @NonNull
   public ItemStack quickMoveStack(@NonNull Player playerIn, int index) {
      ItemStack original = ItemStack.EMPTY;
      Slot slot = this.slots.get(index);
      if (slot != null && slot.hasItem()) {
         ItemStack stackInSlot = slot.getItem();
         original = stackInSlot.copy();
         if (inRange(index, this.outputStart, this.outputEnd)) {
            if (!this.moveItemStackTo(stackInSlot, this.inventoryStart, this.hotbarEnd, true)) {
               return ItemStack.EMPTY;
            }

            slot.onQuickCraft(stackInSlot, original);
         } else if (inRange(index, this.inventoryStart, this.hotbarEnd)) {
            if (TileEntityFirePit.isFirePitBurnable(stackInSlot)) {
               if (!this.moveItemStackTo(stackInSlot, this.inputStart, this.inputEnd, false)) {
                  return ItemStack.EMPTY;
               }
            } else if (this.firePit.canPlaceItem(3, stackInSlot)) {
               if (!this.moveItemStackTo(stackInSlot, this.fuelStart, this.fuelEnd, false)) {
                  return ItemStack.EMPTY;
               }
            } else if (inRange(index, this.inventoryStart, this.inventoryEnd)) {
               if (!this.moveItemStackTo(stackInSlot, this.hotbarStart, this.hotbarEnd, false)) {
                  return ItemStack.EMPTY;
               }
            } else if (!this.moveItemStackTo(stackInSlot, this.inventoryStart, this.inventoryStart, false)) {
               return ItemStack.EMPTY;
            }
         } else if (!this.moveItemStackTo(stackInSlot, this.inventoryStart, this.hotbarEnd, false)) {
            return ItemStack.EMPTY;
         }

         if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }

         if (stackInSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
         }

         slot.onTake(playerIn, stackInSlot);
      }

      return original;
   }
}
