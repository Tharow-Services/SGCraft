package gcewing.sg.features.rings;

import net.malisis.core.inventory.MalisisInventory;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

// Where ring platforms meet MalisisCore. Only called when MalisisCore is installed, so classes that need it
// (RingPlatformMalisisTE) are only ever loaded then: the platform block and the mod reach them through here, and so
// load nothing of MalisisCore's without it.
public final class RingPlatformMalisis {

    private RingPlatformMalisis() {}

    public static void registerTileEntity(ResourceLocation id) {
        GameRegistry.registerTileEntity(RingPlatformMalisisTE.class, id);
    }

    public static RingPlatformTE create() {
        return new RingPlatformMalisisTE();
    }

    // Opens its MalisisCore screen, with its fuel slots; false if it isn't a MalisisCore platform (one placed before)
    public static boolean openScreen(EntityPlayerMP player, RingPlatformTE te) {
        if (te instanceof RingPlatformMalisisTE) {
            MalisisInventory.open(player, (RingPlatformMalisisTE) te);
            return true;
        }
        return false;
    }
}
