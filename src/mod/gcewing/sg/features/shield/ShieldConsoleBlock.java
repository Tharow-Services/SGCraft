package gcewing.sg.features.shield;

import gcewing.sg.features.shield.network.ShieldConsoleNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// A console that raises or lowers the shield of the nearest Stargate with one, within shieldConsoleRange blocks
public class ShieldConsoleBlock extends Block {

    public ShieldConsoleBlock() {
        super(Material.IRON, MapColor.GRAY);
        setHardness(1.5F);
        setResistance(10F);
        setSoundType(SoundType.METAL);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            ShieldConsoleNetworkHandler.openConsole(player, pos);
        }
        return true;
    }
}
