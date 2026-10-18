package gcewing.sg.features.cityshield.network;

import net.minecraft.util.math.BlockPos;

// A City Shield Generator's settings and power, as the server sends them to its screen
public class CityShieldStatus {
    public BlockPos pos;
    public boolean active;
    public int width, above, below;
    public int maxRadius;
    public double energyPerBlockPerTick;
    public double availableEnergy;
}
