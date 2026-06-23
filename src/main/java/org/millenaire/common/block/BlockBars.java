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
 * Wooden bars. Vanilla {@link IronBarsBlock} handles the N/E/S/W connection state and shapes
 * via {@code getStateForPlacement} + {@code updateShape}.
 *
 * <p>1.12 reference: {@code BlockBars.canPaneConnectTo} connected to vanilla panes
 * ({@code func_193393_b}), to anything that returned {@code canBeConnectedTo} (other panes/bars,
 * glass, solid faces), <b>and</b> to Millénaire walls ({@code state.getBlock() instanceof
 * BlockMillWall}). That extra wall connection was dropped in the first port.
 *
 * <p>26.2 API: vanilla {@code attachsTo(state, faceSolid)} already covers other {@link IronBarsBlock}
 * (panes/bars) and any block in {@code BlockTags.WALLS}, but it is declared {@code final} so it cannot
 * be overridden, and the Millénaire wall blocks ({@link BlockMillWall} and the sandstone/snow walls)
 * are not in the vanilla WALLS tag. The connection is therefore restored by overriding
 * {@link #getStateForPlacement} and {@link #updateShape} with the same per-side computation vanilla
 * uses, but routed through {@link #connectsTo} which adds the 1.12 "connect to any {@link WallBlock}"
 * rule on top of vanilla's {@code attachsTo}. Placement, collision and rendering are unchanged — they
 * all derive from the same NORTH/EAST/SOUTH/WEST booleans.
 */
public class BlockBars extends IronBarsBlock {
   public BlockBars(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(5.0F, 10.0F)
         .noOcclusion());
   }

   /**
    * 1.12 {@code canPaneConnectTo}: vanilla's {@code attachsTo} (panes/bars/WALLS-tag/solid face) plus
    * a connection to any Millénaire wall. Every Mill wall extends {@link WallBlock}, so an
    * {@code instanceof WallBlock} test covers {@link BlockMillWall} and the sandstone/snow walls.
    */
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
