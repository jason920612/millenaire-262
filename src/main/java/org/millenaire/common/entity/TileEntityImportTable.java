package org.millenaire.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.millenaire.client.network.ClientSender;
import org.millenaire.common.buildingplan.BuildingImportExport;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;

public class TileEntityImportTable extends BlockEntity {
   private String buildingKey = null;
   private int variation = 0;
   private int upgradeLevel = 0;
   private int length;
   private int width;
   private int startingLevel = -1;
   private int orientation = 0;
   private boolean exportSnow = false;
   private boolean importMockBlocks = true;
   private boolean autoconvertToPreserveGround = true;
   private boolean exportRegularChests = false;
   private Point parentTablePos = null;

   public TileEntityImportTable(BlockPos pos, BlockState state) {
      super(MillBlockEntities.IMPORT_TABLE, pos, state);
   }

   public void activate(Player player) {
      ItemStack held = player.getMainHandItem();
      if (held != ItemStack.EMPTY && held.getItem() == MillItems.SUMMONING_WAND) {
         if (player.level().isClientSide() && this.buildingKey != null) {
            MillLog.temp(this, "Activating. Building key: " + this.buildingKey);
            ClientSender.importTableImportBuildingPlan(
               player,
               new Point(this.getBlockPos()),
               BuildingImportExport.EXPORT_DIR,
               this.buildingKey,
               false,
               this.variation,
               this.upgradeLevel,
               this.orientation,
               this.importMockBlocks
            );
         } else {
            this.sendUpdates();
         }
      } else if (held != ItemStack.EMPTY && held.getItem() == MillItems.NEGATION_WAND) {
         if (player.level().isClientSide() && this.buildingKey != null) {
            BuildingImportExport.importTableExportBuildingPlan(player.level(), this, this.getUpgradeLevel());
         } else {
            this.sendUpdates();
         }
      } else if (!player.level().isClientSide()) {
         this.sendUpdates();
         ServerSender.displayImportTableGUI(player, new Point(this.getBlockPos()));
      }
   }

   public boolean autoconvertToPreserveGround() {
      return this.autoconvertToPreserveGround;
   }

   public boolean exportRegularChests() {
      return this.exportRegularChests;
   }

   public boolean exportSnow() {
      return this.exportSnow;
   }

   public String getBuildingKey() {
      return this.buildingKey;
   }

   public int getLength() {
      return this.length;
   }

   public int getOrientation() {
      return this.orientation;
   }

   public Point getParentTablePos() {
      return this.parentTablePos;
   }

   public Point getPosPoint() {
      return new Point(this.getBlockPos());
   }

   public int getStartingLevel() {
      return this.startingLevel;
   }

   private BlockState getState() {
      return this.level.getBlockState(this.getBlockPos());
   }

   public int getUpgradeLevel() {
      return this.upgradeLevel;
   }

   public int getVariation() {
      return this.variation;
   }

   public int getWidth() {
      return this.width;
   }

   public boolean importMockBlocks() {
      return this.importMockBlocks;
   }

   private void sendUpdates() {
      // 26.2: Level.sendBlockUpdated re-syncs the block-entity to nearby clients (getUpdatePacket /
      // getUpdateTag below), replacing the 1.12 markBlockRangeForRenderUpdate + notifyBlockUpdate.
      BlockState state = this.getState();
      this.level.sendBlockUpdated(this.getBlockPos(), state, state, 3);
      this.setChanged();
   }

   public void setAutoconvertToPreserveGround(boolean autoconvertToPreserveGround) {
      this.autoconvertToPreserveGround = autoconvertToPreserveGround;
   }

   public void setBuildingKey(String buildingKey) {
      this.buildingKey = buildingKey;
   }

   public void setExportRegularChests(boolean exportRegularChests) {
      this.exportRegularChests = exportRegularChests;
   }

   public void setExportSnow(boolean exportSnow) {
      this.exportSnow = exportSnow;
   }

   public void setImportMockBlocks(boolean importMockBlocks) {
      this.importMockBlocks = importMockBlocks;
   }

   public void setLength(int length) {
      this.length = length;
   }

   public void setOrientation(int orientation) {
      this.orientation = orientation;
   }

   public void setParentTablePos(Point parentTablePos) {
      this.parentTablePos = parentTablePos;
   }

   public void setStartingLevel(int startingLevel) {
      this.startingLevel = startingLevel;
   }

   public void setUpgradeLevel(int upgradeLevel) {
      this.upgradeLevel = upgradeLevel;
   }

   private void updateAttachedSign() {
      // 1.12: placed a generic WALL_SIGN at -1,0,0 and wrote 3 lines via TileEntitySign.signText[].
      // 26.2: WALL_SIGN → OAK_WALL_SIGN, BlockWallSign.FACING → WallSignBlock.FACING, and sign text is
      // a SignText written through setMessage(line, Component)/setText(text, isFrontText).
      Point signPos = new Point(this.getBlockPos()).getRelative(-1.0, 0.0, 0.0);
      signPos.setBlockState(
         this.level,
         net.minecraft.world.level.block.Blocks.OAK_WALL_SIGN.defaultBlockState()
            .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, net.minecraft.core.Direction.WEST)
      );
      SignBlockEntity sign = signPos.getSign(this.level);
      if (sign == null) {
         return;
      }

      net.minecraft.world.level.block.entity.SignText text = sign.getFrontText();
      String line0 = this.buildingKey != null
         ? this.buildingKey + "_" + (char)(65 + this.variation) + this.upgradeLevel
         : "";
      text = text.setMessage(0, net.minecraft.network.chat.Component.literal(line0));
      text = text.setMessage(1, net.minecraft.network.chat.Component.literal("Start level: " + this.startingLevel));
      text = text.setMessage(2, net.minecraft.network.chat.Component.literal(this.length + "x" + this.width));
      sign.setText(text, true);
      sign.setChanged();
      BlockState signState = this.level.getBlockState(sign.getBlockPos());
      this.level.sendBlockUpdated(sign.getBlockPos(), signState, signState, 3);
   }

   public void updatePlan(String buildingKey, int length, int width, int variation, int level, int startLevel, Player player) {
      this.buildingKey = buildingKey;
      MillLog.temp(this, "updatePlan : Updating buildingKey to: " + buildingKey);
      this.length = length;
      this.width = width;
      this.variation = variation;
      this.upgradeLevel = level;
      this.startingLevel = startLevel;
      this.updateAttachedSign();
      if (player != null) {
         this.sendUpdates();
      }
   }

   public void updateSettings(
      int upgradeLevel,
      int orientation,
      int startingLevel,
      boolean exportSnow,
      boolean importMockBlocks,
      boolean autoconvertToPreserveGround,
      boolean exportRegularChests,
      Player player
   ) {
      this.upgradeLevel = upgradeLevel;
      this.orientation = orientation;
      this.startingLevel = startingLevel;
      this.exportSnow = exportSnow;
      this.importMockBlocks = importMockBlocks;
      this.autoconvertToPreserveGround = autoconvertToPreserveGround;
      this.exportRegularChests = exportRegularChests;
      this.updateAttachedSign();
      if (player != null) {
         this.sendUpdates();
      }
   }

   // --- NBT + sync ---

   @Override
   protected void loadAdditional(ValueInput input) {
      super.loadAdditional(input);
      this.buildingKey = input.getStringOr("buildingKey", "");
      this.variation = input.getIntOr("variation", 0);
      this.length = input.getIntOr("length", 0);
      this.width = input.getIntOr("width", 0);
      this.upgradeLevel = input.getIntOr("upgradeLevel", 0);
      this.startingLevel = input.getIntOr("startingLevel", 0);
      this.orientation = input.getIntOr("orientation", 0);
      this.exportSnow = input.getBooleanOr("exportSnow", false);
      this.importMockBlocks = input.getBooleanOr("importMockBlocks", false);
      this.autoconvertToPreserveGround = input.getBooleanOr("autoconvertToPreserveGround", false);
      this.exportRegularChests = input.getBooleanOr("exportRegularChests", false);
      this.parentTablePos = TileEntityLockedChest.readPoint(input, "parentTablePos");
   }

   @Override
   protected void saveAdditional(ValueOutput output) {
      super.saveAdditional(output);
      if (this.buildingKey != null) {
         output.putString("buildingKey", this.buildingKey);
      }
      output.putInt("variation", this.variation);
      output.putInt("length", this.length);
      output.putInt("width", this.width);
      output.putInt("upgradeLevel", this.upgradeLevel);
      output.putInt("startingLevel", this.startingLevel);
      output.putInt("orientation", this.orientation);
      output.putBoolean("exportSnow", this.exportSnow);
      output.putBoolean("importMockBlocks", this.importMockBlocks);
      output.putBoolean("autoconvertToPreserveGround", this.autoconvertToPreserveGround);
      output.putBoolean("exportRegularChests", this.exportRegularChests);
      TileEntityLockedChest.writePoint(output, "parentTablePos", this.parentTablePos);
   }

   @Override
   public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
      return this.saveWithoutMetadata(registries);
   }

   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
