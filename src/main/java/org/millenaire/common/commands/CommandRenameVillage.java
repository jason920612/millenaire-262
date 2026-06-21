package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

public class CommandRenameVillage {
   public static final String NAME = "millRenameVillage";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal(NAME)
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(
               Commands.argument("village", StringArgumentType.string())
                  .suggests(CommandUtilities::suggestVillages)
                  .then(
                     Commands.argument("name", StringArgumentType.string())
                        .executes(c -> execute(c, ""))
                        .then(
                           Commands.argument("qualifier", StringArgumentType.string())
                              .executes(c -> execute(c, StringArgumentType.getString(c, "qualifier")))
                        )
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> c, String qualifier) {
      CommandSourceStack source = c.getSource();
      String villageParam = StringArgumentType.getString(c, "village");
      String newName = StringArgumentType.getString(c, "name");
      MillWorldData worldData = Mill.getMillWorld(source.getLevel());
      List<Building> townHalls = CommandUtilities.getMatchingVillages(worldData, villageParam);
      if (townHalls.size() == 0) {
         source.sendFailure(Component.literal(LanguageUtilities.string("command.tp_nomatchingvillage")));
         return 0;
      }

      if (townHalls.size() > 1) {
         source.sendFailure(Component.literal(LanguageUtilities.string("command.tp_multiplematchingvillages", "" + townHalls.size())));
         return 0;
      }

      Building village = townHalls.get(0);
      String oldName = village.getVillageQualifiedName();
      village.changeVillageName(newName);
      village.changeVillageQualifier(qualifier);

      for (ServerPlayer player : source.getLevel().players()) {
         ServerSender.sendTranslatedSentence(
            player, '9', "command.renamevillage_notification", source.getTextName(), oldName, village.getVillageQualifiedName()
         );
      }

      return 1;
   }
}
