package org.millenaire.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decorative "mock" banner block-entity used by Mill's building-import mock blocks.
 *
 * <p>In 1.12 this was an empty subclass of vanilla {@code TileEntityBanner},
 * inheriting all banner pattern/colour storage and rendering. In 26.2
 * {@code BannerBlockEntity}'s constructors hard-code {@code BlockEntityTypes.BANNER}
 * and require an {@code AbstractBannerBlock} for the base colour, so it can no
 * longer be subclassed with a distinct registered type. Mill therefore keeps a
 * minimal standalone block-entity for its own renderer ({@code TESRMockBanner}).
 *
 * <p>26.2: the banner colour/pattern storage is ported — {@link #setItemValues} derives the base
 * {@link DyeColor} from the banner item/block and the {@link BannerPatternLayers} from the item's
 * BANNER_PATTERNS component, exposed via {@link #getBaseColor()} / {@link #getPatterns()} for the
 * renderer. (The {@code TESRMockBanner} renderer itself lives in the client package.)
 */
public class TileEntityMockBanner extends BlockEntity {

	private ItemStack bannerItem = ItemStack.EMPTY;
	private DyeColor baseColor = DyeColor.WHITE;
	private BannerPatternLayers patterns = BannerPatternLayers.EMPTY;

	public TileEntityMockBanner(BlockPos pos, BlockState state) {
		super(MillBlockEntities.MOCK_BANNER, pos, state);
	}

	/**
	 * Stores the banner {@link ItemStack} this mock banner displays and derives the base
	 * {@link DyeColor} + {@link BannerPatternLayers} for the renderer ({@code TESRMockBanner}). 1.12 read
	 * the pattern/colour NBT off the item; on 26.2 the patterns live in the BANNER_PATTERNS data
	 * component and the base colour comes from the (coloured) banner item/block.
	 */
	public void setItemValues(ItemStack stack, boolean refresh) {
		this.bannerItem = stack == null ? ItemStack.EMPTY : stack;
		if (!this.bannerItem.isEmpty()) {
			if (this.bannerItem.getItem() instanceof net.minecraft.world.item.BannerItem bannerItem) {
				this.baseColor = bannerItem.getColor();
			} else if (this.bannerItem.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
				&& blockItem.getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock bannerBlock) {
				this.baseColor = bannerBlock.getColor();
			}
			this.patterns = this.bannerItem.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		}
		this.setChanged();
	}

	public ItemStack getBannerItem() {
		return this.bannerItem;
	}

	public DyeColor getBaseColor() {
		return this.baseColor;
	}

	public BannerPatternLayers getPatterns() {
		return this.patterns;
	}
}
