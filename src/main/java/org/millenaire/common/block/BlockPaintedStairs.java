package org.millenaire.common.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.DyeColor;

public class BlockPaintedStairs extends BlockMillStairs implements IPaintedBlock {
   private final String baseBlockName;
   private final DyeColor colour;

   public BlockPaintedStairs(String baseBlockName, BlockState baseBlock, DyeColor colour) {
      super(baseBlockName + "_" + colour.getName(), baseBlock);
      this.baseBlockName = baseBlockName;
      this.colour = colour;
   }

   @Override
   public String getBlockType() {
      return this.baseBlockName;
   }

   @Override
   public DyeColor getDyeColour() {
      return this.colour;
   }
}
