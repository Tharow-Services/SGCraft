package gcewing.sg.features.edd;

import gcewing.sg.generator.GeneratorAddressRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

// What each player knows of the server's generated gates: which they have visited, and the names they gave them.
// It is kept with the player, in the part of their data that survives death, not on the Explorer Dialer Device,
// so it carries over to a new device and counts visits made without one.
public final class ExplorerData {

    private static final String ROOT = "sgcraft_explorer";
    private static final String VISITED = "visited";
    private static final String NAMES = "names";
    // Longest name a player can give an address
    public static final int MAX_NAME_LENGTH = 40;

    private ExplorerData() {}

    // The player's explorer data, created if they have none yet
    public static NBTTagCompound of(final EntityPlayer player) {
        final NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        final NBTTagCompound persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(ROOT)) {
            persisted.setTag(ROOT, new NBTTagCompound());
        }
        return persisted.getCompoundTag(ROOT);
    }

    private static NBTTagCompound section(final EntityPlayer player, final String name) {
        final NBTTagCompound root = of(player);
        if (!root.hasKey(name)) {
            root.setTag(name, new NBTTagCompound());
        }
        return root.getCompoundTag(name);
    }

    // Addresses are kept without dashes and in upper case
    public static String normalize(final String address) {
        return address == null ? "" : address.replace("-", "").trim().toUpperCase();
    }

    // Records that the player has been at a gate, if it's one of the generated ones; true if that's new
    public static boolean markVisited(final EntityPlayer player, final String address) {
        final String key = normalize(address);
        if (key.isEmpty() || !GeneratorAddressRegistry.isGenerated(key)) {
            return false;
        }
        final NBTTagCompound visited = section(player, VISITED);
        if (visited.getBoolean(key)) {
            return false;
        }
        visited.setBoolean(key, true);
        return true;
    }

    public static boolean isVisited(final EntityPlayer player, final String address) {
        return section(player, VISITED).getBoolean(normalize(address));
    }

    public static String getName(final EntityPlayer player, final String address) {
        return section(player, NAMES).getString(normalize(address));
    }

    // Sets the name the player gave an address; a blank name removes it
    public static void setName(final EntityPlayer player, final String address, final String name) {
        final String key = normalize(address);
        if (key.isEmpty()) {
            return;
        }
        final NBTTagCompound names = section(player, NAMES);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        if (trimmed.isEmpty()) {
            names.removeTag(key);
        } else {
            names.setString(key, trimmed);
        }
    }

    // Changes whenever the generated gates or this player's visits or names change
    public static int revisionFor(final EntityPlayer player) {
        return 31 * GeneratorAddressRegistry.getRevision() + of(player).hashCode();
    }
}
