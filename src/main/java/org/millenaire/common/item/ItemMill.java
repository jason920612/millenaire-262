package org.millenaire.common.item;

import net.minecraft.world.item.Item;

import org.millenaire.common.forge.MillRegistry;

/**
 * Base Millénaire item (coins/deniers etc.). 1.12 set translationKey/registryName/
 * creativeTab and registered a model in initModel(); on 26.2 the registry id is carried
 * on the Properties via setId, the model is the inventory model JSON in resources, and
 * creative-tab membership is added separately (ItemGroupEvents).
 */
public class ItemMill extends Item {
   public ItemMill(String itemName) {
      super(new Item.Properties().setId(MillRegistry.itemKey(itemName)));
   }

   /**
    * Lets subclasses supply extra {@link Item.Properties} (e.g. {@code stacksTo(1)}). The registry id is
    * always (re)applied here so callers don't have to remember {@code setId}.
    */
   protected ItemMill(String itemName, Item.Properties properties) {
      super(properties.setId(MillRegistry.itemKey(itemName)));
   }
}
