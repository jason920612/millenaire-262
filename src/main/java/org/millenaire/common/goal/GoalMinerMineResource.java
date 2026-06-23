package org.millenaire.common.goal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.OreVeinMiner;
import com.coderyo.jason.ops.TaskPointStore;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

@DocumentedElement.Documentation("Go and mine rocks etc at the villager's house.")
public class GoalMinerMineResource extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] IS_ULU;
   public String buildingTag = null;

   private static ItemStack[] isUlu() {
      if (IS_ULU == null) {
         IS_ULU = new ItemStack[]{new ItemStack(MillItems.ULU, 1)};
      }
      return IS_ULU;
   }

   public GoalMinerMineResource() {
      this.icon = InvItem.createInvItem(Items.IRON_PICKAXE);
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like mining is now driven per-tick by performAction (reach → break-over-time → pickup): the real
      // duration emerges from the break math + walk-to-each-drop, so the action is re-entered every tick (1) rather
      // than waiting a fixed 1.12 countdown then teleporting the yield into the inventory.
      return 1;
   }

   public List<Building> getBuildings(MillVillager villager) {
      List<Building> buildings = new ArrayList<>();
      if (this.buildingTag == null) {
         buildings.add(villager.getHouse());
      } else {
         for (Building b : villager.getTownHall().getBuildingsWithTag(this.buildingTag)) {
            buildings.add(b);
         }
      }

      return buildings;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      List<Building> buildings = this.getBuildings(villager);
      List<Point> validSources = new ArrayList<>();
      List<Building> validDests = new ArrayList<>();

      for (Building possibleDest : buildings) {
         List<CopyOnWriteArrayList<Point>> sources = possibleDest.getResManager().sources;

         for (int i = 0; i < sources.size(); i++) {
            BlockState sourceBlockState = possibleDest.getResManager().sourceTypes.get(i);

            for (int j = 0; j < sources.get(i).size(); j++) {
               BlockState actualBlockState = WorldUtilities.getBlockState(villager.level(), sources.get(i).get(j));
               if (actualBlockState == sourceBlockState) {
                  validSources.add(sources.get(i).get(j));
                  validDests.add(possibleDest);
               }
            }
         }
      }

      if (validSources.isEmpty()) {
         return null;
      } else {
         int randomTarget = MillRandom.randomInt(validSources.size());
         return this.packDest(validSources.get(randomTarget), validDests.get(randomTarget));
      }
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) throws Exception {
      Block targetBlock = villager.getBlock(villager.getGoalDestPoint());
      if (targetBlock == Blocks.SAND || targetBlock == Blocks.CLAY || targetBlock == Blocks.GRAVEL) {
         return villager.getBestShovelStack();
      } else {
         return targetBlock != Blocks.SNOW && targetBlock != Blocks.ICE ? villager.getBestPickaxeStack() : isUlu();
      }
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_WIDE_NO_LEAVES : JPS_CONFIG_WIDE;
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) throws Exception {
      return this.getDestination(villager) != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   /**
    * Player-like mine cycle, driven one tick at a time (actionDuration == 1). Returns {@code false} while the
    * cycle is mid-way (goal stays in action), {@code true} when the whole cycle is done (goal advances).
    *
    * <p>Cycle: ensureTool (strict pickaxe/shovel; ULU-mined snow/ice are exempt — the goal already holds the ULU)
    * → {@link VillagerWorldOps#breakTick} until the source block really breaks over time (reach-gated, cracks,
    * real tool-aware drops) → {@link VillagerWorldOps#pickupTick} walking to each dropped ItemEntity → reconcile
    * to the 1.12 yield for the Mill-special / balance-affecting blocks → schedule the ore to REGROW on the point.
    *
    * <p>1.12 fidelity: 1.12 never removed the source block (it faked the break and {@code addToInv}'d a fixed item),
    * so the supply was renewable. We restore that net effect by really breaking + regrowing, and we keep the 1.12
    * NET YIELD per block where the real drop would differ (see {@code grantMillYield}).
    */
   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true; // no target left — let the goal re-pick.
      }

      // --- Phase 1 (#3): REAL ore-vein / cave mining first (com.coderyo.jason.ops). Anchor the mine on the
      // worksite building (stable per-mine) so its frontier + hazard set are shared across villagers + ticks.
      // The driver scans for real ore, tunnels to the vein, flood-mines it (real drops carried home), uses
      // natural caves, advances the FRONTIER outward when local ore is exhausted, and is LAVA-SAFE throughout.
      // Only when NO real ore is reachable do we fall back to the legacy regrowing-source-block heuristic below.
      BlockPos anchor = mineAnchor(villager, dest);
      OreVeinMiner.MineResult mineResult = OreVeinMiner.mineTick(villager, anchor);
      switch (mineResult) {
         case WORKING:
            return false; // mid-cycle: navigating / tunnelling / flood-mining / picking up. Stay in-action.
         case CYCLE_DONE:
            return true;  // a vein was cleared / the frontier advanced — let the goal re-pick.
         case NEED_TOOL:
            // No pickaxe: do NOT break (no drops). Stay in-goal; GoalGetTool will pre-empt and fetch one.
            if (MillConfigValues.LogMiner >= 2 && villager.extraLog) {
               MillLog.debug(this, "No pickaxe for real ore mining at " + anchor + "; waiting for tool.");
            }
            return false;
         case NO_ORE:
         default:
            // No reachable ore AND the frontier can't advance (boxed in by hazards/bedrock): this mine spot is
            // exhausted/blocked. Let the goal re-pick so the village relocates the mine / picks a new lead — do
            // NOT revert to the old regrowing-source-block heuristic. (Emergent vision, per the user: REAL ore-
            // vein mining ONLY — no fallback to the original logic.)
            return true;
      }
   }

   /**
    * The stable ANCHOR for this villager's mine: the worksite building's position (so every miner of the same
    * building shares one frontier + hazard set), falling back to the destination source point if no building dest
    * is resolved. Keying the {@link TaskPointStore.MineState} here makes the excavation point-owned + shared.
    */
   private BlockPos mineAnchor(MillVillager villager, Point dest) {
      Point bp = villager.getGoalBuildingDestPoint();
      if (bp != null) {
         return bp.getBlockPos();
      }
      return dest.getBlockPos();
   }

   /** Walk-to-each-drop pickup; on COMPLETE reconcile to the 1.12 yield and finish the goal. */
   private boolean runPickupAndFinish(MillVillager villager, BlockPos pos, Block block) {
      OpState pst = VillagerWorldOps.pickupTick(villager, pos);
      if (pst != OpState.COMPLETE) {
         return false; // still walking to / collecting drops.
      }
      // The block at pos is now air (we broke it), so `block` may be AIR on a later-tick re-entry. The broken
      // block's identity persists on the POINT via the regrow schedule — recover it from there for the yield
      // reconciliation so a multi-tick pickup still grants the correct 1.12 Mill yield (snow/ice/clay).
      Block brokenBlock = block;
      if (brokenBlock.defaultBlockState().isAir()) {
         TaskPointStore.Regrow regrow = TaskPointStore.get().peekRegrow(villager.level(), pos);
         if (regrow != null) {
            brokenBlock = regrow.state().getBlock();
         }
      }
      grantMillYield(villager, brokenBlock);
      if (MillConfigValues.LogMiner >= 3 && villager.extraLog) {
         MillLog.debug(this, "Mined + collected " + block + " at " + pos);
      }
      return true;
   }

   /** Schedule the broken source to regrow on the point, back to its source state, after the default delay. */
   private void scheduleRegrow(MillVillager villager, BlockPos pos, BlockState sourceState) {
      TaskPointStore.get().scheduleRegrow(villager.level(), pos, sourceState, villager.level().getGameTime());
      if (MillConfigValues.LogMiner >= 2 && villager.extraLog) {
         MillLog.debug(this, "Scheduled regrow of " + sourceState.getBlock() + " at " + pos);
      }
   }

   /**
    * Reconcile the picked-up real drops to the 1.12 NET YIELD where it matters (balance preservation):
    *
    * <ul>
    *   <li>SAND / SANDSTONE / GRAVEL / STONE(→cobblestone): real drop == 1.12 item, nothing to do — the pickup
    *       already put the right item in the inventory.</li>
    *   <li>CLAY: real break drops 4 clay balls; 1.12 granted exactly 1 → trim to 1 (kept as the 1.12 balance).</li>
    *   <li>SNOW (layer): real break drops snowballs; 1.12 converted it to a Mill {@code SNOW_BRICK} → grant 1
    *       SNOW_BRICK (the picked-up snowballs are the worksite by-product; the economy item is the brick).</li>
    *   <li>ICE: real break drops nothing (no silk-touch); 1.12 converted it to a Mill {@code ICE_BRICK} → grant 1
    *       ICE_BRICK.</li>
    * </ul>
    *
    * These are the explicit "kept as 1.12" yield cases for the mine.
    */
   private void grantMillYield(MillVillager villager, Block block) {
      if (block == Blocks.CLAY) {
         // 1.12 net yield was 1 clay ball; the real break dropped 4. Remove the 3 extra so balance is unchanged.
         int extra = 3;
         villager.takeFromInv(Items.CLAY_BALL, 0, extra);
      } else if (block == Blocks.SNOW) {
         villager.addToInv(MillBlocks.SNOW_BRICK, 1);
      } else if (block == Blocks.ICE) {
         villager.addToInv(MillBlocks.ICE_BRICK, 1);
      }
      // SAND, GRAVEL, SANDSTONE, STONE(→COBBLESTONE): real drop already equals the 1.12 item; no adjustment.
   }

   @Override
   public int priority(MillVillager villager) throws Exception {
      return 30;
   }

   @Override
   public int range(MillVillager villager) {
      return 5;
   }

   @Override
   public boolean stuckAction(MillVillager villager) throws Exception {
      return this.performAction(villager);
   }

   @Override
   public boolean swingArms() {
      return true;
   }
}
