package org.millenaire.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.entity.MillBlockEntities;
import org.millenaire.common.entity.TileEntityLockedChest;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.utilities.Point;

public class BlockLockedChest extends BaseEntityBlock {
   public static final MapCodec<BlockLockedChest> CODEC = simpleCodec(p -> {
      throw new UnsupportedOperationException("BlockLockedChest is registered programmatically");
   });
   public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
   protected static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

   public BlockLockedChest(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.WOOD)
         .strength(50.0F, 2000.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
   }

   @Override
   protected MapCodec<BlockLockedChest> codec() {
      return CODEC;
   }

   @Override
   protected net.minecraft.world.level.block.RenderShape getRenderShape(net.minecraft.world.level.block.state.BlockState state) {
      // TESR-only: TileEntityLockedChestRenderer draws the chest. 26.2 BaseEntityBlock defaults to MODEL,
      // so the block's own model (a plank/missing-texture cube) rendered over the chest. INVISIBLE = only
      // the renderer draws.
      return net.minecraft.world.level.block.RenderShape.INVISIBLE;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(FACING);
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TileEntityLockedChest(pos, state);
   }

   @Override
   public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      // Client-only ticker interpolates the lid-open animation (matches ChestBlock.getTicker).
      return level.isClientSide() ? createTickerHelper(type, MillBlockEntities.LOCKED_CHEST, TileEntityLockedChest::lidAnimateTick) : null;
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (org.millenaire.common.utilities.MillLog.debugOn()) {
         org.millenaire.common.utilities.MillLog.milldebug("Interaction", "player=" + player.getName().getString() + " right-clicked block=locked_chest at " + pos + " (client=" + level.isClientSide() + ") action=openLockedChestGUI");
      }

      if (level.isClientSide()) {
         ClientSender.activateMillChest(player, new Point(pos));
      }
      return InteractionResult.SUCCESS;
   }

   // 1.12 onBlockActivated fired on every right-click regardless of held item; 26.2 splits item/empty-hand
   // dispatch, so route the with-item click to the same GUI-open path so the chest opens even with an item held.
   @Override
   protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
      return this.useWithoutItem(state, level, pos, player, hitResult);
   }
}
