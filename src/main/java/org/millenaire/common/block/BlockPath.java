package org.millenaire.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.millenaire.common.forge.MillRegistry;

/** Full (double-height) decorative path block. STABLE controls the "trodden" look. */
public class BlockPath extends Block implements IBlockPath {
   protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);
   private final String singleSlabBlockName;
   private final String doubleSlabName;

   public BlockPath(String blockName, MapColor color, SoundType soundType) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .mapColor(color)
         .sound(soundType)
         .strength(0.8F)
         .noOcclusion());
      this.singleSlabBlockName = blockName + "_slab";
      this.doubleSlabName = blockName;
      this.registerDefaultState(this.stateDefinition.any().setValue(STABLE, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(STABLE);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return this.defaultBlockState().setValue(STABLE, true);
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
      return true;
   }
}
