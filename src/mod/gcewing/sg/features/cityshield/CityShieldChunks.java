package gcewing.sg.features.cityshield;

import gcewing.sg.SGCraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Keeps every chunk a raised city shield covers loaded, its generator's among them, with Forge chunk-loading tickets.
// Forge saves the tickets with the world and hands them back as it loads (see BaseTEChunkManager.ticketsLoaded), so a
// shield that was up comes back up with all its chunks. Kept apart from CityShieldTE, which needs MalisisCore.
public final class CityShieldChunks {

    public static final String TICKET_TYPE = "CityShield";

    // Tickets handed back as a world loaded, waiting for their generator to take them up (see adopt)
    private static final Map<World, Map<BlockPos, List<Ticket>>> waiting = new WeakHashMap<>();
    private static boolean warned = false;

    private CityShieldChunks() {}

    // The chunks within radius blocks of the generator across the ground (the shield is round from above)
    public static List<ChunkPos> covered(BlockPos pos, int radius) {
        List<ChunkPos> chunks = new ArrayList<>();
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        for (int x = (int) Math.floor((cx - radius) / 16); x <= (int) Math.floor((cx + radius) / 16); x++) {
            for (int z = (int) Math.floor((cz - radius) / 16); z <= (int) Math.floor((cz + radius) / 16); z++) {
                // The nearest point of the chunk to the generator
                double dx = Math.max(x * 16, Math.min(cx, x * 16 + 16)) - cx;
                double dz = Math.max(z * 16, Math.min(cz, z * 16 + 16)) - cz;
                if (dx * dx + dz * dz <= (double) radius * radius) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
        }
        return chunks;
    }

    // Forces the chunks a shield of the given radius covers, on as many tickets as Forge needs for them. If Forge runs
    // out of tickets (see its forgeChunkLoading.cfg), as many chunks as it allows are kept loaded.
    public static List<Ticket> force(World world, BlockPos pos, int radius) {
        List<Ticket> tickets = new ArrayList<>();
        List<ChunkPos> chunks = covered(pos, radius);
        // The generator's own chunk first, so it's kept if any is
        ChunkPos own = new ChunkPos(pos);
        chunks.remove(own);
        chunks.add(0, own);
        int next = 0;
        while (next < chunks.size()) {
            Ticket ticket = ForgeChunkManager.requestTicket(SGCraft.mod, world, Type.NORMAL);
            if (ticket == null) {
                if (!warned) {
                    warned = true;
                    System.err.printf("SGCraft: Out of chunk-loading tickets: a city shield at %s keeps %s of its %s chunks loaded."
                        + " Raise maximumTicketCount or maximumChunksPerTicket in forgeChunkLoading.cfg\n", pos, next, chunks.size());
                }
                break;
            }
            int count = Math.min(ticket.getMaxChunkListDepth(), chunks.size() - next);
            int[] coords = new int[count * 2];
            for (int i = 0; i < count; i++) {
                ChunkPos chunk = chunks.get(next + i);
                coords[2 * i] = chunk.x;
                coords[2 * i + 1] = chunk.z;
            }
            NBTTagCompound nbt = ticket.getModData();
            nbt.setString("type", TICKET_TYPE);
            nbt.setInteger("xCoord", pos.getX());
            nbt.setInteger("yCoord", pos.getY());
            nbt.setInteger("zCoord", pos.getZ());
            nbt.setIntArray("chunks", coords);
            forceChunks(ticket);
            tickets.add(ticket);
            next += count;
        }
        return tickets;
    }

    private static void forceChunks(Ticket ticket) {
        int[] coords = ticket.getModData().getIntArray("chunks");
        for (int i = 0; i + 1 < coords.length; i += 2) {
            ForgeChunkManager.forceChunk(ticket, new ChunkPos(coords[i], coords[i + 1]));
        }
    }

    public static void release(List<Ticket> tickets) {
        if (tickets != null) {
            for (Ticket ticket : tickets) {
                ForgeChunkManager.releaseTicket(ticket);
            }
        }
    }

    // A city shield ticket handed back as a world loads: its chunks are loaded again if its generator is still there,
    // and it waits for the generator to take it up; otherwise it's let go
    public static void ticketLoaded(Ticket ticket, World world) {
        NBTTagCompound nbt = ticket.getModData();
        BlockPos pos = new BlockPos(nbt.getInteger("xCoord"), nbt.getInteger("yCoord"), nbt.getInteger("zCoord"));
        if (SGCraft.cityShieldGenerator == null || world.getBlockState(pos).getBlock() != SGCraft.cityShieldGenerator) {
            ForgeChunkManager.releaseTicket(ticket);
            return;
        }
        forceChunks(ticket);
        waiting.computeIfAbsent(world, w -> new HashMap<>()).computeIfAbsent(pos, p -> new ArrayList<>()).add(ticket);
    }

    // The tickets the world handed back for the generator at pos, if any; they're the generator's now
    public static List<Ticket> adopt(World world, BlockPos pos) {
        Map<BlockPos, List<Ticket>> forWorld = waiting.get(world);
        return forWorld == null ? null : forWorld.remove(pos);
    }
}
