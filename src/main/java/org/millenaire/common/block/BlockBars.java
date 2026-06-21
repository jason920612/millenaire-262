package org.millenaire.common.block;

import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.millenaire.common.forge.MillRegistry;

/**
 * Wooden bars. Vanilla {@link IronBarsBlock} handles the N/E/S/W connection state and
 * shapes; the 1.12 custom {@code canPaneConnectTo}/{@code getActualState} overrides
 * (which also connected to Millénaire walls) are dropped — TODO: re-add wall connection
 * via {@code attachsTo} once BlockMillWall is ported.
 */
public class BlockBars extends IronBarsBlock {
   public BlockBars(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(5.0F, 10.0F)
         .noOcclusion());
   }
}
