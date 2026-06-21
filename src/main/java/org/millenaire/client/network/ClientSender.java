package org.millenaire.client.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.FriendlyByteBuf;
import org.millenaire.common.network.MillNetworking;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.ui.GuiActions;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.BuildingProject;

@Environment(EnvType.CLIENT)
public class ClientSender {
   /**
    * Test-only observability counter. Incremented every time a Mill client→server packet is dispatched
    * through {@link #createAndSendServerPacket}. The automated client self-test
    * ({@code org.millenaire.client.test.MillClientSelfTest}) reads this to verify that simulated GUI
    * clicks actually produce the expected outbound packet (e.g. the trade-slot click → trade packet).
    * It is a single {@code volatile} increment with zero effect on normal gameplay.
    */
   public static volatile long TEST_PACKETS_SENT = 0L;
   /** Test-only: the int packet id (first int written) of the most recently dispatched Mill packet, or -1. */
   public static volatile int TEST_LAST_PACKET_ID = -1;
   /**
    * Test-only: a copy of the full readable bytes (leading int id + body) of the most recently dispatched
    * Mill packet, or {@code null}. Lets the headless suite decode the exact payload the trade-slot click
    * built and cross-check the write order against {@link org.millenaire.common.network.ServerReceiver}'s
    * read order — the mismatch class that broke selling. Captured BEFORE the actual network send.
    */
   public static volatile byte[] TEST_LAST_PACKET_BODY = null;
   /**
    * Test-only switch. When true, {@link #createAndSendServerPacket} records the packet (id + body) and
    * returns WITHOUT calling {@link ClientPlayNetworking#send}, which requires a live client connection and
    * is unavailable in a headless JUnit JVM. Zero effect on normal gameplay (stays false in the client).
    */
   public static volatile boolean TEST_CAPTURE_ONLY = false;

   /** A fresh packet buffer; write the int packet id first, then the body (Mill's dispatch scheme). */
   public static FriendlyByteBuf getPacketBuffer() {
      return new FriendlyByteBuf(Unpooled.buffer());
   }

   /**
    * Send a written Mill buffer to the server.
    *
    * <p>26.2 PORT NOTE: the 1.12 {@code FMLProxyPacket} + {@code Mill.millChannel.sendToServer} flow
    * is replaced by Fabric {@link ClientPlayNetworking#send} of a {@link MillNetworking.MillPayload}.
    */
   public static void createAndSendServerPacket(FriendlyByteBuf data) {
      // Test observability: record that an outbound Mill packet was produced (and its leading id). The
      // readerIndex(0) peek is non-destructive — it is restored before the payload is built/sent.
      TEST_PACKETS_SENT++;
      try {
         if (data.readableBytes() >= 4) {
            int reader = data.readerIndex();
            TEST_LAST_PACKET_ID = data.getInt(reader);
         }
         byte[] body = new byte[data.readableBytes()];
         data.getBytes(data.readerIndex(), body);
         TEST_LAST_PACKET_BODY = body;
      } catch (Throwable ignored) {
         // Never let the test probe affect real packet dispatch.
      }
      // Headless test mode: the probe above has recorded the packet; skip the live-connection send.
      if (TEST_CAPTURE_ONLY) {
         return;
      }
      ClientPlayNetworking.send(MillNetworking.toPayload(data));
   }

   public static void activateMillChest(Player player, Point pos) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(81);
      StreamReadWrite.writeNullablePoint(pos, data);
      createAndSendServerPacket(data);
   }

   public static void controlledBuildingsForgetBuilding(Player player, Building townHall, BuildingProject project) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(71);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      data.writeUtf(project.key);
      StreamReadWrite.writeNullablePoint(project.location.pos, data);
      createAndSendServerPacket(data);
      GuiActions.controlledBuildingsForgetBuilding(player, townHall, project);
   }

   public static void controlledBuildingsToggleUpgrades(Player player, Building townHall, BuildingProject project, boolean allow) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(70);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      data.writeUtf(project.key);
      StreamReadWrite.writeNullablePoint(project.location.pos, data);
      data.writeBoolean(allow);
      createAndSendServerPacket(data);
      GuiActions.controlledBuildingsToggleUpgrades(player, townHall, project, allow);
   }

   public static void controlledMilitaryCancelRaid(Player player, Building th) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(92);
      StreamReadWrite.writeNullablePoint(th.getPos(), data);
      createAndSendServerPacket(data);
      GuiActions.controlledMilitaryCancelRaid(player, th);
   }

   public static void controlledMilitaryDiplomacy(Player player, Building th, Point target, int amount) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(90);
      StreamReadWrite.writeNullablePoint(th.getPos(), data);
      StreamReadWrite.writeNullablePoint(target, data);
      data.writeInt(amount);
      createAndSendServerPacket(data);
      GuiActions.controlledMilitaryDiplomacy(player, th, target, amount);
   }

   public static void controlledMilitaryPlanRaid(Player player, Building th, Point target) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(91);
      StreamReadWrite.writeNullablePoint(th.getPos(), data);
      StreamReadWrite.writeNullablePoint(target, data);
      createAndSendServerPacket(data);
      GuiActions.controlledMilitaryPlanRaid(player, th, th.mw.getBuilding(target));
   }

   public static void devCommand(int devcommand) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(206);
      data.writeInt(devcommand);
      createAndSendServerPacket(data);
   }

   public static void displayVillageList(boolean loneBuildings) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(201);
      data.writeBoolean(loneBuildings);
      createAndSendServerPacket(data);
   }

   public static void hireExtend(Player player, MillVillager villager) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(31);
      data.writeLong(villager.getVillagerId());
      createAndSendServerPacket(data);
      GuiActions.hireExtend(player, villager);
   }

   public static void hireHire(Player player, MillVillager villager) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(30);
      data.writeLong(villager.getVillagerId());
      createAndSendServerPacket(data);
      GuiActions.hireHire(player, villager);
   }

   public static void hireRelease(Player player, MillVillager villager) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(32);
      data.writeLong(villager.getVillagerId());
      createAndSendServerPacket(data);
      GuiActions.hireRelease(player, villager);
   }

   public static void hireToggleStance(Player player, boolean stance) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(33);
      data.writeBoolean(stance);
      createAndSendServerPacket(data);
   }

   public static void importTableCreateNewBuilding(Point tablePos, int length, int width, int startLevel, boolean clearGround) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(102);
      StreamReadWrite.writeNullablePoint(tablePos, data);
      data.writeInt(length);
      data.writeInt(width);
      data.writeInt(startLevel);
      data.writeBoolean(clearGround);
      createAndSendServerPacket(data);
   }

   public static void importTableImportBuildingPlan(
      Player player,
      Point tablePos,
      String source,
      String buildingKey,
      boolean importAll,
      int variation,
      int level,
      int orientation,
      boolean importMockBlocks
   ) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(100);
      StreamReadWrite.writeNullablePoint(tablePos, data);
      StreamReadWrite.writeNullableString(source, data);
      StreamReadWrite.writeNullableString(buildingKey, data);
      data.writeBoolean(importAll);
      data.writeInt(variation);
      data.writeInt(level);
      data.writeInt(orientation);
      data.writeBoolean(importMockBlocks);
      createAndSendServerPacket(data);
   }

   public static void importTableUpdateSettings(
      Point tablePos,
      int upgradeLevel,
      int orientation,
      int startingLevel,
      boolean exportSnow,
      boolean importMockBlocks,
      boolean autoconvertToPreserveGround,
      boolean exportRegularChests
   ) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(101);
      StreamReadWrite.writeNullablePoint(tablePos, data);
      data.writeInt(upgradeLevel);
      data.writeInt(orientation);
      data.writeInt(startingLevel);
      data.writeBoolean(exportSnow);
      data.writeBoolean(importMockBlocks);
      data.writeBoolean(autoconvertToPreserveGround);
      data.writeBoolean(exportRegularChests);
      createAndSendServerPacket(data);
   }

   public static void negationWand(Player player, Building townHall) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(40);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      createAndSendServerPacket(data);
   }

   public static void newBuilding(Player player, Building townHall, Point pos, String planKey) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(50);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      StreamReadWrite.writeNullablePoint(pos, data);
      data.writeUtf(planKey);
      createAndSendServerPacket(data);
   }

   public static void newCustomBuilding(Player player, Building townHall, Point pos, String planKey) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(51);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      StreamReadWrite.writeNullablePoint(pos, data);
      data.writeUtf(planKey);
      createAndSendServerPacket(data);
   }

   public static void newVillageCreation(Player player, Point pos, String cultureKey, String villageTypeKey) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(20);
      data.writeUtf(cultureKey);
      data.writeUtf(villageTypeKey);
      StreamReadWrite.writeNullablePoint(pos, data);
      createAndSendServerPacket(data);
   }

   public static void playerToggleDonation(Player player, boolean donation) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(61);
      data.writeBoolean(donation);
      createAndSendServerPacket(data);
   }

   /**
    * Run a trade transaction on the SERVER (packet 200 / sub-id 62). Mill builds {@code ContainerTrade}
    * client-only, so the buy/sell logic in {@link org.millenaire.common.ui.ContainerTrade#executeTrade}
    * must execute server-side. We identify the shop by either the building {@link Point} (village shop)
    * or the merchant villager id (foreign merchant), plus the {@link TradeGood} unique key.
    */
   public static void sendTrade(
      Player player, Building building, MillVillager merchant, TradeGood good, boolean sellingSlot, boolean merchantTrade, int nbItems
   ) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(62);
      data.writeBoolean(merchantTrade);
      if (merchantTrade) {
         data.writeLong(merchant.getVillagerId());
      } else {
         StreamReadWrite.writeNullablePoint(building.getPos(), data);
      }

      // Identify the good by item id + meta, NOT good.key: auto-generated goods all share the
      // placeholder key "generated" (set in StreamReadWrite.readNullableGoods when the client rebuilds
      // the shop), so the key cannot pick out the clicked good on the server -> "unknown good key:
      // generated". The item+meta IS preserved across the shop packet and uniquely identifies the good.
      data.writeInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(good.item.getItem()));
      data.writeInt(good.item.meta);
      data.writeBoolean(sellingSlot);
      data.writeInt(nbItems);
      createAndSendServerPacket(data);
   }

   public static void pujasChangeEnchantment(Player player, Building temple, int enchantmentId) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(60);
      StreamReadWrite.writeNullablePoint(temple.getPos(), data);
      data.writeShort(enchantmentId);
      createAndSendServerPacket(data);
   }

   public static void questCompleteStep(Player player, MillVillager villager) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(10);
      data.writeLong(villager.getVillagerId());
      createAndSendServerPacket(data);
   }

   public static void questRefuse(Player player, MillVillager villager) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(11);
      data.writeLong(villager.getVillagerId());
      createAndSendServerPacket(data);
   }

   public static void requestMapInfo(Building townHall) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(203);
      StreamReadWrite.writeNullablePoint(townHall.getPos(), data);
      createAndSendServerPacket(data);
   }

   public static void sendAvailableContent() {
      FriendlyByteBuf data = getPacketBuffer();

      try {
         data.writeInt(205);
         data.writeUtf(MillConfigValues.effective_language);
         data.writeUtf(MillConfigValues.fallback_language);
         data.writeShort(Culture.ListCultures.size());

         for (Culture culture : Culture.ListCultures) {
            culture.writeCultureAvailableContentPacket(data);
         }
      } catch (Exception var3) {
         MillLog.printException("Error in displayVillageList", var3);
      }

      createAndSendServerPacket(data);
   }

   public static void sendVersionInfo() {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(202);
      data.writeUtf("8.1.2");
      createAndSendServerPacket(data);
   }

   public static void updateCustomBuilding(Player player, Building building) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(52);
      StreamReadWrite.writeNullablePoint(building.getPos(), data);
      createAndSendServerPacket(data);
   }

   public static void villageChiefPerformBuilding(Player player, MillVillager chief, String planKey) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(1);
      data.writeLong(chief.getVillagerId());
      data.writeUtf(planKey);
      createAndSendServerPacket(data);
   }

   public static void villageChiefPerformCrop(Player player, MillVillager chief, String value) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(2);
      data.writeLong(chief.getVillagerId());
      data.writeUtf(value);
      createAndSendServerPacket(data);
   }

   public static void villageChiefPerformCultureControl(Player player, MillVillager chief) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(3);
      data.writeLong(chief.getVillagerId());
      createAndSendServerPacket(data);
   }

   public static void villageChiefPerformDiplomacy(Player player, MillVillager chief, Point village, boolean praise) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(4);
      data.writeLong(chief.getVillagerId());
      StreamReadWrite.writeNullablePoint(village, data);
      data.writeBoolean(praise);
      createAndSendServerPacket(data);
      GuiActions.villageChiefPerformDiplomacy(player, chief, village, praise);
   }

   public static void villageChiefPerformHuntingDrop(Player player, MillVillager chief, String value) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(6);
      data.writeLong(chief.getVillagerId());
      data.writeUtf(value);
      createAndSendServerPacket(data);
   }

   public static void villageChiefPerformVillageScroll(Player player, MillVillager chief) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(200);
      data.writeInt(5);
      data.writeLong(chief.getVillagerId());
      createAndSendServerPacket(data);
   }

   public static void villagerInteractSpecial(Player player, MillVillager villager) {
      FriendlyByteBuf data = getPacketBuffer();
      data.writeInt(204);
      data.writeLong(villager.getVillagerId());
      createAndSendServerPacket(data);
   }
}
