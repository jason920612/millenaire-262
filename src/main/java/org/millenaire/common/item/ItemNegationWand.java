package org.millenaire.common.item;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.buildingplan.BuildingImportExport;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

public class ItemNegationWand extends ItemMill {
   public ItemNegationWand(String itemName) {
      super(itemName);
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      Level world = context.getLevel();
      BlockPos bp = context.getClickedPos();
      Point pos = new Point(bp);
      BlockState bs = world.getBlockState(bp);
      if (bs.getBlock() == MillBlocks.IMPORT_TABLE) {
         return InteractionResult.PASS;
      } else if (world.isClientSide()) {
         if (bs.getBlock() == Blocks.OAK_SIGN && world.isClientSide()) {
            BuildingImportExport.negationWandExportBuilding(player, world, pos);
            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.FAIL;
         }
      } else {
         MillWorldData mw = Mill.getMillWorld(world);

         for (int i = 0; i < 2; i++) {
            MillCommonUtilities.VillageList list;
            if (i == 0) {
               list = mw.loneBuildingsList;
            } else {
               list = mw.villagesList;
            }

            for (int j = 0; j < list.names.size(); j++) {
               Point p = list.pos.get(j);
               int distance = Mth.floor(p.horizontalDistanceTo(pos));
               if (distance <= 30) {
                  Building th = mw.getBuilding(p);
                  if (th != null && th.isTownhall) {
                     if (th.chestLocked && !MillConfigValues.DEV) {
                        ServerSender.sendTranslatedSentence(player, '6', "negationwand.villagelocked", th.villageType.name);
                        return InteractionResult.SUCCESS;
                     }

                     ServerSender.displayNegationWandGUI(player, th);
                  }
               }
            }
         }

         return InteractionResult.FAIL;
      }
   }
}
