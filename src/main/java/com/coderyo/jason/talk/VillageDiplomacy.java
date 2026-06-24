package com.coderyo.jason.talk;

import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;

import com.coderyo.jason.build.MillNeedsModel;
import com.coderyo.jason.merge.VillageMergeFound;
import com.coderyo.jason.war.VillageWar;

/**
 * Phase 6 (#7) — the inter-village DIPLOMACY engine (the social driver that ties the vision together).
 *
 * <p>Faithful Java port of the sim-validated {@code propose()} / {@code respond()} / {@code apply_diplomacy()}
 * in {@code task-ops-sim/talksim.py}. One village PROPOSES to another based on RELATION + NEEDS, the other
 * ACCEPTS or REJECTS from its OWN state, and the accepted proposal's EFFECT is applied for real.
 *
 * <h2>PROPOSE — {@link #propose(Building, Building)}</h2>
 * The proposer reads its relation to + needs of the target and picks ONE kind (the talksim priority order):
 * <ol>
 *   <li><b>PEACE</b> — if the two are AT WAR and the proposer has taken heavy losses (low strength) it sues for
 *       a ceasefire;</li>
 *   <li><b>TRADE</b> — if the proposer has a SURPLUS resource the target NEEDS (complementary economies);</li>
 *   <li><b>MERGE</b> — if they are CLOSE (relation ≥ merge threshold) AND the SAME culture;</li>
 *   <li><b>ALLIANCE</b> — if they are merely FRIENDLY (relation ≥ alliance threshold);</li>
 *   <li>otherwise nothing (no proposal — the strict no-fabrication outcome).</li>
 * </ol>
 *
 * <h2>RESPOND — {@link #respond(Building, Building, Kind)}</h2>
 * The target accepts/rejects from its own state: PEACE is always accepted (no one prefers a costly war); TRADE if
 * it has its own complementary surplus or a non-negative relation; ALLIANCE if relation is friendly enough; MERGE
 * only if relation is high, cultures match, and the target is the SMALLER village (the smaller consents to join).
 *
 * <h2>APPLY — {@link #apply(Building, Building, Kind, boolean)}</h2>
 * An accepted proposal's effect is applied through the EXISTING systems — this is the phase's job of wiring the
 * prior phases' proxies to real negotiation:
 * <ul>
 *   <li><b>ALLIANCE</b>: mutual friendly relation (the authoritative two-sided {@link Building#adjustRelation});</li>
 *   <li><b>TRADE</b>: a relation boost (a standing trade pact warms relations);</li>
 *   <li><b>PEACE</b>: routed through {@link VillageWar#makePeace} — the Phase-5 war now ENDS via this negotiated
 *       ceasefire (replacing the proxy of war ending only by strength-resolution);</li>
 *   <li><b>MERGE</b>: routed through {@link VillageMergeFound#tryMerge} — the Phase-4 "both consent" merge proxy is
 *       now a REAL merge NEGOTIATION (the larger absorbs the smaller through the registry-safe merge path).</li>
 * </ul>
 *
 * <p>Strict no-grant / no-fallback: an unaccepted proposal does nothing; a merge still has to pass the Phase-4
 * gates (overlap / culture / consent) to actually absorb; peace only ends a war that was real. Every step emits
 * the greppable {@code ███ SIM DIPLOMACY} evidence.
 */
public final class VillageDiplomacy {

   /** Greppable tag for all diplomacy observation lines. */
   public static final String TAG = "███ SIM DIPLOMACY";

   // ---- thresholds (mirror talksim.py) ----------------------------------------------------------
   /** Relation ≥ this AND same culture → the proposer offers a MERGE (talksim {@code rel>=50}). */
   public static final int MERGE_RELATION = 50;
   /** Relation ≥ this → the proposer offers an ALLIANCE (talksim {@code rel>=20}). */
   public static final int ALLIANCE_RELATION = 20;
   /** The target accepts an ALLIANCE only if its own relation ≥ this (talksim {@code rel>=10}). */
   public static final int ALLIANCE_ACCEPT_RELATION = 10;
   /** A proposer this weak (defending strength ≤) while at war SUES FOR PEACE (talksim heavy-losses proxy). */
   public static final int PEACE_WEAKNESS_STRENGTH = 8;
   /** Mutual relation set on an accepted ALLIANCE (talksim {@code apply_diplomacy} alliance = 80). */
   public static final int ALLIANCE_RELATION_SET = 80;
   /** Relation gained by each side on an accepted TRADE pact (talksim {@code apply_diplomacy} trade += 10). */
   public static final int TRADE_RELATION_GAIN = 10;

   private VillageDiplomacy() {
   }

   /** The kind of diplomacy a proposal/response concerns (mirrors talksim.py's tagged kinds). */
   public enum Kind {
      ALLIANCE, TRADE, PEACE, MERGE, NONE
   }

   /** A proposal: its kind + the templated, culture-toned line (NONE/null when nothing is proposed). */
   public static final class Proposal {
      public final Kind kind;
      public final String line;
      /** For TRADE, the surplus↔need resource the pact concerns (else null). */
      public final String resource;

      Proposal(Kind kind, String line, String resource) {
         this.kind = kind;
         this.line = line;
         this.resource = resource;
      }

      static Proposal none() {
         return new Proposal(Kind.NONE, null, null);
      }
   }

   /** The full negotiation outcome (proposal → response → applied effect) for the {@code ███ SIM DIPLOMACY} log. */
   public static final class NegotiationResult {
      public final Proposal proposal;
      public final boolean accepted;
      public final String effect;

      NegotiationResult(Proposal proposal, boolean accepted, String effect) {
         this.proposal = proposal;
         this.accepted = accepted;
         this.effect = effect;
      }
   }

   // ===============================================================================================
   // PROPOSE
   // ===============================================================================================

   /**
    * Village {@code a} proposes to village {@code b} based on RELATION + NEEDS (mirrors talksim.py {@code propose}).
    * Returns the chosen proposal (kind + templated line), or {@link Proposal#none()} if nothing is warranted.
    */
   public static Proposal propose(Building a, Building b) {
      if (a == null || b == null || a == b) {
         return Proposal.none();
      }
      String culture = TalkTemplates.cultureKey(a);
      String va = safeName(a);
      String vb = safeName(b);
      int rel = relation(a, b);

      // ---- (1) AT WAR + heavy losses → sue for PEACE ----
      if (VillageWar.atWar(a, b)) {
         if (strengthOf(a) <= PEACE_WEAKNESS_STRENGTH) {
            String line = TalkTemplates.fill("dipl.peace", "culture", culture, "village", va, "other", vb);
            return new Proposal(Kind.PEACE, line, null);
         }
         return Proposal.none(); // still strong enough to fight on — no proposal
      }

      // ---- (2) complementary SURPLUS↔NEED → TRADE ----
      String tradeRes = complementaryResource(a, b);
      if (tradeRes != null) {
         String line = TalkTemplates.fill("dipl.trade", "culture", culture, "village", va, "other", vb,
            "surplus_res", tradeRes, "need_res", tradeRes);
         return new Proposal(Kind.TRADE, line, tradeRes);
      }

      // ---- (3) close + same culture → MERGE ----
      if (rel >= MERGE_RELATION && sameCulture(a, b)) {
         String line = TalkTemplates.fill("dipl.merge", "culture", culture, "village", va, "other", vb);
         return new Proposal(Kind.MERGE, line, null);
      }

      // ---- (4) friendly → ALLIANCE ----
      if (rel >= ALLIANCE_RELATION) {
         String line = TalkTemplates.fill("dipl.alliance", "culture", culture, "village", va, "other", vb);
         return new Proposal(Kind.ALLIANCE, line, null);
      }

      return Proposal.none();
   }

   // ===============================================================================================
   // RESPOND
   // ===============================================================================================

   /**
    * Village {@code b} accepts/rejects {@code a}'s proposal from its OWN state (mirrors talksim.py {@code respond}).
    */
   public static boolean respond(Building b, Building a, Kind kind) {
      if (b == null || a == null || kind == null) {
         return false;
      }
      int rel = relation(b, a);
      switch (kind) {
         case PEACE:
            return true; // always accept an end to a costly war
         case TRADE:
            // accept if b has its own complementary surplus a needs, or relations are non-negative
            return complementaryResource(b, a) != null || rel >= 0;
         case ALLIANCE:
            return rel >= ALLIANCE_ACCEPT_RELATION;
         case MERGE:
            // the SMALLER consents to join: relation high, cultures match, and b is not larger than a
            return rel >= MERGE_RELATION && sameCulture(b, a) && popOf(b) <= popOf(a);
         default:
            return false;
      }
   }

   // ===============================================================================================
   // APPLY (wires into the existing systems)
   // ===============================================================================================

   /**
    * Apply the accepted proposal's EFFECT through the existing systems (mirrors talksim.py {@code apply_diplomacy}
    * + the integration the plan requires). Returns a human-readable description of the applied effect. A rejected
    * proposal applies nothing.
    */
   public static String apply(Building a, Building b, Kind kind, boolean accepted) {
      if (!accepted || kind == null || kind == Kind.NONE) {
         return "no effect (proposal not accepted)";
      }
      switch (kind) {
         case ALLIANCE:
            setMutualRelation(a, b, ALLIANCE_RELATION_SET);
            return "ALLIANCE pact: mutual relation set to " + ALLIANCE_RELATION_SET;
         case TRADE:
            adjustMutualRelation(a, b, TRADE_RELATION_GAIN);
            return "TRADE pact: each side +" + TRADE_RELATION_GAIN + " relation (now "
               + relation(a, b) + "/" + relation(b, a) + ")";
         case PEACE: {
            // Route through the Phase-5 war system: this negotiated ceasefire ENDS the war (replaces the proxy).
            boolean wasWar = VillageWar.makePeace(a, b);
            return "CEASEFIRE via VillageWar.makePeace: war " + (wasWar ? "ENDED" : "was not active")
               + ", relations eased to -20 (Phase-5 war-peace now flows through diplomacy)";
         }
         case MERGE: {
            // Route through the Phase-4 merge: the "both consent" proxy is now this real negotiation. The merge
            // still has to pass the Phase-4 gates (overlap / culture / consent) to actually absorb — strict.
            VillageMergeFound.MergeOutcome m = VillageMergeFound.tryMerge(a, b);
            return "MERGE via VillageMergeFound.tryMerge: result=" + m.result + " (" + m.reason + ") — Phase-4 "
               + "merge-consent now flows through diplomacy"
               + (m.survivor != null ? " survivor='" + safeName(m.survivor) + "'" : "");
         }
         default:
            return "no effect";
      }
   }

   // ===============================================================================================
   // full negotiation + logging
   // ===============================================================================================

   /**
    * Run a full negotiation a→b (propose → b responds → apply) and emit the {@code ███ SIM DIPLOMACY} evidence.
    * Returns the outcome. This is the single entry the ambient tick + the player-representation path both use.
    */
   public static NegotiationResult negotiate(Building a, Building b) {
      Proposal p = propose(a, b);
      if (p.kind == Kind.NONE) {
         MillLog.major(null, TAG + " '" + safeName(a) + "' → '" + safeName(b)
            + "' no proposal (relation=" + relation(a, b) + ", atWar=" + VillageWar.atWar(a, b) + ")");
         return new NegotiationResult(p, false, "no proposal");
      }
      boolean accepted = respond(b, a, p.kind);
      String effect = apply(a, b, p.kind, accepted);
      MillLog.major(null, TAG + " '" + safeName(a) + "' → '" + safeName(b) + "' " + p.kind
         + " | \"" + p.line + "\" | " + (accepted ? "ACCEPTED" : "REJECTED") + " | effect: " + effect);
      return new NegotiationResult(p, accepted, effect);
   }

   // ===============================================================================================
   // state readers (relation + needs + surplus) — all from the live village data
   // ===============================================================================================

   /**
    * The resource {@code a} has in SURPLUS that {@code b} NEEDS (a complementary trade), or null. "Surplus" =
    * stock at/above the needs-model target; "need" = stock below it. We compare the three tracked resources
    * (wood/stone/food) so a real complementary pair surfaces.
    */
   public static String complementaryResource(Building a, Building b) {
      MillNeedsModel.VillageState sa = read(a);
      MillNeedsModel.VillageState sb = read(b);
      if (sa == null || sb == null) {
         return null;
      }
      if (sa.woodStock >= MillNeedsModel.NEED_WOOD && sb.woodStock < MillNeedsModel.NEED_WOOD) {
         return "wood";
      }
      if (sa.stoneStock >= MillNeedsModel.NEED_STONE && sb.stoneStock < MillNeedsModel.NEED_STONE) {
         return "stone";
      }
      if (sa.foodStock >= MillNeedsModel.NEED_FOOD && sb.foodStock < MillNeedsModel.NEED_FOOD) {
         return "food";
      }
      return null;
   }

   private static MillNeedsModel.VillageState read(Building b) {
      try {
         return MillNeedsModel.readVillage(b);
      } catch (Throwable t) {
         return null;
      }
   }

   private static int relation(Building a, Building b) {
      try {
         Integer r = a.getRelations().get(b.getPos());
         return r == null ? 0 : r;
      } catch (Throwable t) {
         return 0;
      }
   }

   private static boolean sameCulture(Building a, Building b) {
      return a.culture != null && b.culture != null && a.culture.key.equals(b.culture.key);
   }

   private static int popOf(Building b) {
      try {
         return b.getVillagerRecords().size();
      } catch (Throwable t) {
         return 0;
      }
   }

   private static int strengthOf(Building b) {
      try {
         return Math.max(0, b.getVillageDefendingStrength());
      } catch (Throwable t) {
         return 0;
      }
   }

   private static void setMutualRelation(Building a, Building b, int value) {
      try {
         a.adjustRelation(b.getPos(), value, true);
      } catch (Throwable t) {
         // adjustRelation sets both sides; if it can't resolve a side, write the maps directly (no grant — only
         // the relation value moves).
         a.getRelations().put(b.getPos(), value);
         b.getRelations().put(a.getPos(), value);
      }
   }

   private static void adjustMutualRelation(Building a, Building b, int delta) {
      try {
         a.adjustRelation(b.getPos(), delta, false);
      } catch (Throwable t) {
         a.getRelations().merge(b.getPos(), delta, Integer::sum);
         b.getRelations().merge(a.getPos(), delta, Integer::sum);
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
