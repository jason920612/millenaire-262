package org.millenaire.common.item;

/**
 * Vishnu amulet — its model displays a "score" (proximity of the nearest hostile mob, 20-block
 * radius) that swaps between 16 sub-models. 1.12 drove this via {@code addPropertyOverride("score")};
 * 26.2 uses a data-driven {@code minecraft:range_dispatch} item-model
 * (assets/millenaire/items/vishnu_amulet.json) reading the custom client property
 * {@code millenaire:amulet_score} (kind=vishnu) implemented by
 * {@link org.millenaire.client.item.AmuletScoreProperty} and registered in the client initializer.
 */
public class ItemAmuletVishnu extends ItemMill {
   private static final int radius = 20;

   public ItemAmuletVishnu(String itemName) {
      super(itemName);
   }
}
