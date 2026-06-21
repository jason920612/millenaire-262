package org.millenaire.common.item;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.forge.MillRegistry;

public class MillItems {
   public static ItemSummoningWand SUMMONING_WAND;
   public static ItemNegationWand NEGATION_WAND;
   public static ItemPurse PURSE;
   public static ItemMill DENIER;
   public static ItemMill DENIER_ARGENT;
   public static ItemMill DENIER_OR;
   public static ItemFoodMultiple CIDER_APPLE;
   public static ItemFoodMultiple CIDER;
   public static ItemFoodMultiple CALVA;
   public static ItemFoodMultiple BOUDIN;
   public static ItemFoodMultiple TRIPES;
   public static ItemMillenaireHoe NORMAN_HOE;
   public static ItemMillenaireAxe NORMAN_AXE;
   public static ItemMillenairePickaxe NORMAN_PICKAXE;
   public static ItemMillenaireShovel NORMAN_SHOVEL;
   public static ItemMillenaireSword NORMAN_SWORD;
   public static ItemMillenaireArmour NORMAN_HELMET;
   public static ItemMillenaireArmour NORMAN_CHESTPLATE;
   public static ItemMillenaireArmour NORMAN_LEGGINGS;
   public static ItemMillenaireArmour NORMAN_BOOTS;
   public static ItemWallDecoration TAPESTRY;
   public static ItemMillenaireHoe MAYAN_HOE;
   public static ItemMillenaireAxe MAYAN_AXE;
   public static ItemMillenairePickaxe MAYAN_PICKAXE;
   public static ItemMillenaireShovel MAYAN_SHOVEL;
   public static ItemMillenaireSword MAYAN_MACE;
   public static ItemMillenaireHoe BYZANTINE_HOE;
   public static ItemMillenaireAxe BYZANTINE_AXE;
   public static ItemMillenairePickaxe BYZANTINE_PICKAXE;
   public static ItemMillenaireShovel BYZANTINE_SHOVEL;
   public static ItemMillenaireSword BYZANTINE_MACE;
   public static ItemMillenaireBow YUMI_BOW;
   public static ItemMillenaireSword TACHI_SWORD;
   public static ItemFoodMultiple OLIVES;
   public static ItemFoodMultiple OLIVEOIL;
   public static ItemMillenaireArmour BYZANTINE_HELMET;
   public static ItemMillenaireArmour BYZANTINE_CHESTPLATE;
   public static ItemMillenaireArmour BYZANTINE_LEGGINGS;
   public static ItemMillenaireArmour BYZANTINE_BOOTS;
   public static ItemMillenaireArmour JAPANESE_RED_HELMET;
   public static ItemMillenaireArmour JAPANESE_RED_CHESTPLATE;
   public static ItemMillenaireArmour JAPANESE_RED_LEGGINGS;
   public static ItemMillenaireArmour JAPANESE_RED_BOOTS;
   public static ItemMillenaireArmour JAPANESE_BLUE_HELMET;
   public static ItemMillenaireArmour JAPANESE_BLUE_CHESTPLATE;
   public static ItemMillenaireArmour JAPANESE_BLUE_LEGGINGS;
   public static ItemMillenaireArmour JAPANESE_BLUE_BOOTS;
   public static ItemMillenaireArmour JAPANESE_GUARD_HELMET;
   public static ItemMillenaireArmour JAPANESE_GUARD_CHESTPLATE;
   public static ItemMillenaireArmour JAPANESE_GUARD_LEGGINGS;
   public static ItemMillenaireArmour JAPANESE_GUARD_BOOTS;
   public static ItemParchment PARCHMENT_NORMAN_VILLAGERS;
   public static ItemParchment PARCHMENT_NORMAN_BUILDINGS;
   public static ItemParchment PARCHMENT_NORMAN_ITEMS;
   public static ItemParchment PARCHMENT_NORMAN_COMPLETE;
   public static ItemParchment PARCHMENT_INDIAN_VILLAGERS;
   public static ItemParchment PARCHMENT_INDIAN_BUILDINGS;
   public static ItemParchment PARCHMENT_INDIAN_ITEMS;
   public static ItemParchment PARCHMENT_INDIAN_COMPLETE;
   public static ItemParchment PARCHMENT_JAPANESE_VILLAGERS;
   public static ItemParchment PARCHMENT_JAPANESE_BUILDINGS;
   public static ItemParchment PARCHMENT_JAPANESE_ITEMS;
   public static ItemParchment PARCHMENT_JAPANESE_COMPLETE;
   public static ItemParchment PARCHMENT_MAYAN_VILLAGERS;
   public static ItemParchment PARCHMENT_MAYAN_BUILDINGS;
   public static ItemParchment PARCHMENT_MAYAN_ITEMS;
   public static ItemParchment PARCHMENT_MAYAN_COMPLETE;
   public static ItemMill AMULET_VISHNU;
   public static ItemMill AMULET_ALCHEMIST;
   public static ItemMill AMULET_YDDRASIL;
   public static ItemMill AMULET_SKOLL_HATI;
   public static ItemParchment PARCHMENT_VILLAGE_SCROLL;
   public static ItemMillSeeds RICE;
   public static ItemMillSeeds TURMERIC;
   public static ItemMillSeeds MAIZE;
   public static ItemMillSeeds GRAPES;
   public static ItemFoodMultiple VEG_CURRY;
   public static ItemFoodMultiple CHICKEN_CURRY;
   public static ItemMill BRICK_MOULD;
   public static ItemFoodMultiple RASGULLA;
   public static ItemWallDecoration INDIAN_STATUE;
   public static ItemWallDecoration MAYAN_STATUE;
   public static ItemFoodMultiple WAH;
   public static ItemFoodMultiple BLANCHE;
   public static ItemFoodMultiple SIKILPAH;
   public static ItemFoodMultiple MASA;
   public static ItemParchment PARCHMENT_SADHU;
   public static ItemMill UNKNOWN_POWDER;
   public static ItemFoodMultiple UDON;
   public static ItemMill OBSIDIAN_FLAKE;
   public static ItemMill SILK;
   public static ItemWallDecoration BYZANTINE_ICON_SMALL;
   public static ItemWallDecoration BYZANTINE_ICON_MEDIUM;
   public static ItemWallDecoration BYZANTINE_ICON_LARGE;
   public static ItemClothes BYZANTINE_CLOTH_WOOL;
   public static ItemClothes BYZANTINE_CLOTH_SILK;
   public static ItemFoodMultiple WINE_FANCY;
   public static ItemFoodMultiple WINE_BASIC;
   public static ItemFoodMultiple FETA;
   public static ItemFoodMultiple SOUVLAKI;
   public static ItemFoodMultiple SAKE;
   public static ItemFoodMultiple CACAUHAA;
   public static ItemMayanQuestCrown MAYAN_QUEST_CROWN;
   public static ItemFoodMultiple IKAYAKI;
   public static ItemFoodMultiple BEARMEAT_RAW;
   public static ItemFoodMultiple BEARMEAT_COOKED;
   public static ItemFoodMultiple WOLFMEAT_RAW;
   public static ItemFoodMultiple WOLFMEAT_COOKED;
   public static ItemFoodMultiple SEAFOOD_RAW;
   public static ItemFoodMultiple SEAFOOD_COOKED;
   public static ItemFoodMultiple INUITBEARSTEW;
   public static ItemFoodMultiple INUITMEATYSTEW;
   public static ItemFoodMultiple INUITPOTATOSTEW;
   public static ItemMillenaireArmour FUR_HELMET;
   public static ItemMillenaireArmour FUR_CHESTPLATE;
   public static ItemMillenaireArmour FUR_LEGGINGS;
   public static ItemMillenaireArmour FUR_BOOTS;
   public static ItemMillenaireBow INUIT_BOW;
   public static ItemMill ULU;
   public static ItemMillenaireSword INUIT_TRIDENT;
   public static ItemMill TANNEDHIDE;
   public static ItemWallDecoration HIDEHANGING;
   public static ItemMockBanner VILLAGEBANNER;
   public static ItemMockBanner CULTUREBANNER;
   public static ItemBannerPattern BANNERPATTERN;
   public static ItemFoodMultiple AYRAN;
   public static ItemFoodMultiple YOGURT;
   public static ItemFoodMultiple PIDE;
   public static ItemFoodMultiple LOKUM;
   public static ItemFoodMultiple HELVA;
   public static ItemFoodMultiple PISTACHIOS;
   public static ItemMillSeeds COTTON;
   public static ItemMillenaireSword SELJUK_SCIMITAR;
   public static ItemMillenaireBow SElJUK_BOW;
   public static ItemMillenaireArmour SELJUK_TURBAN;
   public static ItemMillenaireArmour SELJUK_HELMET;
   public static ItemMillenaireArmour SELJUK_CHESTPLATE;
   public static ItemMillenaireArmour SELJUK_LEGGINGS;
   public static ItemMillenaireArmour SELJUK_BOOTS;
   public static ItemWallDecoration WALLCARPETSMALL;
   public static ItemWallDecoration WALLCARPETMEDIUM;
   public static ItemWallDecoration WALLCARPETLARGE;
   public static ItemClothes SELJUK_CLOTH_WOOL;
   public static ItemClothes SELJUK_CLOTH_COTTON;
   public static Map<DyeColor, ItemPaintBucket> PAINT_BUCKETS = new HashMap<>();
   public static ItemFoodMultiple CHERRIES;
   public static ItemFoodMultiple CHERRY_BLOSSOM;

   public static void register() {
      SUMMONING_WAND = MillRegistry.item("summoningwand", new ItemSummoningWand("summoningwand"));
      NEGATION_WAND = MillRegistry.item("negationwand", new ItemNegationWand("negationwand"));
      DENIER = MillRegistry.item("denier", new ItemMill("denier"));
      DENIER_ARGENT = MillRegistry.item("denierargent", new ItemMill("denierargent"));
      DENIER_OR = MillRegistry.item("denieror", new ItemMill("denieror"));
      PURSE = MillRegistry.item("purse", new ItemPurse("purse"));
      RICE = MillRegistry.item("rice", new ItemMillSeeds(MillBlocks.CROP_RICE, "rice"));
      TURMERIC = MillRegistry.item("turmeric", new ItemMillSeeds(MillBlocks.CROP_TURMERIC, "turmeric"));
      MAIZE = MillRegistry.item("maize", new ItemMillSeeds(MillBlocks.CROP_MAIZE, "maize"));
      GRAPES = MillRegistry.item("grapes", new ItemMillSeeds(MillBlocks.CROP_VINE, "grapes"));
      COTTON = MillRegistry.item("cotton", new ItemMillSeeds(MillBlocks.CROP_COTTON, "cotton"));
      NORMAN_PICKAXE = MillRegistry.item("normanpickaxe", new ItemMillenairePickaxe("normanpickaxe", MillToolMaterials.NORMAN));
      NORMAN_AXE = MillRegistry.item("normanaxe", new ItemMillenaireAxe("normanaxe", MillToolMaterials.NORMAN, 8.0F, -3.0F));
      NORMAN_SHOVEL = MillRegistry.item("normanshovel", new ItemMillenaireShovel("normanshovel", MillToolMaterials.NORMAN));
      NORMAN_HOE = MillRegistry.item("normanhoe", new ItemMillenaireHoe("normanhoe", MillToolMaterials.NORMAN));
      MAYAN_PICKAXE = MillRegistry.item("mayanpickaxe", new ItemMillenairePickaxe("mayanpickaxe", MillToolMaterials.OBSIDIAN));
      MAYAN_AXE = MillRegistry.item("mayanaxe", new ItemMillenaireAxe("mayanaxe", MillToolMaterials.OBSIDIAN, 8.0F, -3.0F));
      MAYAN_SHOVEL = MillRegistry.item("mayanshovel", new ItemMillenaireShovel("mayanshovel", MillToolMaterials.OBSIDIAN));
      MAYAN_HOE = MillRegistry.item("mayanhoe", new ItemMillenaireHoe("mayanhoe", MillToolMaterials.OBSIDIAN));
      BYZANTINE_PICKAXE = MillRegistry.item("byzantinepickaxe", new ItemMillenairePickaxe("byzantinepickaxe", MillToolMaterials.BYZANTINE));
      BYZANTINE_AXE = MillRegistry.item("byzantineaxe", new ItemMillenaireAxe("byzantineaxe", MillToolMaterials.BYZANTINE, 8.0F, -3.0F));
      BYZANTINE_SHOVEL = MillRegistry.item("byzantineshovel", new ItemMillenaireShovel("byzantineshovel", MillToolMaterials.BYZANTINE));
      BYZANTINE_HOE = MillRegistry.item("byzantinehoe", new ItemMillenaireHoe("byzantinehoe", MillToolMaterials.BYZANTINE));
      NORMAN_SWORD = MillRegistry.item("normanbroadsword", new ItemMillenaireSword("normanbroadsword", MillToolMaterials.NORMAN, -1, false));
      MAYAN_MACE = MillRegistry.item("mayanmace", new ItemMillenaireSword("mayanmace", ToolMaterial.IRON, -1, false));
      TACHI_SWORD = MillRegistry.item("tachisword", new ItemMillenaireSword("tachisword", MillToolMaterials.OBSIDIAN, -1, false));
      BYZANTINE_MACE = MillRegistry.item("byzantinemace", new ItemMillenaireSword("byzantinemace", ToolMaterial.IRON, -1, true));
      INUIT_TRIDENT = MillRegistry.item("inuittrident", new ItemMillenaireSword("inuittrident", ToolMaterial.IRON, 20, false));
      SELJUK_SCIMITAR = MillRegistry.item("seljukscimitar", new ItemMillenaireSword("seljukscimitar", MillToolMaterials.BETTER_STEEL, -1, false));
      YUMI_BOW = MillRegistry.item("yumibow", new ItemMillenaireBow("yumibow", 2.0F, 0.5F, 1));
      INUIT_BOW = MillRegistry.item("inuitbow", new ItemMillenaireBow("inuitbow", 1.0F, 0.0F, 20));
      SElJUK_BOW = MillRegistry.item("seljukbow", new ItemMillenaireBow("seljukbow", 1.5F, 1.5F, 20));
      NORMAN_HELMET = MillRegistry.item("normanhelmet", new ItemMillenaireArmour("normanhelmet", MillArmorMaterials.NORMAN, EquipmentSlot.HEAD));
      NORMAN_CHESTPLATE = MillRegistry.item("normanplate", new ItemMillenaireArmour("normanplate", MillArmorMaterials.NORMAN, EquipmentSlot.CHEST));
      NORMAN_LEGGINGS = MillRegistry.item("normanlegs", new ItemMillenaireArmour("normanlegs", MillArmorMaterials.NORMAN, EquipmentSlot.LEGS));
      NORMAN_BOOTS = MillRegistry.item("normanboots", new ItemMillenaireArmour("normanboots", MillArmorMaterials.NORMAN, EquipmentSlot.FEET));
      JAPANESE_BLUE_HELMET = MillRegistry.item("japanesebluehelmet", new ItemMillenaireArmour("japanesebluehelmet", MillArmorMaterials.JAPANESE_BLUE, EquipmentSlot.HEAD));
      JAPANESE_BLUE_CHESTPLATE = MillRegistry.item("japaneseblueplate", new ItemMillenaireArmour("japaneseblueplate", MillArmorMaterials.JAPANESE_BLUE, EquipmentSlot.CHEST));
      JAPANESE_BLUE_LEGGINGS = MillRegistry.item("japanesebluelegs", new ItemMillenaireArmour("japanesebluelegs", MillArmorMaterials.JAPANESE_BLUE, EquipmentSlot.LEGS));
      JAPANESE_BLUE_BOOTS = MillRegistry.item("japaneseblueboots", new ItemMillenaireArmour("japaneseblueboots", MillArmorMaterials.JAPANESE_BLUE, EquipmentSlot.FEET));
      JAPANESE_RED_HELMET = MillRegistry.item("japaneseredhelmet", new ItemMillenaireArmour("japaneseredhelmet", MillArmorMaterials.JAPANESE_RED, EquipmentSlot.HEAD));
      JAPANESE_RED_CHESTPLATE = MillRegistry.item("japaneseredplate", new ItemMillenaireArmour("japaneseredplate", MillArmorMaterials.JAPANESE_RED, EquipmentSlot.CHEST));
      JAPANESE_RED_LEGGINGS = MillRegistry.item("japaneseredlegs", new ItemMillenaireArmour("japaneseredlegs", MillArmorMaterials.JAPANESE_RED, EquipmentSlot.LEGS));
      JAPANESE_RED_BOOTS = MillRegistry.item("japaneseredboots", new ItemMillenaireArmour("japaneseredboots", MillArmorMaterials.JAPANESE_RED, EquipmentSlot.FEET));
      JAPANESE_GUARD_HELMET = MillRegistry.item("japaneseguardhelmet", new ItemMillenaireArmour("japaneseguardhelmet", MillArmorMaterials.JAPANESE_GUARD, EquipmentSlot.HEAD));
      JAPANESE_GUARD_CHESTPLATE = MillRegistry.item("japaneseguardplate", new ItemMillenaireArmour("japaneseguardplate", MillArmorMaterials.JAPANESE_GUARD, EquipmentSlot.CHEST));
      JAPANESE_GUARD_LEGGINGS = MillRegistry.item("japaneseguardlegs", new ItemMillenaireArmour("japaneseguardlegs", MillArmorMaterials.JAPANESE_GUARD, EquipmentSlot.LEGS));
      JAPANESE_GUARD_BOOTS = MillRegistry.item("japaneseguardboots", new ItemMillenaireArmour("japaneseguardboots", MillArmorMaterials.JAPANESE_GUARD, EquipmentSlot.FEET));
      BYZANTINE_HELMET = MillRegistry.item("byzantinehelmet", new ItemMillenaireArmour("byzantinehelmet", MillArmorMaterials.BYZANTINE, EquipmentSlot.HEAD));
      BYZANTINE_CHESTPLATE = MillRegistry.item("byzantineplate", new ItemMillenaireArmour("byzantineplate", MillArmorMaterials.BYZANTINE, EquipmentSlot.CHEST));
      BYZANTINE_LEGGINGS = MillRegistry.item("byzantinelegs", new ItemMillenaireArmour("byzantinelegs", MillArmorMaterials.BYZANTINE, EquipmentSlot.LEGS));
      BYZANTINE_BOOTS = MillRegistry.item("byzantineboots", new ItemMillenaireArmour("byzantineboots", MillArmorMaterials.BYZANTINE, EquipmentSlot.FEET));
      FUR_HELMET = MillRegistry.item("furhelmet", new ItemMillenaireArmour("furhelmet", MillArmorMaterials.FUR, EquipmentSlot.HEAD));
      FUR_CHESTPLATE = MillRegistry.item("furplate", new ItemMillenaireArmour("furplate", MillArmorMaterials.FUR, EquipmentSlot.CHEST));
      FUR_LEGGINGS = MillRegistry.item("furlegs", new ItemMillenaireArmour("furlegs", MillArmorMaterials.FUR, EquipmentSlot.LEGS));
      FUR_BOOTS = MillRegistry.item("furboots", new ItemMillenaireArmour("furboots", MillArmorMaterials.FUR, EquipmentSlot.FEET));
      SELJUK_HELMET = MillRegistry.item("seljukhelmet", new ItemMillenaireArmour("seljukhelmet", MillArmorMaterials.SELJUK, EquipmentSlot.HEAD));
      SELJUK_TURBAN = MillRegistry.item("seljukturban", new ItemMillenaireArmour("seljukturban", MillArmorMaterials.SELJUK_WOOL, EquipmentSlot.HEAD));
      SELJUK_CHESTPLATE = MillRegistry.item("seljukplate", new ItemMillenaireArmour("seljukplate", MillArmorMaterials.SELJUK, EquipmentSlot.CHEST));
      SELJUK_LEGGINGS = MillRegistry.item("seljuklegs", new ItemMillenaireArmour("seljuklegs", MillArmorMaterials.SELJUK, EquipmentSlot.LEGS));
      SELJUK_BOOTS = MillRegistry.item("seljukboots", new ItemMillenaireArmour("seljukboots", MillArmorMaterials.SELJUK, EquipmentSlot.FEET));
      CIDER_APPLE = MillRegistry.item("ciderapple", new ItemFoodMultiple("ciderapple", 0, 0, 1, 0.05F, false, 0).setMaxStackSize(64));
      OLIVES = MillRegistry.item("olives", new ItemFoodMultiple("olives", 0, 0, 1, 0.05F, false, 0).setMaxStackSize(64));
      OLIVEOIL = MillRegistry.item("oliveoil", new ItemFoodMultiple("oliveoil", 0, 0, 0, 0.0F, true, 0, true).setClearEffects(true).setMaxStackSize(16));
      CIDER = MillRegistry.item("cider", new ItemFoodMultiple("cider", 4, 15, 0, 0.0F, true, 5, true).setAlwaysEdible().setMaxDamage(384));
      CALVA = MillRegistry.item("calva", new ItemFoodMultiple("calva", 8, 30, 0, 0.0F, true, 10, true).setAlwaysEdible().setMaxDamage(1024));
      BOUDIN = MillRegistry.item("boudin", new ItemFoodMultiple("boudin", 0, 0, 8, 1.0F, false, 0, true).setAlwaysEdible().setMaxDamage(384));
      TRIPES = MillRegistry.item("tripes", new ItemFoodMultiple("tripes", 0, 0, 10, 1.0F, false, 0, true).setAlwaysEdible().setMaxDamage(512));
      VEG_CURRY = MillRegistry.item("vegcurry", new ItemFoodMultiple("vegcurry", 0, 0, 6, 0.6F, false, 0).setMaxDamage(384));
      CHICKEN_CURRY = MillRegistry.item("chickencurry", new ItemFoodMultiple("chickencurry", 0, 0, 8, 0.8F, false, 0).setMaxDamage(512));
      RASGULLA = MillRegistry.item(
         "rasgulla",
         new ItemFoodMultiple("rasgulla", 2, 30, 0, 0.0F, false, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.SPEED, 9600, 1), 1.0F)
            .setAlwaysEdible()
            .setMaxStackSize(64)
      );
      MASA = MillRegistry.item("masa", new ItemFoodMultiple("masa", 0, 0, 6, 0.6F, false, 0).setMaxDamage(256));
      WAH = MillRegistry.item("wah", new ItemFoodMultiple("wah", 0, 0, 10, 1.0F, false, 0).setMaxDamage(384));
      BLANCHE = MillRegistry.item(
         "balche",
         new ItemFoodMultiple("balche", 6, 20, 0, 0.0F, true, 7, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 9600, 1), 1.0F)
            .setAlwaysEdible()
            .setMaxDamage(512)
      );
      SIKILPAH = MillRegistry.item("sikilpah", new ItemFoodMultiple("sikilpah", 0, 0, 7, 0.7F, false, 0).setMaxDamage(448));
      CACAUHAA = MillRegistry.item(
         "cacauhaa",
         new ItemFoodMultiple("cacauhaa", 6, 30, 0, 0.0F, true, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 9600, 1), 1.0F)
            .setAlwaysEdible()
            .setMaxDamage(384)
      );
      UDON = MillRegistry.item("udon", new ItemFoodMultiple("udon", 0, 0, 8, 0.8F, false, 0).setMaxDamage(384));
      SAKE = MillRegistry.item(
         "sake",
         new ItemFoodMultiple("sake", 8, 30, 0, 0.0F, true, 10, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 9600, 1), 1.0F)
            .setAlwaysEdible()
            .setMaxDamage(512)
      );
      IKAYAKI = MillRegistry.item(
         "ikayaki",
         new ItemFoodMultiple("ikayaki", 0, 0, 10, 1.0F, false, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 9600, 2), 1.0F)
            .setAlwaysEdible()
            .setMaxDamage(512)
      );
      FETA = MillRegistry.item("feta", new ItemFoodMultiple("feta", 2, 15, 0, 0.0F, false, 0).setMaxStackSize(64));
      SOUVLAKI = MillRegistry.item(
         "souvlaki",
         new ItemFoodMultiple("souvlaki", 0, 0, 10, 1.0F, false, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 2), 1.0F)
            .setAlwaysEdible()
            .setMaxDamage(512)
      );
      WINE_BASIC = MillRegistry.item("winebasic", new ItemFoodMultiple("winebasic", 3, 15, 0, 0.0F, true, 5, true).setAlwaysEdible().setMaxDamage(384));
      WINE_FANCY = MillRegistry.item(
         "winefancy",
         new ItemFoodMultiple("winefancy", 8, 30, 0, 0.0F, true, 5, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.RESISTANCE, 9600, 2), 1.0F)
            .setAlwaysEdible()
            .setMaxDamage(1024)
      );
      BEARMEAT_RAW = MillRegistry.item(
         "bearmeat_raw",
         new ItemFoodMultiple("bearmeat_raw", 0, 0, 4, 0.5F, false, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.STRENGTH, 4800, 1), 1.0F)
            .setAlwaysEdible()
            .setMaxStackSize(64)
      );
      BEARMEAT_COOKED = MillRegistry.item(
         "bearmeat_cooked",
         new ItemFoodMultiple("bearmeat_cooked", 0, 0, 10, 1.0F, false, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.STRENGTH, 9600, 2), 1.0F)
            .setAlwaysEdible()
            .setMaxStackSize(64)
      );
      WOLFMEAT_RAW = MillRegistry.item("wolfmeat_raw", new ItemFoodMultiple("wolfmeat_raw", 0, 0, 3, 0.3F, false, 0).setMaxStackSize(64));
      WOLFMEAT_COOKED = MillRegistry.item(
         "wolfmeat_cooked",
         new ItemFoodMultiple("wolfmeat_cooked", 0, 0, 5, 0.6F, false, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.STRENGTH, 1200, 1), 1.0F)
            .setAlwaysEdible()
            .setMaxStackSize(64)
      );
      SEAFOOD_RAW = MillRegistry.item("seafood_raw", new ItemFoodMultiple("seafood_raw", 0, 0, 2, 0.2F, false, 0, true).setAlwaysEdible());
      SEAFOOD_COOKED = MillRegistry.item(
         "seafood_cooked",
         new ItemFoodMultiple("seafood_cooked", 0, 0, 2, 0.25F, false, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200, 1), 1.0F)
            .setAlwaysEdible()
      );
      INUITPOTATOSTEW = MillRegistry.item("inuitpotatostew", new ItemFoodMultiple("inuitpotatostew", 0, 0, 6, 0.6F, false, 0).setMaxDamage(384));
      INUITMEATYSTEW = MillRegistry.item("inuitmeatystew", new ItemFoodMultiple("inuitmeatystew", 0, 0, 8, 0.8F, false, 0).setMaxDamage(512));
      INUITBEARSTEW = MillRegistry.item(
         "inuitbearstew",
         new ItemFoodMultiple("inuitbearstew", 0, 0, 8, 1.0F, false, 0, true)
            .setPotionEffect(new MobEffectInstance(MobEffects.STRENGTH, 9600, 3), 1.0F)
            .setAlwaysEdible()
            .setMaxDamage(512)
      );
      AYRAN = MillRegistry.item("ayran", new ItemFoodMultiple("ayran", 3, 15, 0, 0.2F, true, 2, true).setAlwaysEdible().setMaxDamage(6));
      YOGURT = MillRegistry.item("yogurt", new ItemFoodMultiple("yogurt", 0, 15, 0, 0.4F, false, 0).setMaxStackSize(64));
      PIDE = MillRegistry.item("pide", new ItemFoodMultiple("pide", 0, 0, 8, 1.0F, false, 0).setMaxDamage(8));
      LOKUM = MillRegistry.item(
         "lokum",
         new ItemFoodMultiple("lokum", 0, 0, 3, 0.0F, false, 0, true).setPotionEffect(new MobEffectInstance(MobEffects.SPEED, 2400, 1), 0.2F).setAlwaysEdible()
      );
      HELVA = MillRegistry.item(
         "helva",
         new ItemFoodMultiple("helva", 0, 0, 5, 0.0F, false, 0, true).setPotionEffect(new MobEffectInstance(MobEffects.RESISTANCE, 2400, 1), 0.2F).setAlwaysEdible()
      );
      PISTACHIOS = MillRegistry.item("pistachios", new ItemFoodMultiple("pistachios", 0, 0, 1, 0.1F, false, 0).setMaxStackSize(64));
      CHERRIES = MillRegistry.item("cherries", new ItemFoodMultiple("cherries", 0, 0, 1, 0.1F, false, 0).setMaxStackSize(64));
      CHERRY_BLOSSOM = MillRegistry.item("cherry_blossom", new ItemFoodMultiple("cherry_blossom", 0, 0, 1, 0.1F, false, 0).setMaxStackSize(64));
      TAPESTRY = MillRegistry.item("tapestry", new ItemWallDecoration("tapestry", 1));
      INDIAN_STATUE = MillRegistry.item("indianstatue", new ItemWallDecoration("indianstatue", 2));
      MAYAN_STATUE = MillRegistry.item("mayanstatue", new ItemWallDecoration("mayanstatue", 3));
      BYZANTINE_ICON_SMALL = MillRegistry.item("byzantineiconsmall", new ItemWallDecoration("byzantineiconsmall", 4));
      BYZANTINE_ICON_MEDIUM = MillRegistry.item("byzantineiconmedium", new ItemWallDecoration("byzantineiconmedium", 5));
      BYZANTINE_ICON_LARGE = MillRegistry.item("byzantineiconlarge", new ItemWallDecoration("byzantineiconlarge", 6));
      HIDEHANGING = MillRegistry.item("hidehanging", new ItemWallDecoration("hidehanging", 7));
      WALLCARPETSMALL = MillRegistry.item("wallcarpetsmall", new ItemWallDecoration("wallcarpetsmall", 8));
      WALLCARPETMEDIUM = MillRegistry.item("wallcarpetmedium", new ItemWallDecoration("wallcarpetmedium", 9));
      WALLCARPETLARGE = MillRegistry.item("wallcarpetlarge", new ItemWallDecoration("wallcarpetlarge", 10));

      for (DyeColor colour : DyeColor.values()) {
         ItemPaintBucket bucket = new ItemPaintBucket("paint_bucket", colour);
         MillRegistry.item("paint_bucket_" + org.millenaire.common.block.BlockPaintedBricks.getColorName(colour), bucket);
         PAINT_BUCKETS.put(colour, bucket);
      }

      PARCHMENT_NORMAN_VILLAGERS = MillRegistry.item("parchment_normanvillagers", new ItemParchment("parchment_normanvillagers", 1, true));
      PARCHMENT_NORMAN_BUILDINGS = MillRegistry.item("parchment_normanbuildings", new ItemParchment("parchment_normanbuildings", 2, true));
      PARCHMENT_NORMAN_ITEMS = MillRegistry.item("parchment_normanitems", new ItemParchment("parchment_normanitems", 3, true));
      PARCHMENT_NORMAN_COMPLETE = MillRegistry.item("parchment_normanfull", new ItemParchment("parchment_normanfull", new int[]{1, 2, 3}, true));
      PARCHMENT_INDIAN_VILLAGERS = MillRegistry.item("parchment_indianvillagers", new ItemParchment("parchment_indianvillagers", 5, true));
      PARCHMENT_INDIAN_BUILDINGS = MillRegistry.item("parchment_indianbuildings", new ItemParchment("parchment_indianbuildings", 6, true));
      PARCHMENT_INDIAN_ITEMS = MillRegistry.item("parchment_indianitems", new ItemParchment("parchment_indianitems", 7, true));
      PARCHMENT_INDIAN_COMPLETE = MillRegistry.item("parchment_indianfull", new ItemParchment("parchment_indianfull", new int[]{5, 6, 7}, true));
      PARCHMENT_MAYAN_VILLAGERS = MillRegistry.item("parchment_mayanvillagers", new ItemParchment("parchment_mayanvillagers", 9, true));
      PARCHMENT_MAYAN_BUILDINGS = MillRegistry.item("parchment_mayanbuildings", new ItemParchment("parchment_mayanbuildings", 10, true));
      PARCHMENT_MAYAN_ITEMS = MillRegistry.item("parchment_mayanitems", new ItemParchment("parchment_mayanitems", 11, true));
      PARCHMENT_MAYAN_COMPLETE = MillRegistry.item("parchment_mayanfull", new ItemParchment("parchment_mayanfull", new int[]{9, 10, 11}, true));
      PARCHMENT_JAPANESE_VILLAGERS = MillRegistry.item("parchment_japanesevillagers", new ItemParchment("parchment_japanesevillagers", 16, true));
      PARCHMENT_JAPANESE_BUILDINGS = MillRegistry.item("parchment_japanesebuildings", new ItemParchment("parchment_japanesebuildings", 17, true));
      PARCHMENT_JAPANESE_ITEMS = MillRegistry.item("parchment_japaneseitems", new ItemParchment("parchment_japaneseitems", 18, true));
      PARCHMENT_JAPANESE_COMPLETE = MillRegistry.item("parchment_japanesefull", new ItemParchment("parchment_japanesefull", new int[]{16, 17, 18}, true));
      PARCHMENT_VILLAGE_SCROLL = MillRegistry.item("parchment_villagescroll", new ItemParchment("parchment_villagescroll", 4, false));
      BRICK_MOULD = MillRegistry.item("brickmould", new ItemBrickMould("brickmould"));
      OBSIDIAN_FLAKE = MillRegistry.item("obsidianflake", new ItemMill("obsidianflake"));
      SILK = MillRegistry.item("silk", new ItemMill("silk"));
      BYZANTINE_CLOTH_WOOL = MillRegistry.item("clothes_byz_wool", new ItemClothes("clothes_byz_wool", 1));
      BYZANTINE_CLOTH_SILK = MillRegistry.item("clothes_byz_silk", new ItemClothes("clothes_byz_silk", 2));
      SELJUK_CLOTH_WOOL = MillRegistry.item("clothes_seljuk_wool", new ItemClothes("clothes_seljuk_wool", 1));
      SELJUK_CLOTH_COTTON = MillRegistry.item("clothes_seljuk_cotton", new ItemClothes("clothes_seljuk_cotton", 2));
      AMULET_VISHNU = MillRegistry.item("vishnu_amulet", new ItemAmuletVishnu("vishnu_amulet"));
      AMULET_ALCHEMIST = MillRegistry.item("alchemist_amulet", new ItemAmuletAlchemist("alchemist_amulet"));
      AMULET_YDDRASIL = MillRegistry.item("yggdrasil_amulet", new ItemAmuletYggdrasil("yggdrasil_amulet"));
      AMULET_SKOLL_HATI = MillRegistry.item("skoll_hati_amulet", new ItemAmuletSkollHati("skoll_hati_amulet"));
      ULU = MillRegistry.item("ulu", new ItemUlu("ulu"));
      TANNEDHIDE = MillRegistry.item("tannedhide", new ItemMill("tannedhide"));
      BANNERPATTERN = MillRegistry.item("bannerpattern", new ItemBannerPattern("bannerpattern"));
      PARCHMENT_SADHU = MillRegistry.item("parchment_sadhu", new ItemParchment("parchment_sadhu", 15, false));
      UNKNOWN_POWDER = MillRegistry.item("unknownpowder", new ItemMill("unknownpowder"));
      MAYAN_QUEST_CROWN = MillRegistry.item("mayanquestcrown", new ItemMayanQuestCrown("mayanquestcrown", EquipmentSlot.HEAD));
   }

   public static final class MillItemNames {
      private static final String DENIER = "denier";
      private static final String DENIEROR = "denieror";
      private static final String DENIERARGENT = "denierargent";
      private static final String CALVA = "calva";
      private static final String TRIPES = "tripes";
      private static final String BOUDIN = "boudin";
      private static final String NORMANPICKAXE = "normanpickaxe";
      private static final String NORMANAXE = "normanaxe";
      private static final String NORMANSHOVEL = "normanshovel";
      private static final String NORMANHOE = "normanhoe";
      private static final String NORMANBROADSWORD = "normanbroadsword";
      private static final String NORMANHELMET = "normanhelmet";
      private static final String NORMANPLATE = "normanplate";
      private static final String NORMANLEGS = "normanlegs";
      private static final String NORMANBOOTS = "normanboots";
      private static final String RICE = "rice";
      private static final String TURMERIC = "turmeric";
      private static final String VEGCURRY = "vegcurry";
      private static final String CHICKENCURRY = "chickencurry";
      private static final String BRICKMOULD = "brickmould";
      private static final String RASGULLA = "rasgulla";
      private static final String INDIANSTATUE = "indianstatue";
      private static final String CIDERAPPLE = "ciderapple";
      private static final String OLIVES = "olives";
      public static final String CIDERAPPLE_PUBLIC = "ciderapple";
      public static final String OLIVES_PUBLIC = "olives";
      private static final String OLIVEOIL = "oliveoil";
      private static final String CIDER = "cider";
      private static final String SUMMONINGWAND = "summoningwand";
      private static final String NEGATIONWAND = "negationwand";
      private static final String NORMANVILLAGERS = "parchment_normanvillagers";
      private static final String NORMANITEMS = "parchment_normanitems";
      private static final String NORMANBUILDINGS = "parchment_normanbuildings";
      private static final String NORMANFULL = "parchment_normanfull";
      private static final String TAPESTRY = "tapestry";
      private static final String VISHNU_AMULET = "vishnu_amulet";
      private static final String ALCHEMIST_AMULET = "alchemist_amulet";
      private static final String YGGDRASIL_AMULET = "yggdrasil_amulet";
      private static final String SKOLL_HATI_AMULET = "skoll_hati_amulet";
      private static final String VILLAGESCROLL = "parchment_villagescroll";
      private static final String PAINT_BUCKET = "paint_bucket";
      private static final String INDIANVILLAGERS = "parchment_indianvillagers";
      private static final String INDIANITEMS = "parchment_indianitems";
      private static final String INDIANBUILDINGS = "parchment_indianbuildings";
      private static final String INDIANFULL = "parchment_indianfull";
      private static final String WAH = "wah";
      private static final String BLANCHE = "balche";
      private static final String SIKILPAH = "sikilpah";
      private static final String MASA = "masa";
      private static final String MAIZE = "maize";
      private static final String MAYANSTATUE = "mayanstatue";
      private static final String MAYANVILLAGERS = "parchment_mayanvillagers";
      private static final String MAYANITEMS = "parchment_mayanitems";
      private static final String MAYANBUILDINGS = "parchment_mayanbuildings";
      private static final String MAYANFULL = "parchment_mayanfull";
      private static final String MAYANMACE = "mayanmace";
      private static final String MAYANPICKAXE = "mayanpickaxe";
      private static final String MAYANAXE = "mayanaxe";
      private static final String MAYANSHOVEL = "mayanshovel";
      private static final String MAYANHOE = "mayanhoe";
      private static final String OBSIDIANFLAKE = "obsidianflake";
      private static final String CACAUHAA = "cacauhaa";
      private static final String UDON = "udon";
      private static final String TACHISWORD = "tachisword";
      private static final String YUMIBOW = "yumibow";
      private static final String SAKE = "sake";
      private static final String IKAYAKI = "ikayaki";
      private static final String JAPANESEBLUELEGS = "japanesebluelegs";
      private static final String JAPANESEBLUEHELMET = "japanesebluehelmet";
      private static final String JAPANESEBLUEPLATE = "japaneseblueplate";
      private static final String JAPANESEBLUEBOOTS = "japaneseblueboots";
      private static final String JAPANESEREDLEGS = "japaneseredlegs";
      private static final String JAPANESEREDHELMET = "japaneseredhelmet";
      private static final String JAPANESEREDPLATE = "japaneseredplate";
      private static final String JAPANESEREDBOOTS = "japaneseredboots";
      private static final String JAPANESEGUARDLEGS = "japaneseguardlegs";
      private static final String JAPANESEGUARDHELMET = "japaneseguardhelmet";
      private static final String JAPANESEGUARDPLATE = "japaneseguardplate";
      private static final String JAPANESEGUARDBOOTS = "japaneseguardboots";
      private static final String JAPANESEVILLAGERS = "parchment_japanesevillagers";
      private static final String JAPANESEITEMS = "parchment_japaneseitems";
      private static final String JAPANESEBUILDINGS = "parchment_japanesebuildings";
      private static final String JAPANESEFULL = "parchment_japanesefull";
      private static final String PARCHMENTSADHU = "parchment_sadhu";
      private static final String UNKNOWNPOWDER = "unknownpowder";
      private static final String MAYANQUESTCROWN = "mayanquestcrown";
      private static final String GRAPES = "grapes";
      private static final String WINEFANCY = "winefancy";
      private static final String SILK = "silk";
      private static final String BYZANTINEICONSMALL = "byzantineiconsmall";
      private static final String BYZANTINEICONMEDIUM = "byzantineiconmedium";
      private static final String BYZANTINEICONLARGE = "byzantineiconlarge";
      private static final String BYZANTINEBOOTS = "byzantineboots";
      private static final String BYZANTINELEGS = "byzantinelegs";
      private static final String BYZANTINEPLATE = "byzantineplate";
      private static final String BYZANTINEHELMET = "byzantinehelmet";
      private static final String BYZANTINEMACE = "byzantinemace";
      private static final String BYZANTINEPICKAXE = "byzantinepickaxe";
      private static final String BYZANTINEAXE = "byzantineaxe";
      private static final String BYZANTINESHOVEL = "byzantineshovel";
      private static final String BYZANTINEHOE = "byzantinehoe";
      private static final String CLOTHES_BYZ_WOOL = "clothes_byz_wool";
      private static final String CLOTHES_BYZ_SILK = "clothes_byz_silk";
      private static final String FETA = "feta";
      private static final String WINEBASIC = "winebasic";
      private static final String SOUVLAKI = "souvlaki";
      private static final String PURSE = "purse";
      private static final String BEARMEAT_RAW = "bearmeat_raw";
      private static final String BEARMEAT_COOKED = "bearmeat_cooked";
      private static final String WOLFMEAT_RAW = "wolfmeat_raw";
      private static final String WOLFMEAT_COOKED = "wolfmeat_cooked";
      private static final String SEAFOOD_RAW = "seafood_raw";
      private static final String SEAFOOD_COOKED = "seafood_cooked";
      private static final String INUITBEARSTEW = "inuitbearstew";
      private static final String INUITMEATYSTEW = "inuitmeatystew";
      private static final String INUITPOTATOSTEW = "inuitpotatostew";
      private static final String INUIT_TRIDENT = "inuittrident";
      private static final String FURHELMET = "furhelmet";
      private static final String FURPLATE = "furplate";
      private static final String FURLEGS = "furlegs";
      private static final String FURBOOTS = "furboots";
      private static final String INUIT_BOW = "inuitbow";
      private static final String ULU = "ulu";
      private static final String TANNEDHIDE = "tannedhide";
      private static final String HIDEHANGING = "hidehanging";
      private static final String VILLAGEBANNER = "villagebanner";
      private static final String CULTUREBANNER = "culturebanner";
      public static final String VILLAGEBANNER_PUBLIC = "villagebanner";
      public static final String CULTUREBANNER_PUBLIC = "culturebanner";
      public static final String BANNERPATTERN = "bannerpattern";
      private static final String AYRAN = "ayran";
      private static final String YOGURT = "yogurt";
      private static final String PIDE = "pide";
      private static final String LOKUM = "lokum";
      private static final String HELVA = "helva";
      private static final String COTTON = "cotton";
      private static final String PISTACHIOS = "pistachios";
      public static final String PISTACHIOS_PUBLIC = "pistachios";
      private static final String SELJUK_SCIMITAR = "seljukscimitar";
      private static final String SELJUK_BOW = "seljukbow";
      private static final String SELJUK_BOOTS = "seljukboots";
      private static final String SELJUK_LEGGINGS = "seljuklegs";
      private static final String SELJUK_CHESTPLATE = "seljukplate";
      private static final String SELJUK_HELMET = "seljukhelmet";
      private static final String SELJUK_TURBAN = "seljukturban";
      private static final String WALLCARPETSMALL = "wallcarpetsmall";
      private static final String WALLCARPETMEDIUM = "wallcarpetmedium";
      private static final String WALLCARPETLARGE = "wallcarpetlarge";
      private static final String CLOTHES_SELJUK_WOOL = "clothes_seljuk_wool";
      private static final String CLOTHES_SELJUK_COTTON = "clothes_seljuk_cotton";
      private static final String CHERRIES = "cherries";
      public static final String CHERRIES_PUBLIC = "cherries";
      private static final String CHERRY_BLOSSOM = "cherry_blossom";
      public static final String CHERRY_BLOSSOM_PUBLIC = "cherry_blossom";
   }
}

