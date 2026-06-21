package org.millenaire.client.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.millenaire.client.gui.GuiFirePit;
import org.millenaire.client.gui.GuiLockedChest;
import org.millenaire.client.gui.GuiPujas;
import org.millenaire.client.gui.GuiTrade;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.entity.TileEntityLockedChest;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.ServerGuiHandler;
import org.millenaire.common.ui.ContainerLockedChest;
import org.millenaire.common.ui.ContainerPuja;
import org.millenaire.common.ui.ContainerTrade;
import org.millenaire.common.ui.firepit.ContainerFirePit;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;

/**
 * Client-side resolver for Mill's custom integer-id GUI dispatch (the 1.12 {@code IGuiHandler}).
 *
 * <p>26.2 PORT NOTE: Mill opens its GUIs through its own packet id, not vanilla
 * {@code player.openMenu(MenuProvider)} + {@code MenuScreens}. So here the client builds the menu
 * instance directly (containerId 0 — the menu is display/sync-only, see {@link org.millenaire.common.ui.MillMenus})
 * and pairs it with the screen. The screens use the modern {@code AbstractContainerScreen}
 * {@code (menu, Inventory, Component)} super-constructor.
 *
 * <p>The locked-chest case (ID 1) builds a {@link ContainerLockedChest} directly from the
 * {@link TileEntityLockedChest} (Mill chests are always single, so no large-chest pairing is needed —
 * the 1.12 {@code BlockLockedChest.getInventory} double-chest path is intentionally not reproduced).
 */
@Environment(EnvType.CLIENT)
public class ClientGuiHandler extends ServerGuiHandler {
   public Object getClientGuiElement(int ID, Player player, Level world, int x, int y, int z) {
      return this.getClientGuiElement(ID, player, world, x, y, z, null);
   }

   public Object getClientGuiElement(int ID, Player player, Level world, int x, int y, int z, Boolean lockedOverride) {
      if (ID == 1) {
         BlockEntity te = world.getBlockEntity(new BlockPos(x, y, z));
         if (te instanceof TileEntityLockedChest chest && (!world.isClientSide() || chest.loaded)) {
            Building building = Mill.clientWorld != null ? Mill.clientWorld.getBuilding(chest.buildingPos) : null;
            boolean locked = lockedOverride != null ? lockedOverride.booleanValue() : chest.isLockedFor(player);
            ContainerLockedChest menu = new ContainerLockedChest(0, player.getInventory(), chest, player, building, locked);
            // 1.12 showed the chest's name top-left; pass its display name (building name when locked, else
            // the locked/unlocked label) instead of an empty title.
            return new GuiLockedChest(menu, player.getInventory(), chest.getName(), locked);
         }
      } else if (ID == 2) {
         Building building = Mill.clientWorld.getBuilding(new Point(x, y, z));
         if (building != null && building.getTownHall() != null) {
            ContainerTrade menu = new ContainerTrade(0, player, building);
            return new GuiTrade(menu, player.getInventory(), Component.empty(), player, building);
         }
      } else if (ID == 8) {
         long id = MillCommonUtilities.unpackLong(x, y);
         if (Mill.clientWorld.getVillagerById(id) != null) {
            ContainerTrade menu = new ContainerTrade(0, player, Mill.clientWorld.getVillagerById(id));
            return new GuiTrade(menu, player.getInventory(), Component.empty(), player, Mill.clientWorld.getVillagerById(id));
         }

         MillLog.error(player, "Failed to find merchant: " + id);
      } else if (ID == 6) {
         Building building = Mill.clientWorld.getBuilding(new Point(x, y, z));
         if (building != null && building.pujas != null) {
            ContainerPuja menu = new ContainerPuja(0, player, building);
            return new GuiPujas(menu, player.getInventory(), Component.empty(), player, building);
         }
      } else if (ID == 16) {
         BlockEntity at = world.getBlockEntity(new BlockPos(x, y, z));
         if (at instanceof TileEntityFirePit) {
            ContainerFirePit menu = new ContainerFirePit(0, player, (TileEntityFirePit)at);
            return new GuiFirePit(menu, player.getInventory(), Component.empty(), (TileEntityFirePit)at);
         }
      }

      return null;
   }
}
