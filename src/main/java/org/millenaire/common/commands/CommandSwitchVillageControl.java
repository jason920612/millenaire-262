package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

public class CommandSwitchVillageControl {
   public static final String NAME = "millSwitchVillageControl";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal(NAME)
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(
               Commands.argument("village", StringArgumentType.string())
                  .suggests(CommandSwitchVillageControl::suggestControlledVillages)
                  .then(
                     Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(), b))
                        .executes(CommandSwitchVillageControl::execute)
                  )
            )
      );
   }

   private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestControlledVillages(
      CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder
   ) {
      MillWorldData worldData = Mill.getMillWorld(ctx.getSource().getLevel());
      List<String> matches = new ArrayList<>();
      if (worldData != null) {
         for (var thPos : worldData.villagesList.pos) {
            Building th = worldData.getBuilding(thPos);
            if (th != null && th.villageType.playerControlled) {
               matches.add(CommandUtilities.normalizeString(th.getVillageQualifiedName()));
            }
         }
      }

      return SharedSuggestionProvider.suggest(matches, builder);
   }

   private static int execute(CommandContext<CommandSourceStack> c) {
      CommandSourceStack source = c.getSource();
      String villageParam = StringArgumentType.getString(c, "village");
      String playerName = StringArgumentType.getString(c, "player");
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
      if (!village.villageType.playerControlled) {
         source.sendFailure(Component.literal(LanguageUtilities.string("command.switchcontrol_notcontrolled", village.getVillageQualifiedName())));
         return 0;
      }

      Optional<NameAndId> profile = source.getServer().services().nameToIdCache().get(playerName);
      if (profile.isEmpty()) {
         source.sendFailure(Component.literal(LanguageUtilities.string("command.switchcontrol_playernotfound", playerName)));
         return 0;
      }

      NameAndId resolved = profile.get();
      String oldControllerName = village.controlledByName;
      village.controlledBy = resolved.id();
      village.controlledByName = resolved.name();
      MillLog.major(
         null, "Switched controller from " + oldControllerName + " to " + village.controlledByName + " via command by " + source.getTextName() + "."
      );

      for (ServerPlayer player : source.getLevel().players()) {
         ServerSender.sendTranslatedSentence(
            player, '9', "command.switchcontrol_notification", source.getTextName(), oldControllerName, resolved.name(), village.getVillageQualifiedName()
         );
      }

      return 1;
   }
}
