package org.millenaire.client;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import org.millenaire.client.book.TextBook;
import org.millenaire.client.book.TextLine;
import org.millenaire.client.book.TextPage;
import org.millenaire.client.forge.ClientProxy;
import org.millenaire.client.gui.DisplayActions;
import org.millenaire.client.gui.text.GuiPanelParchment;
import org.millenaire.client.gui.text.GuiText;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.TileEntityPanel;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.quest.QuestInstance;
import org.millenaire.common.utilities.DevModUtilities;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.VillageUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.buildingmanagers.PanelContentGenerator;
import org.millenaire.common.world.UserProfile;

@Environment(EnvType.CLIENT)
public class MillClientUtilities {
   private static final int VILLAGE_RADIUS_WARNING_LEVEL = 120;
   private static long lastPing = 0L;
   private static long lastFreeRes = 0L;

   /** Raw GLFW key check via the current window. Replaces 1.12 {@code Keyboard.isKeyDown(lwjglCode)}. */
   private static boolean rawKeyDown(int glfwKey) {
      return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), glfwKey);
   }

   public static void displayChunkPanel(Level world, Player player) {
      List<List<String>> pages = new ArrayList<>();
      List<String> page = new ArrayList<>();
      page.add(LanguageUtilities.string("chunk.chunkmap"));
      pages.add(page);
      page = new ArrayList<>();
      page.add(LanguageUtilities.string("chunk.caption"));
      page.add(LanguageUtilities.string(""));
      page.add(LanguageUtilities.string("chunk.captiongeneral"));
      page.add(LanguageUtilities.string("chunk.captiongreen"));
      page.add(LanguageUtilities.string("chunk.captionblue"));
      page.add(LanguageUtilities.string("chunk.captionpurple"));
      page.add(LanguageUtilities.string("chunk.captionwhite"));
      page.add(LanguageUtilities.string(""));
      page.add(LanguageUtilities.string("chunk.playerposition", (int)player.getX() + "/" + (int)player.getZ()));
      page.add(LanguageUtilities.string(""));
      page.add(LanguageUtilities.string("chunk.settings", "" + MillConfigValues.KeepActiveRadius, "" + MillConfigValues.KeepActiveRadius));
      page.add(LanguageUtilities.string(""));
      page.add(LanguageUtilities.string("chunk.explanations"));
      pages.add(page);
      TextBook book = TextBook.convertStringsToBook(pages);
      Minecraft.getInstance().setScreenAndShow(new GuiPanelParchment(player, book, null, 2, true));
   }

   public static void displayInfoPanel(Level world, Player player) {
      TextBook book = new TextBook();
      TextPage page = new TextPage();
      page.addLine(new TextLine(new GuiText.MillGuiButton(LanguageUtilities.string("ui.helpbutton"), 2000, new ItemStack(Items.OAK_SIGN))));
      page.addLine(new TextLine(new GuiText.MillGuiButton(LanguageUtilities.string("ui.travelbookbutton"), 5000, new ItemStack(Items.WRITABLE_BOOK))));
      page.addLine(new TextLine(new GuiText.MillGuiButton(LanguageUtilities.string("ui.configbutton"), 4000, new ItemStack(Items.REDSTONE))));
      if (!Mill.serverWorlds.isEmpty()) {
         page.addLine(new TextLine(new GuiText.MillGuiButton(LanguageUtilities.string("ui.chunkbutton"), 3000, new ItemStack(Items.MAP))));
      }

      page.addLine(LanguageUtilities.string("info.culturetitle"), "§1");
      page.addBlankLine();

      for (Culture culture : Culture.ListCultures) {
         page.addLine(LanguageUtilities.string("info.culture", culture.getAdjectiveTranslated()));
         String colour = "";
         if (culture.getLocalPlayerReputation() > 0) {
            colour = "§2";
         } else if (culture.getLocalPlayerReputation() < 0) {
            colour = "§4";
         }

         page.addLine(LanguageUtilities.string("info.culturereputation", culture.getLocalPlayerReputationString()), colour);
         if (MillConfigValues.languageLearning) {
            page.addLine(LanguageUtilities.string("info.culturelanguage", culture.getLanguageLevelString()));
         }

         page.addBlankLine();
      }

      book.addPage(page);
      page = new TextPage();
      page.addLine(LanguageUtilities.string("quest.creationqueststatus"), "§1");
      page.addBlankLine();

      for (String s : Mill.proxy.getClientProfile().getWorldQuestStatus()) {
         page.addLine(s);
      }

      page.addBlankLine();
      page.addLine(LanguageUtilities.string("quest.questlist"));
      page.addBlankLine();
      boolean questShown = false;
      UserProfile profile = Mill.proxy.getClientProfile();

      for (QuestInstance qi : profile.questInstances) {
         String s = qi.getListing(profile);
         if (s != null) {
            questShown = true;
            page.addLine(s);
            long timeLeft = qi.currentStepStart + qi.getCurrentStep().duration * 1000 - world.getOverworldClockTime();
            timeLeft = Math.round((float)(timeLeft / 1000L));
            if (timeLeft == 0L) {
               page.addLine(LanguageUtilities.string("quest.lessthananhourleft"), "§4");
            } else {
               page.addLine(LanguageUtilities.string("quest.timeremaining") + ": " + timeLeft + " " + LanguageUtilities.string("quest.hours"));
            }
         }
      }

      if (!questShown) {
         page.addLine(LanguageUtilities.string("quest.noquestsvisible"));
      }

      book.addPage(page);
      Minecraft.getInstance().setScreenAndShow(new GuiPanelParchment(player, book, null, 0, true));
   }

   public static void displayPanel(Level world, Player player, Point p) {
      TileEntityPanel panel = p.getPanel(world);
      if (panel != null && panel.buildingPos != null) {
         Building building = Mill.clientWorld.getBuilding(panel.buildingPos);
         if (building != null) {
            TextBook book = panel.getFullText(player);
            if (book != null) {
               DisplayActions.displayParchmentPanelGUI(player, book, building, panel.getMapType(), false);
            }
         }
      }
   }

   public static void displayStartupText(boolean error) {
      if (error) {
         Mill.proxy.sendChatAdmin(LanguageUtilities.string("startup.loadproblem", "Millénaire 8.1.2"));
         Mill.proxy.sendChatAdmin(LanguageUtilities.string("startup.checkload"));
         MillLog.error(null, "There was an error when trying to load Millénaire 8.1.2.");
      } else {
         if (MillConfigValues.displayStart) {
            String bonus = "";
            if (MillConfigValues.bonusEnabled) {
               bonus = " " + LanguageUtilities.string("startup.bonus");
            }

            Mill.proxy
               .sendChatAdmin(
                  LanguageUtilities.string("startup.millenaireloaded", "Millénaire 8.1.2", ClientProxy.KB_VILLAGES.getTranslatedKeyMessage().getString())
               );
            Mill.proxy.sendChatAdmin(LanguageUtilities.string("startup.bonus", "Millénaire 8.1.2", bonus), ChatFormatting.BLUE);
         }

         if (MillConfigValues.DEV) {
            Mill.proxy.sendChatAdmin(LanguageUtilities.string("startup.devmode1"), ChatFormatting.RED);
            Mill.proxy.sendChatAdmin(LanguageUtilities.string("startup.devmode2"), ChatFormatting.RED);
         }

         if (MillConfigValues.VillageRadius > 120) {
            Mill.proxy.sendChatAdmin(LanguageUtilities.string("startup.radiuswarning", "100"));
         }
      }
   }

   public static void displayTradeHelp(Building shop, Player player, Screen callingGui) {
      List<List<TextLine>> pages = new ArrayList<>();
      List<TextLine> page = new ArrayList<>();
      page.add(new TextLine("<darkblue>" + LanguageUtilities.string("tradehelp.title", shop.getNativeBuildingName())));
      page.add(new TextLine(""));
      page.add(new TextLine("<darkblue>" + LanguageUtilities.string("tradehelp.goodssold")));
      page.add(new TextLine(""));
      List<TradeGood> tradeGood = shop.calculateSellingGoods(null);
      if (tradeGood != null) {
         String lastDesc = null;
         List<ItemStack> stacks = new ArrayList<>();
         List<Integer> prices = new ArrayList<>();

         for (TradeGood g : tradeGood) {
            if (lastDesc != null && !lastDesc.equals(g.travelBookCategory)) {
               List<String> vprices = new ArrayList<>();

               for (int p : prices) {
                  vprices.add(LanguageUtilities.string("tradehelp.sellingprice") + " " + MillCommonUtilities.getShortPrice(p));
               }

               page.add(new TextLine(stacks, vprices, LanguageUtilities.string(lastDesc), 72));
               page.add(new TextLine());
               stacks = new ArrayList<>();
               prices = new ArrayList<>();
            }

            stacks.add(new ItemStack(g.item.getItem()));
            prices.add(g.getBasicSellingPrice(shop));
            if (g.travelBookCategory != null) {
               lastDesc = g.travelBookCategory;
            } else {
               lastDesc = "";
            }
         }

         if (lastDesc != null) {
            List<String> vprices = new ArrayList<>();

            for (int p : prices) {
               vprices.add(LanguageUtilities.string("tradehelp.sellingprice") + " " + MillCommonUtilities.getShortPrice(p));
            }

            page.add(new TextLine(stacks, vprices, LanguageUtilities.string(lastDesc), 72));
            page.add(new TextLine());
         }
      }

      page.add(new TextLine("<darkblue>" + LanguageUtilities.string("tradehelp.goodsbought")));
      page.add(new TextLine(""));
      tradeGood = shop.calculateBuyingGoods(null);
      if (tradeGood != null) {
         String lastDesc = null;
         List<ItemStack> stacks = new ArrayList<>();
         List<Integer> prices = new ArrayList<>();

         for (TradeGood g : tradeGood) {
            if (lastDesc != null && !lastDesc.equals(g.travelBookCategory)) {
               List<String> vprices = new ArrayList<>();

               for (int p : prices) {
                  vprices.add(LanguageUtilities.string("tradehelp.buyingprice") + " " + MillCommonUtilities.getShortPrice(p));
               }

               page.add(new TextLine(stacks, vprices, LanguageUtilities.string(lastDesc), 72));
               page.add(new TextLine());
               stacks = new ArrayList<>();
               prices = new ArrayList<>();
            }

            stacks.add(new ItemStack(g.item.getItem()));
            prices.add(g.getBasicBuyingPrice(shop));
            if (g.travelBookCategory != null) {
               lastDesc = g.travelBookCategory;
            } else {
               lastDesc = "";
            }
         }

         if (lastDesc != null) {
            List<String> vprices = new ArrayList<>();

            for (int p : prices) {
               vprices.add(LanguageUtilities.string("tradehelp.buyingprice") + " " + MillCommonUtilities.getShortPrice(p));
            }

            page.add(new TextLine(stacks, vprices, LanguageUtilities.string(lastDesc), 72));
            page.add(new TextLine());
         }
      }

      pages.add(page);
      page = new ArrayList<>();
      page.add(new TextLine("<darkblue>" + LanguageUtilities.string("tradehelp.helptitle")));
      page.add(new TextLine());
      page.add(new TextLine(LanguageUtilities.string("tradehelp.helptext")));
      pages.add(page);
      TextBook book = TextBook.convertLinesToBook(pages);
      GuiText helpGui = new GuiPanelParchment(player, null, book, 0, true);
      helpGui.setCallingScreen(callingGui);
      Minecraft.getInstance().setScreenAndShow(helpGui);
   }

   public static void displayVillageBook(Level world, Player player, Point p) {
      Building townHall = Mill.clientWorld.getBuilding(p);
      if (townHall != null) {
         TextBook book = new TextBook();
         TextPage page = new TextPage();
         page.addLine(LanguageUtilities.string("panels.villagescroll") + ": " + townHall.getVillageQualifiedName());
         page.addLine("");
         book.addPage(page);
         TextBook newBook = PanelContentGenerator.generateSummary(townHall);
         book.addBook(newBook);
         newBook = PanelContentGenerator.generateEtatCivil(townHall);
         book.addBook(newBook);
         newBook = PanelContentGenerator.generateConstructions(townHall);
         book.addBook(newBook);
         newBook = PanelContentGenerator.generateProjects(player, townHall);
         book.addBook(newBook);
         newBook = PanelContentGenerator.generateResources(townHall);
         book.addBook(newBook);
         newBook = PanelContentGenerator.generateInnGoods(townHall);
         book.addBook(newBook);
         DisplayActions.displayParchmentPanelGUI(player, book, townHall, 1, true);
      }
   }

   public static void handleKeyPress(Level world) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.gui.screen() == null) {
         Player player = minecraft.player;
         if (System.currentTimeMillis() - lastPing > 2000L) {
            try {
               if (player.level().dimension() == Level.OVERWORLD) {
                  if (ClientProxy.KB_VILLAGES.isDown()) {
                     ClientSender.displayVillageList(false);
                     lastPing = System.currentTimeMillis();
                  }

                  if (ClientProxy.KB_ESCORTS.isDown()) {
                     boolean stance = !rawKeyDown(340);
                     ClientSender.hireToggleStance(player, stance);
                     lastPing = System.currentTimeMillis();
                  }

                  if (ClientProxy.KB_MENU.isDown()) {
                     displayInfoPanel(world, player);
                     lastPing = System.currentTimeMillis();
                  }

                  if (MillConfigValues.DEV) {
                     if (rawKeyDown(340) && rawKeyDown(82) && System.currentTimeMillis() - lastFreeRes > 5000L) {
                        DevModUtilities.fillInFreeGoods(player);
                        lastFreeRes = System.currentTimeMillis();
                     }

                     if (rawKeyDown(340) && rawKeyDown(263)) {
                        player.setPos(player.getX() + 10000.0, player.getY() + 10.0, player.getZ());
                        lastPing = System.currentTimeMillis();
                     }

                     if (rawKeyDown(340) && rawKeyDown(262)) {
                        player.setPos(player.getX() - 10000.0, player.getY() + 10.0, player.getZ());
                        lastPing = System.currentTimeMillis();
                     }

                     if (rawKeyDown(76)) {
                        ClientSender.displayVillageList(true);
                        lastPing = System.currentTimeMillis();
                     }

                     if (rawKeyDown(77) && rawKeyDown(340)) {
                        ClientSender.devCommand(1);
                        lastPing = System.currentTimeMillis();
                     }

                     if (rawKeyDown(89) && rawKeyDown(341)) {
                        Mill.proxy.sendChatAdmin("Sending test path request.");
                        ClientSender.devCommand(2);
                        lastPing = System.currentTimeMillis();
                     }

                     if (rawKeyDown(84)) {
                        Mill.clientWorld.displayTagActionData(player);
                        lastPing = System.currentTimeMillis();
                     }
                  }
               }
            } catch (Exception var4) {
               MillLog.printException("Exception while handling key presses:", var4);
            }
         }
      }
   }

   public static void putVillagerSentenceInChat(MillVillager v) {
      if (v.dialogueTargetFirstName == null || v.dialogueChat) {
         int radius = 0;
         if (Mill.isDistantClient()) {
            radius = MillConfigValues.VillagersSentenceInChatDistanceClient;
         } else {
            radius = MillConfigValues.VillagersSentenceInChatDistanceSP;
         }

         Player player = Mill.proxy.getTheSinglePlayer();
         if (!(v.getPos().distanceTo(player) > radius)) {
            String gameSpeech = VillageUtilities.getVillagerSentence(v, player.getName().getString(), false);
            String nativeSpeech = VillageUtilities.getVillagerSentence(v, player.getName().getString(), true);
            if (nativeSpeech != null || gameSpeech != null) {
               String s;
               if (v.dialogueTargetFirstName != null) {
                  s = LanguageUtilities.string("other.chattosomeone", v.getVillagerName(), v.dialogueTargetFirstName + " " + v.dialogueTargetLastName) + ": ";
               } else {
                  s = v.getVillagerName() + ": ";
               }

               if (nativeSpeech != null) {
                  s = s + "§9" + nativeSpeech;
               }

               if (nativeSpeech != null && gameSpeech != null) {
                  s = s + " ";
               }

               if (gameSpeech != null) {
                  s = s + "§4" + gameSpeech;
               }

               Mill.proxy.sendLocalChat(Mill.proxy.getTheSinglePlayer(), v.dialogueColour, s);
            }
         }
      }
   }
}
