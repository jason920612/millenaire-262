package org.millenaire.common.forge;

import java.io.File;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;

import org.millenaire.common.network.ServerReceiver;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.world.UserProfile;

/**
 * Server-side proxy. On Fabric there is no Forge SidedProxy/IGuiHandler/RegistryEvent;
 * registration is done directly in MillenaireMod#onInitialize, so the old
 * registerBlocks/registerItems(Register&lt;…&gt;) handlers and createGuiHandler() are removed.
 * The remaining getters/hooks (logFile, configFile, logPrefix, …) are still referenced via
 * {@code Mill.proxy} across the mod, so this stays as a concrete server proxy; the client
 * subclass overrides the client-only behaviour.
 */
public class CommonProxy {

   public String getBlockName(Block block, int meta) {
      return null;
   }

   public UserProfile getClientProfile() {
      return null;
   }

   public File getConfigFile() {
      return new File(MillCommonUtilities.getMillenaireContentDir(), "config-server.txt");
   }

   public File getCustomConfigFile() {
      return new File(MillCommonUtilities.getMillenaireCustomContentDir(), "config-server-custom.txt");
   }

   public String getItemName(Item item, int meta) {
      return "";
   }

   public String getKeyString(int value) {
      return "";
   }

   public File getLogFile() {
      return new File(MillCommonUtilities.getMillenaireCustomContentDir(), "millenaire-server.log");
   }

   public String getQuestKeyName() {
      return "";
   }

   public String getSinglePlayerName() {
      return null;
   }

   public Player getTheSinglePlayer() {
      return null;
   }

   public void handleClientGameUpdate() {
   }

   public void handleClientLogin() {
   }

   public void initNetwork() {
   }

   public boolean isTrueServer() {
      return true;
   }

   public int loadKeySetting(String value) {
      return 0;
   }

   public void loadLanguagesIfNeeded() {
      LanguageUtilities.loadLanguages(null);
   }

   public void localTranslatedSentence(Player player, char colour, String code, String... values) {
   }

   public String logPrefix() {
      return "SRV ";
   }

   public void refreshClientResources() {
   }

   public void registerForgeClientClasses() {
   }

   public void registerKeyBindings() {
   }

   public void sendChatAdmin(String s) {
   }

   public void sendChatAdmin(String s, ChatFormatting colour) {
   }

   public void sendLocalChat(Player player, char colour, String s) {
   }

   public void setGraphicsLevel(LeavesBlock blockLeaves, boolean value) {
   }
}
