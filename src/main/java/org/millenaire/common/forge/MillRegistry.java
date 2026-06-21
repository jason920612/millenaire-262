package org.millenaire.common.forge;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Fabric registration helpers for Millénaire, replacing the Forge
 * {@code @ObjectHolder} + {@code RegistryEvent.Register} pattern.
 *
 * <p>In 1.12/Forge, blocks and items carried their own registry name
 * ({@code setRegistryName}) and were registered through the registry event,
 * then injected back into static fields via {@code @ObjectHolder}. On Fabric
 * we register directly into {@link BuiltInRegistries} and the caller assigns
 * the returned instance to its static field, e.g.
 * {@code MillBlocks.WET_BRICK = reg.block("wet_brick", new BlockWetBrick("wet_brick"));}
 */
public final class MillRegistry {

	public static final String MODID = "millenaire";

	/** Everything registered, in registration order, so creative tabs can be populated. */
	public static final List<Block> REGISTERED_BLOCKS = new ArrayList<>();
	public static final List<Item> REGISTERED_ITEMS = new ArrayList<>();

	private MillRegistry() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MODID, path);
	}

	/** ResourceKey for a block, required by BlockBehaviour.Properties.setId(...) in 26.2. */
	public static ResourceKey<Block> blockKey(String name) {
		return ResourceKey.create(Registries.BLOCK, id(name));
	}

	/** ResourceKey for an item, required by Item.Properties.setId(...) in 26.2. */
	public static ResourceKey<Item> itemKey(String name) {
		return ResourceKey.create(Registries.ITEM, id(name));
	}

	/**
	 * ResourceKey for the {@code BlockItem} of an already-registered block (same path as the block).
	 * Used by the Mill BlockItem subclasses (ItemBlockMeta/ItemHalfSlab/...) which only receive the
	 * block, mirroring the 1.12 {@code setRegistryName(block.getRegistryName())} idiom.
	 */
	public static ResourceKey<Item> itemKeyFor(Block block) {
		return ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
	}

	/** Register a block under {@code millenaire:<name>} and return it. */
	public static <T extends Block> T block(String name, T block) {
		Registry.register(BuiltInRegistries.BLOCK, id(name), block);
		REGISTERED_BLOCKS.add(block);
		return block;
	}

	/** Register an item under {@code millenaire:<name>} and return it. */
	public static <T extends Item> T item(String name, T item) {
		Registry.register(BuiltInRegistries.ITEM, id(name), item);
		REGISTERED_ITEMS.add(item);
		return item;
	}

	// --- Creative tabs (replacing the old anonymous CreativeModeTab subclasses) ---

	public static CreativeModeTab TAB_MILLENAIRE;
	public static CreativeModeTab TAB_MILLENAIRE_CONTENT_CREATOR;

	/**
	 * Builds and registers the two Millénaire creative tabs. Icons are resolved
	 * lazily so the referenced items only need to exist by the time the tab is
	 * first displayed. Call after items/blocks are registered.
	 */
	public static void registerCreativeTabs() {
		org.millenaire.common.utilities.MillLog.major(null, "Registering Millénaire creative tabs with " + REGISTERED_ITEMS.size() + " items.");
		TAB_MILLENAIRE = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			id("millenaire"),
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.millenaire"))
				.icon(() -> new net.minecraft.world.item.ItemStack(org.millenaire.common.item.MillItems.DENIER_OR))
				// 26.2: items no longer auto-populate a tab via a per-item setCreativeTab; the tab must
				// list them itself, else it shows empty. Show every registered Millénaire item/block-item.
				.displayItems((params, output) -> populateTab("millenaire", output))
				.build());

		// "Millénaire — For Content Creators" tab removed at the user's request (content-creator/dev tooling,
		// not for normal play). The items still exist and live in the main millenaire tab; re-register this
		// block to bring the second tab back.
	}

	/**
	 * Fills a Mill creative tab with every registered Mill item/block-item. Runs lazily when the tab is
	 * first built (client-side); a per-item guard means one problematic stack can't blank the whole tab,
	 * and the count is logged so we can confirm the generator actually ran client-side.
	 */
	private static void populateTab(String tabName, net.minecraft.world.item.CreativeModeTab.Output output) {
		int added = 0;
		int failed = 0;
		for (Item item : REGISTERED_ITEMS) {
			try {
				output.accept(new net.minecraft.world.item.ItemStack(item));
				added++;
			} catch (Throwable t) {
				failed++;
				if (failed <= 3) {
					org.millenaire.common.utilities.MillLog.error(null,
						"Could not add " + BuiltInRegistries.ITEM.getKey(item) + " to creative tab " + tabName + ": " + t);
				}
			}
		}
		org.millenaire.common.utilities.MillLog.major(null,
			"Populated creative tab " + tabName + ": " + added + " items added, " + failed + " failed.");
	}

	/**
	 * [MILLDEBUG] Startup registration summary. Logs the totals of registered Mill blocks/items/
	 * block-entities/entities, and flags any registered item that has no model file detectable on the
	 * classpath (best-effort). Only runs when DEBUG_MODE is on. Call after all register() steps.
	 */
	public static void logRegistrationSummary() {
		if (!org.millenaire.common.utilities.MillLog.debugOn()) {
			return;
		}

		int nBlocks = REGISTERED_BLOCKS.size();
		int nItems = REGISTERED_ITEMS.size();
		int nBlockEntities = org.millenaire.common.entity.MillBlockEntities.REGISTERED.size();
		int nEntities = org.millenaire.common.entity.MillEntities.REGISTERED.size();

		org.millenaire.common.utilities.MillLog.milldebug(
			"Registration",
			"TOTALS items=" + nItems + " blocks=" + nBlocks + " blockEntities=" + nBlockEntities + " entities=" + nEntities);

		// Best-effort model presence check: a Mill item with no item-model JSON on the classpath will
		// produce the vanilla "missing model" WARN; we additionally list the Mill ids here so they are
		// greppable as [MILLDEBUG]. (Resources are bundled, so checking the classpath is reliable.)
		int missingModels = 0;
		for (Item item : REGISTERED_ITEMS) {
			Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
			if (!MODID.equals(itemId.getNamespace())) {
				continue;
			}
			String modelPath = "/assets/" + itemId.getNamespace() + "/models/item/" + itemId.getPath() + ".json";
			if (MillRegistry.class.getResource(modelPath) == null) {
				missingModels++;
				if (missingModels <= 40) {
					org.millenaire.common.utilities.MillLog.milldebug(
						"Registration", "item with NO item-model JSON: " + itemId);
				}
			}
		}
		org.millenaire.common.utilities.MillLog.milldebug(
			"Registration", "items missing item-model JSON: " + missingModels + " of " + nItems);
	}
}
