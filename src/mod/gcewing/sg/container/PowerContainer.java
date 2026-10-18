//------------------------------------------------------------------------------------------------
//
//   SG Craft - Power unit gui container
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.container;

import gcewing.sg.BaseContainer;
import gcewing.sg.client.gui.PowerScreen;
import gcewing.sg.tileentity.PowerTE;
import net.minecraft.entity.player.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public class PowerContainer extends BaseContainer {

    public PowerTE te;
    
    public static PowerContainer create(EntityPlayer player, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof PowerTE)
            return new PowerContainer(player, (PowerTE)te);
        else
            return null;
    }
    
    public PowerContainer(EntityPlayer player, PowerTE te) {
        // Todo: this is insanity....
        super(148, 64);
        this.te = te;
    }

    @Override
    protected TileEntity tileEntity() {
        return te;
    }

}
