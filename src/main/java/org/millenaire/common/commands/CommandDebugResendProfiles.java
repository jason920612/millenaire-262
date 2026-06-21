package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;

public class CommandDebugResendProfiles {
   public static final String NAME = "millDebugSendProfiles";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal(NAME)
            .then(
               Commands.argument("target", StringArgumentType.word())
                  .suggests((c, b) -> {
                     List<String> options = new ArrayList<>();
                     options.add("all");
                     for (String name : c.getSource().getServer().getPlayerNames()) {
                        options.add(name);
                     }
                     return SharedSuggestionProvider.suggest(options, b);
                  })
                  .executes(c -> execute(c, StringArgumentType.getString(c, "target")))
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> c, String target) {
      CommandSourceStack source = c.getSource();
      Level world = source.getLevel();
      ServerPlayer senderPlayer = source.getPlayer();

      if (target.equals("all")) {
         MillWorldData worldData = Mill.getMillWorld(world);

         for (UserProfile profile : worldData.profiles.values()) {
            if (profile.connected) {
               profile.sendInitialPackets();
               if (senderPlayer != null) {
                  ServerSender.sendTranslatedSentence(senderPlayer, '2', "Resent profile data for " + profile.playerName);
               } else {
                  MillLog.major(profile, "Resent profile data.");
               }
            }
         }
      } else {
         Player player = source.getServer().getPlayerList().getPlayerByName(target);
         if (player == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
               "This command requires a player name or 'all' as first parameter."));
            return 0;
         }

         MillWorldData worldData = Mill.getMillWorld(world);
         UserProfile profilex = worldData.getProfile(player);
         profilex.sendInitialPackets();
         if (senderPlayer != null) {
            ServerSender.sendTranslatedSentence(senderPlayer, '2', "Resent profile data for " + profilex.playerName);
         } else {
            MillLog.major(profilex, "Resent profile data.");
         }
      }

      return 1;
   }
}
