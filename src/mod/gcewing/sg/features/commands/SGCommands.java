package gcewing.sg.features.commands;

import gcewing.sg.util.SGAddress;
import gcewing.sg.util.SGAddressMap;
import gcewing.sg.util.SGAddressing;
import gcewing.sg.util.SGLocation;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
public final class SGCommands extends CommandBase {

    private final StargateCommands Stargate;

    public SGCommands() {
        super();
        this.Stargate=new StargateCommands();
    }
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

    private static final List<String> subcommands = new ArrayList<>();

    static {
        subcommands.add("gate");
        subcommands.add("link");
        subcommands.add("unlink");
        subcommands.add("checklink");
        subcommands.add("address");
        subcommands.add("localaddress");
        subcommands.add("currentaddress");
        subcommands.add("location");
        subcommands.add("locate");
        subcommands.add("tp");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length<=1) {
            return subcommands;
        }
        if (args.length<=2) {
            return Stargate.getTabCompletions(server, sender, args, targetPos);
        }
        return super.getTabCompletions(server, sender, args, targetPos);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        switch (args[0].toLowerCase()) {
            case "gate": Stargate.execute(server, sender, args); break;
            case "link": {
                SGAddress AddressOne = new SGAddress(parseInt(args[1]),parseInt(args[2]),parseInt(args[3]));
                SGAddress AddressTwo = new SGAddress(parseInt(args[4]),parseInt(args[5]),parseInt(args[6]));
                SGAddressMap.AddAddress(AddressOne, AddressTwo);
                sender.sendMessage(new TextComponentString("Added Address Ref To Translation Map"));
                break;
            }
            case "unlink": {
                SGAddress AddressOne = new SGAddress(parseInt(args[1]),parseInt(args[2]),parseInt(args[3]));
                SGAddressMap.RemoveAddress(AddressOne);
                sender.sendMessage(new TextComponentString("Removed Address Ref From Translation Map"));
                break;
            }
            case "checklink": sender.sendMessage(new TextComponentString("Stargate Links to: "+SGAddressMap.getAddress(parseInt(args[1]),parseInt(args[2]),parseInt(args[3])).toString())); break;
            case "address": {
                try {
                    sender.sendMessage(new TextComponentString("Stargate Address: "+ SGAddressing.addressForLocation(new SGLocation(parseInt(args[1]),new BlockPos(parseInt(args[2])*16,127,parseInt(args[3])*16)))));
                } catch (SGAddressing.AddressingError e) {
                    throw new CommandException("message.sgcraft:AddressingError",e);
                }
                break;
            }
            case "localaddress": {
                try {
                    sender.sendMessage(new TextComponentString("Stargate Address: "+SGAddressing.addressForLocation(new SGLocation(sender.getEntityWorld().provider.getDimension(),new BlockPos(parseInt(args[1])*16,127,parseInt(args[2])*16)))));
                } catch (SGAddressing.AddressingError e) {
                    throw new CommandException("message.sgcraft:AddressingError",e);
                }
                break;
            }
            case "currentaddress": {
                try {
                    sender.sendMessage(new TextComponentString("Stargate Address: "+SGAddressing.addressForLocation(new SGLocation(sender.getEntityWorld().provider.getDimension(),sender.getPosition()))));
                } catch (SGAddressing.AddressingError e) {
                    throw new CommandException("message.sgcraft:AddressingError",e);
                }
                break;
            }
            case "location": {
                try {
                    SGAddressing.Location(args[1], sender, false);
                } catch (SGAddressing.AddressingError e) {
                    throw new CommandException("message.sgcraft:AddressingError",e);
                }
                break;
            }
            case "locate": {
                try {
                    String address=args[1].toUpperCase().replaceAll("-", "");
                    SGAddressing.validateAddress(address);
                    SGLocation location= new SGLocation(SGAddressing.findAddressedStargate(address, sender.getEntityWorld(), false));
                    sender.sendMessage(new TextComponentString("Stargate Found At: [Dim: "+location.dimension+"X: "+location.pos.getX()+", Y: "+location.pos.getY()+", Z: "+location.pos.getZ()+"]"));
                } catch (SGAddressing.AddressingError e) {
                    throw new CommandException("message.sgcraft:AddressingError",e);
                }
                break;
            }
            case "tp": {
                try {
                    String address=args[1].toUpperCase().replaceAll("-", "");
                    SGAddressing.validateAddress(address);
                    SGLocation location= new SGLocation(SGAddressing.findAddressedStargate(address, sender.getEntityWorld(), false));
                    sender.sendMessage(new TextComponentString("Stargate Found At: [Dim: "+location.dimension+"X: "+location.pos.getX()+", Y: "+location.pos.getY()+", Z: "+location.pos.getZ()+"]"));
                    getCommandSenderAsPlayer(sender).attemptTeleport(location.pos.getX(), location.pos.getY()+1, location.pos.getZ());
                } catch (SGAddressing.AddressingError e) {
                    throw new CommandException("message.sgcraft:AddressingError",e);
                }
                break;
            }
            case "test1": {
                server.getWorld(sender.getEntityWorld().provider.getDimension()).setBlockState(sender.getPosition(), Blocks.STONE.getDefaultState()); break;
            }
            case "test2": {
                sender.getEntityWorld().setBlockState(sender.getPosition(), Blocks.STONE.getDefaultState()); break;
            }
            default: {
                sender.sendMessage(new TextComponentString(getUsage(sender)));
            }
        }
    }


}
