//------------------------------------------------------------------------------------------------
//
//   SG Craft - /sgpower command: where the gate near the player gets its energy
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.command;

import gcewing.sg.SGCraft;
import gcewing.sg.interfaces.ISGEnergySource;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import java.text.DecimalFormat;
import java.util.List;

// /sgpower
//
// Lists, in the player's chat, every source the gate they're at would draw on to dial: its own base, if it holds
// energy, and each power unit, generator, ZPM device and DHD it can see, with where it is and how much it can give. In
// Forge Energy (as a Stargate Power Unit takes it), and in the gate's own units.
public class SGPowerCommand extends CommandBase {

    // How far from the player the gate can be
    static final int RADIUS = 8;
    static final DecimalFormat whole = new DecimalFormat("#,##0"), units = new DecimalFormat("#,##0.##");

    @Override
    public String getName() {
        return "sgpower";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sgpower - lists the power sources of the stargate you're at";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true; // anyone may ask about a gate they can use (see execute)
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        TileEntity found = GateUtil.locateGateFor(player.world, player, RADIUS);
        SGBaseTE gate = found instanceof SGBaseTE ? (SGBaseTE) found
            : found instanceof DHDTE ? ((DHDTE) found).getLinkedStargateTE() : null;
        if (gate == null || !gate.isMerged) {
            send(player, TextFormatting.RED + "No stargate within " + RADIUS + " blocks");
            return;
        }
        boolean admin = SGCraft.hasPermission(player, "sgcraft.admin");
        if (!gate.allowGateAccess(player) && !admin) {
            send(player, TextFormatting.RED + "You don't have access to this stargate");
            return;
        }

        List<ISGEnergySource> sources = gate.energySources();
        double total = gate.energyInBuffer;
        for (ISGEnergySource source : sources) {
            total += source.availableEnergy();
        }
        send(player, TextFormatting.GOLD + "Stargate at " + where(gate.getPos()) + ": " + amount(total) + " available"
            + TextFormatting.GRAY + " (" + whole.format(SGCraft.FPPerSGEnergyUnit) + " FE a gate unit)");
        if (gate.requiresNoPower) {
            send(player, TextFormatting.GREEN + " It needs no power to dial");
        }

        // Its own base, which keeps what it has drawn and not yet used
        if (!gate.isSupergate()) {
            line(player, gate, gate.energyInBuffer, "stored in the gate, of " + whole.format(gate.maxEnergyBuffer)
                + " gate units it can hold");
        }
        if (sources.isEmpty()) {
            send(player, TextFormatting.GRAY + " No power sources in reach");
        }
        for (ISGEnergySource source : sources) {
            if (source instanceof TileEntity) {
                line(player, (TileEntity) source, source.availableEnergy(), null);
            } else {
                send(player, " " + source + ": " + amount(source.availableEnergy()));
            }
        }

        // Why a source in sight might not count
        DHDTE dhd = gate.getLinkedControllerTE();
        if (dhd != null && !sources.contains(dhd)) {
            send(player, TextFormatting.GRAY + " DHD at " + where(dhd.getPos()) + " not used: "
                + (!gate.useDHDFuelSource ? "its fuel is turned off (dhd.useDHDFuelSource)" : "the last address dialed needs a ZPM"));
        }
        if (gate.isSupergate()) {
            send(player, TextFormatting.GRAY + " A supergate runs on its ZPMs alone");
        } else {
            send(player, TextFormatting.GRAY + " Sources count within 4 blocks of the base, plus ZPM devices linked to it");
        }
    }

    private static void line(EntityPlayerMP player, TileEntity te, double energy, String note) {
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        ITextComponent text = new TextComponentString(" ");
        ITextComponent name = new TextComponentTranslation(state.getBlock().getTranslationKey() + ".name");
        name.getStyle().setColor(TextFormatting.AQUA);
        text.appendSibling(name);
        text.appendSibling(new TextComponentString(TextFormatting.WHITE + " at " + where(te.getPos()) + ": " + amount(energy)
            + (note != null ? TextFormatting.GRAY + " " + note : "")));
        player.sendMessage(text);
    }

    private static String amount(double gateUnits) {
        return whole.format(Math.floor(gateUnits * SGCraft.FPPerSGEnergyUnit)) + " FE" + TextFormatting.GRAY + " ("
            + units.format(gateUnits) + " gate)" + TextFormatting.WHITE;
    }

    private static String where(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static void send(EntityPlayerMP player, String text) {
        player.sendMessage(new TextComponentString(text));
    }
}
