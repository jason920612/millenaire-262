package org.millenaire.common.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;

import org.millenaire.common.forge.MillRegistry;

/**
 * Millénaire armour piece. 1.12 extended {@code ItemArmor} with an {@code ArmorMaterial} +
 * {@code EquipmentSlot}; on 26.2 there is no ItemArmor — armour is a plain {@link Item} built with
 * {@code Item.Properties().humanoidArmor(ArmorMaterial, ArmorType)}. The registration still passes a
 * vanilla {@link EquipmentSlot} which is mapped to the 26.2 {@code ArmorType} here.
 */
public class ItemMillenaireArmour extends Item {
   public ItemMillenaireArmour(String itemName, ArmorMaterial material, EquipmentSlot type) {
      super(new Item.Properties()
         .humanoidArmor(material, MillArmorMaterials.armorType(type))
         .setId(MillRegistry.itemKey(itemName)));
   }
}
