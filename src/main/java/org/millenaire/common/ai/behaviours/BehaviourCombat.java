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
   private static final double MELEE_RANGE = 3.0; // player-like reach (was 2.0 — felt too short)
   private static final double SPEED = 0.6;
   // scoring weights
   private static final float W_DANGER = 1.0F;
   private static final float W_RANGE = 2.0F;
   private static final float W_ALLY = 1.5F;
   private int rangedCooldown = 0;
   private int repathCooldown = 0;
   private int strafeTimer = 0;
   private boolean strafeRight = true;
   private int unreachableTicks = 0;

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
      double optimal = ranged ? 11.0 : 2.2; // weapon-dependent optimal distance band (req 10)
      double dist = villager.distanceTo(target);

      // 1. Intent-driven tactical movement (req 3/5): CLOSE if too far, KITE back if too close (ranged),
      //    STRAFE sideways when at range. If the target sits where we can't stand next to it (in water, on a
      //    pillar), head for the nearest REACHABLE point toward it (vanilla moveTo stops at the closest node,
      //    e.g. the shore) and wait there — then GIVE UP after a while instead of freezing forever.
      if (--this.repathCooldown <= 0 || villager.getNavigation().isDone()) {
         this.repathCooldown = 5;
         BlockPos goal = desiredCombatCell(villager, target, optimal, dist);
         double gx = goal != null ? goal.getX() + 0.5 : target.getX();
         double gy = goal != null ? goal.getY() : target.getY();
         double gz = goal != null ? goal.getZ() + 0.5 : target.getZ();
         double sp = (dist > optimal + 4.0) ? SPEED + 0.25 : SPEED; // hurry to close the gap
         villager.getNavigation().moveTo(gx, gy, gz, sp);
      }
      boolean inRange = (!ranged && dist <= MELEE_RANGE)
         || (ranged && dist <= optimal + 4.0 && villager.hasLineOfSight(target));
      if (!inRange && villager.getNavigation().isDone()) {
         // Parked at the nearest reachable point but still can't hit it → unreachable.
         if (isRangedHostile(target) && villager.hasLineOfSight(target) && !villager.isRaider) {
            // DEFENDERS retreat out of a ranged enemy's line of fire instead of standing and being shot, then
            // give up quickly. RAIDERS do NOT retreat — they press the assault (village-war balance: retreat
            // must not blunt an attack or erase the ranged cultures' kiting edge — see balance research).
            BlockPos cover = retreatFromRanged(villager, target);
            if (cover != null) {
               villager.getNavigation().moveTo(cover.getX() + 0.5, cover.getY(), cover.getZ() + 0.5, SPEED + 0.3);
            }
            if (++this.unreachableTicks > 40) {
               return false;
            }
         } else if (++this.unreachableTicks > (villager.isRaider ? 200 : 120)) {
            // Can't hurt us here (or we're a raider pressing on) — hold, raiders longer, then drop the target.
            return false;
         }
      } else {
         this.unreachableTicks = 0;
      }

      // 2. Attack when in reach.
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
   public void onStart(MillVillager villager) {
      // Call for help: rally fellow villagers in the village (callForHelp gives those who help-in-attacks the
      // target + clears their goal, so the engine's combat behaviour makes them drop work and join the fight).
      LivingEntity target = villager.getTarget();
      if (target != null && villager.getTownHall() != null) {
         villager.getTownHall().callForHelp(target);
      }
   }

   @Override
   public void onStop(MillVillager villager) {
      villager.getNavigation().stop();
   }

   /**
    * Intent-driven combat goal cell: a standable spot at the weapon's optimal distance from the target —
    * CLOSING when too far (so a melee villager charges a skeleton instead of standing), KITING when too close
    * (ranged), or a sideways STRAFE when already at range (走位, so we don't stand still). The engaged
    * target's OWN danger is deliberately ignored (we must close on it); only OTHER hostiles bias the strafe.
    */
   private BlockPos desiredCombatCell(MillVillager villager, LivingEntity target, double optimal, double dist) {
      net.minecraft.world.phys.Vec3 vp = villager.position();
      net.minecraft.world.phys.Vec3 tp = target.position();
      double dx = tp.x - vp.x;
      double dz = tp.z - vp.z;
      double horiz = Math.sqrt(dx * dx + dz * dz);
      if (horiz < 0.01) {
         return null;
      }
      double nx = dx / horiz;
      double nz = dz / horiz; // unit vector toward target (horizontal)
      double gx;
      double gz;
      if (dist > optimal + 1.5 || dist < optimal - 1.5) {
         // Too far → approach; too close → kite back: a point at optimal distance from the target on our side.
         // PACK TACTICS (群狼): rotate that approach direction by a per-villager angle so allies come in from
         // DIFFERENT sides and SURROUND the target instead of stacking — emergent encirclement, stable per
         // villager (uuid) so they don't jitter between sides.
         double spread = ((villager.getUUID().hashCode() % 7) - 3) * 0.35; // ~±1.05 rad spread across the pack
         double cos = Math.cos(spread);
         double sin = Math.sin(spread);
         double rnx = nx * cos - nz * sin;
         double rnz = nx * sin + nz * cos;
         gx = tp.x - rnx * optimal;
         gz = tp.z - rnz * optimal;
      } else {
         // At range → strafe perpendicular so we keep moving. RANDOM side + random duration so an archer
         // can't lead the shot (req: unpredictable). Only overridden if a side is clearly more dangerous.
         if (--this.strafeTimer <= 0) {
            this.strafeTimer = 6 + villager.getRandom().nextInt(14);
            this.strafeRight = pickStrafeSide(villager, vp, nx, nz);
         }
         double side = this.strafeRight ? 1.0 : -1.0;
         gx = vp.x + (-nz) * side * 2.5;
         gz = vp.z + (nx) * side * 2.5;
      }
      return findStandable(villager.level(), (int) Math.floor(gx), (int) Math.floor(tp.y), (int) Math.floor(gz));
   }

   /** RANDOM strafe side (unpredictable to aim at), overridden only when one side is clearly more dangerous. */
   private static boolean pickStrafeSide(MillVillager villager, net.minecraft.world.phys.Vec3 vp, double nx, double nz) {
      boolean r = villager.getRandom().nextBoolean();
      MillInfluenceGrid danger = villager.getAiInfluence();
      float right = danger.dangerAt((int) Math.floor(vp.x - nz * 2.5), (int) Math.floor(vp.z + nx * 2.5));
      float left = danger.dangerAt((int) Math.floor(vp.x + nz * 2.5), (int) Math.floor(vp.z - nx * 2.5));
      if (r && right > left + 4.0F) {
         return false;
      }
      if (!r && left > right + 4.0F) {
         return true;
      }
      return r;
   }

   /** First standable cell near (x,z), scanning a couple of blocks up/down from the target's height. */
   private static BlockPos findStandable(Level level, int x, int y, int z) {
      for (int dy = 2; dy >= -3; dy--) {
         BlockPos foot = new BlockPos(x, y + dy, z);
         if (standable(level, foot)) {
            return foot;
         }
      }
      return null;
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

   /** Does this HOSTILE attack at range (so standing in its line of fire gets us shot)? Covers skeletons,
    *  pillagers/crossbow illagers, and anything holding a (vanilla or modded) projectile weapon. */
   private static boolean isRangedHostile(LivingEntity e) {
      if (e instanceof net.minecraft.world.entity.monster.RangedAttackMob) {
         return true;
      }
      return e.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
   }

   /** Retreat away from a ranged enemy to a standable cell 6-12 blocks back, PREFERRING one the enemy can't
    *  see (cover) so we leave its line of fire rather than stand and get shot. */
   private static BlockPos retreatFromRanged(MillVillager villager, LivingEntity target) {
      net.minecraft.world.phys.Vec3 vp = villager.position();
      double ax = vp.x - target.getX();
      double az = vp.z - target.getZ();
      double len = Math.sqrt(ax * ax + az * az);
      if (len < 0.01) {
         return null;
      }
      ax /= len;
      az /= len;
      Level level = villager.level();
      BlockPos fallback = null;
      for (int d = 6; d <= 12; d += 2) {
         BlockPos cell = findStandable(level, (int) Math.floor(vp.x + ax * d), (int) Math.floor(vp.y), (int) Math.floor(vp.z + az * d));
         if (cell != null) {
            if (fallback == null) {
               fallback = cell;
            }
            if (!cellVisibleTo(level, target, cell)) {
               return cell; // behind cover — out of the enemy's line of fire
            }
         }
      }
      return fallback; // no cover found — at least back off out of close range
   }

   /** Approximate whether the enemy's eyes have a clear line to a candidate cell (for picking cover). */
   private static boolean cellVisibleTo(Level level, LivingEntity enemy, BlockPos cell) {
      net.minecraft.world.phys.Vec3 from = enemy.getEyePosition();
      net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(cell.getX() + 0.5, cell.getY() + 1.0, cell.getZ() + 0.5);
      net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
         from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
         net.minecraft.world.level.ClipContext.Fluid.NONE, enemy));
      return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
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
