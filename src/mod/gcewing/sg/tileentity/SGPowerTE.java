//------------------------------------------------------------------------------------------------
//
//   SG Craft - RF Stargate Power Unit Tile Entity
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.tileentity;

import gcewing.sg.SGCraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

public class SGPowerTE extends PowerTE implements IEnergyStorage {

    public SGPowerTE() {
        super(SGCraft.FPMaxEnergyBuffer, SGCraft.FPPerSGEnergyUnit);
    }

    @Override
    public String getScreenTitle() {
        return "SGPU";
    }

    @Override
    public String getUnitName() {
        return "FE"; // what it holds: the Forge Energy fed to it, not the gate's energy units
    }

    @Override
    public void readContentsFromNBT(NBTTagCompound nbttagcompound) {
        super.readContentsFromNBT(nbttagcompound);
        if (SGCraft.forceFPCfgUpdate) {
            energyMax = SGCraft.FPMaxEnergyBuffer;
            energyPerSGEnergyUnit = SGCraft.FPPerSGEnergyUnit;
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(this);
        }
        return null;
    }

    //------------------------ IEnergyStorage ---------------------------

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int energyReceived = Math.max(0, Math.min((int)Math.floor(energyMax - energyBuffer),maxReceive));
        // Cables ask (simulate) all the time: only energy actually taken in changes it, and needs saving and sending
        if (!simulate && energyReceived > 0) {
            energyBuffer += energyReceived;
            markChanged();
        }
        return energyReceived;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.floor(energyBuffer);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.floor(energyMax);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    public double totalAvailableEnergy() {
        return energyBuffer;
    }
}
