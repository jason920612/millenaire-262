package org.millenaire.common.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.millenaire.common.forge.MillRegistry;

/**
 * Fabric networking foundation for Millénaire. The 1.12 mod used a single Forge FML
 * channel ({@code FMLEventChannel}/{@code FMLProxyPacket}) and dispatched by an int
 * packet-id written at the head of the buffer (see {@link ServerReceiver} PACKET_* ids).
 * We preserve that design with minimal change: a single {@link MillPayload} carrying the
 * raw buffer bytes (id + body), registered once for C2S and S2C. ServerSender builds a
 * {@link FriendlyByteBuf} (writing the int id then the body) and sends a MillPayload;
 * ServerReceiver reads the int id and runs the existing switch.
 *
 * <p>Wiring (call from MillenaireMod): {@link #registerPayloads()} (both sides) and
 * {@link #registerServerReceiver()} (server). Replaces the old {@code Mill.millChannel}.
 */
public final class MillNetworking {
   public static final CustomPacketPayload.Type<MillPayload> TYPE =
      new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "main"));

   private MillNetworking() {
   }

   /** Payload carrying the raw Millénaire packet bytes (leading int id + body). */
   public record MillPayload(byte[] data) implements CustomPacketPayload {
      public static final StreamCodec<ByteBuf, MillPayload> STREAM_CODEC =
         ByteBufCodecs.BYTE_ARRAY.map(MillPayload::new, MillPayload::data);

      @Override
      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** A fresh buffer to write a packet into; first write the int packet id, then the body. */
   public static FriendlyByteBuf newBuffer() {
      return new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
   }

   /** Wrap a written buffer into a payload (copies the readable bytes). */
   public static MillPayload toPayload(FriendlyByteBuf buf) {
      byte[] bytes = new byte[buf.readableBytes()];
      buf.getBytes(buf.readerIndex(), bytes);
      return new MillPayload(bytes);
   }

   /** Send a written buffer to a single player. */
   public static void sendToPlayer(ServerPlayer player, FriendlyByteBuf buf) {
      net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, toPayload(buf));
   }

   /** Register the payload type on both sides (call during mod init, common). */
   public static void registerPayloads() {
      net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(TYPE, MillPayload.STREAM_CODEC);
      net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(TYPE, MillPayload.STREAM_CODEC);
   }

   /** Register the server-side receiver that dispatches to ServerReceiver (call on server init). */
   public static void registerServerReceiver() {
      net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
         FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload.data()));
         context.server().execute(() -> ServerReceiver.handlePacket(context.player(), buf));
      });
   }
}
