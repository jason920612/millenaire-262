package org.millenaire.common.network;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.TileEntityLockedChest;
import org.millenaire.common.entity.TileEntityPanel;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

public class ServerSender {
   public static void displayControlledMilitaryGUI(Player player, Building townHall) {
      townHall.sendBuildingPacket(player, false);
      MillWorldData mw = Mill.getMillWorld(player.level());

      for (Point p : townHall.getKnownVillages()) {
         Building b = mw.getBuilding(p);
         if (b != null) {
            b.sendBuildingPacket(player, false);
         }
      }

      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(14);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      sendPacketToPlayer(data, player);
   }

   public static void displayControlledProjectGUI(Player player, Building townHall) {
      townHall.sendBuildingPacket(player, false);
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(11);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      sendPacketToPlayer(data, player);
   }

   public static void displayHireGUI(Player player, MillVillager villager) {
      villager.getTownHall().sendBuildingPacket(player, false);
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(12);
      data.writeLong(villager.getVillagerId());
      sendPacketToPlayer(data, player);
   }

   public static void displayImportTableGUI(Player player, Point tableLocation) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(15);
      StreamReadWrite.writeNullablePoint(tableLocation, data);
      sendPacketToPlayer(data, player);
   }

   /** Opens the Pujas (shrine) GUI for the given temple — 1.12 {@code openGui(...,6,...)}. */
   public static void displayPujasGUI(Player player, Building temple) {
      temple.sendBuildingPacket(player, true);
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(6);
      StreamReadWrite.writeNullablePoint(temple.getPos(), data);
      sendPacketToPlayer(data, player);
   }

   public static void displayMerchantTradeGUI(Player player, MillVillager villager) {
      FriendlyByteBuf data = getPacketBuffer();
      int[] ids = MillCommonUtilities.packLong(villager.getVillagerId());
      data.writeInt(104);
      data.writeInt(8);
      data.writeInt(ids[0]);
      data.writeInt(ids[1]);
      villager.getHouse().sendBuildingPacket(player, true);
      villager.getTownHall().sendBuildingPacket(player, true);
      sendPacketToPlayer(data, player);
      // The PACKET_OPENGUI(104) packet written above drives the client to build + show the screen via
      // ClientReceiver.readGUIPacket → ClientGuiHandler (Mill's own GUI flow; no vanilla player.openMenu needed).
   }

   public static void displayMillChest(Player player, Point chestPos) {
      TileEntityLockedChest chest = chestPos.getMillChest(player.level());
      if (chest != null) {
         MillWorldData mw = Mill.getMillWorld(player.level());
         if (chest.buildingPos != null) {
            Building building = mw.getBuilding(chest.buildingPos);
            if (building != null) {
               building.sendBuildingPacket(player, true);
            } else {
               chest.buildingPos = null;
               chest.sendUpdatePacket(player);
            }
         } else {
            chest.sendUpdatePacket(player);
         }

         FriendlyByteBuf data = getPacketBuffer();
         data.writeInt(104);
         data.writeInt(1);
         StreamReadWrite.writeNullablePoint(chestPos, data);
         data.writeBoolean(chest.isLockedFor(player));
         sendPacketToPlayer(data, player);
         // The PACKET_OPENGUI(104) packet written above drives the client to build + show the screen via
      // ClientReceiver.readGUIPacket → ClientGuiHandler (Mill's own GUI flow; no vanilla player.openMenu needed).
      }
   }

   public static void displayNegationWandGUI(Player player, Building townHall) {
      townHall.sendBuildingPacket(player, false);
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(9);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      sendPacketToPlayer(data, player);
   }

   public static void displayNewBuildingProjectGUI(Player player, Building townHall, Point pos) {
      townHall.sendBuildingPacket(player, false);
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(10);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      StreamReadWrite.writeNullablePoint(pos, data);
      sendPacketToPlayer(data, player);
   }

   public static void displayNewVillageGUI(Player player, Point pos) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(13);
      StreamReadWrite.writeNullablePoint(pos, data);
      sendPacketToPlayer(data, player);
   }

   public static void displayPanel(Player player, Point signPos) {
      TileEntityPanel panel = signPos.getPanel(player.level());
      if (panel != null) {
         MillWorldData mw = Mill.getMillWorld(player.level());
         if (panel.buildingPos != null) {
            Building building = mw.getBuilding(panel.buildingPos);
            if (building != null) {
               building.sendBuildingPacket(player, true);
            }
         }

         FriendlyByteBuf data = getPacketBuffer();
         data.writeInt(104);
         data.writeInt(7);
         StreamReadWrite.writeNullablePoint(signPos, data);
         sendPacketToPlayer(data, player);
      }
   }

   public static void displayQuestGUI(Player player, MillVillager villager) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(3);
      data.writeLong(villager.getVillagerId());
      sendPacketToPlayer(data, player);
   }

   public static void displayVillageBookGUI(Player player, Point p) {
      MillWorldData mw = Mill.getMillWorld(player.level());
      Building th = mw.getBuilding(p);
      if (th != null) {
         th.sendBuildingPacket(player, true);
         FriendlyByteBuf data = getPacketBuffer();
         data.writeInt(104);
         data.writeInt(5);
         StreamReadWrite.writeNullablePoint(p, data);
         sendPacketToPlayer(data, player);
      }
   }

   public static void displayVillageChiefGUI(Player player, MillVillager chief) {
      if (chief.getTownHall() == null) {
         MillLog.error(chief, "Needed to send chief's TH but TH is null.");
      } else {
         chief.getTownHall().sendBuildingPacket(player, false);
         MillWorldData mw = Mill.getMillWorld(player.level());

         for (Point p : chief.getTownHall().getKnownVillages()) {
            Building b = mw.getBuilding(p);
            if (b != null) {
               b.sendBuildingPacket(player, false);
            }
         }

         FriendlyByteBuf data = getPacketBuffer();
         data.writeInt(104);
         data.writeInt(4);
         data.writeLong(chief.getVillagerId());
         sendPacketToPlayer(data, player);
      }
   }

   public static void displayVillageTradeGUI(Player player, Building building) {
      building.computeShopGoods(player);
      building.sendShopPacket(player);
      building.sendBuildingPacket(player, true);
      if (!building.isTownhall) {
         building.getTownHall().sendBuildingPacket(player, false);
      }

      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(104);
      data.writeInt(2);
      StreamReadWrite.writeNullablePoint(building.getPos(), data);
      sendPacketToPlayer(data, player);
      // The PACKET_OPENGUI(104) packet written above drives the client to build + show the screen via
      // ClientReceiver.readGUIPacket → ClientGuiHandler (Mill's own GUI flow; no vanilla player.openMenu needed).
   }

   public static FriendlyByteBuf getPacketBuffer() {
      return new FriendlyByteBuf(Unpooled.buffer());
   }

   public static void sendAdvancementEarned(ServerPlayer player, String advancementKey) {
      if (player != null) {
         if (player instanceof ServerPlayer) {
            FriendlyByteBuf data = getPacketBuffer();
            data.writeInt(109);
            data.writeUtf(advancementKey);
            sendPacketToPlayer(data, player);
         }
      }
   }

   public static void sendAnimalBreeding(Animal animal) {
      FriendlyByteBuf data = getPacketBuffer();
      Point pos = new Point(animal);
      data.writeInt(107);
      StreamReadWrite.writeNullablePoint(pos, data);
      data.writeInt(animal.getId());
      sendPacketToPlayersInRange(data, pos, 50);
   }

   public static void sendChat(Player player, ChatFormatting colour, String s) {
      net.minecraft.network.chat.MutableComponent chat = Component.literal(s);
      chat.setStyle(chat.getStyle().withColor(colour));
      player.sendSystemMessage(chat);
   }

   public static void sendContentUnlocked(Player player, int contentType, String cultureKey, String contentKey, int nbUnlocked, int nbTotal) {
      if (player != null) {
         if (player instanceof ServerPlayer) {
            FriendlyByteBuf data = getPacketBuffer();
            data.writeInt(110);
            data.writeInt(contentType);
            data.writeUtf(cultureKey);
            data.writeUtf(contentKey);
            data.writeInt(nbUnlocked);
            data.writeInt(nbTotal);
            sendPacketToPlayer(data, player);
         }
      }
   }

   public static void sendContentUnlockedMultiple(
      Player player, int contentType, String cultureKey, List<String> contentKeys, int nbUnlocked, int nbTotal
   ) {
      if (player != null) {
         if (player instanceof ServerPlayer) {
            FriendlyByteBuf data = getPacketBuffer();
            data.writeInt(111);
            data.writeInt(contentType);
            data.writeUtf(cultureKey);
            StreamReadWrite.writeStringList(contentKeys, data);
            data.writeInt(nbUnlocked);
            data.writeInt(nbTotal);
            sendPacketToPlayer(data, player);
         }
      }
   }

   public static void sendLockedChestUpdatePacket(TileEntityLockedChest chest, Player player) {
      FriendlyByteBuf data = getPacketBuffer();
      Point pos = new Point(chest.getBlockPos());
      data.writeInt(5);
      StreamReadWrite.writeNullablePoint(pos, data);
      StreamReadWrite.writeNullablePoint(chest.buildingPos, data);
      data.writeBoolean(MillConfigValues.DEV);
      data.writeByte(chest.getContainerSize());

      for (int i = 0; i < chest.getContainerSize(); i++) {
         StreamReadWrite.writeNullableItemStack(chest.getItem(i), data);
      }

      sendPacketToPlayer(data, player);
   }

   public static void sendPacketToPlayer(FriendlyByteBuf packetBuffer, Player player) {
      // 1.12 Forge FMLProxyPacket + Mill.millChannel.sendTo → Fabric custom payload.
      if (player instanceof ServerPlayer serverPlayer) {
         if (MillLog.debugOn()) {
            MillLog.milldebug("Packet", "SENT id=" + peekPacketId(packetBuffer) + " to=" + player.getName().getString());
         }

         MillNetworking.sendToPlayer(serverPlayer, packetBuffer);
      }
   }

   /** [MILLDEBUG] Reads the leading Mill packet-id int without consuming the buffer's reader index. */
   private static int peekPacketId(FriendlyByteBuf packetBuffer) {
      try {
         return packetBuffer.getInt(0);
      } catch (Exception e) {
         return -1;
      }
   }

   public static void sendPacketToPlayersInRange(FriendlyByteBuf packetBuffer, Point p, int range) {
      // 1.12 used a Forge TargetPoint (overworld dim 0) + millChannel.sendToAllAround. We resolve the
      // overworld from the loaded Mill world; callers that know their level should use the Level overload.
      if (!org.millenaire.common.forge.Mill.serverWorlds.isEmpty()) {
         sendPacketToPlayersInRange(packetBuffer, org.millenaire.common.forge.Mill.serverWorlds.get(0).world, p, range);
      }
   }

   public static void sendPacketToPlayersInRange(FriendlyByteBuf packetBuffer, net.minecraft.world.level.Level level, Point p, int range) {
      // 26.2: Fabric's PlayerLookup.around(ServerLevel, Vec3, radius) replaces the Forge TargetPoint
      // broadcast; send the Mill payload to every player tracking within range.
      if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
         org.millenaire.common.network.MillNetworking.MillPayload payload =
            org.millenaire.common.network.MillNetworking.toPayload(packetBuffer);
         int sent = 0;
         for (net.minecraft.server.level.ServerPlayer player :
               net.fabricmc.fabric.api.networking.v1.PlayerLookup.around(serverLevel, new net.minecraft.world.phys.Vec3(p.x, p.y, p.z), range)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            sent++;
         }

         if (MillLog.debugOn()) {
            MillLog.milldebug("Packet", "SENT(range) id=" + peekPacketId(packetBuffer) + " around=" + p + " range=" + range + " recipients=" + sent);
         }
      }
   }

   public static void sendTranslatedSentence(Player player, char colour, String code, String... values) {
      if (player != null) {
         if (player instanceof ServerPlayer) {
            FriendlyByteBuf data = getPacketBuffer();
            data.writeInt(100);
            data.writeChar(colour);
            data.writeUtf(code);
            data.writeInt(values.length);

            for (String value : values) {
               StreamReadWrite.writeNullableString(value, data);
            }

            sendPacketToPlayer(data, player);
         }
      }
   }

   public static void sendTranslatedSentenceInRange(Level world, Point p, int range, char colour, String key, String... values) {
      for (Object oplayer : world.players()) {
         Player player = (Player)oplayer;
         if (p.distanceTo(player) < range) {
            sendTranslatedSentence(player, colour, key, values);
         }
      }
   }

   public static void sendVillagerSentence(ServerPlayer player, MillVillager v) {
      if (player != null) {
         if (player instanceof ServerPlayer) {
            FriendlyByteBuf data = getPacketBuffer();
            data.writeInt(108);
            data.writeLong(v.getVillagerId());
            sendPacketToPlayer(data, player);
         }
      }
   }

   public static void sendVillageSentenceInRange(Level world, Point p, int range, MillVillager v) {
      for (Object oplayer : world.players()) {
         ServerPlayer player = (ServerPlayer)oplayer;
         if (p.distanceTo(player) < range) {
            sendVillagerSentence(player, v);
         }
      }
   }
}
