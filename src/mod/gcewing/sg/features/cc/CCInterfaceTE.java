//------------------------------------------------------------------------------------------------
//
//   SG Craft - Computercraft Interface Tile Entity
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.features.cc;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import gcewing.sg.interfaces.IComputerInterface;
import gcewing.sg.tileentity.SGInterfaceTE;
import net.minecraft.tileentity.TileEntity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class CCInterfaceTE extends SGInterfaceTE implements IComputerInterface {

    private IPeripheral peripheral;

    Set<IComputerAccess> attachedComputers = new HashSet<IComputerAccess>();

    // A computer that has just detached (turned off, rebooted or broken) is only dropped from the set on the next tick,
    // and until then ComputerCraft throws when it's sent anything: from the gate's tick, that crashed the server
    public void postEvent(TileEntity source, String name, Object... args) {
        //System.out.printf("CCInterfaceTE.postEvent: %s\n", name);
        for (Iterator<IComputerAccess> it = attachedComputers.iterator(); it.hasNext(); ) {
            IComputerAccess cpu = it.next();
            try {
                cpu.queueEvent(name, prependArgs(cpu.getAttachmentName(), args));
            } catch (RuntimeException e) {
                it.remove(); // not attached any more
            }
        }
    }

    public IPeripheral getPeripheral() {
        return this.peripheral;
    }

    public void setPeripheral(IPeripheral peripheral) {
        this.peripheral = peripheral;
    }
}
