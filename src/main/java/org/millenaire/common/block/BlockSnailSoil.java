package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import org.millenaire.common.forge.MillRegistry;

public class BlockSnailSoil extends Block {
   public static final EnumProperty<EnumType> PROGRESS = EnumProperty.create("progress", EnumType.class);

   public BlockSnailSoil(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.GRAVEL)
         .strength(0.5F)
         .randomTicks());
      this.registerDefaultState(this.stateDefinition.any().setValue(PROGRESS, EnumType.SNAIL_SOIL_EMPTY));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(PROGRESS);
   }

   @Override
   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      int currentValue = state.getValue(PROGRESS).getMetadata();
      if (currentValue < 3) {
         boolean waterAbove = level.getBlockState(pos.above()).getBlock() == Blocks.WATER;
         BlockPos twoAbove = pos.above().above();
         boolean airAboveWater = !level.getBlockState(twoAbove).isSuffocating(level, twoAbove);
         if (waterAbove && airAboveWater && random.nextInt(2) == 0) {
            level.setBlockAndUpdate(pos, state.setValue(PROGRESS, EnumType.byMetadata(++currentValue)));
         }
      }
   }

   public enum EnumType implements StringRepresentable {
      SNAIL_SOIL_EMPTY(0, "snail_soil_empty"),
      SNAIL_SOIL_IP1(1, "snail_soil_ip1"),
      SNAIL_SOIL_IP2(2, "snail_soil_ip2"),
      SNAIL_SOIL_FULL(3, "snail_soil_full");

      private static final EnumType[] META_LOOKUP = new EnumType[values().length];
      private final int meta;
      private final String name;

      EnumType(int meta, String name) {
         this.meta = meta;
         this.name = name;
      }

      public static EnumType byMetadata(int meta) {
         if (meta < 0 || meta >= META_LOOKUP.length) {
            meta = 0;
         }
         return META_LOOKUP[meta];
      }

      public int getMetadata() {
         return this.meta;
      }

      public String getName() {
         return this.name;
      }

      @Override
      public String getSerializedName() {
         return this.name;
      }

      @Override
      public String toString() {
         return this.name;
      }

      static {
         for (EnumType t : values()) {
            META_LOOKUP[t.meta] = t;
         }
      }
   }
}
