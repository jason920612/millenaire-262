package org.millenaire.common.item;

import net.minecraft.world.item.DyeColor;
import org.millenaire.common.block.BlockPaintedBricks;

public class ItemPaintBucket extends ItemMill {
   private final DyeColor colour;

   public ItemPaintBucket(String baseName, DyeColor colour) {
      super(baseName + "_" + BlockPaintedBricks.getColorName(colour));
      this.colour = colour;
   }

   public DyeColor getColour() {
      return this.colour;
   }
}
