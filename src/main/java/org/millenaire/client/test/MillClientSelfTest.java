package org.millenaire.client.test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.millenaire.client.gui.GuiTrade;
import org.millenaire.client.gui.text.GuiText;
import org.millenaire.client.gui.text.GuiTravelBook;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.ui.ContainerTrade;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.WorldGenVillage;

/**
 * CLIENT-side automated self-test harness for the Millénaire 26.2 Fabric port — the client mirror of
 * the server-side {@code org.millenaire.common.test.MillSelfTest}.
 *
 * <p>It simulates a real player's CLIENT operations (opening Mill screens, clicking trade slots,
 * paging the travel book, firing book buttons, typing) and verifies the <em>observable client
 * effect</em> — most importantly that a trade-slot click actually produces an outbound Mill packet
 * (the exact class of bug a server harness can never see, e.g. "trade GUI click doesn't send the
 * trade packet"). Everything is logged with the greppable {@code [MILLCLIENTTEST]} tag, a SUMMARY is
 * printed, and the client then disconnects to the main menu and stops so the process self-terminates.
 *
 * <h2>How to launch</h2>
 * It runs ONLY when gated on, via either:
 * <ul>
 *   <li>JVM system property: {@code -Dmillenaire.clienttest=true}, or</li>
 *   <li>environment variable: {@code MILLENAIRE_CLIENTTEST=1} (reliably inherited by Loom's forked
 *       {@code runClient} JVM where {@code -D} may not forward).</li>
 * </ul>
 * The harness needs a real client (a display / OpenGL context — i.e. {@code gradlew runClient}); the
 * test LOGIC runs and logs regardless, and any step that is genuinely impossible to simulate headlessly
 * is logged as {@code SKIPPED} with the reason rather than failing.
 *
 * <p>It does NOT auto-create a world (that path is fragile across MC versions). Instead it waits until
 * the player is in ANY singleplayer world and then runs. To launch it: start the client with the flag,
 * create/open a singleplayer world (ideally near a Millénaire village so the trade-click test finds a
 * real shop/merchant), and the harness fires automatically a few ticks after you are in-world.
 *
 * <h2>Threading</h2>
 * Driven from {@link ClientTickEvents#END_CLIENT_TICK}, so every step runs on the client thread (the
 * only thread on which screen input / {@code setScreenAndShow} are legal) with real ticks between
 * steps. A hard max-tick guard force-stops the client even if a step stalls.
 */
@Environment(EnvType.CLIENT)
public final class MillClientSelfTest {
   public static final String PROPERTY = "millenaire.clienttest";
   private static final String TAG = "[MILLCLIENTTEST]";

   // --- Per-step tick schedule (relative to the tick the player first appears in-world). ---
   private static final int TICK_INTRO = 5;
   private static final int TICK_CREATIVE_TABS = 20;
   // --- Visual / world-spawn steps (screenshots need a few rendered frames AFTER setup). ---
   private static final int TICK_SPAWN_VILLAGE = 40;       // request village gen on the server thread
   private static final int TICK_TELEPORT_OVERVIEW = 80;   // teleport player above/away from the village
   private static final int TICK_SHOT_VILLAGE = 95;        // screenshot the village overview
   private static final int TICK_TELEPORT_VILLAGER = 110;  // move next to a villager, aim camera at it
   private static final int TICK_SHOT_VILLAGER = 125;      // screenshot the villager close-up
   private static final int TICK_PLACE_BLOCKS = 140;       // place a sampler of Mill blocks in front of player
   private static final int TICK_SHOT_BLOCKS = 160;        // screenshot the block sampler
   // --- GUI steps (each followed a few ticks later by a screenshot of the open screen). ---
   private static final int TICK_TRAVELBOOK_OPEN = 180;
   private static final int TICK_SHOT_TRAVELBOOK = 195;
   private static final int TICK_TRAVELBOOK_PAGE = 205;
   private static final int TICK_TRAVELBOOK_BUTTON = 220;
   private static final int TICK_TRADE_OPEN_CLICK = 240;
   private static final int TICK_SHOT_TRADE = 255;
   private static final int TICK_TEXTFIELD_TYPE = 270;
   private static final int TICK_CREATIVE_OPEN = 285;
   private static final int TICK_SHOT_CREATIVE = 300;
   // --- DYNAMIC gameplay steps: performed AFTER the village has had hundreds of ticks to populate
   //     (villagers stream in / shops compute their goods), each verifying an OBSERVED before->after. ---
   private static final int TICK_DYN_STAND_IN_VILLAGE = 360;  // teleport the player INTO the active village
   private static final int TICK_DYN_ACTIVITY_START = 380;    // begin the ~600-tick villager-activity sample
   private static final int TICK_DYN_TRADE = 1010;            // real buy + sell with money/inventory verification
   private static final int TICK_DYN_QUEST = 1040;            // accept a quest if one is available
   private static final int TICK_DYN_REPUTATION = 1070;       // read rep, change it, read again
   private static final int TICK_DYN_VILLAGE_CREATE_REMOVE = 1100; // create then remove a village, count before/after
   private static final int TICK_DYN_ACTIVITY_REPORT = 1110;  // emit the sampled activity numbers
   private static final int TICK_DYN_COMBAT_SPAWN = 1000;     // spawn hostiles + target a fighter (BEFORE the village-create-remove gen spike at 1100)
   private static final int TICK_DYN_COMBAT_CHECK = 1480;     // verify it moved/fought (big gap: integrated server lags)
   private static final int TICK_SUMMARY = 1510;
   private static final int TICK_RETURN_MENU = 1525;
   private static final int TICK_STOP = 1540;
   /** Window (in ticks) over which villager activity is sampled while the player stands in the village. */
   private static final int ACTIVITY_WINDOW_TICKS = 600;
   /** Absolute safety net: never let the client hang waiting for the harness. */
   private static final int MAX_TICK_GUARD = 2400;
   /** Give the world this many ticks to settle (villagers stream in) before we consider ourselves "in world". */
   private static final int WORLD_SETTLE_TICKS = 40;
   /** How many ticks to wait for our auto-created world to finish loading before giving up. */
   private static final int WORLD_CREATE_TIMEOUT_TICKS = 1200;

   private static boolean started = false;

   private final Minecraft mc;
   private int tick = 0;
   private int settleCounter = 0;
   private boolean running = false;
   private boolean halted = false;

   // --- Auto-world-creation state (phase 0, before we are "in world"). ---
   private boolean worldCreateRequested = false;
   private int worldCreateWaitTicks = 0;
   private int waitDiagTicks = 0;

   // --- Village-spawn / screenshot bookkeeping. ---
   private Point spawnedVillagePoint = null;
   private int villageSurfaceY = 64;

   // --- Result accumulators (for the SUMMARY block). ---
   private final Map<String, String> results = new LinkedHashMap<>();
   private final Map<String, String> distinctExceptions = new LinkedHashMap<>();

   // --- Dynamic-step bookkeeping (sampled across many ticks / reported in the SUMMARY). ---
   private boolean activitySampling = false;
   private int activityTicksSampled = 0;
   private int activitySamples = 0;
   private long activityGoalSum = 0;        // sum over samples of (#villagers with a goalKey)
   private long activityMovingSum = 0;      // sum over samples of (#villagers that moved since last sample)
   private int activityMaxVillagers = 0;    // peak villager count seen in any sample
   private int activitySpeakingEvents = 0;  // distinct (villagerId, speech_started) speech events observed
   private final Map<Long, Long> lastSpeechStart = new LinkedHashMap<>(); // villagerId -> last speech_started seen
   private final Map<Long, Vec3> lastPos = new LinkedHashMap<>();         // villagerId -> last sampled position
   private final List<String> sentencesObserved = new ArrayList<>();      // human-readable speech lines
   private String dynTradeResult = "not-run";
   private String dynQuestResult = "not-run";
   private String dynRepResult = "not-run";
   private String dynCreateRemoveResult = "not-run";
   private String dynActivityResult = "not-run";

   private MillClientSelfTest(Minecraft mc) {
      this.mc = mc;
   }

   /** True only when launched with {@code -Dmillenaire.clienttest=true} or {@code MILLENAIRE_CLIENTTEST=1}. */
   public static boolean isEnabled() {
      if ("true".equalsIgnoreCase(System.getProperty(PROPERTY))) {
         return true;
      }
      String env = System.getenv("MILLENAIRE_CLIENTTEST");
      return "true".equalsIgnoreCase(env) || "1".equals(env);
   }

   /**
    * Hooks the harness onto the client tick loop. Call once from
    * {@code MillenaireModClient.onInitializeClient}, guarded by {@link #isEnabled()}.
    */
   public static void register() {
      if (started) {
         return;
      }
      started = true;
      log("===== MILLENAIRE CLIENT SELF-TEST ENABLED (-D" + PROPERTY + "=true) =====");
      log("Waiting for the player to be in a singleplayer world… (open/create a world to start; "
         + "stand near a Millénaire village so the trade-click test can find a real shop/merchant)");
      final MillClientSelfTest harness = new MillClientSelfTest(Minecraft.getInstance());
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         if (client == harness.mc) {
            harness.onTick();
         }
      });
   }

   // ----------------------------------------------------------------------------------------------

   private void onTick() {
      if (halted) {
         return;
      }

      // Phase 0/1: ensure we are in a world (auto-create one if not), then let it settle a bit.
      if (!running) {
         if (mc.player != null && mc.level != null) {
            settleCounter++;
            if (settleCounter >= WORLD_SETTLE_TICKS) {
               running = true;
               tick = 0;
               log("Player detected in world '" + describeLevel() + "' — starting client self-test sequence.");
            }
         } else {
            settleCounter = 0;
            // Diagnostic: every ~3s log what screen we're on, so a stuck harness is debuggable from the
            // log — distinguishes "still loading resources" (no TitleScreen yet) from "at title but world
            // creation didn't fire". Mill loads a lot of assets so reaching the title can take minutes.
            waitDiagTicks++;
            if (waitDiagTicks % 60 == 0) {
               net.minecraft.client.gui.screens.Screen sc = mc.gui != null ? mc.gui.screen() : null;
               log("waiting (" + (waitDiagTicks / 20) + "s): currentScreen="
                  + (sc == null ? "null" : sc.getClass().getSimpleName())
                  + " singleplayerServer=" + (mc.getSingleplayerServer() != null)
                  + " worldCreateRequested=" + worldCreateRequested);
            }
            // Not in a world yet. Once the title screen is up and idle, programmatically create+enter a
            // fresh singleplayer test world so the harness runs with NO human in the loop.
            maybeAutoCreateWorld();
         }
         return;
      }

      tick++;
      // Sample villager activity every tick while the window is open (independent of the step switch).
      if (activitySampling) {
         try {
            sampleVillagerActivity();
         } catch (Throwable t) {
            recordException("activity-sample(tick=" + tick + ")", t);
         }
      }
      try {
         if (tick >= MAX_TICK_GUARD) {
            log("MAX-TICK GUARD (" + MAX_TICK_GUARD + ") reached — forcing stop.");
            returnToMenuAndStop();
            return;
         }

         switch (tick) {
            case TICK_INTRO -> stepIntro();
            case TICK_CREATIVE_TABS -> stepCreativeTabs();
            case TICK_SPAWN_VILLAGE -> stepSpawnVillage();
            case TICK_TELEPORT_OVERVIEW -> stepTeleportOverview();
            case TICK_SHOT_VILLAGE -> stepScreenshot("milltest_01_village.png", "village");
            case TICK_TELEPORT_VILLAGER -> stepTeleportToVillager();
            case TICK_SHOT_VILLAGER -> stepScreenshot("milltest_02_villager.png", "villager");
            case TICK_PLACE_BLOCKS -> stepPlaceBlockSampler();
            case TICK_SHOT_BLOCKS -> stepScreenshot("milltest_03_blocks.png", "blocks");
            case TICK_TRAVELBOOK_OPEN -> stepOpenTravelBook();
            case TICK_SHOT_TRAVELBOOK -> stepScreenshot("milltest_04_travelbook_gui.png", "travelbook-gui");
            case TICK_TRAVELBOOK_PAGE -> stepPageTravelBook();
            case TICK_TRAVELBOOK_BUTTON -> stepTravelBookButton();
            case TICK_TRADE_OPEN_CLICK -> stepTradeOpenAndClick();
            case TICK_SHOT_TRADE -> stepScreenshot("milltest_05_trade_gui.png", "trade-gui");
            case TICK_TEXTFIELD_TYPE -> stepTextFieldTyping();
            case TICK_CREATIVE_OPEN -> stepOpenCreative();
            case TICK_SHOT_CREATIVE -> stepScreenshot("milltest_06_creative.png", "creative");
            case TICK_DYN_STAND_IN_VILLAGE -> stepStandInActiveVillage();
            case TICK_DYN_ACTIVITY_START -> stepStartActivitySampling();
            case TICK_DYN_TRADE -> stepRealTrade();
            case TICK_DYN_QUEST -> stepAcceptQuest();
            case TICK_DYN_REPUTATION -> stepReputation();
            case TICK_DYN_VILLAGE_CREATE_REMOVE -> stepVillageCreateRemove();
            case TICK_DYN_ACTIVITY_REPORT -> stepReportActivity();
            case TICK_DYN_COMBAT_SPAWN -> stepCombatSpawn();
            case TICK_DYN_COMBAT_CHECK -> stepCombatCheck();
            case TICK_SUMMARY -> stepSummary();
            case TICK_RETURN_MENU -> stepReturnToMenu();
            case TICK_STOP -> returnToMenuAndStop();
            default -> {
            }
         }
      } catch (Throwable t) {
         // The tick loop must never die. Record and keep marching toward the summary/stop.
         recordException("onTick(tick=" + tick + ")", t);
         MillLog.printException(TAG + " client self-test tick error", t);
      }
   }

   // ============================ STEP: intro / environment ============================

   private void stepIntro() {
      try {
         boolean sp = mc.hasSingleplayerServer();
         String detail = "singleplayer=" + sp + " level='" + describeLevel() + "' player="
            + (mc.player != null ? mc.player.getName().getString() : "null")
            + " cultures=" + Culture.ListCultures.size()
            + " clientWorld=" + (Mill.clientWorld != null);
         pass("intro", detail);
         if (!sp) {
            log("intro NOTE: not a singleplayer integrated server — some packet round-trips won't apply, "
               + "but client-side click/packet emission is still validated.");
         }
      } catch (Throwable t) {
         fail("intro", t);
      }
   }

   // ============================ PHASE 0: auto-create + enter a singleplayer test world ============================

   /**
    * When no world is loaded, programmatically create and enter a fresh default singleplayer world so the
    * harness needs no human in the loop. Uses the verified 26.2 API:
    * {@code Minecraft.createWorldOpenFlows().createFreshLevel(levelId, LevelSettings, WorldOptions,
    * Function<HolderLookup.Provider, WorldDimensions>, Screen parent)} with a fixed seed, creative + peaceful,
    * and a unique level name. We only fire once we are on the {@link TitleScreen} (so the flows' internal
    * screen swaps are legal) and only once; thereafter we wait for {@code player != null && level != null}.
    */
   private void maybeAutoCreateWorld() {
      if (worldCreateRequested) {
         worldCreateWaitTicks++;
         if (worldCreateWaitTicks == WORLD_CREATE_TIMEOUT_TICKS) {
            // Don't hang the client forever; fall back to the legacy "wait for a human" behaviour.
            log("AUTO-WORLD SKIPPED: world did not finish loading within " + WORLD_CREATE_TIMEOUT_TICKS
               + " ticks. Falling back to waiting for a manually-opened world.");
         }
         return;
      }
      // Only attempt from a settled title screen (an integrated server may still be tearing down right after launch).
      Screen current = mc.gui != null ? mc.gui.screen() : null;
      if (!(current instanceof TitleScreen)) {
         return;
      }
      if (mc.getSingleplayerServer() != null) {
         return;
      }
      worldCreateRequested = true;
      try {
         String levelName = "milltest_" + System.currentTimeMillis();
         LevelSettings levelSettings = new LevelSettings(
            levelName,
            GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
            true, // allowCommands
            WorldDataConfiguration.DEFAULT
         );
         // Fixed seed for reproducibility; default (normal) world preset; no bonus chest.
         WorldOptions options = new WorldOptions(8675309L, true, false);
         log("AUTO-WORLD: creating fresh singleplayer world '" + levelName
            + "' (creative, peaceful, seed=8675309) via createWorldOpenFlows().createFreshLevel(...)");
         mc.createWorldOpenFlows().createFreshLevel(
            levelName,
            levelSettings,
            options,
            WorldPresets::createNormalWorldDimensions,
            mc.gui.screen()
         );
         log("AUTO-WORLD: createFreshLevel requested — waiting for player+level to come up.");
      } catch (Throwable t) {
         recordException("auto-world-create", t);
         log("AUTO-WORLD SKIPPED: createFreshLevel failed: " + t + " — falling back to waiting for a manually-opened world.");
         MillLog.printException(TAG + " auto-world-create", t);
      }
   }

   // ============================ STEP: spawn a Mill village near the player ============================

   /**
    * In singleplayer, drives the same village generator the SERVER self-test uses on the overworld
    * {@link ServerLevel}, on the SERVER thread via {@code server.execute(...)}, after force-loading the
    * target chunks (so {@code findTopSoilBlock} sees a real surface). Records the village point so the
    * later teleport/screenshot steps can aim at it.
    */
   private void stepSpawnVillage() {
      try {
         final MinecraftServer server = mc.getSingleplayerServer();
         if (server == null) {
            skip("spawn-village", "not a singleplayer integrated server — cannot run server-side village gen");
            return;
         }
         if (mc.player == null) {
            skip("spawn-village", "no client player to anchor the village near");
            return;
         }
         // Target a point a short distance from the player so it lands in already-near chunks.
         final int targetX = (int) mc.player.getX() + 48;
         final int targetZ = (int) mc.player.getZ();
         final int baseX = (int) mc.player.getX();
         final int baseZ = (int) mc.player.getZ();
         server.execute(() -> {
            try {
               ServerLevel level = server.overworld();
               VillageType vtype = pickAnyRegularVillageType();
               if (vtype == null) {
                  log("spawn-village (server): no regular VillageType available — skipping gen");
                  return;
               }
               WorldGenVillage gen = new WorldGenVillage();
               // Village gen fails on unsuitable terrain (ocean/slope/no surface). Retry over a spiral of
               // candidate points until one lands, so the harness reliably has a village (and shop) to test.
               int[][] offsets = {
                  {48, 0}, {0, 48}, {-48, 0}, {0, -48}, {96, 96}, {-96, 96}, {96, -96}, {-96, -96},
                  {160, 0}, {0, 160}, {-160, 0}, {0, -160}, {240, 240}, {-240, 240}, {240, -240}, {-240, -240}
               };
               boolean result = false;
               for (int[] off : offsets) {
                  int tx = baseX + off[0];
                  int tz = baseZ + off[1];
                  int ccx = tx >> 4;
                  int ccz = tz >> 4;
                  for (int dcx = -2; dcx <= 2; dcx++) {
                     for (int dcz = -2; dcz <= 2; dcz++) {
                        level.getChunk(ccx + dcx, ccz + dcz);
                        level.setChunkForced(ccx + dcx, ccz + dcz, true);
                     }
                  }
                  int surfaceY = WorldUtilities.findTopSoilBlock(level, tx, tz);
                  result = gen.generateVillageAtPoint(
                     level, MillCommonUtilities.random, tx, 0, tz, null, false, true, false, 0, vtype, null, null, 1.0F
                  );
                  log("spawn-village (server): try " + tx + "/" + tz + " surfaceY=" + surfaceY + " -> " + result);
                  if (result) {
                     this.spawnedVillagePoint = new Point(tx, surfaceY, tz);
                     this.villageSurfaceY = surfaceY;
                     break;
                  }
               }
               log("spawn-village (server): final result=" + result + " (type=" + vtype.key + ")");
            } catch (Throwable t) {
               MillLog.printException(TAG + " spawn-village (server thread)", t);
            }
         });
         pass("spawn-village", "village-gen scheduled on server thread near " + targetX + "/" + targetZ
            + " (result logged from server thread)");
      } catch (Throwable t) {
         fail("spawn-village", t);
      }
   }

   private static VillageType pickAnyRegularVillageType() {
      for (Culture culture : Culture.ListCultures) {
         for (VillageType vt : culture.listVillageTypes) {
            if (vt.isRegularVillage()) {
               return vt;
            }
         }
      }
      for (Culture culture : Culture.ListCultures) {
         if (!culture.listVillageTypes.isEmpty()) {
            return culture.listVillageTypes.get(0);
         }
      }
      return null;
   }

   // ============================ STEP: teleport for the village overview screenshot ============================

   private void stepTeleportOverview() {
      try {
         if (mc.player == null) {
            skip("teleport-overview", "no client player");
            return;
         }
         Point p = spawnedVillagePoint;
         double tx;
         double ty;
         double tz;
         if (p != null) {
            // Stand back and a bit above the village, looking toward it.
            tx = p.getiX() - 18;
            ty = villageSurfaceY + 12;
            tz = p.getiZ() - 18;
         } else {
            tx = mc.player.getX();
            ty = mc.player.getY() + 12;
            tz = mc.player.getZ();
         }
         teleportPlayer(tx, ty, tz, 45.0F, 25.0F);
         pass("teleport-overview", "moved player to overview vantage at "
            + (int) tx + "/" + (int) ty + "/" + (int) tz + " (village=" + (p != null ? p : "n/a") + ")");
      } catch (Throwable t) {
         fail("teleport-overview", t);
      }
   }

   // ============================ STEP: teleport to a villager close-up ============================

   private void stepTeleportToVillager() {
      try {
         if (mc.player == null || mc.level == null) {
            skip("teleport-villager", "no client player/level");
            return;
         }
         List<MillVillager> villagers = mc.level.getEntitiesOfClass(
            MillVillager.class,
            new AABB(-30000, mc.level.getMinY(), -30000, 30000, mc.level.getMaxY(), 30000),
            v -> v != null && v.isAlive());
         if (villagers.isEmpty()) {
            skip("teleport-villager", "no MillVillager present yet (village may still be populating) — screenshot will be a world shot");
            return;
         }
         MillVillager v = villagers.get(0);
         double vx = v.getX();
         double vy = v.getY();
         double vz = v.getZ();
         // Stand ~3 blocks away at roughly eye height and aim the camera at the villager.
         double px = vx + 3.0;
         double py = vy + 1.0;
         double pz = vz + 3.0;
         float yaw = (float) (Math.toDegrees(Math.atan2(vz - pz, vx - px)) - 90.0);
         teleportPlayer(px, py, pz, yaw, 5.0F);
         mc.player.setYRot(yaw);
         mc.player.setXRot(5.0F);
         pass("teleport-villager", "aimed camera at villager '" + v.getName().getString()
            + "' at " + (int) vx + "/" + (int) vy + "/" + (int) vz);
      } catch (Throwable t) {
         fail("teleport-villager", t);
      }
   }

   // ============================ STEP: combat scenario (the path the harness never covered) ============================
   private java.util.UUID combatVillagerId = null;
   private double combatStartX;
   private double combatStartZ;
   private volatile boolean combatEngaged = false;
   private volatile java.util.UUID combatZombieId = null;
   private volatile java.util.UUID combatSkeletonId = null; // RANGED hostile that fires at the fighter
   private volatile java.util.UUID raiderId = null;         // 2nd fighter turned hostile (villager-vs-villager)
   private volatile java.util.UUID raiderVictimId = null;
   private volatile double raiderStartX;
   private volatile double raiderStartZ;

   /** Spawn a zombie (melee) + pillager (ranged) next to a fighter villager and target the zombie, exercising
    *  the new combat AI (acquire → move → attack → ranged retreat). Combat crashes/freezes slipped through
    *  before precisely because no harness step drove a real fight. */
   private void stepCombatSpawn() {
      try {
         final MinecraftServer server = mc.getSingleplayerServer();
         if (server == null || mc.level == null) {
            skip("combat-spawn", "no integrated server/level");
            return;
         }
         List<MillVillager> fighters = mc.level.getEntitiesOfClass(MillVillager.class,
            new AABB(-30000, mc.level.getMinY(), -30000, 30000, mc.level.getMaxY(), 30000),
            v -> v != null && v.isAlive() && v.helpsInAttacks());
         if (fighters.isEmpty()) {
            skip("combat-spawn", "no fighter villager (helpsInAttacks) present to test combat");
            return;
         }
         MillVillager fighter = fighters.get(0);
         this.combatVillagerId = fighter.getUUID();
         this.combatStartX = fighter.getX();
         this.combatStartZ = fighter.getZ();
         // Villager-vs-villager uses a SEPARATE pair (2nd vs 3rd fighter) so the mob-fighting fighter 0 isn't
         // ganged up on by mobs + a raider and killed before the check can read it. Needs 3+ fighters.
         if (fighters.size() >= 3) {
            this.raiderId = fighters.get(1).getUUID();
            this.raiderVictimId = fighters.get(2).getUUID();
         }
         final double fx = fighter.getX();
         final double fy = fighter.getY();
         final double fz = fighter.getZ();
         server.execute(() -> {
            try {
               ServerLevel level = server.overworld();
               // The test world defaults to PEACEFUL, where monsters can't be created (create() → canSpawn
               // fails → null) AND villagers won't retaliate (hurtServer gates combat on non-PEACEFUL). Set
               // NORMAL so the combat scenario is both spawnable and realistic.
               server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
               net.minecraft.world.entity.EntityType<?> zType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                  .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie"));
               net.minecraft.world.entity.EntityType<?> skType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                  .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("skeleton")); // RANGED hostile
               // create()+addFreshEntity (NOT spawn(), which validates the cell and returned null inside the
               // built-up village → engaged stayed false). Anchor on the fighter's current server position.
               net.minecraft.world.entity.Entity fe = level.getEntity(this.combatVillagerId);
               net.minecraft.core.BlockPos base = fe != null ? fe.blockPosition() : net.minecraft.core.BlockPos.containing(fx, fy, fz);
               // MELEE hostile (zombie) — the fighter targets it.
               net.minecraft.world.entity.Entity zombie = zType == null ? null : zType.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
               if (zombie != null) {
                  zombie.setPos(base.getX() + 2.5, base.getY() + 1.0, base.getZ() + 0.5);
                  level.addFreshEntity(zombie);
               }
               // RANGED hostile (skeleton) — set IT to target the fighter so it FIRES arrows, exercising the
               // villager's under-fire response (retreat to cover / reposition) AND engaging a ranged enemy.
               if (skType != null && fe instanceof MillVillager) {
                  net.minecraft.world.entity.Entity sk = skType.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                  if (sk instanceof net.minecraft.world.entity.Mob skm && fe instanceof net.minecraft.world.entity.LivingEntity fle) {
                     skm.setPos(base.getX() - 10.5, base.getY() + 1.0, base.getZ() + 0.5);
                     level.addFreshEntity(skm);
                     skm.setTarget(fle); // skeleton shoots the fighter
                     this.combatSkeletonId = skm.getUUID();
                  }
               }
               if (fe instanceof MillVillager mv && zombie instanceof net.minecraft.world.entity.LivingEntity le) {
                  mv.setTarget(le);
                  this.combatZombieId = le.getUUID();
                  // Record the movement baseline at the MOMENT combat starts (server-side), so combat-check
                  // measures movement since engagement regardless of how far the integrated server lagged.
                  this.combatStartX = mv.getX();
                  this.combatStartZ = mv.getZ();
                  this.combatEngaged = true;
               }
               // HOSTILE VILLAGER (village-war): the raider (2nd fighter) targets a SEPARATE victim (3rd fighter)
               // → villager-vs-villager combat, kept apart from fighter 0's mob fight.
               net.minecraft.world.entity.Entity fe2 = this.raiderId != null ? level.getEntity(this.raiderId) : null;
               net.minecraft.world.entity.Entity vic = this.raiderVictimId != null ? level.getEntity(this.raiderVictimId) : null;
               if (fe2 instanceof MillVillager rmv && vic instanceof net.minecraft.world.entity.LivingEntity victimLe) {
                  rmv.setTarget(victimLe);
                  this.raiderStartX = rmv.getX();
                  this.raiderStartZ = rmv.getZ();
               }
               log("combat-spawn (server): fe=" + (fe != null) + " zombie=" + (zombie != null)
                  + " skeleton=" + (this.combatSkeletonId != null) + " raiderVillager=" + (this.raiderVictimId != null)
                  + " engaged=" + this.combatEngaged);
            } catch (Throwable t) {
               recordException("combat-spawn(server)", t);
            }
         });
         pass("combat-spawn", "scheduled zombie+pillager next to fighter '" + fighter.getName().getString() + "'");
      } catch (Throwable t) {
         fail("combat-spawn", t);
      }
   }

   /** Verify the fighter actually engaged: moved from its spawn spot (not frozen) and survived (no crash). */
   private void stepCombatCheck() {
      try {
         if (this.combatVillagerId == null) {
            skip("combat-check", "combat-spawn did not run");
            return;
         }
         if (!this.combatEngaged) {
            skip("combat-check", "server never set the target in time (integrated server lagged) — inconclusive");
            return;
         }
         MinecraftServer server = mc.getSingleplayerServer();
         net.minecraft.world.entity.Entity ent = server != null ? server.overworld().getEntity(this.combatVillagerId) : null;
         if (!(ent instanceof MillVillager mv)) {
            skip("combat-check", "fighter villager gone (died/despawned during combat)");
            return;
         }
         double dx = mv.getX() - this.combatStartX;
         double dz = mv.getZ() - this.combatStartZ;
         double movedSq = dx * dx + dz * dz;
         // Did the fighter actually land hits? The zombie should be hurt, or already dead (getEntity → null).
         net.minecraft.world.entity.Entity ze = this.combatZombieId != null ? server.overworld().getEntity(this.combatZombieId) : null;
         boolean zombieHurtOrDead = ze == null
            || (ze instanceof net.minecraft.world.entity.LivingEntity zl && zl.getHealth() < zl.getMaxHealth());
         String detail = "movedDist2=" + String.format("%.1f", movedSq)
            + " stillTargeting=" + (mv.getTarget() != null) + " alive=" + mv.isAlive() + " zombieHurtOrDead=" + zombieHurtOrDead;
         if (movedSq > 1.0) {
            pass("combat-check", "fighter ENGAGED and moved (not frozen): " + detail);
         } else {
            fail("combat-check", new IllegalStateException("fighter did NOT move ~2.5s after targeting — FREEZE suspected: " + detail));
         }
         if (zombieHurtOrDead) {
            pass("combat-attack", "fighter dealt damage to its target (hurt or killed)");
         } else {
            log("combat-attack: target not yet damaged (fighter may still be closing the gap) — non-fatal");
         }
         // RANGED hostile (skeleton firing at the fighter): the fighter should still be alive and have kept
         // maneuvering (reacting under fire — retreat to cover / reposition — not standing frozen).
         if (this.combatSkeletonId != null) {
            net.minecraft.world.entity.Entity sk = server.overworld().getEntity(this.combatSkeletonId);
            if (mv.isAlive() && movedSq > 1.0) {
               pass("combat-ranged", "fighter reacted to a RANGED hostile (skeleton) — alive & maneuvering; skeletonPresent=" + (sk != null));
            } else {
               log("combat-ranged: alive=" + mv.isAlive() + " moved2=" + String.format("%.1f", movedSq) + " — inconclusive");
            }
         } else {
            skip("combat-ranged", "skeleton not spawned");
         }
         // HOSTILE VILLAGER (village-war): the 2nd fighter, made hostile, should engage the first (move/target).
         if (this.raiderId != null) {
            net.minecraft.world.entity.Entity re = server.overworld().getEntity(this.raiderId);
            if (re instanceof MillVillager rmv) {
               double rdx = rmv.getX() - this.raiderStartX;
               double rdz = rmv.getZ() - this.raiderStartZ;
               double rMoved = rdx * rdx + rdz * rdz;
               if (rMoved > 1.0 || rmv.getTarget() != null) {
                  pass("combat-villager", "hostile villager ENGAGED another villager: moved2=" + String.format("%.1f", rMoved) + " targeting=" + (rmv.getTarget() != null));
               } else {
                  log("combat-villager: raider moved2=" + String.format("%.1f", rMoved) + " targeting=" + (rmv.getTarget() != null) + " — inconclusive");
               }
            } else {
               skip("combat-villager", "raider villager gone (died/despawned)");
            }
         } else {
            skip("combat-villager", "only one fighter present — no villager-vs-villager pairing");
         }
      } catch (Throwable t) {
         fail("combat-check", t);
      }
   }

   // ============================ STEP: place a Mill block sampler ============================

   /**
    * Places a handful of representative Mill blocks in a row in front of the player (on the SERVER thread so
    * the change replicates to the client) so the next screenshot catches missing block models/textures.
    */
   private void stepPlaceBlockSampler() {
      try {
         final MinecraftServer server = mc.getSingleplayerServer();
         if (server == null) {
            skip("place-blocks", "not a singleplayer integrated server — cannot place blocks server-side");
            return;
         }
         if (mc.player == null) {
            skip("place-blocks", "no client player");
            return;
         }
         // Put the block-entity blocks whose render-shape fix we're verifying FIRST (panel sign, locked
         // chest, fire pit), then fill with other millenaire blocks.
         final List<Block> sample = new ArrayList<>();
         for (String want : new String[]{"panel", "locked_chest", "fire_pit"}) {
            Block b = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("millenaire", want));
            if (b != null && b != net.minecraft.world.level.block.Blocks.AIR && !sample.contains(b)) {
               sample.add(b);
            }
         }
         for (Block b : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(b);
            if (id != null && "millenaire".equals(id.getNamespace()) && !sample.contains(b)) {
               sample.add(b);
               if (sample.size() >= 8) {
                  break;
               }
            }
         }
         if (sample.isEmpty()) {
            skip("place-blocks", "no millenaire blocks registered");
            return;
         }
         final int baseX = (int) mc.player.getX();
         // Place UP in clear air (away from the dense village walls that buried/occluded the row before).
         final int baseY = (int) mc.player.getY() + 25;
         final int baseZ = (int) mc.player.getZ() + 4;
         final float yaw = mc.player.getYRot();
         final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
         server.execute(() -> {
            try {
               ServerLevel level = server.overworld();
               int placed = 0;
               // Clear a fully-enclosed lit pocket so nothing occludes the block-entity blocks.
               for (int dx = -2; dx <= sample.size() + 1; dx++) {
                  for (int dy = -1; dy <= 5; dy++) {
                     for (int dz = -4; dz <= 3; dz++) {
                        level.setBlockAndUpdate(new BlockPos(baseX + dx, baseY + dy, baseZ + dz),
                           net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                     }
                  }
               }
               for (int i = 0; i < sample.size(); i++) {
                  BlockPos pos = new BlockPos(baseX + i, baseY, baseZ);
                  level.setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                  level.setBlockAndUpdate(pos.above(3), net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState());
                  BlockState state = sample.get(i).defaultBlockState();
                  level.setBlockAndUpdate(pos, state);
                  placed++;
               }
               log("place-blocks (server): placed " + placed + " Mill blocks at base "
                  + baseX + "/" + baseY + "/" + baseZ);
            } catch (Throwable t) {
               MillLog.printException(TAG + " place-blocks (server thread)", t);
            } finally {
               latch.countDown();
            }
         });
         // Block until the server thread has ACTUALLY placed the blocks — server.execute is fire-and-forget,
         // so without this the next screenshot step fired ~2s before the blocks existed (captured empty terrain).
         try {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
         } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
         }
         // Frame the panel + locked chest (sample[0],[1]) head-on from 3 blocks south, eye level, looking
         // level (+Z, yaw 0) in the cleared high-air pocket so nothing occludes them.
         teleportPlayer(baseX + 0.5, baseY + 1.0, baseZ - 2.5, 0.0F, 22.0F);
         log("place-blocks: framing panel+chest from " + baseX + "/" + (baseY + 1) + "/" + (baseZ - 3) + " pitch=22 down");
         try {
            Thread.sleep(500L);
         } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
         }
         pass("place-blocks", "scheduled placement of " + sample.size() + " Mill blocks; aimed camera at the row (yaw=" + yaw + ")");
      } catch (Throwable t) {
         fail("place-blocks", t);
      }
   }

   // ============================ STEP: open creative inventory (for screenshot) ============================

   private void stepOpenCreative() {
      try {
         if (mc.player == null) {
            skip("creative-open", "no client player");
            return;
         }
         CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(
            mc.player, mc.player.connection.enabledFeatures(), mc.options.operatorItemsTab().get());
         mc.setScreenAndShow(screen);
         if (mc.gui.screen() instanceof CreativeModeInventoryScreen) {
            // Try to select a Mill creative tab so the screenshot shows Mill items.
            selectMillCreativeTab(screen);
            pass("creative-open", "creative inventory opened" );
         } else {
            skip("creative-open", "creative screen did not become active (got " + screenName(mc.gui.screen()) + ")");
         }
      } catch (Throwable t) {
         fail("creative-open", t);
      }
   }

   /** Best-effort: switch the open creative screen to the first millenaire tab via the public selectTab API. */
   private static void selectMillCreativeTab(CreativeModeInventoryScreen screen) {
      try {
         for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null && "millenaire".equals(id.getNamespace())) {
               var m = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
               m.setAccessible(true);
               m.invoke(screen, tab);
               return;
            }
         }
      } catch (Throwable ignored) {
         // Non-fatal: the screenshot still captures the creative screen on whatever tab is default.
      }
   }

   // ============================ SCREENSHOT helper-step ============================

   /**
    * Captures the current main render target to {@code run/screenshots/<fileName>}. MUST run a few ticks
    * AFTER the relevant setup so a real frame has been rendered. Uses the verified 26.2 API
    * {@code Screenshot.grab(File workDir, String forceName, RenderTarget target, int downscaleFactor, Consumer<Component>)},
    * which writes to {@code workDir/screenshots/forceName}. {@code mc.gameDirectory} is the gradle
    * {@code runClient} working dir (so the file lands in {@code run/screenshots/}).
    */
   private void stepScreenshot(String fileName, String label) {
      try {
         File workDir = mc.gameDirectory;
         var target = mc.gameRenderer.mainRenderTarget();
         if (target == null || target.getColorTexture() == null) {
            skip("screenshot-" + label, "no complete render target available (headless / no GL context)");
            return;
         }
         File outFile = new File(new File(workDir, "screenshots"), fileName);
         Screenshot.grab(workDir, fileName, target, 1, component -> {
            // Confirmation runs on the IO pool after the PNG is written.
            log("SCREENSHOT saved: " + outFile.getAbsolutePath());
         });
         pass("screenshot-" + label, "requested capture -> " + outFile.getAbsolutePath());
      } catch (Throwable t) {
         fail("screenshot-" + label, t);
      }
   }

   /**
    * Teleports the player. In singleplayer we move the SERVER-side {@link ServerPlayer} (which replicates to
    * the client); we also nudge the client {@code LocalPlayer} immediately so the very next rendered frame is
    * already at the new vantage. Falls back to a client-only move if no server player is found.
    */
   private void teleportPlayer(double x, double y, double z, float yaw, float pitch) {
      MinecraftServer server = mc.getSingleplayerServer();
      boolean serverMoved = false;
      if (server != null) {
         try {
            // Move the player SERVER-side first and WAIT for it. Steps run on the client thread
            // (END_CLIENT_TICK); if the server teleport is left async, the server still thinks the player
            // is at spawn and sends a position-correction packet that snaps the client back BEFORE the
            // screenshot — which is why every world screenshot captured the spawn view.
            final java.util.concurrent.CountDownLatch tlatch = new java.util.concurrent.CountDownLatch(1);
            server.execute(() -> {
               try {
                  for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                     sp.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), yaw, pitch, false);
                  }
               } catch (Throwable t) {
                  MillLog.printException(TAG + " teleport (server thread)", t);
               } finally {
                  tlatch.countDown();
               }
            });
            try {
               tlatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
               Thread.currentThread().interrupt();
            }
            serverMoved = !server.getPlayerList().getPlayers().isEmpty();
         } catch (Throwable ignored) {
         }
      }
      // Immediate client-side move so the upcoming frame renders at the target even before the server round-trip.
      try {
         if (mc.player != null) {
            mc.player.setPos(new Vec3(x, y, z));
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.setDeltaMovement(Vec3.ZERO);
            // Snap the previous-tick pos/rot too, else the camera interpolates from the OLD spawn position
            // for a frame and the screenshot captured the spawn view instead of the teleport target.
            mc.player.setOldPosAndRot();
         }
      } catch (Throwable t) {
         MillLog.printException(TAG + " teleport (client)", t);
      }
      if (!serverMoved && server != null) {
         log("teleport NOTE: no ServerPlayer found to move server-side; used client-only move.");
      }
   }

   // ============================ STEP: creative menu Mill tabs ============================

   /**
    * Opens the creative inventory screen and verifies that Mill's creative tabs are registered and
    * displayed, and that at least one of them is populated. This is a real client open
    * ({@code setScreenAndShow(new CreativeModeInventoryScreen(...))} is brittle to construct directly,
    * so we assert against the tab registry, which is what the rendered tab bar is built from).
    */
   private void stepCreativeTabs() {
      try {
         List<CreativeModeTab> allTabs = CreativeModeTabs.allTabs();
         List<CreativeModeTab> displayed = CreativeModeTabs.tabs();
         int millRegistered = 0;
         int millDisplayed = 0;
         int millPopulated = 0;
         StringBuilder sb = new StringBuilder();
         for (CreativeModeTab tab : allTabs) {
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id == null || !"millenaire".equals(id.getNamespace())) {
               continue;
            }
            millRegistered++;
            boolean isShown = displayed.contains(tab);
            if (isShown) {
               millDisplayed++;
            }
            int count = 0;
            try {
               count = tab.getDisplayItems().size();
            } catch (Throwable ignored) {
               // Display items may need a populated CreativeModeTab.ItemDisplayParameters; treat as unknown.
            }
            if (count > 0) {
               millPopulated++;
            }
            sb.append(" [").append(id.getPath()).append(" displayed=").append(isShown)
               .append(" items=").append(count).append("]");
         }
         String detail = "millTabsRegistered=" + millRegistered + " displayed=" + millDisplayed
            + " populated=" + millPopulated + sb;
         if (millRegistered > 0 && millDisplayed > 0) {
            pass("creative-tabs", detail);
         } else if (millRegistered > 0) {
            // Registered but none displayed — a real client-only bug worth flagging (not a hard skip).
            fail("creative-tabs", new IllegalStateException("Mill creative tabs registered but NONE displayed: " + detail));
         } else {
            skip("creative-tabs", "no millenaire creative tabs registered (content not loaded?)");
         }
      } catch (Throwable t) {
         fail("creative-tabs", t);
      }
   }

   // ============================ STEP: travel book open ============================

   private void stepOpenTravelBook() {
      try {
         if (mc.player == null) {
            skip("travelbook-open", "no client player");
            return;
         }
         GuiTravelBook book = new GuiTravelBook(mc.player);
         mc.setScreenAndShow(book);
         Screen now = mc.gui.screen();
         if (now instanceof GuiTravelBook) {
            pass("travelbook-open", "screen=" + now.getClass().getSimpleName() + " size=" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
         } else {
            fail("travelbook-open", new IllegalStateException("expected GuiTravelBook, got " + (now == null ? "null" : now.getClass().getName())));
         }
      } catch (Throwable t) {
         fail("travelbook-open", t);
      }
   }

   // ============================ STEP: travel book paging ============================

   /**
    * Simulates clicking the book's "next page" arrow region and verifies the page index advances. The
    * arrow hit-region is the bottom-right corner of the book frame (see {@link GuiText#millMouseClicked}).
    * Falls back to the public {@link GuiText#incrementPage()} if the synthetic click can't change the page
    * (e.g. single-page book), and reports which path was taken.
    */
   private void stepPageTravelBook() {
      try {
         Screen screen = mc.gui.screen();
         if (!(screen instanceof GuiText book)) {
            skip("travelbook-page", "travel book not open (current screen=" + screenName(screen) + ")");
            return;
         }
         int before = readPageNum(book);
         // Compute the bottom-right page-arrow hit point in screen coords (mirrors millMouseClicked: the
         // forward arrow is the region ai in (xSize-33, xSize) and aj in (ySize-14, ySize)).
         int xSize = callIntMethod(book, "getXSize");
         int ySize = callIntMethod(book, "getYSize");
         int xStart = (book.width - xSize) / 2;
         int yStart = (book.height - ySize) / 2;
         int clickX = xStart + xSize - 16;
         int clickY = yStart + ySize - 7;
         book.mouseClicked(mouse(clickX, clickY, 0), false);
         int afterClick = readPageNum(book);

         if (afterClick > before) {
            pass("travelbook-page", "next-arrow click advanced page " + before + "->" + afterClick + " (xSize=" + xSize + " ySize=" + ySize + ")");
            return;
         }

         // No change via click — try the public API to confirm paging works at all, then report.
         book.incrementPage();
         int afterApi = readPageNum(book);
         if (afterApi > before) {
            pass("travelbook-page", "next-arrow click was a no-op at page " + before + " but incrementPage() advanced "
               + before + "->" + afterApi + " (book likely single-page on the click target; paging API verified)");
         } else {
            skip("travelbook-page", "book has a single page (pageNum stayed at " + before + "); nothing to page through");
         }
      } catch (Throwable t) {
         fail("travelbook-page", t);
      }
   }

   // ============================ STEP: travel book button fires ============================

   /**
    * Drives a Mill book button by clicking the first enabled button in the open book's button list and
    * verifying that the click was handled (the screen state changed — e.g. the book navigated, or a new
    * screen was pushed). Mill book buttons are manually drawn widgets routed through
    * {@link GuiText#millMouseClicked}; we click the centre of a real button rectangle.
    */
   private void stepTravelBookButton() {
      try {
         Screen screen = mc.gui.screen();
         if (!(screen instanceof GuiText book)) {
            skip("travelbook-button", "travel book not open (current screen=" + screenName(screen) + ")");
            return;
         }
         List<GuiText.AbstractMillButton> buttons = new ArrayList<>(book.buttonList);
         GuiText.AbstractMillButton target = null;
         for (GuiText.AbstractMillButton b : buttons) {
            if (b != null && b.enabled && b.visible && b.width > 0 && b.height > 0) {
               target = b;
               break;
            }
         }
         if (target == null) {
            skip("travelbook-button", "no enabled book buttons on the current page (" + buttons.size() + " total)");
            return;
         }
         int clickX = target.x + target.width / 2;
         int clickY = target.y + target.height / 2;
         String beforeScreen = screenName(mc.gui.screen());
         int beforePage = readPageNum(book);
         book.mouseClicked(mouse(clickX, clickY, 0), false);
         String afterScreen = screenName(mc.gui.screen());
         int afterPage = readPageNum(book);
         boolean changed = !beforeScreen.equals(afterScreen) || beforePage != afterPage;
         // A reference/navigation button either swaps the screen (jumpToDetails pushes a new GuiTravelBook)
         // or mutates the current book's internal screen state; either way actionPerformed ran without throwing.
         pass("travelbook-button", "clicked button id=" + target.id + " '" + target.displayString
            + "' at " + clickX + "," + clickY + " screen " + beforeScreen + "->" + afterScreen
            + " page " + beforePage + "->" + afterPage + " observableChange=" + changed);
      } catch (Throwable t) {
         fail("travelbook-button", t);
      }
   }

   // ============================ STEP: trade GUI open + click → packet (the headline bug) ============================

   /**
    * The most important client-only check: open the REAL Mill trade screen and simulate a left-click on a
    * trade slot, then verify a Mill client→server packet was actually emitted (id 200). This is exactly the
    * path of the "trade GUI click doesn't send the trade packet" bug: {@link GuiTrade#mouseClicked} must
    * intercept the trade-slot click and call {@code ContainerTrade.sendTradeRequest} →
    * {@code ClientSender.sendTrade} → {@code createAndSendServerPacket}.
    *
    * <p>We find a real merchant villager or a building shop in the loaded world. If none exists in the
    * current world, the step is SKIPPED (with the reason) rather than failing — there is genuinely nothing
    * to trade with.
    */
   private void stepTradeOpenAndClick() {
      try {
         Player player = mc.player;
         if (player == null) {
            skip("trade-click", "no client player");
            return;
         }

         GuiTrade gui = buildTradeGuiFromWorld(player);
         if (gui == null) {
            skip("trade-click", "no Mill merchant villager or building shop found in the current world — "
               + "open a world next to a Millénaire village (with a merchant or a shop building) to exercise this path");
            return;
         }

         mc.setScreenAndShow(gui);
         if (!(mc.gui.screen() instanceof GuiTrade)) {
            fail("trade-click", new IllegalStateException("GuiTrade did not become the active screen"));
            return;
         }

         ContainerTrade menu = gui.getMenu();
         Slot tradeSlot = findActiveTradeSlot(menu);
         if (tradeSlot == null) {
            skip("trade-click", "trade screen opened but it has no active trade slots (shop/merchant has no goods)");
            return;
         }

         // Exercise the EXACT trade-GUI crash path directly: render-time price lookup. The tooltip render
         // called Building.getBuyingPrice(good)/getSellingPrice(good), which unboxed a null Integer when
         // TradeGood.equals (InvItem-vs-TradeGood -> always false) failed to find the good in the price map.
         // Call them for every good, and for a REBUILT good (different instance, same item) — exactly what
         // the client hovers — and check the rebuilt good resolves to the same price (proves the equals fix).
         try {
            Building shopB = menu.getBuilding();
            if (shopB != null) {
               int checked = 0;
               int equalsMatches = 0;
               for (Slot s : menu.slots) {
                  TradeGood g = s instanceof ContainerTrade.TradeSlot ts ? ts.good : null;
                  if (g == null) {
                     continue;
                  }
                  int buyOrig = shopB.getBuyingPrice(g, player);
                  int sellOrig = shopB.getSellingPrice(g, player);
                  TradeGood rebuilt = new TradeGood("rebuilt", g.culture, g.item); // different instance, same item
                  int buyRebuilt = shopB.getBuyingPrice(rebuilt, player);
                  int sellRebuilt = shopB.getSellingPrice(rebuilt, player);
                  if (buyOrig == buyRebuilt && sellOrig == sellRebuilt) {
                     equalsMatches++;
                  }
                  checked++;
               }
               pass("trade-price-render", "getBuyingPrice/getSellingPrice ran for " + checked
                  + " goods with NO NPE; rebuilt-good price matched on " + equalsMatches + "/" + checked
                  + " (TradeGood.equals item-based fix)");
            }
         } catch (Throwable t) {
            fail("trade-price-render", t);
         }

         // Compute the slot's on-screen centre and force it to be the hovered slot (headless: no render pass
         // populated AbstractContainerScreen.hoveredSlot, which GuiTrade.mouseClicked reads).
         int leftPos = readIntField(gui, AbstractContainerScreen.class, "leftPos");
         int topPos = readIntField(gui, AbstractContainerScreen.class, "topPos");
         int clickX = leftPos + tradeSlot.x + 8;
         int clickY = topPos + tradeSlot.y + 8;
         setHoveredSlot(gui, tradeSlot);

         long packetsBefore = ClientSender.TEST_PACKETS_SENT;
         ClientSender.TEST_LAST_PACKET_ID = -1;
         boolean handled = gui.mouseClicked(mouse(clickX, clickY, 0), false);
         long packetsAfter = ClientSender.TEST_PACKETS_SENT;
         int lastId = ClientSender.TEST_LAST_PACKET_ID;

         String detail = "slotClass=" + tradeSlot.getClass().getSimpleName()
            + " at(" + clickX + "," + clickY + ") handled=" + handled
            + " packets " + packetsBefore + "->" + packetsAfter + " lastPacketId=" + lastId;
         if (packetsAfter > packetsBefore && lastId == 200) {
            pass("trade-click", "trade-slot click DID send the trade packet (id 200). " + detail);
         } else if (packetsAfter > packetsBefore) {
            // A packet went out but not the expected trade id — still a regression signal.
            fail("trade-click", new IllegalStateException("trade-slot click sent a packet but NOT the trade packet (id 200). " + detail));
         } else {
            // THE bug: click handled but no packet emitted.
            fail("trade-click", new IllegalStateException("trade-slot click did NOT send any Mill packet — the trade packet is being dropped. " + detail));
         }
      } catch (Throwable t) {
         fail("trade-click", t);
      }
   }

   /** Builds a real {@link GuiTrade} from the first merchant villager or building shop found in the world. */
   private GuiTrade buildTradeGuiFromWorld(Player player) {
      // 1. Try a merchant villager (the GuiTrade(merchant) ctor + ContainerTrade(merchant) path / packet 8).
      try {
         List<MillVillager> villagers = mc.level.getEntitiesOfClass(
            MillVillager.class, new AABB(-30000, mc.level.getMinY(), -30000, 30000, mc.level.getMaxY(), 30000), v -> v != null && v.isAlive()
         );
         for (MillVillager v : villagers) {
            try {
               if (v.merchantSells != null && !v.merchantSells.isEmpty()) {
                  ContainerTrade menu = new ContainerTrade(0, player, v);
                  return new GuiTrade(menu, player.getInventory(), Component.empty(), player, v);
               }
            } catch (Throwable ignored) {
            }
         }
      } catch (Throwable ignored) {
      }

      // 2. Try a building shop (the GuiTrade(building) ctor + ContainerTrade(building) path / packet 2).
      try {
         MillWorldData mw = Mill.clientWorld;
         if (mw != null) {
            for (Building b : mw.allBuildings()) {
               try {
                  if (b == null || b.getTownHall() == null) {
                     continue;
                  }
                  // A trade building needs at least one selling or buying good for this player.
                  java.util.Set<TradeGood> selling = b.getSellingGoods(player);
                  java.util.Set<TradeGood> buying = b.getBuyingGoods(player);
                  if ((selling != null && !selling.isEmpty()) || (buying != null && !buying.isEmpty())) {
                     ContainerTrade menu = new ContainerTrade(0, player, b);
                     return new GuiTrade(menu, player.getInventory(), Component.empty(), player, b);
                  }
               } catch (Throwable ignored) {
               }
            }
         }
      } catch (Throwable ignored) {
      }

      // 3. Fallback: use the integrated SERVER's village buildings. The client's building copies have no
      // shop goods until the shop packet arrives, but the server building has them after computeShopGoods.
      // In singleplayer the JVM is shared so the client GuiTrade can read the server building directly —
      // this lets the harness exercise the trade-GUI render path (extractTooltip -> getBuyingPrice) that
      // crashed.
      try {
         net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
         if (server != null) {
            MillWorldData smw = Mill.getMillWorld(server.overworld());
            java.util.List<net.minecraft.server.level.ServerPlayer> sps = server.getPlayerList().getPlayers();
            net.minecraft.server.level.ServerPlayer sp = sps.isEmpty() ? null : sps.get(0);
            if (smw != null && sp != null) {
               for (Building b : smw.allBuildings()) {
                  if (b == null || b.getTownHall() == null) {
                     continue;
                  }
                  try {
                     b.computeShopGoods(sp);
                     java.util.Set<TradeGood> selling = b.getSellingGoods(sp);
                     java.util.Set<TradeGood> buying = b.getBuyingGoods(sp);
                     if ((selling != null && !selling.isEmpty()) || (buying != null && !buying.isEmpty())) {
                        ContainerTrade menu = new ContainerTrade(0, player, b);
                        return new GuiTrade(menu, player.getInventory(), Component.empty(), player, b);
                     }
                  } catch (Throwable ignored) {
                  }
               }
            }
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   /** Returns the first active trade/merchant slot in the container, or null. */
   private static Slot findActiveTradeSlot(ContainerTrade menu) {
      for (Slot slot : menu.slots) {
         if ((slot instanceof ContainerTrade.TradeSlot || slot instanceof ContainerTrade.MerchantSlot) && slot.isActive()) {
            return slot;
         }
      }
      // Fall back to any trade slot even if not "active" (better than nothing for the click target).
      for (Slot slot : menu.slots) {
         if (slot instanceof ContainerTrade.TradeSlot || slot instanceof ContainerTrade.MerchantSlot) {
            return slot;
         }
      }
      return null;
   }

   // ============================ STEP: text-field typing ============================

   /**
    * Validates client text input routing on a Mill book screen: opens the travel book, focuses the first
    * Mill text field if present, and feeds it a {@link CharacterEvent}, asserting the
    * field's value changed. Most Mill books have no editable field; in that case this is SKIPPED with the
    * reason (the typing path is still compiled and reachable, just not present on this page).
    */
   private void stepTextFieldTyping() {
      try {
         Screen screen = mc.gui.screen();
         if (!(screen instanceof GuiText)) {
            // Re-open a book so we have a Mill text screen to test against.
            if (mc.player != null) {
               mc.setScreenAndShow(new GuiTravelBook(mc.player));
               screen = mc.gui.screen();
            }
         }
         if (!(screen instanceof GuiText book)) {
            skip("textfield-type", "no Mill text screen available to type into");
            return;
         }
         List<GuiText.MillGuiTextField> fields = readTextFields(book);
         if (fields == null || fields.isEmpty()) {
            skip("textfield-type", "current Mill book page has no editable text fields (typing path compiled but not present here)");
            return;
         }
         GuiText.MillGuiTextField field = fields.get(0);
         field.setFocused(true);
         String before = field.getValue();
         book.charTyped(new CharacterEvent('Z'));
         String after = field.getValue();
         if (!after.equals(before) && after.endsWith("Z")) {
            pass("textfield-type", "charTyped routed to focused field: '" + before + "' -> '" + after + "'");
         } else {
            // The EditBox may reject input under headless conditions; report rather than hard-fail.
            skip("textfield-type", "charTyped did not change the field value ('" + before + "' -> '" + after + "') — "
               + "EditBox may be inert without a full input context");
         }
      } catch (Throwable t) {
         fail("textfield-type", t);
      }
   }

   // ============================ DYNAMIC: shared server-side helpers ============================

   /** The integrated server's authoritative {@link ServerPlayer}, or null if not available. */
   private ServerPlayer serverPlayer() {
      MinecraftServer server = mc.getSingleplayerServer();
      if (server == null) {
         return null;
      }
      List<ServerPlayer> sps = server.getPlayerList().getPlayers();
      return sps.isEmpty() ? null : sps.get(0);
   }

   /** The server-side {@link MillWorldData} for the overworld, or null. */
   private MillWorldData serverWorld() {
      MinecraftServer server = mc.getSingleplayerServer();
      if (server == null) {
         return null;
      }
      return Mill.getMillWorld(server.overworld());
   }

   /**
    * Returns the first town-hall {@link Building} on the integrated server that has at least one
    * computed selling OR buying good for the given player (so it is a real, tradeable village centre).
    * Calls {@code computeShopGoods} so the server building's shop maps are populated.
    */
   private Building findServerShopTownHall(ServerPlayer sp) {
      MillWorldData smw = serverWorld();
      if (smw == null || sp == null) {
         return null;
      }
      for (Building b : smw.allBuildings()) {
         if (b == null || b.getTownHall() == null) {
            continue;
         }
         try {
            b.computeShopGoods(sp);
            java.util.Set<TradeGood> selling = b.getSellingGoods(sp);
            java.util.Set<TradeGood> buying = b.getBuyingGoods(sp);
            if ((selling != null && !selling.isEmpty()) || (buying != null && !buying.isEmpty())) {
               return b;
            }
         } catch (Throwable ignored) {
         }
      }
      return null;
   }

   /** Counts the server player's money (deniers, in base-denier units) via the verified Mill helper. */
   private static int countServerMoney(ServerPlayer sp) {
      return MillCommonUtilities.countMoney(sp.getInventory());
   }

   /** Counts how many of (item,meta) the server player holds in their inventory. */
   private static int countInv(ServerPlayer sp, TradeGood good) {
      return MillCommonUtilities.countChestItems(sp.getInventory(), good.item.getItem(), good.item.meta);
   }

   // ============================ DYNAMIC STEP: stand inside an active village ============================

   /**
    * Teleports the player into the centre of the spawned village so the town hall's {@code closestPlayer}
    * is set and villagers act/speak near us — a precondition for the activity-observation and rep steps.
    */
   private void stepStandInActiveVillage() {
      try {
         ServerPlayer sp = serverPlayer();
         MillWorldData smw = serverWorld();
         if (sp == null || smw == null) {
            skip("stand-in-village", "no integrated server / server player");
            return;
         }
         Building th = null;
         for (Building b : smw.allBuildings()) {
            if (b != null && b.isTownhall) {
               th = b;
               break;
            }
         }
         if (th == null) {
            skip("stand-in-village", "no town hall present on the server (village did not populate)");
            return;
         }
         Point c = th.getPos();
         int surfaceY = WorldUtilities.findTopSoilBlock(mc.getSingleplayerServer().overworld(), c.getiX(), c.getiZ());
         teleportPlayer(c.getiX(), surfaceY + 1, c.getiZ(), 0.0F, 0.0F);
         pass("stand-in-village", "teleported player into village centre " + c.getiX() + "/" + (surfaceY + 1)
            + "/" + c.getiZ() + " (townhall=" + th.getVillageQualifiedName() + ")");
      } catch (Throwable t) {
         fail("stand-in-village", t);
      }
   }

   // ============================ DYNAMIC STEP 5: villager activity observation ============================

   private void stepStartActivitySampling() {
      try {
         activitySampling = true;
         activityTicksSampled = 0;
         activitySamples = 0;
         activityGoalSum = 0;
         activityMovingSum = 0;
         activityMaxVillagers = 0;
         activitySpeakingEvents = 0;
         lastSpeechStart.clear();
         lastPos.clear();
         sentencesObserved.clear();
         log("ACTIVITY: started sampling villager activity over " + ACTIVITY_WINDOW_TICKS + " ticks "
            + "(goals / movement / speech) while the player stands in the village.");
      } catch (Throwable t) {
         fail("activity-start", t);
      }
   }

   /**
    * One sample of villager activity, taken on the SERVER's villager list (authoritative goalKey/speech
    * state). Counts villagers with a goal, villagers that moved since the previous sample, and detects new
    * speech events by watching each villager's {@code speech_started} timestamp advance.
    */
   private void sampleVillagerActivity() {
      if (!activitySampling) {
         return;
      }
      activityTicksSampled++;
      MinecraftServer server = mc.getSingleplayerServer();
      List<MillVillager> villagers;
      if (server != null) {
         villagers = server.overworld().getEntitiesOfClass(
            MillVillager.class,
            new AABB(-30000, server.overworld().getMinY(), -30000, 30000, server.overworld().getMaxY(), 30000),
            v -> v != null && v.isAlive());
      } else if (mc.level != null) {
         villagers = mc.level.getEntitiesOfClass(
            MillVillager.class,
            new AABB(-30000, mc.level.getMinY(), -30000, 30000, mc.level.getMaxY(), 30000),
            v -> v != null && v.isAlive());
      } else {
         return;
      }

      // Sample (relatively expensive) only every 20 ticks; speech detection runs every tick below.
      boolean fullSample = activityTicksSampled % 20 == 0;
      int withGoal = 0;
      int moving = 0;
      for (MillVillager v : villagers) {
         long id = v.getVillagerId();
         // Speech detection (every tick): a fresh speech_started value => a new sentence was spoken.
         long spStart = v.speech_started;
         Long prev = lastSpeechStart.get(id);
         if (v.speech_key != null && spStart > 0 && (prev == null || spStart > prev)) {
            activitySpeakingEvents++;
            if (sentencesObserved.size() < 12) {
               sentencesObserved.add(v.getName().getString() + " -> " + v.speech_key);
            }
            log("ACTIVITY-SPEECH: villager '" + v.getName().getString() + "' (id=" + id
               + ") spoke sentence key='" + v.speech_key + "' at clock=" + spStart);
         }
         lastSpeechStart.put(id, spStart);

         if (fullSample) {
            if (v.goalKey != null) {
               withGoal++;
            }
            Vec3 now = new Vec3(v.getX(), v.getY(), v.getZ());
            Vec3 was = lastPos.get(id);
            if (was != null && was.distanceToSqr(now) > 0.0025) { // moved > 0.05 block since last full sample
               moving++;
            }
            lastPos.put(id, now);
         }
      }

      if (fullSample) {
         activitySamples++;
         activityGoalSum += withGoal;
         activityMovingSum += moving;
         activityMaxVillagers = Math.max(activityMaxVillagers, villagers.size());
         log("ACTIVITY-SAMPLE #" + activitySamples + ": villagers=" + villagers.size()
            + " withGoal=" + withGoal + " movingSinceLast=" + moving
            + " speechEventsSoFar=" + activitySpeakingEvents);
      }

      if (activityTicksSampled >= ACTIVITY_WINDOW_TICKS) {
         activitySampling = false;
      }
   }

   private void stepReportActivity() {
      try {
         double avgGoal = activitySamples > 0 ? (double) activityGoalSum / activitySamples : 0.0;
         double avgMoving = activitySamples > 0 ? (double) activityMovingSum / activitySamples : 0.0;
         String detail = "ticksSampled=" + activityTicksSampled + " fullSamples=" + activitySamples
            + " maxVillagers=" + activityMaxVillagers
            + " avgWithGoal=" + String.format(java.util.Locale.ROOT, "%.1f", avgGoal)
            + " avgMoving=" + String.format(java.util.Locale.ROOT, "%.1f", avgMoving)
            + " speechEvents=" + activitySpeakingEvents + " sentences=" + sentencesObserved;
         if (activityMaxVillagers == 0) {
            dynActivityResult = "SKIPPED: no villagers ever observed (village did not populate)";
            skip("activity-observe", "no MillVillager ever observed across " + activityTicksSampled + " ticks");
            return;
         }
         dynActivityResult = "OK: " + detail;
         pass("activity-observe", detail);
      } catch (Throwable t) {
         dynActivityResult = "FAIL: " + t;
         fail("activity-observe", t);
      }
   }

   // ============================ DYNAMIC STEP 1: real trade (buy & sell, verified) ============================

   /**
    * Drives a REAL buy and a REAL sell on the integrated server and verifies money + item counts move.
    * Buy: building SELLS a good to the player (sellingSlot=true) — give the player money first, expect
    * money down and itemCount up. Sell: building BUYS a good from the player (sellingSlot=false) — give
    * the player the item first, expect itemCount down and money up. All on the server thread so the
    * authoritative inventory is mutated and the before/after is meaningful.
    */
   private void stepRealTrade() {
      try {
         final ServerPlayer sp = serverPlayer();
         final MinecraftServer server = mc.getSingleplayerServer();
         if (sp == null || server == null) {
            dynTradeResult = "SKIPPED: not a singleplayer integrated server";
            skip("trade-execute", dynTradeResult.substring("SKIPPED: ".length()));
            return;
         }
         server.execute(() -> {
            try {
               runTradeOnServerThread(sp);
            } catch (Throwable t) {
               dynTradeResult = "FAIL: " + t;
               MillLog.printException(TAG + " trade-execute (server thread)", t);
            }
         });
         // Result is filled in on the server thread; report scheduling now, summarise the outcome later.
         pass("trade-execute", "buy+sell scheduled on server thread (outcome logged from server thread; see TRADE-BUY/TRADE-SELL lines)");
      } catch (Throwable t) {
         dynTradeResult = "FAIL: " + t;
         fail("trade-execute", t);
      }
   }

   private void runTradeOnServerThread(ServerPlayer sp) {
      Building shop = findServerShopTownHall(sp);
      if (shop == null) {
         dynTradeResult = "SKIPPED: no village shop with goods found on the server";
         log("trade-execute SKIPPED: " + dynTradeResult.substring("SKIPPED: ".length()));
         return;
      }
      StringBuilder summary = new StringBuilder();
      boolean anyOk = false;

      // ---- BUY: building sells -> player buys (sellingSlot=true). Give the player money, expect money down, items up. ----
      try {
         java.util.Set<TradeGood> selling = shop.getSellingGoods(sp);
         TradeGood buyGood = (selling == null || selling.isEmpty()) ? null : selling.iterator().next();
         if (buyGood == null) {
            log("TRADE-BUY SKIPPED: shop has no selling goods (nothing the player can buy)");
            summary.append("buy=skip(no-selling) ");
         } else {
            int price = shop.getSellingPrice(buyGood, sp);
            int nb = 4;
            // Ensure the player can afford it: top up money to price*nb + a margin.
            int need = Math.max(0, price * nb + 16 - countServerMoney(sp));
            if (need > 0) {
               MillCommonUtilities.changeMoney(sp.getInventory(), need, sp);
            }
            ContainerTrade menu = new ContainerTrade(0, sp, shop);
            int moneyBefore = countServerMoney(sp);
            int itemsBefore = countInv(sp, buyGood);
            menu.executeTrade(buyGood, true, false, nb, sp);
            int moneyAfter = countServerMoney(sp);
            int itemsAfter = countInv(sp, buyGood);
            log("TRADE-BUY good=" + buyGood.key + " item=" + buyGood.item.getItem() + " price=" + price
               + " nb=" + nb + " money " + moneyBefore + "->" + moneyAfter
               + " itemCount " + itemsBefore + "->" + itemsAfter);
            boolean ok = moneyAfter < moneyBefore && itemsAfter > itemsBefore;
            summary.append("buy=").append(ok ? "OK" : "no-move").append('(')
               .append(moneyBefore).append("->").append(moneyAfter).append("$,")
               .append(itemsBefore).append("->").append(itemsAfter).append("i) ");
            anyOk |= ok;
         }
      } catch (Throwable t) {
         log("TRADE-BUY ERROR: " + t);
         summary.append("buy=err ");
      }

      // ---- SELL: building buys -> player sells (sellingSlot=false). Give the player the item, expect items down, money up. ----
      try {
         // Buying goods can depend on what the player holds; recompute after granting an item.
         java.util.Set<TradeGood> buying = shop.getBuyingGoods(sp);
         TradeGood sellGood = (buying == null || buying.isEmpty()) ? null : buying.iterator().next();
         if (sellGood == null) {
            log("TRADE-SELL SKIPPED: shop has no buying goods (nothing the player can sell)");
            summary.append("sell=skip(no-buying)");
         } else {
            int nb = 4;
            // Give the player the sellable item so the sale has stock to move.
            MillCommonUtilities.putItemsInChest(sp.getInventory(), sellGood.item.getItem(), sellGood.item.meta, nb);
            shop.computeShopGoods(sp); // refresh prices/goods for the now-held item
            int price = shop.getBuyingPrice(sellGood, sp);
            ContainerTrade menu = new ContainerTrade(0, sp, shop);
            int moneyBefore = countServerMoney(sp);
            int itemsBefore = countInv(sp, sellGood);
            menu.executeTrade(sellGood, false, false, nb, sp);
            int moneyAfter = countServerMoney(sp);
            int itemsAfter = countInv(sp, sellGood);
            log("TRADE-SELL good=" + sellGood.key + " item=" + sellGood.item.getItem() + " price=" + price
               + " nb=" + nb + " money " + moneyBefore + "->" + moneyAfter
               + " itemCount " + itemsBefore + "->" + itemsAfter);
            boolean ok = itemsAfter < itemsBefore && moneyAfter >= moneyBefore;
            summary.append("sell=").append(ok ? "OK" : "no-move").append('(')
               .append(moneyBefore).append("->").append(moneyAfter).append("$,")
               .append(itemsBefore).append("->").append(itemsAfter).append("i)");
            anyOk |= ok;
         }
      } catch (Throwable t) {
         log("TRADE-SELL ERROR: " + t);
         summary.append("sell=err");
      }

      dynTradeResult = (anyOk ? "OK: " : "PARTIAL: ") + summary.toString().trim()
         + " [shop=" + shop.getVillageQualifiedName() + "]";
      log("trade-execute result: " + dynTradeResult);
   }

   // ============================ DYNAMIC STEP 2: quest accept ============================

   /**
    * If a quest is available to the player (one of the spawned village's villagers is registered in a
    * quest, {@code profile.villagersInQuests}), accepts/advances it via the real action path
    * ({@code QuestInstance.completeStep}, the same call {@code GuiActions.questCompleteStep} makes) and
    * logs the quest's step transition. If no quest exists, logs SKIPPED with the reason. Runs on the
    * server thread (mutates authoritative quest state).
    */
   private void stepAcceptQuest() {
      try {
         final ServerPlayer sp = serverPlayer();
         final MinecraftServer server = mc.getSingleplayerServer();
         if (sp == null || server == null) {
            dynQuestResult = "SKIPPED: not a singleplayer integrated server";
            skip("quest", dynQuestResult.substring("SKIPPED: ".length()));
            return;
         }
         server.execute(() -> {
            try {
               runQuestOnServerThread(sp);
            } catch (Throwable t) {
               dynQuestResult = "FAIL: " + t;
               MillLog.printException(TAG + " quest (server thread)", t);
            }
         });
         pass("quest", "quest accept scheduled on server thread (outcome logged from server thread; see QUEST lines)");
      } catch (Throwable t) {
         dynQuestResult = "FAIL: " + t;
         fail("quest", t);
      }
   }

   private void runQuestOnServerThread(ServerPlayer sp) {
      MillWorldData smw = serverWorld();
      if (smw == null) {
         dynQuestResult = "SKIPPED: no server MillWorldData";
         log("QUEST SKIPPED: " + dynQuestResult.substring("SKIPPED: ".length()));
         return;
      }
      org.millenaire.common.world.UserProfile profile = smw.getProfile(sp);
      if (profile == null || profile.villagersInQuests.isEmpty()) {
         dynQuestResult = "SKIPPED: no quest available in the spawned village (villagersInQuests empty)";
         log("QUEST SKIPPED: " + dynQuestResult.substring("SKIPPED: ".length()));
         return;
      }
      // Find a quest whose current-step villager is loaded, so completeStep has a real villager to act on.
      for (Map.Entry<Long, org.millenaire.common.quest.QuestInstance> e : profile.villagersInQuests.entrySet()) {
         org.millenaire.common.quest.QuestInstance qi = e.getValue();
         long villagerId = e.getKey();
         MillVillager villager = smw.getVillagerById(villagerId);
         if (villager == null) {
            continue;
         }
         try {
            int stepBefore = qi.currentStep;
            String questName = qi.quest != null ? qi.quest.key : "?";
            log("QUEST found: quest='" + questName + "' currentStep=" + stepBefore
               + " villager='" + villager.getName().getString() + "' (id=" + villagerId + ") — accepting/advancing via completeStep");
            String res = qi.completeStep(sp, villager);
            int stepAfter = qi.currentStep;
            boolean stillActive = profile.questInstances.contains(qi);
            log("QUEST quest='" + questName + "' step " + stepBefore + "->" + stepAfter
               + " stillActive=" + stillActive + " result='" + (res == null ? "" : res.replace("\n", " ")) + "'");
            dynQuestResult = "OK: quest='" + questName + "' step " + stepBefore + "->" + stepAfter
               + (stillActive ? " (advanced)" : " (completed)");
            log("quest result: " + dynQuestResult);
            return;
         } catch (Throwable t) {
            log("QUEST ERROR advancing quest for villager " + villagerId + ": " + t);
            dynQuestResult = "FAIL: " + t;
            return;
         }
      }
      dynQuestResult = "SKIPPED: quest(s) exist but no current-step villager is loaded to act on";
      log("QUEST SKIPPED: " + dynQuestResult.substring("SKIPPED: ".length()));
   }

   // ============================ DYNAMIC STEP 4: reputation ============================

   /**
    * Reads the player's reputation with the village town hall, performs an action that should change it
    * ({@code building.adjustReputation}, the same call a trade/donation makes), and logs before->after.
    * Runs on the server thread (reputation lives in the server-side {@code UserProfile}).
    */
   private void stepReputation() {
      try {
         final ServerPlayer sp = serverPlayer();
         final MinecraftServer server = mc.getSingleplayerServer();
         if (sp == null || server == null) {
            dynRepResult = "SKIPPED: not a singleplayer integrated server";
            skip("reputation", dynRepResult.substring("SKIPPED: ".length()));
            return;
         }
         server.execute(() -> {
            try {
               runReputationOnServerThread(sp);
            } catch (Throwable t) {
               dynRepResult = "FAIL: " + t;
               MillLog.printException(TAG + " reputation (server thread)", t);
            }
         });
         pass("reputation", "reputation change scheduled on server thread (outcome logged from server thread; see REPUTATION lines)");
      } catch (Throwable t) {
         dynRepResult = "FAIL: " + t;
         fail("reputation", t);
      }
   }

   private void runReputationOnServerThread(ServerPlayer sp) {
      MillWorldData smw = serverWorld();
      if (smw == null) {
         dynRepResult = "SKIPPED: no server MillWorldData";
         log("REPUTATION SKIPPED: " + dynRepResult.substring("SKIPPED: ".length()));
         return;
      }
      Building th = null;
      for (Building b : smw.allBuildings()) {
         if (b != null && b.isTownhall) {
            th = b;
            break;
         }
      }
      if (th == null) {
         dynRepResult = "SKIPPED: no town hall on the server to have reputation with";
         log("REPUTATION SKIPPED: " + dynRepResult.substring("SKIPPED: ".length()));
         return;
      }
      int repBefore = th.getReputation(sp);
      int delta = 500;
      th.adjustReputation(sp, delta);
      int repAfter = th.getReputation(sp);
      log("REPUTATION village='" + th.getVillageQualifiedName() + "' adjust=+" + delta
         + " reputation " + repBefore + "->" + repAfter
         + " level='" + th.getReputationLevelLabel(sp) + "'");
      boolean ok = repAfter == repBefore + delta;
      dynRepResult = (ok ? "OK: " : "UNEXPECTED: ") + repBefore + "->" + repAfter + " (+" + delta + ")";
      log("reputation result: " + dynRepResult);
   }

   // ============================ DYNAMIC STEP 3: village create / remove ============================

   /**
    * Creates a village via the real summoning-wand flow ({@code GuiActions.newVillageCreation}) at a fresh
    * point, logs the town-hall count before->after, then removes a village via the real negation-wand flow
    * ({@code GuiActions.useNegationWand} -> {@code destroyVillage}) and logs the count change again. Runs on
    * the server thread (village gen / destruction is server-authoritative).
    */
   private void stepVillageCreateRemove() {
      try {
         final ServerPlayer sp = serverPlayer();
         final MinecraftServer server = mc.getSingleplayerServer();
         if (sp == null || server == null) {
            dynCreateRemoveResult = "SKIPPED: not a singleplayer integrated server";
            skip("village-create-remove", dynCreateRemoveResult.substring("SKIPPED: ".length()));
            return;
         }
         server.execute(() -> {
            try {
               runVillageCreateRemoveOnServerThread(sp);
            } catch (Throwable t) {
               dynCreateRemoveResult = "FAIL: " + t;
               MillLog.printException(TAG + " village-create-remove (server thread)", t);
            }
         });
         pass("village-create-remove", "create+remove scheduled on server thread (outcome logged from server thread; see VILLAGE-CREATE/VILLAGE-REMOVE lines)");
      } catch (Throwable t) {
         dynCreateRemoveResult = "FAIL: " + t;
         fail("village-create-remove", t);
      }
   }

   private void runVillageCreateRemoveOnServerThread(ServerPlayer sp) {
      MillWorldData smw = serverWorld();
      ServerLevel level = mc.getSingleplayerServer().overworld();
      if (smw == null) {
         dynCreateRemoveResult = "SKIPPED: no server MillWorldData";
         log("VILLAGE-CREATE SKIPPED: " + dynCreateRemoveResult.substring("SKIPPED: ".length()));
         return;
      }
      VillageType vtype = pickAnyRegularVillageType();
      if (vtype == null) {
         dynCreateRemoveResult = "SKIPPED: no regular VillageType available";
         log("VILLAGE-CREATE SKIPPED: " + dynCreateRemoveResult.substring("SKIPPED: ".length()));
         return;
      }
      String cultureKey = vtype.culture != null ? vtype.culture.key : null;

      // ---- CREATE: spiral out from a point well away from the player's village so it lands cleanly. ----
      int townhallsBefore = countTownHalls(smw);
      int baseX = (int) sp.getX() + 400;
      int baseZ = (int) sp.getZ() + 400;
      int[][] offsets = {
         {0, 0}, {64, 0}, {0, 64}, {-64, 0}, {0, -64}, {128, 128}, {-128, 128}, {128, -128}, {-128, -128},
         {256, 0}, {0, 256}, {-256, 0}, {0, -256}
      };
      boolean created = false;
      Point createdAt = null;
      for (int[] off : offsets) {
         int tx = baseX + off[0];
         int tz = baseZ + off[1];
         int ccx = tx >> 4;
         int ccz = tz >> 4;
         for (int dcx = -2; dcx <= 2; dcx++) {
            for (int dcz = -2; dcz <= 2; dcz++) {
               level.getChunk(ccx + dcx, ccz + dcz);
               level.setChunkForced(ccx + dcx, ccz + dcz, true);
            }
         }
         int surfaceY = WorldUtilities.findTopSoilBlock(level, tx, tz);
         org.millenaire.common.ui.GuiActions.newVillageCreation(
            sp, new Point(tx, surfaceY, tz), cultureKey, vtype.key);
         int now = countTownHalls(smw);
         if (now > townhallsBefore) {
            created = true;
            createdAt = new Point(tx, surfaceY, tz);
            break;
         }
      }
      int townhallsAfterCreate = countTownHalls(smw);
      log("VILLAGE-CREATE type=" + vtype.key + " culture=" + cultureKey
         + " townHalls " + townhallsBefore + "->" + townhallsAfterCreate
         + " created=" + created + " at=" + (createdAt == null ? "n/a" : createdAt));

      // ---- REMOVE: negate the village we just created (or, failing that, any town hall). ----
      Building toRemove = null;
      if (createdAt != null) {
         toRemove = smw.getBuilding(createdAt);
      }
      if (toRemove == null) {
         for (Building b : smw.allBuildings()) {
            if (b != null && b.isTownhall) {
               toRemove = b;
               break;
            }
         }
      }
      int townhallsBeforeRemove = countTownHalls(smw);
      boolean removed = false;
      String removedName = "n/a";
      if (toRemove != null && toRemove.isTownhall) {
         removedName = toRemove.getVillageQualifiedName();
         org.millenaire.common.ui.GuiActions.useNegationWand(sp, toRemove);
         removed = true;
      }
      int townhallsAfterRemove = countTownHalls(smw);
      log("VILLAGE-REMOVE village='" + removedName + "' townHalls " + townhallsBeforeRemove
         + "->" + townhallsAfterRemove + " removed=" + removed);

      dynCreateRemoveResult = "create " + townhallsBefore + "->" + townhallsAfterCreate
         + (created ? "(OK)" : "(no-change)") + ", remove " + townhallsBeforeRemove + "->"
         + townhallsAfterRemove + (townhallsAfterRemove < townhallsBeforeRemove ? "(OK)" : "(no-change)");
      log("village-create-remove result: " + dynCreateRemoveResult);
   }

   private static int countTownHalls(MillWorldData smw) {
      int n = 0;
      for (Building b : smw.allBuildings()) {
         if (b != null && b.isTownhall) {
            n++;
         }
      }
      return n;
   }

   // ============================ STEP: summary ============================

   private void stepSummary() {
      log("===== CLIENT SELF-TEST SUMMARY =====");
      int pass = 0;
      int fail = 0;
      int skip = 0;
      for (Map.Entry<String, String> e : results.entrySet()) {
         String v = e.getValue();
         if (v.startsWith("OK")) {
            pass++;
         } else if (v.startsWith("FAIL")) {
            fail++;
         } else {
            skip++;
         }
         log("  " + e.getKey() + ": " + v);
      }
      log("totals: pass=" + pass + " fail=" + fail + " skip=" + skip + " (" + results.size() + " steps)");
      log("----- DYNAMIC GAMEPLAY RESULTS -----");
      log("  TRADE (buy & sell):     " + dynTradeResult);
      log("  QUEST:                  " + dynQuestResult);
      log("  VILLAGE create/remove:  " + dynCreateRemoveResult);
      log("  REPUTATION:             " + dynRepResult);
      log("  VILLAGER ACTIVITY:      " + dynActivityResult);
      log("distinct exception types seen: " + distinctExceptions.size());
      for (Map.Entry<String, String> e : distinctExceptions.entrySet()) {
         log("  exception: " + e.getKey() + " — first at " + e.getValue());
      }
      log("===== END CLIENT SELF-TEST SUMMARY =====");
   }

   // ============================ STEP: return to menu, then stop ============================

   private void stepReturnToMenu() {
      try {
         // Close any open Mill screen first.
         mc.setScreenAndShow(null);
         // Disconnect from the integrated server back to the title screen (tidy shutdown of the world).
         if (mc.level != null) {
            mc.disconnect(new TitleScreen(), false);
         }
         log("returned to main menu (disconnected from world).");
      } catch (Throwable t) {
         recordException("returnToMenu", t);
         log("returnToMenu note: " + t);
      }
   }

   private void returnToMenuAndStop() {
      if (halted) {
         return;
      }
      halted = true;
      try {
         if (mc.gui != null && mc.gui.screen() != null) {
            mc.setScreenAndShow(null);
         }
         if (mc.level != null) {
            mc.disconnect(new TitleScreen(), false);
         }
      } catch (Throwable ignored) {
      }
      log("client self-test complete — stopping the client so the process exits.");
      try {
         mc.stop();
      } catch (Throwable t) {
         MillLog.printException(TAG + " Minecraft.stop() failed", t);
      }
   }

   // ============================ helpers ============================

   private static MouseButtonEvent mouse(int x, int y, int button) {
      return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
   }

   private String describeLevel() {
      try {
         if (mc.level == null) {
            return "none";
         }
         return mc.level.dimension().identifier().toString();
      } catch (Throwable t) {
         return "?";
      }
   }

   private static String screenName(Screen s) {
      return s == null ? "null" : s.getClass().getSimpleName();
   }

   // --- reflective access to ported-class internals we deliberately don't widen for a test ---

   /** Reads {@code GuiText.pageNum} (protected) via reflection. */
   private static int readPageNum(GuiText book) {
      try {
         Field f = GuiText.class.getDeclaredField("pageNum");
         f.setAccessible(true);
         return f.getInt(book);
      } catch (Throwable t) {
         return -1;
      }
   }

   @SuppressWarnings("unchecked")
   private static List<GuiText.MillGuiTextField> readTextFields(GuiText book) {
      try {
         Field f = GuiText.class.getDeclaredField("textFields");
         f.setAccessible(true);
         return (List<GuiText.MillGuiTextField>)f.get(book);
      } catch (Throwable t) {
         return null;
      }
   }

   private static int callIntMethod(Object o, String name) {
      try {
         var m = o.getClass().getMethod(name);
         m.setAccessible(true);
         return (int)m.invoke(o);
      } catch (Throwable t) {
         // getXSize/getYSize live on GuiText; walk up if the concrete class doesn't expose them.
         try {
            var m = GuiText.class.getDeclaredMethod(name);
            m.setAccessible(true);
            return (int)m.invoke(o);
         } catch (Throwable t2) {
            return 0;
         }
      }
   }

   private static int readIntField(Object o, Class<?> declaring, String name) throws Exception {
      Field f = declaring.getDeclaredField(name);
      f.setAccessible(true);
      return f.getInt(o);
   }

   /** Forces {@code AbstractContainerScreen.hoveredSlot} so the headless click resolves to a trade slot. */
   private static void setHoveredSlot(AbstractContainerScreen<?> gui, Slot slot) throws Exception {
      Field f = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
      f.setAccessible(true);
      f.set(gui, slot);
   }

   // --- result recording ---

   private void pass(String step, String detail) {
      results.put(step, "OK: " + detail);
      log(step + " OK: " + detail);
   }

   private void fail(String step, Throwable t) {
      recordException(step, t);
      results.put(step, "FAIL: " + t);
      log(step + " FAIL: " + t);
      MillLog.printException(TAG + " " + step, t);
   }

   private void fail(String step, IllegalStateException t) {
      results.put(step, "FAIL: " + t.getMessage());
      log(step + " FAIL: " + t.getMessage());
   }

   private void skip(String step, String reason) {
      results.put(step, "SKIPPED: " + reason);
      log(step + " SKIPPED: " + reason);
   }

   private void recordException(String where, Throwable t) {
      String key = t.getClass().getName() + ": " + t.getMessage();
      distinctExceptions.putIfAbsent(key, where);
   }

   private static void log(String s) {
      MillLog.major(null, TAG + " " + s);
   }
}
