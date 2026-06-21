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

public class MockBlockTreeSpawn extends Block {
   public static final EnumProperty<TreeType> TREETYPE = EnumProperty.create("treetype", TreeType.class);

   public MockBlockTreeSpawn(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.GRASS)
         .strength(-1.0F, 3600000.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(TREETYPE, TreeType.OAK));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(TREETYPE);
   }

   @Override
   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
         InteractionHand hand, BlockHitResult hitResult) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (stack.getItem() == MillItems.NEGATION_WAND) {
         int meta = state.getValue(TREETYPE).meta + 1;
         if (TreeType.fromMeta(meta) == null) {
            meta = 0;
         }
         level.setBlock(pos, state.setValue(TREETYPE, TreeType.fromMeta(meta)), 3);
         Mill.proxy.sendLocalChat(player, 'a', TreeType.fromMeta(meta).name);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   public enum TreeType implements StringRepresentable {
      OAK(0, "oak"),
      SPRUCE(1, "pine"),
      BIRCH(2, "birch"),
      JUNGLE(3, "jungle"),
      ACACIA(4, "acacia"),
      DARK_OAK(5, "darkoak"),
      APPLE_TREE(6, "appletree"),
      PISTACHIO(7, "pistachiotree"),
      OLIVE_TREE(8, "olivetree"),
      CHERRY_TREE(9, "cherrytree"),
      SAKURA_TREE(10, "sakuratree");

      public final int meta;
      public final String name;

      TreeType(int m, String n) {
         this.meta = m;
         this.name = n;
      }

      public static TreeType fromMeta(int meta) {
         for (TreeType t : values()) {
            if (t.meta == meta) {
               return t;
            }
         }
         return null;
      }

      public static int getMetaFromName(String name) {
         for (TreeType type : values()) {
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
         return "Tree Spawn (" + this.name + ")";
      }
   }
}
