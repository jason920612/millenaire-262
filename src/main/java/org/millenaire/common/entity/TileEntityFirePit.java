package org.millenaire.common.entity;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.millenaire.common.block.BlockFirePit;
import org.millenaire.common.block.MillBlocks;

/**
 * Cooking block-entity for the Mill fire pit. Three input slots (0-2), one fuel
 * slot (3), three output slots (4-6).
 *
 * <p>1.12→26.2 changes: dropped the Forge {@code ItemStackHandler}/{@code IItemHandler}
 * capability machinery — slots are now a plain {@link NonNullList} exposed through
 * {@link Container} (Fabric's transfer API can wrap this later via
 * {@code InventoryStorage} if hopper interaction is needed, see TODO). Smelting
 * now goes through {@link RecipeManager#createCheck} + {@link SingleRecipeInput},
 * fuel through {@code level.fuelValues()}, and ticking through the static
 * {@link #serverTick} hooked from a {@code BlockEntityTicker}. NBT uses
 * {@link ValueInput}/{@link ValueOutput} ({@code loadAdditional}/{@code saveAdditional}).
 */
public class TileEntityFirePit extends BlockEntity implements Container {

	private static final int SIZE = 7;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int[] cookTimes = new int[3];
	private int burnTime = 0;
	private int totalBurnTime = 0;

	private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> quickCheck =
		RecipeManager.createCheck(RecipeType.SMELTING);

	public TileEntityFirePit(BlockPos pos, BlockState state) {
		super(MillBlockEntities.FIRE_PIT, pos, state);
	}

	public static boolean isFirePitBurnable(ItemStack stack) {
		// 1.12 checked ItemFood + a furnace recipe whose result was also food. In 26.2 food is a
		// data component (DataComponents.FOOD); the recipe result is resolved lazily server-side.
		return stack.has(DataComponents.FOOD);
	}

	// --- smelting helpers (mirrors AbstractFurnaceBlockEntity) ---

	private SmeltingRecipe getRecipe(int idx, Level level) {
		ItemStack stack = this.items.get(idx);
		if (stack.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		RecipeHolder<SmeltingRecipe> holder = this.quickCheck.getRecipeFor(new SingleRecipeInput(stack), serverLevel).orElse(null);
		return holder == null ? null : holder.value();
	}

	private boolean canSmelt(int idx, Level level) {
		SmeltingRecipe recipe = this.getRecipe(idx, level);
		if (recipe == null) {
			return false;
		}
		ItemStack result = recipe.assemble(new SingleRecipeInput(this.items.get(idx)));
		if (result.isEmpty()) {
			return false;
		}
		ItemStack output = this.items.get(4 + idx);
		return output.isEmpty()
			|| ItemStack.isSameItemSameComponents(result, output)
				&& output.getCount() + result.getCount() <= output.getMaxStackSize();
	}

	public void smeltItem(int idx) {
		Level level = this.level;
		if (level != null && this.canSmelt(idx, level)) {
			ItemStack input = this.items.get(idx);
			SmeltingRecipe recipe = this.getRecipe(idx, level);
			ItemStack result = recipe.assemble(new SingleRecipeInput(input));
			ItemStack output = this.items.get(4 + idx);
			if (output.isEmpty()) {
				this.items.set(4 + idx, result.copy());
			} else {
				output.grow(result.getCount());
			}
			input.shrink(1);
		}
	}

	public void dropAll() {
		if (this.level != null) {
			net.minecraft.world.Containers.dropContents(this.level, this.getBlockPos(), this);
		}
	}

	// --- accessors used by the GUI/container ---

	public int getBurnTime() {
		return this.burnTime;
	}

	public int getCookTime(int idx) {
		return this.cookTimes[idx];
	}

	public int getTotalBurnTime() {
		return this.totalBurnTime;
	}

	public void setBurnTime(int burnTime) {
		this.burnTime = burnTime;
	}

	public void setCookTime(int idx, int cookTime) {
		this.cookTimes[idx] = cookTime;
	}

	public void setTotalBurnTime(int totalBurnTime) {
		this.totalBurnTime = totalBurnTime;
	}

	// --- ticking ---

	/** Server-side tick; hook from a {@code BlockEntityTicker} created by {@link BlockFirePit}. */
	public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, TileEntityFirePit fp) {
		boolean wasBurning = fp.burnTime > 0;
		boolean dirty = false;
		if (wasBurning) {
			fp.burnTime--;
		}

		ItemStack fuelStack = fp.items.get(3);
		for (int i = 0; i < 3; i++) {
			ItemStack inputStack = fp.items.get(i);
			if ((fp.burnTime > 0 || !fuelStack.isEmpty()) && !inputStack.isEmpty()) {
				if (fp.burnTime <= 0 && fp.canSmelt(i, level)) {
					fp.burnTime = level.fuelValues().burnDuration(fuelStack);
					fp.totalBurnTime = fp.burnTime;
					if (fp.burnTime > 0) {
						dirty = true;
						if (!fuelStack.isEmpty()) {
							fuelStack.shrink(1);
							if (fuelStack.isEmpty()) {
								// 26.2: crafting remainder replaces Forge getContainerItem
								net.minecraft.world.item.ItemStackTemplate remainder = fuelStack.getItem().getCraftingRemainder();
								fp.items.set(3, remainder != null ? remainder.create() : ItemStack.EMPTY);
								fuelStack = fp.items.get(3);
							}
						}
					}
				}

				if (fp.burnTime > 0 && fp.canSmelt(i, level)) {
					fp.cookTimes[i]++;
					if (fp.cookTimes[i] == 200) {
						fp.cookTimes[i] = 0;
						fp.smeltItem(i);
						dirty = true;
					}
				} else {
					fp.cookTimes[i] = 0;
				}
			} else if (fp.burnTime <= 0 && fp.cookTimes[i] > 0) {
				dirty = true;
				fp.cookTimes[i] = Mth.clamp(fp.cookTimes[i] - 2, 0, 200);
			}
		}

		if (wasBurning != fp.burnTime > 0) {
			dirty = true;
			BlockState newState = state;
			if (!(state.getBlock() instanceof BlockFirePit)) {
				newState = MillBlocks.FIRE_PIT.defaultBlockState();
			}
			level.setBlockAndUpdate(pos, newState.setValue(BlockFirePit.LIT, fp.burnTime > 0));
		}

		if (dirty) {
			fp.setChanged();
		}
	}

	// --- Container ---

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack s : this.items) {
			if (!s.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack result = ContainerHelper.removeItem(this.items, slot, count);
		if (!result.isEmpty()) {
			this.setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		if (stack.getCount() > this.getMaxStackSize()) {
			stack.setCount(this.getMaxStackSize());
		}
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot >= 0 && slot < 3) {
			return isFirePitBurnable(stack);
		}
		if (slot == 3) {
			return this.level != null && this.level.fuelValues().isFuel(stack);
		}
		return slot >= 4 && slot < 7; // output slots: no manual placement restriction
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	// --- NBT (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items.clear();
		// Items are stored slot-indexed by ContainerHelper.
		input.childOrEmpty("Inventory"); // legacy container tag no longer used directly
		ContainerHelper.loadAllItems(input, this.items);
		this.burnTime = input.getIntOr("BurnTime", 0);
		this.cookTimes = Arrays.copyOf(input.getIntArray("CookTime").orElse(new int[3]), 3);
		this.totalBurnTime = input.getIntOr("TotalBurnTime", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("BurnTime", this.burnTime);
		output.putIntArray("CookTime", this.cookTimes);
		output.putInt("TotalBurnTime", this.totalBurnTime);
	}

	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		return this.saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}
}
