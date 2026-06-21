package org.millenaire.common.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import org.millenaire.common.forge.MillRegistry;

/** Pickaxe: 26.2 has no PickaxeItem — a plain Item built with the pickaxe tool properties. */
public class ItemMillenairePickaxe extends Item {
   public ItemMillenairePickaxe(String itemName, ToolMaterial material) {
      super(new Item.Properties().pickaxe(material, 1.0F, -2.8F).setId(MillRegistry.itemKey(itemName)));
   }
}
