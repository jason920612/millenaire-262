package org.millenaire.common.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;

import org.millenaire.common.forge.MillRegistry;

/**
 * Coloured "stained glass" pane with variants. Connection state + rotation/mirror are
 * handled by vanilla {@link IronBarsBlock}; the 1.12 beacon-colour hooks
 * ({@code BeaconBlock.updateColorAsync} in breakBlock/onBlockAdded) are dropped — modern
 * beacons recompute their beam automatically. TRANSLUCENT render layer is set client-side.
 */
public class BlockMillStainedGlass extends IronBarsBlock {
   public static final EnumProperty<EnumType> VARIANT = EnumProperty.create("variant", EnumType.class);

   public BlockMillStainedGlass(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.GLASS)
         .mapColor(MapColor.COLOR_GRAY)
         .strength(0.3F)
         .noOcclusion());
      this.registerDefaultState(this.defaultBlockState().setValue(VARIANT, EnumType.WHITE));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(VARIANT);
   }

   public enum EnumType implements StringRepresentable {
      WHITE(0, "white"),
      YELLOW(1, "yellow"),
      YELLOW_RED(2, "yellow_red"),
      RED_BLUE(3, "red_blue"),
      GREEN_BLUE(4, "green_blue");

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
