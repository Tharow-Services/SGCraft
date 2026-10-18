//------------------------------------------------------------------------------------------------
//
//   SG Craft - Base class for stargate computer interface tile entities
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.tileentity;

import gcewing.sg.BaseTileEntity;
import gcewing.sg.SGCraft;
import gcewing.sg.features.zpm.ZpmSlotSource;
import gcewing.sg.features.zpm.link.ILinkableZpm;
import gcewing.sg.interfaces.ISGEnergySource;
import gcewing.sg.util.ComputerLookupLimiter;
import gcewing.sg.util.SGAddressing;
import gcewing.sg.util.SGAddressing.AddressingError;
import gcewing.sg.util.SGState;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SGInterfaceTE extends BaseTileEntity {

    public SGBaseTE getBaseTE() {
        return SGBaseTE.get(getWorld(), pos.add(0, 1, 0));
    }
    
    // Signature is really prependArgs(Object..., Object[])
    
    public static Object[] prependArgs(Object... args) {
        int preLength = args.length - 1;
        Object[] post = (Object[])args[preLength];
        Object[] xargs = new Object[preLength + post.length];
        System.arraycopy(args, 0, xargs, 0, preLength);
        System.arraycopy(post, 0, xargs, preLength, post.length);
        return xargs;
    }
    
    public void rebroadcastNetworkPacket(Object packet) {
    }

    public static class CIStargateState {
        public String state;
        public int chevrons;
        public String direction;
        public CIStargateState(String state, int chevrons, String direction) {
            this.state = state;
            this.chevrons = chevrons;
            this.direction = direction;
        }
    }
        
    public SGBaseTE requireBaseTE() {
        SGBaseTE te = getBaseTE();
        if (te != null && te.isMerged)
            return te;
        throw new IllegalArgumentException("missingStargate");
    }
    
    public SGBaseTE requireIrisTE() {
        SGBaseTE te = requireBaseTE();
        if (te != null && te.hasIrisUpgrade)
            return te;
        else
            throw new IllegalArgumentException("missingIris");
    }
    
    String directionDescription(SGBaseTE te) {
        //Todo: this was changed from te.isConnected() since that doesn't help during dialing process.
        if (te.state != SGState.Idle) {
            if (te.isInitiator) {
                return "Outgoing";
            } else {
                return "Incoming";
            }
        } else {
            return "Not Connected";
        }
    }
    
    public CIStargateState ciStargateState() {
        SGBaseTE te = getBaseTE();
        if (te != null)
            return new CIStargateState(te.sgStateDescription(), te.numEngagedChevrons, directionDescription(te));
        else
            return new CIStargateState("Offline", 0, "");
    }
    
    public double ciEnergyAvailable() {
        SGBaseTE te = getBaseTE();
        return te != null ? te.availableEnergy() : 0;
    }
    
    // ---- ZPMs: what the gate can draw on, for computers ----

    // Each ZPM device the gate can draw on: ZPM consoles (and with IC2, interface carts and hubs) near its base or linked
    // to it, empty or not, and a supergate's own ZPM slots. Keyed 1, 2, 3... (both computer mods make a Map a table;
    // ComputerCraft doesn't an array). Energy is in ZPM units, as the devices' screens and ZPMs' tooltips show it.
    public Map<Integer, Map<String, Object>> ciZpmDevices() {
        SGBaseTE gate = requireBaseTE();
        Map<Integer, Map<String, Object>> devices = new LinkedHashMap<>();
        // A supergate's own slots (only those holding a ZPM with energy are sources)
        for (ISGEnergySource source : gate.zpmSources()) {
            if (source instanceof ZpmSlotSource) {
                devices.put(devices.size() + 1, zpmDevice("stargate", gate.getPos(), source.availableEnergy(), 1, false));
            }
        }
        // ZPM devices: near the base, and linked to the gate wherever they are
        Set<BlockPos> seen = new HashSet<>();
        List<TileEntity> found = new ArrayList<>();
        int r = SGCraft.zpmSearchRange;
        for (BlockPos p : BlockPos.getAllInBox(gate.getPos().add(-r, -r, -r), gate.getPos().add(r, r, r))) {
            if (world.isBlockLoaded(p)) {
                TileEntity te = world.getTileEntity(p);
                if (te instanceof ILinkableZpm && seen.add(p.toImmutable())) {
                    found.add(te);
                }
            }
        }
        for (ILinkableZpm device : gate.linkedZpmDevices()) {
            TileEntity te = (TileEntity) device;
            if (seen.add(te.getPos())) {
                found.add(te);
            }
        }
        for (TileEntity te : found) {
            ILinkableZpm device = (ILinkableZpm) te;
            String type = te.getBlockType().getRegistryName() != null ? te.getBlockType().getRegistryName().getPath() : "zpm";
            devices.put(devices.size() + 1, zpmDevice(type, te.getPos(), device.availableEnergy(), zpmsIn(te),
                device.getZpmLink().isLinkedTo(gate.getPos())));
        }
        return devices;
    }

    // The ZPM energy the gate can draw on, and how many ZPMs hold it
    public Object[] ciZpmEnergy() {
        double energy = 0;
        int zpms = 0;
        for (Map<String, Object> device : ciZpmDevices().values()) {
            energy += (Double) device.get("energy");
            zpms += (Integer) device.get("zpms");
        }
        return new Object[] {energy, zpms};
    }

    private static Map<String, Object> zpmDevice(String type, BlockPos pos, double sgEnergy, int zpms, boolean linked) {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("type", type);
        device.put("x", pos.getX());
        device.put("y", pos.getY());
        device.put("z", pos.getZ());
        device.put("energy", sgEnergy * SGCraft.ZPMEnergyPerSGEnergyUnit);
        device.put("zpms", zpms);
        device.put("linked", linked);
        return device;
    }

    // How many ZPMs a device holds
    private static int zpmsIn(TileEntity te) {
        int zpms = 0;
        if (te instanceof IInventory) {
            IInventory inventory = (IInventory) te;
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                if (inventory.getStackInSlot(i).getItem() == SGCraft.zpm) {
                    zpms++;
                }
            }
        }
        return zpms;
    }

    public double ciEnergyToDial(String address, boolean pending) {
        SGBaseTE te = requireBaseTE();
        try {
            address = SGAddressing.normalizeAddress(address);
            // Looking a gate up loads its chunk: computers may only do so now and then (see ComputerLookupLimiter)
            Double cached = ComputerLookupLimiter.cachedEnergy(te, address);
            if (cached != null) {
                return cached;
            }
            if (ComputerLookupLimiter.knownMissing(te, address)) {
                throw new IllegalArgumentException("unknownAddress");
            }
            ComputerLookupLimiter.checkLookup(te, address);
            SGBaseTE dte = SGAddressing.findAddressedStargate(address, te.getWorld(), pending);
            if (dte == null) {
                ComputerLookupLimiter.cacheMissing(te, address);
                throw new IllegalArgumentException("unknownAddress");
            }
            double distanceFactor = dte.distanceFactorForCoordDifference(te, dte);
            double energy = te.energyToOpen * distanceFactor;
            ComputerLookupLimiter.cache(te, address, energy);
            return energy;
        } catch (AddressingError e) {
            //System.out.printf("SGBaseTE.ciEnergyToDial: caught %s\n", e);
            throw new IllegalArgumentException(e.getMessage());
        }
    }
    
    public String ciLocalAddress() {
        SGBaseTE te = getBaseTE();
        try {
            return te != null ? te.getHomeAddress() : "";
        } catch (AddressingError e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
    
    public String ciRemoteAddress() {
        SGBaseTE te = requireBaseTE();
        try {
            return te.connectedLocation != null ? SGAddressing.addressForLocation(te.connectedLocation) : "";
        } catch (AddressingError e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
    
    public void ciDial(String address, boolean pending) {
        SGBaseTE te = requireBaseTE();
//         try {
//             address = SGAddressing.normalizedRelativeAddress(address, te.getHomeAddress());
//         }
//         catch (AddressingError e) {
//             throw new IllegalArgumentException(e.getMessage());
//         }
        address = SGAddressing.normalizeAddress(address);
        // Dialling looks the gate up too, loading its chunk (see ComputerLookupLimiter)
        if (te.state == SGState.Idle) {
            if (ComputerLookupLimiter.knownMissing(te, address)) {
                throw new IllegalArgumentException("unknownAddress");
            }
            ComputerLookupLimiter.checkLookup(te, address);
        }
        //System.out.printf("SGBaseTE.ciDial: dialling symbols %s\n", address);
        String error = te.connect(address, null, true, pending);
        if ("unknownAddress".equals(error)) {
            ComputerLookupLimiter.cacheMissing(te, address);
        }
        if (error != null)
            throw new IllegalArgumentException(error);
    }
    
    public void ciDisconnect() {
        SGBaseTE te = requireBaseTE();
        String error = te.disconnect(null);
        if (error != null)
            throw new IllegalArgumentException(error);
    }
    
    public String ciIrisState() {
        SGBaseTE te = getBaseTE();
        return te != null && te.hasIrisUpgrade ? te.irisStateDescription() : "Offline";
    }
    
    public void ciOpenIris() {
        requireIrisTE().openIris();
    }
    
    public void ciCloseIris() {
        requireIrisTE().closeIris();
    }
    
    public void ciSendMessage(Object[] args) {
        SGBaseTE te = requireBaseTE();
        if (args.length > maxMessageArgs || messageSize(args, 0) > maxMessageSize) {
            throw new IllegalArgumentException("messageTooLarge");
        }
        String error = te.sendMessage(args);
        if (error != null)
            throw new IllegalArgumentException(error);
    }

    // How much a message may carry. Every computer at the far gate is sent it as an event, kept until read, and
    // OpenComputers logs a warning for each part of a kind it can't pass on: unlimited, a script could flood the far
    // computers and the server log.
    static final int maxMessageArgs = 32;
    static final int maxMessageSize = 16384;

    // Its size: the length of each string and byte array, and one for anything else, a table's keys and values included
    private static int messageSize(Object value, int depth) {
        if (depth > 8) {
            return maxMessageSize + 1;
        }
        if (value instanceof String) {
            return ((String) value).length();
        }
        if (value instanceof byte[]) {
            return ((byte[]) value).length;
        }
        if (value instanceof Object[]) {
            int size = 0;
            for (Object part : (Object[]) value) {
                size += 1 + messageSize(part, depth + 1);
                if (size > maxMessageSize) {
                    break;
                }
            }
            return size;
        }
        if (value instanceof Map) {
            int size = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                size += 2 + messageSize(entry.getKey(), depth + 1) + messageSize(entry.getValue(), depth + 1);
                if (size > maxMessageSize) {
                    break;
                }
            }
            return size;
        }
        return 1;
    }
}
