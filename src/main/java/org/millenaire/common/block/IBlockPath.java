package org.millenaire.common.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

public interface IBlockPath {
   BooleanProperty STABLE = BooleanProperty.create("stable");

   BlockPath getDoubleSlab();

   BlockPathSlab getSingleSlab();

   boolean isFullPath();
}
