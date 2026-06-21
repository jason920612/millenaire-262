package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

public class CommandTeleportToVillage {
   public static final String NAME = "millTp";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal(NAME)
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(
               Commands.argument("village", StringArgumentType.string())
                  .suggests(CommandUtilities::suggestVillages)
                  .executes(c -> execute(c, null))
                  .then(
                     Commands.argument("player", EntityArgument.player())
                        .executes(c -> execute(c, EntityArgument.getPlayer(c, "player")))
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> c, ServerPlayer target) throws CommandSyntaxException {
      CommandSourceStack source = c.getSource();
      String villageParam = StringArgumentType.getString(c, "village");
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
      Entity entity = target;
      if (entity == null) {
         entity = source.getPlayer();
      }

      if (entity instanceof ServerPlayer serverPlayer) {
         serverPlayer.stopRiding();
         serverPlayer.connection
            .teleport(
               village.location.getSellingPos().x,
               village.location.getSellingPos().y,
               village.location.getSellingPos().z,
               0.0F,
               0.0F
            );
      }

      return 1;
   }
}
