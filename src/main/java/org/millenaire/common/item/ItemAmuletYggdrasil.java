package org.millenaire.common.item;

/**
 * Yggdrasil amulet — its model displays a "score" derived from the holder's altitude (floor(Y)/8,
 * capped at 127) that swaps between 16 sub-models. 1.12 drove this via
 * {@code addPropertyOverride("score")}; 26.2 uses a data-driven {@code minecraft:range_dispatch}
 * item-model (assets/millenaire/items/yggdrasil_amulet.json) reading the custom client property
 * {@code millenaire:amulet_score} (kind=yggdrasil) implemented by
 * {@link org.millenaire.client.item.AmuletScoreProperty} and registered in the client initializer.
 */
public class ItemAmuletYggdrasil extends ItemMill {
   public ItemAmuletYggdrasil(String itemName) {
      super(itemName);
   }
}
