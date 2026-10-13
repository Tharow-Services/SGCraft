//------------------------------------------------------------------------------------------------
//
//   SG Craft - /sgspawn command (testing aid)
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.command;

import gcewing.sg.SGCraft;
import gcewing.sg.block.SGBaseBlock;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateBuilder;
import gcewing.sg.util.GateShape;
import gcewing.sg.util.GateType;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

// /sgspawn <stargate|supergate> [milkyway|pegasus] [size]
//
// Builds a finished gate a few blocks in front of the player, facing them. A supergate's size is
// its width in blocks, an odd number; without one it gets the default size.
public class SGSpawnCommand extends CommandBase {

    static final int MIN_SIZE = 2 * GateShape.MIN_SUPERGATE_RADIUS + 1;
    static final int MAX_SIZE = 2 * GateShape.MAX_SUPERGATE_RADIUS + 1;
    // How far in front of the player the base goes
    static final int DISTANCE = 3;

    @Override
    public String getName() {
        return "sgspawn";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sgspawn <stargate|supergate> [milkyway|pegasus] [size " + MIN_SIZE + "-" + MAX_SIZE + ", supergate only]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 3) {
            throw new WrongUsageException(getUsage(sender));
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        boolean supergate;
        switch (args[0].toLowerCase()) {
            case "stargate": supergate = false; break;
            case "supergate": supergate = true; break;
            default: throw new WrongUsageException(getUsage(sender));
        }

        GateType type = GateType.MILKY_WAY;
        if (args.length >= 2) {
            switch (args[1].toLowerCase()) {
                case "milkyway": type = GateType.MILKY_WAY; break;
                case "pegasus": type = GateType.PEGASUS; break;
                default: throw new CommandException("Unknown gate type '" + args[1] + "': use milkyway or pegasus");
            }
        }

        SGBaseBlock baseBlock = supergate ? SGCraft.supergateBaseBlock : SGCraft.sgBaseBlock;
        GateShape shape = baseBlock.getShape();
        if (args.length >= 3) {
            if (!supergate) {
                throw new CommandException("Only a supergate can be given a size");
            }
            int size = parseInt(args[2], MIN_SIZE, MAX_SIZE);
            if (size % 2 == 0) {
                throw new CommandException("The size must be an odd number, so the base is in the middle: try "
                    + (size - 1) + " or " + (size + 1));
            }
            shape = GateShape.supergate((size - 1) / 2);
        }

        EnumFacing facing = player.getHorizontalFacing();
        BlockPos pos = new BlockPos(player).offset(facing, DISTANCE);
        SGBaseTE te = GateBuilder.build(player.world, pos, facing, baseBlock, shape, type);
        if (te == null) {
            throw new CommandException("The gate was placed at " + describe(pos) + " but its ring did not merge");
        }
        String name = (type == GateType.PEGASUS ? "Pegasus " : "Milky Way ") + (supergate ? "supergate" : "stargate");
        sender.sendMessage(new TextComponentString("Built a " + shape.width + "-block " + name + " at " + describe(pos)));
    }

    static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        switch (args.length) {
            case 1:
                return getListOfStringsMatchingLastWord(args, "stargate", "supergate");
            case 2:
                return getListOfStringsMatchingLastWord(args, "milkyway", "pegasus");
            case 3:
                if (args[0].equalsIgnoreCase("supergate")) {
                    List<String> sizes = new ArrayList<>();
                    for (int s = MIN_SIZE; s <= MAX_SIZE; s += 2) {
                        sizes.add(Integer.toString(s));
                    }
                    return getListOfStringsMatchingLastWord(args, sizes);
                }
                return Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }

}
