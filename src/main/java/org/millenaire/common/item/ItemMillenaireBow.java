package org.millenaire.common.item;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;

import org.millenaire.common.forge.MillRegistry;

public class ItemMillenaireBow extends BowItem {
   public float speedFactor;
   public float damageBonus;
   private final int enchantability;

   public ItemMillenaireBow(String itemName, float speedFactor, float damageBonus, int enchantability) {
      super(new Item.Properties().enchantable(enchantability).setId(MillRegistry.itemKey(itemName)));
      this.speedFactor = speedFactor;
      this.damageBonus = damageBonus;
      this.enchantability = enchantability;
   }
}
