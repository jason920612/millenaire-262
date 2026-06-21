package org.millenaire.common.village.buildingmanagers;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.millenaire.common.block.BlockMillSapling;
import org.millenaire.common.block.BlockSilkWorm;
import org.millenaire.common.block.BlockSnailSoil;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.TileEntityLockedChest;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

public class ResManager {
   public CopyOnWriteArrayList<Point> brickspot = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> chests = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> fishingspots = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> sugarcanesoils = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> healingspots = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> furnaces = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> firepits = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> brewingStands = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> signs = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> banners = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> cultureBanners = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<CopyOnWriteArrayList<Point>> sources = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<BlockState> sourceTypes = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<CopyOnWriteArrayList<Point>> spawns = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Identifier> spawnTypes = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<CopyOnWriteArrayList<Point>> mobSpawners = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Identifier> mobSpawnerTypes = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<CopyOnWriteArrayList<Point>> soils = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Identifier> soilTypes = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> stalls = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> woodspawn = new CopyOnWriteArrayList<>();
   public ConcurrentHashMap<Point, String> woodspawnTypes = new ConcurrentHashMap<>();
   public CopyOnWriteArrayList<Point> netherwartsoils = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> silkwormblock = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> snailsoilblock = new CopyOnWriteArrayList<>();
   public CopyOnWriteArrayList<Point> dispenderUnknownPowder = new CopyOnWriteArrayList<>();
   private Point sleepingPos = null;
   private Point sellingPos = null;
   private Point craftingPos = null;
   private Point defendingPos = null;
   private Point shelterPos = null;
   private Point pathStartPos = null;
   private Point leasurePos = null;
   private final Building building;

   public ResManager(Building b) {
      this.building = b;
   }

   public void addMobSpawnerPoint(Identifier type, Point p) {
      if (!this.mobSpawnerTypes.contains(type)) {
         CopyOnWriteArrayList<Point> spawnsPoint = new CopyOnWriteArrayList<>();
         spawnsPoint.add(p);
         this.mobSpawners.add(spawnsPoint);
         this.mobSpawnerTypes.add(type);
      } else {
         for (int i = 0; i < this.mobSpawnerTypes.size(); i++) {
            if (this.mobSpawnerTypes.get(i).equals(type) && !this.mobSpawners.get(i).contains(p)) {
               this.mobSpawners.get(i).add(p);
            }
         }
      }
   }

   public void addSoilPoint(Identifier type, Point p) {
      if (!this.soilTypes.contains(type)) {
         CopyOnWriteArrayList<Point> spawnsPoint = new CopyOnWriteArrayList<>();
         spawnsPoint.add(p);
         this.soils.add(spawnsPoint);
         this.soilTypes.add(type);
      } else {
         for (int i = 0; i < this.soilTypes.size(); i++) {
            if (this.soilTypes.get(i).equals(type) && !this.soils.get(i).contains(p)) {
               this.soils.get(i).add(p);
            }
         }
      }
   }

   public void addSourcePoint(BlockState blockState, Point p) {
      if (!this.sourceTypes.contains(blockState)) {
         CopyOnWriteArrayList<Point> spawnsPoint = new CopyOnWriteArrayList<>();
         spawnsPoint.add(p);
         this.sources.add(spawnsPoint);
         this.sourceTypes.add(blockState);
      } else {
         for (int i = 0; i < this.sourceTypes.size(); i++) {
            if (this.sourceTypes.get(i).equals(blockState) && !this.sources.get(i).contains(p)) {
               this.sources.get(i).add(p);
            }
         }
      }
   }

   public void addSpawnPoint(Identifier type, Point p) {
      if (!this.spawnTypes.contains(type)) {
         CopyOnWriteArrayList<Point> spawnsPoint = new CopyOnWriteArrayList<>();
         spawnsPoint.add(p);
         this.spawns.add(spawnsPoint);
         this.spawnTypes.add(type);
      } else {
         for (int i = 0; i < this.spawnTypes.size(); i++) {
            if (this.spawnTypes.get(i).equals(type) && !this.spawns.get(i).contains(p)) {
               this.spawns.get(i).add(p);
            }
         }
      }
   }

   public HashMap<InvItem, Integer> getChestsContent() {
      HashMap<InvItem, Integer> contents = new HashMap<>();

      for (Point p : this.chests) {
         TileEntityLockedChest chest = p.getMillChest(this.building.world);
         if (chest != null) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
               ItemStack stack = chest.getItem(i);
               if (stack != null && stack.getItem() != Items.AIR) {
                  InvItem key = InvItem.createInvItem(stack);
                  if (stack != null) {
                     if (contents.containsKey(key)) {
                        contents.put(key, stack.getCount() + contents.get(key));
                     } else {
                        contents.put(key, stack.getCount());
                     }
                  }
               }
            }
         }
      }

      return contents;
   }

   public Point getCocoaHarvestLocation() {
      for (int i = 0; i < this.soilTypes.size(); i++) {
         if (this.soilTypes.get(i).equals(Mill.CROP_CACAO)) {
            for (Point p : this.soils.get(i)) {
               BlockState state = p.getBlockActualState(this.building.world);
               if (state.getBlock() == Blocks.COCOA && (Integer)state.getValue(CocoaBlock.AGE) >= 2) {
                  return p;
               }
            }
         }
      }

      return null;
   }

   public Point getCocoaPlantingLocation() {
      for (int i = 0; i < this.soilTypes.size(); i++) {
         if (this.soilTypes.get(i).equals(Mill.CROP_CACAO)) {
            for (Point p : this.soils.get(i)) {
               if (p.getBlock(this.building.world) == Blocks.AIR) {
                  if (this.isBlockJungleWood(p.getNorth().getBlockActualState(this.building.world))) {
                     return p;
                  }

                  if (this.isBlockJungleWood(p.getEast().getBlockActualState(this.building.world))) {
                     return p;
                  }

                  if (this.isBlockJungleWood(p.getSouth().getBlockActualState(this.building.world))) {
                     return p;
                  }

                  if (this.isBlockJungleWood(p.getWest().getBlockActualState(this.building.world))) {
                     return p;
                  }
               }
            }
         }
      }

      return null;
   }

   public Point getCraftingPos() {
      if (this.craftingPos != null) {
         return this.craftingPos;
      } else {
         return this.sellingPos != null ? this.sellingPos : this.sleepingPos;
      }
   }

   public Point getDefendingPos() {
      if (this.defendingPos != null) {
         return this.defendingPos;
      } else {
         return this.sellingPos != null ? this.sellingPos : this.sleepingPos;
      }
   }

   public Point getEmptyBrickLocation() {
      if (this.brickspot.size() == 0) {
         return null;
      } else {
         for (int i = 0; i < this.brickspot.size(); i++) {
            Point p = this.brickspot.get(i);
            if (WorldUtilities.getBlock(this.building.world, p) == Blocks.AIR) {
               return p;
            }
         }

         return null;
      }
   }

   public Point getFullBrickLocation() {
      if (this.brickspot.size() == 0) {
         return null;
      } else {
         for (int i = 0; i < this.brickspot.size(); i++) {
            Point p = this.brickspot.get(i);
            if (WorldUtilities.getBlockState(this.building.world, p) == MillBlocks.BS_MUD_BRICK) {
               return p;
            }
         }

         return null;
      }
   }

   public Point getLeasurePos() {
      return this.leasurePos != null ? this.leasurePos : this.getSellingPos();
   }

   public int getNbEmptyBrickLocation() {
      if (this.brickspot.size() == 0) {
         return 0;
      } else {
         int nb = 0;

         for (int i = 0; i < this.brickspot.size(); i++) {
            Point p = this.brickspot.get(i);
            if (WorldUtilities.getBlock(this.building.world, p) == Blocks.AIR) {
               nb++;
            }
         }

         return nb;
      }
   }

   public int getNbFullBrickLocation() {
      if (this.brickspot.size() == 0) {
         return 0;
      } else {
         int nb = 0;

         for (int i = 0; i < this.brickspot.size(); i++) {
            Point p = this.brickspot.get(i);
            if (WorldUtilities.getBlockState(this.building.world, p) == MillBlocks.BS_MUD_BRICK) {
               nb++;
            }
         }

         return nb;
      }
   }

   public int getNbNetherWartHarvestLocation() {
      if (this.netherwartsoils.size() == 0) {
         return 0;
      } else {
         int nb = 0;

         for (int i = 0; i < this.netherwartsoils.size(); i++) {
            Point p = this.netherwartsoils.get(i);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.NETHER_WART
               && WorldUtilities.getBlockMeta(this.building.world, p.getAbove()) >= 3) {
               nb++;
            }
         }

         return nb;
      }
   }

   public int getNbNetherWartPlantingLocation() {
      if (this.netherwartsoils.size() == 0) {
         return 0;
      } else {
         int nb = 0;

         for (int i = 0; i < this.netherwartsoils.size(); i++) {
            Point p = this.netherwartsoils.get(i);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.AIR) {
               nb++;
            }
         }

         return nb;
      }
   }

   public int getNbSilkWormHarvestLocation() {
      if (this.silkwormblock.size() == 0) {
         return 0;
      } else {
         int nb = 0;

         for (int i = 0; i < this.silkwormblock.size(); i++) {
            Point p = this.silkwormblock.get(i);
            if (WorldUtilities.getBlockState(this.building.world, p)
               == MillBlocks.SILK_WORM.defaultBlockState().setValue(BlockSilkWorm.PROGRESS, BlockSilkWorm.EnumType.SILKWORMFULL)) {
               nb++;
            }
         }

         return nb;
      }
   }

   public int getNbSnailSoilHarvestLocation() {
      if (this.snailsoilblock.size() == 0) {
         return 0;
      } else {
         int nb = 0;

         for (int i = 0; i < this.snailsoilblock.size(); i++) {
            Point p = this.snailsoilblock.get(i);
            if (WorldUtilities.getBlockState(this.building.world, p)
               == MillBlocks.SNAIL_SOIL.defaultBlockState().setValue(BlockSnailSoil.PROGRESS, BlockSnailSoil.EnumType.SNAIL_SOIL_FULL)) {
               nb++;
            }
         }

         return nb;
      }
   }

   public int getNbSugarCaneHarvestLocation() {
      if (this.sugarcanesoils.size() == 0) {
         return 0;
      } else {
         int nb = 0;

         for (int i = 0; i < this.sugarcanesoils.size(); i++) {
            Point p = this.sugarcanesoils.get(i);
            if (WorldUtilities.getBlock(this.building.world, p.getRelative(0.0, 2.0, 0.0)) == Blocks.SUGAR_CANE) {
               nb++;
            }
         }

         return nb;
      }
   }

   public int getNbSugarCanePlantingLocation() {
      if (this.sugarcanesoils.size() == 0) {
         return 0;
      } else {
         int nb = 0;

         for (int i = 0; i < this.sugarcanesoils.size(); i++) {
            Point p = this.sugarcanesoils.get(i);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.AIR) {
               nb++;
            }
         }

         return nb;
      }
   }

   public Point getNetherWartsHarvestLocation() {
      if (this.netherwartsoils.size() == 0) {
         return null;
      } else {
         int start = MillCommonUtilities.randomInt(this.netherwartsoils.size());

         for (int i = start; i < this.netherwartsoils.size(); i++) {
            Point p = this.netherwartsoils.get(i);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.NETHER_WART
               && WorldUtilities.getBlockMeta(this.building.world, p.getAbove()) == 3) {
               return p;
            }
         }

         for (int ix = 0; ix < start; ix++) {
            Point p = this.netherwartsoils.get(ix);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.NETHER_WART
               && WorldUtilities.getBlockMeta(this.building.world, p.getAbove()) == 3) {
               return p;
            }
         }

         return null;
      }
   }

   public Point getNetherWartsPlantingLocation() {
      if (this.netherwartsoils.size() == 0) {
         return null;
      } else {
         int start = MillCommonUtilities.randomInt(this.netherwartsoils.size());

         for (int i = start; i < this.netherwartsoils.size(); i++) {
            Point p = this.netherwartsoils.get(i);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.AIR
               && WorldUtilities.getBlock(this.building.world, p) == Blocks.SOUL_SAND) {
               return p;
            }
         }

         for (int ix = 0; ix < start; ix++) {
            Point p = this.netherwartsoils.get(ix);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.AIR
               && WorldUtilities.getBlock(this.building.world, p) == Blocks.SOUL_SAND) {
               return p;
            }
         }

         return null;
      }
   }

   public Point getPathStartPos() {
      return this.pathStartPos != null ? this.pathStartPos : this.getSellingPos();
   }

   public Point getPlantingLocation() {
      for (Point p : this.woodspawn) {
         Block block = WorldUtilities.getBlock(this.building.world, p);
         if (block == Blocks.AIR
            || block == Blocks.SNOW
            || BlockItemUtilities.isBlockDecorativePlant(block) && !(block instanceof SaplingBlock) && !(block instanceof BlockMillSapling)) {
            return p;
         }
      }

      return null;
   }

   public String getPlantingLocationType(Point target) {
      return this.woodspawnTypes.get(target);
   }

   public Point getSellingPos() {
      return this.sellingPos != null ? this.sellingPos : this.sleepingPos;
   }

   public Point getShelterPos() {
      return this.shelterPos != null ? this.shelterPos : this.sleepingPos;
   }

   public Point getSilkwormHarvestLocation() {
      if (this.silkwormblock.size() == 0) {
         return null;
      } else {
         int start = MillCommonUtilities.randomInt(this.silkwormblock.size());

         for (int i = start; i < this.silkwormblock.size(); i++) {
            Point p = this.silkwormblock.get(i);
            if (WorldUtilities.getBlockState(this.building.world, p)
               == MillBlocks.SILK_WORM.defaultBlockState().setValue(BlockSilkWorm.PROGRESS, BlockSilkWorm.EnumType.SILKWORMFULL)) {
               return p;
            }
         }

         for (int ix = 0; ix < start; ix++) {
            Point p = this.silkwormblock.get(ix);
            if (WorldUtilities.getBlockState(this.building.world, p)
               == MillBlocks.SILK_WORM.defaultBlockState().setValue(BlockSilkWorm.PROGRESS, BlockSilkWorm.EnumType.SILKWORMFULL)) {
               return p;
            }
         }

         return null;
      }
   }

   public Point getSleepingPos() {
      return this.sleepingPos;
   }

   public Point getSnailSoilHarvestLocation() {
      if (this.snailsoilblock.size() == 0) {
         return null;
      } else {
         int start = MillCommonUtilities.randomInt(this.snailsoilblock.size());

         for (int i = start; i < this.snailsoilblock.size(); i++) {
            Point p = this.snailsoilblock.get(i);
            if (WorldUtilities.getBlockState(this.building.world, p)
               == MillBlocks.SNAIL_SOIL.defaultBlockState().setValue(BlockSnailSoil.PROGRESS, BlockSnailSoil.EnumType.SNAIL_SOIL_FULL)) {
               return p;
            }
         }

         for (int ix = 0; ix < start; ix++) {
            Point p = this.snailsoilblock.get(ix);
            if (WorldUtilities.getBlockState(this.building.world, p)
               == MillBlocks.SNAIL_SOIL.defaultBlockState().setValue(BlockSnailSoil.PROGRESS, BlockSnailSoil.EnumType.SNAIL_SOIL_FULL)) {
               return p;
            }
         }

         return null;
      }
   }

   public List<Point> getSoilPoints(Identifier type) {
      for (int i = 0; i < this.soilTypes.size(); i++) {
         if (this.soilTypes.get(i).equals(type)) {
            return this.soils.get(i);
         }
      }

      return null;
   }

   public Point getSugarCaneHarvestLocation() {
      if (this.sugarcanesoils.size() == 0) {
         return null;
      } else {
         int start = MillCommonUtilities.randomInt(this.sugarcanesoils.size());

         for (int i = start; i < this.sugarcanesoils.size(); i++) {
            Point p = this.sugarcanesoils.get(i);
            if (WorldUtilities.getBlock(this.building.world, p.getRelative(0.0, 2.0, 0.0)) == Blocks.SUGAR_CANE) {
               return p;
            }
         }

         for (int ix = 0; ix < start; ix++) {
            Point p = this.sugarcanesoils.get(ix);
            if (WorldUtilities.getBlock(this.building.world, p.getRelative(0.0, 2.0, 0.0)) == Blocks.SUGAR_CANE) {
               return p;
            }
         }

         return null;
      }
   }

   public Point getSugarCanePlantingLocation() {
      if (this.sugarcanesoils.size() == 0) {
         return null;
      } else {
         int start = MillCommonUtilities.randomInt(this.sugarcanesoils.size());

         for (int i = start; i < this.sugarcanesoils.size(); i++) {
            Point p = this.sugarcanesoils.get(i);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.AIR) {
               return p;
            }
         }

         for (int ix = 0; ix < start; ix++) {
            Point p = this.sugarcanesoils.get(ix);
            if (WorldUtilities.getBlock(this.building.world, p.getAbove()) == Blocks.AIR) {
               return p;
            }
         }

         return null;
      }
   }

   private boolean isBlockJungleWood(BlockState state) {
      return state.getBlock() == Blocks.JUNGLE_LOG || state.getBlock() == Blocks.JUNGLE_WOOD;
   }

   public void readDataStream(FriendlyByteBuf ds) throws IOException {
      this.chests = StreamReadWrite.readPointList(ds);
      this.furnaces = StreamReadWrite.readPointList(ds);
      this.firepits = StreamReadWrite.readPointList(ds);
      this.signs = StreamReadWrite.readPointList(ds);
      this.stalls = StreamReadWrite.readPointList(ds);
      this.banners = StreamReadWrite.readPointList(ds);
      this.cultureBanners = StreamReadWrite.readPointList(ds);

      for (Point p : this.chests) {
         TileEntityLockedChest chest = p.getMillChest(this.building.mw.world);
         if (chest != null) {
            chest.buildingPos = this.building.getPos();
         }
      }
   }

   public void readFromNBT(CompoundTag nbttagcompound) {
      this.sleepingPos = Point.read(nbttagcompound, "spawnPos");
      this.sellingPos = Point.read(nbttagcompound, "sellingPos");
      this.craftingPos = Point.read(nbttagcompound, "craftingPos");
      this.defendingPos = Point.read(nbttagcompound, "defendingPos");
      this.shelterPos = Point.read(nbttagcompound, "shelterPos");
      this.pathStartPos = Point.read(nbttagcompound, "pathStartPos");
      this.leasurePos = Point.read(nbttagcompound, "leasurePos");
      if (this.sleepingPos == null) {
         this.sleepingPos = this.building.getPos().getAbove();
      }

      ListTag nbttaglist = nbttagcompound.getListOrEmpty("chests");

      for (int i = 0; i < nbttaglist.size(); i++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(i);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null && !this.chests.contains(p)) {
            this.chests.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("furnaces");

      for (int ix = 0; ix < nbttaglist.size(); ix++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ix);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.furnaces.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("firepits");

      for (int ixx = 0; ixx < nbttaglist.size(); ixx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.firepits.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("brewingStands");

      for (int ixxx = 0; ixxx < nbttaglist.size(); ixxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.brewingStands.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("banners");

      for (int ixxxx = 0; ixxxx < nbttaglist.size(); ixxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.banners.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("culturebanners");

      for (int ixxxxx = 0; ixxxxx < nbttaglist.size(); ixxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.cultureBanners.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("signs");

      for (int ixxxxxx = 0; ixxxxxx < nbttaglist.size(); ixxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         this.signs.add(p);
      }

      nbttaglist = nbttagcompound.getListOrEmpty("netherwartsoils");

      for (int ixxxxxx = 0; ixxxxxx < nbttaglist.size(); ixxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.netherwartsoils.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("silkwormblock");

      for (int ixxxxxxx = 0; ixxxxxxx < nbttaglist.size(); ixxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.silkwormblock.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("snailsoilblock");

      for (int ixxxxxxxx = 0; ixxxxxxxx < nbttaglist.size(); ixxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.snailsoilblock.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("sugarcanesoils");

      for (int ixxxxxxxxx = 0; ixxxxxxxxx < nbttaglist.size(); ixxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.sugarcanesoils.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("fishingspots");

      for (int ixxxxxxxxxx = 0; ixxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.fishingspots.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("healingspots");

      for (int ixxxxxxxxxxx = 0; ixxxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.healingspots.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("stalls");

      for (int ixxxxxxxxxxxx = 0; ixxxxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.stalls.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("woodspawn");

      for (int ixxxxxxxxxxxxx = 0; ixxxxxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.woodspawn.add(p);
            String type = nbttagcompound1.getStringOr("type", "");
            if (type != null) {
               this.woodspawnTypes.put(p, type);
            }
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("brickspot");

      for (int ixxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxxxxxx);
         Point p = Point.read(nbttagcompound1, "pos");
         if (p != null) {
            this.brickspot.add(p);
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("spawns");

      for (int ixxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxxxxxxx);
         Identifier spawnType = net.minecraft.resources.Identifier.parse(nbttagcompound1.getStringOr("type", ""));
         this.spawnTypes.add(spawnType);
         CopyOnWriteArrayList<Point> v = new CopyOnWriteArrayList<>();
         ListTag nbttaglist2 = nbttagcompound1.getListOrEmpty("points");

         for (int j = 0; j < nbttaglist2.size(); j++) {
            CompoundTag nbttagcompound2 = nbttaglist2.getCompoundOrEmpty(j);
            Point p = Point.read(nbttagcompound2, "pos");
            if (p != null) {
               v.add(p);
               if (MillConfigValues.LogHybernation >= 2) {
                  MillLog.minor(this, "Loaded spawn point: " + p);
               }
            }
         }

         this.spawns.add(v);
         if (MillConfigValues.LogHybernation >= 2) {
            MillLog.minor(this, "Loaded " + v.size() + " spawn points for " + this.spawnTypes.get(ixxxxxxxxxxxxxxx));
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("mobspawns");

      for (int ixxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxxxxxxx);
         this.mobSpawnerTypes.add(net.minecraft.resources.Identifier.parse(nbttagcompound1.getStringOr("type", "")));
         CopyOnWriteArrayList<Point> v = new CopyOnWriteArrayList<>();
         ListTag nbttaglist2 = nbttagcompound1.getListOrEmpty("points");

         for (int jx = 0; jx < nbttaglist2.size(); jx++) {
            CompoundTag nbttagcompound2 = nbttaglist2.getCompoundOrEmpty(jx);
            Point p = Point.read(nbttagcompound2, "pos");
            if (p != null) {
               v.add(p);
               if (MillConfigValues.LogHybernation >= 2) {
                  MillLog.minor(this, "Loaded spawn point: " + p);
               }
            }
         }

         this.mobSpawners.add(v);
         if (MillConfigValues.LogHybernation >= 2) {
            MillLog.minor(this, "Loaded " + v.size() + " mob spawn points for " + this.spawnTypes.get(ixxxxxxxxxxxxxxx));
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("sources");

      for (int ixxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxxxxxxx++) {
         CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxxxxxxx);
         if (nbttagcompound1.contains("block_rl")) {
            Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(nbttagcompound1.getStringOr("block_rl", "")));
            int meta = nbttagcompound1.getIntOr("block_meta", 0);
            BlockState blockState = block.defaultBlockState();
            this.sourceTypes.add(blockState);
         } else {
            int blockId = nbttagcompound1.getIntOr("type", 0);
            this.sourceTypes.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(blockId).defaultBlockState());
         }

         CopyOnWriteArrayList<Point> v = new CopyOnWriteArrayList<>();
         ListTag nbttaglist2 = nbttagcompound1.getListOrEmpty("points");

         for (int jxx = 0; jxx < nbttaglist2.size(); jxx++) {
            CompoundTag nbttagcompound2 = nbttaglist2.getCompoundOrEmpty(jxx);
            Point p = Point.read(nbttagcompound2, "pos");
            if (p != null) {
               v.add(p);
               if (MillConfigValues.LogHybernation >= 3) {
                  MillLog.debug(this, "Loaded source point: " + p);
               }
            }
         }

         this.sources.add(v);
         if (MillConfigValues.LogHybernation >= 1) {
            MillLog.debug(this, "Loaded " + v.size() + " sources points for " + this.sourceTypes.get(ixxxxxxxxxxxxxxx).toString());
         }
      }

      nbttaglist = nbttagcompound.getListOrEmpty("genericsoils");

      for (int ixxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxx < nbttaglist.size(); ixxxxxxxxxxxxxxx++) {
         CompoundTag nbttagcompound1x = nbttaglist.getCompoundOrEmpty(ixxxxxxxxxxxxxxx);
         Identifier type = net.minecraft.resources.Identifier.parse(nbttagcompound1x.getStringOr("type", ""));
         ListTag nbttaglist2 = nbttagcompound1x.getListOrEmpty("points");

         for (int jxxx = 0; jxxx < nbttaglist2.size(); jxxx++) {
            CompoundTag nbttagcompound2 = nbttaglist2.getCompoundOrEmpty(jxxx);
            Point p = Point.read(nbttagcompound2, "pos");
            if (p != null) {
               this.addSoilPoint(type, p);
            }
         }
      }

      for (Point p : this.chests) {
         if (this.building.world.hasChunk(p.getiX() / 16, p.getiZ() / 16) && p.getMillChest(this.building.world) != null) {
            p.getMillChest(this.building.world).buildingPos = this.building.getPos();
         }
      }
   }

   public void sendBuildingPacket(FriendlyByteBuf data) throws IOException {
      StreamReadWrite.writePointList(this.chests, data);
      StreamReadWrite.writePointList(this.furnaces, data);
      StreamReadWrite.writePointList(this.firepits, data);
      StreamReadWrite.writePointList(this.signs, data);
      StreamReadWrite.writePointList(this.stalls, data);
      StreamReadWrite.writePointList(this.banners, data);
      StreamReadWrite.writePointList(this.cultureBanners, data);
   }

   public void setCraftingPos(Point p) {
      this.craftingPos = p;
   }

   public void setDefendingPos(Point p) {
      this.defendingPos = p;
   }

   public void setLeasurePos(Point p) {
      this.leasurePos = p;
   }

   public void setPathStartPos(Point p) {
      this.pathStartPos = p;
   }

   public void setSellingPos(Point p) {
      this.sellingPos = p;
   }

   public void setShelterPos(Point p) {
      this.shelterPos = p;
   }

   public void setSleepingPos(Point p) {
      this.sleepingPos = p;
   }

   public void writeToNBT(CompoundTag nbttagcompound) {
      if (this.sleepingPos != null) {
         this.sleepingPos.write(nbttagcompound, "spawnPos");
      }

      if (this.sellingPos != null) {
         this.sellingPos.write(nbttagcompound, "sellingPos");
      }

      if (this.craftingPos != null) {
         this.craftingPos.write(nbttagcompound, "craftingPos");
      }

      if (this.defendingPos != null) {
         this.defendingPos.write(nbttagcompound, "defendingPos");
      }

      if (this.shelterPos != null) {
         this.shelterPos.write(nbttagcompound, "shelterPos");
      }

      if (this.pathStartPos != null) {
         this.pathStartPos.write(nbttagcompound, "pathStartPos");
      }

      if (this.leasurePos != null) {
         this.leasurePos.write(nbttagcompound, "leasurePos");
      }

      ListTag nbttaglist = new ListTag();

      for (Point p : this.signs) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         if (p != null) {
            p.write(nbttagcompound1, "pos");
         } else {
            nbttagcompound1.putDouble("pos_xCoord", 0.0);
            nbttagcompound1.putDouble("pos_yCoord", 0.0);
            nbttagcompound1.putDouble("pos_zCoord", 0.0);
         }

         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("signs", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.netherwartsoils) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("netherwartsoils", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.silkwormblock) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("silkwormblock", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.snailsoilblock) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("snailsoilblock", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.sugarcanesoils) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("sugarcanesoils", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.fishingspots) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("fishingspots", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.healingspots) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("healingspots", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.stalls) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("stalls", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.woodspawn) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         if (this.woodspawnTypes.containsKey(p)) {
            nbttagcompound1.putString("type", this.woodspawnTypes.get(p));
         }

         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("woodspawn", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.brickspot) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("brickspot", nbttaglist);
      nbttaglist = new ListTag();

      for (int i = 0; i < this.spawns.size(); i++) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         nbttagcompound1.putString("type", this.spawnTypes.get(i).toString());
         ListTag nbttaglist2 = new ListTag();

         for (Point p : this.spawns.get(i)) {
            CompoundTag nbttagcompound2 = new CompoundTag();
            p.write(nbttagcompound2, "pos");
            nbttaglist2.add(nbttagcompound2);
         }

         nbttagcompound1.put("points", nbttaglist2);
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("spawns", nbttaglist);
      nbttaglist = new ListTag();

      for (int i = 0; i < this.soilTypes.size(); i++) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         nbttagcompound1.putString("type", this.soilTypes.get(i).toString());
         ListTag nbttaglist2 = new ListTag();

         for (Point p : this.soils.get(i)) {
            CompoundTag nbttagcompound2 = new CompoundTag();
            p.write(nbttagcompound2, "pos");
            nbttaglist2.add(nbttagcompound2);
         }

         nbttagcompound1.put("points", nbttaglist2);
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("genericsoils", nbttaglist);
      nbttaglist = new ListTag();

      for (int i = 0; i < this.mobSpawners.size(); i++) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         nbttagcompound1.putString("type", this.mobSpawnerTypes.get(i).toString());
         ListTag nbttaglist2 = new ListTag();

         for (Point p : this.mobSpawners.get(i)) {
            CompoundTag nbttagcompound2 = new CompoundTag();
            p.write(nbttagcompound2, "pos");
            nbttaglist2.add(nbttagcompound2);
         }

         nbttagcompound1.put("points", nbttaglist2);
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("mobspawns", nbttaglist);
      nbttaglist = new ListTag();

      for (int i = 0; i < this.sources.size(); i++) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         nbttagcompound1.putString("block_rl", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this.sourceTypes.get(i).getBlock()).toString());
         nbttagcompound1.putInt("block_meta", 0);
         ListTag nbttaglist2 = new ListTag();

         for (Point p : this.sources.get(i)) {
            CompoundTag nbttagcompound2 = new CompoundTag();
            p.write(nbttagcompound2, "pos");
            nbttaglist2.add(nbttagcompound2);
         }

         nbttagcompound1.put("points", nbttaglist2);
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("sources", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.chests) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("chests", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.furnaces) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("furnaces", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.firepits) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("firepits", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.brewingStands) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("brewingStands", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.banners) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("banners", nbttaglist);
      nbttaglist = new ListTag();

      for (Point p : this.cultureBanners) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         p.write(nbttagcompound1, "pos");
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put("culturebanners", nbttaglist);
   }
}
