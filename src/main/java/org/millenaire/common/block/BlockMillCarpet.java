package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.millenaire.common.forge.MillRegistry;

/**
 * Canonical example of porting interaction/collision blocks: 1.12 AABB (0-1 scale) +
 * {@code getBoundingBox}/{@code canPlaceBlockAt}/{@code neighborChanged} become a
 * {@link VoxelShape} via {@link Block#box} (0-16 scale), {@code getShape}, {@code canSurvive}
 * and {@code updateShape} (which drops the carpet to air when its support is gone).
 */
public class BlockMillCarpet extends Block {
   protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

   public BlockMillCarpet(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOL)
         .strength(0.1F)
         .noOcclusion());
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   @Override
   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      return !level.getBlockState(pos.below()).isAir();
   }

   @Override
   protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
         Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
      return !state.canSurvive(level, pos)
         ? Blocks.AIR.defaultBlockState()
         : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
   }
}
