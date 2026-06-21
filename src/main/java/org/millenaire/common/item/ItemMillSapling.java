package org.millenaire.common.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.world.UserProfile;

/**
 * Sapling BlockItem that gates planting on the player having learned the crop. 1.12 overrode
 * {@code onItemUse(EntityPlayer, World, BlockPos, EnumHand, EnumFacing, ...)}; on 26.2 placement goes
 * through {@code useOn(UseOnContext)} returning {@link InteractionResult}.
 */
public class ItemMillSapling extends BlockItem {
   public final String cropKey;

   public ItemMillSapling(Block block, String cropKey) {
      super(block, new Item.Properties().setId(MillRegistry.itemKeyFor(block)));
      this.cropKey = cropKey;
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      Level world = context.getLevel();
      if (player == null) {
         return InteractionResult.FAIL;
      }

      UserProfile profile = Mill.getMillWorld(world).getProfile(player);
      if (!profile.isTagSet("cropplanting_" + this.cropKey) && !MillConfigValues.DEV) {
         if (!world.isClientSide()) {
            ServerSender.sendTranslatedSentence(player, 'f', "ui.cropplantingknowledge");
         }

         return InteractionResult.FAIL;
      } else {
         return super.useOn(context);
      }
   }
}
