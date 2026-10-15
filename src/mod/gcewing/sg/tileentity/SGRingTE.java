//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate ring tile entity
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.tileentity;

import gcewing.sg.BaseTileEntity;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.math.*;

public class SGRingTE extends BaseTileEntity {

    public boolean isMerged;
    public BlockPos basePos = new BlockPos(0, 0, 0);

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        //System.out.printf("SGRingTE.readFromNBT\n");
        super.readFromNBT(nbt);
        boolean wasMerged = isMerged;
        isMerged = nbt.getBoolean("isMerged");
        if (wasMerged && !isMerged && world != null && world.isRemote) {
            world.checkLight(pos); // no longer part of a gate, so the client stops lighting it by the event horizon
        }
        int baseX = nbt.getInteger("baseX");
        int baseY = nbt.getInteger("baseY");
        int baseZ = nbt.getInteger("baseZ");
        basePos = new BlockPos(baseX, baseY, baseZ);
    }
    
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        //System.out.printf("SGRingTE.writeToNBT\n");
        super.writeToNBT(nbt);
        nbt.setBoolean("isMerged", isMerged);
        nbt.setInteger("baseX", basePos.getX());
        nbt.setInteger("baseY", basePos.getY());
        nbt.setInteger("baseZ", basePos.getZ());
        return nbt;
    }
    
    public SGBaseTE getBaseTE() {
        if (isMerged) {
            TileEntity bte = world.getTileEntity(basePos);
            if (bte instanceof SGBaseTE)
                return (SGBaseTE)bte;
        }
        return null;
    }
}
