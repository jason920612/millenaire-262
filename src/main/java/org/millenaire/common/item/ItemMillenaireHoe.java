package org.millenaire.common.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import org.millenaire.common.forge.MillRegistry;

public class ItemMillenaireHoe extends Item {
   public ItemMillenaireHoe(String itemName, ToolMaterial material) {
      super(new Item.Properties().hoe(material, 0.0F, -3.0F).setId(MillRegistry.itemKey(itemName)));
   }
}
