package com.coderyo.jason.talk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.world.entity.player.Player;

import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.quest.Quest;
import org.millenaire.common.quest.QuestInstance;
import org.millenaire.common.quest.QuestInstanceVillager;
import org.millenaire.common.quest.QuestStep;
import org.millenaire.common.quest.QuestVillager;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.VillagerRecord;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;

/**
 * Phase 7 (#7) — DISCUSSION → a REAL {@link QuestInstance}.
 *
 * <p>The villager {@linkplain VillageDiscussion discussion} settles on a pressing topic (defend / gather / build).
 * This class turns that topic into an ACTUAL data-defined Millénaire quest seeded on the player's profile — so the
 * player sees / accepts / tracks it through the normal {@code GuiQuest} + {@code QuestInstance.completeStep} path,
 * not a throwaway templated string.
 *
 * <p>The seeding pattern is the proven one the headless observer used ({@code MillSimObserver.findOrSeedQuest}),
 * lifted here so the REAL interaction path uses it too: pick a single-villager data quest whose starting villager
 * type is actually present in this village's records, build the {@link QuestInstanceVillager} from that record, and
 * register a {@link QuestInstance} on the profile + {@code villagersInQuests} + send the client packet. The choice
 * is BIASED by the discussion topic (a GATHER discussion prefers a quest whose first step asks for a
 * {@code requiredgood}; a BUILD/DEFEND discussion takes any present single-villager quest) so the seeded quest
 * matches what the village discussed where the shipped quest data allows.
 *
 * <p>Strict no-fabrication: we only ever seed a quest whose starting villager genuinely exists here (so the
 * required-good / reward flow is real). If no shipped quest maps to a present villager we seed NOTHING and report
 * it (the templated discussion line is still shown by the dialogue path) — we never invent a fake quest.
 */
public final class DiscussionQuests {

   public static final String TAG = VillageDiscussion.TAG;

   private DiscussionQuests() {
   }

   /**
    * Seed a real {@link QuestInstance} for {@code profile} from the village's discussion {@code result}, preferring
    * a shipped quest whose topic matches and whose starting villager is present in {@code townHall}'s records. When
    * {@code clicked} is a live villager of the village it is preferred as the quest's starting villager (so the
    * player took the quest from the very villager they spoke to). Returns the seeded (or already-existing) instance,
    * or {@code null} if no shipped quest maps to a present villager (no fabrication).
    */
   public static QuestInstance seedFor(MillWorldData mw, Building townHall, UserProfile profile,
                                       VillageDiscussion.DiscussionResult result, MillVillager clicked) {
      if (mw == null || townHall == null || profile == null) {
         return null;
      }
      try {
         // (1) Reuse any quest the player already has whose current villager belongs to this village.
         for (QuestInstance qi : new ArrayList<>(profile.questInstances)) {
            try {
               if (qi.getCurrentVillager() != null && townHall.getPos().equals(qi.getCurrentVillager().townHall)) {
                  return qi;
               }
            } catch (Throwable ignored) {
            }
         }

         VillageDiscussion.Topic topic = result != null ? result.topic : VillageDiscussion.Topic.IDLE;

         // (2) Seed a single-villager data quest whose starting villager type is present in this village, biased by
         //     the discussion topic. Two passes: first only quests that MATCH the topic, then any present quest.
         QuestInstance qi = trySeed(mw, townHall, profile, clicked, topic, true);
         if (qi != null) {
            return qi;
         }
         return trySeed(mw, townHall, profile, clicked, topic, false);
      } catch (Throwable t) {
         MillLog.printException(TAG + " DiscussionQuests.seedFor failed", t);
         return null;
      }
   }

   private static QuestInstance trySeed(MillWorldData mw, Building townHall, UserProfile profile,
                                        MillVillager clicked, VillageDiscussion.Topic topic, boolean topicMatch) {
      for (Quest q : Quest.quests.values()) {
         try {
            if (q.steps.isEmpty() || q.villagersOrdered.isEmpty() || q.villagersOrdered.size() != 1) {
               continue; // single-villager quests only — multi-villager quests reference other villages.
            }
            if (topicMatch && !topicMatches(q, topic)) {
               continue;
            }
            QuestVillager startVillager = q.villagersOrdered.get(0);
            String startKey = q.steps.get(0).villager;
            if (startKey == null) {
               continue;
            }
            // Prefer the clicked villager's record if it satisfies the quest's starting villager test.
            VillagerRecord match = pickRecord(townHall, profile, startVillager, clicked);
            if (match == null) {
               continue;
            }
            HashMap<String, QuestInstanceVillager> villagers = new HashMap<>();
            villagers.put(startKey, new QuestInstanceVillager(mw, townHall.getPos(), match.getVillagerId(), match));
            QuestInstance qi = new QuestInstance(mw, q, profile, villagers, clockTime(townHall));
            profile.questInstances.add(qi);
            for (QuestInstanceVillager qiv : villagers.values()) {
               profile.villagersInQuests.put(qiv.id, qi);
            }
            try {
               profile.sendQuestInstancePacket(qi);
            } catch (Throwable t) {
               // Headless / unresolvable client — the quest is still seeded server-side; don't fail the seed.
               MillLog.printException(TAG + " sendQuestInstancePacket skipped (headless)", t);
            }
            MillLog.major(null, TAG + " QUEST seeded from discussion topic=" + topic + " quest=" + q.key
               + " startVillager='" + match.getName() + "' (id=" + match.getVillagerId() + ")"
               + " topicMatched=" + topicMatch + " village='" + safeName(townHall) + "'");
            return qi;
         } catch (Throwable ignored) {
         }
      }
      return null;
   }

   /**
    * A villager record in this village whose type satisfies the quest's starting-villager test. The {@code clicked}
    * villager's own record is preferred (the player took the quest from the very villager they spoke to).
    */
   private static VillagerRecord pickRecord(Building townHall, UserProfile profile,
                                            QuestVillager startVillager, MillVillager clicked) {
      try {
         if (clicked != null && clicked.getRecord() != null && startVillager.testVillager(profile, clicked.getRecord())) {
            return clicked.getRecord();
         }
      } catch (Throwable ignored) {
      }
      for (VillagerRecord vr : new ArrayList<>(townHall.getAllVillagerRecords())) {
         try {
            if (startVillager.testVillager(profile, vr)) {
               return vr;
            }
         } catch (Throwable ignored) {
         }
      }
      return null;
   }

   /**
    * Does a shipped quest match a discussion topic? GATHER ↔ a first step that asks for a {@code requiredgood};
    * DEFEND ↔ a quest that hands out a weapon/armour reward (a combat-flavoured quest); BUILD ↔ a quest that asks
    * for building materials. IDLE matches nothing. This is a best-effort bias over the shipped data, not a hard
    * gate — the second seed pass takes any present quest so the player is never left without a real quest to track.
    */
   private static boolean topicMatches(Quest q, VillageDiscussion.Topic topic) {
      QuestStep first = q.steps.get(0);
      switch (topic) {
         case GATHER:
            return !first.requiredGood.isEmpty();
         case BUILD:
            // building-material gather quests (stone/wood/clay required goods) suit a BUILD discussion.
            for (org.millenaire.common.item.InvItem iv : first.requiredGood.keySet()) {
               String n = iv == null ? "" : String.valueOf(iv.getName()).toLowerCase();
               if (n.contains("stone") || n.contains("wood") || n.contains("log")
                  || n.contains("plank") || n.contains("brick") || n.contains("clay")) {
                  return true;
               }
            }
            return false;
         case DEFEND:
            // a combat-flavoured quest rewards a weapon/armour.
            for (org.millenaire.common.item.InvItem iv : first.rewardGoods.keySet()) {
               String n = iv == null ? "" : String.valueOf(iv.getName()).toLowerCase();
               if (n.contains("sword") || n.contains("axe") || n.contains("bow")
                  || n.contains("armor") || n.contains("armour") || n.contains("shield")) {
                  return true;
               }
            }
            return false;
         default:
            return false;
      }
   }

   private static long clockTime(Building townHall) {
      try {
         if (townHall.world instanceof net.minecraft.server.level.ServerLevel sl) {
            return sl.getOverworldClockTime();
         }
         return townHall.world.getGameTime();
      } catch (Throwable t) {
         return 0L;
      }
   }

   private static String safeName(Building th) {
      try {
         String n = th.getVillageQualifiedName();
         if (n != null && !n.isEmpty()) {
            return n;
         }
      } catch (Throwable ignored) {
      }
      return String.valueOf(th.getPos());
   }
}
