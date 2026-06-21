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
import org.millenaire.common.utilities.MillCommonUtilities;

/**
 * Canonical example of porting a 1.12 metadata-variant block to 26.2:
 * the old {@code int} metadata + {@code getMetaFromState}/{@code getStateFromMeta}/
 * {@code getSubBlocks}/{@code damageDropped} machinery is gone. Variants become a
 * proper {@link EnumProperty} declared in {@link #createBlockStateDefinition}; the
 * inner enum implements {@link StringRepresentable}. {@code updateTick} -> {@code randomTick}.
 */
public class BlockWetBrick extends Block {
   public static final EnumProperty<EnumType> PROGRESS = EnumProperty.create("progress", EnumType.class);

   public BlockWetBrick(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.GRAVEL)
         .strength(0.8F)
         .randomTicks());
      this.registerDefaultState(this.stateDefinition.any().setValue(PROGRESS, EnumType.WETBRICK0));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(PROGRESS);
   }

   @Override
   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      int currentValue = state.getValue(PROGRESS).getMetadata();
      if (level.getMaxLocalRawBrightness(pos.above()) > 14) {
         if (++currentValue < 2 && MillCommonUtilities.chanceOn(2)) {
            level.setBlockAndUpdate(pos, state.setValue(PROGRESS, EnumType.byMetadata(++currentValue)));
         } else if (currentValue < 3) {
            level.setBlockAndUpdate(pos, state.setValue(PROGRESS, EnumType.byMetadata(currentValue)));
         } else {
            level.setBlockAndUpdate(pos, MillBlocks.STONE_DECORATION.defaultBlockState()
               .setValue(BlockDecorativeStone.VARIANT, BlockDecorativeStone.EnumType.MUDBRICK));
         }
      }
   }

   public enum EnumType implements StringRepresentable {
      WETBRICK0(0, "wetbrick0"),
      WETBRICK1(1, "wetbrick1"),
      WETBRICK2(2, "wetbrick2");

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
