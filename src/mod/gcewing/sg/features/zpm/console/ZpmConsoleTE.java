package gcewing.sg.features.zpm.console;

import static gcewing.sg.BaseUtils.min;
import static gcewing.sg.features.zpm.console.ZpmConsole.ZPM_LOADED;

import gcewing.sg.BaseTileInventory;
import gcewing.sg.SGCraft;
import gcewing.sg.features.zpm.link.ILinkableZpm;
import gcewing.sg.features.zpm.link.ZpmLink;
import gcewing.sg.features.zpm.ZPMItem;
import gcewing.sg.interfaces.ISGEnergySource;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ZpmConsoleTE extends BaseTileInventory implements ISGEnergySource, ILinkableZpm, IEnergyStorage, IInventory, ITickable {
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    // Todo: specify console buffer
    private EnergyStorage storage;
    private double maxEnergyBufferSize = Integer.MAX_VALUE;
    private double energyPerSGEnergyUnit = 80;

    private int maxInput = 0;
    private int maxOutput = 10000;

    private static final int firstZpmSlot = 0;
    public static final int numSlots = 1;

    public boolean loaded = false;
    private boolean debugOutput = false;
    private int maxExtract = 5000;
    // Reading saved data, which puts the ZPM back in its slot with its energy already in the storage
    private boolean reading = false;

    public ZpmConsoleTE() {
        this.storage = emptyStorage(0);
    }

    public ZpmConsoleTE(double zpmEnergyPerSGEnergyUnit) {
        this.energyPerSGEnergyUnit = zpmEnergyPerSGEnergyUnit;
        this.storage = emptyStorage(0);
    }

    private EnergyStorage emptyStorage(int energy) {
        return new EnergyStorage((int) maxEnergyBufferSize, maxInput, maxOutput, energy);
    }

    /* TileEntity */

    @Override
    public void readContentsFromNBT(NBTTagCompound nbttagcompound) {
        super.readContentsFromNBT(nbttagcompound);
        if (nbttagcompound.hasKey("capacity")) {
            int capacity = nbttagcompound.getInteger("capacity");
            int energy = nbttagcompound.getInteger("energy");
            this.storage = new EnergyStorage(capacity, maxInput, maxOutput, energy);
        }

        if (SGCraft.forceZPMCfgUpdate) {
            energyPerSGEnergyUnit = SGCraft.ZPMEnergyPerSGEnergyUnit;
            this.storage = emptyStorage(this.storage.getEnergyStored()); // the ZPM in it keeps its energy
        }
    }

    // The stargate it's linked to, if any (see ZpmLinks)
    private final ZpmLink zpmLink = new ZpmLink();

    @Override
    public ZpmLink getZpmLink() {
        return zpmLink;
    }

    @Override
    public boolean hasNoZpm() {
        return isEmpty();
    }

    @Override
    public void readFromNBT(final NBTTagCompound compound) {
        // On the client, what the server sends is all it holds: a slot it leaves out is empty (the block shows what's
        // here, see getActualState), not whatever was there before
        if (world != null && world.isRemote) {
            items.clear();
        }
        reading = true;
        try {
            super.readFromNBT(compound);
        } finally {
            reading = false;
        }
        this.loaded = isValidFuelItem(items.get(0));
        this.zpmLink.readFromNBT(compound);
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound compound) {
        super.writeToNBT(compound);
        this.zpmLink.writeToNBT(compound);
        return compound;
    }

    @Override
    public void invalidate() {
        super.invalidate(); // this is important for mc!
    }

    @Override
    public void writeContentsToNBT(NBTTagCompound nbttagcompound) {
        super.writeContentsToNBT(nbttagcompound);
        nbttagcompound.setInteger("capacity", this.storage.getMaxEnergyStored());
        nbttagcompound.setInteger("energy", this.storage.getEnergyStored());
    }

    @Override
    @Nonnull
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, this.getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        final NBTTagCompound result = new NBTTagCompound();
        this.writeToNBT(result);
        return result;
    }

    @Override
    public void onDataPacket(final NetworkManager net, final SPacketUpdateTileEntity packet) {
        final NBTTagCompound tag = packet.getNbtCompound();
        this.readFromNBT(tag);
    }

    /* Inventory */

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public IInventory getInventory() {
        return this;
    }

    @Override
    public boolean isEmpty() {
        for(final ItemStack item : this.items) {
            if(!item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override // This prevents the zpm from being input/extract from the console.
    public int[] getSlotsForFace(EnumFacing side) {
        return new int[0];
    }

    @Override
    public ItemStack getStackInSlot(final int index) {
        return this.items.get(0);
    }

    @Override
    public ItemStack removeStackFromSlot(final int index) {
        return decrStackSize(index, 1);
    }

    // The ZPM, with what's left of its energy (see setInventorySlotContents)
    @Override
    public ItemStack decrStackSize(final int index, final int quantity) {
        final ItemStack item = this.items.get(0);
        if (item.isEmpty() || quantity <= 0) {
            return ItemStack.EMPTY;
        }
        setInventorySlotContents(0, ItemStack.EMPTY);
        return item;
    }

    private void pingSGBaseTE() {
       if (world != null && !world.isRemote) {
           TileEntity localGateTE = GateUtil.locateLocalGate(this.world, this.pos, SGCraft.zpmSearchRange, false);

           if (localGateTE instanceof SGBaseTE) {
               SGBaseTE localGate = (SGBaseTE) localGateTE;
               localGate.validateZPM();
           }
       }
    }

    // A ZPM's energy goes into the storage as it goes into the slot, and what's left goes back onto it as it leaves,
    // however it leaves: taken, shift-clicked, swapped with a hotbar key or for the stack on the cursor, or by another
    // mod. Only one taken through decrStackSize used to get it back, so a ZPM swapped out kept the energy it went in
    // with while the console kept supplying it too: endless energy. (On a client the server's sync sets the storage.)
    @Override
    public void setInventorySlotContents(final int index, final ItemStack item) {
        final ItemStack old = this.items.get(0);
        final boolean server = world != null && !world.isRemote && !reading;
        if (server && old != item) {
            if (isValidFuelItem(old)) {
                NBTTagCompound tag = old.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    old.setTagCompound(tag);
                }
                tag.setDouble(ZPMItem.ENERGY, this.storage.getEnergyStored());
                tag.setBoolean(ZPMItem.LOADED, false);
            }
            this.storage = emptyStorage(0);
            this.loaded = false;
            if (isValidFuelItem(item)) {
                NBTTagCompound tag = item.getTagCompound();
                if (tag == null) {
                    tag = new NBTTagCompound();
                    item.setTagCompound(tag);
                }
                if (!tag.hasKey(ZPMItem.ENERGY, 99)) {
                    tag.setDouble(ZPMItem.ENERGY, Integer.MAX_VALUE);
                }
                tag.setBoolean(ZPMItem.LOADED, true);
                this.storage = emptyStorage((int) Math.max(0, Math.min(Integer.MAX_VALUE, tag.getDouble(ZPMItem.ENERGY))));
                this.loaded = true;
            }
        }
        this.items.set(0, item);

        if (world != null) {
            // Only while it's still a console: this also runs as it's broken (see ZpmConsole.breakBlock)
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof ZpmConsole && state.getValue(ZPM_LOADED) != isValidFuelItem(item)) {
                world.setBlockState(pos, state.withProperty(ZPM_LOADED, isValidFuelItem(item)), 3);
            }
        }
        if (server) {
            markChanged(); // its screen shows the new ZPM's energy
        } else {
            markDirty();
        }
        if (server && item.isEmpty() && !old.isEmpty()) {
            pingSGBaseTE();
        }
    }

    public static boolean isValidFuelItem(ItemStack stack) {
        return stack != null && stack.getItem() == SGCraft.zpm && stack.getCount() > 0;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUsableByPlayer(final EntityPlayer player) {
        return player.getDistanceSq((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D) <= 64.0D;
    }


    @Override
    public boolean isItemValidForSlot(final int index, final ItemStack item) {
        return item.getItem() instanceof ZPMItem;
    }

    @Override
    public int getField(final int id) {
        return 0;
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        this.items.clear();
    }

    @Override
    public String getName() {
        return "container.zpm_console";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentString("ZPM Console");
    }

    public static ZpmConsoleTE at(IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof ZpmConsoleTE ? (ZpmConsoleTE) te : null;
    }


    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY || super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            // Hand out the console itself, not its storage, so external cables (EnderIO conduits etc.) go through the
            // extraction cap, taint check and receive block below.
            return this.storage != null ? (T) this : null;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void update() {
        if(!this.world.isRemote) {
            for (EnumFacing side : EnumFacing.values()) {
                if (side == EnumFacing.UP)
                    continue;
                TileEntity tile = this.world.getTileEntity(this.pos.offset(side));
                // Note: make sure the neighbor isn't also a ZpmConsoleTE otherwise a loopback is created.  This only applies to forge based power with this update method.
                if (tile != null && (!(tile instanceof ZpmConsoleTE))) {
                    if (tile.hasCapability(CapabilityEnergy.ENERGY, side.getOpposite())) {
                        IEnergyStorage target = tile.getCapability(CapabilityEnergy.ENERGY, side.getOpposite());
                        // Offer it what it will take, asking first: cables and conduits that pass power on report no
                        // store of their own (0 of 0), so going by spare room would never feed them (issue #107). Only a
                        // draw that moves something is made, as a real draw also syncs the console and checks for taint.
                        if (target != null && target.canReceive()) {
                            int accepted = target.receiveEnergy(this.extractEnergy(50000, true), true);
                            if (accepted > 0) {
                                target.receiveEnergy(this.extractEnergy(accepted, false), false);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        if (oldState.getBlock() != newState.getBlock()) { // Prevents the TE from nullifying itself when we force change the state to change it models.  Vanilla mechanics invalidate the TE.
            return true;
        } else {
            return false;
        }
    }

    // **********************************************************
    // IEnergyStorage interfaces
    // **********************************************************
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0; // A ZPM is a source only; never accept power from cables.
    }

    @Override
    public int extractEnergy(int extract, boolean simulate) {
        if (extract <= 0) {
            return 0; // a negative draw would add energy
        }
        if (extract > this.maxExtract) {
            extract = this.maxExtract;
        }
        int result = storage.extractEnergy(extract, simulate);

        // Cables simulate constantly; only a real draw should set off a tainted ZPM or dirty the TE.
        if (!simulate) {
            if (isTainted(this.getStackInSlot(0))) {
                world.newExplosion(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), (float)250, true, true);
            }

            markChanged();
        }

        return result;
    }

    @Override
    public int getEnergyStored() {
        return storage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return storage.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return storage.canExtract();
    }

    @Override
    public boolean canReceive() {
        return false;
    }

    @Override
    public double availableEnergy() {
        double available = this.storage.getEnergyStored() / energyPerSGEnergyUnit;
       // System.out.println("ZPM Console at: " + this.pos + " / " + this.world + " Power debug: " + this.storage.getEnergyStored() + " / " + energyPerSGEnergyUnit + " == " + (int)available);
        return available;
    }

    // **********************************************************
    // SGEnergyStorage interfaces
    // **********************************************************

    public double totalAvailableEnergy() {
        return storage.getEnergyStored();
    }

    public double drawEnergyDouble(double request) {
        double available = this.storage.getEnergyStored() / energyPerSGEnergyUnit;
        double supply = Math.max(0, min(request, available));
        // Straight from the storage: its extractEnergy is capped per call (maxOutput) for cables, and a gate was credited
        // all it asked for while the storage gave only that much
        int drawn = (int) Math.min(this.storage.getEnergyStored(), Math.ceil(supply * energyPerSGEnergyUnit));
        this.storage = emptyStorage(this.storage.getEnergyStored() - drawn);
        markChanged();

        if (isTainted(this.getStackInSlot(0))) {
            world.newExplosion(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), (float)250, true, true);
        }

        if(debugOutput)
            System.out.printf("SGCraft: ZPM Console: Supplying %s SGU of %s requested\n", supply, request);
        return supply;
    }

    private boolean isTainted(ItemStack item) {
        boolean hasTaint = false;
        NBTTagList nbttaglist = item.getEnchantmentTagList();
        for (int j = 0; j < nbttaglist.tagCount(); ++j) {
            NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(j);
            int k = nbttagcompound.getShort("id");
            int l = nbttagcompound.getShort("lvl");
            Enchantment enchantment = Enchantment.getEnchantmentByID(k);
            if (k == 51) {
                hasTaint = true;
            }
        }
        return hasTaint;
    }
}
