package org.millenaire.common.goal.generic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import org.millenaire.common.annotedparameters.AnnotedParameter;
import org.millenaire.common.annotedparameters.ConfigAnnotations;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;

public class GoalGenericMining extends GoalGeneric {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] IS_ULU;
   public static final String GOAL_TYPE = "mining";

   private static ItemStack[] isUlu() {
      if (IS_ULU == null) {
         IS_ULU = new ItemStack[]{new ItemStack(MillItems.ULU, 1)};
      }
      return IS_ULU;
   }
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.BLOCKSTATE
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Blockstate of the source, like stone (not necessarily the block being harvest - stone gives cobblestone for example)."
   )
   public BlockState sourceBlockState = null;
   @ConfigAnnotations.ConfigField(
      type = AnnotedParameter.ParameterType.INVITEM_NUMBER_ADD,
      paramName = "loot"
   )
   @ConfigAnnotations.FieldDocumentation(
      explanation = "Blocks or items  gained when mining."
   )
   public Map<InvItem, Integer> loots = new HashMap<>();

   @Override
   public int actionDuration(MillVillager villager) {
      Block block = this.sourceBlockState.getBlock();
      if (block == Blocks.STONE || block == Blocks.SANDSTONE) {
         int toolEfficiency = (int)villager.getBestPickaxe().getDestroySpeed(new ItemStack(villager.getBestPickaxe(), 1), Blocks.SANDSTONE.defaultBlockState());
         return 140 - 4 * toolEfficiency;
      } else if (block != Blocks.SAND && block != Blocks.CLAY && block != Blocks.GRAVEL) {
         return 70;
      } else {
         int toolEfficiency = (int)villager.getBestShovel().getDestroySpeed(new ItemStack(villager.getBestShovel(), 1), Blocks.SAND.defaultBlockState());
         return 140 - 4 * toolEfficiency;
      }
   }

   @Override
   public void applyDefaultSettings() {
      this.lookAtGoal = true;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) throws Exception {
      List<Building> buildings = this.getBuildings(villager);
      List<Point> validSources = new ArrayList<>();
      List<Building> validDests = new ArrayList<>();

      for (Building possibleDest : buildings) {
         List<CopyOnWriteArrayList<Point>> sources = possibleDest.getResManager().sources;

         for (int i = 0; i < sources.size(); i++) {
            if (this.sourceBlockState == possibleDest.getResManager().sourceTypes.get(i)) {
               for (int j = 0; j < sources.get(i).size(); j++) {
                  BlockState actualBlockState = WorldUtilities.getBlockState(villager.level(), sources.get(i).get(j));
                  if (actualBlockState == this.sourceBlockState) {
                     validSources.add(sources.get(i).get(j));
                     validDests.add(possibleDest);
                  }
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
      Block targetBlock = this.sourceBlockState.getBlock();
      if (targetBlock == Blocks.SAND || targetBlock == Blocks.CLAY || targetBlock == Blocks.GRAVEL) {
         return villager.getBestShovelStack();
      } else {
         return targetBlock != Blocks.SNOW_BLOCK && targetBlock != Blocks.ICE ? villager.getBestPickaxeStack() : isUlu();
      }
   }

   @Override
   public ItemStack getIcon() {
      if (this.icon != null) {
         return this.icon.getItemStack();
      } else {
         return this.sourceBlockState != null
            ? new ItemStack(this.sourceBlockState.getBlock(), 1)
            : null;
      }
   }

   @Override
   public AStarConfig getPathingConfig(MillVillager villager) {
      return !villager.canVillagerClearLeaves() ? JPS_CONFIG_WIDE_NO_LEAVES : JPS_CONFIG_WIDE;
   }

   @Override
   public String getTypeLabel() {
      return "mining";
   }

   @Override
   public boolean isDestPossibleSpecific(MillVillager villager, Building b) {
      return true;
   }

   @Override
   public boolean isPossibleGenericGoal(MillVillager villager) throws Exception {
      return true;
   }

   /**
    * Player-like mine cycle (data-driven twin of {@code GoalMinerMineResource}), driven one tick at a time
    * (actionDuration is the 1.12 figure but the cycle re-enters every tick until COMPLETE). STATELESS — all phase
    * is derived from the WORLD/point each tick (shared SINGLETON, no per-goal mutable progress).
    *
    * <p>Cycle: ensureTool (strict pickaxe/shovel; snow/ice are ULU-mined, exempt — the held-item path already equips
    * the ULU) → {@link VillagerWorldOps#breakTick} (reach-gated, cracks, real tool-aware drops over time) → schedule
    * the source to REGROW on the point (renewable mine, as 1.12) → {@link VillagerWorldOps#pickupTick} walking to each
    * dropped ItemEntity → grant the CONFIGURED {@link #loots} as the kept 1.12 net yield (the real drops are the
    * worksite by-product; the configured loot is the economy item, exactly as 1.12 granted it).
    */
   @Override
   public boolean performAction(MillVillager villager) throws Exception {
      Point dest = villager.getGoalDestPoint();
      if (dest == null) {
         return true;
      }
      net.minecraft.core.BlockPos pos = dest.getBlockPos();
      BlockState blockState = WorldUtilities.getBlockState(villager.level(), dest);
      Block block = blockState.getBlock();
      boolean wasPresent = !blockState.isAir();

      // Tool kind: snow/ice are knife(ULU)-mined in 1.12, exempt from the strict pickaxe/shovel gate (the held-item
      // path already equips the ULU), so pass tool == null for those; the proper digging tool otherwise.
      boolean uluMined = block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.ICE;
      com.coderyo.jason.ops.VillagerWorldOps.ToolKind kind =
         uluMined ? null : com.coderyo.jason.ops.VillagerWorldOps.miningToolFor(block);

      // HARVEST via the AI-invokable facade: ensureTool (strict pickaxe/shovel; ULU exempt) → reach-gate → break the
      // source over the real player destroy-math → walk to + collect each real tool-aware drop, all in one call.
      com.coderyo.jason.ops.OpState st = com.coderyo.jason.ops.VillagerActions.harvestBlock(villager, pos, kind);

      // Schedule the source to REGROW (renewable mine, as 1.12) the moment it actually breaks — captured from the
      // state seen WHILE the block was still present, and only once (peekRegrow guard) so multi-tick pickup doesn't
      // re-schedule it. This preserves the renewable-mine net effect through the bundled break+pickup facade.
      if (wasPresent && villager.level().getBlockState(pos).isAir()
            && com.coderyo.jason.ops.TaskPointStore.get().peekRegrow(villager.level(), pos) == null) {
         com.coderyo.jason.ops.TaskPointStore.get().scheduleRegrow(
            villager.level(), pos, blockState, villager.level().getGameTime());
      }

      switch (st) {
         case APPROACHING:
         case EXTENDING_REACH:
         case IN_PROGRESS:
         case PICKING_UP:
            return false; // walking into reach / breaking / collecting the real drops — keep going.
         case BLOCKED:
            // No correct tool (defer to GoalGetTool) or genuinely unbreakable/unreachable. Stay in-goal when it's a
            // tool issue so GoalGetTool can pre-empt; the strict facade returns BLOCKED for a missing tool.
            if (MillConfigValues.LogMiner >= 2 && villager.extraLog) {
               MillLog.debug(this, "Blocked mining " + block + " at " + dest + " (" + st + "); waiting/abandoning.");
            }
            return false;
         case COMPLETE:
            // Block broken AND its real drops collected: grant the CONFIGURED 1.12 loot yield (the economy item) and
            // finish. The real drops are the worksite by-product; the configured loot is the kept 1.12 net yield.
            for (InvItem key : this.loots.keySet()) {
               villager.addToInv(key, this.loots.get(key));
               if (MillConfigValues.LogMiner >= 3 && villager.extraLog) {
                  MillLog.debug(this, "Gathered " + key + " at: " + villager.getGoalDestPoint());
               }
            }
            return true;
         default:
            return false;
      }
   }

   @Override
   public boolean stuckAction(MillVillager villager) throws Exception {
      return this.performAction(villager);
   }

   @Override
   public boolean swingArms() {
      return true;
   }

   @Override
   public boolean validateGoal() {
      return true;
   }
}
