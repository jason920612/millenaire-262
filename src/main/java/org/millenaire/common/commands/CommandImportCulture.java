package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.common.buildingplan.BuildingImportExport;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

public class CommandImportCulture {
   public static final String NAME = "millImportCulture";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal(NAME)
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(
               Commands.argument("culture", StringArgumentType.word())
                  .suggests(CommandImportCulture::suggestCultures)
                  .executes(c -> execute(c, false))
                  .then(
                     Commands.argument("x", IntegerArgumentType.integer())
                        .then(
                           Commands.argument("z", IntegerArgumentType.integer())
                              .executes(c -> execute(c, true))
                        )
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> c, boolean hasPos) throws CommandSyntaxException {
      CommandSourceStack source = c.getSource();
      Level world = source.getLevel();
      ServerPlayer player = source.getPlayer();

      String cultureParam = StringArgumentType.getString(c, "culture");
      Culture culture = Culture.getCultureByName(cultureParam);
      if (culture == null) {
         source.sendFailure(Component.literal(LanguageUtilities.string("command.spawnvillage_unknownculture", cultureParam)));
         return 0;
      }

      int x;
      int z;
      if (hasPos) {
         x = IntegerArgumentType.getInteger(c, "x");
         z = IntegerArgumentType.getInteger(c, "z");
      } else {
         player = source.getPlayerOrException();
         x = (int)player.getX();
         z = (int)player.getZ();
      }

      int y = WorldUtilities.findTopSoilBlock(world, x, z);
      Point startPoint = new Point(x, y, z);
      Point adjustedStartPoint = new Point(x, y, z);
      List<BuildingPlanSet> planSets = new ArrayList<>(culture.ListPlanSets);

      for (BuildingPlanSet planSet : planSets.stream().sorted((p1, p2) -> p1.mainFile.compareTo(p2.mainFile)).collect(Collectors.toList())) {
         if (!planSet.getFirstStartingPlan().isSubBuilding) {
            ServerSender.sendTranslatedSentence(player, 'f', "command.importculture_importingbuilding", planSet.getNameTranslated());
            int xDelta = BuildingImportExport.importTableHandleImportRequest(player, adjustedStartPoint, culture.key, planSet.key, true, 0, 0, 0, true);
            adjustedStartPoint = new Point(adjustedStartPoint.x + xDelta + 5.0, startPoint.y, startPoint.z);
         }
      }

      return 1;
   }

   private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCultures(
      CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder
   ) {
      List<String> matches = new ArrayList<>();
      for (Culture cu : Culture.ListCultures) {
         matches.add(CommandUtilities.normalizeString(cu.key));
      }

      return SharedSuggestionProvider.suggest(matches, builder);
   }
}
