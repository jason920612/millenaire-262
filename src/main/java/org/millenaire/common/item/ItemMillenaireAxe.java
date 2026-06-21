package org.millenaire.common.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import org.millenaire.common.forge.MillRegistry;

public class ItemMillenaireAxe extends Item {
   public ItemMillenaireAxe(String itemName, ToolMaterial material, float damage, float speed) {
      super(new Item.Properties().axe(material, damage, speed).setId(MillRegistry.itemKey(itemName)));
   }
}
