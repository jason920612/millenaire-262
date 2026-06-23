package org.millenaire.common.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

import org.millenaire.common.block.BlockDecorativeStone;
import org.millenaire.common.forge.MillRegistry;

/**
 * BlockItem for the old metadata-variant blocks (decorative stone/wood/earth, stained glass, etc.).
 * 1.12 set {@code hasSubtypes}/{@code maxDamage=0} and routed {@code getTranslationKey(stack)} through
 * {@link IMetaBlockName#getSpecialName}. Item metadata is gone in 26.2, so each variant is now a
 * distinct blockstate carried on the placed block via the {@code minecraft:block_state} data component.
 *
 * <p>For {@link BlockDecorativeStone} this exposes one creative-tab entry per {@code VARIANT} value
 * (matching the 1.12 {@code getSubBlocks}, which added every variant except COOKEDBRICK) via
 * {@link IVariantCreativeItem}. Other blocks keep a single plain entry.
 */
public class ItemBlockMeta extends BlockItem implements IVariantCreativeItem {
   public ItemBlockMeta(Block block) {
      super(block, new Item.Properties().setId(MillRegistry.itemKeyFor(block)));
      // 26.2: item metadata is gone. The 1.12 hasSubtypes/getMetadata/getTranslationKey(stack) variant
      // naming (via IMetaBlockName.getSpecialName) no longer applies — variants are separate blockstates
      // handled by blockstate/model JSON; the variant rides on the BLOCK_STATE data component (below).
   }

   @Override
   public List<ItemStack> creativeVariants() {
      List<ItemStack> stacks = new ArrayList<>();
      if (this.getBlock() instanceof BlockDecorativeStone) {
         // 1.12 BlockDecorativeStone.getSubBlocks added every variant EXCEPT COOKEDBRICK.
         for (BlockDecorativeStone.EnumType variant : BlockDecorativeStone.EnumType.values()) {
            if (variant == BlockDecorativeStone.EnumType.COOKEDBRICK) {
               continue;
            }
            ItemStack stack = new ItemStack(this);
            BlockItemStateProperties props = BlockItemStateProperties.EMPTY.with(BlockDecorativeStone.VARIANT, variant);
            stack.set(DataComponents.BLOCK_STATE, props);
            stacks.add(stack);
         }
      } else {
         stacks.add(new ItemStack(this));
      }
      return stacks;
   }
}
