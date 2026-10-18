package gcewing.sg.features.obelisk;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

// A transporter's settings, as its configurator screen shows and edits them (Asgard obelisks and ring platforms, see
// ConfigurableTransport)
public class ObeliskConfig {
    public BlockPos pos;
    // The screen's title (a lang key)
    public String titleKey = "sgcraft.gui.obelisk.label.title";
    public boolean linked;
    public String linkWorld = "";
    public BlockPos linkPos = BlockPos.ORIGIN;
    public String ownerName = "";
    public boolean allowEveryone = true;
    public List<ObeliskAccess> access = new ArrayList<>();
    // For ring platforms, their power: energy held (FE) and the most they hold, their naquadah and what it's worth, and
    // about how many transports that makes
    public boolean hasPower;
    public long energy, capacity, naquadah, naquadahEnergy, uses;
}
