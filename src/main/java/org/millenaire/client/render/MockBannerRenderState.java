package org.millenaire.client.render;

import com.mojang.math.Transformation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Render state for {@link TESRMockBanner}: the banner attachment type (standing/wall), its base
 * colour, the resolved pattern layers, and the standing/wall transform.
 */
@Environment(EnvType.CLIENT)
public class MockBannerRenderState extends BlockEntityRenderState {
   public BannerBlock.AttachmentType attachmentType = BannerBlock.AttachmentType.WALL;
   public DyeColor baseColor = DyeColor.WHITE;
   public BannerPatternLayers patterns = BannerPatternLayers.EMPTY;
   public Transformation transformation = Transformation.IDENTITY;
}
