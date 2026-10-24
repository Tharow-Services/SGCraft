package gcewing.sg.util;

import gcewing.sg.Trans3;
import gcewing.sg.Vector3;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.IPhysicsEntity;
import valkyrienwarfare.api.IPhysicsEntityManager;
import valkyrienwarfare.api.TransformType;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static valkyrienwarfare.api.TransformType.SUBSPACE_TO_GLOBAL;

public class VSUtil {
    public static boolean ENABLED = false; //Used at the start of every function to quickly exit if VS isn't loaded

    @NonNull public static AxisAlignedBB getAABBInGlobal(@NonNull AxisAlignedBB axisAlignedBB,
                                         @Nullable World world, @Nullable BlockPos pos) {
        if (!ENABLED) return axisAlignedBB;
        return ValkyrienUtils.getAABBInGlobal(axisAlignedBB, world, pos);
    }

    public static Entity subToGlobalTransform(World world, @NonNull Vector3 pos, Vector3 rot, BiFunction<Vector3, Vector3, Entity> consumer) {
        IPhysicsEntity inst = getPhysicsEntityFromShipSpace(world, pos.blockPos());
        if (!ENABLED || inst==null) { return consumer.apply(pos, rot); }
        return consumer.apply(new Vector3(inst.transformVector(pos.toVec3d(), SUBSPACE_TO_GLOBAL)), new Vector3(inst.rotateVector(rot.toVec3d(), SUBSPACE_TO_GLOBAL)));
    }

    public static Trans3 localToGlobalTransformation(World world, Trans3 vec) {
        if (!ENABLED) return vec;
        IPhysicsEntity inst = getPhysicsEntityFromShipSpace(world, vec.offset.blockPos());
        if (inst!=null) {
            return localToGlobalTransformation(world, vec.offset.blockPos());
        }

        return vec;
    }

    public static Trans3 localToGlobalTransformation(World world, BlockPos pos) {
            IPhysicsEntity inst = getPhysicsEntityFromShipSpace(world, pos);
            if (inst!=null) {
                return new Trans3(inst.transformVector(Vector3.blockCenter(pos).toVec3d(), SUBSPACE_TO_GLOBAL));
            }
        return new Trans3(Vector3.blockCenter(pos));
    }

    public static @NonNull Trans3 localToGlobalTransformation(@NonNull IBlockAccess blockAccess, BlockPos pos) {
        net.minecraft.tileentity.TileEntity te = blockAccess.getTileEntity(pos);
        if (!ENABLED || te==null) return new Trans3(pos);
        return localToGlobalTransformation(te.getWorld(), pos);
    }

    public static @Nullable IPhysicsEntity getPhysicsEntityFromShipSpace(World world, BlockPos pos) {
        if (!ENABLED) return null;
        return IPhysicsEntityManager.INSTANCE.getPhysicsEntityFromShipSpace(world, pos);
    }
}
