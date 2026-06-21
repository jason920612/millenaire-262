package org.millenaire.common.forge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.village.Building;

/**
 * Force-loads the chunks around a town hall. 1.12 used Forge's {@code ForgeChunkManager}
 * ticket system; on 26.2 this uses vanilla {@link ServerLevel#setChunkForced(int, int, boolean)}
 * and tracks the forced chunks itself (no tickets / LoadingCallback).
 */
public class BuildingChunkLoader {
   Building townHall;
   private final List<ChunkPos> forcedChunks = new ArrayList<>();
   public boolean chunksLoaded = false;

   public BuildingChunkLoader(Building th) {
      this.townHall = th;
   }

   public void loadChunks() {
      if (this.townHall.winfo != null && this.townHall.world instanceof ServerLevel serverLevel) {
         int nbLoaded = 0;

         for (int cx = this.townHall.winfo.chunkStartX - 1; cx < this.townHall.winfo.chunkStartX + this.townHall.winfo.length / 16 + 1; cx++) {
            for (int cz = this.townHall.winfo.chunkStartZ - 1; cz < this.townHall.winfo.chunkStartZ + this.townHall.winfo.width / 16 + 1; cz++) {
               serverLevel.setChunkForced(cx, cz, true);
               this.forcedChunks.add(new ChunkPos(cx, cz));
               nbLoaded++;
            }
         }

         this.chunksLoaded = true;
         if (MillConfigValues.LogChunkLoader >= 1) {
            MillLog.major(this.townHall, "Force-Loaded " + nbLoaded + " chunks.");
         }
      }
   }

   public void unloadChunks() {
      if (this.townHall.world instanceof ServerLevel serverLevel) {
         for (ChunkPos pos : this.forcedChunks) {
            serverLevel.setChunkForced(pos.x(), pos.z(), false);
         }
      }

      this.forcedChunks.clear();
      this.chunksLoaded = false;
      if (MillConfigValues.LogChunkLoader >= 1) {
         MillLog.major(this.townHall, "Unloaded the chunks.");
      }
   }
}
