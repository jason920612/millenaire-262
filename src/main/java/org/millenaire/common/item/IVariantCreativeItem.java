package org.millenaire.common.item;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * Implemented by Mill {@link net.minecraft.world.item.BlockItem}s whose block carries several 1.12
 * metadata variants on a single blockstate {@code VARIANT} property (decorative stone, stone slabs).
 *
 * <p>1.12 reference: those blocks overrode {@code getSubBlocks} to add one creative-tab {@link ItemStack}
 * per variant (with the variant in item metadata). 26.2 has no item metadata, so the variant is carried
 * on the placed block via the {@code minecraft:block_state} data component ({@code DataComponents.BLOCK_STATE});
 * {@code BlockItem.getPlacementState} applies it on placement. The Mill creative-tab generator
 * ({@code MillRegistry.populateTab}) calls {@link #creativeVariants()} instead of adding a single default
 * stack, so every variant appears as its own distinct creative entry exactly as in 1.12.
 */
public interface IVariantCreativeItem {
   /** One stack per variant that should appear in the creative tab (matching 1.12 {@code getSubBlocks}). */
   List<ItemStack> creativeVariants();
}
