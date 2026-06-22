package org.millenaire.common.forge;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.annotedparameters.ParametersManager;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.buildingplan.BuildingDevUtilities;
import org.millenaire.common.buildingplan.BuildingPlan;
import org.millenaire.common.commands.CommandDebugResendProfiles;
import org.millenaire.common.commands.CommandDebugResetVillagers;
import org.millenaire.common.commands.CommandGiveReputation;
import org.millenaire.common.commands.CommandImportCulture;
import org.millenaire.common.commands.CommandListActiveVillages;
import org.millenaire.common.commands.CommandRenameVillage;
import org.millenaire.common.commands.CommandSpawnVillage;
import org.millenaire.common.commands.CommandSwitchVillageControl;
import org.millenaire.common.commands.CommandTeleportToVillage;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.deployer.ContentDeployer;
import org.millenaire.common.entity.EntityTargetedBlaze;
import org.millenaire.common.entity.EntityTargetedGhast;
import org.millenaire.common.entity.EntityTargetedWitherSkeleton;
import org.millenaire.common.entity.EntityWallDecoration;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.VillagerConfig;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.quest.Quest;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.virtualdir.VirtualDir;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.WorldGenVillage;

/**
 * Millénaire core holder. On 1.12 this was the Forge {@code @Mod} class with SidedProxy +
 * FML lifecycle events; on 26.2 the entrypoint is {@code org.millenaire.MillenaireMod} (Fabric
 * {@link net.fabricmc.api.ModInitializer}) which sets the proxy, runs the init phases here, and
 * registers content/networking/tick callbacks. This class keeps the static state + helpers the
 * rest of the mod references via {@code Mill.proxy} / {@code Mill.instance}.
 */
public class Mill {
   public static final String MODID = "millenaire";
   public static final String MODNAME = "Millénaire";
   public static final String VERSION_NUMBER = "8.1.2";
   public static final String MINECRAFT_VERSION_NUMBER = "1.12.2";
   public static final String VERSION = "Millénaire 8.1.2";
   public static final Logger LOGGER = LogManager.getLogger("millenaire");
   // 1.12 @SidedProxy → set by the Fabric entrypoint (server default; the client initializer
   // replaces it with org.millenaire.client.forge.ClientProxy).
   public static CommonProxy proxy = new CommonProxy();
   // 1.12 @Instance.
   public static Mill instance = new Mill();
   public static List<MillWorldData> serverWorlds = new ArrayList<>();
   public static MillWorldData clientWorld = null;
   public static List<File> loadingDirs = new ArrayList<>();
   public static VirtualDir virtualLoadingDir;
   // 1.12 FMLEventChannel removed — networking is handled by MillNetworking (Fabric payloads).
   public static boolean loadingComplete = false;
   public static final Identifier ENTITY_PIG = Identifier.withDefaultNamespace("pig");
   public static final Identifier ENTITY_COW = Identifier.withDefaultNamespace("cow");
   public static final Identifier ENTITY_CHICKEN = Identifier.withDefaultNamespace("chicken");
   public static final Identifier ENTITY_SHEEP = Identifier.withDefaultNamespace("sheep");
   public static final Identifier ENTITY_SQUID = Identifier.withDefaultNamespace("squid");
   public static final Identifier ENTITY_WOLF = Identifier.withDefaultNamespace("wolf");
   public static final Identifier ENTITY_POLAR_BEAR = Identifier.withDefaultNamespace("polar_bear");
   public static final Identifier ENTITY_SKELETON = Identifier.withDefaultNamespace("skeleton");
   public static final Identifier ENTITY_CREEPER = Identifier.withDefaultNamespace("creeper");
   public static final Identifier ENTITY_SPIDER = Identifier.withDefaultNamespace("spider");
   public static final Identifier ENTITY_CAVESPIDER = Identifier.withDefaultNamespace("cave_spider");
   public static final Identifier ENTITY_ZOMBIE = Identifier.withDefaultNamespace("zombie");
   public static final Identifier ENTITY_TARGETED_GHAST = Identifier.fromNamespaceAndPath("millenaire", "millghast");
   public static final Identifier ENTITY_TARGETED_BLAZE = Identifier.fromNamespaceAndPath("millenaire", "millblaze");
   public static final Identifier ENTITY_TARGETED_WITHERSKELETON = Identifier.fromNamespaceAndPath("millenaire", "millwitherskeleton");
   public static final Identifier CROP_WHEAT = Identifier.withDefaultNamespace("wheat");
   public static final Identifier CROP_CARROT = Identifier.withDefaultNamespace("carrots");
   public static final Identifier CROP_POTATO = Identifier.withDefaultNamespace("potatoes");
   public static final Identifier CROP_RICE = Identifier.fromNamespaceAndPath("millenaire", "crop_rice");
   public static final Identifier CROP_TURMERIC = Identifier.fromNamespaceAndPath("millenaire", "crop_turmeric");
   public static final Identifier CROP_MAIZE = Identifier.fromNamespaceAndPath("millenaire", "crop_maize");
   public static final Identifier CROP_VINE = Identifier.fromNamespaceAndPath("millenaire", "crop_vine");
   public static final Identifier CROP_CACAO = Identifier.withDefaultNamespace("cocoa");
   public static final Identifier CROP_FLOWER = Identifier.withDefaultNamespace("flower");
   public static final Identifier CROP_COTTON = Identifier.fromNamespaceAndPath("millenaire", "crop_cotton");
   public static boolean startupError = false;
   public static boolean checkedMillenaireDir = false;
   public static boolean displayMillenaireLocationError = false;
   public static SoundEvent SOUND_NORMAN_BELLS;
   static final Class[] BANNER_CLASSES = new Class[]{String.class, String.class, ItemStack.class};
   public static final String[] BANNER_SHORTNAMES = new String[]{
      "byz",
      "by1",
      "by2",
      "sjk",
      "may",
      "inu",
      "ind",
      "in1",
      "in2",
      "in3",
      "in4",
      "in5",
      "nor",
      "ma1",
      "ma2",
      "ma3",
      "ma4",
      "iu1",
      "iu2",
      "iu3",
      "iu4",
      "jap",
      "jaa",
      "jam",
      "jar",
      "jat",
      "sjkr",
      "sjkm"
   };

   public static MillWorldData getMillWorld(Level world) {
      if (clientWorld != null && clientWorld.world == world) {
         return clientWorld;
      } else {
         for (MillWorldData mw : serverWorlds) {
            if (mw.world == world) {
               return mw;
            }
         }

         return serverWorlds != null && serverWorlds.size() > 0 ? serverWorlds.get(0) : null;
      }
   }

   public static boolean isDistantClient() {
      return clientWorld != null && serverWorlds.isEmpty();
   }

   private void addBannerPatterns() {
      // 1.12 extended the vanilla BannerPattern enum via EnumHelper here. On 26.2 banner patterns are
      // a datapack registry (Registries.BANNER_PATTERN), so Millénaire's 28 culture patterns are now
      // declared as JSON under data/millenaire/banner_pattern/<long>.json (asset_id/translation_key,
      // matching BannerPattern.DIRECT_CODEC) with matching textures at
      // assets/millenaire/textures/entity/banner/<long>.png. They register automatically at world load.
      // Touch MillBannerPatterns so its shortname->ResourceKey map (the EnumHelper hashname bridge) is
      // initialised and the long-name/short-name parity is validated on startup.
      if (MillBannerPatterns.BANNER_LONGNAMES.length != BANNER_SHORTNAMES.length) {
         // FAIL-FAST: the short<->long banner-name tables must be parallel; a mismatch means culture banner
         // codes resolve to the wrong (or no) registered pattern, silently corrupting every banner. Crash.
         throw MillCrash.fail("Registry", "banner pattern short/long name count mismatch: "
            + BANNER_SHORTNAMES.length + " short vs " + MillBannerPatterns.BANNER_LONGNAMES.length + " long");
      }
   }

   public void init() {
      if (!startupError) {
         // 1.12 set MapGenVillage.VILLAGE_SPAWN_BIOMES = emptyList() here. On 26.2 villages are a
         // data-driven structure set, removed per-level at SERVER_STARTED (see
         // MillenaireMod + VillageStructureSuppressor) since the registry is frozen by this point.

         MillBlocks.initBlockStates();
         boolean error = false;
         if (!error) {
            InvItem.loadItemList();
         }

         if (!error) {
            error = BuildingPlan.loadBuildingPoints();
         }

         if (!error) {
            VillagerConfig.loadConfigs();
         }

         if (!error) {
            Goal.initGoals();
         }

         if (!error) {
            error = Culture.loadCultures();
         }

         if (!error) {
            Quest.loadQuests();
         }

         if (MillConfigValues.generateHelpData) {
            ParametersManager.generateHelpFiles();
            DocumentedElement.generateHelpFiles();
         }

         startupError = error;
         // 1.12 EntityRegistry.registerModEntity(...) for the villager + targeted-mob + wall-decoration
         // entities is replaced by EntityType registration in MillEntities.register(), called from
         // MillenaireMod.onInitialize() (Fabric). Attribute suppliers via FabricDefaultAttributeRegistry.
         // 1.12 GameRegistry.addSmelting(...) smelting recipes are now data-driven JSON in
         // data/millenaire/recipe/: stone_deco_to_painted_brick_white, bearmeat_cooked,
         // wolfmeat_cooked, seafood_cooked (type minecraft:smelting).
         Identifier location = Identifier.fromNamespaceAndPath("millenaire", "norman_bells");
         SOUND_NORMAN_BELLS = SoundEvent.createVariableRangeEvent(location);
         loadingComplete = true;
         if (MillConfigValues.LogOther >= 1) {
            if (startupError) {
               MillLog.major(this, "Millénaire Millénaire 8.1.2 loaded unsuccessfully.");
            } else {
               MillLog.major(this, "Millénaire Millénaire 8.1.2 loaded successfully.");
            }
         }

         // 26.2: the 1.12 Forge event-bus / network / worldgen / chunk wiring done here is now registered
         // from MillenaireMod.onInitialize via Fabric APIs:
         //  - ServerTickHandler  -> ServerTickEvents.END_SERVER_TICK
         //  - ServerReceiver     -> MillNetworking.registerServerReceiver()/registerPayloads() + Fabric menu screens
         //  - WorldGenVillage    -> ServerChunkEvents.CHUNK_GENERATE
         //  - MillEventController -> ServerPlayConnectionEvents + the server-lifecycle world loop
         //  - chunk forcing       -> ServerLevel.setChunkForced (see BuildingChunkLoader)
         proxy.registerForgeClientClasses();
         proxy.registerKeyBindings();
         MillAdvancements.registerTriggers();
         proxy.loadLanguagesIfNeeded();
         if (MillConfigValues.DEV && !proxy.isTrueServer()) {
            BuildingDevUtilities.exportMissingTravelBookDesc();
            BuildingDevUtilities.exportTravelBookDescCSV();
         }

         this.addBannerPatterns();
      }
   }

   public void postInit() {
   }

   public void preInit() {
      // 1.12 used the FML mod source jar (event.getSourceFile()); on Fabric resolve it from the mod
      // container's origin. ContentDeployer copies the bundled todeploy/ via java.util.jar.JarFile, so
      // it needs a real jar — in a dev run the origin is a classes/resources directory, so we skip.
      File modJar = null;
      try {
         net.fabricmc.loader.api.ModContainer mc =
            net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MODID).orElse(null);
         if (mc != null && mc.getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) {
            for (java.nio.file.Path p : mc.getOrigin().getPaths()) {
               if (java.nio.file.Files.isRegularFile(p)) {
                  modJar = p.toFile();
                  break;
               }
            }
         }
      } catch (Exception e) {
         MillLog.printException("Could not resolve Millénaire mod jar for content deployment", e);
      }

      if (modJar != null) {
         ContentDeployer.deployContent(modJar);
      } else {
         MillLog.major(this, "Skipping todeploy/ content deployment: not running from a jar (dev env) or jar not found.");
      }
      MillConfigValues.initConfig();
      // [MILLDEBUG] now that the config (incl. DEBUG_MODE) is loaded and registration ran during
      // onInitialize, dump the Mill registration totals.
      MillRegistry.logRegistrationSummary();
      proxy.refreshClientResources();
      BlockItemUtilities.initBlockTypes();
   }

   public void serverLoad(net.minecraft.server.MinecraftServer server) {
      // 1.12 registered ICommand instances via FMLServerStartingEvent.registerServerCommand(...).
      // On 26.2 the commands are Brigadier and are registered from MillenaireMod via Fabric's
      // CommandRegistrationCallback (see the command classes' register(dispatcher) methods).
   }
}
