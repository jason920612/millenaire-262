package org.millenaire.common.ui;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.buildingplan.BuildingCustomPlan;
import org.millenaire.common.buildingplan.BuildingImportExport;
import org.millenaire.common.buildingplan.BuildingPlan;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.ItemParchment;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.quest.QuestInstance;
import org.millenaire.common.quest.SpecialQuestActions;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.BuildingProject;
import org.millenaire.common.village.VillagerRecord;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;
import org.millenaire.common.world.WorldGenVillage;

public class GuiActions {
   public static final int VILLAGE_SCROLL_PRICE = 128;
   public static final int VILLAGE_SCROLL_REPUTATION = 8192;
   public static final int CROP_REPUTATION = 8192;
   public static final int CROP_PRICE = 512;
   public static final int CULTURE_CONTROL_REPUTATION = 131072;

   public static void activateMillChest(Player player, Point p) {
      Level world = player.level();
      if (MillConfigValues.DEV) {
         MillWorldData mw = Mill.getMillWorld(world);
         if (mw.buildingExists(p)) {
            Building ent = mw.getBuilding(p);
            if (player.getInventory().getSelectedItem() != ItemStack.EMPTY
               && player.getInventory().getSelectedItem().getItem() == Blocks.SAND.asItem()) {
               ent.testModeGoods();
               return;
            }

            if (player.getInventory().getSelectedItem() != ItemStack.EMPTY
               && player.getInventory().getSelectedItem().getItem() == MillBlocks.PATHDIRT.asItem()) {
               ent.recalculatePaths(true);
               ent.clearOldPaths();
               ent.constructCalculatedPaths();
               return;
            }

            if (player.getInventory().getSelectedItem() != ItemStack.EMPTY
               && player.getInventory().getSelectedItem().getItem() == MillBlocks.PATHGRAVEL.asItem()) {
               ent.clearOldPaths();
               return;
            }

            if (player.getInventory().getSelectedItem() != ItemStack.EMPTY
               && player.getInventory().getSelectedItem().getItem() == MillBlocks.PATHDIRT_SLAB.asItem()) {
               ent.recalculatePaths(true);
               return;
            }

            if (player.getInventory().getSelectedItem() != ItemStack.EMPTY && player.getInventory().getSelectedItem().getItem() == MillItems.DENIER_OR) {
               ent.displayInfos(player);
               return;
            }

            if (player.getInventory().getSelectedItem() != ItemStack.EMPTY
               && player.getInventory().getSelectedItem().getItem() == Items.GLASS_BOTTLE) {
               mw.setGlobalTag("alchemy");
               MillLog.major(mw, "Set alchemy tag.");
               return;
            }

            if (player.getInventory().getSelectedItem() != ItemStack.EMPTY
               && player.getInventory().getSelectedItem().getItem() == MillItems.SUMMONING_WAND) {
               ent.displayInfos(player);

               try {
                  if (ent.isTownhall) {
                     ent.rushCurrentConstructions(false);
                  }

                  if (ent.isInn) {
                     ent.attemptMerchantMove(true);
                  }

                  if (ent.hasVisitors) {
                     ent.getVisitorManager().update(true);
                  }
               } catch (Exception summoningWandException) {
                  // FAIL-FAST: a summoning-wand admin action (rush builds / merchant move / visitor update)
                  // mutates authoritative village state; a swallow hides a real failure mid-mutation.
                  throw MillCrash.fail("UI", "summoning-wand action failed for building " + ent + ": " + summoningWandException);
               }

               return;
            }

            if (player.getInventory().getSelectedItem() != ItemStack.EMPTY
               && player.getInventory().getSelectedItem().getItem() == MillBlocks.PAINTED_BRICK_WHITE.asItem()) {
               ent.choseAndApplyBrickTheme();
               MillLog.major(mw, "Changed theme of village " + ent.getVillageQualifiedName() + " to: " + ent.brickColourTheme.key);
               return;
            }
         }
      }

      ServerSender.displayMillChest(player, p);
   }

   public static void controlledBuildingsForgetBuilding(Player player, Building townHall, BuildingProject project) {
      townHall.cancelBuilding(project.location);
   }

   public static void controlledBuildingsToggleUpgrades(Player player, Building townHall, BuildingProject project, boolean allow) {
      project.location.upgradesAllowed = allow;
      if (allow) {
         townHall.noProjectsLeft = false;
      }
   }

   public static void controlledMilitaryCancelRaid(Player player, Building townHall) {
      if (townHall.raidStart == 0L) {
         townHall.cancelRaid();
         if (!townHall.world.isClientSide()) {
            townHall.sendBuildingPacket(player, false);
         }
      }
   }

   public static void controlledMilitaryDiplomacy(Player player, Building townHall, Point target, int level) {
      townHall.adjustRelation(target, level, true);
      if (!townHall.world.isClientSide()) {
         townHall.sendBuildingPacket(player, false);
      }
   }

   public static void controlledMilitaryPlanRaid(Player player, Building townHall, Building target) {
      if (townHall.raidStart == 0L) {
         townHall.adjustRelation(target.getPos(), -100, true);
         townHall.planRaid(target);
         if (!townHall.world.isClientSide()) {
            townHall.sendBuildingPacket(player, false);
         }
      }
   }

   public static void hireExtend(Player player, MillVillager villager) {
      villager.hiredBy = player.getName().getString();
      villager.hiredUntil += 24000L;
      MillCommonUtilities.changeMoney(player.getInventory(), -villager.getHireCost(player), player);
   }

   public static void hireHire(Player player, MillVillager villager) {
      villager.hiredBy = player.getName().getString();
      villager.hiredUntil = villager.level().getGameTime() + 24000L;
      VillagerRecord vr = villager.getRecord();
      if (vr != null) {
         vr.awayhired = true;
      }

      MillAdvancements.HIRED.grant(player);
      MillCommonUtilities.changeMoney(player.getInventory(), -villager.getHireCost(player), player);
   }

   public static void hireRelease(Player player, MillVillager villager) {
      villager.hiredBy = null;
      villager.hiredUntil = 0L;
      VillagerRecord vr = villager.getRecord();
      if (vr != null) {
         vr.awayhired = false;
      }
   }

   public static void hireToggleStance(Player player, boolean stance) {
      AABB surroundings = new AABB(
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getX() + 1.0,
            player.getY() + 1.0,
            player.getZ() + 1.0
         )
         .inflate(16.0, 8.0, 16.0)
         .inflate(-16.0, -8.0, -16.0);

      for (Object o : player.level().getEntitiesOfClass(MillVillager.class, surroundings)) {
         MillVillager villager = (MillVillager)o;
         if (player.getName().getString().equals(villager.hiredBy)) {
            villager.aggressiveStance = stance;
         }
      }
   }

   public static void newBuilding(Player player, Building townHall, Point pos, String planKey) {
      BuildingPlanSet set = townHall.culture.getBuildingPlanSet(planKey);
      if (set != null) {
         BuildingPlan plan = set.getRandomStartingPlan();
         BuildingPlan.LocationReturn lr = plan.testSpot(
            townHall.winfo,
            townHall.regionMapper,
            townHall.getPos(),
            pos.getiX() - townHall.winfo.mapStartX,
            pos.getiZ() - townHall.winfo.mapStartZ,
            MillRandom.getRandom(),
            -1,
            true
         );
         if (lr.location == null) {
            String error = null;
            if (lr.errorCode == 3) {
               error = "ui.constructionforbidden";
            } else if (lr.errorCode == 2) {
               error = "ui.locationclash";
            } else if (lr.errorCode == 1) {
               error = "ui.outsideradius";
            } else if (lr.errorCode == 4) {
               error = "ui.wrongelevation";
            } else if (lr.errorCode == 5) {
               error = "ui.danger";
            } else if (lr.errorCode == 6) {
               error = "ui.notreachable";
            } else {
               error = "ui.unknownerror";
            }

            if (MillConfigValues.DEV) {
               WorldUtilities.setBlock(townHall.mw.world, lr.errorPos.getRelative(0.0, 30.0, 0.0), Blocks.GRAVEL);
            }

            ServerSender.sendTranslatedSentence(player, '6', "ui.problemat", pos.distanceDirectionShort(lr.errorPos), error);
         } else {
            lr.location.level = -1;
            BuildingProject project = new BuildingProject(set);
            project.location = lr.location;
            setSign(townHall, lr.location.minx, lr.location.minz, project);
            setSign(townHall, lr.location.maxx, lr.location.minz, project);
            setSign(townHall, lr.location.minx, lr.location.maxz, project);
            setSign(townHall, lr.location.maxx, lr.location.maxz, project);
            townHall.buildingProjects.get(BuildingProject.EnumProjects.CUSTOMBUILDINGS).add(project);
            townHall.noProjectsLeft = false;
            ServerSender.sendTranslatedSentence(player, '2', "ui.projectadded");
         }
      }
   }

   public static void newCustomBuilding(Player player, Building townHall, Point pos, String planKey) {
      BuildingCustomPlan customBuilding = townHall.culture.getBuildingCustom(planKey);
      if (customBuilding != null) {
         try {
            townHall.addCustomBuilding(customBuilding, pos);
         } catch (Exception customBuildingException) {
            // FAIL-FAST: adding the custom building mutates the town hall's project list; a swallow leaves
            // the building half-registered and hides the cause. Surface it loudly.
            throw MillCrash.fail("UI", "failed to create custom building '" + planKey + "': " + customBuildingException);
         }
      }
   }

   public static void newVillageCreation(Player player, Point pos, String cultureKey, String villageTypeKey) {
      Culture culture = Culture.getCultureByName(cultureKey);
      if (culture != null) {
         VillageType villageType = culture.getVillageType(villageTypeKey);
         if (villageType != null) {
            WorldGenVillage genVillage = new WorldGenVillage();
            boolean result = genVillage.generateVillageAtPoint(
               player.level(),
               MillRandom.random,
               pos.getiX(),
               pos.getiY(),
               pos.getiZ(),
               player,
               false,
               true,
               false,
               0,
               villageType,
               null,
               null,
               0.0F
            );
            if (result) {
               MillAdvancements.SUMMONING_WAND.grant(player);
               if (villageType.playerControlled && MillAdvancements.VILLAGE_LEADER_ADVANCEMENTS.containsKey(cultureKey)) {
                  MillAdvancements.VILLAGE_LEADER_ADVANCEMENTS.get(cultureKey).grant(player);
               }

               if (villageType.playerControlled && villageType.customCentre != null) {
                  MillAdvancements.AMATEUR_ARCHITECT.grant(player);
               }
            }
         }
      }
   }

   public static void pujasChangeEnchantment(Player player, Building temple, int enchantmentId) {
      if (temple != null && temple.pujas != null) {
         temple.pujas.changeEnchantment(enchantmentId);
         temple.sendBuildingPacket(player, false);
         if (temple.pujas.type == 0) {
            MillAdvancements.PUJA.grant(player);
         } else if (temple.pujas.type == 1) {
            MillAdvancements.SACRIFICE.grant(player);
         }
      }
   }

   public static void questCompleteStep(Player player, MillVillager villager) {
      UserProfile profile = Mill.getMillWorld(player.level()).getProfile(player);
      QuestInstance qi = profile.villagersInQuests.get(villager.getVillagerId());
      if (qi == null) {
         MillLog.error(villager, "Could not find quest instance for this villager.");
      } else {
         qi.completeStep(player, villager);
      }
   }

   public static void questRefuse(Player player, MillVillager villager) {
      UserProfile profile = Mill.getMillWorld(player.level()).getProfile(player);
      QuestInstance qi = profile.villagersInQuests.get(villager.getVillagerId());
      if (qi == null) {
         MillLog.error(villager, "Could not find quest instance for this villager.");
      } else {
         qi.refuseQuest(player, villager);
      }
   }

   private static void setSign(Building townHall, int i, int j, BuildingProject project) {
      WorldUtilities.setBlockAndMetadata(townHall.world, i, WorldUtilities.findTopSoilBlock(townHall.world, i, j), j, Blocks.OAK_SIGN, 0, true, false);
      SignBlockEntity sign = (SignBlockEntity)townHall.world.getBlockEntity(new BlockPos(i, WorldUtilities.findTopSoilBlock(townHall.world, i, j), j));
      if (sign != null) {
         // 26.2: sign text is an immutable SignText updated via updateText (no signText[] array).
         sign.updateText(text -> text
               .setMessage(0, Component.literal(project.getNativeName()))
               .setMessage(1, Component.literal(""))
               .setMessage(2, Component.literal(project.getGameName()))
               .setMessage(3, Component.literal("")),
            true);
      }
   }

   public static void updateCustomBuilding(Player player, Building building) {
      if (building.location.getCustomPlan() != null) {
         building.location.getCustomPlan().registerResources(building, building.location);
      }
   }

   public static void useNegationWand(Player player, Building townHall) {
      ServerSender.sendTranslatedSentence(player, '4', "negationwand.destroyed", townHall.villageType.name);
      if (!townHall.villageType.lonebuilding) {
         MillAdvancements.SCIPIO.grant(player);
      }

      townHall.destroyVillage();
   }

   public static InteractionResult useSummoningWand(ServerPlayer player, Point pos) {
      MillWorldData mw = Mill.getMillWorld(player.level());
      Block block = WorldUtilities.getBlock(player.level(), pos);
      Building closestVillage = mw.getClosestVillage(pos);
      if (closestVillage != null && pos.squareRadiusDistance(closestVillage.getPos()) < closestVillage.villageType.radius + 10) {
         if (block == Blocks.OAK_SIGN) {
            return InteractionResult.FAIL;
         } else if (closestVillage.controlledBy(player)) {
            Building b = closestVillage.getBuildingAtCoordPlanar(pos);
            if (b != null) {
               if (b.location.isCustomBuilding) {
                  ServerSender.displayNewBuildingProjectGUI(player, closestVillage, pos);
               } else {
                  ServerSender.sendTranslatedSentence(player, 'e', "ui.wand_locationinuse");
               }
            } else {
               ServerSender.displayNewBuildingProjectGUI(player, closestVillage, pos);
            }

            return InteractionResult.SUCCESS;
         } else {
            ServerSender.sendTranslatedSentence(player, 'e', "ui.wand_invillagerange", closestVillage.getVillageQualifiedName());
            return InteractionResult.FAIL;
         }
      } else if (block != Blocks.OAK_SIGN) {
         if (block == MillBlocks.LOCKED_CHEST) {
            return InteractionResult.PASS;
         } else if (block == Blocks.OBSIDIAN) {
            WorldGenVillage genVillage = new WorldGenVillage();
            genVillage.generateVillageAtPoint(
               player.level(), MillRandom.random, pos.getiX(), pos.getiY(), pos.getiZ(), player, false, true, false, 0, null, null, null, 0.0F
            );
            return InteractionResult.SUCCESS;
         } else if (block == Blocks.GOLD_BLOCK) {
            ServerSender.displayNewVillageGUI(player, pos);
            return InteractionResult.SUCCESS;
         } else if (mw.getProfile(player).isTagSet("normanmarvel_picklocation")) {
            SpecialQuestActions.normanMarvelPickLocation(mw, player, pos);
            return InteractionResult.SUCCESS;
         } else {
            ServerSender.sendTranslatedSentence(player, 'f', "ui.wandinstruction");
            return InteractionResult.FAIL;
         }
      } else {
         if (Mill.proxy.isTrueServer()
            && !net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) {
            ServerSender.sendTranslatedSentence(player, '4', "ui.serverimportforbidden");
         } else {
            BuildingImportExport.summoningWandImportBuildingRequest(player, Mill.serverWorlds.get(0).world, pos);
         }

         return InteractionResult.SUCCESS;
      }
   }

   public static void villageChiefPerformBuilding(Player player, MillVillager chief, String planKey) {
      BuildingPlan plan = chief.getTownHall().culture.getBuildingPlanSet(planKey).getRandomStartingPlan();
      chief.getTownHall().buildingsBought.add(planKey);
      MillCommonUtilities.changeMoney(player.getInventory(), -plan.price, player);
      ServerSender.sendTranslatedSentence(player, 'f', "ui.housebought", chief.getVillagerName(), plan.nativeName);
   }

   public static void villageChiefPerformCrop(Player player, MillVillager chief, String value) {
      UserProfile profile = Mill.getMillWorld(player.level()).getProfile(player);
      profile.setTag("cropplanting_" + value);
      MillCommonUtilities.changeMoney(player.getInventory(), -512, player);
      Item crop = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("millenaire", value));
      ServerSender.sendTranslatedSentence(player, 'f', "ui.croplearned", chief.getVillagerName(), "ui.crop." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(crop).getPath());
   }

   public static void villageChiefPerformCultureControl(Player player, MillVillager chief) {
      UserProfile profile = Mill.getMillWorld(player.level()).getProfile(player);
      profile.setTag("culturecontrol_" + chief.getCulture().key);
      ServerSender.sendTranslatedSentence(player, 'f', "ui.control_gotten", chief.getVillagerName(), chief.getCulture().getAdjectiveTranslatedKey());
   }

   public static void villageChiefPerformDiplomacy(Player player, MillVillager chief, Point village, boolean praise) {
      float effect = 0.0F;
      if (praise) {
         effect = 10.0F;
      } else {
         effect = -10.0F;
      }

      int reputation = Math.min(chief.getTownHall().getReputation(player), 32768);
      float coeff = (float)((Math.log(reputation) / Math.log(32768.0) * 2.0 + reputation / 32768) / 3.0);
      effect *= coeff;
      effect = (float)(effect * ((MillRandom.randomInt(40) + 80) / 100.0));
      chief.getTownHall().adjustRelation(village, (int)effect, false);
      UserProfile profile = Mill.getMillWorld(player.level()).getProfile(player);
      profile.adjustDiplomacyPoint(chief.getTownHall(), -1);
      if (MillConfigValues.LogVillage >= 1) {
         MillLog.major(chief.getTownHall(), "Adjusted relation by " + effect + " (coef: " + coeff + ")");
      }
   }

   public static void villageChiefPerformHuntingDrop(Player player, MillVillager chief, String value) {
      UserProfile profile = Mill.getMillWorld(player.level()).getProfile(player);
      profile.setTag("huntingdrop_" + value);
      MillCommonUtilities.changeMoney(player.getInventory(), -512, player);
      Item drop = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("millenaire", value));
      ServerSender.sendTranslatedSentence(
         player, 'f', "ui.huntingdroplearned", chief.getVillagerName(), "ui.huntingdrop." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(drop).getPath()
      );
   }

   public static void villageChiefPerformVillageScroll(Player player, MillVillager chief) {
      for (int i = 0; i < Mill.getMillWorld(player.level()).villagesList.pos.size(); i++) {
         Point p = Mill.getMillWorld(player.level()).villagesList.pos.get(i);
         if (chief.getTownHall().getPos().sameBlock(p)) {
            MillCommonUtilities.changeMoney(player.getInventory(), -128, player);
            player.getInventory().add(ItemParchment.createParchmentForVillage(chief.getTownHall()));
            ServerSender.sendTranslatedSentence(player, 'f', "ui.scrollbought", chief.getVillagerName());
         }
      }
   }
}
