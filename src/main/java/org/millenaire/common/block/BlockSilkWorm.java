package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import org.millenaire.common.forge.MillRegistry;

/**
 * Silkworm-on-mulberry block: 1.12 metadata "progress" variant becomes an {@link EnumProperty}.
 * {@code updateTick} -> {@code randomTick}; the old item-metadata machinery
 * (getMetaFromState/getSubBlocks/getRenderLayer/initModel/IMetaBlockName) is removed.
 */
public class BlockSilkWorm extends Block {
   public static final EnumProperty<EnumType> PROGRESS = EnumProperty.create("progress", EnumType.class);

   public BlockSilkWorm(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(2.0F, 5.0F)
         .randomTicks()
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(PROGRESS, EnumType.SILKWORMEMPTY));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(PROGRESS);
   }

   @Override
   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      int currentValue = state.getValue(PROGRESS).getMetadata();
      if (currentValue < 3 && level.getMaxLocalRawBrightness(pos.above()) < 7 && random.nextInt(2) == 0) {
         level.setBlockAndUpdate(pos, state.setValue(PROGRESS, EnumType.byMetadata(++currentValue)));
      }
   }

   public enum EnumType implements StringRepresentable {
      SILKWORMEMPTY(0, "silkwormempty"),
      SILKWORMIP1(1, "silkwormip1"),
      SILKWORMIP2(2, "silkwormip2"),
      SILKWORMFULL(3, "silkwormfull");

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
