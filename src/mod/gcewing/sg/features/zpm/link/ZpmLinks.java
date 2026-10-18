package gcewing.sg.features.zpm.link;

import static gcewing.sg.tileentity.SGBaseTE.sendBasicMsg;
import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;

import gcewing.sg.SGCraft;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.tileentity.SGRingTE;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Linking ZPM devices to stargates, as Asgard obelisks are linked: sneak and right-click a ZPM console (or, with IC2, an
// interface cart or hub), then any block of a stargate in the same world, and the gate draws on the device wherever it
// stands, however big the gate. Clicking a device with the Configurator shows what it's linked to (ZpmLinkScreen).
public final class ZpmLinks {

    // Where the device each player started linking is (sneak-clicked), and when, to link with the gate they click next.
    // Kept by place, not the device itself, which may be unloaded and loaded again before then.
    private static final class LinkStart {
        final int dimension;
        final BlockPos pos;
        final long startedAt; // System.currentTimeMillis

        LinkStart(World world, BlockPos pos) {
            this.dimension = world.provider.getDimension();
            this.pos = pos.toImmutable();
            this.startedAt = System.currentTimeMillis();
        }

        boolean is(World world, BlockPos pos) {
            return dimension == world.provider.getDimension() && this.pos.equals(pos);
        }
    }

    // A gate a player has just linked a device to, and when: holding the button down repeats the click every few ticks,
    // and the repeats shouldn't open the gate's screen
    private static final class LinkDone {
        final BlockPos gatePos;
        final long at;

        LinkDone(BlockPos gatePos) {
            this.gatePos = gatePos;
            this.at = System.currentTimeMillis();
        }
    }

    private static final Map<UUID, LinkStart> linking = new HashMap<>();
    private static final Map<UUID, LinkDone> justLinked = new HashMap<>();

    // A held button's repeated clicks come within this long (ms) of the first, and are ignored; a link not finished
    // within the time limit is forgotten, so a gate clicked long after isn't linked by surprise
    private static final long repeatMillis = 500, linkTimeoutMillis = 60000;

    private ZpmLinks() {}

    // Before SGCraft.onRightClickBlock, which would take a click on a device in front of a gate's drawn ring for the gate
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        UUID id = player.getUniqueID();
        BlockPos pos = event.getPos();
        TileEntity te = world.getTileEntity(pos);
        boolean configurator = SGCraft.configurator != null && player.getHeldItemMainhand().getItem() == SGCraft.configurator;
        long now = System.currentTimeMillis();

        if (te instanceof ILinkableZpm) {
            if (configurator && !player.isSneaking()) {
                ZpmLinkNetworkHandler.openScreen(player, pos); // what it's linked to, and to unlink it
                handled(event);
            } else if (player.isSneaking()) {
                startLinking(player, world, pos, now);
                handled(event);
            }
            return;
        }
        if ((te instanceof SGBaseTE || te instanceof SGRingTE) && gateClicked(player, gateAt(world, pos))) {
            handled(event);
        }
    }

    // A player clicked a gate (gate is its base, or null if the block isn't part of a whole gate). While a device waits
    // to be linked, that links the two, sneaking or not, and doesn't open the gate's screen; nor do the repeats of a
    // button held down just after. Whether it was taken for that. Clicks on a gate's drawn ring, beyond its blocks, come
    // here too (see GateUtil.activateGate).
    public static boolean gateClicked(EntityPlayer player, SGBaseTE gate) {
        if (player.world.isRemote) {
            return false;
        }
        UUID id = player.getUniqueID();
        long now = System.currentTimeMillis();
        LinkStart start = linking.get(id);
        if (start != null && now - start.startedAt > linkTimeoutMillis) {
            linking.remove(id);
            start = null;
        }
        if (start != null) {
            linking.remove(id);
            if (gate == null) {
                sendErrorMsg(player, "zpmLinkNoGate");
            } else if (finishLinking(player, start, gate)) {
                justLinked.put(id, new LinkDone(gate.getPos()));
            }
            return true;
        }
        // The button still held after linking: its repeats aren't meant for the gate's screen
        LinkDone done = justLinked.get(id);
        if (done != null) {
            if (now - done.at > repeatMillis) {
                justLinked.remove(id);
            } else if (gate != null && gate.getPos().equals(done.gatePos)) {
                return true;
            }
        }
        return false;
    }

    // Done with: the block's own use (its screen, say) and the item's are skipped
    private static void handled(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    // Sneak-clicking a device starts linking it; sneak-clicking it again (not just holding the button) cancels
    private static void startLinking(EntityPlayer player, World world, BlockPos pos, long now) {
        LinkStart start = linking.get(player.getUniqueID());
        if (start != null && start.is(world, pos)) {
            if (now - start.startedAt <= repeatMillis) {
                return; // the button held down, not a second click
            }
            linking.remove(player.getUniqueID());
            sendBasicMsg(player, "zpmLinkCancelled");
            return;
        }
        linking.put(player.getUniqueID(), new LinkStart(world, pos));
        sendBasicMsg(player, "zpmLinkStart");
    }

    // Links the device the player started with to the gate; whether it did
    private static boolean finishLinking(EntityPlayer player, LinkStart start, SGBaseTE gate) {
        World world = gate.getWorld();
        if (start.dimension != world.provider.getDimension()) {
            sendErrorMsg(player, "zpmLinkOtherWorld");
            return false;
        }
        TileEntity te = world.getTileEntity(start.pos);
        if (!(te instanceof ILinkableZpm)) {
            sendErrorMsg(player, "zpmLinkDeviceGone");
            return false;
        }
        if (!gate.isMerged) {
            sendErrorMsg(player, "zpmLinkNoGate");
            return false;
        }
        ILinkableZpm device = (ILinkableZpm) te;
        unlink(world, start.pos); // from any gate it was linked to before
        device.getZpmLink().linkTo(gate.getPos());
        changed((TileEntity) device);
        gate.addLinkedZpm(start.pos);
        BlockPos g = gate.getPos();
        sendBasicMsg(player, "zpmLinked", g.getX(), g.getY(), g.getZ());
        return true;
    }

    // Unlinks the device at pos from its gate, at both ends
    public static void unlink(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof ILinkableZpm)) {
            return;
        }
        ZpmLink link = ((ILinkableZpm) te).getZpmLink();
        if (link.linked) {
            SGBaseTE gate = SGBaseTE.at(world, link.gatePos);
            if (gate != null) {
                gate.removeLinkedZpm(pos);
            }
            link.unlink();
            changed(te);
        }
    }

    // Saves a device's link and sends it to players' games, which show it (the gate's Configurator lists its devices)
    private static void changed(TileEntity te) {
        te.markDirty();
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        te.getWorld().notifyBlockUpdate(te.getPos(), state, state, 3);
    }

    // The base of the stargate a block belongs to (its base, or any block of its ring), if it's a whole gate
    public static SGBaseTE gateAt(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof SGBaseTE) {
            return (SGBaseTE) te;
        }
        if (te instanceof SGRingTE) {
            return ((SGRingTE) te).getBaseTE();
        }
        return null;
    }
}
