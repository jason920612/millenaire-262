package org.millenaire.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.VillageInventory;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

/**
 * Brick mould — packs dirt + sand into a wet brick on right-click. 1.12 used Forge's
 * {@code onItemUseFirst}; 26.2 has no such hook, so the logic moves to {@code useOn(UseOnContext)}.
 * {@code getItemDamage}/{@code damageItem}→{@code getDamageValue}/{@code hurtAndBreak}.
 */
public class ItemBrickMould extends ItemMill {
   public ItemBrickMould(String itemName) {
      super(itemName);
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

      if (world.getBlockState(pos).getBlock() == Blocks.SNOW) {
         side = Direction.DOWN;
      } else {
         pos = pos.relative(side);
      }

      // 26.2: the 1.12 world.mayPlace(WET_BRICK, ...) check is gone; the air check below plus the wet-brick
      // block's own canSurvive (run by WorldUtilities.setBlockstate) cover the same placement guard.
      if (world.getBlockState(pos).getBlock() != Blocks.AIR) {
         return InteractionResult.PASS;
      } else {
         ItemStack is = player.getItemInHand(hand);
         if (is.getDamageValue() % 4 == 0) {
            if (VillageInventory.countChestItems(player.getInventory(), Blocks.DIRT, 0) == 0
               || VillageInventory.countChestItems(player.getInventory(), Blocks.SAND, 0) == 0) {
               if (!world.isClientSide()) {
                  ServerSender.sendTranslatedSentence(player, 'f', "ui.brickinstructions");
               }

               return InteractionResult.PASS;
            }

            VillageInventory.getItemsFromChest(player.getInventory(), Blocks.DIRT, 0, 1);
            VillageInventory.getItemsFromChest(player.getInventory(), Blocks.SAND, 0, 1);
         }

         WorldUtilities.setBlockstate(world, new Point(pos), MillBlocks.BS_WET_BRICK, true, false);
         is.hurtAndBreak(1, player, hand);
         return InteractionResult.SUCCESS;
      }
   }
}
