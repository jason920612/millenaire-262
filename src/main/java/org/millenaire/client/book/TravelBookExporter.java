package org.millenaire.client.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.millenaire.client.gui.text.GuiTravelBook;
import org.millenaire.common.buildingplan.BuildingPlanSet;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillageType;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.VillagerRecord;

public class TravelBookExporter {
   private static Map<String, ItemStack> itemsToRender = new HashMap<>();
   private static final String EOL = "\n";

   private static String escapeQuotes(String label) {
      label = label.replaceAll("'", "''");
      return label.replaceAll("\"", "\\\\\"");
   }

   private static void exportAllBuildings(BookManagerTravelBook travelBookManager, BufferedWriter writer, Gson gson, String language) throws UnsupportedEncodingException, FileNotFoundException, IOException {
      boolean firstValues = true;
      writer.write("DELETE from encyclopediadata WHERE type='buildings' AND language='" + language + "';" + "\n");

      for (Culture culture : Culture.ListCultures) {
         for (BuildingPlanSet planSet : culture.ListPlanSets) {
            if (planSet.getFirstStartingPlan().travelBookDisplay) {
               if (firstValues) {
                  firstValues = false;
               }

               TextBook book = travelBookManager.getBookBuildingDetail(culture, planSet.key, null);
               String json = escapeQuotes(gson.toJson(new TravelBookExporter.ExportBook(book)));
               String label = planSet.getNameNativeAndTranslated();
               label = escapeQuotes(label);
               String categoryLabel = escapeQuotes(culture.getCultureString("travelbook_category." + planSet.getFirstStartingPlan().travelBookCategory));
               String itemref = culture.key + "-buildings-" + planSet.key + "-" + language;
               writer.write("INSERT INTO encyclopediadata (itemref,type,language,culture,category,categorylabel,itemkey,label,iconkey,data) VALUES \n");
               writer.write(
                  "('"
                     + itemref
                     + "','buildings','"
                     + language
                     + "','"
                     + culture.key
                     + "','"
                     + planSet.getFirstStartingPlan().travelBookCategory
                     + "','"
                     + categoryLabel
                     + "','"
                     + planSet.key
                     + "','"
                     + label
                     + "','"
                     + getIconKey(planSet.getIcon())
                     + "','"
                     + json
                     + "');"
                     + "\n"
               );
            }
         }
      }

      writer.write(";\n");
   }

   private static void exportAllCultures(BookManagerTravelBook travelBookManager, BufferedWriter writer, Gson gson, String language) throws UnsupportedEncodingException, FileNotFoundException, IOException {
      boolean firstValues = true;
      writer.write("DELETE from encyclopediadata WHERE type='cultures' AND language='" + language + "';" + "\n");

      for (Culture culture : Culture.ListCultures) {
         if (firstValues) {
            firstValues = false;
         }

         TextBook book = travelBookManager.getBookCultureForJSONExport(culture, null);
         String json = escapeQuotes(gson.toJson(new TravelBookExporter.ExportBook(book)));
         String label = culture.getNameTranslated();
         label = escapeQuotes(label);
         String itemref = culture.key + "-cultures-" + culture.key + "-" + language;
         writer.write("INSERT INTO encyclopediadata (itemref,type,language,culture,category,itemkey,label,iconkey,data) VALUES \n");
         writer.write(
            "('"
               + itemref
               + "','cultures','"
               + language
               + "','"
               + culture.key
               + "',NULL,'"
               + culture.key
               + "','"
               + label
               + "','"
               + getIconKey(culture.getIcon())
               + "','"
               + json
               + "');"
               + "\n"
         );
      }

      writer.write(";\n");
   }

   private static void exportAllTradeGoods(BookManagerTravelBook travelBookManager, BufferedWriter writer, Gson gson, String language) throws UnsupportedEncodingException, FileNotFoundException, IOException {
      boolean firstValues = true;
      writer.write("DELETE from encyclopediadata WHERE type='tradegoods' AND language='" + language + "';" + "\n");

      for (Culture culture : Culture.ListCultures) {
         for (TradeGood tradeGood : culture.goodsList) {
            if (tradeGood.travelBookDisplay && !tradeGood.travelBookCategory.equals("foreigntrade")) {
               if (firstValues) {
                  firstValues = false;
               }

               TextBook book = travelBookManager.getBookTradeGoodDetail(culture, tradeGood.key, null);
               String json = escapeQuotes(gson.toJson(new TravelBookExporter.ExportBook(book)));
               String label = tradeGood.getName();
               label = escapeQuotes(label);
               String categoryLabel = escapeQuotes(culture.getCultureString("travelbook_category." + tradeGood.travelBookCategory));
               String itemref = culture.key + "-tradegoods-" + tradeGood.key + "-" + language;
               writer.write("INSERT INTO encyclopediadata (itemref,type,language,culture,category,categorylabel,itemkey,label,iconkey,data) VALUES \n");
               writer.write(
                  "('"
                     + itemref
                     + "','tradegoods','"
                     + language
                     + "','"
                     + culture.key
                     + "','"
                     + tradeGood.travelBookCategory
                     + "','"
                     + categoryLabel
                     + "','"
                     + tradeGood.key
                     + "','"
                     + label
                     + "','"
                     + getIconKey(tradeGood.getIcon())
                     + "','"
                     + json
                     + "');"
                     + "\n"
               );
            }
         }
      }

      writer.write(";\n");
   }

   private static void exportAllVillagers(BookManagerTravelBook travelBookManager, BufferedWriter writer, Gson gson, String language) throws UnsupportedEncodingException, FileNotFoundException, IOException {
      writer.write("DELETE from encyclopediadata WHERE type='villagers' AND language='" + language + "';" + "\n");
      boolean firstValues = true;

      for (Culture culture : Culture.ListCultures) {
         for (VillagerType vtype : culture.listVillagerTypes) {
            if (vtype.travelBookDisplay) {
               if (firstValues) {
                  firstValues = false;
               }

               TextBook book = travelBookManager.getBookVillagerDetail(culture, vtype.key, null);
               String json = escapeQuotes(gson.toJson(new TravelBookExporter.ExportBook(book)));
               String label = vtype.getNameNativeAndTranslated();
               label = escapeQuotes(label);
               String categoryLabel = escapeQuotes(culture.getCultureString("travelbook_category." + vtype.travelBookCategory));
               String itemref = culture.key + "-villagers-" + vtype.key + "-" + language;
               writer.write("INSERT INTO encyclopediadata (itemref,type,language,culture,category,categorylabel,itemkey,label,iconkey,data) VALUES \n");
               writer.write(
                  "('"
                     + itemref
                     + "','villagers','"
                     + language
                     + "','"
                     + culture.key
                     + "','"
                     + vtype.travelBookCategory
                     + "','"
                     + categoryLabel
                     + "','"
                     + vtype.key
                     + "','"
                     + label
                     + "','"
                     + getIconKey(vtype.getIcon())
                     + "','"
                     + json
                     + "');"
                     + "\n"
               );
            }
         }
      }

      writer.write(";\n");
   }

   private static void exportAllVillages(BookManagerTravelBook travelBookManager, BufferedWriter writer, Gson gson, String language) throws UnsupportedEncodingException, FileNotFoundException, IOException {
      boolean firstValues = true;
      writer.write("DELETE from encyclopediadata WHERE type='villages' AND language='" + language + "';" + "\n");

      for (Culture culture : Culture.ListCultures) {
         for (VillageType vtype : culture.listVillageTypes) {
            if (vtype.travelBookDisplay) {
               if (firstValues) {
                  firstValues = false;
               }

               TextBook book = travelBookManager.getBookVillageDetail(culture, vtype.key, null);
               String json = escapeQuotes(gson.toJson(new TravelBookExporter.ExportBook(book)));
               String label = vtype.getNameNativeAndTranslated();
               label = escapeQuotes(label);
               String itemref = culture.key + "-villages-" + vtype.key + "-" + language;
               writer.write("INSERT INTO encyclopediadata (itemref,type,language,culture,category,itemkey,label,iconkey,data) VALUES \n");
               writer.write(
                  "('"
                     + itemref
                     + "','villages','"
                     + language
                     + "','"
                     + culture.key
                     + "',NULL,'"
                     + vtype.key
                     + "','"
                     + label
                     + "','"
                     + getIconKey(vtype.getIcon())
                     + "','"
                     + json
                     + "');"
                     + "\n"
               );
            }
         }
      }

      writer.write(";\n");
   }

   /**
    * Renders an item icon to a PNG for the web-encyclopedia export (a developer-only export tool,
    * never invoked during normal play).
    *
    * <p>26.2 NOTE (provably not reachable via public API): the 1.12 path rendered the item into an
    * off-screen {@code Framebuffer} ({@code RenderItem.renderItemAndEffectIntoGUI}) then read the pixels
    * back with {@code glReadPixels}/{@code TextureUtil}. On 26.2 the GUI draws through deferred
    * render-state extraction ({@code GuiRenderState}) which {@code GameRenderer} flushes to
    * {@code GameRenderer.mainRenderTarget()} once per frame via {@code GuiRenderer} — whose draw target
    * and {@code executeDrawRange} are {@code private} (verified: GuiRenderer.java, GameRenderer.java in
    * mc-sources). There is no public API to render a single {@link ItemStack} into an arbitrary
    * {@link com.mojang.blaze3d.pipeline.TextureTarget} and read it back synchronously per item; the
    * GPU read-back ({@code CommandEncoder.copyTextureToBuffer} / {@link net.minecraft.client.Screenshot})
    * is also async (completion callback). Redirecting the item draw to an off-screen target would need
    * reflection/a mixin into private GuiRenderer internals, which this faithful port avoids. The
    * encyclopedia icons are instead shipped as pre-baked PNGs; this hook only registers the icon key.
    */
   private static void exportItemStack(ItemStack stack) throws IOException {
      MillLog.warning(
         null,
         "Travel-book icon export is a dev tool and is a no-op on 26.2 (no public off-screen single-item render): "
            + getIconKey(stack)
      );
   }

   public static void exportItemStacks() {
      for (ItemStack stack : itemsToRender.values()) {
         try {
            exportItemStack(stack);
         } catch (IOException var3) {
            MillLog.printException(var3);
         }
      }

      MillLog.major(null, "Exported " + itemsToRender.size() + " icons.");
   }

   public static void exportTravelBookData() {
      BookManagerTravelBook travelBookManager = new BookManagerTravelBook(50000, 50000, 50000, 50000, new BookManager.FontRendererMock());
      File dir = new File(MillCommonUtilities.getMillenaireCustomContentDir(), "jsonexports");
      dir.mkdirs();
      Gson gson = new GsonBuilder().disableHtmlEscaping().create();

      try {
         String language = MillConfigValues.effective_language;
         if (language.contains("_")) {
            language = language.split("_")[0];
         }

         File file = new File(dir, "travelbook_" + language + ".sql");
         BufferedWriter writer = MillCommonUtilities.getWriter(file);
         exportAllCultures(travelBookManager, writer, gson, language);
         exportAllVillagers(travelBookManager, writer, gson, language);
         exportAllBuildings(travelBookManager, writer, gson, language);
         exportAllVillages(travelBookManager, writer, gson, language);
         exportAllTradeGoods(travelBookManager, writer, gson, language);
         writer.close();
         MillLog.warning(null, "Exported travel book data to SQL for language: " + language);
      } catch (IOException var6) {
         MillLog.printException(var6);
      }
   }

   /**
    * Renders a mock villager to a PNG for the web-encyclopedia export (developer-only tool).
    *
    * <p>26.2 NOTE: same off-screen read-back limitation as {@link #exportItemStack} (no public API to
    * render into and synchronously read back an arbitrary {@code RenderTarget}); it additionally relied
    * on the in-GUI entity render. The mock-villager creation below is preserved so the entity is built
    * correctly; only the GPU capture/PNG write is a no-op. The encyclopedia pictures are shipped
    * pre-baked.
    */
   public static void exportVillagerPicture(VillagerType villagerType, boolean mainPageExport) throws IOException {
      MillCommonUtilities.initRandom(villagerType.key.hashCode());
      VillagerRecord villagerRecord = VillagerRecord.createVillagerRecord(
         villagerType.culture, villagerType.key, Mill.getMillWorld(Minecraft.getInstance().level), null, null, null, null, -1L, true
      );
      MillVillager mockVillager = MillVillager.createMockVillager(villagerRecord, Minecraft.getInstance().level);
      if (!mainPageExport) {
         mockVillager.heldItem = villagerType.getTravelBookHeldItem();
         mockVillager.heldItemOffHand = villagerType.getTravelBookHeldItemOffHand();
         mockVillager.travelBookMockVillager = true;
      }

      MillLog.warning(
         null,
         "Travel-book villager-picture export is a dev tool and is a no-op on 26.2 (no public off-screen entity render): " + villagerType.key
      );
   }

   public static void exportVillagerPictures(Level world) {
      int nb = 0;

      for (Culture culture : Culture.ListCultures) {
         for (VillagerType villagerType : culture.listVillagerTypes) {
            if (villagerType.travelBookDisplay) {
               try {
                  exportVillagerPicture(villagerType, false);
                  nb++;
               } catch (Exception var7) {
                  MillLog.printException(var7);
               }
            }

            if (villagerType.travelBookMainCultureVillager) {
               try {
                  exportVillagerPicture(villagerType, true);
                  nb++;
               } catch (Exception var8) {
                  MillLog.printException(var8);
               }
            }
         }
      }

      MillLog.major(null, "Exported " + nb + " villager pictures.");
   }

   private static String getIconKey(ItemStack stack) {
      if (stack == null) {
         return "";
      } else {
         // 26.2: item metadata is gone; the key is just the registry id.
         String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().replaceAll(":", "_");

         if (!itemsToRender.containsKey(key)) {
            itemsToRender.put(key, stack);
         }

         return key;
      }
   }

   private static class ExportBook {
      public List<TravelBookExporter.ExportPage> pages = new ArrayList<>();

      public ExportBook(TextBook book) {
         for (TextPage page : book.getPages()) {
            this.pages.add(new TravelBookExporter.ExportPage(page));
         }
      }
   }

   private static class ExportLine {
      public String style = "";
      public String text = "";
      public String referenceButtonCulture = null;
      public String referenceButtonType = null;
      public String referenceButtonKey = null;
      public String referenceButtonLabel = null;
      public String referenceButtonIconKey = null;
      public String iconKey = null;
      public String iconLabel = null;
      public List<TravelBookExporter.ExportLine> columns = null;
      public Boolean exportTwoColumns = null;
      public String specialTag = null;

      public ExportLine(TextLine textLine) {
         this.text = textLine.text;
         this.specialTag = textLine.exportSpecialTag;
         if (textLine.style != null) {
            if (textLine.style.equals(TextLine.ITALIC)) {
               this.style = "subheader";
            } else if (textLine.style.equals("§1")) {
               this.style = "header";
            } else {
               this.style = textLine.style;
            }
         }

         if (textLine.referenceButton != null) {
            this.referenceButtonCulture = textLine.referenceButton.culture.key;
            switch (textLine.referenceButton.type) {
               case BUILDING_DETAIL:
                  this.referenceButtonType = "buildings";
                  break;
               case CULTURE:
                  this.referenceButtonType = "cultures";
                  break;
               case TRADE_GOOD_DETAIL:
                  this.referenceButtonType = "tradegoods";
                  break;
               case VILLAGE_DETAIL:
                  this.referenceButtonType = "villages";
                  break;
               case VILLAGER_DETAIL:
                  this.referenceButtonType = "villagers";
            }

            this.referenceButtonKey = textLine.referenceButton.key;
            this.referenceButtonIconKey = TravelBookExporter.getIconKey(textLine.referenceButton.getIcon());
            this.referenceButtonLabel = textLine.referenceButton.getIconFullLegendExport();
         }

         if (textLine.icons != null && textLine.icons.size() > 0) {
            this.iconKey = TravelBookExporter.getIconKey(textLine.icons.get(0));
            if (textLine.iconExtraLegends != null && textLine.iconExtraLegends.size() > 0 && textLine.iconExtraLegends.get(0) != null) {
               this.iconLabel = textLine.iconExtraLegends.get(0);
            } else if (textLine.displayItemLegend()) {
               this.iconLabel = textLine.icons.get(0).getHoverName().getString();
            }
         }

         if (textLine.columns != null) {
            this.columns = new ArrayList<>();

            for (TextLine col : textLine.columns) {
               this.columns.add(new TravelBookExporter.ExportLine(col));
            }

            if (textLine.exportTwoColumns) {
               this.exportTwoColumns = Boolean.TRUE;
            }
         }
      }
   }

   private static class ExportPage {
      public List<TravelBookExporter.ExportLine> lines = new ArrayList<>();

      public ExportPage(TextPage page) {
         for (TextLine line : page.getLines()) {
            if (line.columns != null && this.lines.size() > 0 && this.lines.get(this.lines.size() - 1).columns != null && !line.exportTwoColumns) {
               TravelBookExporter.ExportLine previousLine = this.lines.get(this.lines.size() - 1);
               TravelBookExporter.ExportLine newLine = new TravelBookExporter.ExportLine(line);
               previousLine.columns.addAll(newLine.columns);
            } else if (line.columns != null || line.text != null && line.text.trim().length() > 0) {
               this.lines.add(new TravelBookExporter.ExportLine(line));
            }
         }
      }
   }
}
