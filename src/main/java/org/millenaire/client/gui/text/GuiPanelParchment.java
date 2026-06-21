package org.millenaire.client.gui.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.millenaire.client.book.BookManager;
import org.millenaire.client.book.TextBook;
import org.millenaire.client.gui.DisplayActions;
import org.millenaire.client.network.ClientSender;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.ui.MillMapInfo;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.ThreadSafeUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.BuildingLocation;
import org.millenaire.common.village.ConstructionIP;
import org.millenaire.common.world.MillWorldData;

public class GuiPanelParchment extends GuiText {
   public static final int VILLAGE_MAP = 1;
   public static final int CHUNK_MAP = 2;
   public static final int chunkMapSizeInBlocks = 1280;
   private boolean isParchment = false;
   private int mapType = 0;
   private Building townHall = null;
   private final Player player;
   Identifier backgroundParchment = Identifier.fromNamespaceAndPath("millenaire", "textures/gui/parchment.png");
   Identifier backgroundPanel = Identifier.fromNamespaceAndPath("millenaire", "textures/gui/panel.png");
   private final float targetHeight = 180.0F;
   private float scaledStartX;
   private float scaledStartY;
   private float scaleFactor;
   /** Window-relative offset baked into the map rects (replaces the old GL translate). */
   private int mapWindowXstart;
   private int mapWindowYstart;

   public GuiPanelParchment(Player player, Building townHall, TextBook textBook, int mapType, boolean isParchment) {
      this.mapType = mapType;
      this.townHall = townHall;
      this.isParchment = isParchment;
      this.player = player;
      this.textBook = textBook;
      this.bookManager = new BookManager(204, 220, 190, 195, new GuiText.FontRendererGUIWrapper(this));
   }

   public GuiPanelParchment(Player player, TextBook textBook, Building townHall, int mapType, boolean isParchment) {
      this.mapType = mapType;
      this.townHall = townHall;
      this.isParchment = isParchment;
      this.player = player;
      this.textBook = textBook;
      this.bookManager = new BookManager(204, 220, 190, 195, new GuiText.FontRendererGUIWrapper(this));
   }

   @Override
   protected void actionPerformed(GuiText.AbstractMillButton guibutton) {
      if (guibutton instanceof GuiText.MillGuiButton) {
         GuiText.MillGuiButton gb = (GuiText.MillGuiButton)guibutton;
         if (gb.id == 2000) {
            DisplayActions.displayHelpGUI();
         } else if (gb.id == 3000) {
            DisplayActions.displayChunkGUI(this.player, this.player.level());
         } else if (gb.id == 4000) {
            DisplayActions.displayConfigGUI();
         } else if (gb.id == 5000) {
            DisplayActions.displayTravelBookGUI(this.player);
         }
      }

      super.actionPerformed(guibutton);
   }

   @Override
   protected void customDrawBackground(int i, int j, float f) {
   }

   @Override
   public void customDrawScreen(int i, int j, float f) {
      try {
         if (this.mapType == 1 && this.pageNum == 0 && this.townHall != null && this.townHall.mapInfo != null) {
            this.drawVillageMap(i, j);
         } else if (this.mapType == 2 && this.pageNum == 0) {
            this.drawChunkMap(i, j);
         }
      } catch (Exception var5) {
         MillLog.printException("Exception while rendering map: ", var5);
      }
   }

   private void drawChunkMap(int i, int j) {
      if (!Mill.serverWorlds.isEmpty()) {
         int windowXstart = (this.width - this.getXSize()) / 2;
         int windowYstart = (this.height - this.getYSize()) / 2;
         this.mapWindowXstart = windowXstart;
         this.mapWindowYstart = windowYstart;
         Level world = Mill.serverWorlds.get(0).world;
         MillWorldData mw = Mill.serverWorlds.get(0);
         int startX = (this.getXSize() - 160) / 2;
         int startY = (this.getYSize() - 160) / 2;
         int posXstart = this.player.chunkPosition().getMinBlockX() - 640;
         int posZstart = this.player.chunkPosition().getMinBlockZ() - 640;
         int mouseX = (i - startX - windowXstart) / 2 * 16 + posXstart;
         int mouseZ = (j - startY - windowYstart) / 2 * 16 + posZstart;
         this.drawGradientRect(startX - 2, startY - 2, startX + 160 + 2, startY + 160 + 2, 536870912, 536870912);
         ArrayList<String> labels = new ArrayList<>();

         for (int x = posXstart; x < posXstart + 1280; x += 16) {
            for (int z = posZstart; z < posZstart + 1280; z += 16) {
               int colour = 0;
               if (!ThreadSafeUtilities.isChunkAtGenerated(world, x, z)) {
                  colour = 1074860305;
               } else {
                  if (ThreadSafeUtilities.isChunkAtLoaded(world, x, z)) {
                     colour = -1073676544;
                  } else {
                     colour = -1057030144;
                  }

                  this.drawPixel(startX + (x - posXstart) / 8, startY + (z - posZstart) / 8, colour);
                  if (mouseX == x && mouseZ == z) {
                     labels.add(LanguageUtilities.string("chunk.chunkcoords", "" + x / 16 + "/" + z / 16));
                  }
               }
            }
         }

         for (Building b : new ArrayList<>(mw.allBuildings())) {
            if (b.isTownhall && b.winfo != null && b.villageType != null) {
               for (int x = b.winfo.mapStartX; x < b.winfo.mapStartX + b.winfo.length; x += 16) {
                  for (int zx = b.winfo.mapStartZ; zx < b.winfo.mapStartZ + b.winfo.width; zx += 16) {
                     if (x >= posXstart && x <= posXstart + 1280 && zx >= posZstart && zx <= posZstart + 1280) {
                        int colour;
                        if (b.villageType.lonebuilding) {
                           colour = -258408295;
                        } else {
                           colour = -268435201;
                        }

                        this.drawPixel(startX + (x - posXstart) / 8 + 1, startY + (zx - posZstart) / 8 + 1, colour);
                        if (mouseX == x && mouseZ == zx) {
                           labels.add(LanguageUtilities.string("chunk.village", b.getVillageQualifiedName()));
                        }
                     }
                  }
               }
            }
         }

         // 26.2: the 1.12 force-loaded-chunk overlay used Forge's ForgeChunkManager.getPersistentChunksFor,
         // which is gone; forced chunks are now server-side state (ServerLevel#getForcedChunks()) not
         // available on the client where this map is drawn, so the overlay is intentionally omitted.

         if (!labels.isEmpty()) {
            int stringlength = 0;

            for (String s : labels) {
               int w = this.fontRenderer.width(s);
               if (w > stringlength) {
                  stringlength = w;
               }
            }

            this.drawGradientRect(i - 3 + 10, j - 3, i + stringlength + 3 + 10, j + 11 * labels.size(), -1073741824, -1073741824);

            for (int si = 0; si < labels.size(); si++) {
               this.drawString(labels.get(si), i + 10, j + 11 * si, 9474192);
            }
         }
      }
   }

   private void drawPixel(int x, int y, int colour) {
      this.drawGradientRect(this.mapWindowXstart + x, this.mapWindowYstart + y, this.mapWindowXstart + x + 1, this.mapWindowYstart + y + 1, colour, colour);
   }

   /**
    * Draws a scaled, coloured map cell. 26.2 PORT: the 1.12 {@code Tessellator}/{@code BufferBuilder}
    * quad is replaced by {@link #drawGradientRect} (which uses {@code GuiGraphicsExtractor.fillGradient}).
    */
   private void drawScaledRect(int left, int top, int right, int bottom, int color) {
      int x0 = (int)(this.mapWindowXstart + this.scaledStartX + left * this.scaleFactor);
      int y0 = (int)(this.mapWindowYstart + this.scaledStartY + top * this.scaleFactor);
      int x1 = (int)(this.mapWindowXstart + this.scaledStartX + right * this.scaleFactor);
      int y1 = (int)(this.mapWindowYstart + this.scaledStartY + bottom * this.scaleFactor);
      this.drawGradientRect(x0, y0, x1, y1, color, color);
   }

   private void drawVillageMap(int i, int j) {
      int xStart = (this.width - this.getXSize()) / 2;
      int yStart = (this.height - this.getYSize()) / 2;
      this.mapWindowXstart = xStart;
      this.mapWindowYstart = yStart;
      MillMapInfo minfo = this.townHall.mapInfo;
      this.scaleFactor = 180.0F / minfo.width;
      this.scaledStartX = (this.getXSize() - minfo.length * this.scaleFactor) / 2.0F;
      this.scaledStartY = (this.getYSize() - minfo.width * this.scaleFactor) / 2.0F;
      this.drawScaledRect(-2, -2, minfo.length + 2, minfo.width + 2, 536870912);
      BuildingLocation locHover = null;
      MillVillager villagerHover = null;
      Player playerHover = null;
      List<BuildingLocation> locations = this.townHall.getLocations();

      for (ConstructionIP cip : this.townHall.getConstructionsInProgress()) {
         if (cip.getBuildingLocation() != null) {
            BuildingLocation bl = cip.getBuildingLocation();
            int left = Math.max(0, bl.minx - minfo.mapStartX);
            int top = Math.max(0, bl.minz - minfo.mapStartZ);
            int right = Math.min(minfo.length - 1, bl.maxx + 1 - minfo.mapStartX);
            int bottom = Math.min(minfo.width - 1, bl.maxz + 1 - minfo.mapStartZ);
            if (left < right && top < bottom) {
               float screenLeft = xStart + this.scaledStartX + left * this.scaleFactor;
               float screenRight = xStart + this.scaledStartX + right * this.scaleFactor;
               float screenTop = yStart + this.scaledStartY + top * this.scaleFactor;
               float screenBottom = yStart + this.scaledStartY + bottom * this.scaleFactor;
               if (i >= screenLeft && i <= screenRight && j >= screenTop && j <= screenBottom) {
                  locHover = bl;
               }

               this.drawScaledRect(left, top, right, bottom, 1090453759);

               for (int x = left; x < right; x++) {
                  Arrays.fill(minfo.data[x], top, bottom, (byte)11);
               }
            }
         }
      }

      for (BuildingLocation bl : locations) {
         if (!bl.isSubBuildingLocation) {
            int left = Math.max(0, bl.minx - minfo.mapStartX);
            int top = Math.max(0, bl.minz - minfo.mapStartZ);
            int right = Math.min(minfo.length - 1, bl.maxx + 1 - minfo.mapStartX);
            int bottom = Math.min(minfo.width - 1, bl.maxz + 1 - minfo.mapStartZ);
            if (left < right && top < bottom) {
               float screenLeft = xStart + this.scaledStartX + left * this.scaleFactor;
               float screenRight = xStart + this.scaledStartX + right * this.scaleFactor;
               float screenTop = yStart + this.scaledStartY + top * this.scaleFactor;
               float screenBottom = yStart + this.scaledStartY + bottom * this.scaleFactor;
               if (i >= screenLeft && i <= screenRight && j >= screenTop && j <= screenBottom) {
                  locHover = bl;
               }

               if (bl.level < 0) {
                  this.drawScaledRect(left, top, right, bottom, 1073741920);
               } else {
                  this.drawScaledRect(left, top, right, bottom, 1073742079);
               }

               for (int x = left; x < right; x++) {
                  Arrays.fill(minfo.data[x], top, bottom, (byte)11);
               }
            }
         }
      }

      for (int x = 0; x < minfo.length; x++) {
         int lastColour = 0;
         int lastZ = 0;

         for (int z = 0; z < minfo.width; z++) {
            int colour = 0;
            byte groundType = minfo.data[x][z];
            if (groundType == 11) {
               colour = 0;
            } else if (groundType == 1) {
               colour = -1439682305;
            } else if (groundType == 2) {
               colour = 1090453504;
            } else if (groundType == 3) {
               colour = 1090518784;
            } else if (groundType == 4) {
               colour = 1090486336;
            } else if (groundType == 5) {
               colour = 268500736;
            } else if (groundType == 10) {
               colour = 1082163328;
            } else if (groundType == 6) {
               colour = 1090474064;
            } else if (groundType == 7) {
               colour = Integer.MIN_VALUE;
            } else if (groundType == 8) {
               colour = 1083834265;
            } else {
               colour = 1073807104;
            }

            if (z == 0) {
               lastColour = colour;
            } else if (colour != lastColour) {
               if (lastColour != 0) {
                  this.drawScaledRect(x, lastZ, x + 1, z, lastColour);
               }

               lastColour = colour;
               lastZ = z;
            }
         }

         if (lastColour != 0) {
            this.drawScaledRect(x, lastZ, x + 1, minfo.width, lastColour);
         }
      }

      for (MillVillager villager : this.townHall.getKnownVillagers()) {
         int mapPosX = (int)(villager.getX() - minfo.mapStartX);
         int mapPosZ = (int)(villager.getZ() - minfo.mapStartZ);
         if (mapPosX > 0 && mapPosZ > 0 && mapPosX < minfo.length && mapPosZ < minfo.width) {
            if (villager.isChild()) {
               this.drawScaledRect(mapPosX - 1, mapPosZ - 1, mapPosX + 1, mapPosZ + 1, -1593835776);
            } else if (villager.getRecord() != null && villager.getRecord().raidingVillage) {
               this.drawScaledRect(mapPosX - 1, mapPosZ - 1, mapPosX + 1, mapPosZ + 1, -1610612736);
            } else if (villager.gender == 1) {
               this.drawScaledRect(mapPosX - 1, mapPosZ - 1, mapPosX + 1, mapPosZ + 1, -1610547201);
            } else {
               this.drawScaledRect(mapPosX - 1, mapPosZ - 1, mapPosX + 1, mapPosZ + 1, -1593901056);
            }

            int screenPosX = (int)(xStart + this.scaledStartX + mapPosX * this.scaleFactor);
            int screenPosY = (int)(yStart + this.scaledStartY + mapPosZ * this.scaleFactor);
            if (screenPosX > i - 2 && screenPosX < i + 2 && screenPosY > j - 2 && screenPosY < j + 2) {
               villagerHover = villager;
            }
         }
      }

      int mapPosX = (int)(this.player.getX() - minfo.mapStartX);
      int mapPosZ = (int)(this.player.getZ() - minfo.mapStartZ);
      if (mapPosX > 0 && mapPosZ > 0 && mapPosX < minfo.length && mapPosZ < minfo.width) {
         this.drawScaledRect(mapPosX - 1, mapPosZ - 1, mapPosX + 2, mapPosZ + 2, -1593835521);
         int screenPosX = (int)(xStart + this.scaledStartX + mapPosX * this.scaleFactor);
         int screenPosY = (int)(yStart + this.scaledStartY + mapPosZ * this.scaleFactor);
         if (screenPosX > i - 2 && screenPosX < i + 3 && screenPosY > j - 2 && screenPosY < j + 3) {
            playerHover = this.player;
         }
      }

      if (villagerHover != null) {
         int stringlength = this.fontRenderer.width(villagerHover.getVillagerName());
         stringlength = Math.max(stringlength, this.fontRenderer.width(villagerHover.getNativeOccupationName()));
         boolean gameString = villagerHover.getGameOccupationName(this.player.getName().getString()) != null
            && villagerHover.getGameOccupationName(this.player.getName().getString()).length() > 0;
         if (gameString) {
            stringlength = Math.max(stringlength, this.fontRenderer.width(villagerHover.getGameOccupationName(this.player.getName().getString())));
            this.drawGradientRect(i + 10 - 3, j + 10 - 3, i + 10 + stringlength + 3, j + 10 + 33, -1073741824, -1073741824);
            this.drawString(villagerHover.getVillagerName(), i + 10, j + 10, 9474192);
            this.drawString(villagerHover.getNativeOccupationName(), i + 10, j + 10 + 11, 9474192);
            this.drawString(villagerHover.getGameOccupationName(this.player.getName().getString()), i + 10, j + 10 + 22, 9474192);
         } else {
            this.drawGradientRect(i + 10 - 3, j + 10 - 3, i + 10 + stringlength + 3, j + 10 + 22, -1073741824, -1073741824);
            this.drawString(villagerHover.getVillagerName(), i + 10, j + 10, 9474192);
            this.drawString(villagerHover.getNativeOccupationName(), i + 10, j + 10 + 11, 9474192);
         }
      } else if (playerHover != null) {
         int stringlength = this.fontRenderer.width(playerHover.getName().getString());
         this.drawGradientRect(i + 10 - 3, j + 10 - 3, i + 10 + stringlength + 3, j + 10 + 11, -1073741824, -1073741824);
         this.drawString(playerHover.getName().getString(), i + 10, j + 10, 9474192);
      } else if (locHover != null) {
         Building b = locHover.getBuilding(this.townHall.world);
         boolean unreachable = b != null && this.townHall.regionMapper != null && !b.isReachableFromRegion(this.townHall.regionMapper.thRegion);
         int stringlength;
         String nativeString;
         if (unreachable) {
            stringlength = this.fontRenderer.width(locHover.getNativeName() + " - " + LanguageUtilities.string("panels.unreachablebuilding"));
            nativeString = locHover.getNativeName() + " - " + LanguageUtilities.string("panels.unreachablebuilding");
         } else {
            stringlength = this.fontRenderer.width(locHover.getNativeName());
            nativeString = locHover.getNativeName();
         }

         int nblines = 1;
         boolean gameString = locHover.getGameName() != null && locHover.getGameName().length() > 0;
         if (gameString) {
            stringlength = Math.max(stringlength, this.fontRenderer.width(locHover.getGameName()));
            nblines++;
         }

         List<String> effects = locHover.getBuildingEffects(this.townHall.world);
         nblines += effects.size();

         for (String s : effects) {
            stringlength = Math.max(stringlength, this.fontRenderer.width(s));
         }

         this.drawGradientRect(i - 3, j - 3, i + stringlength + 3, j + 11 * nblines, -1073741824, -1073741824);
         this.drawString(nativeString, i, j, 9474192);
         int pos = 1;
         if (gameString) {
            this.drawString(locHover.getGameName(), i, j + 11, 9474192);
            pos++;
         }

         for (String s : effects) {
            this.drawString(s, i, j + 11 * pos, 9474192);
            pos++;
         }
      }

   }

   @Override
   public Identifier getPNGPath() {
      return this.isParchment ? this.backgroundParchment : this.backgroundPanel;
   }

   @Override
   public void initData() {
      this.textBook = this.bookManager.adjustTextBookLineLength(this.textBook);
      if (this.mapType == 1 && this.townHall != null) {
         ClientSender.requestMapInfo(this.townHall);
      }
   }

   public void updateScreen() {
   }
}
