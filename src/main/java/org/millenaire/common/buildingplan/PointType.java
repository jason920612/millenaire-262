package org.millenaire.common.buildingplan;

import java.util.HashMap;
import java.util.Optional;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import org.millenaire.common.config.MillConfigValues;
import org.millenaire.common.convert.BlockSpec;
import org.millenaire.common.convert.ItemSpec;
import org.millenaire.common.convert.MillConvert;
import org.millenaire.common.convert.PlanColour;
import org.millenaire.common.item.InvItem;
import org.millenaire.common.utilities.MillLog;

public class PointType {
   public static final String SUBTYPE_SIGN = "sign";
   public static final String SUBTYPE_MAINCHEST = "mainchest";
   public static final String SUBTYPE_LOCKEDCHEST = "lockedchest";
   public static final String SUBTYPE_VILLAGEBANNERWALL = "villageBannerWall";
   public static final String SUBTYPE_VILLAGEBANNERSTANDING = "villageBannerStanding";
   public static final String SUBTYPE_CULTUREBANNERWALL = "cultureBannerWall";
   public static final String SUBTYPE_CULTUREBANNERSTANDING = "cultureBannerStanding";
   public static HashMap<Integer, PointType> colourPoints = new HashMap<>();
   final int colour;
   final String specialType;
   final String label;
   private final Block block;
   private final int meta;
   private final BlockState blockState;
   private InvItem costItem = null;
   private BlockState costBlockState = null;
   private int costQuantity = 1;
   boolean secondStep = false;

   static PointType readColourPoint(String s) throws Exception {
      String[] params = s.trim().split(";", -1);
      if (params.length != 5 && params.length != 8) {
         throw new MillLog.MillenaireException("Line " + s + " in blocklist.txt does not have five or eight fields.");
      } else {
         String[] rgb = params[4].split("/", -1);
         if (rgb.length != 3) {
            throw new MillLog.MillenaireException("Colour in line " + s + " does not have three values.");
         } else {
            int colour = (Integer.parseInt(rgb[0]) << 16) + (Integer.parseInt(rgb[1]) << 8) + (Integer.parseInt(rgb[2]) << 0);
            String param_special_type = params[0];
            String param_block_location = params[1];
            String param_meta_or_values = params[2];
            String param_set_after = params[3];
            String param_cost_itemorblock = null;
            String param_cost_quantity = null;
            if (params.length >= 8) {
               param_cost_itemorblock = params[5];
               param_cost_quantity = params[7];
            }

            if (MillConfigValues.LogBuildingPlan >= 1) {
               MillLog.major(null, "Loading colour point: " + BuildingPlan.getColourString(colour) + ", " + param_special_type);
            }

            if (param_block_location.length() == 0) {
               // Special-type-only line (no block). These keep their own cost handling because their cost
               // can be an "item:"-prefixed InvItem (banners) that the unified BlockSpec path does not carry.
               return readSpecialPoint(s, colour, param_special_type, param_cost_itemorblock, param_cost_quantity);
            }

            // Block-bearing line: the block, its state and its (simple) cost come from the unified
            // conversion table parsed once in LegacyTables.loadPlanColours(). The meta column is still kept
            // here for the few exporters that read getMeta() (PANEL->wall sign, flower pots). A colour the
            // central parse could not resolve (e.g. a stale custom-mod line) was already logged-and-skipped
            // there; surface it as the recoverable MillenaireException so loadBuildingPointsFile skips this
            // line too (matching the legacy per-line content-error contract) rather than fatalising.
            BlockSpec spec;
            try {
               spec = MillConvert.planColourToSpec(new PlanColour(colour));
            } catch (RuntimeException unresolved) {
               throw new MillLog.MillenaireException(
                  "Block for line " + s + " could not be resolved: " + unresolved.getMessage()
               );
            }
            int meta = param_meta_or_values.matches("\\d+") ? Integer.parseInt(param_meta_or_values) : 0;
            PointType pt = new PointType(colour, param_special_type, spec, meta, Boolean.parseBoolean(param_set_after));
            pt.applyCost(spec.cost());
            return pt;
         }
      }
   }

   /**
    * Builds a special-type-only point (empty block column). Preserves the legacy cost branches that the
    * unified {@link BlockSpec} path cannot represent: free, {@code anywood} (sticks), and {@code item:}
    * InvItem references (banners), which may carry a meta/special the simple {@link ItemSpec} cannot.
    */
   private static PointType readSpecialPoint(String s, int colour, String specialType, String costRef, String costQty)
      throws MillLog.MillenaireException {
      if (!SpecialPointTypeList.isSpecialPointTypeKnow(specialType)) {
         throw new MillLog.MillenaireException("Special block type " + specialType + " in line " + s + " is not a recognized special type.");
      }

      PointType pt = new PointType(colour, specialType);

      if (costRef == null) {
         // 5-field special line with no explicit cost and no block to self-price.
         throw new MillLog.MillenaireException(
            "The cost of block " + specialType + " is not set and it has no blockstate to use as a generic cost."
         );
      }

      int quantity = Integer.parseInt(costQty);
      if (quantity == 0) {
         pt.setCostToFree();
      } else if (costRef.equals("anywood")) {
         pt.setCost(Items.STICK, quantity);
      } else if (costRef.startsWith("item:")) {
         String itemKey = costRef.substring("item:".length()).toLowerCase();
         if (!InvItem.INVITEMS_BY_NAME.containsKey(itemKey)) {
            throw new MillLog.MillenaireException("Unknown item: " + itemKey + " in line " + s);
         }
         pt.setCost(InvItem.INVITEMS_BY_NAME.get(itemKey), quantity);
      } else {
         Item costItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getValue(net.minecraft.resources.Identifier.parse(costRef));
         if (costItem == Items.AIR) {
            throw new MillLog.MillenaireException(
               "The cost of block " + specialType + " in line " + s + " could not be read. "
                  + costRef + " is not a recognised item or block."
            );
         }
         pt.setCost(costItem, quantity);
      }

      return pt;
   }

   /** Applies a {@link BlockSpec} cost (block-bearing lines): empty = free, present = item+quantity. */
   private void applyCost(Optional<ItemSpec> cost) {
      if (cost.isEmpty()) {
         setCostToFree();
      } else {
         ItemSpec itemSpec = cost.get();
         setCost(itemSpec.item(), itemSpec.count().value());
      }
   }

   public PointType(int colour, String name) {
      this.specialType = name;
      this.colour = colour;
      this.block = null;
      this.label = name;
      this.meta = -1;
      this.blockState = null;
   }

   /**
    * Block-bearing point built from the unified {@link BlockSpec} (block + state resolved once in
    * {@code LegacyTables}). The {@code meta} is the legacy metadata column, kept only for the exporters
    * that still read {@link #getMeta()}.
    */
   public PointType(int colour, String label, BlockSpec spec, int meta, boolean secondStep) {
      this.colour = colour;
      this.blockState = spec.state();
      this.block = spec.state().getBlock();
      this.meta = meta;
      this.secondStep = secondStep;
      this.specialType = null;
      this.label = label;
   }

   public Block getBlock() {
      return this.block;
   }

   public BlockState getBlockState() {
      return this.blockState;
   }

   public InvItem getCostInvItem() {
      if (this.costQuantity == 0) {
         return null;
      } else if (this.costItem != null) {
         return this.costItem;
      } else if (this.costBlockState != null) {
         return InvItem.createInvItem(this.costBlockState);
      } else if (this.blockState != null && this.blockState.getBlock() != Blocks.AIR) {
         return InvItem.createInvItem(this.blockState);
      } else {
         MillLog.error(this, "PointType has neither blockstate nor explicit cost item. Cannot price it.");
         return null;
      }
   }

   public int getCostQuantity() {
      return this.costQuantity;
   }

   public int getMeta() {
      return this.meta;
   }

   public String getSpecialType() {
      return this.specialType;
   }

   public boolean isSubType(String type) {
      return this.specialType == null ? false : this.specialType.startsWith(type);
   }

   public boolean isType(String type) {
      return type.equalsIgnoreCase(this.specialType);
   }

   public void setCost(BlockState blockState, int quantity) {
      this.costBlockState = blockState;
      this.costQuantity = quantity;
      this.costItem = null;
   }

   public void setCost(InvItem item, int quantity) {
      this.costBlockState = null;
      this.costQuantity = quantity;
      this.costItem = item;
   }

   public void setCost(Item item, int quantity) {
      this.costBlockState = null;
      this.costQuantity = quantity;
      this.costItem = InvItem.createInvItem(item, 0);
   }

   public void setCostToFree() {
      this.costBlockState = null;
      this.costQuantity = 0;
      this.costItem = null;
   }

   @Override
   public String toString() {
      return this.label + "/" + this.specialType + "/" + BuildingPlan.getColourString(this.colour) + "/" + this.block + "/" + this.meta;
   }
}
