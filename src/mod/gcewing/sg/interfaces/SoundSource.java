package gcewing.sg.interfaces;

import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface SoundSource {
    World getSoundWorld();

    BlockPos getSoundPos();

    // Where the sound is heard from. A source bigger than its block can put it at its middle.
    default Vec3d getSoundOrigin() {
        BlockPos pos = getSoundPos();
        return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
    }

    boolean isSoundActive(SoundEvent sound);

    default boolean isSoundRepeatable(SoundEvent sound) {
        return false;
    }

    default float getSoundPitch(SoundEvent sound) {
        return 1F;
    }

    default float getSoundVolume(SoundEvent sound) {
        return 1F;
    }

    @SideOnly(Side.CLIENT)
    default void updateSound(SoundEvent sound) {}
}
