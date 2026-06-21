package org.millenaire.common.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.forge.MillRegistry;

public class BlockMillStairs extends StairBlock {
   public BlockMillStairs(String blockName, BlockState baseBlock) {
      super(baseBlock, BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(baseBlock.getBlock().defaultBlockState().getSoundType()));
      // 1.12 useNeighborBrightness / setCreativeTab obsolete; render model from JSON assets.
   }
}
