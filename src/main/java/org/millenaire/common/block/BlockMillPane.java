package org.millenaire.common.block;

import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.millenaire.common.forge.MillRegistry;

/**
 * Paper-wall style pane. The 1.12 ctor took a Material; on 26.2 that is gone, so the
 * sound is passed directly. Custom wall connection logic dropped (see {@link BlockBars}).
 */
public class BlockMillPane extends IronBarsBlock {
   public BlockMillPane(String blockName, SoundType soundType) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(soundType)
         .strength(0.1F)
         .noOcclusion());
   }
}
