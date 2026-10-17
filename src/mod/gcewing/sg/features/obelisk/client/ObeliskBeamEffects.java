package gcewing.sg.features.obelisk.client;

import gcewing.sg.features.obelisk.AsgardObelisks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// What the player being beamed by an Asgard obelisk sees and hears: the beam's sound, played to them alone and not from
// anywhere, so it carries on through the beam; and their screen fading to white as the beam comes, then back
@SideOnly(Side.CLIENT)
public final class ObeliskBeamEffects {

    // The beam's sound as it plays to this player, to stop it if the beam is called off
    private static ISound sound;

    // When the current beam started (System.currentTimeMillis), or 0, and when it comes (millis after that)
    private static long start = 0;
    private static long beamAt = 2500;
    // How long the screen takes to go white before the beam, stays white, and takes to clear after it
    private static final long fadeIn = 700, hold = 250, fadeOut = 900;

    private ObeliskBeamEffects() {}

    public static void start(int delayTicks) {
        start = System.currentTimeMillis();
        beamAt = delayTicks * 50L;
        if (AsgardObelisks.beamSound != null) {
            sound = new PositionedSoundRecord(AsgardObelisks.beamSound.getSoundName(), SoundCategory.BLOCKS, 1F, 1F, false, 0,
                ISound.AttenuationType.NONE, 0, 0, 0);
            Minecraft.getMinecraft().getSoundHandler().playSound(sound);
        }
    }

    // The player moved, so the beam was called off: the sound stops and the screen clears
    public static void cancel() {
        start = 0;
        if (sound != null) {
            Minecraft.getMinecraft().getSoundHandler().stopSound(sound);
            sound = null;
        }
    }

    // How white (0 to 1) the screen is now
    private static float whiteness() {
        if (start == 0) {
            return 0;
        }
        long t = System.currentTimeMillis() - start;
        if (t < beamAt - fadeIn) {
            return 0;
        } else if (t < beamAt) {
            return (float) (t - (beamAt - fadeIn)) / fadeIn;
        } else if (t < beamAt + hold) {
            return 1;
        } else if (t < beamAt + hold + fadeOut) {
            return 1 - (float) (t - beamAt - hold) / fadeOut;
        }
        start = 0;
        return 0;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        float white = whiteness();
        if (white <= 0) {
            return;
        }
        ScaledResolution screen = event.getResolution();
        GlStateManager.enableBlend();
        Gui.drawRect(0, 0, screen.getScaledWidth(), screen.getScaledHeight(), ((int) (white * 255) << 24) | 0xFFFFFF);
        GlStateManager.color(1, 1, 1, 1);
    }
}
