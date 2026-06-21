package org.millenaire.common.item;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import org.millenaire.common.forge.MillRegistry;

/**
 * Mayan quest crown — a helmet that auto-applies Respiration/Aqua Affinity/Protection. 1.12 extended
 * ItemArmor and re-applied the enchantments in onItemUseFirst/onUpdate. On 26.2 armour is a plain
 * {@link Item} with humanoidArmor properties, and enchantments are data components, so the
 * auto-enchantment is a TODO (see {@link #applyEnchantments}).
 */
public class ItemMayanQuestCrown extends Item implements InvItem.IItemInitialEnchantmens {
   public ItemMayanQuestCrown(String itemName, EquipmentSlot type) {
      super(new Item.Properties()
         .humanoidArmor(MillArmorMaterials.MAYAN_QUEST_CROWN, MillArmorMaterials.armorType(type))
         .setId(MillRegistry.itemKey(itemName)));
   }

   @Override
   public void applyEnchantments(ItemStack stack, RegistryAccess registryAccess) {
      // 1.12 re-applied Respiration 3 / Aqua Affinity 1 / Protection 4 when the helmet lacked them.
      // 26.2: resolve each enchantment Holder from the dynamic registry and write via ItemStack.enchant.
      net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> ench = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
      applyIfMissing(stack, ench.getOrThrow(Enchantments.RESPIRATION), 3);
      applyIfMissing(stack, ench.getOrThrow(Enchantments.AQUA_AFFINITY), 1);
      applyIfMissing(stack, ench.getOrThrow(Enchantments.PROTECTION), 4);
   }

   private static void applyIfMissing(ItemStack stack, Holder<Enchantment> enchantment, int level) {
      if (EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) == 0) {
         stack.enchant(enchantment, level);
      }
   }
}
