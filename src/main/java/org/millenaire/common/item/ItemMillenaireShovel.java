package org.millenaire.common.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import org.millenaire.common.forge.MillRegistry;

public class ItemMillenaireShovel extends Item {
   public ItemMillenaireShovel(String itemName, ToolMaterial material) {
      super(new Item.Properties().shovel(material, 1.5F, -3.0F).setId(MillRegistry.itemKey(itemName)));
   }
}
