package gcewing.sg.features.obelisk;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

// A player on an Asgard obelisk's access list: whether they may use it, and whether they may configure it (admin)
public class ObeliskAccess {

    public final UUID uuid;
    public final String name;
    public boolean use;
    public boolean admin;

    public ObeliskAccess(UUID uuid, String name, boolean use, boolean admin) {
        this.uuid = uuid;
        this.name = name;
        this.use = use;
        this.admin = admin;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setUniqueId("uuid", uuid);
        nbt.setString("name", name);
        nbt.setBoolean("use", use);
        nbt.setBoolean("admin", admin);
        return nbt;
    }

    public static ObeliskAccess fromNBT(NBTTagCompound nbt) {
        return new ObeliskAccess(nbt.getUniqueId("uuid"), nbt.getString("name"), nbt.getBoolean("use"), nbt.getBoolean("admin"));
    }
}
