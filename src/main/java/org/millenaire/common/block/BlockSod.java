package org.millenaire.common.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import org.millenaire.common.forge.MillRegistry;

/**
 * Sod block. 1.12 used the vanilla {@code BlockPlanks.EnumType} for its wood variants;
 * that enum no longer exists (planks are separate blocks now), so we carry a local
 * {@link WoodType} enum matching the original variant names.
 */
public class BlockSod extends Block {
   public static final EnumProperty<WoodType> VARIANT = EnumProperty.create("variant", WoodType.class);

   public BlockSod(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(2.0F, 5.0F));
      this.registerDefaultState(this.stateDefinition.any().setValue(VARIANT, WoodType.OAK));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(VARIANT);
   }

   public enum WoodType implements StringRepresentable {
      OAK(0, "oak"),
      SPRUCE(1, "spruce"),
      BIRCH(2, "birch"),
      JUNGLE(3, "jungle"),
      ACACIA(4, "acacia"),
      DARK_OAK(5, "dark_oak");

      private static final WoodType[] META_LOOKUP = new WoodType[values().length];
      private final int meta;
      private final String name;

      WoodType(int meta, String name) {
         this.meta = meta;
         this.name = name;
      }

      public static WoodType byMetadata(int meta) {
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
         for (WoodType t : values()) {
            META_LOOKUP[t.meta] = t;
         }
      }
   }
}
