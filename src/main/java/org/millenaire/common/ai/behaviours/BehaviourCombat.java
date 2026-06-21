package org.millenaire.common.ai.behaviours;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.millenaire.common.ai.MillBehaviour;
import org.millenaire.common.ai.MillInfluenceGrid;
import org.millenaire.common.entity.MillVillager;

/**
 * Tactical combat behaviour (reqs 3/5/6/10). Each tick it: validates/acquires a target, decides melee vs
 * ranged from the held weapon (generic {@link ProjectileWeaponItem} so MODDED bows work too), then picks
 * the best nearby standable cell by POSITION SCORING over the shared {@link MillInfluenceGrid} —
 * safety + weapon-optimal range + not-crowding-allies — and moves there before attacking. 迂迴 / kite /
 * retreat / spread emerge from the scoring (no state machine). Never teleports (req 1): an unreachable
 * target is dropped gracefully.
 */
public final class BehaviourCombat implements MillBehaviour {
   private static final int SCAN = 4;           // candidate-cell search radius
   private static final double MELEE_RANGE = 2.0;
   private static final double SPEED = 0.6;
   // scoring weights
   private static final float W_DANGER = 1.0F;
   private static final float W_RANGE = 2.0F;
   private static final float W_ALLY = 1.5F;
   private int rangedCooldown = 0;

   @Override
   public boolean canRun(MillVillager villager) {
      LivingEntity t = villager.getTarget();
      return t != null && t.isAlive();
   }

   @Override
   public int priority(MillVillager villager) {
      return 50; // preempts work/idle
   }

   @Override
   public boolean tick(MillVillager villager) {
      LivingEntity target = villager.getTarget();
      if (target == null || !target.isAlive()) {
         return false;
      }
      boolean ranged = isRanged(villager);
      double optimal = ranged ? 12.0 : 1.6; // weapon-dependent optimal distance band (req 10)

      // 1. Tactical positioning: move to the highest-scoring safe cell (req 5/6). 迂迴/kite emerge here.
      BlockPos best = bestCombatCell(villager, target, optimal, ranged);
      if (best != null && !villager.blockPosition().equals(best)) {
         boolean pathed = villager.getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, SPEED);
         if (!pathed && villager.distanceToSqr(target) > 64 * 64) {
            return false; // can't reach a useful position and target is far → drop target (no warp)
         }
      }

      // 2. Attack when the target is within reach (melee) — ranged firing is delegated to the entity's
      //    ranged-attack hook when in band + line of sight (wired per villager type).
      double dist = villager.distanceTo(target);
      villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
      if (!ranged && dist <= MELEE_RANGE) {
         villager.doHurtTarget(villager.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null, target);
      } else if (ranged && dist <= optimal + 4.0 && villager.hasLineOfSight(target)) {
         if (this.rangedCooldown > 0) {
            this.rangedCooldown--;
         } else if (!friendlyFireRisk(villager, target)) {
            // req 12: only loose the arrow if no ally is in the line of fire — otherwise hold (the tactical
            // repositioning above will move us to a clear angle next tick).
            tryRangedAttack(villager, target);
            this.rangedCooldown = 30; // ~1.5s between shots
         }
      }
      return true;
   }

   @Override
   public void onStop(MillVillager villager) {
      villager.getNavigation().stop();
   }

   /** Score nearby standable cells; higher = safer + better weapon range + not crowding allies. */
   private static BlockPos bestCombatCell(MillVillager villager, LivingEntity target, double optimal, boolean ranged) {
      Level level = villager.level();
      MillInfluenceGrid danger = villager.getAiInfluence(); // shared danger field (kept current by the villager tick)
      List<MillVillager> allies = nearbyAllies(villager);
      BlockPos origin = villager.blockPosition();
      BlockPos best = null;
      float bestScore = -Float.MAX_VALUE;
      for (int dx = -SCAN; dx <= SCAN; dx++) {
         for (int dz = -SCAN; dz <= SCAN; dz++) {
            for (int dy = 1; dy >= -2; dy--) {
               BlockPos cell = origin.offset(dx, dy, dz);
               if (!standable(level, cell)) {
                  continue;
               }
               double d = Math.sqrt(cell.distToCenterSqr(target.getX(), target.getY(), target.getZ()));
               float score = 0.0F;
               score -= W_DANGER * danger.dangerAt(cell);                       // safety (req 4/5)
               score -= W_RANGE * (float) Math.abs(d - optimal);                // weapon optimal range (req 10)
               score -= W_ALLY * allyCrowding(allies, villager, cell);          // don't bunch up (req 6)
               if (score > bestScore) {
                  bestScore = score;
                  best = cell;
               }
               break; // first standable y for this column
            }
         }
      }
      return best;
   }

   private static float allyCrowding(List<MillVillager> allies, MillVillager self, BlockPos cell) {
      float c = 0.0F;
      for (MillVillager a : allies) {
         if (a == self) {
            continue;
         }
         double dsq = a.blockPosition().distSqr(cell);
         if (dsq < 4.0) {
            c += (float) (4.0 - dsq); // penalise standing on top of an ally
         }
      }
      return c;
   }

   /**
    * req 12: would a shot at {@code target} pass too close to a fellow villager? Projects each nearby ally
    * onto the firing line (eye→target); if one sits in front of the target and within ~0.6 blocks of the
    * line, firing is unsafe and we hold.
    */
   private static boolean friendlyFireRisk(MillVillager villager, LivingEntity target) {
      net.minecraft.world.phys.Vec3 from = villager.getEyePosition();
      net.minecraft.world.phys.Vec3 dir = target.getEyePosition().subtract(from);
      double len = dir.length();
      if (len < 0.01) {
         return false;
      }
      net.minecraft.world.phys.Vec3 norm = dir.scale(1.0 / len);
      for (MillVillager ally : nearbyAllies(villager)) {
         if (ally == villager) {
            continue;
         }
         net.minecraft.world.phys.Vec3 toAlly = ally.getEyePosition().subtract(from);
         double proj = toAlly.dot(norm);
         if (proj <= 0.0 || proj >= len) {
            continue; // ally is behind us or beyond the target — not in the way
         }
         double perp = toAlly.subtract(norm.scale(proj)).length();
         if (perp < 0.6) {
            return true; // ally sits on the shot line
         }
      }
      return false;
   }

   /** Nearby fellow villagers, queried via getEntities (NOT getEntitiesOfClass — see updateNewAiDanger note:
    *  the class-cache path is not reentrancy-safe during the tick and caused a CME). */
   private static List<MillVillager> nearbyAllies(MillVillager villager) {
      List<MillVillager> out = new java.util.ArrayList<>();
      for (net.minecraft.world.entity.Entity e : villager.level().getEntities(
         villager, villager.getBoundingBox().inflate(SCAN + 2), e -> e instanceof MillVillager)) {
         out.add((MillVillager) e);
      }
      return out;
   }

   private static boolean isRanged(MillVillager villager) {
      ItemStack main = villager.getMainHandItem();
      return main.getItem() instanceof ProjectileWeaponItem;
   }

   private static void tryRangedAttack(MillVillager villager, LivingEntity target) {
      // Prefer the entity's own ranged hook if it has one; otherwise fire a generic arrow so ANY villager
      // holding a (vanilla or modded) ranged weapon can shoot (req 10).
      if (villager instanceof net.minecraft.world.entity.monster.RangedAttackMob rm) {
         rm.performRangedAttack(target, 1.0F);
         return;
      }
      if (!(villager.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
         return;
      }
      net.minecraft.world.item.ItemStack ammo = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW);
      net.minecraft.world.entity.projectile.arrow.Arrow arrow =
         new net.minecraft.world.entity.projectile.arrow.Arrow(sl, villager, ammo, villager.getMainHandItem());
      double dx = target.getX() - villager.getX();
      double dy = target.getY(0.3333333333333333) - arrow.getY();
      double dz = target.getZ() - villager.getZ();
      double horiz = Math.sqrt(dx * dx + dz * dz);
      arrow.shoot(dx, dy + horiz * 0.2, dz, 1.6F, 6.0F);
      villager.playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1.0F,
         1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
      // Defer the spawn to the end of the server tick: adding an entity mid entity-tick-loop can corrupt the
      // section iteration (the ConcurrentModificationException we hit). execute() runs it safely after the loop.
      sl.getServer().execute(() -> sl.addFreshEntity(arrow));
   }

   private static boolean standable(Level level, BlockPos foot) {
      BlockState ground = level.getBlockState(foot.below());
      BlockState at = level.getBlockState(foot);
      BlockState head = level.getBlockState(foot.above());
      return ground.isSolid() && at.getFluidState().isEmpty() && at.isAir() && head.isAir();
   }
}
