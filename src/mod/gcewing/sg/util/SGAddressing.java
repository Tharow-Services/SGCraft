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
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;

public class SGAddressing {

    static boolean debugAddressing = true;

    public static class AddressingError extends Exception {
        AddressingError(String s) {super(s);}
    }

    public static String symbolChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static int numSymbols = symbolChars.length();
    public static int numCoordSymbols = 7;
    public static int numDimensionSymbols = 2;
    
    public static int maxAddressLength = numCoordSymbols + numDimensionSymbols;
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
    static long mc = coordRange + 2; // == 2 * maxCoord + 3;
    static long pc = 93563;   //  (pc * qc) % mc == 1
    static long qc = 153742;
//     final static long md = dimensionRange + 2;
    static long md = dimensionRange + 1;
    static long pd = 953;  //  (pd * qd) % md == 1
    static long qd = 788;
     // Historical error
    static long mdOld = dimensionRange + 2;
    static long qdOld = 459;

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

    public static String addressForLocation(SGLocation loc) throws AddressingError {
        if (debugAddressing)
            System.out.printf("SGAddressing.addressForLocation: coord range = %d to %d\n",
                minCoord, maxCoord);
        int chunkx = loc.pos.getX() >> 4;
        int chunkz = loc.pos.getZ() >> 4;
        int dim = loc.dimension;
        // START OF MAPPER //
        SGAddress addr=SGAddressMap.getAddress(dim,chunkx,chunkz);
        dim=addr.getD();chunkx=addr.getCX();chunkz=addr.getCZ();
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
        int[] xz = uninterleaveCoords(c);
        int chunkX = minCoord + hash(xz[0], qc, mc);
        int chunkZ = minCoord + hash(xz[1], qc, mc);
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
            SGAddress addr=SGAddressMap.getAddress(dm,chunkX,chunkZ);
            dm=addr.getD();chunkX=addr.getCX();chunkZ=addr.getCZ();
            // End of Mapper //
            if (debugAddressing)
                System.out.printf("SGAddressing.findAddressedStargate: d = %s dimension = %s\n",
                    d, dm);
            te = getBaseTE(chunkX, chunkZ, dm, pending);
            if (te == null) {
                // Try old interpretation of dimension symbols
                int dimOld = minDirectDimension + hash(d, qdOld, mdOld);
                if (debugAddressing)
                    System.out.printf("SGAddressing.findAddressedStargate: Trying dimension = %s\n",
                        dimOld);
                te = getBaseTE(chunkX, chunkZ, dimOld, pending);
            }
        }
        else {
            // Relative address
            int dimension = fromWorld.provider.getDimension();
            // Start of Mapper //
            SGAddress addr=SGAddressMap.getAddress(dimension,chunkX,chunkZ);
            dimension=addr.getD();chunkX=addr.getCX();chunkZ=addr.getCZ();
            // End of Mapper //
            te = getBaseTE(chunkX, chunkZ, dimension, pending);
        }
        return te;
    }

    public static void Location(String address, ICommandSender sender, boolean b) throws SGAddressing.AddressingError {
        sender.sendMessage(new TextComponentString("SGAddressing.findAddressedStargate: "+address));
        validateAddress(address);
        String csyms = address.substring(0, numCoordSymbols);
        long c = longFromSymbols(csyms);
        int[] xz = uninterleaveCoords(c);
        int chunkX = minCoord + hash(xz[0], qc, mc);
        int chunkZ = minCoord + hash(xz[1], qc, mc);

        sender.sendMessage(new TextComponentString("SGAddressing.findAddressedStargate: c = "+c+" chunk = ("+chunkX+","+chunkZ+")\n"));
        if (address.length() == maxAddressLength) {
            // Absolute address
            String dsyms = address.substring(numCoordSymbols);
            int d = intFromSymbols(dsyms);
            int dp = hash(d, qd, md);
            int di = unpermuteDimension(c, dp);
            Integer dm = SGDimensionMap.dimensionForIndex(di);
            // Start of Mapper //
            SGAddress addr=SGAddressMap.getAddress(dm,chunkX,chunkZ);
            dm=addr.getD();chunkX=addr.getCX();chunkZ=addr.getCZ();
            // End of Mapper //
            sender.sendMessage(new TextComponentString("SGAddressing.findAddressedStargate: d = "+d+" dimension = "+dm+"\n"));
            sender.sendMessage(new TextComponentString("Address Belongs to[Dim: "+dm+", Chunk X: "+chunkX+", Chunk Z: "+chunkZ+"]"));
            if (b) {sender.sendMessage(new TextComponentString("Address Belongs to[Dim: "+dm+", X: "+((chunkX*16)+8)+", Z: "+((chunkZ*16)+8)+"]"));}
            int dimOld = minDirectDimension + hash(d, qdOld, mdOld);
            sender.sendMessage(new TextComponentString("Old Address Belongs to[Dim: "+dimOld+", Chunk X: "+chunkX+", Chunk Z: "+chunkZ+"]"));
            if (b) {sender.sendMessage(new TextComponentString("Old Address Belongs to[Dim: "+dimOld+", X: "+((chunkX*16)+8)+", Z: "+((chunkZ*16)+8)+"]"));}

        }
        else {
            // Relative address
            int dimension = sender.getEntityWorld().provider.getDimension();
            // Start of Mapper //
            SGAddress addr=SGAddressMap.getAddress(dimension,chunkX,chunkZ);
            dimension=addr.getD();chunkX=addr.getCX();chunkZ=addr.getCZ();
            // End of Mapper //
            sender.sendMessage(new TextComponentString("Local Address Belongs to[Dim: "+dimension+", Chunk X: "+chunkX+", Chunk Z: "+chunkZ+"]"));
            if (b) {sender.sendMessage(new TextComponentString("Local Address Belongs to[Dim: "+dimension+", X: "+((chunkX*16)+8)+", Z: "+((chunkZ*16)+8)+"]"));}
        }
    }
    
    protected static SGBaseTE getBaseTE(int chunkX, int chunkZ, int dimension, boolean pending) {
        World toWorld = getWorld(dimension);
        if (toWorld != null) {
            Chunk chunk = toWorld.getChunk(chunkX, chunkZ);
            if (chunk != null)
                for (Object te : chunk.getTileEntityMap().values()) {
                    if (te instanceof SGBaseTE) {
                        ((SGBaseTE) te).isPending = pending;
                        return (SGBaseTE) te;
                    }
                }
        }
        return null;
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
    
    protected static int[] uninterleaveCoords(long c) {
        int p6 = 1;
        int[] xy = {0, 0};
        while (c > 0) {
            xy[0] += p6 * (c % 6); c /= 6;
            xy[1] += p6 * (c % 6); c /= 6;
            p6 *= 6;
        }
        return xy;
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
    
    public static String formatAddress(String address, String sep1, String sep2) {
        String coord = address.substring(0, numCoordSymbols);
        String dimen = address.substring(numCoordSymbols);
        int i = (numCoordSymbols + 1) / 2;
        String result = coord.substring(0, i) + sep1 + coord.substring(i);
        if (dimen.length() > 0)
            result += sep2 + dimen;
        return result;
    }
    
    public static String localAddress(String address) {
        return address.substring(0, numCoordSymbols);
    }

}
