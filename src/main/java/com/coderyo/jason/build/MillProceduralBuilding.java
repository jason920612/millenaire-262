package com.coderyo.jason.build;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import org.millenaire.common.utilities.Point;

import com.coderyo.jason.build.MillNeedsModel.BuildType;
import com.coderyo.jason.build.MillCultureStyle.RoofShape;
import com.coderyo.jason.build.MillCultureStyle.Style;

/**
 * Phase 2 (#6) PROCEDURAL BUILDING — the LAYOUT GENERATOR (functional-unit composition).
 *
 * <p>Given a building TYPE (from {@link MillNeedsModel}) + a culture {@link Style}, this composes the
 * building's functional ROOMS (the sim's {@code ROOMS} catalog: house = bedroom+hearth, workshop =
 * workroom+storage, market = stall+stall+courtyard, tower = guardroom+battlement, granary =
 * storage+storage) into a single CONNECTED footprint: rooms are laid left-to-right and EACH room is
 * connected to the previous by a DOOR — guaranteed connectivity (doors == rooms-1), exactly the
 * invariant buildsim.py asserts. It then walls the footprint, lays a floor, builds the culture-styled
 * roof (gable/hip/flat/dome), and punches windows.
 *
 * <p>The output is a {@link Plan}: an ordered list of {@link Placement}s — each a RELATIVE {@link Point}
 * + the exact {@link BlockState} + the construction material {@link Item} — that the construction system
 * ({@code com.coderyo.jason.ops.VillagerWorldOps.place}) lays one-for-one, player-like. This is the
 * BuildingPlan-equivalent the task asked for, in a format the player-like placement ops consume directly.
 */
public final class MillProceduralBuilding {

   /** Interior height of every room (floor at y=0, walls y=1..ROOM_HEIGHT, roof above). */
   public static final int ROOM_HEIGHT = 4;

   private MillProceduralBuilding() {
   }

   /** One placement: a position RELATIVE to the building origin + the state to lay + its material item. */
   public static final class Placement {
      public final Point rel;        // relative to origin (x along length, y up, z along width)
      public final BlockState state;
      public final Item material;    // the item the builder must hold/consume (block.asItem()); AIR if free

      public Placement(Point rel, BlockState state, Item material) {
         this.rel = rel;
         this.state = state;
         this.material = material;
      }
   }

   /** A single functional room cell in the footprint grid. */
   public static final class Room {
      public final String name;
      public final int cellX;   // grid column (0-based, along length)
      public final int cellZ;   // grid row (here always 0 — single row, like the sim)

      Room(String name, int cellX, int cellZ) {
         this.name = name;
         this.cellX = cellX;
         this.cellZ = cellZ;
      }
   }

   /** A door link between two adjacent room cells (the connectivity proof). */
   public static final class Door {
      public final int fromCellX;
      public final int toCellX;

      Door(int fromCellX, int toCellX) {
         this.fromCellX = fromCellX;
         this.toCellX = toCellX;
      }
   }

   /** The generated procedural building: rooms, doors, footprint size, and the ordered placements. */
   public static final class Plan {
      public final BuildType type;
      public final Style style;
      public final List<Room> rooms;
      public final List<Door> doors;
      public final List<Placement> placements;
      public final int lengthX;   // footprint extent along x (rooms * roomSize)
      public final int widthZ;    // footprint extent along z
      public final int height;

      Plan(BuildType type, Style style, List<Room> rooms, List<Door> doors,
           List<Placement> placements, int lengthX, int widthZ, int height) {
         this.type = type;
         this.style = style;
         this.rooms = rooms;
         this.doors = doors;
         this.placements = placements;
         this.lengthX = lengthX;
         this.widthZ = widthZ;
         this.height = height;
      }

      public boolean fullyConnected() {
         return doors.size() == Math.max(0, rooms.size() - 1);
      }

      public List<String> roomNames() {
         List<String> n = new ArrayList<>();
         for (Room r : rooms) {
            n.add(r.name);
         }
         return n;
      }
   }

   /** Per-room interior footprint size (a room is ROOM_SIZE x ROOM_SIZE on the floor). */
   public static final int ROOM_SIZE = 4;

   /** The room catalog per building type — identical to buildsim.py {@code ROOMS}. */
   public static String[] roomsFor(BuildType type) {
      switch (type) {
         case HOUSE:    return new String[]{"bedroom", "hearth"};
         case WORKSHOP: return new String[]{"workroom", "storage"};
         case MARKET:   return new String[]{"stall", "stall", "courtyard"};
         case TOWER:    return new String[]{"guardroom", "battlement"};
         case GRANARY:  return new String[]{"storage", "storage"};
         default:       return new String[]{"room"};
      }
   }

   /**
    * Generate the procedural building. {@code sizeBoost} (0..) adds extra rooms for bigger buildings
    * ("Big buildings = more rooms"): each boost duplicates the last room type, extending the footprint.
    */
   public static Plan generate(BuildType type, Style style, int sizeBoost) {
      String[] base = roomsFor(type);
      List<String> roomNames = new ArrayList<>();
      for (String r : base) {
         roomNames.add(r);
      }
      String last = base[base.length - 1];
      for (int i = 0; i < sizeBoost; i++) {
         roomNames.add(last);
      }

      List<Room> rooms = new ArrayList<>();
      List<Door> doors = new ArrayList<>();
      for (int i = 0; i < roomNames.size(); i++) {
         rooms.add(new Room(roomNames.get(i), i, 0));
         if (i > 0) {
            doors.add(new Door(i - 1, i));   // each room connects to the previous → guaranteed connectivity.
         }
      }

      int nRooms = rooms.size();
      // The footprint: rooms in a row sharing interior walls. Total interior length = nRooms*ROOM_SIZE,
      // plus shared walls between them; we use a contiguous box of (nRooms*ROOM_SIZE + 1) by (ROOM_SIZE + 1).
      int innerLen = nRooms * ROOM_SIZE + (nRooms - 1); // +shared wall columns
      int lengthX = innerLen + 1;   // +1 for the far wall
      int widthZ = ROOM_SIZE + 1;
      int height = (type == BuildType.TOWER) ? ROOM_HEIGHT + 3 : ROOM_HEIGHT; // towers are taller.

      List<Placement> placements = new ArrayList<>();
      Item wallMat = style.wall.getBlock().asItem();
      Item floorMat = style.floor.getBlock().asItem();
      Item accentMat = style.accent.getBlock().asItem();

      // ---- FLOOR (y=0) ----
      for (int x = 0; x <= lengthX; x++) {
         for (int z = 0; z <= widthZ; z++) {
            placements.add(new Placement(rel(x, 0, z), style.floor, floorMat));
         }
      }

      // ---- PERIMETER WALLS (y=1..height) with corner accents ----
      for (int y = 1; y <= height; y++) {
         for (int x = 0; x <= lengthX; x++) {
            for (int z = 0; z <= widthZ; z++) {
               boolean perimeter = (x == 0 || x == lengthX || z == 0 || z == widthZ);
               if (!perimeter) {
                  continue;
               }
               boolean corner = (x == 0 || x == lengthX) && (z == 0 || z == widthZ);
               BlockState st = corner ? style.accent : style.wall;
               Item mat = corner ? accentMat : wallMat;
               placements.add(new Placement(rel(x, y, z), st, mat));
            }
         }
      }

      // ---- INTERIOR PARTITION WALLS between rooms (with a DOOR per partition = connectivity) ----
      // Partition i sits at x = (i+1)*ROOM_SIZE + i  (the shared-wall column between room i and i+1).
      for (Door d : doors) {
         int partitionX = (d.toCellX) * ROOM_SIZE + d.toCellX; // column index of the shared wall
         partitionX = Math.min(partitionX, lengthX - 1);
         for (int y = 1; y <= height; y++) {
            for (int z = 1; z < widthZ; z++) {
               placements.add(new Placement(rel(partitionX, y, z), style.wall, wallMat));
            }
         }
         // Punch a door (lower+upper) in the middle of this partition → the rooms are connected.
         int doorZ = widthZ / 2;
         addDoor(placements, partitionX, doorZ);
      }

      // ---- ENTRANCE DOOR in the front wall (z=0) of the first room ----
      addDoor(placements, ROOM_SIZE / 2, 0);

      // ---- WINDOWS: glass panes punched into the side walls of each room ----
      Item glassMat = Blocks.GLASS_PANE.asItem();
      for (Room r : rooms) {
         int cx = r.cellX * ROOM_SIZE + r.cellX + ROOM_SIZE / 2;
         cx = Math.min(cx, lengthX - 1);
         int wy = 2;
         // window on z=0 and z=widthZ side walls (skip where the entrance door is).
         if (!(r.cellX == 0 && cx == ROOM_SIZE / 2)) {
            placements.add(new Placement(rel(cx, wy, 0), Blocks.GLASS_PANE.defaultBlockState(), glassMat));
         }
         placements.add(new Placement(rel(cx, wy, widthZ), Blocks.GLASS_PANE.defaultBlockState(), glassMat));
      }

      // ---- ROOF (culture-styled shape) ----
      addRoof(placements, style, lengthX, widthZ, height);

      // ---- FUNCTIONAL FURNITURE per room (hearth/storage/stall/etc.) ----
      addFurniture(placements, rooms, type);

      return new Plan(type, style, rooms, doors, placements, lengthX, widthZ, height);
   }

   private static void addDoor(List<Placement> placements, int x, int z) {
      BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
         .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
         .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
      BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
         .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
         .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
      Item doorMat = Blocks.OAK_DOOR.asItem();
      // Clear the two cells (so a wall placement there is overwritten by the door), then place both halves.
      placements.add(new Placement(rel(x, 1, z), lower, doorMat));
      placements.add(new Placement(rel(x, 2, z), upper, doorMat));
   }

   private static void addRoof(List<Placement> placements, Style style, int lengthX, int widthZ, int height) {
      int roofY = height + 1;
      Item roofMat = style.roof.getBlock().asItem();
      Item wallMat = style.wall.getBlock().asItem();
      switch (style.roofShape) {
         case FLAT: {
            // a flat slab/solid cap.
            for (int x = 0; x <= lengthX; x++) {
               for (int z = 0; z <= widthZ; z++) {
                  placements.add(new Placement(rel(x, roofY, z), style.roof, roofMat));
               }
            }
            break;
         }
         case DOME: {
            // a stepped dome: shrinking solid rings rising to a central block.
            int cx = lengthX / 2;
            int cz = widthZ / 2;
            int rings = Math.min(lengthX, widthZ) / 2 + 1;
            for (int r = 0; r < rings; r++) {
               int y = roofY + r;
               for (int x = r; x <= lengthX - r; x++) {
                  for (int z = r; z <= widthZ - r; z++) {
                     boolean edge = (x == r || x == lengthX - r || z == r || z == widthZ - r);
                     if (edge || r == rings - 1) {
                        placements.add(new Placement(rel(x, y, z), style.roof, roofMat));
                     }
                  }
               }
            }
            // gilded cupola block at the apex.
            placements.add(new Placement(rel(cx, roofY + rings, cz), style.accent, style.accent.getBlock().asItem()));
            break;
         }
         case HIP: {
            // a pyramidal hip roof: rings shrinking inward from all four sides.
            int steps = (Math.min(lengthX, widthZ) / 2) + 1;
            for (int s = 0; s <= steps; s++) {
               int y = roofY + s;
               int x0 = s, x1 = lengthX - s, z0 = s, z1 = widthZ - s;
               if (x0 > x1 || z0 > z1) {
                  break;
               }
               for (int x = x0; x <= x1; x++) {
                  for (int z = z0; z <= z1; z++) {
                     boolean edge = (x == x0 || x == x1 || z == z0 || z == z1);
                     if (edge) {
                        placements.add(new Placement(rel(x, y, z), style.roof, roofMat));
                     }
                  }
               }
            }
            break;
         }
         case GABLE:
         default: {
            // a two-slope gable along the length: each step raises the ridge and pulls the eaves inward on z.
            int steps = widthZ / 2 + 1;
            for (int s = 0; s <= steps; s++) {
               int y = roofY + s;
               int zNorth = s;
               int zSouth = widthZ - s;
               if (zNorth > zSouth) {
                  break;
               }
               for (int x = 0; x <= lengthX; x++) {
                  placements.add(new Placement(rel(x, y, zNorth), style.roof, roofMat));
                  if (zSouth != zNorth) {
                     placements.add(new Placement(rel(x, y, zSouth), style.roof, roofMat));
                  }
               }
            }
            // gable end-walls fill the triangle under the slopes.
            for (int s = 1; s < steps; s++) {
               int y = roofY + s;
               for (int z = s; z <= widthZ - s; z++) {
                  placements.add(new Placement(rel(0, y, z), style.wall, wallMat));
                  placements.add(new Placement(rel(lengthX, y, z), style.wall, wallMat));
               }
            }
            break;
         }
      }
   }

   private static void addFurniture(List<Placement> placements, List<Room> rooms, BuildType type) {
      for (Room r : rooms) {
         int cx = r.cellX * ROOM_SIZE + r.cellX + ROOM_SIZE / 2;
         int cz = ROOM_SIZE / 2;
         switch (r.name) {
            case "hearth":
               placements.add(new Placement(rel(cx, 1, cz), Blocks.FURNACE.defaultBlockState(), Blocks.FURNACE.asItem()));
               break;
            case "bedroom":
               // a crafting/utility marker rather than a multi-block bed (kept single-cube, player-placeable).
               placements.add(new Placement(rel(cx, 1, cz), Blocks.CRAFTING_TABLE.defaultBlockState(), Blocks.CRAFTING_TABLE.asItem()));
               break;
            case "storage":
               placements.add(new Placement(rel(cx, 1, cz), Blocks.CHEST.defaultBlockState(), Blocks.CHEST.asItem()));
               break;
            case "workroom":
               placements.add(new Placement(rel(cx, 1, cz), Blocks.CRAFTING_TABLE.defaultBlockState(), Blocks.CRAFTING_TABLE.asItem()));
               break;
            case "stall":
               placements.add(new Placement(rel(cx, 1, cz), Blocks.BARREL.defaultBlockState(), Blocks.BARREL.asItem()));
               break;
            case "guardroom":
               placements.add(new Placement(rel(cx, 1, cz), Blocks.CHEST.defaultBlockState(), Blocks.CHEST.asItem()));
               break;
            default:
               break;
         }
      }
   }

   private static Point rel(int x, int y, int z) {
      return new Point(x, y, z);
   }
}
