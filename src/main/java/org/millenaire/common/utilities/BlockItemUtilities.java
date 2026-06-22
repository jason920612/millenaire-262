package org.millenaire.common.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import org.millenaire.common.block.BlockMillCrops;
import org.millenaire.common.block.BlockPathSlab;
import org.millenaire.common.block.IBlockPath;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.BuildingLocation;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;

public class BlockItemUtilities {
   // 26.2: net.minecraft.block.material.Material was removed, so there is no single material per block.
   // getBlockMaterialName() now recovers the placement-critical 1.12 material names used by
   // blocktypes.txt (air/water/lava/ice/snow/grass/ground/rock/sand/clay/fire/tnt/cactus/glass/iron/
   // anvil/sponge) from block identity + vanilla block tags, so the *_MATERIALS sets stay functional.
   // The *_BLOCKS / *_EXCEPTIONS name-based sets still cover anything not derivable from a tag, and a
   // handful of obscure 1.12 materials (e.g. circuits/redstone_light) remain unmapped (null).
   private static final Set<String> FORBIDDEN_MATERIALS = new HashSet<>(20);
   private static final Set<String> FORBIDDEN_BLOCKS = new HashSet<>(20);
   private static final Set<String> FORBIDDEN_EXCEPTIONS = new HashSet<>(20);
   private static final Set<String> GROUND_MATERIALS = new HashSet<>(20);
   private static final Set<String> GROUND_BLOCKS = new HashSet<>(20);
   private static final Set<String> GROUND_EXCEPTIONS = new HashSet<>(20);
   private static final Set<String> DANGER_MATERIALS = new HashSet<>(20);
   private static final Set<String> DANGER_BLOCKS = new HashSet<>(20);
   private static final Set<String> DANGER_EXCEPTIONS = new HashSet<>(20);
   private static final Set<String> WATER_MATERIALS = new HashSet<>(20);
   private static final Set<String> WATER_BLOCKS = new HashSet<>(20);
   private static final Set<String> WATER_EXCEPTIONS = new HashSet<>(20);
   private static final Set<String> PATH_REPLACEABLE_MATERIALS = new HashSet<>(20);
   private static final Set<String> PATH_REPLACEABLE_BLOCKS = new HashSet<>(20);
   private static final Set<String> PATH_REPLACEABLE_EXCEPTIONS = new HashSet<>(20);

   public static void checkForHarvestTheft(Player player, BlockPos pos) {
      MillWorldData mwd = Mill.getMillWorld(player.level());
      Point actionPos = new Point(pos);
      Building closestVillageTH = mwd.getClosestVillage(actionPos);
      if (closestVillageTH != null && !closestVillageTH.controlledBy(player)) {
         BuildingLocation location = closestVillageTH.getLocationAtCoord(actionPos);
         if (location != null) {
            Building building = location.getBuilding(player.level());
            if (building != null) {
               boolean isBuildingPlayerOwned = building.location.getPlan() != null
                  && (building.location.getPlan().price > 0 || building.location.getPlan().isgift);
               if (!isBuildingPlayerOwned) {
                  UserProfile serverProfile = VillageUtilities.getServerProfile(player.level(), player);
                  if (serverProfile != null) {
                     int reputationLost = 100;
                     serverProfile.adjustReputation(closestVillageTH, -100);
                     ServerSender.sendTranslatedSentence(player, '6', "ui.stealingcrops", "100");
                  }
               }
            }
         }
      }
   }

   public static String getBlockCanonicalName(Block block) {
      if (block == null) {
         return null;
      }
      Identifier id = BuiltInRegistries.BLOCK.getKey(block);
      return id != null ? id.toString() : null;
   }

   public static String getBlockMaterialName(Block block) {
      // 26.2: net.minecraft.block.material.Material was fully removed, so there is no single material per
      // block to map to the 1.12 names. This recovers the placement-critical 1.12 material names used by
      // blocktypes.txt (air/water/lava/ice/packed_ice/snow/grass/ground/clay/sand/rock/fire/tnt/cactus/
      // glass/iron/anvil/sponge) from block identity + vanilla block tags, so the *_MATERIALS sets stay
      // functional for the village placement / path / forbidden / danger checks. The *_BLOCKS/*_EXCEPTIONS
      // name sets cover anything not derivable from a tag; a few obscure 1.12 materials remain null.
      if (block == null) {
         return null;
      }
      BlockState state = block.defaultBlockState();
      if (state.isAir()) {
         return "air";
      }
      if (block == Blocks.WATER) {
         return "water";
      }
      if (block == Blocks.LAVA) {
         return "lava";
      }
      if (block == Blocks.ICE || block == Blocks.FROSTED_ICE || block == Blocks.BLUE_ICE) {
         return "ice";
      }
      if (block == Blocks.PACKED_ICE) {
         return "packed_ice";
      }
      if (block == Blocks.SNOW) {
         return "snow";
      }
      if (block == Blocks.SNOW_BLOCK) {
         return "crafted_snow";
      }
      // Catch other (modded) fluid blocks as water so they are still treated as liquid for placement.
      if (block instanceof LiquidBlock && !state.getFluidState().isEmpty()) {
         return "water";
      }
      // Recover the remaining placement-critical 1.12 material names used by blocktypes.txt
      // (grass/ground/rock/sand/clay/fire/tnt/cactus/glass/iron/anvil/sponge) from block identity +
      // vanilla block tags. These drive the village placement / path / forbidden / danger checks.
      if (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL) {
         return "grass";
      }
      if (state.is(net.minecraft.tags.BlockTags.DIRT) || block == Blocks.FARMLAND || block == Blocks.DIRT_PATH || block == Blocks.GRAVEL) {
         return "ground";
      }
      if (block == Blocks.CLAY) {
         return "clay";
      }
      if (state.is(net.minecraft.tags.BlockTags.SAND)) {
         return "sand";
      }
      if (state.is(net.minecraft.tags.BlockTags.ICE)) {
         // ICE / FROSTED_ICE / BLUE_ICE already handled above; this also covers any other ice-tagged blocks.
         return "ice";
      }
      if (state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
         || state.is(net.minecraft.tags.BlockTags.STONE_BRICKS)
         || block == Blocks.COBBLESTONE
         || block == Blocks.STONE) {
         return "rock";
      }
      if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
         return "fire";
      }
      if (block == Blocks.TNT) {
         return "tnt";
      }
      if (block == Blocks.CACTUS) {
         return "cactus";
      }
      if (block == Blocks.GLASS || block instanceof net.minecraft.world.level.block.StainedGlassBlock) {
         return "glass";
      }
      if (block == Blocks.IRON_BLOCK) {
         return "iron";
      }
      if (state.is(net.minecraft.tags.BlockTags.ANVIL)) {
         return "anvil";
      }
      if (block == Blocks.SPONGE || block == Blocks.WET_SPONGE) {
         return "sponge";
      }
      return null;
   }

   // 26.2: FlowerPotBlock.EnumFlowerType was removed along with item/block metadata, so the signature
   // takes the legacy enum ordinal as an int. The index → item mapping below reproduces the original
   // EnumFlowerType ordering exactly (POPPY=0 … CACTUS=20), returning the corresponding 26.2 flattened
   // flower/sapling/plant item (1.12 returned the shared red_flower/sapling block + metadata; those are
   // now distinct items). Empty/unknown → EMPTY, matching the old EMPTY/default case.
   public static ItemStack getFlowerpotItemStackFromEnum(int type) {
      switch (type) {
         case 0:
            return new ItemStack(Blocks.POPPY);
         case 1:
            return new ItemStack(Blocks.BLUE_ORCHID);
         case 2:
            return new ItemStack(Blocks.ALLIUM);
         case 3:
            return new ItemStack(Blocks.AZURE_BLUET);
         case 4:
            return new ItemStack(Blocks.RED_TULIP);
         case 5:
            return new ItemStack(Blocks.ORANGE_TULIP);
         case 6:
            return new ItemStack(Blocks.WHITE_TULIP);
         case 7:
            return new ItemStack(Blocks.PINK_TULIP);
         case 8:
            return new ItemStack(Blocks.OXEYE_DAISY);
         case 9:
            return new ItemStack(Blocks.DANDELION);
         case 10:
            return new ItemStack(Blocks.OAK_SAPLING);
         case 11:
            return new ItemStack(Blocks.SPRUCE_SAPLING);
         case 12:
            return new ItemStack(Blocks.BIRCH_SAPLING);
         case 13:
            return new ItemStack(Blocks.JUNGLE_SAPLING);
         case 14:
            return new ItemStack(Blocks.ACACIA_SAPLING);
         case 15:
            return new ItemStack(Blocks.DARK_OAK_SAPLING);
         case 16:
            return new ItemStack(Blocks.RED_MUSHROOM);
         case 17:
            return new ItemStack(Blocks.BROWN_MUSHROOM);
         case 18:
            return new ItemStack(Blocks.DEAD_BUSH);
         case 19:
            return new ItemStack(Blocks.FERN);
         case 20:
            return new ItemStack(Blocks.CACTUS);
         default:
            return ItemStack.EMPTY;
      }
   }

   public static ItemStack getItemStackFromBlockState(BlockState state, int quantity) {
      return new ItemStack(state.getBlock(), quantity); // 26.2: item/block metadata removed; the BlockState identity is the item
   }

   public static BlockState getLogBlockstateFromPlankMeta(int plankMeta) {
      // 26.2: the 1.12 plank "variant" metadata (BlockPlanks.EnumType, 0-5) is gone; each wood type is
      // now a distinct log block. This maps the legacy plank meta to the matching 26.2 log block.
      switch (plankMeta) {
         case 1:
            return Blocks.SPRUCE_LOG.defaultBlockState();
         case 2:
            return Blocks.BIRCH_LOG.defaultBlockState();
         case 3:
            return Blocks.JUNGLE_LOG.defaultBlockState();
         case 4:
            return Blocks.ACACIA_LOG.defaultBlockState();
         case 5:
            return Blocks.DARK_OAK_LOG.defaultBlockState();
         default:
            return Blocks.OAK_LOG.defaultBlockState();
      }
   }

   public static void initBlockTypes() {
      File mainBlockTypesFile = new File(MillCommonUtilities.getMillenaireContentDir(), "blocktypes.txt");
      if (!mainBlockTypesFile.exists()) {
         System.err.println("ERROR: Could not find the blocktypes file at " + mainBlockTypesFile.getAbsolutePath());
         Mill.startupError = true;
      } else {
         boolean success = readBlockTypesFile(mainBlockTypesFile);
         if (!success) {
            System.err.println("ERROR: Could not read the blocktypes file at " + mainBlockTypesFile.getAbsolutePath());
            Mill.startupError = true;
         } else {
            File customBlockTypesFile = new File(MillCommonUtilities.getMillenaireCustomContentDir(), "blocktypes.txt");
            if (customBlockTypesFile.exists()) {
               readBlockTypesFile(customBlockTypesFile);
            }
         }
      }
   }

   public static boolean isBlockDangerous(Block b) {
      if (b == null || b == Blocks.AIR || DANGER_EXCEPTIONS.contains(getBlockCanonicalName(b))) {
         return false;
      } else {
         return DANGER_MATERIALS.contains(getBlockMaterialName(b)) ? true : DANGER_BLOCKS.contains(getBlockCanonicalName(b));
      }
   }

   public static boolean isBlockDecorativePlant(Block block) {
      if (block == null || block == Blocks.AIR) {
         return false;
      } else {
         return block instanceof BlockMillCrops || block instanceof CropBlock ? false : block instanceof BushBlock;
      }
   }

   public static boolean isBlockForbidden(Block b) {
      if (b == null || b == Blocks.AIR || FORBIDDEN_EXCEPTIONS.contains(getBlockCanonicalName(b))) {
         return false;
      } else if (b instanceof EntityBlock) {
         return true;
      } else {
         return FORBIDDEN_MATERIALS.contains(getBlockMaterialName(b)) ? true : FORBIDDEN_BLOCKS.contains(getBlockCanonicalName(b));
      }
   }

   public static boolean isBlockGround(Block b) {
      if (b == null || b == Blocks.AIR || GROUND_EXCEPTIONS.contains(getBlockCanonicalName(b))) {
         return false;
      } else {
         return GROUND_MATERIALS.contains(getBlockMaterialName(b)) ? true : GROUND_BLOCKS.contains(getBlockCanonicalName(b));
      }
   }

   public static boolean isBlockLiquid(Block b) {
      return b == null || b == Blocks.AIR ? false : b instanceof LiquidBlock;
   }

   public static boolean isBlockOpaqueCube(Block block) {
      BlockState bs = block.defaultBlockState();
      return bs.isSolidRender();
   }

   public static boolean isBlockPathReplaceable(Block b) {
      if (b == null) {
         return false;
      } else if (b == Blocks.AIR) {
         return false;
      } else if (PATH_REPLACEABLE_EXCEPTIONS.contains(getBlockCanonicalName(b))) {
         return false;
      } else {
         return PATH_REPLACEABLE_MATERIALS.contains(getBlockMaterialName(b)) ? true : PATH_REPLACEABLE_BLOCKS.contains(getBlockCanonicalName(b));
      }
   }

   public static boolean isBlockSolid(Block b) {
      if (b == null) {
         return false;
      } else {
         return isTopSolid(b)
            ? true
            : b == Blocks.GLASS
               || b == Blocks.GLASS_PANE
               || b == Blocks.STONE_SLAB
               || b instanceof SlabBlock
               || b instanceof StairBlock
               || isFence(b)
               || b == MillBlocks.PAPER_WALL
               || b instanceof IBlockPath
               || b instanceof FarmlandBlock;
      }
   }

   public static boolean isBlockWalkable(Block b) {
      if (b == null) {
         return false;
      } else {
         return isTopSolid(b)
            ? true
            : b == Blocks.GLASS
               || b == Blocks.STONE_SLAB
               || b instanceof SlabBlock
               || b instanceof StairBlock
               || b instanceof IBlockPath
               || b instanceof FarmlandBlock;
      }
   }

   private static boolean isTopSolid(Block b) {
      BlockState bs = b.defaultBlockState();
      return bs.isSolidRender()
         || bs.isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO, net.minecraft.core.Direction.UP);
   }

   public static boolean isBlockWater(Block b) {
      if (b == null || b == Blocks.AIR || WATER_EXCEPTIONS.contains(getBlockCanonicalName(b))) {
         return false;
      } else {
         return WATER_MATERIALS.contains(getBlockMaterialName(b)) ? true : WATER_BLOCKS.contains(getBlockCanonicalName(b));
      }
   }

   public static boolean isFence(Block block) {
      return block == Blocks.ACACIA_FENCE
         || block == Blocks.BIRCH_FENCE
         || block == Blocks.DARK_OAK_FENCE
         || block == Blocks.JUNGLE_FENCE
         || block == Blocks.OAK_FENCE
         || block == Blocks.SPRUCE_FENCE;
   }

   public static boolean isFenceGate(Block block) {
      return block == Blocks.ACACIA_FENCE_GATE
         || block == Blocks.BIRCH_FENCE_GATE
         || block == Blocks.DARK_OAK_FENCE_GATE
         || block == Blocks.JUNGLE_FENCE_GATE
         || block == Blocks.OAK_FENCE_GATE
         || block == Blocks.SPRUCE_FENCE_GATE;
   }

   public static boolean isPath(Block block) {
      return block instanceof IBlockPath || block instanceof BlockPathSlab;
   }

   public static boolean isPathSlab(Block block) {
      return block instanceof BlockPathSlab;
   }

   public static boolean isWoodenDoor(Block block) {
      // 26.2: block Material is removed (was getMaterial() == Material.WOOD on a DoorBlock). The vanilla
      // BlockTags.WOODEN_DOORS tag is the faithful replacement and also covers modded wooden doors.
      return block instanceof DoorBlock && block.defaultBlockState().is(net.minecraft.tags.BlockTags.WOODEN_DOORS);
   }

   private static boolean readBlockTypesFile(File file) {
      if (!file.exists()) {
         return false;
      } else {
         try {
            BufferedReader reader = MillCommonUtilities.getReader(file);

            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
               if (line.trim().length() > 0 && !line.startsWith("//")) {
                  String[] temp = line.split("=");
                  if (temp.length == 2) {
                     String key = temp[0].trim().toLowerCase();
                     String value = temp[1];
                     if (key.equals("forbidden_materials")) {
                        FORBIDDEN_MATERIALS.clear();
                        FORBIDDEN_MATERIALS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("forbidden_blocks")) {
                        FORBIDDEN_BLOCKS.clear();
                        FORBIDDEN_BLOCKS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("forbidden_exceptions")) {
                        FORBIDDEN_EXCEPTIONS.clear();
                        FORBIDDEN_EXCEPTIONS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("ground_materials")) {
                        GROUND_MATERIALS.clear();
                        GROUND_MATERIALS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("ground_blocks")) {
                        GROUND_BLOCKS.clear();
                        GROUND_BLOCKS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("ground_exceptions")) {
                        GROUND_EXCEPTIONS.clear();
                        GROUND_EXCEPTIONS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("danger_materials")) {
                        DANGER_MATERIALS.clear();
                        DANGER_MATERIALS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("danger_blocks")) {
                        DANGER_BLOCKS.clear();
                        DANGER_BLOCKS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("danger_exceptions")) {
                        DANGER_EXCEPTIONS.clear();
                        DANGER_EXCEPTIONS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("water_materials")) {
                        WATER_MATERIALS.clear();
                        WATER_MATERIALS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("water_blocks")) {
                        WATER_BLOCKS.clear();
                        WATER_BLOCKS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("water_exceptions")) {
                        WATER_EXCEPTIONS.clear();
                        WATER_EXCEPTIONS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("path_replaceable_materials")) {
                        PATH_REPLACEABLE_MATERIALS.clear();
                        PATH_REPLACEABLE_MATERIALS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("path_replaceable_blocks")) {
                        PATH_REPLACEABLE_BLOCKS.clear();
                        PATH_REPLACEABLE_BLOCKS.addAll(Arrays.asList(value.split(",")));
                     } else if (key.equals("path_replaceable_exceptions")) {
                        PATH_REPLACEABLE_EXCEPTIONS.clear();
                        PATH_REPLACEABLE_EXCEPTIONS.addAll(Arrays.asList(value.split(",")));
                     } else {
                        MillLog.error(null, "Unknown block type category on line: " + line);
                     }
                  }
               }
            }

            reader.close();
            return true;
         } catch (Exception blockTypesException) {
            // FAIL-FAST: a parse error left the block-category lists (forbidden/ground/danger/water/path
            // materials, used by pathfinding) empty or partial. 1.12 returned false (a generic startup
            // error). Surface the real cause instead of a vague boolean.
            throw MillCrash.fail("BlockTypes", "failed to read blocktypes file " + file.getName() + ": " + blockTypesException);
         }
      }
   }
}
