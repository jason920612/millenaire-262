package org.millenaire.common.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.millenaire.common.buildingplan.BuildingPlan;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.buildingplan.TreeClearer;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.ItemParchment;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.pathing.atomicstryker.RegionMapper;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.BuildingLocation;
import org.millenaire.common.village.BuildingProject;
import org.millenaire.common.village.VillageMapInfo;

// 26.2 worldgen: the 1.12 Forge IWorldGenerator is gone. The per-chunk invocation of generate(Random,
// chunkX, chunkZ, world) below is now driven by Fabric's ServerChunkEvents.CHUNK_GENERATE, registered in
// MillenaireMod.onInitialize (the closest equivalent to the old per-chunk hook). The village-placement
// logic (generateVillage / generateVillageAtPoint / generateBedrockLoneBuilding) is preserved unchanged.
// (Mill villages are bespoke block-by-block placement, not vanilla Feature/Structure/jigsaw, so no
// Structure registration is involved; biome eligibility is checked inside the placement logic.)
public class WorldGenVillage {
   private static final int HAMLET_ATTEMPT_ANGLE_STEPS = 36;
   private static final int CHUNK_DISTANCE_LOAD_TEST = 8;
   private static final int HAMLET_MAX_DISTANCE = 350;
   private static final int HAMLET_MIN_DISTANCE = 250;
   private static final double MINIMUM_USABLE_BLOCK_PERC = 0.7;
   private static HashSet<Integer> chunkCoordsTried = new HashSet<>();

   /** Spawn position of the world (replaces removed {@code getSpawnPos(world)}). */
   private static BlockPos getSpawnPos(Level world) {
      return world.getRespawnData().pos();
   }

   public static boolean generateBedrockLoneBuilding(
      Point p, Level world, VillageType village, Random random, int minDistance, int maxDistance, Player player
   ) throws MillLog.MillenaireException {
      if (world.isClientSide()) {
         return false;
      } else if (isWithinSpawnRadiusProtection(world, village, p)) {
         return false;
      } else if (village.centreBuilding == null) {
         // 1.12.2 logged and returned false. A bedrock lone building type without a centre is a malformed
         // VillageType definition, not a runtime condition — fatalize.
         throw MillCrash.fail("Buildings", "Bedrock lone building '" + village + "' has no centre building defined.");
      } else {
         if (MillConfigValues.LogWorldGeneration >= 1) {
            MillLog.major(null, "Generating bedrockbuilding: " + village);
         }

         BuildingPlan plan = village.centreBuilding.getRandomStartingPlan();
         BuildingLocation location = null;

         for (int i = 0; i < 100 && location == null; i++) {
            int x = minDistance + MillRandom.randomInt(maxDistance - minDistance);
            int z = minDistance + MillRandom.randomInt(maxDistance - minDistance);
            if (MillRandom.chanceOn(2)) {
               x = -x;
            }

            if (MillRandom.chanceOn(2)) {
               z = -z;
            }

            BuildingPlan.LocationReturn lr = plan.testSpotBedrock(world, p.getiX() + x, p.getiZ() + z);
            location = lr.location;
         }

         if (location == null) {
            MillLog.major(null, "No spot found for: " + village);
            int xx = minDistance + MillRandom.randomInt(maxDistance - minDistance);
            int zx = minDistance + MillRandom.randomInt(maxDistance - minDistance);
            if (MillRandom.chanceOn(2)) {
               xx = -xx;
            }

            if (MillRandom.chanceOn(2)) {
               zx = -zx;
            }

            location = new BuildingLocation(plan, new Point(p.getiX() + xx, 2.0, p.getiZ() + zx), 0);
            location.bedrocklevel = true;
         }

         if (isWithinSpawnRadiusProtection(world, village, location.pos)) {
            return false;
         } else {
            List<BuildingPlan.LocationBuildingPair> lbps = village.centreBuilding
               .buildLocation(Mill.getMillWorld(world), village, location, true, true, null, false, false, null);
            Building townHallEntity = lbps.get(0).building;
            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(null, "Registering building: " + townHallEntity);
            }

            townHallEntity.villageType = village;
            townHallEntity.findName(null);
            townHallEntity.initialiseBuildingProjects();
            townHallEntity.registerBuildingLocation(location);

            for (BuildingPlan.LocationBuildingPair lbp : lbps) {
               if (lbp != lbps.get(0)) {
                  townHallEntity.registerBuildingEntity(lbp.building);
                  townHallEntity.registerBuildingLocation(lbp.location);
               }
            }

            townHallEntity.initialiseVillage();
            String playerName = null;
            if (player != null) {
               playerName = player.getScoreboardName();
            }

            Mill.getMillWorld(world)
               .registerLoneBuildingsLocation(
                  world,
                  townHallEntity.getPos(),
                  townHallEntity.getVillageQualifiedName(),
                  townHallEntity.villageType,
                  townHallEntity.culture,
                  true,
                  playerName
               );
            MillLog.major(null, "Finished bedrock building " + village + " at " + townHallEntity.getPos());
            return true;
         }
      }
   }

   private static boolean isWithinSpawnRadiusProtection(Level world, VillageType villageType, Point villagePos) {
      if (MillConfigValues.spawnProtectionRadius == 0) {
         return false;
      } else {
         int villageRadius = MillConfigValues.VillageRadius;
         if (villageType != null) {
            villageRadius = villageType.radius;
         }

         if (villagePos.horizontalDistanceTo(getSpawnPos(world)) < MillConfigValues.spawnProtectionRadius + villageRadius) {
            if (MillConfigValues.LogWorldGeneration >= 3) {
               MillLog.debug(
                  null,
                  "Blocking spawn at "
                     + villagePos
                     + ". Distance to spawn: "
                     + villagePos.horizontalDistanceTo(getSpawnPos(world))
                     + ", min acceptable : "
                     + (MillConfigValues.spawnProtectionRadius + villageRadius)
               );
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private int computeChunkCoordsHash(int x, int z) {
      return (x >> 4) + (z >> 4 << 16);
   }

   // 26.2: this was the Forge IWorldGenerator per-chunk callback; it is now invoked once per
   // newly-generated overworld chunk from Fabric's ServerChunkEvents.CHUNK_GENERATE (registered in
   // MillenaireMod.onInitialize). Body unchanged from 1.12.
   public void generate(Random random, int chunkX, int chunkZ, Level world) {
      if (world.dimension() == Level.OVERWORLD) {
         StackTraceElement[] trace = Thread.currentThread().getStackTrace();

         for (int i = 2; i < trace.length; i++) {
            if (trace[i].getClassName().equals(this.getClass().getName())) {
               return;
            }
         }

         Point generationPoint = new Point(chunkX * 16, 0.0, chunkZ * 16);
         double distanceToSpawn = generationPoint.horizontalDistanceTo(getSpawnPos(world));
         float completionRatio = 0.0F;
         if (MillConfigValues.villageSpawnCompletionMaxPercentage > 0 && distanceToSpawn > MillConfigValues.villageSpawnCompletionMinDistance) {
            if (distanceToSpawn > MillConfigValues.villageSpawnCompletionMaxDistance) {
               completionRatio = MillConfigValues.villageSpawnCompletionMaxPercentage / 100;
            } else {
               completionRatio = (float)(
                  MillConfigValues.villageSpawnCompletionMaxPercentage
                     * ((distanceToSpawn - MillConfigValues.villageSpawnCompletionMinDistance) / MillConfigValues.villageSpawnCompletionMaxDistance)
                     / 100.0
               );
            }

            completionRatio = (float)(Math.random() * completionRatio);
         }

         if (MillLog.debugOn()) {
            MillLog.milldebug(
               "WorldGen",
               "generate() attempt at chunk " + chunkX + "/" + chunkZ + " point=" + generationPoint
                  + " biome=" + this.getBiomeNameAtPos(world, chunkX * 16, chunkZ * 16)
                  + " distToSpawn=" + (int) distanceToSpawn + " completionRatio=" + completionRatio
            );
         }

         try {
            this.generateVillageAtPoint(
               world, random, chunkX * 16, 0, chunkZ * 16, null, true, false, true, Integer.MAX_VALUE, null, null, null, completionRatio
            );
         } catch (Exception worldGenException) {
            // 1.12.2 logged and swallowed this, leaving a partially-built / missing village in the world.
            // A worldgen failure mid-placement is corruption — fatalize instead of degrading.
            MillLog.milldebug("WorldGen", "EXCEPTION during generate() at chunk " + chunkX + "/" + chunkZ + ": " + worldGenException);
            throw MillCrash.fail(
               "Buildings",
               "Exception generating village in " + world + " (dimension: " + world.dimension().identifier() + ") at chunk "
                  + chunkX + "/" + chunkZ + ": " + worldGenException
            );
         }
      }
   }

   private boolean generateCustomVillage(Point p, Level world, VillageType villageType, Player player, Random random) throws MillLog.MillenaireException {
      long startTime = System.nanoTime();
      MillWorldData mw = Mill.getMillWorld(world);
      BuildingLocation location = new BuildingLocation(villageType.customCentre, p, true);
      Building townHall = new Building(mw, villageType.culture, villageType, location, true, true, p);
      villageType.customCentre.registerResources(townHall, location);
      townHall.initialise(player, true);
      BuildingProject project = new BuildingProject(villageType.customCentre, location);
      if (!townHall.buildingProjects.containsKey(BuildingProject.EnumProjects.CUSTOMBUILDINGS)) {
         townHall.buildingProjects.put(BuildingProject.EnumProjects.CUSTOMBUILDINGS, new CopyOnWriteArrayList<>());
      }

      townHall.buildingProjects.get(BuildingProject.EnumProjects.CUSTOMBUILDINGS).add(project);
      townHall.initialiseVillage();
      mw.registerVillageLocation(
         world, townHall.getPos(), townHall.getVillageQualifiedName(), townHall.villageType, townHall.culture, true, player.getScoreboardName()
      );
      townHall.initialiseRelations(null);
      townHall.updateWorldInfo();
      townHall.storeItemStack(ItemParchment.createParchmentForVillage(townHall.getTownHall()));
      if (MillConfigValues.LogWorldGeneration >= 1) {
         MillLog.major(this, "New custom village generated at " + p + ", took: " + (System.nanoTime() - startTime));
      }

      return true;
   }

   private void generateHamlet(Level world, VillageType hamlet, Point centralVillage, String name, Random random, float completionRatio) {
      boolean generated = false;

      for (int minRadius = 250; !generated && minRadius < 350; minRadius += 50) {
         double angle = 0.06280000000000001 * MillRandom.randomInt(100);

         for (int attempts = 0; !generated && attempts < 36; attempts++) {
            angle += 0.17444444444444446;
            int radius = minRadius + MillRandom.randomInt(40);
            int dx = (int)(Math.cos(angle) * radius);
            int dz = (int)(Math.sin(angle) * radius);
            if (MillConfigValues.LogWorldGeneration >= 1) {
               MillLog.major(this, "Trying to generate a hamlet " + hamlet + " around: " + (centralVillage.getiX() + dx) + "/" + (centralVillage.getiZ() + dz));
            }

            generated = this.generateVillageAtPoint(
               world,
               random,
               centralVillage.getiX() + dx,
               0,
               centralVillage.getiZ() + dz,
               null,
               false,
               true,
               false,
               250,
               hamlet,
               name,
               centralVillage,
               completionRatio
            );
         }
      }

      if (!generated && MillConfigValues.LogWorldGeneration >= 1) {
         MillLog.major(this, "Could not generate hamlet " + hamlet);
      }
   }

   private boolean generateVillage(
      Point targetPos,
      Level world,
      VillageType villageType,
      Player player,
      Player closestPlayer,
      Random random,
      int minDistance,
      String name,
      Point parentVillage,
      float completionRatio,
      boolean testBiomeValidity,
      boolean alwaysSpawn
   ) throws MillLog.MillenaireException {
      if (MillLog.debugOn()) {
         MillLog.milldebug(
            "WorldGen",
            "generateVillage START type=" + villageType.key + " culture="
               + (villageType.culture != null ? villageType.culture.key : "null") + " at " + targetPos
               + " completionRatio=" + completionRatio + " testBiome=" + testBiomeValidity + " alwaysSpawn=" + alwaysSpawn
         );
      }

      if (testBiomeValidity) {
         boolean biomeValidity = this.isInValidBiomes(world, villageType, targetPos);
         if (!biomeValidity) {
            MillLog.milldebug("WorldGen", "generateVillage FAILED type=" + villageType.key + " reason=invalid_biome at " + targetPos);
            return false;
         }
      }

      VillageMapInfo winfo = new VillageMapInfo();
      List<BuildingLocation> plannedBuildings = new ArrayList<>();
      MillWorldData mw = Mill.getMillWorld(world);
      targetPos = new Point(targetPos.x, WorldUtilities.findTopSoilBlock(world, targetPos.getiX(), targetPos.getiZ()), targetPos.z);

      for (int x = targetPos.getChunkX() - villageType.radius / 16 - 1; x <= targetPos.getChunkX() + villageType.radius / 16; x++) {
         for (int z = targetPos.getChunkZ() - villageType.radius / 16 - 1; z <= targetPos.getChunkZ() + villageType.radius / 16; z++) {
            if (!world.hasChunk(x, z)) {
               // force-load/generate the chunk (was getChunkProvider().provideChunk in 1.12)
               world.getChunk(x, z);
            }
         }
      }

      winfo.update(world, plannedBuildings, targetPos, villageType.radius);
      if (!alwaysSpawn && !this.isUsableArea(winfo, targetPos, villageType.radius)) {
         MillLog.milldebug("WorldGen", "generateVillage FAILED type=" + villageType.key + " reason=area_not_usable(>30% blocked) at " + targetPos);
         return false;
      } else {
         long startTime = System.nanoTime();
         BuildingLocation location = villageType.centreBuilding
            .getRandomStartingPlan()
            .findBuildingLocation(winfo, null, targetPos, villageType.radius, random, 3);
         if (location == null) {
            MillLog.milldebug("WorldGen", "generateVillage FAILED type=" + villageType.key + " reason=no_place_for_central_building " + villageType.centreBuilding + " at " + targetPos);
            if (MillConfigValues.LogWorldGeneration >= 2) {
               MillLog.minor(this, "Could not find place for central building: " + villageType.centreBuilding);
            }

            if (player != null) {
               ServerSender.sendTranslatedSentence(player, '6', "ui.generatenotenoughspace");
            }

            return false;
         } else {
            Point thPos = location.pos;
            if (isWithinSpawnRadiusProtection(world, villageType, thPos)) {
               if (Mill.proxy.isTrueServer()) {
                  if (player != null) {
                     ServerSender.sendTranslatedSentence(player, '6', "ui.tooclosetospawn");
                  }

                  return false;
               }

               if (!alwaysSpawn) {
                  return false;
               }
            } else if (MillConfigValues.LogWorldGeneration >= 2) {
               MillLog.minor(
                  this,
                  "Distance to spawn of "
                     + location.pos.horizontalDistanceTo(getSpawnPos(world))
                     + " is sufficient. Pos: "
                     + thPos
                     + ", spawn: "
                     + getSpawnPos(world)
               );
            }

            if (!alwaysSpawn) {
               int minDistanceWithVillages;
               int minDistanceWithLoneBuildings;
               if (villageType.lonebuilding) {
                  if (villageType.isKeyLoneBuildingForGeneration(closestPlayer)) {
                     minDistanceWithVillages = Math.min(minDistance, MillConfigValues.minDistanceBetweenVillagesAndLoneBuildings) / 2;
                     minDistanceWithLoneBuildings = Math.min(minDistance, MillConfigValues.minDistanceBetweenLoneBuildings) / 2;
                  } else {
                     minDistanceWithVillages = Math.min(minDistance, MillConfigValues.minDistanceBetweenVillagesAndLoneBuildings);
                     minDistanceWithLoneBuildings = Math.min(minDistance, MillConfigValues.minDistanceBetweenLoneBuildings);
                  }
               } else {
                  minDistanceWithVillages = Math.min(minDistance, MillConfigValues.minDistanceBetweenVillages);
                  minDistanceWithLoneBuildings = Math.min(minDistance, MillConfigValues.minDistanceBetweenVillagesAndLoneBuildings);
               }

               for (Point thp : mw.villagesList.pos) {
                  if (thPos.distanceTo(thp) < minDistanceWithVillages) {
                     if (MillConfigValues.LogWorldGeneration >= 1) {
                        MillLog.major(this, "Found a nearby village with final position. TargetPos / ThPos distance: " + targetPos.directionTo(thPos));
                     }

                     return false;
                  }
               }

               for (Point thpx : mw.loneBuildingsList.pos) {
                  if (thPos.distanceTo(thpx) < minDistanceWithLoneBuildings) {
                     if (MillConfigValues.LogWorldGeneration >= 1) {
                        MillLog.major(this, "Found a nearby lone building final position. TargetPos / ThPos distance: " + targetPos.directionTo(thPos));
                     }

                     return false;
                  }
               }
            }

            if (MillConfigValues.LogWorldGeneration >= 2) {
               MillLog.minor(this, "Place found for TownHall (village type: " + villageType.key + "). Checking for the rest.");
            }

            plannedBuildings.add(location);
            winfo.update(world, plannedBuildings, thPos, villageType.radius);
            RegionMapper regionMapper = new RegionMapper();
            regionMapper.createConnectionsTable(winfo, thPos);
            boolean areaChanged = false;
            VillageWallGenerator wallGenerator = new VillageWallGenerator(world);
            if (villageType.innerWallType != null) {
               List<BuildingLocation> wallLocations = wallGenerator.computeWallBuildingLocations(
                  villageType, villageType.innerWallType, villageType.innerWallRadius, regionMapper, thPos, winfo
               );
               plannedBuildings.addAll(wallLocations);
               areaChanged = winfo.update(world, plannedBuildings, thPos, villageType.radius);
               if (areaChanged) {
                  regionMapper.createConnectionsTable(winfo, thPos);
               }
            }

            if (villageType.outerWallType != null) {
               List<BuildingLocation> wallLocations = wallGenerator.computeWallBuildingLocations(
                  villageType, villageType.outerWallType, 0, regionMapper, thPos, winfo
               );
               plannedBuildings.addAll(wallLocations);
               areaChanged = winfo.update(world, plannedBuildings, thPos, villageType.radius);
               if (areaChanged) {
                  regionMapper.createConnectionsTable(winfo, thPos);
               }
            }

            boolean couldBuildKeyBuildings = true;

            for (BuildingPlanSet planSet : villageType.startBuildings) {
               location = planSet.getRandomStartingPlan().findBuildingLocation(winfo, regionMapper, thPos, villageType.radius, random, -1);
               if (location != null) {
                  plannedBuildings.add(location);
                  areaChanged = winfo.update(world, plannedBuildings, thPos, villageType.radius);
                  if (areaChanged) {
                     regionMapper.createConnectionsTable(winfo, thPos);
                  }
               } else {
                  couldBuildKeyBuildings = false;
                  MillLog.milldebug("WorldGen", "generateVillage type=" + villageType.key + ": could not place key building planSet=" + planSet.key + " at " + thPos);
                  if (MillConfigValues.LogWorldGeneration >= 2) {
                     MillLog.minor(this, "Couldn't build " + planSet.key + ".");
                  }
               }
            }

            if (MillConfigValues.LogWorldGeneration >= 3) {
               MillLog.debug(this, "Time taken for finding if building possible: " + (System.nanoTime() - startTime));
            }

            if (!couldBuildKeyBuildings) {
               MillLog.milldebug("WorldGen", "generateVillage FAILED type=" + villageType.key + " reason=not_enough_space_for_key_buildings at " + thPos);
               if (player != null) {
                  ServerSender.sendTranslatedSentence(player, '6', "ui.generatenotenoughspacevillage");
               }

               return false;
            } else {
               if (MillLog.debugOn()) {
                  MillLog.milldebug("WorldGen", "generateVillage type=" + villageType.key + " at " + thPos + ": placing " + plannedBuildings.size() + " planned buildings, completionRatio=" + completionRatio);
                  for (BuildingLocation bl : plannedBuildings) {
                     MillLog.milldebug("WorldGen", "  planned building plan=" + bl.planKey + " at " + bl.minx + "/" + bl.minz + " to " + bl.maxx + "/" + bl.maxz + " level=" + bl.level);
                  }
               }

               if (MillConfigValues.LogWorldGeneration >= 1) {
                  MillLog.major(this, thPos + ": Generating village with completion of: " + completionRatio);
               }

               if (MillConfigValues.LogWorldGeneration >= 1) {
                  for (BuildingLocation bl : plannedBuildings) {
                     MillLog.major(this, "Building " + bl.planKey + ": " + bl.minx + "/" + bl.minz + " to " + bl.maxx + "/" + bl.maxz);
                  }
               }

               startTime = System.nanoTime();
               TreeClearer.cumulatedTimeTreeFinding = 0L;
               TreeClearer.cumulatedTimeLeaveDecay = 0L;
               List<BuildingPlan.LocationBuildingPair> lbps = villageType.centreBuilding
                  .buildLocation(mw, villageType, plannedBuildings.get(0), true, true, null, false, false, player);
               Building townHallEntity = lbps.get(0).building;
               if (MillConfigValues.LogWorldGeneration >= 1) {
                  MillLog.major(this, "Registering building: " + townHallEntity);
               }

               townHallEntity.villageType = villageType;
               townHallEntity.findName(name);
               townHallEntity.initialiseBuildingProjects();
               townHallEntity.registerBuildingLocation(plannedBuildings.get(0));

               for (BuildingPlan.LocationBuildingPair lbp : lbps) {
                  if (lbp != lbps.get(0)) {
                     townHallEntity.registerBuildingEntity(lbp.building);
                     townHallEntity.registerBuildingLocation(lbp.location);
                  }
               }

               for (int i = 1; i < plannedBuildings.size(); i++) {
                  BuildingLocation bl = plannedBuildings.get(i);
                  BuildingPlanSet planSetx = villageType.culture.getBuildingPlanSet(bl.planKey);
                  if (bl.level == -1) {
                     if ((!(completionRatio > 0.0F) || !(Math.random() <= completionRatio)) && !(completionRatio > 0.33)) {
                        BuildingProject project = new BuildingProject(planSetx);
                        project.location = bl;
                        if (planSetx.getFirstStartingPlan().isWallSegment) {
                           if (!townHallEntity.buildingProjects.containsKey(BuildingProject.EnumProjects.WALLBUILDING)) {
                              townHallEntity.buildingProjects.put(BuildingProject.EnumProjects.WALLBUILDING, new CopyOnWriteArrayList<>());
                           }

                           townHallEntity.buildingProjects.get(BuildingProject.EnumProjects.WALLBUILDING).add(project);
                        } else {
                           townHallEntity.buildingProjects.get(BuildingProject.EnumProjects.EXTRA).add(project);
                        }
                     } else {
                        bl.level = 0;
                     }
                  }

                  if (bl.level >= 0) {
                     lbps = planSetx.buildLocation(mw, villageType, bl, true, false, townHallEntity, false, false, player);
                     if (MillConfigValues.LogWorldGeneration >= 1) {
                        MillLog.major(this, "Registering building: " + bl.planKey);
                     }

                     for (BuildingPlan.LocationBuildingPair lbpx : lbps) {
                        townHallEntity.registerBuildingEntity(lbpx.building);
                        townHallEntity.registerBuildingLocation(lbpx.location);
                     }
                  }
               }

               townHallEntity.initialiseVillage();
               if (completionRatio > 0.0F && !villageType.playerControlled) {
                  int nbRushedBuildingsTarget = 0;

                  for (BuildingProject project : townHallEntity.getFlatProjectList()) {
                     if (project.location != null) {
                        nbRushedBuildingsTarget = project.planSet.plans.get(project.location.getVariation()).length - project.location.level;
                     } else {
                        nbRushedBuildingsTarget = project.planSet.getFirstStartingPlan().plan.length;
                     }
                  }

                  nbRushedBuildingsTarget = (int)(townHallEntity.getNbProjects() * completionRatio);
                  int nbBuildingRushed = townHallEntity.rushCurrentConstructions(true);

                  while (nbBuildingRushed < nbRushedBuildingsTarget || completionRatio >= 1.0F) {
                     int rushAttempts = 0;

                     int nbRushed;
                     for (nbRushed = 0; rushAttempts < 3 && nbRushed == 0; rushAttempts++) {
                        nbRushed = townHallEntity.rushCurrentConstructions(true);
                     }

                     if (nbRushed == 0) {
                        MillLog.temp(
                           townHallEntity, "Finished rushing at " + nbBuildingRushed + " on " + nbRushedBuildingsTarget + " as no more rushing was possible."
                        );
                        break;
                     }

                     nbBuildingRushed += nbRushed;
                  }

                  townHallEntity.resetConstructionsAndGoals();
                  MillLog.temp(townHallEntity, "Finished rushing at " + nbBuildingRushed + " on " + nbRushedBuildingsTarget + ".");
               }

               String playerName = null;
               if (closestPlayer != null) {
                  playerName = closestPlayer.getScoreboardName();
               }

               if (villageType.lonebuilding) {
                  mw.registerLoneBuildingsLocation(
                     world,
                     townHallEntity.getPos(),
                     townHallEntity.getVillageQualifiedName(),
                     townHallEntity.villageType,
                     townHallEntity.culture,
                     true,
                     playerName
                  );
               } else {
                  mw.registerVillageLocation(
                     world,
                     townHallEntity.getPos(),
                     townHallEntity.getVillageQualifiedName(),
                     townHallEntity.villageType,
                     townHallEntity.culture,
                     true,
                     playerName
                  );
                  townHallEntity.initialiseRelations(parentVillage);
                  if (villageType.playerControlled) {
                     townHallEntity.storeItemStack(ItemParchment.createParchmentForVillage(townHallEntity.getTownHall()));
                  }
               }

               if (MillConfigValues.LogWorldGeneration >= 1) {
                  MillLog.major(
                     this,
                     "New village generated at "
                        + thPos
                        + ", took: "
                        + (System.nanoTime() - startTime) / 1000000L
                        + " ms, of which tree finding: "
                        + TreeClearer.cumulatedTimeTreeFinding / 1000000L
                        + "ms and leave & log decay: "
                        + TreeClearer.cumulatedTimeLeaveDecay / 1000000L
                        + "ms."
                  );
               }

               if (MillLog.debugOn()) {
                  MillLog.milldebug(
                     "WorldGen",
                     "generateVillage SUCCESS name=" + townHallEntity.getVillageQualifiedName()
                        + " culture=" + (townHallEntity.culture != null ? townHallEntity.culture.key : "null")
                        + " type=" + villageType.key + " at " + thPos
                        + " buildings=" + townHallEntity.buildings.size()
                        + " villagers=" + townHallEntity.getKnownVillagers().size()
                        + " loneBuilding=" + villageType.lonebuilding
                  );
               }

               for (String key : villageType.hamlets) {
                  VillageType hamlet = villageType.culture.getVillageType(key);
                  if (hamlet != null) {
                     if (MillConfigValues.LogWorldGeneration >= 1) {
                        MillLog.major(this, "Trying to generate a hamlet: " + hamlet);
                     }

                     this.generateHamlet(world, hamlet, townHallEntity.getPos(), townHallEntity.getVillageNameWithoutQualifier(), random, completionRatio);
                  }
               }

               return true;
            }
         }
      }
   }

   public boolean generateVillageAtPoint(
      Level world,
      Random random,
      int x,
      int y,
      int z,
      Player generatingPlayer,
      boolean checkForUnloaded,
      boolean alwaysGenerate,
      boolean testBiomeValidity,
      int minDistance,
      VillageType specificVillageType,
      String name,
      Point parentVillage,
      float completionRatio
   ) {
      if (world.isClientSide()) {
         return false;
      } else {
         MillWorldData mw = Mill.getMillWorld(world);
         if (mw == null) {
            return false;
         } else {
            boolean generateVillages = MillConfigValues.generateVillages;
            if (mw.generateVillagesSet) {
               generateVillages = mw.generateVillages;
            }

            if (Mill.loadingComplete && (generateVillages || MillConfigValues.generateLoneBuildings || alwaysGenerate)) {
               Point p = new Point(x, y, z);
               if (isWithinSpawnRadiusProtection(world, specificVillageType, p)) {
                  if (Mill.proxy.isTrueServer()) {
                     if (generatingPlayer != null) {
                        ServerSender.sendTranslatedSentence(generatingPlayer, '6', "ui.tooclosetospawn");
                     }

                     return false;
                  }

                  if (!alwaysGenerate) {
                     return false;
                  }
               }

               Player closestPlayer = generatingPlayer;
               if (generatingPlayer == null) {
                  closestPlayer = world.getNearestPlayer(x, 64.0, z, 200.0, false);
               }

               try {
                  if (MillConfigValues.LogWorldGeneration >= 3) {
                     MillLog.debug(this, "Called for point: " + x + "/" + y + "/" + z);
                  }

                  MillRandom.random = random;
                  if (checkForUnloaded) {
                     int villageRadius = MillConfigValues.VillageRadius;
                     if (specificVillageType != null) {
                        villageRadius = specificVillageType.radius;
                     }

                     p = this.generateVillageAtPoint_checkForUnloaded(world, x, y, z, generatingPlayer, p, villageRadius);
                     if (p == null) {
                        return false;
                     }
                  }

                  long startTime = System.nanoTime();
                  chunkCoordsTried.add(this.computeChunkCoordsHash(p.getiX(), p.getiZ()));
                  if (generateVillages || alwaysGenerate) {
                     boolean canAttemptVillage = this.generateVillageAtPoint_canAttemptVillage(world, generatingPlayer, minDistance, mw, p, startTime);
                     if (canAttemptVillage) {
                        VillageType villageType;
                        if (specificVillageType == null) {
                           villageType = this.generateVillageAtPoint_findVillageType(world, p.getiX(), p.getiZ(), mw, closestPlayer);
                        } else {
                           villageType = specificVillageType;
                        }

                        if (villageType != null) {
                           if (MillLog.debugOn()) {
                              MillLog.milldebug(
                                 "WorldGen",
                                 "Chosen VillageType=" + villageType.key + " culture="
                                    + (villageType.culture != null ? villageType.culture.key : "null")
                                    + " at " + p + " (custom=" + (villageType.customCentre != null) + ")"
                              );
                           }

                           boolean result;
                           if (villageType.customCentre == null) {
                              result = this.generateVillage(
                                 p,
                                 world,
                                 villageType,
                                 generatingPlayer,
                                 closestPlayer,
                                 random,
                                 minDistance,
                                 name,
                                 parentVillage,
                                 completionRatio,
                                 testBiomeValidity,
                                 alwaysGenerate
                              );
                           } else {
                              result = this.generateCustomVillage(p, world, villageType, generatingPlayer, random);
                           }

                           if (result) {
                              return true;
                           }
                        }
                     }
                  }

                  if (generatingPlayer == null && MillConfigValues.generateLoneBuildings && specificVillageType == null) {
                     boolean keyLoneBuildingsOnly = false;
                     int minDistanceWithVillages = Math.min(minDistance, MillConfigValues.minDistanceBetweenVillagesAndLoneBuildings);
                     int minDistanceWithLoneBuildings = Math.min(minDistance, MillConfigValues.minDistanceBetweenLoneBuildings);

                     for (Point thp : mw.villagesList.pos) {
                        if (p.distanceTo(thp) < minDistanceWithVillages / 2) {
                           if (MillConfigValues.LogWorldGeneration >= 3) {
                              MillLog.debug(this, "Time taken for finding near villages: " + (System.nanoTime() - startTime));
                           }

                           return false;
                        }

                        if (p.distanceTo(thp) < minDistanceWithVillages) {
                           keyLoneBuildingsOnly = true;
                        }
                     }

                     for (Point thp : mw.loneBuildingsList.pos) {
                        if (p.distanceTo(thp) < minDistanceWithLoneBuildings / 4) {
                           if (MillConfigValues.LogWorldGeneration >= 3) {
                              MillLog.debug(this, "Time taken for finding near villages: " + (System.nanoTime() - startTime));
                           }

                           return false;
                        }

                        if (p.distanceTo(thp) < minDistanceWithLoneBuildings) {
                           keyLoneBuildingsOnly = true;
                        }
                     }

                     if (MillConfigValues.LogWorldGeneration >= 3) {
                        MillLog.debug(this, "Time taken for finding near villages (not found): " + (System.nanoTime() - startTime));
                     }

                     List<VillageType> acceptableLoneBuildingsType = new ArrayList<>();
                     HashMap<String, Integer> nbLoneBuildings = new HashMap<>();

                     for (String type : mw.loneBuildingsList.types) {
                        if (nbLoneBuildings.containsKey(type)) {
                           nbLoneBuildings.put(type, nbLoneBuildings.get(type) + 1);
                        } else {
                           nbLoneBuildings.put(type, 1);
                        }
                     }

                     String biomeName = this.getBiomeNameAtPos(world, p.getiX(), p.getiZ());

                     for (Culture c : Culture.ListCultures) {
                        for (VillageType vt : c.listLoneBuildingTypes) {
                           if (vt.isValidForGeneration(mw, closestPlayer, nbLoneBuildings, new Point(x, 60.0, z), biomeName, keyLoneBuildingsOnly)) {
                              acceptableLoneBuildingsType.add(vt);
                           }
                        }
                     }

                     if (acceptableLoneBuildingsType.size() == 0) {
                        return false;
                     } else {
                        VillageType loneBuilding = (VillageType)MillRandom.getWeightedChoice(acceptableLoneBuildingsType, closestPlayer);
                        if (MillConfigValues.LogWorldGeneration >= 2) {
                           MillLog.minor(null, "Attempting to find lone building: " + loneBuilding);
                        }

                        if (loneBuilding == null) {
                           return false;
                        } else {
                           if (loneBuilding.isKeyLoneBuildingForGeneration(closestPlayer) && MillConfigValues.LogWorldGeneration >= 1) {
                              MillLog.major(null, "Attempting to generate key lone building: " + loneBuilding.key);
                           }

                           boolean success = this.generateVillage(
                              p,
                              world,
                              loneBuilding,
                              generatingPlayer,
                              closestPlayer,
                              random,
                              minDistance,
                              name,
                              null,
                              completionRatio,
                              testBiomeValidity,
                              alwaysGenerate
                           );
                           if (success
                              && closestPlayer != null
                              && loneBuilding.isKeyLoneBuildingForGeneration(closestPlayer)
                              && loneBuilding.keyLoneBuildingGenerateTag != null) {
                              UserProfile profile = mw.getProfile(closestPlayer);
                              profile.clearTag(loneBuilding.keyLoneBuildingGenerateTag);
                           }

                           return success;
                        }
                     }
                  } else {
                     return false;
                  }
               } catch (Exception villageBuildException) {
                  // 1.12.2 logged and returned false, leaving a half-placed village in the world. A failure
                  // partway through placement is corruption — fatalize instead of degrading.
                  MillLog.milldebug("WorldGen", "EXCEPTION generating village at " + p + ": " + villageBuildException);
                  throw MillCrash.fail("Buildings", "Exception generating village at " + p + ": " + villageBuildException);
               }
            } else {
               return false;
            }
         }
      }
   }

   private boolean generateVillageAtPoint_canAttemptVillage(
      Level world, Player generatingPlayer, int minDistance, MillWorldData mw, Point p, long startTime
   ) {
      boolean canAttemptVillage = true;
      int minDistanceVillages = Math.min(minDistance, MillConfigValues.minDistanceBetweenVillages);
      int minDistanceLoneBuildings = Math.min(minDistance, MillConfigValues.minDistanceBetweenVillagesAndLoneBuildings);
      if (generatingPlayer == null) {
         for (Point thp : mw.villagesList.pos) {
            if (p.distanceTo(thp) < minDistanceVillages) {
               if (MillConfigValues.LogWorldGeneration >= 3) {
                  MillLog.debug(this, "Time taken for finding near villages: " + (System.nanoTime() - startTime));
               }

               canAttemptVillage = false;
            }
         }

         for (Point thpx : mw.loneBuildingsList.pos) {
            if (p.distanceTo(thpx) < minDistanceLoneBuildings) {
               if (MillConfigValues.LogWorldGeneration >= 3) {
                  MillLog.debug(this, "Time taken for finding near lone buildings: " + (System.nanoTime() - startTime));
               }

               canAttemptVillage = false;
            }
         }
      }

      if (MillConfigValues.LogWorldGeneration >= 3) {
         MillLog.debug(this, "Time taken for finding near villages (not found): " + (System.nanoTime() - startTime));
      }

      return canAttemptVillage;
   }

   private Point generateVillageAtPoint_checkForUnloaded(Level world, int x, int y, int z, Player generatingPlayer, Point p, int villageRadius) {
      boolean areaLoaded = false;
      int chunkRadius = villageRadius / 16 + 2;
      if (!WorldUtilities.checkChunksGenerated(world, x - 16 * chunkRadius, z - 16 * chunkRadius, x + 16 * chunkRadius, z + 16 * chunkRadius)) {
         for (int i = -8; i <= 8 && !areaLoaded; i++) {
            for (int j = -8; j <= 8 && !areaLoaded; j++) {
               int tx = x + i * 16;
               int tz = z + j * 16;
               if (!chunkCoordsTried.contains(this.computeChunkCoordsHash(tx, tz))
                  && WorldUtilities.checkChunksGenerated(world, tx - 16 * chunkRadius, tz - 16 * chunkRadius, tx + 16 * chunkRadius, tz + 16 * chunkRadius)) {
                  areaLoaded = true;
                  p = new Point((tx >> 4) * 16 + 8, 0.0, (tz >> 4) * 16 + 8);
               }
            }
         }
      } else {
         areaLoaded = true;
      }

      if (!areaLoaded) {
         if (generatingPlayer != null) {
            ServerSender.sendTranslatedSentence(generatingPlayer, '6', "ui.worldnotgenerated");
         }

         return null;
      } else {
         return p;
      }
   }

   private VillageType generateVillageAtPoint_findVillageType(Level world, int x, int z, MillWorldData mw, Player closestPlayer) throws IllegalArgumentException, IllegalAccessException {
      List<VillageType> acceptableVillageType = new ArrayList<>();
      HashMap<String, Integer> nbVillages = new HashMap<>();

      for (String type : mw.villagesList.types) {
         if (nbVillages.containsKey(type)) {
            nbVillages.put(type, nbVillages.get(type) + 1);
         } else {
            nbVillages.put(type, 1);
         }
      }

      String biomeName = this.getBiomeNameAtPos(world, x, z);

      for (Culture c : Culture.ListCultures) {
         for (VillageType vt : c.listVillageTypes) {
            if (vt.isValidForGeneration(Mill.getMillWorld(world), closestPlayer, nbVillages, new Point(x, 60.0, z), biomeName, false)) {
               acceptableVillageType.add(vt);
            }
         }
      }

      VillageType village;
      if (acceptableVillageType.size() != 0) {
         village = (VillageType)MillRandom.getWeightedChoice(acceptableVillageType, closestPlayer);
      } else {
         village = null;
      }

      return village;
   }

   private String getBiomeNameAtPos(Level world, int x, int z) {
      // 26.2: 1.12 read Biome.biomeName; biomes are now a dynamic registry with no display name, so the
      // biome is identified by its registry key path (lowercased), matched against villageType.biomes.
      return world.getBiome(new BlockPos(x, 0, z))
         .unwrapKey()
         .map(key -> key.identifier().getPath())
         .orElse("")
         .toLowerCase();
   }

   private boolean isInValidBiomes(Level world, VillageType villageType, Point p) {
      int biomeTotalCounter = 0;
      int biomeValidCounter = 0;

      for (int x = p.getiX() - villageType.radius; x <= p.getiX() + villageType.radius; x += 16) {
         for (int z = p.getiZ() - villageType.radius; z <= p.getiZ() + villageType.radius; z += 16) {
            // 1.12.2 wrapped the per-tile biome read in a swallow, silently undercounting valid tiles on
            // failure (skewing the validity percentage). A biome read that throws is a bug — fatalize.
            String localBiome = this.getBiomeNameAtPos(world, x, z);
            biomeTotalCounter++;
            if (villageType.biomes.contains(localBiome)) {
               biomeValidCounter++;
            }
         }
      }

      float validPerc = (float)biomeValidCounter / biomeTotalCounter;
      if (MillConfigValues.LogWorldGeneration >= 2) {
         MillLog.minor(villageType, "Biome validity around " + p + ": " + validPerc + ", required: " + villageType.getMinimumBiomeValidity());
      }

      return validPerc >= villageType.getMinimumBiomeValidity();
   }

   private boolean isUsableArea(VillageMapInfo winfo, Point centre, int radius) {
      int nbtiles = 0;
      int usabletiles = 0;

      for (int i = 0; i < winfo.length; i++) {
         for (int j = 0; j < winfo.width; j++) {
            nbtiles++;
            if (winfo.canBuild[i][j]) {
               usabletiles++;
            }
         }
      }

      return usabletiles * 1.0 / nbtiles > 0.7;
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + "@" + this.hashCode();
   }
}
