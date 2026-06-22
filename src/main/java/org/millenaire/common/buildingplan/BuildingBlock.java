package org.millenaire.common.buildingplan;

import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.TagParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.millenaire.common.block.IBlockPath;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.block.mock.MockBlockBannerHanging;
import org.millenaire.common.block.mock.MockBlockBannerStanding;
import org.millenaire.common.entity.EntityWallDecoration;
import org.millenaire.common.entity.TileEntityMockBanner;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.item.ItemMockBanner;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.PathUtilities;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.WorldGenAppleTree;
import org.millenaire.common.world.WorldGenCherry;
import org.millenaire.common.world.WorldGenOliveTree;
import org.millenaire.common.world.WorldGenPistachio;
import org.millenaire.common.world.WorldGenSakura;

public class BuildingBlock {
   public static byte TAPESTRY = 1;
   public static byte OAKSPAWN = 2;
   public static byte PINESPAWN = 3;
   public static byte BIRCHSPAWN = 4;
   public static byte INDIANSTATUE = 5;
   public static byte PRESERVEGROUNDDEPTH = 6;
   public static byte CLEARTREE = 7;
   public static byte MAYANSTATUE = 8;
   public static byte SPAWNERSKELETON = 9;
   public static byte SPAWNERZOMBIE = 10;
   public static byte SPAWNERSPIDER = 11;
   public static byte SPAWNERCAVESPIDER = 12;
   public static byte SPAWNERCREEPER = 13;
   public static byte DISPENDERUNKNOWNPOWDER = 14;
   public static byte JUNGLESPAWN = 15;
   public static byte INVERTED_DOOR = 16;
   public static byte CLEARGROUND = 17;
   public static byte BYZANTINEICONSMALL = 18;
   public static byte BYZANTINEICONMEDIUM = 19;
   public static byte BYZANTINEICONLARGE = 20;
   public static byte PRESERVEGROUNDSURFACE = 21;
   public static byte SPAWNERBLAZE = 22;
   public static byte ACACIASPAWN = 23;
   public static byte DARKOAKSPAWN = 24;
   public static byte TORCHGUESS = 25;
   public static byte CHESTGUESS = 26;
   public static byte FURNACEGUESS = 27;
   public static byte CLEARGROUNDOUTSIDEBUILDING = 28;
   public static byte HIDEHANGING = 29;
   public static byte APPLETREESPAWN = 30;
   public static byte CLEARGROUNDBORDER = 31;
   public static byte OLIVETREESPAWN = 32;
   public static byte PISTACHIOTREESPAWN = 33;
   public static byte WALLCARPETSMALL = 40;
   public static byte WALLCARPETMEDIUM = 41;
   public static byte WALLCARPETLARGE = 42;
   public static byte CHERRYTREESPAWN = 43;
   public static byte SAKURATREESPAWN = 43;
   public final Block block;
   private byte meta;
   public final Point p;
   private BlockState blockState;
   public byte special;

   public BuildingBlock(Point p, Block block, int meta) {
      this.p = p;
      this.block = block;
      this.meta = (byte)meta;
      this.blockState = block.defaultBlockState();
      this.special = 0;
   }

   public BuildingBlock(Point p, DataInputStream ds) throws IOException {
      this.p = p;
      this.block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(ds.readInt());
      this.meta = ds.readByte();
      this.special = ds.readByte();
      if (this.block != null) {
         this.blockState = this.block.defaultBlockState();
      } else {
         this.blockState = Blocks.AIR.defaultBlockState();
      }
   }

   public BuildingBlock(Point p, BlockState bs) {
      this.p = p;
      this.block = bs.getBlock();
      this.meta = 0;
      this.blockState = bs;
      this.special = 0;
   }

   public BuildingBlock(Point p, int special) {
      this.p = p;
      this.block = Blocks.AIR;
      this.meta = 0;
      this.special = (byte)special;
      this.blockState = Blocks.AIR.defaultBlockState();
   }

   public boolean alreadyDone(Level world) {
      if (this.special != 0) {
         return false;
      } else {
         Block block = WorldUtilities.getBlock(world, this.p);
         if (this.block != block) {
            return false;
         } else {
            int meta = WorldUtilities.getBlockMeta(world, this.p);
            return this.meta == meta;
         }
      }
   }

   public boolean build(Level world, Building townHall, boolean worldGeneration, boolean wandimport) {
      boolean blockSet = false;

      // [MILLDEBUG] a normal block spec (special==0) whose block resolved to minecraft:air almost always
      // means the building-data block id/legend entry failed to resolve. Flag it with id + position.
      if (MillLog.debugOn() && this.special == 0 && this.block == Blocks.AIR) {
         MillLog.milldebug(
            "BuildingBlock",
            "UNRESOLVED block spec -> minecraft:air at " + this.p + " (meta=" + this.meta + ", building="
               + (townHall != null ? townHall.getVillageQualifiedName() : "n/a") + ")"
         );
      }

      try {
         boolean notifyBlocks = true;
         boolean playSound = !worldGeneration && !wandimport;
         if (this.special == 0) {
            blockSet = this.buildNormalBlock(world, townHall, wandimport, true, playSound);
         } else if (this.special == PRESERVEGROUNDDEPTH || this.special == PRESERVEGROUNDSURFACE) {
            blockSet = this.buildPreserveGround(world, worldGeneration, true, playSound);
         } else if (this.special == CLEARTREE) {
            blockSet = this.buildClearTree(world, worldGeneration, true, playSound);
         } else if (this.special == CLEARGROUND || this.special == CLEARGROUNDOUTSIDEBUILDING || this.special == CLEARGROUNDBORDER) {
            blockSet = this.buildClearGround(world, worldGeneration, wandimport, true, playSound);
         } else if (this.special == TAPESTRY
            || this.special == INDIANSTATUE
            || this.special == MAYANSTATUE
            || this.special == BYZANTINEICONSMALL
            || this.special == BYZANTINEICONMEDIUM
            || this.special == BYZANTINEICONLARGE
            || this.special == HIDEHANGING
            || this.special == WALLCARPETSMALL
            || this.special == WALLCARPETMEDIUM
            || this.special == WALLCARPETLARGE) {
            blockSet = this.buildPicture(world);
         } else if (this.special == OAKSPAWN
            || this.special == PINESPAWN
            || this.special == BIRCHSPAWN
            || this.special == JUNGLESPAWN
            || this.special == ACACIASPAWN
            || this.special == DARKOAKSPAWN
            || this.special == APPLETREESPAWN
            || this.special == OLIVETREESPAWN
            || this.special == PISTACHIOTREESPAWN
            || this.special == CHERRYTREESPAWN
            || this.special == SAKURATREESPAWN) {
            blockSet = this.buildTreeSpawn(world, worldGeneration);
         } else if (this.special == SPAWNERSKELETON) {
            this.placeSpawner(world, Mill.ENTITY_SKELETON);
            blockSet = true;
         } else if (this.special == SPAWNERZOMBIE) {
            this.placeSpawner(world, Mill.ENTITY_ZOMBIE);
            blockSet = true;
         } else if (this.special == SPAWNERSPIDER) {
            this.placeSpawner(world, Mill.ENTITY_SPIDER);
            blockSet = true;
         } else if (this.special == SPAWNERCAVESPIDER) {
            this.placeSpawner(world, Mill.ENTITY_CAVESPIDER);
            blockSet = true;
         } else if (this.special == SPAWNERCREEPER) {
            this.placeSpawner(world, Identifier.withDefaultNamespace("creeper"));
            blockSet = true;
         } else if (this.special == SPAWNERBLAZE) {
            this.placeSpawner(world, Identifier.withDefaultNamespace("blaze"));
            blockSet = true;
         } else if (this.special == DISPENDERUNKNOWNPOWDER) {
            WorldUtilities.setBlockAndMetadata(world, this.p, Blocks.DISPENSER, 0);
            DispenserBlockEntity dispenser = this.p.getDispenser(world);
            MillCommonUtilities.putItemsInChest(dispenser, MillItems.UNKNOWN_POWDER, 2);
            blockSet = true;
         } else if (this.special == FURNACEGUESS) {
            Direction facing = this.guessChestFurnaceFacing(world, this.p);
            BlockState furnaceBS = Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, facing);
            world.setBlock(this.p.getBlockPos(), furnaceBS, 3);
            blockSet = true;
         } else if (this.special == CHESTGUESS) {
            Direction facing = this.guessChestFurnaceFacing(world, this.p);
            BlockState chestBS = MillBlocks.LOCKED_CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
            world.setBlock(this.p.getBlockPos(), chestBS, 3);
            blockSet = true;
         } else if (this.special == TORCHGUESS) {
            BlockState bs = this.guessTorchState(world);
            world.setBlock(this.p.getBlockPos(), bs, 3);
            blockSet = true;
         } else if (this.special == INVERTED_DOOR) {
            // A door's lower half placed alone with a neighbour update (flag 3) immediately pops
            // off (no upper half / re-validation) → the world re-read below returned AIR and
            // setValue(half) on air crashed, aborting the whole building. Place the lower half
            // without a neighbour update (flag 2) so it survives, and derive the upper half from
            // the known door state instead of re-reading the world.
            if (this.blockState.hasProperty(DoorBlock.HALF)
               && this.blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
               world.setBlock(this.p.getBlockPos(), this.blockState, 2);
               BlockState bs = this.blockState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                  .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
               WorldUtilities.setBlockstate(world, this.p.getAbove(), bs, true, playSound);
            } else {
               world.setBlock(this.p.getBlockPos(), this.blockState, 3);
            }

            blockSet = true;
         }
      } catch (Exception var10) {
         MillLog.milldebug("BuildingBlock", "EXCEPTION building block " + this.block + " at " + this.p + " (special=" + this.special + "): " + var10);
         MillLog.printException("Exception in BuildingBlock.build():", var10);
      }

      return blockSet;
   }

   private boolean buildClearGround(Level world, boolean worldGeneration, boolean wandimport, boolean notifyBlocks, boolean playSound) {
      boolean shouldSetBlock = false;
      boolean shouldSetBlockBelow = false;
      Block existingBlock = WorldUtilities.getBlock(world, this.p);
      BlockState targetBlockState = null;
      BlockState targetBelowBlockState = null;
      if ((!wandimport || existingBlock != Blocks.OAK_SIGN && existingBlock != MillBlocks.IMPORT_TABLE)
         && !BlockItemUtilities.isBlockDecorativePlant(existingBlock)) {
         if (this.special == CLEARGROUNDBORDER && !(existingBlock instanceof LeavesBlock) && existingBlock != Blocks.AIR) {
            if (this.p.getEast().getBlock(world) instanceof LiquidBlock
               || this.p.getWest().getBlock(world) instanceof LiquidBlock
               || this.p.getNorth().getBlock(world) instanceof LiquidBlock
               || this.p.getSouth().getBlock(world) instanceof LiquidBlock) {
               BlockState blockStateBelow = WorldUtilities.getBlockState(world, this.p.getBelow());
               targetBlockState = WorldUtilities.getBlockStateValidGround(blockStateBelow, true);
               if (targetBlockState == null) {
                  targetBlockState = Blocks.DIRT.defaultBlockState();
               }

               if (existingBlock != targetBlockState.getBlock()) {
                  shouldSetBlock = true;
               }
            } else if (existingBlock != Blocks.AIR) {
               targetBlockState = Blocks.AIR.defaultBlockState();
               shouldSetBlock = true;
            }
         } else if (existingBlock != Blocks.AIR
            && (this.special != CLEARGROUNDOUTSIDEBUILDING && this.special != CLEARGROUNDBORDER || !(existingBlock instanceof LeavesBlock))) {
            targetBlockState = Blocks.AIR.defaultBlockState();
            shouldSetBlock = true;
         }
      }

      BlockState blockStateBelowx = WorldUtilities.getBlockState(world, this.p.getBelow());
      targetBelowBlockState = WorldUtilities.getBlockStateValidGround(blockStateBelowx, true);
      if (worldGeneration && targetBelowBlockState == Blocks.DIRT.defaultBlockState()) {
         targetBelowBlockState = Blocks.GRASS_BLOCK.defaultBlockState();
         shouldSetBlockBelow = true;
      } else if (targetBlockState != null
         && (targetBlockState != Blocks.DIRT.defaultBlockState() || blockStateBelowx.getBlock() != Blocks.GRASS_BLOCK)) {
         shouldSetBlock = true;
      }

      if (shouldSetBlock) {
         WorldUtilities.setBlockstate(world, this.p, targetBlockState, notifyBlocks, playSound);
      }

      if (shouldSetBlockBelow) {
         WorldUtilities.setBlockstate(world, this.p.getBelow(), targetBelowBlockState, notifyBlocks, playSound);
      }

      return shouldSetBlock || shouldSetBlockBelow;
   }

   private boolean buildClearTree(Level world, boolean worldGeneration, boolean notifyBlocks, boolean playSound) {
      Block block = WorldUtilities.getBlock(world, this.p);
      if (!block.defaultBlockState().is(net.minecraft.tags.BlockTags.LOGS)) {
         return false;
      } else {
         WorldUtilities.setBlockAndMetadata(world, this.p, Blocks.AIR, 0, notifyBlocks, playSound);
         BlockState blockStateBelow = WorldUtilities.getBlockState(world, this.p.getBelow());
         BlockState targetBlockState = WorldUtilities.getBlockStateValidGround(blockStateBelow, true);
         if (worldGeneration && targetBlockState != null && targetBlockState.getBlock() == Blocks.DIRT) {
            WorldUtilities.setBlock(world, this.p.getBelow(), Blocks.GRASS_BLOCK, notifyBlocks, playSound);
         } else if (targetBlockState != null && (targetBlockState != Blocks.DIRT.defaultBlockState() || block != Blocks.GRASS_BLOCK)) {
            WorldUtilities.setBlockstate(world, this.p.getBelow(), targetBlockState, notifyBlocks, playSound);
         }

         return true;
      }
   }

   private boolean buildNormalBlock(Level world, Building townHall, boolean wandimport, boolean notifyBlocks, boolean playSound) {
      boolean blockSet = false;
      if (this.block instanceof DoorBlock) {
         if (this.blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getAbove(), Blocks.AIR, 0, notifyBlocks, playSound);
         }
      } else if (this.block instanceof BedBlock) {
         Direction facing = (Direction)this.blockState.getValue(HorizontalDirectionalBlock.FACING);
         if (facing == Direction.EAST) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getWest(), Blocks.AIR, 0, notifyBlocks, playSound);
         } else if (facing == Direction.SOUTH) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getNorth(), Blocks.AIR, 0, notifyBlocks, playSound);
         } else if (facing == Direction.WEST) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getEast(), Blocks.AIR, 0, notifyBlocks, playSound);
         } else if (facing == Direction.NORTH) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getSouth(), Blocks.AIR, 0, notifyBlocks, playSound);
         }
      }

      if (!wandimport || this.block != Blocks.AIR || WorldUtilities.getBlock(world, this.p) != Blocks.OAK_SIGN) {
         Block existingBlock = WorldUtilities.getBlock(world, this.p);
         if (this.block == Blocks.AIR) {
            if (!BlockItemUtilities.isBlockDecorativePlant(existingBlock)) {
               WorldUtilities.setBlockAndMetadata(world, this.p, this.block, this.meta, notifyBlocks, playSound);
               blockSet = true;
            }
         } else if (this.block instanceof FlowerPotBlock) {
            if (this.meta == -1) {
               this.meta = 0;
            }

            // 26.2: places the (empty) FLOWER_POT here; a non-empty pot's plant is applied as the
            // distinct POTTED_* block in the placeSpecial() FlowerPotBlock branch (pottedBlockForMeta).
            WorldUtilities.setBlockstate(world, this.p, this.blockState, notifyBlocks, playSound);
         } else {
            if (existingBlock instanceof BedBlock) {
               world.removeBlock(this.p.getBlockPos(), false);
            }

            if (this.block instanceof BedBlock) {
               WorldUtilities.setBlockAndMetadata(world, this.p, Blocks.AIR, 0, notifyBlocks, playSound);
               Direction facing = (Direction)this.blockState.getValue(HorizontalDirectionalBlock.FACING);
               if (facing == Direction.EAST) {
                  WorldUtilities.setBlockAndMetadata(world, this.p.getWest(), Blocks.AIR, 0, notifyBlocks, playSound);
               } else if (facing == Direction.SOUTH) {
                  WorldUtilities.setBlockAndMetadata(world, this.p.getNorth(), Blocks.AIR, 0, notifyBlocks, playSound);
               } else if (facing == Direction.WEST) {
                  WorldUtilities.setBlockAndMetadata(world, this.p.getEast(), Blocks.AIR, 0, notifyBlocks, playSound);
               } else if (facing == Direction.NORTH) {
                  WorldUtilities.setBlockAndMetadata(world, this.p.getSouth(), Blocks.AIR, 0, notifyBlocks, playSound);
               }
            }

            if (this.blockState != Blocks.DIRT.defaultBlockState() || existingBlock != Blocks.GRASS_BLOCK) {
               WorldUtilities.setBlockAndMetadata(world, this.p, this.block, this.meta, notifyBlocks, playSound);
               blockSet = true;
            }
         }
      }

      if (this.block instanceof DoorBlock) {
         if (this.blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockState bs = this.blockState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            if (this.special == INVERTED_DOOR) {
               bs = bs.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
            }

            WorldUtilities.setBlockstate(world, this.p.getAbove(), bs, notifyBlocks, playSound);
         }
      } else if (this.block instanceof BedBlock) {
         Direction facing = (Direction)this.blockState.getValue(HorizontalDirectionalBlock.FACING);
         if (facing == Direction.EAST) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getWest(), this.block, this.meta - 8, notifyBlocks, playSound);
         } else if (facing == Direction.SOUTH) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getNorth(), this.block, this.meta - 8, notifyBlocks, playSound);
         } else if (facing == Direction.WEST) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getEast(), this.block, this.meta - 8, notifyBlocks, playSound);
         } else if (facing == Direction.NORTH) {
            WorldUtilities.setBlockAndMetadata(world, this.p.getSouth(), this.block, this.meta - 8, notifyBlocks, playSound);
         }
      } else if (this.block == Blocks.WATER) {
         world.setBlock(this.p.getBlockPos(), Blocks.WATER.defaultBlockState(), 11);
      } else if (this.block == Blocks.NETHER_PORTAL) {
         // 1.12 called Blocks.PORTAL.trySpawnPortal(world, pos), which validated the obsidian frame and
         // filled it with portal blocks. The 26.2 equivalent is PortalShape: find an empty portal shape
         // at this position and create its portal blocks (preferred axis X, matching trySpawnPortal).
         net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(world, this.p.getBlockPos(), net.minecraft.core.Direction.Axis.X)
            .ifPresent(shape -> shape.createPortalBlocks(world));
      } else if (this.block instanceof DoublePlantBlock) {
         BlockState bs = this.blockState.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
         WorldUtilities.setBlockstate(world, this.p.getAbove(), bs, notifyBlocks, playSound);
      } else if (this.block instanceof FlowerPotBlock) {
         // 1.12 flower pots stored their plant as a BlockEntity + meta on a single FlowerPotBlock. On 26.2
         // each potted plant is its own block (Blocks.POTTED_*), so map the legacy meta (the old
         // BlockFlowerPot.EnumFlowerType ordinal) to the matching POTTED_* block and place it directly.
         // meta 0 / unknown keeps the already-placed empty FLOWER_POT.
         Block potted = pottedBlockForMeta(this.meta);
         if (potted != null) {
            WorldUtilities.setBlockstate(world, this.p, potted.defaultBlockState(), notifyBlocks, playSound);
         }
      } else if (this.block instanceof MockBlockBannerHanging) {
         MockBlockBannerHanging bannerBlock = (MockBlockBannerHanging)this.block;
         BlockEntity bannerEntity = world.getBlockEntity(this.p.getBlockPos());
         if (bannerEntity instanceof TileEntityMockBanner) {
            try {
               if (townHall == null) {
                  ItemStack bannerStack = ItemMockBanner.makeBanner(
                     bannerBlock.asItem(),
                     ItemMockBanner.BANNER_COLOURS[bannerBlock.bannerType],
                     TagParser.parseCompoundFully(ItemMockBanner.BANNER_DESIGNS[bannerBlock.bannerType])
                  );
                  ((TileEntityMockBanner)bannerEntity).setItemValues(bannerStack, true);
               } else if (bannerBlock.bannerType == ItemMockBanner.BANNER_VILLAGE) {
                  ((TileEntityMockBanner)bannerEntity).setItemValues(townHall.getBannerStack(), true);
               } else {
                  ((TileEntityMockBanner)bannerEntity).setItemValues(townHall.culture.cultureBannerItemStack, true);
               }
            } catch (CommandSyntaxException var12) {
               MillLog.printException(var12);
            }
         }
      } else if (this.block instanceof MockBlockBannerStanding) {
         MockBlockBannerStanding bannerBlock = (MockBlockBannerStanding)this.block;
         BlockEntity bannerEntity = world.getBlockEntity(this.p.getBlockPos());
         if (bannerEntity instanceof TileEntityMockBanner) {
            try {
               if (townHall == null) {
                  ItemStack bannerStack = ItemMockBanner.makeBanner(
                     bannerBlock.asItem(),
                     ItemMockBanner.BANNER_COLOURS[bannerBlock.bannerType],
                     TagParser.parseCompoundFully(ItemMockBanner.BANNER_DESIGNS[bannerBlock.bannerType])
                  );
                  ((TileEntityMockBanner)bannerEntity).setItemValues(bannerStack, true);
               } else if (bannerBlock.bannerType == ItemMockBanner.BANNER_VILLAGE) {
                  ((TileEntityMockBanner)bannerEntity).setItemValues(townHall.getBannerStack(), true);
               } else {
                  ((TileEntityMockBanner)bannerEntity).setItemValues(townHall.culture.cultureBannerItemStack, true);
               }
            } catch (CommandSyntaxException var11) {
               MillLog.printException(var11);
            }
         }
      }

      return blockSet;
   }

   private boolean buildPicture(Level world) {
      EntityWallDecoration art = null;
      if (this.special == TAPESTRY) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 1);
      } else if (this.special == INDIANSTATUE) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 2);
      } else if (this.special == MAYANSTATUE) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 3);
      } else if (this.special == BYZANTINEICONSMALL) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 4);
      } else if (this.special == BYZANTINEICONMEDIUM) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 5);
      } else if (this.special == BYZANTINEICONLARGE) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 6);
      } else if (this.special == HIDEHANGING) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 7);
      } else if (this.special == WALLCARPETSMALL) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 8);
      } else if (this.special == WALLCARPETMEDIUM) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 9);
      } else if (this.special == WALLCARPETLARGE) {
         art = EntityWallDecoration.createWallDecoration(world, this.p, 10);
      }

      if (art != null && art.survives() && !world.isClientSide()) {
         world.addFreshEntity(art);
         return true;
      } else {
         return false;
      }
   }

   /**
    * Maps the legacy 1.12 flower-pot metadata (BlockFlowerPot.EnumFlowerType ordinal) to the 26.2
    * potted-plant block. Returns null for an empty pot / unknown meta (keeps the plain FLOWER_POT).
    */
   private static Block pottedBlockForMeta(int meta) {
      switch (meta) {
         case 1: return Blocks.POTTED_POPPY;
         case 2: return Blocks.POTTED_BLUE_ORCHID;
         case 3: return Blocks.POTTED_ALLIUM;
         case 4: return Blocks.POTTED_AZURE_BLUET;
         case 5: return Blocks.POTTED_RED_TULIP;
         case 6: return Blocks.POTTED_ORANGE_TULIP;
         case 7: return Blocks.POTTED_WHITE_TULIP;
         case 8: return Blocks.POTTED_PINK_TULIP;
         case 9: return Blocks.POTTED_OXEYE_DAISY;
         case 10: return Blocks.POTTED_DANDELION;
         case 11: return Blocks.POTTED_OAK_SAPLING;
         case 12: return Blocks.POTTED_SPRUCE_SAPLING;
         case 13: return Blocks.POTTED_BIRCH_SAPLING;
         case 14: return Blocks.POTTED_JUNGLE_SAPLING;
         case 15: return Blocks.POTTED_ACACIA_SAPLING;
         case 16: return Blocks.POTTED_DARK_OAK_SAPLING;
         case 17: return Blocks.POTTED_RED_MUSHROOM;
         case 18: return Blocks.POTTED_BROWN_MUSHROOM;
         case 19: return Blocks.POTTED_DEAD_BUSH;
         case 20: return Blocks.POTTED_FERN;
         case 21: return Blocks.POTTED_CACTUS;
         default: return null;
      }
   }

   /** 1.12 Material GROUND/ROCK/SAND/CLAY equivalent: a natural dirt/stone/sand/clay full cube. */
   private static boolean isNaturalGround(BlockState state) {
      if (!state.isSolidRender()) {
         return false;
      }
      return state.is(net.minecraft.tags.BlockTags.DIRT)
         || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
         || state.is(net.minecraft.tags.BlockTags.SAND)
         || state.is(Blocks.GRAVEL)
         || state.is(Blocks.CLAY);
   }

   private boolean buildPreserveGround(Level world, boolean worldGeneration, boolean notifyBlocks, boolean playSound) {
      BlockState existingBlockState = WorldUtilities.getBlockState(world, this.p);
      boolean surface = this.special == PRESERVEGROUNDSURFACE;
      // 1.12 skipped already-natural ground: if the existing block was an opaque full cube whose Material
      // was GROUND (dirt), ROCK (stone), SAND or CLAY, it was left as-is. Material is removed on 26.2, so
      // we test the equivalent vanilla block tags (dirt/base-stone/sand) + clay, which faithfully
      // reproduces that set instead of skipping every solid block.
      if (!surface && isNaturalGround(existingBlockState)) {
         return false;
      }

      BlockState targetGroundBlockState = WorldUtilities.getBlockStateValidGround(existingBlockState, surface);
      if (targetGroundBlockState == null) {
         for (Point below = this.p.getBelow(); targetGroundBlockState == null && below.getiY() > 0; below = below.getBelow()) {
            this.blockState = WorldUtilities.getBlockState(world, below);
            if (WorldUtilities.getBlockStateValidGround(this.blockState, surface) != null) {
               targetGroundBlockState = WorldUtilities.getBlockStateValidGround(this.blockState, surface);
            }
         }

         if (targetGroundBlockState == null) {
            targetGroundBlockState = Blocks.DIRT.defaultBlockState();
         }
      }

      if (targetGroundBlockState.getBlock() == Blocks.DIRT && worldGeneration && surface) {
         targetGroundBlockState = Blocks.GRASS_BLOCK.defaultBlockState();
      }

      if (targetGroundBlockState.getBlock() == Blocks.GRASS_BLOCK && !worldGeneration) {
         targetGroundBlockState = Blocks.DIRT.defaultBlockState();
      }

      if (targetGroundBlockState == null || targetGroundBlockState.getBlock() == Blocks.AIR) {
         if (worldGeneration && surface) {
            targetGroundBlockState = Blocks.GRASS_BLOCK.defaultBlockState();
         } else {
            targetGroundBlockState = Blocks.DIRT.defaultBlockState();
         }
      }

      if (targetGroundBlockState == existingBlockState) {
         return false;
      } else if (existingBlockState.getBlock() == Blocks.GRASS_BLOCK && targetGroundBlockState.getBlock() == Blocks.DIRT) {
         return false;
      } else {
         WorldUtilities.setBlockstate(world, this.p, targetGroundBlockState, notifyBlocks, playSound);
         return true;
      }
   }

   private boolean buildTreeSpawn(Level world, boolean worldGeneration) {
      if (!worldGeneration || !(world instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
         return false;
      }

      net.minecraft.core.BlockPos pos = this.p.getBlockPos();
      java.util.Random rand = MillCommonUtilities.random;

      // Vanilla tree types: place the matching vanilla ConfiguredFeature via the registry.
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> vanillaKey = null;
      if (this.special == OAKSPAWN) {
         vanillaKey = net.minecraft.data.worldgen.features.TreeFeatures.OAK;
      } else if (this.special == PINESPAWN) {
         vanillaKey = net.minecraft.data.worldgen.features.TreeFeatures.SPRUCE;
      } else if (this.special == BIRCHSPAWN) {
         vanillaKey = net.minecraft.data.worldgen.features.TreeFeatures.BIRCH;
      } else if (this.special == JUNGLESPAWN) {
         vanillaKey = net.minecraft.data.worldgen.features.TreeFeatures.JUNGLE_TREE;
      } else if (this.special == ACACIASPAWN) {
         vanillaKey = net.minecraft.data.worldgen.features.TreeFeatures.ACACIA;
      } else if (this.special == DARKOAKSPAWN) {
         vanillaKey = net.minecraft.data.worldgen.features.TreeFeatures.DARK_OAK;
      }

      if (vanillaKey != null) {
         net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?> feature = serverLevel.registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
            .getValue(vanillaKey);
         if (feature == null) {
            return false;
         }
         return feature.place(
            serverLevel,
            serverLevel.getChunkSource().getGenerator(),
            serverLevel.getRandom(),
            pos
         );
      }

      // Millénaire custom fruit trees: imperative placement (verified against 26.2 API).
      org.millenaire.common.world.MillTreeGenerator wg = null;
      if (this.special == APPLETREESPAWN) {
         wg = new WorldGenAppleTree(true);
      } else if (this.special == OLIVETREESPAWN) {
         wg = new WorldGenOliveTree(true);
      } else if (this.special == PISTACHIOTREESPAWN) {
         wg = new WorldGenPistachio(true);
      } else if (this.special == CHERRYTREESPAWN) {
         wg = new WorldGenCherry(true);
      } else if (this.special == SAKURATREESPAWN) {
         wg = new WorldGenSakura(true);
      }

      if (wg != null) {
         return wg.generate(serverLevel, rand, pos);
      }

      return false;
   }

   private void placeSpawner(Level world, Identifier entityId) {
      WorldUtilities.setBlockAndMetadata(world, this.p, Blocks.SPAWNER, 0);
      BlockEntity be = this.p.getTileEntity(world);
      if (be instanceof SpawnerBlockEntity spawner) {
         EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
         if (type != null) {
            spawner.setEntityId(type, world.getRandom());
         }
      }
   }

   public BlockState getBlockstate() {
      return this.blockState;
   }

   public byte getMeta() {
      return this.meta;
   }

   private Direction guessChestFurnaceFacing(Level world, Point p) {
      BlockState bsNorth = p.getNorth().getBlockActualState(world);
      BlockState bsSouth = p.getSouth().getBlockActualState(world);
      BlockState bsWest = p.getWest().getBlockActualState(world);
      BlockState bsEast = p.getEast().getBlockActualState(world);
      if (bsNorth.isSolidRender()
         && bsNorth.getBlock() != Blocks.FURNACE
         && bsNorth.getBlock() != MillBlocks.LOCKED_CHEST
         && !bsSouth.isSolidRender()) {
         return Direction.SOUTH;
      } else if (bsSouth.isSolidRender()
         && bsSouth.getBlock() != Blocks.FURNACE
         && bsSouth.getBlock() != MillBlocks.LOCKED_CHEST
         && !bsNorth.isSolidRender()) {
         return Direction.NORTH;
      } else if (bsWest.isSolidRender()
         && bsWest.getBlock() != Blocks.FURNACE
         && bsWest.getBlock() != MillBlocks.LOCKED_CHEST
         && !bsEast.isSolidRender()) {
         return Direction.EAST;
      } else if (bsEast.isSolidRender()
         && bsEast.getBlock() != Blocks.FURNACE
         && bsEast.getBlock() != MillBlocks.LOCKED_CHEST
         && !bsWest.isSolidRender()) {
         return Direction.WEST;
      } else if (!bsSouth.isSolidRender()) {
         return Direction.SOUTH;
      } else if (!bsNorth.isSolidRender()) {
         return Direction.NORTH;
      } else if (!bsEast.isSolidRender()) {
         return Direction.EAST;
      } else {
         return !bsWest.isSolidRender() ? Direction.WEST : Direction.NORTH;
      }
   }

   /**
    * Replicates 1.12 {@code BlockTorch.getStateForPlacement} for a {@code torchGuess} special point.
    *
    * <p>In 1.12 a single {@code BlockTorch} encoded both the floor torch (FACING=UP) and the wall
    * torches (FACING=NORTH/SOUTH/EAST/WEST) and {@code getStateForPlacement} picked the floor variant
    * if it could stand on the block below, otherwise it attached to the first solid horizontal
    * neighbour. 26.2 split these into {@code minecraft:torch} (no facing) and {@code minecraft:wall_torch}
    * (facing). The straight port hard-coded the floor torch, so torchGuess torches with air below ended
    * up FLOATING. This restores the original guess: floor torch when it can survive, else a wall_torch
    * whose FACING points away from a solid neighbour (the wall it hangs on is at relative(opposite)).</p>
    */
   private BlockState guessTorchState(Level world) {
      BlockPos pos = this.p.getBlockPos();
      BlockState floor = Blocks.TORCH.defaultBlockState();
      if (floor.canSurvive(world, pos)) {
         return floor;
      }
      for (Direction facing : Direction.Plane.HORIZONTAL) {
         // wall_torch FACING points away from the wall; the supporting block is at the OPPOSITE side.
         BlockState wall = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
         if (wall.canSurvive(world, pos)) {
            return wall;
         }
      }
      return floor;
   }

   public void pathBuild(Building th) {
      BlockState currentBlockState = this.p.getBlockActualState(th.world);
      if (!BlockItemUtilities.isPath(currentBlockState.getBlock()) && PathUtilities.canPathBeBuiltHere(currentBlockState)) {
         this.build(th.world, null, false, false);
      } else if (BlockItemUtilities.isPath(currentBlockState.getBlock())) {
         int targetPathLevel = 0;
         IBlockPath bp = (IBlockPath)this.block;

         for (int i = 0; i < th.villageType.pathMaterial.size(); i++) {
            if (BlockItemUtilities.isPath(this.block)
               && (th.villageType.pathMaterial.get(i).getBlock() == bp.getSingleSlab() || th.villageType.pathMaterial.get(i).getBlock() == bp.getDoubleSlab())) {
               targetPathLevel = i;
            }
         }

         int currentPathLevel = Integer.MAX_VALUE;
         IBlockPath currentPathBlock = (IBlockPath)currentBlockState.getBlock();

         for (int ix = 0; ix < th.villageType.pathMaterial.size(); ix++) {
            if (th.villageType.pathMaterial.get(ix).getBlock() == currentPathBlock.getDoubleSlab()
               || th.villageType.pathMaterial.get(ix).getBlock() == currentPathBlock.getSingleSlab()) {
               currentPathLevel = ix;
            }
         }

         if (currentPathLevel < targetPathLevel) {
            this.build(th.world, null, false, false);
         }
      }
   }

   public void setBlockstate(BlockState bs) {
      this.blockState = bs;
      this.meta = 0;
   }

   public void setMeta(byte meta) {
      this.meta = meta;
      this.blockState = this.block.defaultBlockState();
   }

   @Override
   public String toString() {
      return "(block: " + this.block + " meta: " + this.meta + " pos:" + this.p + ")";
   }
}
