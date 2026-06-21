package org.millenaire.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.millenaire.common.forge.MillRegistry;

public class BlockCustomIce extends Block {
   public BlockCustomIce(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.GLASS)
         .strength(0.5F)
         .friction(0.98F)
         .noOcclusion());
      // 1.12 lightOpacity=20 / TRANSLUCENT render layer now handled by the model +
      // BlockRenderLayerMap (client init); isOpaqueCube/isFullCube==false -> noOcclusion().
   }
}
