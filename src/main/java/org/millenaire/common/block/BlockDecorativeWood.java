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

public class BlockDecorativeWood extends Block {
   public static final EnumProperty<EnumType> VARIANT = EnumProperty.create("variant", EnumType.class);

   public BlockDecorativeWood(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(2.0F, 5.0F)
         .mapColor((Function<BlockState, MapColor>) (state -> state.getValue(VARIANT).getMapColor())));
      this.registerDefaultState(this.stateDefinition.any().setValue(VARIANT, EnumType.TIMBERFRAMEPLAIN));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(VARIANT);
   }

   public enum EnumType implements StringRepresentable {
      TIMBERFRAMEPLAIN(0, "timberframeplain", MapColor.COLOR_BROWN, true),
      TIMBERFRAMECROSS(1, "timberframecross", MapColor.COLOR_BROWN, false),
      THATCH(2, "thatch", MapColor.COLOR_YELLOW, true),
      BEEHIVE(3, "beehive", MapColor.COLOR_YELLOW, false);

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

      public String getUnlocalizedName() {
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
