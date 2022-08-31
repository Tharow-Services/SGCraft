//------------------------------------------------------------------------------------------------
//
//   SG Craft - Dimension map
//
//------------------------------------------------------------------------------------------------
package gcewing.sg.util;

import java.util.*;

import gcewing.sg.BaseUtils;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;

//import net.minecraftforge.common.*;

public class SGAddressMap extends WorldSavedData {

    public static boolean debugAddressMap = true;
    protected Map<String, String> addressMap = new HashMap<>();

    public SGAddressMap(String name) {
        super(name);
    }

    protected String StoreAddress(ChunkPos chunk) {
        return chunk.d+" "+chunk.x+" "+chunk.z;
    }

    protected ChunkPos UnStoreAddress(String stored) {
        String[] s = stored.split(" ");
        return new ChunkPos(
                Integer.parseInt(s[0]),
                Integer.parseInt(s[1]),
                Integer.parseInt(s[2])
        );
    }

    public static SGAddressMap get() {
        World world = BaseUtils.getWorldForDimension(0);
        assert world != null;
        return BaseUtils.getWorldData(world, SGAddressMap.class, "sgcraft-address_map");
    }

    public static ChunkPos getAddress(int d, int cx, int cz) {
        return get().getSGAddress(new ChunkPos(d, cx, cz));
    }

    public static ChunkPos getAddress(ChunkPos address) {return get().getSGAddress(address);}
    protected ChunkPos getSGAddress(ChunkPos addr) {
        if (addressMap.containsKey(StoreAddress(addr))) {
            if (debugAddressMap)
                System.out.printf("SGAddressMap: Address: %s Was Switched\n", addr);
            return UnStoreAddress(addressMap.get(StoreAddress(addr)));
        }

        if (debugAddressMap)
            System.out.printf("SGAddressMap: Address: %s Wasn't Switched\n", addr);
        return addr;
    }

    public static void AddAddressInt(int d, int cx, int cz, int d1, int cx1, int cz1) {
        get().addAddress(new ChunkPos(d, cx, cz), new ChunkPos(d1, cx1, cz1));
    }

    public static void AddAddress(ChunkPos one, ChunkPos two) {
        get().addAddress(one, two);
    }

    protected void addAddress(ChunkPos one, ChunkPos two) {
        addressMap.put(StoreAddress(one), StoreAddress(two));
        addressMap.put(StoreAddress(two), StoreAddress(one));
        markDirty();
    }

    public static void RemoveAddress(ChunkPos addr) {get().removeAddress(addr);}

    protected void removeAddress(ChunkPos addr) {
        addressMap.remove(addressMap.get(StoreAddress(addr)));
        addressMap.remove(StoreAddress(addr));
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (debugAddressMap)
            System.out.printf("SGAddressMap: Reading from nbt\n");


        for (String key : nbt.getKeySet()) {
            addressMap.put(key, nbt.getString(key));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        if (debugAddressMap)
            System.out.printf("SGAddressMap: Writing to nbt\n");

        for (Map.Entry<String, String> entry : addressMap.entrySet() ) {
            nbt.setString(entry.getKey(),entry.getValue());
        }

        return nbt;
    }

}
