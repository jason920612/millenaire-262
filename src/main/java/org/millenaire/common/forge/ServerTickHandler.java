package org.millenaire.common.forge;

import java.util.ArrayList;

import org.millenaire.common.world.MillWorldData;

/**
 * Server tick logic. The 1.12 Forge {@code @SubscribeEvent TickEvent.ServerTickEvent} is
 * replaced by a Fabric {@code ServerTickEvents.END_SERVER_TICK} callback registered in
 * MillenaireMod; this class just exposes the per-tick work.
 */
public class ServerTickHandler {
   public static void onServerTick() {
      if (!Mill.startupError) {
         for (MillWorldData mw : new ArrayList<>(Mill.serverWorlds)) {
            mw.updateWorldServer();
         }
      }
   }
}
