package org.millenaire.common.entity;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

/**
 * A hanging wall decoration (tapestry / statue / icon / hide / carpet).
 *
 * <p>1.12→26.2: extended {@code EntityHanging} + Forge {@code IEntityAdditionalSpawnData}.
 * In 26.2 {@code HangingEntity} is abstract and entity facing lives in synched data
 * via {@code setDirection}/{@code getDirection}; subclasses must implement
 * {@link #calculateBoundingBox} and {@link #playPlacementSound}. Renames:
 * {@code updateFacingWithBoundingBox}→{@code setDirection}, {@code onValidSurface}→
 * {@code survives}, {@code facingDirection}→{@code getDirection()}, {@code hangingPosition}→
 * {@code pos}, {@code onUpdate}→{@code tick}, {@code readEntityFromNBT}/{@code writeEntityToNBT}→
 * {@code readAdditionalSaveData}/{@code addAdditionalSaveData} (ValueInput/Output),
 * {@code entityDropItem}→{@code spawnAtLocation}.
 *
 * <p>26.2 spawn sync: Forge's {@code IEntityAdditionalSpawnData} is replaced by
 * {@link SynchedEntityData} — the chosen decoration is published in {@link #DATA_ART} and resolved on
 * the client in {@link #onSyncedDataUpdated} (facing/pos are already synced by {@link HangingEntity};
 * {@code type} is recovered from the art's type). NBT still persists the same data server-side.
 */
public class EntityWallDecoration extends HangingEntity {
   public static final Identifier WALL_DECORATION = Identifier.fromNamespaceAndPath("millenaire", "walldecoration");
   public static final int NORMAN_TAPESTRY = 1;
   public static final int INDIAN_STATUE = 2;
   public static final int MAYAN_STATUE = 3;
   public static final int BYZANTINE_ICON_SMALL = 4;
   public static final int BYZANTINE_ICON_MEDIUM = 5;
   public static final int BYZANTINE_ICON_LARGE = 6;
   public static final int HIDE_HANGING = 7;
   public static final int WALL_CARPET_SMALL = 8;
   public static final int WALL_CARPET_MEDIUM = 9;
   public static final int WALL_CARPET_LARGE = 10;
   public EntityWallDecoration.EnumWallDecoration millArt;
   public int type;

   // 26.2 replaces Forge's IEntityAdditionalSpawnData: the chosen decoration (its enum ordinal + 1, 0 =
   // none) is synced to clients through SynchedEntityData so the renderer knows which art to draw.
   // Facing/pos are already synced by HangingEntity; type is recovered from millArt.type on the client.
   private static final EntityDataAccessor<Integer> DATA_ART =
      SynchedEntityData.defineId(EntityWallDecoration.class, EntityDataSerializers.INT);

   public EntityWallDecoration(EntityType<? extends EntityWallDecoration> entityType, Level level) {
      super(entityType, level);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_ART, 0);
   }

   /** Server-side: publish the chosen decoration to clients (ordinal + 1; 0 = none). */
   private void syncArt() {
      this.entityData.set(DATA_ART, this.millArt == null ? 0 : this.millArt.ordinal() + 1);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (DATA_ART.equals(key) && this.level().isClientSide()) {
         int id = this.entityData.get(DATA_ART);
         if (id > 0 && id <= EnumWallDecoration.values().length) {
            this.millArt = EnumWallDecoration.values()[id - 1];
            this.type = this.millArt.type;
            this.recalculateBoundingBox();
         }
      }
   }

   public static EntityWallDecoration createWallDecoration(Level world, Point p, int type) {
      Direction facing = guessOrientation(world, p);
      EntityWallDecoration deco = MillEntities.WALL_DECORATION.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
      if (deco != null) {
         deco.initDecoration(p.getBlockPos(), facing, type, false);
      }
      return deco;
   }

   private static Direction guessOrientation(Level world, Point p) {
      if (BlockItemUtilities.isBlockSolid(WorldUtilities.getBlock(world, p.getNorth()))) {
         return Direction.SOUTH;
      } else if (BlockItemUtilities.isBlockSolid(WorldUtilities.getBlock(world, p.getSouth()))) {
         return Direction.NORTH;
      } else if (BlockItemUtilities.isBlockSolid(WorldUtilities.getBlock(world, p.getEast()))) {
         return Direction.WEST;
      } else {
         return BlockItemUtilities.isBlockSolid(WorldUtilities.getBlock(world, p.getWest())) ? Direction.EAST : Direction.WEST;
      }
   }

   /** Picks the art motive + facing; was the body of the 1.12 (world,pos,facing,type,largest) ctor. */
   public void initDecoration(BlockPos pos, Direction facing, int type, boolean largestPossible) {
      this.type = type;
      this.pos = pos;
      ArrayList<EntityWallDecoration.EnumWallDecoration> arraylist = new ArrayList<>();
      int maxSize = 0;

      for (EntityWallDecoration.EnumWallDecoration enumart : EntityWallDecoration.EnumWallDecoration.values()) {
         if (enumart.type == type) {
            this.millArt = enumart;
            this.setDirection(facing);
            if (this.survives()) {
               if (!largestPossible && enumart.sizeX * enumart.sizeY > maxSize) {
                  arraylist.clear();
               }

               arraylist.add(enumart);
               maxSize = enumart.sizeX * enumart.sizeY;
            }
         }
      }

      if (arraylist.size() > 0) {
         this.millArt = (EntityWallDecoration.EnumWallDecoration)MillRandom.getWeightedChoice(arraylist, null);
      }

      if (MillConfigValues.LogBuildingPlan >= 1 && this.millArt != null) {
         MillLog.major(this, "Creating wall decoration: " + pos + "/" + facing + "/" + type + "/" + largestPossible
            + ". Result: " + this.millArt.title + " picked among " + arraylist.size());
      }

      this.setDirection(facing);
      this.syncArt();
   }

   public Item getDropItem() {
      switch (this.type) {
         case 1: return MillItems.TAPESTRY;
         case 2: return MillItems.INDIAN_STATUE;
         case 3: return MillItems.MAYAN_STATUE;
         case 4: return MillItems.BYZANTINE_ICON_SMALL;
         case 5: return MillItems.BYZANTINE_ICON_MEDIUM;
         case 6: return MillItems.BYZANTINE_ICON_LARGE;
         case 7: return MillItems.HIDEHANGING;
         case 8: return MillItems.WALLCARPETSMALL;
         case 9: return MillItems.WALLCARPETMEDIUM;
         case 10: return MillItems.WALLCARPETLARGE;
         default:
            throw MillCrash.fail("Entity", "EntityWallDecoration.getDropItem: unknown walldecoration type " + this.type + " at " + this.pos);
      }
   }

   public int getWidthPixels() {
      return this.millArt != null ? this.millArt.sizeX : 16;
   }

   public int getHeightPixels() {
      return this.millArt != null ? this.millArt.sizeY : 16;
   }

   @Override
   protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
      // Mirrors vanilla Painting.calculateBoundingBox, but Mill art sizes are in pixels (16 = 1 block)
      // so width/height are converted to blocks. This restores the proper centred hanging box that the
      // 1.12 EntityHanging.updateBoundingBox derived from getWidthPixels()/getHeightPixels().
      int widthBlocks = this.getWidthPixels() / 16;
      int heightBlocks = this.getHeightPixels() / 16;
      net.minecraft.world.phys.Vec3 attachedToWall = net.minecraft.world.phys.Vec3.atCenterOf(pos).relative(direction, -0.46875);
      double horizontalOffset = widthBlocks % 2 == 0 ? 0.5 : 0.0;
      double verticalOffset = heightBlocks % 2 == 0 ? 0.5 : 0.0;
      Direction left = direction.getCounterClockWise();
      net.minecraft.world.phys.Vec3 position = attachedToWall.relative(left, horizontalOffset).relative(Direction.UP, verticalOffset);
      Direction.Axis axis = direction.getAxis();
      double xSize = axis == Direction.Axis.X ? 0.0625 : widthBlocks;
      double ySize = heightBlocks;
      double zSize = axis == Direction.Axis.Z ? 0.0625 : widthBlocks;
      return AABB.ofSize(position, xSize, ySize, zSize);
   }

   @Override
   public void dropItem(net.minecraft.server.level.ServerLevel level, @org.jspecify.annotations.Nullable Entity brokenEntity) {
      if (level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS)) {
         this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
         if (brokenEntity instanceof Player player && player.hasInfiniteMaterials()) {
            return;
         }
         Item drop = this.getDropItem();
         if (drop != null) {
            this.spawnAtLocation(level, new ItemStack(drop), 0.0F);
         }
      }
   }

   @Override
   public void playPlacementSound() {
      this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
   }

   @Override
   protected void readAdditionalSaveData(ValueInput input) {
      this.type = input.getIntOr("Type", 0);
      String s = input.getStringOr("Motive", "");
      for (EntityWallDecoration.EnumWallDecoration enumart : EntityWallDecoration.EnumWallDecoration.values()) {
         if (enumart.title.equals(s)) {
            this.millArt = enumart;
         }
      }
      if (this.millArt == null) {
         this.millArt = EntityWallDecoration.EnumWallDecoration.Griffon;
      }
      if (this.type == 0) {
         this.type = 1;
      }
      super.readAdditionalSaveData(input);
      this.syncArt();
   }

   @Override
   protected void addAdditionalSaveData(ValueOutput output) {
      output.putInt("Type", this.type);
      if (this.millArt != null) {
         output.putString("Motive", this.millArt.title);
      }
      super.addAdditionalSaveData(output);
   }

   // 26.2: the 1.12 Forge IEntityAdditionalSpawnData (type + motive title + point + facing) is now
   // covered by SynchedEntityData (DATA_ART, see top of class) for the art and by HangingEntity's own
   // facing/pos sync, so the bespoke writeSpawnData/readSpawnData methods are no longer needed.

   @Override
   public String toString() {
      return "Tapestry (" + (this.millArt != null ? this.millArt.title : "?") + ") " + super.toString();
   }

   public static enum EnumWallDecoration implements MillCommonUtilities.WeightedChoice {
      Griffon("Griffon", 16, 16, 0, 0, 1),
      Oiseau("Oiseau", 16, 16, 16, 0, 1),
      CorbeauRenard("CorbeauRenard", 32, 16, 32, 0, 1),
      Serment("Serment", 80, 48, 0, 16, 1),
      MortHarold("MortHarold", 64, 48, 80, 16, 1),
      Drakar("Drakar", 96, 48, 144, 16, 1),
      MontStMichel("MontStMichel", 48, 32, 0, 64, 1),
      Bucherons("Bucherons", 48, 32, 48, 64, 1),
      Cuisine("Cuisine", 48, 32, 96, 64, 1),
      Flotte("Flotte", 240, 48, 0, 96, 1),
      Chasse("Chasse", 96, 48, 0, 144, 1),
      Siege("Siege", 256, 48, 0, 192, 1),
      Ganesh("Ganesh", 32, 48, 0, 0, 2),
      Kali("Kali", 32, 48, 32, 0, 2),
      Shiva("Shiva", 32, 48, 64, 0, 2),
      Osiyan("Osiyan", 32, 48, 96, 0, 2),
      Durga("Durga", 32, 48, 128, 0, 2),
      MayanTeal("MayanTeal", 32, 32, 0, 48, 3),
      MayanGold("MayanGold", 32, 32, 32, 48, 3),
      LargeJesus("LargeJesus", 32, 48, 0, 80, 6),
      LargeVirgin("LargeVirgin", 32, 48, 32, 80, 6),
      MediumVirgin1("MediumVirgin1", 32, 32, 0, 128, 5),
      MediumVirgin2("MediumVirgin2", 32, 32, 32, 128, 5),
      SmallJesus1("SmallJesus1", 16, 16, 0, 160, 4),
      SmallJesus2("SmallJesus2", 16, 16, 16, 160, 4),
      SmallSaint1("SmallSaint1", 16, 16, 32, 160, 4),
      SmallAngel1("SmallAngel1", 16, 16, 48, 160, 4),
      SmallVirgin1("SmallVirgin1", 16, 16, 64, 160, 4),
      SmallAngel2("SmallAngel2", 16, 16, 80, 160, 4),
      HideSmallCow("HideSmallCow", 16, 16, 0, 176, 7, 10),
      HideSmallRabbit("HideSmallRabbit", 16, 16, 16, 176, 7, 10),
      HideSmallSpider("HideSmallSpider", 16, 16, 32, 176, 7, 1),
      HideLargeCow("HideLargeCow", 32, 32, 0, 192, 7, 10),
      HideLargeBear("HideLargeBear", 32, 32, 32, 192, 7, 5),
      HideLargeZombie("HideLargeZombie", 32, 32, 64, 192, 7, 1),
      HideLargeWolf("HideLargeWolf", 32, 32, 96, 192, 7, 5),
      WallCarpet1("WallCarpet1", 16, 32, 0, 224, 8),
      WallCarpet2("WallCarpet2", 16, 32, 16, 224, 8),
      WallCarpet3("WallCarpet3", 16, 32, 32, 224, 8),
      WallCarpet4("WallCarpet4", 16, 32, 48, 224, 8),
      WallCarpet5("WallCarpet5", 16, 32, 64, 224, 8),
      WallCarpet6("WallCarpet6", 16, 32, 80, 224, 8),
      WallCarpet7("WallCarpet7", 16, 32, 96, 224, 8),
      WallCarpet8("WallCarpet8", 32, 48, 160, 176, 9),
      WallCarpet9("WallCarpet9", 32, 48, 192, 176, 9),
      WallCarpet10("WallCarpet10", 32, 48, 224, 176, 9),
      WallCarpet11("WallCarpet11", 48, 32, 112, 224, 10),
      WallCarpet12("WallCarpet12", 48, 32, 160, 224, 10),
      WallCarpet13("WallCarpet13", 48, 32, 208, 224, 10);

      public static final int maxArtTitleLength = "SkullAndRoses".length();
      public final String title;
      public final int sizeX;
      public final int sizeY;
      public final int offsetX;
      public final int offsetY;
      public final int type;
      private final int weight;

      private EnumWallDecoration(String title, int sizeX, int sizeY, int offsetX, int offsetY, int type) {
         this(title, sizeX, sizeY, offsetX, offsetY, type, 1);
      }

      private EnumWallDecoration(String title, int sizeX, int sizeY, int offsetX, int offsetY, int type, int weight) {
         this.title = title;
         this.sizeX = sizeX;
         this.sizeY = sizeY;
         this.offsetX = offsetX;
         this.offsetY = offsetY;
         this.type = type;
         this.weight = weight;
      }

      @Override
      public int getChoiceWeight(Player player) {
         return this.weight;
      }
   }
}
