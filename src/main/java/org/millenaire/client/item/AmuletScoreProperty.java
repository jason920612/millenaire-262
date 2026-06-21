package org.millenaire.client.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import org.millenaire.common.utilities.Point;
import org.millenaire.common.utilities.WorldUtilities;

/**
 * Client-side {@link RangeSelectItemModelProperty} that reproduces the 1.12 "score" item-property the
 * three Millénaire amulets used to drive their {@code addPropertyOverride} model swap.
 *
 * <p>1.12 registered an {@code IItemPropertyGetter} on {@code Identifier("score")} returning a float
 * 0..15; the item model picked one of 16 sub-models (amulet0..amulet15) via {@code overrides}. 26.2
 * removed item-property overrides; the equivalent is a data-driven {@code minecraft:range_dispatch}
 * item-model that reads a {@link RangeSelectItemModelProperty} and selects a sub-model by numeric
 * threshold. As the amulet score is a Millénaire-custom value with no vanilla number-provider, this
 * class is registered as a custom property ({@code millenaire:amulet_score}) keyed in
 * {@link RangeSelectItemModelProperties#ID_MAPPER}; the per-amulet JSON sets {@code "kind"}.
 *
 * <p>The three score formulas are ported verbatim from ItemAmuletVishnu/Alchemist/Yggdrasil.
 */
@Environment(EnvType.CLIENT)
public record AmuletScoreProperty(Kind kind) implements RangeSelectItemModelProperty {
   public static final Identifier ID = Identifier.fromNamespaceAndPath("millenaire", "amulet_score");

   public static final MapCodec<AmuletScoreProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(Kind.CODEC.fieldOf("kind").forGetter(AmuletScoreProperty::kind)).apply(i, AmuletScoreProperty::new)
   );

   /** Registers the custom property type so {@code "property":"millenaire:amulet_score"} resolves. */
   public static void register() {
      RangeSelectItemModelProperties.ID_MAPPER.put(ID, MAP_CODEC);
   }

   @Override
   public float get(final ItemStack itemStack, final @Nullable ClientLevel level, final @Nullable ItemOwner owner, final int seed) {
      if (owner == null || level == null) {
         return 0.0F;
      }

      Vec3 pos = owner.position();
      Point p = new Point(pos.x, pos.y, pos.z);
      return switch (this.kind) {
         case VISHNU -> vishnuScore(level, p);
         case ALCHEMIST -> alchemistScore(level, p);
         case YGGDRASIL -> yggdrasilScore(p);
      };
   }

   /** Proximity of the nearest hostile mob within 20 blocks → (20-distance)/20 * 15, else 0. */
   private static float vishnuScore(final ClientLevel level, final Point p) {
      double closestDistance = Double.MAX_VALUE;
      for (Monster ent : WorldUtilities.getEntitiesWithinAABB(level, Monster.class, p, 20, 20)) {
         double d = p.distanceTo(ent);
         if (d < closestDistance) {
            closestDistance = d;
         }
      }

      double levelValue = closestDistance > 20.0 ? 0.0 : (20.0 - closestDistance) / 20.0;
      return (float)(levelValue * 15.0);
   }

   /** Sum of ore "richness" in a 5-block radius (coal=1, iron/redstone=5, lapis/gold=10,
    * diamond/emerald=30), capped at 100, scaled to 0..15. */
   private static float alchemistScore(final ClientLevel level, final Point p) {
      float score = 0.0F;
      int startY = Math.max(p.getiY() - 5, level.getMinY());
      int endY = Math.min(p.getiY() + 5, level.getMaxY());

      for (int i = p.getiX() - 5; i < p.getiX() + 5; i++) {
         for (int j = p.getiZ() - 5; j < p.getiZ() + 5; j++) {
            for (int k = startY; k < endY; k++) {
               score += oreValue(WorldUtilities.getBlock(level, i, k, j));
            }
         }
      }

      if (score > 100.0F) {
         score = 100.0F;
      }

      return score * 15.0F / 100.0F;
   }

   /** floor(Y)/8, capped at 127, → 0..15. */
   private static float yggdrasilScore(final Point p) {
      int level = (int)Math.floor(p.getiY());
      if (level > 127) {
         level = 127;
      }
      return level / 8.0F >= 15.0F ? 15.0F : (float)(level / 8);
   }

   /** Ore richness weights, matching 1.12 ItemAmuletAlchemist (deepslate variants share the weight). */
   private static float oreValue(final Block block) {
      if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
         return 1.0F;
      } else if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
         return 30.0F;
      } else if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
         return 30.0F;
      } else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
         return 10.0F;
      } else if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
         return 5.0F;
      } else if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
         return 10.0F;
      } else if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
         return 5.0F;
      }
      return 0.0F;
   }

   @Override
   public MapCodec<? extends RangeSelectItemModelProperty> type() {
      return MAP_CODEC;
   }

   /** The three amulets, selected by the {@code "kind"} JSON field. */
   @Environment(EnvType.CLIENT)
   public enum Kind implements net.minecraft.util.StringRepresentable {
      VISHNU("vishnu"),
      ALCHEMIST("alchemist"),
      YGGDRASIL("yggdrasil");

      public static final com.mojang.serialization.Codec<Kind> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Kind::values);
      private final String name;

      Kind(final String name) {
         this.name = name;
      }

      @Override
      public String getSerializedName() {
         return this.name;
      }
   }
}
