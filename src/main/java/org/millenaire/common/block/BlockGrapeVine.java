package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockGrapeVine extends BlockMillCrops {
   public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

   public BlockGrapeVine(String cropName, boolean requireIrrigation, boolean slowGrowth, Identifier seed) {
      super(cropName, requireIrrigation, slowGrowth, seed);
      this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0).setValue(HALF, DoubleBlockHalf.LOWER));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(AGE, HALF);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return Shapes.block();
   }

   @Override
   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
         BlockState below = level.getBlockState(pos.below());
         return below.getBlock() == this && below.getValue(HALF) == DoubleBlockHalf.LOWER;
      } else {
         BlockState above = level.getBlockState(pos.above());
         return super.canSurvive(state, level, pos)
            && (above.isAir() || (above.getBlock() == this && above.getValue(HALF) == DoubleBlockHalf.UPPER));
      }
   }

   @Override
   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      level.setBlock(pos.above(), this.defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER), 2);
   }

   /**
    * 1.12 {@code checkAndDropBlock} (func_176475_e), called at the top of each random tick: if this vine can
    * no longer survive (its paired half is missing or the lower half lost its soil) remove BOTH halves so a
    * broken/orphaned half doesn't linger. The 26.2 {@link #canSurvive} already encodes the same
    * lower-needs-soil / halves-must-pair logic, so reuse it.
    */
   private void checkAndDropBlock(ServerLevel level, BlockPos pos, BlockState state) {
      if (!this.canSurvive(state, level, pos)) {
         boolean upper = state.getValue(HALF) == DoubleBlockHalf.UPPER;
         BlockPos upperPos = upper ? pos : pos.above();
         BlockPos lowerPos = upper ? pos.below() : pos;
         if (level.getBlockState(upperPos).getBlock() == this) {
            level.setBlock(upperPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
         }
         if (level.getBlockState(lowerPos).getBlock() == this) {
            level.setBlock(lowerPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
         }
      }
   }

   @Override
   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      this.checkAndDropBlock(level, pos, state);
      if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
         return;
      }
      if (level.getMaxLocalRawBrightness(pos.above()) >= 9) {
         int age = this.getAge(state);
         if (age < this.getMaxAge()) {
            float growthChance = this.getMillGrowthChance(level, pos);
            if (growthChance > 0.0F && random.nextInt((int) (25.0F / growthChance)) == 0) {
               level.setBlock(pos, this.getStateForAge(age + 1).setValue(HALF, DoubleBlockHalf.LOWER), 2);
               level.setBlock(pos.above(), this.getStateForAge(age + 1).setValue(HALF, DoubleBlockHalf.UPPER), 2);
            }
         }
      }
   }

   @Override
   public void growCrops(Level level, BlockPos pos, BlockState state) {
      int age = Math.min(this.getMaxAge(), this.getAge(state) + this.getBonemealAgeIncrease(level));
      if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
         level.setBlock(pos.below(), this.getStateForAge(age).setValue(HALF, DoubleBlockHalf.LOWER), 2);
         level.setBlock(pos, this.getStateForAge(age).setValue(HALF, DoubleBlockHalf.UPPER), 2);
      } else {
         level.setBlock(pos, this.getStateForAge(age).setValue(HALF, DoubleBlockHalf.LOWER), 2);
         level.setBlock(pos.above(), this.getStateForAge(age).setValue(HALF, DoubleBlockHalf.UPPER), 2);
      }
   }
}
