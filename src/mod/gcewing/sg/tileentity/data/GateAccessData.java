package gcewing.sg.tileentity.data;

import static com.google.common.base.Preconditions.checkNotNull;

import gcewing.sg.util.SGAddressing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

public class GateAccessData {
    public static final String GATEACCESSLIST = "gateaccesslist";
    public static final String ADDRESS = "address";
    private static final String IACCESS = "iaccess";
    private static final String OACCESS = "oaccess";

    private String address;
    private boolean iaccess;
    private boolean oaccess;

    public GateAccessData(final String address, final boolean iaccess, boolean oaccess) {
        this.address = address;
        this.iaccess = iaccess;
        this.oaccess = oaccess;
    }

    // An address without its dashes, in capitals, so "abcd-efg-hi" and "ABCDEFGHI" match
    public static String normalize(String address) {
        return address == null ? "" : address.replace("-", "").trim().toUpperCase();
    }

    // A full 9-symbol address as XXXX-XXX-XX, or null if it isn't one. Only full addresses are kept: a 7-symbol one
    // would mean a different gate depending on which dimension it was dialled from.
    @Nullable
    public static String formatForList(String address) {
        String normalized = normalize(address);
        if (normalized.length() != SGAddressing.maxAddressLength) {
            return null;
        }
        try {
            SGAddressing.validateAddress(normalized);
        } catch (SGAddressing.AddressingError e) {
            return null;
        }
        return SGAddressing.formatAddress(normalized, "-", "-");
    }

    public boolean matches(String otherAddress) {
        return normalize(address).equals(normalize(otherAddress));
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean hasIncomingAccess() {
        return iaccess;
    }

    public void setIncomingAccess(boolean value) {
        this.iaccess = value;
    }

    public boolean hasOutgoingAccess() {
        return oaccess;
    }

    public void setOutgoingAccess(boolean value) {
        this.oaccess = value;
    }

    public static List<GateAccessData> getGateAccessList(final NBTTagCompound compound) {
        checkNotNull(compound);

        final List<GateAccessData> gateAccessData = new ArrayList<>();
        final NBTTagList list = compound.getTagList(GATEACCESSLIST, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound addressCompound = list.getCompoundTagAt(i);
            gateAccessData.add(new GateAccessData(addressCompound.getString(ADDRESS), addressCompound.getBoolean(IACCESS),
                addressCompound.getBoolean(OACCESS)));
        }

        return gateAccessData;
    }

    public static NBTTagCompound writeAddresses(final NBTTagCompound compound, final List<GateAccessData> gateAccessData) {
        checkNotNull(compound);
        checkNotNull(gateAccessData);

        final NBTTagList list = new NBTTagList();
        for (final GateAccessData data : gateAccessData) {
            final NBTTagCompound addressCompound = new NBTTagCompound();
            addressCompound.setString(ADDRESS, data.getAddress());
            addressCompound.setBoolean(IACCESS, data.hasIncomingAccess());
            addressCompound.setBoolean(OACCESS, data.hasOutgoingAccess());
            list.appendTag(addressCompound);
        }
        compound.setTag(GATEACCESSLIST, list);

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
        GateAccessData that = (GateAccessData) o;
        return iaccess == that.iaccess && oaccess == that.oaccess && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, iaccess, oaccess);
    }
}
