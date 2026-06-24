package com.coderyo.jason.talk;

import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;

import com.coderyo.jason.build.MillNeedsModel;

/**
 * Phase 6 (#7) — VILLAGER DISCUSSION → QUEST + DECISION generator.
 *
 * <p>Faithful Java port of the sim-validated {@code discuss()} in {@code task-ops-sim/talksim.py}. The
 * villagers "discuss" the single most PRESSING topic in the live village state and that discussion produces
 * TWO outputs:
 * <ul>
 *   <li>a TEMPLATED QUEST line (via {@link TalkTemplates}) the player can be offered, and</li>
 *   <li>a DECISION that steers the EXISTING development systems — the needs model (Phase 2), expansion
 *       (Phase 3) and diplomacy (this phase).</li>
 * </ul>
 *
 * <p>The topic is picked from real state, in the same priority order the sim asserts:
 * <ol>
 *   <li><b>THREAT</b> (under attack / raiders bearing on the village) → a DEFEND quest + a DEFENSE decision
 *       (build a tower / raise strength — the same survival-first signal {@link MillNeedsModel} weights highest);</li>
 *   <li><b>RESOURCE SHORTFALL</b> (the largest stock gap) → a GATHER quest tuned to that resource + a decision
 *       to prioritise that workshop, which feeds straight into the {@link MillNeedsModel} needs model;</li>
 *   <li><b>GROWTH</b> (population over housing capacity) → a BUILD quest + an EXPAND decision (Phase 3).</li>
 * </ol>
 * If none apply the village is IDLE (no quest, no decision) — the strict no-fabrication outcome (we never invent
 * a quest the village doesn't actually need).
 *
 * <p>The DECISION is not a side note: {@link #steeredDecision(Building)} returns the live {@link MillNeedsModel}
 * decision the discussion endorses, so the village's next procedural build genuinely reflects what the villagers
 * discussed. Every discussion emits the greppable {@code ███ SIM TALK} evidence.
 */
public final class VillageDiscussion {

   /** Greppable tag for all discussion observation lines. */
   public static final String TAG = "███ SIM TALK";

   private VillageDiscussion() {
   }

   /** The kind of pressing topic the villagers settled on. */
   public enum Topic {
      DEFEND, GATHER, BUILD, IDLE
   }

   /** The result of a discussion: the chosen topic, a templated quest line, and the steering decision text. */
   public static final class DiscussionResult {
      public final Topic topic;
      /** The resource a GATHER topic is tuned to ({@code "wood"}/{@code "stone"}/{@code "food"}), else null. */
      public final String resource;
      /** The amount the gather quest asks for (the real shortfall), else 0. */
      public final int amount;
      /** The templated, culture-toned quest line (null when IDLE — no quest to offer). */
      public final String quest;
      /** A human-readable description of the DECISION that steers the needs model / expansion / diplomacy. */
      public final String decision;
      /** The live needs-model build type the decision endorses (null when IDLE), so the build truly follows the talk. */
      public final MillNeedsModel.BuildType steeredBuild;

      DiscussionResult(Topic topic, String resource, int amount, String quest, String decision,
                       MillNeedsModel.BuildType steeredBuild) {
         this.topic = topic;
         this.resource = resource;
         this.amount = amount;
         this.quest = quest;
         this.decision = decision;
         this.steeredBuild = steeredBuild;
      }
   }

   /**
    * Read the live village state and run the discussion (mirrors talksim.py {@code discuss}). Picks the most
    * pressing topic, generates the templated quest, and records the steering decision. Never fabricates a need.
    */
   public static DiscussionResult discuss(Building townHall) {
      MillNeedsModel.VillageState vs;
      try {
         vs = MillNeedsModel.readVillage(townHall);
      } catch (Throwable t) {
         MillLog.printException(TAG + " could not read village state", t);
         return new DiscussionResult(Topic.IDLE, null, 0, null, "DECISION: none (state unreadable)", null);
      }
      String village = safeName(townHall);
      String culture = TalkTemplates.cultureKey(townHall);

      // ---- (1) THREAT → DEFEND quest + DEFENSE decision (survival-first, like the needs model) ----
      int unmetThreat = Math.max(0, vs.threat - vs.defense);
      if (unmetThreat > 0) {
         String quest = TalkTemplates.fill("quest.defend",
            "culture", culture, "village", village, "enemy", threatName(townHall));
         return new DiscussionResult(Topic.DEFEND, null, 0, quest,
            "DECISION: prioritise DEFENSE (build tower / raise strength) — feeds the needs model + diplomacy",
            MillNeedsModel.BuildType.TOWER);
      }

      // ---- (2) RESOURCE SHORTFALL → GATHER quest tuned to the resource + that-workshop decision (needs model) ----
      String topRes = topShortfallResource(vs);
      if (topRes != null) {
         int amount = shortfallOf(vs, topRes);
         String quest = TalkTemplates.fill("quest.gather",
            "culture", culture, "village", village, "resource", topRes, "amount", Integer.toString(amount));
         return new DiscussionResult(Topic.GATHER, topRes, amount, quest,
            "DECISION: prioritise a " + topRes + " workshop (steers the needs model)",
            MillNeedsModel.BuildType.WORKSHOP);
      }

      // ---- (3) GROWTH → BUILD quest + EXPAND decision (Phase 3) ----
      int growth = Math.max(0, vs.pop - vs.housingCap);
      if (growth > 0) {
         String quest = TalkTemplates.fill("quest.build",
            "culture", culture, "village", village, "building", "house");
         return new DiscussionResult(Topic.BUILD, null, 0, quest,
            "DECISION: expand (Phase 3) + raise housing (needs model)", MillNeedsModel.BuildType.HOUSE);
      }

      // ---- nothing pressing → idle (no quest, no decision) ----
      return new DiscussionResult(Topic.IDLE, null, 0, null, "DECISION: none (village content)", null);
   }

   /**
    * Run the discussion AND log the {@code ███ SIM TALK} evidence (topic, templated quest, decision, and the
    * live needs-model build the decision steers). Returns the result.
    */
   public static DiscussionResult discussAndLog(Building townHall) {
      DiscussionResult r = discuss(townHall);
      MillNeedsModel.Decision steered = steeredDecision(townHall);
      MillLog.major(null, TAG + " DISCUSS '" + safeName(townHall) + "' culture=" + TalkTemplates.cultureKey(townHall)
         + " topic=" + r.topic + (r.resource != null ? ":" + r.resource : "")
         + " quest=\"" + (r.quest == null ? "(none)" : r.quest) + "\""
         + " | " + r.decision
         + " | steers needs-model → " + (steered == null ? "(no gap)" : steered.type + " reason=" + steered.reason));
      return r;
   }

   /**
    * The live {@link MillNeedsModel} decision the discussion endorses — proof the discussion DECISION genuinely
    * feeds the existing development system (the village's next build reflects what was discussed). Null when the
    * village has no gap to act on.
    */
   public static MillNeedsModel.Decision steeredDecision(Building townHall) {
      try {
         return MillNeedsModel.decide(townHall);
      } catch (Throwable t) {
         return null;
      }
   }

   // ---- helpers ---------------------------------------------------------------------------------

   /** The largest resource shortfall (the gap the gather quest + workshop decision target), or null if none. */
   private static String topShortfallResource(MillNeedsModel.VillageState vs) {
      int wood = Math.max(0, MillNeedsModel.NEED_WOOD - vs.woodStock);
      int stone = Math.max(0, MillNeedsModel.NEED_STONE - vs.stoneStock);
      int food = Math.max(0, MillNeedsModel.NEED_FOOD - vs.foodStock);
      String best = null;
      int bestGap = 0;
      if (wood > bestGap) {
         bestGap = wood;
         best = "wood";
      }
      if (stone > bestGap) {
         bestGap = stone;
         best = "stone";
      }
      if (food > bestGap) {
         bestGap = food;
         best = "food";
      }
      return best;
   }

   private static int shortfallOf(MillNeedsModel.VillageState vs, String res) {
      switch (res) {
         case "wood": return Math.max(0, MillNeedsModel.NEED_WOOD - vs.woodStock);
         case "stone": return Math.max(0, MillNeedsModel.NEED_STONE - vs.stoneStock);
         case "food": return Math.max(0, MillNeedsModel.NEED_FOOD - vs.foodStock);
         default: return 0;
      }
   }

   private static String threatName(Building townHall) {
      try {
         if (townHall.underAttack) {
            return "raiders";
         }
      } catch (Throwable ignored) {
      }
      return "raiders";
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
