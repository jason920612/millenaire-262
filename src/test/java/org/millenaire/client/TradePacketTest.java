package org.millenaire.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.millenaire.MillHeadlessTest;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

/**
 * Headless client test for the villager-trade packet — the bug class that broke selling. It does NOT
 * launch a client: {@link ClientSender#TEST_CAPTURE_ONLY} makes the sender record the packet bytes
 * instead of dispatching over a (nonexistent) connection, and Mockito stands in for the world-bound
 * Building/MillVillager. Each assertion mirrors {@code ServerReceiver.readTradePacket}'s read order.
 *
 * <p>Pins the fix for "Trade packet for unknown good key: generated": the good must be wired by item
 * id + meta (unique, survives the shop packet), NOT good.key (which the client rebuilds as the shared
 * placeholder "generated").
 */
class TradePacketTest extends MillHeadlessTest {

   @AfterEach
   void reset() {
      ClientSender.TEST_CAPTURE_ONLY = false;
      ClientSender.TEST_LAST_PACKET_BODY = null;
   }

   private static TradeGood good(Item item) {
      try {
         return new TradeGood("generated", null, InvItem.createInvItem(item, 0));
      } catch (Throwable t) {
         org.junit.jupiter.api.Assumptions.abort("InvItem needs full mod init (DataComponents/MillBlocks) not available headlessly: " + t);
         return null; // unreachable
      }
   }

   @Test
   void buildingShopSell_writesPacket200_62_identifiesGoodByItemNotKey() {
      ClientSender.TEST_CAPTURE_ONLY = true;
      Building building = mock(Building.class);
      when(building.getPos()).thenReturn(new Point(10, 64, 20));

      ClientSender.sendTrade(null, building, null, good(Items.OAK_LOG), true, false, 8);

      byte[] body = ClientSender.TEST_LAST_PACKET_BODY;
      assertNotNull(body, "trade packet should have been captured");
      assertEquals(200, ClientSender.TEST_LAST_PACKET_ID, "leading packet id");

      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(body));
      assertEquals(200, buf.readInt(), "packet id");
      assertEquals(62, buf.readInt(), "sub-id GUIACTION_TRADE_EXECUTE");
      assertFalse(buf.readBoolean(), "merchantTrade=false for a building shop");
      assertEquals(new Point(10, 64, 20), StreamReadWrite.readNullablePoint(buf), "building pos round-trips");
      assertEquals(BuiltInRegistries.ITEM.getId(Items.OAK_LOG), buf.readInt(), "good item id (not the key)");
      assertEquals(0, buf.readInt(), "good meta");
      assertTrue(buf.readBoolean(), "sellingSlot=true");
      assertEquals(8, buf.readInt(), "nbItems");
   }

   @Test
   void merchantTrade_writesVillagerIdInsteadOfPoint() {
      ClientSender.TEST_CAPTURE_ONLY = true;
      MillVillager merchant = mock(MillVillager.class);
      when(merchant.getVillagerId()).thenReturn(123456789L);

      ClientSender.sendTrade(null, null, merchant, good(Items.DIAMOND), false, true, 64);

      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(ClientSender.TEST_LAST_PACKET_BODY));
      assertEquals(200, buf.readInt());
      assertEquals(62, buf.readInt());
      assertTrue(buf.readBoolean(), "merchantTrade=true");
      assertEquals(123456789L, buf.readLong(), "merchant villager id");
      assertEquals(BuiltInRegistries.ITEM.getId(Items.DIAMOND), buf.readInt(), "good item id");
      assertEquals(0, buf.readInt(), "good meta");
      assertFalse(buf.readBoolean(), "sellingSlot=false (buying)");
      assertEquals(64, buf.readInt(), "nbItems");
   }
}
