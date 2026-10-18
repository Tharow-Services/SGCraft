package gcewing.sg.features.ic2.zpm.modulehub;

import ic2.api.energy.prefab.BasicSource;
import ic2.api.energy.tile.IEnergyAcceptor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

public final class ZpmHubBasicSource extends BasicSource {

    private final TileEntity parent;

    public ZpmHubBasicSource(final TileEntity parent, final double capacity, final int tier) {
        super(parent, capacity, tier);
        this.parent = parent;
    }

    // ZPMs in a hub together go further: with n of them, each unit drawn costs them 1/n (the "optimization factor" its
    // screen shows), shared among them. Their own energies, which they take back as they leave, used to be lowered by
    // whole units only, so small draws (from a slow cable) took nothing from them at all.
    @Override
    public void drawEnergy(double amount) {
        ZpmHubTE te = ((ZpmHubTE) this.parent);
        if (te != null && amount > 0) {
            int zpmCount = te.getZpmSlotsloaded();
            te.drawFromZpms(zpmCount > 0 ? amount / zpmCount : amount); // sets this source's energy from theirs

            te.markChanged();
            // Todo: fix this.
            /*
            if (((ZpmHubTE)this.parent).isTainted(((ZpmHubTE)this.parent).getStackInSlot(0))) {
                world.newExplosion(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(), (float)250, true, true);
            } */
        }
    }

    @Override
    public boolean emitsEnergyTo(IEnergyAcceptor iEnergyAcceptor, EnumFacing direction) {
        // Todo: math needed to only output energy on one side of the zpm interface cart
        return true;
    }
}
