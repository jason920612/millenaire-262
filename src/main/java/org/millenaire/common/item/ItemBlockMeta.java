package org.millenaire.common.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import org.millenaire.common.forge.MillRegistry;

/**
 * BlockItem for the old metadata-variant blocks (decorative stone/wood/earth, stained glass, etc.).
 * 1.12 set {@code hasSubtypes}/{@code maxDamage=0} and routed {@code getTranslationKey(stack)} through
 * {@link IMetaBlockName#getSpecialName}. Item metadata is gone in 26.2, so each variant is now a
 * distinct blockstate/item and the per-meta naming is dropped.
 */
public class ItemBlockMeta extends BlockItem {
   public ItemBlockMeta(Block block) {
      super(block, new Item.Properties().setId(MillRegistry.itemKeyFor(block)));
      // 26.2: item metadata is gone. The 1.12 hasSubtypes/getMetadata/getTranslationKey(stack) variant
      // naming (via IMetaBlockName.getSpecialName) no longer applies — variants are separate blockstates
      // handled by blockstate/model JSON, so this is a plain BlockItem with no per-meta validation.
   }
}
