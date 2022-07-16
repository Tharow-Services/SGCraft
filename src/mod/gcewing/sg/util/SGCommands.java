package gcewing.sg.util;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import static java.lang.Integer.parseInt;
public final class SGCommands extends CommandBase {
    @Override
    public String getName() {
        return "SG";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/SGCraft Add <DimID> <Chunk X> <Chunk Y> <DimID> <Chunk X> <Chunk Y> Switch the address of two stargates" +
               "/SGCraft Remove <DimID> <Chunk X> <Chunk Y> Remove Switched Address of a stargate" +
               "/SGCraft Check <DimID> <Chunk X> <Chunk Y> Return the location of a stargate" +
               "/SGCraft Address <DimID> <Chunk X> <Chunk Y> Gives The Address From Chunk Location" +
               "/SGCraft Location <Address> Gives The Location of a Address";
    }

    private void setAddressingValues(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {

        String tbs;

        if (args[1].equalsIgnoreCase("Set")) {
            int input=parseInt(args[3]);
            switch (args[2].toLowerCase()) {
                // Coords block
                case "mc": SGAddressing.mc=input; break;
                case "pc": SGAddressing.pc=input; break;
                case "qc": SGAddressing.qc=input; break;
                // Dim Block
                case "md": SGAddressing.md=input; break;
                case "pd": SGAddressing.pd=input; break;
                case "qd": SGAddressing.qd=input; break;
                // Other Stuff Block
                case "maxcoord": SGAddressing.maxCoord=input; break;
                case "mindim": SGAddressing.minDirectDimension=input; break;
                case "maxdim": SGAddressing.maxDimensionIndex=input; break;
                default: {
                    sender.sendMessage(new TextComponentString("invited: mc, pc, qc, md, pd, qd, maxcoord, mindim, maxdim"));
                }
            }
            sender.sendMessage(new TextComponentString("Value Updated"));
            return;
        }

        if (args[1].equalsIgnoreCase("Get")) {
            sender.sendMessage(new TextComponentString(
                    "Current Values: " +
                         "\n  MC: "+SGAddressing.mc+
                         "\n  PC: "+SGAddressing.pc+
                         "\n  QC: "+SGAddressing.qc+
                         "\n\n  MD: "+SGAddressing.md+
                         "\n  PD: "+SGAddressing.pd+
                         "\n  QD: "+SGAddressing.qd+
                         "\n  Max Coord: "+SGAddressing.maxCoord+
                         "\n  Coord Range: "+SGAddressing.coordRange+
                         "\n  Min Dim: "+SGAddressing.minDirectDimension+
                         "\n  Max Dim: "+SGAddressing.maxDimensionIndex
            ));
        }
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args[0].equalsIgnoreCase("add")) {
            SGAddress AddressOne = new SGAddress(parseInt(args[1]),parseInt(args[2]),parseInt(args[3]));
            SGAddress AddressTwo = new SGAddress(parseInt(args[4]),parseInt(args[5]),parseInt(args[6]));
            SGAddressMap.AddAddress(AddressOne, AddressTwo);
            sender.sendMessage(new TextComponentString("Added Address Ref To Translation Map"));
        }
        if (args[0].equalsIgnoreCase("remove")) {
            SGAddress AddressOne = new SGAddress(parseInt(args[1]),parseInt(args[2]),parseInt(args[3]));
            SGAddressMap.RemoveAddress(AddressOne);
            sender.sendMessage(new TextComponentString("Removed Address Ref From Translation Map"));
        }

        if (args[0].equalsIgnoreCase("check")) {
            sender.sendMessage(new TextComponentString("Stargate Links to: "+SGAddressMap.getAddress(parseInt(args[1]),parseInt(args[2]),parseInt(args[3])).toString()));
        }

        if (args[0].equalsIgnoreCase("Address")) {
            try {
                sender.sendMessage(new TextComponentString("Stargate Address: "+SGAddressing.addressForLocation(new SGLocation(parseInt(args[1]),new BlockPos(parseInt(args[2])*16,127,parseInt(args[3])*16)))));
            } catch (SGAddressing.AddressingError e) {
                throw new CommandException("message.sgcraft:AddressingError",e);
            }
        }

        if (args[0].equalsIgnoreCase("HA")) {
            try {
                sender.sendMessage(new TextComponentString("Stargate Address: "+SGAddressing.addressForLocation(new SGLocation(sender.getEntityWorld().provider.getDimension(),new BlockPos(parseInt(args[1])*16,127,parseInt(args[2])*16)))));
            } catch (SGAddressing.AddressingError e) {
                throw new CommandException("message.sgcraft:AddressingError",e);
            }
        }

        if (args[0].equalsIgnoreCase("CA")) {
            try {
                sender.sendMessage(new TextComponentString("Stargate Address: "+SGAddressing.addressForLocation(new SGLocation(sender.getEntityWorld().provider.getDimension(),sender.getPosition()))));
            } catch (SGAddressing.AddressingError e) {
                throw new CommandException("message.sgcraft:AddressingError",e);
            }
        }

        if (args[0].equalsIgnoreCase("Location")) {
            try {
                SGAddressing.Location(args[1], sender, false);
            } catch (SGAddressing.AddressingError e) {
                throw new CommandException("message.sgcraft:AddressingError",e);
            }
        }

        if (args[0].equalsIgnoreCase("RL")) {
            try {
                SGAddressing.Location(args[1], sender, true);
            } catch (SGAddressing.AddressingError e) {
                throw new CommandException("message.sgcraft:AddressingError",e);
            }
        }

        if (args[0].equalsIgnoreCase("TELocation")) {
            try {
                SGLocation location=SGAddressing.findAddressedStargate(args[1], sender.getEntityWorld(), false).connectedLocation;
                sender.sendMessage(new TextComponentString("Stargate Found At: [Dim: "+location.dimension+"X: "+location.pos.getX()+", Y: "+location.pos.getY()+", Z: "+location.pos.getZ()+"]"));
            } catch (SGAddressing.AddressingError e) {
                throw new CommandException("message.sgcraft:AddressingError",e);
            }
        }
        if (args[0].equalsIgnoreCase("Values")) {
            setAddressingValues(server, sender, args);
        }
    }


}
