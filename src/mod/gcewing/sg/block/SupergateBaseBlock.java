//------------------------------------------------------------------------------------------------
//
//   SG Craft - Supergate base block
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.block;

import gcewing.sg.BaseMod.ModelSpec;
import gcewing.sg.SGCraft;
import gcewing.sg.util.GateShape;
import net.minecraft.block.state.IBlockState;

import java.util.ArrayList;
import java.util.List;

// The base of a supergate: a larger ring built from supergate ring blocks. It otherwise
// behaves like a stargate base and uses the same tile entity.
public class SupergateBaseBlock extends SGBaseBlock {

    protected static String[] supergateTextures = {"supergateblock", "supergatering", "supergatebase_front"};
    protected static ModelSpec supergateModel = new ModelSpec("block/sg_base_block.smeg", supergateTextures);

    @Override
    public String[] getTextureNames() {
        return supergateTextures;
    }

    @Override
    public ModelSpec getModelSpec(IBlockState state) {
        return supergateModel;
    }

    @Override
    public GateShape getShape() {
        return GateShape.supergate(GateShape.DEFAULT_SUPERGATE_RADIUS);
    }

    // A supergate can be any size in range; the smallest complete ring around the base wins
    @Override
    public List<GateShape> getPossibleShapes() {
        List<GateShape> shapes = new ArrayList<>();
        for (int r = GateShape.MIN_SUPERGATE_RADIUS; r <= GateShape.MAX_SUPERGATE_RADIUS; r++) {
            shapes.add(GateShape.supergate(r));
        }
        return shapes;
    }

    @Override
    public SGRingBlock getRingBlock() {
        return SGCraft.supergateRingBlock;
    }

}
