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

public class MockBlockAnimalSpawn extends Block {
   public static final EnumProperty<Creature> CREATURE = EnumProperty.create("creature", Creature.class);

   public MockBlockAnimalSpawn(String blockName) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(blockName))
         .sound(SoundType.STONE)
         .strength(-1.0F, 3600000.0F)
         .noOcclusion());
      this.registerDefaultState(this.stateDefinition.any().setValue(CREATURE, Creature.COW));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(CREATURE);
   }

   @Override
   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
         InteractionHand hand, BlockHitResult hitResult) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (stack.getItem() == MillItems.NEGATION_WAND) {
         int meta = state.getValue(CREATURE).meta + 1;
         if (Creature.fromMeta(meta) == null) {
            meta = 0;
         }
         level.setBlock(pos, state.setValue(CREATURE, Creature.fromMeta(meta)), 3);
         Mill.proxy.sendLocalChat(player, 'a', Creature.fromMeta(meta).name);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   public enum Creature implements StringRepresentable {
      COW(0, "cow"),
      PIG(1, "pig"),
      SHEEP(2, "sheep"),
      CHICKEN(3, "chicken"),
      SQUID(4, "squid"),
      WOLF(5, "wolf"),
      POLARBEAR(6, "polarbear");

      public final int meta;
      public final String name;

      Creature(int m, String n) {
         this.meta = m;
         this.name = n;
      }

      public static Creature fromMeta(int meta) {
         for (Creature t : values()) {
            if (t.meta == meta) {
               return t;
            }
         }
         return null;
      }

      public static int getMetaFromName(String name) {
         for (Creature creature : values()) {
            if (creature.name.equalsIgnoreCase(name)) {
               return creature.meta;
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
         return "Animal Spawn (" + this.name + ")";
      }
   }
}
