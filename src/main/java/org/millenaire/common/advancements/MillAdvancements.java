package org.millenaire.common.advancements;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;

public class MillAdvancements {
   public static final String NORMAN = "norman";
   public static final String INDIAN = "indian";
   public static final String MAYAN = "mayan";
   public static final String JAPANESE = "japanese";
   public static final String BYZANTINES = "byzantines";
   public static final String INUITS = "inuits";
   public static final String SELJUK = "seljuk";
   public static String[] ADVANCEMENT_CULTURES = new String[]{"norman", "indian", "mayan", "japanese", "byzantines", "inuits", "seljuk"};
   public static final int MEDIEVAL_METROPOLIS_VILLAGER_NUMBER = 100;
   public static final GenericAdvancement FIRST_CONTACT = new GenericAdvancement("firstcontact");
   public static final GenericAdvancement CRESUS = new GenericAdvancement("cresus");
   public static final GenericAdvancement SUMMONING_WAND = new GenericAdvancement("summoningwand");
   public static final GenericAdvancement AMATEUR_ARCHITECT = new GenericAdvancement("amateurarchitect");
   public static final GenericAdvancement MEDIEVAL_METROPOLIS = new GenericAdvancement("medievalmetropolis");
   public static final GenericAdvancement THE_QUEST = new GenericAdvancement("thequest");
   public static final GenericAdvancement MAITRE_A_PENSER = new GenericAdvancement("maitreapenser");
   public static final GenericAdvancement EXPLORER = new GenericAdvancement("explorer");
   public static final GenericAdvancement MARCO_POLO = new GenericAdvancement("marcopolo");
   public static final GenericAdvancement MAGELLAN = new GenericAdvancement("magellan");
   public static final GenericAdvancement SELF_DEFENSE = new GenericAdvancement("selfdefense");
   public static final GenericAdvancement PANTHEON = new GenericAdvancement("pantheon");
   public static final GenericAdvancement DARK_SIDE = new GenericAdvancement("darkside");
   public static final GenericAdvancement SCIPIO = new GenericAdvancement("scipio");
   public static final GenericAdvancement ATTILA = new GenericAdvancement("attila");
   public static final GenericAdvancement VIKING = new GenericAdvancement("viking");
   public static final GenericAdvancement CHEERS = new GenericAdvancement("cheers");
   public static final GenericAdvancement HIRED = new GenericAdvancement("hired");
   public static final GenericAdvancement MASTER_FARMER = new GenericAdvancement("masterfarmer");
   public static final GenericAdvancement GREAT_HUNTER = new GenericAdvancement("greathunter");
   public static final GenericAdvancement A_FRIEND_INDEED = new GenericAdvancement("friendindeed");
   public static final GenericAdvancement RAINBOW = new GenericAdvancement("rainbow");
   public static final GenericAdvancement ISTANBUL = new GenericAdvancement("seljuk_istanbul");
   public static final GenericAdvancement NOTTODAY = new GenericAdvancement("byzantines_nottoday");
   public static final GenericAdvancement MP_WEAPON = new GenericAdvancement("mp_weapon");
   public static final GenericAdvancement MP_HIREDGOON = new GenericAdvancement("mp_hiredgoon");
   public static final GenericAdvancement MP_FRIENDLYVILLAGE = new GenericAdvancement("mp_friendlyvillage");
   public static final GenericAdvancement MP_NEIGHBOURTRADE = new GenericAdvancement("mp_neighbourtrade");
   public static final GenericAdvancement MP_RAIDONPLAYER = new GenericAdvancement("mp_raidonplayer");
   public static final Map<String, GenericAdvancement> REP_ADVANCEMENTS = new HashMap<>();
   public static final Map<String, GenericAdvancement> COMPLETE_ADVANCEMENTS = new HashMap<>();
   public static final Map<String, GenericAdvancement> VILLAGE_LEADER_ADVANCEMENTS = new HashMap<>();
   public static final GenericAdvancement WQ_INDIAN = new GenericAdvancement("wq_indian");
   public static final GenericAdvancement WQ_NORMAN = new GenericAdvancement("wq_norman");
   public static final GenericAdvancement WQ_MAYAN = new GenericAdvancement("wq_mayan");
   public static final GenericAdvancement PUJA = new GenericAdvancement("puja");
   public static final GenericAdvancement SACRIFICE = new GenericAdvancement("sacrifice");
   public static final GenericAdvancement MARVEL_NORMAN = new GenericAdvancement("marvel_norman");
   public static final List<GenericAdvancement> MILL_ADVANCEMENTS = new ArrayList<>();

   public static void addToStats(Player player, String key) {
      if (player == null) {
         // Advancement granted in a no-player context (e.g. world-gen / a system grant with no specific
         // player to attribute). There is no creative/survival player to record the Mill stat against, so
         // skip it — NOT an error to crash on (this was the byzantines village-gen NPE: player.isCreative()
         // on a null player). The ServerPlayer-specific datapack award in grant() is already null-guarded.
         return;
      }
      if (!player.isCreative() && !MillConfigValues.DEV) {
         MillConfigValues.advancementsSurvival.add(key);
      } else {
         MillConfigValues.advancementsCreative.add(key);
      }

      MillConfigValues.writeConfigFile();
   }

   public static long computeKey() {
      long key = 346186835L;

      for (String advancement : MillConfigValues.advancementsSurvival) {
         key += customStringHash(advancement + "survival");
      }

      for (String advancement : MillConfigValues.advancementsCreative) {
         key += customStringHash(advancement + "creative");
      }

      return key + customStringHash("" + MillConfigValues.randomUid) * 250;
   }

   private static int customStringHash(String string) {
      int hash = 0;
      hash = string.length();
      hash += string.indexOf("e");
      return hash + string.indexOf("a") * 2;
   }

   public static void registerTriggers() {
      // 26.2: CriteriaTriggers.register(ICriterionTrigger) was removed — custom code-granted triggers
      // no longer need a registered trigger type. Millénaire's triggers were all "always true once the
      // code path runs", so the datapack advancements (data/millenaire/advancement/*.json) declare the
      // built-in `minecraft:impossible` trigger for their single criterion, and GenericAdvancement.grant(...)
      // awards that criterion by name (PlayerAdvancements.award) — making the advancement appear and toast
      // exactly as in 1.12. No custom trigger registration is therefore required here.
      MillLog.minor(null, "Mill advancements are datapack-defined (impossible trigger, code-granted via GenericAdvancement.grant).");
   }

   static {
      for (Field field : MillAdvancements.class.getDeclaredFields()) {
         if (field.getType() == GenericAdvancement.class) {
            try {
               MILL_ADVANCEMENTS.add((GenericAdvancement)field.get(null));
            } catch (Exception advancementReflectException) {
               // FAIL-FAST: reflecting a static GenericAdvancement field cannot fail unless the class is
               // mis-defined; a swallow silently drops an advancement. Crash on the code/registry bug.
               throw MillCrash.fail("Advancements", "failed to enumerate advancement field " + field.getName() + ": " + advancementReflectException);
            }
         }
      }

      for (String culture : ADVANCEMENT_CULTURES) {
         REP_ADVANCEMENTS.put(culture, new GenericAdvancement("rep_" + culture));
         COMPLETE_ADVANCEMENTS.put(culture, new GenericAdvancement("complete_" + culture));
         VILLAGE_LEADER_ADVANCEMENTS.put(culture, new GenericAdvancement("leader_" + culture));
      }

      MILL_ADVANCEMENTS.addAll(REP_ADVANCEMENTS.values());
      MILL_ADVANCEMENTS.addAll(COMPLETE_ADVANCEMENTS.values());
      MILL_ADVANCEMENTS.addAll(VILLAGE_LEADER_ADVANCEMENTS.values());
   }
}
