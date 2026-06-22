package org.millenaire.common.utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.millenaire.common.advancements.GenericAdvancement;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.world.UserProfile;

public class MillCommonUtilities {
   private static final String MILLENAIRE_ORG_ROOT = "http://millenaire.org";
   public static Random random = new Random();
   private static File baseDir = null;
   private static File customDir = null;

   public static boolean chanceOn(int i) {
      return getRandom().nextInt(i) == 0;
   }

   public static void changeMoney(Container chest, int toChange, Player player) {
      boolean hasPurse = false;

      for (int i = 0; i < chest.getContainerSize() && !hasPurse; i++) {
         ItemStack stack = chest.getItem(i);
         if (stack != null && stack.getItem() == MillItems.PURSE) {
            hasPurse = true;
         }
      }

      if (hasPurse) {
         int current_denier = WorldUtilities.getItemsFromChest(chest, MillItems.DENIER, 0, Integer.MAX_VALUE);
         int current_DENIER_ARGENT = WorldUtilities.getItemsFromChest(chest, MillItems.DENIER_ARGENT, 0, Integer.MAX_VALUE);
         int current_DENIER_OR = WorldUtilities.getItemsFromChest(chest, MillItems.DENIER_OR, 0, Integer.MAX_VALUE);
         int finalChange = current_DENIER_OR * 64 * 64 + current_DENIER_ARGENT * 64 + current_denier + toChange;

         for (int ix = 0; ix < chest.getContainerSize() && finalChange != 0; ix++) {
            ItemStack stack = chest.getItem(ix);
            if (stack != null && stack.getItem() == MillItems.PURSE) {
               int content = MillItems.PURSE.totalDeniers(stack) + finalChange;
               if (content >= 0) {
                  MillItems.PURSE.setDeniers(stack, player, content);
                  finalChange = 0;
               } else {
                  MillItems.PURSE.setDeniers(stack, player, 0);
                  finalChange = content;
               }
            }
         }
      } else {
         int total = toChange + countMoney(chest);
         int denier = total % 64;
         int DENIER_ARGENT = (total - denier) / 64 % 64;
         int DENIER_OR = (total - denier - DENIER_ARGENT * 64) / 4096;
         if (player != null && DENIER_OR > 0) {
            MillAdvancements.CRESUS.grant(player);
         }

         int current_denier = countChestItems(chest, MillItems.DENIER, 0);
         int current_DENIER_ARGENT = countChestItems(chest, MillItems.DENIER_ARGENT, 0);
         int current_DENIER_OR = countChestItems(chest, MillItems.DENIER_OR, 0);
         if (MillConfigValues.LogWifeAI >= 1) {
            MillLog.major(
               null,
               "Putting: "
                  + denier
                  + "/"
                  + DENIER_ARGENT
                  + "/"
                  + DENIER_OR
                  + " replacing "
                  + current_denier
                  + "/"
                  + current_DENIER_ARGENT
                  + "/"
                  + current_DENIER_OR
            );
         }

         if (denier < current_denier) {
            WorldUtilities.getItemsFromChest(chest, MillItems.DENIER, 0, current_denier - denier);
         } else if (denier > current_denier) {
            putItemsInChest(chest, MillItems.DENIER, 0, denier - current_denier);
         }

         if (DENIER_ARGENT < current_DENIER_ARGENT) {
            WorldUtilities.getItemsFromChest(chest, MillItems.DENIER_ARGENT, 0, current_DENIER_ARGENT - DENIER_ARGENT);
         } else if (DENIER_ARGENT > current_DENIER_ARGENT) {
            putItemsInChest(chest, MillItems.DENIER_ARGENT, 0, DENIER_ARGENT - current_DENIER_ARGENT);
         }

         if (DENIER_OR < current_DENIER_OR) {
            WorldUtilities.getItemsFromChest(chest, MillItems.DENIER_OR, 0, current_DENIER_OR - DENIER_OR);
         } else if (DENIER_OR > current_DENIER_OR) {
            putItemsInChest(chest, MillItems.DENIER_OR, 0, DENIER_OR - current_DENIER_OR);
         }
      }
   }

   public static int countChestItems(Container chest, Block block, int meta) {
      return countChestItems(chest, block.asItem(), meta);
   }

   public static int countChestItems(Container chest, BlockState blockState) {
      return countChestItems(chest, blockState.getBlock(), 0);
   }

   public static int countChestItems(Container chest, Item item, int meta) {
      if (chest == null) {
         return 0;
      } else {
         int maxSlot = chest.getContainerSize();
         if (chest instanceof Inventory) {
            maxSlot -= 5;
         }

         int nb = 0;

         for (int i = 0; i < maxSlot; i++) {
            ItemStack stack = chest.getItem(i);
            if (stack != null && stack.getItem() == item) {
               nb += stack.getCount();
            }

            if (item == Blocks.OAK_LOG.asItem()
               && meta == -1
               && stack != null
               && stack.getItem() == Blocks.ACACIA_LOG.asItem()) {
               nb += stack.getCount();
            }
         }

         return nb;
      }
   }

   public static int countFurnaceItems(Container furnace, Item item, int meta) {
      if (furnace == null) {
         return 0;
      } else {
         int nb = 0;
         ItemStack stack = furnace.getItem(2);
         if (stack != null && stack.getItem() == item) {
            nb += stack.getCount();
         }

         if (item == Blocks.OAK_LOG.asItem()
            && meta == -1
            && stack != null
            && stack.getItem() == Blocks.ACACIA_LOG.asItem()) {
            nb += stack.getCount();
         }

         return nb;
      }
   }

   public static int countMoney(Container chest) {
      int deniers = 0;

      for (int i = 0; i < chest.getContainerSize(); i++) {
         ItemStack stack = chest.getItem(i);
         if (stack != null) {
            if (stack.getItem() == MillItems.PURSE) {
               deniers += MillItems.PURSE.totalDeniers(stack);
            } else if (stack.getItem() == MillItems.DENIER) {
               deniers += stack.getCount();
            } else if (stack.getItem() == MillItems.DENIER_ARGENT) {
               deniers += stack.getCount() * 64;
            } else if (stack.getItem() == MillItems.DENIER_OR) {
               deniers += stack.getCount() * 64 * 64;
            }
         }
      }

      return deniers;
   }

   public static boolean deleteDir(File dir) {
      if (dir.isDirectory()) {
         String[] children = dir.list();

         for (int i = 0; i < children.length; i++) {
            boolean success = deleteDir(new File(dir, children[i]));
            if (!success) {
               return false;
            }
         }
      }

      return dir.delete();
   }

   public static String flattenStrings(Collection<String> strings) {
      return strings.stream().collect(Collectors.joining(", "));
   }

   public static void generateHearts(Entity ent) {
      float width = ent.getBbWidth();
      float height = ent.getBbHeight();
      for (int var3 = 0; var3 < 7; var3++) {
         double var4 = random.nextGaussian() * 0.02;
         double var6 = random.nextGaussian() * 0.02;
         double var8 = random.nextGaussian() * 0.02;
         ent.level()
            .addParticle(
               ParticleTypes.HEART,
               ent.getX() + random.nextFloat() * width * 2.0F - width,
               ent.getY() + 0.5 + random.nextFloat() * height,
               ent.getZ() + random.nextFloat() * width * 2.0F - width,
               var4,
               var6,
               var8
            );
      }
   }

   public static BufferedWriter getAppendWriter(File file) throws UnsupportedEncodingException, FileNotFoundException {
      FileOutputStream fos = new FileOutputStream(file, true);

      try {
         return new BufferedWriter(new OutputStreamWriter(fos, "UTF8"));
      } catch (UnsupportedEncodingException | RuntimeException var3) {
         try {
            fos.close();
         } catch (IOException var2) {
         }

         throw var3;
      }
   }

   public static File getBuildingsDir(Level world) {
      File saveDir = getWorldSaveDir(world);
      File millenaireDir = new File(saveDir, "millenaire");
      if (!millenaireDir.exists()) {
         millenaireDir.mkdir();
      }

      File buildingsDir = new File(millenaireDir, "buildings");
      if (!buildingsDir.exists()) {
         buildingsDir.mkdir();
      }

      return buildingsDir;
   }

   public static String getCardinalDirectionStringFromAngle(int angle) {
      angle %= 360;
      if (angle < 0) {
         angle += 360;
      }

      if (angle < 22 || angle > 338) {
         return "south";
      } else if (angle < 68) {
         return "south-west";
      } else if (angle < 112) {
         return "west";
      } else if (angle < 158) {
         return "north-west";
      } else if (angle < 202) {
         return "north";
      } else if (angle < 248) {
         return "north-east";
      } else {
         return angle < 292 ? "east" : "south-east";
      }
   }

   // 26.2: these 1.12 helpers reflectively fetched GuiContainer's private slot/item-render methods
   // (via the removed Forge ReflectionHelper) so the Mill trade GUI could re-render slots. The 26.2
   // GUI render-state pipeline exposes AbstractContainerScreen.extractSlot directly (GuiTrade overrides
   // it), so the reflection is no longer needed and these are unused no-ops kept for source parity.
   public static Method getDrawItemStackInventoryMethod(Object gui) {
      return null;
   }

   public static Method getDrawSlotInventoryMethod(Object gui) {
      return null;
   }

   public static File getExportDir() {
      File exportDir = new File(getMillenaireCustomContentDir(), "exports");
      if (!exportDir.exists()) {
         exportDir.mkdirs();
      }

      return exportDir;
   }

   public static List<String> getFileLines(File file) throws IOException {
      List<String> lines = new ArrayList<>();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"))) {
         for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            lines.add(line);
         }
      }

      return lines;
   }

   public static int getInvItemHashTotal(HashMap<InvItem, Integer> map) {
      int total = 0;

      for (InvItem key : map.keySet()) {
         total += map.get(key);
      }

      return total;
   }

   public static Item getItemById(int id) {
      return BuiltInRegistries.ITEM.byId(id);
   }

   public static double getItemWeaponDamage(Item item) {
      net.minecraft.world.item.component.ItemAttributeModifiers modifiers = item.components()
         .get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
      if (modifiers == null) {
         return 0.0;
      }
      return modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
   }

   public static int[] getJumpDestination(Level world, int x, int y, int z) {
      if (!WorldUtilities.isBlockFullCube(world, x, y, z) && !WorldUtilities.isBlockFullCube(world, x, y + 1, z)) {
         return new int[]{x, y, z};
      } else if (!WorldUtilities.isBlockFullCube(world, x + 1, y, z) && !WorldUtilities.isBlockFullCube(world, x + 1, y + 1, z)) {
         return new int[]{x + 1, y, z};
      } else if (!WorldUtilities.isBlockFullCube(world, x - 1, y, z) && !WorldUtilities.isBlockFullCube(world, x - 1, y + 1, z)) {
         return new int[]{x - 1, y, z};
      } else if (!WorldUtilities.isBlockFullCube(world, x, y, z + 1) && !WorldUtilities.isBlockFullCube(world, x, y + 1, z + 1)) {
         return new int[]{x, y, z + 1};
      } else {
         return !WorldUtilities.isBlockFullCube(world, x, y, z - 1) && !WorldUtilities.isBlockFullCube(world, x, y + 1, z - 1) ? new int[]{x, y, z - 1} : null;
      }
   }

   public static File getMillenaireContentDir() {
      if (baseDir == null) {
         baseDir = new File(getModsDir(), "millenaire");
      }

      return baseDir;
   }

   public static File getMillenaireCustomContentDir() {
      if (customDir == null) {
         customDir = new File(getModsDir(), "millenaire-custom");
      }

      return customDir;
   }

   public static File getMillenaireHelpDir() {
      return new File(getMillenaireContentDir(), "help");
   }

   public static File getModsDir() {
      return new File(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile(), "mods");
   }

   public static int getPriceColourMC(int price) {
      if (price >= 4096) {
         return 14;
      } else {
         return price >= 64 ? 15 : 6;
      }
   }

   public static Random getRandom() {
      if (random == null) {
         random = new Random();
      }

      return random;
   }

   public static BufferedReader getReader(File file) throws IOException {
      byte[] bytes;
      try (FileInputStream fis = new FileInputStream(file)) {
         bytes = fis.readAllBytes();
      }

      String content;
      try {
         // Strict UTF-8 first: newer / translation content (including CJK like the Chinese villager speech)
         // is UTF-8 and decodes cleanly here.
         content = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString();
      } catch (java.nio.charset.CharacterCodingException notUtf8) {
         // Legacy Millénaire content (culture name lists, sentences, dialogues) is Windows-1252: single-byte
         // accents like ü=0xFC that are INVALID UTF-8 — reading them as UTF-8 produced the villager-name
         // mojibake ("Türkoglu" → "T�rkoglu"). Decode those as Windows-1252. (1.12 read with the platform
         // default, which was Windows-1252 on the author's system; this detects per file so both encodings work.)
         content = new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
      }

      return new BufferedReader(new java.io.StringReader(content));
   }

   public static String getShortPrice(int price) {
      String res = "";
      if (price >= 4096) {
         res = (int)Math.floor(price / 4096) + "o ";
         price %= 4096;
      }

      if (price >= 64) {
         res = res + (int)Math.floor(price / 64) + "a ";
         price %= 64;
      }

      if (price > 0) {
         res = res + price + "d";
      }

      return res.trim();
   }

   public static MillCommonUtilities.WeightedChoice getWeightedChoice(List<? extends MillCommonUtilities.WeightedChoice> choices, Player player) {
      int weightTotal = 0;
      List<Integer> weights = new ArrayList<>();

      for (MillCommonUtilities.WeightedChoice choice : choices) {
         weightTotal += choice.getChoiceWeight(player);
         weights.add(choice.getChoiceWeight(player));
      }

      if (weightTotal < 1) {
         return null;
      } else {
         int random = randomInt(weightTotal);
         int count = 0;

         for (int i = 0; i < choices.size(); i++) {
            count += weights.get(i);
            if (random < count) {
               return choices.get(i);
            }
         }

         return null;
      }
   }

   public static File getWorldSaveDir(Level world) {
      MinecraftServer server = world.getServer();
      return server != null ? server.getWorldPath(LevelResource.ROOT).toFile() : null;
   }

   public static BufferedWriter getWriter(File file) throws UnsupportedEncodingException, FileNotFoundException {
      FileOutputStream fos = new FileOutputStream(file);

      try {
         return new BufferedWriter(new OutputStreamWriter(fos, "UTF8"));
      } catch (UnsupportedEncodingException | RuntimeException var3) {
         try {
            fos.close();
         } catch (IOException var2) {
         }

         throw var3;
      }
   }

   public static void initRandom(int seed) {
      random = new Random(seed);
   }

   public static void logInstance(Level world) {
      if (MillConfigValues.sendStatistics) {
         String os = System.getProperty("os.name");
         String mode;
         if (Mill.proxy.isTrueServer()) {
            mode = "s";
         } else if (Mill.isDistantClient()) {
            mode = "c";
         } else {
            mode = "l";
         }

         int totalexp = 0;
         if (Mill.proxy.isTrueServer()) {
            if (!Mill.serverWorlds.isEmpty()) {
               for (UserProfile p : Mill.serverWorlds.get(0).profiles.values()) {
                  for (Culture c : Culture.ListCultures) {
                     totalexp += Math.abs(p.getCultureReputation(c.key));
                  }
               }
            }
         } else {
            UserProfile p = Mill.proxy.getClientProfile();
            if (p != null) {
               for (Culture c : Culture.ListCultures) {
                  totalexp += Math.abs(p.getCultureReputation(c.key));
               }
            }
         }

         String lang = "";
         if (MillConfigValues.mainLanguage != null) {
            lang = MillConfigValues.mainLanguage.language;
         }

         int nbplayers = 1;
         if (Mill.proxy.isTrueServer() && !Mill.serverWorlds.isEmpty()) {
            nbplayers = Mill.serverWorlds.get(0).profiles.size();
         }

         String advancementsSurvivalDone = null;

         for (GenericAdvancement advancement : MillAdvancements.MILL_ADVANCEMENTS) {
            if (advancementsSurvivalDone == null) {
               advancementsSurvivalDone = "";
            } else {
               advancementsSurvivalDone = advancementsSurvivalDone + ",";
            }

            advancementsSurvivalDone = advancementsSurvivalDone
               + advancement.getKey()
               + ":"
               + MillConfigValues.advancementsSurvival.contains(advancement.getKey());
         }

         String advancementsCreativeDone = null;

         for (GenericAdvancement advancement : MillAdvancements.MILL_ADVANCEMENTS) {
            if (advancementsCreativeDone == null) {
               advancementsCreativeDone = "";
            } else {
               advancementsCreativeDone = advancementsCreativeDone + ",";
            }

            advancementsCreativeDone = advancementsCreativeDone
               + advancement.getKey()
               + ":"
               + MillConfigValues.advancementsCreative.contains(advancement.getKey());
         }

         String url = "http://millenaire.org/php/mlnuse.php?uid="
            + MillConfigValues.randomUid
            + "&mlnversion="
            + "8.1.2"
            + "&mode="
            + mode
            + "&lang="
            + lang
            + "&backuplang="
            + MillConfigValues.fallback_language
            + "&nbplayers="
            + nbplayers
            + "&os="
            + os
            + "&totalexp="
            + totalexp
            + "&advancementssurvival="
            + advancementsSurvivalDone
            + "&advancementscreative="
            + advancementsCreativeDone
            + "&validation="
            + MillAdvancements.computeKey();
         if (Mill.proxy.getClientProfile() != null && MillConfigValues.sendAdvancementLogin) {
            url = url + "&login=" + Mill.proxy.getClientProfile().playerName;
         }

         url = url.replaceAll(" ", "%20");
         MillConfigValues.logPerformed = true;
         new MillCommonUtilities.LogThread(url).start();
      }
   }

   public static int[] packLong(long nb) {
      return new int[]{(int)(nb >> 32), (int)nb};
   }

   public static String parseItemString(Culture culture, String inputString) {
      if (inputString.split("/").length != 2) {
         return "";
      } else {
         String result = "";
         String goodKey = inputString.split("/")[0];
         TradeGood good = culture.getTradeGood(goodKey);
         if (good != null) {
            result = good.getName() + ": " + inputString.split("/")[1];
         }

         return result;
      }
   }

   public static boolean probability(double probability) {
      return getRandom().nextDouble() < probability;
   }

   public static int putItemsInChest(Container chest, Block block, int toPut) {
      return putItemsInChest(chest, block.asItem(), 0, toPut);
   }

   public static int putItemsInChest(Container chest, Block block, int meta, int toPut) {
      return putItemsInChest(chest, block.asItem(), meta, toPut);
   }

   public static int putItemsInChest(Container chest, Item item, int toPut) {
      return putItemsInChest(chest, item, 0, toPut);
   }

   public static int putItemsInChest(Container chest, Item item, int meta, int toPut) {
      if (chest == null) {
         return 0;
      } else {
         int nb = 0;
         int maxSlot = chest.getContainerSize();
         if (chest instanceof Inventory) {
            maxSlot -= 5;
         }

         for (int i = 0; i < maxSlot && nb < toPut; i++) {
            ItemStack stack = chest.getItem(i);
            if (stack != ItemStack.EMPTY && stack.getItem() == item) {
               if (stack.getMaxStackSize() - stack.getCount() >= toPut - nb) {
                  stack.setCount(stack.getCount() + toPut - nb);
                  nb = toPut;
               } else {
                  nb += stack.getMaxStackSize() - stack.getCount();
                  stack.setCount(stack.getMaxStackSize());
               }

               chest.setItem(i, stack);
            }
         }

         for (int ix = 0; ix < maxSlot && nb < toPut; ix++) {
            ItemStack stack = chest.getItem(ix);
            if (stack == ItemStack.EMPTY) {
               stack = new ItemStack(item, 1);
               if (stack.getItem() instanceof InvItem.IItemInitialEnchantmens && !Mill.serverWorlds.isEmpty()) {
                  // Enchantments are dynamic-registry Holders now; resolve via the server world's RegistryAccess.
                  ((InvItem.IItemInitialEnchantmens)stack.getItem())
                     .applyEnchantments(stack, Mill.serverWorlds.get(0).world.registryAccess());
               }

               if (toPut - nb <= stack.getMaxStackSize()) {
                  stack.setCount(toPut - nb);
                  nb = toPut;
               } else {
                  stack.setCount(stack.getMaxStackSize());
                  nb += stack.getCount();
               }

               chest.setItem(ix, stack);
            }
         }

         return nb;
      }
   }

   public static int randomInt(int max) {
      return getRandom().nextInt(max);
   }

   public static long randomLong() {
      return getRandom().nextLong();
   }

   public static int readInteger(String line) throws Exception {
      int res = 1;

      for (String s : line.trim().split("\\*")) {
         res *= Integer.parseInt(s);
      }

      return res;
   }

   public static void readInventory(ListTag nbttaglist, Map<InvItem, Integer> inventory) {
      for (int i = 0; i < nbttaglist.size(); i++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(i);
         String itemName = nbttagcompound1.getStringOr("item", "");
         String itemMod = nbttagcompound1.getStringOr("itemmod", "");
         int itemMeta = nbttagcompound1.getIntOr("meta", 0);
         Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemMod + ":" + itemName));
         inventory.put(InvItem.createInvItem(item, itemMeta), nbttagcompound1.getIntOr("amount", 0));
      }
   }

   public static boolean testResourcePresence(String domain, String path) {
      return VillagerType.class.getResourceAsStream("/assets/" + domain + "/" + path) != null;
   }

   public static long unpackLong(int nb1, int nb2) {
      return (long)nb1 << 32 | nb2 & 4294967295L;
   }

   public static ListTag writeInventory(Map<InvItem, Integer> inventory) {
      ListTag nbttaglist = new ListTag();

      for (InvItem key : inventory.keySet()) {
         if (key.getItem() != null) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(key.getItem());
            CompoundTag nbttagcompound1 = new CompoundTag();
            nbttagcompound1.putString("item", itemId.getPath());
            nbttagcompound1.putString("itemmod", itemId.getNamespace());
            nbttagcompound1.putInt("meta", key.meta);
            nbttagcompound1.putInt("amount", inventory.get(key));
            nbttaglist.add(nbttagcompound1);
         } else {
            MillLog.error(null, "Key with null item when saving inventory: " + key);
         }
      }

      return nbttaglist;
   }

   /**
    * Opens an InputStream for the given URL with finite connect/read timeouts so that
    * background network threads can never block indefinitely on a hung socket (which
    * would keep the JVM alive past the client shutdown watchdog).
    */
   private static InputStream openStreamWithTimeout(String url) throws IOException {
      URLConnection connection = new URL(url).openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      return connection.getInputStream();
   }

   public static class BonusThread extends Thread {
      String login;

      public BonusThread(String login) {
         this.login = login;
         this.setDaemon(true);
      }

      @Override
      public void run() {
         try {
            InputStream stream = openStreamWithTimeout("http://millenaire.org/php/bonuscheck.php?login=" + this.login);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String result = reader.readLine();
            if (result != null && result.trim().equals("thik hai")) {
               MillConfigValues.bonusEnabled = true;
               MillConfigValues.bonusCode = MillConfigValues.calculateLoginMD5(this.login);
               MillConfigValues.writeConfigFile();
            }
         } catch (Exception var4) {
         }
      }
   }

   public static class ExtFileFilter implements FilenameFilter {
      String ext = null;

      public ExtFileFilter(String ext) {
         this.ext = ext;
      }

      @Override
      public boolean accept(File file, String name) {
         return !name.toLowerCase().endsWith("." + this.ext) ? false : !name.startsWith(".");
      }
   }

   private static class LogThread extends Thread {
      String url;

      public LogThread(String url) {
         this.url = url;
         this.setDaemon(true);
      }

      @Override
      public void run() {
         try {
            InputStream stream = openStreamWithTimeout(this.url);
            stream.close();
         } catch (Exception var2) {
            if (MillConfigValues.DEV) {
               MillLog.error(null, "Exception when calling statistic service:" + var2.getMessage().substring(0, var2.getMessage().indexOf("?")));
            }
         }
      }
   }

   public static class PrefixExtFileFilter implements FilenameFilter {
      String ext = null;
      String prefix = null;

      public PrefixExtFileFilter(String pref, String ext) {
         this.ext = ext;
         this.prefix = pref;
      }

      @Override
      public boolean accept(File file, String name) {
         if (!name.toLowerCase().endsWith("." + this.ext)) {
            return false;
         } else {
            return !name.toLowerCase().startsWith(this.prefix) ? false : !name.startsWith(".");
         }
      }
   }

   public static class VersionCheckThread extends Thread {
      public VersionCheckThread() {
         this.setDaemon(true);
      }

      @Override
      public void run() {
         try {
            if ("8.1.2".contains("@VERSION@")) {
               return;
            }

            Thread.sleep(60000L);
            boolean devVersion = false;
            if ("8.1.2".contains("alpha") || "8.1.2".contains("beta") || "8.1.2".contains("rc")) {
               devVersion = true;
            }

            String url = "http://millenaire.org/lastversion/1.12.2";
            if (devVersion) {
               url = url + "-dev";
            }

            url = url + ".txt";
            InputStream stream = openStreamWithTimeout(url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String currentVersion = reader.readLine();
            if (currentVersion != null) {
               currentVersion = currentVersion.trim();
               if (!currentVersion.equals("8.1.2")) {
                  String releaseNotesEN = reader.readLine().trim();
                  String releaseNotesFR = reader.readLine().trim();
                  if (!MillConfigValues.mainLanguage.language.startsWith("fr")
                     && !MillConfigValues.mainLanguage.language.startsWith("en")
                     && !MillConfigValues.fallback_language.equals("fr")) {
                     ;
                  }

                  String var9 = devVersion ? "startup.outdatedversiondev" : "startup.outdatedversion";
               }
            }
         } catch (Exception var10) {
            MillLog.printException("Error when checking version:", var10);
         }
      }
   }

   public static class VillageInfo implements Comparable<MillCommonUtilities.VillageInfo> {
      public String textKey;
      public String[] values;
      public int distance;

      public int compareTo(MillCommonUtilities.VillageInfo arg0) {
         return arg0.distance - this.distance;
      }

      @Override
      public boolean equals(Object o) {
         return o != null && o instanceof MillCommonUtilities.VillageInfo ? this.distance == ((MillCommonUtilities.VillageInfo)o).distance : false;
      }

      @Override
      public int hashCode() {
         return super.hashCode();
      }
   }

   public static class VillageList {
      public List<Point> pos = new ArrayList<>();
      public List<String> names = new ArrayList<>();
      public List<String> types = new ArrayList<>();
      public List<String> cultures = new ArrayList<>();
      public List<String> generatedFor = new ArrayList<>();
      public List<List<Long>> buildingsTime = new ArrayList<>();
      public List<List<Long>> villagersTime = new ArrayList<>();
      public Map<Point, Integer> rankByPos = new HashMap<>();

      public void addVillage(Point p, String name, String type, String culture, String generatedFor) {
         this.pos.add(p);
         this.names.add(name);
         this.types.add(type);
         this.cultures.add(culture);
         this.generatedFor.add(generatedFor);
         this.buildingsTime.add(new ArrayList<>());
         this.villagersTime.add(new ArrayList<>());
         this.rankByPos.put(p, this.pos.size() - 1);
      }

      public void removeVillage(Point p) {
         int id = -1;

         for (int i = 0; i < this.pos.size() && id == -1; i++) {
            if (p.sameBlock(this.pos.get(i))) {
               id = i;
            }
         }

         if (id != -1) {
            this.pos.remove(id);
            this.names.remove(id);
            this.types.remove(id);
            this.cultures.remove(id);
            this.generatedFor.remove(id);
         }

         this.rankByPos.clear();

         for (int ix = 0; ix < this.pos.size(); ix++) {
            this.rankByPos.put(this.pos.get(ix), ix);
         }
      }
   }

   public interface WeightedChoice {
      int getChoiceWeight(Player var1);
   }
}
