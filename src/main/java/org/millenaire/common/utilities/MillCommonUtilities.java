package org.millenaire.common.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.millenaire.common.advancements.GenericAdvancement;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.world.UserProfile;

public class MillCommonUtilities {
   private static final String MILLENAIRE_ORG_ROOT = "http://millenaire.org";

   public static String flattenStrings(Collection<String> strings) {
      return strings.stream().collect(Collectors.joining(", "));
   }

   public static void generateHearts(Entity ent) {
      float width = ent.getBbWidth();
      float height = ent.getBbHeight();
      for (int var3 = 0; var3 < 7; var3++) {
         double var4 = MillRandom.random.nextGaussian() * 0.02;
         double var6 = MillRandom.random.nextGaussian() * 0.02;
         double var8 = MillRandom.random.nextGaussian() * 0.02;
         ent.level()
            .addParticle(
               ParticleTypes.HEART,
               ent.getX() + MillRandom.random.nextFloat() * width * 2.0F - width,
               ent.getY() + 0.5 + MillRandom.random.nextFloat() * height,
               ent.getZ() + MillRandom.random.nextFloat() * width * 2.0F - width,
               var4,
               var6,
               var8
            );
      }
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

   public static int getPriceColourMC(int price) {
      if (price >= 4096) {
         return 14;
      } else {
         return price >= 64 ? 15 : 6;
      }
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

   public static int readInteger(String line) throws Exception {
      int res = 1;

      for (String s : line.trim().split("\\*")) {
         res *= Integer.parseInt(s);
      }

      return res;
   }

   public static boolean testResourcePresence(String domain, String path) {
      return VillagerType.class.getResourceAsStream("/assets/" + domain + "/" + path) != null;
   }

   public static long unpackLong(int nb1, int nb2) {
      return (long)nb1 << 32 | nb2 & 4294967295L;
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
