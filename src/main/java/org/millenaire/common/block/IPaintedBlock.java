package org.millenaire.common.block;

import net.minecraft.world.item.DyeColor;

public interface IPaintedBlock {
   String getBlockType();

   DyeColor getDyeColour();
}
