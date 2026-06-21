package org.millenaire.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import org.millenaire.common.forge.MillRegistry;

/**
 * Holder for Millénaire's {@link ModelLayerLocation}s and their {@code LayerDefinition} registration.
 *
 * <p>26.2 PORT NOTE: 1.12 had no model-layer registry — {@code ModelBase} subclasses built their
 * geometry in their constructors. 26.2 bakes geometry from a {@code LayerDefinition} keyed by a
 * {@link ModelLayerLocation}, registered via Fabric's {@code EntityModelLayerRegistry}. Call
 * {@link #register()} from the client initializer.
 */
@Environment(EnvType.CLIENT)
public final class MillModelLayers {
   public static final ModelLayerLocation PANEL =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "panel"), "main");
   public static final ModelLayerLocation VILLAGER_MALE =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_male"), "main");
   public static final ModelLayerLocation VILLAGER_FEMALE_ASYM =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_female_asym"), "main");
   public static final ModelLayerLocation VILLAGER_FEMALE_SYM =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_female_sym"), "main");

   // Inflated cloth overlay layers (one per body model × 2 cloth layers). 1.12 inflated the cloth
   // model by 0.1F*(layer+1) over the base; here we bake a deformed mesh per (model, layer).
   public static final ModelLayerLocation VILLAGER_MALE_CLOTH_0 =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_male"), "cloth0");
   public static final ModelLayerLocation VILLAGER_MALE_CLOTH_1 =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_male"), "cloth1");
   public static final ModelLayerLocation VILLAGER_FEMALE_ASYM_CLOTH_0 =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_female_asym"), "cloth0");
   public static final ModelLayerLocation VILLAGER_FEMALE_ASYM_CLOTH_1 =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_female_asym"), "cloth1");
   public static final ModelLayerLocation VILLAGER_FEMALE_SYM_CLOTH_0 =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_female_sym"), "cloth0");
   public static final ModelLayerLocation VILLAGER_FEMALE_SYM_CLOTH_1 =
      new ModelLayerLocation(Identifier.fromNamespaceAndPath(MillRegistry.MODID, "villager_female_sym"), "cloth1");

   /** Cloth inflation per layer, matching 1.12 {@code LayerVillagerClothes}: {@code 0.1F*(layer+1)}. */
   public static float clothInflation(int layer) {
      return 0.1F * (layer + 1);
   }

   private MillModelLayers() {
   }

   public static void register() {
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(PANEL, ModelPanel::createLayer);
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_MALE, ModelMillVillager::createBodyLayer);
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_FEMALE_ASYM, ModelFemaleAsymmetrical::createBodyLayer);
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_FEMALE_SYM, ModelFemaleSymmetrical::createBodyLayer);

      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_MALE_CLOTH_0, () -> ModelMillVillager.createClothLayer(clothInflation(0)));
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_MALE_CLOTH_1, () -> ModelMillVillager.createClothLayer(clothInflation(1)));
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_FEMALE_ASYM_CLOTH_0, () -> ModelFemaleAsymmetrical.createClothLayer(clothInflation(0)));
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_FEMALE_ASYM_CLOTH_1, () -> ModelFemaleAsymmetrical.createClothLayer(clothInflation(1)));
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_FEMALE_SYM_CLOTH_0, () -> ModelFemaleSymmetrical.createClothLayer(clothInflation(0)));
      net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(VILLAGER_FEMALE_SYM_CLOTH_1, () -> ModelFemaleSymmetrical.createClothLayer(clothInflation(1)));
   }
}
