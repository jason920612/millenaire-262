package org.millenaire.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.millenaire.common.entity.TileEntityPanel;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

public class BlockPanel extends BaseEntityBlock {
   public static final MapCodec<BlockPanel> CODEC = simpleCodec(p -> {
      throw new UnsupportedOperationException("BlockPanel is registered programmatically");
   });
   public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
   protected static final VoxelShape SIGN_EAST_SHAPE = Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0);
   protected static final VoxelShape SIGN_WEST_SHAPE = Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0);
   protected static final VoxelShape SIGN_SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0);
   protected static final VoxelShape SIGN_NORTH_SHAPE = Block.box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0);

   public BlockPanel(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .strength(1.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
   }

   @Override
   protected MapCodec<BlockPanel> codec() {
      return CODEC;
   }

   @Override
   protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
      // TESR-only: TileEntityPanelRenderer draws the sign board + the building-info text/icons on the
      // panel face. 26.2's BaseEntityBlock defaults to RenderShape.MODEL, so without this the plank-cube
      // block model ALSO renders and hides the panel — it looked like a plain oak-plank block. 1.12 used
      // ENTITYBLOCK_ANIMATED (model not drawn); the 26.2 equivalent is INVISIBLE.
      return net.minecraft.world.level.block.RenderShape.INVISIBLE;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(FACING);
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TileEntityPanel(pos, state);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return switch (state.getValue(FACING)) {
         case SOUTH -> SIGN_SOUTH_SHAPE;
         case WEST -> SIGN_WEST_SHAPE;
         case EAST -> SIGN_EAST_SHAPE;
         default -> SIGN_NORTH_SHAPE;
      };
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return Shapes.empty();
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   @Override
   protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
      Direction facing = state.getValue(FACING);
      if (!level.getBlockState(pos.relative(facing.getOpposite())).isSolid()) {
         dropResources(state, level, pos);
         level.removeBlock(pos, false);
      }
      super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (org.millenaire.common.utilities.MillLog.debugOn()) {
         org.millenaire.common.utilities.MillLog.milldebug("Interaction", "player=" + player.getName().getString() + " right-clicked block=panel at " + pos + " (client=" + level.isClientSide() + ")");
      }

      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      TileEntityPanel panel = (TileEntityPanel) level.getBlockEntity(pos);
      if (panel != null && panel.panelType != 0) {
         Building building = Mill.getMillWorld(level).getBuilding(panel.buildingPos);
         if (building == null) {
            return InteractionResult.PASS;
         } else if (panel.panelType == 4 && building.controlledBy(player)) {
            ServerSender.displayControlledProjectGUI(player, building);
            return InteractionResult.SUCCESS;
         } else if (panel.panelType == 13 && building.controlledBy(player)) {
            ServerSender.displayControlledMilitaryGUI(player, building);
            return InteractionResult.SUCCESS;
         } else {
            ServerSender.displayPanel(player, new Point(pos));
            return InteractionResult.SUCCESS;
         }
      }
      return InteractionResult.PASS;
   }

   // 1.12 onBlockActivated fired regardless of held item; 26.2 splits item/empty-hand dispatch, so route the
   // with-item click to the same panel-open path so panels open even with an item held.
   @Override
   protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
      return this.useWithoutItem(state, level, pos, player, hitResult);
   }
}
