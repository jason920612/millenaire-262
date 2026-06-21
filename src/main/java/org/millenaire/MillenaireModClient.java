package org.millenaire;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

import org.millenaire.client.network.ClientReceiver;
import org.millenaire.client.render.RenderMillVillager;
import org.millenaire.client.render.RenderWallDecoration;
import org.millenaire.common.entity.MillEntities;
import org.millenaire.client.render.MillModelLayers;
import org.millenaire.client.render.TESRFirePit;
import org.millenaire.client.render.TESRMockBanner;
import org.millenaire.client.render.TESRPanel;
import org.millenaire.client.render.TileEntityLockedChestRenderer;
import org.millenaire.client.render.TileEntityMillBedRenderer;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.entity.MillBlockEntities;
import org.millenaire.common.ui.MillMenus;

/**
 * Fabric client entrypoint. Mirrors the wiring the 1.12 {@code ClientProxy} did from FML client
 * lifecycle events (see {@code ClientProxy.registerForgeClientClasses} for the full checklist).
 *
 * <p>Wired here: menu {@code MenuType}s, model layers, block-entity renderers, entity renderers,
 * the client networking receiver, and the foliage colour provider (block tint). Still TODO:
 * {@code EntityRendererRegistry} for RenderMillVillager / RenderWallDecoration, and
 * {@code MenuScreens.register(...)} for the GUI screens + key bindings.
 */
@Environment(EnvType.CLIENT)
public class MillenaireModClient implements ClientModInitializer {

   @Override
   public void onInitializeClient() {
      // 0. Install the client proxy. 1.12 @SidedProxy gave the client a ClientProxy; without this
      // Mill.proxy stays the default CommonProxy whose getTheSinglePlayer()/getClientProfile() return
      // null, so any client GUI that reads the player's UserProfile (e.g. GuiNewVillage) NPEs every
      // frame and the screen appears frozen. On a dedicated server this initializer never runs, so the
      // server correctly keeps CommonProxy. ClientProxy extends CommonProxy and also serves the
      // integrated server in singleplayer.
      org.millenaire.common.forge.Mill.proxy = new org.millenaire.client.forge.ClientProxy();

      // 1. Menu types (must exist before screen registration / opening).
      MillMenus.register();

      // 2. Model layers (panel + villager body layers).
      MillModelLayers.register();

      // 3. Block-entity renderers.
      BlockEntityRenderers.register(MillBlockEntities.MILL_BED, TileEntityMillBedRenderer::new);
      BlockEntityRenderers.register(MillBlockEntities.LOCKED_CHEST, TileEntityLockedChestRenderer::new);
      BlockEntityRenderers.register(MillBlockEntities.PANEL, TESRPanel.FACTORY);
      BlockEntityRenderers.register(MillBlockEntities.FIRE_PIT, TESRFirePit.FACTORY);
      BlockEntityRenderers.register(MillBlockEntities.MOCK_BANNER, TESRMockBanner::new);

      // 4. Client networking receiver.
      ClientReceiver.registerClientReceiver();

      // 5. Entity renderers (villager subtypes share one renderer; wall decoration its own).
      EntityRendererRegistry.register(MillEntities.VILLAGER_MALE, RenderMillVillager.FACTORY_MALE);
      EntityRendererRegistry.register(MillEntities.VILLAGER_FEMALE_SYM, RenderMillVillager.FACTORY_FEMALE_SYM);
      EntityRendererRegistry.register(MillEntities.VILLAGER_FEMALE_ASYM, RenderMillVillager.FACTORY_FEMALE_ASYM);
      EntityRendererRegistry.register(MillEntities.WALL_DECORATION, RenderWallDecoration.FACTORY_WALL_DECORATION);

      // 6. Foliage colour for the pistachio leaves.
      // 26.2 replaced the runtime IBlockColor model with BlockTintSource-based factories; Fabric
      // exposes BlockColorRegistry.register(List<BlockTintSource>, Block...). BlockTintSources.foliage()
      // is the exact vanilla leaves source (in-hand = FoliageColor.FOLIAGE_DEFAULT, in-world =
      // BiomeColors.getAverageFoliageColor), matching the original 1.12 ClientProxy handler.
      // NOTE: there is no item-color runtime API on this Fabric version (1.12's IItemColor was
      // dropped); 26.2 item tinting is data-driven via the item model JSON's "tints" field. The
      // pistachio-leaves inventory-icon tint (1.12 used ColorizerFoliage.getFoliageColorBasic() =
      // -12012264) is now provided by a "minecraft:constant" tint entry in
      // assets/millenaire/items/leaves_pistachio.json (matching vanilla oak_leaves), so no runtime
      // item-color registration is needed.
      BlockColorRegistry.register(List.of(BlockTintSources.foliage()), MillBlocks.LEAVES_PISTACHIO);

      // 7. Amulet "score" model property. 1.12 drove the Vishnu/Alchemist/Yggdrasil amulet model swap
      // via an addPropertyOverride on Identifier("score"). 26.2 replaced item-property overrides with
      // data-driven minecraft:range_dispatch item-models reading a RangeSelectItemModelProperty. Since
      // the amulet score is a Mill-custom value, register a custom property (millenaire:amulet_score);
      // the per-amulet items/<amulet>.json range_dispatch picks the kind via "kind".
      org.millenaire.client.item.AmuletScoreProperty.register();

      // 7b. CLIENT self-test harness (mirror of the server-side MillSelfTest). Entirely out of the normal
      // code path unless launched with -Dmillenaire.clienttest=true (or MILLENAIRE_CLIENTTEST=1): it then
      // hooks the client tick loop, waits until the player is in a world, simulates Mill GUI operations
      // (open screens, trade-slot click → packet, paging, buttons, typing), logs a [MILLCLIENTTEST] report
      // and self-terminates the client. See org.millenaire.client.test.MillClientSelfTest.
      if (org.millenaire.client.test.MillClientSelfTest.isEnabled()) {
         org.millenaire.client.test.MillClientSelfTest.register();
      }

      // NOTE: Mill opens its container screens through its own PACKET_OPENGUI(104) flow
      // (ServerSender → ClientReceiver.readGUIPacket → ClientGuiHandler builds + shows the Screen),
      // not via vanilla MenuScreens.register, so no screen registration is needed here.

      // 8. Mill key bindings (menu / villages / escorts). 1.12 ClientRegistry.registerKeyBinding is
      // replaced by fabric-key-mapping-api; ClientProxy.registerKeyBindings creates + registers them
      // via KeyMappingHelper. Must run at client init (not server lifecycle) so they appear in Controls.
      org.millenaire.client.forge.ClientProxy.registerMillKeyBindings();

      // 9. Client-side MillWorldData (Mill.clientWorld). 1.12 created it on the client WorldEvent.Load;
      // without it ClientReceiver drops EVERY Mill packet ("Received a packet despite null clientWorld")
      // so no village / culture / building data ever reaches the client (empty village HUD + book). The
      // server side is wired in MillenaireMod (SERVER_STARTED → worldLoaded); this is its client mirror.
      final org.millenaire.common.forge.MillEventController clientEvents =
         new org.millenaire.common.forge.MillEventController();
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
         if (client.level != null) {
            clientEvents.worldLoaded(client.level);
         }
      });
      // The actual per-tick client update (1.12 ClientTickEvent → ClientTickHandler.tickStart): rebuilds
      // the client villager list so the villager-data packet can resolve villagers, ticks client-side
      // buildings, handles keys/panels. This was never wired, so client villagers never received their
      // type/texture and rendered invisible.
      final org.millenaire.client.forge.ClientTickHandler clientTickHandler =
         new org.millenaire.client.forge.ClientTickHandler();
      net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
         if (client.level != null) {
            if (org.millenaire.common.forge.Mill.clientWorld == null
               || org.millenaire.common.forge.Mill.clientWorld.world != client.level) {
               clientEvents.worldLoaded(client.level);
            }
            clientTickHandler.tickStart();
         } else if (org.millenaire.common.forge.Mill.clientWorld != null) {
            org.millenaire.common.forge.Mill.clientWorld = null;
         }
      });
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
         if (client.level != null) {
            clientEvents.worldUnloaded(client.level);
         } else {
            org.millenaire.common.forge.Mill.clientWorld = null;
         }
      });

      // 10. [MILLDEBUG] When the creative menu opens, log the display state of each Mill creative tab so
      // we can tell from the log (no client visibility) whether a tab is missing from the rendered tab
      // bar and why — registered (allTabs) vs displayed (tabs), shouldDisplay, and its row/column slot
      // (two tabs landing on the same row+column would hide one another).
      net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
         if (org.millenaire.common.utilities.MillLog.debugOn()
            && screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            java.util.List<net.minecraft.world.item.CreativeModeTab> displayed =
               net.minecraft.world.item.CreativeModeTabs.tabs();
            for (net.minecraft.world.item.CreativeModeTab tab : net.minecraft.world.item.CreativeModeTabs.allTabs()) {
               net.minecraft.resources.Identifier id =
                  net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
               if (id != null && "millenaire".equals(id.getNamespace())) {
                  org.millenaire.common.utilities.MillLog.milldebug("CreativeTab",
                     id + " displayed=" + displayed.contains(tab) + " shouldDisplay=" + tab.shouldDisplay()
                        + " row=" + tab.row() + " column=" + tab.column()
                        + " name=" + tab.getDisplayName().getString());
               }
            }
         }
      });
   }
}
