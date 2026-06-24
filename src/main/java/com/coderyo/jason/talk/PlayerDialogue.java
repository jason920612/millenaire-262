package com.coderyo.jason.talk;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;

import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.quest.QuestInstance;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;

import com.coderyo.jason.build.MillNeedsModel;

/**
 * Phase 6 (#7) — PLAYER DIALOGUE: the player talks WITH a village (templated, culture-toned).
 *
 * <p>Faithful Java port of the player-facing half of {@code task-ops-sim/talksim.py}. It hooks into the
 * villager chat path ({@link ServerSender#sendChat}) and the village's discussion/diplomacy so a player can:
 * <ul>
 *   <li><b>TAKE the discussion-generated QUEST</b> — {@link #takeQuest(Player, Building)} runs the village
 *       {@link VillageDiscussion discussion}, shows the templated quest line in chat, and returns the
 *       discussion result so the surrounding system can register the quest;</li>
 *   <li><b>ASK the village STATE</b> — {@link #askState(Player, Building)} replies with a templated, live-state
 *       sentence (population, mood, top need) filled from the real {@link MillNeedsModel} read;</li>
 *   <li><b>REPRESENT the village in DIPLOMACY</b> — {@link #representInDiplomacy(Player, Building, Building)}
 *       carries the village's diplomacy proposal to ANOTHER village and reports the negotiated outcome — the
 *       player as the courier/envoy between two villages.</li>
 * </ul>
 *
 * <p>Every line shown to the player is TEMPLATED via {@link TalkTemplates} (culture greeting + slots from live
 * state), so no hand-authored corpus is needed. All three actions also emit the greppable {@code ███ SIM TALK}
 * evidence so the headless observer can prove the dialogue. Strict: an IDLE village offers no quest (we never
 * invent one), and a represent call only reports what the diplomacy engine actually negotiated.
 */
public final class PlayerDialogue {

   /** Greppable tag (shared with the discussion observer master tag). */
   public static final String TAG = VillageDiscussion.TAG;

   private PlayerDialogue() {
   }

   /**
    * The player ASKS the village how it's doing → a TEMPLATED, live-state reply (the {@code state} template:
    * "{village} ({culture}): pop {pop}, {mood}. We most need {top_need}."). Sends it to the player's chat and
    * returns the rendered line. Pure read of village state — no mutation.
    */
   public static String askState(Player player, Building townHall) {
      MillNeedsModel.VillageState vs;
      try {
         vs = MillNeedsModel.readVillage(townHall);
      } catch (Throwable t) {
         MillLog.printException(TAG + " askState could not read village", t);
         return null;
      }
      String village = safeName(townHall);
      String culture = TalkTemplates.cultureKey(townHall);
      String mood = moodOf(vs);
      String topNeed = topNeedOf(townHall, vs);
      String line = TalkTemplates.fill("state",
         "village", village, "culture", culture, "pop", Integer.toString(vs.pop),
         "mood", mood, "top_need", topNeed);
      sendChat(player, line);
      MillLog.major(null, TAG + " PLAYER asks state of '" + village + "': \"" + line + "\"");
      return line;
   }

   /**
    * The player TAKES the village's discussion-generated quest from the villager they spoke to. Runs the
    * {@link VillageDiscussion discussion}, shows the templated quest line in chat (or a "nothing needed" note when
    * the village is IDLE), then SEEDS a REAL {@link QuestInstance} on the player's profile via
    * {@link DiscussionQuests} — mapped to the discussion topic and preferring the {@code clicked} villager as the
    * quest's starting villager — so the player can accept / track / complete it through the normal GuiQuest path.
    * Returns the discussion result. No fabrication: an IDLE village offers no quest, and a real quest is only
    * seeded when a shipped quest maps to a villager actually present here.
    */
   public static VillageDiscussion.DiscussionResult takeQuest(Player player, Building townHall, MillVillager clicked) {
      VillageDiscussion.DiscussionResult r = VillageDiscussion.discussAndLog(townHall);
      if (r.quest == null) {
         String greet = TalkTemplates.greet(townHall);
         sendChat(player, greet + " " + safeName(townHall) + " needs nothing of you right now.");
         MillLog.major(null, TAG + " PLAYER take-quest at '" + safeName(townHall) + "': IDLE (no quest offered)");
         return r;
      }
      sendChat(player, r.quest);
      MillLog.major(null, TAG + " PLAYER takes quest at '" + safeName(townHall) + "' topic=" + r.topic
         + (r.resource != null ? ":" + r.resource : "") + " quest=\"" + r.quest + "\" steers=" + r.decision);

      // Seed a REAL QuestInstance (mapped to the discussion topic) so this is not a templated string only.
      try {
         MillWorldData mw = townHall.mw;
         UserProfile profile = (mw != null && player != null) ? mw.getProfile(player) : null;
         QuestInstance qi = DiscussionQuests.seedFor(mw, townHall, profile, r, clicked);
         if (qi != null) {
            sendChat(player, TalkTemplates.greet(townHall) + " (quest '" + qi.quest.key + "' is now in your journal.)");
         } else {
            MillLog.major(null, TAG + " PLAYER take-quest at '" + safeName(townHall)
               + "': no shipped quest maps to a present villager — templated line shown, no QuestInstance seeded");
         }
      } catch (Throwable t) {
         MillLog.printException(TAG + " takeQuest could not seed a QuestInstance", t);
      }
      return r;
   }

   /**
    * The player REPRESENTS {@code from} in diplomacy toward {@code to}: carries {@code from}'s proposal to
    * {@code to}, the diplomacy engine negotiates, and the outcome is reported to the player + logged. This is the
    * player-as-envoy path the vision calls for. Returns the negotiation result (NONE proposal when {@code from}
    * has nothing to propose).
    */
   public static VillageDiplomacy.NegotiationResult representInDiplomacy(Player player, Building from, Building to) {
      VillageDiplomacy.Proposal p = VillageDiplomacy.propose(from, to);
      if (p.kind == VillageDiplomacy.Kind.NONE) {
         String greet = TalkTemplates.greet(from);
         sendChat(player, greet + " " + safeName(from) + " has no proposal for " + safeName(to) + " at this time.");
         MillLog.major(null, TAG + " PLAYER represents '" + safeName(from) + "' → '" + safeName(to)
            + "': no proposal to carry");
         return new VillageDiplomacy.NegotiationResult(p, false, "no proposal");
      }
      // Show the player the proposal they are carrying, then negotiate it.
      sendChat(player, "[" + safeName(from) + " → " + safeName(to) + "] " + p.line);
      VillageDiplomacy.NegotiationResult nr = VillageDiplomacy.negotiate(from, to);
      sendChat(player, "[" + safeName(to) + "] " + (nr.accepted ? "We accept." : "We decline.")
         + " (" + nr.effect + ")");
      MillLog.major(null, TAG + " PLAYER carried '" + safeName(from) + "' " + p.kind + " to '" + safeName(to)
         + "' → " + (nr.accepted ? "ACCEPTED" : "REJECTED") + " effect: " + nr.effect);
      return nr;
   }

   // ---- helpers ---------------------------------------------------------------------------------

   /** A coarse mood from the live state (templated slot): under threat → uneasy, gaps → wanting, else content. */
   private static String moodOf(MillNeedsModel.VillageState vs) {
      if (vs.threat - vs.defense > 0) {
         return "uneasy";
      }
      if (vs.pop - vs.housingCap > 0) {
         return "crowded";
      }
      if (vs.woodStock < MillNeedsModel.NEED_WOOD || vs.stoneStock < MillNeedsModel.NEED_STONE
         || vs.foodStock < MillNeedsModel.NEED_FOOD) {
         return "wanting for supplies";
      }
      return "content";
   }

   /** The village's top need as a templated slot, derived from the live needs-model decision (or "nothing"). */
   private static String topNeedOf(Building townHall, MillNeedsModel.VillageState vs) {
      try {
         MillNeedsModel.Decision d = MillNeedsModel.decide(vs);
         if (d != null) {
            if (d.resource != null && d.resource != MillNeedsModel.Resource.NONE) {
               return d.resource.name().toLowerCase();
            }
            switch (d.type) {
               case HOUSE: return "housing";
               case TOWER: return "defense";
               case MARKET: return "trade";
               case GRANARY: return "storage";
               default: return d.reason;
            }
         }
      } catch (Throwable ignored) {
      }
      return "nothing";
   }

   private static void sendChat(Player player, String line) {
      if (player == null) {
         return;
      }
      try {
         ServerSender.sendChat(player, ChatFormatting.YELLOW, line);
      } catch (Throwable t) {
         // The headless fake player may not be resolvable for a client packet — the templated line is still the
         // dialogue (logged below); a chat-send failure must not break the dialogue path.
         MillLog.printException(TAG + " sendChat skipped (headless client-notify): " + line, t);
      }
   }

   static String safeName(Building th) {
      try {
         String n = th.getVillageQualifiedName();
         if (n != null && !n.isEmpty()) {
            return n;
         }
      } catch (Throwable ignored) {
      }
      try {
         return "village@" + th.getPos();
      } catch (Throwable ignored) {
         return "village?";
      }
   }
}
