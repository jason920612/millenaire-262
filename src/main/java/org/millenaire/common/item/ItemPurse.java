package org.millenaire.common.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.WorldUtilities;

/**
 * Coin purse — stores/withdraws deniers (bronze/silver/gold). 1.12 stored three ints in the stack's
 * NBT ({@code getTagCompound}); on 26.2 stacks carry data components, so the purse state lives in the
 * {@link CustomData} (custom_data) component. {@code onItemRightClick}→{@code use} returning
 * {@link InteractionResult}; the custom display name moves to {@code getName(ItemStack)}.
 */
public class ItemPurse extends ItemMill {
   private static final String ML_PURSE_DENIER = "ml_Purse_DENIER";
   private static final String ML_PURSE_DENIERARGENT = "ml_Purse_DENIERargent";
   private static final String ML_PURSE_DENIEROR = "ml_Purse_DENIERor";
   private static final String ML_PURSE_RAND = "ml_Purse_rand";

   public ItemPurse(String itemName) {
      super(itemName);
   }

   private static CompoundTag tag(ItemStack stack) {
      return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
   }

   private static void setTag(ItemStack stack, CompoundTag tag) {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
   }

   private static boolean hasTag(ItemStack stack) {
      return stack.has(DataComponents.CUSTOM_DATA);
   }

   @Override
   public Component getName(ItemStack purse) {
      Component base = super.getName(purse);
      if (!hasTag(purse)) {
         return base;
      }

      CompoundTag tag = tag(purse);
      int DENIERs = tag.getIntOr(ML_PURSE_DENIER, 0);
      int DENIERargent = tag.getIntOr(ML_PURSE_DENIERARGENT, 0);
      int DENIERor = tag.getIntOr(ML_PURSE_DENIEROR, 0);
      StringBuilder label = new StringBuilder();
      if (DENIERor != 0) {
         label.append("§e").append(DENIERor).append("o ");
      }

      if (DENIERargent != 0) {
         label.append("§f").append(DENIERargent).append("a ");
      }

      if (DENIERs != 0 || label.length() == 0) {
         label.append("§6").append(DENIERs).append("d");
      }

      return Component.literal("§f" + base.getString() + ": " + label.toString().trim());
   }

   @Override
   public InteractionResult use(Level worldIn, Player playerIn, InteractionHand handIn) {
      ItemStack purse = playerIn.getItemInHand(handIn);
      if (this.totalDeniers(purse) > 0) {
         this.removeDeniersFromPurse(purse, playerIn);
      } else {
         this.storeDeniersInPurse(purse, playerIn);
      }

      return InteractionResult.SUCCESS;
   }

   private void removeDeniersFromPurse(ItemStack purse, Player player) {
      if (hasTag(purse)) {
         CompoundTag tag = tag(purse);
         int DENIERs = tag.getIntOr(ML_PURSE_DENIER, 0);
         int DENIERargent = tag.getIntOr(ML_PURSE_DENIERARGENT, 0);
         int DENIERor = tag.getIntOr(ML_PURSE_DENIEROR, 0);
         int result = MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.DENIER, DENIERs);
         tag.putInt(ML_PURSE_DENIER, DENIERs - result);
         result = MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.DENIER_ARGENT, DENIERargent);
         tag.putInt(ML_PURSE_DENIERARGENT, DENIERargent - result);
         result = MillCommonUtilities.putItemsInChest(player.getInventory(), MillItems.DENIER_OR, DENIERor);
         tag.putInt(ML_PURSE_DENIEROR, DENIERor - result);
         tag.putInt(ML_PURSE_RAND, player.level().isClientSide() ? 0 : 1);
         setTag(purse, tag);
      }
   }

   public void setDeniers(ItemStack purse, Player player, int amount) {
      int denier = amount % 64;
      int denier_argent = (amount - denier) / 64 % 64;
      int denier_or = (amount - denier - denier_argent * 64) / 4096;
      this.setDeniers(purse, player, denier, denier_argent, denier_or);
   }

   public void setDeniers(ItemStack purse, Player player, int DENIER, int DENIERargent, int DENIERor) {
      CompoundTag tag = tag(purse);
      tag.putInt(ML_PURSE_DENIER, DENIER);
      tag.putInt(ML_PURSE_DENIERARGENT, DENIERargent);
      tag.putInt(ML_PURSE_DENIEROR, DENIERor);
      tag.putInt(ML_PURSE_RAND, player.level().isClientSide() ? 0 : 1);
      setTag(purse, tag);
   }

   private void storeDeniersInPurse(ItemStack purse, Player player) {
      int deniers = WorldUtilities.getItemsFromChest(player.getInventory(), MillItems.DENIER, 0, Integer.MAX_VALUE);
      int deniersargent = WorldUtilities.getItemsFromChest(player.getInventory(), MillItems.DENIER_ARGENT, 0, Integer.MAX_VALUE);
      int deniersor = WorldUtilities.getItemsFromChest(player.getInventory(), MillItems.DENIER_OR, 0, Integer.MAX_VALUE);
      int total = this.totalDeniers(purse) + deniers + deniersargent * 64 + deniersor * 64 * 64;
      int new_denier = total % 64;
      int new_deniers_argent = (total - new_denier) / 64 % 64;
      int new_deniers_or = (total - new_denier - new_deniers_argent * 64) / 4096;
      this.setDeniers(purse, player, new_denier, new_deniers_argent, new_deniers_or);
   }

   public int totalDeniers(ItemStack purse) {
      if (!hasTag(purse)) {
         return 0;
      } else {
         CompoundTag tag = tag(purse);
         int deniers = tag.getIntOr(ML_PURSE_DENIER, 0);
         int denier_argent = tag.getIntOr(ML_PURSE_DENIERARGENT, 0);
         int denier_or = tag.getIntOr(ML_PURSE_DENIEROR, 0);
         return deniers + denier_argent * 64 + denier_or * 64 * 64;
      }
   }
}
