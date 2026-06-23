package org.millenaire.common.forge;

import java.util.ArrayList;

import com.coderyo.jason.ops.TaskPointStore;
import org.millenaire.common.world.MillWorldData;

/**
 * Server tick logic. The 1.12 Forge {@code @SubscribeEvent TickEvent.ServerTickEvent} is
 * replaced by a Fabric {@code ServerTickEvents.END_SERVER_TICK} callback registered in
 * MillenaireMod; this class just exposes the per-tick work.
 */
public class ServerTickHandler {
   /** Sweep abandoned TaskPoint break records this often (ticks); cheap, so once a second is plenty. */
   private static final int DECAY_INTERVAL_TICKS = 20;
   private static long tickCounter;

   public static void onServerTick() {
      if (!Mill.startupError) {
         for (MillWorldData mw : new ArrayList<>(Mill.serverWorlds)) {
            mw.updateWorldServer();
            // Point-owned ore regrow: restore broken mine sources whose delay has elapsed (renewable mine, as 1.12).
            if (mw.world != null) {
               TaskPointStore.get().tickRegrow(mw.world, mw.world.getGameTime());
            }
         }

         // Periodically drop stale break-progress records from abandoned worksites so the store doesn't leak.
         if (++tickCounter % DECAY_INTERVAL_TICKS == 0) {
            TaskPointStore.get().decay();
         }
      }
   }
}
