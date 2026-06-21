package org.millenaire.client.render;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;

/**
 * Custom render state for {@link RenderMillVillager}, carrying the data the 1.12 immediate-mode
 * renderer pulled directly off the entity at draw time: the per-villager texture, the per-record
 * model scale, the cloth-layer textures, and the multi-line floating label text.
 *
 * <p>26.2 PORT NOTE: 26.2 split rendering into an extract pass (entity → state, here) and a submit
 * pass (state → {@code SubmitNodeCollector}). The renderer is no longer allowed to touch the live
 * entity in {@code submit}, so everything the floating text/overlays need must be copied here in
 * {@code extractRenderState}.
 */
@Environment(EnvType.CLIENT)
public class MillVillagerRenderState extends HumanoidRenderState {
   /** A coloured floating label line. */
   public record LabelLine(String text, int colour) {}

   /** Per-villager body texture (resolved from {@code MillVillager.texture}). */
   public Identifier texture = null;

   /** Cloth-layer textures, index 0 and 1 (null when that layer is absent). */
   public final Identifier[] clothTextures = new Identifier[2];

   /** Per-record model scale (1.0 when no record). */
   public float millScale = 1.0F;

   /** Travel-book mock villager holding a main-hand item — drives the 1.12 left-arm "presenting" pose. */
   public boolean mockHoldingMainHand = false;

   /** Travel-book mock villager holding an off-hand item — drives the 1.12 right-arm "presenting" pose. */
   public boolean mockHoldingOffHand = false;

   /** Floating label lines, bottom-to-top order as they should stack above the head. */
   public final List<LabelLine> labelLines = new ArrayList<>();

   /**
    * Resolved floating special-icon row (chief crown, current-goal icon, purse, denier, raider axe),
    * built in the extract pass via {@code ItemModelResolver.updateForTopItem}. Drawn above the head.
    */
   public final List<ItemStackRenderState> specialIcons = new ArrayList<>();
}
