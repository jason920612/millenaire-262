package org.millenaire.common.convert;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A resolved modern item plus quantity — the target of {@link LegacyItem} conversion, and the cost
 * component of a {@link BlockSpec}.
 *
 * <p>M0 of the unified conversion protocol.</p>
 */
public record ItemSpec(Item item, Count count) {

   /** Materialises this spec as a fresh {@link ItemStack}. */
   public ItemStack toStack() {
      return new ItemStack(item, count.value());
   }
}
