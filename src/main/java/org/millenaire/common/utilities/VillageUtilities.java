package org.millenaire.common.utilities;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;

public class VillageUtilities {
   public static String getRelationName(int relation) {
      if (relation >= 90) {
         return "relation.excellent";
      } else if (relation >= 70) {
         return "relation.verygood";
      } else if (relation >= 50) {
         return "relation.good";
      } else if (relation >= 30) {
         return "relation.decent";
      } else if (relation >= 10) {
         return "relation.fair";
      } else if (relation <= -90) {
         return "relation.openconflict";
      } else if (relation <= -70) {
         return "relation.atrocious";
      } else if (relation <= -50) {
         return "relation.verybad";
      } else if (relation <= -30) {
         return "relation.bad";
      } else {
         return relation <= -10 ? "relation.chilly" : "relation.neutral";
      }
   }

   public static List<ServerPlayer> getServerPlayers(Level world) {
      List<ServerPlayer> players = new ArrayList<>();

      for (Player p : world.players()) {
         players.add((ServerPlayer)p);
      }

      return players;
   }

   public static UserProfile getServerProfile(Level world, Player player) {
      MillWorldData mw = Mill.getMillWorld(world);
      return mw == null ? null : mw.getProfile(player);
   }

   public static String getVillagerSentence(MillVillager v, String playerName, boolean nativeSpeech) {
      if (v.speech_key == null) {
         return null;
      } else if (!nativeSpeech && !v.getCulture().canReadDialogues(playerName)) {
         return null;
      } else {
         List<String> variants = v.getCulture().getSentences(v.speech_key);
         if (variants != null && variants.size() > v.speech_variant) {
            String s = variants.get(v.speech_variant).replaceAll("\\$name", playerName);
            if (v.getGoalDestEntity() != null && v.getGoalDestEntity() instanceof MillVillager) {
               s = s.replaceAll("\\$targetfirstname", v.dialogueTargetFirstName);
               s = s.replaceAll("\\$targetlastname", v.dialogueTargetLastName);
            } else {
               s = s.replaceAll("\\$targetfirstname", "");
               s = s.replaceAll("\\$targetlastname", "");
            }

            if (!nativeSpeech) {
               if (s.split("/").length > 1) {
                  s = s.split("/")[1].trim();
                  if (s.length() == 0) {
                     s = null;
                  }

                  return s;
               } else {
                  return null;
               }
            } else {
               if (s.split("/").length > 1) {
                  s = s.split("/")[0].trim();
               }

               if (s.length() == 0) {
                  s = null;
               }

               return s;
            }
         } else {
            return v.speech_key;
         }
      }
   }
}
