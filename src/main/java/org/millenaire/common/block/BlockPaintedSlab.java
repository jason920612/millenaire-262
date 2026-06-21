package org.millenaire.common.block;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class BlockPaintedSlab extends BlockHalfSlab implements IPaintedBlock {
   private final DyeColor colour;
   private final String blockType;

   public BlockPaintedSlab(String blockType, Block baseBlock, DyeColor colour) {
      super(blockType + "_" + colour.getName(), baseBlock);
      this.blockType = blockType;
      this.colour = colour;
   }

   @Override
   public String getBlockType() {
      return this.blockType;
   }

   @Override
   public DyeColor getDyeColour() {
      return this.colour;
   }

   /** Middle-click pick yields the matching painted brick item. */
   public ItemStack pickStack() {
      return new ItemStack(MillBlocks.PAINTED_BRICK_MAP.get(this.getBlockType()).get(this.colour));
   }

   // 1.12 getItemDropped/getItem are obsolete: block drops are now data-driven
   // via the millenaire loot tables (assets/.../loot_tables).
}
