package gcewing.sg.event;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.common.MinecraftForge;

public class SGMergeEvent extends Event {

    /*
     Private because the event should be read-only - including both the gatePosition and address
     in case changes are made to gate address generation
     */
    private final String worldName;
    private final BlockPos gatePosition;
    private final String address;
    private final boolean merged;
    private final World world;

    /**
     * Fired when a Stargate is merged or unmerged (the multiblock structure is completed or broken)
     */
    public SGMergeEvent(String worldName, String address, boolean merged, BlockPos gatePosition, World world) {
        this.worldName = worldName;
        this.gatePosition = gatePosition;
        this.address = address;
        this.merged = merged;
        this.world = world;
    }

    public static SGMergeEvent fireEvent(String worldName, String address, boolean merged, BlockPos gatePosition, World world) {
        SGMergeEvent event = new SGMergeEvent(worldName, address, merged, gatePosition, world);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    public String getWorldName() {
        return worldName;
    }

    public BlockPos getGatePosition() {
        return gatePosition;
    }

    public String getAddress() {
        return address;
    }

    public boolean isMerged() {
        return this.merged;
    }

    public World getWorld() {
        return world;
    }
}
