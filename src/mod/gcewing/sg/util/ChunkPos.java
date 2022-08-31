package gcewing.sg.util;

import net.minecraft.util.math.BlockPos;

public class ChunkPos extends net.minecraft.util.math.ChunkPos {
    public final int d;

    public ChunkPos(ChunkPos pos, int dim) {
        super(pos.x, pos.z);
        this.d=dim;
    }

    public ChunkPos(int d, int x, int z) {
        super(x,z);
        this.d=d;
    }
    public ChunkPos(int x, int z) {
        super(x,z); this.d=0;
    }

    public ChunkPos withDim(int dim) {
        return new ChunkPos(this, dim);
    }

    public BlockPos getCenter() {
        return getBlock(8,8,8);
    }
    public SGLocation toLocation() {
        return new SGLocation(d, getCenter());
    }
}