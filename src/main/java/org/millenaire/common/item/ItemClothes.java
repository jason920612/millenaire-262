package org.millenaire.common.item;

/**
 * Villager clothing item — pure data (a cloth name + a priority). 1.12 called {@code setMaxDamage(0)};
 * that is the default for a non-damageable item now, so it is dropped.
 */
public class ItemClothes extends ItemMill {
   private final String clothName;
   private final int priority;

   public ItemClothes(String itemName, int priority) {
      super(itemName);
      this.clothName = itemName;
      this.priority = priority;
   }

   public String getClothName(int meta) {
      return this.clothName;
   }

   public int getClothPriority(int meta) {
      return this.priority;
   }
}
