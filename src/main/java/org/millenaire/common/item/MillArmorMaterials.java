package org.millenaire.common.item;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Millénaire armour materials. 1.12 created these with
 * {@code EnumHelper.addArmorMaterial(name, texture, durabilityMultiplier, int[]{boots,legs,chest,helm},
 * enchantability, equipSound, toughness)}; on 26.2 {@link ArmorMaterial} is a record
 * {@code (durability, Map<ArmorType,Integer> defense, enchantmentValue, Holder<SoundEvent> equipSound,
 * toughness, knockbackResistance, TagKey<Item> repairIngredient, ResourceKey<EquipmentAsset> assetId)}.
 *
 * <p>The 1.12 {@code durability} value is the per-slot multiplier (ArmorType.getDurability multiplies it),
 * so it maps directly to the record's {@code durability}. The {@code int[]} reductions map to
 * {@code defense} via {@link #defense} (the 5th "body" slot is unused for humanoid armour, set to 0).
 * {@code assetId} points at a custom millenaire equipment asset (the texture set) — the actual
 * equipment_asset JSON is a client resource; here we just allocate the registry key.
 */
public final class MillArmorMaterials {
   private MillArmorMaterials() {
   }

   private static ResourceKey<EquipmentAsset> asset(String name) {
      return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("millenaire", name));
   }

   /** @param boots/legs/chest/helm — same order as the 1.12 reduction int[]. */
   private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helm) {
      return Map.of(
         ArmorType.BOOTS, boots,
         ArmorType.LEGGINGS, legs,
         ArmorType.CHESTPLATE, chest,
         ArmorType.HELMET, helm,
         ArmorType.BODY, 0
      );
   }

   private static ArmorMaterial make(
      int durability, int boots, int legs, int chest, int helm, int enchantability, Holder<SoundEvent> equipSound, float toughness, String assetName
   ) {
      return new ArmorMaterial(
         durability,
         defense(boots, legs, chest, helm),
         enchantability,
         equipSound,
         toughness,
         0.0F,
         ItemTags.REPAIRS_IRON_ARMOR,
         asset(assetName)
      );
   }

   public static final ArmorMaterial NORMAN =
      make(66, 3, 8, 6, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, "norman");
   public static final ArmorMaterial JAPANESE_RED =
      make(33, 2, 6, 5, 2, 25, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, "japanese_red");
   public static final ArmorMaterial JAPANESE_BLUE =
      make(33, 2, 6, 5, 2, 25, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, "japanese_blue");
   public static final ArmorMaterial JAPANESE_GUARD =
      make(25, 2, 5, 4, 1, 25, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, "japanese_guard");
   public static final ArmorMaterial BYZANTINE =
      make(33, 3, 8, 6, 3, 20, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, "byzantine");
   public static final ArmorMaterial MAYAN_QUEST_CROWN =
      make(33, 3, 6, 8, 3, 10, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.0F, "mayan_quest_crown");
   public static final ArmorMaterial SELJUK =
      make(66, 3, 8, 6, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, "seljuk");
   public static final ArmorMaterial SELJUK_WOOL =
      make(7, 2, 5, 3, 1, 10, SoundEvents.ARMOR_EQUIP_GENERIC, 0.0F, "seljuk_wool");
   public static final ArmorMaterial FUR =
      make(7, 2, 5, 3, 1, 25, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, "furcoat");

   /** Maps the 1.12 {@code EquipmentSlot} the registration passed to the 26.2 {@link ArmorType}. */
   public static ArmorType armorType(net.minecraft.world.entity.EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> ArmorType.HELMET;
         case CHEST -> ArmorType.CHESTPLATE;
         case LEGS -> ArmorType.LEGGINGS;
         case FEET -> ArmorType.BOOTS;
         default -> ArmorType.BODY;
      };
   }
}
