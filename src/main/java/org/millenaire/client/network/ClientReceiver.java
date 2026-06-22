package org.millenaire.client.network;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.network.FriendlyByteBuf;
import org.millenaire.client.MillClientUtilities;
import org.millenaire.client.forge.ClientGuiHandler;
import org.millenaire.client.gui.DisplayActions;
import org.millenaire.client.gui.UnlockingToast;
import org.millenaire.common.network.MillNetworking;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.TileEntityLockedChest;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.ui.MillMapInfo;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.UserProfile;

@Environment(EnvType.CLIENT)
public class ClientReceiver {
   private static final ClientReceiver INSTANCE = new ClientReceiver();
   /** [MILLDEBUG] one-shot flag so we only log the first village-data receipt per session. */
   private static boolean firstVillageDataReceived = false;

   /**
    * Register the Fabric client-side receiver for Mill's payload.
    *
    * <p>26.2 PORT NOTE: replaces the Forge {@code @SubscribeEvent ClientCustomPacketEvent} hook.
    * Call from the client initializer.
    */
   public static void registerClientReceiver() {
      ClientPlayNetworking.registerGlobalReceiver(MillNetworking.TYPE, (payload, context) -> {
         FriendlyByteBuf data = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload.data()));
         context.client().execute(() -> {
            if (Mill.clientWorld == null) {
               if (MillLog.debugOn()) {
                  MillLog.milldebug("Packet", "RECEIVED but DROPPED: Mill.clientWorld is null (client not yet synced).");
               }

               MillLog.error(INSTANCE, "Received a packet despite null clientWorld.");
            } else {
               INSTANCE.processPacket(data);
            }
         });
      });
   }

   private void processPacket(FriendlyByteBuf data) {
      try {
         int packettype = data.readInt();
         Mill.clientWorld.millenaireEnabled = true;
         if (MillLog.debugOn()) {
            MillLog.milldebug("Packet", "RECEIVED id=" + packettype);
         }

         if (MillConfigValues.LogNetwork >= 3) {
            MillLog.debug(this, "Received client packet type: " + packettype);
         }

         UserProfile profile = Mill.proxy.getClientProfile();
         if (packettype == 2) {
            Building.readBuildingPacket(Mill.clientWorld, data);
         } else if (packettype == 11) {
            Building.readShopPacket(Mill.clientWorld, data);
         } else if (packettype == 3) {
            MillVillager.readVillagerPacket(data);
         } else if (packettype == 100) {
            this.readTranslatedChatPackage(data);
         } else if (packettype == 108) {
            this.readVillagerSentencePackage(data);
         } else if (packettype == 109) {
            this.readAdvancementPackage(data);
         } else if (packettype == 110) {
            this.readContentUnlockedPackage(data);
         } else if (packettype == 111) {
            this.readContentUnlockedPackageMultiple(data);
         } else if (packettype == 5) {
            TileEntityLockedChest.readUpdatePacket(data, Mill.clientWorld.world);
         } else if (packettype == 101) {
            profile.receiveProfilePacket(data);
         } else if (packettype == 102) {
            profile.receiveQuestInstancePacket(data);
         } else if (packettype == 103) {
            profile.receiveQuestInstanceDestroyPacket(data);
         } else if (packettype == 104) {
            this.readGUIPacket(data);
         } else if (packettype == 7) {
            MillMapInfo.readPacket(data);
         } else if (packettype == 9) {
            if (MillLog.debugOn() && !firstVillageDataReceived) {
               firstVillageDataReceived = true;
               MillLog.milldebug("ClientWorld", "FIRST village-list data (packet 9) received from server.");
            }

            Mill.clientWorld.receiveVillageListPacket(data);
         } else if (packettype == 10) {
            this.readServerContentPacket(data);
         } else if (packettype == 107) {
            this.readAnimalBreedPacket(data);
         } else {
            MillLog.error(null, "Received packet with unknown type: " + packettype);
         }
      } catch (Exception packetHandlingError) {
         // A swallowed packet-handler error is a silent client/server desync: the handler aborted partway,
         // leaving the client view inconsistent with the server's. Crash loudly instead of logging-and-continuing.
         throw MillCrash.fail("Net", "ClientReceiver.processPacket failed handling an incoming Mill packet", packetHandlingError);
      }
   }

   private void readAdvancementPackage(FriendlyByteBuf data) {
      String advancementKey = data.readUtf();
      MillAdvancements.addToStats(Mill.proxy.getTheSinglePlayer(), advancementKey);
   }

   private void readAnimalBreedPacket(FriendlyByteBuf data) {
      Point p = StreamReadWrite.readNullablePoint(data);
      int endId = data.readInt();

      for (Entity ent : WorldUtilities.getEntitiesWithinAABB(Mill.clientWorld.world, Animal.class, p, 5, 5)) {
         Animal animal = (Animal)ent;
         if (animal.getId() == endId) {
            animal.setInLove(null);
            MillCommonUtilities.generateHearts(animal);
         }
      }
   }

   private void readContentUnlockedPackage(FriendlyByteBuf data) {
      int contentType = data.readInt();
      String cultureKey = data.readUtf();
      String contentKey = data.readUtf();
      int nbUnlocked = data.readInt();
      int nbTotal = data.readInt();
      Culture culture = Culture.getCultureByName(cultureKey);
      if (culture != null) {
         if (contentType == 1) {
            BuildingPlanSet planSet = culture.getBuildingPlanSet(contentKey);
            if (planSet != null) {
               Minecraft.getInstance().gui.toastManager().addToast(new UnlockingToast(planSet, nbUnlocked, nbTotal));
            }
         } else if (contentType == 4) {
            TradeGood tradeGood = culture.getTradeGood(contentKey);
            if (tradeGood != null) {
               Minecraft.getInstance().gui.toastManager().addToast(new UnlockingToast(tradeGood, nbUnlocked, nbTotal));
            }
         } else if (contentType == 2) {
            VillageType villageType = culture.getVillageType(contentKey);
            if (villageType != null) {
               Minecraft.getInstance().gui.toastManager().addToast(new UnlockingToast(villageType, nbUnlocked, nbTotal));
            }
         } else if (contentType == 3) {
            VillagerType villagerType = culture.getVillagerType(contentKey);
            if (villagerType != null) {
               Minecraft.getInstance().gui.toastManager().addToast(new UnlockingToast(villagerType, nbUnlocked, nbTotal));
            }
         }
      }
   }

   private void readContentUnlockedPackageMultiple(FriendlyByteBuf data) {
      int contentType = data.readInt();
      String cultureKey = data.readUtf();
      List<String> contentKeys = StreamReadWrite.readStringList(data);
      int nbUnlocked = data.readInt();
      int nbTotal = data.readInt();
      Culture culture = Culture.getCultureByName(cultureKey);
      if (culture != null && contentType == 5) {
         List<TradeGood> tradeGoods = new ArrayList<>();

         for (String contentKey : contentKeys) {
            TradeGood tradeGood = culture.getTradeGood(contentKey);
            if (tradeGood != null) {
               tradeGoods.add(tradeGood);
            }
         }

         if (tradeGoods.size() > 0) {
            Minecraft.getInstance().gui.toastManager().addToast(new UnlockingToast(tradeGoods, nbUnlocked, nbTotal));
         }
      }
   }

   private void readGUIPacket(FriendlyByteBuf data) {
      int guiId = data.readInt();
      if (guiId == 3) {
         MillVillager v = Mill.clientWorld.getVillagerById(data.readLong());
         if (v != null) {
            DisplayActions.displayQuestGUI(Mill.proxy.getTheSinglePlayer(), v);
         } else {
            MillLog.error(this, "Unknown villager id in readGUIPacket: " + guiId);
         }
      } else if (guiId == 12) {
         MillVillager v = Mill.clientWorld.getVillagerById(data.readLong());
         if (v != null) {
            DisplayActions.displayHireGUI(Mill.proxy.getTheSinglePlayer(), v);
         } else {
            MillLog.error(this, "Unknown villager id in readGUIPacket: " + guiId);
         }
      } else if (guiId == 4) {
         MillVillager v = Mill.clientWorld.getVillagerById(data.readLong());
         if (v != null) {
            DisplayActions.displayVillageChiefGUI(Mill.proxy.getTheSinglePlayer(), v);
         } else {
            MillLog.error(this, "Unknown villager id in readGUIPacket: " + guiId);
         }
      } else if (guiId == 5) {
         Point p = StreamReadWrite.readNullablePoint(data);
         if (p != null) {
            DisplayActions.displayVillageBookGUI(Mill.proxy.getTheSinglePlayer(), p);
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 9) {
         Point p = StreamReadWrite.readNullablePoint(data);
         if (p != null) {
            Building building = Mill.clientWorld.getBuilding(p);
            if (building != null) {
               DisplayActions.displayNegationWandGUI(Mill.proxy.getTheSinglePlayer(), building);
            }
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 10) {
         Point thPos = StreamReadWrite.readNullablePoint(data);
         Point pos = StreamReadWrite.readNullablePoint(data);
         if (thPos != null && pos != null) {
            Building townHall = Mill.clientWorld.getBuilding(thPos);
            if (townHall != null) {
               Building building = townHall.getBuildingAtCoordPlanar(pos);
               if (building != null && building.location.isCustomBuilding) {
                  DisplayActions.displayEditCustomBuildingGUI(Mill.proxy.getTheSinglePlayer(), building);
               } else {
                  DisplayActions.displayNewBuildingProjectGUI(Mill.proxy.getTheSinglePlayer(), townHall, pos);
               }
            }
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 13) {
         Point pos = StreamReadWrite.readNullablePoint(data);
         if (pos != null) {
            DisplayActions.displayNewVillageGUI(Mill.proxy.getTheSinglePlayer(), pos);
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 11) {
         Point thPos = StreamReadWrite.readNullablePoint(data);
         if (thPos != null) {
            Building building = Mill.clientWorld.getBuilding(thPos);
            if (building != null) {
               DisplayActions.displayControlledProjectGUI(Mill.proxy.getTheSinglePlayer(), building);
            }
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 14) {
         Point thPos = StreamReadWrite.readNullablePoint(data);
         if (thPos != null) {
            Building building = Mill.clientWorld.getBuilding(thPos);
            if (building != null) {
               DisplayActions.displayControlledMilitaryGUI(Mill.proxy.getTheSinglePlayer(), building);
            }
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 7) {
         Point p = StreamReadWrite.readNullablePoint(data);
         if (p != null) {
            MillClientUtilities.displayPanel(Mill.clientWorld.world, Mill.proxy.getTheSinglePlayer(), p);
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 2) {
         Point p = StreamReadWrite.readNullablePoint(data);
         if (p != null) {
            openMillGui(2, p.getiX(), p.getiY(), p.getiZ());
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 8) {
         int id1 = data.readInt();
         int id2 = data.readInt();
         openMillGui(8, id1, id2, 0);
      } else if (guiId == 1) {
         Point p = StreamReadWrite.readNullablePoint(data);
         boolean locked = data.readBoolean();
         if (p != null) {
            TileEntityLockedChest chest = p.getMillChest(Mill.clientWorld.world);
            if (chest != null && chest.loaded) {
               openMillGui(1, p.getiX(), p.getiY(), p.getiZ(), locked);
            }
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 15) {
         Point tablePos = StreamReadWrite.readNullablePoint(data);
         if (tablePos != null) {
            DisplayActions.displayImportTableGUI(Mill.proxy.getTheSinglePlayer(), tablePos);
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 6) {
         Point p = StreamReadWrite.readNullablePoint(data);
         if (p != null) {
            openMillGui(6, p.getiX(), p.getiY(), p.getiZ());
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else if (guiId == 16) {
         Point p = StreamReadWrite.readNullablePoint(data);
         if (p != null) {
            openMillGui(16, p.getiX(), p.getiY(), p.getiZ());
         } else {
            MillLog.error(this, "Unknown point in readGUIPacket: " + guiId);
         }
      } else {
         MillLog.error(null, "Unknown GUI: " + guiId);
      }
   }

   /**
    * Opens a Mill container-screen by its legacy integer GUI id.
    *
    * <p>26.2 PORT NOTE: replaces {@code player.openGui(Mill.instance, id, world, x, y, z)} (the Forge
    * {@code IGuiHandler} flow is gone). The screen is resolved client-side by {@link ClientGuiHandler}
    * and shown directly (Mill builds its menus itself; see {@link org.millenaire.common.ui.MillMenus}).
    */
   private static void openMillGui(int id, int x, int y, int z) {
      openMillGui(id, x, y, z, null);
   }

   private static void openMillGui(int id, int x, int y, int z, Boolean lockedOverride) {
      Object screen = new ClientGuiHandler().getClientGuiElement(id, Mill.proxy.getTheSinglePlayer(), Mill.clientWorld.world, x, y, z, lockedOverride);
      if (screen instanceof Screen) {
         Minecraft.getInstance().setScreenAndShow((Screen)screen);
      }
   }

   private void readServerContentPacket(FriendlyByteBuf data) {
      int nbCultures = data.readShort();

      for (int i = 0; i < nbCultures; i++) {
         Culture.readCultureMissingContentPacket(data);
      }

      Culture.refreshLists();
   }

   private void readTranslatedChatPackage(FriendlyByteBuf data) {
      char colour = data.readChar();
      String s = data.readUtf();
      String[] values = new String[data.readInt()];

      for (int i = 0; i < values.length; i++) {
         values[i] = LanguageUtilities.unknownString(StreamReadWrite.readNullableString(data));
      }

      s = LanguageUtilities.string(s, values);
      Mill.proxy.sendLocalChat(Mill.proxy.getTheSinglePlayer(), colour, s);
   }

   private void readVillagerSentencePackage(FriendlyByteBuf data) {
      MillVillager v = Mill.clientWorld.getVillagerById(data.readLong());
      if (v != null) {
         MillClientUtilities.putVillagerSentenceInChat(v);
      }
   }
}
