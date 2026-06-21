package org.millenaire.common.item;

/**
 * Alchemist amulet — its model displays a "score" (nearby ore richness in a 5-block radius) that
 * swaps between 16 sub-models. 1.12 drove this via {@code addPropertyOverride("score")}; 26.2 uses a
 * data-driven {@code minecraft:range_dispatch} item-model
 * (assets/millenaire/items/alchemist_amulet.json) reading the custom client property
 * {@code millenaire:amulet_score} (kind=alchemist) implemented by
 * {@link org.millenaire.client.item.AmuletScoreProperty} and registered in the client initializer.
 */
public class ItemAmuletAlchemist extends ItemMill {
   private static final int radius = 5;

   public ItemAmuletAlchemist(String itemName) {
      super(itemName);
   }
}
