package org.millenaire.common.ui;

import org.jspecify.annotations.NonNull;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.ui.MillMenus;
import org.millenaire.common.village.Building;

public class ContainerLockedChest extends AbstractContainerMenu {
   private final Container lowerChestInventory;
   private final int numRows;
   private final boolean locked;

   public ContainerLockedChest(int containerId, Inventory playerInventory, Container chestInventory, Player player, Building building, boolean locked) {
      super(MillMenus.LOCKED_CHEST, containerId);
      this.locked = locked;
      this.lowerChestInventory = chestInventory;
      this.numRows = chestInventory.getContainerSize() / 9;
      chestInventory.startOpen(player);
      int i = (this.numRows - 4) * 18;

      for (int j = 0; j < this.numRows; j++) {
         for (int k = 0; k < 9; k++) {
            if (locked) {
               this.addSlot(new ContainerLockedChest.LockedSlot(chestInventory, k + j * 9, 8 + k * 18, 18 + j * 18));
            } else {
               this.addSlot(new ContainerLockedChest.CachedSlot(chestInventory, k + j * 9, 8 + k * 18, 18 + j * 18, building));
            }
         }
      }

      for (int l = 0; l < 3; l++) {
         for (int j1 = 0; j1 < 9; j1++) {
            this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + i));
         }
      }

      for (int i1 = 0; i1 < 9; i1++) {
         this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 161 + i));
      }
   }

   @Override
   public boolean stillValid(@NonNull Player playerIn) {
      return this.lowerChestInventory.stillValid(playerIn);
   }

   public Container getLowerChestInventory() {
      return this.lowerChestInventory;
   }

   @Override
   public void removed(@NonNull Player playerIn) {
      super.removed(playerIn);
      this.lowerChestInventory.stopOpen(playerIn);
   }

   @Override
   @NonNull
   public ItemStack quickMoveStack(@NonNull Player playerIn, int index) {
      ItemStack itemstack = ItemStack.EMPTY;
      Slot slot = this.slots.get(index);
      if (slot != null && slot.hasItem()) {
         ItemStack itemstack1 = slot.getItem();
         itemstack = itemstack1.copy();
         if (index < this.numRows * 9) {
            if (!this.moveItemStackTo(itemstack1, this.numRows * 9, this.slots.size(), true)) {
               return ItemStack.EMPTY;
            }
         } else if (this.locked) {
            // A locked chest is fully read-only to the player (1.12 func_184996_a returned EMPTY for any
            // locked-slot interaction). LockedSlot.mayPlace==false blocks inserting into an EMPTY slot, but
            // moveItemStackTo still TOPS UP an existing matching stack (the merge path skips mayPlace), so a
            // shift-click from the player inventory could still deposit into a locked chest. Block the whole
            // player->chest move when locked so nothing can be added.
            return ItemStack.EMPTY;
         } else if (!this.moveItemStackTo(itemstack1, 0, this.numRows * 9, false)) {
            return ItemStack.EMPTY;
         }

         if (itemstack1.isEmpty()) {
            slot.set(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }
      }

      return itemstack;
   }

   public static class CachedSlot extends Slot {
      final Building building;

      public CachedSlot(Container inventoryIn, int index, int xPosition, int yPosition, Building building) {
         super(inventoryIn, index, xPosition, yPosition);
         this.building = building;
      }

      @Override
      public void setChanged() {
         super.setChanged();
         if (this.building != null) {
            this.building.invalidateInventoryCache();
         }
      }
   }

   public static class LockedSlot extends Slot {
      public LockedSlot(Container inventoryIn, int index, int xPosition, int yPosition) {
         super(inventoryIn, index, xPosition, yPosition);
      }

      @Override
      public boolean mayPickup(@NonNull Player playerIn) {
         return false;
      }

      // 1.12 ContainerLockedChest additionally overrode slotClick (func_184996_a) to make every
      // interaction with a locked slot a no-op. mayPickup==false only blocks taking; on 26.2 the
      // place path (doClick / moveItemStackTo for empty targets) is gated by mayPlace, so block that
      // too to restore the original "view-only, cannot modify" locked chest behaviour.
      @Override
      public boolean mayPlace(@NonNull ItemStack stack) {
         return false;
      }
   }
}
