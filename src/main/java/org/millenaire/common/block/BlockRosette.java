package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

import org.millenaire.common.forge.MillRegistry;

/**
 * Rosette pane. Vanilla {@link IronBarsBlock} handles the N/E/S/W bar connections; the six
 * extra ROSETTE_* booleans encode adjacency of like rosette blocks (the multipart
 * {@code rosette.json} blockstate keys off both sets to pick the decorative join model).
 *
 * <p>1.12 reference: {@code getActualState} recomputed all six ROSETTE_* every render by checking,
 * for each of the six directions, whether the neighbour {@code == this} ({@code hasRosette}). The
 * vanilla N/E/S/W bar connections came from {@code BlockPane.getActualState}. (The
 * {@code BlockBeacon.updateColorAsync} hooks were a leftover from copying the beacon block and are
 * not part of the rosette behaviour, so they are intentionally not ported.)
 *
 * <p>26.2 API: blockstates are immutable and computed once at placement / on neighbour change, so the
 * 1.12 per-render {@code getActualState} is replaced by computing ROSETTE_* in {@link #getStateForPlacement}
 * and recomputing them in {@link #updateShape}. The vanilla horizontal bar connection is preserved by
 * delegating to {@code super}; the six ROSETTE_* (including UP/DOWN, which vanilla bars never touch)
 * are layered on top so the decorative joins render.
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

   /** 1.12 {@code hasRosette}: a like rosette block sits in the given direction. */
   private boolean hasRosette(BlockGetter level, BlockPos pos, Direction direction) {
      return level.getBlockState(pos.relative(direction)).getBlock() == this;
   }

   /**
    * 1.12 {@code canPaneConnectTo}: vanilla's {@code attachsTo} (panes/bars/WALLS-tag/solid face) plus
    * a connection to any Millénaire wall (all extend {@link WallBlock}). Mirrors {@link BlockBars#connectsTo}.
    */
   private boolean connectsTo(BlockState neighbour, boolean faceSolid) {
      return neighbour.getBlock() instanceof WallBlock || this.attachsTo(neighbour, faceSolid);
   }

   /** Recompute the four vanilla bar sides with the 1.12 wall-aware {@link #connectsTo} predicate. */
   private BlockState withBarSides(BlockState state, BlockGetter level, BlockPos pos) {
      BlockPos north = pos.north();
      BlockPos south = pos.south();
      BlockPos west = pos.west();
      BlockPos east = pos.east();
      BlockState ns = level.getBlockState(north);
      BlockState ss = level.getBlockState(south);
      BlockState ws = level.getBlockState(west);
      BlockState es = level.getBlockState(east);
      return state
         .setValue(NORTH, this.connectsTo(ns, ns.isFaceSturdy(level, north, Direction.SOUTH)))
         .setValue(SOUTH, this.connectsTo(ss, ss.isFaceSturdy(level, south, Direction.NORTH)))
         .setValue(WEST, this.connectsTo(ws, ws.isFaceSturdy(level, west, Direction.EAST)))
         .setValue(EAST, this.connectsTo(es, es.isFaceSturdy(level, east, Direction.WEST)));
   }

   /**
    * Sets all six ROSETTE_* on {@code state} from the neighbours around {@code pos}. Used both at
    * placement and whenever any neighbour changes, so the decorative join always matches reality.
    */
   private BlockState withRosetteJoins(BlockState state, BlockGetter level, BlockPos pos) {
      return state
         .setValue(ROSETTE_NORTH, this.hasRosette(level, pos, Direction.NORTH))
         .setValue(ROSETTE_EAST, this.hasRosette(level, pos, Direction.EAST))
         .setValue(ROSETTE_SOUTH, this.hasRosette(level, pos, Direction.SOUTH))
         .setValue(ROSETTE_WEST, this.hasRosette(level, pos, Direction.WEST))
         .setValue(ROSETTE_UP, this.hasRosette(level, pos, Direction.UP))
         .setValue(ROSETTE_DOWN, this.hasRosette(level, pos, Direction.DOWN));
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      // super computes the vanilla N/E/S/W bar connections + waterlogging. Recompute the bar sides with
      // the wall-aware predicate (1.12 connected to Mill walls too), then layer the ROSETTE_* on top.
      BlockState placed = super.getStateForPlacement(context);
      placed = this.withBarSides(placed, context.getLevel(), context.getClickedPos());
      return this.withRosetteJoins(placed, context.getLevel(), context.getClickedPos());
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
      // super updates the vanilla bar connection (horizontal sides) + waterlogging tick; then recompute
      // all six ROSETTE_*. A neighbour change in ANY of the six directions (incl. UP/DOWN, which vanilla
      // bars ignore) can change the decorative join, so all six are recomputed here, not just the changed side.
      BlockState updated = super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
      if (directionToNeighbour.getAxis().isHorizontal()) {
         // super set only the changed side via the (final, non-wall-aware) attachsTo; recompute all four
         // sides with the wall-aware predicate so a Mill-wall neighbour also reads as connected.
         updated = this.withBarSides(updated, level, pos);
      }
      return this.withRosetteJoins(updated, level, pos);
   }
}
