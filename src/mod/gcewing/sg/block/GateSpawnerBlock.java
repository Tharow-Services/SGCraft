//------------------------------------------------------------------------------------------------
//
//   SG Craft - Gate spawner block (testing aid)
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.block;

import gcewing.sg.util.GateBuilder;
import gcewing.sg.util.GateType;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// Placing this block builds a finished vertical gate of its base block's default size in its
// place: the base goes where the block was placed, facing the same way a base placed by the
// player would, and the ring rises from it. Meant for testing, so it is only in the creative
// inventory and has no recipe. /sgspawn does the same with a choice of size.
public class GateSpawnerBlock extends Block {

    private final SGBaseBlock baseBlock;
    private final GateType gateType;

    public GateSpawnerBlock(SGBaseBlock baseBlock, GateType gateType) {
        super(Material.ROCK);
        this.baseBlock = baseBlock;
        this.gateType = gateType;
        setHardness(1.5F);
        setSoundType(SoundType.STONE);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) {
            GateBuilder.build(world, pos, placer.getHorizontalFacing(), baseBlock, baseBlock.getShape(), gateType);
        }
    }

}
