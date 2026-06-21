package org.millenaire.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.millenaire.common.forge.MillRegistry;

public class BlockMillStatue extends DirectionalBlock {
   private static final VoxelShape CARVING_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

   public BlockMillStatue(String blockName, SoundType sound) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(sound)
         .strength(0.5F, 2.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN));
   }

   @Override
   protected MapCodec<? extends DirectionalBlock> codec() {
      return simpleCodec(p -> {
         throw new UnsupportedOperationException("BlockMillStatue is registered programmatically");
      });
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(FACING);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return CARVING_SHAPE;
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      Direction f = context.getNearestLookingDirection().getOpposite();
      return f != Direction.DOWN && f != Direction.UP
         ? this.defaultBlockState().setValue(FACING, f)
         : this.defaultBlockState().setValue(FACING, Direction.SOUTH);
   }
}
