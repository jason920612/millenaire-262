package org.millenaire.common.block;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.Point;

public class BlockFruitLeaves extends LeavesBlock implements BonemealableBlock {
   public static final MapCodec<BlockFruitLeaves> CODEC = simpleCodec(p -> {
      throw new UnsupportedOperationException("BlockFruitLeaves is registered programmatically");
   });
   public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
   private final BlockMillSapling.EnumMillWoodType type;
   private final Identifier fruitRL;
   private final Identifier saplingRL;

   public BlockFruitLeaves(String blockName, BlockMillSapling.EnumMillWoodType type, Identifier saplingRL, Identifier fruitRL) {
      super(0.01F, BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .strength(0.2F)
         .randomTicks()
         .noOcclusion());
      this.type = type;
      this.fruitRL = fruitRL;
      this.saplingRL = saplingRL;
      this.registerDefaultState(this.stateDefinition.any()
         .setValue(AGE, 0).setValue(DISTANCE, 7).setValue(PERSISTENT, false).setValue(WATERLOGGED, false));
   }

   @Override
   public MapCodec<? extends LeavesBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(AGE);
   }

   protected int getAge(BlockState state) {
      return state.getValue(AGE);
   }

   public int getMaxAge() {
      return 3;
   }

   public boolean isMaxAge(BlockState state) {
      return this.getAge(state) >= this.getMaxAge();
   }

   public BlockState withAge(int age) {
      return this.defaultBlockState().setValue(AGE, age);
   }

   public BlockMillSapling.EnumMillWoodType getMillType() {
      return this.type;
   }

   @Override
   protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
      // Mill fruit leaves do not spawn falling-leaf particles.
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (this.getAge(state) == this.getMaxAge()) {
         BlockItemUtilities.checkForHarvestTheft(player, pos);
         popResource(level, pos.below(), new ItemStack(BuiltInRegistries.ITEM.getValue(this.fruitRL), 1));
         level.setBlockAndUpdate(pos, state.setValue(AGE, 0));
         return InteractionResult.SUCCESS;
      }
      return InteractionResult.PASS;
   }

   // 1.12 onBlockActivated fired the fruit-harvest regardless of held item; 26.2 splits item/empty-hand
   // dispatch, so route the with-item click to the same harvest path (harvest only triggers at max age,
   // so it never conflicts with bonemeal which only applies below max age).
   @Override
   protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
         net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
      InteractionResult result = this.useWithoutItem(state, level, pos, player, hitResult);
      return result == InteractionResult.PASS ? InteractionResult.TRY_WITH_EMPTY_HAND : result;
   }

   @Override
   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      super.randomTick(state, level, pos, random);
      long worldTime = level.getOverworldClockTime() % 24000L;
      int targetAge = 0;
      if (worldTime > 3000L && worldTime < 5000L) {
         targetAge = 1;
      } else if (worldTime > 5000L && worldTime < 6000L) {
         targetAge = 2;
      } else if (worldTime > 6000L && worldTime < 10000L) {
         targetAge = 3;
      }

      int validCurrentAge = targetAge - 1;
      if (validCurrentAge < 0) {
         validCurrentAge = this.getMaxAge();
      }

      if (this.getAge(state) == validCurrentAge) {
         List<Point> pointsToTest = new ArrayList<>();
         pointsToTest.add(new Point(pos));

         for (int count = 0; !pointsToTest.isEmpty() && count < 10000; count++) {
            Point p = pointsToTest.get(pointsToTest.size() - 1);
            BlockState bs = p.getBlockActualState(level);
            if (bs.getBlock() == this && bs.getValue(AGE) == validCurrentAge) {
               p.setBlockState(level, bs.setValue(AGE, targetAge));
               for (int dx = -1; dx < 2; dx++) {
                  for (int dy = -1; dy < 2; dy++) {
                     for (int dz = -1; dz < 2; dz++) {
                        pointsToTest.add(p.getRelative(dx, dy, dz));
                     }
                  }
               }
            }
            pointsToTest.remove(p);
         }
      }
   }

   @Override
   public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
      return !this.isMaxAge(state);
   }

   @Override
   public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
      return true;
   }

   @Override
   public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
      level.setBlock(pos, this.withAge(this.getMaxAge()), 2);
   }
}
