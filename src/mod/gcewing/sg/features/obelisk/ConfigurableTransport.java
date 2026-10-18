package gcewing.sg.features.obelisk;

import net.minecraft.entity.player.EntityPlayer;

import java.util.List;

// A transporter with an owner, a player access list and a link, set up on the obelisk configurator screen (opened by
// clicking it with the Configurator): Asgard obelisks and ring platforms. Each is a tile entity.
//
// (Its methods' names are its own, not Minecraft's: getPos and the like are renamed in the built mod, and a tile entity
// would then not implement them here.)
public interface ConfigurableTransport {

    boolean mayConfigure(EntityPlayer player);

    // Its settings, as the screen shows them
    ObeliskConfig transportConfig();

    // Its access list as it stands, and as the screen saves it
    List<ObeliskAccess> transportAccess();

    void setTransportAccess(boolean allowEveryone, List<ObeliskAccess> access);

    // Unlinks it, and its partner from it
    void unlinkTransport();
}
