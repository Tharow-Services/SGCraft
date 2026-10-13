//------------------------------------------------------------------------------------------------
//
//   SG Craft - Builds a finished gate in the world (testing aid)
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.util;

import static gcewing.sg.BaseOrientation.Orient4WaysByState.FACING;

import gcewing.sg.Trans3;
import gcewing.sg.block.SGBaseBlock;
import gcewing.sg.block.SGRingBlock;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class GateBuilder {

    // Builds a vertical gate of the given shape and type with its base at pos, facing the same
    // way as a base placed by a player looking towards `facing`, so its front faces back towards
    // them. Blocks in the opening are cleared so it can be walked through. Returns the base's
    // tile entity, or null if the gate did not merge.
    public static SGBaseTE build(World world, BlockPos pos, EnumFacing facing, SGBaseBlock baseBlock,
        GateShape shape, GateType type)
    {
        SGRingBlock ringBlock = baseBlock.getRingBlock();
        world.setBlockState(pos, baseBlock.getDefaultState().withProperty(FACING, facing));
        Trans3 t = baseBlock.localToGlobalTransformation(world, pos);

        // Place the ring without each block searching for a base, then merge it once
        SGRingBlock.suppressBaseSearch = true;
        try {
            for (int y = 0; y < shape.height; y++) {
                int[] span = ringSpan(shape, y);
                for (int x = -shape.halfWidth; x <= shape.halfWidth; x++) {
                    if (x == 0 && y == 0) {
                        continue;
                    }
                    BlockPos p = t.p(x, y, 0).blockPos();
                    int cell = shape.cellAt(x, y);
                    if (cell == GateShape.RING || cell == GateShape.CHEVRON) {
                        int variant = cell == GateShape.CHEVRON ? 1 : 0;
                        world.setBlockState(p, ringBlock.getDefaultState().withProperty(SGRingBlock.VARIANT, variant));
                    } else if (span != null && x > span[0] && x < span[1]) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState());
                    }
                }
            }
        } finally {
            SGRingBlock.suppressBaseSearch = false;
        }
        baseBlock.checkForVerticalMerge(world, pos);

        SGBaseTE te = SGBaseTE.at(world, pos);
        if (te == null || !te.isMerged) {
            return null;
        }
        te.gateType = type.id;
        if (type == GateType.PEGASUS) {
            te.ringRotationSpeed = 6.0D; // As the Pegasus upgrade sets it
        }
        te.markChanged();
        return te;
    }

    // The leftmost and rightmost cells of the ring in row y, or null if the row has none
    private static int[] ringSpan(GateShape shape, int y) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = -shape.halfWidth; x <= shape.halfWidth; x++) {
            if (shape.cellAt(x, y) != GateShape.EMPTY || (x == 0 && y == 0)) {
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
        }
        return lo <= hi ? new int[] {lo, hi} : null;
    }

}
