package org.millenaire.common.utilities;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.millenaire.common.entity.TileEntityFirePit;
import org.millenaire.common.entity.TileEntityImportTable;
import org.millenaire.common.entity.TileEntityLockedChest;
import org.millenaire.common.entity.TileEntityPanel;
import org.millenaire.common.pathing.atomicstryker.AStarNode;
import org.millenaire.common.pathing.atomicstryker.RegionMapper;

public class Point {
   public final double x;
   public final double y;
   public final double z;

   public static final Point read(CompoundTag nbttagcompound, String label) {
      double x = nbttagcompound.getDoubleOr(label + "_xCoord", 0.0);
      double y = nbttagcompound.getDoubleOr(label + "_yCoord", 0.0);
      double z = nbttagcompound.getDoubleOr(label + "_zCoord", 0.0);
      return x == 0.0 && y == 0.0 && z == 0.0 ? null : new Point(x, y, z);
   }

   public Point(AStarNode node) {
      this.x = node.x;
      this.y = node.y;
      this.z = node.z;
   }

   public Point(BlockPos pos) {
      this.x = pos.getX();
      this.y = pos.getY();
      this.z = pos.getZ();
   }

   public Point(double i, double j, double k) {
      this.x = i;
      this.y = j;
      this.z = k;
   }

   public Point(Entity ent) {
      this.x = ent.getX();
      this.y = ent.getY();
      this.z = ent.getZ();
   }

   public Point(Node pp) {
      this.x = pp.x;
      this.y = pp.y;
      this.z = pp.z;
   }

   public Point(String s) {
      String[] scoord = s.split("/");
      this.x = Double.parseDouble(scoord[0]);
      this.y = Double.parseDouble(scoord[1]);
      this.z = Double.parseDouble(scoord[2]);
   }

   public String approximateDistanceLongString(Point p) {
      int dist = (int)this.distanceTo(p);
      if (dist < 950) {
         return dist / 100 * 100 + " " + LanguageUtilities.string("other.metre");
      } else {
         dist = Math.round((float)(dist / 500));
         return dist % 2 == 0
            ? dist / 2 + " " + LanguageUtilities.string("other.kilometre")
            : (dist - 1) / 2 + LanguageUtilities.string("other.andhalf") + " " + LanguageUtilities.string("other.kilometre");
      }
   }

   public String approximateDistanceShortString(Point p) {
      int dist = (int)this.distanceTo(p);
      if (dist < 950) {
         return dist / 100 * 100 + "m";
      } else {
         dist /= 500;
         return dist % 2 == 0 ? dist / 2 + "km" : (dist - 1) / 2 + ".5 km";
      }
   }

   public String directionTo(Point p) {
      return this.directionTo(p, false);
   }

   public String directionTo(Point p, boolean prefixed) {
      String prefix;
      if (prefixed) {
         prefix = "other.tothe";
      } else {
         prefix = "other.";
      }

      int xdist = Mth.floor(p.x - this.x);
      int zdist = Mth.floor(p.z - this.z);
      String direction;
      if (Math.abs(xdist) > Math.abs(zdist) * 0.6 && Math.abs(xdist) < Math.abs(zdist) * 1.4
         || Math.abs(zdist) > Math.abs(xdist) * 0.6 && Math.abs(zdist) < Math.abs(xdist) * 1.4) {
         if (zdist > 0) {
            direction = prefix + "south-";
         } else {
            direction = prefix + "north-";
         }

         if (xdist > 0) {
            direction = direction + "east";
         } else {
            direction = direction + "west";
         }
      } else if (Math.abs(xdist) > Math.abs(zdist)) {
         if (xdist > 0) {
            direction = prefix + "east";
         } else {
            direction = prefix + "west";
         }
      } else if (zdist > 0) {
         direction = prefix + "south";
      } else {
         direction = prefix + "north";
      }

      return direction;
   }

   public String directionToShort(Point p) {
      int xdist = Mth.floor(p.x - this.x);
      int zdist = Mth.floor(p.z - this.z);
      String direction;
      if (Math.abs(xdist) > Math.abs(zdist) * 0.6 && Math.abs(xdist) < Math.abs(zdist) * 1.4
         || Math.abs(zdist) > Math.abs(xdist) * 0.6 && Math.abs(zdist) < Math.abs(xdist) * 1.4) {
         if (zdist > 0) {
            direction = LanguageUtilities.string("other.south_short");
         } else {
            direction = LanguageUtilities.string("other.north_short");
         }

         if (xdist > 0) {
            direction = direction + LanguageUtilities.string("other.east_short");
         } else {
            direction = direction + LanguageUtilities.string("other.west_short");
         }
      } else if (Math.abs(xdist) > Math.abs(zdist)) {
         if (xdist > 0) {
            direction = LanguageUtilities.string("other.east_short");
         } else {
            direction = LanguageUtilities.string("other.west_short");
         }
      } else if (zdist > 0) {
         direction = LanguageUtilities.string("other.south_short");
      } else {
         direction = LanguageUtilities.string("other.north_short");
      }

      return direction;
   }

   public String distanceDirectionShort(Point p) {
      return LanguageUtilities.string("other.directionshort", this.directionToShort(p), "" + (int)this.horizontalDistanceTo(p) + "m");
   }

   public double distanceTo(double px, double py, double pz) {
      double d = px - this.x;
      double d1 = py - this.y;
      double d2 = pz - this.z;
      return Math.sqrt(d * d + d1 * d1 + d2 * d2);
   }

   public double distanceTo(Entity e) {
      return this.distanceTo(e.getX(), e.getY(), e.getZ());
   }

   public double distanceTo(Point p) {
      return p == null ? -1.0 : this.distanceTo(p.x, p.y, p.z);
   }

   public double distanceToSquared(double px, double py, double pz) {
      double d = px - this.x;
      double d1 = py - this.y;
      double d2 = pz - this.z;
      return d * d + d1 * d1 + d2 * d2;
   }

   public double distanceToSquared(Entity e) {
      return this.distanceToSquared(e.getX(), e.getY(), e.getZ());
   }

   public double distanceToSquared(Node pp) {
      return this.distanceToSquared(pp.x, pp.y, pp.z);
   }

   public double distanceToSquared(Point p) {
      return this.distanceToSquared(p.x, p.y, p.z);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (!(o instanceof Point)) {
         return false;
      } else {
         Point p = (Point)o;
         return p.x == this.x && p.y == this.y && p.z == this.z;
      }
   }

   public Point getAbove() {
      return new Point(this.x, this.y + 1.0, this.z);
   }

   public Point getBelow() {
      return new Point(this.x, this.y - 1.0, this.z);
   }

   public Block getBlock(Level world) {
      return world.getBlockState(this.getBlockPos()).getBlock();
   }

   public BlockState getBlockActualState(Level world) {
      Block block = this.getBlock(world);
      BlockPos pos = this.getBlockPos();
      BlockState state = world.getBlockState(pos);
      return state;
   }

   public BlockPos getBlockPos() {
      return new BlockPos(this.getiX(), this.getiY(), this.getiZ());
   }

   public BrewingStandBlockEntity getBrewingStand(Level world) {
      BlockEntity ent = world.getBlockEntity(this.getBlockPos());
      return ent != null && ent instanceof BrewingStandBlockEntity ? (BrewingStandBlockEntity)ent : null;
   }

   public String getChunkString() {
      return this.getChunkX() + "/" + this.getChunkZ();
   }

   public int getChunkX() {
      return this.getiX() >> 4;
   }

   public int getChunkZ() {
      return this.getiZ() >> 4;
   }

   public DispenserBlockEntity getDispenser(Level world) {
      BlockEntity ent = world.getBlockEntity(this.getBlockPos());
      return ent != null && ent instanceof DispenserBlockEntity ? (DispenserBlockEntity)ent : null;
   }

   public Point getEast() {
      return new Point(this.x + 1.0, this.y, this.z);
   }

   public TileEntityFirePit getFirePit(Level world) {
      BlockEntity ent = world.getBlockEntity(this.getBlockPos());
      return ent != null && ent instanceof TileEntityFirePit ? (TileEntityFirePit)ent : null;
   }

   public FurnaceBlockEntity getFurnace(Level world) {
      BlockEntity ent = world.getBlockEntity(this.getBlockPos());
      return ent != null && ent instanceof FurnaceBlockEntity ? (FurnaceBlockEntity)ent : null;
   }

   public TileEntityImportTable getImportTable(Level world) {
      BlockEntity ent = world.getBlockEntity(this.getBlockPos());
      return ent != null && ent instanceof TileEntityImportTable ? (TileEntityImportTable)ent : null;
   }

   public IntPoint getIntPoint() {
      return new IntPoint(this);
   }

   public String getIntString() {
      return this.getiX() + "/" + this.getiY() + "/" + this.getiZ();
   }

   public int getiX() {
      return Mth.floor(this.x);
   }

   public int getiY() {
      return Mth.floor(this.y);
   }

   public int getiZ() {
      return Mth.floor(this.z);
   }

   public int getMeta(Level world) {
      // 26.2: block metadata is gone; reconstruct the legacy 1.12 meta from the BlockState so callers depending on
      // variant/orientation/age (wool colour, chest/sign facing, building export...) keep working.
      return WorldUtilities.blockStateToLegacyMeta(world.getBlockState(this.getBlockPos()));
   }

   public TileEntityLockedChest getMillChest(Level world) {
      BlockEntity ent = world.getBlockEntity(this.getBlockPos());
      return ent != null && ent instanceof TileEntityLockedChest ? (TileEntityLockedChest)ent : null;
   }

   public List<Point> getNeightbours() {
      return Arrays.asList(this.getAbove(), this.getBelow(), this.getNorth(), this.getEast(), this.getSouth(), this.getWest());
   }

   public Point getNorth() {
      return new Point(this.x, this.y, this.z - 1.0);
   }

   public RegionMapper.Point2D getP2D() {
      return new RegionMapper.Point2D(this.getiX(), this.getiZ());
   }

   public TileEntityPanel getPanel(Level world) {
      BlockEntity ent = world.getBlockEntity(this.getBlockPos());
      return ent != null && ent instanceof TileEntityPanel ? (TileEntityPanel)ent : null;
   }

   public Node getPathPoint() {
      return new Node((int)this.x, (int)this.y, (int)this.z);
   }

   public String getPathString() {
      return this.getiX() + "_" + this.getiY() + "_" + this.getiZ();
   }

   public Point getRelative(double dx, double dy, double dz) {
      return new Point(this.x + dx, this.y + dy, this.z + dz);
   }

   public SignBlockEntity getSign(Level world) {
      BlockEntity ent = world.getBlockEntity(this.getBlockPos());
      return ent != null && ent instanceof SignBlockEntity ? (SignBlockEntity)ent : null;
   }

   public Point getSouth() {
      return new Point(this.x, this.y, this.z + 1.0);
   }

   public BlockEntity getTileEntity(Level world) {
      return world.getBlockEntity(this.getBlockPos());
   }

   public Point getWest() {
      return new Point(this.x - 1.0, this.y, this.z);
   }

   @Override
   public int hashCode() {
      return (int)(this.x + ((int)this.y << 8) + ((int)this.z << 16));
   }

   public double horizontalDistanceTo(BlockPos bp) {
      return this.horizontalDistanceTo(bp.getX(), bp.getZ());
   }

   public double horizontalDistanceTo(double px, double pz) {
      double d = px - this.x;
      double d2 = pz - this.z;
      return Math.sqrt(d * d + d2 * d2);
   }

   public double horizontalDistanceTo(Entity e) {
      return this.horizontalDistanceTo(e.getX(), e.getZ());
   }

   public double horizontalDistanceTo(Node p) {
      return p == null ? 0.0 : this.horizontalDistanceTo(p.x, p.z);
   }

   public double horizontalDistanceTo(Point p) {
      return p == null ? 0.0 : this.horizontalDistanceTo(p.x, p.z);
   }

   public double horizontalDistanceToSquared(double px, double pz) {
      double d = px - this.x;
      double d2 = pz - this.z;
      return d * d + d2 * d2;
   }

   public double horizontalDistanceToSquared(Entity e) {
      return this.horizontalDistanceToSquared(e.getX(), e.getZ());
   }

   public double horizontalDistanceToSquared(Point p) {
      return this.horizontalDistanceToSquared(p.x, p.z);
   }

   public boolean isBlockPassable(Level world) {
      return !world.getBlockState(this.getBlockPos()).blocksMotion();
   }

   public boolean sameBlock(Node p) {
      return p == null ? false : this.getiX() == p.x && this.getiY() == p.y && this.getiZ() == p.z;
   }

   public boolean sameBlock(Point p) {
      return p == null ? false : this.getiX() == p.getiX() && this.getiY() == p.getiY() && this.getiZ() == p.getiZ();
   }

   public void setBlock(Level world, Block block, int meta, boolean notify, boolean sound) {
      WorldUtilities.setBlockAndMetadata(world, this, block, meta, notify, sound);
   }

   public void setBlockState(Level world, BlockState state) {
      world.setBlockAndUpdate(this.getBlockPos(), state);
   }

   public int squareRadiusDistance(Point p) {
      return (int)Math.max(Math.abs(this.x - p.x), Math.abs(this.z - p.z));
   }

   @Override
   public String toString() {
      return Math.round(this.x * 100.0) / 100L + "/" + Math.round(this.y * 100.0) / 100L + "/" + Math.round(this.z * 100.0) / 100L;
   }

   public void write(CompoundTag nbttagcompound, String label) {
      nbttagcompound.putDouble(label + "_xCoord", this.x);
      nbttagcompound.putDouble(label + "_yCoord", this.y);
      nbttagcompound.putDouble(label + "_zCoord", this.z);
   }
}
