package gcewing.sg.features.obelisk;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;
import gcewing.sg.SGCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// The bottom half of an Asgard obelisk: which obelisk it's linked to, in which dimension
public class AsgardObeliskTE extends TileEntity {

    public boolean linked;
    public int linkDimension;
    public BlockPos linkPos = BlockPos.ORIGIN;
    // ---- Who may use it, and configure it ----

    // Whoever placed it: they may always use and configure it
    public UUID owner;
    public String ownerName = "";
    // Whether anyone may use it (the default), save those listed as denied; or only its owner and those listed as allowed
    public boolean allowEveryone = true;
    public final List<ObeliskAccess> access = new ArrayList<>();

    public ObeliskAccess accessFor(EntityPlayer player) {
        for (ObeliskAccess entry : access) {
            if (entry.uuid.equals(player.getUniqueID())) {
                return entry;
            }
        }
        return null;
    }

    public boolean isOwner(EntityPlayer player) {
        return owner != null && owner.equals(player.getUniqueID());
    }

    // A server operator, or one with the permission system's sgcraft.admin, may use and configure any obelisk
    public static boolean isServerAdmin(EntityPlayer player) {
        if (SGCraft.hasPermissionSystem() && SGCraft.hasPermission(player, "sgcraft.admin")) {
            return true;
        }
        MinecraftServer server = player.getServer();
        return server != null && server.getPlayerList().canSendCommands(player.getGameProfile());
    }

    public boolean mayUse(EntityPlayer player) {
        if (isOwner(player) || isServerAdmin(player)) {
            return true;
        }
        ObeliskAccess entry = accessFor(player);
        return allowEveryone ? entry == null || entry.use : entry != null && entry.use;
    }

    // One placed before it had an owner may be configured by anyone (with a Configurator)
    public boolean mayConfigure(EntityPlayer player) {
        if (owner == null || isOwner(player) || isServerAdmin(player)) {
            return true;
        }
        ObeliskAccess entry = accessFor(player);
        return entry != null && entry.admin;
    }

    // When (world time) it last started beaming someone, for its gem to shine; sent to nearby clients
    public long beamStart = -1000;

    public void startBeam() {
        beamStart = world.getTotalWorldTime();
        markDirty();
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    // The beam was called off: the gem goes dark
    public void stopBeam() {
        beamStart = -1000;
        markDirty();
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    // How long (ticks) before the beam its gem starts lighting up beyond the beam's own wait: 0.75 seconds
    private static final double shineLead = 15;

    // How brightly (0 to 1) its gem shines now: lit from the moment it's touched (as if 0.75 seconds further on), building
    // up to its brightest as the beam comes, then dying away
    public float shine(float partialTicks) {
        double ticks = world.getTotalWorldTime() + partialTicks - beamStart;
        double t = ticks / AsgardObelisks.beamDelayTicks;
        if (ticks < 0 || t > 1.6) {
            return 0;
        }
        if (t > 1) {
            return (float) (1 - (t - 1) / 0.6);
        }
        double u = (ticks + shineLead) / (AsgardObelisks.beamDelayTicks + shineLead);
        return (float) (u * u);
    }

    public void linkTo(int dimension, BlockPos pos) {
        linked = true;
        linkDimension = dimension;
        linkPos = pos;
        markDirty();
    }

    public void unlink() {
        linked = false;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        linked = nbt.getBoolean("linked");
        linkDimension = nbt.getInteger("linkDimension");
        linkPos = new BlockPos(nbt.getInteger("linkX"), nbt.getInteger("linkY"), nbt.getInteger("linkZ"));
        beamStart = nbt.hasKey("beamStart") ? nbt.getLong("beamStart") : -1000;
        owner = nbt.hasUniqueId("owner") ? nbt.getUniqueId("owner") : null;
        ownerName = nbt.getString("ownerName");
        allowEveryone = !nbt.hasKey("allowEveryone") || nbt.getBoolean("allowEveryone");
        access.clear();
        NBTTagList list = nbt.getTagList("access", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            access.add(ObeliskAccess.fromNBT(list.getCompoundTagAt(i)));
        }
    }

    // Sent to clients nearby, so they know when its gem should shine; who may use it is the server's business
    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound nbt = writeToNBT(new NBTTagCompound());
        nbt.removeTag("access");
        nbt.removeTag("owner");
        nbt.removeTag("ownerMost");
        nbt.removeTag("ownerLeast");
        return nbt;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    // Big enough to take in its top half, where the gem is, and the gem's shine
    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos.add(-1, 0, -1), pos.add(2, 2, 2));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("linked", linked);
        nbt.setInteger("linkDimension", linkDimension);
        nbt.setInteger("linkX", linkPos.getX());
        nbt.setInteger("linkY", linkPos.getY());
        nbt.setInteger("linkZ", linkPos.getZ());
        nbt.setLong("beamStart", beamStart);
        if (owner != null) {
            nbt.setUniqueId("owner", owner);
        }
        nbt.setString("ownerName", ownerName);
        nbt.setBoolean("allowEveryone", allowEveryone);
        NBTTagList list = new NBTTagList();
        for (ObeliskAccess entry : access) {
            list.appendTag(entry.toNBT());
        }
        nbt.setTag("access", list);
        return nbt;
    }
}
