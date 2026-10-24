//------------------------------------------------------------------------------------------------
//
//   SG Craft - Conversions between coordinates and  stargate addresses
//
//------------------------------------------------------------------------------------------------
//
//   New Addressing Scheme
//   ---------------------
//
//   There are 36 symbols. An address consists of 7 or 9 symbols:
//
//      CCCCCCC
//
//   or
//
//      CCCCCCCDD
//
//   where DD encodes the dimension and CCCCCCC encode the coordinates within
//   the dimension. When connecting to a gate in the same dimension, DD may
//   be omitted, allowing a 7-chevron gate to be used.
//
//   The coordinate range is +/- sqrt(36**7))/2-1 = 139,967 chunks = 2,239,472 blocks
//
//   The dimension range is -36**2/2 to 36**2/2-1 = -648 to 647
//

package gcewing.sg.util;

import gcewing.sg.BaseUtils;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;

public class SGAddressing {

    static boolean debugAddressing = false;

        public static class AddressingError extends Exception {
        AddressingError(String s) {super(s);}
    }

    public static String symbolChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static int numSymbols = symbolChars.length();
    public static int numCoordSymbols = 7;
    public static int numDimensionSymbols = 2;
    
    public static int maxAddressLength = numCoordSymbols + numDimensionSymbols;
    //
    public static int maxCoord = 139967;
    public static int minCoord = -maxCoord;
    public static int coordRange = maxCoord - minCoord + 1;
//     public final static int minDimension = -648;
//     public final static int maxDimension = 647;
//     public final static int dimensionRange = maxDimension - minDimension + 1;
    public static int maxDimensionIndex = 1295;
    public static int minDirectDimension = -648;
    public static int dimensionRange = maxDimensionIndex + 1;
    final static String padding = "---------";
    public static long mc = coordRange + 2; // == 2 * maxCoord + 3;
    public static long pc = 93563;   //  (pc * qc) % mc == 1
    public static long qc = 153742;
//     final static long md = dimensionRange + 2;
    public static long md = dimensionRange + 1;
    public static long pd = 953;  //  (pd * qd) % md == 1
    public static long qd = 788;

    public static void setup36Symbols() {
        symbolChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        numSymbols = symbolChars.length();
        maxCoord = Math.toIntExact(Math.round(Math.sqrt(numSymbols ^ numCoordSymbols)))-1;
        minCoord = -maxCoord;
        coordRange = maxCoord - minCoord + 1;
        maxDimensionIndex = 1295;
        minDirectDimension = -648;
        dimensionRange = maxDimensionIndex + 1;
        mc = coordRange + 2; // == 2 * maxCoord + 3;
        pc = 93563;   //  (pc * qc) % mc == 1
        qc = 153742;
        md = dimensionRange + 1;
        pd = 953;  //  (pd * qd) % md == 1
        qd = 788;
    }

    public static void setup38Symbols() {
        symbolChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<>";
        numSymbols = symbolChars.length();
        maxCoord = 169126;
        minCoord = -maxCoord;
        coordRange = maxCoord - minCoord + 1;
        maxDimensionIndex = 1443-1;
        minDirectDimension = -722;
        dimensionRange = maxDimensionIndex + 1;
        mc = coordRange + 2; // == 2 * maxCoord + 3;
        pc = 93563;   //  (pc * qc) % mc == 1
        qc = 104257;
        md = 1445;
        pd = 953;  //  (pd * qd) % md == 1
        qd = 1072;
    }

    public static boolean isValidSymbolChar(String c) {
        return symbolChars.contains(c);
    }

    public static char symbolToChar(int i) {
        return symbolChars.charAt(i);
    }

    public static int charToSymbol(char c) {
        return charToSymbol(String.valueOf(c));
    }

    public static int charToSymbol(String c) {
        return symbolChars.indexOf(c);
    }

    public static boolean validSymbols(String s) {
        for (int i = 0; i < s.length(); i++)
            if (charToSymbol(s.charAt(i)) < 0)
                return false;
        return true;
    }

    public static void validateAddress(String s) throws AddressingError {
        int l = s.length();
        if ((l ==  numCoordSymbols || l == numCoordSymbols + numDimensionSymbols)
            && validSymbols(s))
                return;
        throw new AddressingError("malformedAddress");
    }
    
    public static String normalizeAddress(String address) {
        return address.replace("-", "").toUpperCase();
    }
    
//     public static String relativeAddress(String targetAddress, String contextAddress)
//         throws AddressingError
//     {
//         validateAddress(targetAddress);
//         if (addressesInSameDimension(targetAddress, contextAddress))
//             return coordSymbolsOf(targetAddress);
//         else
//             return targetAddress;
//     }
    
//     public static String normalizedRelativeAddress(String targetAddress, String contextAddress)
//         throws AddressingError
//     {
//         return relativeAddress(normalizeAddress(targetAddress), contextAddress);
//     }
    

    public static String coordSymbolsOf(String address) {
        return address.substring(0, numCoordSymbols);
    }
    
    public static int dimensionSymbolsOf(String address) {
        int dimensionID = -999;
        try {
            validateAddress(address);
        } catch (AddressingError addressingError) {
            addressingError.printStackTrace();
        }
        String csyms = address.substring(0, numCoordSymbols);
        long c = longFromSymbols(csyms);
        if (address.length() == maxAddressLength) {
            String dsyms = address.substring(numCoordSymbols);
            int d = intFromSymbols(dsyms);
            int dp = hash(d, qd, md);
            dimensionID = unpermuteDimension(c, dp);
        }
        return dimensionID;
    }

    public static boolean inSameDimension(String origin, String destination) {
        int originDimension = dimensionSymbolsOf(origin);
        int destinationDimension = dimensionSymbolsOf(destination);
        if (originDimension == destinationDimension) {
            return true;
        } else {
            return false;
        }
    }

    // The full address of a local one (as dialled within one dimension) in that dimension. Its dimension symbols can't be
    // copied from another address in the same dimension: they're mixed with each address's own coordinates.
    public static String fullAddress(String address, int dimension) {
        address = address.replace("-", "").toUpperCase();
        if (address.length() != numCoordSymbols) {
            return address;
        }
        long c = longFromSymbols(address);
        int d = hash(permuteDimension(c, SGDimensionMap.indexForDimension(dimension)), pd, md);
        return address + intToSymbols(d, numDimensionSymbols);
    }

    public static String addressForLocation(SGLocation loc) throws AddressingError {
        if (debugAddressing)
            System.out.printf("SGAddressing.addressForLocation: coord range = %d to %d\n",
                minCoord, maxCoord);
        int chunkx = loc.pos.getX() >> 4;
        int chunkz = loc.pos.getZ() >> 4;
        int dim = loc.dimension;
        // START OF MAPPER //
        ChunkPos addr=SGAddressMap.getAddress(dim,chunkx,chunkz);
        dim=addr.d;chunkx=addr.x;chunkz=addr.z;
        // END OF MAPPER //

        if (!inCoordRange(chunkx) || !inCoordRange(chunkz))
            throw new AddressingError("targetOutOfRange");
//         if (!inDimensionRange(loc.dimension))
//             throw dimensionRangeError;
        Integer di = SGDimensionMap.indexForDimension(dim);
        if (di > maxDimensionIndex)
            throw new AddressingError("dimensionTooFar");
        long c = interleaveCoords(hash(chunkx - minCoord, pc, mc), hash(chunkz - minCoord, pc, mc));
//         int d = hash(loc.dimension - minDimension, pd, md);
        int dp = permuteDimension(c, di);
        int d = hash(dp, pd, md);
        if (debugAddressing)
            System.out.printf(
                "SGAddressing.addressForLocation: chunk (%d,%d) in dimension %d gives c = %s d = %d\n",
                chunkx, chunkz, loc.dimension, c, d);
        return longToSymbols(c, numCoordSymbols) + intToSymbols(d, numDimensionSymbols);
    }
    
    public static SGBaseTE findAddressedStargate(String address, World fromWorld, boolean pending) throws AddressingError {
        if (debugAddressing)
            System.out.printf("SGAddressing.findAddressedStargate: %s\n", address);
        validateAddress(address);
        String csyms = address.substring(0, numCoordSymbols);
        long c = longFromSymbols(csyms);
        ChunkPos hashedPos = uninterleaveCoords(c);
        int chunkX = minCoord + hash(hashedPos.x, qc, mc);
        int chunkZ = minCoord + hash(hashedPos.z, qc, mc);
        if (debugAddressing)
            System.out.printf("SGAddressing.findAddressedStargate: c = %s chunk = (%d,%d)\n",
                c, chunkX, chunkZ);
        SGBaseTE te = null;
        if (address.length() == maxAddressLength) {
            // Absolute address
            String dsyms = address.substring(numCoordSymbols);
            int d = intFromSymbols(dsyms);
            int dp = hash(d, qd, md);
            int di = unpermuteDimension(c, dp);
            Integer dm = SGDimensionMap.dimensionForIndex(di);
            // Start of Mapper //
            ChunkPos addr=SGAddressMap.getAddress(dm,chunkX,chunkZ);
            dm=addr.d;chunkX=addr.x;chunkZ=addr.z;
            // End of Mapper //
            if (debugAddressing)
                System.out.printf("SGAddressing.findAddressedStargate: d = %s dimension = %s\n",
                    d, dm);
            te = getBaseTE(chunkX, chunkZ, dm, pending);
        }
        else {
            // Relative address
            int dimension = fromWorld.provider.getDimension();
            // Start of Mapper //
            ChunkPos addr=SGAddressMap.getAddress(dimension,chunkX,chunkZ);
            dimension=addr.d;chunkX=addr.x;chunkZ=addr.z;
            // End of Mapper //
            te = getBaseTE(chunkX, chunkZ, dimension, pending);
        }
        return te;
    }

    public static ChunkPos locationForAddress(String address, int dimension) throws SGAddressing.AddressingError {
        String addr = normalizeAddress(address);
        validateAddress(address);
        long c = longFromSymbols(addr.substring(0, numCoordSymbols));
        ChunkPos hashedPos = uninterleaveCoords(c);
        ChunkPos pos = new ChunkPos(minCoord+hash(hashedPos.x,qc,mc),minCoord+hash(hashedPos.z,qc,mc));
        ChunkPos chunkPos;

        if (addr.length()==maxAddressLength) {
            chunkPos= pos.withDim(unpermuteDimension(c, hash(intFromSymbols(addr.substring(numCoordSymbols)), qd, md)));
        } else {
            chunkPos= pos.withDim(dimension);
        }
        // SGAddressMap
        chunkPos= SGAddressMap.getAddress(chunkPos);
        return chunkPos;

    }

    // Whether there's certainly no stargate at a full address: its world is there, and the chunk it names either was never
    // generated or holds no stargate base at all. False whenever that can't be told (an unknown or unloaded world, or a
    // shorter address), and for a base whose ring isn't whole yet, as the rest may still come with a chunk not generated yet.
    public static boolean noGateAt(String address) {
        address = address.replace("-", "").toUpperCase();
        try {
            validateAddress(address);
        } catch (AddressingError e) {
            return false;
        }
        if (address.length() != maxAddressLength) {
            return false;
        }
        long c = longFromSymbols(address.substring(0, numCoordSymbols));
        ChunkPos hashedpos = uninterleaveCoords(c);
        int chunkX = minCoord + hash(hashedpos.x, qc, mc);
        int chunkZ = minCoord + hash(hashedpos.z, qc, mc);
        int d = intFromSymbols(address.substring(numCoordSymbols));
        Integer dimension = SGDimensionMap.dimensionForIndex(unpermuteDimension(c, hash(d, qd, md)));
        if (dimension == null || getWorld(dimension) == null) {
            return false;
        }
        return !hasBaseAt(chunkX, chunkZ, dimension);
    }

    // Whether the chunk holds any stargate base, loading it from disk if need be (never generating it), and letting it go
    // again unless something else holds it. A dimension that isn't registered has no world, so no gate (getWorld loads
    // any that is).
    private static boolean hasBaseAt(int chunkX, int chunkZ, int dimension) {
        WorldServer world = getWorld(dimension);
        if (world == null) {
            return false;
        }
        ChunkProviderServer provider = world.getChunkProvider();
        Chunk chunk = provider.loadedChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        boolean wasLoaded = chunk != null;
        if (!wasLoaded) {
            if (!provider.isChunkGeneratedAt(chunkX, chunkZ)) {
                return false;
            }
            // Straight from disk: under Sponge, World.getChunk can hand back an empty stand-in for a chunk not loaded,
            // which would look like one without the gate
            chunk = provider.loadChunk(chunkX, chunkZ);
        }
        if (chunk == null || chunk.isEmpty()) {
            return true; // can't be looked in: say there may be one, so nothing is taken off any list
        }
        boolean found = false;
        for (TileEntity te : chunk.getTileEntityMap().values()) {
            if (te instanceof SGBaseTE) {
                found = true;
                break;
            }
        }
        if (!wasLoaded && !world.getPlayerChunkMap().contains(chunkX, chunkZ)) {
            provider.queueUnload(chunk);
        }
        return found;
    }

    // The stargate in the chunk an address names: a complete one first, as a lone base block put in the same chunk would
    // otherwise take the gate's dials, or hide it. Looking leaves the chunk as it was: one never generated isn't made
    // just to look in (no gate can be there), and one loaded only to look in is let go again, unless a dial keeps it (the
    // gate then holds it with a ticket) or a player is there. Computers asking about addresses over and over were
    // keeping chunks loaded that way, and making new ones (issue #163). (A gate found is no longer marked pending here:
    // connect does that for a gate it dials, and a lookup that didn't dial left the gate unable to be removed.)
    protected static SGBaseTE getBaseTE(int chunkX, int chunkZ, int dimension, boolean pending) {
        WorldServer toWorld = getWorld(dimension);
        if (toWorld == null) {
            return null;
        }
        ChunkProviderServer provider = toWorld.getChunkProvider();
        Chunk chunk = provider.loadedChunks.get(ChunkPos.asLong(chunkX, chunkZ)); // loaded, without keeping it so
        boolean wasLoaded = chunk != null;
        if (!wasLoaded) {
            if (!provider.isChunkGeneratedAt(chunkX, chunkZ)) {
                return null;
            }
            // Straight from disk: under Sponge, World.getChunk can hand back an empty stand-in for a chunk not loaded
            chunk = provider.loadChunk(chunkX, chunkZ);
            if (chunk == null) {
                return null;
            }
        }
        SGBaseTE found = null;
        for (TileEntity te : chunk.getTileEntityMap().values()) {
            if (te instanceof SGBaseTE && (found == null || ((SGBaseTE) te).isMerged && !found.isMerged)) {
                found = (SGBaseTE) te;
            }
        }
        if (!wasLoaded && !toWorld.getPlayerChunkMap().contains(chunkX, chunkZ)) {
            provider.queueUnload(chunk);
        }
        return found;
    }
    
    protected static int permuteDimension(long c, int d) {
        return (int)((d + c) % dimensionRange);
    }
    
    protected static int unpermuteDimension(long c, int d) {
        int i = (int)((d - c) % dimensionRange);
        if (i < 0)
            i += dimensionRange;
        return i;
    }
    
    protected static long interleaveCoords(int x, int z) {
        if (debugAddressing)
            System.out.printf("SGAddressing.interleaveCoords: %d, %d\n", x, z);
        long p6 = 1;
        long c = 0;
        while (x > 0 || z > 0) {
            System.out.printf("SGAddressing.interleaveCoords: half-digits %d %d\n", x % 6, z % 6);
            System.out.printf("SGAddressing.interleaveCoords: step c %d \n", c);
            System.out.printf("SGAddressing.interleaveCoords: step p6 %d \n", p6);
            c += p6 * (x % 6); x /= 6; p6 *= 6;
            c += p6 * (z % 6); z /= 6; p6 *= 6;

        }
        if (debugAddressing) {
            System.out.printf("SGAddressing.interleaveCoords: half-digits %d %d\n", x % 6, z % 6);
            System.out.printf("SGAddressing.interleaveCoords: c %d\n", c);
        }
        return c;
    }
    
    protected static ChunkPos uninterleaveCoords(long c) {
        int p6 = 1;
        int x=0;
        int y=0;
        while (c > 0) {
            x += p6 * (c % 6); c /= 6;
            y += p6 * (c % 6); c /= 6;
            p6 *= 6;
        }
        return new ChunkPos(x,y);
    }
    
    protected static int hash(int i, long f, long m) {
        int h = (int)(((i + 1) * f) % m) - 1;
        System.out.printf("SGAddressing.hash(%s, %s, %s) = %s\n", i, f, m, h);

        return h;
    }

    @Nullable
    public static WorldServer getWorld(int dimension) {
        return BaseUtils.getWorldForDimension(dimension);
    }
    
    protected static boolean inCoordRange(int i) {
        return i >= minCoord && i <= maxCoord;
    }
    
//     protected static boolean inDimensionRange(int i) {
//         return i >= minDimension && i <= maxDimension;
//     }

    protected static String intToSymbols(int i, int n) {
        return longToSymbols(i, n);
    }

    protected static String longToSymbols(long i, int n) {
        String s = "";
        while (n-- > 0) {
            s = symbolToChar((int)(i % numSymbols)) + s;
            i /= numSymbols;
        }
        return s;
    }
    
    protected static int intFromSymbols(String s) {
        return (int)longFromSymbols(s);
    }
    
    protected static long longFromSymbols(String s) {
        long i = 0;
        int n = s.length();
        for (int j = 0; j < n; j++) {
            char c = s.charAt(j);
            i = i * numSymbols + charToSymbol(c);
        }
        return i;
    }
    
    public static String padAddress(String address, String caret, int maxLength) {
        if (maxLength < numCoordSymbols)
            maxLength = numCoordSymbols;
        return formatAddress(address + padding.substring(address.length(), maxLength), " ", " ");
    }
    
    public static String formatAddress(@NonNull String address, String sep1, String sep2) {
        String coord = address.substring(0, numCoordSymbols);
        String dimen = address.substring(numCoordSymbols);
        int i = (numCoordSymbols + 1) / 2;
        String result = coord.substring(0, i) + sep1 + coord.substring(i);
        if (dimen.length() > 0)
            result += sep2 + dimen;
        return result;
    }
    
    public static String localAddress(@NonNull String address) {
        return address.substring(0, numCoordSymbols);
    }

}
