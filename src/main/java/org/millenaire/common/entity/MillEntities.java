package org.millenaire.common.entity;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.millenaire.common.forge.MillRegistry;

/**
 * Holder + Fabric registration for Millénaire {@link EntityType}s.
 *
 * <p>1.12 registered entities by class+name through {@code EntityRegistry}/
 * {@code EntityList}, and {@link MillVillager} subclasses were looked up by their
 * registry name string. In 26.2 every entity needs an {@link EntityType} built via
 * {@code EntityType.Builder.of(factory, MobCategory).sized(w,h).build(ResourceKey)}
 * and registered into {@link BuiltInRegistries#ENTITY_TYPE}.
 *
 * <p>26.2: all Mill entities are registered here — the three generic villager EntityTypes
 * (VILLAGER_MALE / VILLAGER_FEMALE_SYM / VILLAGER_FEMALE_ASYM, onto which the dynamic culture
 * villager types map by model), the wall-decoration entity, and the "targeted" mobs — each with its
 * attribute supplier via {@code FabricDefaultAttributeRegistry}.
 */
public final class MillEntities {

	public static final List<EntityType<?>> REGISTERED = new ArrayList<>();

	public static EntityType<EntityTargetedBlaze> TARGETED_BLAZE;
	public static EntityType<EntityTargetedGhast> TARGETED_GHAST;
	public static EntityType<EntityTargetedWitherSkeleton> TARGETED_WITHER_SKELETON;
	// The three concrete MillVillager subtypes. Typed as EntityType<MillVillager> so the shared
	// RenderMillVillager factories (EntityRendererProvider<MillVillager>) register against them.
	public static EntityType<MillVillager> VILLAGER_MALE;
	public static EntityType<MillVillager> VILLAGER_FEMALE_SYM;
	public static EntityType<MillVillager> VILLAGER_FEMALE_ASYM;
	public static EntityType<EntityWallDecoration> WALL_DECORATION;

	private MillEntities() {
	}

	private static <T extends Entity> EntityType<T> register(
			String name, EntityType.EntityFactory<T> factory, MobCategory category, float width, float height) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MillRegistry.id(name));
		EntityType<T> type = EntityType.Builder.of(factory, category).sized(width, height).build(key);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
		REGISTERED.add(type);
		return type;
	}

	public static void register() {
		TARGETED_BLAZE = register("targeted_blaze", EntityTargetedBlaze::new, MobCategory.MONSTER, 0.6F, 1.8F);
		TARGETED_GHAST = register("targeted_ghast", EntityTargetedGhast::new, MobCategory.MONSTER, 4.0F, 4.0F);
		TARGETED_WITHER_SKELETON = register("targeted_wither_skeleton", EntityTargetedWitherSkeleton::new, MobCategory.MONSTER, 0.7F, 2.4F);

		VILLAGER_MALE = register("villager_male", MillVillager.EntityGenericMale::new, MobCategory.CREATURE, 0.6F, 1.8F);
		VILLAGER_FEMALE_SYM = register("villager_female_sym", MillVillager.EntityGenericSymmFemale::new, MobCategory.CREATURE, 0.6F, 1.8F);
		VILLAGER_FEMALE_ASYM = register("villager_female_asym", MillVillager.EntityGenericAsymmFemale::new, MobCategory.CREATURE, 0.6F, 1.8F);
		WALL_DECORATION = register("wall_decoration", EntityWallDecoration::new, MobCategory.MISC, 0.5F, 0.5F);

		// Living-entity attributes (villagers). HangingEntity (wall decoration) + the targeted mobs
		// inherit vanilla attribute suppliers via their base types' own registration.
		FabricDefaultAttributeRegistry.register(VILLAGER_MALE, MillVillager.createAttributes());
		FabricDefaultAttributeRegistry.register(VILLAGER_FEMALE_SYM, MillVillager.createAttributes());
		FabricDefaultAttributeRegistry.register(VILLAGER_FEMALE_ASYM, MillVillager.createAttributes());
		FabricDefaultAttributeRegistry.register(TARGETED_BLAZE, net.minecraft.world.entity.monster.Blaze.createAttributes());
		FabricDefaultAttributeRegistry.register(TARGETED_GHAST, net.minecraft.world.entity.monster.Ghast.createAttributes());
		FabricDefaultAttributeRegistry.register(TARGETED_WITHER_SKELETON, net.minecraft.world.entity.monster.skeleton.AbstractSkeleton.createAttributes());
	}
}
