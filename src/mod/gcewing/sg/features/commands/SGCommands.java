package gcewing.sg.features.commands;

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

import javax.annotation.Nullable;
import java.util.*;

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
        return "Sub Commands" + String.join(", ", subcommands);
    }

    private static final List<String> subcommands = new ArrayList<>();

    static {
        subcommands.add("gate");
        subcommands.add("link");
        subcommands.add("unlink");
        subcommands.add("checklink");
        subcommands.add("address");
        subcommands.add("currentaddress");
        subcommands.add("location");
        subcommands.add("locate");
        subcommands.add("teleport");
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
    private static void sendMessage(ICommandSender sender, String key, Object ... args) {sender.sendMessage(new TextComponentTranslation(key, args));}
    private static void sendMessage(ICommandSender sender, String key) {sender.sendMessage(new TextComponentTranslation(key));}
    private boolean HelpArgs(ICommandSender sender, String[] args, String message) {
        if (args.length<=1) {sendMessage(sender, message); return false;}
        if (args[1].equalsIgnoreCase("?")) {sendMessage(sender, message); return false;}
        if (args[1].equalsIgnoreCase("help")) {sendMessage(sender, message); return false;}
        return true;
    }
    private int getDimFromSender(ICommandSender s) {
        return s.getEntityWorld().provider.getDimension();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender s, String[] args) throws CommandException {
        switch (args[0].toLowerCase()) {
            case "link": {
                if (args.length == 3) {
                    try {
                        ChunkPos AddressOne = SGAddressing.locationForAddress(args[1], getDimFromSender(s));
                        ChunkPos AddressTwo = SGAddressing.locationForAddress(args[2], getDimFromSender(s));
                        SGAddressMap.AddAddress(AddressOne, AddressTwo);
                        sendMessage(s, "sgcraft.command.link:Complete"); break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError",e);
                        throw new CommandException("message.sgcraft:AddressingError",e);
                    }
                }
                if (args.length==7) {
                    try {
                        ChunkPos AddressOne = new ChunkPos(parseInt(args[1]), parseInt(args[2]), parseInt(args[3]));
                        ChunkPos AddressTwo = new ChunkPos(parseInt(args[4]), parseInt(args[5]), parseInt(args[6]));
                        SGAddressMap.AddAddress(AddressOne, AddressTwo);
                        sendMessage(s, "sgcraft.command.link:Complete");
                        break;

                    } catch (NumberInvalidException e) {
                        sendMessage(s, "sgcraft.command.link:Error", e);
                        throw new CommandException("sgcraft.command.link:Error", e);
                    }
                }
                sendMessage(s, "sgcraft.command.link:Usage");break;
            }
            case "unlink": {
                if (args.length==2) {
                    try {
                        SGAddressMap.RemoveAddress(SGAddressing.locationForAddress(args[1], getDimFromSender(s)));
                        sendMessage(s, "sgcraft.command.unlink:Complete"); break;
                    } catch (SGAddressing.AddressingError e) {
                        sendMessage(s, "message.sgcraft:AddressingError",e);
                        throw new CommandException("message.sgcraft:AddressingError",e);
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
                        ChunkPos pos = SGAddressMap.getAddress(SGAddressing.locationForAddress(args[1], getDimFromSender(s)));
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
                        sendMessage(s,"sgcraft.command.address:Complete", SGAddressing.addressForLocation(new ChunkPos(getDimFromSender(s), parseInt(args[1]), parseInt(args[2])).toLocation()));
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
                        ChunkPos pos = SGAddressing.locationForAddress(args[1], args.length==2 ? getDimFromSender(s) : parseInt(args[2]));
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
                    sendMessage(s, "sgcraft.command:CurrentLocation",SGAddressing.addressForLocation(new SGLocation(getDimFromSender(s),s.getPosition())));
                    break;
                } catch (SGAddressing.AddressingError e) {
                    sendMessage(s, "message.sgcraft:AddressingError",e);
                    throw new CommandException("message.sgcraft:AddressingError",e);
            }
            case "locate": {
                if (args.length==2 || args.length==3) {
                    try {
                        TileEntity tile = SGAddressing.findAddressedStargate(args[1], server.getWorld(args.length==2 ? getDimFromSender(s) : parseInt(args[2])), false);
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
                        TileEntity tile = SGAddressing.findAddressedStargate(args[1], server.getWorld(args.length==3 ? getDimFromSender(s) : parseInt(args[2])), false);
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
            default: {
                sendMessage(s, "sgcraft.command:Usage");
            }
        }
    }


}
