//------------------------------------------------------------------------------------------------
//
//   SG Craft - Naquadah ore world generation
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.generator;

import java.util.*;

import gcewing.sg.BaseConfiguration;
import gcewing.sg.util.SGChunkData;
import gcewing.sg.SGCraft;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.config.Property;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.*;

public class NaquadahOreWorldGen implements IWorldGenerator {

    static int genUnderLavaOdds = 1;
    static int maxNodesUnderLava = 16;
    static int genIsolatedOdds = 1;
    static int maxIsolatedNodes = 4;

    static boolean debugLava = false;
    static boolean debugRandom = false;
    static int debugLevel = 0;

    // Every world but Nether and End ones, by default
    static final String defaultWorlds = "*, !the_nether, !the_end";
    static String dimensionIDs = defaultWorlds;

    Random random;
    World world;
    Chunk chunk;
    int x0, z0;

    Block stone = Blocks.STONE;
    Block lava = Blocks.LAVA;
    IBlockState naquadah = SGCraft.naquadahOre.getDefaultState();
    
    public static void configure(BaseConfiguration cfg) {
        genUnderLavaOdds = cfg.getInteger("naquadah", "genUnderLavaOdds", genUnderLavaOdds);
        maxNodesUnderLava = cfg.getInteger("naquadah", "maxNodesUnderLava", maxNodesUnderLava);
        genIsolatedOdds = cfg.getInteger("naquadah", "genIsolatedOdds", genIsolatedOdds);
        maxIsolatedNodes = cfg.getInteger("naquadah", "maxIsolatedNodes", maxIsolatedNodes);
        debugLava = cfg.getBoolean("naquadah", "debugLava", debugLava);
        debugRandom = cfg.getBoolean("naquadah", "debugRandom", debugRandom);
        debugLevel = cfg.getInteger("naquadah", "debugLevel", debugLevel);

        // The worlds it generates in were "dimensionIDs", which defaulted to dimension 0 alone, and adding ore to chunks
        // made before it was "options.addOresToExistingWorlds", off by default. They're now "worlds" and
        // "addToExistingChunks", so the new defaults (every world but the Nether and End, and on) reach config files that
        // have the old ones written in; a list of worlds someone chose is kept.
        String carried = defaultWorlds;
        if (cfg.hasKey("naquadah", "dimensionIDs")) {
            String old = cfg.getString("naquadah", "dimensionIDs", "0").trim();
            if (!old.equals("0")) {
                carried = old;
            }
            cfg.removeKey("naquadah", "dimensionIDs");
        }
        cfg.removeKey("options", "addOresToExistingWorlds");
        Property worlds = cfg.get("naquadah", "worlds", carried);
        worlds.setComment("Worlds naquadah ore generates in, separated by commas: dimension numbers, world names, or kinds of "
            + "world (overworld, the_nether, the_end); * for every world; ! in front leaves one out");
        dimensionIDs = worlds.getString();
        dimensions = parseDimensions(dimensionIDs);
        Property existing = cfg.get("naquadah", "addToExistingChunks", true);
        existing.setComment("Add naquadah ore to chunks made before (in the worlds above), once each, as they load");
        SGCraft.addOresToExistingWorlds = existing.getBoolean(true);
    }

    // The worlds it generates in, from the "worlds" setting: each a dimension number, a world's name (each Sponge world
    // has its own), or a kind of dimension (overworld, the_nether, the_end), separated by commas; or * for every world.
    // An entry with ! in front leaves those worlds out, whatever else names them.
    static String[] dimensions = parseDimensions(dimensionIDs);

    static String[] parseDimensions(String list) {
        String[] entries = list.split(",");
        for (int i = 0; i < entries.length; i++) {
            entries[i] = entries[i].trim();
        }
        return entries;
    }

    static boolean generatesIn(World world) {
        int dimension = world.provider.getDimension();
        String worldName = world.getWorldInfo().getWorldName();
        String kind = world.provider.getDimensionType().getName();
        boolean named = false;
        for (String entry : dimensions) {
            boolean leaveOut = entry.startsWith("!");
            String name = leaveOut ? entry.substring(1).trim() : entry;
            if (name.isEmpty() || !names(name, dimension, worldName, kind)) {
                continue;
            }
            if (leaveOut) {
                return false;
            }
            named = true;
        }
        return named;
    }

    private static boolean names(String entry, int dimension, String worldName, String kind) {
        if (entry.equals("*") || entry.equalsIgnoreCase(worldName) || entry.equalsIgnoreCase(kind)) {
            return true;
        }
        try {
            return Integer.parseInt(entry) == dimension;
        } catch (NumberFormatException ex) {
            return false; // a name, not a number
        }
    }

    public void generate(Random random, int chunkX, int chunkZ, World world,
        IChunkGenerator chunkGenerator, IChunkProvider chunkProvider)
    {
        //System.out.printf("NaquadahOreWorldGen: chunk (%d, %d)\n", chunkX, chunkZ);
        this.random = random;
        this.world = world;
        x0 = chunkX * 16;
        z0 = chunkZ * 16;
        chunk = world.getChunk(chunkX, chunkZ);
        if (generatesIn(world)) {
            generateChunk();
        }
    }
    
    public void regenerate(Chunk chunk) {
        this.chunk = chunk;
        world = chunk.getWorld();
        int chunkX = chunk.x;
        int chunkZ = chunk.z;
        long worldSeed = world.getSeed();
        random = new Random(worldSeed);
        long xSeed = random.nextLong() >> 2 + 1L;
        long zSeed = random.nextLong() >> 2 + 1L;
        random.setSeed((xSeed * chunkX + zSeed * chunkZ) ^ worldSeed);
        x0 = chunkX * 16;
        z0 = chunkZ * 16;
        if (!generatesIn(world)) {
            return;
        }
        // A chunk already holding naquadah ore had it when it was made: its record of that may have been lost (chunks of
        // different worlds once shared one), and it shouldn't get a second lot
        if (hasNaquadah(chunk)) {
            SGChunkData.forChunk(chunk).oresGenerated = true;
            return;
        }
        generateChunk();
    }

    // Whether there's naquadah ore anywhere it generates (below y 64)
    private boolean hasNaquadah(Chunk chunk) {
        for (ExtendedBlockStorage storage : chunk.getBlockStorageArray()) {
            if (storage == null || storage == Chunk.NULL_BLOCK_STORAGE || storage.isEmpty() || storage.getYLocation() >= 64) {
                continue;
            }
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (storage.get(x, y, z).getBlock() == SGCraft.naquadahOre) {
                            return true;
                        }
        }
        return false;
    }

    Block getBlock(int x, int y, int z) {
        return getBlock(new BlockPos(x, y, z));
    }
    
    Block getBlock(BlockPos pos) {
        return chunk.getBlockState(pos).getBlock();
    }
    
    boolean isSolidBlock(int x, int y, int z) {
        return isSolidBlock(new BlockPos(x, y, z));
    }
    
    boolean isSolidBlock(BlockPos pos) {
        IBlockState state = chunk.getBlockState(pos);
        return state.getMaterial().isSolid();
    }
    
    void setBlockState(BlockPos pos, IBlockState state) {
        chunk.setBlockState(pos, state);
    }
    
    void generateNode(IBlockState id, int x, int y, int z, int sx, int sy, int sz) {
        int dx = random.nextInt(sx);
        int dy = random.nextInt(sy);
        int dz = random.nextInt(sz);
        int h = world.getHeight();
        if (debugRandom && debugLevel >= 1)
            System.out.printf("NaquadahOreWorldGen: %d x %d x %d node at (%d, %d, %d)\n",
                dx + 1, dy + 1, dz + 1, x0 + x, y, z0 + z);
        for (int i = x; i <= x + dx; i++)
            for (int j = y; j <= y + dy; j++)
                for (int k = z; k <= z + dz; k++)
                    if (i < 16 && j < h && k < 16) {
                        BlockPos pos = new BlockPos(i, j, k);
                        if (isSolidBlock(pos)) {
                            if (debugRandom && debugLevel >= 2)
                                System.out.printf("NaquadahOreWorldGen: block at (%d, %d, %d)\n",
                                    x0 + i, j, z0 + k);
                            setBlockState(pos, id);
                        }
                    }
    }
    
    boolean odds(int n) {
        return random.nextInt(n) == 0;
    }

    void generateChunk() {
        SGChunkData.forChunk(chunk).oresGenerated = true;
        // Odds or a count of 0 or less turn that kind off, rather than crashing on nextInt(0)
        if (genUnderLavaOdds > 0 && maxNodesUnderLava > 0 && odds(genUnderLavaOdds)) {
            int n = random.nextInt(maxNodesUnderLava) + 1;
            for (int i = 0; i < n; i++) {
                int x = random.nextInt(16);
                int z = random.nextInt(16);
                for (int y = 1; y < 64; y++)
                    if (isSolidBlock(x, y, z) && getBlock(x, y+1, z) == lava) {
                        if (debugLava)
                            System.out.printf("NaquadahOreWorldGen: generating under lava at (%d, %d, %d)\n", x0+x, y, z0+z);
                        generateNode(naquadah, x, y, z, 3, 1, 3);
                }
            }
        }
        if (genIsolatedOdds > 0 && maxIsolatedNodes > 0 && odds(genIsolatedOdds)) {
            int n = random.nextInt(maxIsolatedNodes) + 1;
            for (int i = 0; i < n; i++) {
                int x = random.nextInt(16);
                int y = random.nextInt(16) + 16;
                int z = random.nextInt(16);
                if (isSolidBlock(x, y, z)) {
                    if (debugRandom)
                        System.out.printf("NaquadahOreWorldGen: generating randomly at (%d, %d, %d)\n", x0+x, y, z0+z);
                    generateNode(naquadah, x, y, z, 2, 2, 2);
                }
            }
        }
    }
}
