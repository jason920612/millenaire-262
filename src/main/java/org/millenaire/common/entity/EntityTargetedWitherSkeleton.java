package org.millenaire.common.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Wither skeleton that uses melee instead of ranged attacks and never despawns.
 *
 * <p>1.12→26.2: {@code entityInit} that cleared {@code tasks}/{@code targetTasks}
 * and re-added {@code EntityAI*} tasks becomes {@link #registerGoals()} adding
 * {@code Goal}s via {@code goalSelector}/{@code targetSelector}. The vanilla
 * {@code AbstractSkeleton} registers a ranged bow goal in its own
 * {@code registerGoals}; Mill instead clears that and installs a melee goal so the
 * wither skeleton fights hand-to-hand. {@code setItemStackToSlot}→{@code setItemSlot}.
 */
public class EntityTargetedWitherSkeleton extends WitherSkeleton {

   public EntityTargetedWitherSkeleton(EntityType<? extends WitherSkeleton> type, Level level) {
      super(type, level);
      this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
   }

   @Override
   protected void registerGoals() {
      // Skip AbstractSkeleton's ranged-bow goals: install Mill's melee set directly.
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 0.31, false));
      this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
      this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
   }

   @Override
   public boolean removeWhenFarAway(double distSqr) {
      return false;
   }

   @Override
   public void aiStep() {
      // 1.12 onLivingUpdate → 26.2 aiStep. Kept the extinguish() to stop it burning in daylight.
      super.aiStep();
      this.clearFire();
   }
}
