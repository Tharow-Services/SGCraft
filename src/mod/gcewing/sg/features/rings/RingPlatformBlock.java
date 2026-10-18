package gcewing.sg.features.rings;

import gcewing.sg.SGCraft;
import gcewing.sg.client.gui.SGGui;
import gcewing.sg.features.obelisk.ObeliskNetworkHandler;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// A ring platform's heart: set in the floor (its rings rise round those standing on it) or in a ceiling (they drop to the
// floor below). Its five rings, 5 blocks across round the 3x3 middle, are drawn by RingPlatformRenderer. Sneak and
// right-click two platforms to link them, as Asgard obelisks are linked.
public class RingPlatformBlock extends Block {

    public final boolean ceiling;

    public RingPlatformBlock(boolean ceiling) {
        super(Material.IRON, MapColor.GRAY);
        this.ceiling = ceiling;
        setHardness(5F);
        setResistance(20F);
        setSoundType(SoundType.METAL);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    // With MalisisCore, its fuel is a MalisisCore inventory for its MalisisCore screen
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return SGCraft.isModLoaded("malisiscore") ? RingPlatformMalisis.create() : new RingPlatformTE();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        RingPlatformTE platform = RingTransports.platformAt(world, pos);
        // With the Configurator: its settings, the obelisks' screen
        if (SGCraft.configurator != null && player.getHeldItemMainhand().getItem() == SGCraft.configurator) {
            if (!world.isRemote && platform != null) {
                ObeliskNetworkHandler.openConfigurator(player, platform);
            }
            return true;
        }
        if (!world.isRemote && platform != null && !platform.mayUse(player)) {
            SGBaseTE.sendErrorMsg(player, "ringNoAccess");
            return true;
        }
        // Its fuel screen: MalisisCore's if it's installed, the vanilla one if not
        if (!player.isSneaking()) {
            if (!world.isRemote) {
                RingPlatformTE te = RingTransports.platformAt(world, pos);
                boolean malisis = te != null && SGCraft.isModLoaded("malisiscore") && player instanceof EntityPlayerMP
                    && RingPlatformMalisis.openScreen((EntityPlayerMP) player, te);
                if (te != null && !malisis) {
                    SGCraft.mod.openGui(player, SGGui.RingFuel, world, pos);
                }
            }
            return true;
        }
        if (!world.isRemote) {
            RingPlatformTE te = RingTransports.platformAt(world, pos);
            if (te != null) {
                RingTransports.link(player, te);
            }
        }
        return true;
    }

    // Whoever placed it owns it: they may always use and configure it
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        RingPlatformTE te = RingTransports.platformAt(world, pos);
        if (te != null && placer instanceof EntityPlayer) {
            te.owner = placer.getUniqueID();
            te.ownerName = placer.getName();
            te.markDirty();
        }
    }

    // Its partner is let go when it's broken
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            RingPlatformTE te = RingTransports.platformAt(world, pos);
            if (te != null) {
                RingTransports.unlink(te);
                InventoryHelper.dropInventoryItems(world, pos, te); // its naquadah
            }
        }
        super.breakBlock(world, pos, state);
    }
}
