package org.millenaire.common.block;

import java.util.function.Function;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;

import org.millenaire.common.forge.MillRegistry;

/** Variant wood slab; see {@link BlockSlabStone} for the slab+variant porting notes. */
public class BlockSlabWood extends SlabBlock {
   public static final EnumProperty<BlockDecorativeWood.EnumType> VARIANT =
      EnumProperty.create("variant", BlockDecorativeWood.EnumType.class);

   public BlockSlabWood(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(2.0F, 5.0F)
         .mapColor((Function<BlockState, MapColor>) (state -> state.getValue(VARIANT).getMapColor())));
      this.registerDefaultState(this.defaultBlockState().setValue(VARIANT, BlockDecorativeWood.EnumType.TIMBERFRAMEPLAIN));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(VARIANT);
   }

   public boolean isDouble() {
      return false;
   }
}
