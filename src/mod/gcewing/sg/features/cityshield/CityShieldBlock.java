package gcewing.sg.features.cityshield;

import gcewing.sg.features.cityshield.network.CityShieldNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// The City Shield Generator block; the shield itself is its tile entity's (CityShieldTE)
public class CityShieldBlock extends Block {

    public CityShieldBlock() {
        super(Material.IRON, MapColor.GRAY);
        setHardness(5F);
        setResistance(2000F); // the shield's heart shouldn't be easy to blow up
        setSoundType(SoundType.METAL);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new CityShieldTE();
    }

    // Its ZPMs drop when it's broken, keeping what energy they have left
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IInventory) {
            InventoryHelper.dropInventoryItems(world, pos, (IInventory) te);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            CityShieldNetworkHandler.openScreen(player, pos);
        }
        return true;
    }
}
