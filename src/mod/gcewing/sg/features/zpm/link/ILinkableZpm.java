package gcewing.sg.features.zpm.link;

import gcewing.sg.interfaces.ISGEnergySource;

// A ZPM device (console, and with IC2, interface cart and hub) that can be linked to one stargate, which then draws on
// it wherever it stands: sneak and right-click the device, then the gate (see ZpmLinks). Devices are tile entities.
//
// Its methods must not share a name with Minecraft's own (isEmpty, markDirty, ...): those are renamed in the built mod,
// and a device would then not implement them here (AbstractMethodError in game, though fine in development).
public interface ILinkableZpm extends ISGEnergySource {

    ZpmLink getZpmLink();

    // Whether it holds no ZPM, or only empty ones
    boolean hasNoZpm();
}
