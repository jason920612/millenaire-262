package org.millenaire.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.millenaire.common.forge.MillRegistry;

public class BlockCustomSnow extends Block {
   public BlockCustomSnow(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.SNOW)
         .strength(0.2F));
      // 1.12 extended BlockSnowBlock; in 26.2 the snow block is a plain Block.
      // quantityDropped / harvest "shovel" are now data-driven (loot table + mineable tag).
   }
}
