package org.millenaire.common.world;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.TileEntityPanel;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.quest.SpecialQuestActions;
import org.millenaire.common.utilities.DevModUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillFiles;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.VillagerRecord;

public class MillWorldData {
   public static final String CULTURE_CONTROL = "culturecontrol_";
   public static final String PUJAS = "pujas";
   public static final String MAYANSACRIFICES = "mayansacrifices";
   private static HashMap<Point, String> buildingsTags = new HashMap<>();
   private static HashMap<Point, Integer> buildingsVariation = new HashMap<>();
   private static HashMap<Point, String> buildingsLocation = new HashMap<>();
   private final HashMap<Point, Building> buildings = new HashMap<>();
   private final HashMap<Long, MillVillager> villagers = new HashMap<>();
   private final HashMap<Long, VillagerRecord> villagerRecords = new HashMap<>();
   public final List<String> globalTags = new ArrayList<>();
   public MillCommonUtilities.VillageList loneBuildingsList = new MillCommonUtilities.VillageList();
   public final File millenaireDir;
   public File saveDir = null;
   public MillCommonUtilities.VillageList villagesList = new MillCommonUtilities.VillageList();
   public long lastWorldUpdate = 0L;
   public HashMap<UUID, UserProfile> profiles = new HashMap<>();
   public List<TileEntityPanel.PanelPacketInfo> panelPacketInfos = new ArrayList<>();
   public Level world;
   public boolean millenaireEnabled = false;
   private int lastForcePreloadUpdate;
   private long lastWorldTime = Long.MAX_VALUE;
   public boolean generateVillages;
   public boolean generateVillagesSet = false;

   /** Spawn position of the world (replaces removed {@code world.getSpawnPoint()}). */
   private static BlockPos getSpawnPos(Level world) {
      return world.getRespawnData().pos();
   }

   /** All loaded entities (replaces removed {@code world.loadedEntityList}). */
   private Iterable<Entity> getAllEntities() {
      if (this.world instanceof ServerLevel sl) {
         return sl.getAllEntities();
      }
      // 26.2: client-side iteration uses ClientLevel.entitiesForRendering() (the 1.12
      // loadedEntityList is gone). The instanceof guard keeps client classes off the server path.
      if (this.world instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
         return cl.entitiesForRendering();
      }
      return java.util.Collections.emptyList();
   }

   public MillWorldData(Level world) {
      this.world = world;
      if (!world.isClientSide()) {
         this.saveDir = MillFiles.getWorldSaveDir(world);
         this.millenaireEnabled = true;
         this.millenaireDir = new File(this.saveDir, "millenaire");
         if (!this.millenaireDir.exists()) {
            this.millenaireDir.mkdir();
         }
      } else {
         this.millenaireDir = null;
         // Client-side MillWorldData must also be "enabled" so the client tick (ClientTickHandler →
         // updateWorldClient) rebuilds the client villager list; without it clientWorld.villagers stays
         // empty, the villager-data packet (id=3) can't resolve its villager, and villagers render with
         // no type/texture (invisible).
         this.millenaireEnabled = true;
      }

      Culture.removeServerContent();
   }

   public void addBuilding(Building b, Point p) {
      this.buildings.put(p, b);
   }

   public Collection<Building> allBuildings() {
      return this.buildings.values();
   }

   public boolean buildingExists(Point p) {
      return this.buildings.containsKey(p);
   }

   public void checkConnections() {
      for (UserProfile profile : this.profiles.values()) {
         if (profile.connected && profile.getPlayer() == null) {
            profile.disconnectUser();
         }
      }
   }

   public void clearGlobalTag(String tag) {
      if (this.globalTags.contains(tag)) {
         this.globalTags.remove(tag);
         this.saveGlobalTags();
         if (!this.world.isClientSide()) {
            for (UserProfile up : this.profiles.values()) {
               if (up.connected) {
                  up.sendProfilePacket(7);
               }
            }
         }
      }
   }

   public void clearPanelQueue() {
      List<TileEntityPanel.PanelPacketInfo> toDelete = new ArrayList<>();

      for (TileEntityPanel.PanelPacketInfo pinfo : this.panelPacketInfos) {
         TileEntityPanel panel = pinfo.pos.getPanel(this.world);
         if (panel != null) {
            panel.panelType = pinfo.panelType;
            panel.buildingPos = pinfo.buildingPos;
            panel.villager_id = pinfo.villager_id;
            toDelete.add(pinfo);
         }
      }

      for (TileEntityPanel.PanelPacketInfo pinfox : toDelete) {
         this.panelPacketInfos.remove(pinfox);
      }
   }

   public void clearVillagerOfId(long id) {
      if (this.villagers.get(id) != null) {
         MillVillager villager = this.villagers.get(id);
         if (MillConfigValues.LogVillagerSpawn >= 1) {
            MillLog.major(this, "Removing village from global list: " + villager);
         }

         if (this.villagers.remove(id) == null) {
            MillLog.error(this, "Could not delete villager " + villager);
         }

         if (villager.getHouse() != null) {
            villager.getHouse().rebuildVillagerList();
         }

         if (villager.getTownHall() != null && villager.getTownHall() != villager.getHouse()) {
            villager.getTownHall().rebuildVillagerList();
         }
      } else if (MillConfigValues.LogVillagerSpawn >= 1) {
         MillLog.major(this, "Could not find villager of id " + id + " to despawn him.");
      }
   }

   public void displayTagActionData(Player player) {
      String s = "";

      for (String tag : this.globalTags) {
         s = s + tag + " ";
      }

      ServerSender.sendChat(player, ChatFormatting.GREEN, "Tags: " + s);
      ServerSender.sendChat(player, ChatFormatting.GREEN, "ActionData: " + s);
      ServerSender.sendChat(player, ChatFormatting.GREEN, "Time: " + this.world.getGameTime() % 24000L + " / " + this.world.getGameTime());
   }

   public void displayVillageList(Player player, boolean loneBuildings) {
      MillCommonUtilities.VillageList list;
      if (loneBuildings) {
         list = this.loneBuildingsList;
      } else {
         list = this.villagesList;
      }

      UserProfile profile = this.getProfile(player);
      List<MillCommonUtilities.VillageInfo> villageList = new ArrayList<>();

      for (int i = 0; i < list.names.size(); i++) {
         Point p = list.pos.get(i);
         int distance = Mth.floor(p.horizontalDistanceTo(player));
         if (distance <= MillConfigValues.BackgroundRadius) {
            String direction = new Point(player).directionTo(p, true);
            Building townHall = this.getBuilding(p);
            String loaded;
            if (townHall == null) {
               loaded = "command.inactive";
            } else if (townHall.isActive) {
               loaded = "command.active";
            } else if (!townHall.isAreaLoaded) {
               loaded = "command.inactive";
            } else {
               loaded = "command.frozen";
            }

            VillageType villageType;
            if (loneBuildings) {
               villageType = Culture.getCultureByName(list.cultures.get(i)).getLoneBuildingType(list.types.get(i));
            } else {
               villageType = Culture.getCultureByName(list.cultures.get(i)).getVillageType(list.types.get(i));
            }

            MillCommonUtilities.VillageInfo vi = new MillCommonUtilities.VillageInfo();
            vi.distance = distance;
            if (villageType != null) {
               String villageTypeNameKey = villageType.getNameTranslationKey(profile);
               if (villageTypeNameKey != null) {
                  vi.textKey = "command.villagelisttranslated";
                  vi.values = new String[]{list.names.get(i), loaded, "" + distance, direction, villageType.name, villageTypeNameKey};
               } else {
                  vi.textKey = "command.villagelist";
                  vi.values = new String[]{list.names.get(i), loaded, "" + distance, direction, villageType.name};
               }
            }

            villageList.add(vi);
         }
      }

      if (!loneBuildings) {
         for (int ix = 0; ix < this.loneBuildingsList.names.size(); ix++) {
            VillageType village = Culture.getCultureByName(this.loneBuildingsList.cultures.get(ix)).getLoneBuildingType(this.loneBuildingsList.types.get(ix));
            if ((village.keyLonebuilding || village.keyLoneBuildingGenerateTag != null)
               && (!village.generatedForPlayer || player.getScoreboardName().equalsIgnoreCase(this.loneBuildingsList.generatedFor.get(ix)))) {
               Point p = this.loneBuildingsList.pos.get(ix);
               int distance = Mth.floor(p.horizontalDistanceTo(player));
               if (distance <= 2000) {
                  String directionx = new Point(player).directionTo(p, true);
                  MillCommonUtilities.VillageInfo vi = new MillCommonUtilities.VillageInfo();
                  vi.distance = distance;
                  if (village != null) {
                     vi.textKey = "command.villagelistkeylonebuilding";
                     vi.values = new String[]{village.name, "" + distance, directionx};
                  }

                  villageList.add(vi);
               }
            }
         }
      }

      if (villageList.size() == 0) {
         ServerSender.sendTranslatedSentence(player, '7', "command.noknowvillage");
      } else {
         Collections.sort(villageList);

         for (MillCommonUtilities.VillageInfo vi : villageList) {
            ServerSender.sendTranslatedSentence(player, '7', vi.textKey, vi.values);
         }

         String directionx = "other.tothe" + MillCommonUtilities.getCardinalDirectionStringFromAngle((int)player.getYRot());
         ServerSender.sendTranslatedSentence(player, '2', "command.facingdirection", directionx);
      }
   }

   public void forcePreload() {
      if (this.world instanceof ServerLevel serverLevel && MillConfigValues.forcePreload > 0) {
         this.lastForcePreloadUpdate++;
         if (this.lastForcePreloadUpdate >= 50) {
            this.lastForcePreloadUpdate = 0;
            int centreX;
            int centreZ;
            if (serverLevel.players().size() > 0) {
               Player player = serverLevel.players().get(0);
               centreX = (int)(player.getX() / 16.0);
               centreZ = (int)(player.getZ() / 16.0);
            } else {
               centreX = getSpawnPos(serverLevel).getX() / 16;
               centreZ = getSpawnPos(serverLevel).getZ() / 16;
            }

            int nbGenerated = 0;

            for (int radius = 1; radius < MillConfigValues.forcePreload; radius++) {
               for (int i = -MillConfigValues.forcePreload; i < MillConfigValues.forcePreload && nbGenerated < 100; i++) {
                  for (int j = -MillConfigValues.forcePreload; j < MillConfigValues.forcePreload && nbGenerated < 100; j++) {
                     if (i * i + j * j < radius * radius && !serverLevel.hasChunk(i + centreX, j + centreZ)) {
                        // force generation of the chunk (was getChunkProvider().provideChunk in 1.12)
                        serverLevel.getChunk(i + centreX, j + centreZ);
                        MillLog.minor(this, "Forcing population of chunk " + (i + centreX) + "/" + (j + centreZ));
                        nbGenerated++;
                     }
                  }
               }
            }

            // 26.2: the 1.12 explicit ChunkProviderServer.saveChunks call is gone — the server
            // autosaves loaded chunks, and the force-generated chunks above are now tracked as loaded
            // (serverLevel.getChunk), so they persist on the normal autosave. A manual flush would be
            // serverLevel.save(null, false, false), but it is not needed here.
         }
      }
   }

   public Collection<MillVillager> getAllKnownVillagers() {
      return this.villagers.values();
   }

   public Building getBuilding(Point p) {
      if (this.buildings.containsKey(p)) {
         Building building = this.buildings.get(p);
         // 1.12.2 logged (MillLog.error / printException) and returned the broken building. A null
         // record or a building with a null location is corruption — fatalize instead of returning it.
         MillCrash.check(building != null, "World", "Building record for " + p + " is null.");
         MillCrash.check(building.location != null, "World", "Building location for " + p + " is null.");
         return building;
      } else {
         if (MillConfigValues.LogWorldInfo >= 2) {
            MillLog.minor(this, "Could not find a building at location " + p + " amoung " + this.buildings.size() + " records.");
         }

         return null;
      }
   }

   public Building getClosestVillage(Point p) {
      int bestDistance = Integer.MAX_VALUE;
      Building bestVillage = null;

      for (Point villageCoord : this.villagesList.pos) {
         int dist = (int)p.distanceToSquared(villageCoord);
         if (bestVillage == null || dist < bestDistance) {
            Building village = this.getBuilding(villageCoord);
            if (village != null) {
               bestVillage = village;
               bestDistance = dist;
            }
         }
      }

      return bestVillage;
   }

   public List<Point> getCombinedVillagesLoneBuildings() {
      List<Point> thPosLists = new ArrayList<>(this.villagesList.pos);
      thPosLists.addAll(this.loneBuildingsList.pos);
      return thPosLists;
   }

   public UserProfile getProfile(Player player) {
      if (this.profiles.containsKey(player.getUUID())) {
         return this.profiles.get(player.getUUID());
      } else if (MillConfigValues.autoConvertProfiles && !Mill.proxy.isTrueServer() && this.profiles.size() > 0) {
         UserProfile profile = this.profiles.get(this.profiles.keySet().stream().findFirst().get());
         profile.migrateToPlayer(player);
         return profile;
      } else {
         UserProfile profile = new UserProfile(this, player);
         this.profiles.put(profile.uuid, profile);
         return profile;
      }
   }

   public UserProfile getProfile(UUID uuid) {
      if (this.profiles.containsKey(uuid)) {
         return this.profiles.get(uuid);
      } else {
         // 1.12 read the player name from PlayerProfileCache.getProfileByUUID(uuid). On 26.2 that cache
         // is the Services name resolver (UserNameToIdResolver.get(UUID)); fall back to an online player
         // of that UUID, then the UUID string, so a name is always available for the profile.
         String name = null;
         net.minecraft.server.MinecraftServer server = this.world.getServer();
         if (server != null) {
            name = server.services().nameToIdCache().get(uuid).map(net.minecraft.server.players.NameAndId::name).orElse(null);
         }
         if (name == null) {
            Player online = this.world.getPlayerByUUID(uuid);
            name = online != null ? online.getScoreboardName() : uuid.toString();
         }
         UserProfile profile = new UserProfile(this, uuid, name);
         this.profiles.put(profile.uuid, profile);
         return profile;
      }
   }

   public MillVillager getVillagerById(long id) {
      return this.villagers.get(id);
   }

   public VillagerRecord getVillagerRecordById(long villagerId) {
      return this.villagerRecords.get(villagerId);
   }

   public boolean isGlobalTagSet(String tag) {
      return this.globalTags.contains(tag);
   }

   private void loadBuildings() {
      long startTime = System.currentTimeMillis();
      File buildingsDir = new File(this.millenaireDir, "buildings");
      if (!buildingsDir.exists()) {
         buildingsDir.mkdir();
      }

      for (File file : buildingsDir.listFiles(new MillFiles.ExtFileFilter("gz"))) {
         try (FileInputStream fileinputstream = new FileInputStream(file)) {
            CompoundTag buildingsFileTag = NbtIo.readCompressed(fileinputstream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            ListTag buildingTagList = buildingsFileTag.getListOrEmpty("buildings");

            for (int buildingIndex = 0; buildingIndex < buildingTagList.size(); buildingIndex++) {
               CompoundTag buildingTag = buildingTagList.getCompoundOrEmpty(buildingIndex);
               new Building(this, buildingTag);
            }
         } catch (Exception loadException) {
            // 1.12.2 logged this and skipped the file, silently dropping a village's buildings.
            // A building file that fails to load is corruption / data loss — fatalize instead.
            throw MillCrash.fail("World", "Error loading building file " + file.getAbsolutePath() + ": " + loadException);
         }
      }

      if (MillConfigValues.LogHybernation >= 1) {
         for (Building b : this.buildings.values()) {
            MillLog.major(null, b + " - " + b.culture);
         }

         MillLog.major(this, "Loaded " + this.buildings.size() + " in " + (System.currentTimeMillis() - startTime) + " ms.");
      }
   }

   public void loadData() {
      if (!this.world.isClientSide()) {
         this.loadWorldConfig();
         this.loadVillagesAndLoneBuildingsLists();
         this.loadGlobalTags();
         this.loadBuildings();
         this.loadVillagerRecords();
         this.loadProfiles();
      }
   }

   private void loadGlobalTags() {
      File tagsFile = new File(this.millenaireDir, "tags.txt");
      this.globalTags.clear();
      if (tagsFile.exists()) {
         try {
            BufferedReader reader = MillFiles.getReader(tagsFile);

            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
               if (line.trim().length() > 0) {
                  this.globalTags.add(line.trim());
               }
            }

            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(null, "Loaded " + this.globalTags.size() + " tags.");
            }
         } catch (Exception tagsLoadException) {
            // 1.12.2 swallowed this; an existing tags.txt that fails to read is corruption — fatalize.
            throw MillCrash.fail("World", "Error loading global tags from " + tagsFile.getAbsolutePath() + ": " + tagsLoadException);
         }
      }
   }

   private void loadProfiles() {
      File profilesDir = new File(this.millenaireDir, "profiles");
      if (!profilesDir.exists()) {
         profilesDir.mkdirs();
      }

      for (File profileDir : profilesDir.listFiles()) {
         if (profileDir.isDirectory() && !profileDir.isHidden()) {
            UserProfile profile = UserProfile.readProfile(this, profileDir);
            if (profile != null) {
               this.profiles.put(profile.uuid, profile);
            }
         }
      }
   }

   private void loadVillagerRecords() {
      if (!this.world.isClientSide()) {
         File file1 = new File(this.millenaireDir, "villagerRecords.gz");
         if (file1.exists()) {
            try (FileInputStream fileinputstream = new FileInputStream(file1)) {
               CompoundTag nbttagcompound = NbtIo.readCompressed(fileinputstream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
               ListTag nbttaglist = nbttagcompound.getListOrEmpty("villagersrecords");
               if (MillConfigValues.LogHybernation >= 1) {
                  MillLog.major(this, "Loading " + nbttaglist.size() + " villagers from main list. Count at start: " + this.villagerRecords.size());
               }

               for (int i = 0; i < nbttaglist.size(); i++) {
                  CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(i);
                  VillagerRecord vr = VillagerRecord.read(this, nbttagcompound1, "vr");
                  if (vr == null) {
                     MillLog.error(this, "Couldn't load VR record.");
                  } else {
                     this.registerVillagerRecord(vr, false);
                     if (MillConfigValues.LogHybernation >= 2) {
                        MillLog.minor(this, "Loaded VR: " + vr);
                     }
                  }
               }

               this.saveVillagerRecords();
               if (MillConfigValues.LogHybernation >= 1) {
                  MillLog.major(this, "Loading from main list over. Count at end: " + this.villagerRecords.size());
               }
            } catch (Exception recordsLoadException) {
               // 1.12.2 swallowed this, silently losing every villager record. Fatalize.
               throw MillCrash.fail("World", "Error loading villager records file " + file1.getAbsolutePath() + ": " + recordsLoadException);
            }
         }
      }
   }

   private void loadVillagesAndLoneBuildingsLists() {
      File villageLog = new File(this.millenaireDir, "villages.txt");
      if (villageLog.exists()) {
         try {
            BufferedReader reader = MillFiles.getReader(villageLog);

            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
               if (line.trim().length() > 0) {
                  String[] p = line.split(";")[1].split("/");
                  String type = "";
                  if (line.split(";").length > 2) {
                     type = line.split(";")[2];
                  }

                  String culture = "";
                  if (line.split(";").length > 3) {
                     culture = line.split(";")[3];
                  }

                  Culture c = Culture.getCultureByName(culture);
                  if (c != null) {
                     String generatedFor = null;
                     if (line.split(";").length > 4) {
                        generatedFor = line.split(";")[4];
                     }

                     this.registerVillageLocation(
                        this.world,
                        new Point(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])),
                        line.split(";")[0],
                        c.getVillageType(type),
                        c,
                        false,
                        generatedFor
                     );
                  } else {
                     MillLog.error(this, "Tried loading a village of culture " + culture + " that cannot be found.");
                  }
               }
            }

            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(null, "Loaded " + this.villagesList.names.size() + " village positions.");
            }
         } catch (Exception villagesLoadException) {
            // 1.12.2 swallowed this; a malformed villages.txt silently loses all village locations. Fatalize.
            throw MillCrash.fail("World", "Error loading villages list from " + villageLog.getAbsolutePath() + ": " + villagesLoadException);
         }
      }

      villageLog = new File(this.millenaireDir, "lonebuildings.txt");
      if (villageLog.exists()) {
         try {
            BufferedReader reader = MillFiles.getReader(villageLog);

            for (String linex = reader.readLine(); linex != null; linex = reader.readLine()) {
               if (linex.trim().length() > 0) {
                  String[] px = linex.split(";")[1].split("/");
                  String typex = "";
                  if (linex.split(";").length > 2) {
                     typex = linex.split(";")[2];
                  }

                  String culturex = "";
                  if (linex.split(";").length > 3) {
                     culturex = linex.split(";")[3];
                  }

                  Culture c = Culture.getCultureByName(culturex);
                  String generatedFor = null;
                  if (linex.split(";").length > 4) {
                     generatedFor = linex.split(";")[4];
                  }

                  this.registerLoneBuildingsLocation(
                     this.world,
                     new Point(Integer.parseInt(px[0]), Integer.parseInt(px[1]), Integer.parseInt(px[2])),
                     linex.split(";")[0],
                     c.getLoneBuildingType(typex),
                     c,
                     false,
                     generatedFor
                  );
               }
            }

            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(null, "Loaded " + this.loneBuildingsList.names.size() + " lone buildings positions.");
            }
         } catch (Exception loneBuildingsLoadException) {
            // 1.12.2 swallowed this; a malformed lonebuildings.txt silently loses all lone-building locations. Fatalize.
            throw MillCrash.fail("World", "Error loading lone buildings list from " + villageLog.getAbsolutePath() + ": " + loneBuildingsLoadException);
         }
      }
   }

   private void loadWorldConfig() {
      this.generateVillages = MillConfigValues.generateVillages;
      File configFile = new File(this.millenaireDir, "config.txt");
      if (configFile != null && configFile.exists()) {
         try {
            BufferedReader reader = MillFiles.getReader(configFile);

            String line;
            while ((line = reader.readLine()) != null) {
               if (line.trim().length() > 0 && !line.startsWith("//")) {
                  String[] temp = line.split("=");
                  if (temp.length == 2) {
                     String key = temp[0];
                     String value = temp[1];
                     if (key.equalsIgnoreCase("generate_villages")) {
                        this.generateVillages = Boolean.parseBoolean(value);
                        this.generateVillagesSet = true;
                     }
                  }
               }
            }

            reader.close();
         } catch (IOException configLoadException) {
            // 1.12.2 swallowed this; a config.txt read failure silently leaves generateVillages wrong. Fatalize.
            throw MillCrash.fail("World", "Error loading world config from " + configFile.getAbsolutePath() + ": " + configLoadException);
         }
      }

      if (MillConfigValues.LogWorldGeneration >= 1) {
         MillLog.major(null, "Config loaded. generateVillages: " + this.generateVillages);
      }
   }

   public int nbCultureInGeneratedVillages() {
      List<String> cultures = new ArrayList<>();

      for (int i = 0; i < this.villagesList.names.size(); i++) {
         if (!cultures.contains(this.villagesList.cultures.get(i))) {
            cultures.add(this.villagesList.cultures.get(i));
         }
      }

      return cultures.size();
   }

   private void rebuildVillagerList() {
      int oldVillagerNumber = this.villagers.size();
      this.villagers.clear();
      Map<Building, Set<MillVillager>> villagersBuilding = new HashMap<>();

      for (Building building : this.buildings.values()) {
         villagersBuilding.put(building, new HashSet<>());
      }

      List<MillVillager> villagersToDespawn = new ArrayList<>();

      for (Entity entity : this.getAllEntities()) {
         if (entity instanceof MillVillager) {
            MillVillager villager = (MillVillager)entity;
            if (villager.getVillagerId() == 7165774886634408160L && !this.world.isClientSide()) {
               MillLog.major(villager, "Villager with forbidden ID again.");
               villagersToDespawn.add(villager);
            } else if (!this.villagerRecords.containsKey(villager.getVillagerId()) && !this.world.isClientSide()) {
               MillLog.temp(villager, "Villager without registered record.");
               villagersToDespawn.add(villager);
            } else {
               if (this.villagers.containsKey(villager.getVillagerId()) && MillConfigValues.LogVillagerSpawn >= 1) {
                  MillLog.major(this, "Duplicate villager: " + villager);
                  villagersToDespawn.add(villager);
               }

               this.villagers.put(villager.getVillagerId(), villager);
               if (villager.getTownHall() != null && villagersBuilding.get(villager.getTownHall()) != null) {
                  villagersBuilding.get(villager.getTownHall()).add(villager);
               }

               if (villager.getHouse() != null && villager.getHouse() != villager.getTownHall() && villagersBuilding.get(villager.getHouse()) != null) {
                  villagersBuilding.get(villager.getHouse()).add(villager);
               }
            }
         }
      }

      for (Building building : villagersBuilding.keySet()) {
         building.setNewVillagerList(villagersBuilding.get(building));
      }

      // [MILLDEBUG] Diagnose the respawn loop: if villagers are getting despawned here (e.g. no matching
      // mw record) or none end up tracked despite records existing, the building keeps respawning them.
      if (MillLog.debugOn() && !this.world.isClientSide()
         && (!villagersToDespawn.isEmpty() || (this.villagerRecords.size() > 0 && this.villagers.isEmpty()))) {
         int total = 0;
         int millInWorld = 0;
         for (Entity e : this.getAllEntities()) {
            total++;
            if (e instanceof MillVillager) {
               millInWorld++;
            }
         }
         MillLog.milldebug("VillagerList",
            "rebuilt: totalEntities=" + total + " millVillagersInWorld=" + millInWorld + " tracked=" + this.villagers.size()
               + " despawnedThisPass=" + villagersToDespawn.size() + " mwRecords=" + this.villagerRecords.size());
      }

      for (MillVillager villager : villagersToDespawn) {
         villager.despawnVillagerSilent();
      }

      if (MillConfigValues.LogVillagerSpawn >= 1 && oldVillagerNumber != this.villagers.size()) {
         MillLog.major(
            this,
            "Villager list rebuilt. Now contains: "
               + this.villagers.size()
               + " villagers instead of "
               + oldVillagerNumber
               + " from a (re-scanned) entity list"
         );
      }
   }

   @Deprecated
   public void receiveVillageListPacket(FriendlyByteBuf data) {
      if (MillConfigValues.LogNetwork >= 2) {
         MillLog.minor(this, "Received village list packet.");
      }

      this.villagesList = new MillCommonUtilities.VillageList();
      this.loneBuildingsList = new MillCommonUtilities.VillageList();
      int nb = data.readInt();

      for (int i = 0; i < nb; i++) {
         this.villagesList.pos.add(StreamReadWrite.readNullablePoint(data));
         this.villagesList.names.add(StreamReadWrite.readNullableString(data));
         this.villagesList.cultures.add(StreamReadWrite.readNullableString(data));
         this.villagesList.types.add(StreamReadWrite.readNullableString(data));
      }

      nb = data.readInt();

      for (int i = 0; i < nb; i++) {
         this.loneBuildingsList.pos.add(StreamReadWrite.readNullablePoint(data));
         this.loneBuildingsList.names.add(StreamReadWrite.readNullableString(data));
         this.loneBuildingsList.cultures.add(StreamReadWrite.readNullableString(data));
         this.loneBuildingsList.types.add(StreamReadWrite.readNullableString(data));
      }
   }

   public void registerLoneBuildingsLocation(Level world, Point pos, String name, VillageType type, Culture culture, boolean newVillage, String playerName) {
      boolean found = false;

      for (Point p : this.loneBuildingsList.pos) {
         if (p.equals(pos)) {
            found = true;
         }
      }

      if (!found) {
         if (!type.generatedForPlayer) {
            playerName = null;
         }

         this.loneBuildingsList.addVillage(pos, name, type.key, culture.key, playerName);
         if (MillConfigValues.LogWorldGeneration >= 1) {
            MillLog.major(null, "Registering lone buildings: " + name + " / " + type + " / " + culture + " / " + pos);
         }

         for (Object o : world.players()) {
            Player player = (Player)o;
            if (newVillage && (type.keyLonebuilding || type.keyLoneBuildingGenerateTag != null)) {
               int distance = Mth.floor(pos.horizontalDistanceTo(player));
               if (distance <= 2000) {
                  String direction = new Point(player).directionTo(pos, true);
                  ServerSender.sendTranslatedSentence(player, 'e', "command.newlonebuildingfound", type.name, "" + distance, direction);
               }
            }
         }

         this.saveLoneBuildingsList();
      }
   }

   public void registerVillageLocation(Level world, Point pos, String name, VillageType type, Culture culture, boolean newVillage, String playerName) {
      boolean found = false;
      if (type == null) {
         MillLog.error(null, "Attempting to register village with null type: " + pos + "/" + culture + "/" + name + "/" + newVillage);
      } else if (culture == null) {
         MillLog.error(null, "Attempting to register village with null culture: " + pos + "/" + type + "/" + name + "/" + newVillage);
      } else {
         for (Point p : this.villagesList.pos) {
            if (p.equals(pos)) {
               found = true;
            }
         }

         if (!found) {
            if (!type.generatedForPlayer) {
               playerName = null;
            }

            this.villagesList.addVillage(pos, name, type.key, culture.key, playerName);
            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(null, "Registering village: " + name + " / " + type + " / " + culture + " / " + pos);
            }

            if (newVillage) {
               for (Object o : world.players()) {
                  Player player = (Player)o;
                  UserProfile profile = this.getProfile(player);
                  int distance = Mth.floor(pos.horizontalDistanceTo(player));
                  if (distance <= 2000 && !world.isClientSide()) {
                     String direction = new Point(player).directionTo(pos, true);
                     String villageTypeNameKey = type.getNameTranslationKey(profile);
                     if (villageTypeNameKey != null) {
                        ServerSender.sendTranslatedSentence(
                           player,
                           'e',
                           "command.newvillagefoundtranslated",
                           name,
                           type.name,
                           culture.getAdjectiveTranslatedKey(),
                           "" + distance,
                           direction,
                           villageTypeNameKey
                        );
                     } else {
                        ServerSender.sendTranslatedSentence(
                           player, 'e', "command.newvillagefound", name, type.name, culture.getAdjectiveTranslatedKey(), "" + distance, direction
                        );
                     }
                  }
               }
            }

            this.saveVillageList();
         }
      }
   }

   public void registerVillager(long id, MillVillager villager) {
      if (MillConfigValues.LogVillagerSpawn >= 1) {
         MillLog.major(this, "Registering villager in global list: " + villager);
      }

      this.villagers.put(id, villager);
      if (villager.getHouse() != null) {
         villager.getHouse().rebuildVillagerList();
      }

      if (villager.getTownHall() != null && villager.getTownHall() != villager.getHouse()) {
         villager.getTownHall().rebuildVillagerList();
      }
   }

   public void registerVillagerRecord(VillagerRecord villagerRecord, boolean forceSave) {
      boolean registeredHouse = false;
      boolean registeredTH = false;
      this.villagerRecords.put(villagerRecord.getVillagerId(), villagerRecord);
      if (villagerRecord.getTownHall() != null) {
         villagerRecord.getTownHall().registerVillagerRecord(villagerRecord);
         registeredTH = true;
      }

      if (villagerRecord.getHouse() != null && villagerRecord.getHouse() != villagerRecord.getTownHall()) {
         villagerRecord.getHouse().registerVillagerRecord(villagerRecord);
         registeredHouse = true;
      }

      if (MillConfigValues.LogHybernation >= 2) {
         if (this.villagerRecords.containsKey(villagerRecord.getVillagerId())) {
            MillLog.minor(this, "Replacing villager record: " + villagerRecord + ". Registered TH: " + registeredTH + ", registeredHouse: " + registeredHouse);
         } else {
            MillLog.minor(this, "Adding villager record: " + villagerRecord + ". Registered TH: " + registeredTH + ", registeredHouse: " + registeredHouse);
         }
      }

      if (!this.world.isClientSide() &&forceSave) {
         this.saveVillagerRecords();
      }
   }

   public void removeBuilding(Point p) {
      this.buildings.remove(p);
   }

   public void removeVillageOrLoneBuilding(Point p) {
      this.loneBuildingsList.removeVillage(p);
      this.villagesList.removeVillage(p);
      this.saveLoneBuildingsList();
      this.saveVillageList();
   }

   public void removeVillagerRecord(long villagerId) {
      VillagerRecord villagerRecord = this.villagerRecords.get(villagerId);
      if (villagerRecord != null) {
         // An emergent MERGE/WAR absorb can DEMOTE/remove this record's town hall or house while the record still
         // points at it, so getTownHall()/getHouse() return null. Those are legitimate nulls (the building is gone):
         // skip the now-defunct building's own record-removal rather than NPE, and still drop the global record.
         if (villagerRecord.getTownHall() != null) {
            villagerRecord.getTownHall().removeVillagerRecord(villagerId);
         }
         if (villagerRecord.getHouse() != null) {
            villagerRecord.getHouse().removeVillagerRecord(villagerId);
         }
      }

      this.villagerRecords.remove(villagerId);
      this.saveVillagerRecords();
   }

   public void reportTime(Building townHall, long timeInNs, boolean villagerTime) {
      try {
         if (townHall != null && this.villagesList.rankByPos.containsKey(townHall.getPos())) {
            int villagePos = this.villagesList.rankByPos.get(townHall.getPos());
            int currentSamplePos = this.villagesList.buildingsTime.get(villagePos).size() - 1;
            if (currentSamplePos >= 0) {
               if (villagerTime) {
                  this.villagesList
                     .villagersTime
                     .get(villagePos)
                     .set(currentSamplePos, this.villagesList.villagersTime.get(villagePos).get(currentSamplePos) + timeInNs);
               } else {
                  this.villagesList
                     .buildingsTime
                     .get(villagePos)
                     .set(currentSamplePos, this.villagesList.buildingsTime.get(villagePos).get(currentSamplePos) + timeInNs);
               }
            }
         }
      } catch (Exception reportTimeException) {
         // 1.12.2 swallowed this. A throw here means the per-village timing lists (rankByPos vs
         // buildingsTime/villagersTime) are out of sync — a structural bug, not a transient. Fatalize.
         throw MillCrash.fail("World", "Exception while logging Millénaire time usage: " + reportTimeException);
      }
   }

   public void saveEverything() {
      if (!this.world.isClientSide()) {
         this.saveGlobalTags();
         this.saveLoneBuildingsList();
         this.saveVillageList();
         this.saveWorldConfig();
         this.saveVillagerRecords();

         for (Building b : this.buildings.values()) {
            if (b.isTownhall && b.isActive) {
               b.saveTownHall("world save");
            }
         }
      }
   }

   private void saveGlobalTags() {
      if (!this.world.isClientSide()) {
         File configFile = new File(this.millenaireDir, "tags.txt");

         try {
            BufferedWriter writer = MillFiles.getWriter(configFile);

            for (String tag : this.globalTags) {
               writer.write(tag + "\n");
            }

            writer.flush();
         } catch (IOException tagsSaveException) {
            // 1.12.2 swallowed this; a failed tags.txt write silently loses global tags. Fatalize.
            throw MillCrash.fail("World", "Error saving global tags to " + configFile.getAbsolutePath() + ": " + tagsSaveException);
         }
      }
   }

   public void saveLoneBuildingsList() {
      if (!this.world.isClientSide()) {
         File millenaireDir = new File(this.saveDir, "millenaire");
         if (!millenaireDir.exists()) {
            millenaireDir.mkdir();
         }

         File villageLog = new File(millenaireDir, "lonebuildings.txt");

         try {
            BufferedWriter writer = MillFiles.getWriter(villageLog);

            for (int i = 0; i < this.loneBuildingsList.pos.size(); i++) {
               Point p = this.loneBuildingsList.pos.get(i);
               String generatedFor = this.loneBuildingsList.generatedFor.get(i);
               if (generatedFor == null) {
                  generatedFor = "";
               }

               writer.write(
                  this.loneBuildingsList.names.get(i)
                     + ";"
                     + p.getiX()
                     + "/"
                     + p.getiY()
                     + "/"
                     + p.getiZ()
                     + ";"
                     + this.loneBuildingsList.types.get(i)
                     + ";"
                     + this.loneBuildingsList.cultures.get(i)
                     + ";"
                     + generatedFor
                     + System.getProperty("line.separator")
               );
            }

            writer.flush();
            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(null, "Saved " + this.loneBuildingsList.names.size() + " lone buildings.txt positions.");
            }
         } catch (IOException loneBuildingsSaveException) {
            // 1.12.2 swallowed this; a failed lonebuildings.txt write silently loses lone-building positions. Fatalize.
            throw MillCrash.fail("World", "Error saving lone buildings to " + villageLog.getAbsolutePath() + ": " + loneBuildingsSaveException);
         }
      }
   }

   public void saveVillageList() {
      if (!this.world.isClientSide()) {
         File millenaireDir = new File(this.saveDir, "millenaire");
         if (!millenaireDir.exists()) {
            millenaireDir.mkdir();
         }

         File villageLog = new File(millenaireDir, "villages.txt");

         try {
            BufferedWriter writer = MillFiles.getWriter(villageLog);

            for (int i = 0; i < this.villagesList.pos.size(); i++) {
               Point p = this.villagesList.pos.get(i);
               String generatedFor = this.villagesList.generatedFor.get(i);
               if (generatedFor == null) {
                  generatedFor = "";
               }

               writer.write(
                  this.villagesList.names.get(i)
                     + ";"
                     + p.getiX()
                     + "/"
                     + p.getiY()
                     + "/"
                     + p.getiZ()
                     + ";"
                     + this.villagesList.types.get(i)
                     + ";"
                     + this.villagesList.cultures.get(i)
                     + ";"
                     + generatedFor
                     + System.getProperty("line.separator")
               );
            }

            writer.flush();
            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(null, "Saved " + this.villagesList.names.size() + " village positions.");
            }
         } catch (IOException villagesSaveException) {
            // 1.12.2 swallowed this; a failed villages.txt write silently loses village positions. Fatalize.
            throw MillCrash.fail("World", "Error saving villages to " + villageLog.getAbsolutePath() + ": " + villagesSaveException);
         }
      }
   }

   private void saveVillagerRecords() {
      if (!this.world.isClientSide()) {
         CompoundTag mainTag = new CompoundTag();
         ListTag nbttaglist = new ListTag();

         for (VillagerRecord vr : this.villagerRecords.values()) {
            CompoundTag nbttagcompound1 = new CompoundTag();
            vr.write(nbttagcompound1, "vr");
            nbttaglist.add(nbttagcompound1);
            if (MillConfigValues.LogHybernation >= 3) {
               MillLog.debug(this, "Writing VR: " + vr);
            }
         }

         mainTag.put("villagersrecords", nbttaglist);
         if (!this.millenaireDir.exists()) {
            this.millenaireDir.mkdir();
         }

         File tempFile = new File(this.millenaireDir, "villagerRecords_temp.gz");

         try {
            try (FileOutputStream fileoutputstream = new FileOutputStream(tempFile)) {
               NbtIo.writeCompressed(mainTag, fileoutputstream);
               fileoutputstream.flush();
            }

            Path finalPath = new File(this.millenaireDir, "villagerRecords.gz").toPath();
            Files.move(tempFile.toPath(), finalPath, StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException recordsSaveException) {
            // 1.12.2 swallowed this; a failed villagerRecords.gz write silently loses every villager record. Fatalize.
            throw MillCrash.fail("World", "Error saving villager records to " + tempFile.getAbsolutePath() + ": " + recordsSaveException);
         }
      }
   }

   public void saveWorldConfig() {
      if (!this.world.isClientSide()) {
         File configFile = new File(this.millenaireDir, "config.txt");

         try {
            BufferedWriter writer = MillFiles.getWriter(configFile);
            if (this.generateVillagesSet) {
               writer.write("generate_villages=" + this.generateVillages + "\n");
            }

            writer.flush();
         } catch (IOException configSaveException) {
            // 1.12.2 swallowed this; a failed config.txt write silently loses the generate_villages setting. Fatalize.
            throw MillCrash.fail("World", "Error saving world config to " + configFile.getAbsolutePath() + ": " + configSaveException);
         }
      }
   }

   public void sendAllVillagerRecords(Player player) {
      FriendlyByteBuf data = ServerSender.getPacketBuffer();
      data.writeInt(12);
      StreamReadWrite.writeVillagerRecordMap(this.villagerRecords, data);
      ServerSender.sendPacketToPlayer(data, player);
   }

   @Deprecated
   public void sendVillageListPacket(Player player) {
      FriendlyByteBuf data = ServerSender.getPacketBuffer();
      data.writeInt(9);
      data.writeInt(this.villagesList.pos.size());

      for (int i = 0; i < this.villagesList.pos.size(); i++) {
         StreamReadWrite.writeNullablePoint(this.villagesList.pos.get(i), data);
         StreamReadWrite.writeNullableString(this.villagesList.names.get(i), data);
         StreamReadWrite.writeNullableString(this.villagesList.cultures.get(i), data);
         StreamReadWrite.writeNullableString(this.villagesList.types.get(i), data);
      }

      data.writeInt(this.loneBuildingsList.pos.size());

      for (int i = 0; i < this.loneBuildingsList.pos.size(); i++) {
         StreamReadWrite.writeNullablePoint(this.loneBuildingsList.pos.get(i), data);
         StreamReadWrite.writeNullableString(this.loneBuildingsList.names.get(i), data);
         StreamReadWrite.writeNullableString(this.loneBuildingsList.cultures.get(i), data);
         StreamReadWrite.writeNullableString(this.loneBuildingsList.types.get(i), data);
      }

      ServerSender.sendPacketToPlayer(data, player);
   }

   public void setGlobalTag(String tag) {
      if (!this.globalTags.contains(tag)) {
         this.globalTags.add(tag);
         this.saveGlobalTags();
         if (!this.world.isClientSide()) {
            for (UserProfile up : this.profiles.values()) {
               if (up.connected) {
                  up.sendProfilePacket(7);
               }
            }
         }
      }
   }

   public void testLocations(String label) {
      if (MillConfigValues.DEV) {
         for (Building b : this.allBuildings()) {
            try {
               if (b.location != null) {
                  String tags = "";

                  for (String s : b.getTags()) {
                     tags = tags + s + ";";
                  }

                  if (!buildingsTags.containsKey(b.getPos())) {
                     if (MillConfigValues.LogTags >= 2) {
                        MillLog.minor(null, "Detected new building: " + b + " with tags: " + tags);
                     }

                     buildingsTags.put(b.getPos(), tags);
                  } else if (!tags.equals(buildingsTags.get(b.getPos()))) {
                     MillLog.warning(null, "Testing locations due to: " + label);
                     MillLog.warning(null, "Tags changed for building: " + b + ". Was: " + buildingsTags.get(b.getPos()) + " now: " + tags);
                     buildingsTags.put(b.getPos(), tags);
                  }

                  if (!buildingsVariation.containsKey(b.getPos())) {
                     if (MillConfigValues.LogTags >= 2) {
                        MillLog.minor(null, "Detected new building: " + b + " with variation: " + b.location.getVariation());
                     }

                     buildingsVariation.put(b.getPos(), b.location.getVariation());
                  } else if (!buildingsVariation.get(b.getPos()).equals(b.location.getVariation())) {
                     MillLog.warning(null, "Testing locations due to: " + label);
                     MillLog.warning(
                        null, "Variation changed for building: " + b + ". Was: " + buildingsVariation.get(b.getPos()) + " now: " + b.location.getVariation()
                     );
                     buildingsVariation.put(b.getPos(), b.location.getVariation());
                  }

                  if (!buildingsLocation.containsKey(b.getPos())) {
                     if (MillConfigValues.LogTags >= 2) {
                        MillLog.minor(null, "Detected new building: " + b + " with location key: " + b.location.planKey);
                     }

                     buildingsLocation.put(b.getPos(), b.location.planKey);
                  } else if (!b.location.planKey.equals(buildingsLocation.get(b.getPos()))) {
                     MillLog.warning(null, "Testing locations due to: " + label);
                     MillLog.warning(
                        null, "Location key changed for building: " + b + ". Was: " + buildingsLocation.get(b.getPos()) + " now: " + b.location.planKey
                     );
                     buildingsLocation.put(b.getPos(), b.location.planKey);
                  }
               }
            } catch (Exception var7) {
               MillLog.printException("Error in dev monitoring of a building building: ", var7);
            }
         }
      }
   }

   private void testLog() {
      if (!MillConfigValues.logPerformed) {
         if (Mill.proxy.isTrueServer()) {
            MillCommonUtilities.logInstance(this.world);
         } else if (!(this.world instanceof ServerLevel)) {
            MillCommonUtilities.logInstance(this.world);
         }
      }
   }

   private void testTimeReset() {
      // lastWorldTime starts at the Long.MAX_VALUE "uninitialised" sentinel, so the FIRST tick after a load
      // always reads getGameTime() < MAX_VALUE and fired a spurious "World time has gone from
      // 9223372036854775807 ... will break Millénaire" warning. Only warn once we hold a REAL previous time
      // (time genuinely moved backward during play, e.g. /time set) — not on that uninitialised first tick.
      if (this.lastWorldTime != Long.MAX_VALUE && this.world.getGameTime() < this.lastWorldTime) {
         ServerSender.sendTranslatedSentenceInRange(
            this.world, new Point(0.0, 0.0, 0.0), Integer.MAX_VALUE, '4', "error.backwardtime", "" + this.lastWorldTime, "" + this.world.getGameTime()
         );
      }

      this.lastWorldTime = this.world.getGameTime();
   }

   @Override
   public String toString() {
      return "Level(" + (this.world instanceof ServerLevel sl ? sl.getSeed() : "client") + ")";
   }

   public void updateWorldClient(boolean inOverworld) {
      if (!Mill.checkedMillenaireDir
         && (!MillFiles.getMillenaireContentDir().exists() || !new File(MillFiles.getMillenaireContentDir(), "config.txt").exists())) {
         Mill.proxy.sendChatAdmin("The millenaire directory could not be found. It should be inside the minecraft \"mods\" directory, alongside the jar.");
         Mill.proxy.sendChatAdmin("Le dossier millenaire est introuvable. Il devrait être dans le dossier \"mods\" de Minecraft, à côté du jar.");
      }

      Mill.checkedMillenaireDir = true;
      this.rebuildVillagerList();
      if (inOverworld) {
         for (Building b : this.allBuildings()) {
            b.updateBuildingClient();
         }
      }

      this.testLog();
   }

   public void updateWorldServer() {
      this.testTimeReset();
      this.rebuildVillagerList();

      for (int i = 0; i < this.villagesList.pos.size(); i++) {
         this.villagesList.buildingsTime.get(i).add(0L);
         this.villagesList.villagersTime.get(i).add(0L);
         if (this.villagesList.buildingsTime.get(i).size() > 20) {
            this.villagesList.buildingsTime.get(i).remove(0);
            this.villagesList.villagersTime.get(i).remove(0);
         }
      }

      for (Building b : this.allBuildings()) {
         long startTime = System.nanoTime();
         b.updateBuildingServer();
         b.updateBackgroundVillage();
         if (b.getTownHall() != null) {
            this.reportTime(b.getTownHall(), System.nanoTime() - startTime, false);
         }
      }

      this.checkConnections();

      for (UserProfile profile : new ArrayList<>(this.profiles.values())) {
         if (!profile.connected && profile.getPlayer() != null) {
            profile.connectUser();
         }

         if (profile.connected) {
            profile.updateProfile();
         }
      }

      for (Object o : this.world.players()) {
         Player player = (Player)o;
         SpecialQuestActions.onTick(this, player);
      }

      if (MillConfigValues.DEV) {
         DevModUtilities.runAutoMove(this.world);
      }

      this.forcePreload();
      this.testLog();
   }
}
