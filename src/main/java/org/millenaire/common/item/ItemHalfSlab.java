package org.millenaire.common.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import org.millenaire.common.block.BlockHalfSlab;
import org.millenaire.common.forge.MillRegistry;

/**
 * BlockItem for Millénaire half-slabs. 1.12 hand-rolled the "place a second slab to form the full
 * block" behaviour (canPlaceBlockOnSide + onItemUse + getCollisionBoundingBox + setBlockState). In
 * 26.2 the vanilla {@link net.minecraft.world.level.block.SlabBlock} handles single→double placement
 * itself through its {@code TYPE} blockstate, so a plain {@link BlockItem} is sufficient and the
 * custom placement code is dropped.
 */
public class ItemHalfSlab extends BlockItem {
   public ItemHalfSlab(BlockHalfSlab singleSlab) {
      super(singleSlab, new Item.Properties().setId(MillRegistry.itemKeyFor(singleSlab)));
      // 26.2: the 1.12 custom double-slab placement (forming the base block on second click) is no
      // longer needed — BlockHalfSlab extends vanilla SlabBlock, whose SlabBlock.TYPE +
      // getStateForPlacement handle single→double placement.
   }
}
