package org.millenaire.common.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.forge.MillRegistry;

/**
 * Holder + Fabric registration for all Millénaire {@link BlockEntityType}s.
 *
 * <p>In 1.12/Forge a {@code TileEntity} had a no-arg constructor and was bound
 * to its block via {@code GameRegistry.registerTileEntity}. In 26.2 every
 * {@link net.minecraft.world.level.block.entity.BlockEntity} requires a
 * {@code BlockEntityType} created from a {@code (BlockPos, BlockState)} factory
 * and the set of blocks it is valid for, then registered into
 * {@link BuiltInRegistries#BLOCK_ENTITY_TYPE}.
 *
 * <p>Mirrors {@link MillRegistry}'s block/item registration style. Call
 * {@link #register()} from the mod initializer after {@link MillBlocks} blocks
 * are created.
 */
public final class MillBlockEntities {

	public static final List<BlockEntityType<?>> REGISTERED = new ArrayList<>();

	public static BlockEntityType<TileEntityFirePit> FIRE_PIT;
	public static BlockEntityType<TileEntityLockedChest> LOCKED_CHEST;
	public static BlockEntityType<TileEntityImportTable> IMPORT_TABLE;
	public static BlockEntityType<TileEntityPanel> PANEL;
	public static BlockEntityType<TileEntityMillBed> MILL_BED;
	public static BlockEntityType<TileEntityMockBanner> MOCK_BANNER;

	private MillBlockEntities() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> register(
			String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... validBlocks) {
		// 26.2: BlockEntityType(factory, Set<Block>); no Builder.build(null) anymore.
		BlockEntityType<T> type = new BlockEntityType(factory, java.util.Set.of(validBlocks));
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MillRegistry.id(name), type);
		REGISTERED.add(type);
		return type;
	}

	/**
	 * Registers every Millénaire block-entity type. Must run after the referenced
	 * blocks in {@link MillBlocks} have been created (their instances are needed to
	 * build the {@code validBlocks} set).
	 */
	public static void register() {
		// Each type's validBlocks come from the ported MillBlocks instances (this runs after
		// MillBlocks.register()). Beds keep a Mill block-entity purely for rendering (vanilla beds
		// dropped theirs); the mock-banner BE covers all four village/culture banner blocks.
		FIRE_PIT = register("fire_pit", TileEntityFirePit::new, MillBlocks.FIRE_PIT);
		LOCKED_CHEST = register("locked_chest", TileEntityLockedChest::new, MillBlocks.LOCKED_CHEST);
		IMPORT_TABLE = register("import_table", TileEntityImportTable::new, MillBlocks.IMPORT_TABLE);
		PANEL = register("panel", TileEntityPanel::new, MillBlocks.PANEL);
		// Beds no longer have a vanilla BlockEntity in 26.2; Mill keeps its own for rendering.
		MILL_BED = register("mill_bed", TileEntityMillBed::new, MillBlocks.BED_STRAW, MillBlocks.BED_CHARPOY);
		MOCK_BANNER = register("mock_banner", TileEntityMockBanner::new,
			MillBlocks.VILLAGE_BANNER_WALL, MillBlocks.VILLAGE_BANNER_STANDING,
			MillBlocks.CULTURE_BANNER_WALL, MillBlocks.CULTURE_BANNER_STANDING);
	}
}
