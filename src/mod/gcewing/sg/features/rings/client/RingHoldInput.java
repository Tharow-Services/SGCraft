//------------------------------------------------------------------------------------------------
//
//   SG Craft - The player's movement keys, stopped while a ring transport holds them
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.features.rings.client;

import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// While a ring transport holds the player (see RingHolds), their keys don't move them; the mouse still turns their head
@SideOnly(Side.CLIENT)
public final class RingHoldInput {

    // When the server last said they're held, or 0; let go anyway after a while, should the server's word be lost
    private static long heldSince = 0;
    private static final long longestHold = 30000;

    private RingHoldInput() {}

    public static void setHeld(boolean held) {
        heldSince = held ? System.currentTimeMillis() : 0;
    }

    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        if (heldSince == 0) {
            return;
        }
        if (System.currentTimeMillis() - heldSince > longestHold) {
            heldSince = 0;
            return;
        }
        MovementInput input = event.getMovementInput();
        input.moveForward = 0;
        input.moveStrafe = 0;
        input.forwardKeyDown = input.backKeyDown = input.leftKeyDown = input.rightKeyDown = false;
        input.jump = false;
        input.sneak = false;
    }
}
