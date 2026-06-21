package org.millenaire.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.entity.MillVillager;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.goal.Goal;
import org.millenaire.common.item.MillItems;
import org.millenaire.common.utilities.LanguageUtilities;
import org.millenaire.common.utilities.MillLog;

/**
 * Entity renderer for Mill villagers.
 *
 * <p>26.2 PORT NOTE: 1.12 {@code RenderBiped}/{@code RenderLivingBase} + {@code RenderManager} +
 * {@code GlStateManager}/{@code Tessellator} → {@link HumanoidMobRenderer} over the render-state
 * extraction model.
 *
 * <p>IMPLEMENTED against 26.2:
 * <ul>
 *   <li>Per-villager texture: carried on {@link MillVillagerRenderState} and returned from
 *       {@link #getTextureLocation}.
 *   <li>Per-record model scale: applied in {@link #extractRenderState} via {@code state.scale}.
 *   <li>Sleeping rotation/offset: now handled by the base {@code LivingEntityRenderer} via the
 *       {@code SLEEPING} pose + {@code bedOrientation} on the render state.
 *   <li>Cloth layers: {@link LayerVillagerClothes} (inflated cloth model + per-layer texture).
 *   <li>Floating name / occupation / speech / status text: rebuilt in {@link #extractRenderState}
 *       into {@code state.labelLines}, then drawn in {@link #submit} via the {@code submitText}
 *       node (one stacked line per entry), replacing the 1.12 immediate-mode {@code drawNameplate}.
 *   <li>Floating special-icon row (chief crown, current-goal icon, purse, denier, raider axe):
 *       resolved into {@code ItemStackRenderState}s in {@link #extractRenderState} via
 *       {@link ItemModelResolver#updateForTopItem} (the same approach as the firepit/panel TESRs),
 *       then submitted billboarded above the head in {@link #submit}.
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class RenderMillVillager extends HumanoidMobRenderer<MillVillager, MillVillagerRenderState, ModelMillVillager> {
   private static final Identifier DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath("millenaire", "textures/entity/default.png");
   /** Vertical gap between stacked floating-text lines, in world units (matches 1.12 0.25F). */
   private static final float LINE_HEIGHT = 0.25F;

   private final ItemModelResolver itemModelResolver;
   /** [MILLDEBUG] villager ids already logged on first render this session (rate-limit to once per id). */
   private static final java.util.Set<Long> DEBUG_LOGGED_IDS = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

   public RenderMillVillager(EntityRendererProvider.Context context, ModelLayerLocation bodyLayer, ModelLayerLocation cloth0, ModelLayerLocation cloth1) {
      super(context, new ModelMillVillager(context.bakeLayer(bodyLayer)), 0.5F);
      this.itemModelResolver = context.getItemModelResolver();
      this.addLayer(new LayerVillagerClothes(this, 0, new ModelMillVillager(context.bakeLayer(cloth0))));
      this.addLayer(new LayerVillagerClothes(this, 1, new ModelMillVillager(context.bakeLayer(cloth1))));
   }

   @Override
   public MillVillagerRenderState createRenderState() {
      return new MillVillagerRenderState();
   }

   @Override
   public void extractRenderState(MillVillager entity, MillVillagerRenderState state, float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.texture = entity.texture;
      state.clothTextures[0] = entity.getClothTexturePath(0);
      state.clothTextures[1] = entity.getClothTexturePath(1);
      // Per-record scale (1.12 preRenderScale): fold into the living-entity render scale.
      float recordScale = entity.getRecord() != null ? entity.getRecord().scale : 1.0F;
      state.millScale = recordScale;
      state.scale *= recordScale;
      // 1.12 setRotationAngles posed the arms of a travel-book mock villager that holds an item; carry
      // the held-item flags so ModelMillVillager.setupAnim can reapply that "presenting" pose.
      state.mockHoldingMainHand = entity.travelBookMockVillager && entity.heldItem != null && !entity.heldItem.isEmpty();
      state.mockHoldingOffHand = entity.travelBookMockVillager && entity.heldItemOffHand != null && !entity.heldItemOffHand.isEmpty();
      buildLabelLines(entity, state);
      buildSpecialIcons(entity, state);

      // [MILLDEBUG] On the first render of each villager id, log the texture Identifier the renderer
      // will bind and whether the body model resolved, so missing/wrong-texture issues show in the log.
      if (MillLog.debugOn() && DEBUG_LOGGED_IDS.add(entity.getVillagerId())) {
         MillLog.milldebug(
            "Render",
            "villager FIRST render id=" + entity.getVillagerId() + " name='" + entity.getVillagerName() + "'"
               + " boundTexture=" + getTextureLocation(state)
               + " clothTex[0]=" + state.clothTextures[0] + " clothTex[1]=" + state.clothTextures[1]
               + " modelResolved=" + (this.getModel() != null) + " scale=" + state.scale
         );
      }
   }

   /**
    * Resolve the floating special-icon row into {@code ItemStackRenderState}s. Mirrors the 1.12
    * {@code defineSpecialIcons}: chief crown (golden helmet), the current goal's floating icon, the
    * foreign-merchant purse, the hireable denier and the raider axe.
    */
   private void buildSpecialIcons(MillVillager villager, MillVillagerRenderState state) {
      state.specialIcons.clear();
      if (villager.vtype == null) {
         return;
      }

      List<ItemStack> stacks = new ArrayList<>();
      if (villager.vtype.isChief) {
         stacks.add(Items.GOLDEN_HELMET.getDefaultInstance());
      }
      Goal goal = villager.getCurrentGoal();
      if (goal != null && goal.getFloatingIcon() != null && !goal.getFloatingIcon().isEmpty()) {
         stacks.add(goal.getFloatingIcon());
      }
      if (villager.isForeignMerchant() && MillItems.PURSE != null) {
         stacks.add(MillItems.PURSE.getDefaultInstance());
      }
      if (villager.vtype.hireCost > 0 && MillItems.DENIER != null) {
         stacks.add(MillItems.DENIER.getDefaultInstance());
      }
      if (villager.isRaider && MillItems.NORMAN_AXE != null) {
         stacks.add(MillItems.NORMAN_AXE.getDefaultInstance());
      }

      int seed = 0;
      for (ItemStack stack : stacks) {
         ItemStackRenderState iconState = new ItemStackRenderState();
         this.itemModelResolver.updateForTopItem(iconState, stack, ItemDisplayContext.GROUND, villager.level(), villager, seed++);
         state.specialIcons.add(iconState);
      }
   }

   /**
    * Recreate the 1.12 {@code doRenderVillagerName} text stack into render-state lines. Only the
    * player-facing rows are reproduced (the DEV path-debug rows are dropped). Lines are stored
    * bottom-to-top so they stack upward above the head, matching the original order.
    */
   private static void buildLabelLines(MillVillager villager, MillVillagerRenderState state) {
      state.labelLines.clear();
      if (villager.vtype == null) {
         return;
      }
      // 1.12 only drew the floating label within VillagersNamesDistance of the player; match that so far-off
      // villagers don't clutter the screen.
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc.player != null && villager.distanceTo(mc.player) > MillConfigValues.VillagersNamesDistance) {
         return;
      }

      String playerName = Mill.proxy != null ? Mill.proxy.getSinglePlayerName() : "";
      String gameSpeech = villager.getGameSpeech(playerName);
      String nativeSpeech = villager.getNativeSpeech(playerName);

      // Speech bubbles (wrapped at 60 chars like the original).
      addWrapped(state, gameSpeech, 0xA1_F1_F6_FB);
      addWrapped(state, nativeSpeech, 0xA0_69_8E_9C);

      // Current-goal name.
      if (MillConfigValues.displayNames) {
         Goal goal = villager.getCurrentGoal();
         if (goal != null) {
            state.labelLines.add(new MillVillagerRenderState.LabelLine(goal.gameName(villager), 0xA1_E8_72_2E));
         }
      }

      // Raider tag.
      if (villager.isRaider) {
         state.labelLines.add(new MillVillagerRenderState.LabelLine(LanguageUtilities.string("ui.raider"), 0xA1_E6_E3_3B));
      }

      // Health (when the villager type opts in).
      if (villager.vtype.showHealth) {
         String health = LanguageUtilities.string("hire.health") + ": " + villager.getHealth() * 0.5 + "/" + villager.getMaxHealth() * 0.5;
         state.labelLines.add(new MillVillagerRenderState.LabelLine(health, 0xA0_69_8E_9C));
      }

      // Name + native occupation (the top row).
      if (MillConfigValues.displayNames && !villager.vtype.hideName) {
         String name = villager.getVillagerName() + ", " + villager.getNativeOccupationName();
         state.labelLines.add(new MillVillagerRenderState.LabelLine(name, 0xA1_F1_F6_FB));
      }
   }

   private static void addWrapped(MillVillagerRenderState state, String text, int colour) {
      if (text == null) {
         return;
      }
      List<String> lines = new ArrayList<>();
      String line = text;
      while (line.length() > 60) {
         int cutoff = line.lastIndexOf(' ', 60);
         if (cutoff == -1) {
            cutoff = 60;
         }
         lines.add(line.substring(0, cutoff));
         line = line.substring(cutoff).trim();
      }
      lines.add(line);
      // Original stacked the wrapped lines bottom-to-top (iterated last→first); preserve that.
      for (int i = lines.size() - 1; i >= 0; i--) {
         state.labelLines.add(new MillVillagerRenderState.LabelLine(lines.get(i), colour));
      }
   }

   @Override
   public Identifier getTextureLocation(MillVillagerRenderState state) {
      return state.texture != null ? state.texture : DEFAULT_TEXTURE;
   }

   @Override
   public void submit(MillVillagerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      super.submit(state, poseStack, collector, camera);
      submitLabelLines(state, poseStack, collector, camera);
      submitSpecialIcons(state, poseStack, collector, camera);
   }

   /**
    * Draw the floating special-icon row above the villager's head, billboarded toward the camera.
    * Mirrors the 1.12 {@code renderIcons}: icons sit at {@code height + 0.5} above the entity, are
    * centred horizontally ({@code pos - (n-1)/2}) and spaced 0.5 world-units apart, at ~0.5 scale.
    */
   private void submitSpecialIcons(MillVillagerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      if (state.specialIcons.isEmpty()) {
         return;
      }
      int n = state.specialIcons.size();
      float topOfHead = state.boundingBoxHeight + 0.5F;
      for (int pos = 0; pos < n; pos++) {
         ItemStackRenderState iconState = state.specialIcons.get(pos);
         if (iconState.isEmpty()) {
            continue;
         }
         float offset = (pos - (n - 1) / 2.0F) * 0.5F;
         poseStack.pushPose();
         poseStack.translate(0.0, topOfHead, 0.0);
         poseStack.mulPose(camera.orientation);
         // Billboarded icons face the camera; spread along the camera-local X axis.
         poseStack.translate(offset, 0.0, 0.0);
         poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F)); // flip upright (GROUND model is laid out for top-down)
         poseStack.scale(0.5F, 0.5F, 0.5F);
         iconState.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
         poseStack.popPose();
      }
   }

   /**
    * Draw the Mill floating-text stack above the villager. The vanilla name-tag submit pass is left
    * disabled (see {@link #getNameTag}); these custom lines replace it. Lines stack upward starting
    * just above where the vanilla name tag would sit. Billboarded toward the camera via the camera
    * render-state orientation quaternion (the same transform vanilla name tags use).
    */
   private void submitLabelLines(MillVillagerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      if (state.labelLines.isEmpty()) {
         return;
      }
      Font font = this.getFont();
      poseStack.pushPose();
      // Position from the bounding box (like the icon row), NOT nameTagAttachment: Mill suppresses the vanilla
      // name tag (getNameTag→null), so vanilla never populates nameTagAttachment — it stays null and the whole
      // speech/goal/name stack was being skipped. boundingBoxHeight+0.5 is where the vanilla tag would sit.
      poseStack.translate(0.0, state.boundingBoxHeight + 0.5, 0.0);
      poseStack.mulPose(camera.orientation);
      // ROOT-CAUSE FIX (label was GENUINELY ABSENT): the X scale must be POSITIVE, exactly like the
      // vanilla name-tag path (EntityRenderer/SubmitNodeCollection.submitNameTag uses scale(+0.025,
      // -0.025, +0.025)) and like the working TESRPanel text (scale(+,-,+)). The previous -0.025F on X
      // gave the scale matrix a POSITIVE determinant ((-)*(-)*(+)), i.e. the OPPOSITE triangle winding
      // from vanilla's NEGATIVE determinant ((+)*(-)*(+)). The world-text render pipeline (RenderPipelines
      // .TEXT) culls back-faces (cull defaults to true), so the glyph quads ended up facing away from the
      // camera and were entirely culled — no text drawn at all, regardless of light/position. Matching the
      // vanilla +X sign restores the correct front-facing winding so the glyphs render.
      poseStack.scale(0.025F, -0.025F, 0.025F);

      float lineGap = 10.0F; // text-space pixels between lines
      float y = 0.0F;
      for (MillVillagerRenderState.LabelLine entry : state.labelLines) {
         FormattedCharSequence seq = Component.literal(entry.text()).getVisualOrderText();
         float x = -font.width(entry.text()) / 2.0F;
         collector.submitText(
            poseStack, x, y, seq, false,
            Font.DisplayMode.NORMAL,
            // Name-tag text is full-bright (like vanilla name tags) — using the entity's lightCoords made the
            // label render dim/invisible at night or indoors, which is why the speech/goal stack "didn't show".
            // 15728880 == packed full-bright (block 15, sky 15).
            15728880, entry.colour(),
            // Semi-transparent dark background box so the text reads against any wall, matching vanilla tags.
            (int) (net.minecraft.client.Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24, 0);
         y -= lineGap; // stack upward (negative y is up after the -0.025F flip)
      }
      poseStack.popPose();
   }

   @Override
   protected Component getNameTag(MillVillager entity) {
      // Mill draws its own multi-line label (name + occupation + speech). Suppress the vanilla tag.
      return null;
   }

   // --- Renderer factories. Each villager subtype shares the renderer but bakes a different body
   //     + cloth model set. -----------------------------------------------------------------------
   public static final EntityRendererProvider<MillVillager> FACTORY_MALE =
      context -> new RenderMillVillager(context, MillModelLayers.VILLAGER_MALE,
         MillModelLayers.VILLAGER_MALE_CLOTH_0, MillModelLayers.VILLAGER_MALE_CLOTH_1);

   public static final EntityRendererProvider<MillVillager> FACTORY_FEMALE_ASYM =
      context -> new RenderMillVillager(context, MillModelLayers.VILLAGER_FEMALE_ASYM,
         MillModelLayers.VILLAGER_FEMALE_ASYM_CLOTH_0, MillModelLayers.VILLAGER_FEMALE_ASYM_CLOTH_1);

   public static final EntityRendererProvider<MillVillager> FACTORY_FEMALE_SYM =
      context -> new RenderMillVillager(context, MillModelLayers.VILLAGER_FEMALE_SYM,
         MillModelLayers.VILLAGER_FEMALE_SYM_CLOTH_0, MillModelLayers.VILLAGER_FEMALE_SYM_CLOTH_1);
}
