package org.millenaire.client.forge;

import java.io.File;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.millenaire.client.MillClientUtilities;
import org.millenaire.common.forge.CommonProxy;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillFiles;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.world.UserProfile;

/**
 * Client-side proxy. Mirrors the (already-ported) server {@link CommonProxy}, overriding the
 * client-only behaviour.
 *
 * <p>26.2: the Forge client registration ({@code ModelRegistryEvent}, {@code RenderingRegistry},
 * {@code ClientRegistry}, {@code FMLClientHandler}) is gone and is now done in the client entrypoint
 * {@code MillenaireModClient.onInitializeClient}: entity renderers via {@code EntityRendererRegistry},
 * block-entity renderers via {@code BlockEntityRenderers}, key bindings via the fabric-key-mapping-api
 * ({@link #registerMillKeyBindings()}), and foliage colour via {@code BlockColorRegistry}. The methods
 * below keep the proxy API shape the rest of Millénaire calls (most are now thin legacy shells).
 */
@Environment(EnvType.CLIENT)
public class ClientProxy extends CommonProxy {
   public static KeyMapping KB_MENU;
   public static KeyMapping KB_VILLAGES;
   public static KeyMapping KB_ESCORTS;

   public static final KeyMapping.Category KEY_CATEGORY =
      KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Mill.MODID, "millenaire"));

   @Override
   public String getBlockName(Block block, int meta) {
      if (block == null) {
         // FAIL-FAST: a null block to name is a caller bug; returning a null name (1.12 behaviour) just
         // moves the NPE downstream into tooltip/GUI rendering. Surface it at the source.
         throw MillCrash.fail("Client", "getBlockName called with null block");
      } else {
         return new ItemStack(block).getHoverName().getString();
      }
   }

   @Override
   public UserProfile getClientProfile() {
      if (Mill.proxy.getTheSinglePlayer() == null) {
         return null;
      } else if (Mill.clientWorld.profiles.containsKey(Mill.proxy.getTheSinglePlayer().getUUID())) {
         return Mill.clientWorld.profiles.get(Mill.proxy.getTheSinglePlayer().getUUID());
      } else {
         UserProfile profile = new UserProfile(Mill.clientWorld, Mill.proxy.getTheSinglePlayer());
         Mill.clientWorld.profiles.put(profile.uuid, profile);
         return profile;
      }
   }

   @Override
   public File getConfigFile() {
      return new File(MillFiles.getMillenaireContentDir(), "config.txt");
   }

   @Override
   public File getCustomConfigFile() {
      return new File(MillFiles.getMillenaireCustomContentDir(), "config-custom.txt");
   }

   @Override
   public String getItemName(Item item, int meta) {
      if (item == null) {
         // FAIL-FAST: a null item to name is a caller bug; returning a null name (1.12 behaviour) just
         // moves the NPE downstream into tooltip/GUI rendering. Surface it at the source.
         throw MillCrash.fail("Client", "getItemName called with null item");
      } else {
         return new ItemStack(item).getHoverName().getString();
      }
   }

   @Override
   public String getKeyString(int value) {
      // 1.12 used lwjgl2 Keyboard.getKeyName(value). On 26.2 the GLFW key value resolves to a display
      // name via InputConstants.Type.KEYSYM (the keysym map), reproducing the original key-name lookup.
      return com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(value).getDisplayName().getString();
   }

   @Override
   public File getLogFile() {
      return new File(MillFiles.getMillenaireCustomContentDir(), "millenaire.log");
   }

   @Override
   public String getQuestKeyName() {
      // 1.12 returned Keyboard.getKeyName(KB_MENU.getKeyCode()). On 26.2 KeyMapping exposes the bound
      // key's already-translated display name directly via getTranslatedKeyMessage().
      return KB_MENU != null ? KB_MENU.getTranslatedKeyMessage().getString() : "key.menu";
   }

   @Override
   public String getSinglePlayerName() {
      return this.getTheSinglePlayer() != null ? this.getTheSinglePlayer().getName().getString() : "NULL_PLAYER";
   }

   @Override
   public Player getTheSinglePlayer() {
      return Minecraft.getInstance().player;
   }

   @Override
   public void handleClientGameUpdate() {
      MillClientUtilities.handleKeyPress(Mill.clientWorld.world);
      if (Mill.clientWorld.world.getGameTime() % 20L == 0L) {
         Mill.clientWorld.clearPanelQueue();
      }

      this.loadLanguagesIfNeeded();
   }

   @Override
   public void handleClientLogin() {
      org.millenaire.client.network.ClientSender.sendVersionInfo();
      org.millenaire.client.network.ClientSender.sendAvailableContent();
   }

   @Override
   public void initNetwork() {
      // 26.2: the 1.12 Mill.millChannel.register(client receiver) is replaced by Fabric
      // ClientPlayNetworking, wired from MillenaireModClient.onInitializeClient via
      // ClientReceiver.registerClientReceiver(); nothing to do on this legacy proxy hook.
   }

   @Override
   public boolean isTrueServer() {
      return false;
   }

   @Override
   public int loadKeySetting(String value) {
      // 1.12 used lwjgl2 Keyboard.getKeyIndex(name.toUpperCase()). On 26.2 InputConstants.getKey resolves
      // the "key.keyboard.<name>" translation key to an InputConstants.Key whose getValue() is the GLFW
      // key code. Returns -1 (unknown) on an unrecognised name, matching the original failure value.
      if (value == null || value.isEmpty()) {
         return -1;
      }
      try {
         return com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard." + value.toLowerCase(java.util.Locale.ROOT)).getValue();
      } catch (Exception e) {
         return -1;
      }
   }

   @Override
   public void loadLanguagesIfNeeded() {
      Minecraft minecraft = Minecraft.getInstance();
      LanguageUtilities.loadLanguages(minecraft.options.languageCode);
   }

   @Override
   public void localTranslatedSentence(Player player, char colour, String code, String... values) {
      for (int i = 0; i < values.length; i++) {
         values[i] = LanguageUtilities.unknownString(values[i]);
      }

      this.sendLocalChat(player, colour, LanguageUtilities.string(code, values));
   }

   @Override
   public String logPrefix() {
      return "CLIENT ";
   }

   @Override
   public void refreshClientResources() {
      Minecraft.getInstance().reloadResourcePacks();
   }

   @Override
   public void registerForgeClientClasses() {
      // 26.2: the 1.12 Forge client-class registration that lived here is done from the client entrypoint
      // MillenaireModClient.onInitializeClient instead (Fabric registration must happen there, not via the
      // proxy at server-lifecycle time): MillMenus.register(), MillModelLayers.register(), the block-entity
      // renderers (BlockEntityRenderers.register), the entity renderers (EntityRendererRegistry.register),
      // ClientReceiver.registerClientReceiver(), ClientTickEvents → handleClientGameUpdate, the foliage
      // BlockColorRegistry tint, AmuletScoreProperty, and the key bindings (registerMillKeyBindings). This
      // legacy proxy hook is therefore a no-op.
   }

   @Override
   public void registerKeyBindings() {
      registerMillKeyBindings();
   }

   /**
    * Creates and registers the Mill key bindings (menu / villages / escorts) via the
    * fabric-key-mapping-api so they appear in Controls, honour user rebinding and respond to isDown().
    * 1.12 ClientRegistry.registerKeyBinding is gone; default GLFW keysyms match the 1.12 LWJGL key
    * codes (M / V / G). Must run at client init; idempotent (registers at most once).
    */
   public static void registerMillKeyBindings() {
      if (KB_MENU == null) {
         KB_MENU = registerKeyBinding(new KeyMapping("key.menu", org.lwjgl.glfw.GLFW.GLFW_KEY_M, KEY_CATEGORY));
         KB_VILLAGES = registerKeyBinding(new KeyMapping("key.villages", org.lwjgl.glfw.GLFW.GLFW_KEY_V, KEY_CATEGORY));
         KB_ESCORTS = registerKeyBinding(new KeyMapping("key.escorts", org.lwjgl.glfw.GLFW.GLFW_KEY_G, KEY_CATEGORY));
      }
   }

   private static KeyMapping registerKeyBinding(KeyMapping mapping) {
      return net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
   }

   @Override
   public void sendChatAdmin(String s) {
      s = s.trim();
      Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(s));
   }

   @Override
   public void sendChatAdmin(String s, ChatFormatting colour) {
      s = s.trim();
      MutableComponent cc = Component.literal(s).withStyle(colour);
      Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(cc);
   }

   @Override
   public void sendLocalChat(Player player, char colour, String s) {
      s = s.trim();
      // The section sign here was corrupted to a CJK char ("禮") during the 1.12 decompile/port, so every
      // Mill chat line rendered "禮9..." instead of applying the §9 colour code. Use the ASCII unicode escape.
      Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("§" + colour + s));
   }

   @Override
   public void setGraphicsLevel(LeavesBlock blockLeaves, boolean value) {
      // 26.2: LeavesBlock.setGraphicsLevel is gone — leaf fancy/fast transparency is no longer a per-block
      // flag but is driven by the graphics setting and the block's render type (cutout_mipped), so there is
      // nothing to toggle here. No-op.
   }
}
