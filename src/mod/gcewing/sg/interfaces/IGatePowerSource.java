package gcewing.sg.interfaces;

// An energy source a stargate may draw on when it's within reach of the gate's base (see SGBaseTE.findEnergySources).
// The gate checks for this, rather than the source's class, so it needn't load classes that need other mods.
public interface IGatePowerSource extends ISGEnergySource {
}
