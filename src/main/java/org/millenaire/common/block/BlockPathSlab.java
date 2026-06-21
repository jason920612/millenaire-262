package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.millenaire.common.forge.MillRegistry;

/** Single (half-height) path slab. Top/bottom via vanilla {@link Half}; STABLE as on {@link BlockPath}. */
public class BlockPathSlab extends Block implements IBlockPath {
   public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
   protected static final VoxelShape BOTTOM_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);
   protected static final VoxelShape TOP_SHAPE = Block.box(0.0, 8.0, 0.0, 16.0, 15.0, 16.0);
   private final String singleSlabBlockName;
   private final String doubleSlabName;

   public BlockPathSlab(String blockName, MapColor color, SoundType soundType) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName + "_slab"))
         .mapColor(color)
         .sound(soundType)
         .strength(0.8F)
         .noOcclusion());
      this.singleSlabBlockName = blockName + "_slab";
      this.doubleSlabName = blockName;
      this.registerDefaultState(this.stateDefinition.any().setValue(HALF, Half.BOTTOM).setValue(STABLE, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(HALF, STABLE);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return state.getValue(HALF) == Half.TOP ? TOP_SHAPE : BOTTOM_SHAPE;
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      Direction face = context.getClickedFace();
      double y = context.getClickLocation().y - context.getClickedPos().getY();
      boolean top = face == Direction.DOWN || (face != Direction.UP && y > 0.5);
      return this.defaultBlockState().setValue(HALF, top ? Half.TOP : Half.BOTTOM).setValue(STABLE, true);
   }

   @Override
   public BlockPath getDoubleSlab() {
      return (BlockPath) BuiltInRegistries.BLOCK.getValue(MillRegistry.id(this.doubleSlabName));
   }

   @Override
   public BlockPathSlab getSingleSlab() {
      return (BlockPathSlab) BuiltInRegistries.BLOCK.getValue(MillRegistry.id(this.singleSlabBlockName));
   }

   @Override
   public boolean isFullPath() {
      return false;
   }
}
