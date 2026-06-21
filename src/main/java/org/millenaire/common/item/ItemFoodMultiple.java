package org.millenaire.common.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.millenaire.common.forge.MillRegistry;

/**
 * Multi-portion food/drink. 1.12 extended {@code ItemFood}; on 26.2 food is data-driven through
 * {@code Item.Properties().food(FoodProperties)}. The constructor builds the FoodProperties; the
 * custom multi-eat behaviour (heal, regeneration, drunkenness, the "64 damage per portion" multi-bite
 * stack) lives in {@link #finishUsingItem}.
 *
 * <p>NOTE for MillItems: 1.12 chained {@code .setMaxStackSize(n)} / {@code .setMaxDamage(n)} /
 * {@code .setAlwaysEdible()} / {@code .setClearEffects(b)} / {@code .setPotionEffect(PotionEffect, p)}
 * AFTER construction. Item.Properties is now sealed at construction, so:
 * <ul>
 *   <li>{@code setAlwaysEdible()} only flips a flag here — true always-edible is set via the
 *       {@code alwaysEdible} constructor arg / the {@code healthAmount>0} path (FoodProperties is
 *       built with {@code .alwaysEdible()} when appropriate);</li>
 *   <li>{@code setMaxStackSize} is a fluent no-op (the single-bite foods used the default 64);
 *       {@code setMaxDamage} stores the multi-portion durability on a field that {@link #finishUsingItem}
 *       applies to the eaten stack's MAX_DAMAGE component, since Properties is sealed at construction;</li>
 *   <li>{@code setPotionEffect} now takes a {@link MobEffectInstance} instead of the removed
 *       {@code PotionEffect}.</li>
 * </ul>
 */
public class ItemFoodMultiple extends Item {
   public static final int DAMAGE_PER_PORTION = 64;
   private final int healthAmount;
   private final boolean drink;
   private final int regenerationDuration;
   private final int drunkDuration;
   private MobEffectInstance potionId = null;
   private float potionEffectProbability = 0.0F;
   private boolean clearEffects = false;
   /** 1.12 maxDamage = number-of-portions * DAMAGE_PER_PORTION; 0 means a single-bite (stackable) food. */
   private int multiBiteMaxDamage = 0;

   private static FoodProperties buildFood(int healthAmount, int foodAmount, float saturation, boolean alwaysEdible) {
      FoodProperties.Builder builder = new FoodProperties.Builder().nutrition(foodAmount).saturationModifier(saturation);
      // 1.12 made a food always-edible (func_77848_i) ONLY when healthAmount>0 OR the call chain ran
      // setClearEffects/setPotionEffect/func_77848_i. Item.Properties is sealed at construction on 26.2, so
      // the post-construction always-edible signals (setClearEffects/setPotionEffect/setAlwaysEdible) must be
      // pre-computed by the caller (MillItems) and passed in here as `alwaysEdible`. The ~13 plain foods that
      // did none of these (ciderapple, olives, vegcurry, …) were NOT always-edible in 1.12, so do NOT set it.
      if (alwaysEdible || healthAmount > 0) {
         builder.alwaysEdible();
      }
      return builder.build();
   }

   public ItemFoodMultiple(String foodName, int healthAmount, int regenerationDuration, int foodAmount, float saturation, boolean drink, int drunkDuration) {
      this(foodName, healthAmount, regenerationDuration, foodAmount, saturation, drink, drunkDuration, false);
   }

   public ItemFoodMultiple(String foodName, int healthAmount, int regenerationDuration, int foodAmount, float saturation, boolean drink, int drunkDuration, boolean alwaysEdible) {
      // 1.12 ItemFoodMultiple.func_77661_b returned EnumAction.DRINK for drinks (drinking animation +
      // drink sound) and EAT otherwise. 26.2 carries the animation/sound on the Consumable component, so
      // pick DEFAULT_DRINK vs DEFAULT_FOOD (both 1.6s = 32 ticks, matching 1.12's 32-tick eat duration).
      super(new Item.Properties()
         .food(
            buildFood(healthAmount, foodAmount, saturation, alwaysEdible),
            drink ? net.minecraft.world.item.component.Consumables.DEFAULT_DRINK : net.minecraft.world.item.component.Consumables.DEFAULT_FOOD
         )
         .stacksTo(1)
         .setId(MillRegistry.itemKey(foodName)));
      this.healthAmount = healthAmount;
      this.drink = drink;
      this.regenerationDuration = regenerationDuration;
      this.drunkDuration = drunkDuration;
   }

   /** The 1.12 maxDamage (number of portions * DAMAGE_PER_PORTION); 0/64 for single-bite foods. */
   public int getMaxDamageValue() {
      return this.multiBiteMaxDamage;
   }

   public int getDrunkDuration() {
      return this.drunkDuration;
   }

   public int getHealthAmount() {
      return this.healthAmount;
   }

   public MobEffectInstance getPotionId() {
      return this.potionId;
   }

   public int getRegenerationDuration() {
      return this.regenerationDuration;
   }

   public boolean isClearEffects() {
      return this.clearEffects;
   }

   public boolean isDrink() {
      return this.drink;
   }

   @Override
   public ItemStack finishUsingItem(ItemStack stack, Level worldIn, LivingEntity entityLiving) {
      if (entityLiving instanceof Player entityplayer) {
         if (!worldIn.isClientSide() && this.clearEffects) {
            // 1.12 called entityLiving.curePotionEffects(MILKSTACK), i.e. removed all milk-curable
            // effects. 26.2 has no stack-based cure helper; removeAllEffects() reproduces the milk-like
            // "clear status effects" behaviour faithfully.
            entityLiving.removeAllEffects();
         }

         entityplayer.heal(this.healthAmount);
         // The vanilla FoodProperties.onConsume (applied via the food data component) handles
         // feeding the player and playing the burp sound, so we no longer call addStats/playSound here.

         if (!worldIn.isClientSide() && this.drink) {
            // 1.12 granted the CHEERS advancement when a player drank a Mill drink.
            org.millenaire.common.advancements.MillAdvancements.CHEERS.grant(entityplayer);
         }

         if (this.regenerationDuration > 0) {
            entityplayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, this.regenerationDuration * 20, 0), entityplayer);
         }

         if (this.drunkDuration > 0) {
            entityplayer.addEffect(new MobEffectInstance(MobEffects.NAUSEA, this.drunkDuration * 20, 0), entityplayer);
         }

         // Probabilistic potion-on-eat: 1.12 vanilla ItemFood.onFoodEaten applied the configured
         // PotionEffect server-side when rand.nextFloat() < probability. Reproduced faithfully here
         // (the 26.2 food/consumable components carry no per-food MobEffect, so this stays in code).
         if (!worldIn.isClientSide() && this.potionId != null && entityLiving.getRandom().nextFloat() < this.potionEffectProbability) {
            entityplayer.addEffect(new MobEffectInstance(this.potionId));
         }
      }

      // Multi-portion stack: 1.12 stored the number of portions as the item's maxDamage (portions*64)
      // and consumed 64 "damage" per bite, shrinking the stack only when exhausted. 26.2 Item.Properties
      // is sealed at construction, so the per-food maxDamage (set via setMaxDamage) is carried on this
      // field; we ensure the stack has the MAX_DAMAGE component so setDamageValue isn't clamped to 0.
      if (this.multiBiteMaxDamage > 0) {
         if (!stack.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE)) {
            stack.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, this.multiBiteMaxDamage);
         }
         int damage = stack.getDamageValue();
         if (damage + DAMAGE_PER_PORTION < this.multiBiteMaxDamage) {
            stack.setDamageValue(damage + DAMAGE_PER_PORTION);
         } else {
            stack.shrink(1);
         }
      } else {
         stack.shrink(1);
      }

      return stack;
   }

   public ItemFoodMultiple setClearEffects(boolean clearEffects) {
      this.clearEffects = clearEffects;
      return this;
   }

   public ItemFoodMultiple setPotionEffect(MobEffectInstance effect, float probability) {
      // 1.12 ItemFood.setPotionEffect(effect, probability) stored the effect + chance and applied it
      // in onFoodEaten. FoodProperties no longer carries MobEffects on 26.2, so the effect is applied
      // in finishUsingItem (server-side, rolled against this probability).
      this.potionId = effect;
      this.potionEffectProbability = probability;
      return this;
   }

   /** Fluent no-op retained for the 1.12 MillItems call chain — always-edible is now set at construction. */
   public ItemFoodMultiple setAlwaysEdible() {
      return this;
   }

   /**
    * 26.2: stack size is set on Properties.stacksTo at construction. The single-bite foods that called
    * this passed 64 (the default for a non-durable item), so this stays a fluent no-op; the multi-bite
    * foods instead use {@link #setMaxDamage} (a durable item already stacks to 1).
    */
   public ItemFoodMultiple setMaxStackSize(int size) {
      return this;
   }

   /**
    * Sets the multi-portion durability (1.12 maxDamage). Item.Properties is sealed at construction on
    * 26.2, so the value is stored here and applied to the eaten stack's MAX_DAMAGE component in
    * {@link #finishUsingItem}, reproducing the original "N bites then consumed" behaviour.
    */
   public ItemFoodMultiple setMaxDamage(int damage) {
      this.multiBiteMaxDamage = damage;
      return this;
   }
}
