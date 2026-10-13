//------------------------------------------------------------------------------------------------
//
//   SG Craft - Supergate ring block
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.block;

import gcewing.sg.BaseMod.ModelSpec;
import gcewing.sg.util.GateShape;
import net.minecraft.block.state.IBlockState;

// Ring and chevron blocks of a supergate (variants 0 and 1, as for a stargate ring block)
public class SupergateRingBlock extends SGRingBlock {

    static String[] supergateTextures = {"supergateblock", "supergatering", "supergatechevron"};
    static ModelSpec supergateModels[] = {
        new ModelSpec("block/sg_ring_block.smeg", "supergateblock", "supergatering"),
        new ModelSpec("block/sg_ring_block.smeg", "supergateblock", "supergatechevron")
    };

    @Override
    public String[] getTextureNames() {
        return supergateTextures;
    }

    @Override
    public ModelSpec getModelSpec(IBlockState state) {
        return supergateModels[state.getValue(VARIANT)];
    }

    // The largest supergate, so that a base is found from any ring block of any size of gate
    @Override
    public GateShape getShape() {
        return GateShape.supergate(GateShape.MAX_SUPERGATE_RADIUS);
    }

}
