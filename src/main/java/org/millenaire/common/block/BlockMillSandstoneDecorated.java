package org.millenaire.common.block;

public class BlockMillSandstoneDecorated extends BlockMillSandstone {
   public BlockMillSandstoneDecorated(String blockName) {
      super(blockName);
   }

   // 1.12 getRenderLayer() == CUTOUT is now registered client-side via
   // BlockRenderLayerMap in the client initializer (see client render setup).
}
