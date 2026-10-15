package gcewing.sg.features.pdd;

import static com.google.common.base.Preconditions.checkNotNull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AddressData implements Comparable<AddressData> {

    static final String ADDRESSES = "addresses";
    private static final String NAME = "name";
    private static final String ADDRESS = "address";
    private static final String LOCKED = "locked";
    private static final String INDEX = "index";
    private static final String UNID = "unid";
    private static final String AUTOCLOSE = "autoclose";

    private final String name;
    private final String address;
    private final boolean locked;
    private final int index;
    private final int unid;
    private final boolean autoClose;

    public AddressData(final String name, final String address, final boolean locked, final int index, final int unid, final boolean autoClose) {
        this.name = name;
        this.address = address;
        this.locked = locked;
        this.index = index;
        this.unid = unid;
        this.autoClose = autoClose;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isAutoClose() {
        return autoClose;
    }

    public int getIndex() {
        return index;
    }

    public int getUnid() {
        return unid;
    }

    public static void updateAddress(EntityPlayer player, NBTTagCompound compound, int unid, String newName, String newAddress, int newIndex, boolean locked, boolean autoClose) {
        checkNotNull(compound);
        final List<AddressData> addresses = new ArrayList<>();
        final NBTTagList list = compound.getTagList(ADDRESSES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound addressCompound = list.getCompoundTagAt(i);

            final int compoundEntryUnid = addressCompound.getInteger(UNID);
            if (compoundEntryUnid == unid) {
                addressCompound.setString(NAME, newName);
                addressCompound.setString(ADDRESS, newAddress);
                addressCompound.setInteger(INDEX, newIndex);
                addressCompound.setBoolean(AUTOCLOSE, autoClose);
            }

            if (!(addressCompound.getInteger(INDEX) == -1)) { // -1 means delete the entry, so skip re-adding it to the list.
                addresses.add(new AddressData(addressCompound.getString(NAME), addressCompound.getString(ADDRESS), addressCompound.getBoolean(LOCKED), addressCompound.getInteger(INDEX), addressCompound.getInteger(UNID), addressCompound.getBoolean(AUTOCLOSE)));
            }
        }

        if (unid == 0) { // Indicates new request.
            addresses.add(new AddressData(newName, newAddress, locked, newIndex, 1, autoClose));
        }

        writeAddresses(compound, addresses);
    }

    // Swaps the entry with the one above (direction -1) or below (+1) it in the list. The two trade index values, so
    // nothing else moves; if the indexes repeat, so the order can't be told apart, the list is renumbered 1, 2, 3...
    public static boolean moveAddress(final NBTTagCompound compound, final int unid, final int direction) {
        checkNotNull(compound);
        final List<AddressData> addresses = getAddresses(compound); // in index order

        int from = -1;
        for (int i = 0; i < addresses.size(); i++) {
            if (addresses.get(i).getUnid() == unid) {
                from = i;
                break;
            }
        }
        final int to = from + Integer.signum(direction);
        if (from < 0 || direction == 0 || to < 0 || to >= addresses.size()) {
            return false;
        }

        final List<Integer> indexes = new ArrayList<>();
        boolean repeated = false;
        for (int i = 0; i < addresses.size(); i++) {
            indexes.add(addresses.get(i).getIndex());
            repeated |= i > 0 && indexes.get(i).equals(indexes.get(i - 1));
        }
        if (repeated) {
            for (int i = 0; i < indexes.size(); i++) {
                indexes.set(i, i + 1);
            }
        }

        Collections.swap(addresses, from, to);
        final List<AddressData> renumbered = new ArrayList<>();
        for (int i = 0; i < addresses.size(); i++) {
            final AddressData a = addresses.get(i);
            renumbered.add(new AddressData(a.getName(), a.getAddress(), a.isLocked(), indexes.get(i), a.getUnid(), a.isAutoClose()));
        }
        writeAddresses(compound, renumbered);
        return true;
    }

    public static List<AddressData> getAddresses(final NBTTagCompound compound) {
        checkNotNull(compound);

        final List<AddressData> addresses = new ArrayList<>();
        final NBTTagList list = compound.getTagList(ADDRESSES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound addressCompound = list.getCompoundTagAt(i);

            final String name = addressCompound.getString(NAME);
            final String address = addressCompound.getString(ADDRESS);
            final boolean locked = addressCompound.getBoolean(LOCKED);
            final int index = addressCompound.getInteger(INDEX);
            final int unid = addressCompound.getInteger(UNID);
            final boolean autoClose = addressCompound.getBoolean(AUTOCLOSE);

            addresses.add(new AddressData(name, address, locked, index, unid, autoClose));
        }

        Collections.sort(addresses);

        return addresses;
    }

    public static NBTTagCompound writeAddresses(final NBTTagCompound compound, final List<AddressData> addresses) {
        checkNotNull(compound);
        checkNotNull(addresses);

        final NBTTagList list = new NBTTagList();

        for (final AddressData data : addresses) {
            final NBTTagCompound addressCompound = new NBTTagCompound();
            addressCompound.setString(NAME, data.getName());
            addressCompound.setString(ADDRESS, data.getAddress());
            addressCompound.setBoolean(LOCKED, data.isLocked());
            addressCompound.setInteger(INDEX, data.getIndex());
            addressCompound.setInteger(UNID, data.hashCode());
            addressCompound.setBoolean(AUTOCLOSE, data.isAutoClose());

            list.appendTag(addressCompound);
        }

        compound.setTag(ADDRESSES, list);

        return compound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AddressData that = (AddressData) o;
        return Objects.equals(address + name + index + locked + autoClose, that.address + that.name + that.index + that.locked + that.autoClose);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address + name + index + locked + autoClose);
    }

    @Override
    public int compareTo(AddressData o) {
        return this.getIndex() - o.getIndex();
    }
}
