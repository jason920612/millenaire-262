package org.millenaire.common.block.mock;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.item.MillItems;

public class MockBlockDecor extends Block {
   public static final EnumProperty<Type> TYPE = EnumProperty.create("type", Type.class);

   public MockBlockDecor(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.STONE)
         .strength(-1.0F, 3600000.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, Type.TAPESTRY));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(TYPE);
   }

   @Override
   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
         InteractionHand hand, BlockHitResult hitResult) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (stack.getItem() == MillItems.NEGATION_WAND) {
         int meta = state.getValue(TYPE).meta + 1;
         if (Type.fromMeta(meta) == null) {
            meta = 0;
         }
         level.setBlock(pos, state.setValue(TYPE, Type.fromMeta(meta)), 3);
         Mill.proxy.sendLocalChat(player, 'a', Type.fromMeta(meta).name);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   public enum Type implements StringRepresentable {
      TAPESTRY(0, "tapestry"),
      INDIAN_STATUE(1, "indianstatue"),
      BYZ_ICON_SMALL(2, "byzantineiconsmall"),
      BYZ_ICON_MEDIUM(3, "byzantineiconmedium"),
      BYZ_ICON_LARGE(4, "byzantineiconlarge"),
      MAYAN_STATUE(5, "mayanstatue"),
      HIDE_HANGING(6, "hidehanging"),
      WALL_CARPET_SMALL(7, "wallcarpetsmall"),
      WALL_CARPET_MEDIUM(8, "wallcarpetmedium"),
      WALL_CARPET_LARGE(9, "wallcarpetlarge");

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
         return "Decor Block (" + this.name + ")";
      }
   }
}
