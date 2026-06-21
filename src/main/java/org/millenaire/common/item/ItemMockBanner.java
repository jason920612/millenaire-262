package org.millenaire.common.item;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.RotationSegment;

import org.millenaire.common.entity.TileEntityMockBanner;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillRegistry;

/**
 * Decorative "mock banner" (village / culture banner) that places a custom banner block carrying a
 * preset design. 1.12 was a metadata item that built the banner NBT with {@code JsonToNBT} +
 * {@code getOrCreateSubCompound("BlockEntityTag")} and {@code DyeColor.getDyeDamage()}.
 *
 * <p>26.2: item metadata, the {@code Patterns}/{@code Base} NBT format, and {@code getDyeDamage} are
 * all gone — banners are component/data-driven ({@code BannerPatternLayers}). The placement logic is
 * ported to {@code useOn(UseOnContext)}; the design payload is a TODO pending the data-driven banner
 * port.
 */
public class ItemMockBanner extends BlockItem {
   public static int BANNER_VILLAGE = 0;
   public static int BANNER_CULTURE = 1;
   public static final DyeColor[] BANNER_COLOURS = new DyeColor[]{DyeColor.RED, DyeColor.YELLOW};
   public static final String[] BANNER_DESIGNS = new String[]{
      "{Patterns:[{Pattern:dls,Color:15},{Pattern:ls,Color:15}]}", "{Patterns:[{Pattern:ls,Color:0},{Pattern:ts,Color:0},{Pattern:bs,Color:0}]}"
   };
   private final int bannerDesign;
   private final WallBannerBlock wallBanner;
   private final DyeColor color;

   /**
    * Maps the 1.12 vanilla banner-pattern hashnames (used by the village/culture BANNER_DESIGNS) to
    * the 26.2 {@link BannerPatterns} registry keys. Mill's own culture-specific pattern codes (byz/may/…
    * — see Mill.BANNER_SHORTNAMES) are resolved separately via
    * {@link org.millenaire.common.forge.MillBannerPatterns} against the data-driven
    * {@code millenaire:<long>} banner_pattern registry.
    */
   private static final Map<String, ResourceKey<BannerPattern>> VANILLA_PATTERN_CODES = Map.ofEntries(
      Map.entry("b", BannerPatterns.BASE),
      Map.entry("bs", BannerPatterns.STRIPE_BOTTOM),
      Map.entry("ts", BannerPatterns.STRIPE_TOP),
      Map.entry("ls", BannerPatterns.STRIPE_LEFT),
      Map.entry("rs", BannerPatterns.STRIPE_RIGHT),
      Map.entry("cs", BannerPatterns.STRIPE_CENTER),
      Map.entry("ms", BannerPatterns.STRIPE_MIDDLE),
      Map.entry("drs", BannerPatterns.STRIPE_DOWNRIGHT),
      Map.entry("dls", BannerPatterns.STRIPE_DOWNLEFT),
      Map.entry("ss", BannerPatterns.STRIPE_SMALL),
      Map.entry("cr", BannerPatterns.CROSS),
      Map.entry("sc", BannerPatterns.STRAIGHT_CROSS),
      Map.entry("ld", BannerPatterns.DIAGONAL_LEFT),
      Map.entry("rud", BannerPatterns.DIAGONAL_RIGHT),
      Map.entry("lud", BannerPatterns.DIAGONAL_LEFT_MIRROR),
      Map.entry("rd", BannerPatterns.DIAGONAL_RIGHT_MIRROR),
      Map.entry("vh", BannerPatterns.HALF_VERTICAL),
      Map.entry("vhr", BannerPatterns.HALF_VERTICAL_MIRROR),
      Map.entry("hh", BannerPatterns.HALF_HORIZONTAL),
      Map.entry("hhb", BannerPatterns.HALF_HORIZONTAL_MIRROR),
      Map.entry("bo", BannerPatterns.BORDER),
      Map.entry("cbo", BannerPatterns.CURLY_BORDER),
      Map.entry("gra", BannerPatterns.GRADIENT),
      Map.entry("gru", BannerPatterns.GRADIENT_UP),
      Map.entry("bri", BannerPatterns.BRICKS),
      Map.entry("mc", BannerPatterns.CIRCLE_MIDDLE),
      Map.entry("mr", BannerPatterns.RHOMBUS_MIDDLE),
      Map.entry("bt", BannerPatterns.TRIANGLE_BOTTOM),
      Map.entry("tt", BannerPatterns.TRIANGLE_TOP),
      Map.entry("bts", BannerPatterns.TRIANGLES_BOTTOM),
      Map.entry("tts", BannerPatterns.TRIANGLES_TOP)
   );

   /**
    * Builds the banner stack: a coloured {@link net.minecraft.world.item.Items#BANNER} (the 1.12 base
    * meta = dye damage is now the per-colour item) carrying a {@link BannerPatternLayers} component
    * built from the legacy {@code Patterns} NBT. Pattern codes resolve through the dynamic
    * banner-pattern registry (via the running server's RegistryAccess); unresolvable Mill-custom codes
    * are skipped (those still need data-driven banner_pattern registration).
    */
   public static ItemStack makeBanner(Item banner, DyeColor color, @Nullable CompoundTag patterns) {
      ItemStack itemstack = new ItemStack(banner, 1);
      if (patterns != null && patterns.contains("Patterns") && !Mill.serverWorlds.isEmpty()) {
         RegistryAccess registryAccess = Mill.serverWorlds.get(0).world.registryAccess();
         net.minecraft.core.HolderLookup.RegistryLookup<BannerPattern> reg = registryAccess.lookupOrThrow(Registries.BANNER_PATTERN);
         BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
         ListTag list = patterns.getListOrEmpty("Patterns");
         boolean any = false;
         for (int i = 0; i < list.size(); i++) {
            CompoundTag layer = list.getCompoundOrEmpty(i);
            String code = layer.getStringOr("Pattern", "");
            // Mill's own culture patterns (byz/may/sjkr/… — see Mill.BANNER_SHORTNAMES) resolve through
            // the data-driven millenaire:<long> banner_pattern registry; vanilla codes (ls/ts/…) map to
            // the BannerPatterns keys. Try Mill codes first, then vanilla.
            ResourceKey<BannerPattern> key = org.millenaire.common.forge.MillBannerPatterns.getKey(code);
            if (key == null) {
               key = VANILLA_PATTERN_CODES.get(code);
            }
            if (key == null) {
               continue; // unknown pattern code
            }
            // 1.12 stored the pattern colour as dye-damage (15=white .. 0=black); 26.2 DyeColor.byId is 0=white.
            DyeColor layerColor = DyeColor.byId(15 - layer.getIntOr("Color", 0));
            builder.add(reg.getOrThrow(key), layerColor);
            any = true;
         }

         if (any) {
            itemstack.set(DataComponents.BANNER_PATTERNS, builder.build());
         }
      }

      return itemstack;
   }

   public ItemMockBanner(BannerBlock standingBanner, WallBannerBlock wallBanner, DyeColor color, int design) {
      super(standingBanner, new Item.Properties().stacksTo(16).setId(MillRegistry.itemKeyFor(standingBanner)));
      this.wallBanner = wallBanner;
      this.color = color;
      this.bannerDesign = design;
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      Level worldIn = context.getLevel();
      BlockPos pos = context.getClickedPos();
      Direction facing = context.getClickedFace();
      if (player == null) {
         return InteractionResult.FAIL;
      }

      BlockState iblockstate = worldIn.getBlockState(pos);
      boolean replaceable = iblockstate.canBeReplaced();
      if (facing != Direction.DOWN && (iblockstate.isSolid() || replaceable) && (!replaceable || facing == Direction.UP)) {
         pos = pos.relative(facing);
         ItemStack itemstack = player.getItemInHand(context.getHand());
         if (player.mayUseItemAt(pos, facing, itemstack)) {
            pos = replaceable ? pos.below() : pos;
            // 1.12 also required the banner block itself to be placeable here (func_176196_c). Without
            // this the mock banner can be placed where it can't survive (unsupported) and instantly pops.
            BlockState toPlace = facing == Direction.UP
               ? this.getBlock().defaultBlockState()
               : this.wallBanner.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
            if (!toPlace.canSurvive(worldIn, pos)) {
               return InteractionResult.FAIL;
            }

            if (facing == Direction.UP) {
               int rot = RotationSegment.convertToSegment(player.getYRot() + 180.0F);
               worldIn.setBlock(pos, this.getBlock().defaultBlockState().setValue(BannerBlock.ROTATION, rot), 3);
            } else {
               worldIn.setBlock(pos, this.wallBanner.defaultBlockState().setValue(WallBannerBlock.FACING, facing), 3);
            }

            BlockEntity bannerEntity = worldIn.getBlockEntity(pos);
            if (bannerEntity instanceof TileEntityMockBanner) {
               // Store a banner stack carrying the preset base colour + pattern layers (built as data
               // components by makeBanner) so the BE / its renderer have the full design, mirroring the
               // 1.12 BlockEntityTag/Base path. Falls back to the held stack if the design fails to parse.
               ItemStack designed = itemstack.copy();
               try {
                  designed = makeBanner(this, this.color, net.minecraft.nbt.TagParser.parseCompoundFully(BANNER_DESIGNS[this.bannerDesign]));
               } catch (Exception ignored) {
                  // keep the held-stack copy
               }
               ((TileEntityMockBanner)bannerEntity).setItemValues(designed, true);
            }

            itemstack.shrink(1);
            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.FAIL;
         }
      } else {
         return InteractionResult.FAIL;
      }
   }
}
