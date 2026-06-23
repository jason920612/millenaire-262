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

/**
 * Variant stone slab. Top/bottom/double are handled natively by {@link SlabBlock}'s
 * {@code TYPE} property; the 1.12 metadata variant becomes a {@link #VARIANT} property
 * layered on top. Item-side per-variant selection is handled by the BlockItem
 * ({@code ItemSlabMeta}), which implements {@code IVariantCreativeItem} and emits one creative-tab
 * stack per slab-capable variant (MUDBRICK + COOKEDBRICK, matching 1.12 {@code getSubBlocks}'s
 * {@code hasSlab()} filter), the variant carried on the {@code minecraft:block_state} data component.
 */
public class BlockSlabStone extends SlabBlock {
   public static final EnumProperty<BlockDecorativeStone.EnumType> VARIANT =
      EnumProperty.create("variant", BlockDecorativeStone.EnumType.class);

   public BlockSlabStone(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.STONE)
         .strength(2.0F, 10.0F)
         .mapColor((Function<BlockState, MapColor>) (state -> state.getValue(VARIANT).getMapColor())));
      this.registerDefaultState(this.defaultBlockState().setValue(VARIANT, BlockDecorativeStone.EnumType.MUDBRICK));
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
