//------------------------------------------------------------------------------------------------
//
//   SG Craft - General/Shared code used by the rest of the SGCraft worldgen system
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.features.commands;

import gcewing.sg.BaseOrientation;
import gcewing.sg.SGCraft;
import gcewing.sg.block.SGRingBlock;
import gcewing.sg.generator.FeatureStargate;
import gcewing.sg.generator.GeneratorAddressRegistry;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateUtil;
import gcewing.sg.util.SGAddressing;
import gcewing.sg.util.SGState;
import net.malisis.core.util.raytrace.Raytrace;
import net.malisis.core.util.raytrace.RaytraceBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class StargateCommands extends CommandBase {

    @Override
    public String getName() {
        return "stargate";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("Gate", "SGGate");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "stargate commands: gen, debug, address, disconnect, reset, status, dial, place, type";
    }

    private static final List<String> subcommands = new ArrayList<>();

    static {
        subcommands.add("gen");
        subcommands.add("debug");
        subcommands.add("address");
        subcommands.add("disconnect");
        subcommands.add("reset");
        subcommands.add("status");
        subcommands.add("dial");
        subcommands.add("place");
        subcommands.add("type");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length<=1) {
            return subcommands;
        }
        return super.getTabCompletions(server, sender, args, targetPos);
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        switch (args[0].toLowerCase()) {
            case "gen": generateStargate(server, sender, args); break;
            case "debug": adminOptions(sender); break;
            case "addr":
            case "address": getAddress(sender); break;
            case "dis":
            case "disconnect": disconnect(sender); break;
            case "reset": reset(sender); break;
            case "status": getStatus(sender); break;
            case "dial": dial(sender, args[1]); break;
            case "place": placeStargate(sender); break;
            case "type": switchType(sender); break;
            default: sender.sendMessage(new TextComponentString(getUsage(sender)));
        }

    }


    public SGBaseTE getStargate(ICommandSender sender) {
        TileEntity gate = null;
        BlockPos pos;
        pos=Minecraft.getMinecraft().objectMouseOver.getBlockPos();
        if (sender.getEntityWorld().getTileEntity(pos) instanceof SGBaseTE) {
            gate= sender.getEntityWorld().getTileEntity(pos);
        }
        if (gate==null){gate= GateUtil.locateLocalGate(sender.getEntityWorld(), sender.getPosition(), 16, true);}
        if (gate!=null) {return (SGBaseTE) gate;}
        sender.sendMessage(new TextComponentTranslation("sgcraft.command.gate:NoGateFound"));return null;
    }

    public void getAddress(ICommandSender sender) {
        SGBaseTE gate= getStargate(sender); if (gate==null) {return;}
        sender.sendMessage(new TextComponentString("Stargate Home Address: "+gate.HomeAddress()));
    }

    public void disconnect(ICommandSender sender) {
        SGBaseTE gate= getStargate(sender); if (gate==null) {return;}
        gate.disconnect();
        sender.sendMessage(new TextComponentString("Stargate Disconnected"));
    }



    public void getStatus(ICommandSender sender) {
        SGBaseTE gate= getStargate(sender); if (gate==null) {return;}
        sender.sendMessage(new TextComponentString("Stargate State: "+gate.sgStateDescription()));
    }

    public void reset(ICommandSender sender) {
        SGBaseTE gate= getStargate(sender); if (gate==null) {return;}
        if (gate.state== SGState.Idle) {
            gate.clearIdleConnection();
        } else {
            gate.clearConnection();
        }
        gate.resetStargate();
        try {
            gate.homeAddress=gate.getHomeAddress();
        } catch (SGAddressing.AddressingError e) {
            gate.addressError=String.valueOf(e);
            sender.sendMessage(new TextComponentString("Warning Stargate Has Invalid Address: "+e.getMessage()));
        }
        sender.sendMessage(new TextComponentString("Stargate Was Reset"));
    }

    public void dial(ICommandSender sender, String address) {
        SGBaseTE gate= getStargate(sender); if (gate==null) {return;}
        sender.sendMessage(new TextComponentTranslation("sgcraft.gui.pdd.label.dialing"));
        String destination = SGAddressing.normalizeAddress(address);
        try {
            SGAddressing.validateAddress(destination);
        } catch (SGAddressing.AddressingError e) {
            sender.sendMessage(new TextComponentString("Address Invalid"));
        }
        gate.connect(destination, null, false, false);

    }
    public void adminOptions(ICommandSender sender) {
        SGBaseTE gate= getStargate(sender); if (gate==null) {return;}
        gate.requiresNoPower = true;
        gate.chevronsLockOnDial = true;
        gate.hasChevronUpgrade = true;
        gate.oneWayTravel = false;
        sender.sendMessage(new TextComponentString("Debugging Defaults Were Applied To Stargate"));
    }
    public void switchType(ICommandSender sender) {
        SGBaseTE gate= getStargate(sender); if (gate==null) {return;}
        if (gate.gateType==1) {
            gate.gateType=2;
        } else {
            gate.gateType=1;
        }
        gate.markChanged();
    }

    public void generateStargate(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, 0);
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        FeatureStargate feature = new FeatureStargate();
        BlockPos pos = player.getPosition();
        EnumFacing facing = player.getHorizontalFacing();
        int Dim = player.getEntityWorld().provider.getDimension();
        World world = server.getWorld(Dim);
        int o = parseInt(args[2]);
        StructureBoundingBox clip = new StructureBoundingBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+o, pos.getY()+o, pos.getZ()+o);
        feature.updateBoundingBox(clip);
        feature.setSpawnDirection(facing);
        feature.setPass(3);
        feature.GenerateSimpleStargatePlatform(world, clip, Blocks.STONE_BRICK_STAIRS, Blocks.STONEBRICK.getDefaultState(), Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());

        //IBlockState sgBase = SGCraft.sgBaseBlock.getDefaultState().withProperty(BaseOrientation.Orient4WaysByState.FACING, facing);
        //SGBaseTE gate= (SGBaseTE) sgBase.getBlock().createTileEntity(world, sgBase);
        //world.setTileEntity(feature.getGatePos(), gate);
        SGBaseTE gate = (SGBaseTE) world.getTileEntity(feature.getGatePos());
        assert gate != null;
        world.markChunkDirty(feature.getGatePos(), gate);

    }

    public void placeStargate(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        BlockPos spawnPos = sender.getPosition();
        IBlockState id = null;
        IBlockState air = Blocks.AIR.getDefaultState();

        IBlockState[] sgRings = new IBlockState[2];
        sgRings[0] = SGCraft.sgRingBlock.getDefaultState();
        sgRings[1] = sgRings[0].withProperty(SGRingBlock.VARIANT, 1);


        int gateX=spawnPos.getX();
        int gateY=spawnPos.getY();
        int gateZ=spawnPos.getZ();
        EnumFacing gateFaces = player.getHorizontalFacing();
        switch (player.getHorizontalFacing()) {
            case NORTH: gateZ -= 2; break;
            case SOUTH: gateZ += 2; break;
            case EAST: gateX += 2; break;
            case WEST: gateX -= 2; break;
            case UP: gateY += 2; break;
            case DOWN: gateY -= 2; break;
        }
        IBlockState sgBase = SGCraft.sgBaseBlock.getDefaultState().withProperty(BaseOrientation.Orient4WaysByState.FACING, gateFaces);
        BlockPos gatePos = new BlockPos(gateX, gateY, gateZ);
        boolean orientNS = (gateFaces == EnumFacing.NORTH) || (gateFaces == EnumFacing.SOUTH);

        World world = player.getEntityWorld();

        sender.sendMessage(new TextComponentString("Stargate built at: " + new BlockPos(gateX, gateY, gateZ)));

        if (gateFaces!=null || gateFaces==EnumFacing.UP || gateFaces==EnumFacing.DOWN) {
            for (int i = -2; i <= 2; i++) {
                for (int j = 0; j <= 4; j++) {
                    if (i == 0 && j == 0) {
                        id = sgBase;
                    } else if (i == -2 || i == 2 || j == 0 || j == 4) {
                        id = sgRings[(i + j + 1) & 1];
                    } else {
                        id = air;
                    }

                    if (orientNS) {

                        world.setBlockState(new BlockPos(gateX + i, gateY + j, gateZ),id);
                        if (id==sgBase) {
                            world.setTileEntity(new BlockPos(gateX + i, gateY + j, gateZ), sgBase.getBlock().createTileEntity(world,id));
                        }
                    } else {
                        world.setBlockState(new BlockPos( gateX, gateY + j, gateZ + i), id);
                        if (id==sgBase) {
                            world.setTileEntity(new BlockPos(gateX + i, gateY + j, gateZ), sgBase.getBlock().createTileEntity(world,id));
                        }
                    }
                }
            }
        } else {
            sender.sendMessage(new TextComponentString("Stargate attempted to spawn Horizontal, but that code is not done yet!"));
            return;
        }

        SGBaseTE te = null;
        if (gatePos != null)
            te = (SGBaseTE)world.getTileEntity(gatePos);

        if (te != null) {
            te.hasChevronUpgrade=true;

            te.gateType = 1;
            te.chevronsLockOnDial = true;
            te.requiresNoPower = true;

            te.markChanged();

            if (te.homeAddress != null) {
                GeneratorAddressRegistry.addAddress(te.getWorld(), te.homeAddress);
                te.isGenerated = true; // Tag gate as being generated here.
            }

            if (te.homeAddress == null) {
                sender.sendMessage(new TextComponentString("Something bad happened!!! please report to Dockter:  unable to assign home address during generation"));
            }
        } else {
            sender.sendMessage(new TextComponentString( "SGCraft: FeatureGeneration is done and Stargate TE was null! That's bad. Pos " + gatePos + " and Direction " + player.getHorizontalFacing()));
        }
    }
}
