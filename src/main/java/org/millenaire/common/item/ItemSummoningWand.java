package org.millenaire.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.ui.GuiActions;
import org.millenaire.common.utilities.Point;

/**
 * Summoning wand — opens the village-summoning GUI on right-click. 1.12 used Forge's
 * {@code onItemUseFirst}; 26.2 has no such hook, so the logic moves to {@code useOn(UseOnContext)}
 * returning {@link InteractionResult}. {@code world.provider.getDimension()==0} (overworld) becomes
 * {@code world.dimension() == Level.OVERWORLD}.
 */
public class ItemSummoningWand extends ItemMill {
   public ItemSummoningWand(String itemName) {
      super(itemName);
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      Level world = context.getLevel();
      BlockPos bp = context.getClickedPos();
      Block targetBlock = world.getBlockState(bp).getBlock();
      if (targetBlock == MillBlocks.IMPORT_TABLE) {
         return InteractionResult.PASS;
      } else if (world.isClientSide()) {
         return InteractionResult.PASS;
      } else if (world.dimension() != Level.OVERWORLD) {
         return InteractionResult.PASS;
      } else {
         Point pos = new Point(bp);
         return GuiActions.useSummoningWand((ServerPlayer)player, pos);
      }
   }
}
