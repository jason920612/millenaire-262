package org.millenaire.common.block;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.Identifier;
import org.millenaire.common.block.mock.MockBlockAnimalSpawn;
import org.millenaire.common.block.mock.MockBlockBannerHanging;
import org.millenaire.common.block.mock.MockBlockBannerStanding;
import org.millenaire.common.block.mock.MockBlockDecor;
import org.millenaire.common.block.mock.MockBlockFree;
import org.millenaire.common.block.mock.MockBlockMainChest;
import org.millenaire.common.block.mock.MockBlockMarker;
import org.millenaire.common.block.mock.MockBlockSoil;
import org.millenaire.common.block.mock.MockBlockSource;
import org.millenaire.common.block.mock.MockBlockTreeSpawn;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.item.ItemBlockMeta;
import org.millenaire.common.item.ItemHalfSlab;
import org.millenaire.common.item.ItemMillBed;
import org.millenaire.common.item.ItemMillSapling;
import org.millenaire.common.item.ItemMockBanner;
import org.millenaire.common.item.ItemPathSlab;
import org.millenaire.common.item.ItemSlabMeta;

public class MillBlocks {
   public static BlockDecorativeWood WOOD_DECORATION;
   public static BlockDecorativeStone STONE_DECORATION;
   public static BlockDecorativeEarth EARTH_DECORATION;
   public static BlockMillWall WALL_MUD_BRICK;
   public static Map<String, Map<DyeColor, Block>> PAINTED_BRICK_MAP = new HashMap<>();
   public static BlockPaintedBricks PAINTED_BRICK_WHITE;
   public static BlockPaintedBricks PAINTED_BRICK_DECORATED_WHITE;
   public static BlockExtendedMudBrick EXTENDED_MUD_BRICK;
   public static BlockSlabWood SLAB_WOOD_DECORATION;
   public static BlockSlabStone SLAB_STONE_DECORATION;
   public static BlockMillStairs STAIRS_TIMBERFRAME;
   public static BlockMillStairs STAIRS_MUDBRICK;
   public static BlockMillStairs STAIRS_COOKEDBRICK;
   public static BlockMillStairs STAIRS_THATCH;
   public static BlockMillStairs STAIRS_BYZ_TILES;
   public static BlockMillSandstone SANDSTONE_CARVED;
   public static BlockMillSandstone SANDSTONE_RED_CARVED;
   public static BlockMillSandstone SANDSTONE_OCHRE_CARVED;
   public static BlockMillStairs STAIRS_SANDSTONE_CARVED;
   public static BlockMillStairs STAIRS_SANDSTONE_RED_CARVED;
   public static BlockMillStairs STAIRS_SANDSTONE_OCHRE_CARVED;
   public static BlockMillSlab SLAB_SANDSTONE_CARVED;
   public static BlockMillSlab SLAB_SANDSTONE_RED_CARVED;
   public static BlockMillSlab SLAB_SANDSTONE_OCHRE_CARVED;
   public static BlockMillWall WALL_SANDSTONE_CARVED;
   public static BlockMillWall WALL_SANDSTONE_RED_CARVED;
   public static BlockMillWall WALL_SANDSTONE_OCHRE_CARVED;
   public static BlockWetBrick WET_BRICK;
   public static BlockSilkWorm SILK_WORM;
   public static BlockSnailSoil SNAIL_SOIL;
   public static BlockPath PATHDIRT;
   public static BlockPathSlab PATHDIRT_SLAB;
   public static BlockPath PATHGRAVEL;
   public static BlockPathSlab PATHGRAVEL_SLAB;
   public static BlockPath PATHSLABS;
   public static BlockPathSlab PATHSLABS_SLAB;
   public static BlockPath PATHSANDSTONE;
   public static BlockPathSlab PATHSANDSTONE_SLAB;
   public static BlockPath PATHGRAVELSLABS;
   public static BlockPathSlab PATHGRAVELSLABS_SLAB;
   public static BlockPath PATHOCHRESLABS;
   public static BlockPathSlab PATHOCHRESLABS_SLAB;
   public static BlockPath PATHSNOW;
   public static BlockPathSlab PATHSNOW_SLAB;
   public static BlockLockedChest LOCKED_CHEST;
   public static BlockMillStainedGlass STAINED_GLASS;
   public static BlockRosette ROSETTE;
   public static BlockPanel PANEL;
   public static BlockMillPane PAPER_WALL;
   public static BlockBars WOODEN_BARS;
   public static BlockBars WOODEN_BARS_INDIAN;
   public static BlockRosetteBars WOODEN_BARS_ROSETTE;
   public static BlockOrientedSlab.BlockOrientedSlabDouble BYZANTINE_TILES;
   public static BlockOrientedSlab.BlockOrientedSlabSlab BYZANTINE_TILES_SLAB;
   public static BlockOrientedSlabDoubleDecorated BYZANTINE_STONE_TILES;
   public static BlockOrientedSlabDoubleDecorated BYZANTINE_SANDSTONE_TILES;
   public static BlockMillSandstoneDecorated BYZANTINE_STONE_ORNAMENT;
   public static BlockMillSandstoneDecorated BYZANTINE_SANDSTONE_ORNAMENT;
   public static BlockMillCrops CROP_RICE;
   public static BlockMillCrops CROP_TURMERIC;
   public static BlockMillCrops CROP_MAIZE;
   public static BlockGrapeVine CROP_VINE;
   public static BlockMillCrops CROP_COTTON;
   public static BlockAlchemistExplosive ALCHEMIST_EXPLOSIVE;
   public static BlockMillBed BED_STRAW;
   public static BlockMillBed BED_CHARPOY;
   public static MockBlockMarker MARKER_BLOCK;
   public static MockBlockMainChest MAIN_CHEST;
   public static MockBlockAnimalSpawn ANIMAL_SPAWN;
   public static MockBlockSource SOURCE;
   public static MockBlockFree FREE_BLOCK;
   public static MockBlockTreeSpawn TREE_SPAWN;
   public static MockBlockSoil SOIL_BLOCK;
   public static MockBlockDecor DECOR_BLOCK;
   public static MockBlockBannerHanging VILLAGE_BANNER_WALL;
   public static MockBlockBannerStanding VILLAGE_BANNER_STANDING;
   public static MockBlockBannerHanging CULTURE_BANNER_WALL;
   public static MockBlockBannerStanding CULTURE_BANNER_STANDING;
   public static BlockSod SOD;
   public static BlockCustomIce ICE_BRICK;
   public static BlockCustomSnow SNOW_BRICK;
   public static BlockMillStatue INUIT_CARVING;
   public static BlockMillWall SNOW_WALL;
   public static BlockFirePit FIRE_PIT;
   public static BlockImportTable IMPORT_TABLE;
   public static BlockMillSapling SAPLING_APPLETREE;
   public static BlockFruitLeaves LEAVES_APPLETREE;
   public static BlockMillSapling SAPLING_OLIVETREE;
   public static BlockFruitLeaves LEAVES_OLIVETREE;
   public static BlockMillSapling SAPLING_PISTACHIO;
   public static BlockFruitLeaves LEAVES_PISTACHIO;
   public static BlockMillStairs STAIRS_GRAY_TILES;
   public static BlockMillStairs STAIRS_GREEN_TILES;
   public static BlockMillStairs STAIRS_RED_TILES;
   public static BlockOrientedSlab.BlockOrientedSlabDouble GRAY_TILES;
   public static BlockOrientedSlab.BlockOrientedSlabDouble GREEN_TILES;
   public static BlockOrientedSlab.BlockOrientedSlabDouble RED_TILES;
   public static BlockOrientedSlab.BlockOrientedSlabSlab GRAY_TILES_SLAB;
   public static BlockOrientedSlab.BlockOrientedSlabSlab GREEN_TILES_SLAB;
   public static BlockOrientedSlab.BlockOrientedSlabSlab RED_TILES_SLAB;
   public static BlockBars WOODEN_BARS_DARK;
   public static BlockMillSapling SAPLING_CHERRY;
   public static BlockFruitLeaves CHERRY_LEAVES;
   public static BlockMillSapling SAPLING_SAKURA;
   public static BlockFruitLeaves SAKURA_LEAVES;
   public static BlockState BS_WET_BRICK;
   public static BlockState BS_MUD_BRICK;
   /** Kept as thin delegates so existing references resolve; tabs are owned by MillRegistry now. */
   public static CreativeModeTab tabMillenaire;
   public static CreativeModeTab tabMillenaireContentCreator;

   public static void initBlockStates() {
      BS_WET_BRICK = WET_BRICK.defaultBlockState().setValue(BlockWetBrick.PROGRESS, BlockWetBrick.EnumType.WETBRICK0);
      BS_MUD_BRICK = STONE_DECORATION.defaultBlockState().setValue(BlockDecorativeStone.VARIANT, BlockDecorativeStone.EnumType.MUDBRICK);
   }

   public static void register() {
      WOOD_DECORATION = MillRegistry.block("wood_deco", new BlockDecorativeWood("wood_deco"));
      STONE_DECORATION = MillRegistry.block("stone_deco", new BlockDecorativeStone("stone_deco"));
      EARTH_DECORATION = MillRegistry.block("earth_deco", new BlockDecorativeEarth("earth_deco"));
      WALL_MUD_BRICK = MillRegistry.block("wall_mud_brick", new BlockMillWall("wall_mud_brick", STONE_DECORATION));
      PAINTED_BRICK_MAP.put("painted_brick", new HashMap<>());
      PAINTED_BRICK_MAP.put("painted_brick_decorated", new HashMap<>());
      PAINTED_BRICK_MAP.put("stairs_painted_brick", new HashMap<>());
      PAINTED_BRICK_MAP.put("slab_painted_brick", new HashMap<>());
      PAINTED_BRICK_MAP.put("wall_painted_brick", new HashMap<>());

      for (DyeColor colour : DyeColor.values()) {
         // Each painted block self-assigns its registry id (Properties.setId) from baseName + "_" + colour;
         // register under the matching name so the Fabric registry id == the block's own id.
         String brickColour = BlockPaintedBricks.getColorName(colour);
         Block paintedBrick = MillRegistry.block("painted_brick_" + brickColour, new BlockPaintedBricks("painted_brick", colour));
         PAINTED_BRICK_MAP.get("painted_brick").put(colour, paintedBrick);
         Block paintedBrickDecorated = MillRegistry.block(
            "painted_brick_decorated_" + brickColour, new BlockPaintedBricks("painted_brick_decorated", colour)
         );
         PAINTED_BRICK_MAP.get("painted_brick_decorated").put(colour, paintedBrickDecorated);
         Block paintedBrickStairs = MillRegistry.block(
            "stairs_painted_brick_" + colour.getName(), new BlockPaintedStairs("stairs_painted_brick", paintedBrick.defaultBlockState(), colour)
         );
         PAINTED_BRICK_MAP.get("stairs_painted_brick").put(colour, paintedBrickStairs);
         Block paintedBrickSlabs = MillRegistry.block(
            "slab_painted_brick_" + colour.getName(), new BlockPaintedSlab("slab_painted_brick", paintedBrick, colour)
         );
         PAINTED_BRICK_MAP.get("slab_painted_brick").put(colour, paintedBrickSlabs);
         Block paintedBrickWall = MillRegistry.block("wall_painted_brick_" + colour.getName(), new BlockPaintedWall("wall_painted_brick", paintedBrick, colour));
         PAINTED_BRICK_MAP.get("wall_painted_brick").put(colour, paintedBrickWall);
      }

      PAINTED_BRICK_WHITE = (BlockPaintedBricks) PAINTED_BRICK_MAP.get("painted_brick").get(DyeColor.WHITE);
      PAINTED_BRICK_DECORATED_WHITE = (BlockPaintedBricks) PAINTED_BRICK_MAP.get("painted_brick_decorated").get(DyeColor.WHITE);
      EXTENDED_MUD_BRICK = MillRegistry.block("extended_mud_brick", new BlockExtendedMudBrick("extended_mud_brick"));
      SLAB_WOOD_DECORATION = MillRegistry.block("slab_wood_deco", new BlockSlabWood("slab_wood_deco"));
      SLAB_STONE_DECORATION = MillRegistry.block("slab_stone_deco", new BlockSlabStone("slab_stone_deco"));
      STAIRS_COOKEDBRICK = MillRegistry.block(
         "stairs_cookedbrick",
         new BlockMillStairs(
            "stairs_cookedbrick", STONE_DECORATION.defaultBlockState().setValue(BlockDecorativeStone.VARIANT, BlockDecorativeStone.EnumType.COOKEDBRICK)
         )
      );
      STAIRS_MUDBRICK = MillRegistry.block(
         "stairs_mudbrick",
         new BlockMillStairs(
            "stairs_mudbrick", STONE_DECORATION.defaultBlockState().setValue(BlockDecorativeStone.VARIANT, BlockDecorativeStone.EnumType.MUDBRICK)
         )
      );
      STAIRS_TIMBERFRAME = MillRegistry.block(
         "stairs_timberframe",
         new BlockMillStairs(
            "stairs_timberframe", WOOD_DECORATION.defaultBlockState().setValue(BlockDecorativeWood.VARIANT, BlockDecorativeWood.EnumType.TIMBERFRAMEPLAIN)
         )
      );
      STAIRS_THATCH = MillRegistry.block(
         "stairs_thatch",
         new BlockMillStairs("stairs_thatch", WOOD_DECORATION.defaultBlockState().setValue(BlockDecorativeWood.VARIANT, BlockDecorativeWood.EnumType.THATCH))
      );
      SANDSTONE_CARVED = MillRegistry.block("sandstone_carved", new BlockMillSandstone("sandstone_carved"));
      SANDSTONE_RED_CARVED = MillRegistry.block("sandstone_red_carved", new BlockMillSandstone("sandstone_red_carved"));
      SANDSTONE_OCHRE_CARVED = MillRegistry.block("sandstone_ochre_carved", new BlockMillSandstone("sandstone_ochre_carved"));
      STAIRS_SANDSTONE_CARVED = MillRegistry.block("stairs_sandstone_carved", new BlockMillStairs("stairs_sandstone_carved", SANDSTONE_CARVED.defaultBlockState()));
      STAIRS_SANDSTONE_RED_CARVED = MillRegistry.block(
         "stairs_sandstone_red_carved", new BlockMillStairs("stairs_sandstone_red_carved", SANDSTONE_RED_CARVED.defaultBlockState())
      );
      STAIRS_SANDSTONE_OCHRE_CARVED = MillRegistry.block(
         "stairs_sandstone_ochre_carved", new BlockMillStairs("stairs_sandstone_ochre_carved", SANDSTONE_OCHRE_CARVED.defaultBlockState())
      );
      SLAB_SANDSTONE_CARVED = MillRegistry.block("slab_sandstone_carved", new BlockMillSlab("slab_sandstone_carved", SANDSTONE_CARVED));
      SLAB_SANDSTONE_RED_CARVED = MillRegistry.block("slab_sandstone_red_carved", new BlockMillSlab("slab_sandstone_red_carved", SANDSTONE_RED_CARVED));
      SLAB_SANDSTONE_OCHRE_CARVED = MillRegistry.block("slab_sandstone_ochre_carved", new BlockMillSlab("slab_sandstone_ochre_carved", SANDSTONE_OCHRE_CARVED));
      WALL_SANDSTONE_CARVED = MillRegistry.block("wall_sandstone_carved", new BlockMillWall("wall_sandstone_carved", SANDSTONE_CARVED));
      WALL_SANDSTONE_RED_CARVED = MillRegistry.block("wall_sandstone_red_carved", new BlockMillWall("wall_sandstone_red_carved", SANDSTONE_RED_CARVED));
      WALL_SANDSTONE_OCHRE_CARVED = MillRegistry.block("wall_sandstone_ochre_carved", new BlockMillWall("wall_sandstone_ochre_carved", SANDSTONE_OCHRE_CARVED));
      STAINED_GLASS = MillRegistry.block("stained_glass", new BlockMillStainedGlass("stained_glass"));
      ROSETTE = MillRegistry.block("rosette", new BlockRosette("rosette", SoundType.GLASS));
      WET_BRICK = MillRegistry.block("wet_brick", new BlockWetBrick("wet_brick"));
      SILK_WORM = MillRegistry.block("silk_worm", new BlockSilkWorm("silk_worm"));
      SNAIL_SOIL = MillRegistry.block("snail_soil", new BlockSnailSoil("snail_soil"));
      PATHDIRT = MillRegistry.block("pathdirt", new BlockPath("pathdirt", MapColor.DIRT, SoundType.GRAVEL));
      PATHDIRT_SLAB = MillRegistry.block("pathdirt_slab", new BlockPathSlab("pathdirt", MapColor.DIRT, SoundType.GRAVEL));
      PATHGRAVEL = MillRegistry.block("pathgravel", new BlockPath("pathgravel", MapColor.COLOR_GRAY, SoundType.GRAVEL));
      PATHGRAVEL_SLAB = MillRegistry.block("pathgravel_slab", new BlockPathSlab("pathgravel", MapColor.COLOR_GRAY, SoundType.GRAVEL));
      PATHSLABS = MillRegistry.block("pathslabs", new BlockPath("pathslabs", MapColor.STONE, SoundType.STONE));
      PATHSLABS_SLAB = MillRegistry.block("pathslabs_slab", new BlockPathSlab("pathslabs", MapColor.STONE, SoundType.STONE));
      PATHSANDSTONE = MillRegistry.block("pathsandstone", new BlockPath("pathsandstone", MapColor.SAND, SoundType.STONE));
      PATHSANDSTONE_SLAB = MillRegistry.block("pathsandstone_slab", new BlockPathSlab("pathsandstone", MapColor.SAND, SoundType.STONE));
      PATHGRAVELSLABS = MillRegistry.block("pathgravelslabs", new BlockPath("pathgravelslabs", MapColor.COLOR_GRAY, SoundType.STONE));
      PATHGRAVELSLABS_SLAB = MillRegistry.block("pathgravelslabs_slab", new BlockPathSlab("pathgravelslabs", MapColor.COLOR_GRAY, SoundType.STONE));
      PATHOCHRESLABS = MillRegistry.block("pathochretiles", new BlockPath("pathochretiles", MapColor.TERRACOTTA_BROWN, SoundType.STONE));
      PATHOCHRESLABS_SLAB = MillRegistry.block("pathochretiles_slab", new BlockPathSlab("pathochretiles", MapColor.TERRACOTTA_BROWN, SoundType.STONE));
      PATHSNOW = MillRegistry.block("pathsnow", new BlockPath("pathsnow", MapColor.SNOW, SoundType.SNOW));
      PATHSNOW_SLAB = MillRegistry.block("pathsnow_slab", new BlockPathSlab("pathsnow", MapColor.SNOW, SoundType.SNOW));
      LOCKED_CHEST = MillRegistry.block("locked_chest", new BlockLockedChest("locked_chest"));
      PANEL = MillRegistry.block("panel", new BlockPanel("panel"));
      CROP_RICE = MillRegistry.block("crop_rice", new BlockMillCrops("crop_rice", true, false, MillRegistry.id("rice")));
      CROP_TURMERIC = MillRegistry.block("crop_turmeric", new BlockMillCrops("crop_turmeric", false, false, MillRegistry.id("turmeric")));
      CROP_MAIZE = MillRegistry.block("crop_maize", new BlockMillCrops("crop_maize", false, true, MillRegistry.id("maize")));
      CROP_VINE = MillRegistry.block("crop_vine", new BlockGrapeVine("crop_vine", false, false, MillRegistry.id("grapes")));
      CROP_COTTON = MillRegistry.block("crop_cotton", new BlockMillCrops("crop_cotton", true, false, MillRegistry.id("cotton")));
      PAPER_WALL = MillRegistry.block("paper_wall", new BlockMillPane("paper_wall", SoundType.WOOL));
      WOODEN_BARS = MillRegistry.block("wooden_bars", new BlockBars("wooden_bars"));
      WOODEN_BARS_INDIAN = MillRegistry.block("wooden_bars_indian", new BlockBars("wooden_bars_indian"));
      WOODEN_BARS_ROSETTE = MillRegistry.block("wooden_bars_rosette", new BlockRosetteBars("wooden_bars_rosette"));
      BYZANTINE_TILES = MillRegistry.block("byzantine_tiles", new BlockOrientedSlab.BlockOrientedSlabDouble("byzantine_tiles"));
      BYZANTINE_TILES_SLAB = MillRegistry.block("byzantine_tiles_slab", new BlockOrientedSlab.BlockOrientedSlabSlab("byzantine_tiles_slab"));
      BYZANTINE_STONE_TILES = MillRegistry.block("byzantine_stone_tiles", new BlockOrientedSlabDoubleDecorated("byzantine_stone_tiles"));
      BYZANTINE_SANDSTONE_TILES = MillRegistry.block("byzantine_sandstone_tiles", new BlockOrientedSlabDoubleDecorated("byzantine_sandstone_tiles"));
      BYZANTINE_STONE_ORNAMENT = MillRegistry.block("byzantine_stone_ornament", new BlockMillSandstoneDecorated("byzantine_stone_ornament"));
      BYZANTINE_SANDSTONE_ORNAMENT = MillRegistry.block("byzantine_sandstone_ornament", new BlockMillSandstoneDecorated("byzantine_sandstone_ornament"));
      STAIRS_BYZ_TILES = MillRegistry.block("stairs_byzantine_tiles", new BlockMillStairs("stairs_byzantine_tiles", BYZANTINE_TILES.defaultBlockState()));
      ALCHEMIST_EXPLOSIVE = MillRegistry.block("alchemistexplosive", new BlockAlchemistExplosive("alchemistexplosive"));
      SOD = MillRegistry.block("sod", new BlockSod("sod"));
      ICE_BRICK = MillRegistry.block("icebrick", new BlockCustomIce("icebrick"));
      SNOW_BRICK = MillRegistry.block("snowbrick", new BlockCustomSnow("snowbrick"));
      SNOW_WALL = MillRegistry.block("snowwall", new BlockMillWall("snowwall", Blocks.SNOW_BLOCK));
      BED_STRAW = MillRegistry.block("bed_straw", new BlockMillBed("bed_straw", 4));
      BED_CHARPOY = MillRegistry.block("bed_charpoy", new BlockMillBed("bed_charpoy", 4));
      IMPORT_TABLE = MillRegistry.block("import_table", new BlockImportTable("import_table"));
      SAPLING_APPLETREE = MillRegistry.block("sapling_appletree", new BlockMillSapling("sapling_appletree", BlockMillSapling.EnumMillWoodType.APPLETREE));
      LEAVES_APPLETREE = MillRegistry.block(
         "leaves_appletree",
         new BlockFruitLeaves("leaves_appletree", BlockMillSapling.EnumMillWoodType.APPLETREE, MillRegistry.id("sapling_appletree"), MillRegistry.id("ciderapple"))
      );
      SAPLING_OLIVETREE = MillRegistry.block("sapling_olivetree", new BlockMillSapling("sapling_olivetree", BlockMillSapling.EnumMillWoodType.OLIVETREE));
      LEAVES_OLIVETREE = MillRegistry.block(
         "leaves_olivetree",
         new BlockFruitLeaves("leaves_olivetree", BlockMillSapling.EnumMillWoodType.OLIVETREE, MillRegistry.id("sapling_olivetree"), MillRegistry.id("olives"))
      );
      SAPLING_PISTACHIO = MillRegistry.block("sapling_pistachio", new BlockMillSapling("sapling_pistachio", BlockMillSapling.EnumMillWoodType.PISTACHIO));
      LEAVES_PISTACHIO = MillRegistry.block(
         "leaves_pistachio",
         new BlockFruitLeaves("leaves_pistachio", BlockMillSapling.EnumMillWoodType.PISTACHIO, MillRegistry.id("sapling_pistachio"), MillRegistry.id("pistachios"))
      );
      SAPLING_CHERRY = MillRegistry.block("sapling_cherry", new BlockMillSapling("sapling_cherry", BlockMillSapling.EnumMillWoodType.CHERRY));
      CHERRY_LEAVES = MillRegistry.block(
         "cherry_leaves",
         new BlockFruitLeaves("cherry_leaves", BlockMillSapling.EnumMillWoodType.CHERRY, MillRegistry.id("sapling_cherry"), MillRegistry.id("cherries"))
      );
      SAPLING_SAKURA = MillRegistry.block("sapling_sakura", new BlockMillSapling("sapling_sakura", BlockMillSapling.EnumMillWoodType.SAKURA));
      SAKURA_LEAVES = MillRegistry.block(
         "sakura_leaves",
         new BlockFruitLeaves("sakura_leaves", BlockMillSapling.EnumMillWoodType.SAKURA, MillRegistry.id("sapling_sakura"), MillRegistry.id("cherry_blossom"))
      );
      MARKER_BLOCK = MillRegistry.block("markerblock", new MockBlockMarker("markerblock"));
      MAIN_CHEST = MillRegistry.block("mainchest", new MockBlockMainChest("mainchest"));
      ANIMAL_SPAWN = MillRegistry.block("animalspawn", new MockBlockAnimalSpawn("animalspawn"));
      SOURCE = MillRegistry.block("source", new MockBlockSource("source"));
      FREE_BLOCK = MillRegistry.block("freeblock", new MockBlockFree("freeblock"));
      TREE_SPAWN = MillRegistry.block("treespawn", new MockBlockTreeSpawn("treespawn"));
      SOIL_BLOCK = MillRegistry.block("soil", new MockBlockSoil("soil"));
      DECOR_BLOCK = MillRegistry.block("decorblock", new MockBlockDecor("decorblock"));
      VILLAGE_BANNER_WALL = MillRegistry.block("villagebannerwall", new MockBlockBannerHanging("villagebannerwall", ItemMockBanner.BANNER_VILLAGE));
      VILLAGE_BANNER_STANDING = MillRegistry.block("villagebannerstanding", new MockBlockBannerStanding("villagebannerstanding", ItemMockBanner.BANNER_VILLAGE));
      CULTURE_BANNER_WALL = MillRegistry.block("culturebannerwall", new MockBlockBannerHanging("culturebannerwall", ItemMockBanner.BANNER_CULTURE));
      CULTURE_BANNER_STANDING = MillRegistry.block("culturebannerstanding", new MockBlockBannerStanding("culturebannerstanding", ItemMockBanner.BANNER_CULTURE));
      FIRE_PIT = MillRegistry.block("fire_pit", new BlockFirePit("fire_pit"));
      GRAY_TILES = MillRegistry.block("gray_tiles", new BlockOrientedSlab.BlockOrientedSlabDouble("gray_tiles"));
      GREEN_TILES = MillRegistry.block("green_tiles", new BlockOrientedSlab.BlockOrientedSlabDouble("green_tiles"));
      RED_TILES = MillRegistry.block("red_tiles", new BlockOrientedSlab.BlockOrientedSlabDouble("red_tiles"));
      GRAY_TILES_SLAB = MillRegistry.block("gray_tiles_slab", new BlockOrientedSlab.BlockOrientedSlabSlab("gray_tiles_slab"));
      GREEN_TILES_SLAB = MillRegistry.block("green_tiles_slab", new BlockOrientedSlab.BlockOrientedSlabSlab("green_tiles_slab"));
      RED_TILES_SLAB = MillRegistry.block("red_tiles_slab", new BlockOrientedSlab.BlockOrientedSlabSlab("red_tiles_slab"));
      STAIRS_GRAY_TILES = MillRegistry.block("stairs_gray_tiles", new BlockMillStairs("stairs_gray_tiles", GRAY_TILES.defaultBlockState()));
      STAIRS_GREEN_TILES = MillRegistry.block("stairs_green_tiles", new BlockMillStairs("stairs_green_tiles", GREEN_TILES.defaultBlockState()));
      STAIRS_RED_TILES = MillRegistry.block("stairs_red_tiles", new BlockMillStairs("stairs_red_tiles", RED_TILES.defaultBlockState()));
      WOODEN_BARS_DARK = MillRegistry.block("wooden_bars_dark", new BlockBars("wooden_bars_dark"));
      INUIT_CARVING = MillRegistry.block("inuitcarving", new BlockMillStatue("inuitcarving", SoundType.SNOW));
   }

   /** A plain vanilla {@link BlockItem} whose id matches the block's registry id. */
   private static BlockItem blockItem(Block block) {
      return new BlockItem(block, new net.minecraft.world.item.Item.Properties().setId(MillRegistry.itemKeyFor(block)));
   }

   private static void blockItem(String name, Block block) {
      MillRegistry.item(name, blockItem(block));
   }

   public static void registerBlockItems() {
      MillRegistry.item("wood_deco", new ItemBlockMeta(WOOD_DECORATION));
      MillRegistry.item("stone_deco", new ItemBlockMeta(STONE_DECORATION));
      MillRegistry.item("earth_deco", new ItemBlockMeta(EARTH_DECORATION));
      blockItem("wall_mud_brick", WALL_MUD_BRICK);

      for (Map<DyeColor, Block> blockMap : PAINTED_BRICK_MAP.values()) {
         for (Block block : blockMap.values()) {
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (block instanceof BlockPaintedSlab blockSlab) {
               MillRegistry.item(name, new ItemHalfSlab(blockSlab));
            } else {
               blockItem(name, block);
            }
         }
      }

      MillRegistry.item("extended_mud_brick", new ItemBlockMeta(EXTENDED_MUD_BRICK));
      MillRegistry.item("slab_wood_deco", new ItemSlabMeta(SLAB_WOOD_DECORATION, SLAB_WOOD_DECORATION));
      MillRegistry.item("slab_stone_deco", new ItemSlabMeta(SLAB_STONE_DECORATION, SLAB_STONE_DECORATION));
      MillRegistry.item("stained_glass", new ItemBlockMeta(STAINED_GLASS));
      blockItem("rosette", ROSETTE);
      blockItem("stairs_mudbrick", STAIRS_MUDBRICK);
      blockItem("stairs_timberframe", STAIRS_TIMBERFRAME);
      blockItem("stairs_thatch", STAIRS_THATCH);
      blockItem("stairs_byzantine_tiles", STAIRS_BYZ_TILES);
      MillRegistry.item("wet_brick", new ItemBlockMeta(WET_BRICK));
      MillRegistry.item("silk_worm", new ItemBlockMeta(SILK_WORM));
      MillRegistry.item("snail_soil", new ItemBlockMeta(SNAIL_SOIL));
      blockItem("sandstone_carved", SANDSTONE_CARVED);
      blockItem("sandstone_red_carved", SANDSTONE_RED_CARVED);
      blockItem("sandstone_ochre_carved", SANDSTONE_OCHRE_CARVED);
      blockItem("stairs_sandstone_carved", STAIRS_SANDSTONE_CARVED);
      blockItem("stairs_sandstone_red_carved", STAIRS_SANDSTONE_RED_CARVED);
      blockItem("stairs_sandstone_ochre_carved", STAIRS_SANDSTONE_OCHRE_CARVED);
      MillRegistry.item("slab_sandstone_carved", new ItemHalfSlab(SLAB_SANDSTONE_CARVED));
      MillRegistry.item("slab_sandstone_red_carved", new ItemHalfSlab(SLAB_SANDSTONE_RED_CARVED));
      MillRegistry.item("slab_sandstone_ochre_carved", new ItemHalfSlab(SLAB_SANDSTONE_OCHRE_CARVED));
      blockItem("wall_sandstone_carved", WALL_SANDSTONE_CARVED);
      blockItem("wall_sandstone_red_carved", WALL_SANDSTONE_RED_CARVED);
      blockItem("wall_sandstone_ochre_carved", WALL_SANDSTONE_OCHRE_CARVED);
      blockItem("pathdirt", PATHDIRT);
      blockItem("pathgravel", PATHGRAVEL);
      blockItem("pathsandstone", PATHSANDSTONE);
      blockItem("pathslabs", PATHSLABS);
      blockItem("pathgravelslabs", PATHGRAVELSLABS);
      blockItem("pathochretiles", PATHOCHRESLABS);
      blockItem("pathsnow", PATHSNOW);
      MillRegistry.item("pathdirt_slab", new ItemPathSlab(PATHDIRT_SLAB, PATHDIRT));
      MillRegistry.item("pathgravel_slab", new ItemPathSlab(PATHGRAVEL_SLAB, PATHGRAVEL));
      MillRegistry.item("pathsandstone_slab", new ItemPathSlab(PATHSANDSTONE_SLAB, PATHSANDSTONE));
      MillRegistry.item("pathslabs_slab", new ItemPathSlab(PATHSLABS_SLAB, PATHSLABS));
      MillRegistry.item("pathgravelslabs_slab", new ItemPathSlab(PATHGRAVELSLABS_SLAB, PATHGRAVELSLABS));
      MillRegistry.item("pathochretiles_slab", new ItemPathSlab(PATHOCHRESLABS_SLAB, PATHOCHRESLABS));
      MillRegistry.item("pathsnow_slab", new ItemPathSlab(PATHSNOW_SLAB, PATHSNOW));
      blockItem("paper_wall", PAPER_WALL);
      blockItem("wooden_bars", WOODEN_BARS);
      blockItem("wooden_bars_indian", WOODEN_BARS_INDIAN);
      blockItem("wooden_bars_rosette", WOODEN_BARS_ROSETTE);
      blockItem("locked_chest", LOCKED_CHEST);
      blockItem("byzantine_stone_tiles", BYZANTINE_STONE_TILES);
      blockItem("byzantine_sandstone_tiles", BYZANTINE_SANDSTONE_TILES);
      blockItem("byzantine_stone_ornament", BYZANTINE_STONE_ORNAMENT);
      blockItem("byzantine_sandstone_ornament", BYZANTINE_SANDSTONE_ORNAMENT);
      blockItem("byzantine_tiles", BYZANTINE_TILES);
      MillRegistry.item("byzantine_tiles_slab", new ItemSlabMeta(BYZANTINE_TILES_SLAB, BYZANTINE_TILES));
      blockItem("alchemistexplosive", ALCHEMIST_EXPLOSIVE);
      MillRegistry.item("sod", new ItemBlockMeta(SOD));
      blockItem("icebrick", ICE_BRICK);
      blockItem("snowbrick", SNOW_BRICK);
      blockItem("inuitcarving", INUIT_CARVING);
      blockItem("snowwall", SNOW_WALL);
      MillRegistry.item("bed_straw", new ItemMillBed(BED_STRAW));
      MillRegistry.item("bed_charpoy", new ItemMillBed(BED_CHARPOY));
      blockItem("import_table", IMPORT_TABLE);
      MillRegistry.item("sapling_appletree", new ItemMillSapling(SAPLING_APPLETREE, "sapling_appletree"));
      blockItem("leaves_appletree", LEAVES_APPLETREE);
      MillRegistry.item("sapling_olivetree", new ItemMillSapling(SAPLING_OLIVETREE, "sapling_olivetree"));
      blockItem("leaves_olivetree", LEAVES_OLIVETREE);
      MillRegistry.item("sapling_pistachio", new ItemMillSapling(SAPLING_PISTACHIO, "sapling_pistachio"));
      blockItem("leaves_pistachio", LEAVES_PISTACHIO);
      MillRegistry.item("markerblock", new ItemBlockMeta(MARKER_BLOCK));
      blockItem("mainchest", MAIN_CHEST);
      MillRegistry.item("animalspawn", new ItemBlockMeta(ANIMAL_SPAWN));
      MillRegistry.item("source", new ItemBlockMeta(SOURCE));
      MillRegistry.item("freeblock", new ItemBlockMeta(FREE_BLOCK));
      MillRegistry.item("treespawn", new ItemBlockMeta(TREE_SPAWN));
      MillRegistry.item("soil", new ItemBlockMeta(SOIL_BLOCK));
      MillRegistry.item("decorblock", new ItemBlockMeta(DECOR_BLOCK));
      MillRegistry.item(
         "villagebanner",
         new ItemMockBanner(
            VILLAGE_BANNER_STANDING, VILLAGE_BANNER_WALL, ItemMockBanner.BANNER_COLOURS[ItemMockBanner.BANNER_VILLAGE], ItemMockBanner.BANNER_VILLAGE
         )
      );
      MillRegistry.item(
         "culturebanner",
         new ItemMockBanner(
            CULTURE_BANNER_STANDING, CULTURE_BANNER_WALL, ItemMockBanner.BANNER_COLOURS[ItemMockBanner.BANNER_CULTURE], ItemMockBanner.BANNER_CULTURE
         )
      );
      // The four mock-banner blocks extend vanilla Banner/WallBannerBlock, whose data-component
      // initialiser requires a registered item with the block's id (else "Missing element item/…").
      // They are placed by buildings (not crafted), so a plain BlockItem under the same id suffices.
      blockItem("villagebannerstanding", VILLAGE_BANNER_STANDING);
      blockItem("villagebannerwall", VILLAGE_BANNER_WALL);
      blockItem("culturebannerstanding", CULTURE_BANNER_STANDING);
      blockItem("culturebannerwall", CULTURE_BANNER_WALL);
      blockItem("fire_pit", FIRE_PIT);
      blockItem("stairs_gray_tiles", STAIRS_GRAY_TILES);
      blockItem("stairs_green_tiles", STAIRS_GREEN_TILES);
      blockItem("stairs_red_tiles", STAIRS_RED_TILES);
      blockItem("gray_tiles", GRAY_TILES);
      blockItem("green_tiles", GREEN_TILES);
      blockItem("red_tiles", RED_TILES);
      MillRegistry.item("gray_tiles_slab", new ItemSlabMeta(GRAY_TILES_SLAB, GRAY_TILES));
      MillRegistry.item("green_tiles_slab", new ItemSlabMeta(GREEN_TILES_SLAB, GREEN_TILES));
      MillRegistry.item("red_tiles_slab", new ItemSlabMeta(RED_TILES_SLAB, RED_TILES));
      blockItem("wooden_bars_dark", WOODEN_BARS_DARK);
      MillRegistry.item("sapling_cherry", new ItemMillSapling(SAPLING_CHERRY, "sapling_cherry"));
      blockItem("cherry_leaves", CHERRY_LEAVES);
      MillRegistry.item("sapling_sakura", new ItemMillSapling(SAPLING_SAKURA, "sapling_sakura"));
      blockItem("sakura_leaves", SAKURA_LEAVES);
   }

   public static class MillBlockNames {
      private static final String WOOD_DECO = "wood_deco";
      private static final String STONE_DECO = "stone_deco";
      private static final String EARTH_DECO = "earth_deco";
      public static final String PAINTED_BRICK = "painted_brick";
      public static final String PAINTED_BRICK_DECORATED = "painted_brick_decorated";
      public static final String STAIRS_PAINTED_BRICK = "stairs_painted_brick";
      public static final String SLAB_PAINTED_BRICK = "slab_painted_brick";
      public static final String WALL_PAINTED_BRICK = "wall_painted_brick";
      private static final String EXTENDED_MUD_BRICK = "extended_mud_brick";
      private static final String SLAB_WOOD_DECO = "slab_wood_deco";
      private static final String SLAB_STONE_DECO = "slab_stone_deco";
      private static final String WALL_MUD_BRICK = "wall_mud_brick";
      private static final String WET_BRICK = "wet_brick";
      private static final String SILK_WORM = "silk_worm";
      private static final String SNAIL_SOIL = "snail_soil";
      private static final String PATHDIRT = "pathdirt";
      private static final String PATHGRAVEL = "pathgravel";
      private static final String PATHSLABS = "pathslabs";
      private static final String PATHSANDSTONE = "pathsandstone";
      private static final String PATHGRAVELSLABS = "pathgravelslabs";
      private static final String PATHOCHRETILES = "pathochretiles";
      private static final String PATHSNOW = "pathsnow";
      private static final String LOCKED_CHEST = "locked_chest";
      private static final String PANEL = "panel";
      private static final String CROP_RICE = "crop_rice";
      private static final String CROP_TURMERIC = "crop_turmeric";
      private static final String CROP_MAIZE = "crop_maize";
      private static final String CROP_VINE = "crop_vine";
      private static final String CROP_COTTON = "crop_cotton";
      private static final String STAINED_GLASS = "stained_glass";
      private static final String ROSETTE = "rosette";
      private static final String PAPER_WALL = "paper_wall";
      private static final String WOODEN_BARS = "wooden_bars";
      private static final String WOODEN_BARS_INDIAN = "wooden_bars_indian";
      private static final String WOODEN_BARS_ROSETTE = "wooden_bars_rosette";
      private static final String BYZ_TILES = "byzantine_tiles";
      private static final String BYZ_TILES_SLAB = "byzantine_tiles_slab";
      private static final String BYZ_STONE_TILES = "byzantine_stone_tiles";
      private static final String BYZ_SANDSTONE_TILES = "byzantine_sandstone_tiles";
      private static final String BYZ_STONE_ORNAMENT = "byzantine_stone_ornament";
      private static final String BYZ_SANDSTONE_ORNAMENT = "byzantine_sandstone_ornament";
      private static final String ALCHEMIST_EXPLOSIVE = "alchemistexplosive";
      private static final String MOCK_BLOCK_MARKER = "markerblock";
      private static final String MAIN_CHEST = "mainchest";
      private static final String ANIMAL_SPAWN = "animalspawn";
      private static final String SOURCE = "source";
      private static final String FREE_BLOCK = "freeblock";
      private static final String TREE_SPAWN = "treespawn";
      private static final String SOIL_BLOCK = "soil";
      private static final String DECOR_BLOCK = "decorblock";
      private static final String VILLAGE_BANNER_WALL = "villagebannerwall";
      private static final String VILLAGE_BANNER_STANDING = "villagebannerstanding";
      private static final String CULTURE_BANNER_WALL = "culturebannerwall";
      private static final String CULTURE_BANNER_STANDING = "culturebannerstanding";
      private static final String MOCK_BANNER = "mockbanner";
      private static final String IMPORT_TABLE = "import_table";
      private static final String STAIRS_TIMBERFRAME = "stairs_timberframe";
      private static final String STAIRS_MUDBRICK = "stairs_mudbrick";
      private static final String STAIRS_COOKEDBRICK = "stairs_cookedbrick";
      private static final String STAIRS_THATCH = "stairs_thatch";
      private static final String STAIRS_BYZ_TILES = "stairs_byzantine_tiles";
      public static final String SANDSTONE_CARVED = "sandstone_carved";
      public static final String SANDSTONE_RED_CARVED = "sandstone_red_carved";
      public static final String SANDSTONE_OCHRE_CARVED = "sandstone_ochre_carved";
      private static final String STAIRS_SANDSTONE_CARVED = "stairs_sandstone_carved";
      private static final String STAIRS_SANDSTONE_RED_CARVED = "stairs_sandstone_red_carved";
      private static final String STAIRS_SANDSTONE_OCHRE_CARVED = "stairs_sandstone_ochre_carved";
      private static final String SLAB_SANDSTONE_CARVED = "slab_sandstone_carved";
      private static final String SLAB_SANDSTONE_RED_CARVED = "slab_sandstone_red_carved";
      private static final String SLAB_SANDSTONE_OCHRE_CARVED = "slab_sandstone_ochre_carved";
      private static final String WALL_SANDSTONE_CARVED = "wall_sandstone_carved";
      private static final String WALL_SANDSTONE_RED_CARVED = "wall_sandstone_red_carved";
      private static final String WALL_SANDSTONE_OCHRE_CARVED = "wall_sandstone_ochre_carved";
      private static final String SOD_BLOCK = "sod";
      private static final String ICE_BRICK_BLOCK = "icebrick";
      private static final String SNOW_BRICK_BLOCK = "snowbrick";
      private static final String INUIT_CARVING = "inuitcarving";
      private static final String SNOW_WALL = "snowwall";
      private static final String BED_STRAW = "bed_straw";
      private static final String BED_CHARPOY = "bed_charpoy";
      private static final String FIRE_PIT = "fire_pit";
      private static final String SAPLING_APPLETREE = "sapling_appletree";
      private static final String LEAVES_APPLETREE = "leaves_appletree";
      private static final String SAPLING_OLIVETREE = "sapling_olivetree";
      private static final String LEAVES_OLIVETREE = "leaves_olivetree";
      private static final String SAPLING_PISTACHIO = "sapling_pistachio";
      private static final String LEAVES_PISTACHIO = "leaves_pistachio";
      private static final String GREEN_TILES = "green_tiles";
      private static final String GRAY_TILES = "gray_tiles";
      private static final String RED_TILES = "red_tiles";
      private static final String GREEN_TILES_SLAB = "green_tiles_slab";
      private static final String GRAY_TILES_SLAB = "gray_tiles_slab";
      private static final String RED_TILES_SLAB = "red_tiles_slab";
      private static final String STAIRS_GRAY_TILES = "stairs_gray_tiles";
      private static final String STAIRS_GREEN_TILES = "stairs_green_tiles";
      private static final String STAIRS_RED_TILES = "stairs_red_tiles";
      private static final String WOODEN_BARS_DARK = "wooden_bars_dark";
      private static final String SAPLING_CHERRY = "sapling_cherry";
      private static final String CHERRY_LEAVES = "cherry_leaves";
      private static final String SAPLING_SAKURA = "sapling_sakura";
      private static final String SAKURA_LEAVES = "sakura_leaves";
   }
}

