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

public class MockBlockSoil extends Block {
   public static final EnumProperty<CropType> CROPTYPE = EnumProperty.create("croptype", CropType.class);

   public MockBlockSoil(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.STONE)
         .strength(-1.0F, 3600000.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(CROPTYPE, CropType.WHEAT));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(CROPTYPE);
   }

   @Override
   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
         InteractionHand hand, BlockHitResult hitResult) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (stack.getItem() == MillItems.NEGATION_WAND) {
         int meta = state.getValue(CROPTYPE).meta + 1;
         if (CropType.fromMeta(meta) == null) {
            meta = 0;
         }
         level.setBlock(pos, state.setValue(CROPTYPE, CropType.fromMeta(meta)), 3);
         Mill.proxy.sendLocalChat(player, 'a', CropType.fromMeta(meta).name);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   public enum CropType implements StringRepresentable {
      WHEAT(0, "soil"),
      RICE(1, "ricesoil"),
      TURMERIC(2, "turmericsoil"),
      SUGAR_CANE(3, "sugarcanesoil"),
      POTATO(4, "potatosoil"),
      NETHER_WART(5, "netherwartsoil"),
      GRAPE(6, "vinesoil"),
      MAIZE(7, "maizesoil"),
      CACAO(8, "cacaospot"),
      CARROT(9, "carrotsoil"),
      FLOWER(10, "flowersoil"),
      COTTON(11, "cottonsoil");

      public final int meta;
      public final String name;

      CropType(int m, String n) {
         this.meta = m;
         this.name = n;
      }

      public static CropType fromMeta(int meta) {
         for (CropType t : values()) {
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
