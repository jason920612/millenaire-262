package org.millenaire.common.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

import org.millenaire.common.forge.MillRegistry;

/**
 * Rosette pane. Vanilla {@link IronBarsBlock} handles the N/E/S/W bar connections; the six
 * extra ROSETTE_* booleans encode adjacency of like rosette blocks. The 1.12
 * {@code getActualState} neighbour computation and {@code BeaconBlock.updateColorAsync}
 * hooks are dropped (TODO: recompute ROSETTE_* in updateShape so the decorative joins
 * still render). The 1.12 ctor took a Material; on 26.2 only the sound is passed.
 */
public class BlockRosette extends IronBarsBlock {
   public static final BooleanProperty ROSETTE_NORTH = BooleanProperty.create("ros_n");
   public static final BooleanProperty ROSETTE_EAST = BooleanProperty.create("ros_e");
   public static final BooleanProperty ROSETTE_SOUTH = BooleanProperty.create("ros_s");
   public static final BooleanProperty ROSETTE_WEST = BooleanProperty.create("ros_w");
   public static final BooleanProperty ROSETTE_UP = BooleanProperty.create("ros_u");
   public static final BooleanProperty ROSETTE_DOWN = BooleanProperty.create("ros_d");

   public BlockRosette(String blockName, SoundType soundType) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(soundType)
         .mapColor(MapColor.COLOR_GRAY)
         .strength(0.3F)
         .noOcclusion());
      this.registerDefaultState(this.defaultBlockState()
         .setValue(ROSETTE_NORTH, false)
         .setValue(ROSETTE_EAST, false)
         .setValue(ROSETTE_SOUTH, false)
         .setValue(ROSETTE_WEST, false)
         .setValue(ROSETTE_UP, false)
         .setValue(ROSETTE_DOWN, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(ROSETTE_NORTH, ROSETTE_EAST, ROSETTE_SOUTH, ROSETTE_WEST, ROSETTE_UP, ROSETTE_DOWN);
   }
}
