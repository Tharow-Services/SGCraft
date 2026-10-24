package gcewing.sg.features.obelisk.client;

import gcewing.sg.features.obelisk.AsgardObelisks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// What the player being beamed by an Asgard obelisk sees and hears: the beam's sound, played to them alone and not from
// anywhere, so it carries on through the beam; and their screen fading to white as the beam comes, then back. Also a ring
// transport's screen, which goes white, black and white again (screenFade).
@SideOnly(Side.CLIENT)
public final class ObeliskBeamEffects {

    // The beam's sound as it plays to this player, to stop it if the beam is called off
    private static ISound sound;

    // When the current beam started (System.currentTimeMillis), or 0, and when it comes (millis after that)
    private static long start = 0;
    private static long beamAt = 2500;
    // How long the screen takes to go white before the beam, stays white, and takes to clear after it
    private static final long fadeIn = 700, hold = 250, fadeOut = 900;
    // A ring transport's screen instead (see RingTransports.transitScreen): from when it started, at each of these times
    // (millis) how covered the screen is (0 to 1) and how light its cover (0 black, 1 white), going smoothly from each to
    // the next; or null
    private static long fadeStart = 0;
    private static int[] fadeAt;
    private static float[] fadeCover, fadeLight;

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

    // A ring transport's screen: white, black and back, as the server gives it (see fadeAt)
    public static void screenFade(int[] at, float[] cover, float[] light) {
        fadeStart = System.currentTimeMillis();
        fadeAt = at;
        fadeCover = cover;
        fadeLight = light;
    }

    // The player moved, so the beam was called off: the sound stops and the screen clears
    public static void cancel() {
        start = 0;
        fadeAt = null;
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

    // The screen's cover now, as {how covered, how light} (see fadeAt), or null when there is none
    private static float[] cover() {
        int[] at = fadeAt;
        if (at != null) {
            long t = System.currentTimeMillis() - fadeStart;
            if (t >= at[at.length - 1]) {
                fadeAt = null;
            } else {
                for (int k = 1; k < at.length; k++) {
                    if (t < at[k]) {
                        float f = at[k] == at[k - 1] ? 1 : (float) (t - at[k - 1]) / (at[k] - at[k - 1]);
                        f = Math.max(0, f);
                        return new float[] {fadeCover[k - 1] + (fadeCover[k] - fadeCover[k - 1]) * f,
                            fadeLight[k - 1] + (fadeLight[k] - fadeLight[k - 1]) * f};
                    }
                }
            }
        }
        float white = whiteness();
        return white > 0 ? new float[] {white, 1} : null;
    }

    // Over everything once the frame is drawn, the HUD included: drawn with the HUD, it was under the hotbar's items (drawn
    // further forward) and other mods' HUD drawn after it
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.END && mc.world != null && mc.currentScreen == null) {
            drawWhite(mc);
        }
    }

    // With a screen open (the pause menu, say), under it, so it can still be used
    @SubscribeEvent
    public static void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != null) {
            drawWhite(mc);
        }
    }

    private static void drawWhite(Minecraft mc) {
        float[] cover = cover();
        if (cover == null || cover[0] <= 0) {
            return;
        }
        int alpha = (int) (Math.min(1, cover[0]) * 255), grey = (int) (Math.max(0, Math.min(1, cover[1])) * 255);
        ScaledResolution screen = new ScaledResolution(mc);
        mc.entityRenderer.setupOverlayRendering();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        Gui.drawRect(0, 0, screen.getScaledWidth(), screen.getScaledHeight(), alpha << 24 | grey << 16 | grey << 8 | grey);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableDepth();
    }
}
