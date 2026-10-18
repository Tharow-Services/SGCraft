package gcewing.sg.features.naquadahgenerator;

import gcewing.sg.BaseTileEntity;
import gcewing.sg.SGCraft;
import gcewing.sg.features.naquadahgenerator.client.gui.NaquadahGeneratorScreen;
import gcewing.sg.interfaces.IGatePowerSource;
import gcewing.sg.interfaces.LoopingSoundSource;
import net.malisis.core.client.gui.MalisisGui;
import net.malisis.core.inventory.IInventoryProvider.IDirectInventoryProvider;
import net.malisis.core.inventory.MalisisInventory;
import net.malisis.core.inventory.MalisisInventoryContainer;
import net.malisis.core.inventory.MalisisSlot;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

// A Naquadah generator's power. Switched on, it burns its fuel (naquadah, or naquadah alloy ingots) to keep its store
// of energy full, as fast as its mark allows; gates within reach draw on that store (it's an IGatePowerSource), and it
// pushes what it makes as Forge Energy into machines beside it, which can also draw on it. Switched off, it gives nothing.
public class NaquadahGeneratorTE extends BaseTileEntity implements ITickable, IGatePowerSource, IDirectInventoryProvider,
        LoopingSoundSource {

    public static final int numFuelSlots = 4;

    // Switched on (its arms out), the energy it holds ready, and what's left of the fuel item it's burning
    public boolean switchedOn;
    public double energy;
    public double fuelLeft;
    // Running: switched on with energy or fuel to give (its lights and hum); worked out on the server, sent to clients
    public boolean running;

    private final MalisisInventory inventory = new MalisisInventory(this, FuelSlot::new, numFuelSlots);
    private final IEnergyStorage forgeEnergy = new ForgeEnergy();
    private final IItemHandler fuelHandler = new FuelHandler();
    private int saveTimer = 0;

    {
        inventory.setName("sgcraft.gui.naquadahGenerator.label.fuel");
    }

    public NaquadahGenerators.Mark mark() {
        Block block = world != null ? getBlockType() : null;
        return block instanceof NaquadahGeneratorBlock ? ((NaquadahGeneratorBlock) block).mark : NaquadahGenerators.Mark.I;
    }

    // ---- Running ----

    @Override
    public void update() {
        if (world.isRemote) {
            if (running) {
                startHum();
            }
            return;
        }
        double before = energy;
        if (switchedOn) {
            generate();
            pushForgeEnergy();
        }
        boolean nowRunning = switchedOn && (energy > 0 || fuelLeft > 0 || hasFuel());
        if (nowRunning != running) {
            running = nowRunning;
            markChanged(); // clients start or stop its lights and hum
        } else if (energy != before && ++saveTimer >= 20) {
            saveTimer = 0;
            markDirty();
        }
    }

    // Burns fuel, a piece at a time, to fill its store at its mark's rate
    private void generate() {
        NaquadahGenerators.Mark mark = mark();
        double need = Math.min(mark.energyPerTick, mark.capacity - energy);
        if (need <= 0) {
            return; // full: it burns nothing while nothing draws on it
        }
        while (fuelLeft < need && burnFuel(mark)) {
            // took another piece
        }
        double made = Math.min(need, fuelLeft);
        energy += made;
        fuelLeft -= made;
    }

    private boolean burnFuel(NaquadahGenerators.Mark mark) {
        for (int i = 0; i < numFuelSlots; i++) {
            MalisisSlot slot = inventory.getSlot(i);
            ItemStack stack = slot.getItemStack();
            double worth = NaquadahGenerators.fuelEnergy(stack);
            if (worth > 0) {
                ItemStack rest = stack.copy();
                rest.shrink(1);
                slot.setItemStack(rest.isEmpty() ? ItemStack.EMPTY : rest);
                fuelLeft += worth * mark.efficiency;
                markDirty();
                return true;
            }
        }
        return false;
    }

    public boolean hasFuel() {
        return fuelInSlots() > 0;
    }

    // What the fuel waiting in its slots is worth to it
    public double fuelInSlots() {
        double total = 0;
        for (int i = 0; i < numFuelSlots; i++) {
            ItemStack stack = inventory.getItemStack(i);
            total += NaquadahGenerators.fuelEnergy(stack) * stack.getCount();
        }
        return total * mark().efficiency;
    }

    public void setSwitchedOn(boolean on) {
        if (switchedOn == on) {
            return;
        }
        switchedOn = on;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof NaquadahGeneratorBlock) {
            world.setBlockState(pos, state.withProperty(NaquadahGeneratorBlock.RUNNING, on), 3); // its arms go out or in
        }
        markChanged();
    }

    // Its arms going out and in changes its block, not it
    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    // ---- Gates ----

    @Override
    public double availableEnergy() {
        return switchedOn ? energy : 0;
    }

    @Override
    public double totalAvailableEnergy() {
        return mark().capacity;
    }

    @Override
    public double drawEnergyDouble(double amount) {
        if (!switchedOn) {
            return 0;
        }
        double supply = Math.min(amount, energy);
        energy -= supply;
        markDirty();
        return supply;
    }

    // ---- Forge Energy ----

    // Most it gives a tick, in Forge Energy: what it makes
    public int forgeEnergyPerTick() {
        return (int) (mark().energyPerTick * SGCraft.FPPerSGEnergyUnit);
    }

    private void pushForgeEnergy() {
        int budget = forgeEnergyPerTick();
        for (EnumFacing side : EnumFacing.values()) {
            if (budget <= 0 || energy <= 0) {
                return;
            }
            BlockPos at = pos.offset(side);
            if (!world.isBlockLoaded(at)) {
                continue;
            }
            TileEntity te = world.getTileEntity(at);
            if (te == null || te instanceof NaquadahGeneratorTE || !te.hasCapability(CapabilityEnergy.ENERGY, side.getOpposite())) {
                continue;
            }
            IEnergyStorage target = te.getCapability(CapabilityEnergy.ENERGY, side.getOpposite());
            if (target != null && target.canReceive()) {
                int offer = (int) Math.min(budget, energy * SGCraft.FPPerSGEnergyUnit);
                int taken = target.receiveEnergy(offer, false);
                energy -= taken / SGCraft.FPPerSGEnergyUnit;
                budget -= taken;
            }
        }
    }

    private class ForgeEnergy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!switchedOn) {
                return 0;
            }
            int given = (int) Math.min(Math.min(maxExtract, forgeEnergyPerTick()), energy * SGCraft.FPPerSGEnergyUnit);
            if (!simulate && given > 0) {
                energy -= given / SGCraft.FPPerSGEnergyUnit;
                markDirty();
            }
            return Math.max(0, given);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energy * SGCraft.FPPerSGEnergyUnit);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, mark().capacity * SGCraft.FPPerSGEnergyUnit);
        }

        @Override
        public boolean canExtract() {
            return switchedOn;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    // ---- Fuel: its slots, filled from its screen or by hoppers and pipes ----

    @Override
    public MalisisInventory getInventory() {
        return inventory;
    }

    // A fuel slot takes only fuel
    public static class FuelSlot extends MalisisSlot {
        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return NaquadahGenerators.fuelEnergy(itemStack) > 0;
        }
    }

    // Hoppers and pipes may put fuel in, but not take it out
    private class FuelHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return numFuelSlots;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getItemStack(slot);
        }

        @Override
        public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || NaquadahGenerators.fuelEnergy(stack) <= 0) {
                return stack;
            }
            MalisisSlot slot = inventory.getSlot(index);
            ItemStack held = slot.getItemStack();
            if (!held.isEmpty() && !ItemHandlerHelper.canItemStacksStack(held, stack)) {
                return stack;
            }
            int room = Math.min(getSlotLimit(index), stack.getMaxStackSize()) - held.getCount();
            int moved = Math.min(room, stack.getCount());
            if (moved <= 0) {
                return stack;
            }
            if (!simulate) {
                slot.setItemStack(ItemHandlerHelper.copyStackWithSize(stack, held.getCount() + moved));
                markDirty();
            }
            return ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY || capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
            || super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(forgeEnergy);
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(fuelHandler);
        }
        return super.getCapability(capability, facing);
    }

    // Its screen, with its fuel slots (see NaquadahGeneratorNetworkHandler.openScreen)
    @SideOnly(Side.CLIENT)
    @Override
    public MalisisGui getGui(MalisisInventoryContainer container) {
        return new NaquadahGeneratorScreen(Minecraft.getMinecraft().player, this, container, inventory);
    }

    // ---- Its hum, while it runs ----

    private boolean humming = false;

    @SideOnly(Side.CLIENT)
    private void startHum() {
        if (!humming && NaquadahGenerators.humSound != null) {
            humming = true;
            SGCraft.playSound(this, NaquadahGenerators.humSound);
        }
    }

    @Override
    public World getSoundWorld() {
        return world;
    }

    @Override
    public BlockPos getSoundPos() {
        return pos;
    }

    @Override
    public Vec3d getSoundOrigin() {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5);
    }

    @Override
    public boolean isSoundActive(SoundEvent sound) {
        boolean active = running && !isInvalid() && world.isBlockLoaded(pos) && world.getTileEntity(pos) == this;
        if (!active) {
            humming = false; // so it starts again when it next runs
        }
        return active;
    }

    @Override
    public float getSoundVolume(SoundEvent sound) {
        return 0.5F;
    }

    // ---- Saving and syncing ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        switchedOn = nbt.getBoolean("switchedOn");
        running = nbt.getBoolean("running");
        energy = nbt.getDouble("energy");
        fuelLeft = nbt.getDouble("fuelLeft");
        inventory.readFromNBT(nbt);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("switchedOn", switchedOn);
        nbt.setBoolean("running", running);
        nbt.setDouble("energy", energy);
        nbt.setDouble("fuelLeft", fuelLeft);
        inventory.writeToNBT(nbt);
        return nbt;
    }
}
