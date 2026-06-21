package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Directional rosette bars. Adds FACING (horizontal) and TOP_BOTTOM ({@link Half}) on top
 * of {@link BlockBars}. Placement keeps the 1.12 neighbour-matching so adjacent bars line
 * up; {@code isFullBlock}→{@code isCollisionShapeFullBlock}. Beacon hooks / custom
 * rotate-mirror dropped (vanilla IronBarsBlock handles connection rotation).
 */
public class BlockRosetteBars extends BlockBars {
   public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
   public static final EnumProperty<Half> TOP_BOTTOM = EnumProperty.create("topbottom", Half.class);

   public BlockRosetteBars(String blockName) {
      super(blockName);
      this.registerDefaultState(this.defaultBlockState()
         .setValue(FACING, Direction.SOUTH)
         .setValue(TOP_BOTTOM, Half.TOP));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(FACING, TOP_BOTTOM);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      Level world = context.getLevel();
      BlockPos pos = context.getClickedPos();
      BlockState above = world.getBlockState(pos.above());
      if (above.getBlock() == this && above.getValue(TOP_BOTTOM) == Half.TOP) {
         return this.defaultBlockState().setValue(TOP_BOTTOM, Half.BOTTOM).setValue(FACING, above.getValue(FACING));
      }
      BlockState west = world.getBlockState(pos.west());
      if (west.getBlock() == this && west.getValue(FACING) == Direction.WEST) {
         return this.defaultBlockState().setValue(FACING, Direction.EAST).setValue(TOP_BOTTOM, west.getValue(TOP_BOTTOM));
      }
      BlockState south = world.getBlockState(pos.south());
      if (south.getBlock() == this && south.getValue(FACING) == Direction.SOUTH) {
         return this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(TOP_BOTTOM, south.getValue(TOP_BOTTOM));
      }
      BlockState below = world.getBlockState(pos.below());
      if (below.getBlock() == this && below.getValue(TOP_BOTTOM) == Half.BOTTOM) {
         return this.defaultBlockState().setValue(TOP_BOTTOM, Half.TOP).setValue(FACING, below.getValue(FACING));
      }
      BlockState east = world.getBlockState(pos.east());
      if (east.getBlock() == this && east.getValue(FACING) == Direction.EAST) {
         return this.defaultBlockState().setValue(FACING, Direction.WEST).setValue(TOP_BOTTOM, east.getValue(TOP_BOTTOM));
      }
      BlockState north = world.getBlockState(pos.north());
      if (north.getBlock() == this && north.getValue(FACING) == Direction.NORTH) {
         return this.defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(TOP_BOTTOM, north.getValue(TOP_BOTTOM));
      }

      BlockState basic = this.defaultBlockState();
      if (!above.isCollisionShapeFullBlock(world, pos.above()) && below.isCollisionShapeFullBlock(world, pos.below())) {
         basic = basic.setValue(TOP_BOTTOM, Half.BOTTOM);
      }
      if (!west.isCollisionShapeFullBlock(world, pos.west()) && east.isCollisionShapeFullBlock(world, pos.east())) {
         basic = basic.setValue(FACING, Direction.EAST);
      } else if (!south.isCollisionShapeFullBlock(world, pos.south()) && north.isCollisionShapeFullBlock(world, pos.north())) {
         basic = basic.setValue(FACING, Direction.NORTH);
      } else if (south.isCollisionShapeFullBlock(world, pos.south()) && !north.isCollisionShapeFullBlock(world, pos.north())) {
         basic = basic.setValue(FACING, Direction.SOUTH);
      } else if (west.isCollisionShapeFullBlock(world, pos.west()) && !east.isCollisionShapeFullBlock(world, pos.east())) {
         basic = basic.setValue(FACING, Direction.WEST);
      }
      return basic;
   }
}
