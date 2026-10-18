package gcewing.sg.util;

import gcewing.sg.SGCraft;
import gcewing.sg.features.zpm.link.ZpmLinks;
import gcewing.sg.Trans3;
import gcewing.sg.Vector3;
import gcewing.sg.block.SGBaseBlock;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GateUtil {
    // The gate whose base, or whose DHD, is nearest the given place, within the given distance each
    // way, or null. Bases of every type count.
    public static TileEntity locateLocalGate(World world, BlockPos pos, int radius, boolean debug) {
        TileEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (final BlockPos.MutableBlockPos nearPos : BlockPos.getAllInBoxMutable(
            pos.add(-radius, -radius, -radius),
            pos.add(radius, radius, radius)
        )) {
            if (world.getBlockState(nearPos).getBlock() instanceof SGBaseBlock || world.getBlockState(nearPos).getBlock() == SGCraft.sgControllerBlock) {
                TileEntity gateTE = world.getTileEntity(nearPos);

                // A DHD stands for the gate it is linked to
                if (gateTE instanceof DHDTE) {
                    DHDTE dhd = (DHDTE) gateTE;
                    gateTE = dhd.isLinkedToStargate ? dhd.getLinkedStargateTE() : null;
                }

                if (gateTE instanceof SGBaseTE) {
                    double d = nearPos.distanceSq(pos);
                    if (d < nearestDistance) {
                        nearest = gateTE;
                        nearestDistance = d;
                    }
                }
            }
        }

        if (debug) {
            System.err.println(nearest != null ? "Found SGBaseTE at: " + nearest.getPos() : "Failed to find SGBaseTE.");
        }
        return nearest;
    }

    public static TileEntity locateDHD(World world, BlockPos pos, int radius, boolean debug) {
        for (final BlockPos.MutableBlockPos nearPos : BlockPos.getAllInBoxMutable(
            pos.add(-radius, -radius, -radius),
            pos.add(radius, radius, radius)
        )) {
            if (world.getBlockState(nearPos).getBlock() == SGCraft.sgControllerBlock) {
                TileEntity dhdTE = world.getTileEntity(nearPos);

                if (dhdTE instanceof DHDTE) {
                    if (dhdTE != null) {
                        if (debug) {
                            System.err.println("Found DHDTE at: " + dhdTE.getPos());
                        }
                        return dhdTE;
                    }
                }
            }
        }

        if (debug) {
            System.err.println("Failed to find DHDTE.");
        }
        return null;
    }

    // The gate a player using a tool such as the PDD means: the one whose ring they are looking
    // at, else one whose base or DHD is within the given distance, else the nearest whose ring or
    // opening is. A supergate's base can be far from where the player stands at its ring.
    public static TileEntity locateGateFor(World world, EntityPlayer player, int radius) {
        SGBaseTE gate = gateLookedAt(world, player, Math.max(radius, reach(player)));
        if (gate != null) {
            return gate;
        }
        TileEntity te = locateLocalGate(world, new BlockPos(player.posX, player.posY, player.posZ), radius, false);
        if (te != null) {
            return te;
        }
        double best = radius;
        Vec3d at = player.getPositionEyes(1);
        for (TileEntity other : world.loadedTileEntityList) {
            if (other instanceof SGBaseTE && ((SGBaseTE) other).isMerged) {
                double d = distanceToGate((SGBaseTE) other, at);
                if (d <= best) {
                    best = d;
                    te = other;
                }
            }
        }
        return te;
    }

    // How far the player can reach to click a block
    public static double reach(EntityPlayer player) {
        return player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
    }

    // The merged gate whose rendered ring the player is looking at, no further away than the given
    // distance, or null. A supergate's ring is drawn far wider than its blocks, so a click on most
    // of it misses them.
    public static SGBaseTE gateLookedAt(World world, EntityPlayer player, double reach) {
        return gateLookedAt(world, player, reach, null);
    }

    // As above, also giving in hitDistance[0], if given, how far away the ring is where it's looked at
    public static SGBaseTE gateLookedAt(World world, EntityPlayer player, double reach, double[] hitDistance) {
        Vec3d eye = player.getPositionEyes(1);
        Vec3d look = player.getLook(1);
        SGBaseTE best = null;
        double bestT = reach;
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof SGBaseTE) || !((SGBaseTE) te).isMerged) {
                continue;
            }
            SGBaseTE gate = (SGBaseTE) te;
            GateShape shape = gate.getShape();
            if (eye.squareDistanceTo(new Vec3d(gate.getPos())) > square(reach + 2 * shape.ringOuterRadius + 1)) {
                continue;
            }
            double[] p = toRing(gate, eye.x, eye.y, eye.z, false);
            double[] d = toRing(gate, look.x, look.y, look.z, true);
            double t = ringHit(shape, p, d);
            if (t < bestT) {
                bestT = t;
                best = gate;
            }
        }
        if (hitDistance != null) {
            hitDistance[0] = bestT;
        }
        return best;
    }

    // The gate whose drawn ring the player is looking at, if no block is in front of it within reach
    public static SGBaseTE gateClickedOn(World world, EntityPlayer player) {
        double reach = reach(player);
        double[] ringDistance = new double[1];
        SGBaseTE gate = gateLookedAt(world, player, reach, ringDistance);
        if (gate == null) {
            return null;
        }
        Vec3d eye = player.getPositionEyes(1);
        Vec3d end = eye.add(player.getLook(1).scale(reach));
        RayTraceResult block = world.rayTraceBlocks(eye, end, false, false, true);
        if (block != null && block.typeOfHit == RayTraceResult.Type.BLOCK && eye.distanceTo(block.hitVec) <= ringDistance[0]) {
            return null;
        }
        return gate;
    }

    // Does what right clicking the gate's base with the player's main hand does
    public static boolean activateGate(SGBaseTE gate, EntityPlayer player) {
        // A ZPM device waiting to be linked to a gate is linked to this one instead (see ZpmLinks)
        if (ZpmLinks.gateClicked(player, gate)) {
            return true;
        }
        World world = gate.getWorld();
        BlockPos pos = gate.getPos();
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        return block instanceof SGBaseBlock
            && block.onBlockActivated(world, pos, state, player, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5f, 0.5f, 0.5f);
    }

    // How far a point is from the gate's rendered ring and the opening inside it
    public static double distanceToGate(SGBaseTE gate, Vec3d at) {
        GateShape shape = gate.getShape();
        double[] p = toRing(gate, at.x, at.y, at.z, false);
        double dr = Math.max(0, Math.sqrt(p[0] * p[0] + p[1] * p[1]) - shape.ringOuterRadius);
        double dz = Math.max(0, Math.abs(p[2]) - shape.ringDepth / 2);
        return Math.sqrt(dr * dr + dz * dz);
    }

    // A point (or a direction) in the world in coordinates centred on the gate's ring, the ring
    // lying in the x-y plane, set out as the iris is (see EntityStargateIris)
    static double[] toRing(SGBaseTE gate, double x, double y, double z, boolean direction) {
        Trans3 t = gate.localToGlobalTransformation();
        Vector3 v = direction ? t.iv(x, y, z) : t.ip(x, y, z);
        double c = direction ? 0 : gate.getShape().centreY;
        if (gate.gateOrientation == 1) {
            return new double[] {v.x, v.y - c, v.z};
        } else {
            return new double[] {v.x, v.z + c, v.y};
        }
    }

    // How far along the ray from p in direction d (unit length, ring coordinates) it first meets
    // the ring's solid, or infinity
    static double ringHit(GateShape shape, double[] p, double[] d) {
        double ri = shape.ringInnerRadius, ro = shape.ringOuterRadius, h = shape.ringDepth / 2;
        double best = Double.POSITIVE_INFINITY;
        // Its faces
        if (d[2] != 0) {
            for (int side = -1; side <= 1; side += 2) {
                double t = (side * h - p[2]) / d[2];
                if (t >= 0) {
                    double x = p[0] + t * d[0], y = p[1] + t * d[1], r2 = x * x + y * y;
                    if (r2 >= ri * ri && r2 <= ro * ro) {
                        best = Math.min(best, t);
                    }
                }
            }
        }
        // Its inner and outer edges
        double a = d[0] * d[0] + d[1] * d[1];
        if (a > 0) {
            for (double r : new double[] {ri, ro}) {
                double b = 2 * (p[0] * d[0] + p[1] * d[1]);
                double c = p[0] * p[0] + p[1] * p[1] - r * r;
                double disc = b * b - 4 * a * c;
                if (disc >= 0) {
                    double s = Math.sqrt(disc);
                    for (double t : new double[] {(-b - s) / (2 * a), (-b + s) / (2 * a)}) {
                        if (t >= 0 && Math.abs(p[2] + t * d[2]) <= h) {
                            best = Math.min(best, t);
                        }
                    }
                }
            }
        }
        return best;
    }

    static double square(double x) {
        return x * x;
    }
}
