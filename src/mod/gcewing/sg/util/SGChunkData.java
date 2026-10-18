//------------------------------------------------------------------------------------------------
//
//   SG Craft - Extra data saved with a chunk
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.util;

import gcewing.sg.SGCraft;
import gcewing.sg.generator.NaquadahOreWorldGen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SGChunkData {

    static boolean debug = false;

    // The data for each loaded chunk, by world (dimension) and then chunk: the same chunk coordinates in two worlds are
    // two different chunks
    static final Map<Integer, Map<ChunkPos, SGChunkData>> worlds = new HashMap<>();

    // Chunks still to have ore added, by world. Ore can't be placed while a chunk loads: Forge sends ChunkDataEvent.Load
    // before the chunk counts as loaded, so anything looking up a block placed then (Sponge does, for every change)
    // loads the chunk again, which sends the event again, until the server runs out of stack.
    static final Map<Integer, Set<ChunkPos>> pendingOres = new HashMap<>();
    // How many chunks get ore added per world each tick, so a burst of loading doesn't stall a tick
    static final int oreChunksPerTick = 16;

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
        pendingOres.remove(world.provider.getDimension());
    }

    // At the end of each world tick, add ore to chunks loaded since; one unloaded in the meantime waits until it loads again
    public static void onWorldTick(WorldServer world) {
        Set<ChunkPos> pending = pendingOres.get(world.provider.getDimension());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        int done = 0;
        for (Iterator<ChunkPos> it = pending.iterator(); it.hasNext() && done < oreChunksPerTick; ) {
            ChunkPos pos = it.next();
            it.remove();
            Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.x, pos.z);
            if (chunk == null || SGCraft.naquadahOreGenerator == null || forChunk(chunk).oresGenerated) {
                if (chunk == null) {
                    NaquadahOreWorldGen.retrogenLog("world %s chunk (%d, %d): unloaded before ore could be added, so it "
                        + "waits until it loads again", world.getWorldInfo().getWorldName(), pos.x, pos.z);
                }
                continue;
            }
            SGCraft.naquadahOreGenerator.regenerate(chunk);
            done++;
        }
    }
    
    // Whether a chunk has been given ore was once saved as "gcewing.sg.oresGenerated", but older versions kept one record
    // per chunk position for every world, and read a chunk's own only when there was none yet: chunks at the same place
    // in different worlds shared one, each saving whatever the others had set. It can't be trusted in any world, so a
    // chunk without the new key is checked again (NaquadahOreWorldGen.regenerate leaves one already holding ore alone).
    static final String oresKey = "gcewing.sg.naquadahGenerated";
    static final String oldOresKey = "gcewing.sg.oresGenerated";

    public void readFromNBT(NBTTagCompound nbt) {
        oresGenerated = nbt.hasKey(oresKey) && nbt.getBoolean(oresKey);
    }

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean(oresKey, oresGenerated);
        nbt.removeTag(oldOresKey);
    }
    
    public static void onChunkLoad(ChunkDataEvent.Load e) {
        Chunk chunk = e.getChunk();
        //System.out.printf("SGChunkData.onChunkLoad: (%d, %d)\n", chunk.xPosition, chunk.zPosition);
        SGChunkData data = SGChunkData.forChunk(chunk, e.getData());
        //if (data.oresGenerated)
        //  System.out.printf("SGChunkData.onChunkLoad: Ores already added to chunk (%d, %d)\n",
        //      chunk.xPosition, chunk.zPosition);
        if (data.oresGenerated && SGCraft.addOresToExistingWorlds) {
            NaquadahOreWorldGen.retrogenLog("%s: marked as already given ore", NaquadahOreWorldGen.where(chunk));
        }
        // No generator when naquadah ore is turned off (options.enableNaquadahOre)
        if (!data.oresGenerated && SGCraft.addOresToExistingWorlds && SGCraft.naquadahOreGenerator == null) {
            NaquadahOreWorldGen.retrogenLog("%s: naquadah ore is turned off (enableNaquadahOre)", NaquadahOreWorldGen.where(chunk));
        }
        if (!data.oresGenerated && SGCraft.addOresToExistingWorlds && SGCraft.naquadahOreGenerator != null) {
            NaquadahOreWorldGen.retrogenLog("%s: queued for ore", NaquadahOreWorldGen.where(chunk));
            if (debug)
                System.out.printf("SGChunkData.onChunkLoad: Adding ores to chunk (%d, %d)\n", chunk.x, chunk.z);
            // Next world tick (see onWorldTick and pendingOres for why not now)
            pendingOres.computeIfAbsent(dimensionOf(chunk), d -> new LinkedHashSet<>()).add(new ChunkPos(chunk.x, chunk.z));
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
