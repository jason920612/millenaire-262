package org.millenaire.common.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;

import org.millenaire.common.advancements.MillAdvancements;
import org.millenaire.common.forge.MillRegistry;
import org.millenaire.common.item.ItemPaintBucket;
import org.millenaire.common.utilities.Point;

public class BlockPaintedBricks extends Block implements IPaintedBlock {
   public static final BooleanProperty TOP_FRIEZE = BooleanProperty.create("top_frieze");
   public static final BooleanProperty BOTTOM_FRIEZE = BooleanProperty.create("bottom_frieze");
   private final String baseBlockName;
   private final DyeColor colour;

   public static BlockState getBlockStateWithColour(BlockState input, DyeColor colour) {
      IPaintedBlock paintedBlock = (IPaintedBlock) input.getBlock();
      Block newBlock = MillBlocks.PAINTED_BRICK_MAP.get(paintedBlock.getBlockType()).get(colour);
      return newBlock.defaultBlockState();
   }

   public static String getColorName(DyeColor colour) {
      String colourName = colour.getName();
      if (colourName.equalsIgnoreCase("lightBlue")) {
         colourName = "light_blue";
      }
      return colourName;
   }

   public static DyeColor getColourFromBlockState(BlockState bs) {
      return bs.getBlock() instanceof IPaintedBlock ? ((IPaintedBlock) bs.getBlock()).getDyeColour() : null;
   }

   public BlockPaintedBricks(String baseBlockName, DyeColor colour) {
      super(BlockBehaviour.Properties.of()
         .setId(MillRegistry.blockKey(baseBlockName + "_" + getColorName(colour)))
         .sound(SoundType.STONE)
         .strength(1.5F, 10.0F));
      this.baseBlockName = baseBlockName;
      this.colour = colour;
      this.registerDefaultState(this.stateDefinition.any().setValue(TOP_FRIEZE, false).setValue(BOTTOM_FRIEZE, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(TOP_FRIEZE, BOTTOM_FRIEZE);
   }

   public int friezePriority(LevelReader level, BlockPos pos, BlockState otherState, Direction side) {
      if (getColourFromBlockState(otherState) == this.colour) {
         return otherState.isFaceSturdy(level, pos, side) ? 0 : 3;
      } else if (otherState.isAir()) {
         return 5;
      } else if (otherState.getBlock() instanceof IronBarsBlock) {
         return 2;
      } else {
         return otherState.getBlock() instanceof IPaintedBlock ? 1 : 10;
      }
   }

   private BlockState computeFrieze(BlockState state, LevelReader level, BlockPos pos) {
      int topPriority = this.friezePriority(level, pos.above(), level.getBlockState(pos.above()), Direction.DOWN);
      int bottomPriority = this.friezePriority(level, pos.below(), level.getBlockState(pos.below()), Direction.UP);
      if (topPriority > bottomPriority) {
         return state.setValue(TOP_FRIEZE, true).setValue(BOTTOM_FRIEZE, false);
      } else if (bottomPriority > 0) {
         return state.setValue(BOTTOM_FRIEZE, true).setValue(TOP_FRIEZE, false);
      } else {
         return state.setValue(BOTTOM_FRIEZE, false).setValue(TOP_FRIEZE, false);
      }
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return this.computeFrieze(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
   }

   @Override
   protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
         Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
      if (direction == Direction.UP || direction == Direction.DOWN) {
         return this.computeFrieze(state, level, pos);
      }
      return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
   }

   @Override
   public String getBlockType() {
      return this.baseBlockName;
   }

   @Override
   public DyeColor getDyeColour() {
      return this.colour;
   }

   @Override
   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
         InteractionHand hand, BlockHitResult hitResult) {
      if (stack.getItem() instanceof ItemPaintBucket bucket) {
         DyeColor targetColor = bucket.getColour();
         if (targetColor != null && this.colour != targetColor) {
            List<Point> pointsToTest = new ArrayList<>();
            pointsToTest.add(new Point(pos));
            int blockColoured = 0;

            while (!pointsToTest.isEmpty()) {
               Point p = pointsToTest.get(pointsToTest.size() - 1);
               BlockState bs = p.getBlockActualState(level);
               if (getColourFromBlockState(bs) == this.colour) {
                  p.setBlockState(level, getBlockStateWithColour(bs, targetColor));
                  blockColoured++;
                  pointsToTest.add(p.getAbove());
                  pointsToTest.add(p.getBelow());
                  pointsToTest.add(p.getNorth());
                  pointsToTest.add(p.getEast());
                  pointsToTest.add(p.getSouth());
                  pointsToTest.add(p.getWest());
               }
               pointsToTest.remove(p);
            }

            if (blockColoured < stack.getMaxDamage() - stack.getDamageValue()) {
               stack.hurtAndBreak(blockColoured, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            } else {
               player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }

            MillAdvancements.RAINBOW.grant(player);
            return InteractionResult.SUCCESS;
         }
      }
      return InteractionResult.PASS;
   }
}
