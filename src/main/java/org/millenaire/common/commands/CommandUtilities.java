package org.millenaire.common.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

public class CommandUtilities {
   /**
    * Brigadier suggestion provider for matching village (town-hall qualified) names.
    */
   public static CompletableFuture<Suggestions> suggestVillages(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
      MillWorldData worldData = Mill.getMillWorld(ctx.getSource().getLevel());
      List<String> matches = new ArrayList<>();
      if (worldData != null) {
         for (Point thPos : worldData.villagesList.pos) {
            Building townHall = worldData.getBuilding(thPos);
            if (townHall != null) {
               matches.add(normalizeString(townHall.getVillageQualifiedName()));
            }
         }
      }

      return SharedSuggestionProvider.suggest(matches, builder);
   }

   public static List<Building> getMatchingVillages(MillWorldData worldData, String param) {
      param = normalizeString(param);
      List<Building> villages = new ArrayList<>();

      for (Point thPos : worldData.villagesList.pos) {
         Building townHall = worldData.getBuilding(thPos);
         if (townHall != null && normalizeString(townHall.getVillageQualifiedName()).startsWith(param)) {
            villages.add(townHall);
         }
      }

      return villages;
   }

   public static String normalizeString(String string) {
      string = string.replaceAll(" ", "_").toLowerCase();
      string = Normalizer.normalize(string, Form.NFD);
      return string.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
   }
}
