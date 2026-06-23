package org.millenaire.common.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.SlabBlock;

import org.millenaire.common.block.BlockDecorativeStone;
import org.millenaire.common.block.BlockSlabStone;
import org.millenaire.common.forge.MillRegistry;

/**
 * BlockItem for the old metadata-variant slab blocks (tile slabs). 1.12 extended {@code ItemSlab}
 * (auto double/single placement based on meta) and routed naming through {@link IMetaBlockName}.
 * Both {@code ItemSlab} and item metadata are gone in 26.2; vanilla {@link SlabBlock} now handles
 * single/double placement itself via its {@code TYPE} blockstate, so a plain {@link BlockItem} on the
 * slab block suffices. The {@code fullBlock} argument the 1.12 ctor took is no longer needed.
 *
 * <p>For {@link BlockSlabStone} this exposes one creative-tab entry per slab-capable {@code VARIANT}
 * value (matching the 1.12 {@code getSubBlocks}, which added every variant whose {@code hasSlab()} was
 * true) via {@link IVariantCreativeItem}; the variant rides on the {@code minecraft:block_state} data
 * component so placement reproduces the right variant. Other slab blocks keep a single plain entry.
 */
public class ItemSlabMeta extends BlockItem implements IVariantCreativeItem {
   public ItemSlabMeta(SlabBlock halfBlock, SlabBlock fullBlock) {
      super(halfBlock, new Item.Properties().setId(MillRegistry.itemKeyFor(halfBlock)));
      // 26.2: item metadata is gone, so the per-meta variant naming via IMetaBlockName.getSpecialName is
      // dropped. Single/double slab state is handled by SlabBlock.TYPE + blockstate JSON. The variant (for
      // BlockSlabStone) rides on the BLOCK_STATE data component (below). The fullBlock parameter is retained
      // only to keep the MillBlocks call sites unchanged.
   }

   @Override
   public List<ItemStack> creativeVariants() {
      List<ItemStack> stacks = new ArrayList<>();
      if (this.getBlock() instanceof BlockSlabStone) {
         // 1.12 BlockSlabStone.getSubBlocks added only variants whose hasSlab() was true.
         for (BlockDecorativeStone.EnumType variant : BlockDecorativeStone.EnumType.values()) {
            if (!variant.hasSlab()) {
               continue;
            }
            ItemStack stack = new ItemStack(this);
            BlockItemStateProperties props = BlockItemStateProperties.EMPTY.with(BlockSlabStone.VARIANT, variant);
            stack.set(DataComponents.BLOCK_STATE, props);
            stacks.add(stack);
         }
      } else {
         stacks.add(new ItemStack(this));
      }
      return stacks;
   }
}
