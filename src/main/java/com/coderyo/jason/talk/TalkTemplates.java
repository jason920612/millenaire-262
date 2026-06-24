package com.coderyo.jason.talk;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.millenaire.common.culture.Culture;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;

/**
 * Phase 6 (#7) — the TEMPLATE + SLOT-FILL engine.
 *
 * <p>Faithful Java port of the sim-validated {@code TEMPLATES} / {@code CULTURE_GREET} / {@code fill()} in
 * {@code task-ops-sim/talksim.py}. All the natural-language Millénaire produces in this phase — discussion
 * quests, village-state replies, diplomacy proposals — comes from a SMALL set of templates whose
 * <code>{slots}</code> are filled from LIVE village state (culture / population / mood / needs / relations /
 * resources). The template grammar is the whole point: it minimises hand-authored text yet scales to the
 * infinite emergent situations the vision generates (a fresh village name, a fresh shortfall resource, a fresh
 * neighbour each produce correct prose with no new strings).
 *
 * <p>Each template also carries a CULTURE-SPECIFIC greeting/tone (the {@code culture_greet} slot, defaulted
 * from {@link #CULTURE_GREET} by the village's culture key) so a Norman village opens with "Bonjour." and a
 * Japanese one with "Konnichiwa." — recognisable per-culture flavour layered over the shared grammar.
 *
 * <p>This class is PURE (no world mutation): it turns a key + slots into a String. The discussion / diplomacy /
 * dialogue engines call {@link #fill} to produce their observable lines. Strict no-fallback: an unknown key
 * throws (a programming error, surfaced loud, not silently swallowed), and a missing required slot throws via
 * {@link String#format} — we never emit half-filled text.
 */
public final class TalkTemplates {

   private TalkTemplates() {
   }

   /** Classpath location of the template resource (the resource keeps the text out of code, per the plan). */
   private static final String TEMPLATES_RESOURCE = "/data/millenaire/talk/templates.txt";

   /** The template grammar (mirrors talksim.py {@code TEMPLATES}). Keys are dot-namespaced by topic. */
   private static final Map<String, String> TEMPLATES = new LinkedHashMap<>();
   /** Per-culture greeting/tone (mirrors talksim.py {@code CULTURE_GREET}); falls back to a neutral "Hello.". */
   public static final Map<String, String> CULTURE_GREET = new LinkedHashMap<>();

   static {
      // In-code defaults = the strict fallback grammar (the engine is never left with zero templates).
      TEMPLATES.put("quest.gather", "{culture_greet} Our {village} lacks {resource}. Bring us {amount} {resource} and be rewarded.");
      TEMPLATES.put("quest.defend", "{culture_greet} {enemy} threatens {village}! Help us drive them back.");
      TEMPLATES.put("quest.build", "{culture_greet} {village} grows — help raise a new {building}.");
      TEMPLATES.put("state", "{village} ({culture}): pop {pop}, {mood}. We most need {top_need}.");
      TEMPLATES.put("dipl.alliance", "{village} proposes an ALLIANCE with {other} — friends share strength.");
      TEMPLATES.put("dipl.trade", "{village} offers {other} a TRADE pact: our {surplus_res} for your {need_res}.");
      TEMPLATES.put("dipl.peace", "{village} sues {other} for PEACE — this war has cost us dearly.");
      TEMPLATES.put("dipl.merge", "{village} proposes to MERGE with {other} into one people.");

      CULTURE_GREET.put("norman", "Bonjour.");
      CULTURE_GREET.put("japanese", "Konnichiwa.");
      CULTURE_GREET.put("mayan", "Greetings, traveler.");
      CULTURE_GREET.put("byzantines", "Khaire.");

      // Override from the resource file (the authoritative, data-driven grammar). Keeps the text templated +
      // minimal, out of code, and extensible (a new line in the resource is a new template — no recompile).
      loadResource();
   }

   /**
    * Load the template + greeting grammar from {@link #TEMPLATES_RESOURCE} on the classpath, overriding the
    * in-code defaults. {@code key=template} lines populate {@link #TEMPLATES}; {@code greet.<culture>=<tone>}
    * lines populate {@link #CULTURE_GREET}. Blank/{@code #}-comment lines are skipped. If the resource is absent
    * the in-code defaults remain (the engine still works) — but a malformed PRESENT resource is logged loud.
    */
   private static void loadResource() {
      try (InputStream in = TalkTemplates.class.getResourceAsStream(TEMPLATES_RESOURCE)) {
         if (in == null) {
            return; // resource not on the classpath in this context → keep the in-code defaults
         }
         try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int loaded = 0;
            while ((line = r.readLine()) != null) {
               String s = line.trim();
               if (s.isEmpty() || s.startsWith("#")) {
                  continue;
               }
               int eq = s.indexOf('=');
               if (eq <= 0) {
                  continue;
               }
               String key = s.substring(0, eq).trim();
               String value = s.substring(eq + 1); // keep template whitespace intact
               if (key.startsWith("greet.")) {
                  CULTURE_GREET.put(key.substring("greet.".length()), value.trim());
               } else {
                  TEMPLATES.put(key, value);
               }
               loaded++;
            }
            MillLog.major(null, "TalkTemplates: loaded " + loaded + " entries from " + TEMPLATES_RESOURCE);
         }
      } catch (Throwable t) {
         MillLog.printException("TalkTemplates: failed to load " + TEMPLATES_RESOURCE + " (using in-code defaults)", t);
      }
   }

   /** The culture-toned greeting for a culture key (neutral "Hello." for any culture without a specific tone). */
   public static String greet(String cultureKey) {
      return CULTURE_GREET.getOrDefault(cultureKey == null ? "" : cultureKey, "Hello.");
   }

   /** The culture-toned greeting for a village's culture. */
   public static String greet(Building townHall) {
      return greet(cultureKey(townHall));
   }

   /**
    * Fill a template by key with the given slots (mirrors talksim.py {@code fill}). If a {@code culture} slot is
    * present and {@code culture_greet} is not, the culture-specific greeting is injected automatically. Throws on
    * an unknown key or a missing required slot — we never emit half-filled prose (strict no-fallback).
    *
    * @param key   one of the template keys (e.g. {@code "quest.gather"}, {@code "dipl.trade"})
    * @param slots flat name→value pairs ({@code "village","Akashi", "resource","wood", ...})
    */
   public static String fill(String key, String... slots) {
      String template = TEMPLATES.get(key);
      if (template == null) {
         throw new IllegalArgumentException("TalkTemplates: unknown template key '" + key + "'");
      }
      Map<String, String> map = new LinkedHashMap<>();
      if (slots != null) {
         if (slots.length % 2 != 0) {
            throw new IllegalArgumentException("TalkTemplates.fill: odd slot count for '" + key + "'");
         }
         for (int i = 0; i + 1 < slots.length; i += 2) {
            map.put(slots[i], slots[i + 1]);
         }
      }
      // Default the culture-toned greeting from the culture slot (the python setdefault).
      if (!map.containsKey("culture_greet")) {
         map.put("culture_greet", greet(map.get("culture")));
      }
      return render(key, template, map);
   }

   /** Render {@code {slot}} placeholders from the map; a referenced-but-missing slot is a strict error. */
   private static String render(String key, String template, Map<String, String> slots) {
      StringBuilder out = new StringBuilder(template.length() + 16);
      int i = 0;
      int n = template.length();
      while (i < n) {
         char c = template.charAt(i);
         if (c == '{') {
            int close = template.indexOf('}', i + 1);
            if (close < 0) {
               throw new IllegalArgumentException("TalkTemplates: unterminated slot in template '" + key + "'");
            }
            String name = template.substring(i + 1, close);
            String value = slots.get(name);
            if (value == null) {
               throw new IllegalArgumentException("TalkTemplates: template '" + key + "' missing slot '" + name + "'");
            }
            out.append(value);
            i = close + 1;
         } else {
            out.append(c);
            i++;
         }
      }
      return out.toString();
   }

   static String cultureKey(Building townHall) {
      Culture c = townHall != null ? townHall.culture : null;
      return c != null ? c.key : "";
   }
}
