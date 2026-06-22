package org.millenaire.common.entity;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.block.BlockFruitLeaves;
import org.millenaire.common.block.BlockMillCrops;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.culture.Culture;
import org.millenaire.common.culture.CultureLanguage;
import org.millenaire.common.culture.VillagerType;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.item.ItemClothes;
import org.millenaire.common.item.ItemMillenaireBow;
import org.millenaire.common.item.TradeGood;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.network.StreamReadWrite;
import org.millenaire.common.pathing.atomicstryker.AS_PathEntity;
import org.millenaire.common.pathing.atomicstryker.AStarConfig;
import org.millenaire.common.pathing.atomicstryker.AStarNode;
import org.millenaire.common.pathing.atomicstryker.AStarPathPlannerJPS;
import org.millenaire.common.pathing.atomicstryker.AStarStatic;
import org.millenaire.common.pathing.atomicstryker.IAStarPathedEntity;
import org.millenaire.common.quest.QuestInstance;
import org.millenaire.common.utilities.BlockItemUtilities;
import org.millenaire.common.utilities.BlockStateUtilities;
import org.millenaire.common.utilities.DevModUtilities;
import org.millenaire.common.utilities.MillCommonUtilities;
import org.millenaire.common.utilities.MillCrash;
import org.millenaire.common.utilities.MillLog;
import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.ThreadSafeUtilities;
import org.millenaire.common.utilities.VillageUtilities;
import org.millenaire.common.utilities.WorldUtilities;
import org.millenaire.common.village.Building;
import org.millenaire.common.village.BuildingLocation;
import org.millenaire.common.village.ConstructionIP;
import org.millenaire.common.village.VillagerRecord;
import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.UserProfile;

// ARCHITECTURAL: MillVillager is the mod's core AI entity (~3500 lines), built on the goal
// (org.millenaire.common.goal.*), village (Building/VillagerRecord/...), atomicstryker A* pathing and
// networking subsystems — all of which are now ported, so this class and its dependencies compile.
// The class structure is 26.2-valid (PathfinderMob constructor, attribute supplier, ValueInput/Output
// NBT, tick hooks). The Forge→Fabric structural mappings applied here are recorded below for reference.
//
// Forge→Fabric notes already applied at the structural level:
//  * IEntityAdditionalSpawnData (Forge custom-spawn-data) has no Fabric equivalent — dropped from the
//    `implements` list; writeSpawnData/readSpawnData remain as helpers to be re-wired onto a Fabric
//    custom spawn payload (see ServerSender). IAStarPathedEntity is a Mill interface (still un-ported).
//  * Construction by name (EntityList.createEntityByIDFromName) → must go through a registered
//    EntityType.create(level) keyed by VillagerType; stubbed where used.
//  * applyEntityAttributes()/SharedMonsterAttributes → static createAttributes()/Attributes + an
//    AttributeSupplier registered via FabricDefaultAttributeRegistry (see MillEntities).
//  * getEntityAttribute(X).setBaseValue(v) → getAttribute(X).setBaseValue(v) (Attributes.*).
//  * defineSynchedData / EntityDataAccessor, readEntityFromNBT/writeEntityToNBT →
//    readAdditionalSaveData/addAdditionalSaveData(ValueInput/Output), onUpdate/onLivingUpdate →
//    tick/aiStep — to be finished alongside the goal system.
public abstract class MillVillager extends PathfinderMob implements IAStarPathedEntity {
   // 26.2: AttributeModifier is keyed by an Identifier (not a UUID+name) and uses an Operation enum.
   private static final Identifier SPRINT_SPEED_BOOST_ID = Identifier.fromNamespaceAndPath("millenaire", "sprint_speed_boost");
   private static final AttributeModifier SPRINT_SPEED_BOOST =
      new AttributeModifier(SPRINT_SPEED_BOOST_ID, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
   private static final double DEFAULT_MOVE_SPEED = 0.5;
   public static final int ATTACK_RANGE_DEFENSIVE = 20;
   private static final String FREE_CLOTHES = "free";
   private static final String NATURAL = "natural";
   private static final int CONCEPTION_CHANCE = 2;
   private static final int VISITOR_NB_NIGHTS_BEFORE_LEAVING = 5;
   public static final int MALE = 1;
   public static final int FEMALE = 2;
   // 26.2 Identifier paths must be [a-z0-9/._-]; the 1.12 camelCase names ("GenericVillager" …) are
   // now lowercased (these are the legacy villager-type name keys; actual EntityTypes live in MillEntities).
   public static final Identifier GENERIC_VILLAGER = Identifier.fromNamespaceAndPath("millenaire", "genericvillager");
   public static final Identifier GENERIC_ASYMM_FEMALE = Identifier.fromNamespaceAndPath("millenaire", "genericasimmfemale");
   public static final Identifier GENERIC_SYMM_FEMALE = Identifier.fromNamespaceAndPath("millenaire", "genericsimmfemale");
   public static final Identifier GENERIC_ZOMBIE = Identifier.fromNamespaceAndPath("millenaire", "genericzombie");
   // 26.2: ItemStack construction requires item registry components to be bound, which only happens
   // after registry freeze. These were eager static initializers, but MillVillager.<clinit> now runs
   // during MillEntities.register() (mod init, before freeze) → "Components not bound yet". Build them
   // lazily on first use instead.
   private static ItemStack[] WOODDEN_HOE_STACK;
   private static ItemStack[] WOODDEN_SHOVEL_STACK;
   private static ItemStack[] WOODDEN_PICKAXE_STACK;
   private static ItemStack[] WOODDEN_AXE_STACK;

   private static ItemStack[] woodenHoeStack() {
      if (WOODDEN_HOE_STACK == null) {
         WOODDEN_HOE_STACK = new ItemStack[]{new ItemStack(Items.WOODEN_HOE, 1)};
      }
      return WOODDEN_HOE_STACK;
   }

   private static ItemStack[] woodenShovelStack() {
      if (WOODDEN_SHOVEL_STACK == null) {
         WOODDEN_SHOVEL_STACK = new ItemStack[]{new ItemStack(Items.WOODEN_SHOVEL, 1)};
      }
      return WOODDEN_SHOVEL_STACK;
   }

   private static ItemStack[] woodenPickaxeStack() {
      if (WOODDEN_PICKAXE_STACK == null) {
         WOODDEN_PICKAXE_STACK = new ItemStack[]{new ItemStack(Items.WOODEN_PICKAXE, 1)};
      }
      return WOODDEN_PICKAXE_STACK;
   }

   private static ItemStack[] woodenAxeStack() {
      if (WOODDEN_AXE_STACK == null) {
         WOODDEN_AXE_STACK = new ItemStack[]{new ItemStack(Items.WOODEN_AXE, 1)};
      }
      return WOODDEN_AXE_STACK;
   }
   static final int GATHER_RANGE = 20;
   private static final int HOLD_DURATION = 20;
   public static final int ATTACK_RANGE = 80;
   public static final int ARCHER_RANGE = 20;
   public static final int MAX_CHILD_SIZE = 20;
   private static final AStarConfig JPS_CONFIG_DEFAULT = new AStarConfig(true, false, false, false, true);
   private static final AStarConfig JPS_CONFIG_NO_LEAVES = new AStarConfig(true, false, false, false, false);
   public VillagerType vtype;
   public int action = 0;
   public String goalKey = null;
   private Goal.GoalInformation goalInformation = null;
   private Point pathDestPoint;
   private Building house = null;
   private Building townHall = null;
   public Point housePoint = null;
   public Point prevPoint = null;
   public Point townHallPoint = null;
   public boolean extraLog = false;
   public String firstName = "";
   public String familyName = "";
   public ItemStack heldItem = ItemStack.EMPTY;
   public ItemStack heldItemOffHand = ItemStack.EMPTY;
   public long timer = 0L;
   public long actionStart = 0L;
   public boolean allowRandomMoves = false;
   public boolean stopMoving = false;
   public int gender = 0;
   public boolean registered = false;
   public int longDistanceStuck;
   public boolean nightActionPerformed = false;
   public long speech_started = 0L;
   public HashMap<InvItem, Integer> inventory;
   public Block previousBlock;
   public int previousBlockMeta;
   public long pathingTime;
   public long timeSinceLastPathingTimeDisplay;
   private long villagerId = -1L;
   // 1.12 sent the villager id (and the rest of the villager stream data) via Forge
   // IEntityAdditionalSpawnData, so the client had it the instant the entity spawned. 26.2 dropped
   // that, so the id is synced via SynchedEntityData here: without it a freshly tracked client villager
   // has id=-1, the periodic villager-data packet (which finds its villager by id) can't match it, and
   // the villager renders with no type/texture (default skin). Synced id -> packet resolves -> textures.
   private static final net.minecraft.network.syncher.EntityDataAccessor<Long> DATA_VILLAGER_ID =
      net.minecraft.network.syncher.SynchedEntityData.defineId(MillVillager.class, net.minecraft.network.syncher.EntityDataSerializers.LONG);
   public int nbPathsCalculated = 0;
   public int nbPathNoStart = 0;
   public int nbPathNoEnd = 0;
   public int nbPathAborted = 0;
   public int nbPathFailure = 0;
   public long goalStarted = 0L;
   public int constructionJobId = -1;
   public int heldItemCount = 0;
   public int heldItemId = -1;
   public int heldItemOffHandId = -1;
   public String speech_key = null;
   public int speech_variant = 0;
   public String dialogueKey = null;
   public int dialogueRole = 0;
   public long dialogueStart = 0L;
   public char dialogueColour = 'f';
   public boolean dialogueChat = false;
   public String dialogueTargetFirstName = null;
   public String dialogueTargetLastName = null;
   private Point doorToClose = null;
   public int visitorNbNights = 0;
   public int foreignMerchantStallId = -1;
   public boolean lastAttackByPlayer = false;
   public HashMap<Goal, Long> lastGoalTime = new HashMap<>();
   public String hiredBy = null;
   public boolean aggressiveStance = false;
   public long hiredUntil = 0L;
   public boolean isUsingBow;
   public boolean isUsingHandToHand;
   public boolean isRaider = false;
   public AStarPathPlannerJPS pathPlannerJPS;
   public AS_PathEntity pathEntity;
   public int updateCounter = 0;
   public long client_lastupdated;
   public MillWorldData mw;
   private boolean pathFailedSincelastTick = false;
   private List<AStarNode> pathCalculatedSinceLastTick = null;
   private int localStuck = 0;
   private final Identifier[] clothTexture = new Identifier[2];
   private String clothName = null;
   public boolean shouldLieDown = false;
   public LinkedHashMap<TradeGood, Integer> merchantSells = new LinkedHashMap<>();
   public Identifier texture = null;
   private int attackTime;
   public boolean isDeadOnServer = false;
   public boolean travelBookMockVillager = false;

   /**
    * Replaces the 1.12 {@code EntityList.createEntityByIDFromName(typeName, world)}. Each VillagerType's
    * model maps to one of the three registered generic villager {@link EntityType}s (see
    * {@link MillEntities}); creation is {@code type.create(world, EntitySpawnReason.MOB_SUMMONED)}.
    */
   private static MillVillager createVillagerEntity(VillagerRecord villagerRecord, Level world) {
      if (villagerRecord == null || villagerRecord.getType() == null) {
         return null;
      }
      // 1.12 looked the entity up by VillagerType.getEntityName(); on 26.2 we map the type's model
      // string to one of the three registered generic villager EntityTypes (see MillEntities).
      String model = villagerRecord.getType().model;
      EntityType<MillVillager> type;
      if ("femaleasymmetrical".equals(model)) {
         type = MillEntities.VILLAGER_FEMALE_ASYM;
      } else if ("femalesymmetrical".equals(model)) {
         type = MillEntities.VILLAGER_FEMALE_SYM;
      } else {
         type = MillEntities.VILLAGER_MALE;
      }
      return type.create(world, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
   }

   public static MillVillager createMockVillager(VillagerRecord villagerRecord, Level world) {
      MillVillager villager = createVillagerEntity(villagerRecord, world);
      if (villager == null) {
         MillLog.error(
            villagerRecord,
            "Could not create mock villager of dynamic type: " + villagerRecord.getType() + " entity: " + villagerRecord.getType().getEntityName()
         );
         return null;
      } else {
         villager.vtype = villagerRecord.getType();
         villager.gender = villagerRecord.getType().gender;
         villager.firstName = villagerRecord.firstName;
         villager.familyName = villagerRecord.familyName;
         villager.texture = villagerRecord.texture;
         villager.setHealth(villager.getMaxHealth());
         villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(villagerRecord.getType().health);
         villager.updateClothTexturePath();
         return villager;
      }
   }

   public static MillVillager createVillager(VillagerRecord villagerRecord, Level world, Point spawnPos, boolean respawn) {
      MillCrash.check(!world.isClientSide() && world instanceof ServerLevel, "Entity",
         "MillVillager.createVillager called on a client/non-server world: " + world + " (villager creation is server-only)");
      if (villagerRecord == null) {
         MillLog.error(villagerRecord, "Tried creating villager from a null record");
         return null;
      } else if (villagerRecord.getType() == null) {
         MillLog.error(null, "Tried creating villager of null type: " + villagerRecord.getType());
         return null;
      } else {
         MillVillager villager = createVillagerEntity(villagerRecord, world);
         if (villager == null) {
            MillLog.error(
               villagerRecord,
               "Could not create villager of dynamic type: " + villagerRecord.getType() + " entity: " + villagerRecord.getType().getEntityName()
            );
            return null;
         } else {
            villager.housePoint = villagerRecord.getHousePos();
            villager.townHallPoint = villagerRecord.getTownHallPos();
            villager.vtype = villagerRecord.getType();
            villager.setVillagerId(villagerRecord.getVillagerId());
            villager.gender = villagerRecord.getType().gender;
            villager.firstName = villagerRecord.firstName;
            villager.familyName = villagerRecord.familyName;
            villager.texture = villagerRecord.texture;
            villager.setHealth(villager.getMaxHealth());
            villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(villagerRecord.getType().health);
            villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(villagerRecord.getType().baseSpeed);
            villager.updateClothTexturePath();
            if (!respawn) {
               for (InvItem item : villagerRecord.getType().startingInv.keySet()) {
                  villager.addToInv(item.getItem(), item.meta, villagerRecord.getType().startingInv.get(item));
               }
            }

            villager.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            if (MillConfigValues.LogVillagerSpawn >= 1) {
               MillLog.major(villager, "Created new villager from record.");
            }

            if (MillLog.debugOn()) {
               MillLog.milldebug(
                  "Villager",
                  "CREATED id=" + villager.getVillagerId() + " type=" + villager.vtype.key
                     + " culture=" + (villager.vtype.culture != null ? villager.vtype.culture.key : "null")
                     + " gender=" + villager.gender + " name='" + villager.firstName + " " + villager.familyName + "'"
                     + " townHall=" + villager.townHallPoint + " house=" + villager.housePoint
                     + " spawnPos=" + spawnPos + " texture=" + villager.texture + " respawn=" + respawn
               );
            }

            return villager;
         }
      }
   }

   public static void readVillagerPacket(FriendlyByteBuf data) {
      try {
         long villager_id = data.readLong();
         if (Mill.clientWorld.getVillagerById(villager_id) != null) {
            Mill.clientWorld.getVillagerById(villager_id).readVillagerStreamdata(data);
         } else if (MillConfigValues.LogNetwork >= 2) {
            MillLog.minor(null, "readVillagerPacket for unknown villager: " + villager_id);
         }
      } catch (IOException var3) {
         // Phase-2 FLAG (spawn/update-data READ): optional fields are handled by the readNullable* helpers;
         // a thrown IOException is a genuine buffer/parse failure, not optional-on-client data. Surface it.
         throw MillCrash.fail("Entity", "MillVillager.readVillagerPacket: villager update-packet parse failed: " + var3);
      }
   }

   public MillVillager(EntityType<? extends MillVillager> entityType, Level world) {
      super(entityType, world);
      // 26.2: `world`/`level` is managed by the superclass (level() accessor). isImmuneToFire is now the
      // fireImmune() EntityType flag; getWorldTime()→level().getGameTime(); getEntityAttribute→getAttribute.
      this.mw = Mill.getMillWorld(world);
      this.inventory = new HashMap<>();
      this.setHealth(this.getMaxHealth());
      this.client_lastupdated = world.getGameTime();
      if (!world.isClientSide()) {
         this.pathPlannerJPS = new AStarPathPlannerJPS(world, this, MillConfigValues.jpsPathing);
      }

      this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.5);
      // Amphibious movement: real 3D, player-like swimming (pitch up/down + thrust) while in water, identical
      // vanilla ground movement otherwise — so a villager in water swims to the surface/shore instead of bobbing.
      this.moveControl = new org.millenaire.common.ai.nav.MillAmphibiousMoveControl(this, 85, 10, 2.0F);
      if (MillConfigValues.LogVillagerSpawn >= 3) {
         Exception e = new Exception();
         MillLog.printException("Creating villager " + this + " in world: " + world, e);
      }
   }

   public void addToInv(Block block, int nb) {
      this.addToInv(block.asItem(), 0, nb);
   }

   public void addToInv(Block block, int meta, int nb) {
      this.addToInv(block.asItem(), meta, nb);
   }

   public void addToInv(BlockState bs, int nb) {
      this.addToInv((bs.getBlock()).asItem(), 0, nb);
   }

   public void addToInv(InvItem iv, int nb) {
      this.addToInv(iv.getItem(), iv.meta, nb);
   }

   public void addToInv(Item item, int nb) {
      this.addToInv(item, 0, nb);
   }

   public void addToInv(Item item, int meta, int nb) {
      InvItem key = InvItem.createInvItem(item, meta);
      if (this.inventory.containsKey(key)) {
         this.inventory.put(key, this.inventory.get(key) + nb);
      } else {
         this.inventory.put(key, nb);
      }

      this.updateVillagerRecord();
      this.updateClothTexturePath();
   }

   /**
    * 26.2 attribute supplier (replaces {@code applyEntityAttributes}). Register per villager
    * EntityType via {@code FabricDefaultAttributeRegistry.register(type, MillVillager.createAttributes().build())}.
    * Per-villager MAX_HEALTH (computeMaxHealth) is applied at spawn in createVillager/createMockVillager.
    */
   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MOVEMENT_SPEED, 0.5)
         .add(Attributes.MAX_HEALTH, 20.0)
         // FOLLOW_RANGE is the navigation's path-length cap (getMaxPathLength) AND scales the node budget
         // (maxVisitedNodes = maxPathLength*16). The vanilla default (~16-32) is far too short for a Mill
         // village — villagers couldn't path around a big obstacle or up varied terrain and got stuck. 96
         // gives village-scale reach (and a ~1536+ node budget for detours).
         .add(Attributes.FOLLOW_RANGE, 96.0)
         // ATTACK_DAMAGE/KNOCKBACK are NOT in PathfinderMob's default set; doHurtTarget reads them, so the
         // new combat behaviour crashed ("Can't find attribute attack_damage") the moment a villager meleed.
         .add(Attributes.ATTACK_DAMAGE, 2.0)
         .add(Attributes.ATTACK_KNOCKBACK, 0.0);
   }

   @Override
   protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(net.minecraft.world.level.Level level) {
      // OPT-IN only (MillConfigValues.NewAI, default false): the rewritten no-warp, terrain+safety-aware
      // navigation. When the flag is off this returns the exact legacy navigation, so none of Mill's existing
      // behaviour changes.
      if (org.millenaire.common.config.MillConfigValues.NewAI) {
         return new org.millenaire.common.ai.MillPathNavigation(this, level);
      }
      return super.createNavigation(level);
   }

   /** New-AI danger field (req 4/5/6), rebuilt periodically from nearby hostiles and fed to the navigation. */
   private org.millenaire.common.ai.MillInfluenceGrid aiInfluence = org.millenaire.common.ai.MillInfluenceGrid.empty();
   private int aiGridCooldown = 0;
   /** NewAI decision engine (tactical combat layer). Lazily created; only ticked under the NewAI flag. */
   private org.millenaire.common.ai.MillAI millAI;

   /**
    * NewAI only: rebuild the local danger field from nearby hostiles (~every 20 ticks) and feed it to the
    * MillPathNavigation cost so paths route safely. Additive + side-effect-free w.r.t. the rest of Mill —
    * it only tunes the navigation cost; goal selection / performAction / economy are untouched.
    */
   private void updateNewAiDanger() {
      if (--this.aiGridCooldown <= 0) {
         this.aiGridCooldown = 10;
         // Use getEntities(except, AABB, predicate) NOT getEntitiesOfClass: the latter goes through
         // ClassInstanceMultiMap's per-class cache (computeIfAbsent), which is not reentrancy-safe during the
         // entity tick loop and was throwing ConcurrentModificationException. getEntities iterates the section
         // lists directly. Enemy covers ALL hostiles incl. raiders/pillagers/zombies.
         java.util.List<net.minecraft.world.entity.LivingEntity> hostiles = new java.util.ArrayList<>();
         for (net.minecraft.world.entity.Entity e : this.level().getEntities(this, this.getBoundingBox().inflate(24.0),
            e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive())) {
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
               hostiles.add(le);
            }
         }
         int bx = this.getBlockX();
         int bz = this.getBlockZ();
         this.aiInfluence = org.millenaire.common.ai.MillInfluenceGrid.build(
            bx, bz, bx, bz, hostiles, org.millenaire.common.ai.MillInfluenceGrid.DEFAULT_RADIUS);

         // PROACTIVE threat detection: a combat villager with no target engages the nearest hostile within
         // ~16 blocks — react to an approaching/aiming hostile (incl. pillagers/raiders) BEFORE being hit,
         // and rally the village. Prefer hostiles already aiming at a villager (clear attack intent).
         if (this.helpsInAttacks() && this.getTarget() == null && !hostiles.isEmpty()) {
            net.minecraft.world.entity.LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (net.minecraft.world.entity.LivingEntity h : hostiles) {
               double d = this.distanceToSqr(h);
               if (d > 16.0 * 16.0) {
                  continue;
               }
               boolean aimingAtVillager = h instanceof net.minecraft.world.entity.Mob m
                  && m.getTarget() instanceof MillVillager;
               double score = aimingAtVillager ? d - 64.0 : d; // bias toward those already threatening us
               if (score < bestScore) {
                  bestScore = score;
                  best = h;
               }
            }
            if (best != null) {
               this.setTarget(best);
               this.clearGoal(); // drop the work goal (and its stopMoving) so combat movement isn't frozen
               if (this.getTownHall() != null) {
                  this.getTownHall().callForHelp(best);
               }
            }
         }
      }
      if (this.getNavigation() instanceof org.millenaire.common.ai.MillPathNavigation nav) {
         // jumpPenalty kept SMALL (0.3): it must only be a gentle tiebreaker for flatter routes, NOT a
         // deterrent — at 2.0 it made every 1-block step-up expensive, so on hilly/uphill terrain the A*
         // exhausted its node budget on flat directions and never found the climb → villagers got stuck.
         nav.configureCost(this.aiInfluence,
            (float) org.millenaire.common.config.MillConfigValues.VFNavDangerWeight,
            (float) org.millenaire.common.config.MillConfigValues.VFNavDropPenalty);
      }
   }

   /** The current danger field (for the combat behaviour's position scoring). */
   public org.millenaire.common.ai.MillInfluenceGrid getAiInfluence() {
      return this.aiInfluence;
   }

   private void applyPathCalculatedSinceLastTick() {
      try {
         AS_PathEntity path = AStarStatic.translateAStarPathtoPathEntity(this.level(), this.pathCalculatedSinceLastTick, this.getPathingConfig());
         this.registerNewPath(path);
      } catch (Exception var2) {
         throw MillCrash.fail("Entity",
            "MillVillager.applyPathCalculatedSinceLastTick: JPS path translate/register failed for " + this + ": " + var2);
      } finally {
         this.pathCalculatedSinceLastTick = null;
      }
   }

   public boolean attackEntity(Entity entity) {
      double distance = this.getPos().distanceTo(entity);
      if (this.vtype.isArcher && distance > 5.0 && this.hasBow()) {
         this.isUsingBow = true;
         this.attackEntity_testHiredGoon(entity);
         if (distance < 20.0 && entity instanceof LivingEntity) {
            if (this.attackTime <= 0) {
               this.attackTime = 100;
               this.swing(InteractionHand.MAIN_HAND);
               float distanceFactor = (float)(distance / 20.0);
               distanceFactor = Mth.clamp(distanceFactor, 0.1F, 1.0F);
               this.attackEntityWithRangedAttack((LivingEntity)entity, distanceFactor);
            } else {
               this.attackTime--;
            }
         }
      } else {
         if (this.attackTime <= 0
            && distance < 2.0
            && entity.getBoundingBox().maxY > this.getBoundingBox().minY
            && entity.getBoundingBox().minY < this.getBoundingBox().maxY) {
            this.attackTime = 20;
            this.swing(InteractionHand.MAIN_HAND);
            this.attackEntity_testHiredGoon(entity);
            return entity.hurtOrSimulate(this.damageSources().mobAttack(this), this.getAttackStrength());
         }

         this.attackTime--;
         this.isUsingHandToHand = true;
      }

      return true;
   }

   private void attackEntity_testHiredGoon(Entity targetedEntity) {
      if (targetedEntity instanceof Player && this.hiredBy != null) {
         Player owner = this.millGetPlayerByName(this.hiredBy);
         if (owner != null && owner != targetedEntity) {
            MillAdvancements.MP_HIREDGOON.grant(owner);
         }
      }
   }

   @Override
   public boolean hurtServer(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount) {
      // 26.2 dispatches damage to hurtServer; the 1.12 villager damage handling lives in
      // attackEntityFrom (source-less environmental-damage immunity + retaliation/reputation), which was
      // otherwise dead code, so villagers took full fall/suffocation/drown/fire damage and died
      // inexplicably (then the building respawned them). Route damage through it.
      return this.attackEntityFrom(source, amount);
   }

   public boolean attackEntityFrom(DamageSource ds, float i) {
      if (ds.getEntity() == null && !ds.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
         return false;
      } else {
         boolean hadFullHealth = this.getMaxHealth() == this.getHealth();
         // Apply the real damage via the PARENT's hurtServer (not hurtOrSimulate, which re-dispatches to
         // this.hurtServer and would infinitely recurse through our override).
         boolean b = super.hurtServer((net.minecraft.server.level.ServerLevel) this.level(), ds, i);
         Entity entity = ds.getEntity();
         this.lastAttackByPlayer = false;
         if (entity != null && entity instanceof LivingEntity) {
            // Any hit from a living attacker WAKES the villager and breaks off its sleep/rest goal — being
            // attacked must never leave it lying down asleep (vanilla stopSleeping does this at the top of
            // hurt). Retaliation target is set per-attacker-type below; this just guarantees it's awake.
            if (this.shouldLieDown) {
               this.shouldLieDown = false;
               this.clearGoal();
            }
            if (entity instanceof Player) {
               if (!((Player)entity).isSpectator() && !((Player)entity).isCreative()) {
                  this.lastAttackByPlayer = true;
                  Player player = (Player)entity;
                  if (!this.isRaider) {
                     if (this.vtype != null && !this.vtype.hostile) {
                        UserProfile serverProfile = VillageUtilities.getServerProfile(player.level(), player);
                        if (serverProfile != null) {
                           serverProfile.adjustReputation(this.getTownHall(), (int)(-i * 10.0F));
                        }
                     }

                     // Retaliate on any non-peaceful player hit (was gated behind health < max-10, which left a
                     // full-health villager — especially one just woken from sleep — passively taking hits).
                     if (this.level().getDifficulty() != Difficulty.PEACEFUL) {
                        this.setTarget((LivingEntity)entity);
                        this.clearGoal();
                        if (this.getTownHall() != null) {
                           this.getTownHall().callForHelp((LivingEntity)entity);
                        }
                     }

                     if (this.vtype != null
                        && !this.vtype.hostile
                        && hadFullHealth
                        && (
                           player.getItemInHand(InteractionHand.MAIN_HAND) == null
                              || MillCommonUtilities.getItemWeaponDamage(player.getUseItem().getItem()) <= 1.0
                        )
                        && !this.level().isClientSide()) {
                        ServerSender.sendTranslatedSentence(player, '6', "ui.communicationexplanations");
                     }
                  }

                  if (this.lastAttackByPlayer && this.getHealth() <= 0.0F) {
                     if (this.vtype != null && this.vtype.hostile) {
                        MillAdvancements.SELF_DEFENSE.grant(player);
                     } else {
                        MillAdvancements.DARK_SIDE.grant(player);
                     }
                  }
               }
            } else if (entity instanceof MillVillager) {
               MillVillager attackingVillager = (MillVillager)entity;
               if (this.isRaider != attackingVillager.isRaider || this.getTownHall() != attackingVillager.getTownHall()) {
                  this.setTarget((LivingEntity)entity);
                  this.clearGoal();
                  if (this.getTownHall() != null) {
                     this.getTownHall().callForHelp((LivingEntity)entity);
                  }
               }
            } else {
               this.setTarget((LivingEntity)entity);
               this.clearGoal();
               if (this.getTownHall() != null) {
                  this.getTownHall().callForHelp((LivingEntity)entity);
               }
            }
         }

         return b;
      }
   }

   public void attackEntityWithRangedAttack(LivingEntity target, float distanceFactor) {
      AbstractArrow entityarrow = this.getArrow(distanceFactor);
      double d0 = target.getX() - this.getX();
      double d1 = target.getBoundingBox().minY + target.getBbHeight() / 3.0F - entityarrow.getY();
      double d2 = target.getZ() - this.getZ();
      double d3 = Math.sqrt(d0 * d0 + d2 * d2);
      float speedFactor = 1.0F;
      float damageBonus = 0.0F;
      ItemStack weapon = this.getWeapon();
      if (weapon != null) {
         Item item = weapon.getItem();
         if (item instanceof ItemMillenaireBow) {
            ItemMillenaireBow bow = (ItemMillenaireBow)item;
            if (bow.speedFactor > speedFactor) {
               speedFactor = bow.speedFactor;
            }

            if (bow.damageBonus > damageBonus) {
               damageBonus = bow.damageBonus;
            }
         }
      }

      // Original: setDamage(getDamage() + damageBonus), where getDamage() was the distance/difficulty
      // value set by getArrow(). AbstractArrow has no baseDamage getter in 26.2, so re-apply the
      // setBaseDamageFromMob formula (power*2 + difficulty triangle) and add the per-bow bonus.
      if (damageBonus > 0.0F) {
         double base = distanceFactor * 2.0F + this.getRandom().triangle(this.level().getDifficulty().getId() * 0.11, 0.57425);
         entityarrow.setBaseDamage(base + damageBonus);
      }
      entityarrow.shoot(d0, d1 + d3 * 0.2F, d2, 1.6F, (14 - this.level().getDifficulty().getId() * 4) * speedFactor);
      this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
      this.level().addFreshEntity(entityarrow);
   }

   public boolean attemptChildConception() {
      int nbChildren = 0;

      for (MillVillager villager : this.getHouse().getKnownVillagers()) {
         if (villager.isChild()) {
            nbChildren++;
         }
      }

      if (nbChildren > 1) {
         if (MillConfigValues.LogChildren >= 3) {
            MillLog.debug(this, "Wife already has " + nbChildren + " children, no need for more.");
         }

         return true;
      } else {
         int nbChildVillage = this.getTownHall().countChildren();
         if (nbChildVillage > MillConfigValues.maxChildrenNumber) {
            if (MillConfigValues.LogChildren >= 3) {
               MillLog.debug(this, "Village already has " + nbChildVillage + ", no need for more.");
            }

            return true;
         } else {
            boolean couldMoveIn = false;

            for (Point housePoint : this.getTownHall().buildings) {
               Building house = this.mw.getBuilding(housePoint);
               if (house != null
                  && !house.equals(this.getHouse())
                  && house.isHouse()
                  && (house.canChildMoveIn(1, this.familyName) || house.canChildMoveIn(2, this.familyName))) {
                  couldMoveIn = true;
               }
            }

            if (nbChildVillage > 5 && !couldMoveIn) {
               if (MillConfigValues.LogChildren >= 3) {
                  MillLog.debug(this, "Village already has " + nbChildVillage + " and no slot is available for the new child.");
               }

               return true;
            } else {
               List<? extends Entity> entities = WorldUtilities.getEntitiesWithinAABB(this.level(), MillVillager.class, this.getPos(), 4, 2);
               boolean manFound = false;

               for (Entity ent : entities) {
                  MillVillager villagerx = (MillVillager)ent;
                  if (villagerx.gender == 1 && !villagerx.isChild()) {
                     manFound = true;
                  }
               }

               if (!manFound) {
                  return false;
               } else {
                  if (MillConfigValues.LogChildren >= 3) {
                     MillLog.debug(this, "Less than two kids and man present, trying for new child.");
                  }

                  boolean createChild = false;
                  int conceptionChances = 2;
                  InvItem conceptionFood = this.getConfig().getBestConceptionFood(this.getHouse());
                  if (conceptionFood != null) {
                     this.getHouse().takeGoods(conceptionFood, 1);
                     conceptionChances += this.getConfig().foodsConception.get(conceptionFood);
                  }

                  if (MillCommonUtilities.randomInt(10) < conceptionChances) {
                     createChild = true;
                     if (MillConfigValues.LogChildren >= 2) {
                        MillLog.minor(this, "Conceiving child. Food available: " + conceptionFood);
                     }
                  } else if (MillConfigValues.LogChildren >= 2) {
                     MillLog.minor(this, "Failed to conceive child. Food available: " + conceptionFood);
                  }

                  if (MillConfigValues.DEV) {
                     createChild = true;
                  }

                  if (createChild) {
                     this.getHouse().createChild(this, this.getTownHall(), this.getRecord().spousesName);
                  }

                  return true;
               }
            }
         }
      }
   }

   public void calculateMerchantGoods() {
      for (InvItem key : this.vtype.foreignMerchantStock.keySet()) {
         if (this.getCulture().getTradeGood(key) != null && this.getBasicForeignMerchantPrice(key) > 0) {
            this.merchantSells.put(this.getCulture().getTradeGood(key), this.getBasicForeignMerchantPrice(key));
         }
      }
   }

   public boolean canBeLeashedTo(Player player) {
      return false;
   }

   public boolean canDespawn() {
      return false;
   }

   public boolean canMeditate() {
      return this.vtype.canMeditate;
   }

   public boolean canPerformSacrifices() {
      return this.vtype.canPerformSacrifices;
   }

   public boolean canVillagerClearLeaves() {
      return !this.vtype.noleafclearing;
   }

   private void checkGoalHeldItems(Goal goal, Point target) throws Exception {
      if (this.heldItemCount > 20) {
         ItemStack[] heldItems = null;
         if (target != null && target.horizontalDistanceTo(this) < goal.range(this)) {
            heldItems = goal.getHeldItemsDestination(this);
         } else {
            heldItems = goal.getHeldItemsTravelling(this);
         }

         if (heldItems != null && heldItems.length > 0) {
            this.heldItemId = (this.heldItemId + 1) % heldItems.length;
            this.heldItem = heldItems[this.heldItemId];
         }

         heldItems = null;
         if (target != null && target.horizontalDistanceTo(this) < goal.range(this)) {
            heldItems = goal.getHeldItemsOffHandDestination(this);
         } else {
            heldItems = goal.getHeldItemsOffHandTravelling(this);
         }

         if (heldItems != null && heldItems.length > 0) {
            this.heldItemOffHandId = (this.heldItemOffHandId + 1) % heldItems.length;
            this.heldItemOffHand = heldItems[this.heldItemOffHandId];
         }

         this.heldItemCount = 0;
      }

      if (this.heldItemCount == 0 && goal.swingArms(this)) {
         this.swing(InteractionHand.MAIN_HAND);
      }

      this.heldItemCount++;
   }

   public void checkGoals() throws Exception {
      Goal goal = Goal.goals.get(this.goalKey);
      if (goal == null) {
         MillLog.error(this, "Invalid goal key: " + this.goalKey);
         this.goalKey = null;
      } else {
         if (this.getGoalDestEntity() != null) {
            if (this.getGoalDestEntity().isRemoved()) {
               this.setGoalDestEntity(null);
               this.setPathDestPoint(null, 0);
            } else {
               this.setPathDestPoint(new Point(this.getGoalDestEntity()), 2);
            }
         }

         Point target = null;
         boolean continuingGoal = true;
         if (this.getPathDestPoint() != null) {
            target = this.getPathDestPoint();
            if (this.pathEntity != null && this.pathEntity.getNodeCount() > 0) {
               target = new Point(this.pathEntity.getEndNode());
            }
         }

         this.speakSentence(goal.sentenceKey());
         if (this.getGoalDestPoint() == null && this.getGoalDestEntity() == null) {
            goal.setVillagerDest(this);
            if (MillConfigValues.LogGeneralAI >= 2 && this.extraLog) {
               MillLog.minor(this, "Goal destination: " + this.getGoalDestPoint() + "/" + this.getGoalDestEntity());
            }
         } else if (target != null && target.horizontalDistanceTo(this) < goal.range(this)) {
            if (this.actionStart == 0L) {
               this.stopMoving = goal.stopMovingWhileWorking();
               this.actionStart = this.level().getOverworldClockTime();
               this.shouldLieDown = goal.shouldVillagerLieDown();
               if (MillConfigValues.LogGeneralAI >= 2 && this.extraLog) {
                  MillLog.minor(this, "Starting action: " + this.actionStart);
               }
            }

            if (this.level().getOverworldClockTime() - this.actionStart >= goal.actionDuration(this)) {
               if (goal.performAction(this)) {
                  this.clearGoal();
                  this.goalKey = goal.nextGoal(this);
                  this.stopMoving = false;
                  this.shouldLieDown = false;
                  this.heldItem = ItemStack.EMPTY;
                  this.heldItemOffHand = ItemStack.EMPTY;
                  continuingGoal = false;
                  if (MillConfigValues.LogGeneralAI >= 2 && this.extraLog) {
                     MillLog.minor(this, "Goal performed. Now doing: " + this.goalKey);
                  }
               } else {
                  this.stopMoving = goal.stopMovingWhileWorking();
               }

               this.actionStart = 0L;
               this.goalStarted = this.level().getOverworldClockTime();
            }
         } else {
            this.stopMoving = false;
            this.shouldLieDown = false;
         }

         if (continuingGoal) {
            if (goal.isStillValid(this)) {
               if (this.level().getOverworldClockTime() - this.goalStarted > goal.stuckDelay(this)) {
                  boolean actionDone = goal.stuckAction(this);
                  if (actionDone) {
                     this.goalStarted = this.level().getOverworldClockTime();
                  }

                  if (goal.isStillValid(this)) {
                     this.allowRandomMoves = goal.allowRandomMoves();
                     // Don't let a goal's stopMoving freeze the nav while the new AI is fighting a target.
                     if (this.stopMoving
                        && !(org.millenaire.common.config.MillConfigValues.NewAI && this.getTarget() != null)) {
                        this.getNavigation().stop();
                        this.pathEntity = null;
                     }

                     this.checkGoalHeldItems(goal, target);
                  }
               } else {
                  this.checkGoalHeldItems(goal, target);
               }
            } else {
               this.stopMoving = false;
               this.shouldLieDown = false;
               goal.onComplete(this);
               this.clearGoal();
               this.goalKey = goal.nextGoal(this);
               this.heldItemCount = 21;
               this.heldItemId = -1;
               this.heldItemOffHandId = -1;
            }
         }
      }
   }

   public void clearGoal() {
      this.setGoalDestPoint(null);
      this.setGoalBuildingDestPoint(null);
      this.setGoalDestEntity(null);
      this.goalKey = null;
      this.shouldLieDown = false;
   }

   private boolean closeFenceGate(int i, int j, int k) {
      Point p = new Point(i, j, k);
      BlockState state = p.getBlockActualState(this.level());
      if (BlockItemUtilities.isFenceGate(state.getBlock()) && (Boolean)state.getValue(FenceGateBlock.OPEN)) {
         p.setBlockState(this.level(), state.setValue(FenceGateBlock.OPEN, false));
         return true;
      } else {
         return false;
      }
   }

   public void computeChildScale() {
      if (this.getRecord() != null) {
         if (this.getSize() == 20) {
            if (this.gender == 1) {
               this.getRecord().scale = 0.9F;
            } else {
               this.getRecord().scale = 0.8F;
            }
         } else {
            this.getRecord().scale = 0.5F + this.getSize() / 100.0F;
         }
      }
   }

   public float computeMaxHealth() {
      if (this.vtype == null || this.getRecord() == null) {
         return 40.0F;
      } else {
         return this.isChild() ? 10 + this.getSize() : this.vtype.health;
      }
   }

   private List<Node> computeNewPath(Point dest) {
      if (this.getPos().sameBlock(dest)) {
         return null;
      } else {
         try {
            if (this.goalKey != null && Goal.goals.containsKey(this.goalKey)) {
               Goal goal = Goal.goals.get(this.goalKey);
               if (goal.range(this) >= this.getPos().horizontalDistanceTo(this.getPathDestPoint())) {
                  return null;
               }
            }

            if (this.pathPlannerJPS.isBusy()) {
               this.pathPlannerJPS.stopPathSearch(true);
            }

            AStarNode destNode = null;
            AStarNode[] possibles = AStarStatic.getAccessNodesSorted(
               this.level(),
               this.doubleToInt(this.getX()),
               this.doubleToInt(this.getY()),
               this.doubleToInt(this.getZ()),
               this.getPathDestPoint().getiX(),
               this.getPathDestPoint().getiY(),
               this.getPathDestPoint().getiZ(),
               this.getPathingConfig()
            );
            if (possibles.length != 0) {
               destNode = possibles[0];
            }

            if (destNode != null) {
               Point startPos = this.getPos().getBelow();
               if (!startPos.isBlockPassable(this.level())) {
                  startPos = startPos.getAbove();
                  if (!startPos.isBlockPassable(this.level())) {
                     startPos = startPos.getAbove();
                  }
               }

               this.pathPlannerJPS
                  .getPath(
                     this.doubleToInt(this.getX()),
                     this.doubleToInt(this.getY()),
                     this.doubleToInt(this.getZ()),
                     destNode.x,
                     destNode.y,
                     destNode.z,
                     this.getPathingConfig()
                  );
            } else {
               this.onNoPathAvailable();
            }
         } catch (ThreadSafeUtilities.ChunkAccessException var5) {
            if (MillConfigValues.LogChunkLoader >= 2) {
               MillLog.minor(this, "LevelChunk access violation while calculating path.");
            }
         }

         return null;
      }
   }

   public int countInv(Block block, int meta) {
      return this.countInv(InvItem.createInvItem(block.asItem(), meta));
   }

   public int countInv(BlockState blockState) {
      return this.countInv(InvItem.createInvItem((blockState.getBlock()).asItem(), 0));
   }

   public int countInv(InvItem key) {
      if (key.block == Blocks.OAK_LOG && key.meta == -1) {
         int nb = 0;
         InvItem tkey = InvItem.createInvItem(Blocks.OAK_LOG.asItem(), 0);
         if (this.inventory.containsKey(tkey)) {
            nb += this.inventory.get(tkey);
         }

         tkey = InvItem.createInvItem(Blocks.OAK_LOG.asItem(), 1);
         if (this.inventory.containsKey(tkey)) {
            nb += this.inventory.get(tkey);
         }

         tkey = InvItem.createInvItem(Blocks.OAK_LOG.asItem(), 2);
         if (this.inventory.containsKey(tkey)) {
            nb += this.inventory.get(tkey);
         }

         tkey = InvItem.createInvItem(Blocks.OAK_LOG.asItem(), 3);
         if (this.inventory.containsKey(tkey)) {
            nb += this.inventory.get(tkey);
         }

         tkey = InvItem.createInvItem(Blocks.ACACIA_LOG.asItem(), 0);
         if (this.inventory.containsKey(tkey)) {
            nb += this.inventory.get(tkey);
         }

         tkey = InvItem.createInvItem(Blocks.ACACIA_LOG.asItem(), 1);
         if (this.inventory.containsKey(tkey)) {
            nb += this.inventory.get(tkey);
         }

         return nb;
      } else if (key.meta == -1) {
         int nbx = 0;

         for (int i = 0; i < 16; i++) {
            InvItem tkeyx = InvItem.createInvItem(key.item, i);
            if (this.inventory.containsKey(tkeyx)) {
               nbx += this.inventory.get(tkeyx);
            }
         }

         return nbx;
      } else {
         return this.inventory.containsKey(key) ? this.inventory.get(key) : 0;
      }
   }

   public int countInv(Item item) {
      return this.countInv(item, 0);
   }

   public int countInv(Item item, int meta) {
      return this.countInv(InvItem.createInvItem(item, meta));
   }

   public int countItemsAround(Item[] items, int radius) {
      List<? extends Entity> list = WorldUtilities.getEntitiesWithinAABB(this.level(), ItemEntity.class, this.getPos(), radius, radius);
      int count = 0;
      if (list != null) {
         for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getClass() == ItemEntity.class) {
               ItemEntity entity = (ItemEntity)list.get(i);
               if (!entity.isRemoved()) {
                  for (Item id : items) {
                     if (id == entity.getItem().getItem()) {
                        count++;
                     }
                  }
               }
            }
         }
      }

      return count;
   }

   public void despawnVillager() {
      if (!this.level().isClientSide()) {
         if (MillLog.debugOn()) {
            MillLog.milldebug("Villager", "DESPAWN id=" + this.getVillagerId() + " name='" + this.firstName + " " + this.familyName + "' type=" + (this.vtype != null ? this.vtype.key : "null") + " at " + this.getPos());
         }

         if (this.hiredBy != null) {
            Player owner = this.millGetPlayerByName(this.hiredBy);
            if (owner != null) {
               ServerSender.sendTranslatedSentence(owner, '4', "hire.hiredied", this.getVillagerName());
            }
         }

         this.mw.clearVillagerOfId(this.getVillagerId());
         super.discard();
      }
   }

   public void despawnVillagerSilent() {
      if (MillConfigValues.LogVillagerSpawn >= 3) {
         Exception e = new Exception();
         MillLog.printException("Despawning villager: " + this, e);
      }

      if (MillLog.debugOn()) {
         MillLog.milldebug("Villager", "DESPAWN(silent) id=" + this.getVillagerId() + " name='" + this.firstName + " " + this.familyName + "' type=" + (this.vtype != null ? this.vtype.key : "null") + " at " + this.getPos());
      }

      this.mw.clearVillagerOfId(this.getVillagerId());
      super.discard();
   }

   public void detrampleCrops() {
      if (this.getPos().sameBlock(this.prevPoint)
         && (this.previousBlock == Blocks.WHEAT || this.previousBlock instanceof BlockMillCrops)
         && this.getBlock(this.getPos()) != Blocks.AIR
         && this.getBlock(this.getPos().getBelow()) == Blocks.DIRT) {
         this.setBlock(this.getPos(), this.previousBlock);
         this.setBlockMetadata(this.getPos(), this.previousBlockMeta);
         this.setBlock(this.getPos().getBelow(), Blocks.FARMLAND);
      }

      this.previousBlock = this.getBlock(this.getPos());
      this.previousBlockMeta = this.getBlockMeta(this.getPos());
   }

   public int doubleToInt(double input) {
      return AStarStatic.getIntCoordFromDoubleCoord(input);
   }

   @Override
   public boolean equals(Object obj) {
      if (obj != null && obj instanceof MillVillager) {
         MillVillager v = (MillVillager)obj;
         return this.getVillagerId() == v.villagerId;
      } else {
         return false;
      }
   }

   public void faceEntity(Entity par1Entity, float par2, float par3) {
   }

   public void faceEntityMill(Entity entityIn, float par2, float par3) {
      this.getLookControl().setLookAt(entityIn, par2, par3);
   }

   public void facePoint(Point p, float par2, float par3) {
      double x = p.x + 0.5;
      double z = p.z + 0.5;
      double y = p.y + 1.0;
      this.getLookControl().setLookAt(x, y, z, 10.0F, (float)this.getMaxHeadXRot());
   }

   private void foreignMerchantUpdate() {
      if (this.foreignMerchantStallId < 0) {
         for (int i = 0; i < this.getHouse().getResManager().stalls.size() && this.foreignMerchantStallId < 0; i++) {
            boolean taken = false;

            for (MillVillager v : this.getHouse().getKnownVillagers()) {
               if (v.foreignMerchantStallId == i) {
                  taken = true;
               }
            }

            if (!taken) {
               this.foreignMerchantStallId = i;
            }
         }
      }

      if (this.foreignMerchantStallId < 0) {
         this.foreignMerchantStallId = 0;
      }
   }

   private Goal getActiveGoal() {
      return this.goalKey != null && Goal.goals.containsKey(this.goalKey) ? Goal.goals.get(this.goalKey) : null;
   }

   protected AbstractArrow getArrow(float distanceFactor) {
      Arrow entitytippedarrow = new Arrow(this.level(), this, new ItemStack(net.minecraft.world.item.Items.ARROW), null);
      // 1.12 setEnchantmentEffectsFromEntity(this, distanceFactor) → 26.2 setBaseDamageFromMob(power):
      // sets base damage from the shot power + difficulty (was distanceFactor*2 + difficulty triangle).
      // Bow enchant effects (Power/Punch/Flame) are now applied from the firing weapon's components, which
      // Mill bows don't carry, so only the base-damage scaling is reproduced here. The per-bow damageBonus
      // is layered on in attackEntityWithRangedAttack (mirrors the original setDamage(getDamage()+bonus)).
      entitytippedarrow.setBaseDamageFromMob(distanceFactor);
      return entitytippedarrow;
   }

   public int getAttackStrength() {
      int attackStrength = this.vtype.baseAttackStrength;
      ItemStack weapon = this.getWeapon();
      if (weapon != null) {
         attackStrength = (int)(attackStrength + Math.ceil((float)MillCommonUtilities.getItemWeaponDamage(weapon.getItem()) / 2.0F));
      }

      return attackStrength;
   }

   public int getBasicForeignMerchantPrice(InvItem item) {
      if (this.getTownHall() == null) {
         return 0;
      } else if (this.getCulture().getTradeGood(item) != null) {
         return this.getCulture() != this.getTownHall().culture
            ? (int)(this.getCulture().getTradeGood(item).foreignMerchantPrice * 1.5)
            : this.getCulture().getTradeGood(item).foreignMerchantPrice;
      } else {
         return 0;
      }
   }

   public float getBedOrientationInDegrees() {
      Point ref = this.getPos();
      if (this.getGoalDestPoint() != null) {
         ref = this.getGoalDestPoint();
      }

      Block block = WorldUtilities.getBlock(this.level(), ref);
      if (block instanceof BedBlock) {
         BlockState state = ref.getBlockActualState(this.level());
         Direction side = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
         if (side == Direction.SOUTH) {
            return 0.0F;
         }

         if (side == Direction.NORTH) {
            return 180.0F;
         }

         if (side == Direction.EAST) {
            return 270.0F;
         }

         if (side == Direction.WEST) {
            return 90.0F;
         }
      } else {
         if (WorldUtilities.getBlock(this.level(), ref.getSouth()) == Blocks.AIR) {
            return 0.0F;
         }

         if (WorldUtilities.getBlock(this.level(), ref.getWest()) == Blocks.AIR) {
            return 90.0F;
         }

         if (WorldUtilities.getBlock(this.level(), ref.getNorth()) == Blocks.AIR) {
            return 180.0F;
         }

         if (WorldUtilities.getBlock(this.level(), ref.getEast()) == Blocks.AIR) {
            return 270.0F;
         }
      }

      return 0.0F;
   }

   public Item getBestAxe() {
      InvItem bestItem = this.getConfig().getBestAxe(this);
      return bestItem != null ? (Item)bestItem.item : (Item)Items.WOODEN_AXE;
   }

   public ItemStack[] getBestAxeStack() {
      InvItem bestItem = this.getConfig().getBestAxe(this);
      return bestItem != null ? bestItem.staticStackArray : woodenAxeStack();
   }

   public ItemStack[] getBestHoeStack() {
      InvItem bestItem = this.getConfig().getBestHoe(this);
      return bestItem != null ? bestItem.staticStackArray : woodenHoeStack();
   }

   public Item getBestPickaxe() {
      InvItem bestItem = this.getConfig().getBestPickaxe(this);
      return bestItem != null ? (Item)bestItem.item : (Item)Items.WOODEN_PICKAXE;
   }

   public ItemStack[] getBestPickaxeStack() {
      InvItem bestItem = this.getConfig().getBestPickaxe(this);
      return bestItem != null ? bestItem.staticStackArray : woodenPickaxeStack();
   }

   public Item getBestShovel() {
      InvItem bestItem = this.getConfig().getBestShovel(this);
      return bestItem != null ? (Item)bestItem.item : (Item)Items.WOODEN_SHOVEL;
   }

   public ItemStack[] getBestShovelStack() {
      InvItem bestItem = this.getConfig().getBestShovel(this);
      return bestItem != null ? bestItem.staticStackArray : woodenShovelStack();
   }

   public Block getBlock(Point p) {
      return WorldUtilities.getBlock(this.level(), p);
   }

   public int getBlockMeta(Point p) {
      return WorldUtilities.getBlockMeta(this.level(), p);
   }

   public float getBlockPathWeight(BlockPos pos) {
      if (!this.allowRandomMoves) {
         if (MillConfigValues.LogPathing >= 3 && this.extraLog) {
            MillLog.debug(this, "Forbiding random moves. Current goal: " + Goal.goals.get(this.goalKey) + " Returning: " + -99999.0F);
         }

         return Float.NEGATIVE_INFINITY;
      } else {
         Point rp = new Point(pos);
         double dist = rp.distanceTo(this.housePoint);
         if (WorldUtilities.getBlock(this.level(), rp.getBelow()) == Blocks.FARMLAND) {
            return -50.0F;
         } else {
            return dist > 10.0 ? -((float)dist) : MillCommonUtilities.randomInt(10);
         }
      }
   }

   public ItemEntity getClosestItemVertical(List<InvItem> goods, int radius, int vertical) {
      return WorldUtilities.getClosestItemVertical(this.level(), this.getPos(), goods, radius, vertical);
   }

   public Identifier getClothTexturePath(int layer) {
      return this.clothTexture[layer];
   }

   public VillagerConfig getConfig() {
      return this.vtype != null && this.vtype.villagerConfig != null ? this.vtype.villagerConfig : VillagerConfig.DEFAULT_CONFIG;
   }

   public Culture getCulture() {
      return this.vtype == null ? null : this.vtype.culture;
   }

   public ConstructionIP getCurrentConstruction() {
      if (this.constructionJobId > -1 && this.constructionJobId < this.getTownHall().getConstructionsInProgress().size()) {
         ConstructionIP cip = this.getTownHall().getConstructionsInProgress().get(this.constructionJobId);
         if (cip.getBuilder() == null || cip.getBuilder() == this) {
            return cip;
         }
      }

      return null;
   }

   public Goal getCurrentGoal() {
      return Goal.goals.containsKey(this.goalKey) ? Goal.goals.get(this.goalKey) : null;
   }

   protected int getExperiencePoints(Player par1EntityPlayer) {
      return this.vtype.expgiven;
   }

   public String getFemaleChild() {
      return this.vtype.femaleChild;
   }

   public String getGameOccupationName(String playername) {
      if (this.getCulture() == null || this.vtype == null || this.getRecord() == null) {
         return "";
      } else if (!this.getCulture().canReadVillagerNames()) {
         return "";
      } else {
         return this.isChild() && this.getSize() == 20
            ? this.getCulture().getCultureString("villager." + this.vtype.altkey)
            : this.getCulture().getCultureString("villager." + this.vtype.key);
      }
   }

   public String getGameSpeech(String playername) {
      if (this.getCulture() == null) {
         return null;
      } else {
         String speech = VillageUtilities.getVillagerSentence(this, playername, false);
         if (speech != null) {
            int duration = 10 + speech.length() / 5;
            duration = Math.min(duration, 30);
            if (this.speech_started + 20 * duration < this.level().getOverworldClockTime()) {
               return null;
            }
         }

         return speech;
      }
   }

   public int getGatheringRange() {
      return 20;
   }

   public String getGenderString() {
      return this.gender == 1 ? "male" : "female";
   }

   public Building getGoalBuildingDest() {
      return this.mw.getBuilding(this.getGoalBuildingDestPoint());
   }

   public Point getGoalBuildingDestPoint() {
      return this.goalInformation == null ? null : this.goalInformation.getDestBuildingPos();
   }

   public Entity getGoalDestEntity() {
      return this.goalInformation == null ? null : this.goalInformation.getTargetEnt();
   }

   public Point getGoalDestPoint() {
      return this.goalInformation == null ? null : this.goalInformation.getDest();
   }

   public String getGoalLabel(String goal) {
      return Goal.goals.containsKey(goal) ? Goal.goals.get(goal).gameName(this) : "none";
   }

   public List<Goal> getGoals() {
      return this.vtype != null ? this.vtype.goals : null;
   }

   public List<InvItem> getGoodsToBringBackHome() {
      return this.vtype.bringBackHomeGoods;
   }

   public List<InvItem> getGoodsToCollect() {
      return this.vtype.collectGoods;
   }

   public int getHireCost(Player player) {
      int cost = this.vtype.hireCost;
      if (this.getTownHall().controlledBy(player)) {
         cost /= 2;
      }

      return cost;
   }

   public Building getHouse() {
      if (this.house != null) {
         return this.house;
      } else {
         if (MillConfigValues.LogVillager >= 3 && this.extraLog) {
            MillLog.debug(this, "Seeking uncached house");
         }

         if (this.mw != null) {
            this.house = this.mw.getBuilding(this.housePoint);
            return this.house;
         } else {
            return null;
         }
      }
   }

   public Set<InvItem> getInventoryKeys() {
      return this.inventory.keySet();
   }

   public List<InvItem> getItemsNeeded() {
      return this.vtype.itemsNeeded;
   }

   public ItemStack getItemStackFromSlot(EquipmentSlot slotIn) {
      if (slotIn == EquipmentSlot.HEAD) {
         for (InvItem item : this.getConfig().armoursHelmetSorted) {
            if (this.countInv(item) > 0) {
               return item.getItemStack();
            }
         }

         return ItemStack.EMPTY;
      } else if (slotIn == EquipmentSlot.CHEST) {
         for (InvItem itemx : this.getConfig().armoursChestplateSorted) {
            if (this.countInv(itemx) > 0) {
               return itemx.getItemStack();
            }
         }

         return ItemStack.EMPTY;
      } else if (slotIn == EquipmentSlot.LEGS) {
         for (InvItem itemxx : this.getConfig().armoursLeggingsSorted) {
            if (this.countInv(itemxx) > 0) {
               return itemxx.getItemStack();
            }
         }

         return ItemStack.EMPTY;
      } else if (slotIn == EquipmentSlot.FEET) {
         for (InvItem itemxxx : this.getConfig().armoursBootsSorted) {
            if (this.countInv(itemxxx) > 0) {
               return itemxxx.getItemStack();
            }
         }

         return ItemStack.EMPTY;
      } else if (this.heldItem != null && slotIn == EquipmentSlot.MAINHAND) {
         return this.heldItem;
      } else {
         return this.heldItemOffHand != null && slotIn == EquipmentSlot.OFFHAND ? this.heldItemOffHand : ItemStack.EMPTY;
      }
   }

   public String getMaleChild() {
      return this.vtype.maleChild;
   }

   // 26.2: Entity.getName() returns Component, so Mill's String name method is renamed to avoid the
   // incompatible-return-type clash. Callers in this file updated accordingly.
   public String getVillagerName() {
      return this.firstName + " " + this.familyName;
   }

   /** Bridge a Mill A* {@link AS_PathEntity} (Node[]) onto a vanilla {@link net.minecraft.world.level.pathfinder.Path}. */
   private static net.minecraft.world.level.pathfinder.Path millToVanillaPath(AS_PathEntity p) {
      net.minecraft.world.level.pathfinder.Node end = p.getEndNode();
      net.minecraft.core.BlockPos target = end == null ? net.minecraft.core.BlockPos.ZERO : new net.minecraft.core.BlockPos(end.x, end.y, end.z);
      return new net.minecraft.world.level.pathfinder.Path(java.util.Arrays.asList(p.pointsCopy), target, true);
   }

   /** 1.12 {@code World.getPlayerEntityByName} has no Level equivalent on 26.2 — scan loaded players. */
   private Player millGetPlayerByName(String name) {
      if (name == null) {
         return null;
      }
      for (Player p : this.level().players()) {
         if (name.equals(p.getName().getString())) {
            return p;
         }
      }
      return null;
   }

   @Override
   public net.minecraft.network.chat.Component getName() {
      return net.minecraft.network.chat.Component.literal(this.getVillagerName());
   }

   public String getNameKey() {
      if (this.vtype != null && this.getRecord() != null) {
         return this.isChild() && this.getSize() == 20 ? this.vtype.altkey : this.vtype.key;
      } else {
         return "";
      }
   }

   public String getNativeOccupationName() {
      if (this.vtype == null) {
         return null;
      } else {
         return this.isChild() && this.getSize() == 20 ? this.vtype.altname : this.vtype.name;
      }
   }

   public String getNativeSpeech(String playername) {
      if (this.getCulture() == null) {
         return null;
      } else {
         String speech = VillageUtilities.getVillagerSentence(this, playername, true);
         if (speech != null) {
            int duration = 10 + speech.length() / 5;
            duration = Math.min(duration, 30);
            if (this.speech_started + 20 * duration < this.level().getOverworldClockTime()) {
               return null;
            }
         }

         return speech;
      }
   }

   public Point getPathDestPoint() {
      return this.pathDestPoint;
   }

   private AStarConfig getPathingConfig() {
      return this.getActiveGoal() != null ? this.getActiveGoal().getPathingConfig(this) : this.getVillagerPathingConfig();
   }

   public Node getPathPointPos() {
      return new Node(
         Mth.floor(this.getBoundingBox().minX),
         Mth.floor(this.getBoundingBox().minY),
         Mth.floor(this.getBoundingBox().minZ)
      );
   }

   public Point getPos() {
      return new Point(this.getX(), this.getY(), this.getZ());
   }

   public HumanoidArm getPrimaryHand() {
      return this.getRecord() != null && this.getRecord().rightHanded ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
   }

   public String getRandomFamilyName() {
      return this.getCulture().getRandomNameFromList(this.vtype.familyNameList);
   }

   public VillagerRecord getRecord() {
      return this.mw == null ? null : this.mw.getVillagerRecordById(this.getVillagerId());
   }

   public int getSize() {
      return this.getRecord() == null ? 0 : this.getRecord().size;
   }

   public MillVillager getSpouse() {
      if (this.getHouse() != null && !this.isChild()) {
         for (MillVillager v : this.getHouse().getKnownVillagers()) {
            if (!v.isChild() && v.gender != this.gender) {
               return v;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public Identifier getTexture() {
      return this.texture;
   }

   @Override
   public boolean removeWhenFarAway(double distanceSquared) {
      // Mill villagers are village-managed, never naturally/egg-spawned, and must NOT be culled by
      // vanilla's far-away/idle creature despawn — otherwise they vanish a short distance from the
      // player ("most villagers disappear"), which also empties the tracked list and drives the
      // endless respawn loop (millVillagersInWorld=0). They persist until Mill itself despawns them.
      return false;
   }

   @Override
   public boolean requiresCustomPersistence() {
      return true;
   }

   public List<String> getToolsCategoriesNeeded() {
      return this.vtype.toolsCategoriesNeeded;
   }

   public int getTotalArmorValue() {
      return this.getRecord() == null ? 0 : this.getRecord().getTotalArmorValue();
   }

   public Building getTownHall() {
      if (this.townHall != null) {
         return this.townHall;
      } else {
         if (MillConfigValues.LogVillager >= 3 && this.extraLog) {
            MillLog.debug(this, "Seeking uncached townHall");
         }

         if (this.mw != null) {
            this.townHall = this.mw.getBuilding(this.townHallPoint);
            return this.townHall;
         } else {
            return null;
         }
      }
   }

   public long getVillagerId() {
      return this.villagerId;
   }

   public AStarConfig getVillagerPathingConfig() {
      return this.vtype.noleafclearing ? JPS_CONFIG_NO_LEAVES : JPS_CONFIG_DEFAULT;
   }

   public ItemStack getWeapon() {
      if (this.vtype == null) {
         return ItemStack.EMPTY;
      } else {
         if (this.isUsingBow) {
            InvItem weapon = this.getConfig().getBestWeaponRanged(this);
            if (weapon != null) {
               return weapon.getItemStack();
            }
         }

         if (this.isUsingHandToHand || !this.vtype.isArcher) {
            InvItem weapon = this.getConfig().getBestWeaponHandToHand(this);
            if (weapon != null) {
               return weapon.getItemStack();
            }
         }

         return this.vtype.startingWeapon != null ? this.vtype.startingWeapon.getItemStack() : ItemStack.EMPTY;
      }
   }

   public void growSize() {
      if (this.getRecord() != null) {
         int growth = 2;
         int nb = 0;
         nb = this.getHouse().takeGoods(Items.EGG, 1);
         if (nb == 1) {
            growth += 1 + MillCommonUtilities.randomInt(5);
         }

         for (InvItem food : this.getConfig().foodsGrowthSorted) {
            if (growth < 10 && this.getRecord().size + growth < 20 && this.getHouse().countGoods(food) > 0) {
               this.getHouse().takeGoods(food, 1);
               growth += this.getConfig().foodsGrowth.get(food) + MillCommonUtilities.randomInt(this.getConfig().foodsGrowth.get(food));
            }
         }

         this.getRecord().size += growth;
         if (this.getRecord().size > 20) {
            this.getRecord().size = 20;
         }

         this.computeChildScale();
         if (MillConfigValues.LogChildren >= 2) {
            MillLog.minor(this, "Child growing by " + growth + ", new size: " + this.getRecord().size);
         }
      }
   }

   private void handleDoorsAndFenceGates() {
      if (this.doorToClose != null
         && (
            this.pathEntity == null
               || this.pathEntity.getNodeCount() == 0
               || this.pathEntity.getPastTargetPathPoint(2) != null && this.doorToClose.sameBlock(this.pathEntity.getPastTargetPathPoint(2))
         )) {
         if (BlockItemUtilities.isWoodenDoor(this.getBlock(this.doorToClose))) {
            if ((Boolean)this.doorToClose.getBlockActualState(this.level()).getValue(DoorBlock.OPEN)) {
               this.toggleDoor(this.doorToClose);
            }

            for (Point nearbyDoor : new Point[]{
               this.doorToClose.getNorth(), this.doorToClose.getSouth(), this.doorToClose.getEast(), this.doorToClose.getWest()
            }) {
               if (BlockItemUtilities.isWoodenDoor(this.getBlock(nearbyDoor))
                  && (Boolean)nearbyDoor.getBlockActualState(this.level()).getValue(DoorBlock.OPEN)) {
                  this.toggleDoor(nearbyDoor);
               }
            }

            this.doorToClose = null;
         } else if (BlockItemUtilities.isFenceGate(this.getBlock(this.doorToClose))) {
            if (this.closeFenceGate(this.doorToClose.getiX(), this.doorToClose.getiY(), this.doorToClose.getiZ())) {
               this.doorToClose = null;
            }
         } else {
            this.doorToClose = null;
         }
      }

      if (this.pathEntity != null && this.pathEntity.getNodeCount() > 0) {
         Node p = null;
         if (this.pathEntity.getCurrentTargetPathPoint() != null) {
            Block currentTargetPathPointBlock = WorldUtilities.getBlock(
               this.level(),
               this.pathEntity.getCurrentTargetPathPoint().x,
               this.pathEntity.getCurrentTargetPathPoint().y,
               this.pathEntity.getCurrentTargetPathPoint().z
            );
            if (BlockItemUtilities.isWoodenDoor(currentTargetPathPointBlock)) {
               p = this.pathEntity.getCurrentTargetPathPoint();
            }
         } else if (this.pathEntity.getNextTargetPathPoint() != null) {
            Block nextTargetPathPointBlock = WorldUtilities.getBlock(
               this.level(),
               this.pathEntity.getNextTargetPathPoint().x,
               this.pathEntity.getNextTargetPathPoint().y,
               this.pathEntity.getNextTargetPathPoint().z
            );
            if (BlockItemUtilities.isWoodenDoor(nextTargetPathPointBlock)) {
               p = this.pathEntity.getNextTargetPathPoint();
            }
         }

         if (p != null) {
            Point point = new Point(p);
            if (!(Boolean)point.getBlockActualState(this.level()).getValue(DoorBlock.OPEN)) {
               this.toggleDoor(new Point(p));
            }

            this.doorToClose = new Point(p);
         } else {
            if (this.pathEntity.getNextTargetPathPoint() != null
               && BlockItemUtilities.isFenceGate(
                  WorldUtilities.getBlock(
                     this.level(),
                     this.pathEntity.getNextTargetPathPoint().x,
                     this.pathEntity.getNextTargetPathPoint().y,
                     this.pathEntity.getNextTargetPathPoint().z
                  )
               )) {
               p = this.pathEntity.getNextTargetPathPoint();
            } else if (this.pathEntity.getCurrentTargetPathPoint() != null
               && BlockItemUtilities.isFenceGate(
                  WorldUtilities.getBlock(
                     this.level(),
                     this.pathEntity.getCurrentTargetPathPoint().x,
                     this.pathEntity.getCurrentTargetPathPoint().y,
                     this.pathEntity.getCurrentTargetPathPoint().z
                  )
               )) {
               p = this.pathEntity.getCurrentTargetPathPoint();
            }

            if (p != null) {
               Point point = new Point(p);
               this.openFenceGate(p.x, p.y, p.z);
               this.doorToClose = point;
            }
         }
      }
   }

   private void handleLeaveClearing() {
      if (this.pathEntity != null && this.pathEntity.getNodeCount() > 0) {
         List<Point> pointsToCheck = new ArrayList<>();
         if (this.pathEntity.getCurrentTargetPathPoint() != null) {
            Point p = new Point(this.pathEntity.getCurrentTargetPathPoint());
            pointsToCheck.add(p);
            pointsToCheck.add(p.getAbove());
         }

         if (this.pathEntity.getNextTargetPathPoint() != null) {
            Point p = new Point(this.pathEntity.getNextTargetPathPoint());

            for (int dx = -1; dx < 2; dx++) {
               for (int dz = -1; dz < 2; dz++) {
                  pointsToCheck.add(p.getRelative(dx, 0.0, dz));
                  pointsToCheck.add(p.getRelative(dx, 1.0, dz));
               }
            }
         }

         for (Point point : pointsToCheck) {
            BlockState blockState = point.getBlockActualState(this.level());
            // 1.12: cleared vanilla leaves (LEAVES/LEAVES2) when decayable, protected fruit leaves, and
            // cleared other modded leaves (decayable or, if no decayable property, unconditionally).
            // 26.2: all leaves are LeavesBlock with a PERSISTENT property (PERSISTENT==true ~ 1.12
            // decayable==false). Protect BlockFruitLeaves; clear any other non-persistent leaves.
            if (blockState.getBlock() instanceof LeavesBlock) {
               if (!(blockState.getBlock() instanceof BlockFruitLeaves) && !blockState.getValue(LeavesBlock.PERSISTENT)) {
                  WorldUtilities.setBlock(this.level(), point, Blocks.AIR, true, true);
               }
            }
         }
      }
   }

   private boolean hasBow() {
      return this.getConfig().getBestWeaponRanged(this) != null;
   }

   public boolean hasChildren() {
      return this.vtype.maleChild != null && this.vtype.femaleChild != null;
   }

   @Override
   public int hashCode() {
      return (int)this.getVillagerId();
   }

   public boolean helpsInAttacks() {
      return this.vtype.helpInAttacks;
   }

   public void interactDev(Player entityplayer) {
      DevModUtilities.villagerInteractDev(entityplayer, this);
   }

   public boolean interactSpecial(Player entityplayer) {
      if (this.getTownHall() == null) {
         MillLog.error(this, "Trying to interact with a villager with no TH.");
      }

      if (this.getHouse() == null) {
         MillLog.error(this, "Trying to interact with a villager with no house.");
      }

      if (this.isChief()) {
         ServerSender.displayVillageChiefGUI(entityplayer, this);
         return true;
      } else {
         UserProfile profile = this.mw.getProfile(entityplayer);
         if (this.canMeditate() && this.mw.isGlobalTagSet("pujas") || this.canPerformSacrifices() && this.mw.isGlobalTagSet("mayansacrifices")) {
            if (MillConfigValues.LogPujas >= 3) {
               MillLog.debug(this, "canMeditate");
            }

            if (this.getTownHall().getReputation(entityplayer) < -1024) {
               ServerSender.sendTranslatedSentence(entityplayer, 'f', "ui.sellerboycott", this.getVillagerName());
               return false;
            }

            for (BuildingLocation l : this.getTownHall().getLocations()) {
               if (l.level >= 0 && l.getSellingPos() != null && l.getSellingPos().distanceTo(this) < 8.0) {
                  Building b = l.getBuilding(this.level());
                  if (b.pujas != null) {
                     if (MillConfigValues.LogPujas >= 3) {
                        MillLog.debug(this, "Found shrine: " + b);
                     }

                     // 1.12 opened GUI 6 directly; 26.2 uses Mill's PACKET_OPENGUI(104) flow.
                     org.millenaire.common.network.ServerSender.displayPujasGUI(entityplayer, b);
                     return true;
                  }
               }
            }
         }

         if (this.isSeller() && !this.getTownHall().controlledBy(entityplayer)) {
            if (this.getTownHall().getReputation(entityplayer) < -1024 || !this.getTownHall().chestLocked) {
               if (!this.getTownHall().chestLocked) {
                  ServerSender.sendTranslatedSentence(entityplayer, 'f', "ui.sellernotcurrently possible", this.getVillagerName());
                  return false;
               }

               ServerSender.sendTranslatedSentence(entityplayer, 'f', "ui.sellerboycott", this.getVillagerName());
               return false;
            }

            for (BuildingLocation lx : this.getTownHall().getLocations()) {
               if (lx.level >= 0
                  && lx.shop != null
                  && lx.shop.length() > 0
                  && (lx.getSellingPos() != null && lx.getSellingPos().distanceTo(this) < 5.0 || lx.sleepingPos.distanceTo(this) < 5.0)) {
                  ServerSender.displayVillageTradeGUI(entityplayer, lx.getBuilding(this.level()));
                  return true;
               }
            }
         }

         if (this.isForeignMerchant()) {
            ServerSender.displayMerchantTradeGUI(entityplayer, this);
            return true;
         } else if (this.vtype.hireCost > 0) {
            if (this.hiredBy != null && !this.hiredBy.equals(entityplayer.getName())) {
               ServerSender.sendTranslatedSentence(entityplayer, 'f', "hire.hiredbyotherplayer", this.getVillagerName(), this.hiredBy);
               return false;
            } else {
               ServerSender.displayHireGUI(entityplayer, this);
               return true;
            }
         } else if (this.isLocalMerchant() && !profile.villagersInQuests.containsKey(this.getVillagerId())) {
            ServerSender.sendTranslatedSentence(entityplayer, '6', "other.localmerchantinteract", this.getVillagerName());
            return false;
         } else {
            return false;
         }
      }
   }

   public boolean isChief() {
      return this.vtype.isChief;
   }

   public boolean isChild() {
      return this.vtype == null ? false : this.vtype.isChild;
   }

   // 1.12 overrode vanilla isChild (func_70631_g_) to return Mill's own child flag, so vanilla code that
   // keys off the baby state (mob tags, leashing, breeding/conversion, drop rules, walk animation) treated
   // Mill children as babies. The 26.2 port renamed that override to a plain isChild() that vanilla never
   // calls, so vanilla saw every Mill villager as an adult. Re-expose the child flag through vanilla isBaby().
   @Override
   public boolean isBaby() {
      return this.isChild();
   }

   // Millénaire scales children itself: getRecord().scale = 0.5 + size/100 is folded into the render
   // scale (RenderMillVillager.extractRenderState) and is the sole authority on visual size, matching
   // 1.12 (which had no vanilla age-based scaling). On 26.2 LivingEntity.getAgeScale() returns 0.5 when
   // isBaby() is true and feeds BOTH the render pose (state.ageScale) AND getDimensions()/the hitbox, so
   // now that isBaby() is true for children it would double-shrink them. Pin getAgeScale to 1.0 so Mill's
   // record scale stays the only size factor and the visual/hitbox size matches 1.12.
   @Override
   public float getAgeScale() {
      return 1.0F;
   }

   public boolean isForeignMerchant() {
      return this.vtype.isForeignMerchant;
   }

   public boolean isHostile() {
      return this.vtype.hostile;
   }

   public boolean isLocalMerchant() {
      return this.vtype.isLocalMerchant;
   }

   protected boolean isMovementBlocked() {
      return this.getHealth() <= 0.0F || this.isVillagerSleeping();
   }

   public boolean isReallyDead() {
      return this.isRemoved() && this.getHealth() <= 0.0F;
   }

   public boolean isSeller() {
      return this.vtype.canSell;
   }

   public boolean isTextureValid(String texture) {
      return this.vtype != null ? this.vtype.isTextureValid(texture) : true;
   }

   public boolean isVillagerSleeping() {
      // A villager with a combat target is never "asleep" — it wakes to fight (fixes lying-in-bed-while-
      // fighting). The night sleep goal can keep re-setting shouldLieDown, so gate the query on the target.
      return this.shouldLieDown && this.getTarget() == null;
   }

   public boolean isVisitor() {
      return this.vtype == null ? false : this.vtype.visitor;
   }

   private void jumpToDest() {
      Point jumpTo = WorldUtilities.findVerticalStandingPos(this.level(), this.getPathDestPoint());
      if (jumpTo != null && jumpTo.distanceTo(this.getPathDestPoint()) < 4.0) {
         if (MillConfigValues.LogPathing >= 1 && this.extraLog) {
            MillLog.major(this, "Jumping from " + this.getPos() + " to " + jumpTo);
         }

         this.setPos(jumpTo.getiX() + 0.5, jumpTo.getiY() + 0.5, jumpTo.getiZ() + 0.5);
         this.longDistanceStuck = 0;
         this.localStuck = 0;
      } else if (this.goalKey != null && Goal.goals.containsKey(this.goalKey)) {
         Goal goal = Goal.goals.get(this.goalKey);

         try {
            goal.unreachableDestination(this);
         } catch (Exception var4) {
            throw MillCrash.fail("Entity",
               "MillVillager.jumpToDest: goal.unreachableDestination failed for goal " + this.goalKey + " on " + this + ": " + var4);
         }
      }
   }

   public void killVillager() {
      if (!this.level().isClientSide() && this.level() instanceof ServerLevel) {
         for (InvItem iv : this.inventory.keySet()) {
            if (this.inventory.get(iv) > 0) {
               WorldUtilities.spawnItem(this.level(), this.getPos(), new ItemStack(iv.getItem(), this.inventory.get(iv)), 0.0F);
            }
         }

         if (this.hiredBy != null) {
            Player owner = this.millGetPlayerByName(this.hiredBy);
            if (owner != null) {
               ServerSender.sendTranslatedSentence(owner, 'f', "hire.hiredied", this.getVillagerName());
            }
         }

         VillagerRecord vr = this.getRecord();
         if (vr != null) {
            if (MillConfigValues.LogGeneralAI >= 1) {
               MillLog.major(this, this.getTownHall() + ": Villager has been killed!");
            }

            vr.killed = true;
         }

         super.discard();
      } else {
         super.discard();
      }
   }

   private void leaveVillage() {
      for (InvItem iv : this.vtype.foreignMerchantStock.keySet()) {
         this.getHouse().takeGoods(iv.getItem(), iv.meta, this.vtype.foreignMerchantStock.get(iv));
      }

      this.mw.removeVillagerRecord(this.villagerId);
      this.despawnVillager();
   }

   public void localMerchantUpdate() throws Exception {
      if (this.getHouse() != null && this.getHouse() == this.getTownHall()) {
         List<Building> buildings = this.getTownHall().getBuildingsWithTag("inn");
         Building inn = null;

         for (Building building : buildings) {
            if (building.merchantRecord == null) {
               inn = building;
            }
         }

         if (inn == null) {
            this.mw.removeVillagerRecord(this.villagerId);
            this.despawnVillager();
            MillLog.error(this, "Merchant had Town Hall as house and inn is full. Killing him.");
         } else {
            this.setHousePoint(inn.getPos());
            VillagerRecord vr = this.getRecord();
            vr.updateRecord(this);
            this.mw.registerVillagerRecord(vr, true);
            MillLog.error(this, "Merchant had Town Hall as house. Moving him to the inn.");
         }
      }
   }

   @Override
   public void die(DamageSource cause) { // 1.12 onDeath → 26.2 die
      super.die(cause);
   }

   // @Override removed: IAStarPathedEntity (Mill interface) is not yet ported so the supertype method
   // isn't visible to javac. Restore once org.millenaire.common.pathing.atomicstryker is ported.
   public void onFoundPath(List<AStarNode> result) {
      this.pathCalculatedSinceLastTick = result;
   }

   @Override
   public void aiStep() { // 1.12 onLivingUpdate → 26.2 aiStep
      super.aiStep();
      this.updateSwingTime(); // updateArmSwingProgress → updateSwingTime
      this.setFacingDirection();
      if (this.isVillagerSleeping()) {
         // 1.12 motionX/Y/Z fields → setDeltaMovement(Vec3)
         this.setDeltaMovement(Vec3.ZERO);
      }

      this.updateSleepingPose();
   }

   @Override
   public void setTarget(net.minecraft.world.entity.LivingEntity target) {
      super.setTarget(target);
      if (target != null) {
         // Entering combat must WAKE the villager SYNCHRONOUSLY — now, before the combat MoveControl drives the
         // body this very tick — otherwise it stays in the vanilla SLEEPING pose (renders lying down) AND skates
         // at combat speed (vanilla travel() never gates movement on sleep). The night sleep goal keeps
         // re-setting shouldLieDown, so clear it too. Mirrors updateSleepingPose()'s wake branch without vanilla
         // startSleeping/stopSleeping's bed-centre teleport.
         this.shouldLieDown = false;
         if (this.getPose() == Pose.SLEEPING) {
            this.setPose(Pose.STANDING);
         }
         if (this.getSleepingPos().isPresent()) {
            this.clearSleepingPos();
         }
      }
   }

   /**
    * Bridges the Mill "lie down" state to the vanilla rendering path. The client villager renderer
    * (LivingEntityRenderer) draws a sleeping entity when {@link #getPose()} is {@link Pose#SLEEPING}
    * and orients the body from {@link #getBedOrientation()} (which reads {@link #getSleepingPos()}).
    *
    * <p>This is a minimal, render-only hook: it sets/clears the synched pose + sleeping-pos so the
    * data reaches the client, but it deliberately does NOT call vanilla {@code startSleeping}/
    * {@code stopSleeping} — those teleport the villager to the bed centre, toggle the bed's OCCUPIED
    * state and feed into {@code isInWall}/stand-up logic, which would fight Mill's own movement and
    * bed AI. Mill keeps owning position/movement; we only supply what the renderer needs.</p>
    */
   private void updateSleepingPose() {
      if (this.isVillagerSleeping()) {
         // Reference cell the villager is sleeping at — its goal destination if set, else its own pos
         // (mirrors getBedOrientationInDegrees). Point at the bed so getBedOrientation() resolves.
         Point ref = this.getGoalDestPoint() != null ? this.getGoalDestPoint() : this.getPos();
         if (ref != null) {
            BlockPos bedPos = ref.getBlockPos();
            if (this.getSleepingPos().map(p -> !p.equals(bedPos)).orElse(true)) {
               this.setSleepingPos(bedPos);
            }
         }

         if (this.getPose() != Pose.SLEEPING) {
            this.setPose(Pose.SLEEPING);
         }
      } else {
         if (this.getPose() == Pose.SLEEPING) {
            this.setPose(Pose.STANDING);
         }

         if (this.getSleepingPos().isPresent()) {
            this.clearSleepingPos();
         }
      }
   }

   // @Override removed: see onFoundPath note (IAStarPathedEntity un-ported).
   public void onNoPathAvailable() {
      this.pathFailedSincelastTick = true;
   }

   @Override
   public void tick() { // 1.12 onUpdate → 26.2 tick
      long startTime = System.nanoTime();
      if (this.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
         this.despawnVillagerSilent();
      }
      // NewAI: keep the navigation's danger-aware cost up to date (server-side only). Side-effect-free w.r.t.
      // the legacy tick that follows — it only tunes path cost. No-op when the flag is off.
      if (org.millenaire.common.config.MillConfigValues.NewAI && !this.level().isClientSide()) {
         this.updateNewAiDanger();
         // Tactical combat layer ONLY (the piece Mill lacks): when a target exists, BehaviourCombat drives
         // smart positioning + ranged/melee. It's additive — when there's no target it does nothing, so the
         // legacy goal movement/economy below is untouched (seamless). Not GoTo/Wander (those would fight the
         // legacy movement); those move in once the legacy mover is fully replaced.
         if (this.millAI == null) {
            // Priority order emerges from each behaviour's priority(): Combat(50) > GoToPoint(10) > Wander(0).
            // GoToPoint drives the villager to the Mill goal's dest point via the multi-objective navigation
            // (the legacy A* mover is gated off above), Wander is the safe idle fallback, Combat preempts both.
            // Priorities: EscapeFluid(lava 100 / water 60) > EscapePit(55) > Combat(50) > GoToPoint(10) > Wander(0).
            this.millAI = new org.millenaire.common.ai.MillAI(java.util.List.of(
               new org.millenaire.common.ai.behaviours.BehaviourEscapeFluid(),
               // Value-field unified escape (Phase 2) — active only under ValueFieldNav; supersedes the legacy
               // BehaviourEscapePit + GoToPoint detour via the G0-feasibility certificate + policy-gated G1.
               new org.millenaire.common.ai.behaviours.BehaviourValueFieldEscape(),
               new org.millenaire.common.ai.behaviours.BehaviourEscapePit(),
               new org.millenaire.common.ai.behaviours.BehaviourCombat(),
               new org.millenaire.common.ai.behaviours.BehaviourGoToPoint(),
               new org.millenaire.common.ai.behaviours.BehaviourWander()));
         }
         this.millAI.tick(this);
      }

      try {
         // mw is set in the ctor from Mill.getMillWorld(level), but that returns null if the level wasn't
         // registered with Mill yet when the entity spawned (client just joined / clientWorld not built).
         // Re-resolve lazily each tick so getProfile/unlock/interact stop NPEing on a null mw.
         if (this.mw == null) {
            this.mw = Mill.getMillWorld(this.level());
         }

         if (this.vtype == null) {
            // CLIENT: a freshly tracked villager has no type until the villager-data packet (id=3)
            // arrives a tick or two later. The 1.12 code killed it immediately, which on 26.2 removed
            // EVERY client villager before its data synced -> villagers were invisible. On the client,
            // just skip the tick and wait for the sync; only the SERVER treats a null type as a real
            // error worth removing.
            if (!this.level().isClientSide() && !this.isRemoved()) {
               MillLog.error(this, "Unknown villager type. Killing him.");
               this.despawnVillagerSilent();
            }

            return;
         }

         if (this.pathFailedSincelastTick) {
            this.pathFailedSinceLastTick();
         }

         // Legacy A* path application (bridge to vanilla nav). Under NewAI the new navigation does its own
         // multi-objective pathing via moveTo(dest) (BehaviourGoToPoint), so skip the bridge.
         if (this.pathCalculatedSinceLastTick != null && !org.millenaire.common.config.MillConfigValues.NewAI) {
            this.applyPathCalculatedSinceLastTick();
         }

         if (this.level().isClientSide()) {
            super.tick();
            return;
         }

         if (this.isRemoved()) {
            super.tick();
            return;
         }

         if (Math.abs(this.level().getOverworldClockTime() + this.hashCode()) % 10L == 2L) {
            this.sendVillagerPacket();
         }

         if (Math.abs(this.level().getOverworldClockTime() + this.hashCode()) % 40L == 4L) {
            this.unlockForNearbyPlayers();
         }

         if (this.hiredBy != null) {
            this.updateHired();
            super.tick();
            return;
         }

         if (this.getTownHall() == null || this.getHouse() == null) {
            if (MillLog.debugOn() && (this.level().getOverworldClockTime() + this.getVillagerId()) % 200L == 0L) {
               MillLog.milldebug("Goal", "id=" + this.getVillagerId() + " type="
                  + (this.vtype != null ? this.vtype.key : "null") + " SKIPPED AI@2180: thPoint=" + this.townHallPoint
                  + " ->th=" + (this.townHallPoint != null && this.mw != null ? this.mw.getBuilding(this.townHallPoint) : "n/a")
                  + " | housePoint=" + this.housePoint
                  + " ->house=" + (this.housePoint != null && this.mw != null ? this.mw.getBuilding(this.housePoint) : "n/a"));
            }
            return;
         }

         if (this.getTownHall() != null && !this.getTownHall().isActive) {
            if (MillLog.debugOn() && (this.level().getOverworldClockTime() + this.getVillagerId()) % 200L == 0L) {
               MillLog.milldebug("Goal", "id=" + this.getVillagerId() + " SKIPPED AI@2184: townhall "
                  + this.getTownHall().getPos() + " is NOT active (isActive=false)");
            }
            return;
         }

         if (this.getPos().distanceTo(this.getTownHall().getPos()) > this.getTownHall().villageType.radius + 100) {
            MillLog.error(this, "Villager is far away from village. Despawning him.");
            this.despawnVillagerSilent();
         }

         try {
            this.timer++;
            if (this.getHealth() < this.getMaxHealth() & MillCommonUtilities.randomInt(1600) == 0) {
               this.setHealth(this.getHealth() + 1.0F);
            }

            this.detrampleCrops();
            this.allowRandomMoves = true;
            this.stopMoving = false;
            if (this.getTownHall() == null || this.getHouse() == null) {
               if (MillLog.debugOn() && (this.level().getOverworldClockTime() + this.getVillagerId()) % 200L == 0L) {
                  MillLog.milldebug("Goal", "id=" + this.getVillagerId() + " type="
                     + (this.vtype != null ? this.vtype.key : "null") + " SKIPPED AI (no townhall/house): "
                     + "thPoint=" + this.townHallPoint + " ->th=" + (this.townHallPoint != null && this.mw != null ? this.mw.getBuilding(this.townHallPoint) : "n/a")
                     + " | housePoint=" + this.housePoint + " ->house=" + (this.housePoint != null && this.mw != null ? this.mw.getBuilding(this.housePoint) : "n/a")
                     + " | mwNull=" + (this.mw == null));
               }
               super.tick();
               return;
            }

            if (Goal.beSeller.key.equals(this.goalKey)) {
               this.townHall.seller = this;
            } else if (Goal.getResourcesForBuild.key.equals(this.goalKey) || Goal.construction.key.equals(this.goalKey)) {
               if (MillConfigValues.LogTileEntityBuilding >= 3) {
                  MillLog.debug(this, "Registering as builder for: " + this.townHall);
               }

               if (this.constructionJobId > -1 && this.townHall.getConstructionsInProgress().size() > this.constructionJobId) {
                  this.townHall.getConstructionsInProgress().get(this.constructionJobId).setBuilder(this);
               }
            }

            if (this.getTownHall().underAttack) {
               if (this.goalKey == null
                  || !this.goalKey.equals(Goal.raidVillage.key) && !this.goalKey.equals(Goal.defendVillage.key) && !this.goalKey.equals(Goal.hide.key)) {
                  this.clearGoal();
               }

               if (this.isRaider) {
                  this.goalKey = Goal.raidVillage.key;
                  this.targetDefender();
               } else if (this.helpsInAttacks()) {
                  this.goalKey = Goal.defendVillage.key;
                  this.targetRaider();
               } else {
                  this.goalKey = Goal.hide.key;
               }

               this.checkGoals();
            }

            if (this.getTarget() == null) {
               if (this.isHostile()
                  && this.level().getDifficulty() != Difficulty.PEACEFUL
                  && this.getTownHall().closestPlayer != null
                  && this.getPos().distanceTo(this.getTownHall().closestPlayer) <= 80.0) {
                  int range = 80;
                  if (this.vtype.isDefensive) {
                     range = 20;
                  }

                  this.setTarget(this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), range, true));
                  this.clearGoal();
               }
            } else {
               if (this.vtype.isDefensive && this.getPos().distanceTo(this.getHouse().getResManager().getDefendingPos()) > 20.0) {
                  this.setTarget(null);
               } else if (!this.getTarget().isAlive()
                  || this.getPos().distanceTo(this.getTarget()) > 80.0
                  || this.level().getDifficulty() == Difficulty.PEACEFUL && this.getTarget() instanceof Player) {
                  this.setTarget(null);
               }

               if (this.getTarget() != null) {
                  this.shouldLieDown = false;
                  // Under NewAI the combat BEHAVIOUR owns attacking + movement. The legacy attackEntity +
                  // setPathDestPoint(target) here re-stopped the navigation EVERY tick (setPathDestPoint calls
                  // getNavigation().stop() whenever the moving target's point changes by > tolerance), which
                  // froze the villager mid-step → "呆呆看著". Skip the legacy attack/move under NewAI.
                  if (!org.millenaire.common.config.MillConfigValues.NewAI) {
                     this.attackEntity(this.getTarget());
                     // attackEntity may clear the target (kill/flee), so re-read it and null-check before
                     // calling onGround() — the 1.12 code re-read getTarget() each line and NPE'd here every
                     // tick once the target died ("Error in onUpdate()").
                     LivingEntity currentTarget = this.getTarget();
                     if (currentTarget != null) {
                        if (currentTarget.onGround()) {
                           this.setPathDestPoint(new Point(currentTarget), 1);
                        } else {
                           Point posToAttack = new Point(currentTarget);

                           while (posToAttack.y > 0.0 && posToAttack.isBlockPassable(this.level())) {
                              posToAttack = posToAttack.getBelow();
                           }

                           if (posToAttack != null) {
                              this.setPathDestPoint(posToAttack.getAbove(), 3);
                           }
                        }
                     }
                  }
               }
            }

            if (this.getTarget() != null) {
               // setGoalDestPoint feeds the legacy stuck/stop tracking — skip under NewAI (the new navigation
               // drives movement); still equip the weapon so combat looks/works right.
               if (!org.millenaire.common.config.MillConfigValues.NewAI) {
                  this.setGoalDestPoint(new Point(this.getTarget()));
               }
               this.heldItem = this.getWeapon();
               this.heldItemOffHand = ItemStack.EMPTY;
               if (this.goalKey != null && !Goal.goals.get(this.goalKey).isFightingGoal()) {
                  this.clearGoal();
               }
            } else if (!this.getTownHall().underAttack) {
               if (this.level().isBrightOutside()) {
                  this.speakSentence("greeting", 12000, 3, 10);
                  this.nightActionPerformed = false;
                  List<InvItem> goods = this.getGoodsToCollect();
                  if (goods != null && (this.level().getOverworldClockTime() + this.getVillagerId()) % 20L == 0L) {
                     ItemEntity item = this.getClosestItemVertical(goods, 5, 30);
                     if (item != null) {
                        item.discard();
                        if (item.getItem().getItem() == Blocks.OAK_SAPLING.asItem()) {
                           this.addToInv(item.getItem().getItem(), item.getItem().getDamageValue() & 3, 1);
                        } else {
                           this.addToInv(item.getItem().getItem(), item.getItem().getDamageValue(), 1);
                        }
                     }
                  }

                  this.specificUpdate();
                  if (!this.isRaider) {
                     if (this.goalKey == null) {
                        this.setNextGoal();
                     }

                     if (this.goalKey != null) {
                        this.checkGoals();
                     } else {
                        this.shouldLieDown = false;
                     }
                  }
               } else if (!this.isRaider) {
                  if (this.goalKey == null) {
                     this.setNextGoal();
                  }

                  if (this.goalKey != null) {
                     this.checkGoals();
                  } else {
                     this.shouldLieDown = false;
                  }
               }
            }

            if (this.getPathDestPoint() != null && this.pathEntity != null && this.pathEntity.getNodeCount() > 0 && !this.stopMoving) {
               // 1.12 parity: there, AS_PathEntity extended vanilla Path, and the custom
               // PathNavigateSimple advanced the SAME path object as the villager walked, so
               // getCurrentTargetPathPoint()/getNextTargetPathPoint() tracked real progress. On 26.2
               // movement is delegated to vanilla navigation which advances its OWN Path copy (built
               // from the same Node[]), leaving pathEntity's index frozen at 0. That froze nextPoint at
               // the start node, so as the villager walked AWAY localStuck climbed +4/tick and warped it
               // back (the "random teleport while not stuck" bug). Sync our index from vanilla's here.
               net.minecraft.world.level.pathfinder.Path vanillaPath = this.getNavigation().getPath();
               if (vanillaPath != null) {
                  int vanillaIndex = vanillaPath.getNextNodeIndex();
                  if (vanillaIndex < 0) {
                     vanillaIndex = 0;
                  }
                  if (vanillaIndex > this.pathEntity.getNodeCount()) {
                     vanillaIndex = this.pathEntity.getNodeCount();
                  }
                  if (vanillaIndex != this.pathEntity.getCurrentPathIndex()) {
                     this.pathEntity.setCurrentPathIndex(vanillaIndex);
                  }
               }

               double olddistance = this.prevPoint.horizontalDistanceToSquared(this.getPathDestPoint());
               double newdistance = this.getPos().horizontalDistanceToSquared(this.getPathDestPoint());
               if (olddistance - newdistance < 2.0E-4) {
                  this.longDistanceStuck++;
               } else {
                  this.longDistanceStuck--;
               }

               if (this.longDistanceStuck < 0) {
                  this.longDistanceStuck = 0;
               }

               if (this.pathEntity != null && this.pathEntity.getNodeCount() > 1 && MillConfigValues.LogPathing >= 2 && this.extraLog) {
                  MillLog.minor(
                     this,
                     "Stuck: "
                        + this.longDistanceStuck
                        + " pos "
                        + this.getPos()
                        + " node: "
                        + this.pathEntity.getCurrentTargetPathPoint()
                        + " next node: "
                        + this.pathEntity.getNextTargetPathPoint()
                        + " dest: "
                        + this.getPathDestPoint()
                  );
               }

               // Legacy long-distance-stuck teleport. Under the new AI (req 1: never warp) this is suppressed
               // — the new navigation re-plans / the goal layer abandons instead. Everything else in this tick
               // (goal selection, performAction, building/economy/social) is untouched, so integration stays
               // seamless with the rest of Mill.
               if (!org.millenaire.common.config.MillConfigValues.NewAI
                  && this.longDistanceStuck > 3000 && (!this.vtype.noTeleport || this.getRecord() != null && this.getRecord().raidingVillage)) {
                  this.jumpToDest();
               }

               Node nextPoint = this.pathEntity.getNextTargetPathPoint();
               if (nextPoint != null) {
                  olddistance = this.prevPoint.distanceToSquared(nextPoint);
                  newdistance = this.getPos().distanceToSquared(nextPoint);
                  if (olddistance - newdistance < 2.0E-4) {
                     this.localStuck += 4;
                  } else {
                     this.localStuck--;
                  }

                  if (this.localStuck < 0) {
                     this.localStuck = 0;
                  }

                  if (this.localStuck > 30) {
                     this.getNavigation().stop();
                     this.pathEntity = null;
                  }

                  // Legacy local-stuck warp to the next node — suppressed under the new AI (req 1: never warp).
                  if (!org.millenaire.common.config.MillConfigValues.NewAI && this.localStuck > 100) {
                     this.setPos(nextPoint.x + 0.5, nextPoint.y + 0.5, nextPoint.z + 0.5);
                     this.localStuck = 0;
                  }
               }
            } else {
               this.longDistanceStuck = 0;
               this.localStuck = 0;
            }

            // Legacy: kick the Mill A* planner toward the dest. Under NewAI the navigation paths there
            // itself (multi-objective cost) via BehaviourGoToPoint, so the old planner is not used.
            if (this.getPathDestPoint() != null && !this.stopMoving && !org.millenaire.common.config.MillConfigValues.NewAI) {
               this.updatePathIfNeeded(this.getPathDestPoint());
            }

            // Legacy: stop the nav when "stopMoving" or the JPS planner is busy. Under NewAI the new engine
            // owns the navigation (and the JPS planner is gated off), so this would just freeze combat/movement.
            if (!org.millenaire.common.config.MillConfigValues.NewAI && (this.stopMoving || this.pathPlannerJPS.isBusy())) {
               this.getNavigation().stop();
               this.pathEntity = null;
            }

            this.prevPoint = this.getPos();
            if (this.canVillagerClearLeaves() && Math.abs(this.level().getOverworldClockTime() + this.hashCode()) % 10L == 6L) {
               this.handleLeaveClearing();
            }

            this.handleDoorsAndFenceGates();
            if (System.currentTimeMillis() - this.timeSinceLastPathingTimeDisplay > 10000L) {
               if (this.pathingTime > 500L) {
                  if (this.getPathDestPoint() != null) {
                     MillLog.warning(
                        this,
                        "Pathing time in last 10 secs: "
                           + this.pathingTime
                           + " dest: "
                           + this.getPathDestPoint()
                           + " dest bid: "
                           + WorldUtilities.getBlock(this.level(), this.getPathDestPoint())
                           + " above bid: "
                           + WorldUtilities.getBlock(this.level(), this.getPathDestPoint().getAbove())
                     );
                  } else {
                     MillLog.warning(this, "Pathing time in last 10 secs: " + this.pathingTime + " null dest point.");
                  }

                  MillLog.warning(
                     this,
                     "nbPathsCalculated: "
                        + this.nbPathsCalculated
                        + " nbPathNoStart: "
                        + this.nbPathNoStart
                        + " nbPathNoEnd: "
                        + this.nbPathNoEnd
                        + " nbPathAborted: "
                        + this.nbPathAborted
                        + " nbPathFailure: "
                        + this.nbPathFailure
                  );
                  if (this.goalKey != null) {
                     MillLog.warning(this, "Current goal: " + Goal.goals.get(this.goalKey));
                  }
               }

               this.timeSinceLastPathingTimeDisplay = System.currentTimeMillis();
               this.pathingTime = 0L;
               this.nbPathsCalculated = 0;
               this.nbPathNoStart = 0;
               this.nbPathNoEnd = 0;
               this.nbPathAborted = 0;
               this.nbPathFailure = 0;
            }
         } catch (MillLog.MillenaireException var8) {
            throw MillCrash.fail("Entity", "MillVillager.tick goal/pathing body failed for " + this.getVillagerName(), var8);
         } catch (Exception var9) {
            throw MillCrash.fail("Entity", "MillVillager.tick goal/pathing body failed for " + this.getVillagerName(), var9);
         }

         if (Math.abs(this.level().getOverworldClockTime() + this.hashCode()) % 10L == 5L) {
            this.triggerMobAttacks();
         }

         this.updateDialogue();
         this.isUsingBow = false;
         this.isUsingHandToHand = false;
         super.tick();
         if (MillConfigValues.DEV) {
            if (this.getPathDestPoint() != null && !this.pathPlannerJPS.isBusy() && this.pathEntity == null) {
            }

            if (this.getPathDestPoint() != null && this.getGoalDestPoint() != null && this.getPathDestPoint().distanceTo(this.getGoalDestPoint()) > 20.0) {
            }
         }
      } catch (Exception var10) {
         throw MillCrash.fail("Entity", "MillVillager.tick() failed for villager " + this, var10);
      }

      if (this.getTownHall() != null) {
         this.mw.reportTime(this.getTownHall(), System.nanoTime() - startTime, true);
      }
   }

   private boolean openFenceGate(int i, int j, int k) {
      Point p = new Point(i, j, k);
      BlockState state = p.getBlockActualState(this.level());
      if (BlockItemUtilities.isFenceGate(state.getBlock()) && !(Boolean)state.getValue(FenceGateBlock.OPEN)) {
         p.setBlockState(this.level(), state.setValue(FenceGateBlock.OPEN, true));
      }

      return true;
   }

   private void pathFailedSinceLastTick() {
      // Legacy "path failed → warp to destination". Suppressed under the new AI (req 1: never warp); the new
      // navigation simply re-plans or the goal layer abandons. Seamless: nothing else here changes.
      if (!org.millenaire.common.config.MillConfigValues.NewAI
         && (!this.vtype.noTeleport || this.getRecord() != null && this.getRecord().raidingVillage)) {
         this.jumpToDest();
      }

      this.pathFailedSincelastTick = false;
   }

   public boolean performNightAction() {
      if (this.getRecord() != null && this.getHouse() != null && this.getTownHall() != null) {
         if (this.isChild()) {
            if (this.getSize() < 20) {
               this.growSize();
            } else {
               this.teenagerNightAction();
            }
         }

         if (this.getHouse().hasVisitors) {
            this.visitorNightAction();
         }

         return this.hasChildren() ? this.attemptChildConception() : true;
      } else {
         return false;
      }
   }

   @Override
   protected net.minecraft.world.InteractionResult mobInteract(Player player, InteractionHand hand) {
      // 26.2 routes entity right-clicks through mobInteract; the 1.12 interaction logic lives in
      // processInteract, which was never wired — so villagers couldn't be talked to / traded with /
      // hired. Delegate to it (it guards client vs server internally and opens GUIs via the 104 packet
      // flow server-side) and report SUCCESS so the click is consumed and sent to the server.
      boolean handled = this.processInteract(player, hand);
      return handled ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
   }

   public boolean processInteract(Player entityplayer, InteractionHand hand) {
      if (this.isVillagerSleeping()) {
         return true;
      } else {
         MillAdvancements.FIRST_CONTACT.grant(entityplayer);
         if (this.vtype != null && (this.vtype.key.equals("indian_sadhu") || this.vtype.key.equals("alchemist"))) {
            MillAdvancements.MAITRE_A_PENSER.grant(entityplayer);
         }

         if (this.level().isClientSide()) {
            return true;
         } else {
            if (MillLog.debugOn()) {
               MillLog.milldebug(
                  "Interaction",
                  "player=" + entityplayer.getName().getString() + " right-clicked villager id=" + this.getVillagerId()
                     + " type=" + (this.vtype != null ? this.vtype.key : "null") + " name='" + this.getVillagerName() + "' hand=" + hand
               );
            }

            UserProfile profile = this.mw.getProfile(entityplayer);
            if (profile.villagersInQuests.containsKey(this.getVillagerId())) {
               QuestInstance qi = profile.villagersInQuests.get(this.getVillagerId());
               if (qi.getCurrentVillager().id == this.getVillagerId()) {
                  ServerSender.displayQuestGUI(entityplayer, this);
               } else {
                  this.interactSpecial(entityplayer);
               }
            } else {
               this.interactSpecial(entityplayer);
            }

            if (MillConfigValues.DEV) {
               this.interactDev(entityplayer);
            }

            return true;
         }
      }
   }

   public int putInBuilding(Building building, Item item, int meta, int nb) {
      nb = this.takeFromInv(item, meta, nb);
      building.storeGoods(item, meta, nb);
      return nb;
   }

   // 26.2: 1.12 readEntityFromNBT(CompoundTag) → readAdditionalSaveData(ValueInput). Ported to read
   // fields directly off ValueInput; Point fields via readPoint(), the inventory ListTag via a
   // CompoundTag wrapper stored under CompoundTag.CODEC (see addAdditionalSaveData).
   @Override
   protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
      super.readAdditionalSaveData(input);
      String type = input.getStringOr("vtype", "");
      String culture = input.getStringOr("culture", "");
      if (Culture.getCultureByName(culture) != null) {
         if (Culture.getCultureByName(culture).getVillagerType(type) != null) {
            this.vtype = Culture.getCultureByName(culture).getVillagerType(type);
         } else {
            MillLog.error(this, "Could not load dynamic NPC: unknown type: " + type + " in culture: " + culture);
         }
      } else {
         MillLog.error(this, "Could not load dynamic NPC: unknown culture: " + culture);
      }

      this.texture = Identifier.fromNamespaceAndPath("millenaire", input.getStringOr("texture", ""));
      this.housePoint = TileEntityLockedChest.readPoint(input, "housePos");
      if (this.housePoint == null) {
         MillLog.error(this, "Error when loading villager: housePoint null");
         Mill.proxy.sendChatAdmin(this.getVillagerName() + ": Could not load house position. Check millenaire.log");
      }

      this.townHallPoint = TileEntityLockedChest.readPoint(input, "townHallPos");
      if (this.townHallPoint == null) {
         MillLog.error(this, "Error when loading villager: townHallPoint null");
         Mill.proxy.sendChatAdmin(this.getVillagerName() + ": Could not load town hall position. Check millenaire.log");
      }

      this.setGoalDestPoint(TileEntityLockedChest.readPoint(input, "destPoint"));
      this.setPathDestPoint(TileEntityLockedChest.readPoint(input, "pathDestPoint"), 0);
      this.setGoalBuildingDestPoint(TileEntityLockedChest.readPoint(input, "destBuildingPoint"));
      this.prevPoint = TileEntityLockedChest.readPoint(input, "prevPoint");
      this.doorToClose = TileEntityLockedChest.readPoint(input, "doorToClose");
      this.action = input.getIntOr("action", 0);
      this.goalKey = input.getStringOr("goal", "");
      if (this.goalKey.trim().length() == 0) {
         this.goalKey = null;
      }

      if (this.goalKey != null && !Goal.goals.containsKey(this.goalKey)) {
         this.goalKey = null;
      }

      this.constructionJobId = input.getIntOr("constructionJobId", 0);
      this.dialogueKey = input.getStringOr("dialogueKey", "");
      this.dialogueStart = input.getLongOr("dialogueStart", 0L);
      this.dialogueRole = input.getIntOr("dialogueRole", 0);
      this.dialogueColour = (char)input.getIntOr("dialogueColour", 0);
      this.dialogueChat = input.getBooleanOr("dialogueChat", false);
      if (this.dialogueKey.trim().length() == 0) {
         this.dialogueKey = null;
      }

      this.familyName = input.getStringOr("familyName", "");
      this.firstName = input.getStringOr("firstName", "");
      this.gender = input.getIntOr("gender", 0);
      if (input.contains("villager_lid")) {
         this.setVillagerId(Math.abs(input.getLongOr("villager_lid", 0L)));
      }

      if (!this.isTextureValid(this.texture.getPath())) {
         Identifier newTexture = this.vtype.getNewTexture();
         MillLog.major(this, "Texture " + this.texture.getPath() + " cannot be found, replacing it with " + newTexture.getPath());
         this.texture = newTexture;
      }

      net.minecraft.nbt.CompoundTag invWrapper = input.read("inventoryNew", net.minecraft.nbt.CompoundTag.CODEC).orElse(null);
      if (invWrapper != null) {
         MillCommonUtilities.readInventory(invWrapper.getListOrEmpty("l"), this.inventory);
      }
      this.previousBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(input.getIntOr("previousBlock", 0));
      this.previousBlockMeta = input.getIntOr("previousBlockMeta", 0);
      this.hiredBy = input.getStringOr("hiredBy", "");
      this.hiredUntil = input.getLongOr("hiredUntil", 0L);
      this.aggressiveStance = input.getBooleanOr("aggressiveStance", false);
      this.isRaider = input.getBooleanOr("isRaider", false);
      this.visitorNbNights = input.getIntOr("visitorNbNights", 0);
      if (this.hiredBy.equals("")) {
         this.hiredBy = null;
      }

      if (input.contains("clothTexture")) {
         this.clothTexture[0] = Identifier.fromNamespaceAndPath("millenaire", input.getStringOr("clothTexture", ""));
      } else {
         for (int i = 0; i < 2; i++) {
            if (input.getStringOr("clothTexture_" + i, "").length() > 0) {
               String texture = input.getStringOr("clothTexture_" + i, "");
               if (texture.contains(":")) {
                  this.clothTexture[i] = Identifier.parse(texture);
               } else {
                  this.clothTexture[i] = Identifier.fromNamespaceAndPath("millenaire", texture);
               }
            } else {
               this.clothTexture[i] = null;
            }
         }
      }

      this.clothName = input.getStringOr("clothName", "");
      if (this.clothName.equals("")) {
         this.clothName = null;

         for (int ix = 0; ix < 2; ix++) {
            this.clothTexture[ix] = null;
         }
      }

      this.updateClothTexturePath();

      if (MillLog.debugOn()) {
         MillLog.milldebug(
            "Villager",
            "LOADED from NBT id=" + this.getVillagerId() + " type=" + (this.vtype != null ? this.vtype.key : "null")
               + " name='" + this.firstName + " " + this.familyName + "'"
               + " gender=" + this.gender + " townHall=" + this.townHallPoint + " house=" + this.housePoint
               + " pos=" + this.getX() + "/" + this.getY() + "/" + this.getZ() + " texture=" + this.texture
         );
      }
   }

   /**
    * Dead in 26.2: villager NBT load now flows through {@link #readAdditionalSaveData} (ValueInput) and
    * save through {@link #addAdditionalSaveData} (ValueOutput). This 1.12-style method has no callers and
    * is retained only as a no-op so any stale references compile.
    */
   public void readFromNBT(CompoundTag compound) {
   }

   public void readSpawnData(ByteBuf ds) {
      FriendlyByteBuf data = new FriendlyByteBuf(ds);

      // Phase-2 FLAG (spawn-data): the client genuinely receives this packet, but optional fields are already
      // handled by the readNullable* helpers returning null. A thrown IOException is a real buffer/parse failure
      // that would leave the villager half-initialised; swallowing it hid that. Surface it.
      try {
         this.setVillagerId(data.readLong());
         this.readVillagerStreamdata(data);
      } catch (IOException var4) {
         throw MillCrash.fail("Entity", "MillVillager.readSpawnData: spawn-packet parse failed for " + this + ": " + var4);
      }
   }

   private void readVillagerStreamdata(FriendlyByteBuf data) throws IOException {
      Culture culture = Culture.getCultureByName(StreamReadWrite.readNullableString(data));
      String vt = StreamReadWrite.readNullableString(data);
      if (culture != null) {
         this.vtype = culture.getVillagerType(vt);
      }

      this.texture = StreamReadWrite.readNullableResourceLocation(data);
      this.goalKey = StreamReadWrite.readNullableString(data);
      this.constructionJobId = data.readInt();
      this.housePoint = StreamReadWrite.readNullablePoint(data);
      this.townHallPoint = StreamReadWrite.readNullablePoint(data);
      this.firstName = StreamReadWrite.readNullableString(data);
      this.familyName = StreamReadWrite.readNullableString(data);
      this.gender = data.readInt();
      this.hiredBy = StreamReadWrite.readNullableString(data);
      this.aggressiveStance = data.readBoolean();
      this.hiredUntil = data.readLong();
      this.isUsingBow = data.readBoolean();
      this.isUsingHandToHand = data.readBoolean();
      this.isRaider = data.readBoolean();
      this.speech_key = StreamReadWrite.readNullableString(data);
      this.speech_variant = data.readInt();
      this.speech_started = data.readLong();
      this.heldItem = StreamReadWrite.readNullableItemStack(data);
      this.heldItemOffHand = StreamReadWrite.readNullableItemStack(data);
      this.inventory = StreamReadWrite.readInventory(data);
      this.clothName = StreamReadWrite.readNullableString(data);

      for (int i = 0; i < 2; i++) {
         this.clothTexture[i] = StreamReadWrite.readNullableResourceLocation(data);
      }

      this.setGoalDestPoint(StreamReadWrite.readNullablePoint(data));
      this.shouldLieDown = data.readBoolean();
      this.dialogueTargetFirstName = StreamReadWrite.readNullableString(data);
      this.dialogueTargetLastName = StreamReadWrite.readNullableString(data);
      this.dialogueColour = data.readChar();
      this.dialogueChat = data.readBoolean();
      this.setHealth(data.readFloat());
      this.visitorNbNights = data.readInt();
      UUID uuid = StreamReadWrite.readNullableUUID(data);
      if (uuid != null) {
         Entity targetEntity = WorldUtilities.getEntityByUUID(this.level(), uuid);
         if (targetEntity != null && targetEntity instanceof LivingEntity) {
            this.setTarget((LivingEntity)targetEntity);
         } else {
            this.setTarget(null);
         }
      } else {
         this.setTarget(null);
      }

      int nbMerchantSells = data.readInt();
      if (nbMerchantSells > -1) {
         this.merchantSells.clear();

         for (int i = 0; i < nbMerchantSells; i++) {
            // Phase-2 FLAG (spawn-data): a swallow here desyncs the rest of the FriendlyByteBuf (the int read
            // after the good would be skipped), corrupting every subsequent field. readNullableGoods throwing
            // is a real resolve bug, not optional-on-client data, so surface it.
            try {
               TradeGood g = StreamReadWrite.readNullableGoods(data, culture);
               this.merchantSells.put(g, data.readInt());
            } catch (MillLog.MillenaireException var9) {
               throw MillCrash.fail("Entity",
                  "MillVillager.readVillagerStreamdata: failed reading merchant-sell good (culture="
                     + (culture != null ? culture.key : "null") + ") for " + this + ": " + var9);
            }
         }
      }

      int goalDestEntityID = data.readInt();
      if (goalDestEntityID != -1) {
         Entity ent = this.level().getEntity(goalDestEntityID);
         if (ent != null) {
            this.setGoalDestEntity(ent);
         }
      }

      this.isDeadOnServer = data.readBoolean();
      this.client_lastupdated = this.level().getOverworldClockTime();
   }

   public void registerNewPath(AS_PathEntity path) throws Exception {
      if (path == null) {
         boolean handled = false;
         if (this.goalKey != null) {
            Goal goal = Goal.goals.get(this.goalKey);
            handled = goal.unreachableDestination(this);
         }

         if (!handled) {
            this.clearGoal();
         }
      } else {
         try {
            this.getNavigation().moveTo(millToVanillaPath(path), 0.5);
         } catch (Exception var4) {
            throw MillCrash.fail("Entity",
               "MillVillager.registerNewPath: navigation.moveTo failed for goal " + this.goalKey
                  + " path to " + this.pathDestPoint + " on " + this + ": " + var4);
         }

         this.pathEntity = path;
         this.xxa = 0.0F;
      }
   }

   public void registerNewPath(List<Node> result) throws Exception {
      AS_PathEntity path = null;
      if (result != null) {
         Node[] pointsCopy = new Node[result.size()];
         int i = 0;

         for (Node p : result) {
            if (p == null) {
               pointsCopy[i] = null;
            } else {
               Node p2 = new Node(p.x, p.y, p.z);
               pointsCopy[i] = p2;
            }

            i++;
         }

         path = new AS_PathEntity(pointsCopy);
      }

      this.registerNewPath(path);
   }

   public HashMap<InvItem, Integer> requiresGoods() {
      if (this.isChild() && this.getSize() < 20) {
         return this.vtype.requiredFoodAndGoods;
      } else {
         return this.hasChildren() && this.getHouse() != null && this.getHouse().getKnownVillagers().size() < 4
            ? this.vtype.requiredFoodAndGoods
            : this.vtype.requiredGoods;
      }
   }

   private void sendVillagerPacket() {
      FriendlyByteBuf data = ServerSender.getPacketBuffer();

      try {
         data.writeInt(3);
         this.writeVillagerStreamData(data, false);
      } catch (IOException var3) {
         throw MillCrash.fail("Entity", "MillVillager.sendVillagerPacket: stream-data write failed for " + this + ": " + var3);
      }

      ServerSender.sendPacketToPlayersInRange(data, this.level(), this.getPos(), 100);
   }

   public boolean setBlock(Point p, Block block) {
      return WorldUtilities.setBlock(this.level(), p, block, true, true);
   }

   public boolean setBlockAndMetadata(Point p, Block block, int metadata) {
      return WorldUtilities.setBlockAndMetadata(this.level(), p, block, metadata, true, true);
   }

   public boolean setBlockMetadata(Point p, int metadata) {
      return WorldUtilities.setBlockMetadata(this.level(), p, metadata);
   }

   public boolean setBlockstate(Point p, BlockState bs) {
      return WorldUtilities.setBlockstate(this.level(), p, bs, true, true);
   }

   public void setDead() {
      if (this.getHealth() <= 0.0F) {
         this.killVillager();
      }

      super.discard();
   }

   private void setFacingDirection() {
      if (this.getTarget() != null) {
         this.faceEntityMill(this.getTarget(), 30.0F, 30.0F);
      } else {
         if (this.goalKey != null && (this.getGoalDestPoint() != null || this.getGoalDestEntity() != null)) {
            Goal goal = Goal.goals.get(this.goalKey);
            if (goal.lookAtGoal()) {
               if (this.getGoalDestEntity() != null && this.getPos().distanceTo(this.getGoalDestEntity()) < goal.range(this)) {
                  this.faceEntityMill(this.getGoalDestEntity(), 10.0F, 10.0F);
               } else if (this.getGoalDestPoint() != null && this.getPos().distanceTo(this.getGoalDestPoint()) < goal.range(this)) {
                  this.facePoint(this.getGoalDestPoint(), 10.0F, 10.0F);
               }
            }

            if (goal.lookAtPlayer()) {
               Player player = this.level().getNearestPlayer(this, 10.0);
               if (player != null) {
                  this.faceEntityMill(player, 10.0F, 10.0F);
                  return;
               }
            }
         }
      }
   }

   public void setGoalBuildingDestPoint(Point newDest) {
      if (this.goalInformation == null) {
         this.goalInformation = new Goal.GoalInformation(null, null, null);
      }

      this.goalInformation.setDestBuildingPos(newDest);
   }

   public void setGoalDestEntity(Entity ent) {
      if (this.goalInformation == null) {
         this.goalInformation = new Goal.GoalInformation(null, null, null);
      }

      this.goalInformation.setTargetEnt(ent);
      if (ent != null) {
         this.setPathDestPoint(new Point(ent), 2);
      }

      if (ent instanceof MillVillager) {
         MillVillager v = (MillVillager)ent;
         this.dialogueTargetFirstName = v.firstName;
         this.dialogueTargetLastName = v.familyName;
      }
   }

   public void setGoalDestPoint(Point newDest) {
      if (this.goalInformation == null) {
         this.goalInformation = new Goal.GoalInformation(null, null, null);
      }

      this.goalInformation.setDest(newDest);
      this.setPathDestPoint(newDest, 0);
   }

   public void setGoalInformation(Goal.GoalInformation info) {
      this.goalInformation = info;
      if (info != null) {
         if (info.getTargetEnt() != null) {
            this.setPathDestPoint(new Point(info.getTargetEnt()), 2);
         } else if (info.getDest() != null) {
            this.setPathDestPoint(info.getDest(), 0);
         } else {
            this.setPathDestPoint(null, 0);
         }
      } else {
         this.setPathDestPoint(null, 0);
      }
   }

   public void setHousePoint(Point p) {
      this.housePoint = p;
      this.house = null;
   }

   public void setInv(Item item, int meta, int nb) {
      this.inventory.put(InvItem.createInvItem(item, meta), nb);
      this.updateVillagerRecord();
   }

   public void setNextGoal() throws Exception {
      Goal nextGoal = null;
      this.clearGoal();

      for (Goal goal : this.getGoals()) {
         if (goal.isPossible(this)) {
            if (MillConfigValues.LogGeneralAI >= 2 && this.extraLog) {
               MillLog.minor(this, "Priority for goal " + goal.gameName(this) + ": " + goal.priority(this));
            }

            if (nextGoal != null && (!nextGoal.leasure || goal.leasure)) {
               if (nextGoal == null || nextGoal.priority(this) < goal.priority(this)) {
                  nextGoal = goal;
               }
            } else {
               nextGoal = goal;
            }
         }
      }

      if (MillConfigValues.LogGeneralAI >= 2 && this.extraLog) {
         MillLog.minor(this, "Selected this: " + nextGoal);
      }

      if (MillLog.debugOn()) {
         java.util.List<Goal> gl = this.getGoals();
         int total = gl == null ? -1 : gl.size();
         int possible = 0;
         if (gl != null) {
            for (Goal g : gl) {
               try {
                  if (g.isPossible(this)) {
                     possible++;
                  }
               } catch (Exception e) {
                  throw MillCrash.fail("Entity",
                     "MillVillager.setNextGoal debug loop: goal.isPossible threw for goal "
                        + (g != null ? g.key : "null") + " on " + this + ": " + e);
               }
            }
         }
         MillLog.milldebug("Goal", "setNextGoal id=" + this.getVillagerId() + " type="
            + (this.vtype != null ? this.vtype.key : "null") + " goalsAvailable=" + total
            + " possibleNow=" + possible + " selected=" + (nextGoal != null ? nextGoal.key : "NONE")
            + " hasHouse=" + (this.getHouse() != null) + " hasTH=" + (this.getTownHall() != null));
      }

      if (nextGoal != null) {
         this.speakSentence(nextGoal.key + ".chosen");
         this.goalKey = nextGoal.key;
         this.heldItem = ItemStack.EMPTY;
         this.heldItemOffHand = ItemStack.EMPTY;
         this.heldItemCount = Integer.MAX_VALUE;
         nextGoal.onAccept(this);
         this.goalStarted = this.level().getOverworldClockTime();
         this.lastGoalTime.put(nextGoal, this.level().getOverworldClockTime());
         AttributeInstance iattributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
         iattributeinstance.removeModifier(SPRINT_SPEED_BOOST);
         if (nextGoal.sprint) {
            iattributeinstance.addTransientModifier(SPRINT_SPEED_BOOST);
         }
      } else {
         this.goalKey = null;
      }

      if (MillConfigValues.LogBuildingPlan >= 1 && nextGoal != null && nextGoal.key.equals(Goal.getResourcesForBuild.key)) {
         ConstructionIP cip = this.getCurrentConstruction();
         if (cip != null) {
            MillLog.major(
               this,
               this.getVillagerName()
                  + " is new builder, for: "
                  + cip.getBuildingLocation().planKey
                  + "_"
                  + cip.getBuildingLocation().level
                  + ". Blocks loaded: "
                  + cip.getBblocks().length
            );
         }
      }
   }

   public void setPathDestPoint(Point newDest, int tolerance) {
      if ((newDest == null || !newDest.equals(this.pathDestPoint))
         && (this.pathDestPoint == null || newDest == null || tolerance < newDest.distanceTo(this.pathDestPoint))) {
         this.getNavigation().stop();
         this.pathEntity = null;
      }

      this.pathDestPoint = newDest;
   }

   public void setTexture(Identifier tx) {
      this.texture = tx;
   }

   public void setTownHallPoint(Point p) {
      this.townHallPoint = p;
      this.townHall = null;
   }

   public void setVillagerId(long villagerId) {
      this.villagerId = villagerId;
      // Publish to clients so a freshly tracked villager gets its real id (and the data packet can then
      // resolve it). entityData exists post-construction; setVillagerId is only called server-side.
      this.entityData.set(DATA_VILLAGER_ID, villagerId);
   }

   @Override
   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_VILLAGER_ID, -1L);
   }

   @Override
   public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (DATA_VILLAGER_ID.equals(key) && this.level().isClientSide()) {
         // Mirror the synced id onto the field so getVillagerId() (used everywhere, incl. the client
         // villager-list registration and the data-packet lookup) returns the real id on the client.
         this.villagerId = this.entityData.get(DATA_VILLAGER_ID);
      }
   }

   public void speakSentence(String key) {
      this.speakSentence(key, 600, 3, 1);
   }

   public void speakSentence(String key, int delay, int distance, int chanceOn) {
      if (delay <= this.level().getOverworldClockTime() - this.speech_started) {
         if (MillCommonUtilities.chanceOn(chanceOn)) {
            if (this.getTownHall() != null
               && this.getTownHall().closestPlayer != null
               && !(this.getPos().distanceTo(this.getTownHall().closestPlayer) > distance)) {
               key = key.toLowerCase();
               this.speech_key = null;
               if (this.getCulture().hasSentences(this.getNameKey() + "." + key)) {
                  this.speech_key = this.getNameKey() + "." + key;
               } else if (this.getCulture().hasSentences(this.getGenderString() + "." + key)) {
                  this.speech_key = this.getGenderString() + "." + key;
               } else if (this.getCulture().hasSentences("villager." + key)) {
                  this.speech_key = "villager." + key;
               }

               if (this.speech_key != null) {
                  this.speech_variant = MillCommonUtilities.randomInt(this.getCulture().getSentences(this.speech_key).size());
                  this.speech_started = this.level().getOverworldClockTime();
                  this.sendVillagerPacket();
                  ServerSender.sendVillageSentenceInRange(this.level(), this.getPos(), 30, this);
               }
            }
         }
      }
   }

   public void specificUpdate() throws Exception {
      if (this.isLocalMerchant()) {
         this.localMerchantUpdate();
      }

      if (this.isForeignMerchant()) {
         this.foreignMerchantUpdate();
      }
   }

   public int takeFromBuilding(Building building, Item item, int meta, int nb) {
      if (item == Blocks.OAK_LOG.asItem() && meta == -1) {
         int total = 0;
         int nb2 = building.takeGoods(item, 0, nb);
         this.addToInv(item, 0, nb2);
         total += nb2;
         nb2 = building.takeGoods(item, 1, nb - total);
         this.addToInv(item, 0, nb2);
         total += nb2;
         nb2 = building.takeGoods(item, 2, nb - total);
         this.addToInv(item, 0, nb2);
         total += nb2;
         nb2 = building.takeGoods(item, 3, nb - total);
         this.addToInv(item, 0, nb2);
         total += nb2;
         nb2 = building.takeGoods(Blocks.ACACIA_LOG.asItem(), 0, nb - total);
         this.addToInv(item, 0, nb2);
         total += nb2;
         nb2 = building.takeGoods(Blocks.ACACIA_LOG.asItem(), 1, nb - total);
         this.addToInv(item, 0, nb2);
         return total + nb2;
      } else {
         nb = building.takeGoods(item, meta, nb);
         this.addToInv(item, meta, nb);
         return nb;
      }
   }

   public int takeFromInv(Block block, int meta, int nb) {
      return this.takeFromInv(block.asItem(), meta, nb);
   }

   public int takeFromInv(BlockState blockState, int nb) {
      return this.takeFromInv((blockState.getBlock()).asItem(), 0, nb);
   }

   public int takeFromInv(InvItem item, int nb) {
      return this.takeFromInv(item.getItem(), item.meta, nb);
   }

   public int takeFromInv(Item item, int meta, int nb) {
      if (item == Blocks.OAK_LOG.asItem() && meta == -1) {
         int total = 0;

         for (int i = 0; i < 16; i++) {
            InvItem key = InvItem.createInvItem(item, i);
            if (this.inventory.containsKey(key)) {
               int nb2 = Math.min(nb, this.inventory.get(key));
               this.inventory.put(key, this.inventory.get(key) - nb2);
               total += nb2;
            }
         }

         for (int ix = 0; ix < 16; ix++) {
            InvItem key = InvItem.createInvItem(Blocks.ACACIA_LOG.asItem(), ix);
            if (this.inventory.containsKey(key)) {
               int nb2 = Math.min(nb, this.inventory.get(key));
               this.inventory.put(key, this.inventory.get(key) - nb2);
               total += nb2;
            }
         }

         this.updateVillagerRecord();
         return total;
      } else {
         InvItem key = InvItem.createInvItem(item, meta);
         if (this.inventory.containsKey(key)) {
            nb = Math.min(nb, this.inventory.get(key));
            this.inventory.put(key, this.inventory.get(key) - nb);
            this.updateVillagerRecord();
            this.updateClothTexturePath();
            return nb;
         } else {
            return 0;
         }
      }
   }

   private void targetDefender() {
      int bestDist = Integer.MAX_VALUE;
      MillVillager target = null;

      for (MillVillager v : this.getTownHall().getKnownVillagers()) {
         if (v.helpsInAttacks() && !v.isRaider && this.getPos().distanceToSquared(v) < bestDist) {
            target = v;
            bestDist = (int)this.getPos().distanceToSquared(v);
         }
      }

      if (target != null && this.getPos().distanceToSquared(target) <= 100.0) {
         this.setTarget(target);
      }
   }

   private void targetRaider() {
      int bestDist = Integer.MAX_VALUE;
      MillVillager target = null;

      for (MillVillager v : this.getTownHall().getKnownVillagers()) {
         if (v.isRaider && this.getPos().distanceToSquared(v) < bestDist) {
            target = v;
            bestDist = (int)this.getPos().distanceToSquared(v);
         }
      }

      if (target != null && this.getPos().distanceToSquared(target) <= 25.0) {
         this.setTarget(target);
      }
   }

   private void teenagerNightAction() {
      for (Point p : this.getTownHall().getKnownVillages()) {
         if (this.getTownHall().getRelationWithVillage(p) > 90) {
            Building distantVillage = this.mw.getBuilding(p);
            if (distantVillage != null && distantVillage.culture == this.getCulture() && distantVillage != this.getTownHall()) {
               boolean canMoveIn = false;
               if (MillConfigValues.LogChildren >= 1) {
                  MillLog.major(this, "Attempting to move to village: " + distantVillage.getVillageQualifiedName());
               }

               Building distantInn = null;

               for (Building distantBuilding : distantVillage.getBuildings()) {
                  if (!canMoveIn && distantBuilding != null && distantBuilding.isHouse()) {
                     if (distantBuilding.canChildMoveIn(this.gender, this.familyName)) {
                        canMoveIn = true;
                     }
                  } else if (distantInn == null && distantBuilding.isInn && distantBuilding.getAllVillagerRecords().size() < 2) {
                     distantInn = distantBuilding;
                  }
               }

               if (canMoveIn && distantInn != null) {
                  if (MillConfigValues.LogChildren >= 1) {
                     MillLog.major(this, "Moving to village: " + distantVillage.getVillageQualifiedName());
                  }

                  this.getHouse().transferVillagerPermanently(this.getRecord(), distantInn);
                  distantInn.visitorsList.add("panels.childarrived;" + this.getVillagerName() + ";" + this.getTownHall().getVillageQualifiedName());
               }
            }
         }
      }
   }

   public boolean millTeleportTo(double d, double d1, double d2) { // 26.2 Entity.teleportTo(d,d,d) is void; renamed to avoid override clash
      // 26.2: posX/Y/Z are no longer assignable fields — the global posX→getX() rename produced invalid
      // assignments; use mutable locals + setPos. setPosition→setPos, isBlockLoaded→isLoaded,
      // getMaterial().blocksMovement()→state.blocksMotion(), getEntityBoundingBox→getBoundingBox.
      double origX = this.getX();
      double origY = this.getY();
      double origZ = this.getZ();
      double tx = d;
      double ty = d1;
      double tz = d2;
      boolean flag = false;
      int i = Mth.floor(tx);
      int j = Mth.floor(ty);
      int k = Mth.floor(tz);
      if (this.level().isLoaded(new BlockPos(i, j, k))) {
         boolean flag1 = false;

         while (!flag1 && j > 0) {
            BlockState bs = WorldUtilities.getBlockState(this.level(), i, j - 1, k);
            if (bs.getBlock() != Blocks.AIR && bs.blocksMotion()) {
               flag1 = true;
            } else {
               ty--;
               j--;
            }
         }

         if (flag1) {
            this.setPos(tx, ty, tz);
            // 1.12: getCollisionBoxes(this,bb).size()==0 && !containsAnyLiquid(bb) → 26.2 equivalents.
            if (this.level().noCollision(this) && !this.level().containsAnyLiquid(this.getBoundingBox())) {
               flag = true;
            }
         }
      }

      if (!flag) {
         this.setPos(origX, origY, origZ);
         return false;
      } else {
         return true;
      }
   }

   public boolean teleportToEntity(Entity entity) {
      Vec3 vec3d = new Vec3(
         this.getX() - entity.getX(),
         this.getBoundingBox().minY + this.getBbHeight() / 2.0F - entity.getY() + entity.getEyeHeight(),
         this.getZ() - entity.getZ()
      );
      vec3d = vec3d.normalize();
      double d = 16.0;
      double d1 = this.getX() + (this.random.nextDouble() - 0.5) * 8.0 - vec3d.x * 16.0;
      double d2 = this.getY() + (this.random.nextInt(16) - 8) - vec3d.y * 16.0;
      double d3 = this.getZ() + (this.random.nextDouble() - 0.5) * 8.0 - vec3d.z * 16.0;
      return this.millTeleportTo(d1, d2, d3);
   }

   private void toggleDoor(Point p) {
      BlockState state = p.getBlockActualState(this.level());
      if ((Boolean)state.getValue(DoorBlock.OPEN)) {
         state = state.setValue(DoorBlock.OPEN, false);
      } else {
         state = state.setValue(DoorBlock.OPEN, true);
      }

      p.setBlockState(this.level(), state);
   }

   @Override
   public String toString() {
      return this.vtype != null
         ? this.getVillagerName() + "/" + this.vtype.key + "/" + this.getVillagerId() + "/" + this.getPos()
         : this.getVillagerName() + "/none/" + this.getVillagerId() + "/" + this.getPos();
   }

   private void triggerMobAttacks() {
      for (Entity ent : WorldUtilities.getEntitiesWithinAABB(this.level(), Monster.class, this.getPos(), 16, 5)) {
         Monster mob = (Monster)ent;
         if (mob.getTarget() == null && mob.hasLineOfSight(this)) {
            mob.setTarget(this);
         }
      }
   }

   private void unlockForNearbyPlayers() {
      if (this.mw == null) {
         this.mw = Mill.getMillWorld(this.level());
      }
      if (this.mw == null) {
         return;
      }
      Player player = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 5.0, false);
      if (player != null) {
         UserProfile profile = this.mw.getProfile(player);
         if (profile != null) {
            profile.unlockVillager(this.getCulture(), this.vtype);
         }
      }
   }

   private void updateClothTexturePath() {
      if (this.vtype != null) {
         boolean[] naturalLayers = this.vtype.getClothLayersOfType("natural");
         String bestClothName = null;
         int clothLevel = -1;
         if (this.vtype.hasClothTexture("free")) {
            bestClothName = "free";
            clothLevel = 0;
         }

         for (InvItem iv : this.inventory.keySet()) {
            if (iv.item instanceof ItemClothes && this.inventory.get(iv) > 0) {
               ItemClothes clothes = (ItemClothes)iv.item;
               if (clothes.getClothPriority(iv.meta) > clothLevel && this.vtype.hasClothTexture(clothes.getClothName(iv.meta))) {
                  bestClothName = clothes.getClothName(iv.meta);
                  clothLevel = clothes.getClothPriority(iv.meta);
               }
            }
         }

         if (bestClothName != null) {
            if (!bestClothName.equals(this.clothName)) {
               this.clothName = bestClothName;

               for (int layer = 0; layer < 2; layer++) {
                  String texture;
                  if (naturalLayers[layer]) {
                     texture = this.vtype.getRandomClothTexture("natural", layer);
                  } else {
                     texture = this.vtype.getRandomClothTexture(bestClothName, layer);
                  }

                  if (texture == null || texture.length() <= 0) {
                     this.clothTexture[layer] = null;
                  } else if (texture.contains(":")) {
                     this.clothTexture[layer] = Identifier.parse(texture);
                  } else {
                     this.clothTexture[layer] = Identifier.fromNamespaceAndPath("millenaire", texture);
                  }
               }

               if (MillLog.debugOn()) {
                  MillLog.milldebug(
                     "Villager",
                     "TEXTURE resolved id=" + this.getVillagerId() + " name='" + this.firstName + " " + this.familyName
                        + "' skin=" + this.texture + " clothName=" + this.clothName
                        + " clothTex[0]=" + this.clothTexture[0] + " clothTex[1]=" + this.clothTexture[1]
                  );
               }
            }
         } else {
            this.clothName = null;

            for (int i = 0; i < 2; i++) {
               this.clothTexture[i] = null;
            }
         }
      }
   }

   private void updateDialogue() {
      if (this.dialogueKey != null) {
         CultureLanguage.Dialogue d = this.getCulture().getDialogue(this.dialogueKey);
         if (d == null) {
            this.dialogueKey = null;
         } else {
            long timePassed = this.level().getOverworldClockTime() - this.dialogueStart;
            if (d.timeDelays.get(d.timeDelays.size() - 1) + 100 < timePassed) {
               this.dialogueKey = null;
            } else {
               String toSpeakKey = null;

               for (int i = 0; i < d.speechBy.size(); i++) {
                  if (this.dialogueRole == d.speechBy.get(i) && timePassed >= d.timeDelays.get(i).intValue()) {
                     toSpeakKey = "chat_" + d.key + "_" + i;
                  }
               }

               if (toSpeakKey != null && (this.speech_key == null || !this.speech_key.contains(toSpeakKey))) {
                  this.speakSentence(toSpeakKey, 0, 10, 1);
               }
            }
         }
      }
   }

   private void updateHired() {
      try {
         if (this.getHealth() < this.getMaxHealth() & MillCommonUtilities.randomInt(1600) == 0) {
            this.setHealth(this.getHealth() + 1.0F);
         }

         Player entityplayer = this.millGetPlayerByName(this.hiredBy);
         if (this.level().getOverworldClockTime() > this.hiredUntil) {
            if (entityplayer != null) {
               ServerSender.sendTranslatedSentence(entityplayer, 'f', "hire.hireover", this.getVillagerName());
            }

            this.hiredBy = null;
            this.hiredUntil = 0L;
            VillagerRecord vr = this.getRecord();
            if (vr != null) {
               vr.awayhired = false;
            }

            return;
         }

         if (this.getTarget() != null) {
            if (this.getPos().distanceTo(this.getTarget()) > 80.0
               || this.level().getDifficulty() == Difficulty.PEACEFUL
               || this.getTarget().isRemoved()) {
               this.setTarget(null);
            }
         } else if (this.isHostile()
            && this.level().getDifficulty() != Difficulty.PEACEFUL
            && this.getTownHall().closestPlayer != null
            && this.getPos().distanceTo(this.getTownHall().closestPlayer) <= 80.0) {
            this.setTarget(this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 100.0, true));
         }

         if (this.getTarget() == null) {
            for (Object o : this.level()
               .getEntitiesOfClass(
                  PathfinderMob.class,
                  new AABB(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        this.getX() + 1.0,
                        this.getY() + 1.0,
                        this.getZ() + 1.0
                     )
                     .inflate(16.0, 8.0, 16.0)
               )) {
               if (this.getTarget() == null) {
                  PathfinderMob creature = (PathfinderMob)o;
                  if (creature.getTarget() == entityplayer && !(creature instanceof Creeper)) {
                     this.setTarget(creature);
                  }
               }
            }

            if (this.getTarget() == null && this.aggressiveStance) {
               List<?> var7 = this.level()
                  .getEntitiesOfClass(
                     Monster.class,
                     new AABB(
                           this.getX(),
                           this.getY(),
                           this.getZ(),
                           this.getX() + 1.0,
                           this.getY() + 1.0,
                           this.getZ() + 1.0
                        )
                        .inflate(16.0, 8.0, 16.0)
                  );
               if (!var7.isEmpty()) {
                  this.setTarget((LivingEntity)var7.get(this.getRandom().nextInt(var7.size())));
                  if (this.getTarget() instanceof Creeper) {
                     this.setTarget(null);
                  }
               }

               if (this.getTarget() == null) {
                  for (Object ox : this.level()
                     .getEntitiesOfClass(
                        MillVillager.class,
                        new AABB(
                              this.getX(),
                              this.getY(),
                              this.getZ(),
                              this.getX() + 1.0,
                              this.getY() + 1.0,
                              this.getZ() + 1.0
                           )
                           .inflate(16.0, 8.0, 16.0)
                     )) {
                     if (this.getTarget() == null) {
                        MillVillager villager = (MillVillager)ox;
                        if (villager.isHostile()) {
                           this.setTarget(villager);
                        }
                     }
                  }
               }
            }
         }

         Entity target = null;
         if (this.getTarget() != null) {
            Entity var10 = this.getTarget();
            this.heldItem = this.getWeapon();
            this.heldItemOffHand = ItemStack.EMPTY;
            Path newPathEntity = this.getNavigation().createPath(var10, 1);
            if (newPathEntity != null) {
               this.getNavigation().moveTo(newPathEntity, 0.5);
            }

            this.attackEntity(this.getTarget());
         } else {
            this.heldItem = ItemStack.EMPTY;
            this.heldItemOffHand = ItemStack.EMPTY;
            int dist = (int)this.getPos().distanceTo(entityplayer);
            if (dist > 16) {
               this.teleportToEntity(entityplayer);
            } else if (dist > 4) {
               boolean rebuildPath = false;
               if (this.getNavigation().getPath() == null) {
                  rebuildPath = true;
               } else {
                  Point currentTargetPoint = new Point(this.getNavigation().getPath().getEndNode());
                  if (currentTargetPoint.distanceTo(entityplayer) > 2.0) {
                     rebuildPath = true;
                  }
               }

               if (rebuildPath) {
                  Path newPathEntity = this.getNavigation().createPath(entityplayer, 1);
                  if (newPathEntity != null) {
                     this.getNavigation().moveTo(newPathEntity, 0.5);
                  }
               }
            }
         }

         this.prevPoint = this.getPos();
         this.handleDoorsAndFenceGates();
      } catch (Exception var6) {
         throw MillCrash.fail("Entity", "MillVillager.updateHired failed for " + this + ": " + var6);
      }
   }

   private void updatePathIfNeeded(Point dest) throws Exception {
      if (dest != null) {
         if (this.pathEntity != null
            && this.pathEntity.getNodeCount() > 0
            && !MillCommonUtilities.chanceOn(50)
            && this.pathEntity.getCurrentTargetPathPoint() != null) {
            // Bridge Mill's A* AS_PathEntity (Node[]) onto vanilla navigation as a Path.
            net.minecraft.world.level.pathfinder.Node end = this.pathEntity.pointsCopy[this.pathEntity.getNodeCount() - 1];
            net.minecraft.world.level.pathfinder.Path rebuilt = new net.minecraft.world.level.pathfinder.Path(
               java.util.Arrays.asList(this.pathEntity.pointsCopy), new net.minecraft.core.BlockPos(end.x, end.y, end.z), true
            );
            // A fresh Path resets nextNodeIndex to 0. In 1.12 this branch re-issued the SAME path object,
            // preserving walking progress; carry the current index over so the villager does not get
            // snapped back to the start of the path roughly every ~50 ticks.
            int carryIndex = this.pathEntity.getCurrentPathIndex();
            if (carryIndex > 0 && carryIndex < rebuilt.getNodeCount()) {
               rebuilt.setNextNodeIndex(carryIndex);
            }
            this.getNavigation().moveTo(rebuilt, 0.5);
         } else if (!this.pathPlannerJPS.isBusy()) {
            this.computeNewPath(dest);
         }
      }
   }

   public float updateRotation(float f, float f1, float f2) {
      float f3 = f1 - f;

      while (f3 < -180.0F) {
         f3 += 360.0F;
      }

      while (f3 >= 180.0F) {
         f3 -= 360.0F;
      }

      if (f3 > f2) {
         f3 = f2;
      }

      if (f3 < -f2) {
         f3 = -f2;
      }

      return f + f3;
   }

   public void updateVillagerRecord() {
      if (!this.level().isClientSide()) {
         this.getRecord().updateRecord(this);
      }
   }

   private boolean visitorNightAction() {
      this.visitorNbNights++;
      if (this.visitorNbNights > 5) {
         this.leaveVillage();
      } else if (this.isForeignMerchant()) {
         boolean hasItems = false;

         for (InvItem key : this.vtype.foreignMerchantStock.keySet()) {
            if (this.getHouse().countGoods(key) > 0) {
               hasItems = true;
            }
         }

         if (!hasItems) {
            this.leaveVillage();
         }
      }

      return true;
   }

   // 26.2: 1.12 writeEntityToNBT(CompoundTag) → addAdditionalSaveData(ValueOutput). Ported to write
   // fields directly onto ValueOutput; Point fields via writePoint(), the inventory ListTag wrapped in
   // a CompoundTag stored under CompoundTag.CODEC (mirrored by readAdditionalSaveData).
   @Override
   protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
      try {
         if (this.vtype == null) {
            MillLog.error(this, "Not saving villager due to null vtype.");
            return;
         }

         super.addAdditionalSaveData(output);
         output.putString("vtype", this.vtype.key);
         output.putString("culture", this.getCulture().key);
         output.putString("texture", this.texture.getPath());
         TileEntityLockedChest.writePoint(output, "housePos", this.housePoint);
         TileEntityLockedChest.writePoint(output, "townHallPos", this.townHallPoint);
         TileEntityLockedChest.writePoint(output, "destPoint", this.getGoalDestPoint());
         TileEntityLockedChest.writePoint(output, "destBuildingPoint", this.getGoalBuildingDestPoint());
         TileEntityLockedChest.writePoint(output, "pathDestPoint", this.getPathDestPoint());
         TileEntityLockedChest.writePoint(output, "prevPoint", this.prevPoint);
         TileEntityLockedChest.writePoint(output, "doorToClose", this.doorToClose);

         output.putInt("action", this.action);
         if (this.goalKey != null) {
            output.putString("goal", this.goalKey);
         }

         output.putInt("constructionJobId", this.constructionJobId);
         output.putString("firstName", this.firstName);
         output.putString("familyName", this.familyName);
         output.putInt("gender", this.gender);
         output.putLong("lastSpeechLong", this.speech_started);
         output.putLong("villager_lid", this.getVillagerId());
         if (this.dialogueKey != null) {
            output.putString("dialogueKey", this.dialogueKey);
            output.putLong("dialogueStart", this.dialogueStart);
            output.putInt("dialogueRole", this.dialogueRole);
            output.putInt("dialogueColour", this.dialogueColour);
            output.putBoolean("dialogueChat", this.dialogueChat);
         }

         ListTag nbttaglist = MillCommonUtilities.writeInventory(this.inventory);
         net.minecraft.nbt.CompoundTag invWrapper = new net.minecraft.nbt.CompoundTag();
         invWrapper.put("l", nbttaglist);
         output.store("inventoryNew", net.minecraft.nbt.CompoundTag.CODEC, invWrapper);
         output.putInt("previousBlock", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(this.previousBlock));
         output.putInt("previousBlockMeta", this.previousBlockMeta);
         if (this.hiredBy != null) {
            output.putString("hiredBy", this.hiredBy);
            output.putLong("hiredUntil", this.hiredUntil);
            output.putBoolean("aggressiveStance", this.aggressiveStance);
         }

         output.putBoolean("isRaider", this.isRaider);
         output.putInt("visitorNbNights", this.visitorNbNights);
         if (this.clothName != null) {
            output.putString("clothName", this.clothName);

            for (int layer = 0; layer < 2; layer++) {
               if (this.clothTexture[layer] != null) {
                  output.putString("clothTexture_" + layer, this.clothTexture[layer].toString());
               }
            }
         }
      } catch (Exception var4) {
         // Phase-2 FLAG (save/NBT-adjacent): this is the WRITE side, not a missing-field READ. A swallow here
         // silently drops the villager from the save (data loss), so surface it. (Field-by-field missing-READ
         // compat remains Phase 4's concern and is NOT fatalised here.)
         throw MillCrash.fail("Entity", "MillVillager.addAdditionalSaveData: save write failed for " + this + ": " + var4);
      }
   }

   public void writeSpawnData(ByteBuf ds) {
      // Phase-2 FLAG (spawn-data WRITE, server-side): an IOException serialising our own state is a real bug,
      // never optional. Surface it instead of sending a truncated spawn packet.
      try {
         this.writeVillagerStreamData(ds, true);
      } catch (IOException var3) {
         throw MillCrash.fail("Entity", "MillVillager.writeSpawnData: spawn-data write failed for " + this + ": " + var3);
      }
   }

   private void writeVillagerStreamData(ByteBuf bb, boolean isSpawn) throws IOException {
      if (this.vtype == null) {
         MillLog.error(this, "Cannot write stream data due to null vtype.");
      } else {
         FriendlyByteBuf data;
         if (bb instanceof FriendlyByteBuf) {
            data = (FriendlyByteBuf)bb;
         } else {
            data = new FriendlyByteBuf(bb);
         }

         data.writeLong(this.getVillagerId());
         StreamReadWrite.writeNullableString(this.vtype.culture.key, data);
         StreamReadWrite.writeNullableString(this.vtype.key, data);
         StreamReadWrite.writeNullableResourceLocation(this.texture, data);
         StreamReadWrite.writeNullableString(this.goalKey, data);
         data.writeInt(this.constructionJobId);
         StreamReadWrite.writeNullablePoint(this.housePoint, data);
         StreamReadWrite.writeNullablePoint(this.townHallPoint, data);
         StreamReadWrite.writeNullableString(this.firstName, data);
         StreamReadWrite.writeNullableString(this.familyName, data);
         data.writeInt(this.gender);
         StreamReadWrite.writeNullableString(this.hiredBy, data);
         data.writeBoolean(this.aggressiveStance);
         data.writeLong(this.hiredUntil);
         data.writeBoolean(this.isUsingBow);
         data.writeBoolean(this.isUsingHandToHand);
         data.writeBoolean(this.isRaider);
         StreamReadWrite.writeNullableString(this.speech_key, data);
         data.writeInt(this.speech_variant);
         data.writeLong(this.speech_started);
         StreamReadWrite.writeNullableItemStack(this.heldItem, data);
         StreamReadWrite.writeNullableItemStack(this.heldItemOffHand, data);
         StreamReadWrite.writeInventory(this.inventory, data);
         StreamReadWrite.writeNullableString(this.clothName, data);

         for (int i = 0; i < 2; i++) {
            StreamReadWrite.writeNullableResourceLocation(this.clothTexture[i], data);
         }

         StreamReadWrite.writeNullablePoint(this.getGoalDestPoint(), data);
         // The lying-down pose is decided on the CLIENT from this synced flag. The client can't reliably tell
         // we're fighting (the target is sent only as a UUID it often fails to resolve, so its getTarget() stays
         // null and isVillagerSleeping() would read true). So gate the WIRE value on the server's authoritative
         // getTarget(): during combat we send shouldLieDown=false, and the client renders STANDING, not asleep.
         data.writeBoolean(this.shouldLieDown && this.getTarget() == null);
         StreamReadWrite.writeNullableString(this.dialogueTargetFirstName, data);
         StreamReadWrite.writeNullableString(this.dialogueTargetLastName, data);
         data.writeChar(this.dialogueColour);
         data.writeBoolean(this.dialogueChat);
         data.writeFloat(this.getHealth());
         data.writeInt(this.visitorNbNights);
         if (this.getTarget() != null) {
            StreamReadWrite.writeNullableUUID(this.getTarget().getUUID(), data);
         } else {
            StreamReadWrite.writeNullableUUID(null, data);
         }

         if (isSpawn) {
            this.calculateMerchantGoods();
            data.writeInt(this.merchantSells.size());

            for (TradeGood g : this.merchantSells.keySet()) {
               StreamReadWrite.writeNullableGoods(g, data);
               data.writeInt(this.merchantSells.get(g));
            }
         } else {
            data.writeInt(-1);
         }

         if (this.getGoalDestEntity() != null) {
            data.writeInt(this.getGoalDestEntity().getId());
         } else {
            data.writeInt(-1);
         }

         data.writeBoolean(this.isRemoved());
      }
   }

   public static class EntityGenericAsymmFemale extends MillVillager {
      public EntityGenericAsymmFemale(EntityType<? extends MillVillager> type, Level world) {
         super(type, world);
      }
   }

   public static class EntityGenericMale extends MillVillager {
      public EntityGenericMale(EntityType<? extends MillVillager> type, Level world) {
         super(type, world);
      }
   }

   public static class EntityGenericSymmFemale extends MillVillager {
      public EntityGenericSymmFemale(EntityType<? extends MillVillager> type, Level world) {
         super(type, world);
      }
   }

   public static class InvItemAlphabeticalComparator implements Comparator<InvItem> {
      public int compare(InvItem arg0, InvItem arg1) {
         return arg0.getName().compareTo(arg1.getName());
      }
   }
}
