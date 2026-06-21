package org.millenaire.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;

import org.millenaire.common.block.BlockSod;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

/**
 * Inuit ulu — harvests snow/ice into bricks and lays sod planks. 1.12 used Forge's
 * {@code onItemUseFirst}; 26.2 routes through {@code useOn(UseOnContext)}.
 *
 * <p>Block renames: 1.12 {@code Blocks.SNOW} (full snow block) → {@code Blocks.SNOW_BLOCK};
 * 1.12 {@code Blocks.SNOW_LAYER} (layered snow) → {@code Blocks.SNOW}. The sod-planks crafting that
 * relied on {@code BlockPlanks.EnumType}/{@code BlockDirt.VARIANT} metadata is reimplemented against the
 * distinct plank blocks and {@link BlockSod#VARIANT}: it picks the first plank variant present in the
 * inventory, consumes one plank + one coarse dirt per batch of 3 uses, and lays SOD with the matching
 * wood variant.
 */
public class ItemUlu extends ItemMill {
   public ItemUlu(String itemName) {
      super(itemName);
   }

   private InteractionResult attemptSodPlanks(Player player, Level world, BlockPos pos, Direction side, InteractionHand hand) {
      if (world.getBlockState(pos).getBlock() == Blocks.SNOW) {
         side = Direction.DOWN;
      } else {
         pos = pos.relative(side);
      }

      if (world.getBlockState(pos).getBlock() != Blocks.AIR) {
         return InteractionResult.PASS;
      } else {
         ItemStack is = player.getItemInHand(hand);

         // 1.12 scanned the player inventory for any vanilla plank variant (BlockPlanks.EnumType) and
         // laid MillBlocks.SOD with the matching wood variant. In 26.2 each plank is a distinct block,
         // so iterate the SOD wood variants and pick the first whose matching vanilla plank is present.
         BlockSod.WoodType chosenWood = null;
         for (BlockSod.WoodType woodType : BlockSod.WoodType.values()) {
            if (MillCommonUtilities.countChestItems(player.getInventory(), plankBlockFor(woodType), 0) > 0) {
               chosenWood = woodType;
               break;
            }
         }

         if (chosenWood == null) {
            if (!world.isClientSide()) {
               ServerSender.sendTranslatedSentence(player, 'f', "ui.uluexplanations");
               ServerSender.sendTranslatedSentence(player, '6', "ui.ulunoplanks");
            }

            return InteractionResult.PASS;
         }

         CompoundTag tag = is.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
         int resUseCount = tag.getIntOr("resUseCount", 0);
         if (resUseCount == 0) {
            // 1.12 consumed one coarse dirt + one plank only when starting a fresh batch of 3 uses.
            if (MillCommonUtilities.countChestItems(player.getInventory(), Blocks.COARSE_DIRT, 0) == 0) {
               if (!world.isClientSide()) {
                  ServerSender.sendTranslatedSentence(player, '6', "ui.ulunodirt");
               }

               return InteractionResult.PASS;
            }

            WorldUtilities.getItemsFromChest(player.getInventory(), Blocks.COARSE_DIRT, 0, 1);
            WorldUtilities.getItemsFromChest(player.getInventory(), plankBlockFor(chosenWood), 0, 1);
            resUseCount = 3;
         } else {
            resUseCount--;
         }

         tag.putInt("resUseCount", resUseCount);
         is.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
         WorldUtilities.setBlockstate(world, new Point(pos),
            MillBlocks.SOD.defaultBlockState().setValue(BlockSod.VARIANT, chosenWood), true, true);
         is.hurtAndBreak(1, player, hand);
         return InteractionResult.SUCCESS;
      }
   }

   /** Maps a SOD wood variant to its matching vanilla plank block (replaces the removed BlockPlanks.EnumType). */
   private static Block plankBlockFor(BlockSod.WoodType woodType) {
      switch (woodType) {
         case SPRUCE:
            return Blocks.SPRUCE_PLANKS;
         case BIRCH:
            return Blocks.BIRCH_PLANKS;
         case JUNGLE:
            return Blocks.JUNGLE_PLANKS;
         case ACACIA:
            return Blocks.ACACIA_PLANKS;
         case DARK_OAK:
            return Blocks.DARK_OAK_PLANKS;
         case OAK:
         default:
            return Blocks.OAK_PLANKS;
      }
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      Level world = context.getLevel();
      BlockPos pos = context.getClickedPos();
      Direction side = context.getClickedFace();
      InteractionHand hand = context.getHand();
      if (player == null) {
         return InteractionResult.PASS;
      }

      ItemStack uluIS = player.getItemInHand(hand);
      if (world.getBlockState(pos).getBlock() == Blocks.SNOW_BLOCK) {
         world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
         MillCommonUtilities.putItemsInChest(player.getInventory(), MillBlocks.SNOW_BRICK, 0, 4);
         uluIS.hurtAndBreak(1, player, hand);
         return InteractionResult.SUCCESS;
      } else if (world.getBlockState(pos).getBlock() == Blocks.SNOW) {
         int snowDepth = world.getBlockState(pos).getValue(SnowLayerBlock.LAYERS);
         world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
         MillCommonUtilities.putItemsInChest(player.getInventory(), MillBlocks.SNOW_BRICK, 0, (snowDepth + 1) / 2);
         uluIS.hurtAndBreak(1, player, hand);
         return InteractionResult.SUCCESS;
      } else if (world.getBlockState(pos).getBlock() == Blocks.ICE) {
         MillCommonUtilities.putItemsInChest(player.getInventory(), MillBlocks.ICE_BRICK, 0, 4);
         world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
         uluIS.hurtAndBreak(1, player, hand);
         return InteractionResult.SUCCESS;
      } else {
         return this.attemptSodPlanks(player, world, pos, side, hand);
      }
   }
}
