//------------------------------------------------------------------------------------------------
//
//   Greg's Mod Base - Chunk manager for tile entities
//
//------------------------------------------------------------------------------------------------

package gcewing.sg;

import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.features.cityshield.CityShieldChunks;
import gcewing.sg.tileentity.SGInterfaceTE;
import gcewing.sg.tileentity.SGRingTE;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BaseTEChunkManager implements ForgeChunkManager.LoadingCallback {

    public boolean debug = false;
    BaseMod base;
    
    public BaseTEChunkManager(BaseMod mod) {
        base = mod;
        ForgeChunkManager.setForcedChunkLoadingCallback(mod, this);
        if (debug)
            System.out.printf("%s: BaseTEChunkManager: Chunk loading callback installed\n",
                base.modPackage);
    }
    
    protected Ticket newTicket(World world) {
        if (debug)
            System.out.printf("%s: BaseTEChunkManager.newTicket for %s\n", base.modPackage, world);
        return ForgeChunkManager.requestTicket(base, world, Type.NORMAL);
    }
    
    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        if (debug)
            System.out.printf("%s: BaseTEChunkManager.ticketsLoaded for %s\n", base.modPackage, world);
        for (Ticket ticket : tickets) {
            NBTTagCompound nbt = ticket.getModData();
            if (nbt != null)
                if (nbt.getString("type").equals("TileEntity")) {
                    int x = nbt.getInteger("xCoord");
                    int y = nbt.getInteger("yCoord");
                    int z = nbt.getInteger("zCoord");
                    TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
                    if (debug)
                        System.out.printf("%s: BaseTEChunkManager.ticketsLoaded: Ticket for %s at (%d, %d, %d)\n",
                            base.modPackage, te, x, y, z);
                    if (!(te instanceof BaseTileEntity && reinstateChunkTicket((BaseTileEntity)te, ticket))) {
                        if (debug)
                            System.out.printf("%s: BaseTEChunkManager.ticketsLoaded: : Unable to reinstate ticket\n", base.modPackage);
                        ForgeChunkManager.releaseTicket(ticket);
                    }
                }
                else if (nbt.getString("type").equals(CityShieldChunks.TICKET_TYPE)) {
                    CityShieldChunks.ticketLoaded(ticket, world);
                }
        }
    }
    
    public void setForcedChunkRange(BaseTileEntity te, int minX, int minZ, int maxX, int maxZ) {
        if (debug) {
            System.out.println("TE: " + te.toString());
        }

        if (te instanceof SGBaseTE) {
            te.releaseChunkTicket();
            Ticket ticket = getChunkTicket(te);
            if (ticket != null) {
                BlockPos pos = te.getPos();
                NBTTagCompound nbt = ticket.getModData();
                nbt.setString("type", "TileEntity");
                nbt.setInteger("xCoord", pos.getX());
                nbt.setInteger("yCoord", pos.getY());
                nbt.setInteger("zCoord", pos.getZ());
                nbt.setInteger("rangeMinX", minX);
                nbt.setInteger("rangeMinZ", minZ);
                nbt.setInteger("rangeMaxX", maxX);
                nbt.setInteger("rangeMaxZ", maxZ);
                forceChunkRangeOnTicket(te, ticket);
            } else {
                System.out.print("SGCraft: unable to create chunk ticket; this will likely cause issues with remote gates prematurely unloading at: " + te.getPos() + " in world: " + te.getWorld().getWorldInfo().getWorldName());
            }
        }
    }
    
    public void clearForcedChunkRange(BaseTileEntity te) {
        te.releaseChunkTicket();
    }

    protected void forceChunkRangeOnTicket(BaseTileEntity te, Ticket ticket) {
        NBTTagCompound nbt = ticket.getModData();
        int minX = nbt.getInteger("rangeMinX");
        int minZ = nbt.getInteger("rangeMinZ");
        int maxX = nbt.getInteger("rangeMaxX");
        int maxZ = nbt.getInteger("rangeMaxZ");
        //if (debug)
            //System.out.printf("BaseChunkLoadingTE: Forcing range (%s,%s)-(%s,%s) in dimension %s\n", minX, minZ, maxX, maxZ, te.getWorld().provider.getDimension());
        BlockPos pos = te.getPos();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (ticket.world == null) {
            if (debug) {
                System.out.println("World is null in chunk ticket!!!");
            }
            return;
        }
        // A ticket holds only so many chunks (forgeChunkLoading.cfg, 25 by default), and Forge lets go of the first
        // forced to make room for more. So force the farthest first and the tile entity's own chunk last, and no more
        // than the ticket holds: if the range is too big, the outer chunks go, never the one it stands in.
        List<ChunkPos> chunks = new ArrayList<>();
        for (int i = minX; i <= maxX; i++)
            for (int j = minZ; j <= maxZ; j++)
                chunks.add(new ChunkPos(chunkX + i, chunkZ + j));
        chunks.sort(Comparator.comparingInt((ChunkPos c) -> -distanceSq(c, chunkX, chunkZ)));
        int depth = ticket.getChunkListDepth();
        if (depth > 0 && chunks.size() > depth) {
            System.out.printf("SGCraft: %s at %s wants %d chunks loaded, but a chunk ticket holds only %d; keeping the nearest\n",
                te.getClass().getSimpleName(), pos, chunks.size(), depth);
            chunks = chunks.subList(chunks.size() - depth, chunks.size());
        }
        for (ChunkPos chunk : chunks) {
            if (debug) {
                System.out.println("Forcing chunk " + chunk + " for: " + te + " with ticket: " + ticket + " (" + ticket.getChunkList().size() + " so far)");
            }
            ForgeChunkManager.forceChunk(ticket, chunk);
        }
    }

    private static int distanceSq(ChunkPos c, int chunkX, int chunkZ) {
        int dx = c.x - chunkX, dz = c.z - chunkZ;
        return dx * dx + dz * dz;
    }

    protected Ticket getChunkTicket(BaseTileEntity te) {
        if (te.chunkTicket == null) {
            if (debug) {
                System.out.println("Creating new chunk ticket for: " + te.toString());
            }
            te.chunkTicket = newTicket(te.getWorld());

            return te.chunkTicket;
        } else {
            if (debug) {
                System.out.println("Chunk ticket was not null; returning existing ticket for: " + te.toString());
            }
            return te.chunkTicket;
        }
    }
    
    public boolean reinstateChunkTicket(BaseTileEntity te, Ticket ticket) {
        if (te.chunkTicket == null) {
            if (debug)
                System.out.printf("BaseChunkLoadingTE: Reinstating chunk ticket %s\n", ticket);
            te.chunkTicket = ticket;
            forceChunkRangeOnTicket(te, ticket);
            // Only while it needs them: a gate that was idle (or had gone idle) lets them go, where they used to stay
            // loaded until its next dial
            if (te instanceof gcewing.sg.tileentity.SGBaseTE) {
                ((gcewing.sg.tileentity.SGBaseTE) te).updateChunkLoadingStatus();
            }
            return true;
        }
        else
            return false;
    }
    
    public void dumpChunkLoadingState(BaseTileEntity te, String label) {
        System.out.printf("%s: Chunk loading state:\n", label);
        System.out.printf("Chunk ticket = %s\n", te.chunkTicket);
        if (te.chunkTicket != null) {
            System.out.printf("Loaded chunks:");
            for (Object item : te.chunkTicket.getChunkList()) {
                ChunkPos coords = (ChunkPos)item;
                System.out.printf(" (%d,%d)", coords.x, coords.z);
            }
            System.out.printf("\n");
        }
    }
}
