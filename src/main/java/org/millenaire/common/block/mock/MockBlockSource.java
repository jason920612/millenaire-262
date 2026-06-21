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

public class MockBlockSource extends Block {
   public static final EnumProperty<Resource> RESOURCE = EnumProperty.create("resource", Resource.class);

   public MockBlockSource(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.STONE)
         .strength(-1.0F, 3600000.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(RESOURCE, Resource.STONE));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(RESOURCE);
   }

   @Override
   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
         InteractionHand hand, BlockHitResult hitResult) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (stack.getItem() == MillItems.NEGATION_WAND) {
         int meta = state.getValue(RESOURCE).meta + 1;
         if (Resource.fromMeta(meta) == null) {
            meta = 0;
         }
         level.setBlock(pos, state.setValue(RESOURCE, Resource.fromMeta(meta)), 3);
         Mill.proxy.sendLocalChat(player, 'a', Resource.fromMeta(meta).name);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   public enum Resource implements StringRepresentable {
      STONE(0, "stone"),
      SAND(1, "sand"),
      SANDSTONE(2, "sandstone"),
      CLAY(3, "clay"),
      GRAVEL(4, "gravel"),
      GRANITE(5, "granite"),
      DORITE(6, "diorite"),
      ANDERITE(7, "andesite"),
      SNOW(8, "snow"),
      ICE(9, "ice"),
      RED_SANDSTONE(10, "redsandstone"),
      QUARTZ(11, "quartz");

      public final int meta;
      public final String name;

      Resource(int m, String n) {
         this.meta = m;
         this.name = n;
      }

      public static Resource fromMeta(int meta) {
         for (Resource t : values()) {
            if (t.meta == meta) {
               return t;
            }
         }
         return null;
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
         return "Source Block (" + this.name + ")";
      }
   }
}
