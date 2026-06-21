package org.millenaire.common.block.mock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.item.MillItems;

public class MockBlockMarker extends Block {
   protected static final VoxelShape CARPET_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
   public static final EnumProperty<Type> VARIANT = EnumProperty.create("variant", Type.class);

   public MockBlockMarker(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.STONE)
         .strength(-1.0F, 3600000.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(VARIANT, Type.PRESERVE_GROUND));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(VARIANT);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return state.getValue(VARIANT) == Type.PRESERVE_GROUND ? Shapes.block() : CARPET_SHAPE;
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return state.getValue(VARIANT) == Type.PRESERVE_GROUND ? Shapes.block() : Shapes.empty();
   }

   @Override
   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
         InteractionHand hand, BlockHitResult hitResult) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (stack.getItem() == MillItems.NEGATION_WAND) {
         int meta = state.getValue(VARIANT).meta + 1;
         if (Type.fromMeta(meta) == null) {
            meta = 0;
         }
         level.setBlock(pos, state.setValue(VARIANT, Type.fromMeta(meta)), 3);
         Mill.proxy.sendLocalChat(player, 'a', Type.fromMeta(meta).name);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
      int color = 16777215;
      switch (state.getValue(VARIANT)) {
         case PRESERVE_GROUND:
            return;
         case SLEEPING_POS:
            color = 14680244;
            break;
         case SELLING_POS:
            color = 65484;
            break;
         case CRAFTING_POS:
            color = 1158400;
            break;
         case DEFENDING_POS:
            color = 16711680;
            break;
         case SHELTER_POS:
            color = 8323127;
            break;
         case PATH_START_POS:
            color = 721110;
            break;
         case LEISURE_POS:
            color = 15763456;
            break;
         case STALL:
            color = 9868800;
            break;
         case BRICK_SPOT:
            color = 8847360;
            break;
         case HEALING_SPOT:
            color = 53760;
            break;
         case FISHING_SPOT:
            color = 120;
            break;
         default:
            break;
      }

      level.addParticle(
         ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, ARGB.color(255, color)),
         pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
   }

   public enum Type implements StringRepresentable {
      PRESERVE_GROUND(0, "preserveground"),
      SLEEPING_POS(1, "sleepingpos"),
      SELLING_POS(2, "sellingpos"),
      CRAFTING_POS(3, "craftingpos"),
      DEFENDING_POS(4, "defendingpos"),
      SHELTER_POS(5, "shelterpos"),
      PATH_START_POS(6, "pathstartpos"),
      LEISURE_POS(7, "leisurepos"),
      STALL(8, "stall"),
      BRICK_SPOT(9, "brickspot"),
      HEALING_SPOT(10, "healingspot"),
      FISHING_SPOT(11, "fishingspot");

      public final int meta;
      public final String name;

      Type(int m, String n) {
         this.meta = m;
         this.name = n;
      }

      public static Type fromMeta(int meta) {
         for (Type t : values()) {
            if (t.meta == meta) {
               return t;
            }
         }
         return null;
      }

      public static int getMetaFromName(String name) {
         for (Type type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
               return type.meta;
            }
         }
         return -1;
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
         return "Marker Pos (" + this.name + ")";
      }
   }
}
