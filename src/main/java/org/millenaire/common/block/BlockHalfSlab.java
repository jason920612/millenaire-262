package org.millenaire.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.millenaire.common.forge.MillRegistry;

/**
 * Base for Millénaire slabs. In 1.12 this hand-rolled the top/bottom/double
 * behaviour; on 26.2 the vanilla {@link SlabBlock} already provides it through
 * its {@code TYPE} (SlabType) and {@code WATERLOGGED} properties, so we simply
 * extend it and keep a reference to the full (double) block for drops/picking.
 */
public abstract class BlockHalfSlab extends SlabBlock {
   private final Block baseBlock;

   public BlockHalfSlab(String name, Block fullBlock) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(name))
         .sound(fullBlock.defaultBlockState().getSoundType())
         .strength(1.5F, 10.0F));
      this.baseBlock = fullBlock;
   }

   public Block getBaseBlock() {
      return this.baseBlock;
   }
}
