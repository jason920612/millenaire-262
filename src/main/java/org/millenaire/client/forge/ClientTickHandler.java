package org.millenaire.client.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.millenaire.client.gui.DisplayActions;
import org.millenaire.common.forge.Mill;

/**
 * Client tick handler. 1.12 Forge {@code @SubscribeEvent ClientTickEvent} is gone; on Fabric this
 * is wired from the client entrypoint via {@code ClientTickEvents.START_CLIENT_TICK.register(mc ->
 * new ClientTickHandler instance::tickStart)} (the instance is held by the client init).
 */
@Environment(EnvType.CLIENT)
public class ClientTickHandler {
   private boolean startupMessageShow;

   public void tickStart() {
      if (Mill.clientWorld != null && Mill.clientWorld.millenaireEnabled && Minecraft.getInstance().player != null) {
         boolean inOverworld = Minecraft.getInstance().player.level().dimension() == Level.OVERWORLD;
         Mill.clientWorld.updateWorldClient(inOverworld);
         if (!this.startupMessageShow) {
            DisplayActions.displayStartupOrError(Minecraft.getInstance().player, Mill.startupError);
            this.startupMessageShow = true;
         }

         Mill.proxy.handleClientGameUpdate();
      }
   }
}
