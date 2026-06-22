package org.millenaire.common.village;

import com.mojang.authlib.GameProfile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.block.BlockMillStainedGlass;
import org.millenaire.common.block.BlockPaintedBricks;
import org.millenaire.common.block.IBlockPath;
import org.millenaire.common.block.IPaintedBlock;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.buildingplan.BuildingBlock;
import org.millenaire.common.buildingplan.BuildingCustomPlan;
import org.millenaire.common.buildingplan.BuildingPlan;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.entity.TileEntityLockedChest;
import org.millenaire.common.entity.TileEntityMockBanner;
import org.millenaire.common.forge.BuildingChunkLoader;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.ItemParchment;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.pathing.atomicstryker.AStarNode;
import org.millenaire.common.pathing.atomicstryker.AStarPathPlannerJPS;
import org.millenaire.common.pathing.atomicstryker.IAStarPathedEntity;
import org.millenaire.common.pathing.atomicstryker.RegionMapper;
import org.millenaire.common.ui.MillMapInfo;
import org.millenaire.common.ui.PujaSacrifice;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.PathUtilities;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.ThreadSafeUtilities;
import org.millenaire.common.utilities.VillageUtilities;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.buildingmanagers.MarvelManager;
import org.millenaire.common.village.buildingmanagers.PanelManager;
import org.millenaire.common.village.buildingmanagers.ResManager;
import org.millenaire.common.village.buildingmanagers.VisitorManager;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;

public class Building {
   public static final AStarConfig PATH_BUILDER_JPS_CONFIG = new AStarConfig(true, false, false, false, true);
   public static final int INVADER_SPAWNING_DELAY = 500;
   public static final int RELATION_NEUTRAL = 0;
   public static final int RELATION_FAIR = 10;
   public static final int RELATION_DECENT = 30;
   public static final int RELATION_GOOD = 50;
   public static final int RELATION_VERYGOOD = 70;
   public static final int RELATION_EXCELLENT = 90;
   public static final int RELATION_CHILLY = -10;
   public static final int RELATION_BAD = -30;
   public static final int RELATION_VERYBAD = -50;
   public static final int RELATION_ATROCIOUS = -70;
   public static final int RELATION_OPENCONFLICT = -90;
   public static final int RELATION_MAX = 100;
   public static final int RELATION_MIN = -100;
   public static final String blTownhall = "townHall";
   private static final int LOCATION_SEARCH_DELAY = 80000;
   public static final int MIN_REPUTATION_FOR_TRADE = -1024;
   public static final int MAX_REPUTATION = 32768;
   public static final String versionCompatibility = "1.0";
   private boolean pathsChanged = false;
   private ItemStack bannerStack = ItemStack.EMPTY;
   public String buildingGoal;
   public String buildingGoalIssue;
   public int buildingGoalLevel = 0;
   public BuildingLocation buildingGoalLocation = null;
   public int buildingGoalVariation = 0;
   public ConcurrentHashMap<BuildingProject.EnumProjects, CopyOnWriteArrayList<BuildingProject>> buildingProjects = new ConcurrentHashMap<>();
   public CopyOnWriteArrayList<Point> buildings = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<String> buildingsBought = new CopyOnWriteArrayList<>();
   public Culture culture;
   private boolean declaredPos = false;
   public HashMap<InvItem, Integer> exported = new HashMap<>();
   public HashMap<InvItem, Integer> imported = new HashMap<>();
   public boolean isActive = false;
   public boolean isAreaLoaded = false;
   public boolean chestLocked;
   public boolean isTownhall = false;
   public boolean isInn = false;
   public boolean isMarket = false;
   public boolean hasVisitors = false;
   public boolean hasAutoSpawn = false;
   private long lastFailedOtherLocationSearch = 0L;
   private long lastFailedProjectLocationSearch = 0L;
   public long lastPathingUpdate;
   private long lastSaved = 0L;
   public long lastVillagerRecordsRepair = 0L;
   public BuildingLocation location;
   public VillagerRecord merchantRecord = null;
   private String name = null;
   private String qualifier = "";
   public int nbNightsMerchant = 0;
   private HashMap<TradeGood, Integer> neededGoodsCached = new HashMap<>();
   private long neededGoodsLastGenerated = 0L;
   public boolean nightActionPerformed = false;
   public boolean noProjectsLeft = false;
   public RegionMapper regionMapper = null;
   public Player closestPlayer = null;
   private Point pos = null;
   private boolean rebuildingRegionMapper = false;
   private boolean saveNeeded = false;
   private String saveReason = null;
   public MillVillager seller = null;
   public Point sellingPlace = null;
   private Point townHallPos = null;
   private Set<MillVillager> villagers = new LinkedHashSet<>();
   public CopyOnWriteArrayList<String> visitorsList = new CopyOnWriteArrayList<>();
   private final Map<Long, VillagerRecord> vrecords = new HashMap<>();
   public VillageType villageType = null;
   private ConcurrentHashMap<Point, Integer> relations = new ConcurrentHashMap<>();
   public Point parentVillage = null;
   public VillageMapInfo winfo = new VillageMapInfo();
   public MillMapInfo mapInfo = null;
   public MillWorldData mw;
   public Level world;
   private boolean nightBackgroundActionPerformed;
   private boolean updateRaidPerformed;
   public CopyOnWriteArrayList<String> raidsPerformed = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<String> raidsSuffered = new CopyOnWriteArrayList<>();
   public Point raidTarget;
   public long raidStart = 0L;
   public long raidPlanningStart;
   public boolean underAttack = false;
   private int nbAnimalsRespawned;
   public PujaSacrifice pujas = null;
   public UUID controlledBy = null;
   public String controlledByName = null;
   private Building.SaveWorker saveWorker = null;
   private long lastGoodsRefresh = 0L;
   private boolean refreshGoodsNightActionPerformed;
   private BuildingChunkLoader chunkLoader = null;
   private final CopyOnWriteArrayList<ConstructionIP> constructionsIP = new CopyOnWriteArrayList<>();
   public List<List<BuildingBlock>> pathsToBuild = null;
   private Building.PathCreatorQueue pathQueue = null;
   public int pathsToBuildIndex = 0;
   public int pathsToBuildPathIndex = 0;
   public List<Point> oldPathPointsToClear = null;
   public int oldPathPointsToClearIndex = 0;
   private boolean autobuildPaths = false;
   private final HashMap<String, LinkedHashMap<TradeGood, Integer>> shopBuys = new HashMap<>();
   private final HashMap<String, LinkedHashMap<TradeGood, Integer>> shopSells = new HashMap<>();
   private final ResManager resManager = new ResManager(this);
   public CopyOnWriteArrayList<Point> subBuildings = new CopyOnWriteArrayList<>();
   private Map<InvItem, Integer> inventoryCache = null;
   private MarvelManager marvelManager;
   private VisitorManager visitorManager = null;
   private PanelManager panelManager = null;
   private final CopyOnWriteArraySet<String> tags = new CopyOnWriteArraySet<>();
   public VillageType.BrickColourTheme brickColourTheme = null;

   public static void readBuildingPacket(MillWorldData mw, FriendlyByteBuf ds) {
      Point pos = null;
      pos = StreamReadWrite.readNullablePoint(ds);
      Building building = mw.getBuilding(pos);
      boolean newbuilding = false;
      if (building == null) {
         building = new Building(mw);
         newbuilding = true;
      }

      building.pos = pos;

      try {
         building.isTownhall = ds.readBoolean();
         building.chestLocked = ds.readBoolean();
         building.controlledBy = StreamReadWrite.readNullableUUID(ds);
         building.controlledByName = StreamReadWrite.readNullableString(ds);
         building.townHallPos = StreamReadWrite.readNullablePoint(ds);
         String cultureKey = StreamReadWrite.readNullableString(ds);
         building.culture = Culture.getCultureByName(cultureKey);
         if (building.culture == null) {
            MillLog.error(building, "Received from the server a building of unknown culture: " + cultureKey);
         }

         String vtype = StreamReadWrite.readNullableString(ds);
         if (building.culture != null && building.culture.getVillageType(vtype) != null) {
            building.villageType = building.culture.getVillageType(vtype);
         } else if (building.culture != null && building.culture.getLoneBuildingType(vtype) != null) {
            building.villageType = building.culture.getLoneBuildingType(vtype);
         }

         building.location = StreamReadWrite.readNullableBuildingLocation(ds);
         building.addTags(StreamReadWrite.readStringCollection(ds), "reading tags client-side");
         building.buildingGoal = StreamReadWrite.readNullableString(ds);
         building.buildingGoalIssue = StreamReadWrite.readNullableString(ds);
         building.buildingGoalLevel = ds.readInt();
         building.buildingGoalVariation = ds.readInt();
         building.buildingGoalLocation = StreamReadWrite.readNullableBuildingLocation(ds);
         List<Boolean> isCIPwall = StreamReadWrite.readBooleanList(ds);
         List<BuildingLocation> bls = StreamReadWrite.readBuildingLocationList(ds);
         building.getConstructionsInProgress().clear();
         int cip_id = 0;

         for (BuildingLocation bl : bls) {
            ConstructionIP cip = new ConstructionIP(building, cip_id, isCIPwall.get(cip_id));
            building.getConstructionsInProgress().add(cip);
            cip.setBuildingLocation(bl);
            cip_id++;
         }

         building.buildingProjects = StreamReadWrite.readProjectListList(ds, building.culture);
         building.buildings = StreamReadWrite.readPointList(ds);
         building.buildingsBought = StreamReadWrite.readStringList(ds);
         building.relations = StreamReadWrite.readPointIntegerMap(ds);
         building.raidsPerformed = StreamReadWrite.readStringList(ds);
         building.raidsSuffered = StreamReadWrite.readStringList(ds);
         Map<Long, VillagerRecord> vrecords = StreamReadWrite.readVillagerRecordMap(mw, ds);

         for (VillagerRecord villagerRecord : vrecords.values()) {
            mw.registerVillagerRecord(villagerRecord, false);
         }

         building.pujas = StreamReadWrite.readOrUpdateNullablePuja(ds, building, building.pujas);
         building.visitorsList = StreamReadWrite.readStringList(ds);
         building.imported = StreamReadWrite.readInventory(ds);
         building.exported = StreamReadWrite.readInventory(ds);
         building.name = StreamReadWrite.readNullableString(ds);
         building.qualifier = StreamReadWrite.readNullableString(ds);
         building.raidTarget = StreamReadWrite.readNullablePoint(ds);
         building.raidPlanningStart = ds.readLong();
         building.raidStart = ds.readLong();
         building.resManager.readDataStream(ds);
         if (building.isTownhall && building.villageType.isMarvel()) {
            building.marvelManager = new MarvelManager(building);
            building.marvelManager.readDataStream(ds);
         }
      } catch (IOException var13) {
         MillLog.printException(var13);
      }

      if (newbuilding) {
         mw.addBuilding(building, pos);
      }
   }

   public static void readShopPacket(MillWorldData mw, FriendlyByteBuf ds) {
      Point pos = null;
      pos = StreamReadWrite.readNullablePoint(ds);
      Building building = mw.getBuilding(pos);
      if (building == null) {
         MillLog.error(null, "Received shop packet for null building at: " + pos);
      } else {
         try {
            int nbSells = ds.readInt();
            if (nbSells > 0) {
               LinkedHashMap<TradeGood, Integer> shopSellsPlayer = new LinkedHashMap<>();

               for (int i = 0; i < nbSells; i++) {
                  TradeGood g = StreamReadWrite.readNullableGoods(ds, building.culture);
                  shopSellsPlayer.put(g, ds.readInt());
               }

               building.shopSells.put(Mill.proxy.getSinglePlayerName(), shopSellsPlayer);
            }

            int nbBuys = ds.readInt();
            if (nbBuys > 0) {
               LinkedHashMap<TradeGood, Integer> shopBuysPlayer = new LinkedHashMap<>();

               for (int i = 0; i < nbBuys; i++) {
                  TradeGood g = StreamReadWrite.readNullableGoods(ds, building.culture);
                  shopBuysPlayer.put(g, ds.readInt());
               }

               building.shopBuys.put(Mill.proxy.getSinglePlayerName(), shopBuysPlayer);
            }
         } catch (MillLog.MillenaireException var9) {
            MillLog.printException(var9);
         }
      }
   }

   private Building(MillWorldData mw) {
      this.mw = mw;
      this.world = mw.world;
   }

   public Building(
      MillWorldData mw, Culture c, VillageType villageType, BuildingLocation location, boolean townHall, boolean villageCreation, Point townHallPos
   ) {
      this.pos = location.chestPos;
      this.mw = mw;
      this.world = mw.world;
      this.location = location.clone();
      if (location.getPlan() != null) {
         this.addTags(location.getPlan().tags, "Adding plan tags");
      }

      this.culture = c;
      this.villageType = villageType;
      if (this.world == null) {
         MillLog.MillenaireException e = new MillLog.MillenaireException("Null world!");
         MillLog.printException(e);
      }

      if (this.pos == null) {
         MillLog.MillenaireException e = new MillLog.MillenaireException("Null pos!");
         MillLog.printException(e);
      }

      if (this.location == null) {
         MillLog.MillenaireException e = new MillLog.MillenaireException("Null location!");
         MillLog.printException(e);
      }

      if (this.culture == null) {
         MillLog.MillenaireException e = new MillLog.MillenaireException("Null culture!");
         MillLog.printException(e);
      }

      mw.addBuilding(this, location.chestPos);
      this.isTownhall = townHall;
      this.regionMapper = null;
      if (this.isTownhall) {
         this.townHallPos = this.getPos();
      } else {
         this.townHallPos = townHallPos;
      }

      this.isTownhall = townHall;
      if (this.containsTags("inn") && !this.isTownhall) {
         this.isInn = true;
      }

      if (this.containsTags("market") && !this.isTownhall) {
         this.isMarket = true;
         this.hasVisitors = true;
      }

      if (this.containsTags("autospawnvillagers")) {
         this.hasAutoSpawn = true;
      }

      if (!location.getVisitors().isEmpty()) {
         this.hasVisitors = true;
      }

      if (this.containsTags("pujas")) {
         this.pujas = new PujaSacrifice(this, (short)0);
      }

      if (this.containsTags("sacrifices")) {
         this.pujas = new PujaSacrifice(this, (short)1);
      }

      if (this.isTownhall && villageType.isMarvel()) {
         this.marvelManager = new MarvelManager(this);
      }
   }

   public Building(MillWorldData mw, CompoundTag nbttagcompound) {
      this.mw = mw;
      this.world = mw.world;
      this.readFromNBT(nbttagcompound);
      if (this.pos == null) {
         MillLog.MillenaireException e = new MillLog.MillenaireException("Null pos!");
         MillLog.printException(e);
      }

      mw.addBuilding(this, this.pos);
   }

   public void addAdult(MillVillager child) throws MillLog.MillenaireException {
      String type = null;
      HashMap<String, Integer> villagerCount = new HashMap<>();
      HashMap<String, Integer> residentCount = new HashMap<>();
      List<String> residents;
      if (child.gender == 1) {
         residents = this.location.getMaleResidents();
      } else {
         residents = this.location.getFemaleResidents();
      }

      for (String s : residents) {
         if (residentCount.containsKey(s)) {
            residentCount.put(s, residentCount.get(s) + 1);
         } else {
            residentCount.put(s, 1);
         }
      }

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (villagerCount.containsKey(vr.type)) {
            villagerCount.put(vr.type, villagerCount.get(vr.type) + 1);
         } else {
            villagerCount.put(vr.type, 1);
         }
      }

      for (String sx : residentCount.keySet()) {
         if (!villagerCount.containsKey(sx)) {
            type = sx;
         } else if (villagerCount.get(sx) < residentCount.get(sx)) {
            type = sx;
         }
      }

      if (type != null) {
         if (MillConfigValues.LogWorldGeneration >= 1) {
            MillLog.major(this, "Creating " + type + " with child " + child.getName() + "/" + child.getVillagerId());
         }

         this.mw.removeVillagerRecord(child.getVillagerId());
         VillagerRecord adultRecord = VillagerRecord.createVillagerRecord(
            this.culture, type, this.mw, this.getPos(), this.getTownHallPos(), child.firstName, child.familyName, child.getVillagerId(), false
         );
         VillagerRecord childRecord = child.getRecord();
         if (childRecord != null) {
            adultRecord.rightHanded = childRecord.rightHanded;
         }

         MillVillager adult = MillVillager.createVillager(adultRecord, this.world, child.getPos(), false);
         if (adult == null) {
            MillLog.error(this, "Couldn't create adult of type " + type + " from child " + child);
         } else {
            adultRecord.updateRecord(adult);

            for (VillagerRecord vrx : this.getVillagerRecords().values()) {
               if (vrx.gender != adult.gender) {
                  if (adult.gender == 2) {
                     adultRecord.maidenName = adultRecord.familyName;
                     adultRecord.familyName = vrx.familyName;
                     adult.familyName = vrx.familyName;
                  }

                  if (vrx.gender == 2) {
                     vrx.maidenName = vrx.familyName;
                     vrx.familyName = adult.familyName;
                     MillVillager spouse = this.mw.getVillagerById(vrx.getVillagerId());
                     if (spouse != null) {
                        spouse.familyName = vrx.familyName;
                     }
                  }

                  adultRecord.spousesName = vrx.getName();
                  vrx.spousesName = adult.getVillagerName();
               }
            }

            child.despawnVillager();
            this.world.addFreshEntity(adult);
            if (this.isInn) {
               this.merchantCreated(adultRecord);
            } else {
               this.getPanelManager().updateSigns();
            }
         }
      } else {
         MillLog.error(this, "Could not find a villager type to create. Gender: " + child.gender);
         MillLog.error(
            this,
            "Villager types: "
               + (
                  child.gender == 1
                     ? MillCommonUtilities.flattenStrings(this.location.getMaleResidents())
                     : MillCommonUtilities.flattenStrings(this.location.getFemaleResidents())
               )
         );
         String sxx = "";

         for (VillagerRecord vrxx : this.getVillagerRecords().values()) {
            sxx = sxx + vrxx.type + " ";
         }

         MillLog.error(this, "Current residents: " + sxx);
      }
   }

   public void addCustomBuilding(BuildingCustomPlan customBuilding, Point pos) throws MillLog.MillenaireException {
      BuildingLocation location = new BuildingLocation(customBuilding, pos, false);
      Building building = new Building(this.mw, this.culture, this.villageType, location, false, false, this.getPos());
      customBuilding.registerResources(building, location);
      building.initialise(null, false);
      this.registerBuildingEntity(building);
      BuildingProject project = new BuildingProject(customBuilding, location);
      if (!this.buildingProjects.containsKey(BuildingProject.EnumProjects.CUSTOMBUILDINGS)) {
         this.buildingProjects.put(BuildingProject.EnumProjects.CUSTOMBUILDINGS, new CopyOnWriteArrayList<>());
      }

      this.buildingProjects.get(BuildingProject.EnumProjects.CUSTOMBUILDINGS).add(project);
      if (MillConfigValues.LogBuildingPlan >= 1) {
         MillLog.major(this, "Created new Custom Building Entity: " + customBuilding.buildingKey + " at " + pos);
      }
   }

   public void addTags(Collection<String> newTags, String reason) {
      int nbTags = this.tags.size();
      List<String> addedTags = new ArrayList<>();

      for (String tag : newTags) {
         if (!this.tags.contains(tag)) {
            addedTags.add(tag);
            this.tags.add(tag);
         }
      }

      if (MillConfigValues.LogTags >= 1 && addedTags.size() > 0 && !reason.contains("client-side")) {
         MillLog.major(
            this,
            "Added tags due to '"
               + reason
               + "': "
               + MillCommonUtilities.flattenStrings(addedTags)
               + ", went from "
               + nbTags
               + " to "
               + this.tags.size()
               + ". Current tags: "
               + MillCommonUtilities.flattenStrings(this.tags)
         );
      }
   }

   public void addToExports(InvItem good, int quantity) {
      if (this.exported.containsKey(good)) {
         this.exported.put(good, this.exported.get(good) + quantity);
      } else {
         this.exported.put(good, quantity);
      }
   }

   public void addToImports(InvItem good, int quantity) {
      if (this.imported.containsKey(good)) {
         this.imported.put(good, this.imported.get(good) + quantity);
      } else {
         this.imported.put(good, quantity);
      }
   }

   public void adjustLanguage(Player player, int l) {
      this.mw.getProfile(player).adjustLanguage(this.getTownHall().culture.key, l);
   }

   public void adjustRelation(Point villagePos, int change, boolean reset) {
      int relation = change;
      if (this.relations.containsKey(villagePos) && !reset) {
         relation = change + this.relations.get(villagePos);
      }

      if (relation > 100) {
         relation = 100;
      } else if (relation < -100) {
         relation = -100;
      }

      this.relations.put(villagePos, relation);
      this.saveNeeded = true;
      if (!this.isActive) {
         this.saveTownHall("distance relation change");
      }

      Building village = this.mw.getBuilding(villagePos);
      if (village == null) {
         MillLog.error(this, "Could not find village at " + villagePos + " in order to adjust relation.");
      } else {
         village.relations.put(this.getPos(), relation);
         village.saveTownHall("distance relation change");
      }
   }

   public void adjustReputation(Player player, int l) {
      this.mw.getProfile(player).adjustReputation(this.getTownHall(), l);
   }

   public void attemptMerchantMove(boolean forced) {
      List<Building> targets = new ArrayList<>();
      List<Building> backupTargets = new ArrayList<>();

      for (Point vp : this.getTownHall().relations.keySet()) {
         Building townHall = this.mw.getBuilding(vp);
         if (townHall != null
            && this.getTownHall() != null
            && townHall.villageType != this.getTownHall().villageType
            && (this.getTownHall().relations.get(vp) >= 0 || this.getTownHall().relations.get(vp) >= 0 && townHall.culture == this.culture)
            && this.getPos().distanceTo(townHall.getPos()) < 2000.0) {
            if (MillConfigValues.LogMerchant >= 2) {
               MillLog.debug(this, "Considering village " + townHall.getVillageQualifiedName() + " for merchant : " + this.merchantRecord);
            }

            for (Building inn : townHall.getBuildingsWithTag("inn")) {
               boolean moveNeeded = false;
               HashMap<InvItem, Integer> content = this.resManager.getChestsContent();

               for (InvItem good : content.keySet()) {
                  if (content.get(good) > 0 && inn.getTownHall().nbGoodNeeded(good.getItem(), good.meta) > 0) {
                     moveNeeded = true;
                     break;
                  }
               }

               if (moveNeeded) {
                  if (inn.merchantRecord == null) {
                     targets.add(inn);
                     targets.add(inn);
                     targets.add(inn);
                  } else if (inn.nbNightsMerchant > 1 || forced) {
                     targets.add(inn);
                  }

                  if (MillConfigValues.LogMerchant >= 2) {
                     MillLog.debug(this, "Found good move in " + townHall.getVillageQualifiedName() + " for merchant : " + this.merchantRecord);
                  }
               } else if (this.nbNightsMerchant > 3) {
                  backupTargets.add(inn);
                  if (MillConfigValues.LogMerchant >= 2) {
                     MillLog.debug(this, "Found backup move in " + townHall.getVillageQualifiedName() + " for merchant : " + this.merchantRecord);
                  }
               }
            }
         }
      }

      if (targets.size() == 0 && backupTargets.size() == 0) {
         if (MillConfigValues.LogMerchant >= 2) {
            MillLog.minor(this, "Failed to find a destination for merchant: " + this.merchantRecord);
         }
      } else {
         Building inn;
         if (targets.size() > 0) {
            inn = targets.get(MillCommonUtilities.randomInt(targets.size()));
         } else {
            inn = backupTargets.get(MillCommonUtilities.randomInt(backupTargets.size()));
         }

         if (inn.merchantRecord == null) {
            this.moveMerchant(inn);
         } else if (inn.nbNightsMerchant > 1 || forced) {
            this.swapMerchants(inn);
         }
      }
   }

   private void attemptPlanNewRaid() {
      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (vr.raidingVillage) {
            return;
         }
      }

      int raidingStrength = (int)(this.getVillageRaidingStrength() * 2.0F);
      if (MillConfigValues.LogDiplomacy >= 3) {
         MillLog.debug(this, "Checking out for new raid, strength: " + raidingStrength);
      }

      if (raidingStrength > 0) {
         List<Building> targets = new ArrayList<>();
         if (this.villageType.lonebuilding) {
            for (Building distantVillage : this.mw.allBuildings()) {
               if (distantVillage != null
                  && distantVillage.isTownhall
                  && distantVillage.villageType != null
                  && !distantVillage.villageType.lonebuilding
                  && this.getPos().distanceTo(distantVillage.getPos()) < MillConfigValues.BanditRaidRadius
                  && distantVillage.getVillageDefendingStrength() < raidingStrength) {
                  if (MillConfigValues.LogDiplomacy >= 3) {
                     MillLog.debug(this, "Lone building valid target: " + distantVillage);
                  }

                  targets.add(distantVillage);
               }
            }
         } else {
            for (Point p : this.relations.keySet()) {
               if (this.relations.get(p) < -90) {
                  Building distantVillagex = this.mw.getBuilding(p);
                  if (distantVillagex != null) {
                     if (MillConfigValues.LogDiplomacy >= 3) {
                        MillLog.debug(this, "Testing village valid target: " + distantVillagex + "/" + distantVillagex.getVillageDefendingStrength());
                     }

                     if (distantVillagex.getVillageDefendingStrength() < raidingStrength) {
                        if (MillConfigValues.LogDiplomacy >= 3) {
                           MillLog.debug(this, "Village valid target: " + distantVillagex);
                        }

                        targets.add(distantVillagex);
                     }
                  }
               }
            }
         }

         if (!targets.isEmpty()) {
            Building target = targets.get(MillCommonUtilities.randomInt(targets.size()));
            if (this.isActive || target.isActive) {
               this.planRaid(target);
            }
         }
      }
   }

   public List<TradeGood> calculateBuyingGoods(Container playerInventory) {
      if (!this.culture.shopBuys.containsKey(this.location.shop) && !this.culture.shopBuysOptional.containsKey(this.location.shop)) {
         return null;
      } else {
         List<TradeGood> baseGoods = this.culture.shopBuys.get(this.location.shop);
         List<TradeGood> extraGoods = new ArrayList<>();
         if (this.culture.shopBuysOptional.containsKey(this.location.shop)) {
            for (TradeGood g : this.culture.shopBuysOptional.get(this.location.shop)) {
               if (playerInventory == null || MillCommonUtilities.countChestItems(playerInventory, g.item.getItem(), g.item.meta) > 0) {
                  extraGoods.add(g);
               }
            }
         }

         if (this.isTownhall) {
            BuildingPlan goalPlan = this.getCurrentGoalBuildingPlan();
            if (goalPlan != null) {
               for (InvItem key : goalPlan.resCost.keySet()) {
                  if (key.meta != -1) {
                     boolean found = false;

                     for (TradeGood tg : baseGoods) {
                        if (tg.item.getItem() == key.getItem() && tg.item.meta == key.meta) {
                           found = true;
                        }
                     }

                     if (!found) {
                        if (this.culture.getTradeGood(key) != null) {
                           extraGoods.add(this.culture.getTradeGood(key));
                        } else {
                           extraGoods.add(new TradeGood("generated", this.culture, key));
                        }
                     }
                  }
               }
            }
         }

         if (extraGoods.size() == 0) {
            return baseGoods;
         } else {
            List<TradeGood> finalGoods = new ArrayList<>();

            for (TradeGood good : baseGoods) {
               finalGoods.add(good);
            }

            for (TradeGood good : extraGoods) {
               finalGoods.add(good);
            }

            return finalGoods;
         }
      }
   }

   private void calculateInventoryCache() {
      this.inventoryCache = new HashMap<>();

      for (Point p : this.resManager.chests) {
         TileEntityLockedChest chest = p.getMillChest(this.world);
         if (chest != null) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
               ItemStack stack = chest.getItem(i);
               if (!stack.isEmpty()) {
                  InvItem invItem = InvItem.createInvItem(stack);
                  if (this.inventoryCache.containsKey(invItem)) {
                     this.inventoryCache.put(invItem, this.inventoryCache.get(invItem) + stack.getCount());
                  } else {
                     this.inventoryCache.put(invItem, stack.getCount());
                  }
               }
            }
         }
      }

      for (Point px : this.resManager.furnaces) {
         net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace = px.getFurnace(this.world);
         if (furnace != null) {
            ItemStack stack = furnace.getItem(2);
            if (stack != null && !stack.isEmpty()) {
               InvItem invItem = InvItem.createInvItem(stack);
               if (this.inventoryCache.containsKey(invItem)) {
                  this.inventoryCache.put(invItem, this.inventoryCache.get(invItem) + stack.getCount());
               } else {
                  this.inventoryCache.put(invItem, stack.getCount());
               }
            }
         }
      }

      for (Point pxx : this.resManager.firepits) {
         TileEntityFirePit firepit = pxx.getFirePit(this.world);
         if (firepit != null) {
            for (int slotPos = 0; slotPos < 3; slotPos++) {
               ItemStack stack = firepit.getItem(slotPos + 4);
               if (stack != null && !stack.isEmpty()) {
                  InvItem invItem = InvItem.createInvItem(stack);
                  if (this.inventoryCache.containsKey(invItem)) {
                     this.inventoryCache.put(invItem, this.inventoryCache.get(invItem) + stack.getCount());
                  } else {
                     this.inventoryCache.put(invItem, stack.getCount());
                  }
               }
            }
         }
      }
   }

   public void calculatePathsToClear() {
      if (this.pathsToBuild != null) {
         List<List<BuildingBlock>> pathsToBuildLocal = this.pathsToBuild;
         long startTime = System.currentTimeMillis();
         List<Point> oldPathPointsToClearNew = new ArrayList<>();
         HashSet<Point> newPathPoints = new HashSet<>();

         for (List<BuildingBlock> path : pathsToBuildLocal) {
            for (BuildingBlock bp : path) {
               newPathPoints.add(bp.p);
            }
         }

         int minX = Math.max(this.winfo.mapStartX, this.getPos().getiX() - this.villageType.radius);
         int maxX = Math.min(this.winfo.mapStartX + this.winfo.length - 1, this.getPos().getiX() + this.villageType.radius);
         int minZ = Math.max(this.winfo.mapStartZ, this.getPos().getiZ() - this.villageType.radius);
         int maxZ = Math.min(this.winfo.mapStartZ + this.winfo.width - 1, this.getPos().getiZ() + this.villageType.radius);

         for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
               int basey = this.winfo.topGround[x - this.winfo.mapStartX][z - this.winfo.mapStartZ];

               for (int dy = -2; dy < 4; dy++) {
                  int y = dy + basey;
                  BlockState blockState = WorldUtilities.getBlockState(this.world, x, y, z);
                  if (BlockItemUtilities.isPath(blockState.getBlock()) && !(Boolean)blockState.getValue(IBlockPath.STABLE)) {
                     Point p = new Point(x, y, z);
                     if (!newPathPoints.contains(p)) {
                        oldPathPointsToClearNew.add(p);
                     }
                  }
               }
            }
         }

         this.oldPathPointsToClearIndex = 0;
         this.oldPathPointsToClear = oldPathPointsToClearNew;
         if (MillConfigValues.LogVillagePaths >= 2) {
            MillLog.minor(
               this,
               "Finished looking for paths to clear. Found: "
                  + this.oldPathPointsToClear.size()
                  + ". Duration: "
                  + (System.currentTimeMillis() - startTime)
                  + " ms."
            );
         }
      }
   }

   public List<TradeGood> calculateSellingGoods(Container playerInventory) {
      return !this.culture.shopSells.containsKey(this.location.shop) ? null : this.culture.shopSells.get(this.location.shop);
   }

   public void callForHelp(LivingEntity attacker) {
      if (MillConfigValues.LogGeneralAI >= 3) {
         MillLog.debug(this, "Calling for help among: " + this.getKnownVillagers().size() + " villagers.");
      }

      for (MillVillager villager : this.getKnownVillagers()) {
         if (MillConfigValues.LogGeneralAI >= 3) {
            MillLog.debug(
               villager,
               "Testing villager. Will fight? "
                  + villager.helpsInAttacks()
                  + ". Current target? "
                  + villager.getTarget()
                  + ". Distance to threat: "
                  + villager.getPos().distanceTo(attacker)
            );
         }

         if (villager.getTarget() == null && villager.helpsInAttacks() && !villager.isRaider && villager.getPos().distanceTo(attacker) < 80.0) {
            if (MillConfigValues.LogGeneralAI >= 1) {
               MillLog.major(villager, "Off to help a friend attacked by attacking: " + attacker);
            }

            villager.setTarget(attacker);
            villager.clearGoal();
            villager.speakSentence("calltoarms", 0, 50, 1);
         }
      }
   }

   private boolean canAffordBuild(BuildingPlan plan) {
      if (plan == null) {
         MillLog.error(this, "Needed to compute plan cost but the plan is null.");
         return false;
      } else if (plan.resCost == null) {
         MillLog.error(this, "Needed to compute plan cost but the plan cost map is null.");
         return false;
      } else {
         for (InvItem key : plan.resCost.keySet()) {
            if (plan.resCost.get(key) > this.nbGoodAvailable(key, true, false, false)) {
               return false;
            }
         }

         return true;
      }
   }

   private boolean canAffordProject(BuildingPlan plan) {
      if (plan == null) {
         MillLog.error(this, "Needed to compute plan cost but the plan is null.");
         return false;
      } else if (plan.resCost == null) {
         MillLog.error(this, "Needed to compute plan cost but the plan cost map is null.");
         return false;
      } else {
         for (InvItem key : plan.resCost.keySet()) {
            if (plan.resCost.get(key) > this.countGoods(key)) {
               return false;
            }
         }

         return true;
      }
   }

   public void cancelBuilding(BuildingLocation location) {
      ConstructionIP cip = this.findConstructionIPforLocation(location);
      if (cip != null && location.isLocationSamePlace(cip.getBuildingLocation())) {
         cip.setBuildingLocation(null);
      }

      if (location.isLocationSamePlace(this.buildingGoalLocation)) {
         this.buildingGoalLocation = null;
         this.buildingGoal = null;
      }

      for (List<BuildingProject> projects : this.buildingProjects.values()) {
         for (BuildingProject project : projects) {
            if (project.location == location) {
               projects.remove(project);
               break;
            }
         }
      }

      Building building = location.getBuilding(this.world);
      if (building != null) {
         for (MillVillager villager : new ArrayList<>(building.villagers)) {
            villager.despawnVillagerSilent();
         }

         for (VillagerRecord vr : new ArrayList<>(building.getAllVillagerRecords())) {
            this.mw.removeVillagerRecord(vr.getVillagerId());
         }
      }

      this.buildings.remove(location.pos);
      this.winfo.removeBuildingLocation(location);
      this.mw.removeBuilding(location.pos);
   }

   public void cancelRaid() {
      if (MillConfigValues.LogDiplomacy >= 1) {
         MillLog.major(this, "Cancelling raid");
      }

      this.raidPlanningStart = 0L;
      this.raidStart = 0L;
      this.raidTarget = null;
   }

   public boolean canChildMoveIn(int pGender, String familyName) {
      if (pGender == 2 && this.location.getFemaleResidents().size() == 0) {
         return false;
      } else if (pGender == 1 && this.location.getMaleResidents().size() == 0) {
         return false;
      } else {
         for (VillagerRecord vr : this.getVillagerRecords().values()) {
            if (vr.gender != pGender && !vr.getType().isChild && vr.familyName.equals(familyName) && this.equals(vr.getHouse())) {
               return false;
            }
         }

         int nbAdultSameSex = 0;

         for (VillagerRecord vrx : this.getVillagerRecords().values()) {
            if (vrx.gender == pGender && vrx.getType() != null && !vrx.getType().isChild && this.equals(vrx.getHouse())) {
               nbAdultSameSex++;
            }
         }

         return pGender == 1 && nbAdultSameSex >= this.location.getMaleResidents().size()
            ? false
            : pGender != 2 || nbAdultSameSex < this.location.getFemaleResidents().size();
      }
   }

   public void changeVillageName(String s) {
      this.name = s;

      for (int i = 0; i < this.mw.villagesList.pos.size(); i++) {
         if (this.mw.villagesList.pos.get(i).equals(this.getPos())) {
            this.mw.villagesList.names.set(i, this.getVillageQualifiedName());
         }
      }

      for (int ix = 0; ix < this.mw.loneBuildingsList.pos.size(); ix++) {
         if (this.mw.loneBuildingsList.pos.get(ix).equals(this.getPos())) {
            this.mw.loneBuildingsList.names.set(ix, this.getVillageQualifiedName());
         }
      }
   }

   public void changeVillageQualifier(String s) {
      this.qualifier = s;

      for (int i = 0; i < this.mw.villagesList.pos.size(); i++) {
         if (this.mw.villagesList.pos.get(i).equals(this.getPos())) {
            this.mw.villagesList.names.set(i, this.getVillageQualifiedName());
         }
      }

      for (int ix = 0; ix < this.mw.loneBuildingsList.pos.size(); ix++) {
         if (this.mw.loneBuildingsList.pos.get(ix).equals(this.getPos())) {
            this.mw.loneBuildingsList.names.set(ix, this.getVillageQualifiedName());
         }
      }
   }

   public void checkBattleStatus() {
      int nbAttackers = 0;
      int nbLiveAttackers = 0;
      int nbLiveDefenders = 0;
      Point attackingVillagePos = null;

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (vr.raidingVillage) {
            nbAttackers++;
            if (!vr.killed) {
               nbLiveAttackers++;
            }

            attackingVillagePos = vr.originalVillagePos;
         } else if (vr.getType().helpInAttacks && !vr.killed && !vr.awayraiding && !vr.awayhired) {
            nbLiveDefenders++;
         }
      }

      if (this.isTownhall) {
         if (this.chestLocked && nbLiveDefenders == 0) {
            this.unlockAllChests();
            ServerSender.sendTranslatedSentenceInRange(
               this.world, this.getPos(), MillConfigValues.BackgroundRadius, '4', "ui.allchestsunlocked", this.getVillageQualifiedName()
            );
         } else if (!this.chestLocked && nbLiveDefenders > 0) {
            this.lockAllBuildingsChests();
         }
      }

      if (nbAttackers > 0) {
         this.underAttack = true;
         if (nbLiveAttackers == 0 || nbLiveDefenders == 0) {
            boolean finish = false;
            if (nbLiveAttackers > 0) {
               for (MillVillager v : this.getKnownVillagers()) {
                  if (!finish && v.isRaider && this.resManager.getDefendingPos().distanceToSquared(v) < 25.0) {
                     finish = true;
                  }
               }
            } else {
               finish = true;
            }

            if (finish) {
               if (attackingVillagePos == null) {
                  MillLog.error(this, "Wanted to end raid but can't find originating village's position.");
                  this.clearAllAttackers();
               } else {
                  Building attackingVillage = this.mw.getBuilding(attackingVillagePos);
                  if (attackingVillage == null) {
                     this.clearAllAttackers();
                  } else {
                     boolean endedProperly = attackingVillage.endRaid();
                     if (!endedProperly) {
                        this.clearAllAttackers();
                     }
                  }
               }
            }
         }
      } else {
         this.underAttack = false;
      }
   }

   private void checkExploreTag(Player player) {
      if (player != null
         && this.location.getPlan() != null
         && !this.mw.getProfile(player).isTagSet(this.location.getPlan().exploreTag)
         && this.resManager.getSleepingPos().distanceToSquared(player) < 16.0) {
         boolean valid = true;
         int x = this.resManager.getSleepingPos().getiX();
         int z = this.resManager.getSleepingPos().getiZ();

         while (valid && (x != (int)player.getX() || z != (int)player.getZ())) {
            Block block = WorldUtilities.getBlock(this.world, x, this.resManager.getSleepingPos().getiY() + 1, z);
            if (block != Blocks.AIR && block.defaultBlockState().blocksMotion()) {
               valid = false;
            } else if (x > (int)player.getX()) {
               x--;
            } else if (x < (int)player.getX()) {
               x++;
            } else if (z > (int)player.getZ()) {
               z--;
            } else if (z < (int)player.getZ()) {
               z++;
            }
         }

         if (valid) {
            this.mw.getProfile(player).setTag(this.location.getPlan().exploreTag);
            ServerSender.sendTranslatedSentence(player, '2', "other.exploredbuilding", this.location.getPlan().nativeName);
         }
      }
   }

   private boolean checkProjectValidity(BuildingProject project, BuildingPlan plan) {
      if (plan.requiredGlobalTag != null && !this.mw.isGlobalTagSet(plan.requiredGlobalTag)) {
         return false;
      } else {
         if (!plan.requiredTags.isEmpty()) {
            if (project.location == null) {
               MillLog.error(this, "Plan " + plan + " has required tags but no location.");
               return false;
            }

            Building building = this.getBuildingFromLocation(project.location);
            if (building == null) {
               MillLog.error(this, "Plan " + plan + " has required tags but building can't be found.");
               return false;
            }

            for (String tag : plan.requiredTags) {
               if (!building.containsTags(tag)) {
                  if (MillConfigValues.LogTags >= 2) {
                     MillLog.minor(this, "Can't build plan " + plan + " as building is missing tag:" + tag);
                  }

                  return false;
               }
            }
         }

         if (!plan.forbiddenTagsInVillage.isEmpty()) {
            for (String forbiddenTag : plan.forbiddenTagsInVillage) {
               Building matchingBuilding = this.getFirstBuildingWithTag(forbiddenTag);
               if (matchingBuilding != null) {
                  if (MillConfigValues.LogTags >= 2) {
                     MillLog.minor(this, "Can't build plan " + plan + " as building " + matchingBuilding + " has tag " + forbiddenTag);
                  }

                  return false;
               }
            }
         }

         if (plan.level > 0 && plan.containsTags("no_upgrade_till_wall_initialized")) {
            for (BuildingProject existingProject : this.getFlatProjectList()) {
               BuildingPlan existingProjectPlan = existingProject.getNextBuildingPlan(false);
               if (existingProjectPlan != null && existingProjectPlan.isWallSegment && existingProjectPlan.level == 0) {
                  if (MillConfigValues.LogTags >= 2) {
                     MillLog.minor(this, "Can't build plan " + plan + " as it requires all wall segments to be initialized.");
                  }

                  return false;
               }
            }
         }

         for (String tagx : plan.requiredVillageTags) {
            if (!this.containsTags(tagx)) {
               if (MillConfigValues.LogTags >= 2) {
                  MillLog.minor(this, "Can't build plan " + plan + " as village is missing tag:" + tagx);
               }

               return false;
            }
         }

         if (project.location != null && !plan.requiredParentTags.isEmpty()) {
            Building parent = null;

            for (BuildingLocation alocation : this.getLocations()) {
               if (!alocation.isSubBuildingLocation && alocation.pos.equals(project.location.pos)) {
                  parent = alocation.getBuilding(this.world);
               }
            }

            if (parent == null) {
               MillLog.error(this, "Building plan " + plan + " has required parent tags but the parent for location " + project.location + " cannot be found.");
               return false;
            }

            for (String tagxx : plan.requiredParentTags) {
               if (!parent.containsTags(tagxx)) {
                  if (MillConfigValues.LogTags >= 2) {
                     MillLog.temp(this, "Can't build plan " + plan + " as parent is missing tag: " + tagxx);
                  }

                  return false;
               }
            }
         }

         return true;
      }
   }

   public void checkSeller() {
      if (this.world.isBrightOutside() && !this.underAttack) {
         if (this.closestPlayer != null && !this.controlledBy(this.closestPlayer)) {
            if (!this.chestLocked && MillConfigValues.LogSelling >= 2) {
               MillLog.minor(this, "Not sending seller because village chests are not locked.");
            }

            if (this.getReputation(this.closestPlayer) < -1024 && MillConfigValues.LogSelling >= 2) {
               MillLog.minor(this, "Not sending seller because player's reputation is not sufficient: " + this.getReputation(this.closestPlayer));
            }

            if (this.closestPlayer != null && this.seller == null && this.getReputation(this.closestPlayer) >= -1024 && this.chestLocked) {
               this.sellingPlace = null;
               if (MillConfigValues.LogSelling >= 2) {
                  MillLog.minor(this, "A seller is required for " + this.closestPlayer.getName());
               }

               for (BuildingLocation l : this.getLocations()) {
                  if (l.level >= 0 && l.chestPos != null && l.shop != null && l.shop.length() > 0) {
                     if (l.getSellingPos() != null && l.getSellingPos().distanceTo(this.closestPlayer) < 3.0) {
                        this.sellingPlace = l.getSellingPos();
                     } else if (l.getSellingPos() == null && l.sleepingPos.distanceTo(this.closestPlayer) < 3.0) {
                        this.sellingPlace = l.sleepingPos;
                     }
                  }
               }

               if (this.sellingPlace == null && MillConfigValues.LogSelling >= 2) {
                  MillLog.minor(this, "Can't send player because there is no selling place.");
               }

               if (this.sellingPlace != null) {
                  if (MillConfigValues.LogSelling >= 2) {
                     MillLog.minor(this, "Checking through villagers to find a seller.");
                  }

                  for (MillVillager villager : this.getKnownVillagers()) {
                     if (villager.isSeller()
                        && this.getConstructionIPforBuilder(villager) == null
                        && (this.seller == null || this.sellingPlace.distanceToSquared(villager) < this.sellingPlace.distanceToSquared(this.seller))) {
                        this.seller = villager;
                     }
                  }

                  if (this.seller != null) {
                     this.seller.clearGoal();
                     this.seller.goalKey = Goal.beSeller.key;
                     Goal.beSeller.onAccept(this.seller);
                     if (MillConfigValues.LogSelling >= 3) {
                        MillLog.debug(this, "Sending seller: " + this.seller);
                     }
                  }
               }
            }
         } else {
            if (this.closestPlayer == null && MillConfigValues.LogSelling >= 3) {
               MillLog.debug(this, "Not sending seller because there are no nearby player.");
            } else if (this.closestPlayer != null && this.controlledBy(this.closestPlayer) && MillConfigValues.LogSelling >= 2) {
               MillLog.minor(this, "Not sending seller because the nearby player owns the village.");
            }
         }
      } else {
         if (MillConfigValues.LogSelling >= 2) {
            MillLog.major(this, "Not sending seller because either: !world.isBrightOutside(): " + !this.world.isBrightOutside() + " or underAttack: " + this.underAttack);
         }
      }
   }

   public void checkWorkers() {
      if (this.seller != null && !Goal.beSeller.key.equals(this.seller.goalKey)) {
         this.seller = null;
      }

      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         if (cip.getBuilder() != null
            && (
               cip.getBuilder().goalKey != null
                     && !Goal.getResourcesForBuild.key.equals(cip.getBuilder().goalKey)
                     && !Goal.construction.key.equals(cip.getBuilder().goalKey)
                  || cip.getId() != cip.getBuilder().constructionJobId
            )) {
            if (MillConfigValues.LogBuildingPlan >= 1) {
               MillLog.major(this, cip.getBuilder().getName() + " is no longer building.");
            }

            cip.setBuilder(null);
         }
      }
   }

   public void choseAndApplyBrickTheme() {
      if (!this.villageType.brickColourThemes.isEmpty()) {
         this.brickColourTheme = (VillageType.BrickColourTheme)MillCommonUtilities.getWeightedChoice(this.villageType.brickColourThemes, null);

         for (Building b : this.getBuildings()) {
            if (b.location.getPlan().randomBrickColours.isEmpty()) {
               b.location.initialiseBrickColoursFromTheme(this, this.brickColourTheme);

               for (int x = b.location.minx; x <= b.location.maxx; x++) {
                  for (int z = b.location.minz; z <= b.location.maxz; z++) {
                     for (int y = b.location.miny - 20; y <= b.location.maxy + 20; y++) {
                        BlockPos bp = new BlockPos(x, y, z);
                        BlockState bs = this.world.getBlockState(bp);
                        if (bs.getBlock() instanceof IPaintedBlock) {
                           DyeColor currentColor = BlockPaintedBricks.getColourFromBlockState(bs);
                           if (b.location.paintedBricksColour.containsKey(currentColor)) {
                              this.world.setBlock(bp, BlockPaintedBricks.getBlockStateWithColour(bs, b.location.paintedBricksColour.get(currentColor)), 3);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void clearAllAttackers() {
      int nbCleared = 0;

      for (VillagerRecord vr : new ArrayList<>(this.getAllVillagerRecords())) {
         if (vr.raidingVillage) {
            this.mw.removeVillagerRecord(vr.getVillagerId());
            nbCleared++;
         }
      }

      if (MillConfigValues.LogDiplomacy >= 1) {
         MillLog.major(this, "Cleared " + nbCleared + " attackers.");
      }

      for (MillVillager villager : new HashSet<>(this.getKnownVillagers())) {
         if (villager.isRaider) {
            villager.despawnVillagerSilent();
            if (MillConfigValues.LogDiplomacy >= 1) {
               MillLog.major(this, "Despawning invader: " + villager);
            }
         }
      }
   }

   public void clearOldPaths() {
      if (this.oldPathPointsToClear != null) {
         for (Point p : this.oldPathPointsToClear) {
            PathUtilities.clearPathBlock(p, this.world);
         }

         this.oldPathPointsToClear = null;
         this.pathsChanged = true;
         this.requestSave("paths clearing rushed");
      }
   }

   public void clearTags(Collection<String> tagsToClear, String reason) {
      int nbTags = this.tags.size();
      List<String> clearedTags = new ArrayList<>();

      for (String tag : tagsToClear) {
         if (this.tags.contains(tag)) {
            clearedTags.add(tag);
            this.tags.remove(tag);
         }
      }

      if (MillConfigValues.LogTags >= 1 && clearedTags.size() > 0 && !reason.contains("client-side")) {
         MillLog.major(
            this,
            "Cleared tags due to '"
               + reason
               + "': "
               + MillCommonUtilities.flattenStrings(clearedTags)
               + ", went from "
               + nbTags
               + " to "
               + this.tags.size()
               + ". Current tags: "
               + MillCommonUtilities.flattenStrings(this.tags)
         );
      }
   }

   private void completeConstruction(ConstructionIP cip) throws MillLog.MillenaireException {
      if (cip.getBuildingLocation() != null && cip.getBblocks() == null) {
         BuildingPlan plan = this.getBuildingPlanForConstruction(cip);
         this.registerBuildingLocation(cip.getBuildingLocation());
         this.updateWorldInfo();
         if (cip.getBuildingLocation() != null && cip.getBuildingLocation().isSameLocation(this.buildingGoalLocation)) {
            this.buildingGoalLocation = null;
            this.buildingGoal = null;
            this.buildingGoalIssue = null;
            this.buildingGoalLevel = -1;
         }

         cip.setBuilder(null);
         cip.setBuildingLocation(null);
         if (plan.rebuildPath || plan.level == 0 || plan.getPreviousBuildingPlan().pathLevel != plan.pathLevel) {
            this.recalculatePaths(false);
         }
      }
   }

   public int computeCurrentWallLevel() {
      int wallLevel = Integer.MAX_VALUE;
      boolean wallFound = false;

      for (BuildingProject project : this.getFlatProjectList()) {
         BuildingPlan initialPlan = project.getPlan(0, 0);
         BuildingPlan plan = project.getNextBuildingPlan(false);
         if (plan != null && plan.isWallSegment) {
            wallFound = true;
            if (this.isValidProject(project)) {
               for (int i = 0; i < 10; i++) {
                  if (i < wallLevel && plan.containsTags("wall_level_" + i)) {
                     wallLevel = i;
                  }
               }
            }
         } else if (initialPlan != null && initialPlan.isWallSegment) {
            wallFound = true;
         }
      }

      return !wallFound ? -1 : wallLevel;
   }

   public void computeShopGoods(Player player) {
      List<TradeGood> sellingGoods = this.calculateSellingGoods(player.getInventory());
      if (sellingGoods != null) {
         LinkedHashMap<TradeGood, Integer> shopSellsPlayer = new LinkedHashMap<>();

         for (TradeGood g : sellingGoods) {
            if (g.getBasicSellingPrice(this) > 0) {
               shopSellsPlayer.put(g, g.getBasicSellingPrice(this));
            }
         }

         this.shopSells.put(player.getName().getString(), shopSellsPlayer);
      }

      List<TradeGood> buyingGoods = this.calculateBuyingGoods(player.getInventory());
      if (buyingGoods != null) {
         LinkedHashMap<TradeGood, Integer> shopBuysPlayer = new LinkedHashMap<>();

         for (TradeGood gx : buyingGoods) {
            if (gx.getBasicBuyingPrice(this) > 0) {
               shopBuysPlayer.put(gx, gx.getBasicBuyingPrice(this));
            }
         }

         this.shopBuys.put(player.getName().getString(), shopBuysPlayer);
      }
   }

   public void constructCalculatedPaths() {
      if (this.pathsToBuild != null) {
         if (MillConfigValues.LogVillagePaths >= 2) {
            MillLog.minor(this, "Rebuilding calculated paths.");
         }

         for (List<BuildingBlock> path : this.pathsToBuild) {
            if (!path.isEmpty()) {
               for (BuildingBlock bp : path) {
                  bp.pathBuild(this);
               }
            }
         }

         this.pathsToBuild = null;
         this.pathsChanged = true;
         this.requestSave("paths rushed");
      }
   }

   public boolean containsTags(String tag) {
      return this.tags.contains(tag.toLowerCase());
   }

   public boolean controlledBy(Player player) {
      if (!this.isTownhall && this.getTownHall() != null) {
         if (this.getTownHall() == this) {
            MillLog.error(this, "isTownHall is false but building is its own Town Hall.");
            return false;
         } else {
            return this.getTownHall().controlledBy(player);
         }
      } else {
         return this.controlledBy != null && this.controlledBy.equals(this.mw.getProfile(player).uuid);
      }
   }

   public int countChildren() {
      int nb = 0;

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (vr.getType() != null && vr.getType().isChild) {
            nb++;
         }
      }

      return nb;
   }

   public int countGoods(Block block, int meta) {
      return this.countGoods(block.asItem(), meta);
   }

   public int countGoods(BlockState blockState) {
      return this.countGoods(blockState.getBlock().asItem(), 0);
   }

   public int countGoods(InvItem iv) {
      return this.countGoods(iv.getItem(), iv.meta);
   }

   public int countGoods(Item item) {
      return this.countGoods(item, 0);
   }

   public int countGoods(Item item, int meta) {
      return this.getInventoryCountFromCache(InvItem.createInvItem(item, meta));
   }

   public int countGoodsOld(Item item, int meta) {
      int count = 0;

      for (Point p : this.resManager.chests) {
         TileEntityLockedChest chest = p.getMillChest(this.world);
         count += MillCommonUtilities.countChestItems(chest, item, meta);
      }

      return count;
   }

   public int countVillageGoods(InvItem iv) {
      int count = 0;

      for (Building b : this.getBuildings()) {
         count += b.countGoods(iv.getItem(), iv.meta);
      }

      return count;
   }

   public MillVillager createChild(MillVillager mother, Building townHall, String fathersName) {
      try {
         if (MillConfigValues.LogWorldGeneration >= 2) {
            MillLog.minor(this, "Creating child: " + mother.familyName);
         }

         int gender = this.getNewGender();
         String type = gender == 1 ? mother.getMaleChild() : mother.getFemaleChild();
         VillagerRecord vr = VillagerRecord.createVillagerRecord(
            townHall.culture, type, this.mw, this.getPos(), this.getTownHallPos(), null, mother.familyName, -1L, false
         );
         MillVillager child = MillVillager.createVillager(vr, this.world, this.resManager.getSleepingPos(), false);
         if (child == null) {
            throw new MillLog.MillenaireException("Child not instancied in createVillager");
         } else {
            vr.fathersName = fathersName;
            vr.mothersName = mother.getVillagerName();
            this.world.addFreshEntity(child);
            return child;
         }
      } catch (Exception var8) {
         Mill.proxy.sendChatAdmin("Error in createChild(). Check millenaire.log.");
         MillLog.error(this, "Exception in createChild.onUpdate(): ");
         MillLog.printException(var8);
         return null;
      }
   }

   public MillVillager createNewVillager(String type) throws MillLog.MillenaireException {
      VillagerRecord vr = VillagerRecord.createVillagerRecord(this.culture, type, this.mw, this.getPos(), this.getTownHallPos(), null, null, -1L, false);
      MillVillager villager = MillVillager.createVillager(vr, this.world, this.resManager.getSleepingPos(), false);
      this.world.addFreshEntity(villager);
      if (villager.vtype.isChild) {
         vr.size = 20;
         villager.growSize();
      }

      return villager;
   }

   public String createResidents() throws MillLog.MillenaireException {
      if (this.location.getMaleResidents().size() + this.location.getFemaleResidents().size() == 0) {
         return null;
      } else {
         String familyName = null;
         String husbandType = null;
         if (this.location.getMaleResidents().size() > 0 && !this.culture.getVillagerType(this.location.getMaleResidents().get(0)).isChild) {
            husbandType = this.location.getMaleResidents().get(0);
         }

         String wifeType = null;
         if (this.location.getFemaleResidents().size() > 0 && !this.culture.getVillagerType(this.location.getFemaleResidents().get(0)).isChild) {
            wifeType = this.location.getFemaleResidents().get(0);
         }

         if (MillConfigValues.LogMerchant >= 2) {
            MillLog.minor(this, "Creating " + husbandType + " and " + wifeType + ": " + familyName);
         }

         VillagerRecord husbandRecord = null;
         VillagerRecord wifeRecord = null;
         if (this.resManager.getSleepingPos() == null) {
            MillLog.error(this, "Wanted to create villagers but sleepingPos is null!");
            return "";
         } else {
            if (husbandType != null) {
               husbandRecord = VillagerRecord.createVillagerRecord(
                  this.culture, husbandType, this.mw, this.getPos(), this.getTownHallPos(), null, null, -1L, false
               );
               MillVillager husband = MillVillager.createVillager(husbandRecord, this.world, this.resManager.getSleepingPos(), false);
               familyName = husband.familyName;
               this.world.addFreshEntity(husband);
            }

            if (wifeType != null) {
               wifeRecord = VillagerRecord.createVillagerRecord(
                  this.culture, wifeType, this.mw, this.getPos(), this.getTownHallPos(), null, familyName, -1L, false
               );
               MillVillager wife = MillVillager.createVillager(wifeRecord, this.world, this.resManager.getSleepingPos(), false);
               wifeRecord = new VillagerRecord(this.mw, wife);
               this.world.addFreshEntity(wife);
            }

            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(this, "Records: " + wifeRecord + "/" + husbandRecord);
            }

            if (wifeRecord != null && husbandRecord != null) {
               wifeRecord.spousesName = husbandRecord.getName();
               husbandRecord.spousesName = wifeRecord.getName();
            }

            int startPos = husbandType == null ? 0 : 1;

            for (int i = startPos; i < this.location.getMaleResidents().size(); i++) {
               this.createNewVillager(this.location.getMaleResidents().get(i));
            }

            startPos = wifeType == null ? 0 : 1;

            for (int i = startPos; i < this.location.getFemaleResidents().size(); i++) {
               this.createNewVillager(this.location.getFemaleResidents().get(i));
            }

            if (this.isInn) {
               this.merchantCreated(husbandRecord);
            } else {
               this.getPanelManager().updateSigns();
            }

            return familyName;
         }
      }
   }

   public void destroyVillage() {
      if (MillConfigValues.LogVillage >= 1) {
         MillLog.major(this, "Destroying the village!");
      }

      for (Point p : this.resManager.chests) {
         TileEntityLockedChest chest = p.getMillChest(this.world);
         if (chest != null) {
            chest.buildingPos = null;
         }
      }

      for (Point px : this.buildings) {
         Building building = this.mw.getBuilding(px);
         if (building != null) {
            for (Point p2 : this.resManager.chests) {
               TileEntityLockedChest chest = p2.getMillChest(this.world);
               if (chest != null) {
                  chest.buildingPos = null;
               }
            }
         }
      }

      for (MillVillager villager : new ArrayList<>(this.getKnownVillagers())) {
         villager.despawnVillager();
      }

      for (Point pxx : this.buildings) {
         this.mw.removeBuilding(pxx);
      }

      this.mw.removeVillageOrLoneBuilding(this.getPos());
      File millenaireDir = this.mw.millenaireDir;
      if (!millenaireDir.exists()) {
         millenaireDir.mkdir();
      }

      File buildingsDir = new File(millenaireDir, "buildings");
      if (!buildingsDir.exists()) {
         buildingsDir.mkdir();
      }

      File file1 = new File(buildingsDir, this.getPos().getPathString() + ".gz");
      if (file1.exists()) {
         file1.renameTo(new File(millenaireDir, this.getPos().getPathString() + "ToDelete"));
         file1.delete();
      }
   }

   public void displayInfos(Player player) {
      if (this.location != null) {
         int nbAdults = 0;
         int nbGrownChild = 0;

         for (MillVillager villager : this.getKnownVillagers()) {
            if (!villager.isChild()) {
               nbAdults++;
            } else if (villager.getSize() == 20) {
               nbGrownChild++;
            }
         }

         ServerSender.sendChat(
            player,
            ChatFormatting.GREEN,
            "It has " + this.getKnownVillagers().size() + " villagers registered. (" + nbAdults + " adults, " + nbGrownChild + " grown children)"
         );
         ServerSender.sendChat(player, ChatFormatting.GREEN, "Pos: " + this.getPos() + " sell pos:" + this.resManager.getSellingPos());
         if (this.isTownhall) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "It has " + this.buildings.size() + " houses registered.");
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Connections build: " + (this.regionMapper != null));
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Village name: " + this.getVillageQualifiedName());

            for (ConstructionIP cip : this.getConstructionsInProgress()) {
               if (cip.getBuildingLocation() != null) {
                  ServerSender.sendChat(
                     player, ChatFormatting.GREEN, "Construction IP: " + this.getBuildingPlanForConstruction(cip) + " at " + cip.getBuildingLocation()
                  );
                  ServerSender.sendChat(player, ChatFormatting.GREEN, "Current builder: " + cip.getBuilder());
               }
            }

            ServerSender.sendChat(player, ChatFormatting.GREEN, "Current seller: " + this.seller);
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Rep: " + this.getReputation(player) + " bought: " + this.buildingsBought);
         }

         if (this.isInn) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Merchant: " + this.merchantRecord);
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Merchant nights: " + this.nbNightsMerchant);
         }

         if (this.getTags() == null) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "UNKNOWN TAGS");
         } else if (this.getTags().size() > 0) {
            String s = "Tags: ";

            for (String tag : this.getTags()) {
               s = s + tag + " ";
            }

            ServerSender.sendChat(player, ChatFormatting.GREEN, s);
         }

         if (this.resManager.chests.size() > 1) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Chests registered: " + this.resManager.chests.size());
         }

         if (this.resManager.furnaces.size() > 1) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Furnaces registered: " + this.resManager.furnaces.size());
         }

         if (this.resManager.firepits.size() > 1) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Firepits registered: " + this.resManager.firepits.size());
         }

         for (int i = 0; i < this.resManager.soilTypes.size(); i++) {
            ServerSender.sendChat(
               player, ChatFormatting.GREEN, "Fields registered: " + this.resManager.soilTypes.get(i) + ": " + this.resManager.soils.get(i).size()
            );
         }

         if (this.resManager.sugarcanesoils.size() > 0) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Sugar cane soils registered: " + this.resManager.sugarcanesoils.size());
         }

         if (this.resManager.fishingspots.size() > 0) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Fishing spots registered: " + this.resManager.fishingspots.size());
         }

         if (this.resManager.stalls.size() > 0) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Stalls registered: " + this.resManager.stalls.size());
         }

         if (this.resManager.woodspawn.size() > 0) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, "Wood spawn registered: " + this.resManager.woodspawn.size());
         }

         if (this.resManager.spawns.size() > 0) {
            String s = "Pens: ";

            for (int i = 0; i < this.resManager.spawns.size(); i++) {
               s = s + this.resManager.spawnTypes.get(i) + ": " + this.resManager.spawns.get(i).size() + " ";
            }

            ServerSender.sendChat(player, ChatFormatting.GREEN, s);
         }

         if (this.resManager.mobSpawners.size() > 0) {
            String s = "Mob spawners: ";

            for (int i = 0; i < this.resManager.mobSpawners.size(); i++) {
               s = s + this.resManager.mobSpawnerTypes.get(i) + ": " + this.resManager.mobSpawners.get(i).size() + " ";
            }

            ServerSender.sendChat(player, ChatFormatting.GREEN, s);
         }

         if (this.resManager.sources.size() > 0) {
            String s = "Sources: ";

            for (int i = 0; i < this.resManager.sources.size(); i++) {
               s = s + this.resManager.sourceTypes.get(i).toString() + ": " + this.resManager.sources.get(i).size() + " ";
            }

            ServerSender.sendChat(player, ChatFormatting.GREEN, s);
         }

         for (MillVillager villagerx : this.getKnownVillagers()) {
            if (villagerx == null) {
               ServerSender.sendChat(player, ChatFormatting.GREEN, "NULL villager!");
            } else {
               ServerSender.sendChat(
                  player,
                  ChatFormatting.GREEN,
                  villagerx.getClass().getSimpleName()
                     + ": "
                     + villagerx.getPos()
                     + (villagerx.isAlive() ? "" : " DEAD")
                     + " "
                     + villagerx.getGoalLabel(villagerx.goalKey)
               );
            }
         }

         String s = "LKey: " + this.location.planKey + " Shop: " + this.location.shop + " special: ";
         if (this.isTownhall) {
            s = s + "Town Hall ";
         }

         if (this.isInn) {
            s = s + "Inn ";
         }

         if (this.isMarket) {
            s = s + "Market";
         }

         if (this.pujas != null) {
            s = s + "Shrine ";
         }

         if (!s.equals("")) {
            ServerSender.sendChat(player, ChatFormatting.GREEN, s);
         }

         if (this.pathsToBuild != null || this.oldPathPointsToClear != null) {
            if (this.pathsToBuild != null) {
               s = "pathsToBuild: " + this.pathsToBuild.size() + " " + this.pathsToBuildIndex + "/" + this.pathsToBuildPathIndex;
            } else {
               s = "pathsToBuild:null";
            }

            if (this.oldPathPointsToClear != null) {
               s = s + " oldPathPointsToClear: " + this.oldPathPointsToClear.size() + " " + this.oldPathPointsToClearIndex;
            } else {
               s = s + " oldPathPointsToClear:null";
            }

            ServerSender.sendChat(player, ChatFormatting.GREEN, s);
         }

         this.validateVillagerList();
      }
   }

   private boolean endRaid() {
      Building targetVillage = this.mw.getBuilding(this.raidTarget);
      if (targetVillage == null) {
         MillLog.error(this, "endRaid() called but couldn't find raidTarget at: " + this.raidTarget);
         return false;
      } else if (targetVillage.location == null) {
         MillLog.error(this, "endRaid() called but target is missing its location at: " + this.raidTarget);
         return false;
      } else {
         if (MillConfigValues.LogDiplomacy >= 1) {
            MillLog.major(this, "Called to end raid on " + targetVillage);
         }

         float defendingForce = targetVillage.getVillageDefendingStrength() * (1.0F + MillCommonUtilities.random.nextFloat());
         float attackingForce = targetVillage.getVillageAttackerStrength() * (1.0F + MillCommonUtilities.random.nextFloat());
         boolean attackersWon;
         if (attackingForce == 0.0F) {
            attackersWon = false;
         } else if (defendingForce == 0.0F) {
            attackersWon = true;
         } else {
            float ratio = attackingForce / defendingForce;
            attackersWon = ratio > 1.2;
         }

         if (MillConfigValues.LogDiplomacy >= 1) {
            MillLog.major(this, "Result of raid: " + attackersWon + " (" + attackingForce + "/" + attackingForce + ")");
         }

         for (VillagerRecord vr : this.getVillagerRecords().values()) {
            if (vr.awayraiding) {
               vr.awayraiding = false;
               VillagerRecord awayRecord = this.mw.getVillagerRecordById(vr.getOriginalId());
               if (awayRecord != null) {
                  vr.killed = awayRecord.killed;
               } else {
                  vr.killed = false;
               }
            }
         }

         targetVillage.clearAllAttackers();

         for (MillVillager v : targetVillage.getKnownVillagers()) {
            if (v.getTarget() != null && v.getTarget() instanceof MillVillager) {
               v.setTarget(null);
            }
         }

         this.cancelRaid();
         targetVillage.underAttack = false;
         if (attackersWon) {
            int nbStolen = 0;
            String taken = "";

            for (TradeGood good : this.culture.goodsList) {
               if (nbStolen <= 1024) {
                  int nbToTake = this.nbGoodNeeded(good.item.getItem(), good.item.meta);
                  nbToTake = Math.min(nbToTake, Math.max(0, 1024 - nbStolen));
                  if (nbToTake > 0) {
                     nbToTake = Math.min(nbToTake, targetVillage.countGoods(good.item));
                     if (nbToTake > 0) {
                        if (MillConfigValues.LogDiplomacy >= 3) {
                           MillLog.debug(this, "Able to take: " + nbToTake + " " + good.getName());
                        }

                        targetVillage.takeGoods(good.item, nbToTake);
                        this.storeGoods(good.item, nbToTake);
                        nbStolen += nbToTake;
                        taken = taken + ";" + good.item.getItem() + "/" + good.item.meta + "/" + nbToTake;
                     }
                  }
               }
            }

            this.raidsPerformed.add("success;" + targetVillage.getVillageQualifiedName() + taken);
            targetVillage.raidsSuffered.add("success;" + this.getVillageQualifiedName() + taken);
            if (MillConfigValues.LogDiplomacy >= 1) {
               MillLog.major(this, "Raid on " + targetVillage + " successfull (" + attackingForce + "/" + defendingForce + ")");
            }

            ServerSender.sendTranslatedSentenceInRange(
               this.world,
               this.getPos(),
               MillConfigValues.BackgroundRadius,
               '4',
               "raid.raidsuccesfull",
               this.getVillageQualifiedName(),
               targetVillage.getVillageQualifiedName(),
               "" + nbStolen
            );
            if (this.controlledBy != null) {
               Player owner = this.world.getPlayerByUUID(this.controlledBy);
               if (owner != null) {
                  MillAdvancements.VIKING.grant(owner);
                  if (this.culture.key.equals("seljuk") && targetVillage.culture.key.equals("byzantines")) {
                     MillAdvancements.ISTANBUL.grant(owner);
                  }

                  if (targetVillage.controlledBy != null && !this.controlledBy.equals(targetVillage.controlledBy)) {
                     MillAdvancements.MP_RAIDONPLAYER.grant(owner);
                  }
               }
            }
         } else {
            this.raidsPerformed.add("failure;" + targetVillage.getVillageQualifiedName());
            targetVillage.raidsSuffered.add("failure;" + this.getVillageQualifiedName());
            if (MillConfigValues.LogDiplomacy >= 1) {
               MillLog.major(this, "Raid on " + targetVillage + " failed (" + attackingForce + "/" + defendingForce + ")");
            }

            if (targetVillage.controlledBy != null && this.culture.key.equals("seljuk") && targetVillage.culture.key.equals("byzantines")) {
               Player targetOwner = this.world.getPlayerByUUID(targetVillage.controlledBy);
               MillAdvancements.NOTTODAY.grant(targetOwner);
            }

            ServerSender.sendTranslatedSentenceInRange(
               this.world,
               this.getPos(),
               MillConfigValues.BackgroundRadius,
               '4',
               "raid.raidfailed",
               this.getVillageQualifiedName(),
               targetVillage.getVillageQualifiedName()
            );
         }

         MillLog.major(this, "Finished ending raid. Records: " + this.getVillagerRecords().size());
         targetVillage.saveTownHall("Raid on village ended");
         this.saveNeeded = true;
         this.saveReason = "Raid finished";
         return true;
      }
   }

   public int estimateAbstractedProductionCapacity(InvItem invItem) {
      BuildingPlan plan = this.location.getPlan();
      return plan != null && plan.abstractedProduction.containsKey(invItem) ? plan.abstractedProduction.get(invItem) : 0;
   }

   private void fillinBuildingLocationInProjects(BuildingLocation location) {
      this.mw.testLocations("fillinBuildingLocation start");
      boolean registered = false;

      for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
         if (this.buildingProjects.containsKey(ep)) {
            List<BuildingProject> projectsLevel = this.buildingProjects.get(ep);

            for (BuildingProject project : new ArrayList<>(projectsLevel)) {
               int pos = 0;
               if (!registered && project.location == null && location.planKey.equals(project.key)) {
                  project.location = location;
                  registered = true;
                  if (MillConfigValues.LogBuildingPlan >= 2) {
                     MillLog.minor(this, "Registered building: " + location + " (level " + location.level + ", variation: " + location.getVariation() + ")");
                  }

                  if (project.location.level >= 0) {
                     for (String s : project.location.subBuildings) {
                        BuildingProject newproject = new BuildingProject(this.culture.getBuildingPlanSet(s), project.location.getPlan());
                        newproject.location = location.createLocationForSubBuilding(s);
                        projectsLevel.add(pos + 1, newproject);
                        if (MillConfigValues.LogBuildingPlan >= 1) {
                           MillLog.major(this, "Adding sub-building to project list: " + newproject + " at pos " + pos + " in " + projectsLevel);
                        }
                     }
                  }

                  pos++;
               } else if (!registered && project.location != null && project.location.level < 0 && project.location.isSameLocation(location)) {
                  project.location = location;
                  registered = true;
                  if (MillConfigValues.LogBuildingPlan >= 1) {
                     MillLog.major(this, "Registered subbuilding: " + location + " (level " + location.level + ", variation: " + location.getVariation() + ")");
                  }
               }
            }
         }
      }

      if (!registered) {
         if (location.isCustomBuilding) {
            BuildingProject projectx = new BuildingProject(this.culture.getBuildingCustom(location.planKey), location);
            this.buildingProjects.get(BuildingProject.EnumProjects.CUSTOMBUILDINGS).add(projectx);
         } else {
            BuildingProject projectx = new BuildingProject(this.culture.getBuildingPlanSet(location.planKey));
            projectx.location = location;
            if (this.villageType.playerControlled) {
               this.buildingProjects.get(BuildingProject.EnumProjects.CORE).add(projectx);
            } else if (location.getPlan().isWallSegment) {
               if (!this.buildingProjects.containsKey(BuildingProject.EnumProjects.WALLBUILDING)) {
                  this.buildingProjects.put(BuildingProject.EnumProjects.WALLBUILDING, new CopyOnWriteArrayList<>());
               }

               this.buildingProjects.get(BuildingProject.EnumProjects.WALLBUILDING).add(projectx);
            } else {
               this.buildingProjects.get(BuildingProject.EnumProjects.EXTRA).add(projectx);
            }
         }
      }

      this.mw.testLocations("fillinBuildingLocation end");
   }

   public void fillStartingGoods() {
      if (this.location.getPlan() != null) {
         for (Point p : this.resManager.chests) {
            TileEntityLockedChest chest = p.getMillChest(this.world);
            if (chest != null) {
               for (int i = 0; i < chest.getContainerSize(); i++) {
                  chest.setItem(i, ItemStack.EMPTY);
               }
            }
         }

         for (BuildingPlan.StartingGood sg : this.location.getPlan().startingGoods) {
            if (MillCommonUtilities.probability(sg.probability)) {
               int nb = sg.fixedNumber;
               if (sg.randomNumber > 0) {
                  nb += MillCommonUtilities.randomInt(sg.randomNumber + 1);
               }

               if (nb > 0) {
                  int chestId = MillCommonUtilities.randomInt(this.resManager.chests.size());
                  TileEntityLockedChest chest = this.resManager.chests.get(chestId).getMillChest(this.world);
                  if (chest != null) {
                     MillCommonUtilities.putItemsInChest(chest, sg.item.getItem(), sg.item.meta, nb);
                  }
               }
            }
         }

         this.invalidateInventoryCache();
         if (MillConfigValues.DEV) {
            this.testModeGoods();
         }
      }
   }

   private Point findAttackerSpawnPoint(Point origin) {
      int x;
      if (origin.getiX() > this.pos.getiX()) {
         x = Math.min(this.winfo.length - 5, this.winfo.length / 2 + 50);
      } else {
         x = Math.max(5, this.winfo.length / 2 - 50);
      }

      int z;
      if (origin.getiZ() > this.pos.getiZ()) {
         z = Math.min(this.winfo.width - 5, this.winfo.width / 2 + 50);
      } else {
         z = Math.max(5, this.winfo.width / 2 - 50);
      }

      for (int i = 0; i < 40; i++) {
         int tx = x + MillCommonUtilities.randomInt(5 + i) - MillCommonUtilities.randomInt(5 + i);
         int tz = z + MillCommonUtilities.randomInt(5 + i) - MillCommonUtilities.randomInt(5 + i);
         tx = Math.max(Math.min(tx, this.winfo.length - 1), 0);
         tz = Math.max(Math.min(tz, this.winfo.width - 1), 0);
         tx = Math.min(tx, this.winfo.length / 2 + 50);
         tx = Math.max(tx, this.winfo.length / 2 - 50);
         tz = Math.min(tz, this.winfo.width / 2 + 50);
         tz = Math.max(tz, this.winfo.width / 2 - 50);
         if (this.winfo.canBuild[tx][tz]) {
            if (this.world.isLoaded(new BlockPos(this.winfo.mapStartX + tx, 0, this.winfo.mapStartZ + tz))) {
               return new Point(
                  this.winfo.mapStartX + tx,
                  WorldUtilities.findTopSoilBlock(this.world, this.winfo.mapStartX + tx, this.winfo.mapStartZ + tz) + 1,
                  this.winfo.mapStartZ + tz
               );
            }
         }
      }

      return this.resManager.getDefendingPos();
   }

   private boolean findBuildingConstruction(boolean ignoreCost) {
      if (this.buildingGoal == null) {
         return false;
      } else {
         if (this.regionMapper == null) {
            try {
               this.rebuildRegionMapper(true);
            } catch (MillLog.MillenaireException var13) {
               MillLog.printException(var13);
            }
         }

         ConstructionIP targetConstruction = null;
         int nbNonWallConstructions = 0;

         for (ConstructionIP cip : this.getConstructionsInProgress()) {
            if (!cip.isWallConstruction()) {
               if (targetConstruction == null && cip.getBuildingLocation() == null) {
                  targetConstruction = cip;
               }

               nbNonWallConstructions++;
            }
         }

         if (targetConstruction == null && nbNonWallConstructions < this.getSimultaneousConstructionSlots()) {
            targetConstruction = new ConstructionIP(this, this.getConstructionsInProgress().size(), false);
            this.getConstructionsInProgress().add(targetConstruction);
         }

         if (targetConstruction == null) {
            return false;
         } else {
            BuildingProject goalProject = null;
            if (this.findConstructionIPforLocation(this.buildingGoalLocation) == null
               && this.findConstructionIPforBuildingPlanKey(this.buildingGoal, false) == null) {
               for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
                  if (this.buildingProjects.containsKey(ep)) {
                     for (BuildingProject project : this.buildingProjects.get(ep)) {
                        if (this.buildingGoalLocation != null && this.buildingGoalLocation.isSameLocation(project.location)) {
                           goalProject = project;
                        } else if (this.buildingGoalLocation == null && project.location == null && this.buildingGoal.equals(project.key)) {
                           goalProject = project;
                        }
                     }
                  }
               }

               if (MillConfigValues.LogBuildingPlan >= 3) {
                  MillLog.debug(this, "Building goal project: " + goalProject + " ");
               }

               if (goalProject == null) {
                  MillLog.error(this, "Could not find building project for " + this.buildingGoal + " and " + this.buildingGoalLocation + ", cancelling goal.");
                  this.buildingGoal = null;
                  this.buildingGoalLocation = null;
                  return false;
               }

               if (goalProject.location != null && goalProject.location.level >= 0 && goalProject.location.upgradesAllowed) {
                  if (!ignoreCost && !this.canAffordProject(goalProject.getPlan(this.buildingGoalVariation, this.buildingGoalLevel))) {
                     this.buildingGoalIssue = "ui.lackingresources";
                  } else {
                     BuildingLocation bl;
                     if (this.buildingGoalLocation != null) {
                        bl = this.buildingGoalLocation;
                     } else {
                        bl = goalProject.location;
                     }

                     targetConstruction.startNewConstruction(
                        bl, goalProject.getPlan(this.buildingGoalVariation, this.buildingGoalLevel).getBuildingPoints(this.world, bl, false, false, false)
                     );
                     if (MillConfigValues.LogBuildingPlan >= 1) {
                        MillLog.major(this, "Upgrade project possible at: " + this.location + " for level " + this.buildingGoalLevel);
                     }

                     if (targetConstruction.getBblocks().length == 0) {
                        MillLog.error(this, "No bblocks for\t " + targetConstruction.getBuildingLocation());

                        try {
                           this.rushCurrentConstructions(false);
                        } catch (Exception var12) {
                           MillLog.printException("Exception when trying to rush building:", var12);
                        }
                     }
                  }
               } else if (goalProject.location != null && goalProject.location.level < 0) {
                  if (!ignoreCost && !this.canAffordProject(goalProject.getPlan(this.buildingGoalVariation, this.buildingGoalLevel))) {
                     this.buildingGoalIssue = "ui.lackingresources";
                  } else {
                     BuildingLocation blx;
                     if (this.buildingGoalLocation != null) {
                        blx = this.buildingGoalLocation;
                     } else {
                        blx = goalProject.location;
                     }

                     targetConstruction.startNewConstruction(
                        blx, goalProject.getPlan(this.buildingGoalVariation, this.buildingGoalLevel).getBuildingPoints(this.world, blx, false, false, false)
                     );
                     if (targetConstruction.getBblocks().length == 0) {
                        MillLog.error(this, "No bblocks for\t " + targetConstruction.getBuildingLocation());
                     }
                  }
               } else if (goalProject.location == null) {
                  boolean canAffordProject = ignoreCost || this.canAffordProject(goalProject.getPlan(this.buildingGoalVariation, 0));
                  if (System.currentTimeMillis() - this.lastFailedProjectLocationSearch > 80000L && canAffordProject) {
                     BuildingLocation location = goalProject.getPlan(this.buildingGoalVariation, 0)
                        .findBuildingLocation(this.winfo, this.regionMapper, this.location.pos, this.villageType.radius, MillCommonUtilities.getRandom(), -1);
                     this.lastFailedProjectLocationSearch = System.currentTimeMillis();
                     if (location != null) {
                        this.lastFailedProjectLocationSearch = 0L;
                        if (this.brickColourTheme != null && location.getPlan().randomBrickColours.isEmpty()) {
                           location.initialiseBrickColoursFromTheme(this, this.brickColourTheme);
                        }

                        this.buildingGoalLocation = location;
                        targetConstruction.startNewConstruction(
                           location, goalProject.getPlan(this.buildingGoalVariation, 0).getBuildingPoints(this.world, location, false, false, false)
                        );
                        if (MillConfigValues.LogBuildingPlan >= 1) {
                           MillLog.major(
                              this,
                              "New project location: Loaded "
                                 + targetConstruction.getBblocks().length
                                 + " building blocks for "
                                 + goalProject.getPlan(this.buildingGoalVariation, 0).planName
                           );
                        }

                        int groundLevel = WorldUtilities.findTopSoilBlock(this.world, location.pos.getiX(), location.pos.getiZ());

                        for (int i = groundLevel + 1; i < location.pos.getiY(); i++) {
                           WorldUtilities.setBlockAndMetadata(this.world, location.pos, Blocks.DIRT, 0);
                        }

                        if (MillConfigValues.LogBuildingPlan >= 1) {
                           MillLog.major(this, "Found location for building project: " + location);
                        }
                     } else {
                        this.buildingGoalIssue = "ui.nospace";
                        this.lastFailedProjectLocationSearch = System.currentTimeMillis();
                        if (MillConfigValues.LogBuildingPlan >= 1) {
                           MillLog.major(this, "Searching for a location for the new project failed.");
                        }
                     }
                  } else if (!canAffordProject) {
                     this.buildingGoalIssue = "ui.lackingresources";
                     if (MillConfigValues.LogBuildingPlan >= 3) {
                        MillLog.debug(this, "Cannot afford building project.");
                     }
                  } else {
                     this.buildingGoalIssue = "ui.nospace";
                  }
               }
            }

            if (targetConstruction.getBuildingLocation() != null) {
               return true;
            } else {
               boolean attemptedConstruction = false;
               List<BuildingProject> possibleProjects = this.getAllPossibleProjects();
               List<BuildingProject> affordableProjects = new ArrayList<>();

               for (BuildingProject projectx : possibleProjects) {
                  if (projectx.planSet != null && (goalProject == null || projectx != goalProject)) {
                     if (projectx.location != null && projectx.location.level >= 0) {
                        if (this.findConstructionIPforLocation(projectx.location) == null) {
                           BuildingPlan plan = projectx.getNextBuildingPlan(true);
                           if (ignoreCost || this.canAffordBuild(plan)) {
                              affordableProjects.add(projectx);
                           }
                        }
                     } else if (this.findConstructionIPforBuildingPlanKey(projectx.planSet.key, true) == null
                        && (ignoreCost || this.canAffordBuild(projectx.planSet.getFirstStartingPlan()))) {
                        affordableProjects.add(projectx);
                     }
                  }
               }

               if (affordableProjects.isEmpty()) {
                  this.lastFailedOtherLocationSearch = System.currentTimeMillis();
                  return false;
               } else {
                  BuildingProject newProject = BuildingProject.getRandomProject(affordableProjects);
                  if (newProject.location != null && newProject.location.level >= 0) {
                     int level = newProject.location.level + 1;
                     int variation = newProject.location.getVariation();
                     BuildingLocation blxx = newProject.location.createLocationForLevel(level);
                     targetConstruction.startNewConstruction(
                        blxx, newProject.getPlan(variation, level).getBuildingPoints(this.world, blxx, false, false, false)
                     );
                     if (MillConfigValues.LogBuildingPlan >= 1) {
                        MillLog.major(
                           this,
                           "Upgrade non-project: Loaded "
                              + targetConstruction.getBblocks().length
                              + " building blocks for "
                              + newProject.getPlan(variation, level).planName
                              + " upgrade. Old level: "
                              + newProject.location.level
                              + " New level: "
                              + level
                        );
                     }
                  } else {
                     BuildingPlan plan = newProject.planSet.getRandomStartingPlan();
                     BuildingLocation location = null;
                     if (ignoreCost || this.canAffordBuild(plan)) {
                        if (newProject.location == null && System.currentTimeMillis() - this.lastFailedOtherLocationSearch > 80000L) {
                           location = plan.findBuildingLocation(
                              this.winfo, this.regionMapper, this.location.pos, this.villageType.radius, MillCommonUtilities.getRandom(), -1
                           );
                        } else if (newProject.location != null) {
                           location = newProject.location.createLocationForLevel(0);
                        }
                     }

                     if (location != null) {
                        this.lastFailedOtherLocationSearch = 0L;
                        targetConstruction.startNewConstruction(location, plan.getBuildingPoints(this.world, location, false, false, false));
                        if (MillConfigValues.LogBuildingPlan >= 1) {
                           MillLog.major(
                              this, "New location non-project: Loaded " + targetConstruction.getBblocks().length + " building blocks for " + plan.planName
                           );
                        }
                     } else {
                        attemptedConstruction = true;
                     }
                  }

                  if (attemptedConstruction) {
                     this.lastFailedOtherLocationSearch = System.currentTimeMillis();
                  }

                  return true;
               }
            }
         }
      }
   }

   private boolean findBuildingConstructionWall(boolean ignoreCost) {
      ConstructionIP targetConstruction = null;
      int nbWallConstructions = 0;

      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         if (cip.isWallConstruction()) {
            if (targetConstruction == null && cip.getBuildingLocation() == null) {
               targetConstruction = cip;
            }

            nbWallConstructions++;
         }
      }

      if (targetConstruction == null && nbWallConstructions < this.getSimultaneousWallConstructionSlots()) {
         targetConstruction = new ConstructionIP(this, this.getConstructionsInProgress().size(), true);
         this.getConstructionsInProgress().add(targetConstruction);
      }

      if (targetConstruction == null) {
         return false;
      } else if (!this.buildingProjects.containsKey(BuildingProject.EnumProjects.WALLBUILDING)) {
         return false;
      } else {
         for (BuildingProject project : this.buildingProjects.get(BuildingProject.EnumProjects.WALLBUILDING)) {
            if (project.planSet != null && this.findConstructionIPforLocation(project.location) == null) {
               if (project.location.level >= 0 || !ignoreCost && !this.canAffordBuild(project.location.getPlan())) {
                  int level = project.location.level + 1;
                  int variation = project.location.getVariation();
                  if (level < project.getLevelsNumber(variation)
                     && this.isValidUpgrade(project)
                     && project.location.upgradesAllowed
                     && (ignoreCost || this.canAffordBuild(project.getPlan(variation, level)))) {
                     BuildingLocation bl = project.location.createLocationForLevel(level);
                     targetConstruction.startNewConstruction(bl, project.getPlan(variation, level).getBuildingPoints(this.world, bl, false, false, false));
                     if (MillConfigValues.LogBuildingPlan >= 1) {
                        MillLog.major(
                           this,
                           " Wall upgrade non-project: Loaded "
                              + targetConstruction.getBblocks().length
                              + " building blocks for "
                              + project.getPlan(variation, level).planName
                              + " upgrade. Old level: "
                              + project.location.level
                              + " New level: "
                              + level
                        );
                     }
                  }
               } else {
                  BuildingPlan plan = project.location.getPlan();
                  if (this.isValidProject(project)) {
                     BuildingLocation location = project.location.createLocationForLevel(0);
                     targetConstruction.startNewConstruction(location, plan.getBuildingPoints(this.world, location, false, false, false));
                  }
               }
            }

            if (targetConstruction.getBuildingLocation() != null) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean findBuildingProject() {
      if (this.buildingGoal != null && this.buildingGoal.length() > 0) {
         return false;
      } else if (this.noProjectsLeft && (this.world.getOverworldClockTime() + this.hashCode()) % 600L != 3L) {
         return false;
      } else {
         this.buildingGoal = null;
         this.buildingGoalLocation = null;
         if (MillConfigValues.LogBuildingPlan >= 2) {
            MillLog.minor(this, "Searching for new building goal");
         }

         List<BuildingProject> possibleProjects = this.getAllPossibleProjects();
         if (possibleProjects.size() == 0) {
            this.noProjectsLeft = true;
            return false;
         } else {
            this.noProjectsLeft = false;
            BuildingProject project = BuildingProject.getRandomProject(possibleProjects);
            BuildingPlan plan = project.getNextBuildingPlan(true);
            this.buildingGoal = project.key;
            this.buildingGoalLevel = plan.level;
            this.buildingGoalVariation = plan.variation;
            if (project.location == null) {
               this.buildingGoalLocation = null;
               ConstructionIP cip = this.findConstructionIPforBuildingPlanKey(this.buildingGoal, true);
               if (cip != null) {
                  this.buildingGoalLocation = cip.getBuildingLocation();
               }
            } else {
               this.buildingGoalLocation = project.location.createLocationForLevel(this.buildingGoalLevel);
            }

            if (MillConfigValues.LogBuildingPlan >= 1) {
               MillLog.major(
                  this,
                  "Picked new upgrade goal: " + this.buildingGoal + " level: " + this.buildingGoalLevel + " buildingGoalLocation: " + this.buildingGoalLocation
               );
            }

            return true;
         }
      }
   }

   public ConstructionIP findConstructionIPforBuildingPlanKey(String key, boolean newBuildingOnly) {
      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         if (cip != null
            && cip.getBuildingLocation() != null
            && cip.getBuildingLocation().getPlan().buildingKey.equals(key)
            && (!newBuildingOnly || cip.getBuildingLocation().level == 0)) {
            return cip;
         }
      }

      return null;
   }

   public ConstructionIP findConstructionIPforLocation(BuildingLocation bl) {
      if (bl == null) {
         return null;
      } else {
         for (ConstructionIP cip : this.getConstructionsInProgress()) {
            if (cip != null && bl.isSameLocation(cip.getBuildingLocation())) {
               return cip;
            }
         }

         return null;
      }
   }

   public void findName(String pname) {
      if (pname != null) {
         this.name = pname;
      } else {
         if (this.villageType.nameList == null) {
            this.name = null;
            return;
         }

         this.name = this.culture.getRandomNameFromList(this.villageType.nameList);
      }

      List<String> qualifiers = new ArrayList<>();

      for (String s : this.villageType.qualifiers) {
         qualifiers.add(s);
      }

      if (this.villageType.hillQualifier != null && this.pos.getiY() > 75 && this.pos.getiY() < 85) {
         qualifiers.add(this.villageType.hillQualifier);
      } else if (this.villageType.mountainQualifier != null && this.pos.getiY() >= 85) {
         qualifiers.add(this.villageType.mountainQualifier);
      }

      if (this.villageType.desertQualifier != null
         || this.villageType.forestQualifier != null
         || this.villageType.lavaQualifier != null
         || this.villageType.lakeQualifier != null
         || this.villageType.oceanQualifier != null) {
         int cactus = 0;
         int wood = 0;
         int lake = 0;
         int ocean = 0;
         int lava = 0;

         for (int i = -50; i < 50; i++) {
            for (int j = -10; j < 20; j++) {
               for (int k = -50; k < 50; k++) {
                  Block block = WorldUtilities.getBlock(this.world, i + this.pos.getiX(), j + this.pos.getiY(), k + this.pos.getiZ());
                  if (block == Blocks.CACTUS) {
                     cactus++;
                  } else if (block == Blocks.OAK_LOG
                     || block == Blocks.SPRUCE_LOG
                     || block == Blocks.BIRCH_LOG
                     || block == Blocks.JUNGLE_LOG
                     || block == Blocks.ACACIA_LOG
                     || block == Blocks.DARK_OAK_LOG) {
                     wood++;
                  } else if (block == Blocks.LAVA) {
                     lava++;
                  } else if (block == Blocks.WATER
                     && WorldUtilities.getBlock(this.world, i + this.pos.getiX(), j + this.pos.getiY() + 1, k + this.pos.getiZ()) == Blocks.AIR) {
                     if (j + this.pos.getiY() < 65) {
                        ocean++;
                     } else {
                        lake++;
                     }
                  }
               }
            }
         }

         if (this.villageType.desertQualifier != null && cactus > 0) {
            qualifiers.add(this.villageType.desertQualifier);
         }

         if (this.villageType.forestQualifier != null && wood > 40) {
            qualifiers.add(this.villageType.forestQualifier);
         }

         if (this.villageType.lavaQualifier != null && lava > 0) {
            qualifiers.add(this.villageType.lavaQualifier);
         }

         if (this.villageType.lakeQualifier != null && lake > 0) {
            qualifiers.add(this.villageType.lakeQualifier);
         }

         if (this.villageType.oceanQualifier != null && ocean > 0) {
            qualifiers.add(this.villageType.oceanQualifier);
         }
      }

      if (qualifiers.size() > 0) {
         this.qualifier = qualifiers.get(MillCommonUtilities.randomInt(qualifiers.size()));
      } else {
         this.qualifier = "";
      }
   }

   public void generateBannerPattern() {
      // 1.12 built a {BlockEntityTag:{Base,Patterns:[{Pattern,Color}]}} NBT blob from either a full
      // banner_JSONs entry or by assembling base + pattern + charge layers. 26.2 banners are data-driven
      // (DataComponents.BANNER_PATTERNS + BannerPatternLayers). We faithfully reassemble the legacy
      // {Patterns:[...]} list (Pattern code + Color dye-damage) and hand it to ItemMockBanner.makeBanner,
      // which resolves both Mill-custom (byz/may/…) and vanilla pattern codes against the live
      // banner_pattern registry into a BannerPatternLayers component. Colours are stored as 1.12
      // dye-damage (15=white..0=black) since makeBanner expects that legacy encoding.

      // Path 1: a full banner JSON blob (may include its own Base + Patterns).
      if (!this.villageType.banner_JSONs.isEmpty()) {
         String bannerJSON = this.villageType
            .banner_JSONs
            .get(MillCommonUtilities.randomInt(this.villageType.banner_JSONs.size()))
            .replace("blockentitytag", "BlockEntityTag")
            .replace("base", "Base")
            .replace("pattern", "Pattern")
            .replace("color", "Color");

         try {
            CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(bannerJSON);
            CompoundTag beTag = tag.getCompoundOrEmpty("BlockEntityTag");
            DyeColor base = DyeColor.byId(15 - beTag.getIntOr("Base", 15 - DyeColor.BLACK.getId()));
            this.bannerStack = org.millenaire.common.item.ItemMockBanner.makeBanner(Items.BANNER.pick(base), base, beTag);
            return;
         } catch (Exception var12) {
            MillLog.error(this, "Bad banner JSON " + bannerJSON + ", using default banner settings");
         }
      }

      String baseColor = "black";
      if (!this.villageType.banner_baseColors.isEmpty()) {
         baseColor = this.villageType.banner_baseColors.get(MillCommonUtilities.randomInt(this.villageType.banner_baseColors.size()));
      }

      DyeColor baseDyeColor = DyeColor.BLACK;

      for (DyeColor dyeColor : DyeColor.values()) {
         if (dyeColor.getName().equals(baseColor)) {
            baseDyeColor = dyeColor;
         }
      }

      // Assemble the pattern + charge layers into a legacy {Patterns:[{Pattern,Color}]} list.
      ListTag patternList = new ListTag();
      if (!this.villageType.banner_patternsColors.isEmpty() && !this.villageType.banner_Patterns.isEmpty()) {
         String patternColor = this.villageType.banner_patternsColors.get(MillCommonUtilities.randomInt(this.villageType.banner_patternsColors.size()));
         int patternColorDamage = dyeDamageForName(patternColor);
         String patterns = this.villageType.banner_Patterns.get(MillCommonUtilities.randomInt(this.villageType.banner_Patterns.size()));

         for (String pattern : patterns.split(",")) {
            CompoundTag patternNBT = new CompoundTag();
            patternNBT.putString("Pattern", pattern);
            patternNBT.putInt("Color", patternColorDamage);
            patternList.add(patternNBT);
         }
      }

      if (!this.villageType.banner_chargeColors.isEmpty() && !this.villageType.banner_chargePatterns.isEmpty()) {
         String chargeColor = this.villageType.banner_chargeColors.get(MillCommonUtilities.randomInt(this.villageType.banner_chargeColors.size()));
         int chargeColorDamage = dyeDamageForName(chargeColor);
         String chargePatterns = this.villageType.banner_chargePatterns.get(MillCommonUtilities.randomInt(this.villageType.banner_chargePatterns.size()));

         for (String chargePattern : chargePatterns.split(",")) {
            CompoundTag chargeNBT = new CompoundTag();
            chargeNBT.putString("Pattern", chargePattern);
            chargeNBT.putInt("Color", chargeColorDamage);
            patternList.add(chargeNBT);
         }
      }

      CompoundTag patternsHolder = new CompoundTag();
      patternsHolder.put("Patterns", patternList);
      this.bannerStack = org.millenaire.common.item.ItemMockBanner.makeBanner(Items.BANNER.pick(baseDyeColor), baseDyeColor, patternsHolder);
   }

   /** 1.12 dye-damage (15=white .. 0=black) for a DyeColor name, defaulting to 0 (black) if unknown. */
   private static int dyeDamageForName(String name) {
      for (DyeColor dyeColor : DyeColor.values()) {
         if (dyeColor.getName().equals(name)) {
            return 15 - dyeColor.getId();
         }
      }
      return 0;
   }

   public Set<String> getAllFamilyNames() {
      Set<String> names = new HashSet<>();

      for (VillagerRecord vr : this.vrecords.values()) {
         names.add(vr.familyName);
      }

      return names;
   }

   private List<BuildingProject> getAllPossibleProjects() {
      List<BuildingProject> possibleProjects = new ArrayList<>();
      boolean foundNewBuildingsLevel = false;

      for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
         if (this.buildingProjects.containsKey(ep) && ep != BuildingProject.EnumProjects.WALLBUILDING) {
            List<BuildingProject> projectsLevel = this.buildingProjects.get(ep);
            boolean includedNewBuildings = false;

            for (BuildingProject project : projectsLevel) {
               project.projectTier = ep;
               if ((project.location == null || project.location.level < 0) && !foundNewBuildingsLevel) {
                  if (this.isValidProject(project)) {
                     possibleProjects.add(project);
                     includedNewBuildings = true;
                     if (MillConfigValues.LogBuildingPlan >= 3) {
                        MillLog.debug(this, "Found a new building to add: " + project);
                     }

                     if (MillConfigValues.LogBuildingPlan >= 2 && project.getChoiceWeight(null) < 1) {
                        MillLog.minor(this, "Project has null or negative weight: " + project + ": " + project.getChoiceWeight(null));
                     }
                  }
               } else if (project.location != null
                  && this.isValidUpgrade(project)
                  && project.location.level >= 0
                  && project.location.level < project.getLevelsNumber(project.location.getVariation())
                  && project.location.upgradesAllowed
                  && project.getChoiceWeight(null) > 0) {
                  possibleProjects.add(project);
               }
            }

            if (includedNewBuildings) {
               foundNewBuildingsLevel = true;
            }
         }
      }

      return possibleProjects;
   }

   public Collection<VillagerRecord> getAllVillagerRecords() {
      return this.getVillagerRecords().values();
   }

   public int getAltitude(int x, int z) {
      if (this.winfo == null) {
         return -1;
      } else {
         return x >= this.winfo.mapStartX
               && x < this.winfo.mapStartX + this.winfo.length
               && z >= this.winfo.mapStartZ
               && z < this.winfo.mapStartZ + this.winfo.width
            ? this.winfo.topGround[x - this.winfo.mapStartX][z - this.winfo.mapStartZ]
            : -1;
      }
   }

   public ItemStack getBannerStack() {
      return this.bannerStack;
   }

   public Building getBuildingAtCoordPlanar(Point p) {
      for (Building b : this.getBuildings()) {
         if (b.location.isInsidePlanar(p)) {
            return b;
         }
      }

      return null;
   }

   public Building getBuildingFromLocation(BuildingLocation location) {
      for (Point p : this.buildings) {
         Building building = this.mw.getBuilding(p);
         if (building != null && building.location.isSameLocation(location)) {
            return building;
         }
      }

      return null;
   }

   public BuildingPlan getBuildingPlanForConstruction(ConstructionIP cip) {
      if (cip.getBuildingLocation() == null) {
         MillLog.error(this, "Couldn't find project for construction with no location: " + cip);
         return null;
      } else {
         for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
            if (this.buildingProjects.containsKey(ep)) {
               for (BuildingProject project : this.buildingProjects.get(ep)) {
                  if (cip.getBuildingLocation().level == 0
                     && (project.location == null || project.location.level < 0)
                     && project.key.equals(cip.getBuildingLocation().planKey)) {
                     if (MillConfigValues.LogBuildingPlan >= 3) {
                        MillLog.debug(
                           this,
                           "Returning building plan for "
                              + cip.getBuildingLocation()
                              + ": "
                              + project.getPlan(cip.getBuildingLocation().getVariation(), cip.getBuildingLocation().level)
                        );
                     }

                     return project.getPlan(cip.getBuildingLocation().getVariation(), cip.getBuildingLocation().level);
                  }

                  if (cip.getBuildingLocation().isSameLocation(project.location)) {
                     if (MillConfigValues.LogBuildingPlan >= 3) {
                        MillLog.debug(
                           this,
                           "Returning building plan for "
                              + cip.getBuildingLocation()
                              + ": "
                              + project.getPlan(cip.getBuildingLocation().getVariation(), cip.getBuildingLocation().level)
                        );
                     }

                     return project.getPlan(cip.getBuildingLocation().getVariation(), cip.getBuildingLocation().level);
                  }
               }
            }
         }

         MillLog.error(this, "Could not find project for current building location: " + cip.getBuildingLocation());
         return null;
      }
   }

   public List<Building> getBuildings() {
      List<Building> vbuildings = new ArrayList<>();

      for (Point p : this.buildings) {
         Building building = this.mw.getBuilding(p);
         if (building != null && building.location != null) {
            vbuildings.add(building);
         }
      }

      return vbuildings;
   }

   public List<Building> getBuildingsWithTag(String s) {
      List<Building> matches = new ArrayList<>();

      for (Point p : this.buildings) {
         Building building = this.mw.getBuilding(p);
         if (building != null && building.location != null && building.getTags() != null && building.containsTags(s)) {
            matches.add(building);
         }
      }

      return matches;
   }

   public Set<TradeGood> getBuyingGoods(Player player) {
      return !this.shopBuys.containsKey(player.getName().getString()) ? null : this.shopBuys.get(player.getName().getString()).keySet();
   }

   public int getBuyingPrice(TradeGood g, Player player) {
      if (player != null) {
         LinkedHashMap<TradeGood, Integer> prices = this.shopBuys.get(player.getName().getString());
         if (prices != null) {
            Integer price = prices.get(g);
            if (price != null) {
               return price;
            }
         }
      }
      return 0;
   }

   public ConstructionIP getConstructionIPforBuilder(MillVillager builder) {
      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         if (cip.getBuilder() == builder) {
            return cip;
         }
      }

      return null;
   }

   public List<ConstructionIP> getConstructionsInProgress() {
      return this.constructionsIP;
   }

   public Point getCurrentClearPathPoint() {
      if (this.oldPathPointsToClear == null) {
         return null;
      } else if (this.oldPathPointsToClearIndex >= this.oldPathPointsToClear.size()) {
         this.oldPathPointsToClear = null;
         return null;
      } else {
         return this.oldPathPointsToClear.get(this.oldPathPointsToClearIndex);
      }
   }

   public BuildingPlan getCurrentGoalBuildingPlan() {
      if (this.buildingGoal == null) {
         return null;
      } else {
         for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
            if (this.buildingProjects.containsKey(ep)) {
               for (BuildingProject project : this.buildingProjects.get(ep)) {
                  if (project.key.equals(this.buildingGoal)) {
                     if (this.buildingGoalLocation == null) {
                        return project.getPlan(this.buildingGoalVariation, 0);
                     }

                     return project.getPlan(this.buildingGoalVariation, this.buildingGoalLocation.level);
                  }
               }
            }
         }

         return null;
      }
   }

   public BuildingBlock getCurrentPathBuildingBlock() {
      if (this.pathsToBuild == null) {
         return null;
      } else {
         while (this.pathsToBuildIndex < this.pathsToBuild.size()) {
            if (this.pathsToBuildPathIndex >= this.pathsToBuild.get(this.pathsToBuildIndex).size()) {
               this.pathsToBuildIndex++;
               this.pathsToBuildPathIndex = 0;
            } else {
               BuildingBlock b = this.pathsToBuild.get(this.pathsToBuildIndex).get(this.pathsToBuildPathIndex);
               BlockState blockState = b.p.getBlockActualState(this.world);
               if (PathUtilities.canPathBeBuiltHere(blockState) && blockState != b.getBlockstate()) {
                  return b;
               }

               this.pathsToBuildPathIndex++;
            }
         }

         this.pathsToBuild = null;
         return null;
      }
   }

   public Building getFirstBuildingWithTag(String s) {
      for (Point p : this.buildings) {
         Building building = this.mw.getBuilding(p);
         if (building != null && building.location != null && building.getTags() != null && building.containsTags(s)) {
            return building;
         }
      }

      return null;
   }

   public List<BuildingProject> getFlatProjectList() {
      List<BuildingProject> projects = new ArrayList<>();

      for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
         if (this.buildingProjects.containsKey(ep)) {
            for (BuildingProject project : this.buildingProjects.get(ep)) {
               projects.add(project);
            }
         }
      }

      return projects;
   }

   public String getGameBuildingName() {
      return this.location.getGameName();
   }

   public ItemStack getIcon() {
      BuildingPlanSet planSet = this.culture.getBuildingPlanSet(this.location.planKey);
      if (planSet != null) {
         BuildingPlan plan = planSet.getPlan(this.location.getVariation(), this.location.level);
         if (plan != null) {
            return plan.getIcon();
         }
      }

      return ItemStack.EMPTY;
   }

   public HashMap<TradeGood, Integer> getImportsNeededbyOtherVillages() {
      if (this.neededGoodsCached != null && System.currentTimeMillis() < this.neededGoodsLastGenerated + 60000L) {
         return this.neededGoodsCached;
      } else {
         this.neededGoodsCached = new HashMap<>();

         for (Point vp : this.mw.villagesList.pos) {
            if (this.world.isLoaded(new BlockPos(vp.getiX(), 0, vp.getiZ()))) {
               Building townHall = this.mw.getBuilding(vp);
               if (townHall != null
                  && this.getTownHall() != null
                  && townHall.villageType != this.getTownHall().villageType
                  && townHall.culture == this.getTownHall().culture
                  && townHall.getBuildingsWithTag("inn").size() > 0) {
                  townHall.getNeededImportGoods(this.neededGoodsCached);
               }
            }
         }

         this.neededGoodsLastGenerated = System.currentTimeMillis();
         return this.neededGoodsCached;
      }
   }

   private int getInventoryCountFromCache(InvItem invItem) {
      if (this.inventoryCache == null) {
         this.calculateInventoryCache();
      }

      if (invItem.item == Blocks.OAK_LOG.asItem() && invItem.meta == -1) {
         int count = 0;

         for (int meta = 0; meta < 15; meta++) {
            InvItem invItemAdjusted = InvItem.createInvItem(invItem.item, meta);
            count += this.getInventoryCountFromCache(invItemAdjusted);
         }

         Item itemLog2 = Blocks.ACACIA_LOG.asItem();

         for (int meta = 0; meta < 15; meta++) {
            InvItem invItemAdjusted = InvItem.createInvItem(itemLog2, meta);
            count += this.getInventoryCountFromCache(invItemAdjusted);
         }

         return count;
      } else if (invItem.meta != -1) {
         return this.inventoryCache.containsKey(invItem) ? this.inventoryCache.get(invItem) : 0;
      } else {
         int count = 0;

         for (int meta = 0; meta < 15; meta++) {
            InvItem invItemAdjusted = InvItem.createInvItem(invItem.item, meta);
            count += this.getInventoryCountFromCache(invItemAdjusted);
         }

         return count;
      }
   }

   public Set<MillVillager> getKnownVillagers() {
      return this.villagers;
   }

   public Set<Point> getKnownVillages() {
      return this.relations.keySet();
   }

   public BuildingLocation getLocationAtCoord(Point p) {
      return this.getLocationAtCoordWithTolerance(p, 0);
   }

   public BuildingLocation getLocationAtCoordPlanar(Point p) {
      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         if (cip.getBuildingLocation() != null && cip.getBuildingLocation().isInsidePlanar(p)) {
            return cip.getBuildingLocation();
         }
      }

      for (BuildingLocation bl : this.getLocations()) {
         if (bl.isInsidePlanar(p)) {
            return bl;
         }
      }

      return null;
   }

   public BuildingLocation getLocationAtCoordWithTolerance(Point p, int tolerance) {
      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         if (cip.getBuildingLocation() != null && cip.getBuildingLocation().isInsideWithTolerance(p, tolerance)) {
            return cip.getBuildingLocation();
         }
      }

      for (BuildingLocation bl : this.getLocations()) {
         if (!bl.isSubBuildingLocation && bl.isInsideWithTolerance(p, tolerance)) {
            return bl;
         }
      }

      return null;
   }

   public List<BuildingLocation> getLocations() {
      List<BuildingLocation> locations = new ArrayList<>();

      for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
         if (this.buildingProjects.containsKey(ep)) {
            for (BuildingProject project : this.buildingProjects.get(ep)) {
               if (project.location != null) {
                  locations.add(project.location);
               }
            }
         }
      }

      return locations;
   }

   public MarvelManager getMarvelManager() {
      return this.marvelManager;
   }

   public String getNativeBuildingName() {
      return this.location.getNativeName();
   }

   public int getNbProjects() {
      int nb = 0;

      for (List<BuildingProject> projects : this.buildingProjects.values()) {
         nb += projects.size();
      }

      return nb;
   }

   public void getNeededImportGoods(HashMap<TradeGood, Integer> neededGoods) {
      for (TradeGood good : this.culture.goodsList) {
         int nbneeded = this.nbGoodNeeded(good.item.getItem(), good.item.meta);
         if (nbneeded > 0) {
            if (MillConfigValues.LogMerchant >= 3) {
               MillLog.debug(this, "Import needed: " + good.getName() + " - " + nbneeded);
            }

            if (neededGoods.containsKey(good)) {
               neededGoods.put(good, neededGoods.get(good) + nbneeded);
            } else {
               neededGoods.put(good, nbneeded);
            }
         }
      }
   }

   public int getNewGender() {
      int nbmales = 0;
      int nbfemales = 0;

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (vr.gender == 1) {
            nbmales++;
         } else {
            nbfemales++;
         }
      }

      int maleChance = 3 + nbfemales - nbmales;
      return MillCommonUtilities.randomInt(6) < maleChance ? 1 : 2;
   }

   public PanelManager getPanelManager() {
      if (this.panelManager == null) {
         this.panelManager = new PanelManager(this);
      }

      return this.panelManager;
   }

   public Building getParentBuilding() {
      if (!this.location.isSubBuildingLocation) {
         return this;
      } else {
         Optional<BuildingLocation> parentLocation = this.getTownHall()
            .getLocations()
            .stream()
            .filter(locationTested -> !locationTested.isSubBuildingLocation && locationTested.pos.equals(this.location.pos))
            .findFirst();
         if (parentLocation.isPresent()) {
            return parentLocation.get().getBuilding(this.world);
         } else {
            MillLog.error(this, "Can't find parent building. Returning itself instead.");
            return this;
         }
      }
   }

   public Point getPos() {
      return this.pos;
   }

   public String getQualifier() {
      return this.qualifier;
   }

   public Map<Point, Integer> getRelations() {
      return this.relations;
   }

   public int getRelationWithVillage(Point p) {
      return this.relations.containsKey(p) ? this.relations.get(p) : 0;
   }

   public int getReputation(Player player) {
      return this.mw.getProfile(player).getReputation(this);
   }

   public String getReputationLevelDesc(Player player) {
      return this.culture.getReputationLevelDesc(this.getReputation(player));
   }

   public String getReputationLevelLabel(Player player) {
      return this.culture.getReputationLevelLabel(this.getReputation(player));
   }

   public ResManager getResManager() {
      return this.resManager;
   }

   public Set<TradeGood> getSellingGoods(Player player) {
      if (!this.shopSells.containsKey(player.getName().getString())) {
         MillLog.error(this, "No selling data from player " + player.getName() + ", only has data for " + this.shopSells.keySet().toArray().toString());
         return null;
      } else {
         return this.shopSells.get(player.getName().getString()).keySet();
      }
   }

   public int getSellingPrice(TradeGood g, Player player) {
      if (player != null) {
         LinkedHashMap<TradeGood, Integer> prices = this.shopSells.get(player.getName().getString());
         if (prices != null) {
            Integer price = prices.get(g);
            if (price != null) {
               return price;
            }
         }
      }
      return 0;
   }

   public List<Building> getShops() {
      List<Building> shops = new ArrayList<>();

      for (Point p : this.buildings) {
         Building building = this.mw.getBuilding(p);
         if (building != null && building.location != null && building.location.shop != null && building.location.shop.length() > 0) {
            shops.add(building);
         }
      }

      return shops;
   }

   private int getSimultaneousConstructionSlots() {
      if (this.villageType == null) {
         return 1;
      } else {
         int nb = this.villageType.maxSimultaneousConstructions;

         for (BuildingLocation bl : this.getLocations()) {
            if (bl.getPlan() != null) {
               nb += bl.getPlan().extraSimultaneousConstructions;
            }
         }

         return nb;
      }
   }

   private int getSimultaneousWallConstructionSlots() {
      if (this.villageType == null) {
         return 1;
      } else {
         int nb = this.villageType.maxSimultaneousWallConstructions;

         for (BuildingLocation bl : this.getLocations()) {
            if (bl.getPlan() != null) {
               nb += bl.getPlan().extraSimultaneousWallConstructions;
            }
         }

         return nb;
      }
   }

   public Set<String> getTags() {
      return this.tags.stream().sorted().collect(Collectors.toCollection(TreeSet::new));
   }

   public Building getTownHall() {
      return this.getTownHallPos() == null ? null : this.mw.getBuilding(this.getTownHallPos());
   }

   public Point getTownHallPos() {
      return this.townHallPos;
   }

   public int getVillageAttackerStrength() {
      int strength = 0;

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (vr.raidingVillage && !vr.killed) {
            strength += vr.getMilitaryStrength();
         }
      }

      return strength;
   }

   public int getVillageDefendingStrength() {
      int strength = 0;

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (vr.getType() != null && vr.getType().helpInAttacks && !vr.killed && !vr.raidingVillage) {
            strength += vr.getMilitaryStrength();
         }
      }

      return strength;
   }

   public int getVillageIrrigation() {
      int irrigation = 0;

      for (BuildingLocation bl : this.getLocations()) {
         if (bl.getPlan() != null) {
            irrigation += bl.getPlan().irrigation;
         }
      }

      return irrigation;
   }

   public String getVillageNameWithoutQualifier() {
      if (this.name != null && this.name.length() != 0) {
         return this.name;
      } else {
         return this.villageType != null ? this.villageType.name : this.getNativeBuildingName();
      }
   }

   public String getVillageQualifiedName() {
      if (this.name != null && this.name.length() != 0) {
         return this.getQualifier() != null && this.getQualifier().length() != 0
            ? this.name + this.culture.qualifierSeparator.replaceAll("_", " ") + this.getQualifier()
            : this.name;
      } else {
         return this.villageType != null ? this.villageType.name : this.getNativeBuildingName();
      }
   }

   public int getVillageRaidingStrength() {
      int strength = 0;

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (vr.getType() != null && vr.getType().isRaider && !vr.killed && !vr.raidingVillage) {
            strength += vr.getMilitaryStrength();
         }
      }

      return strength;
   }

   public VillagerRecord getVillagerRecordById(long id) {
      return this.getVillagerRecords().get(id);
   }

   public Map<Long, VillagerRecord> getVillagerRecords() {
      return this.vrecords;
   }

   public VisitorManager getVisitorManager() {
      if (this.visitorManager == null) {
         this.visitorManager = new VisitorManager(this);
      }

      return this.visitorManager;
   }

   public int getWoodCount() {
      if (!this.containsTags("grove")) {
         return 0;
      } else {
         int nb = 0;

         for (int i = this.location.minx - 3; i < this.location.maxx + 3; i++) {
            for (int j = this.location.pos.getiY() - 1; j < this.location.pos.getiY() + 10; j++) {
               for (int k = this.location.minz - 3; k < this.location.maxz + 3; k++) {
                  Block woodBlock = WorldUtilities.getBlock(this.world, i, j, k);
                  if (woodBlock == Blocks.OAK_LOG
                     || woodBlock == Blocks.SPRUCE_LOG
                     || woodBlock == Blocks.BIRCH_LOG
                     || woodBlock == Blocks.JUNGLE_LOG
                     || woodBlock == Blocks.ACACIA_LOG
                     || woodBlock == Blocks.DARK_OAK_LOG) {
                     nb++;
                  }
               }
            }
         }

         return nb;
      }
   }

   public Point getWoodLocation() {
      if (!this.containsTags("grove")) {
         return null;
      } else {
         for (int xPos = this.location.minx - 3; xPos < this.location.maxx + 3; xPos++) {
            for (int yPos = this.location.miny - 1; yPos < this.location.maxy + 20; yPos++) {
               for (int zPos = this.location.minz - 3; zPos < this.location.maxz + 3; zPos++) {
                  Block block = WorldUtilities.getBlock(this.world, xPos, yPos, zPos);
                  if (block == Blocks.OAK_LOG
                     || block == Blocks.SPRUCE_LOG
                     || block == Blocks.BIRCH_LOG
                     || block == Blocks.JUNGLE_LOG
                     || block == Blocks.ACACIA_LOG
                     || block == Blocks.DARK_OAK_LOG) {
                     return new Point(xPos, yPos, zPos);
                  }
               }
            }
         }

         return null;
      }
   }

   public void growTree(Level world, int x, int y, int z, Random random) {
      // 26.2: 1.12 forced a sapling to grow via the removed WorldGen* tree generators + sapling metadata.
      // Each sapling is now a distinct block and trees are data-driven features, so this delegates to the
      // sapling block's own SaplingBlock#advanceTree, which grows the configured tree feature (handling
      // 2x2 mega-trees and single saplings) exactly as a naturally-grown sapling would.
      BlockPos bp = new BlockPos(x, y, z);
      BlockState saplingBlockState = WorldUtilities.getBlockState(world, x, y, z);
      if (saplingBlockState.getBlock() instanceof SaplingBlock saplingBlock && world instanceof ServerLevel serverLevel) {
         saplingBlock.advanceTree(serverLevel, bp, saplingBlockState, serverLevel.getRandom());
      }
   }

   private void handlePathingResult() {
      if (this.pathQueue != null) {
         Collections.reverse(this.pathQueue.pathsReceived);
         Collections.reverse(this.pathQueue.pathCreators);
         this.pathsToBuild = new ArrayList<>();

         for (int i = 0; i < this.pathQueue.pathsReceived.size(); i++) {
            if (this.pathQueue.pathsReceived.get(i) != null) {
               this.pathsToBuild
                  .add(
                     PathUtilities.buildPath(
                        this,
                        this.pathQueue.pathsReceived.get(i),
                        this.pathQueue.pathCreators.get(i).pathConstructionGood.block,
                        this.pathQueue.pathCreators.get(i).pathConstructionGood.meta,
                        this.pathQueue.pathCreators.get(i).pathWidth
                     )
                  );
            }
         }

         this.pathsToBuildIndex = 0;
         this.pathsToBuildPathIndex = 0;
         this.calculatePathsToClear();
         this.pathsChanged = true;
         this.pathQueue = null;
      }
   }

   public void initialise(Player owner, boolean villageCreation) {
      if (MillConfigValues.LogWorldGeneration >= 1) {
         MillLog.major(this, "Initialising building at " + this.getPos() + ", TH pos: " + this.getTownHallPos() + ", TH: " + this.getTownHall());
      }

      if (this.isHouse()) {
         try {
            this.initialiseHouse(villageCreation);
         } catch (Exception var4) {
            MillLog.printException("Error when trying to create a building: " + this.name, var4);
         }

         this.getPanelManager().updateSigns();
      }

      if (this.isTownhall) {
         this.initialiseTownHall(owner);
      } else {
         this.chestLocked = this.getTownHall().chestLocked;
         if (!this.chestLocked) {
            this.unlockChests();
         }
      }

      if (villageCreation && this.resManager.spawns.size() > 0) {
         this.updatePens(true);
      }
   }

   public void initialiseBuildingProjects() {
      if (this.villageType == null) {
         MillLog.error(this, "villageType is null!");
      } else {
         this.buildingProjects = this.villageType.getBuildingProjects();
      }
   }

   public void initialiseConstruction(ConstructionIP cip, Point refPos) throws MillLog.MillenaireException {
      boolean isTownHall = false;
      if (cip.getBuildingLocation().equals(this.location)) {
         isTownHall = true;
      }

      if (cip.getBuildingLocation().level != 0) {
         MillLog.printException(
            new MillLog.MillenaireException("Trying to call initialiseConstruction on a location with non-0 level: " + cip.getBuildingLocation())
         );
      } else {
         Building building = new Building(this.mw, this.culture, this.villageType, cip.getBuildingLocation(), isTownHall, false, this.getPos());
         BuildingPlan plan = this.getBuildingPlanForConstruction(cip);
         plan.updateBuildingForPlan(building);
         building.initialise(null, false);
         this.registerBuildingEntity(building);
         if (MillConfigValues.LogBuildingPlan >= 1) {
            MillLog.major(this, "Created new Building Entity: " + plan.planName + " at " + refPos);
         }

         this.completeConstruction(cip);
      }
   }

   private void initialiseHouse(boolean villageCreation) throws MillLog.MillenaireException {
      if (villageCreation) {
         this.createResidents();
      }
   }

   public void initialiseRelations(Point parentVillage) {
      if (!this.villageType.lonebuilding) {
         this.parentVillage = parentVillage;

         for (Point p : this.mw.villagesList.pos) {
            if (!this.pos.sameBlock(p) && this.pos.distanceToSquared(p) < MillConfigValues.BackgroundRadius * MillConfigValues.BackgroundRadius) {
               Building distantVillage = this.mw.getBuilding(p);
               if (distantVillage != null) {
                  if (parentVillage == null || !p.sameBlock(parentVillage) && !parentVillage.sameBlock(distantVillage.parentVillage)) {
                     if (this.villageType.playerControlled && this.controlledBy.equals(distantVillage.controlledBy)) {
                        this.adjustRelation(p, 100, true);
                     } else if (distantVillage.culture == this.culture) {
                        this.adjustRelation(p, 50, true);
                     } else {
                        this.adjustRelation(p, -30, true);
                     }
                  } else {
                     this.adjustRelation(p, 100, true);
                  }
               }
            }
         }
      }
   }

   public void initialiseTownHall(Player controller) {
      if (this.name == null) {
         this.findName(null);
      }

      if (MillConfigValues.LogWorldGeneration >= 1) {
         MillLog.major(this, "Initialising town hall: " + this.getVillageQualifiedName());
      }

      this.buildings.add(this.getPos());
      if (this.villageType.playerControlled && controller != null) {
         UserProfile profile = this.mw.getProfile(controller);
         this.controlledBy = profile.uuid;
         profile.adjustReputation(this, 131072);
      }
   }

   public void initialiseVillage() {
      boolean noMenLeft = true;

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         if (vr.gender == 1 && !vr.getType().isChild) {
            noMenLeft = false;
         }
      }

      for (Point p : this.buildings) {
         Building b = this.mw.getBuilding(p);
         if (b != null) {
            if (noMenLeft) {
               b.unlockChests();
            } else {
               b.lockChests();
            }
         }
      }

      this.choseAndApplyBrickTheme();
      this.recalculatePaths(true);
   }

   public void invalidateInventoryCache() {
      this.inventoryCache = null;
   }

   public boolean isDisplayableProject(BuildingProject project) {
      if (project.getPlan(0, 0).requiredGlobalTag != null) {
         if (!this.mw.isGlobalTagSet(project.getPlan(0, 0).requiredGlobalTag)) {
            return false;
         }
      } else if (project.getPlan(0, 0).isgift && !MillConfigValues.bonusEnabled) {
         return false;
      }

      return true;
   }

   public boolean isHouse() {
      return this.location != null && (this.location.getMaleResidents().size() > 0 || this.location.getFemaleResidents().size() > 0);
   }

   public boolean isPointProtectedFromPathBuilding(Point p) {
      Point above = p.getAbove();
      Point below = p.getBelow();

      for (Building b : this.getBuildings()) {
         if (b.location != null && b.location.isInsidePlanar(p)) {
            if (b.containsTags("nopaths")) {
               return true;
            }

            if (b.resManager.soils != null) {
               for (List<Point> vpoints : b.resManager.soils) {
                  if (vpoints.contains(p) || vpoints.contains(above) || vpoints.contains(below)) {
                     return true;
                  }
               }
            }

            if (b.resManager.sources != null) {
               for (List<Point> vpointsx : b.resManager.sources) {
                  if (vpointsx.contains(p) || vpointsx.contains(above) || vpointsx.contains(below)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   public boolean isReachableFromRegion(short regionId) {
      if (this.getTownHall().regionMapper == null) {
         return true;
      } else if (this.getTownHall().regionMapper.regions[this.resManager.getSleepingPos().getiX() - this.getTownHall().winfo.mapStartX][this.resManager
               .getSleepingPos()
               .getiZ()
            - this.getTownHall().winfo.mapStartZ]
         != regionId) {
         return false;
      } else if (this.getTownHall().regionMapper.regions[this.resManager.getSellingPos().getiX() - this.getTownHall().winfo.mapStartX][this.resManager
               .getSellingPos()
               .getiZ()
            - this.getTownHall().winfo.mapStartZ]
         != regionId) {
         return false;
      } else {
         return this.getTownHall().regionMapper.regions[this.resManager.getDefendingPos().getiX() - this.getTownHall().winfo.mapStartX][this.resManager
                     .getDefendingPos()
                     .getiZ()
                  - this.getTownHall().winfo.mapStartZ]
               != regionId
            ? false
            : this.getTownHall().regionMapper.regions[this.resManager.getShelterPos().getiX() - this.getTownHall().winfo.mapStartX][this.resManager
                     .getShelterPos()
                     .getiZ()
                  - this.getTownHall().winfo.mapStartZ]
               == regionId;
      }
   }

   public boolean isValidProject(BuildingProject project) {
      BuildingPlan plan = project.getNextBuildingPlan(false);
      if (plan == null) {
         MillLog.error(this, "Building project " + project + " has no building plan.");
         return false;
      } else {
         return !this.villageType.playerControlled && (plan.price > 0 || plan.isgift) && !this.buildingsBought.contains(project.key)
            ? false
            : this.checkProjectValidity(project, plan);
      }
   }

   public boolean isValidUpgrade(BuildingProject project) {
      if (project.location == null) {
         return false;
      } else if (project.getPlan(project.location.getVariation(), project.location.level + 1) == null) {
         return false;
      } else {
         return project.getPlan(project.location.getVariation(), project.location.level + 1).version != project.location.version
            ? false
            : this.checkProjectValidity(project, project.getPlan(project.location.getVariation(), project.location.level + 1));
      }
   }

   private boolean isVillageChunksLoaded() {
      if (this.world.isClientSide()) {
         MillLog.printException("Trying to check chunk status client side", new Exception());
         return false;
      } else {
         ServerLevel serverLevel = (ServerLevel)this.world;

         for (int x = this.winfo.mapStartX; x < this.winfo.mapStartX + this.winfo.width; x += 16) {
            for (int z = this.winfo.mapStartZ; z < this.winfo.mapStartZ + this.winfo.length; z += 16) {
               if (!serverLevel.hasChunk(x >> 4, z >> 4)) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private void killMobs() {
      if (this.winfo != null) {
         Point start = new Point(this.location.pos.x - this.villageType.radius, this.location.pos.getiY() - 20, this.location.pos.z - this.villageType.radius);
         Point end = new Point(this.location.pos.x + this.villageType.radius, this.location.pos.getiY() + 50, this.location.pos.z + this.villageType.radius);
         if (this.containsTags("despawnallmobs")) {
            for (Entity ent : WorldUtilities.getEntitiesWithinAABB(this.world, Monster.class, start, end)) {
               if (!ent.isRemoved()) {
                  if (MillConfigValues.LogTileEntityBuilding >= 3) {
                     MillLog.debug(this, "Killing mob " + ent + " at " + ent.getX() + "/" + ent.getY() + "/" + ent.getZ());
                  }

                  ent.discard();
               }
            }
         } else {
            for (Entity entx : WorldUtilities.getEntitiesWithinAABB(this.world, Creeper.class, start, end)) {
               if (!entx.isRemoved()) {
                  if (MillConfigValues.LogTileEntityBuilding >= 3) {
                     MillLog.debug(this, "Killing creeper " + entx + " at " + entx.getX() + "/" + entx.getY() + "/" + entx.getZ());
                  }

                  entx.discard();
               }
            }

            for (Entity entxx : WorldUtilities.getEntitiesWithinAABB(this.world, EnderMan.class, start, end)) {
               if (!entxx.isRemoved()) {
                  if (MillConfigValues.LogTileEntityBuilding >= 3) {
                     MillLog.debug(this, "Killing enderman " + entxx + " at " + entxx.getX() + "/" + entxx.getY() + "/" + entxx.getZ());
                  }

                  entxx.discard();
               }
            }
         }
      }
   }

   private void loadChunks() {
      if (this.winfo != null && this.winfo.width > 0) {
         if (this.chunkLoader == null) {
            this.chunkLoader = new BuildingChunkLoader(this);
         }

         if (!this.chunkLoader.chunksLoaded) {
            this.chunkLoader.loadChunks();
         }
      }
   }

   public void lockAllBuildingsChests() {
      for (Point p : this.buildings) {
         Building b = this.mw.getBuilding(p);
         if (b != null) {
            b.lockChests();
         }
      }

      this.saveNeeded = true;
      this.saveReason = "Locking chests";
   }

   public void lockChests() {
      this.chestLocked = true;

      for (Point p : this.resManager.chests) {
         TileEntityLockedChest chest = p.getMillChest(this.world);
         if (chest != null) {
            chest.buildingPos = this.getPos();
         }
      }
   }

   public boolean lockedForPlayer(Player player) {
      return !this.chestLocked ? false : !this.controlledBy(player);
   }

   private void merchantCreated(VillagerRecord villagerRecord) {
      if (MillConfigValues.LogMerchant >= 2) {
         MillLog.minor(this, "Creating a new merchant");
      }

      this.merchantRecord = villagerRecord;
      this.visitorsList.add("panels.startedtrading;" + this.merchantRecord.getName() + ";" + this.merchantRecord.getNativeOccupationName());
   }

   private void moveMerchant(Building destInn) {
      HashMap<InvItem, Integer> contents = this.resManager.getChestsContent();

      for (InvItem key : contents.keySet()) {
         int nb = this.takeGoods(key.getItem(), key.meta, 9999999);
         destInn.storeGoods(key.getItem(), key.meta, nb);
         destInn.addToImports(key, nb);
         this.addToExports(key, nb);
      }

      this.transferVillagerPermanently(this.merchantRecord, destInn);
      this.visitorsList
         .add(
            "panels.merchantmovedout;"
               + this.merchantRecord.getName()
               + ";"
               + this.merchantRecord.getNativeOccupationName()
               + ";"
               + destInn.getTownHall().getVillageQualifiedName()
               + ";"
               + this.nbNightsMerchant
         );
      destInn.visitorsList
         .add(
            "panels.merchantarrived;"
               + this.merchantRecord.getName()
               + ";"
               + this.merchantRecord.getNativeOccupationName()
               + ";"
               + this.getTownHall().getVillageQualifiedName()
         );
      if (MillConfigValues.LogMerchant >= 1) {
         MillLog.major(this, "Moved merchant " + this.merchantRecord + " to " + destInn.getTownHall());
      }

      destInn.merchantRecord = this.merchantRecord;
      this.merchantRecord = null;
      this.nbNightsMerchant = 0;
   }

   public int nbGoodAvailable(BlockState bs, boolean forConstruction, boolean forExport, boolean forShop) {
      return this.nbGoodAvailable(InvItem.createInvItem(bs), forConstruction, forExport, forShop);
   }

   public int nbGoodAvailable(InvItem ii, boolean forConstruction, boolean forExport, boolean forShop) {
      if (this.resManager.chests.isEmpty()) {
         return 0;
      } else {
         if (forShop && this.culture.shopNeeds.containsKey(this.location.shop)) {
            for (InvItem item : this.culture.shopNeeds.get(this.location.shop)) {
               if (item.matches(ii)) {
                  return 0;
               }
            }
         }

         int nb = this.countGoods(ii.getItem(), ii.meta);
         if (this.isTownhall) {
            boolean projectHandled = false;
            BuildingPlan project = this.getCurrentGoalBuildingPlan();

            for (ConstructionIP cip : this.getConstructionsInProgress()) {
               if (cip.getBuildingLocation() != null) {
                  BuildingPlan plan = cip.getBuildingLocation().getPlan();
                  if (plan != null) {
                     for (InvItem key : plan.resCost.keySet()) {
                        if (key.matches(ii)) {
                           int builderHas = cip.getBuilder() != null ? cip.getBuilder().countInv(key) : 0;
                           if (builderHas < plan.resCost.get(key)) {
                              nb -= plan.resCost.get(key) - builderHas;
                           }
                        }
                     }
                  }

                  if (project == plan) {
                     projectHandled = true;
                  }
               }
            }

            if (!projectHandled && project != null) {
               for (InvItem keyx : project.resCost.keySet()) {
                  if (keyx.matches(ii)) {
                     nb -= project.resCost.get(keyx);
                  }
               }
            }
         }

         boolean tradedHere = false;
         if (this.location.shop != null && this.culture.shopSells.containsKey(this.location.shop)) {
            for (TradeGood g : this.culture.shopSells.get(this.location.shop)) {
               if (g.item.matches(ii)) {
                  tradedHere = true;
               }
            }
         }

         if (!forConstruction && (this.isTownhall || tradedHere || forExport)) {
            for (InvItem keyxx : this.culture.getInvItemsWithTradeGoods()) {
               if (keyxx.matches(ii) && this.culture.getTradeGood(keyxx) != null) {
                  TradeGood good = this.culture.getTradeGood(keyxx);
                  if (good != null) {
                     if (forExport) {
                        nb -= good.targetQuantity;
                     } else {
                        nb -= good.reservedQuantity;
                     }
                  }
               }
            }
         }

         if (!forConstruction) {
            for (VillagerRecord vr : this.getVillagerRecords().values()) {
               if (vr.getHousePos() != null && vr.getHousePos().equals(this.getPos()) && vr.getType() != null) {
                  for (InvItem requiredItem : vr.getType().requiredFoodAndGoods.keySet()) {
                     if (ii.matches(requiredItem)) {
                        nb -= vr.getType().requiredFoodAndGoods.get(requiredItem);
                     }
                  }
               }
            }
         }

         return Math.max(nb, 0);
      }
   }

   public int nbGoodAvailable(Item item, int meta, boolean forConstruction, boolean forExport, boolean forShop) {
      return this.nbGoodAvailable(InvItem.createInvItem(item, meta), forConstruction, forExport, forShop);
   }

   public int nbGoodNeeded(Item item, int meta) {
      int nb = this.countGoods(item, meta);

      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         if (cip.getBuilder() != null && cip.getBuildingLocation() != null && cip.getBuildingLocation().planKey.equals(this.buildingGoal)) {
            nb += cip.getBuilder().countInv(item, meta);
         }
      }

      int targetAmount = 0;
      InvItem invitem = InvItem.createInvItem(item, meta);
      if (meta == -1) {
         for (int i = 0; i < 16; i++) {
            if (this.culture.getTradeGood(invitem) != null) {
               TradeGood good = this.culture.getTradeGood(InvItem.createInvItem(item, i));
               if (good != null) {
                  targetAmount += good.targetQuantity;
               }
            }
         }
      } else if (this.culture.getTradeGood(invitem) != null) {
         TradeGood good = this.culture.getTradeGood(invitem);
         if (good != null) {
            targetAmount = good.targetQuantity;
         }
      }

      BuildingPlan project = this.getCurrentGoalBuildingPlan();
      int neededForProject = 0;
      if (project != null) {
         for (InvItem key : project.resCost.keySet()) {
            if (key.getItem() == item && (key.meta == meta || meta == -1 || key.meta == -1)) {
               neededForProject += project.resCost.get(key);
            }
         }
      }

      int needed = Math.max(neededForProject + targetAmount - nb, 0);
      if (needed == 0) {
         return 0;
      } else {
         if (MillConfigValues.LogMerchant >= 3) {
            MillLog.debug(this, "Goods needed: " + invitem.getName() + ": " + targetAmount + "/" + neededForProject + "/" + nb);
         }

         return needed;
      }
   }

   public void planRaid(Building target) {
      this.raidPlanningStart = this.world.getOverworldClockTime();
      this.raidStart = 0L;
      this.raidTarget = target.getPos();
      if (MillConfigValues.LogDiplomacy >= 1) {
         MillLog.major(this, "raidTarget set: " + this.raidTarget + " name: " + target.name);
      }

      this.saveNeeded = true;
      this.saveReason = "Raid planned";
      ServerSender.sendTranslatedSentenceInRange(
         this.world,
         this.getPos(),
         MillConfigValues.BackgroundRadius,
         '4',
         "raid.planningstarted",
         this.getVillageQualifiedName(),
         target.getVillageQualifiedName()
      );
   }

   public boolean readFromNBT(CompoundTag nbttagcompound) {
      try {
         String version = nbttagcompound.getStringOr("versionCompatibility", "");
         if (!version.equals("1.0")) {
            MillLog.error(this, "Tried to load building with incompatible version: " + version);
            return false;
         } else {
            if (this.pos == null) {
               this.pos = Point.read(nbttagcompound, "pos");
            }

            this.chestLocked = nbttagcompound.getBooleanOr("chestLocked", false);
            List<String> tags = new ArrayList<>();
            ListTag nbttaglist = nbttagcompound.getListOrEmpty("tags");

            for (int i = 0; i < nbttaglist.size(); i++) {
               CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(i);
               String value = nbttagcompound1.getStringOr("value", "");
               tags.add(value);
               if (MillConfigValues.LogTags >= 2) {
                  MillLog.minor(this, "Loading tag: " + value);
               }
            }

            this.addTags(tags, "loading from NBT");
            if (this.getTags().size() > 0 && MillConfigValues.LogTags >= 1) {
               MillLog.major(this, "Tags loaded: " + MillCommonUtilities.flattenStrings(this.getTags()));
            }

            this.location = BuildingLocation.read(nbttagcompound, "buildingLocation", "self", this);
            if (this.location == null) {
               MillLog.error(this, "No location found!");
               return false;
            } else {
               String cultureKey = nbttagcompound.getStringOr("culture", "");
               if (cultureKey.equals("hindi")) {
                  MillLog.major(this, "Converting village culture from hindi to indian.");
                  cultureKey = "indian";
               }

               this.culture = Culture.getCultureByName(cultureKey);
               if (this.culture == null) {
                  MillLog.error(this, "Could not load culture: " + nbttagcompound.getStringOr("culture", "") + ", skipping building.");
                  return false;
               } else {
                  if (nbttagcompound.contains("isTownhall")) {
                     this.isTownhall = nbttagcompound.getBooleanOr("isTownhall", false);
                  } else {
                     this.isTownhall = this.location.planKey.equals("townHall");
                  }

                  this.townHallPos = Point.read(nbttagcompound, "townHallPos");
                  this.nightActionPerformed = nbttagcompound.getBooleanOr("nightActionPerformed", false);
                  this.nightBackgroundActionPerformed = nbttagcompound.getBooleanOr("nightBackgroundActionPerformed", false);
                  this.nbAnimalsRespawned = nbttagcompound.getIntOr("nbAnimalsRespawned", 0);
                  if (nbttagcompound.contains("villagersrecords")) {
                     nbttaglist = nbttagcompound.getListOrEmpty("villagersrecords");
                     MillLog.major(this, "Loading " + nbttaglist.size() + " villagers from building list.");

                     for (int ix = 0; ix < nbttaglist.size(); ix++) {
                        CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ix);
                        VillagerRecord vr = VillagerRecord.read(this.mw, nbttagcompound1, "vr");
                        if (vr == null) {
                           MillLog.error(this, "Couldn't load VR record.");
                        } else {
                           this.mw.registerVillagerRecord(vr, false);
                           if (MillConfigValues.LogHybernation >= 2) {
                              MillLog.minor(this, "Loaded VR: " + vr);
                           }
                        }
                     }

                     MillLog.major(this, "Finished loading villagers from building list.");
                  }

                  nbttaglist = nbttagcompound.getListOrEmpty("visitorsList");

                  for (int ixx = 0; ixx < nbttaglist.size(); ixx++) {
                     CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixx);
                     this.visitorsList.add(nbttagcompound1.getStringOr("visitor", ""));
                  }

                  nbttaglist = nbttagcompound.getListOrEmpty("subBuildings");

                  for (int ixx = 0; ixx < nbttaglist.size(); ixx++) {
                     CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixx);
                     Point p = Point.read(nbttagcompound1, "pos");
                     if (p != null) {
                        this.subBuildings.add(p);
                     }
                  }

                  if (this.containsTags("pujas") || this.containsTags("sacrifices")) {
                     this.pujas = new PujaSacrifice(this, nbttagcompound.getCompoundOrEmpty("pujas"));
                     if (MillConfigValues.LogPujas >= 2) {
                        MillLog.minor(this, "read pujas object");
                     }
                  }

                  this.lastGoodsRefresh = nbttagcompound.getLongOr("lastGoodsRefresh", 0L);
                  if (this.containsTags("inn") && !this.isTownhall) {
                     this.isInn = true;
                     this.readInn(nbttagcompound);
                  }

                  if (this.isInn && this.getVillagerRecords().size() > 0) {
                     this.merchantRecord = this.getVillagerRecords().get(this.getVillagerRecords().keySet().iterator().next());
                  }

                  if (this.containsTags("autospawnvillagers")) {
                     this.hasAutoSpawn = true;
                  }

                  if (this.containsTags("market") && !this.isTownhall) {
                     this.isMarket = true;
                     this.hasVisitors = true;
                  }

                  if (!this.location.getVisitors().isEmpty()) {
                     this.hasVisitors = true;
                  }

                  if (this.isTownhall) {
                     if (MillConfigValues.LogHybernation >= 1) {
                        MillLog.major(this, "Loading Townhall data.");
                     }

                     this.readTownHall(nbttagcompound);
                  }

                  this.resManager.readFromNBT(nbttagcompound);
                  if (this.isTownhall && this.villageType.isMarvel()) {
                     this.marvelManager = new MarvelManager(this);
                     this.marvelManager.readFromNBT(nbttagcompound);
                  }

                  BuildingPlan plan = this.location.getPlan();
                  if (plan != null) {
                     if (plan.shop != null && this.location.shop == null) {
                        this.location.shop = plan.shop;
                     }

                     plan.updateTags(this);
                  }

                  if (MillConfigValues.LogTileEntityBuilding >= 3) {
                     MillLog.debug(this, "Loading building. Type: " + this.location + ", pos: " + this.getPos());
                  }

                  return true;
               }
            }
         }
      } catch (Exception loadError) {
         // Swallowing this previously dropped the whole building from the save (it
         // silently vanished). An unexpected exception mid-read means corrupt NBT:
         // fail loudly. Deliberate skip cases above still return false normally.
         throw MillCrash.fail("Save", "loading building of type " + this.location + " at " + this.pos + ": " + loadError);
      }
   }

   public void readInn(CompoundTag nbttagcompound) throws MillLog.MillenaireException {
      ListTag nbttaglist = nbttagcompound.getListOrEmpty("importedGoods");

      for (int i = 0; i < nbttaglist.size(); i++) {
         CompoundTag tag = nbttaglist.getCompoundOrEmpty(i);
         InvItem good = InvItem.createInvItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(tag.getIntOr("itemid", 0)), tag.getIntOr("itemmeta", 0));
         this.imported.put(good, tag.getIntOr("quantity", 0));
      }

      nbttaglist = nbttagcompound.getListOrEmpty("exportedGoods");

      for (int i = 0; i < nbttaglist.size(); i++) {
         CompoundTag tag = nbttaglist.getCompoundOrEmpty(i);
         InvItem good = InvItem.createInvItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(tag.getIntOr("itemid", 0)), tag.getIntOr("itemmeta", 0));
         this.exported.put(good, tag.getIntOr("quantity", 0));
      }

      nbttaglist = nbttagcompound.getListOrEmpty("importedGoodsNew");
      MillCommonUtilities.readInventory(nbttaglist, this.imported);
      nbttaglist = nbttagcompound.getListOrEmpty("exportedGoodsNew");
      MillCommonUtilities.readInventory(nbttaglist, this.exported);
   }

   private void readPaths() {
      File buildingsDir = MillCommonUtilities.getBuildingsDir(this.world);
      File file1 = new File(buildingsDir, this.getPos().getPathString() + "_paths.bin");
      if (file1.exists()) {
         try (FileInputStream fis = new FileInputStream(file1);
              DataInputStream ds = new DataInputStream(fis)) {
            int size = ds.readInt();
            this.pathsToBuild = new ArrayList<>();

            for (int i = 0; i < size; i++) {
               List<BuildingBlock> path = new ArrayList<>();
               int sizePath = ds.readInt();

               for (int j = 0; j < sizePath; j++) {
                  Point p = new Point(ds.readInt(), ds.readShort(), ds.readInt());
                  BuildingBlock b = new BuildingBlock(p, ds);
                  path.add(b);
               }

               this.pathsToBuild.add(path);
            }
         } catch (Exception readError) {
            // Half-read path file would leave pathsToBuild partially populated and the
            // building silently mis-pathed: fail loudly instead of hiding the corruption.
            throw MillCrash.fail("Save", "reading pathsToBuild for " + this.getPos() + ": " + readError);
         }
      }

      file1 = new File(buildingsDir, this.getPos().getPathString() + "_pathstoclear.bin");
      if (file1.exists()) {
         try (FileInputStream fis = new FileInputStream(file1);
              DataInputStream ds = new DataInputStream(fis)) {
            int size = ds.readInt();
            this.oldPathPointsToClear = new ArrayList<>();

            for (int i = 0; i < size; i++) {
               Point p = new Point(ds.readInt(), ds.readShort(), ds.readInt());
               this.oldPathPointsToClear.add(p);
            }
         } catch (Exception readError) {
            throw MillCrash.fail("Save", "reading oldPathPointsToClear for " + this.getPos() + ": " + readError);
         }
      }
   }

   public void readTownHall(CompoundTag nbttagcompound) {
      this.name = nbttagcompound.getStringOr("name", "");
      this.qualifier = nbttagcompound.getStringOr("qualifier", "");
      String vtype = nbttagcompound.getStringOr("villageType", "");
      if (vtype.length() == 0) {
         this.villageType = this.culture.getRandomVillage();
      } else if (this.culture.getVillageType(vtype) != null) {
         this.villageType = this.culture.getVillageType(vtype);
      } else if (this.culture.getLoneBuildingType(vtype) != null) {
         this.villageType = this.culture.getLoneBuildingType(vtype);
      } else {
         this.villageType = this.culture.getRandomVillage();
      }

      if (nbttagcompound.getStringOr("controlledBy", "").length() > 0) {
         String controlledByName = nbttagcompound.getStringOr("controlledBy", "");
         GameProfile profile = this.world.getServer() == null
            ? null
            : this.world.getServer().services().profileResolver().fetchByName(controlledByName).orElse(null);
         if (profile != null) {
            this.controlledBy = profile.id();
            MillLog.major(this, "Converted controlledBy from name '" + controlledByName + "' to UUID " + this.controlledBy);
         } else {
            MillLog.error(this, "Could not convert controlledBy from name '" + controlledByName + "'.");
         }
      }

      UUID controlledByUUID = nbttagcompound.read("controlledByUUID", net.minecraft.core.UUIDUtil.CODEC).orElse(new UUID(0L, 0L));
      if (!controlledByUUID.equals(new UUID(0L, 0L))) {
         this.controlledBy = controlledByUUID;
         GameProfile profile = this.mw.world.getServer() == null
            ? null
            : this.mw.world.getServer().services().profileResolver().fetchById(this.controlledBy).orElse(null);
         if (profile != null) {
            this.controlledByName = profile.name();
         }
      }

      ListTag nbttaglist = nbttagcompound.getListOrEmpty("buildings");

      for (int i = 0; i < nbttaglist.size(); i++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(i);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            if (this.buildings.contains(p)) {
               MillLog.warning(this, "Trying to add a building that is already there: " + p);
            } else {
               this.buildings.add(p);
            }
         }
      }

      this.initialiseBuildingProjects();
      nbttaglist = nbttagcompound.getListOrEmpty("locations");

      for (int ix = 0; ix < nbttaglist.size(); ix++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ix);
         BuildingLocation location = BuildingLocation.read(nbttagcompound1, "location", "locations", null);
         if (location == null) {
            MillLog.error(this, "Could not load building location. Skipping.");
         } else {
            this.fillinBuildingLocationInProjects(location);
         }
      }

      for (int ixx = this.buildings.size() - 1; ixx >= 0; ixx--) {
         boolean foundLocation = false;

         for (BuildingLocation l : this.getLocations()) {
            if (this.buildings.get(ixx).equals(l.chestPos)) {
               foundLocation = true;
            }
         }

         if (!foundLocation) {
            MillLog.error(this, "Deleting building as could not find the location for it at: " + this.buildings.get(ixx));
            this.buildings.remove(ixx);
         }
      }

      if (this.villageType.playerControlled) {
         for (List<BuildingProject> level : this.buildingProjects.values()) {
            List<BuildingProject> toDelete = new ArrayList<>();

            for (BuildingProject project : level) {
               if (project.location == null) {
                  toDelete.add(project);
               }
            }

            for (BuildingProject projectx : toDelete) {
               level.remove(projectx);
            }
         }
      }

      this.buildingGoal = nbttagcompound.getStringOr("buildingGoal", "");
      if (this.culture.getBuildingPlanSet(this.buildingGoal) == null) {
         this.buildingGoal = null;
         this.buildingGoalLevel = 0;
         this.buildingGoalVariation = 0;
         if (MillConfigValues.LogHybernation >= 1) {
            MillLog.major(this, "No goal found: " + this.buildingGoal);
         }
      } else {
         this.buildingGoalLevel = nbttagcompound.getIntOr("buildingGoalLevel", 0);
         this.buildingGoalVariation = nbttagcompound.getIntOr("buildingGoalVariation", 0);
         if (MillConfigValues.LogHybernation >= 1) {
            MillLog.major(this, "Reading building goal: " + this.buildingGoal);
         }
      }

      this.buildingGoalLocation = BuildingLocation.read(nbttagcompound, "buildingGoalLocation", "buildingGoalLocation", null);
      if (this.buildingGoalLocation != null && MillConfigValues.LogHybernation >= 1) {
         MillLog.major(this, "Loaded buildingGoalLocation: " + this.buildingGoalLocation);
      }

      this.buildingGoalIssue = nbttagcompound.getStringOr("buildingGoalIssue", "");
      if (this.buildingGoal != null) {
         BuildingPlanSet planSet = this.culture.getBuildingPlanSet(this.buildingGoal);
         if (planSet != null) {
            if (this.buildingGoalVariation >= planSet.plans.size() || this.buildingGoalLevel >= planSet.plans.get(this.buildingGoalVariation).length) {
               this.buildingGoal = null;
               this.buildingGoalLevel = 0;
               this.buildingGoalVariation = 0;
               this.buildingGoalLocation = null;
            } else if (this.buildingGoalLocation != null && this.buildingGoalLocation.version != planSet.plans.get(this.buildingGoalVariation)[0].version) {
               this.buildingGoal = null;
               this.buildingGoalLevel = 0;
               this.buildingGoalVariation = 0;
               this.buildingGoalLocation = null;
            }
         }
      }

      int nbConstructions = nbttagcompound.getIntOr("nbConstructions", 0);

      for (int ixx = 0; ixx < nbConstructions; ixx++) {
         ConstructionIP cip = new ConstructionIP(this, ixx, nbttagcompound.getBooleanOr("buildingLocationIP_" + ixx + "_isWall", false));
         this.getConstructionsInProgress().add(cip);
         cip.setBuildingLocation(BuildingLocation.read(nbttagcompound, "buildingLocationIP_" + ixx, "buildingLocationIP_" + ixx, null));
         if (cip.getBuildingLocation() != null) {
            if (this.culture.getBuildingPlanSet(cip.getBuildingLocation().planKey) == null) {
               cip.clearBuildingLocation();
            } else {
               BuildingPlanSet set = this.culture.getBuildingPlanSet(cip.getBuildingLocation().planKey);
               if (cip.getBuildingLocation().level >= set.plans.get(cip.getBuildingLocation().getVariation()).length) {
                  cip.clearBuildingLocation();
               }
            }

            cip.readBblocks();
            cip.setBblockPos(nbttagcompound.getIntOr("bblocksPos_" + ixx, 0));
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("buildingsBought");

      for (int ixxx = 0; ixxx < nbttaglist.size(); ixxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxx);
         this.buildingsBought.add(nbttagcompound1.getStringOr("key", ""));
      }

      this.parentVillage = Point.read(nbttagcompound, "parentVillage");
      if (nbttagcompound.contains("relations")) {
         nbttaglist = nbttagcompound.getListOrEmpty("relations");

         for (int ixxx = 0; ixxx < nbttaglist.size(); ixxx++) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxx);
            this.relations.put(Point.read(nbttagcompound1, "pos"), nbttagcompound1.getIntOr("value", 0));
         }
      }

      this.updateRaidPerformed = nbttagcompound.getBooleanOr("updateRaidPerformed", false);
      this.nightBackgroundActionPerformed = nbttagcompound.getBooleanOr("nightBackgroundActionPerformed", false);
      this.raidTarget = Point.read(nbttagcompound, "raidTarget");
      this.raidPlanningStart = nbttagcompound.getLongOr("raidPlanningStart", 0L);
      this.raidStart = nbttagcompound.getLongOr("raidStart", 0L);
      this.underAttack = nbttagcompound.getBooleanOr("underAttack", false);
      nbttaglist = nbttagcompound.getListOrEmpty("raidsPerformed");

      for (int ixxx = 0; ixxx < nbttaglist.size(); ixxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxx);
         this.raidsPerformed.add(nbttagcompound1.getStringOr("raid", ""));
      }

      nbttaglist = nbttagcompound.getListOrEmpty("raidsTaken");

      for (int ixxx = 0; ixxx < nbttaglist.size(); ixxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxx);
         this.raidsSuffered.add(nbttagcompound1.getStringOr("raid", ""));
      }

      this.pathsToBuildIndex = nbttagcompound.getIntOr("pathsToBuildIndex", 0);
      this.pathsToBuildPathIndex = nbttagcompound.getIntOr("pathsToBuildPathIndex", 0);
      this.oldPathPointsToClearIndex = nbttagcompound.getIntOr("oldPathPointsToClearIndex", 0);
      String brickThemeKey = nbttagcompound.getStringOr("brickColourTheme", "");
      if (this.villageType != null && brickThemeKey != null && brickThemeKey.length() > 0) {
         for (VillageType.BrickColourTheme theme : this.villageType.brickColourThemes) {
            if (theme.key.equals(brickThemeKey)) {
               this.brickColourTheme = theme;
            }
         }

         if (this.brickColourTheme == null) {
            MillLog.warning(this, "Could not load brick colour theme: " + brickThemeKey);
         } else {
            MillLog.temp(this, "Loaded theme: " + this.brickColourTheme.key);
         }
      }

      this.readPaths();
      if (nbttagcompound.contains("bannerStack")) {
         this.bannerStack = nbttagcompound.read("bannerStack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
      } else {
         this.generateBannerPattern();
      }
   }

   public boolean rebuildRegionMapper(boolean sync) throws MillLog.MillenaireException {
      this.updateWorldInfo();
      if (sync) {
         RegionMapper temp = new RegionMapper();
         if (temp.createConnectionsTable(this.winfo, this.resManager.getSleepingPos())) {
            this.regionMapper = temp;
            this.lastPathingUpdate = this.world.getOverworldClockTime();
            return true;
         } else {
            this.regionMapper = null;
            this.lastPathingUpdate = this.world.getOverworldClockTime();
            return false;
         }
      } else if (!this.rebuildingRegionMapper) {
         try {
            this.rebuildingRegionMapper = true;
            Building.RegionMapperThread thread = new Building.RegionMapperThread(this.winfo.clone());
            thread.setPriority(1);
            thread.setDaemon(true);
            if (MillConfigValues.LogPathing >= 1) {
               MillLog.major(this, "Thread starting.");
            }

            thread.start();
            if (MillConfigValues.LogPathing >= 1) {
               MillLog.major(this, "Thread started.");
            }
         } catch (CloneNotSupportedException var4) {
            MillLog.printException(var4);
         }

         return true;
      } else {
         return true;
      }
   }

   public void rebuildVillagerList() {
      Set<MillVillager> newSet = new LinkedHashSet<>();

      for (MillVillager villager : this.mw.getAllKnownVillagers()) {
         if (villager.getHouse() == this || villager.getTownHall() == this) {
            newSet.add(villager);
         }
      }

      this.villagers = newSet;
      if (MillConfigValues.LogVillagerSpawn >= 2) {
         for (Entity villagerEntity : WorldUtilities.getEntitiesWithinAABB(
            this.world, MillVillager.class, this.pos, Math.max(this.winfo.length, this.winfo.width) / 2 + 20, 40
         )) {
            MillVillager villagerx = (MillVillager)villagerEntity;
            if ((villagerx.getTownHall() == this || villagerx.getHouse() == this) && !this.villagers.contains(villagerx)) {
               MillLog.warning(this, "Found a villager nearby that isn't registered! : " + villagerx);
            }
         }
      }
   }

   public void recalculatePaths(boolean autobuild) {
      if (MillConfigValues.BuildVillagePaths) {
         int nbPaths = 0;

         for (Building b : this.getBuildings()) {
            if (b != this
               && b.location != null
               && b.location.getPlan() != null
               && !b.location.getPlan().isSubBuilding
               && b.resManager.getPathStartPos() != null) {
               nbPaths++;
            }
         }

         Building.PathCreatorQueue queue = new Building.PathCreatorQueue();
         this.autobuildPaths = autobuild;
         Point townHallPathPoint = this.resManager.getPathStartPos();
         List<Point> nodePoints = new ArrayList<>();

         for (Building bx : this.getBuildings()) {
            if (bx != this && bx.containsTags("pathnode")) {
               nodePoints.add(bx.resManager.getPathStartPos());
            }
         }

         if (MillConfigValues.LogVillagePaths >= 2) {
            MillLog.minor(this, "Launching path rebuild, expected paths number: " + nbPaths);
         }

         for (Building bxx : this.getBuildings()) {
            Point pathStartPos = bxx.resManager.getPathStartPos();
            if (bxx != this
               && bxx.location != null
               && bxx.location.getPlan() != null
               && !bxx.location.getPlan().isSubBuilding
               && pathStartPos != null
               && !bxx.location.getPlan().noPathsToBuilding) {
               InvItem pathMaterial = this.villageType.pathMaterial.get(0);
               if (bxx.location.getPlan().pathLevel < this.villageType.pathMaterial.size()) {
                  pathMaterial = this.villageType.pathMaterial.get(bxx.location.getPlan().pathLevel);
               }

               Point pathDest = townHallPathPoint;
               if (!bxx.containsTags("pathnode")) {
                  for (Point nodePoint : nodePoints) {
                     if (pathDest == townHallPathPoint) {
                        if (nodePoint.distanceTo(pathStartPos) * 1.3 < pathDest.distanceTo(pathStartPos)) {
                           pathDest = nodePoint;
                        }
                     } else if (nodePoint.distanceTo(pathStartPos) < pathDest.distanceTo(pathStartPos)) {
                        pathDest = nodePoint;
                     }
                  }

                  if (pathDest.distanceTo(pathStartPos) > 20.0) {
                     Point otherBuildingDest = null;

                     for (Building otherBuilding : this.getBuildings()) {
                        if (otherBuilding != this
                           && otherBuilding.location != null
                           && otherBuilding.location.getPlan() != null
                           && !otherBuilding.location.getPlan().isSubBuilding
                           && otherBuilding.resManager.getPathStartPos() != null
                           && !otherBuilding.containsTags("pathnode")
                           && !otherBuilding.location.getPlan().noPathsToBuilding) {
                           Point otherBuildingPathStart = otherBuilding.resManager.getPathStartPos();
                           if (townHallPathPoint.distanceToSquared(pathStartPos) > townHallPathPoint.distanceToSquared(otherBuildingPathStart)
                              && otherBuildingPathStart.distanceTo(pathStartPos) * 1.5 < pathDest.distanceTo(pathStartPos)
                              && (
                                 otherBuildingDest == null
                                    || pathStartPos.distanceToSquared(otherBuildingDest) > pathStartPos.distanceToSquared(otherBuildingPathStart)
                              )) {
                              otherBuildingDest = otherBuildingPathStart;
                           }
                        }
                     }

                     if (otherBuildingDest != null) {
                        pathDest = otherBuildingDest;
                     }
                  }
               }

               Building.PathCreator pathCreator = new Building.PathCreator(queue, pathMaterial, bxx.location.getPlan().pathWidth, bxx, pathDest, pathStartPos);
               queue.addPathCreator(pathCreator);
            }
         }

         queue.startNextPath();
      }
   }

   private void refreshGoods() {
      if (this.location != null && this.location.getPlan() != null && this.location.getPlan().startingGoods.size() != 0) {
         if (this.world.isBrightOutside()) {
            this.refreshGoodsNightActionPerformed = false;
         } else if (!this.refreshGoodsNightActionPerformed) {
            long interval;
            if (this.chestLocked) {
               interval = 20L;
            } else {
               interval = 100L;
            }

            if (this.lastGoodsRefresh + interval * 24000L < this.world.getOverworldClockTime() && this.chestLocked) {
               this.fillStartingGoods();
               this.lastGoodsRefresh = this.world.getOverworldClockTime();
            }

            this.refreshGoodsNightActionPerformed = true;
         }
      }
   }

   public void registerBuildingEntity(Building buildingEntity) throws MillLog.MillenaireException {
      if (this.buildings.contains(buildingEntity.getPos())) {
         MillLog.warning(this, "Trying to the register building that is already present: " + buildingEntity.getPos());
      } else {
         this.buildings.add(buildingEntity.getPos());
      }

      this.saveNeeded = true;
      this.saveReason = "Registering building";
   }

   public void registerBuildingLocation(BuildingLocation location) {
      boolean registered = false;

      for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
         if (this.buildingProjects.containsKey(ep)) {
            for (BuildingProject project : this.buildingProjects.get(ep)) {
               if (location.level == 0 && location.isSubBuildingLocation) {
                  if (project.key.equals(location.planKey) && (project.location == null || project.location.level < 0)) {
                     if (project.location != null) {
                        location.upgradesAllowed = project.location.upgradesAllowed;
                     }

                     project.location = location.clone();
                     registered = true;
                     if (MillConfigValues.LogBuildingPlan >= 1) {
                        MillLog.major(this, "Updated building project: " + project + " with initial location.");
                     }
                  }
               } else if (location.level == 0) {
                  if (project.key.equals(location.planKey)
                     && (project.location == null || project.location.level < 0 && project.location.isSameLocation(location))) {
                     if (project.location != null) {
                        location.upgradesAllowed = project.location.upgradesAllowed;
                     }

                     project.location = location;
                     registered = true;
                     if (MillConfigValues.LogBuildingPlan >= 1) {
                        MillLog.major(this, "Updated building project: " + project + " with initial location.");
                     }
                  }
               } else if (location.isSameLocation(project.location)) {
                  if (MillConfigValues.LogBuildingPlan >= 1) {
                     MillLog.major(this, "Updated building project: " + project + " from level " + project.location.level + " to " + location.level);
                  }

                  location.upgradesAllowed = project.location.upgradesAllowed;
                  project.location = location;
                  registered = true;
               }

               if (registered) {
                  break;
               }
            }
         }

         if (registered) {
            break;
         }
      }

      if (!registered) {
         BuildingProject project = new BuildingProject(location.getPlan().getPlanSet());
         project.location = location;
         if (location.getPlan().isWallSegment) {
            if (!this.buildingProjects.containsKey(BuildingProject.EnumProjects.WALLBUILDING)) {
               this.buildingProjects.put(BuildingProject.EnumProjects.WALLBUILDING, new CopyOnWriteArrayList<>());
            }

            this.buildingProjects.get(BuildingProject.EnumProjects.WALLBUILDING).add(project);
         } else {
            this.buildingProjects.get(BuildingProject.EnumProjects.EXTRA).add(project);
         }
      }

      if (MillConfigValues.LogBuildingPlan >= 1) {
         MillLog.major(this, "Registered building location: " + location);
      }

      BuildingPlan plan = location.getPlan();
      if (plan != null) {
         for (Point p : this.buildings) {
            Building building = this.mw.getBuilding(p);
            if (building != null && building.location != null && building.location.isSameLocation(location)) {
               building.location = building.location.createLocationForLevel(location.level);
               plan = location.getPlan();
               if (MillConfigValues.LogBuildingPlan >= 1) {
                  MillLog.major(this, "Updated building location for building: " + building + " now at upgrade: " + location.level);
               }
            }
         }
      }

      for (String s : location.subBuildings) {
         boolean found = false;
         List<BuildingProject> parentProjectLevel = null;
         int parentPos = 0;

         for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
            if (this.buildingProjects.containsKey(ep)) {
               List<BuildingProject> projectsLevel = this.buildingProjects.get(ep);
               int pos = 0;

               for (BuildingProject project : projectsLevel) {
                  if (project.location != null) {
                     if (project.location.isLocationSamePlace(location) && project.key.equals(s)) {
                        found = true;
                     } else if (project.location.isSameLocation(location)) {
                        parentProjectLevel = projectsLevel;
                        parentPos = pos;
                     }
                  }

                  pos++;
               }
            }
         }

         if (!found && parentProjectLevel != null) {
            if (this.culture.getBuildingPlanSet(s) == null) {
               MillLog.error(this, "Could not find plan for finished building: " + s);
               return;
            }

            BuildingProject project = new BuildingProject(this.culture.getBuildingPlanSet(s), location.getPlan());
            project.location = location.createLocationForSubBuilding(s);
            parentProjectLevel.add(parentPos + 1, project);
         }
      }

      this.saveNeeded = true;
      this.saveReason = "Registering location";
   }

   public void registerVillagerRecord(VillagerRecord villagerRecord) {
      this.getVillagerRecords().put(villagerRecord.getVillagerId(), villagerRecord);
   }

   public boolean removeVillagerRecord(long vid) {
      return this.getVillagerRecords().remove(vid) != null;
   }

   public void requestSave(String reason) {
      this.saveNeeded = true;
      this.saveReason = reason;
   }

   public void resetConstructionsAndGoals() {
      this.constructionsIP.clear();
      this.buildingGoal = null;
      this.buildingGoalLocation = null;
   }

   public void respawnVillager(VillagerRecord vr, Point villagerPos) {
      MillVillager villager = MillVillager.createVillager(vr, this.world, villagerPos, true);
      if (villager == null) {
         MillLog.error(this, "Could not recreate villager " + vr + " of type " + vr.type);
      } else {
         if (!vr.killed) {
            if (MillConfigValues.LogVillagerSpawn >= 1) {
               MillLog.major(this, "Giving the villager back " + vr.inventory.size() + " item types.");
            }

            for (InvItem iv : vr.inventory.keySet()) {
               villager.inventory.put(iv, vr.inventory.get(iv));
            }
         }

         if (!vr.isTextureValid(vr.texture.getPath())) {
            vr.texture = vr.getType().getNewTexture();
         }

         vr.killed = false;
         if (villager.getHouse() != null) {
            villager.setTexture(vr.texture);
            villager.isRaider = vr.raidingVillage;
            if (villager.isChild()) {
               villager.computeChildScale();
            }

            boolean added = this.world.addFreshEntity(villager);
            if (MillLog.debugOn()) {
               MillLog.milldebug("Villager", "SPAWNED(respawn) id=" + villager.getVillagerId() + " type=" + vr.type + " name='" + villager.getVillagerName() + "' at " + villagerPos + " building=" + this.getPos()
                  + " addFreshEntity=" + added + " isRemoved=" + villager.isRemoved()
                  + " mwHasRecord=" + this.mw.getVillagerRecordById(villager.getVillagerId()) + " inWorld=" + (this.mw.getVillagerById(villager.getVillagerId()) != null));
            }
         }
      }
   }

   private void respawnVillagersIfNeeded() throws MillLog.MillenaireException {
      int time = (int)(this.world.getOverworldClockTime() % 24000L);
      boolean resurect = time >= 13000 && time < 13100;

      for (VillagerRecord vr : this.getVillagerRecords().values()) {
         MillVillager foundVillager = this.mw.getVillagerById(vr.getVillagerId());
         if (foundVillager == null) {
            boolean respawn = false;
            if (!vr.flawedRecord) {
               if (vr.raidingVillage) {
                  if (!vr.killed && this.world.getOverworldClockTime() > vr.raiderSpawn + 500L) {
                     respawn = true;
                  }
               } else if (!vr.awayraiding && !vr.awayhired && !vr.getType().noResurrect && (!vr.killed || resurect)) {
                  respawn = true;
               }
            }

            if (respawn) {
               if (MillConfigValues.LogVillagerSpawn >= 1) {
                  MillLog.major(this, "Recreating missing villager from record " + vr + ". Killed: " + vr.killed);
               }

               if (this.mw.getBuilding(vr.getHousePos()) == null) {
                  MillLog.error(this, "Error when trying to recreate a villager from record " + vr + ": couldn't load house at " + vr.getHousePos() + ".");
                  vr.flawedRecord = true;
               } else {
                  Point villagerPos;
                  if (vr.raidingVillage && vr.originalVillagePos != null) {
                     villagerPos = this.findAttackerSpawnPoint(vr.originalVillagePos);
                  } else if (this.underAttack) {
                     if (vr.getType().helpInAttacks) {
                        villagerPos = this.resManager.getDefendingPos();
                     } else {
                        villagerPos = this.resManager.getShelterPos();
                     }
                  } else {
                     villagerPos = this.mw.getBuilding(vr.getHousePos()).resManager.getSleepingPos();
                  }

                  this.respawnVillager(vr, villagerPos);
               }
            }
         } else if (vr.getHousePos() == null
            || vr.texture == null
            || vr.getNameKey() == null
            || vr.getNameKey().length() == 0
            || vr.getNameKey().equals("villager")) {
            MillLog.major(this, "Updating record for villager: " + foundVillager);
            vr.updateRecord(foundVillager);
            vr.flawedRecord = false;
         }
      }
   }

   public int rushCurrentConstructions(boolean worldGeneration) throws MillLog.MillenaireException {
      int nbRushed = 0;

      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         if (cip.getBuildingLocation() != null) {
            BuildingPlan plan = this.getBuildingPlanForConstruction(cip);
            nbRushed++;
            List<BuildingPlan.LocationBuildingPair> lbps;
            if (cip.getBuildingLocation().isSameLocation(this.location)) {
               lbps = plan.build(this.mw, this.villageType, cip.getBuildingLocation(), worldGeneration, true, this, false, false, null, true);
            } else {
               lbps = plan.build(this.mw, this.villageType, cip.getBuildingLocation(), worldGeneration, false, this, false, false, null, true);
            }

            for (BuildingPlan.LocationBuildingPair b : lbps) {
               this.registerBuildingEntity(b.building);
            }

            cip.clearBblocks();
            this.completeConstruction(cip);
         }
      }

      this.updateConstructionQueue(worldGeneration);
      return nbRushed;
   }

   public void saveTownHall(String reason) {
      if (!this.world.isClientSide() && this.saveWorker == null) {
         this.saveWorker = new Building.SaveWorker(reason);
         this.saveWorker.start();
      }
   }

   public void sendBuildingPacket(Player player, boolean sendChest) {
      if (!this.world.isClientSide()) {
         if (this.culture == null) {
            MillLog.error(this, "Unknown culture for this village.");
         } else {
            if (sendChest) {
               this.sendChestPackets(player);
            }

            FriendlyByteBuf data = ServerSender.getPacketBuffer();

            try {
               data.writeInt(2);
               StreamReadWrite.writeNullablePoint(this.getPos(), data);
               data.writeBoolean(this.isTownhall);
               data.writeBoolean(this.chestLocked);
               StreamReadWrite.writeNullableUUID(this.controlledBy, data);
               StreamReadWrite.writeNullableString(this.controlledByName, data);
               StreamReadWrite.writeNullablePoint(this.getTownHallPos(), data);
               StreamReadWrite.writeNullableString(this.culture.key, data);
               String vtype = null;
               if (this.villageType != null) {
                  vtype = this.villageType.key;
               }

               StreamReadWrite.writeNullableString(vtype, data);
               StreamReadWrite.writeNullableBuildingLocation(this.location, data);
               StreamReadWrite.writeStringCollection(this.getTags(), data);
               StreamReadWrite.writeNullableString(this.buildingGoal, data);
               StreamReadWrite.writeNullableString(this.buildingGoalIssue, data);
               data.writeInt(this.buildingGoalLevel);
               data.writeInt(this.buildingGoalVariation);
               StreamReadWrite.writeNullableBuildingLocation(this.buildingGoalLocation, data);
               List<BuildingLocation> bls = new ArrayList<>();
               List<Boolean> isCIPwall = new ArrayList<>();

               for (ConstructionIP cip : this.getConstructionsInProgress()) {
                  isCIPwall.add(cip.isWallConstruction());
                  bls.add(cip.getBuildingLocation());
               }

               StreamReadWrite.writeBooleanList(isCIPwall, data);
               StreamReadWrite.writeBuildingLocationList(bls, data);
               StreamReadWrite.writeProjectListList(this.buildingProjects, data);
               StreamReadWrite.writePointList(this.buildings, data);
               StreamReadWrite.writeStringList(this.buildingsBought, data);
               StreamReadWrite.writePointIntegerMap(this.relations, data);
               StreamReadWrite.writeStringList(this.raidsPerformed, data);
               StreamReadWrite.writeStringList(this.raidsSuffered, data);
               StreamReadWrite.writeVillagerRecordMap(this.getVillagerRecords(), data);
               StreamReadWrite.writeNullablePuja(this.pujas, data);
               StreamReadWrite.writeStringList(this.visitorsList, data);
               StreamReadWrite.writeInventory(this.imported, data);
               StreamReadWrite.writeInventory(this.exported, data);
               StreamReadWrite.writeNullableString(this.name, data);
               StreamReadWrite.writeNullableString(this.getQualifier(), data);
               StreamReadWrite.writeNullablePoint(this.raidTarget, data);
               data.writeLong(this.raidPlanningStart);
               data.writeLong(this.raidStart);
               this.resManager.sendBuildingPacket(data);
               if (this.marvelManager != null) {
                  this.marvelManager.sendBuildingPacket(data);
               }
            } catch (IOException var9) {
               MillLog.printException(this + ": Error in sendUpdatePacket", var9);
            }

            this.mw.getProfile(player).buildingsSent.put(this.pos, this.mw.world.getOverworldClockTime());
            ServerSender.sendPacketToPlayer(data, player);
         }
      }
   }

   public void sendChestPackets(Player player) {
      for (Point p : this.resManager.chests) {
         TileEntityLockedChest chest = p.getMillChest(this.world);
         if (chest != null) {
            chest.buildingPos = this.getPos();
            chest.sendUpdatePacket(player);
         }
      }
   }

   private void sendInitialBuildingPackets() {
      for (Player player : VillageUtilities.getServerPlayers(this.mw.world)) {
         if (this.pos.distanceToSquared(player) < 256.0) {
            UserProfile profile = VillageUtilities.getServerProfile(this.mw.world, player);
            if (profile != null && !profile.buildingsSent.containsKey(this.pos)) {
               this.sendBuildingPacket(player, false);
            }
         }
      }
   }

   public void sendMapInfo(Player player) {
      if (this.getTownHall() != null && this.getTownHall().winfo != null) {
         MillMapInfo minfo = new MillMapInfo(this.getTownHall(), this.getTownHall().winfo);
         minfo.sendMapInfoPacket(player);
      }
   }

   public void sendShopPacket(Player player) {
      FriendlyByteBuf data = ServerSender.getPacketBuffer();
      data.writeInt(11);
      StreamReadWrite.writeNullablePoint(this.getPos(), data);
      if (this.shopSells.containsKey(player.getName().getString())) {
         data.writeInt(this.shopSells.get(player.getName().getString()).size());

         for (TradeGood g : this.shopSells.get(player.getName().getString()).keySet()) {
            StreamReadWrite.writeNullableGoods(g, data);
            data.writeInt(this.shopSells.get(player.getName().getString()).get(g));
         }
      } else {
         data.writeInt(0);
      }

      if (this.shopBuys.containsKey(player.getName().getString())) {
         data.writeInt(this.shopBuys.get(player.getName().getString()).size());

         for (TradeGood g : this.shopBuys.get(player.getName().getString()).keySet()) {
            StreamReadWrite.writeNullableGoods(g, data);
            data.writeInt(this.shopBuys.get(player.getName().getString()).get(g));
         }
      } else {
         data.writeInt(0);
      }

      ServerSender.sendPacketToPlayer(data, player);
   }

   public void sendVillagerOnRaid(VillagerRecord vr, Building target) {
      if (MillConfigValues.LogDiplomacy >= 2) {
         MillLog.minor(this, "Sending villager " + vr + " to raid" + target + ".");
      }

      vr.awayraiding = true;
      VillagerRecord raidRecord = vr.generateRaidRecord(target);
      this.mw.registerVillagerRecord(raidRecord, true);
      MillVillager v = this.mw.getVillagerById(vr.getVillagerId());
      if (v != null) {
         v.despawnVillagerSilent();
         if (MillConfigValues.LogDiplomacy >= 3) {
            MillLog.debug(this, "Villager entity despawned.");
         }
      }

      target.getTownHall().saveTownHall("incoming villager");
   }

   public void setGoods(Item item, int meta, int newVal) {
      int nb = this.countGoods(item, meta);
      if (nb < newVal) {
         this.storeGoods(item, meta, newVal - nb);
      } else {
         this.takeGoods(item, meta, nb - newVal);
      }
   }

   public void setNewVillagerList(Set<MillVillager> villagers) {
      this.villagers = villagers;
   }

   private void startRaid() {
      Building distantVillage = this.mw.getBuilding(this.raidTarget);
      if (this.relations.get(this.raidTarget) != null && this.relations.get(this.raidTarget) > -90) {
         this.cancelRaid();
      }

      if (distantVillage != null) {
         this.raidStart = this.world.getOverworldClockTime();
         int nbRaider = 0;

         for (VillagerRecord vr : new ArrayList<>(this.getVillagerRecords().values())) {
            if (vr.getType().isRaider && !vr.killed && !vr.raidingVillage && !vr.awayraiding && !vr.awayhired) {
               if (MillConfigValues.LogDiplomacy >= 2) {
                  MillLog.minor(this, "Need to transfer raider; " + vr);
               }

               vr.getHouse().sendVillagerOnRaid(vr, distantVillage);
               nbRaider++;
            }
         }

         if (nbRaider > 0) {
            ServerSender.sendTranslatedSentenceInRange(
               this.world,
               this.getPos(),
               MillConfigValues.BackgroundRadius,
               '4',
               "raid.started",
               this.getVillageQualifiedName(),
               distantVillage.getVillageQualifiedName(),
               "" + nbRaider
            );
            distantVillage.cancelRaid();
            distantVillage.underAttack = true;
         } else {
            this.cancelRaid();
         }
      } else {
         this.cancelRaid();
      }
   }

   public int storeGoods(Block block, int toPut) {
      return this.storeGoods(block.asItem(), 0, toPut);
   }

   public int storeGoods(Block block, int meta, int toPut) {
      return this.storeGoods(block.asItem(), meta, toPut);
   }

   public int storeGoods(BlockState bs, int toPut) {
      return this.storeGoods(bs.getBlock().asItem(), 0, toPut);
   }

   public int storeGoods(InvItem item, int toPut) {
      return this.storeGoods(item.getItem(), item.meta, toPut);
   }

   public int storeGoods(Item item, int toPut) {
      return this.storeGoods(item, 0, toPut);
   }

   public int storeGoods(Item item, int meta, int toPut) {
      int stored = 0;

      for (int i = 0; stored < toPut && i < this.resManager.chests.size(); i++) {
         TileEntityLockedChest chest = this.resManager.chests.get(i).getMillChest(this.world);
         if (chest != null) {
            stored += MillCommonUtilities.putItemsInChest(chest, item, meta, toPut - stored);
         }
      }

      this.invalidateInventoryCache();
      return stored;
   }

   public boolean storeItemStack(ItemStack is) {
      for (Point p : this.resManager.chests) {
         TileEntityLockedChest chest = p.getMillChest(this.world);
         if (chest != null) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
               ItemStack stack = chest.getItem(i);
               if (stack.isEmpty()) {
                  chest.setItem(i, is);
                  this.invalidateInventoryCache();
                  return true;
               }
            }
         }
      }

      return false;
   }

   private void swapMerchants(Building destInn) {
      HashMap<InvItem, Integer> contents = this.resManager.getChestsContent();
      HashMap<InvItem, Integer> destContents = destInn.resManager.getChestsContent();

      for (InvItem key : contents.keySet()) {
         int nb = this.takeGoods(key.getItem(), key.meta, contents.get(key));
         destInn.storeGoods(key.getItem(), key.meta, nb);
         destInn.addToImports(key, nb);
         this.addToExports(key, nb);
      }

      for (InvItem key : destContents.keySet()) {
         int nb = destInn.takeGoods(key.getItem(), key.meta, destContents.get(key));
         this.storeGoods(key.getItem(), key.meta, nb);
         destInn.addToExports(key, nb);
         this.addToImports(key, nb);
      }

      VillagerRecord oldMerchant = this.merchantRecord;
      VillagerRecord newMerchant = destInn.merchantRecord;
      this.transferVillagerPermanently(this.merchantRecord, destInn);
      destInn.transferVillagerPermanently(destInn.merchantRecord, this);
      this.visitorsList
         .add(
            "panels.merchantmovedout;"
               + oldMerchant.getName()
               + ";"
               + oldMerchant.getNativeOccupationName()
               + ";"
               + destInn.getTownHall().getVillageQualifiedName()
               + ";"
               + this.nbNightsMerchant
         );
      destInn.visitorsList
         .add(
            "panels.merchantmovedout;"
               + newMerchant.getName()
               + ";"
               + newMerchant.getNativeOccupationName()
               + ";"
               + this.getTownHall().getVillageQualifiedName()
               + ";"
               + this.nbNightsMerchant
         );
      this.visitorsList
         .add(
            "panels.merchantarrived;"
               + newMerchant.getName()
               + ";"
               + newMerchant.getNativeOccupationName()
               + ";"
               + destInn.getTownHall().getVillageQualifiedName()
         );
      destInn.visitorsList
         .add(
            "panels.merchantarrived;"
               + oldMerchant.getName()
               + ";"
               + oldMerchant.getNativeOccupationName()
               + ";"
               + this.getTownHall().getVillageQualifiedName()
         );
      if (MillConfigValues.LogMerchant >= 1) {
         MillLog.major(this, "Swaped merchant " + oldMerchant + " and " + newMerchant + " with " + destInn.getTownHall());
      }

      this.merchantRecord = newMerchant;
      destInn.merchantRecord = oldMerchant;
      this.nbNightsMerchant = 0;
      destInn.nbNightsMerchant = 0;
      destInn.saveTownHall("merchant moved");
      this.saveNeeded = true;
      this.saveReason = "Swapped merchant";
   }

   public int takeGoods(Block block, int meta, int toTake) {
      return this.takeGoods(block.asItem(), meta, toTake);
   }

   public int takeGoods(BlockState blockState, int toTake) {
      return this.takeGoods(blockState.getBlock().asItem(), 0, toTake);
   }

   public int takeGoods(InvItem item, int toTake) {
      return this.takeGoods(item.getItem(), item.meta, toTake);
   }

   public int takeGoods(Item item, int toTake) {
      return this.takeGoods(item, 0, toTake);
   }

   public int takeGoods(Item item, int meta, int toTake) {
      int taken = 0;

      for (int i = 0; taken < toTake && i < this.resManager.chests.size(); i++) {
         TileEntityLockedChest chest = this.resManager.chests.get(i).getMillChest(this.world);
         if (chest != null) {
            taken += WorldUtilities.getItemsFromChest(chest, item, meta, toTake - taken);
         }
      }

      for (int var7 = 0; taken < toTake && var7 < this.resManager.furnaces.size(); var7++) {
         net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace = this.resManager.furnaces.get(var7).getFurnace(this.world);
         if (furnace != null) {
            taken += WorldUtilities.getItemsFromFurnace(furnace, item, toTake - taken);
         }
      }

      for (int var8 = 0; taken < toTake && var8 < this.resManager.firepits.size(); var8++) {
         TileEntityFirePit firepit = (TileEntityFirePit)this.world.getBlockEntity(this.resManager.firepits.get(var8).getBlockPos());
         if (firepit != null) {
            taken += WorldUtilities.getItemsFromFirePit(firepit, item, toTake - taken);
         }
      }

      this.invalidateInventoryCache();
      return taken;
   }

   public void testModeGoods() {
      if (this.isTownhall && !this.villageType.lonebuilding) {
         int stored = this.storeGoods(MillItems.DENIER_OR, 64);
         if (stored < 64) {
            MillLog.error(this, "Should have stored 64 test goods but stored only " + stored);
         }

         this.storeGoods(MillItems.SUMMONING_WAND, 1);
         if (this.culture.key.equals("indian")) {
            this.storeGoods(MillItems.INDIAN_STATUE, 64);
            this.storeGoods(MillBlocks.BS_MUD_BRICK, 2048);
            this.storeGoods(MillBlocks.PAINTED_BRICK_WHITE, 0, 2048);
            this.storeGoods(MillBlocks.PAINTED_BRICK_DECORATED_WHITE, 0, 512);
            this.storeGoods(Blocks.SANDSTONE, 2048);
            this.storeGoods(Blocks.STONE, 2048);
            this.storeGoods(Blocks.COBBLESTONE, 512);
            this.storeGoods(Blocks.ACACIA_LOG, 0, 1024);
            this.storeGoods(MillBlocks.BED_CHARPOY, 0, 64);
            this.storeGoods(MillBlocks.WOODEN_BARS, 0, 64);
            this.storeGoods(MillBlocks.WOODEN_BARS_INDIAN, 0, 64);
            this.storeGoods(MillBlocks.WOOD_DECORATION, 2, 512);
         } else if (this.culture.key.equals("mayan")) {
            this.storeGoods(Blocks.SANDSTONE, 512);
            this.storeGoods(Blocks.STONE, 3500);
            this.storeGoods(Blocks.COBBLESTONE, 2048);
            this.storeGoods(MillBlocks.STONE_DECORATION, 2, 64);
            this.storeGoods(Blocks.OAK_LOG, 1, 512);
            this.storeGoods(Blocks.OAK_LOG, 3, 1024);
         } else if (this.culture.key.equals("japanese")) {
            this.storeGoods(Blocks.OAK_SAPLING, 64);
            this.storeGoods(MillBlocks.WOOD_DECORATION, 2, 2048);
            this.storeGoods(Blocks.GRAVEL, 512);
            this.storeGoods(MillBlocks.PAPER_WALL, 2048);
            this.storeGoods(Blocks.STONE, 2048);
            this.storeGoods(Blocks.COBBLESTONE, 1024);
            this.storeGoods(MillBlocks.WOOD_DECORATION, 0, 512);
            this.storeGoods(MillBlocks.WOOD_DECORATION, 1, 512);
            this.storeGoods(Blocks.OAK_LOG, 1, 512);
         } else if (this.culture.key.equals("byzantines")) {
            this.storeGoods(Blocks.GLASS, 512);
            this.storeGoods(Blocks.COBBLESTONE, 1500);
            this.storeGoods(Blocks.STONE, 1500);
            this.storeGoods(Blocks.BRICKS, 512);
            this.storeGoods(Blocks.SANDSTONE, 512);
            this.storeGoods(Blocks.WOOL.pick(DyeColor.byId(11)), 0, 64);
            this.storeGoods(Blocks.WOOL.pick(DyeColor.byId(14)), 0, 64);
            this.storeGoods(Blocks.OAK_LOG, 2, 128);
            this.storeGoods(Blocks.BOOKSHELF, 0, 64);
            this.storeGoods(MillBlocks.BYZANTINE_TILES, 128);
            this.storeGoods(MillBlocks.BYZANTINE_TILES_SLAB, 128);
            this.storeGoods(MillBlocks.BYZANTINE_STONE_TILES, 128);
         } else if (this.culture.key.equals("norman")) {
            this.storeGoods(Blocks.GLASS, 64);
            this.storeGoods(Blocks.COBBLESTONE, 2048);
            this.storeGoods(Blocks.STONE, 3500);
            this.storeGoods(MillBlocks.WOOD_DECORATION, 0, 2048);
            this.storeGoods(MillBlocks.WOOD_DECORATION, 1, 2048);
            this.storeGoods(Blocks.WOOL.pick(DyeColor.byId(11)), 0, 64);
            this.storeGoods(Blocks.WOOL.pick(DyeColor.byId(14)), 0, 64);
            this.storeGoods(Blocks.SPRUCE_LOG.defaultBlockState(), 512);
            this.storeGoods(Blocks.DARK_OAK_LOG.defaultBlockState(), 1024);
            this.storeGoods(MillBlocks.BED_STRAW, 64);
            this.storeGoods(MillBlocks.STAINED_GLASS.defaultBlockState().setValue(BlockMillStainedGlass.VARIANT, BlockMillStainedGlass.EnumType.WHITE), 64);
            this.storeGoods(MillBlocks.STAINED_GLASS.defaultBlockState().setValue(BlockMillStainedGlass.VARIANT, BlockMillStainedGlass.EnumType.YELLOW), 64);
            this.storeGoods(
               MillBlocks.STAINED_GLASS.defaultBlockState().setValue(BlockMillStainedGlass.VARIANT, BlockMillStainedGlass.EnumType.YELLOW_RED), 64
            );
            this.storeGoods(
               MillBlocks.STAINED_GLASS.defaultBlockState().setValue(BlockMillStainedGlass.VARIANT, BlockMillStainedGlass.EnumType.GREEN_BLUE), 64
            );
            this.storeGoods(MillBlocks.STAINED_GLASS.defaultBlockState().setValue(BlockMillStainedGlass.VARIANT, BlockMillStainedGlass.EnumType.RED_BLUE), 64);
            this.storeGoods(MillBlocks.ROSETTE, 64);
            this.storeGoods(MillBlocks.BED_STRAW, 64);
         } else if (this.culture.key.equals("inuits")) {
            this.storeGoods(MillBlocks.ICE_BRICK, 128);
            this.storeGoods(MillBlocks.SNOW_BRICK, 1024);
            this.storeGoods(MillBlocks.SNOW_WALL, 128);
            this.storeGoods(Blocks.OAK_LOG, 1, 1024);
            this.storeGoods(Blocks.OAK_LOG, 2, 1024);
            this.storeGoods(Blocks.BONE_BLOCK, 256);
            this.storeGoods(MillBlocks.INUIT_CARVING, 0, 64);
         } else {
            this.storeGoods(Blocks.GLASS, 512);
            this.storeGoods(Blocks.COBBLESTONE, 2048);
            this.storeGoods(Blocks.STONE, 3500);
            this.storeGoods(MillBlocks.WOOD_DECORATION, 0, 2048);
            this.storeGoods(MillBlocks.WOOD_DECORATION, 1, 2048);
            this.storeGoods(Blocks.WOOL.pick(DyeColor.byId(11)), 0, 64);
            this.storeGoods(Blocks.WOOL.pick(DyeColor.byId(14)), 0, 64);
         }

         this.storeGoods(Blocks.OAK_LOG, 1024);
         this.storeGoods(Items.IRON_INGOT, 256);
         this.storeGoods(Blocks.WOOL.pick(DyeColor.WHITE), 64);
         this.storeGoods(Blocks.GRAVEL, 64);
         this.storeGoods(Blocks.SAND, 64);
         this.storeGoods(MillBlocks.BED_STRAW, 0, 128);
      }
   }

   public void testShowGroundLevel() {
      for (int i = 0; i < this.winfo.length; i++) {
         for (int j = 0; j < this.winfo.width; j++) {
            Point p = new Point(this.winfo.mapStartX + i, this.winfo.topGround[i][j] - 1, this.winfo.mapStartZ + j);
            if (WorldUtilities.getBlock(this.world, p) != MillBlocks.LOCKED_CHEST) {
               if (!this.winfo.topAdjusted[i][j]) {
                  WorldUtilities.setBlockAndMetadata(this.world, p, Blocks.WOOL.pick(DyeColor.byId(this.regionMapper.regions[i][j] % 16)), 0);
               } else {
                  WorldUtilities.setBlockAndMetadata(this.world, p, Blocks.IRON_BLOCK, 0);
               }
            }
         }
      }
   }

   @Override
   public String toString() {
      return this.location != null ? "(" + this.location + "/" + this.getVillageQualifiedName() + "/" + this.world + ")" : super.toString();
   }

   public void transferVillagerPermanently(VillagerRecord vr, Building dest) {
      if (MillConfigValues.LogDiplomacy >= 2) {
         MillLog.minor(this, "Transfering villager " + vr + " permanently to " + dest + ".");
      }

      this.mw.removeVillagerRecord(vr.getVillagerId());
      vr.setHousePos(dest.getPos());
      vr.setTownHallPos(dest.getTownHall().getPos());
      this.mw.registerVillagerRecord(vr, true);
      MillVillager v = this.mw.getVillagerById(vr.getVillagerId());
      if (v != null) {
         this.villagers.remove(v);
         this.getTownHall().villagers.remove(v);
         if (dest.getTownHall().isActive) {
            v.setHousePoint(dest.getPos());
            v.setTownHallPoint(dest.getTownHall().getPos());
            v.isRaider = false;
            v.setPos(dest.resManager.getSleepingPos().getiX(), dest.resManager.getSleepingPos().getiY(), dest.resManager.getSleepingPos().getiZ());
         } else {
            v.despawnVillager();
            if (MillConfigValues.LogDiplomacy >= 3) {
               MillLog.debug(this, "Villager entity despawned.");
            }
         }
      }

      dest.getTownHall().saveTownHall("incoming villager");
   }

   private void triggerCompletionAdvancements() {
      if (this.buildingGoal == null) {
         if (this.villageType.isRegularVillage()) {
            Player player = this.world.getNearestPlayer(this.pos.getiX(), this.pos.getiY(), this.pos.getiZ(), 5.0, false);
            if (player != null) {
               if (this.getReputation(player) > 32768) {
                  String cultureKey = this.culture.key.toLowerCase();
                  if (MillAdvancements.COMPLETE_ADVANCEMENTS.containsKey(cultureKey)) {
                     MillAdvancements.COMPLETE_ADVANCEMENTS.get(cultureKey).grant(player);
                  }
               }
            }
         }
      }
   }

   private void unloadChunks() {
      if (this.chunkLoader != null && this.chunkLoader.chunksLoaded) {
         this.chunkLoader.unloadChunks();
      }
   }

   public void unlockAllChests() {
      this.chestLocked = false;

      for (Point p : this.buildings) {
         Building b = this.mw.getBuilding(p);
         if (b != null) {
            b.unlockChests();
         }
      }

      if (this.countGoods(MillItems.NEGATION_WAND) == 0) {
         this.storeGoods(MillItems.NEGATION_WAND, 1);
      }
   }

   public void unlockChests() {
      if (!this.isMarket) {
         this.chestLocked = false;

         for (Point p : this.resManager.chests) {
            TileEntityLockedChest chest = p.getMillChest(this.world);
            if (chest != null) {
               chest.buildingPos = this.getPos();
            }
         }
      }
   }

   private void unlockForNearbyPlayers() {
      Point p = this.resManager.getSellingPos();
      if (p == null) {
         p = this.pos;
      }

      for (Player player : this.world
         .getEntitiesOfClass(
            Player.class,
            new AABB(
               this.location.minx - 2, this.location.miny - 2, this.location.minz - 2, this.location.maxx + 2, this.location.maxy + 2, this.location.maxz + 2
            )
         )) {
         UserProfile profile = this.mw.getProfile(player);
         if (profile != null) {
            profile.unlockBuilding(this.culture, this.culture.getBuildingPlanSet(this.location.planKey));
            if (this.getTownHall() != null) {
               profile.unlockVillage(this.culture, this.getTownHall().villageType);
            }
         }
      }
   }

   private void updateAchievements() {
      if (this.villageType != null) {
         for (Entity ent : WorldUtilities.getEntitiesWithinAABB(this.world, Player.class, this.getPos(), this.villageType.radius, 20)) {
            Player player = (Player)ent;
            if (this.villageType.lonebuilding) {
               MillAdvancements.EXPLORER.grant(player);
            }

            if (this.containsTags("hof")) {
               MillAdvancements.PANTHEON.grant(player);
            }

            int nbcultures = this.mw.nbCultureInGeneratedVillages();
            if (nbcultures >= 3) {
               MillAdvancements.MARCO_POLO.grant(player);
            }

            if (nbcultures >= Culture.ListCultures.size()) {
               MillAdvancements.MAGELLAN.grant(player);
            }
         }

         if (this.controlledBy != null && this.getVillagerRecords().size() >= 100) {
            Player playerx = this.world.getPlayerByUUID(this.controlledBy);
            if (playerx != null) {
               MillAdvancements.MEDIEVAL_METROPOLIS.grant(playerx);
            }
         }
      }
   }

   private void updateAutoSpawn() {
      if (!this.world.isBrightOutside()
         && (this.location.getMaleResidents().size() > 0 || this.location.getFemaleResidents().size() > 0)
         && this.getVillagerRecords().size() == 0) {
         try {
            this.createResidents();
         } catch (MillLog.MillenaireException var2) {
            MillLog.printException("Exception when auto-spawning villagers for " + this + ":", var2);
         }
      }
   }

   public void updateBackgroundVillage() {
      if (!this.world.isClientSide()) {
         if (this.villageType != null && this.isTownhall && this.location != null) {
            Player player = this.world.getNearestPlayer(this.pos.getiX(), this.pos.getiY(), this.pos.getiZ(), MillConfigValues.BackgroundRadius, false);
            if (player != null) {
               if (this.world.isBrightOutside()) {
                  this.nightBackgroundActionPerformed = false;
               } else if (!this.nightBackgroundActionPerformed) {
                  if (this.villageType.carriesRaid && this.raidTarget == null && MillCommonUtilities.randomInt(100) < MillConfigValues.RaidingRate) {
                     if (MillConfigValues.LogDiplomacy >= 3) {
                        MillLog.debug(this, "Calling attemptPlanNewRaid");
                     }

                     this.attemptPlanNewRaid();
                  }

                  this.nightBackgroundActionPerformed = true;
               }
            }

            if (this.world.getOverworldClockTime() % 24000L <= 23500L || this.raidTarget == null) {
               this.updateRaidPerformed = false;
            } else if (!this.updateRaidPerformed) {
               if (MillConfigValues.LogDiplomacy >= 3) {
                  MillLog.debug(this, "Calling updateRaid for raid: " + this.raidPlanningStart + "/" + this.raidStart + "/" + this.world.getOverworldClockTime());
               }

               this.updateRaid();
               this.updateRaidPerformed = true;
            }
         }
      }
   }

   public void updateBanners() {
      if (this.getTownHall().bannerStack == ItemStack.EMPTY) {
         this.getTownHall().generateBannerPattern();
      }

      for (Point p : this.resManager.banners) {
         BlockEntity te = this.mw.world.getBlockEntity(p.getBlockPos());
         if (te instanceof TileEntityMockBanner) {
            // 26.2: banner base-colour/pattern are data-driven (DataComponents.BANNER_PATTERNS); the
            // 1.12 BlockEntityTag/Base NBT lookup is gone, so the banner ItemStack (carrying the pattern
            // component) is applied to the block entity directly via setItemValues.
            ((TileEntityMockBanner)te).setItemValues(this.getTownHall().bannerStack, false);
            this.mw
               .world
               .sendBlockUpdated(te.getBlockPos(), this.mw.world.getBlockState(te.getBlockPos()), this.mw.world.getBlockState(te.getBlockPos()), 3);
         }
      }

      for (Point px : this.resManager.cultureBanners) {
         BlockEntity te = this.mw.world.getBlockEntity(px.getBlockPos());
         if (te instanceof TileEntityMockBanner) {
            ((TileEntityMockBanner)te).setItemValues(this.culture.cultureBannerItemStack, true);
            this.mw
               .world
               .sendBlockUpdated(te.getBlockPos(), this.mw.world.getBlockState(te.getBlockPos()), this.mw.world.getBlockState(te.getBlockPos()), 3);
         }
      }
   }

   public void updateBuildingClient() {
      if ((this.world.getOverworldClockTime() + this.hashCode()) % 20L == 8L) {
         this.rebuildVillagerList();
      }

      if (this.isActive && this.isTownhall && (this.world.getOverworldClockTime() + this.hashCode()) % 100L == 48L) {
         this.triggerCompletionAdvancements();
      }
   }

   public void updateBuildingServer() {
      if (!this.world.isClientSide()) {
         if (this.mw.getBuilding(this.pos) != this) {
            MillLog.error(this, "Other building registered in my place: " + this.mw.getBuilding(this.pos));
         }

         if (this.location != null) {
            if (this.isActive && (this.world.getOverworldClockTime() + this.hashCode()) % 40L == 15L) {
               this.rebuildVillagerList();
            }

            if (this.isActive && (this.world.getOverworldClockTime() + this.hashCode()) % 100L == 48L) {
               this.triggerCompletionAdvancements();
            }

            Player player = this.world.getNearestPlayer(this.pos.getiX(), this.pos.getiY(), this.pos.getiZ(), MillConfigValues.KeepActiveRadius, false);
            if (this.isTownhall) {
               if (player != null && this.getPos().distanceTo(player) < MillConfigValues.KeepActiveRadius) {
                  this.loadChunks();
               } else if (player == null || this.getPos().distanceTo(player) > MillConfigValues.KeepActiveRadius + 32) {
                  this.unloadChunks();
               }

               this.isAreaLoaded = this.isVillageChunksLoaded();
               if (this.isActive && !this.isAreaLoaded) {
                  this.isActive = false;

                  for (Object o : this.world.players()) {
                     Player p = (Player)o;
                     this.sendBuildingPacket(p, false);
                  }

                  if (MillConfigValues.LogChunkLoader >= 1) {
                     MillLog.major(this, "Becoming inactive");
                  }

                  this.saveTownHall("becoming inactive");
               } else if (!this.isActive && this.isAreaLoaded) {
                  for (Object o : this.world.players()) {
                     Player p = (Player)o;
                     this.sendBuildingPacket(p, false);
                  }

                  if (MillConfigValues.LogChunkLoader >= 1) {
                     MillLog.major(this, "Becoming active");
                  }

                  this.isActive = true;
               }

               if (!this.isActive) {
                  return;
               }
            } else if (this.getTownHall() == null || !this.getTownHall().isActive) {
               return;
            }

            if (this.location != null) {
               try {
                  if (this.isTownhall && this.villageType != null) {
                     this.updateTownHall();
                  }

                  if (this.containsTags("grove")) {
                     this.updateGrove();
                  }

                  if (this.resManager.spawns.size() > 0) {
                     this.updatePens(false);
                  }

                  if (this.resManager.healingspots.size() > 0) {
                     this.updateHealingSpots();
                  }

                  if (this.resManager.mobSpawners.size() > 0 && player != null && this.pos.distanceToSquared(player) < 400.0) {
                     this.updateMobSpawners();
                  }

                  if (this.resManager.dispenderUnknownPowder.size() > 0) {
                     this.updateDispensers();
                  }

                  if (this.resManager.netherwartsoils.size() > 0) {
                     this.updateNetherWartSoils();
                  }

                  if (this.isInn) {
                     this.updateInn();
                  }

                  if (this.hasVisitors) {
                     this.getVisitorManager().update(false);
                  }

                  if (this.hasAutoSpawn) {
                     this.updateAutoSpawn();
                  }

                  if (Math.abs(this.world.getOverworldClockTime() + this.hashCode()) % 20L == 4L) {
                     this.unlockForNearbyPlayers();
                  }

                  this.getPanelManager().updateSigns();
                  if (this.isTownhall) {
                     if (this.saveNeeded) {
                        this.saveTownHall("Save needed");
                     } else if (this.world.getOverworldClockTime() - this.lastSaved > 1000L) {
                        this.saveTownHall("Delay up");
                     }
                  }

                  if (player != null && this.location.getPlan() != null && this.location.getPlan().exploreTag != null) {
                     this.checkExploreTag(player);
                  }

                  this.sendInitialBuildingPackets();
                  if (MillCommonUtilities.chanceOn(100)) {
                     for (Point p : this.resManager.chests) {
                        if (p.getMillChest(this.world) != null) {
                           p.getMillChest(this.world).buildingPos = this.getPos();
                        }
                     }
                  }

                  this.refreshGoods();
               } catch (Exception var5) {
                  int nbRepeats = MillLog.printException("Exception in TileEntityBuilding.onUpdate() for building " + this + ": ", var5);
                  if (nbRepeats < 5) {
                     Mill.proxy.sendChatAdmin(LanguageUtilities.string("ui.updateEntity"));
                  }
               }
            }
         }
      }
   }

   private boolean updateConstructionQueue(boolean ignoreCost) {
      boolean change = false;
      if (MillConfigValues.ignoreResourceCost) {
         ignoreCost = true;
      }

      change = this.findBuildingProject();
      change |= this.findBuildingConstruction(ignoreCost);
      if (this.getSimultaneousWallConstructionSlots() > 0) {
         change |= this.findBuildingConstructionWall(ignoreCost);
      }

      return change;
   }

   private void updateDispensers() {
      for (Point p : this.resManager.dispenderUnknownPowder) {
         if (MillCommonUtilities.chanceOn(5000)) {
            net.minecraft.world.level.block.entity.DispenserBlockEntity dispenser = p.getDispenser(this.world);
            if (dispenser != null) {
               MillCommonUtilities.putItemsInChest(dispenser, MillItems.UNKNOWN_POWDER, 1);
            }
         }
      }
   }

   private void updateGrove() {
      for (Point p : this.resManager.woodspawn) {
         if (MillCommonUtilities.chanceOn(4000) && WorldUtilities.getBlock(this.world, p) == Blocks.OAK_SAPLING) {
            this.growTree(this.world, p.getiX(), p.getiY(), p.getiZ(), MillCommonUtilities.random);
         }
      }
   }

   private void updateHealingSpots() {
      if (this.world.getOverworldClockTime() % 100L == 0L) {
         for (Point p : this.resManager.healingspots) {
            Player player = this.world.getNearestPlayer(p.getiX(), p.getiY(), p.getiZ(), 4.0, false);
            if (player != null && player.getHealth() < player.getMaxHealth()) {
               player.setHealth(player.getHealth() + 1.0F);
               ServerSender.sendTranslatedSentence(player, 'a', "other.buildinghealing", this.getNativeBuildingName());
            }
         }
      }
   }

   private void updateInn() {
      if (this.world.isBrightOutside()) {
         this.nightActionPerformed = false;
      } else if (!this.nightActionPerformed) {
         if (this.merchantRecord != null) {
            this.nbNightsMerchant++;
            if (this.nbNightsMerchant > 1) {
               this.attemptMerchantMove(false);
            }
         }

         this.nightActionPerformed = true;
      }
   }

   private void updateMobSpawners() {
      for (int i = 0; i < this.resManager.mobSpawners.size(); i++) {
         for (int j = 0; j < this.resManager.mobSpawners.get(i).size(); j++) {
            if (MillCommonUtilities.chanceOn(180)) {
               Block block = WorldUtilities.getBlock(this.world, this.resManager.mobSpawners.get(i).get(j));
               if (block == Blocks.SPAWNER) {
                  net.minecraft.world.entity.EntityType<?> spawnerType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                     .getValue(this.resManager.mobSpawnerTypes.get(i));
                  Class<? extends Entity> targetClass = spawnerType == null ? Entity.class : spawnerType.getBaseClass();
                  List<? extends Entity> mobs = WorldUtilities.getEntitiesWithinAABB(this.world, targetClass, this.resManager.mobSpawners.get(i).get(j), 10, 5);
                  int nbmob = mobs.size();
                  if (nbmob < 4) {
                     WorldUtilities.spawnMobsSpawner(this.world, this.resManager.mobSpawners.get(i).get(j), this.resManager.mobSpawnerTypes.get(i));
                  }
               }
            }
         }
      }
   }

   private void updateNetherWartSoils() {
      for (Point p : this.resManager.netherwartsoils) {
         if (MillCommonUtilities.chanceOn(1000) && WorldUtilities.getBlock(this.world, p.getAbove()) == Blocks.NETHER_WART) {
            int meta = WorldUtilities.getBlockMeta(this.world, p.getAbove());
            if (meta < 3) {
               WorldUtilities.setBlockMetadata(this.world, p.getAbove(), meta + 1);
            }
         }
      }
   }

   private void updatePens(boolean completeRespawn) {
      if ((completeRespawn || !this.world.isBrightOutside())
         && (this.getVillagerRecords().size() > 0 || this.location.getMaleResidents().isEmpty() && this.location.getFemaleResidents().isEmpty())
         && !this.world.isClientSide()) {
         int nbMaxRespawn = 0;

         for (List<Point> spawnPoints : this.resManager.spawns) {
            nbMaxRespawn += spawnPoints.size();
         }

         if (this.nbAnimalsRespawned <= nbMaxRespawn) {
            int sheep = 0;
            int cow = 0;
            int pig = 0;
            int chicken = 0;
            int squid = 0;
            int wolves = 0;

            for (Entity animal : WorldUtilities.getEntitiesWithinAABB(this.world, Animal.class, this.getPos(), 15, 20)) {
               if (animal instanceof Sheep) {
                  sheep++;
               } else if (animal instanceof Pig) {
                  pig++;
               } else if (animal instanceof Cow) {
                  cow++;
               } else if (animal instanceof Chicken) {
                  chicken++;
               } else if (animal instanceof Squid) {
                  squid++;
               } else if (animal instanceof Wolf) {
                  wolves++;
               }
            }

            for (int i = 0; i < this.resManager.spawns.size(); i++) {
               int nb = 0;
               if (this.resManager.spawnTypes.get(i).equals(Mill.ENTITY_SHEEP)) {
                  nb = sheep;
               } else if (this.resManager.spawnTypes.get(i).equals(Mill.ENTITY_CHICKEN)) {
                  nb = chicken;
               } else if (this.resManager.spawnTypes.get(i).equals(Mill.ENTITY_PIG)) {
                  nb = pig;
               } else if (this.resManager.spawnTypes.get(i).equals(Mill.ENTITY_COW)) {
                  nb = cow;
               } else if (this.resManager.spawnTypes.get(i).equals(Mill.ENTITY_SQUID)) {
                  nb = squid;
               } else if (this.resManager.spawnTypes.get(i).equals(Mill.ENTITY_WOLF)) {
                  nb = wolves;
               }

               int multipliyer = 1;
               if (this.resManager.spawnTypes.get(i).equals(Mill.ENTITY_SQUID)) {
                  multipliyer = 2;
               }

               for (int j = 0; j < this.resManager.spawns.get(i).size() * multipliyer - nb; j++) {
                  if (completeRespawn || MillCommonUtilities.chanceOn(100)) {
                     net.minecraft.world.entity.EntityType<?> spawnType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getValue(this.resManager.spawnTypes.get(i));
                     net.minecraft.world.entity.Entity created = spawnType == null
                        ? null
                        : spawnType.create(this.world, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
                     if (created instanceof Mob animalx) {
                        Point pen = this.resManager.spawns.get(i).get(MillCommonUtilities.randomInt(this.resManager.spawns.get(i).size()));
                        animalx.setPos(pen.getiX() + 0.5, pen.getiY(), pen.getiZ() + 0.5);
                        this.world.addFreshEntity(animalx);
                        this.nbAnimalsRespawned++;
                     }
                  }
               }
            }
         }
      } else {
         this.nbAnimalsRespawned = 0;
      }
   }

   private void updateRaid() {
      if (this.world.getOverworldClockTime() > this.raidPlanningStart + 24000L && this.raidStart == 0L) {
         if (MillConfigValues.LogDiplomacy >= 2) {
            MillLog.minor(this, "Starting raid on " + this.mw.getBuilding(this.raidTarget));
         }

         this.startRaid();
      } else if (this.raidStart > 0L && this.world.getOverworldClockTime() > this.raidStart + 23000L) {
         Building distantVillage = this.mw.getBuilding(this.raidTarget);
         if (distantVillage != null) {
            if (!distantVillage.isActive) {
               this.endRaid();
            }
         } else {
            this.cancelRaid();

            for (VillagerRecord vr : this.getVillagerRecords().values()) {
               vr.awayraiding = false;
            }
         }
      }
   }

   private void updateTownHall() throws MillLog.MillenaireException {
      if (this.getVillagerRecords().size() > 0) {
         this.updateWorldInfo();
      }

      if (MillConfigValues.autoConvertProfiles
         && !Mill.proxy.isTrueServer()
         && this.villageType.playerControlled
         && Mill.proxy.getTheSinglePlayer() != null
         && (this.controlledBy == null || !this.controlledBy.equals(Mill.proxy.getTheSinglePlayer().getUUID()))) {
         UUID oldControlledBy = this.controlledBy;
         this.controlledBy = Mill.proxy.getTheSinglePlayer().getUUID();
         this.controlledByName = Mill.proxy.getTheSinglePlayer().getName().getString();
         MillLog.major(
            this, "Switched controller from " + oldControlledBy + " to " + this.controlledBy + " (" + this.controlledByName + "), the new single player."
         );
      }

      this.closestPlayer = this.world.getNearestPlayer(this.pos.getiX(), this.pos.getiY(), this.pos.getiZ(), 100.0, false);

      for (ConstructionIP cip : this.getConstructionsInProgress()) {
         this.completeConstruction(cip);
      }

      if ((this.world.getOverworldClockTime() + this.hashCode()) % 20L == 3L) {
         this.updateConstructionQueue(false);
      }

      this.checkSeller();
      this.checkWorkers();
      if ((this.world.getOverworldClockTime() + this.hashCode()) % 10L == 0L) {
         this.checkBattleStatus();
      }

      if ((this.world.getOverworldClockTime() + this.hashCode()) % 10L == 5L) {
         this.killMobs();
      }

      if (!this.declaredPos && this.world != null) {
         if (this.villageType.lonebuilding) {
            this.mw.registerLoneBuildingsLocation(this.world, this.getPos(), this.getVillageQualifiedName(), this.villageType, this.culture, false, null);
         } else {
            this.mw.registerVillageLocation(this.world, this.getPos(), this.getVillageQualifiedName(), this.villageType, this.culture, false, null);
         }

         this.declaredPos = true;
      }

      if (this.lastVillagerRecordsRepair == 0L) {
         this.lastVillagerRecordsRepair = this.world.getOverworldClockTime();
      } else if (this.world.getOverworldClockTime() - this.lastVillagerRecordsRepair >= 100L) {
         this.respawnVillagersIfNeeded();
         this.lastVillagerRecordsRepair = this.world.getOverworldClockTime();
      }

      if (this.world.isBrightOutside()) {
         this.nightActionPerformed = false;
      } else if (!this.nightActionPerformed) {
         if (!this.villageType.playerControlled && !this.villageType.lonebuilding) {
            for (Player player : VillageUtilities.getServerPlayers(this.world)) {
               UserProfile profile = VillageUtilities.getServerProfile(this.world, player);
               if (profile != null) {
                  profile.adjustDiplomacyPoint(this, 5);
               }
            }

            for (Point p : this.relations.keySet()) {
               if (MillCommonUtilities.chanceOn(10)) {
                  Building village = this.mw.getBuilding(p);
                  if (village != null) {
                     int relation = this.relations.get(p);
                     int improveChance;
                     if (relation < -90) {
                        improveChance = 0;
                     } else if (relation < -50) {
                        improveChance = 30;
                     } else if (relation < 0) {
                        improveChance = 40;
                     } else if (relation > 90) {
                        improveChance = 100;
                     } else if (relation > 50) {
                        improveChance = 70;
                     } else {
                        improveChance = 60;
                     }

                     if (MillCommonUtilities.randomInt(100) < improveChance) {
                        if (this.relations.get(p) < 100) {
                           this.adjustRelation(p, 10 + MillCommonUtilities.randomInt(10), false);
                           ServerSender.sendTranslatedSentenceInRange(
                              this.world,
                              this.getPos(),
                              MillConfigValues.KeepActiveRadius,
                              '2',
                              "ui.relationfriendly",
                              this.getVillageQualifiedName(),
                              village.getVillageQualifiedName(),
                              VillageUtilities.getRelationName(this.relations.get(p))
                           );
                        }
                     } else if (this.relations.get(p) > -100) {
                        this.adjustRelation(p, -10 - MillCommonUtilities.randomInt(10), false);
                        ServerSender.sendTranslatedSentenceInRange(
                           this.world,
                           this.getPos(),
                           MillConfigValues.KeepActiveRadius,
                           '6',
                           "ui.relationunfriendly",
                           this.getVillageQualifiedName(),
                           village.getVillageQualifiedName(),
                           VillageUtilities.getRelationName(this.relations.get(p))
                        );
                     }
                  }
               }
            }
         }

         this.nightActionPerformed = true;
      }

      if (this.world.getOverworldClockTime() % 1000L == 0L
         && (this.villageType.playerControlled || MillConfigValues.DEV)
         && this.countGoods(MillItems.PARCHMENT_VILLAGE_SCROLL, 0) == 0) {
         for (int i = 0; i < this.mw.villagesList.pos.size(); i++) {
            Point px = this.mw.villagesList.pos.get(i);
            if (this.getPos().sameBlock(px)) {
               this.storeItemStack(ItemParchment.createParchmentForVillage(this));
            }
         }
      }

      if (this.villageType.playerControlled && this.world.getOverworldClockTime() % 1000L == 0L && this.countGoods(MillItems.NEGATION_WAND) == 0) {
         this.storeGoods(MillItems.NEGATION_WAND, 1);
      }

      if (this.controlledBy != null && this.controlledByName == null) {
         GameProfile profile = this.world.getServer() == null
            ? null
            : this.world.getServer().services().profileResolver().fetchById(this.controlledBy).orElse(null);
         if (profile != null) {
            this.controlledByName = profile.name();
         }
      }

      if (this.world.getOverworldClockTime() % 200L == 0L) {
         this.updateAchievements();
      }

      this.handlePathingResult();
      if (this.autobuildPaths) {
         this.clearOldPaths();
         this.constructCalculatedPaths();
      }

      if (this.marvelManager != null) {
         this.marvelManager.update();
      }
   }

   public void updateWorldInfo() throws MillLog.MillenaireException {
      if (this.villageType == null) {
         MillLog.error(this, "updateWorldInfo: villageType is null");
      } else {
         List<BuildingLocation> locations = new ArrayList<>();

         for (BuildingLocation l : this.getLocations()) {
            locations.add(l);
         }

         for (ConstructionIP cip : this.getConstructionsInProgress()) {
            if (cip.getBuildingLocation() != null) {
               locations.add(cip.getBuildingLocation());
            }
         }

         if (this.winfo.world == null) {
            boolean areaChanged = this.winfo.update(this.world, locations, this.location.pos, this.villageType.radius);
            if (areaChanged) {
               this.rebuildRegionMapper(true);
            }
         } else {
            this.winfo.updateNextChunk();
         }
      }
   }

   private void validateVillagerList() {
      for (MillVillager v : this.getKnownVillagers()) {
         if (v == null) {
            MillLog.error(this, "Null value in villager list");
         }

         if (v.isReallyDead() && MillConfigValues.LogTileEntityBuilding >= 2) {
            MillLog.minor(this, "Villager " + v + " is dead.");
         }

         List<VillagerRecord> found = new ArrayList<>();

         for (VillagerRecord vr : this.getVillagerRecords().values()) {
            if (vr.matches(v)) {
               found.add(vr);
            }
         }

         if (found.size() == 0) {
            MillLog.error(this, "Villager " + v + " not present in records.");
         } else if (found.size() > 1) {
            MillLog.error(this, "Villager " + v + " present " + found.size() + " times in records: ");

            for (VillagerRecord vrx : found) {
               MillLog.major(this, vrx.toString() + " / " + vrx.hashCode());
            }
         }
      }

      for (VillagerRecord vrx : this.getVillagerRecords().values()) {
         List<MillVillager> found = new ArrayList<>();
         if (vrx.getHousePos() == null) {
            MillLog.error(this, "Record " + vrx + " has no house.");
         }

         for (MillVillager v : this.getKnownVillagers()) {
            if (vrx.matches(v)) {
               found.add(v);
            }
         }

         if (found.size() != vrx.nb) {
            MillLog.error(this, "Record " + vrx + " present " + found.size() + " times in villagers, previously: " + vrx.nb + ". Villagers: ");

            for (MillVillager vx : found) {
               MillLog.major(this, vx.toString() + " / " + vx.hashCode());
            }

            vrx.nb = found.size();
         }
      }
   }

   private void writePaths() {
      File buildingsDir = MillCommonUtilities.getBuildingsDir(this.world);
      File file1 = new File(buildingsDir, this.getPos().getPathString() + "_paths.bin");
      if (this.pathsToBuild != null) {
         try (FileOutputStream fos = new FileOutputStream(file1);
              DataOutputStream ds = new DataOutputStream(fos)) {
            ds.writeInt(this.pathsToBuild.size());

            for (List<BuildingBlock> path : this.pathsToBuild) {
               ds.writeInt(path.size());

               for (BuildingBlock b : path) {
                  ds.writeInt(b.p.getiX());
                  ds.writeShort(b.p.getiY());
                  ds.writeInt(b.p.getiZ());
                  ds.writeInt(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(b.block));
                  ds.writeByte(b.getMeta());
                  ds.writeByte(b.special);
               }
            }
         } catch (IOException writeError) {
            // A swallowed write leaves a truncated paths file that mis-loads next time.
            throw MillCrash.fail("Save", "writing pathsToBuild for " + this.getPos() + ": " + writeError);
         }
      } else {
         file1.renameTo(new File(buildingsDir, this.getPos().getPathString() + "ToDelete"));
         file1.delete();
      }

      file1 = new File(buildingsDir, this.getPos().getPathString() + "_pathstoclear.bin");
      if (this.oldPathPointsToClear != null) {
         try (FileOutputStream fos = new FileOutputStream(file1);
              DataOutputStream ds = new DataOutputStream(fos)) {
            ds.writeInt(this.oldPathPointsToClear.size());

            for (Point p : this.oldPathPointsToClear) {
               ds.writeInt(p.getiX());
               ds.writeShort(p.getiY());
               ds.writeInt(p.getiZ());
            }
         } catch (IOException writeError) {
            throw MillCrash.fail("Save", "writing oldPathPointsToClear for " + this.getPos() + ": " + writeError);
         }
      } else {
         file1.delete();
      }

      this.pathsChanged = false;
   }

   public void writeToNBT(CompoundTag nbttagcompound) {
      if (this.location == null) {
         MillLog.error(this, "Null location. Skipping write.");
      } else {
         nbttagcompound.putString("versionCompatibility", "1.0");

         try {
            this.pos.write(nbttagcompound, "pos");
            this.location.writeToNBT(nbttagcompound, "buildingLocation", "self");
            nbttagcompound.putBoolean("chestLocked", this.chestLocked);
            if (this.name != null && this.name.length() > 0) {
               nbttagcompound.putString("name", this.name);
            }

            nbttagcompound.putString("qualifier", this.getQualifier());
            nbttagcompound.putBoolean("isTownhall", this.isTownhall);
            nbttagcompound.putString("culture", this.culture.key);
            if (this.villageType != null) {
               nbttagcompound.putString("villageType", this.villageType.key);
            }

            if (this.controlledBy != null) {
               nbttagcompound.store("controlledByUUID", net.minecraft.core.UUIDUtil.CODEC, this.controlledBy);
            }

            if (this.getTownHallPos() != null) {
               this.getTownHallPos().write(nbttagcompound, "townHallPos");
            }

            nbttagcompound.putBoolean("nightActionPerformed", this.nightActionPerformed);
            nbttagcompound.putBoolean("nightBackgroundActionPerformed", this.nightBackgroundActionPerformed);
            nbttagcompound.putInt("nbAnimalsRespawned", this.nbAnimalsRespawned);
            ListTag nbttaglist = new ListTag();

            for (Point p : this.buildings) {
               CompoundTag nbttagcompound1 = new CompoundTag();
               p.write(nbttagcompound1, "pos");
               nbttaglist.add(nbttagcompound1);
            }

            nbttagcompound.put("buildings", nbttaglist);

            for (ConstructionIP cip : this.getConstructionsInProgress()) {
               nbttagcompound.putInt("bblocksPos_" + cip.getId(), cip.getBblocksPos());
               if (cip.isBblocksChanged()) {
                  cip.writeBblocks();
                  if (MillConfigValues.LogHybernation >= 1) {
                     MillLog.major(this, "Saved bblocks.");
                  }
               }
            }

            nbttaglist = new ListTag();

            for (BuildingProject.EnumProjects ep : BuildingProject.EnumProjects.values()) {
               if (this.buildingProjects.containsKey(ep)) {
                  CopyOnWriteArrayList<BuildingProject> projectsLevel = this.buildingProjects.get(ep);

                  for (BuildingProject project : projectsLevel) {
                     if (project.location != null && !project.location.isSubBuildingLocation) {
                        CompoundTag nbttagcompound1 = new CompoundTag();
                        project.location.writeToNBT(nbttagcompound1, "location", "buildingProjects");
                        nbttaglist.add(nbttagcompound1);
                        if (MillConfigValues.LogHybernation >= 1) {
                           MillLog.major(
                              this,
                              "Writing building location: "
                                 + project.location
                                 + " (level: "
                                 + project.location.level
                                 + ", variation: "
                                 + project.location.getVariation()
                                 + ")"
                           );
                        }
                     }
                  }

                  for (BuildingProject projectx : projectsLevel) {
                     if (projectx.location != null && projectx.location.isSubBuildingLocation) {
                        CompoundTag nbttagcompound1 = new CompoundTag();
                        projectx.location.writeToNBT(nbttagcompound1, "location", "buildingProjects");
                        nbttaglist.add(nbttagcompound1);
                        if (MillConfigValues.LogHybernation >= 1) {
                           MillLog.major(
                              this,
                              "Writing building location: "
                                 + projectx.location
                                 + " (level: "
                                 + projectx.location.level
                                 + ", variation: "
                                 + projectx.location.getVariation()
                                 + ")"
                           );
                        }
                     }
                  }
               }
            }

            nbttagcompound.put("locations", nbttaglist);
            if (this.buildingGoal != null) {
               nbttagcompound.putString("buildingGoal", this.buildingGoal);
               if (MillConfigValues.LogHybernation >= 1) {
                  MillLog.major(this, "Writing building goal: " + this.buildingGoal);
               }
            }

            nbttagcompound.putInt("buildingGoalLevel", this.buildingGoalLevel);
            nbttagcompound.putInt("buildingGoalVariation", this.buildingGoalVariation);
            if (this.buildingGoalIssue != null) {
               nbttagcompound.putString("buildingGoalIssue", this.buildingGoalIssue);
            }

            if (this.buildingGoalLocation != null) {
               this.buildingGoalLocation.writeToNBT(nbttagcompound, "buildingGoalLocation", "buildingGoalLocation");
               if (MillConfigValues.LogHybernation >= 1) {
                  MillLog.major(this, "Writing buildingGoalLocation: " + this.buildingGoalLocation);
               }
            }

            nbttagcompound.putInt("nbConstructions", this.getConstructionsInProgress().size());

            for (ConstructionIP cipx : this.getConstructionsInProgress()) {
               nbttagcompound.putBoolean("buildingLocationIP_" + cipx.getId() + "_isWall", cipx.isWallConstruction());
               if (cipx.getBuildingLocation() != null) {
                  cipx.getBuildingLocation().writeToNBT(nbttagcompound, "buildingLocationIP_" + cipx.getId(), "buildingLocationIP_" + cipx.getId());
                  if (MillConfigValues.LogHybernation >= 1) {
                     MillLog.major(this, "Writing buildingLocationIP_" + cipx.getId() + ": " + cipx.getBuildingLocation());
                  }
               }
            }

            nbttaglist = new ListTag();

            for (String s : this.visitorsList) {
               CompoundTag nbttagcompound1 = new CompoundTag();
               nbttagcompound1.putString("visitor", s);
               nbttaglist.add(nbttagcompound1);
            }

            nbttagcompound.put("visitorsList", nbttaglist);
            nbttaglist = new ListTag();

            for (String s : this.buildingsBought) {
               CompoundTag nbttagcompound1 = new CompoundTag();
               nbttagcompound1.putString("key", s);
               nbttaglist.add(nbttagcompound1);
            }

            nbttagcompound.put("buildingsBought", nbttaglist);
            nbttagcompound.putBoolean("updateRaidPerformed", this.updateRaidPerformed);
            nbttagcompound.putBoolean("nightBackgroundActionPerformed", this.nightBackgroundActionPerformed);
            nbttagcompound.putBoolean("nightActionPerformed", this.nightActionPerformed);
            nbttagcompound.putBoolean("underAttack", this.underAttack);
            if (this.raidTarget != null) {
               this.raidTarget.write(nbttagcompound, "raidTarget");
               nbttagcompound.putLong("raidPlanningStart", this.raidPlanningStart);
               nbttagcompound.putLong("raidStart", this.raidStart);
            }

            nbttaglist = new ListTag();

            for (String s : this.raidsPerformed) {
               CompoundTag nbttagcompound1 = new CompoundTag();
               nbttagcompound1.putString("raid", s);
               nbttaglist.add(nbttagcompound1);
            }

            nbttagcompound.put("raidsPerformed", nbttaglist);
            nbttaglist = new ListTag();

            for (String s : this.raidsSuffered) {
               CompoundTag nbttagcompound1 = new CompoundTag();
               nbttagcompound1.putString("raid", s);
               nbttaglist.add(nbttagcompound1);
            }

            nbttagcompound.put("raidsTaken", nbttaglist);
            if (this.villageType != null && !this.villageType.lonebuilding) {
               nbttaglist = new ListTag();

               for (Point p : this.relations.keySet()) {
                  Building dv = this.mw.getBuilding(p);
                  if (dv != null && dv.villageType == null) {
                     MillLog.error(dv, "No village type!");
                  } else if (dv != null && !dv.villageType.lonebuilding) {
                     CompoundTag nbttagcompound1 = new CompoundTag();
                     p.write(nbttagcompound1, "pos");
                     nbttagcompound1.putInt("value", this.relations.get(p));
                     nbttaglist.add(nbttagcompound1);
                  }
               }

               nbttagcompound.put("relations", nbttaglist);
            }

            if (this.parentVillage != null) {
               this.parentVillage.write(nbttagcompound, "parentVillage");
            }

            nbttaglist = MillCommonUtilities.writeInventory(this.imported);
            nbttagcompound.put("importedGoodsNew", nbttaglist);
            nbttaglist = MillCommonUtilities.writeInventory(this.exported);
            nbttagcompound.put("exportedGoodsNew", nbttaglist);
            if (MillConfigValues.LogTileEntityBuilding >= 3) {
               MillLog.debug(this, "Saving building. Location: " + this.location + ", pos: " + this.getPos());
            }

            nbttaglist = new ListTag();

            for (Point px : this.subBuildings) {
               CompoundTag nbttagcompound1 = new CompoundTag();
               px.write(nbttagcompound1, "pos");
               nbttaglist.add(nbttagcompound1);
            }

            nbttagcompound.put("subBuildings", nbttaglist);
            if (this.pujas != null) {
               CompoundTag tag = new CompoundTag();
               this.pujas.writeToNBT(tag);
               nbttagcompound.put("pujas", tag);
            }

            nbttagcompound.putLong("lastGoodsRefresh", this.lastGoodsRefresh);
            nbttagcompound.putInt("pathsToBuildIndex", this.pathsToBuildIndex);
            nbttagcompound.putInt("pathsToBuildPathIndex", this.pathsToBuildPathIndex);
            nbttagcompound.putInt("oldPathPointsToClearIndex", this.oldPathPointsToClearIndex);
            if (this.brickColourTheme != null) {
               nbttagcompound.putString("brickColourTheme", this.brickColourTheme.key);
            }

            Set<String> tags = this.getTags();
            if (tags.size() > 0 && MillConfigValues.LogTags >= 1) {
               MillLog.major(this, "Tags to write: " + MillCommonUtilities.flattenStrings(tags));
            }

            nbttaglist = new ListTag();

            for (String tag : tags) {
               CompoundTag nbttagcompound1 = new CompoundTag();
               nbttagcompound1.putString("value", tag);
               nbttaglist.add(nbttagcompound1);
               if (MillConfigValues.LogTags >= 3) {
                  MillLog.debug(this, "Writing tag: " + tag);
               }
            }

            nbttagcompound.put("tags", nbttaglist);
            this.resManager.writeToNBT(nbttagcompound);
            if (this.marvelManager != null) {
               this.marvelManager.writeToNBT(nbttagcompound);
            }

            if (this.pathsChanged) {
               this.writePaths();
            }

            if (this.isTownhall && this.bannerStack != null && !this.bannerStack.isEmpty()) {
               nbttagcompound.store("bannerStack", ItemStack.CODEC, this.bannerStack);
            }
         } catch (Exception saveError) {
            // Swallowing this previously wrote a partial/empty building tag, silently
            // corrupting the save. Fail loudly so the save is not committed half-written.
            throw MillCrash.fail("Save", "saving building of type " + this.location + " at " + this.pos + ": " + saveError);
         }
      }
   }

   private class PathCreator implements IAStarPathedEntity {
      final Building.PathCreatorQueue queue;
      final InvItem pathConstructionGood;
      final int pathWidth;
      final Building destination;
      final Point startPos;
      final Point endPos;

      PathCreator(Building.PathCreatorQueue info, InvItem pathConstructionGood, int pathWidth, Building destination, Point startPos, Point endPos) {
         this.pathConstructionGood = pathConstructionGood;
         this.pathWidth = pathWidth;
         this.destination = destination;
         this.queue = info;
         this.startPos = startPos;
         this.endPos = endPos;
      }

      @Override
      public void onFoundPath(List<AStarNode> result) {
         if (this.queue.isComplete()) {
            MillLog.error(Building.this, "onFoundPath triggered on completed info object.");
         } else {
            this.queue.addReceivedPath(result);
         }
      }

      @Override
      public void onNoPathAvailable() {
         if (this.queue.isComplete()) {
            MillLog.error(Building.this, "onNoPathAvailable triggered on completed info object.");
         } else {
            if (MillConfigValues.LogVillagePaths >= 2) {
               MillLog.minor(Building.this, "Path calculation failed. Target: " + this.destination);
            }

            this.queue.addFailedPath();
         }
      }
   }

   private class PathCreatorQueue {
      private final List<Building.PathCreator> pathCreators = new ArrayList<>();
      private final List<List<AStarNode>> pathsReceived = new ArrayList<>();
      int nbAnswers = 0;
      int pos = 0;

      PathCreatorQueue() {
      }

      public void addFailedPath() {
         this.pathsReceived.add(null);
         this.nbAnswers++;
         if (this.isComplete()) {
            this.sendNewPathsToBuilding();
         } else {
            this.startNextPath();
         }
      }

      public void addPathCreator(Building.PathCreator pathCreator) {
         this.pathCreators.add(pathCreator);
      }

      public void addReceivedPath(List<AStarNode> path) {
         this.pathsReceived.add(path);
         this.nbAnswers++;
         if (this.isComplete()) {
            this.sendNewPathsToBuilding();
         } else {
            this.startNextPath();
         }
      }

      public boolean isComplete() {
         return this.pathCreators.size() == this.nbAnswers;
      }

      private void sendNewPathsToBuilding() {
         Building.this.pathQueue = this;
      }

      public void startNextPath() {
         if (this.pos < this.pathCreators.size()) {
            Building.PathCreator pathCreator = this.pathCreators.get(this.pos);
            this.pos++;
            AStarPathPlannerJPS jpsPathPlanner = new AStarPathPlannerJPS(Building.this.world, pathCreator, false);

            try {
               jpsPathPlanner.getPath(
                  pathCreator.startPos.getiX(),
                  pathCreator.startPos.getiY(),
                  pathCreator.startPos.getiZ(),
                  pathCreator.endPos.getiX(),
                  pathCreator.endPos.getiY(),
                  pathCreator.endPos.getiZ(),
                  Building.PATH_BUILDER_JPS_CONFIG
               );
            } catch (ThreadSafeUtilities.ChunkAccessException var4) {
               if (MillConfigValues.LogChunkLoader >= 1) {
                  MillLog.major(this, "LevelChunk access violation while calculating new path.");
               }
            }
         }
      }
   }

   public class RegionMapperThread extends Thread {
      VillageMapInfo winfo;

      public RegionMapperThread(VillageMapInfo wi) {
         this.winfo = wi;
      }

      @Override
      public void run() {
         RegionMapper temp = new RegionMapper();
         if (MillConfigValues.LogPathing >= 1) {
            MillLog.major(this, "Start");
         }

         long tm = System.currentTimeMillis();

         try {
            if (temp.createConnectionsTable(this.winfo, Building.this.resManager.getSleepingPos())) {
               Building.this.regionMapper = temp;
               Building.this.lastPathingUpdate = Building.this.world.getOverworldClockTime();
            } else {
               Building.this.lastPathingUpdate = Building.this.world.getOverworldClockTime();
               Building.this.regionMapper = null;
            }
         } catch (MillLog.MillenaireException var5) {
            MillLog.printException(var5);
         }

         if (MillConfigValues.LogPathing >= 1) {
            MillLog.major(this, "Done: " + (System.currentTimeMillis() - tm) * 1.0 / 1000.0);
         }

         Building.this.rebuildingRegionMapper = false;
      }
   }

   private class SaveWorker extends Thread {
      private final String reason;

      private SaveWorker(String reason) {
         this.reason = reason;
      }

      @Override
      public void run() {
         if (Building.this.isTownhall) {
            synchronized (Building.this) {
               long startTime = System.currentTimeMillis();
               CompoundTag mainTag = new CompoundTag();
               ListTag nbttaglist = new ListTag();

               for (int i = 0; i < Building.this.buildings.size(); i++) {
                  Point p = Building.this.buildings.get(i);
                  if (p != null) {
                     Building b = Building.this.mw.getBuilding(p);
                     if (b != null) {
                        CompoundTag buildingTag = new CompoundTag();
                        b.writeToNBT(buildingTag);
                        nbttaglist.add(buildingTag);
                     }
                  }
               }

               mainTag.put("buildings", nbttaglist);
               File millenaireDir = Building.this.mw.millenaireDir;
               if (!millenaireDir.exists()) {
                  millenaireDir.mkdir();
               }

               File buildingsDir = new File(millenaireDir, "buildings");
               if (!buildingsDir.exists()) {
                  buildingsDir.mkdir();
               }

               File tempFile = new File(buildingsDir, Building.this.getPos().getPathString() + "_temp.gz");

               try {
                  try (FileOutputStream fileoutputstream = new FileOutputStream(tempFile)) {
                     NbtIo.writeCompressed(mainTag, fileoutputstream);
                     fileoutputstream.flush();
                  }
                  Path finalPath = new File(buildingsDir, Building.this.getPos().getPathString() + ".gz").toPath();
                  // Windows: Files.move(REPLACE_EXISTING) throws FileSystemException "file in use" if the
                  // target .gz is momentarily held (concurrent autosave/load handle, AV scan). Retry a few
                  // times so a transient lock doesn't silently lose this building's saved state; if it still
                  // fails after retries, report it loudly (don't swallow) so the data-loss is visible.
                  IOException moveFailure = null;
                  for (int attempt = 0; attempt < 8; attempt++) {
                     try {
                        Files.move(tempFile.toPath(), finalPath, StandardCopyOption.REPLACE_EXISTING);
                        moveFailure = null;
                        break;
                     } catch (IOException moveEx) {
                        moveFailure = moveEx;
                        try {
                           Thread.sleep(25L);
                        } catch (InterruptedException ie) {
                           Thread.currentThread().interrupt();
                           break;
                        }
                     }
                  }
                  if (moveFailure != null) {
                     MillLog.error(Building.this, "FAILED to save building " + Building.this.getPos()
                        + " after retries (target .gz locked) — state for this building was NOT persisted: " + moveFailure);
                  }
               } catch (IOException var12) {
                  MillLog.printException(var12);
               }

               if (MillConfigValues.LogHybernation >= 1) {
                  MillLog.major(
                     Building.this,
                     "Saved "
                        + Building.this.buildings.size()
                        + " buildings in "
                        + (System.currentTimeMillis() - startTime)
                        + " ms due to "
                        + this.reason
                        + " ("
                        + Building.this.saveReason
                        + ")."
                  );
               }

               Building.this.lastSaved = Building.this.world.getOverworldClockTime();
               Building.this.saveNeeded = false;
               Building.this.saveReason = null;
               Building.this.saveWorker = null;
            }
         }
      }
   }
}
