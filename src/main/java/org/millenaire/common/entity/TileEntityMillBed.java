package org.millenaire.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mill's custom bed block-entity.
 *
 * <p>In 1.12 this extended {@code TileEntityBed} (a vanilla block entity that
 * carried the bed colour and was rendered by a TESR). In 26.2 beds no longer
 * have a vanilla {@code BlockEntity} at all — bed colour is encoded in the block
 * itself — so there is no {@code BedBlockEntity} to extend. Mill keeps a minimal
 * standalone block entity purely so its custom bed renderer can hook onto it.
 *
 * <p>NOTE: the 1.12 class was likewise effectively empty (the bed renders from its block model / JSON),
 * so this minimal marker block-entity is the faithful equivalent and needs no per-bed render state. The
 * matching renderer is intentionally a no-op. If a future custom bed renderer needed state (colour /
 * facing) it would be stored here, but none is required today.
 */
public class TileEntityMillBed extends BlockEntity {

	private ItemStack bedItem = ItemStack.EMPTY;

	public TileEntityMillBed(BlockPos pos, BlockState state) {
		super(MillBlockEntities.MILL_BED, pos, state);
	}

	/** Stores the bed {@link ItemStack} (design/colour) for the Mill bed renderer. */
	public void setItemValues(ItemStack stack) {
		this.bedItem = stack == null ? ItemStack.EMPTY : stack;
		this.setChanged();
	}

	public ItemStack getBedItem() {
		return this.bedItem;
	}
}
