package org.millenaire.common.village;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.millenaire.common.buildingplan.BuildingBlock;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

public class ConstructionIP {
   private final Building townHall;
   private BuildingBlock[] bblocks = null;
   private int bblocksPos = 0;
   private final int id;
   private BuildingLocation buildingLocation;
   private boolean bblocksChanged = false;
   private final boolean wallConstruction;
   private MillVillager builder = null;

   public ConstructionIP(Building th, int id, boolean wallConstruction) {
      this.townHall = th;
      this.id = id;
      this.wallConstruction = wallConstruction;
   }

   public boolean areBlocksLeft() {
      return this.bblocks == null ? false : this.bblocksPos < this.bblocks.length;
   }

   public void clearBblocks() {
      this.bblocks = null;
   }

   public void clearBuildingLocation() {
      this.buildingLocation = null;
   }

   public BuildingBlock[] getBblocks() {
      return this.bblocks;
   }

   public int getBblocksPos() {
      return this.bblocksPos;
   }

   public MillVillager getBuilder() {
      return this.builder;
   }

   public BuildingLocation getBuildingLocation() {
      return this.buildingLocation;
   }

   public BuildingBlock getCurrentBlock() {
      if (this.bblocks == null) {
         return null;
      } else {
         return this.bblocksPos >= this.bblocks.length ? null : this.bblocks[this.bblocksPos];
      }
   }

   public int getId() {
      return this.id;
   }

   public void incrementBblockPos() {
      this.bblocksPos++;
      if (!this.areBlocksLeft()) {
         this.bblocks = null;
         this.bblocksPos = 0;
         this.bblocksChanged = true;
      }
   }

   public boolean isBblocksChanged() {
      return this.bblocksChanged;
   }

   public boolean isWallConstruction() {
      return this.wallConstruction;
   }

   public void readBblocks() {
      File buildingsDir = MillCommonUtilities.getBuildingsDir(this.townHall.world);
      File bblocksFile = new File(buildingsDir, this.townHall.getPos().getPathString() + "_bblocks_" + this.id + ".bin");
      if (bblocksFile.exists()) {
         try (FileInputStream fileStream = new FileInputStream(bblocksFile);
              DataInputStream dataStream = new DataInputStream(fileStream)) {
            int blockCount = dataStream.readInt();
            this.bblocks = new BuildingBlock[blockCount];

            for (int i = 0; i < blockCount; i++) {
               Point p = new Point(dataStream.readInt(), dataStream.readShort(), dataStream.readInt());
               BuildingBlock b = new BuildingBlock(p, dataStream);
               this.bblocks[i] = b;
            }
         } catch (Exception readError) {
            // Persisted construction-block file is corrupt: fail loudly rather than
            // silently dropping the building's in-progress construction blocks.
            throw MillCrash.fail("Save", "reading bblocks for " + this.townHall.getPos() + " id " + this.id + ": " + readError);
         }

         if (this.bblocks.length == 0) {
            MillLog.error(this, "Saved bblocks had zero elements. Rushing construction.");

            try {
               this.townHall.rushCurrentConstructions(false);
            } catch (Exception rushError) {
               MillLog.printException("Exception when trying to rush building:", rushError);
            }
         }
      }
   }

   public void setBblockPos(int pos) {
      this.bblocksPos = pos;
   }

   public void setBuilder(MillVillager builder) {
      this.builder = builder;
   }

   public void setBuildingLocation(BuildingLocation buildingLocation) {
      this.buildingLocation = buildingLocation;
   }

   public void startNewConstruction(BuildingLocation bl, BuildingBlock[] bblocks) {
      this.buildingLocation = bl;
      this.bblocks = bblocks;
      this.bblocksPos = 0;
      this.bblocksChanged = true;
      if (this.townHall.winfo != null) {
         this.townHall.winfo.addBuildingLocationToMap(bl);
      }
   }

   public void writeBblocks() {
      File buildingsDir = MillCommonUtilities.getBuildingsDir(this.townHall.world);
      File blocksFile = new File(buildingsDir, this.townHall.getPos().getPathString() + "_bblocks_" + this.id + ".bin");
      BuildingBlock[] blocks = this.getBblocks();
      if (blocks != null) {
         try (FileOutputStream fileStream = new FileOutputStream(blocksFile);
              DataOutputStream dataStream = new DataOutputStream(fileStream)) {
            dataStream.writeInt(blocks.length);

            for (int i = 0; i < blocks.length; i++) {
               BuildingBlock b = blocks[i];
               dataStream.writeInt(b.p.getiX());
               dataStream.writeShort(b.p.getiY());
               dataStream.writeInt(b.p.getiZ());
               dataStream.writeInt(WorldUtilities.getBlockId(b.block));
               dataStream.writeByte(b.getMeta());
               dataStream.writeByte(b.special);
            }
         } catch (IOException writeError) {
            // A swallowed write leaves a truncated bblocks file on disk that will be
            // mis-read on next load: fail loudly so the save is not silently corrupted.
            throw MillCrash.fail("Save", "writing bblocks for " + this.townHall.getPos() + " id " + this.id + ": " + writeError);
         }
      } else {
         try {
            if (blocksFile.exists()) {
               Files.delete(blocksFile.toPath());
            }
         } catch (IOException deleteError) {
            throw MillCrash.fail("Save", "deleting stale bblocks for " + this.townHall.getPos() + " id " + this.id + ": " + deleteError);
         }
      }

      this.bblocksChanged = false;
   }
}
