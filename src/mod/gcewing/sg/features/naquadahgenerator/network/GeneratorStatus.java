package gcewing.sg.features.naquadahgenerator.network;

import net.minecraft.util.math.BlockPos;

// A Naquadah generator's state and power, as the server sends them to its screen (energy in gate energy units)
public class GeneratorStatus {
    public BlockPos pos;
    public boolean switchedOn, running;
    public double energy, capacity, energyPerTick;
    // What its fuel (what's left of the piece burning, and what's in its slots) is worth
    public double fuelEnergy;
}
