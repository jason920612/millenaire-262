package org.millenaire.common.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.entity.VillagerConfig;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillRandom;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.world.MillWorldData;

public class VillagerRecord implements Cloneable {
   private static final double RIGHT_HANDED_CHANCE = 0.8;
   private Culture culture;
   public String fathersName = "";
   public String mothersName = "";
   public String spousesName = "";
   public String maidenName = "";
   public boolean flawedRecord = false;
   public boolean killed = false;
   public boolean raidingVillage = false;
   public boolean awayraiding = false;
   public boolean awayhired = false;
   private Point housePos;
   private Point townHallPos;
   public Point originalVillagePos;
   private long villagerId;
   public long raiderSpawn = 0L;
   public int nb;
   public int gender;
   public int size;
   public float scale = 1.0F;
   public boolean rightHanded = true;
   public HashMap<InvItem, Integer> inventory = new HashMap<>();
   public List<String> questTags = new ArrayList<>();
   public String type;
   public String firstName;
   public String familyName;
   public Identifier texture;
   private Building house;
   private Building townHall;
   private Building originalVillage;
   public MillWorldData mw;
   private long originalId = -1L;

   public static VillagerRecord createVillagerRecord(
      Culture c, String type, MillWorldData worldData, Point housePos, Point thPos, String firstName, String familyName, long villagerId, boolean mockVillager
   ) {
      if (mockVillager || !worldData.world.isClientSide() && worldData.world instanceof ServerLevel) {
         VillagerRecord villagerRecord = new VillagerRecord(worldData);
         if (type == null || type.length() == 0) {
            MillLog.error(null, "Tried creating villager of null type: " + type);
         }

         if (c.getVillagerType(type.toLowerCase()) == null) {
            for (Culture c2 : Culture.ListCultures) {
               if (c2.getVillagerType(type) != null) {
                  MillLog.error(null, "Could not find villager type " + type + " in culture " + c.key + " but could in " + c2.key + " so switching.");
                  c = c2;
               }
            }
         }

         villagerRecord.setCulture(c);
         if (c.getVillagerType(type.toLowerCase()) != null) {
            VillagerType vtype = c.getVillagerType(type.toLowerCase());
            villagerRecord.type = vtype.key;
            if (!mockVillager) {
               villagerRecord.setHousePos(housePos);
               villagerRecord.setTownHallPos(thPos);
            }

            if (familyName != null) {
               villagerRecord.familyName = familyName;
            } else {
               Set<String> namesTaken;
               if (thPos != null) {
                  namesTaken = worldData.getBuilding(thPos).getAllFamilyNames();
               } else {
                  namesTaken = new HashSet<>();
               }

               villagerRecord.familyName = vtype.getRandomFamilyName(namesTaken);
            }

            if (firstName != null) {
               villagerRecord.firstName = firstName;
            } else {
               villagerRecord.firstName = vtype.getRandomFirstName();
            }

            if (villagerId == -1L) {
               villagerRecord.setVillagerId(Math.abs(MillRandom.randomLong()));
            } else {
               villagerRecord.setVillagerId(villagerId);
            }

            villagerRecord.gender = vtype.gender;
            villagerRecord.texture = vtype.getNewTexture();
            initialisePersonalizedData(villagerRecord, vtype);
            villagerRecord.rightHanded = MillRandom.random.nextDouble() < 0.8;
            if (MillConfigValues.LogVillagerSpawn >= 1) {
               MillLog.major(villagerRecord, "Created new villager record.");
            }

            if (MillLog.debugOn()) {
               MillLog.milldebug(
                  "VillagerRecord",
                  "RECORD created id=" + villagerRecord.getVillagerId() + " type=" + villagerRecord.type
                     + " culture=" + c.key + " gender=" + villagerRecord.gender
                     + " name='" + villagerRecord.firstName + " " + villagerRecord.familyName + "'"
                     + " house=" + housePos + " townHall=" + thPos + " texture=" + villagerRecord.texture
                     + " mock=" + mockVillager
               );
            }

            if (!mockVillager) {
               worldData.registerVillagerRecord(villagerRecord, true);
            }

            return villagerRecord;
         } else {
            MillLog.error(null, "Unknown villager type: " + type + " for culture " + c);
            return null;
         }
      } else {
         MillLog.printException("Tried creating a villager record in client world: " + worldData.world, new Exception());
         return null;
      }
   }

   private static void initialisePersonalizedData(VillagerRecord villagerRecord, VillagerType vtype) {
      if (vtype.isChild) {
         villagerRecord.size = 0;
         villagerRecord.scale = villagerRecord.getType().baseScale;
      } else {
         villagerRecord.scale = villagerRecord.getType().baseScale * ((80.0F + MillRandom.randomInt(10)) / 100.0F);
      }
   }

   /** The Unicode replacement character that marks a name decoded with the wrong charset (see read()). */
   private static final char CORRUPT_NAME_MARKER = '\uFFFD';

   private static boolean isCorruptName(String name) {
      return name != null && name.indexOf(CORRUPT_NAME_MARKER) >= 0;
   }

   /**
    * Regenerate any villager name that was persisted with the U+FFFD replacement char (charset-mangled
    * before getReader was fixed). Reuses the new-villager naming path (VillagerType.getRandomFirstName /
    * getRandomFamilyName) so the save self-heals on load. No-op for valid names.
    */
   private static void repairCorruptName(VillagerRecord vr) {
      VillagerType vtype = vr.getType();
      if (vtype == null) {
         return;
      }

      if (isCorruptName(vr.firstName)) {
         String old = vr.firstName;
         vr.firstName = vtype.getRandomFirstName();
         MillLog.major(vr, "Regenerated corrupt firstName (was '" + old + "') -> '" + vr.firstName + "'");
      }

      if (isCorruptName(vr.familyName)) {
         String old = vr.familyName;
         Set<String> namesTaken;
         if (vr.getTownHallPos() != null && vr.mw.getBuilding(vr.getTownHallPos()) != null) {
            namesTaken = vr.mw.getBuilding(vr.getTownHallPos()).getAllFamilyNames();
         } else {
            namesTaken = new HashSet<>();
         }

         vr.familyName = vtype.getRandomFamilyName(namesTaken);
         MillLog.major(vr, "Regenerated corrupt familyName (was '" + old + "') -> '" + vr.familyName + "'");
      }
   }

   public static VillagerRecord read(MillWorldData mw, CompoundTag nbttagcompound, String label) {
      if (!nbttagcompound.contains(label + "_id") && !nbttagcompound.contains(label + "_lid")) {
         return null;
      } else {
         VillagerRecord vr = new VillagerRecord(mw, Culture.getCultureByName(nbttagcompound.getStringOr(label + "_culture", "")));
         if (nbttagcompound.contains(label + "_lid")) {
            vr.setVillagerId(Math.abs(nbttagcompound.getLongOr(label + "_lid", 0L)));
         }

         vr.nb = nbttagcompound.getIntOr(label + "_nb", 0);
         vr.gender = nbttagcompound.getIntOr(label + "_gender", 0);
         vr.type = nbttagcompound.getStringOr(label + "_type", "").toLowerCase();
         vr.raiderSpawn = nbttagcompound.getLongOr(label + "_raiderSpawn", 0L);
         vr.firstName = nbttagcompound.getStringOr(label + "_firstName", "");
         vr.familyName = nbttagcompound.getStringOr(label + "_familyName", "");
         String texture = nbttagcompound.getStringOr(label + "_texture", "");
         if (texture.contains(":")) {
            vr.texture = Identifier.parse(texture);
         } else {
            vr.texture = Identifier.fromNamespaceAndPath("millenaire", texture);
         }

         vr.setHousePos(Point.read(nbttagcompound, label + "_housePos"));
         vr.setTownHallPos(Point.read(nbttagcompound, label + "_townHallPos"));
         vr.originalId = nbttagcompound.getLongOr(label + "_originalId", 0L);
         vr.originalVillagePos = Point.read(nbttagcompound, label + "_originalVillagePos");
         vr.size = nbttagcompound.getIntOr(label + "_size", 0);
         vr.scale = nbttagcompound.getFloatOr(label + "_scale", 0.0F);
         if (nbttagcompound.contains(label + "_rightHanded")) {
            vr.rightHanded = nbttagcompound.getBooleanOr(label + "_rightHanded", false);
         }

         vr.fathersName = nbttagcompound.getStringOr(label + "_fathersName", "");
         vr.mothersName = nbttagcompound.getStringOr(label + "_mothersName", "");
         vr.maidenName = nbttagcompound.getStringOr(label + "_maidenName", "");
         vr.spousesName = nbttagcompound.getStringOr(label + "_spousesName", "");
         vr.killed = nbttagcompound.getBooleanOr(label + "_killed", false);
         vr.raidingVillage = nbttagcompound.getBooleanOr(label + "_raidingVillage", false);
         vr.awayraiding = nbttagcompound.getBooleanOr(label + "_awayraiding", false);
         vr.awayhired = nbttagcompound.getBooleanOr(label + "_awayhired", false);
         ListTag nbttaglist = nbttagcompound.getListOrEmpty(label + "questTags");

         for (int i = 0; i < nbttaglist.size(); i++) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(i);
            vr.questTags.add(nbttagcompound1.getStringOr("tag", ""));
         }

         nbttaglist = nbttagcompound.getListOrEmpty(label + "_inventory");

         for (int i = 0; i < nbttaglist.size(); i++) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompoundOrEmpty(i);
            vr.inventory
               .put(
                  InvItem.createInvItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(nbttagcompound1.getIntOr("item", 0)), nbttagcompound1.getIntOr("meta", 0)),
                  nbttagcompound1.getIntOr("amount", 0)
               );
         }

         nbttaglist = nbttagcompound.getListOrEmpty(label + "_inventoryNew");
         MillCommonUtilities.readInventory(nbttaglist, vr.inventory);
         if (vr.getType() == null) {
            MillLog.error(vr, "Could not find type " + vr.type + " for VR. Skipping.");
            return null;
         } else {
            if (vr.scale == 0.0F || vr.scale == 1.0F) {
               initialisePersonalizedData(vr, vr.getType());
            }

            // 26.2 PORT FIX: pre-fix saves persisted names that getReader had decoded as UTF-8 from a
            // Windows-1252 source, turning bytes like 0xFC ('ü') into the U+FFFD replacement char. The
            // original bytes are gone, so the stored name is unrecoverable -> regenerate from the culture's
            // name lists (the same path new villagers use) so the save self-heals on load. Only triggers on
            // names actually containing U+FFFD, never on valid names.
            repairCorruptName(vr);

            return vr;
         }
      }
   }

   public VillagerRecord(MillWorldData mw) {
      this.mw = mw;
   }

   private VillagerRecord(MillWorldData mw, Culture c) {
      this.setCulture(c);
      this.mw = mw;
   }

   public VillagerRecord(MillWorldData mw, MillVillager v) {
      this.mw = mw;
      this.setCulture(v.getCulture());
      this.setVillagerId(v.getVillagerId());
      if (v.vtype != null) {
         this.type = v.vtype.key;
      }

      this.firstName = v.firstName;
      this.familyName = v.familyName;
      this.gender = v.gender;
      this.nb = 1;
      this.texture = v.getTexture();
      this.setHousePos(v.housePoint);
      this.setTownHallPos(v.townHallPoint);
      this.raidingVillage = v.isRaider;

      for (InvItem iv : v.getInventoryKeys()) {
         this.inventory.put(iv, v.countInv(iv));
      }

      if (this.getHousePos() == null) {
         MillLog.error(this, "Creation constructor: House position in record is null.");
         this.flawedRecord = true;
      }
   }

   public VillagerRecord clone() {
      try {
         return (VillagerRecord)super.clone();
      } catch (CloneNotSupportedException var2) {
         MillLog.printException(var2);
         return null;
      }
   }

   public int countInv(InvItem invItem) {
      return this.inventory.containsKey(invItem) ? this.inventory.get(invItem) : 0;
   }

   public int countInv(Item item) {
      return this.countInv(item, 0);
   }

   public int countInv(Item item, int meta) {
      InvItem key = InvItem.createInvItem(item, meta);
      return this.countInv(key);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (!(obj instanceof VillagerRecord)) {
         return false;
      } else {
         VillagerRecord other = (VillagerRecord)obj;
         return other.getVillagerId() == this.getVillagerId();
      }
   }

   public VillagerRecord generateRaidRecord(Building target) {
      VillagerRecord raidRecord = this.clone();
      raidRecord.setVillagerId(Math.abs(MillRandom.randomLong()));
      raidRecord.setHousePos(target.getPos());
      raidRecord.setTownHallPos(target.getTownHall().getPos());
      raidRecord.townHall = target.getTownHall();
      raidRecord.house = target;
      raidRecord.raidingVillage = true;
      raidRecord.awayraiding = false;
      raidRecord.originalVillagePos = this.getTownHall().getPos();
      raidRecord.originalId = this.getVillagerId();
      raidRecord.raiderSpawn = this.getTownHall().world.getOverworldClockTime();
      return raidRecord;
   }

   public InvItem getArmourPiece(EquipmentSlot slotIn) {
      if (slotIn == EquipmentSlot.HEAD) {
         for (InvItem item : this.getConfig().armoursHelmetSorted) {
            if (this.countInv(item) > 0) {
               return item;
            }
         }

         return null;
      } else if (slotIn == EquipmentSlot.CHEST) {
         for (InvItem itemx : this.getConfig().armoursChestplateSorted) {
            if (this.countInv(itemx) > 0) {
               return itemx;
            }
         }

         return null;
      } else if (slotIn == EquipmentSlot.LEGS) {
         for (InvItem itemxx : this.getConfig().armoursLeggingsSorted) {
            if (this.countInv(itemxx) > 0) {
               return itemxx;
            }
         }

         return null;
      } else if (slotIn == EquipmentSlot.FEET) {
         for (InvItem itemxxx : this.getConfig().armoursBootsSorted) {
            if (this.countInv(itemxxx) > 0) {
               return itemxxx;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public Item getBestMeleeWeapon() {
      double max = 1.0;
      Item best = null;

      for (InvItem item : this.inventory.keySet()) {
         if (this.inventory.get(item) > 0) {
            if (item.getItem() == null) {
               MillLog.error(this, "Attempting to check null melee weapon with id: " + this.inventory.get(item));
            } else if (MillCommonUtilities.getItemWeaponDamage(item.getItem()) > max) {
               max = MillCommonUtilities.getItemWeaponDamage(item.getItem());
               best = item.getItem();
            }
         }
      }

      if (this.getType() != null
         && this.getType().startingWeapon != null
         && MillCommonUtilities.getItemWeaponDamage(this.getType().startingWeapon.getItem()) > max) {
         max = MillCommonUtilities.getItemWeaponDamage(this.getType().startingWeapon.getItem());
         best = this.getType().startingWeapon.getItem();
      }

      return best;
   }

   public VillagerConfig getConfig() {
      return this.getType().villagerConfig;
   }

   public Culture getCulture() {
      return this.culture;
   }

   public String getGameOccupation() {
      if (this.getCulture() != null && this.getCulture().getVillagerType(this.type) != null) {
         String s = this.getCulture().getVillagerType(this.type).name;
         if (this.getCulture().canReadVillagerNames()) {
            String game = this.getCulture().getCultureString("villager." + this.getNameKey());
            if (!game.equals("")) {
               s = s + " (" + game + ")";
            }
         }

         return s;
      } else {
         return "";
      }
   }

   public Building getHouse() {
      if (this.house != null) {
         return this.house;
      } else {
         if (MillConfigValues.LogVillager >= 3) {
            MillLog.debug(this, "Seeking uncached house");
         }

         this.house = this.mw.getBuilding(this.getHousePos());
         return this.house;
      }
   }

   public Point getHousePos() {
      return this.housePos;
   }

   public int getMaxHealth() {
      if (this.getType() == null) {
         return 20;
      } else {
         return this.getType().isChild ? 10 + this.size / 2 : this.getType().health;
      }
   }

   public int getMilitaryStrength() {
      int strength = this.getMaxHealth() / 2;
      int attack = this.getType().baseAttackStrength;
      Item bestMelee = this.getBestMeleeWeapon();
      if (bestMelee != null) {
         attack = (int)(attack + MillCommonUtilities.getItemWeaponDamage(bestMelee));
      }

      strength += attack * 2;
      if (this.getType().isArcher && this.countInv(Items.BOW) > 0 || this.countInv(MillItems.YUMI_BOW) > 0) {
         strength += 10;
      }

      return strength + this.getTotalArmorValue() * 2;
   }

   public String getName() {
      return this.firstName + " " + this.familyName;
   }

   public String getNameKey() {
      return this.getType().isChild && this.size == 20 ? this.getType().altkey : this.getType().key;
   }

   public String getNativeOccupationName() {
      return this.getType().isChild && this.size == 20 ? this.getType().altname : this.getType().name;
   }

   public long getOriginalId() {
      return this.originalId;
   }

   public Building getOriginalVillage() {
      if (this.originalVillage != null) {
         return this.originalVillage;
      } else {
         if (MillConfigValues.LogVillager >= 3) {
            MillLog.debug(this, "Seeking uncached originalVillage");
         }

         this.originalVillage = this.mw.getBuilding(this.originalVillagePos);
         return this.originalVillage;
      }
   }

   public int getTotalArmorValue() {
      int total = 0;

      for (EquipmentSlot slot : new EquipmentSlot[]{
         EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
      }) {
         InvItem armour = this.getArmourPiece(slot);
         if (armour != null && armour.getItem() != null) {
            net.minecraft.world.item.component.ItemAttributeModifiers modifiers = armour.getItem()
               .components()
               .get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
            if (modifiers != null) {
               total += (int)modifiers.compute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, 0.0, slot);
            }
         }
      }

      return total;
   }

   public Building getTownHall() {
      if (this.townHall != null) {
         return this.townHall;
      } else {
         if (MillConfigValues.LogVillager >= 3) {
            MillLog.debug(this, "Seeking uncached townHall");
         }

         this.townHall = this.mw.getBuilding(this.getTownHallPos());
         return this.townHall;
      }
   }

   public Point getTownHallPos() {
      return this.townHallPos;
   }

   public VillagerType getType() {
      if (this.getCulture().getVillagerType(this.type) == null) {
         for (Culture c : Culture.ListCultures) {
            if (c.getVillagerType(this.type) != null) {
               MillLog.error(
                  this, "Could not find villager type " + this.type + " in culture " + this.getCulture().key + " but could in " + c.key + " so switching."
               );
               this.setCulture(c);
            }
         }
      }

      return this.getCulture().getVillagerType(this.type);
   }

   public long getVillagerId() {
      return this.villagerId;
   }

   @Override
   public int hashCode() {
      return Long.valueOf(this.getVillagerId()).hashCode();
   }

   public boolean isTextureValid(String texture) {
      return this.getType() != null ? this.getType().isTextureValid(texture) : true;
   }

   public boolean matches(MillVillager v) {
      return this.getVillagerId() == v.getVillagerId();
   }

   public void setCulture(Culture culture) {
      this.culture = culture;
   }

   public void setHousePos(Point housePos) {
      this.housePos = housePos;
      this.house = null;
   }

   public void setTownHallPos(Point townHallPos) {
      this.townHallPos = townHallPos;
      this.townHall = null;
   }

   public void setVillagerId(long id) {
      this.villagerId = id;
   }

   @Override
   public String toString() {
      return this.firstName + " " + this.familyName + "/" + this.type + "/" + this.getVillagerId();
   }

   public void updateRecord(MillVillager v) {
      if (v.vtype != null) {
         this.type = v.vtype.key;
      }

      this.firstName = v.firstName;
      this.familyName = v.familyName;
      this.gender = v.gender;
      this.nb = 1;
      this.texture = v.getTexture();
      this.setHousePos(v.housePoint);
      this.setTownHallPos(v.townHallPoint);
      this.raidingVillage = v.isRaider;
      this.killed = v.isReallyDead();
      if (this.getHousePos() == null) {
         MillLog.error(this, "updateRecord(): House position in record is null.");
         this.flawedRecord = true;
      }

      this.inventory.clear();

      for (InvItem iv : v.getInventoryKeys()) {
         this.inventory.put(iv, v.countInv(iv));
      }
   }

   public void write(CompoundTag nbttagcompound, String label) {
      nbttagcompound.putLong(label + "_lid", this.getVillagerId());
      nbttagcompound.putInt(label + "_nb", this.nb);
      nbttagcompound.putString(label + "_type", this.type);
      nbttagcompound.putString(label + "_firstName", this.firstName);
      nbttagcompound.putString(label + "_familyName", this.familyName);
      if (this.fathersName != null && this.fathersName.length() > 0) {
         nbttagcompound.putString(label + "_fathersName", this.fathersName);
      }

      if (this.mothersName != null && this.mothersName.length() > 0) {
         nbttagcompound.putString(label + "_mothersName", this.mothersName);
      }

      if (this.maidenName != null && this.maidenName.length() > 0) {
         nbttagcompound.putString(label + "_maidenName", this.maidenName);
      }

      if (this.spousesName != null && this.spousesName.length() > 0) {
         nbttagcompound.putString(label + "_spousesName", this.spousesName);
      }

      nbttagcompound.putInt(label + "_gender", this.gender);
      nbttagcompound.putString(label + "_texture", this.texture.toString());
      nbttagcompound.putBoolean(label + "_killed", this.killed);
      nbttagcompound.putBoolean(label + "_raidingVillage", this.raidingVillage);
      nbttagcompound.putBoolean(label + "_awayraiding", this.awayraiding);
      nbttagcompound.putBoolean(label + "_awayhired", this.awayhired);
      nbttagcompound.putLong(label + "_raiderSpawn", this.raiderSpawn);
      if (this.getHousePos() != null) {
         this.getHousePos().write(nbttagcompound, label + "_housePos");
      }

      if (this.getTownHallPos() != null) {
         this.getTownHallPos().write(nbttagcompound, label + "_townHallPos");
      }

      nbttagcompound.putLong(label + "_originalId", this.originalId);
      if (this.originalVillagePos != null) {
         this.originalVillagePos.write(nbttagcompound, label + "_originalVillagePos");
      }

      nbttagcompound.putInt(label + "_size", this.size);
      nbttagcompound.putFloat(label + "_scale", this.scale);
      nbttagcompound.putBoolean(label + "_rightHanded", this.rightHanded);
      ListTag nbttaglist = new ListTag();

      for (String tag : this.questTags) {
         CompoundTag nbttagcompound1 = new CompoundTag();
         nbttagcompound1.putString("tag", tag);
         nbttaglist.add(nbttagcompound1);
      }

      nbttagcompound.put(label + "questTags", nbttaglist);
      nbttaglist = MillCommonUtilities.writeInventory(this.inventory);
      nbttagcompound.put(label + "_inventoryNew", nbttaglist);
      nbttagcompound.putString(label + "_culture", this.getCulture().key);
   }
}
