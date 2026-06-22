package org.millenaire.common.entity;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.Font;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.millenaire.client.book.BookManager;
import org.millenaire.client.book.TextBook;
import org.millenaire.client.gui.text.GuiText;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.buildingmanagers.PanelContentGenerator;

public class TileEntityPanel extends BlockEntity {
   public static final int MAP_VILLAGE_MAP = 1;
   public static final int ETAT_CIVIL = 1;
   public static final int CONSTRUCTIONS = 2;
   public static final int PROJECTS = 3;
   public static final int CONTROLLED_PROJECTS = 4;
   public static final int HOUSE = 5;
   public static final int RESOURCES = 6;
   public static final int ARCHIVES = 7;
   public static final int VILLAGE_MAP = 8;
   public static final int MILITARY = 9;
   public static final int INN_TRADE_GOODS = 10;
   public static final int INN_VISITORS = 11;
   public static final int MARKET_MERCHANTS = 12;
   public static final int CONTROLLED_MILITARY = 13;
   public static final int VISITORS = 14;
   public static final int WALLS = 15;
   public static final int MARVEL_DONATIONS = 20;
   public static final int MARVEL_RESOURCES = 21;
   public List<TileEntityPanel.PanelUntranslatedLine> untranslatedLines = new ArrayList<>();
   public List<TileEntityPanel.PanelDisplayLine> displayLines = new ArrayList<>();
   public Point buildingPos = null;
   public long villager_id = 0L;
   public int panelType = 0;
   public Identifier texture = null;

   public TileEntityPanel(BlockPos pos, BlockState state) {
      super(MillBlockEntities.PANEL, pos, state);
   }

   public TextBook getFullText(Player player) {
      if (this.panelType != 0 && this.buildingPos != null) {
         Building building = Mill.clientWorld.getBuilding(this.buildingPos);
         if (this.panelType == 1) {
            return PanelContentGenerator.generateEtatCivil(building);
         } else if (this.panelType == 2) {
            return PanelContentGenerator.generateConstructions(building);
         } else if (this.panelType == 3) {
            return PanelContentGenerator.generateProjects(player, building);
         } else if (this.panelType == 5) {
            return PanelContentGenerator.generateHouse(building);
         } else if (this.panelType == 7) {
            return PanelContentGenerator.generateArchives(building, this.villager_id);
         } else if (this.panelType == 6) {
            return PanelContentGenerator.generateResources(building);
         } else if (this.panelType == 8) {
            return PanelContentGenerator.generateVillageMap(building);
         } else if (this.panelType == 9) {
            return PanelContentGenerator.generateMilitary(building);
         } else if (this.panelType == 10) {
            return PanelContentGenerator.generateInnGoods(building);
         } else if (this.panelType == 11) {
            return PanelContentGenerator.generateInnVisitors(building);
         } else if (this.panelType == 12) {
            return PanelContentGenerator.generateVisitors(building, true);
         } else if (this.panelType == 14) {
            return PanelContentGenerator.generateVisitors(building, false);
         } else if (this.panelType == 15) {
            return PanelContentGenerator.generateWalls(player, building);
         } else if (this.panelType == 20) {
            return building.getMarvelManager().generateDonationPanelText();
         } else {
            return this.panelType == 21 ? building.getMarvelManager().generateResourcesPanelText() : null;
         }
      } else {
         return null;
      }
   }

   public int getMapType() {
      return this.panelType == 8 ? 1 : 0;
   }

   private BlockState getState() {
      return this.level.getBlockState(this.getBlockPos());
   }

   private String translatedLines_cutLines(Font fontrenderer, String text, int maxLength) {
      if (fontrenderer.width(text) > maxLength) {
         while (fontrenderer.width(text + "...") > maxLength) {
            text = text.substring(0, text.length() - 1);
         }

         text = text + "...";
      }

      return text;
   }

   @Environment(EnvType.CLIENT)
   public void translateLines(Font fontrenderer) {
      this.displayLines.clear();
      int nbExtraLines = 0;

      for (TileEntityPanel.PanelUntranslatedLine line : this.untranslatedLines) {
         TileEntityPanel.PanelDisplayLine displayLine = new TileEntityPanel.PanelDisplayLine();
         displayLine.leftIcon = line.leftIcon;
         displayLine.middleIcon = line.middleIcon;
         displayLine.rightIcon = line.rightIcon;
         displayLine.fullLine = LanguageUtilities.string(line.fullLine);
         displayLine.leftColumn = LanguageUtilities.string(line.leftColumn);
         displayLine.rightColumn = LanguageUtilities.string(line.rightColumn);
         displayLine.centerLine = line.centerLine;
         int maxLength = 80;
         if (displayLine.leftIcon.getItem() != Items.AIR) {
            maxLength = 62;
         }

         displayLine.leftColumn = this.translatedLines_cutLines(fontrenderer, displayLine.leftColumn, 32);
         displayLine.rightColumn = this.translatedLines_cutLines(fontrenderer, displayLine.rightColumn, 32);
         List<String> splitStrings = BookManager.splitStringByLength(new GuiText.FontRendererWrapped(fontrenderer), displayLine.fullLine, maxLength);
         displayLine.fullLine = splitStrings.get(0);
         this.displayLines.add(displayLine);
         if (splitStrings.size() > 1 && this.untranslatedLines.size() + nbExtraLines + 1 < 8) {
            TileEntityPanel.PanelDisplayLine extraDisplayLine = new TileEntityPanel.PanelDisplayLine();
            extraDisplayLine.fullLine = splitStrings.get(1);
            this.displayLines.add(extraDisplayLine);
            nbExtraLines++;
         }
      }
   }

   public void triggerUpdate() {
      // 26.2: Level.sendBlockUpdated re-syncs the block-entity to nearby clients (via getUpdatePacket /
      // getUpdateTag below), replacing the 1.12 markBlockRangeForRenderUpdate + notifyBlockUpdate combo.
      // The 1.12 scheduleBlockUpdate was a render-timing nicety and is not needed here.
      BlockState state = this.getState();
      this.level.sendBlockUpdated(this.getBlockPos(), state, state, 3);
      this.setChanged();
   }

   // --- NBT + sync (26.2 ValueInput/ValueOutput) ---

   @Override
   protected void loadAdditional(ValueInput input) {
      super.loadAdditional(input);
      this.buildingPos = TileEntityLockedChest.readPoint(input, "buildingPos");
      this.panelType = input.getIntOr("panelType", 0);
      this.villager_id = input.getLongOr("villager_id", 0L);
      input.getString("texture").ifPresent(s -> this.texture = Identifier.parse(s));
      this.untranslatedLines.clear();
      for (int i = 0; ; i++) {
         CompoundTag lineTag = input.read("Lines_" + i, CompoundTag.CODEC).orElse(null);
         if (lineTag == null) {
            break;
         }
         this.untranslatedLines.add(TileEntityPanel.PanelUntranslatedLine.readFromNBT(lineTag));
      }
   }

   @Override
   protected void saveAdditional(ValueOutput output) {
      super.saveAdditional(output);
      try {
         TileEntityLockedChest.writePoint(output, "buildingPos", this.buildingPos);
         output.putInt("panelType", this.panelType);
         output.putLong("villager_id", this.villager_id);
         if (this.texture != null) {
            output.putString("texture", this.texture.toString());
         }
         for (int i = 0; i < this.untranslatedLines.size(); i++) {
            output.store("Lines_" + i, CompoundTag.CODEC, this.untranslatedLines.get(i).writeToNBT(new CompoundTag()));
         }
      } catch (Exception var3) {
         // Phase-2 FLAG (save/NBT WRITE): swallowing here silently corrupts/loses the panel on save. WRITE
         // failures are real bugs (not missing-field READ compat, which stays Phase 4). Surface it.
         throw MillCrash.fail("Entity", "TileEntityPanel.saveAdditional: panel NBT write failed at " + this.getBlockPos() + ": " + var3);
      }
   }

   @Override
   public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
      return this.saveWithoutMetadata(registries);
   }

   @Nullable
   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public static class PanelDisplayLine {
      public String fullLine = "";
      public String leftColumn = "";
      public String rightColumn = "";
      public ItemStack leftIcon = ItemStack.EMPTY;
      public ItemStack middleIcon = ItemStack.EMPTY;
      public ItemStack rightIcon = ItemStack.EMPTY;
      public boolean centerLine = true;
   }

   public static class PanelPacketInfo {
      public Point pos;
      public Point buildingPos;
      public String[][] lines;
      public long villager_id;
      public int panelType;

      public PanelPacketInfo(Point pos, String[][] lines, Point buildingPos, int panelType, long village_id) {
         this.pos = pos;
         this.lines = lines;
         this.buildingPos = buildingPos;
         this.panelType = panelType;
         this.villager_id = village_id;
      }
   }

   public static class PanelUntranslatedLine {
      private String[] fullLine = new String[]{""};
      private String[] leftColumn = new String[]{""};
      public String[] rightColumn = new String[]{""};
      public ItemStack leftIcon = ItemStack.EMPTY;
      public ItemStack middleIcon = ItemStack.EMPTY;
      public ItemStack rightIcon = ItemStack.EMPTY;
      public boolean centerLine = true;

      public static TileEntityPanel.PanelUntranslatedLine readFromNBT(CompoundTag compound) {
         TileEntityPanel.PanelUntranslatedLine line = new TileEntityPanel.PanelUntranslatedLine();
         line.fullLine = readText(compound, "fullLine");
         line.leftColumn = readText(compound, "leftColumn");
         line.rightColumn = readText(compound, "rightColumn");
         line.leftIcon = compound.read("leftIcon", ItemStack.CODEC).orElse(ItemStack.EMPTY);
         line.middleIcon = compound.read("middleIcon", ItemStack.CODEC).orElse(ItemStack.EMPTY);
         line.rightIcon = compound.read("rightIcon", ItemStack.CODEC).orElse(ItemStack.EMPTY);
         line.centerLine = compound.getBooleanOr("centerLine", false);
         return line;
      }

      private static String[] readText(CompoundTag compound, String key) {
         List<String> lineFragment = new ArrayList<>();

         for (int i = 0; compound.contains(key + "_" + i); i++) {
            lineFragment.add(compound.getStringOr(key + "_" + i, ""));
         }

         return lineFragment.toArray(new String[0]);
      }

      private static void writeText(CompoundTag compound, String[] text, String key) {
         for (int i = 0; i < text.length; i++) {
            compound.putString(key + "_" + i, text[i]);
         }
      }

      public void setFullLine(String[] fullLine) {
         this.fullLine = fullLine;

         for (int i = 0; i < this.fullLine.length; i++) {
            if (this.fullLine[i] == null) {
               this.fullLine[i] = "";
            }
         }
      }

      public void setLeftColumn(String[] leftColumn) {
         this.leftColumn = leftColumn;

         for (int i = 0; i < this.leftColumn.length; i++) {
            if (this.leftColumn[i] == null) {
               this.leftColumn[i] = "";
            }
         }
      }

      public void setRightColumn(String[] rightColumn) {
         this.rightColumn = rightColumn;

         for (int i = 0; i < this.rightColumn.length; i++) {
            if (this.rightColumn[i] == null) {
               this.rightColumn[i] = "";
            }
         }
      }

      public CompoundTag writeToNBT(CompoundTag compound) {
         writeText(compound, this.fullLine, "fullLine");
         writeText(compound, this.leftColumn, "leftColumn");
         writeText(compound, this.rightColumn, "rightColumn");
         if (!this.leftIcon.isEmpty()) {
            compound.store("leftIcon", ItemStack.CODEC, this.leftIcon);
         }
         if (!this.middleIcon.isEmpty()) {
            compound.store("middleIcon", ItemStack.CODEC, this.middleIcon);
         }
         if (!this.rightIcon.isEmpty()) {
            compound.store("rightIcon", ItemStack.CODEC, this.rightIcon);
         }
         compound.putBoolean("centerLine", this.centerLine);
         return compound;
      }
   }
}

