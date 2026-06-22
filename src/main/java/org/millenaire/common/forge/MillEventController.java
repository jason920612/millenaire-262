package org.millenaire.common.forge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.VillageUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.BuildingLocation;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;

/**
 * Millénaire event hooks. 1.12 used Forge {@code @SubscribeEvent} handlers; on 26.2 these are
 * plain methods wired from {@code MillenaireMod} to Fabric callbacks:
 * <ul>
 *   <li>{@link #playerLoggedIn} ← {@code ServerPlayConnectionEvents.JOIN}</li>
 *   <li>{@link #connectionClosed} ← {@code ServerPlayConnectionEvents.DISCONNECT}</li>
 *   <li>{@link #worldLoaded}/{@link #worldUnloaded} ← {@code ServerLifecycleEvents.SERVER_STARTED/
 *       SERVER_STOPPING} looped over {@code server.getAllLevels()} (this Fabric API ver has no
 *       {@code ServerWorldEvents})</li>
 *   <li>{@link #worldSaved} ← {@code ServerLifecycleEvents} (periodic/world-save) — no direct event</li>
 *   <li>{@link #damageOnPlayer} ← {@code LivingEntityDamageMixin} (HEAD on hurtServer)</li>
 *   <li>{@link #addInuitDrops} ← {@code LivingEntityDropsMixin} (TAIL on dropAllDeathLoot)</li>
 *   <li>{@link #handleFurnaceWithdrawals} ← {@code FurnaceResultSlotMixin} (HEAD on onTake)</li>
 *   <li>{@code clientLoggedIn} ← {@code ClientPlayConnectionEvents.JOIN} (client init)</li>
 * </ul>
 */
public class MillEventController {

   private static String itemPath(net.minecraft.world.item.Item item) {
      return BuiltInRegistries.ITEM.getKey(item).getPath();
   }

   // Wired via LivingEntityDropsMixin (@Inject TAIL on LivingEntity.dropAllDeathLoot) — no Fabric
   // drops event exists, so the mixin collects the extra ItemEntitys and adds them after vanilla loot.
   public void addInuitDrops(LivingEntity dying, DamageSource source, Collection<ItemEntity> drops) {
      if (dying instanceof Guardian || dying instanceof Squid) {
         this.inuitDropsSeaFood(dying, source, drops);
      } else if (dying instanceof Wolf) {
         this.inuitDropsWolfMeat(dying, source, drops);
      } else if (dying instanceof net.minecraft.world.entity.animal.polarbear.PolarBear) {
         int quantity = 1 + MillCommonUtilities.randomInt(2);
         drops.add(new ItemEntity(dying.level(), dying.getX(), dying.getY(), dying.getZ(),
            new ItemStack(MillItems.BEARMEAT_RAW, quantity)));
      }
   }

   public void clientLoggedIn() {
      Mill.proxy.handleClientLogin();
   }

   public void connectionClosed() {
      for (MillWorldData mw : Mill.serverWorlds) {
         mw.checkConnections();
      }
   }

   // Wired via LivingEntityDamageMixin (@Inject HEAD on LivingEntity.hurtServer) — the mixin passes
   // the damaged entity + DamageSource so hired villagers retaliate against their owner's attacker.
   public void damageOnPlayer(LivingEntity victim, DamageSource damageSource) {
      if (victim instanceof Player player) {
         LivingEntity source = null;
         if (damageSource.getDirectEntity() instanceof LivingEntity direct) {
            source = direct;
         } else if (damageSource.getEntity() instanceof LivingEntity attacker) {
            source = attacker;
         }

         if (source != null) {
            MillWorldData mw = Mill.getMillWorld(victim.level());
            String playerName = player.getName().getString();
            for (var villager : mw.getAllKnownVillagers()) {
               if (playerName.equals(villager.hiredBy) && damageSource.getEntity() instanceof LivingEntity trueSource) {
                  villager.setTarget(trueSource);
               }
            }
         }
      }
   }

   // Wired via FurnaceResultSlotMixin (@Inject HEAD on FurnaceResultSlot.onTake) — there is no Fabric
   // "item smelted" event, so the mixin reports the withdrawn stack here for the village-tax check.
   public void handleFurnaceWithdrawals(Player player, ItemStack smelting) {
      if (smelting.getCount() != 0) {
         MillWorldData mwd = Mill.getMillWorld(player.level());
         Point playerPos = new Point(player);
         Building closestVillageTH = mwd.getClosestVillage(playerPos);
         if (closestVillageTH != null && !closestVillageTH.controlledBy(player)) {
            BuildingLocation location = closestVillageTH.getLocationAtCoordWithTolerance(playerPos, 4);
            if (location != null) {
               Building building = location.getBuilding(player.level());
               if (building != null) {
                  boolean isBuildingPlayerOwned = building.location.getPlan() != null
                     && (building.location.getPlan().price > 0 || building.location.getPlan().isgift);
                  if (!isBuildingPlayerOwned && !building.getResManager().furnaces.isEmpty()) {
                     UserProfile serverProfile = VillageUtilities.getServerProfile(player.level(), player);
                     if (serverProfile != null) {
                        int reputationLost = smelting.getCount() * 100;
                        serverProfile.adjustReputation(closestVillageTH, -reputationLost);
                        ServerSender.sendTranslatedSentence(player, '6', "ui.stealingsmelteditems", "" + reputationLost);
                     }
                  }
               }
            }
         }
      }
   }

   private void inuitDropsSeaFood(LivingEntity dying, DamageSource source, Collection<ItemEntity> drops) {
      if (source != null && source.getEntity() instanceof Player player) {
         UserProfile profile = Mill.getMillWorld(dying.level()).getProfile(player);
         if (profile.isTagSet("huntingdrop_" + itemPath(MillItems.SEAFOOD_RAW))) {
            int quantity = 0;
            if (dying instanceof Squid) {
               if (MillCommonUtilities.chanceOn(10)) {
                  quantity = 1;
               }
            } else if (dying instanceof ElderGuardian) {
               quantity = 5 + MillCommonUtilities.randomInt(5);
            } else if (dying instanceof Guardian) {
               quantity = 2 + MillCommonUtilities.randomInt(2);
            }

            if (quantity > 0) {
               drops.add(new ItemEntity(dying.level(), dying.getX(), dying.getY(), dying.getZ(),
                  new ItemStack(MillItems.SEAFOOD_RAW, quantity)));
               MillAdvancements.GREAT_HUNTER.grant(player);
            }
         }
      }
   }

   private void inuitDropsWolfMeat(LivingEntity dying, DamageSource source, Collection<ItemEntity> drops) {
      if (source != null && source.getEntity() instanceof Player player) {
         UserProfile profile = Mill.getMillWorld(dying.level()).getProfile(player);
         if (profile.isTagSet("huntingdrop_" + itemPath(MillItems.WOLFMEAT_RAW))) {
            int quantity = MillCommonUtilities.randomInt(3);
            if (quantity > 0) {
               drops.add(new ItemEntity(dying.level(), dying.getX(), dying.getY(), dying.getZ(),
                  new ItemStack(MillItems.WOLFMEAT_RAW, quantity)));
               MillAdvancements.GREAT_HUNTER.grant(player);
            }
         }
      }
   }

   public void playerLoggedIn(ServerPlayer player) {
      try {
         UserProfile profile = VillageUtilities.getServerProfile(player.level(), player);
         String name = player.getName().getString();
         if (profile != null && !name.equals(profile.playerName)) {
            MillLog.major(null, "Name of player with UUID '" + profile.uuid + "' changed from '" + profile.playerName + "' to '" + name + "'.");
            profile.playerName = name;
            profile.saveProfile();
         }

         // FAIL-FAST: a null profile here means the player connects with no Millénaire state and NPEs in
         // village/reputation AI far away. Surface the missing profile at login instead of logging on.
         MillCrash.need(profile, "Registry", "user profile on login for '" + name + "'").connectUser();
      } catch (IllegalStateException crash) {
         throw crash; // already a fail-fast crash (null profile); propagate unchanged
      } catch (Exception loginException) {
         // FAIL-FAST: login profile handling threw; 1.12 logged-and-continued, leaving a connected player
         // with broken Millénaire state. Crash at the failure instead.
         throw MillCrash.fail("Registry", "failed to handle login for player: " + loginException);
      }
   }

   public void worldLoaded(Level level) {
      Mill.proxy.loadLanguagesIfNeeded();
      if (Mill.displayMillenaireLocationError && !Mill.proxy.isTrueServer()) {
         Mill.proxy.sendLocalChat(Mill.proxy.getTheSinglePlayer(), '4',
            "ERROR: Could not find the config file at " + Mill.proxy.getConfigFile().getAbsolutePath()
               + ". Check that the millenaire directory is in minecraft/mods/");
      } else if (level instanceof ServerLevel) {
         MillWorldData newWorld = new MillWorldData(level);
         Mill.serverWorlds.add(newWorld);
         newWorld.loadData();
      } else {
         Mill.clientWorld = new MillWorldData(level);
         if (MillLog.debugOn()) {
            MillLog.milldebug("ClientWorld", "CREATED Mill.clientWorld for level=" + level.dimension().identifier());
         }
      }
   }

   public void worldSaved(Level level) {
      if (!Mill.startupError && level.dimension() == Level.OVERWORLD) {
         if (level instanceof ServerLevel) {
            for (MillWorldData mw : Mill.serverWorlds) {
               if (mw.world == level) {
                  mw.saveEverything();
               }
            }
         } else if (Mill.clientWorld != null) {
            Mill.clientWorld.saveEverything();
         }
      }
   }

   public void worldUnloaded(Level level) {
      if (!Mill.startupError) {
         if (level instanceof ServerLevel) {
            List<MillWorldData> toDelete = new ArrayList<>();
            for (MillWorldData mw : Mill.serverWorlds) {
               if (mw.world == level) {
                  toDelete.add(mw);
               }
            }
            Mill.serverWorlds.removeAll(toDelete);
         } else if (Mill.clientWorld != null && Mill.clientWorld.world == level) {
            if (MillLog.debugOn()) {
               MillLog.milldebug("ClientWorld", "CLEARED Mill.clientWorld for level=" + level.dimension().identifier());
            }

            Mill.clientWorld = null;
         }
      }
   }
}
