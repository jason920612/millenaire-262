package org.millenaire.common.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ToolMaterial;

/**
 * Millénaire tool materials. 1.12 created these with
 * {@code EnumHelper.addToolMaterial(name, harvestLevel, durability, efficiency, damage, enchantability)};
 * on 26.2 {@link ToolMaterial} is a record
 * {@code (incorrectBlocksForDrops, durability, speed, attackDamageBonus, enchantmentValue, repairItems)}.
 * Harvest level 2 ≈ iron tier, 3 ≈ diamond tier.
 */
public final class MillToolMaterials {
   private MillToolMaterials() {
   }

   public static final ToolMaterial NORMAN =
      new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 1561, 10.0F, 4.0F, 10, ItemTags.IRON_TOOL_MATERIALS);
   public static final ToolMaterial BETTER_STEEL =
      new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 1561, 5.0F, 3.0F, 10, ItemTags.IRON_TOOL_MATERIALS);
   public static final ToolMaterial BYZANTINE =
      new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 1561, 12.0F, 3.0F, 15, ItemTags.IRON_TOOL_MATERIALS);
   public static final ToolMaterial OBSIDIAN =
      new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561, 6.0F, 2.0F, 25, ItemTags.DIAMOND_TOOL_MATERIALS);
}
