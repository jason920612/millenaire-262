package org.millenaire.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.utilities.BlockItemUtilities;

public class BlockMillCrops extends CropBlock {
   public static final MapCodec<BlockMillCrops> CODEC = simpleCodec(p -> {
      throw new UnsupportedOperationException("BlockMillCrops is registered programmatically");
   });
   private final boolean requireIrrigation;
   private final boolean slowGrowth;
   private final Identifier seed;

   public BlockMillCrops(String cropName, boolean requireIrrigation, boolean slowGrowth, Identifier seed) {
      this(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(cropName))
         .sound(SoundType.GRASS)
         .strength(0.0F)
         .randomTicks()
         .noOcclusion()
         .noCollision(), requireIrrigation, slowGrowth, seed);
   }

   protected BlockMillCrops(BlockBehaviour.Properties properties, boolean requireIrrigation, boolean slowGrowth, Identifier seed) {
      super(properties);
      this.requireIrrigation = requireIrrigation;
      this.slowGrowth = slowGrowth;
      this.seed = seed;
   }

   /** Mill-specific growth chance: 0 when irrigation is required but missing. */
   protected float getMillGrowthChance(Level level, BlockPos pos) {
      // 1.12 read the legacy farmland metadata below the crop, which was the farmland
      // MOISTURE value (0 = dry). In 26.2 metadata is gone, so read the MOISTURE
      // blockstate property directly. Non-farmland (or dry farmland) reads as 0.
      BlockState soil = level.getBlockState(pos.below());
      int irrigation = soil.getBlock() instanceof FarmlandBlock ? soil.getValue(FarmlandBlock.MOISTURE) : 0;
      if (this.requireIrrigation && irrigation == 0) {
         return 0.0F;
      }
      return this.slowGrowth ? 4.0F : 8.0F;
   }

   @Override
   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (level.getMaxLocalRawBrightness(pos.above()) >= 9) {
         int age = this.getAge(state);
         if (age < this.getMaxAge()) {
            float growthChance = this.getMillGrowthChance(level, pos);
            if (growthChance > 0.0F && random.nextInt((int) (25.0F / growthChance)) == 0) {
               level.setBlock(pos, this.getStateForAge(age + 1), 2);
            }
         }
      }
   }

   protected ItemLike getSeed() {
      return BuiltInRegistries.ITEM.getValue(this.seed);
   }

   @Override
   protected ItemLike getBaseSeedId() {
      return this.getSeed();
   }

   @Override
   protected ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
      return new ItemStack(this.getSeed());
   }

   @Override
   public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack tool) {
      super.playerDestroy(level, player, pos, state, blockEntity, tool);
      BlockItemUtilities.checkForHarvestTheft(player, pos);
   }
}
