package org.millenaire.common.entity;

import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.village.Building;
import org.millenaire.common.world.MillWorldData;

/**
 * Building chest that may be locked to non-owning players.
 *
 * <p>1.12→26.2: extended {@code TileEntityLockableLoot} + {@code ISidedInventory}
 * + the Forge item-handler capability, with a hand-rolled lid animation and a
 * large-chest pairing inner class. In 26.2 it extends
 * {@link BaseContainerBlockEntity} (which is {@code Container} + {@code MenuProvider}
 * + {@code Nameable}); it additionally implements {@link WorldlyContainer}
 * (replacing {@code ISidedInventory}) exposing no sides so hoppers can't touch it.
 *
 * <p>It implements {@link LidBlockEntity} so the single chest reproduces the 1.12 lid open/close
 * animation: opener changes flip a {@link ChestLidController}'s target and the client {@link #lidAnimateTick}
 * interpolates the lid. Mill chests are always single (no large-chest pairing), so only the single-lid
 * path is needed.
 */
public class TileEntityLockedChest extends BaseContainerBlockEntity implements WorldlyContainer, LidBlockEntity {

	private static final int[] NO_SLOTS = new int[0];
	private final ChestLidController chestLidController = new ChestLidController();
	private int openCount = 0;
	private NonNullList<ItemStack> chestContents = NonNullList.withSize(27, ItemStack.EMPTY);
	public Point buildingPos = null;
	public boolean loaded = false;
	public boolean serverDevMode = false;

	public TileEntityLockedChest(BlockPos pos, BlockState state) {
		super(MillBlockEntities.LOCKED_CHEST, pos, state);
	}

	// readUpdatePacket kept (server→client content sync); StreamReadWrite/Point bridging is its own task.
	public static void readUpdatePacket(FriendlyByteBuf data, Level world) {
		Point pos = StreamReadWrite.readNullablePoint(data);
		TileEntityLockedChest te = pos.getMillChest(world);
		if (te != null) {
			try {
				te.buildingPos = StreamReadWrite.readNullablePoint(data);
				te.serverDevMode = data.readBoolean();
				byte nb = data.readByte();

				for (int i = 0; i < nb; i++) {
					ItemStack stack = StreamReadWrite.readNullableItemStack(data);
					if (stack == null) {
						MillLog.error(te, "Received a null stack!");
						stack = ItemStack.EMPTY;
					}

					te.setItem(i, stack);
				}

				te.loaded = true;
				if (Mill.clientWorld != null) {
					Building building = Mill.clientWorld.getBuilding(te.buildingPos);
					if (building != null) {
						building.invalidateInventoryCache();
					}
				}
			} catch (Exception var7) {
				MillLog.printException(te + ": Error in readUpdatePacket", var7);
			}
		}
	}

	// --- Container / BaseContainerBlockEntity ---

	@Override
	public int getContainerSize() {
		return 27;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.chestContents;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.chestContents = items;
	}

	@Override
	protected Component getDefaultName() {
		if (this.buildingPos == null) {
			return LanguageUtilities.textComponent("ui.unlockedchest");
		}
		Building building = Mill.clientWorld != null ? Mill.clientWorld.getBuilding(this.buildingPos) : null;
		if (building == null) {
			return LanguageUtilities.textComponent("ui.unlockedchest");
		}
		String s = building.getNativeBuildingName();
		return Component.literal(building.chestLocked
			? s + ": " + LanguageUtilities.string("ui.lockedchest")
			: s + ": " + LanguageUtilities.string("ui.unlockedchest"));
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		// 26.2: Mill does NOT use the vanilla openMenu/MenuProvider path for the locked chest. The GUI is
		// driven by Mill's own PACKET_OPENGUI(104) flow: ServerSender.displayMillChest → ClientReceiver
		// (guiId==5) → ClientGuiHandler builds ContainerLockedChest/GuiLockedChest client-side. So this
		// vanilla factory is intentionally unused; returning null is correct (nothing calls it).
		return null;
	}

	// --- WorldlyContainer (replaces ISidedInventory): expose nothing to automation ---

	@Override
	public int[] getSlotsForFace(Direction side) {
		return NO_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
		return false;
	}

	@Override
	public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
		return false;
	}

	// --- Mill locking logic ---

	public boolean isLockedFor(Player player) {
		if (player == null) {
			MillLog.printException("Null player", new Exception());
			return true;
		} else if (!this.loaded && this.level.isClientSide()) {
			return true;
		} else if (this.buildingPos == null) {
			return false;
		} else if (!this.level.isClientSide() && MillConfigValues.DEV) {
			return false;
		} else if (this.serverDevMode) {
			return false;
		} else {
			MillWorldData mw = Mill.getMillWorld(this.level);
			if (mw == null) {
				MillLog.printException("Null MillWorldData", new Exception());
				return true;
			} else {
				Building building = mw.getBuilding(this.buildingPos);
				return building == null || building.lockedForPlayer(player);
			}
		}
	}

	public String getInvLargeName() {
		if (this.buildingPos == null) {
			return LanguageUtilities.string("ui.largeunlockedchest");
		}
		Building building = Mill.clientWorld != null ? Mill.clientWorld.getBuilding(this.buildingPos) : null;
		if (building == null) {
			return LanguageUtilities.string("ui.largeunlockedchest");
		}
		String s = building.getNativeBuildingName();
		return building.chestLocked
			? s + ": " + LanguageUtilities.string("ui.largelockedchest")
			: s + ": " + LanguageUtilities.string("ui.largeunlockedchest");
	}

	public void sendUpdatePacket(Player player) {
		ServerSender.sendLockedChestUpdatePacket(this, player);
	}

	// --- NBT (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input); // handles lock + CustomName
		this.chestContents = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.chestContents);
		this.buildingPos = readPoint(input, "buildingPos");
		if (Mill.clientWorld != null) {
			Building building = Mill.clientWorld.getBuilding(this.buildingPos);
			if (building != null) {
				building.invalidateInventoryCache();
			}
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output); // handles lock + CustomName
		ContainerHelper.saveAllItems(output, this.chestContents);
		writePoint(output, "buildingPos", this.buildingPos);
	}

	// 26.2 Point persistence: Point.read/write target CompoundTag; ValueInput/ValueOutput expose
	// the same double get/put so we mirror Point's 3-double encoding here (null = all-zero).
	static Point readPoint(ValueInput input, String label) {
		double x = input.getDoubleOr(label + "_xCoord", 0.0);
		double y = input.getDoubleOr(label + "_yCoord", 0.0);
		double z = input.getDoubleOr(label + "_zCoord", 0.0);
		return x == 0.0 && y == 0.0 && z == 0.0 ? null : new Point(x, y, z);
	}

	static void writePoint(ValueOutput output, String label, Point p) {
		if (p != null) {
			output.putDouble(label + "_xCoord", p.x);
			output.putDouble(label + "_yCoord", p.y);
			output.putDouble(label + "_zCoord", p.z);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	// --- LidBlockEntity (single-chest open/close animation) ---

	/**
	 * Client ticker registered by {@link org.millenaire.common.block.BlockLockedChest#getTicker}.
	 * Interpolates the lid each client tick (mirrors {@code ChestBlockEntity.lidAnimateTick}).
	 */
	public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, TileEntityLockedChest be) {
		be.chestLidController.tickLid();
	}

	@Override
	public float getOpenNess(float partialTick) {
		return this.chestLidController.getOpenness(partialTick);
	}

	@Override
	public boolean triggerEvent(int id, int value) {
		// Block event id 1 carries the opener count → drives the client lid controller (as vanilla chests do).
		if (id == 1) {
			this.chestLidController.shouldBeOpen(value > 0);
			return true;
		}
		return super.triggerEvent(id, value);
	}

	@Override
	public void startOpen(ContainerUser containerUser) {
		if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
			this.openerCountChanged(this.openCount, this.openCount + 1);
			this.openCount++;
		}
	}

	@Override
	public void stopOpen(ContainerUser containerUser) {
		if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
			this.openerCountChanged(this.openCount, Math.max(this.openCount - 1, 0));
			this.openCount = Math.max(this.openCount - 1, 0);
		}
	}

	private void openerCountChanged(int previous, int current) {
		if (this.level == null) {
			return;
		}
		BlockPos pos = this.getBlockPos();
		// Lid sound on the 0↔1 transition (single chest, matches vanilla open/close sounds).
		if (previous == 0 && current > 0) {
			playLidSound(this.level, pos, SoundEvents.CHEST_OPEN);
		} else if (previous > 0 && current == 0) {
			playLidSound(this.level, pos, SoundEvents.CHEST_CLOSE);
		}

		if (previous != current) {
			if (!this.level.isClientSide()) {
				// Server: broadcast a block event so the client lid controller opens/closes.
				this.level.blockEvent(pos, this.getBlockState().getBlock(), 1, current);
			} else {
				// Client-only open (Mill's GUI flow runs client-side): drive the lid directly.
				this.chestLidController.shouldBeOpen(current > 0);
			}
		}
	}

	private static void playLidSound(Level level, BlockPos pos, net.minecraft.sounds.SoundEvent event) {
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, event,
			SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
	}
}
