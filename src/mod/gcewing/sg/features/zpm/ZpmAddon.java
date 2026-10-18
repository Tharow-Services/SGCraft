package gcewing.sg.features.zpm;

import static gcewing.sg.BaseMod.isModLoaded;

import gcewing.sg.features.ic2.zpm.interfacecart.ZpmInterfaceCartTE;
import gcewing.sg.features.ic2.zpm.modulehub.ZpmHubTE;
import gcewing.sg.features.zpm.console.ZpmConsoleTE;
import gcewing.sg.interfaces.ISGEnergySource;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ZpmAddon {

    static boolean debugAddon = true;
    final static DecimalFormat dFormat = new DecimalFormat("###,###,###,##0");

    public static boolean routeRequiresZPM(World origin, World destination) {
        return routeRequiresZpm(origin.getWorldInfo().getWorldName().toLowerCase(), destination.getWorldInfo().getWorldName().toLowerCase());
    }

    public static boolean routeRequiresZpm(String origin, String destination) {
        Optional<Double> multiplier = ZPMMultiplierRegistry.getMultiplierFrom(origin, destination);
        if (!multiplier.isPresent()) {
            return false;
        }

        if (multiplier.get() > 0) {
            return true;
        } else {
            return false;
        }
    }

    public static int routeZpmMultiplier(String origin, String destination) {
        Optional<Double> multiplier = ZPMMultiplierRegistry.getMultiplierFrom(origin, destination);
        if (!multiplier.isPresent()) {
            return 0;
        }

        return multiplier.get().intValue();
    }

    // The loaded ZPMs (in consoles, and with IC2 in carts and hubs) within radius blocks of pos, empty ones left out
    public static List<ISGEnergySource> findZpmSources(World world, BlockPos pos, int radius) {
        List<ISGEnergySource> sources = new ArrayList<>();
        boolean ic2 = isModLoaded("ic2");
        for (final BlockPos.MutableBlockPos nearPos : BlockPos.getAllInBoxMutable(pos.add(-radius, -radius, -radius), pos.add(radius, radius, radius))) {
            if (!world.isBlockLoaded(nearPos)) {
                continue;
            }
            TileEntity nte = world.getTileEntity(nearPos);
            if (nte instanceof ZpmConsoleTE && !((ZpmConsoleTE) nte).isEmpty()) {
                sources.add((ISGEnergySource) nte);
            } else if (ic2 && nte instanceof ZpmInterfaceCartTE && !((ZpmInterfaceCartTE) nte).isEmpty()) {
                sources.add((ISGEnergySource) nte);
            } else if (ic2 && nte instanceof ZpmHubTE && !((ZpmHubTE) nte).isEmpty()) {
                sources.add((ISGEnergySource) nte);
            }
        }
        return sources;
    }

    // Draws amount from the sources in equal shares, so they run down together rather than one after another. A source
    // that can't give its share gives what it has, and the rest is shared out among the others. Returns what was drawn.
    public static double drawEvenly(List<ISGEnergySource> sources, double amount) {
        List<ISGEnergySource> giving = new ArrayList<>(sources);
        double drawn = 0;
        while (amount - drawn > 1e-9 && !giving.isEmpty()) {
            double share = (amount - drawn) / giving.size();
            List<ISGEnergySource> stillGiving = new ArrayList<>();
            for (ISGEnergySource source : giving) {
                double got = source.drawEnergyDouble(share);
                drawn += got;
                if (got >= share - 1e-9) {
                    stillGiving.add(source); // gave all it was asked, so may have more
                }
            }
            if (stillGiving.size() == giving.size()) {
                break; // everyone gave their full share: done
            }
            giving = stillGiving;
        }
        return drawn;
    }

    public static double zpmPowerAvailable(World world, BlockPos pos, int radius, boolean debug) {
        debugAddon = debug;
        double zpmPower = 0.0;
        for (final BlockPos.MutableBlockPos nearPos : BlockPos.getAllInBoxMutable(
            pos.add(-radius, -radius, -radius),
            pos.add(radius, radius, radius)
        )) {
            TileEntity nte = world.getTileEntity(nearPos);
            if (nte != null) {
                if (isModLoaded("ic2")) {
                    if (nte instanceof ZpmInterfaceCartTE) {
                        if (debugAddon) {
                            System.out.printf("SGBaseTE.zpmInterfaceCartNear: %s at %s\n", nte, nearPos);
                        }
                        if (((ZpmInterfaceCartTE) nte).isEmpty()) {
                            if (debugAddon) {
                                System.out.println("ZPM cart is empty");
                            }
                        } else {
                            zpmPower += ((ISGEnergySource) nte).availableEnergy();
                        }
                    }
                    if (nte instanceof ZpmHubTE) {
                        if (debugAddon) {
                            System.out.printf("SGBaseTE.zpmHubNear: %s at %s\n", nte, nearPos);
                        }
                        if (((ZpmHubTE) nte).isEmpty()) {
                            if (debugAddon) {
                                System.out.println("ZPM Hub is empty");
                            }
                        } else {
                            zpmPower += ((ISGEnergySource) nte).availableEnergy();
                        }
                    }
                }
                if (nte instanceof ZpmConsoleTE) {
                    if (debugAddon) {
                        System.out.printf("SGBaseTE.zpmConsoleNear: %s at %s\n", nte, nearPos);
                    }
                    if (((ZpmConsoleTE) nte).isEmpty()) {
                        if (debugAddon) {
                            System.out.println("ZPM cart is empty");
                        }
                    } else {
                        zpmPower += ((ISGEnergySource) nte).availableEnergy();
                    }
                }
            }
        }
        if (debugAddon) {
            System.out.println("SGCraft:ZpmAddon - Power Available: " + dFormat.format(zpmPower));
        }
        if (zpmPower <= 0) {
            return 0;
        } else {
            return zpmPower;
        }
    }
}
