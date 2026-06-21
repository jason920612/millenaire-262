package org.millenaire.common.ui;

import org.jspecify.annotations.NonNull;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.ui.MillMenus;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;

public class ContainerPuja extends AbstractContainerMenu {
   PujaSacrifice shrine;
   ContainerPuja.ToolSlot slotTool;

   public ContainerPuja(int containerId, Player player, Building temple) {
      super(MillMenus.PUJA, containerId);

      try {
         Inventory playerInventory = player.getInventory();
         this.shrine = temple.pujas;
         this.slotTool = new ContainerPuja.ToolSlot(temple.pujas, 4, 86, 37);
         this.addSlot(new ContainerPuja.OfferingSlot(temple.pujas, 0, 26, 19));
         this.addSlot(new ContainerPuja.MoneySlot(temple.pujas, 1, 8, 55));
         this.addSlot(new ContainerPuja.MoneySlot(temple.pujas, 2, 26, 55));
         this.addSlot(new ContainerPuja.MoneySlot(temple.pujas, 3, 44, 55));
         this.addSlot(this.slotTool);

         for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 9; k++) {
               this.addSlot(new Slot(playerInventory, k + i * 9 + 9, 8 + k * 18, 106 + i * 18));
            }
         }

         for (int j = 0; j < 9; j++) {
            this.addSlot(new Slot(playerInventory, j, 8 + j * 18, 164));
         }
      } catch (Exception var5) {
         MillLog.printException("Exception in ContainerPuja(): ", var5);
      }
   }

   @Override
   public boolean stillValid(@NonNull Player entityplayer) {
      return true;
   }

   @Override
   @NonNull
   public ItemStack quickMoveStack(@NonNull Player par1EntityPlayer, int stackID) {
      ItemStack itemstack = ItemStack.EMPTY;
      Slot slot = this.slots.get(stackID);
      if (slot != null && slot.hasItem()) {
         ItemStack itemstack1 = slot.getItem();
         itemstack = itemstack1.copy();
         if (stackID == 4) {
            if (!this.moveItemStackTo(itemstack1, 5, 41, true)) {
               return ItemStack.EMPTY;
            }

            slot.onQuickCraft(itemstack1, itemstack);
         } else if (stackID > 4) {
            if (itemstack1.getItem() != MillItems.DENIER
               && itemstack1.getItem() != MillItems.DENIER_ARGENT
               && itemstack1.getItem() != MillItems.DENIER_OR) {
               if (this.shrine.getOfferingValue(itemstack1) > 0) {
                  if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                     return ItemStack.EMPTY;
                  }
               } else {
                  if (!this.slotTool.mayPlace(itemstack1)) {
                     return ItemStack.EMPTY;
                  }

                  if (!this.moveItemStackTo(itemstack1, 4, 5, false)) {
                     return ItemStack.EMPTY;
                  }
               }
            } else if (!this.moveItemStackTo(itemstack1, 1, 4, false)) {
               return ItemStack.EMPTY;
            }
         } else if (!this.moveItemStackTo(itemstack1, 5, 41, false)) {
            return ItemStack.EMPTY;
         }

         if (itemstack1.getCount() == 0) {
            slot.set(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }

         if (itemstack1.getCount() == itemstack.getCount()) {
            return ItemStack.EMPTY;
         }

         slot.onTake(par1EntityPlayer, itemstack1);
      }

      return itemstack;
   }

   public static class MoneySlot extends Slot {
      PujaSacrifice shrine;

      public MoneySlot(PujaSacrifice shrine, int par2, int par3, int par4) {
         super(shrine, par2, par3, par4);
         this.shrine = shrine;
      }

      @Override
      public boolean mayPlace(@NonNull ItemStack is) {
         return is.getItem() == MillItems.DENIER || is.getItem() == MillItems.DENIER_OR || is.getItem() == MillItems.DENIER_ARGENT;
      }

      @Override
      public void setChanged() {
         if (!this.shrine.temple.world.isClientSide()) {
            this.shrine.temple.getTownHall().requestSave("Puja money slot changed");
         }

         super.setChanged();
      }
   }

   public static class OfferingSlot extends Slot {
      PujaSacrifice shrine;

      public OfferingSlot(PujaSacrifice shrine, int par2, int par3, int par4) {
         super(shrine, par2, par3, par4);
         this.shrine = shrine;
      }

      @Override
      public boolean mayPlace(@NonNull ItemStack par1ItemStack) {
         return this.shrine.getOfferingValue(par1ItemStack) > 0;
      }

      @Override
      public void setChanged() {
         if (!this.shrine.temple.world.isClientSide()) {
            this.shrine.temple.getTownHall().requestSave("Puja offering slot changed");
         }

         super.setChanged();
      }
   }

   public static class ToolSlot extends Slot {
      PujaSacrifice shrine;

      public ToolSlot(PujaSacrifice shrine, int par2, int par3, int par4) {
         super(shrine, par2, par3, par4);
         this.shrine = shrine;
      }

      @Override
      public boolean mayPlace(@NonNull ItemStack is) {
         Item item = is.getItem();
         // 1.12 used ItemSword/ItemArmor/ItemPickaxe/ItemSpade/ItemAxe/ItemBow instanceof checks; on
         // 26.2 those subclasses are gone, so this delegates to PujaSacrifice.validForItem which now
         // classifies via data components. Mayan shrine (type 1) accepted sword/armour/bow + axe
         // (== UNBREAKABLE plus axe); the Puja shrine accepted shovel/axe/pickaxe (== TOOL).
         return this.shrine.type == 1
            ? PujaSacrifice.validForItem(PujaSacrifice.UNBREAKABLE, item) || item instanceof AxeItem
            : PujaSacrifice.validForItem(PujaSacrifice.TOOL, item);
      }

      @Override
      public void setChanged() {
         this.shrine.calculateOfferingsNeeded();
         if (!this.shrine.temple.world.isClientSide()) {
            this.shrine.temple.getTownHall().requestSave("Puja tool slot changed");
         }

         super.setChanged();
      }
   }
}
