package org.millenaire.common.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SlabBlock;

import org.millenaire.common.forge.MillRegistry;

/**
 * BlockItem for the old metadata-variant slab blocks (tile slabs). 1.12 extended {@code ItemSlab}
 * (auto double/single placement based on meta) and routed naming through {@link IMetaBlockName}.
 * Both {@code ItemSlab} and item metadata are gone in 26.2; vanilla {@link SlabBlock} now handles
 * single/double placement itself via its {@code TYPE} blockstate, so a plain {@link BlockItem} on the
 * slab block suffices. The {@code fullBlock} argument the 1.12 ctor took is no longer needed.
 */
public class ItemSlabMeta extends BlockItem {
   public ItemSlabMeta(SlabBlock halfBlock, SlabBlock fullBlock) {
      super(halfBlock, new Item.Properties().setId(MillRegistry.itemKeyFor(halfBlock)));
      // 26.2: item metadata is gone, so the per-meta variant naming via IMetaBlockName.getSpecialName is
      // dropped. Single/double slab state is handled by SlabBlock.TYPE + blockstate JSON. The fullBlock
      // parameter is retained only to keep the MillBlocks call sites unchanged.
   }
}
