package org.millenaire.common.block;

public class BlockOrientedSlabDoubleDecorated extends BlockOrientedSlab {
   public BlockOrientedSlabDoubleDecorated(String slabName) {
      super(slabName);
   }

   // 1.12 getRenderLayer()==CUTOUT now registered via BlockRenderLayerMap in client init.

   public boolean isDouble() {
      return true;
   }
}

