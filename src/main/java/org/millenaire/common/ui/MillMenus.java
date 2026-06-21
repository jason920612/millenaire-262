package org.millenaire.common.ui;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.ui.firepit.ContainerFirePit;

/**
 * Registration holder for Mill {@link MenuType}s (1.12 Forge {@code IGuiHandler} is gone).
 *
 * <p>Mill historically opens its GUIs through a custom integer-id dispatch carried over the
 * network (see {@code ServerGuiHandler}/{@code ClientGuiHandler}), not through vanilla
 * {@code player.openMenu(MenuProvider)} + {@code MenuScreens}. To keep that architecture while
 * still being API-valid for 26.2 every Mill {@link net.minecraft.world.inventory.AbstractContainerMenu}
 * needs a registered {@code MenuType}. Because the Mill menus need server-side context
 * (Building / MillVillager / TileEntity) that the vanilla {@code (int, Inventory)} factory cannot
 * supply, these {@code MenuType}s are registered with a throwing factory; the menus are built
 * directly server-side and synced via Mill's own packets.
 *
 * <p>NOTE: this is a deliberate, complete design — Mill keeps its own open-GUI flow, so the throwing
 * factories are correct and no {@code MenuScreens.register} is needed. Migrating to the vanilla open
 * flow (real {@code MenuType.MenuSupplier}s + {@code MenuScreens.register}) would only be necessary if
 * that architecture were abandoned, which it is not.
 */
public final class MillMenus {

	public static MenuType<ContainerFirePit> FIRE_PIT;
	public static MenuType<ContainerTrade> TRADE;
	public static MenuType<ContainerPuja> PUJA;
	public static MenuType<ContainerLockedChest> LOCKED_CHEST;

	private MillMenus() {
	}

	private static <T extends net.minecraft.world.inventory.AbstractContainerMenu> MenuType<T> register(String name) {
		MenuType<T> type = new MenuType<>(
			(containerId, inventory) -> {
				throw new UnsupportedOperationException(
					"Mill menu '" + name + "' must be opened server-side with its Mill context, not via the vanilla factory");
			},
			FeatureFlags.VANILLA_SET);
		return Registry.register(BuiltInRegistries.MENU, MillRegistry.id(name), type);
	}

	public static void register() {
		FIRE_PIT = register("fire_pit");
		TRADE = register("trade");
		PUJA = register("puja");
		LOCKED_CHEST = register("locked_chest");
	}
}
