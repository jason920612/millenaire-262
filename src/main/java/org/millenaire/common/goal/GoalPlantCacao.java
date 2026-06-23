package org.millenaire.common.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import com.coderyo.jason.ops.OpState;
import com.coderyo.jason.ops.VillagerWorldOps;
import org.millenaire.common.config.DocumentedElement;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.Point;

@DocumentedElement.Documentation("Plant cacao seeds at home.")
public class GoalPlantCacao extends Goal {
   // 26.2: ItemStacks can't be built before registry freeze (Goal classes load during init); lazy-init.
   private static ItemStack[] cacao;

   public GoalPlantCacao() {
      this.icon = InvItem.createInvItem(Items.COCOA_BEANS);
   }

   private int getCocoaMeta(Level world, Point p) {
      Block var5 = p.getRelative(0.0, 0.0, -1.0).getBlock(world);
      Block var6 = p.getRelative(0.0, 0.0, 1.0).getBlock(world);
      Block var7 = p.getRelative(-1.0, 0.0, 0.0).getBlock(world);
      Block var8 = p.getRelative(1.0, 0.0, 0.0).getBlock(world);
      byte meta = 0;
      if (var5 == Blocks.OAK_LOG) {
         meta = 2;
      }

      if (var6 == Blocks.OAK_LOG) {
         meta = 0;
      }

      if (var7 == Blocks.OAK_LOG) {
         meta = 1;
      }

      if (var8 == Blocks.OAK_LOG) {
         meta = 3;
      }

      return meta;
   }

   @Override
   public int actionDuration(MillVillager villager) {
      // Player-like plant is driven per-tick (reach → real place): re-enter every tick (1).
      return 1;
   }

   @Override
   public Goal.GoalInformation getDestination(MillVillager villager) {
      Point p = villager.getHouse().getResManager().getCocoaPlantingLocation();
      return this.packDest(p, villager.getHouse());
   }

   @Override
   public ItemStack[] getHeldItemsTravelling(MillVillager villager) {
      return cacao != null ? cacao : (cacao = new ItemStack[]{new ItemStack(Blocks.COCOA, 1)});
   }

   @Override
   public boolean isPossibleSpecific(MillVillager villager) {
      return this.getDestination(villager).getDest() != null;
   }

   @Override
   public boolean lookAtGoal() {
      return true;
   }

   /**
    * Player-like cacao plant, driven one tick at a time (actionDuration == 1). STATELESS — phase derived from the
    * WORLD each tick, never a per-goal field (shared SINGLETON).
    *
    * <p>1.12 mechanic KEPT: 1.12 set a {@code COCOA} block facing the adjacent oak log (the {@link #getCocoaMeta}
    * facing logic, preserved exactly). Now the villager does a REAL player-like place — reach-gated, swing, place
    * sound — of the same facing cocoa. Economy: 1.12 planted for free; to make it a genuine place we CONSUME one cocoa
    * bean from stock when available (the harvest replenishes it) and FALL BACK to a free place if none, keeping 1.12's
    * "the pod always establishes" net behaviour. The facing meta is unchanged (via {@code setBlockAndMetadata}, which
    * 26.2 maps to the proper facing BlockState).
    */
   @Override
   public boolean performAction(MillVillager villager) {
      Point cropPoint = villager.getGoalDestPoint();
      if (cropPoint == null) {
         return true;
      }
      if (villager.getBlock(cropPoint) != Blocks.AIR) {
         return true; // slot occupied / already planted — done.
      }

      BlockPos pos = cropPoint.getBlockPos();
      if (!VillagerWorldOps.withinReach(villager, pos)) {
         OpState reach = VillagerWorldOps.ensureReach(villager, pos);
         if (reach == OpState.BLOCKED) {
            return true;
         }
         return false;
      }

      // Consume one bean from stock if available (real placement cost); else plant free (1.12 fidelity). Swing + the
      // 1.12 facing placement (setBlockAndMetadata preserves the oak-log-facing meta).
      villager.takeFromInv(Items.COCOA_BEANS, 0, 1);
      villager.swing(InteractionHand.MAIN_HAND);
      villager.setBlockAndMetadata(cropPoint, Blocks.COCOA, this.getCocoaMeta(villager.level(), cropPoint));
      return true;
   }

   @Override
   public int priority(MillVillager villager) {
      return 120;
   }
}
