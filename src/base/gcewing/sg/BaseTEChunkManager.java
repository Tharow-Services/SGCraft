//------------------------------------------------------------------------------------------------
//
//   Greg's Mod Base - Chunk manager for tile entities
//
//------------------------------------------------------------------------------------------------

package gcewing.sg;

import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
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
        for (int i = minX; i <= maxX; i++)
            for (int j = minZ; j <= maxZ; j++) {
                int x = chunkX + i, z = chunkZ + j;
                if (debug) {
                    System.out.println("Created chunk ticket at: " + x + "/" + z + " for: " + te + " with ticket: " + ticket);
                }
                if (ticket.world == null) {
                    if (debug) {
                        System.out.println("World is null in chunk ticket!!!");
                    }
                    return;
                }
                if (debug) {
                    System.out.println("Ticket World: " + ticket.world);
                    System.out.println("Ticket Size: " + ticket.getChunkList().size());
                }
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(x, z));
            }
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
