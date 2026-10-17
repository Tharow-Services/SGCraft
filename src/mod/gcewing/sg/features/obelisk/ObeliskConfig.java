package gcewing.sg.features.obelisk;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

// An Asgard obelisk's settings, as its configurator screen shows and edits them
public class ObeliskConfig {
    public BlockPos pos;
    public boolean linked;
    public String linkWorld = "";
    public BlockPos linkPos = BlockPos.ORIGIN;
    public String ownerName = "";
    public boolean allowEveryone = true;
    public List<ObeliskAccess> access = new ArrayList<>();
}
