package org.millenaire.common.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraft.network.FriendlyByteBuf;

import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.utilities.Point;

public class BlockFirePit extends BaseEntityBlock {
   public static final MapCodec<BlockFirePit> CODEC = simpleCodec(p -> {
      throw new UnsupportedOperationException("BlockFirePit is registered programmatically");
   });
   public static final BooleanProperty LIT = BooleanProperty.create("lit");
   public static final EnumProperty<EnumAlignment> ALIGNMENT = EnumProperty.create("alignment", EnumAlignment.class);
   public static final VoxelShape FIRE_PIT_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0);

   public BlockFirePit(String name) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(name))
         .sound(SoundType.WOOD)
         .strength(0.2F)
         .lightLevel(state -> state.getValue(LIT) ? 15 : 0)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false).setValue(ALIGNMENT, EnumAlignment.Z));
   }

   @Override
   protected MapCodec<BlockFirePit> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(LIT, ALIGNMENT);
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TileEntityFirePit(pos, state);
   }

   @Override
   public <T extends BlockEntity> @org.jspecify.annotations.Nullable BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type
   ) {
      // 1.12 TileEntityFirePit implemented ITickable.update(); 26.2 routes ticking through a
      // BlockEntityTicker handed out here. Without this override TileEntityFirePit.serverTick was never
      // called, so the fire pit never consumed fuel or smelted anything. Server-only (matches furnaces).
      return level instanceof ServerLevel serverLevel
         ? createTickerHelper(
            type,
            org.millenaire.common.entity.MillBlockEntities.FIRE_PIT,
            (innerLevel, pos, blockState, firePit) -> TileEntityFirePit.serverTick(serverLevel, pos, blockState, firePit)
         )
         : null;
   }

   @Override
   protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
      BlockEntity te = level.getBlockEntity(pos);
      if (te instanceof TileEntityFirePit firePit) {
         firePit.dropAll();
      }
      super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return FIRE_PIT_SHAPE;
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return Shapes.empty();
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return this.defaultBlockState().setValue(ALIGNMENT, EnumAlignment.fromAxis(context.getHorizontalDirection().getAxis()));
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (org.millenaire.common.utilities.MillLog.debugOn()) {
         org.millenaire.common.utilities.MillLog.milldebug("Interaction", "player=" + player.getName().getString() + " right-clicked block=fire_pit at " + pos + " (client=" + level.isClientSide() + ") action=openFirePitGUI(104/16)");
      }

      if (!level.isClientSide()) {
         // 1.12: playerIn.openGui(Mill.instance, 16, world, x, y, z). The Forge IGuiHandler flow is gone;
         // Mill's own PACKET_OPENGUI(104) flow drives the client (ClientReceiver.readGUIPacket has a
         // guiId==16 branch that reads the Point and opens ContainerFirePit/GuiFirePit). Send the open-GUI
         // packet with the fire-pit position, matching the ServerSender.displayPanel pattern (104, 16, Point).
         FriendlyByteBuf data = ServerSender.getPacketBuffer();
         data.writeInt(104);
         data.writeInt(16);
         StreamReadWrite.writeNullablePoint(new Point(pos), data);
         ServerSender.sendPacketToPlayer(data, player);
      }
      return InteractionResult.SUCCESS;
   }

   // 1.12 onBlockActivated fired regardless of held item; 26.2 splits item/empty-hand dispatch, so route the
   // with-item click to the same GUI-open path so the fire pit opens even with an item held.
   @Override
   protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
      return this.useWithoutItem(state, level, pos, player, hitResult);
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
      if (state.getValue(LIT)) {
         level.playLocalSound(
            pos.getX() + 0.5, pos.getY() + FIRE_PIT_SHAPE.min(Direction.Axis.Y), pos.getZ() + 0.5,
            SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
            1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
         if (random.nextInt(24) == 0) {
            for (int i = 0; i < 3; i++) {
               double x = pos.getX() + random.nextDouble();
               double y = pos.getY() + random.nextDouble() * 0.5 + 0.5;
               double z = pos.getZ() + random.nextDouble();
               level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0, 0.0, 0.0);
            }
         }
      }
   }

   public enum EnumAlignment implements StringRepresentable {
      X("x", 0, 90.0),
      Z("z", 1, 0.0);

      private final String name;
      private final int meta;
      public final double angle;

      EnumAlignment(String name, int meta, double angle) {
         this.name = name;
         this.meta = meta;
         this.angle = angle;
      }

      public static EnumAlignment fromAxis(Direction.Axis axis) {
         if (axis == Direction.Axis.X) {
            return Z;
         } else if (axis == Direction.Axis.Z) {
            return X;
         } else {
            throw new UnsupportedOperationException("Y isn't horizontal!");
         }
      }

      public static EnumAlignment fromMeta(int flag) {
         return flag != 0 ? X : Z;
      }

      public int getMeta() {
         return this.meta;
      }

      public String getName() {
         return this.name;
      }

      @Override
      public String getSerializedName() {
         return this.name;
      }
   }
}
