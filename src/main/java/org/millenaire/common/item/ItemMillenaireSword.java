package org.millenaire.common.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.Holder;

import org.millenaire.common.forge.MillRegistry;

/**
 * Sword: 26.2 has no SwordItem — a plain Item with the sword tool properties. The 1.12
 * auto-knockback / onCreated / onItemUseFirst / onLeftClickEntity hooks are Forge-specific
 * and the enchantment system is now component-based; applyEnchantments writes the ENCHANTMENTS
 * data component via ItemStack.enchant (implemented below).
 */
public class ItemMillenaireSword extends Item implements InvItem.IItemInitialEnchantmens {
   private final boolean knockback;
   private final int enchantability;

   public ItemMillenaireSword(String itemName, ToolMaterial material, int enchantability, boolean knockback) {
      super(new Item.Properties()
         .sword(material, 3.0F, -2.4F)
         .enchantable(enchantability >= 0 ? enchantability : 14)
         .setId(MillRegistry.itemKey(itemName)));
      this.knockback = knockback;
      this.enchantability = enchantability;
   }

   @Override
   public void applyEnchantments(ItemStack stack, RegistryAccess registryAccess) {
      // 1.12 applied Knockback II when this is a knockback sword and it isn't already enchanted with it.
      // 26.2: resolve the dynamic-registry Holder<Enchantment> from the RegistryAccess threaded in by the
      // call site, then ItemStack.enchant(Holder, level) (writes the ENCHANTMENTS data component).
      if (this.knockback) {
         Holder<Enchantment> kb = registryAccess.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK);
         if (EnchantmentHelper.getItemEnchantmentLevel(kb, stack) == 0) {
            stack.enchant(kb, 2);
         }
      }
   }

   @Override
   public void hurtEnemy(ItemStack itemStack, net.minecraft.world.entity.LivingEntity mob, net.minecraft.world.entity.LivingEntity attacker) {
      super.hurtEnemy(itemStack, mob, attacker);
      // 1.12 granted MP_WEAPON when a player struck another PLAYER with the Millénaire sword (onLeftClick
      // Entity). The port dropped that hook so the advancement was unobtainable; restore it here.
      if (mob instanceof net.minecraft.world.entity.player.Player
         && attacker instanceof net.minecraft.world.entity.player.Player p) {
         org.millenaire.common.advancements.MillAdvancements.MP_WEAPON.grant(p);
      }
   }
}
