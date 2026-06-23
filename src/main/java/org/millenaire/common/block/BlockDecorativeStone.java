package org.millenaire.common.block;

import java.util.function.Function;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;

import org.millenaire.common.forge.MillRegistry;

/**
 * Decorative full block carrying its 1.12 metadata variants as a {@link EnumProperty}.
 * Per-variant map colour is supplied through the {@code mapColor(Function)} property.
 * The old item-metadata machinery (getMetaFromState/getSubBlocks/IMetaBlockName) is gone;
 * each variant is exposed as its own creative-tab entry by the item layer — the BlockItem
 * ({@code ItemBlockMeta}) implements {@code IVariantCreativeItem} and emits one stack per variant
 * (all except COOKEDBRICK, matching 1.12 {@code getSubBlocks}), the variant carried on the
 * {@code minecraft:block_state} data component so placement reproduces it.
 */
public class BlockDecorativeStone extends Block {
   public static final EnumProperty<EnumType> VARIANT = EnumProperty.create("variant", EnumType.class);

   public BlockDecorativeStone(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.STONE)
         .strength(1.5F, 10.0F)
         .mapColor((Function<BlockState, MapColor>) (state -> state.getValue(VARIANT).getMapColor())));
      this.registerDefaultState(this.stateDefinition.any().setValue(VARIANT, EnumType.MUDBRICK));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(VARIANT);
   }

   public enum EnumType implements StringRepresentable {
      MUDBRICK(0, "mudbrick", MapColor.COLOR_BROWN, true),
      COOKEDBRICK(1, "cookedbrick", MapColor.TERRACOTTA_WHITE, true),
      MAYANGOLDBLOCK(2, "mayangoldblock", MapColor.GOLD, false),
      BYZANTINEMOSAICRED(3, "byzantine_mosaic_red", MapColor.COLOR_RED, false),
      BYZANTINEMOSAICBLUE(4, "byzantine_mosaic_blue", MapColor.COLOR_BLUE, false),
      LIGHTBLUEBRICK_BLOCK(5, "lightbluebrick_block", MapColor.COLOR_BLUE, false),
      LIGHTBLUECHISELED_BLOCK(6, "lightbluechiseled_block", MapColor.COLOR_BLUE, false);

      private static final EnumType[] META_LOOKUP = new EnumType[values().length];
      private final int meta;
      private final String name;
      private final MapColor mapColor;
      private final boolean hasSlab;

      EnumType(int meta, String name, MapColor mapColor, boolean hasSlab) {
         this.meta = meta;
         this.name = name;
         this.mapColor = mapColor;
         this.hasSlab = hasSlab;
      }

      public static EnumType byMetadata(int meta) {
         if (meta < 0 || meta >= META_LOOKUP.length) {
            meta = 0;
         }
         return META_LOOKUP[meta];
      }

      public MapColor getMapColor() {
         return this.mapColor;
      }

      public int getMetadata() {
         return this.meta;
      }

      public String getName() {
         return this.name;
      }

      public boolean hasSlab() {
         return this.hasSlab;
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
