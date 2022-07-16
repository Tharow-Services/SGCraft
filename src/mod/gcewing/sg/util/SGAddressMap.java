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

    public static SGAddressMap get() {
        World world = BaseUtils.getWorldForDimension(0);
        return BaseUtils.getWorldData(world, SGAddressMap.class, "sgcraft-address_map");
    }

    public static boolean CheckAddress(int d, int cx, int cz) {
        return get().checkAddress(d,cx,cz);
    }

    protected boolean checkAddress(int d, int cx, int cz) {
        return addressMap.containsKey(new SGAddress(d,cx,cz).toString());
    }

    public static SGAddress getAddress(int d, int cx, int cz) {
        return get().getSGAddress(new SGAddress(d, cx, cz));
    }

    protected SGAddress getSGAddress(SGAddress addr) {
        if (addressMap.containsKey(addr.toString())) {
            if (debugAddressMap)
                System.out.printf("SGAddressMap: Address: %s Was Switched\n", addr);
            return new SGAddress(addressMap.get(addr.toString()));
        }

        if (debugAddressMap)
            System.out.printf("SGAddressMap: Address: %s Wasn't Switched\n", addr);
        return addr;
    }

    public static void AddAddressInt(int d, int cx, int cz, int d1, int cx1, int cz1) {
        get().addAddress(new SGAddress(d, cx, cz), new SGAddress(d1, cx1, cz1));
    }

    public static void AddAddress(SGAddress one, SGAddress two) {
        get().addAddress(one, two);
    }

    protected void addAddress(SGAddress one, SGAddress two) {
        addressMap.put(one.toString(), two.toString());
        addressMap.put(two.toString(), one.toString());
        markDirty();
    }

    public static void RemoveAddress(SGAddress addr) {}

    protected void removeAddress(SGAddress addr) {
        addressMap.remove(addressMap.get(addr.toString()));
        addressMap.remove(addr.toString());
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
