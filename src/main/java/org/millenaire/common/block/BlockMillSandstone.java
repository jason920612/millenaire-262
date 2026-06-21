package org.millenaire.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.millenaire.common.forge.MillRegistry;

public class BlockMillSandstone extends Block {
   public BlockMillSandstone(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.STONE)
         .strength(0.8F));
      // 1.12 setHarvestLevel("pickaxe", 0) is now data-driven via the
      // millenaire:mineable/pickaxe block tag. setTranslationKey/setRegistryName/
      // setCreativeTab are obsolete (handled by the registry id and creative tab contents).
   }
}
