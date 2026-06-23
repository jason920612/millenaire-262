package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import org.millenaire.common.forge.MillRegistry;

/**
 * Paper-wall style pane. The 1.12 ctor took a Material; on 26.2 that is gone, so the sound is passed
 * directly.
 *
 * <p>1.12 reference: like {@link BlockBars}, {@code BlockMillPane.canPaneConnectTo} connected to vanilla
 * panes/glass/solid faces <b>and</b> to Millénaire walls ({@code instanceof BlockMillWall}). 26.2's
 * {@code attachsTo} is {@code final} and the Mill walls are not in {@code BlockTags.WALLS}, so the wall
 * connection is restored here exactly as in {@link BlockBars}: {@link #getStateForPlacement} and
 * {@link #updateShape} compute the per-side booleans through {@link #connectsTo}, which adds the
 * "connect to any {@link WallBlock}" rule on top of vanilla's {@code attachsTo}.
 */
public class BlockMillPane extends IronBarsBlock {
   public BlockMillPane(String blockName, SoundType soundType) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(soundType)
         .strength(0.1F)
         .noOcclusion());
   }

   /** 1.12 {@code canPaneConnectTo}: vanilla {@code attachsTo} plus connection to any Millénaire wall. */
   protected boolean connectsTo(BlockState neighbour, boolean faceSolid) {
      return neighbour.getBlock() instanceof WallBlock || this.attachsTo(neighbour, faceSolid);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockGetter level = context.getLevel();
      BlockPos pos = context.getClickedPos();
      FluidState replacedFluid = level.getFluidState(pos);
      BlockPos north = pos.north();
      BlockPos south = pos.south();
      BlockPos west = pos.west();
      BlockPos east = pos.east();
      BlockState northState = level.getBlockState(north);
      BlockState southState = level.getBlockState(south);
      BlockState westState = level.getBlockState(west);
      BlockState eastState = level.getBlockState(east);
      return this.defaultBlockState()
         .setValue(NORTH, this.connectsTo(northState, northState.isFaceSturdy(level, north, Direction.SOUTH)))
         .setValue(SOUTH, this.connectsTo(southState, southState.isFaceSturdy(level, south, Direction.NORTH)))
         .setValue(WEST, this.connectsTo(westState, westState.isFaceSturdy(level, west, Direction.EAST)))
         .setValue(EAST, this.connectsTo(eastState, eastState.isFaceSturdy(level, east, Direction.WEST)))
         .setValue(WATERLOGGED, replacedFluid.is(Fluids.WATER));
   }

   @Override
   protected BlockState updateShape(
      BlockState state,
      LevelReader level,
      ScheduledTickAccess ticks,
      BlockPos pos,
      Direction directionToNeighbour,
      BlockPos neighbourPos,
      BlockState neighbourState,
      RandomSource random
   ) {
      if (state.getValue(WATERLOGGED)) {
         ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
      }

      return directionToNeighbour.getAxis().isHorizontal()
         ? state.setValue(
            PROPERTY_BY_DIRECTION.get(directionToNeighbour),
            this.connectsTo(neighbourState, neighbourState.isFaceSturdy(level, neighbourPos, directionToNeighbour.getOpposite()))
         )
         : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
   }
}
