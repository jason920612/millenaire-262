package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.text.NumberFormat;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

public class CommandListActiveVillages {
   public static final String NAME = "millListActiveVillages";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal(NAME).executes(c -> execute(c.getSource())));
   }

   private static int execute(CommandSourceStack source) {
      Level world = source.getLevel();
      ServerPlayer senderPlayer = source.getPlayer();
      MillWorldData worldData = Mill.getMillWorld(world);

      for (int i = 0; i < worldData.villagesList.pos.size(); i++) {
         Building townHall = worldData.getBuilding(worldData.villagesList.pos.get(i));
         if (townHall != null && townHall.isActive) {
            long villagersTime = 0L;
            long buildingsTime = 0L;

            for (Long sample : worldData.villagesList.villagersTime.get(i)) {
               villagersTime += sample;
            }

            for (Long sample : worldData.villagesList.buildingsTime.get(i)) {
               buildingsTime += sample;
            }

            NumberFormat nf = NumberFormat.getInstance();
            String buildingsTimeStr = nf.format(buildingsTime / 1000L);
            String villagersTimeStr = nf.format(villagersTime / 1000L);
            List<Player> entities = WorldUtilities.getEntitiesWithinAABB(
               world, Player.class, townHall.getPos(), MillConfigValues.KeepActiveRadius, 1000
            );
            String players = "";

            for (Player player : entities) {
               if (players.length() > 0) {
                  players = players + ", ";
               }

               players = players + player.getName().getString();
            }

            if (senderPlayer == null) {
               MillLog.major(
                  null,
                  "Village "
                     + townHall.getVillageQualifiedName()
                     + " is active. It knows "
                     + townHall.getKnownVillagers().size()
                     + " villagers ("
                     + townHall.getAllVillagerRecords().size()
                     + " in the archives). Within the last 20 ticks, it took "
                     + buildingsTimeStr
                     + " ns to handle buildings and "
                     + villagersTimeStr
                     + " ns to handle villagers. Kept alive by: "
                     + players
               );
            } else {
               ServerSender.sendTranslatedSentence(
                  senderPlayer,
                  'f',
                  "command.listactivevillages_list",
                  townHall.getVillageQualifiedName(),
                  "" + townHall.getKnownVillagers().size(),
                  "" + townHall.getAllVillagerRecords().size(),
                  buildingsTimeStr,
                  villagersTimeStr,
                  players
               );
            }
         }
      }

      return 1;
   }
}
