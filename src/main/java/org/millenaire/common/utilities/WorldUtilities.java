package org.millenaire.common.utilities;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.item.InvItem;

public class WorldUtilities {
   public static boolean checkChunksGenerated(Level world, int start_x, int start_z, int end_x, int end_z) {
      start_x >>= 4;
      start_z >>= 4;
      end_x >>= 4;
      end_z >>= 4;
      end_x++;
      end_z++;

      for (int k1 = start_x; k1 <= end_x; k1++) {
         for (int l1 = start_z; l1 <= end_z; l1++) {
            if (!world.hasChunk(k1, l1)) {
               return false;
            }
         }
      }

      return true;
   }

   public static int countBlocksAround(Level world, int x, int y, int z, int rx, int ry, int rz) {
      int counter = 0;

      for (int i = x - rx; i <= x + rx; i++) {
         for (int j = y - ry; j <= y + ry; j++) {
            for (int k = z - rz; k <= z + rz; k++) {
               if (getBlock(world, i, j, k) != null && getBlockState(world, i, j, k).blocksMotion()) {
                  counter++;
               }
            }
         }
      }

      return counter;
   }

   public static Point findRandomStandingPosAround(Level world, Point dest) {
      if (dest == null) {
         return null;
      } else {
         for (int i = 0; i < 50; i++) {
            Point testdest = dest.getRelative(
               5 - MillCommonUtilities.randomInt(10), 5 - MillCommonUtilities.randomInt(20), 5 - MillCommonUtilities.randomInt(10)
            );
            if (BlockItemUtilities.isBlockWalkable(getBlock(world, testdest.getiX(), testdest.getiY() - 1, testdest.getiZ()))
               && !BlockItemUtilities.isBlockSolid(getBlock(world, testdest.getiX(), testdest.getiY(), testdest.getiZ()))
               && !BlockItemUtilities.isBlockSolid(getBlock(world, testdest.getiX(), testdest.getiY() + 1, testdest.getiZ()))) {
               return testdest;
            }
         }

         return null;
      }
   }

   public static int findSurfaceBlock(Level world, int x, int z) {
      BlockPos pos = new BlockPos(x, world.getMaxY(), z);

      while (
         pos.getY() > -1
            && !BlockItemUtilities.isBlockGround(getBlock(world, x, pos.getY(), z))
            && !(getBlock(world, x, pos.getY(), z) instanceof LiquidBlock)
      ) {
         pos = new BlockPos(x, pos.getY() - 1, z);
      }

      if (pos.getY() > 254) {
         pos = new BlockPos(x, 254, z);
      }

      return pos.getY() + 1;
   }

   public static Point findTopNonPassableBlock(Level world, int x, int z) {
      for (int y = 255; y > 0; y--) {
         if (getBlock(world, x, y, z).defaultBlockState().isSolid()) {
            return new Point(x, y, z);
         }
      }

      return null;
   }

   public static int findTopSoilBlock(Level world, int x, int z) {
      BlockPos pos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));

      while (pos.getY() > -1 && !BlockItemUtilities.isBlockGround(getBlock(world, x, pos.getY(), z))) {
         pos = new BlockPos(x, pos.getY() - 1, z);
      }

      if (pos.getY() > 254) {
         pos = new BlockPos(x, 254, z);
      }

      return pos.getY() + 1;
   }

   public static Point findVerticalStandingPos(Level world, Point dest) {
      if (dest == null) {
         return null;
      } else {
         int y = dest.getiY();

         while (
            y < 250
               && (
                  BlockItemUtilities.isBlockSolid(getBlock(world, dest.getiX(), y, dest.getiZ()))
                     || BlockItemUtilities.isBlockSolid(getBlock(world, dest.getiX(), y + 1, dest.getiZ()))
               )
         ) {
            y++;
         }

         while (y > 0 && !BlockItemUtilities.isBlockSolid(getBlock(world, dest.getiX(), y - 1, dest.getiZ()))) {
            y--;
         }

         if (y == 250) {
            return null;
         } else {
            return !BlockItemUtilities.isBlockWalkable(getBlock(world, dest.getiX(), y - 1, dest.getiZ())) ? null : new Point(dest.getiX(), y, dest.getiZ());
         }
      }
   }

   public static Block getBlock(Level world, int x, int y, int z) {
      return world.getBlockState(new BlockPos(x, y, z)).getBlock();
   }

   public static Block getBlock(Level world, Point p) {
      if (p.x < -3.2E7 || p.z < -3.2E7 || p.x >= 3.2E7 || p.z > 3.2E7) {
         return null;
      } else if (p.y < 0.0) {
         return null;
      } else {
         return p.y >= 256.0 ? null : getBlock(world, p.getiX(), p.getiY(), p.getiZ());
      }
   }

   public static int getBlockId(Block b) {
      return BuiltInRegistries.BLOCK.getId(b);
   }

   public static int getBlockMeta(Level world, int i, int j, int k) {
      return getBlockMeta(world, new Point(i, j, k));
   }

   public static int getBlockMeta(Level world, Point p) {
      if (p.x < -3.2E7 || p.z < -3.2E7 || p.x >= 3.2E7 || p.z > 3.2E7) {
         return -1;
      } else if (p.y < 0.0) {
         return -1;
      } else if (p.y >= 256.0) {
         return -1;
      } else {
         // 26.2: block metadata is gone; reconstruct the legacy 1.12 meta from the BlockState so callers that
         // depend on orientation/variant/age (lumberman, building export, nether wart, beds, wool...) keep working.
         return blockStateToLegacyMeta(world.getBlockState(p.getBlockPos()));
      }
   }

   public static int getBlockMetadata(Level world, int x, int y, int z) {
      return getBlockMeta(world, new Point(x, y, z));
   }

   /**
    * Reconstructs the 1.12 "block metadata" int from a 26.2 BlockState. Thin shim: the actual bit-packing
    * logic lives in {@link org.millenaire.common.convert.MillConvert#blockStateToLegacyMeta(BlockState)},
    * the single source of truth for 1.12↔26.2 conversion (this mirrors how {@link #legacyMetaToBlockState}
    * delegates the forward direction). Kept here so existing callers do not churn.
    */
   public static int blockStateToLegacyMeta(BlockState state) {
      return org.millenaire.common.convert.MillConvert.blockStateToLegacyMeta(state);
   }

   /**
    * Reverse of {@link #blockStateToLegacyMeta}: apply a 1.12 metadata value to the matching 26.2 BlockState
    * property when setBlockAndMetadata is asked to place a meta-bearing block. Covers the growth/orientation
    * cases Mill actually writes (crop/nether-wart age, farmland moisture, log axis); other blocks keep their
    * default state. Without this, incrementing writes like nether-wart growth reset the block to age 0.
    */
   public static BlockState legacyMetaToBlockState(Block block, int meta) {
      // M3: routed through the unified conversion protocol. The declarative legacy-blocks.txt table
      // covers the fixed-variant families (logs/slabs/stairs/ladders/torches/beds); the unbounded numeric
      // families (crop/wart age, farmland moisture, generic pillar axis) are applied property-driven.
      // Behaviour-identical to the former ad-hoc meta switch — see MillConvert.blockState(Block, int).
      return org.millenaire.common.convert.MillConvert.blockState(block, meta);
   }

   public static BlockState getBlockState(Level world, int x, int y, int z) {
      return world.getBlockState(new BlockPos(x, y, z));
   }

   public static BlockState getBlockState(Level world, Point p) {
      return world.getBlockState(p.getBlockPos());
   }

   public static BlockState getBlockStateValidGround(BlockState currentBlockState, boolean surface) {
      Block b = currentBlockState.getBlock();
      if (b == Blocks.BEDROCK) {
         return Blocks.DIRT.defaultBlockState();
      } else if (b == Blocks.STONE && surface) {
         return Blocks.DIRT.defaultBlockState();
      } else if (b == Blocks.STONE && !surface) {
         return currentBlockState;
      } else if (b == Blocks.DIRT) {
         return currentBlockState;
      } else if (b == Blocks.GRASS_BLOCK) {
         return Blocks.DIRT.defaultBlockState();
      } else if (b == Blocks.GRAVEL) {
         return currentBlockState;
      } else if (b == Blocks.SAND) {
         return currentBlockState;
      } else if (b == Blocks.SANDSTONE && surface) {
         return Blocks.SAND.defaultBlockState();
      } else if (b == Blocks.SANDSTONE && !surface) {
         return currentBlockState;
      } else {
         return b == Blocks.TERRACOTTA ? currentBlockState : null;
      }
   }

   public static Point getClosestBlock(Level world, Block[] blocks, Point pos, int rx, int ry, int rz) {
      return getClosestBlockMeta(world, blocks, -1, pos, rx, ry, rz);
   }

   public static Point getClosestBlockMeta(Level world, Block[] blocks, int meta, Point pos, int rx, int ry, int rz) {
      Point closest = null;
      double minDistance = 9.99999999E8;

      for (int i = pos.getiX() - rx; i <= pos.getiX() + rx; i++) {
         for (int j = pos.getiY() - ry; j <= pos.getiY() + ry; j++) {
            for (int k = pos.getiZ() - rz; k <= pos.getiZ() + rz; k++) {
               for (int l = 0; l < blocks.length; l++) {
                  if (getBlock(world, i, j, k) == blocks[l] && (meta == -1 || getBlockMeta(world, i, j, k) == meta)) {
                     Point temp = new Point(i, j, k);
                     if (closest == null || temp.distanceTo(pos) < minDistance) {
                        closest = temp;
                        minDistance = temp.distanceTo(pos);
                     }
                  }
               }
            }
         }
      }

      return minDistance < 9.99999999E8 ? closest : null;
   }

   public static ItemEntity getClosestItemVertical(Level world, Point p, List<InvItem> goods, int radius, int vertical) {
      List<Entity> list = getEntitiesWithinAABB(world, Entity.class, p, radius, vertical);
      double bestdist = Double.MAX_VALUE;
      ItemEntity citem = null;

      for (Entity ent : list) {
         if (ent.getClass() == ItemEntity.class) {
            ItemEntity item = (ItemEntity)ent;
            if (!item.isRemoved()) {
               for (InvItem key : goods) {
                  if (item.getItem().getItem() == key.getItem()) {
                     double dist = item.distanceToSqr(p.x, p.y, p.z);
                     if (dist < bestdist) {
                        bestdist = dist;
                        citem = item;
                     }
                  }
               }
            }
         }
      }

      return citem == null ? null : citem;
   }

   @SuppressWarnings("unchecked")
   public static <T extends Entity> List<T> getEntitiesWithinAABB(Level world, Class<T> type, Point p, int hradius, int vradius) {
      AABB area = new AABB(p.x, p.y, p.z, p.x + 1.0, p.y + 1.0, p.z + 1.0)
         .inflate(hradius, vradius, hradius);
      return world.getEntitiesOfClass(type, area);
   }

   public static <T extends Entity> List<T> getEntitiesWithinAABB(Level world, Class<T> type, Point pstart, Point pend) {
      AABB area = new AABB(pstart.x, pstart.y, pstart.z, pend.x, pend.y, pend.z);
      return world.getEntitiesOfClass(type, area);
   }

   public static Entity getEntityByUUID(Level world, UUID uuid) {
      return world.getEntity(uuid);
   }

   public static int getItemsFromChest(Container chest, Block block, int meta, int toTake) {
      return getItemsFromChest(chest, block.asItem(), meta, toTake);
   }

   public static int getItemsFromChest(Container chest, BlockState blockState, int toTake) {
      return getItemsFromChest(chest, blockState.getBlock(), 0, toTake);
   }

   public static int getItemsFromChest(Container chest, Item item, int meta, int toTake) {
      if (chest == null) {
         return 0;
      } else {
         int nb = 0;
         int maxSlot = chest.getContainerSize() - 1;
         if (chest instanceof Inventory) {
            maxSlot -= 4;
         }

         for (int i = maxSlot; i >= 0 && nb < toTake; i--) {
            ItemStack stack = chest.getItem(i);
            if (stack != null && stack.getItem() == item) {
               if (stack.getCount() <= toTake - nb) {
                  nb += stack.getCount();
                  chest.setItem(i, ItemStack.EMPTY);
               } else {
                  chest.removeItem(i, toTake - nb);
                  nb = toTake;
               }
            }

            if (item == Blocks.OAK_LOG.asItem()
               && meta == -1
               && stack != null
               && stack.getItem() == Blocks.ACACIA_LOG.asItem()) {
               if (stack.getCount() <= toTake - nb) {
                  nb += stack.getCount();
                  chest.setItem(i, ItemStack.EMPTY);
               } else {
                  chest.removeItem(i, toTake - nb);
                  nb = toTake;
               }
            }
         }

         return nb;
      }
   }

   public static int getItemsFromFirePit(TileEntityFirePit firepit, Item item, int toTake) {
      if (firepit == null) {
         return 0;
      } else {
         int taken = 0;

         for (int stackNb = 0; stackNb < 3; stackNb++) {
            ItemStack stack = firepit.getItem(4 + stackNb);
            if (taken < toTake && stack != null && stack.getItem() == item) {
               taken += firepit.removeItem(4 + stackNb, toTake).getCount();
            }
         }

         return taken;
      }
   }

   public static int getItemsFromFurnace(FurnaceBlockEntity furnace, Item item, int toTake) {
      if (furnace == null) {
         return 0;
      } else {
         int nb = 0;
         ItemStack stack = furnace.getItem(2);
         if (stack != null && stack.getItem() == item) {
            if (stack.getCount() <= toTake - nb) {
               nb += stack.getCount();
               furnace.setItem(2, ItemStack.EMPTY);
            } else {
               furnace.removeItem(2, toTake - nb);
               nb = toTake;
            }
         }

         return nb;
      }
   }

   public static Direction guessPanelFacing(Level world, Point p) {
      boolean northOpen = true;
      boolean southOpen = true;
      boolean eastOpen = true;
      boolean westOpen = true;
      if (getBlockState(world, p.getNorth()).isSolidRender()) {
         northOpen = false;
      }

      if (getBlockState(world, p.getEast()).isSolidRender()) {
         eastOpen = false;
      }

      if (getBlockState(world, p.getSouth()).isSolidRender()) {
         southOpen = false;
      }

      if (getBlockState(world, p.getWest()).isSolidRender()) {
         westOpen = false;
      }

      if (!eastOpen) {
         return Direction.WEST;
      } else if (!westOpen) {
         return Direction.EAST;
      } else if (!southOpen) {
         return Direction.NORTH;
      } else {
         return !northOpen ? Direction.SOUTH : null;
      }
   }

   public static boolean isBlockFullCube(Level world, int i, int j, int k) {
      BlockPos pos = new BlockPos(i, j, k);
      BlockState bs = world.getBlockState(pos);
      return bs == null ? false : bs.isCollisionShapeFullBlock(world, pos);
   }

   public static void playSound(Level world, Point p, SoundEvent sound, SoundSource category, float volume, float pitch) {
      world.playSound(null, p.x + 0.5, p.y + 0.5, p.z + 0.5, sound, category, volume, pitch);
   }

   public static void playSoundBlockBreaking(Level world, Point p, Block b, float volume) {
      if (b != null) {
         SoundType st = b.defaultBlockState().getSoundType();
         playSound(world, p, st.getBreakSound(), SoundSource.BLOCKS, st.getVolume() * volume, st.getPitch());
      }
   }

   public static void playSoundBlockPlaced(Level world, Point p, Block b, float volume) {
      if (b != null) {
         SoundType st = b.defaultBlockState().getSoundType();
         playSound(world, p, st.getPlaceSound(), SoundSource.BLOCKS, st.getVolume() * volume, st.getPitch());
      }
   }

   public static void playSoundByMillName(Level world, Point p, String soundMill, float volume) {
      if (soundMill.equals("metal")) {
         playSoundBlockPlaced(world, p, Blocks.IRON_BLOCK, volume);
      } else if (soundMill.equals("wood")) {
         playSoundBlockPlaced(world, p, Blocks.OAK_LOG, volume);
      } else if (soundMill.equals("wool")) {
         playSoundBlockPlaced(world, p, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), volume);
      } else if (soundMill.equals("glass")) {
         playSoundBlockPlaced(world, p, Blocks.GLASS, volume);
      } else if (soundMill.equals("stone")) {
         playSoundBlockPlaced(world, p, Blocks.STONE, volume);
      } else if (soundMill.equals("earth")) {
         playSoundBlockPlaced(world, p, Blocks.DIRT, volume);
      } else if (soundMill.equals("sand")) {
         playSoundBlockPlaced(world, p, Blocks.SAND, volume);
      } else {
         // FAIL-FAST: a building referenced a sound name that maps to nothing (1.12 logged a synthetic
         // exception and silently played nothing). An unknown sound id is corrupt content; surface it.
         throw MillCrash.fail("World", "playSoundByMillName: unknown sound name '" + soundMill + "'");
      }
   }

   public static boolean setBlock(Level world, Point p, Block block) {
      return setBlock(world, p, block, true, false);
   }

   public static boolean setBlock(Level world, Point p, Block block, boolean notify, boolean playSound) {
      if (p.x < -3.2E7 || p.z < -3.2E7 || p.x >= 3.2E7 || p.z > 3.2E7) {
         return false;
      } else if (p.y < 0.0) {
         return false;
      } else if (p.y >= 256.0) {
         return false;
      } else {
         if (playSound && block == Blocks.AIR) {
            Block oldBlock = getBlock(world, p.getiX(), p.getiY(), p.getiZ());
            if (oldBlock != null) {
               if (oldBlock.defaultBlockState().getSoundType() == SoundType.GRAVEL) {
                  playSoundBlockBreaking(world, p, oldBlock, 0.5F);
               } else {
                  playSoundBlockBreaking(world, p, oldBlock, 1.0F);
               }
            }
         }

         if (notify) {
            world.setBlockAndUpdate(p.getBlockPos(), block.defaultBlockState());
         } else {
            world.setBlock(p.getBlockPos(), block.defaultBlockState(), 2);
         }

         if (playSound && block != Blocks.AIR) {
            if (block.defaultBlockState().getSoundType() == SoundType.GRAVEL) {
               playSoundBlockBreaking(world, p, block, 0.5F);
            } else {
               playSoundBlockBreaking(world, p, block, 1.0F);
            }
         }

         return true;
      }
   }

   public static boolean setBlockAndMetadata(Level world, int x, int y, int z, Block block, int metadata, boolean notify, boolean playSound) {
      if (x < -32000000 || z < -32000000 || x >= 32000000 || z > 32000000) {
         return false;
      } else if (y < 0) {
         return false;
      } else if (y >= 256) {
         return false;
      } else {
         if (playSound && block != Blocks.AIR) {
            Block oldBlock = getBlock(world, x, y, z);
            if (oldBlock != null) {
               playSoundBlockBreaking(world, new Point(x, y, z), oldBlock, 1.0F);
            }
         }

         if (block == null) {
            // FAIL-FAST: a caller asked to place a null block (1.12 logged-and-returned-false). A null block
            // reference is a programming/content bug that silently skips the write; surface it loudly.
            throw MillCrash.fail("World", "setBlockAndMetadata called with null block at " + x + "/" + y + "/" + z);
         } else {
            BlockState state = legacyMetaToBlockState(block, metadata); // apply the legacy meta to the matching BlockState property (crop/wart age, farmland moisture, log axis) instead of ignoring it
            if (notify) {
               world.setBlockAndUpdate(new BlockPos(x, y, z), state);
            } else {
               world.setBlock(new BlockPos(x, y, z), state, 2);
            }

            if (playSound && block != Blocks.AIR) {
               playSoundBlockPlaced(world, new Point(x, y, z), block, 2.0F);
            }

            return true;
         }
      }
   }

   public static boolean setBlockAndMetadata(Level world, Point p, Block block, int metadata) {
      return setBlockAndMetadata(world, p, block, metadata, true, false);
   }

   public static boolean setBlockAndMetadata(Level world, Point p, Block block, int metadata, boolean notify, boolean playSound) {
      return setBlockAndMetadata(world, p.getiX(), p.getiY(), p.getiZ(), block, metadata, notify, playSound);
   }

   public static boolean setBlockMetadata(Level world, int x, int y, int z, int metadata, boolean notify) {
      if (x < -32000000 || z < -32000000 || x >= 32000000 || z > 32000000) {
         return false;
      } else if (y < 0) {
         return false;
      } else if (y >= 256) {
         return false;
      } else {
         Point p = new Point(x, y, z);
         BlockState state = p.getBlockActualState(world); // 26.2: getStateFromMeta is gone; the legacy "metadata" arg no longer selects a variant — use the block default state
         if (notify) {
            world.setBlockAndUpdate(p.getBlockPos(), state);
         } else {
            world.setBlock(p.getBlockPos(), state, 2);
         }

         return true;
      }
   }

   public static boolean setBlockMetadata(Level world, Point p, int metadata) {
      return setBlockMetadata(world, p, metadata, true);
   }

   public static boolean setBlockMetadata(Level world, Point p, int metadata, boolean notify) {
      return setBlockMetadata(world, p.getiX(), p.getiY(), p.getiZ(), metadata, notify);
   }

   public static boolean setBlockstate(Level world, Point p, BlockState bs, boolean notify, boolean playSound) {
      if (p.x < -3.2E7 || p.z < -3.2E7 || p.x >= 3.2E7 || p.z > 3.2E7 || p.y < 0.0 || p.y >= 256.0) {
         return false;
      }

      if (playSound && bs.getBlock() != Blocks.AIR) {
         Block oldBlock = getBlock(world, p.getiX(), p.getiY(), p.getiZ());
         if (oldBlock != null) {
            playSoundBlockBreaking(world, p, oldBlock, 1.0F);
         }
      }

      if (notify) {
         world.setBlockAndUpdate(p.getBlockPos(), bs);
      } else {
         world.setBlock(p.getBlockPos(), bs, 2);
      }

      if (playSound && bs.getBlock() != Blocks.AIR) {
         playSoundBlockPlaced(world, p, bs.getBlock(), 2.0F);
      }

      return true;
   }

   public static void spawnExp(Level world, Point p, int nb) {
      if (world instanceof ServerLevel serverLevel) {
         ExperienceOrb.award(serverLevel, new net.minecraft.world.phys.Vec3(p.x + 0.5, p.y + 5.0, p.z + 0.5), nb);
      }
   }

   public static ItemEntity spawnItem(Level world, Point p, ItemStack itemstack, float f) {
      if (world.isClientSide()) {
         return null;
      } else {
         ItemEntity entityitem = new ItemEntity(world, p.x, p.y + f, p.z, itemstack);
         entityitem.setDefaultPickUpDelay();
         world.addFreshEntity(entityitem);
         return entityitem;
      }
   }

   private static Mob createMob(Level world, Identifier mobType) {
      EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(mobType);
      if (type == null) {
         return null;
      }
      Entity entity = type.create(world, EntitySpawnReason.SPAWNER);
      return entity instanceof Mob ? (Mob)entity : null;
   }

   public static void spawnMobsAround(Level world, Point p, int radius, Identifier mobType, int minNb, int extraNb) {
      if (!(world instanceof ServerLevel serverLevel)) {
         return;
      }
      int nb = minNb;
      if (extraNb > 0) {
         nb = minNb + MillCommonUtilities.randomInt(extraNb);
      }

      for (int i = 0; i < nb; i++) {
         Mob entityliving = createMob(world, mobType);
         if (entityliving != null) {
            boolean spawned = false;

            for (int j = 0; j < 20 && !spawned; j++) {
               double ex = p.x + (world.getRandom().nextDouble() * 2.0 - 1.0) * radius;
               double ey = p.y + world.getRandom().nextInt(3) - 1.0;
               double ez = p.z + (world.getRandom().nextDouble() * 2.0 - 1.0) * radius;
               Point ep = new Point(ex, ey, ez);
               BlockPos belowPos = ep.getBelow().getBlockPos();
               if (world.getBlockState(belowPos).isCollisionShapeFullBlock(world, belowPos)) {
                  entityliving.snapTo(ex, ey, ez, world.getRandom().nextFloat() * 360.0F, 0.0F);
                  if (entityliving.checkSpawnRules(world, EntitySpawnReason.SPAWNER)) {
                     serverLevel.addFreshEntity(entityliving);
                     MillLog.major(null, "Entering world: " + entityliving.getClass().getName());
                     spawned = true;
                  }
               }
            }

            if (!spawned) {
               MillLog.major(null, "No valid space found.");
            }

            entityliving.spawnAnim();
         }
      }
   }

   public static Entity spawnMobsSpawner(Level world, Point p, Identifier mobType) {
      if (!(world instanceof ServerLevel serverLevel)) {
         return null;
      }
      Mob entityliving = createMob(world, mobType);
      if (entityliving == null) {
         return null;
      } else {
         int x = MillCommonUtilities.randomInt(2) - 1;
         int z = MillCommonUtilities.randomInt(2) - 1;
         int ex = (int)(p.x + x);
         int ey = (int)p.y;
         int ez = (int)(p.z + z);
         if (getBlock(world, ex, ey, ez) != Blocks.AIR && getBlock(world, ex, ey + 1, ez) != Blocks.AIR) {
            return null;
         } else {
            entityliving.snapTo(ex, ey, ez, world.getRandom().nextFloat() * 360.0F, 0.0F);
            serverLevel.addFreshEntity(entityliving);
            entityliving.spawnAnim();
            return entityliving;
         }
      }
   }
}
