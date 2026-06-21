package org.millenaire.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.millenaire.common.forge.MillRegistry;

/**
 * The 1.12 BlockDecorativeEarth declared a metadata {@code variant} PropertyEnum with a SINGLE value
 * (DIRTWALL). 26.2's {@code StateDefinition} rejects an EnumProperty with &lt;= 1 possible values
 * (crashes at construction), and a one-variant block has no need of the property at all — so it is a
 * plain single-state block. Its blockstate JSON uses the default {@code ""} variant.
 */
public class BlockDecorativeEarth extends Block {

   public BlockDecorativeEarth(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.GRAVEL)
         .strength(0.8F));
   }
}
