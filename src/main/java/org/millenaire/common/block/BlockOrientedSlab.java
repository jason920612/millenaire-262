package org.millenaire.common.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import org.millenaire.common.forge.MillRegistry;

/**
 * Oriented slab (byzantine tiles): vanilla {@link SlabBlock} provides bottom/top/double
 * via TYPE; the 1.12 metadata AXIS becomes an {@link EnumProperty} of {@link Direction.Axis}.
 */
public abstract class BlockOrientedSlab extends SlabBlock {
   // The 1.12 block carried a single-value "variant" enum (DEFAULT only); 26.2 forbids a &lt;=1-value
   // EnumProperty, and it conveyed nothing, so only AXIS remains.
   public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.create("axis", Direction.Axis.class);

   public BlockOrientedSlab(String slabName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(slabName))
         .sound(SoundType.STONE)
         .strength(1.5F, 10.0F));
      this.registerDefaultState(this.defaultBlockState().setValue(AXIS, Direction.Axis.X));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(AXIS);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      BlockState base = super.getStateForPlacement(context);
      if (base == null) {
         return null;
      }
      Direction.Axis axis = context.getClickedFace().getAxis();
      if (axis == Direction.Axis.Y) {
         axis = Direction.Axis.X;
      }
      return base.setValue(AXIS, axis);
   }

   public abstract boolean isDouble();

   public static class BlockOrientedSlabDouble extends BlockOrientedSlab {
      public BlockOrientedSlabDouble(String slabName) {
         super(slabName);
      }

      @Override
      public boolean isDouble() {
         return true;
      }
   }

   public static class BlockOrientedSlabSlab extends BlockOrientedSlab {
      public BlockOrientedSlabSlab(String slabName) {
         super(slabName);
      }

      @Override
      public boolean isDouble() {
         return false;
      }
   }

   public enum Variant implements StringRepresentable {
      DEFAULT;

      public String getName() {
         return "default";
      }

      @Override
      public String getSerializedName() {
         return "default";
      }
   }
}
