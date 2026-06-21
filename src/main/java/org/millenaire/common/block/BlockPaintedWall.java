package org.millenaire.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.DyeColor;

public class BlockPaintedWall extends BlockMillWall implements IPaintedBlock {
   private final String baseBlockName;
   private final DyeColor colour;

   public BlockPaintedWall(String baseBlockName, Block baseBlock, DyeColor colour) {
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
