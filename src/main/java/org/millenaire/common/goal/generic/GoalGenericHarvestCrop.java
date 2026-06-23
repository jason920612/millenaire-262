package org.millenaire.common.goal.generic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.annotedparameters.AnnotedParameter;
import org.millenaire.common.annotedparameters.ConfigAnnotations;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

public class GoalGenericHarvestCrop extends GoalGeneric {
   public static final String GOAL_TYPE = "harvesting";
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCK_ID
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Type of plant to harvest."
   )
   public Identifier cropType = null;
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BONUS_ITEM_ADD
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Item to be harvested, with chance."
   )
   public List<AnnotedParameter.BonusItem> harvestItem = new ArrayList<>();
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.INVITEM
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Boons for irrigated villages."
   )
   public InvItem irrigationBonusCrop = null;
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCKSTATE
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Blockstate the crop must have to be harvested. If not set, must have a meta of 7."
   )
   public BlockState harvestBlockState = null;

   public static int getCropBlockRipeMeta(Identifier cropType) {
      return 7;
   }

   @Override
   public void applyDefaultSettings() {
      // Player-like harvest is driven one tick at a time (reach → break-over-time → pickup → auto-replant), so the
      // action is re-entered every tick rather than the 1.12 fixed 2-tick countdown that instant-set the crop to air
      // and teleported the yield into the inventory.
      this.duration = 1;
      this.lookAtGoal = true;
      this.tags.add("tag_agriculture");
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws MillLog.MillenaireException {
      Point dest = null;
      Building destBuilding = null;

      for (Building buildingDest : this.getBuildings(villager)) {
         if (this.isDestPossible(villager, buildingDest)) {
            List<Point> soils = buildingDest.getResManager().getSoilPoints(this.cropType);
            if (soils != null) {
               for (Point p : soils) {
                  if (this.isValidHarvestSoil(villager.level(), p) && (dest == null || p.distanceTo(villager) < dest.distanceTo(villager))) {
                     dest = p.getAbove();
                     destBuilding = buildingDest;
                  }
               }
            }
         }
      }

      return dest == null ? null : this.packDest(dest, destBuilding);
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) throws Exception {
      return villager.getBestHoeStack();
   }

   @Override
   public ItemStack getIcon() {
      if (this.icon != null) {
         return this.icon.getItemStack();
      } else {
         return !this.harvestItem.isEmpty() ? this.harvestItem.get(0).item.getItemStack() : null;
      }
   }

   @Override
   public String getTypeLabel() {
      return "harvesting";
   }

   @Override
   public boolean isDestPossibleSpecific(MillVillager villager, Building b) {
      return true;
   }

   @Override
   public boolean isPossibleGenericGoal(MillVillager villager) throws Exception {
      return this.getDestination(villager) != null;
   }

   /**
    * MATURE check, unchanged from 1.12 intent: only RIPE crops are harvested. 1.12 compared the legacy meta (== crop
    * AGE) to 7; in 26.2 metadata is gone, so we read the AGE blockstate property directly — ripe == max age (7 for
    * vanilla crops). Immature crops fail this test and are SKIPPED (left to grow), exactly as the sim's
    * {@code if crop_age < CROP_MAX_AGE: skip}.
    */
   private boolean isValidHarvestSoil(Level world, Point p) {
      if (this.harvestBlockState != null) {
         return p.getAbove().getBlockActualState(world) == this.harvestBlockState;
      }
      Block cropBlock = BuiltInRegistries.BLOCK.getValue(this.cropType);
      if (p.getAbove().getBlock(world) != cropBlock) {
         return false;
      }
      BlockState cropState = p.getAbove().getBlockActualState(world);
      if (cropBlock instanceof CropBlock crop) {
         return crop.getAge(cropState) >= getCropBlockRipeMeta(this.cropType);
      }
      return false;
   }

   /**
    * Player-like harvest cycle, driven one tick at a time (duration == 1). Faithful to the Python sim's
    * {@code harvest_replant}: for each MATURE crop (and only mature — immature is left alone by the MATURE check in
    * {@link #isValidHarvestSoil} / {@link #getDestination}):
    *
    * <ol>
    *   <li>{@code ensureReach(cropPos)} — walk within player reach (crops are at ground level so this is normally a
    *       no-op; reach-extension is shared infrastructure).</li>
    *   <li>{@code breakTick(cropPos)} — REALLY break the ripe crop. Crops are 0-hardness, so the op's 0-hardness
    *       guard breaks it in a single tick (vanilla: a player hit pops a ripe crop instantly), spawning the real
    *       vanilla drops (wheat + seeds) as ItemEntities.</li>
    *   <li>{@code pickupTick} — the villager WALKS to each dropped ItemEntity and collects it (player-like; a Mob has
    *       no item-magnet).</li>
    *   <li>Grant the 1.12 authored Mill yield ({@link #harvestItem} + irrigation bonus) — this is the AUTHORITATIVE
    *       economy yield (see note below), then AUTO-REPLANT a fresh age-0 crop in place, consuming ONE seed.</li>
    * </ol>
    *
    * <p><b>Kept-1.12 yield (balance-affecting):</b> the real vanilla break-drops the villager picks up are a
    * worksite by-product; the Millénaire ECONOMY yield is the authored {@code harvestItem} list (e.g. wheat:
    * {@code wheat,100}+{@code seeds,100}+{@code seeds,75}; carrots: {@code carrot,100/75/50/25}) and the irrigation
    * bonus, neither of which equals the vanilla drop for every crop (cotton/grapes/Mill crops drop nothing useful
    * vanilla-side). So we KEEP granting the 1.12 {@code harvestItem}/{@code irrigationBonusCrop} for an unchanged
    * economy, while the break+pickup is now a genuine player-like operation. The picked-up vanilla seeds also fund
    * the replant so the seed economy stays balanced (sim: harvest seed → replant consumes one).
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point above = villager.getGoalDestPoint();
      if (above == null) {
         return true; // no target — let the goal re-pick.
      }
      Point soil = above.getBelow();
      BlockPos cropPos = above.getBlockPos();

      // STATELESS phase derivation from the WORLD (Goal instances are SINGLETONS shared across all villagers, so no
      // mutable per-goal field may hold per-villager phase — mirror the mine/chop goals which derive everything from
      // the world + point-owned TaskPointStore each tick). The dest point was selected because it held a RIPE crop
      // (getDestination → isValidHarvestSoil), so:
      //   - still a ripe crop   → BREAK phase (the crop hasn't been harvested yet this cycle).
      //   - now AIR             → we already broke it → PICKUP the real drops, then grant yield + auto-replant.
      //   - anything else       → already replanted / no longer ripe → recompute the next destination.
      boolean ripe = this.isValidHarvestSoil(villager.level(), soil);

      if (ripe) {
         // BREAK: ensureReach (crops are ground-level; normally already in reach) then break the ripe crop. A
         // 0-hardness crop breaks in a single breakTick (the op's 0-hardness guard) spawning the real drops.
         if (!VillagerWorldOps.withinReach(villager, cropPos)) {
            OpState reach = VillagerWorldOps.ensureReach(villager, cropPos);
            if (reach != OpState.COMPLETE) {
               return false; // approaching / extending reach — keep going next tick.
            }
         }
         OpState st = VillagerWorldOps.breakTick(villager, cropPos);
         switch (st) {
            case APPROACHING:
            case EXTENDING_REACH:
            case IN_PROGRESS:
               return false; // keep breaking / walking closer next tick.
            case BLOCKED:
               return true; // a crop should never be unbreakable; abandon so the goal re-picks.
            case COMPLETE:
               // The crop just broke; a DoublePlant crop (e.g. tall flowers) also clears its upper half. Next tick
               // the point is air → the PICKUP+replant branch below runs.
               if (villager.getBlock(above.getAbove()) instanceof DoublePlantBlock) {
                  VillagerWorldOps.breakTick(villager, above.getAbove().getBlockPos());
               }
               return false;
            default:
               return false;
         }
      } else if (villager.level().getBlockState(cropPos).isAir()) {
         // PICKUP + FINISH: we broke the crop (the point is now air). Walk to + collect each real drop; once all are
         // collected, grant the 1.12 authored yield and auto-replant a fresh age-0 crop, then advance to the next.
         OpState pst = VillagerWorldOps.pickupTick(villager, cropPos);
         if (pst != OpState.COMPLETE) {
            return false; // still walking to / collecting the real wheat+seeds drops.
         }
         grantMillYield(villager);
         replant(villager, above, soil);
         // fall through to recompute the next destination below.
      }
      // else: the point is neither a ripe crop nor air-after-harvest (already replanted / never matured) — skip it,
      // recompute the next destination below. This is the MATURE-only guarantee: immature crops are never touched.

      if (this.isDestPossibleSpecific(villager, villager.getGoalBuildingDest())) {
         try {
            villager.setGoalInformation(this.getDestination(villager));
         } catch (MillLog.MillenaireException destException) {
            // FAIL-FAST: failing to recompute the harvest destination left the villager's goal state
            // stale (1.12 logged-and-continued). Surface the navigation corruption loudly.
            throw MillCrash.fail("Goal", "failed to recompute harvest-crop destination for " + villager + ": " + destException);
         }

         return false;
      } else {
         return true;
      }
   }

   /**
    * Grant the 1.12 authored Mill economy yield: the {@link #irrigationBonusCrop} (chance scaled by the village's
    * irrigation) and each rolled {@link #harvestItem}. Identical to the 1.12 {@code performAction} grant — this is
    * the balance-preserving yield (the real picked-up vanilla drop is a by-product, see {@link #performAction}).
    */
   private void grantMillYield(MillVillager villager) {
      if (this.irrigationBonusCrop != null) {
         float irrigation = villager.getTownHall().getVillageIrrigation();
         double rand = Math.random();
         if (rand < irrigation / 100.0F) {
            villager.addToInv(this.irrigationBonusCrop, 1);
         }
      }

      Building dest = villager.getGoalBuildingDest();
      for (AnnotedParameter.BonusItem bonusItem : this.harvestItem) {
         if ((bonusItem.tag == null || dest != null && dest.containsTags(bonusItem.tag)) && MillRandom.randomInt(100) <= bonusItem.chance) {
            villager.addToInv(bonusItem.item, 1);
         }
      }
   }

   /**
    * AUTO-REPLANT in place (sim parity): place a fresh age-0 crop of the same type back on the (still-tilled) soil,
    * consuming ONE seed from the villager's stock (the seed it just picked up / the authored {@code seeds} yield).
    * If no seed is available the plot is left empty for the separate {@link GoalGenericPlantCrop} (first-planting)
    * goal to handle — we never fabricate a crop without a seed. Only replants when the broken spot is now air and the
    * soil below survived, so we never clobber a player build there.
    */
   private void replant(MillVillager villager, Point above, Point soil) {
      Block cropBlock = BuiltInRegistries.BLOCK.getValue(this.cropType);
      if (!(cropBlock instanceof CropBlock)) {
         return; // only auto-replant true crops (flowers/grapes/Mill specials keep their dedicated plant goal).
      }
      // Spot must be clear (we just broke the crop) so we never clobber a player build that appeared there.
      if (!villager.level().getBlockState(above.getBlockPos()).isAir()) {
         return;
      }
      int taken = consumeOneSeed(villager);
      if (taken == 0) {
         if (MillConfigValues.LogOther >= 2 && villager.extraLog) {
            MillLog.debug(this, "No seed to auto-replant " + this.cropType + " at " + above + "; leaving for plant goal.");
         }
         return; // no seed → leave the plot for the first-planting goal.
      }
      BlockState fresh = cropBlock.defaultBlockState(); // age 0.
      villager.setBlockstate(above, fresh);
      villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
      if (MillConfigValues.LogOther >= 3 && villager.extraLog) {
         MillLog.debug(this, "Auto-replanted age-0 " + this.cropType + " at " + above + " (consumed 1 seed).");
      }
   }

   /**
    * Consume one seed for the replant from the villager's stock. Wheat drops {@code minecraft:wheat_seeds}; other
    * vanilla crops (carrots/potatoes/beetroot) replant from the food item / their own seed. We try the crop's own
    * item first (carrot/potato are both seed and food), then the vanilla wheat/beetroot seeds, returning the count
    * actually removed (0 if none available). This keeps the seed economy balanced (one harvested seed → one replant).
    */
   private int consumeOneSeed(MillVillager villager) {
      Block cropBlock = BuiltInRegistries.BLOCK.getValue(this.cropType);
      // The seed item a crop replants from is NOT the crop block's item (a wheat/carrot CROP block has no item form
      // — asItem() is AIR). Map each vanilla crop to the item that plants it (what its harvest drops): wheat ←
      // wheat_seeds, carrots ← carrot, potatoes ← potato, beetroot ← beetroot_seeds. Try the mapped seed for THIS
      // crop, then fall back to the common wheat seed. Consuming one keeps the seed economy balanced (sim: one
      // harvested seed → one replant).
      net.minecraft.world.item.Item seedItem = null;
      if (cropBlock == net.minecraft.world.level.block.Blocks.WHEAT) {
         seedItem = net.minecraft.world.item.Items.WHEAT_SEEDS;
      } else if (cropBlock == net.minecraft.world.level.block.Blocks.CARROTS) {
         seedItem = net.minecraft.world.item.Items.CARROT;
      } else if (cropBlock == net.minecraft.world.level.block.Blocks.POTATOES) {
         seedItem = net.minecraft.world.item.Items.POTATO;
      } else if (cropBlock == net.minecraft.world.level.block.Blocks.BEETROOTS) {
         seedItem = net.minecraft.world.item.Items.BEETROOT_SEEDS;
      }
      if (seedItem != null) {
         int t = villager.takeFromInv(seedItem, 0, 1);
         if (t > 0) {
            return t;
         }
      }
      // Fallback for any other CropBlock-derived crop: the most common seed (wheat seeds).
      return villager.takeFromInv(net.minecraft.world.item.Items.WHEAT_SEEDS, 0, 1);
   }

   @Override
   public int priority(MillVillager villager) throws MillLog.MillenaireException {
      Goal.GoalInformation info = this.getDestination(villager);
      return info != null && info.getDest() != null ? (int)(1000.0 - villager.getPos().distanceTo(info.getDest())) : -1;
   }

   @Override
   public boolean validateGoal() {
      if (this.cropType == null) {
         MillLog.error(this, "The croptype is mandatory in custom harvest goals.");
         return false;
      } else {
         return true;
      }
   }
}
