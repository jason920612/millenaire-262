package org.millenaire;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.millenaire.common.world.MillWorldData;
import org.millenaire.common.world.WorldGenVillage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.millenaire.common.block.MillBlocks;
import org.millenaire.common.commands.MillCommands;
import org.millenaire.common.entity.MillBlockEntities;
import org.millenaire.common.entity.MillEntities;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillEventController;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.forge.ServerTickHandler;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.network.MillNetworking;

/**
 * Fabric entrypoint for Millénaire — replaces the 1.12 Forge {@code @Mod}/SidedProxy/FML
 * lifecycle. Wires the foundational subsystems that are already ported; the content
 * registration and event mapping are added as those subsystems land (see memory port-state).
 */
public class MillenaireMod implements ModInitializer {
	public static final String MOD_ID = MillRegistry.MODID;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Millénaire (Fabric 26.2 port) initialising…");

		// --- Content registration (strict order: blocks → items → block-items → block-entities → entities) ---
		MillBlocks.register();
		MillItems.register();
		MillBlocks.registerBlockItems();
		MillBlockEntities.register();
		MillEntities.register();
		MillRegistry.registerCreativeTabs();
		MillBlocks.initBlockStates();
		// Registration totals are logged from Mill.preInit (after the config / DEBUG_MODE is read).
		// (Creative-tab population is done by MillRegistry.registerCreativeTabs() above, which lists
		// every registered Mill item/block-item via the tab's displayItems generator.)

		// --- Brigadier commands (replaces 1.12 FMLServerStartingEvent.registerServerCommand) ---
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MillCommands.registerAll(dispatcher));

		// --- Networking (Fabric custom payloads, replacing the Forge FML channel) ---
		MillNetworking.registerPayloads();
		MillNetworking.registerServerReceiver();

		// --- Server tick (replaces Forge TickEvent.ServerTickEvent) ---
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerTickHandler.onServerTick();
			processVillageGenQueue();
		});

		// --- Village world-gen (replaces 1.12 GameRegistry.registerWorldGenerator(new WorldGenVillage())) ---
		// ServerChunkEvents.CHUNK_GENERATE is the closest equivalent to the old IWorldGenerator per-chunk
		// hook (fires once per newly generated chunk). WorldGenVillage.generate keeps its own distance /
		// existing-village / random guards. (Mill villages span many chunks; placement runs on the chunk
		// generation callback and touches neighbouring chunks via the world's normal block-set path.)
		ServerChunkEvents.CHUNK_GENERATE.register((world, chunk) -> {
			if (Mill.startupError || world.dimension() != Level.OVERWORLD) {
				return;
			}
			MillWorldData mw = Mill.getMillWorld(world);
			if (mw != null && mw.generateVillages) {
				// 26.2: village placement force-loads neighbouring chunks via world.getChunk(...).
				// Doing that INSIDE the chunk-generation callback re-enters the single-threaded chunk
				// system and DEADLOCKS the server thread (game freeze). Defer the gen to END_SERVER_TICK,
				// where getChunk is safe; enqueue the chunk coords now.
				VILLAGE_GEN_QUEUE.add(new Object[]{world, chunk.getPos().x(), chunk.getPos().z()});
			}
		});

		// --- MillEventController hooks → Fabric callbacks (1.12 @SubscribeEvent handlers) ---
		final MillEventController events = new MillEventController();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> events.playerLoggedIn(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> events.connectionClosed());
		// The damage-on-player / Inuit-drops / furnace-withdrawal hooks have no direct Fabric event and
		// are wired via mixins instead: LivingEntityDamageMixin (hurtServer → damageOnPlayer),
		// LivingEntityDropsMixin (dropAllDeathLoot → addInuitDrops) and FurnaceResultSlotMixin
		// (vanilla furnace withdrawal → MillEventController). See org.millenaire.mixin.*.

		// --- Server lifecycle (replaces Forge FML pre/init/serverStarting events) ---
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Mill.instance.preInit();
			Mill.instance.init();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Mill.instance.serverLoad(server);
			// 1.12 created a MillWorldData per world on WorldEvent.Load; this Fabric API version has no
			// ServerWorldEvents.LOAD, so load all currently-present levels here instead.
			for (ServerLevel level : server.getAllLevels()) {
				events.worldLoaded(level);
				// 1.12 stopDefaultVillages emptied MapGenVillage's spawn-biome list; the 26.2 equivalent
				// is removing the vanilla 'villages' structure set from each level's generator state.
				if (org.millenaire.common.config.MillConfigValues.stopDefaultVillages) {
					org.millenaire.common.world.VillageStructureSuppressor.suppressVanillaVillages(level);
				}
			}
			// --- Automated server-side self-test harness (only with -Dmillenaire.selftest=true) ---
			// Entirely out of the normal code path otherwise; it ticks the world, exercises worldgen /
			// villagers / buildings / trade / items / blocks / interaction, logs a [MILLTEST] report and
			// then halts the server itself. See org.millenaire.common.test.MillSelfTest.
			if (org.millenaire.common.test.MillSelfTest.isEnabled()) {
				org.millenaire.common.test.MillSelfTest.register(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				events.worldSaved(level);
				events.worldUnloaded(level);
			}
		});

		LOGGER.info("Millénaire core wiring registered.");
	}

	/**
	 * Chunks pending Millénaire village-generation. CHUNK_GENERATE enqueues; the server tick drains.
	 * Each entry is {ServerLevel world, Integer chunkX, Integer chunkZ}.
	 */
	private static final java.util.Queue<Object[]> VILLAGE_GEN_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();

	/**
	 * Runs queued village generation on the server thread (END_SERVER_TICK), where forcing neighbouring
	 * chunks to load via {@code world.getChunk(...)} is safe — doing it inside CHUNK_GENERATE re-enters
	 * the chunk system and deadlocks. A per-tick time budget lets the many cheap no-op chunks (no village
	 * spawns there) fly through while capping heavy real village placements to roughly one per tick.
	 */
	private static void processVillageGenQueue() {
		if (Mill.startupError) {
			VILLAGE_GEN_QUEUE.clear();
			return;
		}
		long deadline = System.nanoTime() + 40_000_000L; // ~40ms/tick budget
		Object[] req;
		while (System.nanoTime() < deadline && (req = VILLAGE_GEN_QUEUE.poll()) != null) {
			net.minecraft.server.level.ServerLevel world = (net.minecraft.server.level.ServerLevel) req[0];
			int cx = (Integer) req[1];
			int cz = (Integer) req[2];
			MillWorldData mw = Mill.getMillWorld(world);
			if (mw == null || !mw.generateVillages || mw.world != world) {
				continue;
			}
			try {
				java.util.Random rand = new java.util.Random(world.getSeed() + cx * 341873128712L + cz * 132897987541L);
				new WorldGenVillage().generate(rand, cx, cz, world);
			} catch (Exception villageGenException) {
				// FAIL-FAST: a swallowed world-gen failure here silently drops a village from the world with
				// no trace (the queue just moves to the next chunk). Surface the placement failure loudly.
				throw org.millenaire.common.utilities.MillCrash.fail("WorldGen", "deferred village generation failed at chunk " + cx + "/" + cz + ": " + villageGenException);
			}
		}
	}
}
