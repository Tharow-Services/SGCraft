package gcewing.sg.features.commands;

import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.*;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;
import java.util.*;

public final class SGCommands extends CommandBase {

    @Override
    @NonNull
    public String getName() {
        return "SG";
    }

    @Override
    @NonNull
    public String getUsage(@NonNull ICommandSender sender) {
        return "Sub Commands" + String.join(", ", subcommands);
    }
    private static final List<String> subcommands = new ArrayList<String>();

    static {
        subcommands.add("clear");
        subcommands.add("link");
        subcommands.add("unlink");
        subcommands.add("cklink");
        subcommands.add("address");
        subcommands.add("ca");
        subcommands.add("location");
        subcommands.add("locate");
        subcommands.add("teleport");
        subcommands.add("teleport");
        subcommands.add("addressing");
        subcommands.add("vs");
        subcommands.add("vss");
    }

    @Override
    @NonNull
    public List<String> getTabCompletions(@NonNull MinecraftServer server, @NonNull ICommandSender sender, String @NonNull [] args, @Nullable BlockPos targetPos) {
        if (args.length<=1) {
            return subcommands;
        }
        return super.getTabCompletions(server, sender, args, targetPos);
    }
    public static void sendMessage(@NonNull ICommandSender sender, String key, Object ... args) {sender.sendMessage(new TextComponentTranslation(key, args));}
    public static void sendMessage(@NonNull ICommandSender sender, String key) {sender.sendMessage(new TextComponentTranslation(key));}

    private void setAddressingValues(MinecraftServer server, ICommandSender sender, String @NonNull [] args) throws CommandException {
        if (args[1].equalsIgnoreCase("Set")) {
            int input=0;
            try {
                input = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {}
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
                case "max-coord": SGAddressing.maxCoord=input;
                case "min-coord": SGAddressing.minCoord=-input;
                case "coord-range": SGAddressing.coordRange= input*2 + 1; break;
                case "min-dim": SGAddressing.minDirectDimension=input; break;
                case "max-dim": SGAddressing.maxDimensionIndex=input; break;
                case "38-preset": SGAddressing.setup38Symbols(); break;
                case "36-preset": SGAddressing.setup36Symbols(); break;
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
    public void execute(@NonNull MinecraftServer server, @NonNull ICommandSender s, String @NonNull [] args) throws CommandException {
        final int dim = s.getEntityWorld().provider.getDimension();
        final SGBaseTE gate = StargateCommands.getStargate(s);
        final ChunkPos chunkPos = gate==null?new ChunkPos(s.getPosition(), dim):gate.getChunkPos();
        if (args.length==0) {
            sendMessage(s, getUsage(s));
        }
        switch (args[0].toLowerCase()) {
            case "clear": {
                SGAddressMap.clearAddressTable();
                sendMessage(s, "sgcraft.command:Clear");
                break;
            }
            case "link": {
                try {
                    switch (args.length-1) {
                        case 1: SGAddressMap.AddAddress(chunkPos, SGAddressing.locationForAddress(args[1], dim)); break;
                        case 2: {
                            ChunkPos AddressOne = SGAddressing.locationForAddress(args[1], dim);
                            ChunkPos AddressTwo = SGAddressing.locationForAddress(args[2], dim);
                            SGAddressMap.AddAddress(AddressOne, AddressTwo);break;
                        }
                        case 3: {
                            ChunkPos AddressOne = new ChunkPos(parseInt(args[1]), parseInt(args[2]), parseInt(args[3]));
                            SGAddressMap.AddAddress(AddressOne, chunkPos); break;
                        }
                        case 6: {
                            ChunkPos AddressOne = new ChunkPos(parseInt(args[1]), parseInt(args[2]), parseInt(args[3]));
                            ChunkPos AddressTwo = new ChunkPos(parseInt(args[4]), parseInt(args[5]), parseInt(args[6]));
                            SGAddressMap.AddAddress(AddressOne, AddressTwo); break;
                        }
                        default:
                            throw new CommandException("message.sgcraft:AddressingError", "No Address Given");
                    }
                    sendMessage(s, "sgcraft.command.link:Complete");break;
                } catch (NumberInvalidException | SGAddressing.AddressingError e) {
                    sendMessage(s, "message.sgcraft:AddressingError",e.getMessage());
                    throw new CommandException("message.sgcraft:AddressingError", String.valueOf(e));
                }
            }
            case "unlink": {
                if (args.length==2) {
                    try {
                        SGAddressMap.RemoveAddress(SGAddressing.locationForAddress(args[1], dim));
                        sendMessage(s, "sgcraft.command.unlink:Complete"); break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError",e);
                        throw new CommandException("message.sgcraft:AddressingError",e.getMessage());
                    }
                }
                if (args.length==4) {
                    ChunkPos AddressOne = new ChunkPos(parseInt(args[1]),parseInt(args[2]),parseInt(args[3]));
                    SGAddressMap.RemoveAddress(AddressOne);
                    sendMessage(s, "sgcraft.command.unlink:Complete"); break;
                }
                sendMessage(s, "sgcraft.command.unlink:Usage"); break;
            }
            case "check":
            case "checklink": {
                if (args.length==2) {
                    try {
                        SGAddressing.validateAddress(args[1]);
                        ChunkPos pos = SGAddressMap.getAddress(SGAddressing.locationForAddress(args[1], dim));
                        sendMessage(s, "sgcraft.command.check:Addresses", args[1], SGAddressing.addressForLocation(pos.toLocation()));
                        break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError",e);
                        throw new CommandException("message.sgcraft:AddressingError",e);
                    }
                }
                if (args.length==4) {
                    ChunkPos pos = SGAddressMap.getAddress(parseInt(args[1]),parseInt(args[2]),parseInt(args[3]));
                    sendMessage(s, "sgcraft.command.check:Locations", parseInt(args[1]),parseInt(args[2]),parseInt(args[3]), pos.d, pos.x, pos.z);
                    break;
                }
                sendMessage(s, "sgcraft.command.check:Usage"); break;
            }
            case "addr":
            case "address": {
                if (args.length==4) {
                    try {
                        sendMessage(s,"sgcraft.command.address:Complete", SGAddressing.addressForLocation(new ChunkPos(parseInt(args[1]),parseInt(args[2]),parseInt(args[3])).toLocation()));
                        break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError",e);
                        throw new CommandException("message.sgcraft:AddressingError", e);
                    }
                }
                if (args.length==3) {
                    try {
                        sendMessage(s,"sgcraft.command.address:Complete", SGAddressing.addressForLocation(new ChunkPos(dim, parseInt(args[1]), parseInt(args[2])).toLocation()));
                        break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError",e);
                        throw new CommandException("message.sgcraft:AddressingError",e);
                    }
                }
                sendMessage(s, "sgcraft.command.address:Usage");
            }
            case "loc":
            case "location": {
                if (args.length==2 || args.length==3) {
                    try {
                        ChunkPos pos = SGAddressing.locationForAddress(args[1], args.length==2 ? dim : parseInt(args[2]));
                        sendMessage(s, "sgcraft.command.location:Complete", pos.d, pos.x, pos.z);
                        break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError", e);
                        throw new CommandException("message.sgcraft:AddressingError", e);
                    }
                }
                sendMessage(s, "sgcraft.command.location:Usage"); break;
            }
            case "ca":
            case "currentaddress": try {
                    sendMessage(s, "sgcraft.command:CurrentLocation",SGAddressing.addressForLocation(new SGLocation(dim,s.getPosition())));
                    break;
                } catch (SGAddressing.AddressingError e) {
                    throw new CommandException("message.sgcraft:AddressingError",e);
            }
            case "locate": {
                if (args.length==2 || args.length==3) {
                    try {
                        TileEntity tile = SGAddressing.findAddressedStargate(args[1], server.getWorld(args.length==2 ? dim : parseInt(args[2])), false);
                        if (tile!=null) {
                            SGLocation loc = new SGLocation(tile);
                            sendMessage(s, "sgcraft.command.locate:Complete", loc.dimension, loc.pos.getX(), loc.pos.getY(), loc.pos.getZ());
                            break;
                        }
                        sendMessage(s, "sgcraft.command.locate:Error"); break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError", e);
                        throw new CommandException("message.sgcraft:AddressingError", e);
                    }
                }
                sendMessage(s, "sgcraft.command.location:Usage"); break;
            }
            case "tp":
            case "teleport": {
                if (args.length==2 || args.length==3) {
                    try {
                        TileEntity tile = SGAddressing.findAddressedStargate(args[1], server.getWorld(args.length==3 ? dim : parseInt(args[1])), false);
                        if (tile!=null) {
                            SGLocation loc = new SGLocation(tile);
                            EntityPlayerMP player = getCommandSenderAsPlayer(s);
                            player.changeDimension(loc.dimension, new FakeTeleporter());
                            if (player.dimension == loc.dimension) {
                                player.connection.setPlayerLocation(loc.pos.getX(), loc.pos.getY()+1, loc.pos.getZ(), player.rotationYaw, player.rotationPitch); break;
                            }
                            sendMessage(s, "sgcraft.command.teleport:Error");
                            break;
                        }
                        sendMessage(s, "sgcraft.command.locate:Error"); break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError", e);
                        throw new CommandException("message.sgcraft:AddressingError", e);
                    }
                }
                sendMessage(s, "sgcraft.command.teleport:Usage"); break;
            }
            case "addressing": setAddressingValues(server, s, args); break;
            case "vs": sendMessage(s, "message.sgcraft:AddressingError", VSUtil.ENABLED); break;
            case "vss": VSUtil.ENABLED=!VSUtil.ENABLED; break;
            default: {
                sendMessage(s, "sgcraft.command:Usage");
            }
        }
    }


}
