package org.millenaire.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.world.WorldGenVillage;

/**
 * Spawns a Millénaire village (or lone building, when {@code spawnLoneBuilding} is true).
 * Syntax: {@code /millSpawnVillage <culture> <villageType> [x z] [completion%]}.
 */
public class CommandSpawnVillage {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean spawnLoneBuilding) {
      String name = spawnLoneBuilding ? "millSpawnLoneBuilding" : "millSpawnVillage";
      dispatcher.register(
         Commands.literal(name)
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(
               Commands.argument("culture", StringArgumentType.word())
                  .suggests(CommandSpawnVillage::suggestCultures)
                  .then(
                     Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> suggestTypes(c, b, spawnLoneBuilding))
                        // /cmd culture type  -> use player position, 0% completion
                        .executes(c -> execute(c, spawnLoneBuilding, false, false))
                        .then(
                           // /cmd culture type completion  -> player position, given completion
                           Commands.argument("completion", IntegerArgumentType.integer(0, 100))
                              .executes(c -> execute(c, spawnLoneBuilding, false, true))
                        )
                        .then(
                           Commands.argument("x", IntegerArgumentType.integer())
                              .then(
                                 Commands.argument("z", IntegerArgumentType.integer())
                                    // /cmd culture type x z
                                    .executes(c -> execute(c, spawnLoneBuilding, true, false))
                                    .then(
                                       // /cmd culture type x z completion
                                       Commands.argument("completion", IntegerArgumentType.integer(0, 100))
                                          .executes(c -> execute(c, spawnLoneBuilding, true, true))
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> c, boolean spawnLoneBuilding, boolean hasPos, boolean hasCompletion) {
      CommandSourceStack source = c.getSource();
      Level world = source.getLevel();
      ServerPlayer player = source.getPlayer();

      String cultureParam = StringArgumentType.getString(c, "culture");
      String villageTypeParam = StringArgumentType.getString(c, "type");
      Culture culture = Culture.getCultureByName(cultureParam);
      if (culture == null) {
         source.sendFailure(Component.literal(LanguageUtilities.string("command.spawnvillage_unknownculture", cultureParam)));
         return 0;
      }

      VillageType villageType = spawnLoneBuilding ? culture.getLoneBuildingType(villageTypeParam) : culture.getVillageType(villageTypeParam);
      if (villageType == null) {
         source.sendFailure(Component.literal(LanguageUtilities.string("command.spawnvillage_unknownvillage", villageTypeParam)));
         return 0;
      }

      int x = 0;
      int z = 0;
      float completion = 0.0F;
      if (hasPos) {
         x = IntegerArgumentType.getInteger(c, "x");
         z = IntegerArgumentType.getInteger(c, "z");
      } else if (player != null) {
         x = (int)player.getX();
         z = (int)player.getZ();
      }

      if (hasCompletion) {
         completion = IntegerArgumentType.getInteger(c, "completion") / 100.0F;
      }

      MillLog.major(null, "Attempting to spawn village of type " + cultureParam + ":" + villageTypeParam + " at " + x + "/" + z + ".");
      WorldGenVillage genVillage = new WorldGenVillage();
      boolean result = genVillage.generateVillageAtPoint(
         world, MillRandom.random, x, 0, z, player, false, true, false, 0, villageType, null, null, completion
      );
      MillLog.major(null, "Result of spawn attempt: " + result);
      return result ? 1 : 0;
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

   private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTypes(
      CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder, boolean spawnLoneBuilding
   ) {
      List<String> matches = new ArrayList<>();
      Culture culture = Culture.getCultureByName(StringArgumentType.getString(ctx, "culture"));
      if (culture != null) {
         List<VillageType> types = spawnLoneBuilding ? culture.listLoneBuildingTypes : culture.listVillageTypes;
         for (VillageType vtype : types) {
            matches.add(CommandUtilities.normalizeString(vtype.key));
         }
      }

      return SharedSuggestionProvider.suggest(matches, builder);
   }
}
