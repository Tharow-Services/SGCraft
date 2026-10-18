package gcewing.sg.features.zpm.link;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

// The stargate a ZPM device is linked to: its base, in the device's own world
public class ZpmLink {

    public boolean linked;
    public BlockPos gatePos = BlockPos.ORIGIN;

    public void linkTo(BlockPos gatePos) {
        this.linked = true;
        this.gatePos = gatePos.toImmutable();
    }

    public void unlink() {
        this.linked = false;
    }

    public boolean isLinkedTo(BlockPos pos) {
        return linked && gatePos.equals(pos);
    }

    public void readFromNBT(NBTTagCompound nbt) {
        linked = nbt.getBoolean("zpmLinked");
        gatePos = linked ? BlockPos.fromLong(nbt.getLong("zpmLinkGate")) : BlockPos.ORIGIN;
    }

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("zpmLinked", linked);
        if (linked) {
            nbt.setLong("zpmLinkGate", gatePos.toLong());
        }
    }
}
