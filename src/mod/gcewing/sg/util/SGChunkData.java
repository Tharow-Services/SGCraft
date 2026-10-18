//------------------------------------------------------------------------------------------------
//
//   SG Craft - Extra data saved with a chunk
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.util;

import gcewing.sg.SGCraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;

import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class SGChunkData {

    static boolean debug = false;

    // The data for each loaded chunk, by world (dimension) and then chunk: the same chunk coordinates in two worlds are
    // two different chunks
    static final Map<Integer, Map<ChunkPos, SGChunkData>> worlds = new HashMap<>();

    public boolean oresGenerated;
    // The chunk has been unloaded: its data goes once it has been saved
    boolean unloaded;

    public static SGChunkData forChunk(Chunk chunk) {
        return forChunk(chunk, null);
    }

    // With nbt, the chunk has just been loaded: what was saved with it is what counts
    public static SGChunkData forChunk(Chunk chunk, NBTTagCompound nbt) {
        Map<ChunkPos, SGChunkData> map = worlds.computeIfAbsent(dimensionOf(chunk), d -> new HashMap<>());
        ChunkPos coords = new ChunkPos(chunk.x, chunk.z);
        SGChunkData data = map.get(coords);
        if (data == null || nbt != null) {
            data = new SGChunkData();
            if (nbt != null) {
                data.readFromNBT(nbt);
            }
            map.put(coords, data);
        }
        return data;
    }

    private static int dimensionOf(Chunk chunk) {
        return chunk.getWorld().provider.getDimension();
    }

    // A chunk unloading is saved just after, which then lets its data go (see onChunkSave). Were it saved first, its data
    // would stay until it loads again (which replaces it) or its world unloads.
    public static void onChunkUnload(Chunk chunk) {
        Map<ChunkPos, SGChunkData> map = worlds.get(dimensionOf(chunk));
        SGChunkData data = map != null ? map.get(new ChunkPos(chunk.x, chunk.z)) : null;
        if (data != null) {
            data.unloaded = true;
        }
    }

    // A world unloading takes all its chunks' data with it
    public static void onWorldUnload(World world) {
        worlds.remove(world.provider.getDimension());
    }
    
    public void readFromNBT(NBTTagCompound nbt) {
        oresGenerated = nbt.getBoolean("gcewing.sg.oresGenerated");
    }
    
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("gcewing.sg.oresGenerated", oresGenerated);
    }
    
    public static void onChunkLoad(ChunkDataEvent.Load e) {
        Chunk chunk = e.getChunk();
        //System.out.printf("SGChunkData.onChunkLoad: (%d, %d)\n", chunk.xPosition, chunk.zPosition);
        SGChunkData data = SGChunkData.forChunk(chunk, e.getData());
        //if (data.oresGenerated)
        //  System.out.printf("SGChunkData.onChunkLoad: Ores already added to chunk (%d, %d)\n",
        //      chunk.xPosition, chunk.zPosition);
        // No generator when naquadah ore is turned off (options.enableNaquadahOre)
        if (!data.oresGenerated && SGCraft.addOresToExistingWorlds && SGCraft.naquadahOreGenerator != null) {
            if (debug)
                System.out.printf("SGChunkData.onChunkLoad: Adding ores to chunk (%d, %d)\n", chunk.x, chunk.z);
            SGCraft.naquadahOreGenerator.regenerate(chunk);
        }
    }
    
    public static void onChunkSave(ChunkDataEvent.Save e) {
        Chunk chunk = e.getChunk();
        //System.out.printf("SGChunkData.onChunkLoad: (%d, %d)\n", chunk.xPosition, chunk.zPosition);
        SGChunkData data = SGChunkData.forChunk(chunk);
        data.writeToNBT(e.getData());
        if (data.unloaded) {
            Map<ChunkPos, SGChunkData> map = worlds.get(dimensionOf(chunk));
            if (map != null) {
                map.remove(new ChunkPos(chunk.x, chunk.z));
            }
        }
    }

}
