//------------------------------------------------------------------------------------------------
//
//   SG Craft - What every type of stargate is drawn with
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.SGCraft;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateShape;
import gcewing.sg.util.GateType;
import gcewing.sg.util.SGState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;

import static org.lwjgl.opengl.GL11.*;

// Draws a gate's rings and chevrons, in the gate's frame (see SGBaseTERenderer.renderStargate),
// with its type's stargate texture bound before and after. Each type of gate has its own (see
// SGBaseTERenderer.gateRenderer); the event horizon and iris are the same for every gate, and
// SGBaseTERenderer draws them.
abstract class GateRenderer {

    static int chevronEngagementSequences[][] = SGBaseTE.chevronEngagementSequences;

    void render(SGBaseTE te, GateShape shape, float partialTicks) {
        glPushMatrix();
        turnGate(te, partialTicks);
        renderRings(te, shape, partialTicks);
        renderChevrons(te, shape, partialTicks);
        glPopMatrix();
    }

    // Turns the whole gate, chevrons and all, before it is drawn; most gates stay put
    void turnGate(SGBaseTE te, float partialTicks) {
    }

    abstract void renderRings(SGBaseTE te, GateShape shape, float partialTicks);

    // Draws the chevron at position i (0 to 8, with 4 being top dead centre), its centre along x,
    // its lights lit if engaged, with the parts that move to lock a symbol moved the given fraction
    // of the way
    abstract void renderChevron(SGBaseTE te, GateShape shape, int i, boolean engaged, double slide);

    // Whether the chevron at position i, which locks j-th, is lit
    boolean chevronEngaged(SGBaseTE te, int i, int j, float a, float partialTicks) {
        return te.chevronIsEngaged(j);
    }

    void renderChevrons(SGBaseTE te, GateShape shape, float partialTicks) {
        int numChevrons = te.getNumChevrons();
        int i0 = numChevrons > 7 ? 0 : 1;
        int k = chevronSequenceRow(te);
        float a = te.angleBetweenChevrons();
        // A Milky Way gate dialling out moves its top chevron down and back up to lock each symbol,
        // as on the show (following the Carter Addon Pack's SG-1 gate). The top chevron lights
        // while it is down; as it comes back up, the chevron for the symbol lights and the top one
        // goes dark. The last symbol locks at the top chevron itself, more slowly, and it stays lit.
        double dip = 0;
        boolean dipping = false, topLit = false, symbolLit = true;
        if (te.getGateType() == GateType.MILKY_WAY) {
            double t = topChevronLockTime(te, partialTicks);
            int length = te.dialledAddress.length();
            double[] timing = length > 0 && te.numEngagedChevrons >= length ? chevronFinalLockTicks : chevronLockTicks;
            if (t < timing[0] + timing[1] + timing[2]) {
                dipping = true;
                dip = chevronDip(t, timing);
                topLit = t >= timing[0];
                symbolLit = t >= timing[0] + timing[1];
            }
        }
        for (int i = i0; i < i0 + numChevrons; i++) {
            int j = chevronEngagementSequences[k][i];
            boolean engaged = chevronEngaged(te, i, j, a, partialTicks);
            if (dipping && j == te.numEngagedChevrons - 1) {
                engaged = i == 4 ? topLit : symbolLit;
            } else if (dipping && i == 4 && topLit && !symbolLit) {
                engaged = true;
            }
            glPushMatrix();
            glRotatef((float) chevronAngle(te, i, a), 0, 0, 1);
            renderChevron(te, shape, i, engaged, i == 4 ? dip : 0);
            glPopMatrix();
        }
    }

    // How long (ticks) the top chevron takes to move down, stays down, and takes to move back up:
    // for each symbol but the last, within the pause between symbols, and for the last, which
    // takes longer, before the gate opens
    final static double[] chevronLockTicks = {6, 10, 6};
    final static double[] chevronFinalLockTicks = {4, 22, 3};

    // Ticks since this gate, dialling out, last locked a chevron, or a long time if it isn't
    static double topChevronLockTime(SGBaseTE te, float partialTicks) {
        double now = te.getWorld().getTotalWorldTime() + partialTicks;
        int locked = te.numEngagedChevrons;
        if (locked != te.chevronsSeenLocked) {
            boolean dialling = te.isInitiator && locked > te.chevronsSeenLocked
                    && te.state != SGState.Connected && te.state != SGState.Disconnecting;
            te.chevronLockSeenTime = dialling ? Math.floor(now) : -1e9;
            te.chevronsSeenLocked = locked;
        }
        return now - te.chevronLockSeenTime;
    }

    // How far down (0 to 1) the top chevron is, the given number of ticks after it starts locking,
    // easing in and out at each end
    static double chevronDip(double t, double[] timing) {
        double p;
        if (t < timing[0]) {
            p = t / timing[0];
        } else if (t < timing[0] + timing[1]) {
            p = 1;
        } else {
            p = 1 - (t - timing[0] - timing[1]) / timing[2];
        }
        p = Math.max(0, Math.min(1, p));
        return 0.5 - 0.5 * Math.cos(Math.PI * p);
    }

    // The position (0 to 8) of the chevron that locks e-th, or -1 if none does
    static int chevronPosition(SGBaseTE te, int row, int e) {
        return te.chevronPosition(row, e);
    }

    // The angle (degrees) at which renderChevrons draws the chevron at position i
    static double chevronAngle(SGBaseTE te, int i, float a) {
        return 90 - (i - 4) * a;
    }

    // Which row of chevronEngagementSequences gives the order the chevrons lock in
    static int chevronSequenceRow(SGBaseTE te) {
        int k = 0;
        if (te.state == SGState.Disconnecting) {
            // Notes:
            // Keep previously locked chevrons because te.dialledAddress.length = 0 when disconnecting, thus only 7 show locked.
            k = te.numEngagedChevrons > 7 ? 1: 0;
        } else {
            if (!te.chevronsLockOnDial) {
                k = te.dialledAddress.length() > 7 ? 1 : 0;
            } else {
                if (!te.immediateDialAddress.isEmpty()) { //client side variable for renderer only, specifically for pdd.
                    k = te.immediateDialAddress.length() > 7 ? 1 : 0;
                } else {
                    k = te.dialledAddress.length() > 7 ? 1 : 0;
                }
            }
        }
        return k;
    }

    // Gates of every size share their gate type's textures
    static String textureFolder(SGBaseTE te) {
        return te.getGateType().assetName;
    }

    static void bindTexture(String path) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(SGCraft.mod.resourceLocation(path));
    }

    // The gate type's stargate texture, which is bound while the gate is drawn
    static void bindStargateTexture(SGBaseTE te) {
        bindTexture("textures/tileentity/" + textureFolder(te) + "/stargate.png");
    }

    // How many smaller copies of itself a smooth texture is given (see bindSmoothTexture)
    final static int smoothTextureLevels = 10;

    // Binds a texture to be drawn smoothly: blended between its pixels close up, and from smaller
    // copies of itself further off, so that fine detail baked into it doesn't break up into
    // grain or shimmer. Minecraft loads textures with no smaller copies, and only lets them be
    // drawn from the full-size one; that limit, still in place, means the texture is new (or has
    // been loaded again, as by F3+T) and needs them made.
    static void bindSmoothTexture(String path) {
        bindTexture(path);
        if (glGetTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL) != smoothTextureLevels) {
            glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, smoothTextureLevels);
            glTexParameterf(GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LOD, smoothTextureLevels);
            org.lwjgl.opengl.GL30.glGenerateMipmap(GL_TEXTURE_2D);
            // Sharper where the gate is seen at a slant, if the graphics card can
            if (org.lwjgl.opengl.GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                float most = glGetFloat(org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                glTexParameterf(GL_TEXTURE_2D, org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(8, most));
            }
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    }

    // Additive drawing that glows whatever the light around the gate, as lit glyphs and lights do
    static void beginGlow() {
        glDisable(GL_LIGHTING);
        setLightingDisabled(true);
        glEnable(GL_BLEND);
        glDepthMask(false);
        glBlendFunc(GL_ONE, GL_ONE);
    }

    static void endGlow(SGBaseTE te) {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        if (!te.transparency) {
            glDisable(GL_BLEND);
        }
        glDepthMask(true);
        glColor4f(1, 1, 1, 1);
        glEnable(GL_LIGHTING);
        setLightingDisabled(false);
    }

    static void setLightingDisabled(boolean off) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        if (off)
            glDisable(GL_TEXTURE_2D);
        else
            glEnable(GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}
