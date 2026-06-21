package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

public class CommandGiveReputation {
   public static final String NAME = "millGiveRep";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal(NAME)
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(
               Commands.argument("player", EntityArgument.player())
                  .then(
                     Commands.argument("village", StringArgumentType.string())
                        .suggests(CommandUtilities::suggestVillages)
                        .then(
                           Commands.argument("amount", IntegerArgumentType.integer())
                              .executes(CommandGiveReputation::execute)
                        )
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CommandSourceStack source = c.getSource();
      ServerPlayer entity = EntityArgument.getPlayer(c, "player");
      String villageParam = StringArgumentType.getString(c, "village");
      int repToGive = IntegerArgumentType.getInteger(c, "amount");

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
      village.adjustReputation(entity, repToGive);
      String senderName = source.getTextName();
      String targetName = entity.getName().getString();
      ServerSender.sendTranslatedSentence(
         entity, '9', "command.giverep_notification", senderName, targetName, "" + repToGive, village.getVillageQualifiedName()
      );
      ServerPlayer senderPlayer = source.getPlayer();
      if (senderPlayer != null) {
         ServerSender.sendTranslatedSentence(
            senderPlayer, '9', "command.giverep_notification", senderName, targetName, "" + repToGive, village.getVillageQualifiedName()
         );
      }

      return 1;
   }
}
