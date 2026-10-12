//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate base tile entity renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.BaseGLUtils;
import gcewing.sg.BaseOrientation;
import gcewing.sg.BaseTileEntityRenderer;
import gcewing.sg.block.SGBaseBlock;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.SGCraft;
import gcewing.sg.util.GateShape;
import gcewing.sg.util.GateType;
import gcewing.sg.util.SGAddressing;
import gcewing.sg.util.SGState;
import gcewing.sg.Vector3;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.min;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL;

public class SGBaseTERenderer extends BaseTileEntityRenderer {

    // Ring and event horizon sizes and detail come from the gate's GateShape
    final static double ringOverlap = 1/64.0;
    final static double ringZOffset = 0.0001;
    // How far a gate's chevrons stand proud of the ring's face
    final static double chevronDepth = 1/16.0;
    final static int textureTilesWide = 32;
    final static int textureTilesHigh = 2;
    final static double textureScaleU = 1.0/(textureTilesWide * 16);
    final static double textureScaleV = 1.0/(textureTilesHigh * 16);
    
    final static double ringSymbolTextureLength = 512.0; //27 * 8;
    final static double ringSymbolTextureHeight = 16.0; //12;
    // Where (texels along the symbol strip) the divider before the strip's first glyph is; it
    // falls just before the strip starts
    final static double ringGlyphStripStart = -0.41;
    // How far to turn a Milky Way gate's symbol ring so that at a ring angle of a whole number of
    // glyphs (SGBaseTE.ringGlyphAngle), that glyph is centred at the top. The strip runs clockwise
    // round the ring from the right.
    final static double ringGlyphRotation = 90 - 360 * (1
            - (ringGlyphStripStart + ringSymbolTextureLength / SGBaseTE.numRingGlyphs / 2) / ringSymbolTextureLength);

    // As on the show, about 20 blades, each curling up at its inner end into one point of the
    // twisting star in the middle (see irisHubBlades)
    final static double numIrisBlades = 20;
    
    static int chevronEngagementSequences[][] = SGBaseTE.chevronEngagementSequences;

    double u0, v0;

    @Override
    public void render(TileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {

        SGBaseTE gate = (SGBaseTE)te;
        if (gate.isMerged) {
            glPushMatrix();
            if (gate.transparency) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            } else {
                glDisable(GL_BLEND);
            }

            glEnable(GL_RESCALE_NORMAL);
            glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // The middle of the base block; renderStargate turns from there to the gate's frame
            glTranslated(x + 0.5, y + 0.5, z + 0.5);

            renderStargate(gate, partialTicks);
            glDisable(GL_RESCALE_NORMAL);
            glPopMatrix();
        }
    }

    // Minecraft normally only draws a tile entity while the 16-block section holding it is in
    // view. A supergate rises well out of its base's section, so it would vanish when looking up
    // at it; a global renderer is drawn whenever its render bounding box is in view instead.
    @Override
    public boolean isGlobalRenderer(TileEntity te) {
        // A Universe gate of any size vanished the same way, so is drawn like this too
        return te instanceof SGBaseTE
                && (((SGBaseTE) te).isSupergate() || ((SGBaseTE) te).getGateType() == GateType.UNIVERSE);
    }

    void renderStargate(SGBaseTE gate, float partialTicks) {
        BaseGLUtils.glMultMatrix(gate.localToGlobalTransformation(Vector3.zero));
        GateShape shape = gate.getShape();
        // From the base block's frame into the gate's (see SGBaseTE.toGateFrame): a gate lying
        // flat has its ring running out from its base, its front up or down; then up its ring to
        // the middle, where the gate is drawn from
        if (gate.gateOrientation == 2) {
            glRotatef(-90, 1, 0, 0);
        } else if (gate.gateOrientation == 3) {
            glRotatef(180, 0, 0, 1);
            glRotatef(-90, 1, 0, 0);
        }
        glTranslated(0, shape.centreY, 0);
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + textureFolder(gate) + "/stargate.png"));

        glNormal3f(0, 1, 0);
        glPushMatrix();
        if (gate.getGateType() == GateType.UNIVERSE) {
            // As on the show, a Universe gate turns as a whole, chevrons and all
            glRotatef((float) gate.interpolatedRingAngle(partialTicks), 0, 0, 1);
            renderUniverseRing(gate, shape);
            renderUniverseSymbols(gate, shape);
        } else if (gate.getGateType() == GateType.MILKY_WAY && milkyWayModel() != null) {
            renderMilkyWayRings(gate, shape, partialTicks);
        } else if (gate.getGateType() == GateType.PEGASUS && pegasusModel() != null) {
            renderPegasusRings(gate, shape, partialTicks);
        } else {
            // The rings are polygons, and the inner one turns, so the outer one overlaps it enough
            // to cover the corners of the inner one's polygon at any angle
            renderRing(shape, shape.ringMidRadius - ringOverlap * shape.detailScale, shape.ringOuterRadius, shape.ringDepth, RingType.Outer, ringZOffset * shape.detailScale);
            renderInnerRing(gate, shape, partialTicks);
        }
        renderChevrons(gate, shape, partialTicks);
        glPopMatrix();
        if (gate.hasIrisUpgrade)
            renderIris(gate, partialTicks);
        if (gate.isConnected() && gate.state != SGState.SyncAwait) {
            renderEventHorizon(gate, partialTicks);
        }
    }
    
    // Gates of every size share their gate type's textures
    static String textureFolder(SGBaseTE te) {
        return te.getGateType().assetName;
    }

    // A Milky Way gate's symbol ring turns to bring each symbol to the top. A Pegasus gate's ring of
    // glyph screens stays still and blank, and lights up as it dials (see renderPegasusGlyphs). A
    // Universe gate has a ring of its own (see renderUniverseRing).
    void renderInnerRing(SGBaseTE te, GateShape shape, float partialTicks) {
        boolean pegasus = te.getGateType() == GateType.PEGASUS;
        double ringAngle = pegasus ? 0 : te.interpolatedRingAngle(partialTicks);
        // As on the show, a Milky Way gate's symbol ring turns inside a trim ring that doesn't
        double trim = pegasus ? 0 : innerTrimWidth * shape.detailScale;
        glPushMatrix();
        glRotatef((float)(ringAngle + (pegasus ? SGBaseTE.ringSymbolAngle / 2 : ringGlyphRotation)), 0, 0, 1);
        renderRing(shape, shape.ringInnerRadius + trim, shape.ringMidRadius, shape.ringDepth, RingType.Inner, 0);
        glPopMatrix();
        if (pegasus) {
            renderPegasusScreens(te, shape);
        }
        if (trim > 0) {
            // It overlaps the symbol ring, as the outer ring does, to cover the corners of the
            // symbol ring's polygon at any angle
            renderTrimRing(shape, shape.ringInnerRadius, shape.ringInnerRadius + trim + ringOverlap * shape.detailScale);
        }
        if (pegasus) {
            renderPegasusGlyphs(te, shape, partialTicks);
        }
    }

    // How wide (blocks, on a standard gate) a Milky Way gate's trim ring is, round the inside of
    // its symbol ring
    final static double innerTrimWidth = 1 / 16.0;

    // Draws the trim ring round the inside of a Milky Way gate's symbol ring, between the given
    // radii, with the ridged edge pattern from the bottom of the ring face tile
    void renderTrimRing(GateShape shape, double r1, double r2) {
        int n = shape.ringSegments;
        double[] c = shape.ringCos, s = shape.ringSin;
        // Just in front of the symbol ring where they overlap
        double z = shape.ringDepth / 2 + ringZOffset * shape.detailScale;
        int k = n / GateShape.BASE_RING_SEGMENTS;
        double tw = 16.0 / k;
        glBegin(GL_QUADS);
        for (int i = 0; i < n; i++) {
            double ta = (i % k) * tw, tb = ta + tw;
            // Its inside, facing into the opening
            selectTile(TextureIndex.RING);
            glNormal3d(-c[i], -s[i], 0);
            vertex(r1 * c[i], r1 * s[i], -z, ta, 0);
            vertex(r1 * c[i], r1 * s[i], z, ta, 16);
            vertex(r1 * c[i + 1], r1 * s[i + 1], z, tb, 16);
            vertex(r1 * c[i + 1], r1 * s[i + 1], -z, tb, 0);
            // Back and front
            selectTile(TextureIndex.RING_FACE);
            for (int side = -1; side <= 1; side += 2) {
                glNormal3f(0, 0, side);
                double[][] p = {{r1 * c[i], r1 * s[i]}, {r2 * c[i], r2 * s[i]},
                        {r2 * c[i + 1], r2 * s[i + 1]}, {r1 * c[i + 1], r1 * s[i + 1]}};
                double[][] uv = {{ta, 16}, {ta, 13}, {tb, 13}, {tb, 16}};
                for (int m = 0; m < 4; m++) {
                    int j = side > 0 ? m : 3 - m;
                    vertex(p[j][0], p[j][1], side * z, uv[j][0], uv[j][1]);
                }
            }
        }
        glEnd();
    }

    // The glow of a Pegasus gate's lit glyphs
    final static double pegasusGlowStrength = 1.0;
    final static double[] pegasusGlowColour = {0.3, 0.7, 1.0};
    // The bottom of the symbol strip, below this, is the ring's bevelled edge, which never lights
    final static double pegasusGlyphBandV = 11;
    // The colour of the symbol strip behind the glyphs, which a blank glyph screen shows
    final static double[] pegasusScreenColour = {0.094, 0.12, 0.15};

    // The plain metal of a Pegasus gate's symbol ring: its dividers between screens, and its
    // inner edge
    final static double[] pegasusMetalColour = {0.36, 0.38, 0.42};
    final static double[] pegasusEdgeColour = {0.22, 0.23, 0.26};
    // How much of each screen's width (each side) a divider between screens takes, and how far
    // (blocks, on a standard gate) the dividers stand out of the ring's face
    final static double pegasusDividerWidth = 0.09;
    final static double pegasusDividerHeight = 0.012;

    // Draws a Pegasus gate's symbol ring as on the show: a ring of separate dark screens, one under
    // each chevron and the rest spread evenly between them (see pegasusScreens), with a raised
    // metal divider between each pair, inside a plain metal inner edge. The screens show nothing
    // until lit (see renderPegasusGlyphs). Drawn over the symbol ring's texture, stopping short of
    // the outer ring where it overlaps.
    void renderPegasusScreens(SGBaseTE te, GateShape shape) {
        PegasusScreens screens = pegasusScreens(te);
        double r1 = shape.ringInnerRadius, r2 = shape.ringMidRadius - ringOverlap * shape.detailScale;
        double rb = shape.ringMidRadius - (shape.ringMidRadius - r1) * pegasusGlyphBandV / ringSymbolTextureHeight;
        double k = shape.detailScale;
        double z = shape.ringDepth / 2 + 0.001 * k, zt = z + pegasusDividerHeight * k;
        int n = shape.ringSegments;
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_QUADS);
        for (int s = 0; s < screens.count(); s++) {
            double centre = screens.angles[s], half = screens.widths[s] / 2;
            double gap = screens.widths[s] * pegasusDividerWidth;
            // The screen
            glColor3d(pegasusScreenColour[0], pegasusScreenColour[1], pegasusScreenColour[2]);
            ringBand(rb, r2, centre - half + gap, centre + half - gap, z, n);
            // Its inner edge
            glColor3d(pegasusEdgeColour[0], pegasusEdgeColour[1], pegasusEdgeColour[2]);
            ringBand(r1, rb, centre - half, centre + half, z, n);
            // The divider after it, standing out of the ring's face, with its sides. Its floor
            // covers the symbol ring's texture, and its sides are drawn facing both ways, so that
            // nothing of the texture shows past it from any angle.
            double d0 = centre + half - gap, d1 = centre + half + gap;
            glColor3d(pegasusMetalColour[0], pegasusMetalColour[1], pegasusMetalColour[2]);
            ringBand(rb, r2, d0, d1, z, n);
            ringBand(rb, r2, d0, d1, zt, n);
            for (double d : new double[] {d0, d1}) {
                double a = Math.toRadians(d), side = d == d0 ? -1 : 1;
                double pr = polygonRadius(1, d, n);
                glNormal3d(-Math.sin(a) * side, Math.cos(a) * side, 0);
                double[][] p = {{rb * pr, z}, {r2 * pr, z}, {r2 * pr, zt}, {rb * pr, zt}};
                for (int m = 0; m < 8; m++) {
                    double[] q = p[m < 4 ? m : 7 - m];
                    glVertex3d(q[0] * Math.cos(a), q[0] * Math.sin(a), q[1]);
                }
            }
        }
        glEnd();
        glColor3d(1, 1, 1);
        glEnable(GL_TEXTURE_2D);
    }

    // A flat band of the ring's face between the given radii and angles (degrees), at depth z.
    // Its edges follow the ring's own polygon of the given number of segments rather than chords
    // of their own, so they meet the ring's edges exactly: chords cut across the polygon would
    // leave slivers of the symbol ring's texture showing, which a big gate makes plain to see.
    // Call inside glBegin(GL_QUADS).
    static void ringBand(double r1, double r2, double from, double to, double z, int segments) {
        glNormal3f(0, 0, 1);
        double step = 360.0 / segments;
        double a0 = from;
        while (a0 < to - 1e-9) {
            // To the next corner of the polygon, or the band's end
            double a1 = Math.min(to, (Math.floor(a0 / step + 1e-9) + 1) * step);
            double p0 = polygonRadius(1, a0, segments), p1 = polygonRadius(1, a1, segments);
            double c0 = Math.cos(Math.toRadians(a0)), s0 = Math.sin(Math.toRadians(a0));
            double c1 = Math.cos(Math.toRadians(a1)), s1 = Math.sin(Math.toRadians(a1));
            glVertex3d(r1 * p0 * c0, r1 * p0 * s0, z);
            glVertex3d(r2 * p0 * c0, r2 * p0 * s0, z);
            glVertex3d(r2 * p1 * c1, r2 * p1 * s1, z);
            glVertex3d(r1 * p1 * c1, r1 * p1 * s1, z);
            a0 = a1;
        }
    }

    // How far from the centre, at the given angle (degrees), the edge of a ring polygon of the
    // given number of segments and corner radius r is
    static double polygonRadius(double r, double angle, int segments) {
        double step = 360.0 / segments;
        double mid = (Math.floor(angle / step + 1e-9) + 0.5) * step;
        return r * Math.cos(Math.toRadians(step / 2)) / Math.cos(Math.toRadians(angle - mid));
    }

    // A Pegasus gate's glyph screens: one centred under each chevron, and the rest spread evenly
    // between them about a glyph apart, so they line up with the chevrons however far apart those
    // are. Angles (degrees) rise anticlockwise round the ring.
    static class PegasusScreens {
        double[] angles, widths;

        int count() {
            return angles.length;
        }

        // The screen nearest the given angle
        int at(double angle) {
            int best = 0;
            double bestDistance = 360;
            for (int k = 0; k < angles.length; k++) {
                double d = Math.abs(((angle - angles[k]) % 360 + 540) % 360 - 180);
                if (d < bestDistance) {
                    best = k;
                    bestDistance = d;
                }
            }
            return best;
        }
    }

    PegasusScreens pegasusScreens(SGBaseTE te) {
        int n = te.getNumChevrons();
        int i0 = n > 7 ? 0 : 1;
        float a = te.angleBetweenChevrons();
        double[] chevrons = new double[n];
        for (int k = 0; k < n; k++) {
            chevrons[k] = (chevronAngle(te, i0 + k, a) % 360 + 360) % 360;
        }
        Arrays.sort(chevrons);
        int[] between = new int[n];
        int count = 0;
        for (int k = 0; k < n; k++) {
            double gap = k + 1 < n ? chevrons[k + 1] - chevrons[k] : chevrons[0] + 360 - chevrons[k];
            between[k] = Math.max(1, (int) Math.round(gap / SGBaseTE.ringSymbolAngle));
            count += between[k];
        }
        PegasusScreens screens = new PegasusScreens();
        screens.angles = new double[count];
        screens.widths = new double[count];
        int s = 0;
        for (int k = 0; k < n; k++) {
            double gap = k + 1 < n ? chevrons[k + 1] - chevrons[k] : chevrons[0] + 360 - chevrons[k];
            for (int t = 0; t < between[k]; t++, s++) {
                screens.angles[s] = chevrons[k] + gap * t / between[k];
                screens.widths[s] = gap / between[k];
            }
        }
        return screens;
    }

    // How far round (degrees, clockwise from the top) a Pegasus gate being dialled has lit its
    // ring, or -1 if it isn't one
    double pegasusFill(SGBaseTE te, float partialTicks) {
        if (te.getGateType() != GateType.PEGASUS || te.isInitiator
                || te.state == SGState.Idle || te.state == SGState.Disconnecting) {
            return -1;
        }
        int length = te.dialledAddress.length();
        if (length > 0 && te.numEngagedChevrons >= length) {
            return 360;
        }
        if (te.pegasusFillEnd <= 0) {
            return 0;
        }
        double now = te.getWorld().getTotalWorldTime() + partialTicks;
        double p = (now - te.pegasusFillStart) / Math.max(1, te.pegasusFillEnd - te.pegasusFillStart);
        return 360 * Math.max(0, Math.min(1, p));
    }

    // Whether a ring lit that far round (see pegasusFill) has reached the given angle
    boolean fillReaches(SGBaseTE te, double fill, double angle) {
        double top = chevronAngle(te, 4, te.angleBetweenChevrons());
        double d = ((top - angle) % 360 + 360) % 360;
        return (d < 1e-6 ? 360 : d) <= fill + 1e-6;
    }

    // Lights a Pegasus gate's glyph screens as the show's gates do (following the Carter Addon
    // Pack's Atlantis gate). Only the glyphs light; the screens stay dark behind them.
    //  - Dialling out, a single light steps round the ring a screen at a time to each chevron in
    //    turn (see SGBaseTE.pegasusLightTravel), and the glyph under each locked chevron stays lit.
    //  - Dialled into, the ring stays dark until the last few seconds of the dial, then fills
    //    with light clockwise from the top, lighting each chevron as it reaches it, so that the
    //    whole ring is lit as the last chevron locks (see pegasusFill).
    void renderPegasusGlyphs(SGBaseTE te, GateShape shape, float partialTicks) {
        PegasusScreens screens = pegasusScreens(te);
        boolean[] lit = pegasusLit(te, screens, partialTicks);
        if (lit == null) {
            return;
        }
        int count = screens.count();

        // Lit glyphs glow regardless of the light around the gate. The glyph sheet is the glyphs
        // alone on black, added on top of the screens: once in their own colour, then twice more
        // in blue for the glow.
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + textureFolder(te) + "/glyphs.png"));
        beginGlow();
        double k = pegasusGlowStrength;
        PegasusGateModel model = pegasusModel();
        for (int layer = 1; layer <= 3; layer++) {
            if (layer == 1) {
                glColor4f(1, 1, 1, 1);
            } else {
                glColor4d(pegasusGlowColour[0] * k, pegasusGlowColour[1] * k, pegasusGlowColour[2] * k, 1);
            }
            for (int s = 0; s < count; s++) {
                if (lit[s]) {
                    litGlyph(shape, screens.angles[s], screens.widths[s] * (1 - 2 * pegasusDividerWidth), pegasusGlyphAt(screens.angles[s]),
                            pegasusGlyphBandV, model, layer);
                }
            }
        }
        endGlow(te);
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + textureFolder(te) + "/stargate.png"));
    }

    // Which glyph is on the screen at the given angle: the glyphs go clockwise round the ring from
    // the one at the top, each a screen apart, in the order of the gate's symbol strip
    static int pegasusGlyphAt(double angle) {
        int k = (int) Math.round((90 - angle) / SGBaseTE.ringSymbolAngle);
        return (k % pegasusGlyphs + pegasusGlyphs) % pegasusGlyphs;
    }

    // Which of a Pegasus gate's screens are lit (see renderPegasusGlyphs), or null if none are
    boolean[] pegasusLit(SGBaseTE te, PegasusScreens screens, float partialTicks) {
        int count = screens.count();
        boolean[] lit = new boolean[count];
        boolean any = false;
        int row = chevronSequenceRow(te);
        float a = te.angleBetweenChevrons();
        int length = te.dialledAddress.length();
        int locked = Math.min(te.numEngagedChevrons, length);
        if (te.isInitiator) {
            for (int e = 0; e < locked; e++) {
                int i = chevronPosition(te, row, e);
                if (i >= 0) {
                    lit[screens.at(chevronAngle(te, i, a))] = any = true;
                }
            }
            if (te.state == SGState.Dialling && locked < length) {
                int i = chevronPosition(te, row, locked);
                int previous = locked == 0 ? 4 : chevronPosition(te, row, locked - 1);
                double travel = te.targetRingAngle - te.startRingAngle;
                if (i >= 0 && previous >= 0 && travel != 0) {
                    double p = (te.interpolatedRingAngle(partialTicks) - te.startRingAngle) / travel;
                    p = Math.max(0, Math.min(1, p));
                    // The screens the light passes on its way, going as SGBaseTE.pegasusLightTravel
                    // does: the long way round, with an extra turn rather than a short hop
                    int to = screens.at(chevronAngle(te, i, a));
                    int from = screens.at(chevronAngle(te, previous, a));
                    int direction = travel > 0 ? 1 : -1;
                    int steps = ((direction * (to - from)) % count + count) % count;
                    if (steps == 0 || steps * 360.0 / count <= 240) {
                        steps += count;
                    }
                    int left = (int) Math.floor((1 - p) * steps + 1e-6);
                    lit[((to - direction * left) % count + count) % count] = any = true;
                }
            }
        } else {
            double fill = pegasusFill(te, partialTicks);
            if (fill > 0) {
                for (int k = 0; k < count; k++) {
                    if (fillReaches(te, fill, screens.angles[k])) {
                        lit[k] = any = true;
                    }
                }
            }
        }
        return any ? lit : null;
    }

    // The glyph sheet holds this many glyphs, in cells of a square grid this many wide
    final static int pegasusGlyphs = 36;
    final static int pegasusGlyphGrid = 6;
    // How far in from the edges of a Pegasus gate's screen its glyph stops, as a fraction of the
    // screen's height
    final static double pegasusGlyphMargin = 0.12;

    // Draws a glyph from the glyph sheet on the screen centred at the given angle (degrees) and
    // the given width, over the symbol strip down to the given row of it, in the current colour;
    // or, given a Pegasus gate's model, on its screens
    void litGlyph(GateShape shape, double centre, double width, int glyph, double bandV, PegasusGateModel model, int layer) {
        double cell = 1.0 / pegasusGlyphGrid;
        double u0 = (glyph % pegasusGlyphGrid) * cell, v0 = (glyph / pegasusGlyphGrid) * cell;
        double r1 = shape.ringInnerRadius, r2 = shape.ringMidRadius;
        double rb = r2 - (r2 - r1) * bandV / ringSymbolTextureHeight;
        double z = shape.ringDepth / 2 + 0.002 * layer * shape.detailScale, zb = z;
        if (model != null) {
            // On the model's screens, a little short of their edges, on their sloping surface
            double s = pegasusScale(shape), sz = pegasusDepthScale(shape);
            double in = model.screenInner, out = model.screenOuter, margin = (out - in) * pegasusGlyphMargin;
            rb = (in + margin) * s;
            r2 = (out - margin) * s;
            zb = (model.screenZ(in + margin) + 0.002 * layer) * sz;
            z = (model.screenZ(out - margin) + 0.002 * layer) * sz;
        }
        int steps = 4;
        glBegin(GL_QUADS);
        glNormal3f(0, 0, 1);
        for (int s = 0; s < steps; s++) {
            double a0 = Math.toRadians(centre - width / 2 + width * s / steps);
            double a1 = Math.toRadians(centre - width / 2 + width * (s + 1) / steps);
            // Upright at the top of the ring: the glyph's left is anticlockwise, its top outwards
            double ua = u0 + cell * (1 - (double) s / steps), ub = u0 + cell * (1 - (double) (s + 1) / steps);
            glTexCoord2d(ua, v0 + cell);
            glVertex3d(rb * Math.cos(a0), rb * Math.sin(a0), zb);
            glTexCoord2d(ua, v0);
            glVertex3d(r2 * Math.cos(a0), r2 * Math.sin(a0), z);
            glTexCoord2d(ub, v0);
            glVertex3d(r2 * Math.cos(a1), r2 * Math.sin(a1), z);
            glTexCoord2d(ub, v0 + cell);
            glVertex3d(rb * Math.cos(a1), rb * Math.sin(a1), zb);
        }
        glEnd();
    }

    // ---- Pegasus gate ---------------------------------------------------------------------------

    // A Pegasus gate is drawn from a model of the gate on the show (see PegasusGateModel), made into
    // display lists for each size of gate, one for each of its groups
    static PegasusGateModel pegasusModel;
    static boolean pegasusModelFailed;
    static final Map<String, Integer> pegasusLists = new HashMap<>();
    static final String[] pegasusGroups = {"front", "back", "outer", "rim", "wall", "screen", "separator",
            "chevron", "chevron_dark", "light_block", "light_bars"};
    // How dark the engraved parts of its chevrons are against the polished metal
    final static double pegasusEngravedDarkness = 0.45;
    // Its chevrons' lights: the block above each, and the bars and lower part, lit and unlit
    final static double[] pegasusBlockLitColour = {1, 1, 1}, pegasusBlockIdleColour = {0.3, 0.36, 0.45};
    final static double[] pegasusBarsLitColour = {0.7, 0.88, 1.0}, pegasusBarsIdleColour = {0.42, 0.45, 0.5};

    static PegasusGateModel pegasusModel() {
        if (pegasusModel == null && !pegasusModelFailed) {
            try (InputStream in = SGBaseTERenderer.class.getResourceAsStream("/assets/sgcraft/models/gate/pegasus_gate.txt")) {
                pegasusModel = PegasusGateModel.load(in);
            } catch (Exception e) {
                pegasusModelFailed = true;
                System.out.printf("SGCraft: Unable to load the Pegasus gate model: %s\n", e);
            }
        }
        return pegasusModel;
    }

    static double pegasusScale(GateShape shape) {
        return shape.ringOuterRadius / pegasusModel.outerRadius;
    }

    static double pegasusDepthScale(GateShape shape) {
        return GateShape.STANDARD.ringOuterRadius / pegasusModel.outerRadius * shape.ringDepth / GateShape.STANDARD.ringDepth;
    }

    // The gate's rings, fixed as on the show: the ring itself and the inner ring's screens, with
    // the glyphs on them lit as it dials (see renderPegasusGlyphs)
    void renderPegasusRings(SGBaseTE te, GateShape shape, float partialTicks) {
        int lists = pegasusLists(pegasusModel(), shape);
        glPushMatrix();
        glRotatef((float) (modelTop(te) - 90), 0, 0, 1);
        glColor3d(1, 1, 1);
        bindSmoothTexture("textures/tileentity/pegasus/front.png");
        glCallList(lists + pegasusGroup("front"));
        bindSmoothTexture("textures/tileentity/pegasus/back.png");
        glCallList(lists + pegasusGroup("back"));
        bindSmoothTexture("textures/tileentity/pegasus/outer.png");
        glCallList(lists + pegasusGroup("outer"));
        bindSmoothTexture("textures/tileentity/pegasus/metal.png");
        glCallList(lists + pegasusGroup("rim"));
        glCallList(lists + pegasusGroup("wall"));
        glCallList(lists + pegasusGroup("separator"));
        glPopMatrix();
        renderPegasusCrystalScreens(te, shape, lists, partialTicks);
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + te.getGateType().assetName + "/stargate.png"));
    }

    // A Pegasus gate's glyphs, from the reference model's (see tools/pegasus/glyphs.js): each a
    // constellation of domes standing out of its screen, in pegasus_glyphs.txt as groups glyph0 to
    // glyph35, in the reference's order
    static PegasusGateModel pegasusGlyphModel;
    static boolean pegasusGlyphModelFailed;
    static final Map<String, Integer> pegasusGlyphLists = new HashMap<>();
    // Which of the reference's glyphs each of the gate's symbols is, in the order of its symbol
    // strip (as on the DHD)
    final static int[] pegasusReferenceGlyph = {
            6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
            24, 25, 28, 32, 29, 31, 34, 33, 26, 27, 30, 35, 0, 1, 2, 3, 4, 5};
    // The screens' crystal is pegasusCrystalSize metres across its texture; each screen shows a
    // different part of it, as the screens were cut from different parts of a block of it
    final static double pegasusCrystalSize = 2;
    // The glyphs' colour: unlit, a dark blue in the crystal, lit by the light round the gate; lit,
    // glowing, and then glowing again in blue on top, so that they bloom. Only the glyphs light,
    // never the screens behind them.
    final static double[] pegasusGlyphIdleColour = {0.16, 0.26, 0.45};
    final static double[] pegasusGlyphLitColour = {0.8, 0.95, 1.0};

    static PegasusGateModel pegasusGlyphModel() {
        if (pegasusGlyphModel == null && !pegasusGlyphModelFailed) {
            try (InputStream in = SGBaseTERenderer.class.getResourceAsStream("/assets/sgcraft/models/gate/pegasus_glyphs.txt")) {
                pegasusGlyphModel = PegasusGateModel.load(in);
            } catch (Exception e) {
                pegasusGlyphModelFailed = true;
                System.out.printf("SGCraft: Unable to load the Pegasus gate's glyphs: %s\n", e);
            }
        }
        return pegasusGlyphModel;
    }

    // The display lists of the glyphs for the given size of gate, one for each, as they are at
    // the top screen
    int pegasusGlyphLists(PegasusGateModel glyphs, GateShape shape) {
        String key = shape.ringOuterRadius + ":" + shape.ringDepth;
        Integer lists = pegasusGlyphLists.get(key);
        if (lists != null) {
            return lists;
        }
        double s = pegasusScale(shape), sz = pegasusDepthScale(shape);
        int base = GLAllocation.generateDisplayLists(pegasusGlyphs);
        for (int g = 0; g < pegasusGlyphs; g++) {
            glNewList(base + g, GL_COMPILE);
            glBegin(GL_TRIANGLES);
            List<double[]> tris = glyphs.group("glyph" + g), normals = glyphs.cornerNormals("glyph" + g);
            for (int i = 0; i < tris.size(); i++) {
                double[] t = tris.get(i), n = normals.get(i);
                for (int v = 0; v < 3; v++) {
                    // Normals scale the other way to the corners
                    double nx = n[3 * v] / s, ny = n[3 * v + 1] / s, nz = n[3 * v + 2] / sz, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    glNormal3d(nx / nl, ny / nl, nz / nl);
                    glTexCoord2d(t[5 * v + 3], t[5 * v + 4]);
                    glVertex3d(t[5 * v] * s, t[5 * v + 1] * s, t[5 * v + 2] * sz);
                }
            }
            glEnd();
            glEndList();
        }
        pegasusGlyphLists.put(key, base);
        return base;
    }

    // The gate's glyph screens: blue crystal, each with its glyph standing out of it, dark until
    // the gate lights it (see pegasusLit)
    void renderPegasusCrystalScreens(SGBaseTE te, GateShape shape, int lists, float partialTicks) {
        PegasusScreens screens = pegasusScreens(te);
        boolean[] lit = pegasusLit(te, screens, partialTicks);
        PegasusGateModel model = pegasusModel();
        // The screens, the crystal across each laid at its own size
        bindSmoothTexture("textures/tileentity/pegasus/crystal.png");
        glColor3d(1, 1, 1);
        double across = Math.toRadians(PegasusGateModel.SCREEN_ANGLE) * (model.screenInner + model.screenOuter) / 2 / pegasusCrystalSize;
        double down = (model.screenOuter - model.screenInner) / pegasusCrystalSize;
        for (int s = 0; s < screens.count(); s++) {
            glMatrixMode(GL_TEXTURE);
            glPushMatrix();
            glTranslated((s * 0.37) % 1, (s * 0.61) % 1, 0);
            glScaled(across, down, 1);
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glRotatef((float) (screens.angles[s] - 90), 0, 0, 1);
            glCallList(lists + pegasusGroup("screen"));
            glPopMatrix();
            glMatrixMode(GL_TEXTURE);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
        }
        // The glyphs: unlit ones, then lit ones glowing, then those again added in blue
        PegasusGateModel glyphs = pegasusGlyphModel();
        if (glyphs == null) {
            return;
        }
        int glyphLists = pegasusGlyphLists(glyphs, shape);
        bindSmoothTexture("textures/tileentity/pegasus/star.png");
        for (int pass = 0; pass < 3; pass++) {
            if (pass == 1) {
                if (lit == null) {
                    break;
                }
                glDisable(GL_LIGHTING);
                setLightingDisabled(true);
            }
            double[] c = pass == 0 ? pegasusGlyphIdleColour : pegasusGlyphLitColour;
            if (pass == 2) {
                glEnable(GL_BLEND);
                glDepthMask(false);
                glDepthFunc(GL_LEQUAL);
                glBlendFunc(GL_ONE, GL_ONE);
                c = new double[] {pegasusGlowColour[0] * pegasusGlowStrength, pegasusGlowColour[1] * pegasusGlowStrength,
                        pegasusGlowColour[2] * pegasusGlowStrength};
            }
            glColor3d(c[0], c[1], c[2]);
            for (int s = 0; s < screens.count(); s++) {
                if ((lit != null && lit[s]) != (pass > 0)) {
                    continue;
                }
                glPushMatrix();
                glRotatef((float) (screens.angles[s] - 90), 0, 0, 1);
                glCallList(glyphLists + pegasusReferenceGlyph[pegasusGlyphAt(screens.angles[s])]);
                glPopMatrix();
            }
        }
        if (lit != null) {
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            if (!te.transparency) {
                glDisable(GL_BLEND);
            }
            glDepthMask(true);
            glEnable(GL_LIGHTING);
            setLightingDisabled(false);
        }
        glColor3d(1, 1, 1);
    }

    // Draws a Pegasus gate's chevron, its centre along x, its lights lit if engaged
    void pegasusChevron(SGBaseTE te, GateShape shape, boolean engaged) {
        int lists = pegasusLists(pegasusModel(), shape);
        glPushMatrix();
        // The model's chevron is at the top
        glRotatef(-90, 0, 0, 1);
        bindSmoothTexture("textures/tileentity/pegasus/metal.png");
        glColor3d(1, 1, 1);
        glCallList(lists + pegasusGroup("chevron"));
        double d = pegasusEngravedDarkness;
        glColor3d(d, d, d);
        glCallList(lists + pegasusGroup("chevron_dark"));
        if (engaged) {
            glDisable(GL_LIGHTING);
            setLightingDisabled(true);
        }
        // The block above it lit from within, a pale spot fading to deep blue (see
        // tools/pegasus/chevronlight.js), dimmed when unlit
        bindSmoothTexture("textures/tileentity/pegasus/chevron_light.png");
        double[] c = engaged ? pegasusBlockLitColour : pegasusBlockIdleColour;
        glColor3d(c[0], c[1], c[2]);
        glCallList(lists + pegasusGroup("light_block"));
        glDisable(GL_TEXTURE_2D);
        c = engaged ? pegasusBarsLitColour : pegasusBarsIdleColour;
        glColor3d(c[0], c[1], c[2]);
        glCallList(lists + pegasusGroup("light_bars"));
        if (engaged) {
            glEnable(GL_LIGHTING);
            setLightingDisabled(false);
        }
        glEnable(GL_TEXTURE_2D);
        glColor3d(1, 1, 1);
        glPopMatrix();
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + te.getGateType().assetName + "/stargate.png"));
    }

    // How many smaller copies of itself a smooth texture is given (see bindSmoothTexture)
    final static int smoothTextureLevels = 10;

    // Binds a texture to be drawn smoothly: blended between its pixels close up, and from smaller
    // copies of itself further off, so that fine detail baked into it doesn't break up into
    // grain or shimmer. Minecraft loads textures with no smaller copies, and only lets them be
    // drawn from the full-size one; that limit, still in place, means the texture is new (or has
    // been loaded again, as by F3+T) and needs them made.
    void bindSmoothTexture(String path) {
        bindTexture(SGCraft.mod.resourceLocation(path));
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

    static int pegasusGroup(String name) {
        return Arrays.asList(pegasusGroups).indexOf(name);
    }

    // The display lists for the given size of gate, one for each group (pegasusGroups): the fixed
    // ring's turned round all nine of its ninths, the screens and dividers round all 36 places
    int pegasusLists(PegasusGateModel model, GateShape shape) {
        String key = shape.ringOuterRadius + ":" + shape.ringDepth;
        Integer lists = pegasusLists.get(key);
        if (lists != null) {
            return lists;
        }
        double s = pegasusScale(shape), sz = pegasusDepthScale(shape);
        int base = GLAllocation.generateDisplayLists(pegasusGroups.length);
        for (int g = 0; g < pegasusGroups.length; g++) {
            String name = pegasusGroups[g];
            boolean ring = name.equals("front") || name.equals("back") || name.equals("outer") || name.equals("rim") || name.equals("wall");
            // The screens are drawn one at a time, each with its own glyph (see
            // renderPegasusCrystalScreens), their texture across each from 0 to 1: from its left
            // (anticlockwise) edge to its right, and from its outside in, as the glyph stands
            boolean screen = name.equals("screen"), slot = name.equals("separator");
            int copies = ring ? PegasusGateModel.CHEVRONS : slot ? PegasusGateModel.SCREENS : 1;
            double half = PegasusGateModel.SCREEN_ANGLE / 2, in = model.screenInner, out = model.screenOuter;
            double step = ring ? PegasusGateModel.SECTION_ANGLE : PegasusGateModel.SCREEN_ANGLE;
            glNewList(base + g, GL_COMPILE);
            glBegin(GL_TRIANGLES);
            List<double[]> tris = model.group(name), normals = model.cornerNormals(name);
            // The light above the chevron has its texture across it from in front, over its bounds
            boolean light = name.equals("light_block");
            double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            for (double[] t : light ? tris : java.util.Collections.<double[]>emptyList()) {
                for (int v = 0; v < 3; v++) {
                    x0 = Math.min(x0, t[5 * v]); x1 = Math.max(x1, t[5 * v]);
                    y0 = Math.min(y0, t[5 * v + 1]); y1 = Math.max(y1, t[5 * v + 1]);
                }
            }
            for (int k = 0; k < copies; k++) {
                double a = Math.toRadians(k * step), c = Math.cos(a), sn = Math.sin(a);
                for (int i = 0; i < tris.size(); i++) {
                    double[] t = tris.get(i), n = normals.get(i);
                    for (int v = 0; v < 3; v++) {
                        // Normals scale the other way to the corners
                        double nx = (n[3 * v] * c - n[3 * v + 1] * sn) / s, ny = (n[3 * v] * sn + n[3 * v + 1] * c) / s;
                        double nz = n[3 * v + 2] / sz, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                        glNormal3d(nx / nl, ny / nl, nz / nl);
                        double x = t[5 * v], y = t[5 * v + 1], z = t[5 * v + 2];
                        if (screen) {
                            double angle = Math.toDegrees(Math.atan2(y, x)), r = Math.hypot(x, y);
                            glTexCoord2d(Math.max(0, Math.min(1, (90 + half - angle) / (2 * half))),
                                    Math.max(0, Math.min(1, (out - r) / (out - in))));
                        } else if (light) {
                            glTexCoord2d((x - x0) / (x1 - x0), (y1 - y) / (y1 - y0));
                        } else {
                            glTexCoord2d(t[5 * v + 3], t[5 * v + 4]);
                        }
                        glVertex3d((x * c - y * sn) * s, (x * sn + y * c) * s, z * sz);
                    }
                }
            }
            glEnd();
            glEndList();
        }
        pegasusLists.put(key, base);
        return base;
    }

    // ---- Milky Way gate ------------------------------------------------------------------------

    // A Milky Way gate is drawn from a model of the gate on the show (see MilkyWayGateModel), made
    // into display lists for each size of gate: the fixed ring's body and detail, the symbol ring's
    // cone and glyphs, and the chevron's metal and lights, each split by how its corners move as the
    // top chevron locks a symbol
    static MilkyWayGateModel milkyWayModel;
    static boolean milkyWayModelFailed;
    static final Map<String, Integer> milkyWayLists = new HashMap<>();
    // The metal each part of the model is drawn in (its lights are coloured glass, see
    // milkyWayChevron), repeating across it every this many metres
    static final String[] milkyWayMetals = {"ring", "detail", "symbols", "glyphs", "chevron", "light"};
    final static double milkyWayMetalRepeat = 1.2;
    // How many steps round the gate its rings are turned in, at least; more on a bigger gate, as
    // many as four to each of its ring's segments
    final static int milkyWaySteps = 180;
    // A chevron's lights are glass, its colour shading across it as on the show, laid over the
    // whole chevron from milkyWayLightHalf metres either side of its middle, and from
    // milkyWayLightTop down to milkyWayLightBottom from the gate's centre: lit, as it is, glowing;
    // unlit, this much as bright, lit by what is round it. Each way the lights move as the top
    // chevron locks (see MilkyWayGateModel) is one of them, with its own glass: as the model has
    // them, the slatted lights beside the V slide down with it, and the triangle above rises.
    final static String[] milkyWayLightGlass = {"light", "slats", "light"};
    final static double milkyWayLightHalf = 0.54, milkyWayLightTop = 6.09, milkyWayLightBottom = 5.45;
    final static double idleMilkyWayLightBrightness = 0.3;

    static MilkyWayGateModel milkyWayModel() {
        if (milkyWayModel == null && !milkyWayModelFailed) {
            try (InputStream in = SGBaseTERenderer.class.getResourceAsStream("/assets/sgcraft/models/gate/milkyway_gate.txt")) {
                milkyWayModel = MilkyWayGateModel.load(in);
            } catch (Exception e) {
                milkyWayModelFailed = true;
                System.out.printf("SGCraft: Unable to load the Milky Way gate model: %s\n", e);
            }
        }
        return milkyWayModel;
    }

    static double milkyWayScale(GateShape shape) {
        return shape.ringOuterRadius / MilkyWayGateModel.OUTER_RADIUS;
    }

    static double milkyWayDepthScale(GateShape shape) {
        return GateShape.STANDARD.ringOuterRadius / MilkyWayGateModel.OUTER_RADIUS * shape.depthScale();
    }

    // Where the top chevron of a gate drawn from a model is in the gate's frame (see
    // renderStargate): at the top, however the gate stands
    static double modelTop(SGBaseTE te) {
        return 90;
    }

    // The gate's rings: the fixed ring, and the symbol ring turned to its angle, with its point of
    // origin under the top chevron at rest
    void renderMilkyWayRings(SGBaseTE te, GateShape shape, float partialTicks) {
        MilkyWayGateModel model = milkyWayModel();
        int lists = milkyWayLists(model, shape);
        glPushMatrix();
        glRotatef((float) (modelTop(te) - 90), 0, 0, 1);
        glColor3d(1, 1, 1);
        for (int part = MilkyWayGateModel.BODY; part <= MilkyWayGateModel.GLYPHS; part++) {
            if (part == MilkyWayGateModel.SYMBOL_RING) {
                glRotatef((float) te.interpolatedRingAngle(partialTicks), 0, 0, 1);
            }
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/milkyway/" + milkyWayMetals[part] + ".png"));
            glCallList(lists + part);
        }
        glPopMatrix();
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + te.getGateType().assetName + "/stargate.png"));
    }

    // Draws a Milky Way gate's chevron, its centre along x, its lights lit if engaged. The parts of
    // the top chevron that move as it locks a symbol are moved the given fraction of the way. As
    // on the show, a locked chevron lights its slatted lights and the top of its triangle, over
    // the ring's rim; the top chevron lights the whole triangle while it is locking a symbol.
    void milkyWayChevron(SGBaseTE te, GateShape shape, boolean engaged, double slide) {
        MilkyWayGateModel model = milkyWayModel();
        int lists = milkyWayLists(model, shape), moves = model.moveCount();
        double s = milkyWayScale(shape), sz = milkyWayDepthScale(shape);
        boolean locking = engaged && slide > 0;
        glPushMatrix();
        // The model's chevron is at the top
        glRotatef(-90, 0, 0, 1);
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/milkyway/chevron.png"));
        for (int piece = 0; piece < MilkyWayGateModel.PIECES; piece++) {
            boolean lit = piece == MilkyWayGateModel.LIGHTS ? engaged : piece == MilkyWayGateModel.LOWER_LIGHT && locking;
            double c = piece == MilkyWayGateModel.METAL || lit ? 1 : idleMilkyWayLightBrightness;
            glColor3d(c, c, c);
            if (lit) {
                glDisable(GL_LIGHTING);
                setLightingDisabled(true);
            }
            for (int m = 0; m < moves; m++) {
                double[] d = model.move(m);
                if (piece != MilkyWayGateModel.METAL) {
                    String glass = milkyWayLightGlass[Math.min(m, milkyWayLightGlass.length - 1)];
                    bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/milkyway/" + glass + ".png"));
                }
                glPushMatrix();
                glTranslated(d[0] * s * slide, d[1] * s * slide, d[2] * sz * slide);
                glCallList(lists + MilkyWayGateModel.CHEVRON + piece * moves + m);
                glPopMatrix();
            }
            if (lit) {
                glEnable(GL_LIGHTING);
                setLightingDisabled(false);
            }
        }
        glColor3d(1, 1, 1);
        glPopMatrix();
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + te.getGateType().assetName + "/stargate.png"));
    }

    // The display lists for the given size of gate: one for each of the rings' parts (BODY to
    // GLYPHS), then from CHEVRON, each piece of the chevron (MilkyWayGateModel.METAL to
    // LOWER_LIGHT), one for each way their corners move
    int milkyWayLists(MilkyWayGateModel model, GateShape shape) {
        int steps = Math.max(milkyWaySteps, shape.ringSegments * 4);
        String key = shape.ringOuterRadius + ":" + shape.ringDepth + ":" + steps;
        Integer lists = milkyWayLists.get(key);
        if (lists != null) {
            return lists;
        }
        int moves = model.moveCount(), count = MilkyWayGateModel.CHEVRON + MilkyWayGateModel.PIECES * moves;
        List<List<double[]>> tris = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tris.add(new ArrayList<>());
        }
        int[] into = new int[1];
        MilkyWayGateModel.Sink sink = (part, a, b, c, n) -> tris.get(part < MilkyWayGateModel.CHEVRON ? part : into[0]).add(
                new double[] {a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], n[0], n[1], n[2]});
        model.buildFixed(sink, (int) Math.ceil(steps / (360 / MilkyWayGateModel.COLUMN)));
        model.buildRing(sink, steps);
        for (int piece = 0; piece < MilkyWayGateModel.PIECES; piece++) {
            for (int m = 0; m < moves; m++) {
                into[0] = MilkyWayGateModel.CHEVRON + piece * moves + m;
                model.buildChevron(sink, piece, m);
            }
        }
        double s = milkyWayScale(shape), sz = milkyWayDepthScale(shape);
        int base = GLAllocation.generateDisplayLists(count);
        for (int i = 0; i < count; i++) {
            glNewList(base + i, GL_COMPILE);
            if (i >= MilkyWayGateModel.CHEVRON + moves) {
                lightTriangles(tris.get(i), s, sz);
            } else {
                modelTriangles(tris.get(i), s, sz, 1 / milkyWayMetalRepeat);
            }
            glEndList();
        }
        milkyWayLists.put(key, base);
        return base;
    }

    // Draws a Milky Way chevron's lights, as modelTriangles, with the glass laid over the chevron
    static void lightTriangles(List<double[]> tris, double s, double sz) {
        double h = milkyWayLightHalf, top = milkyWayLightTop, bottom = milkyWayLightBottom;
        glBegin(GL_TRIANGLES);
        for (double[] t : tris) {
            double nx = t[9] / s, ny = t[10] / s, nz = t[11] / sz, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            glNormal3d(nx / nl, ny / nl, nz / nl);
            for (int v = 0; v < 3; v++) {
                double x = t[3 * v], y = t[3 * v + 1], z = t[3 * v + 2];
                glTexCoord2d((x + h) / (2 * h), (top - y) / (top - bottom));
                glVertex3d(x * s, y * s, z * sz);
            }
        }
        glEnd();
    }

    // Draws triangles {ax, ay, az, bx, ..., cz, nx, ny, nz} of a model, scaled by s across the gate
    // and sz through it, with a metal laid flat on whichever of the gate's planes each faces most,
    // repeating every 1 / k of the model's units
    static void modelTriangles(List<double[]> tris, double s, double sz, double k) {
        glBegin(GL_TRIANGLES);
        for (double[] t : tris) {
            // Normals scale the other way to the corners
            double nx = t[9] / s, ny = t[10] / s, nz = t[11] / sz, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            glNormal3d(nx / nl, ny / nl, nz / nl);
            double ax = Math.abs(t[9]), ay = Math.abs(t[10]), az = Math.abs(t[11]);
            for (int v = 0; v < 3; v++) {
                double x = t[3 * v], y = t[3 * v + 1], z = t[3 * v + 2];
                if (az >= ax && az >= ay) {
                    glTexCoord2d(x * k, y * k);
                } else if (ax >= ay) {
                    glTexCoord2d(y * k, z * k);
                } else {
                    glTexCoord2d(x * k, z * k);
                }
                glVertex3d(x * s, y * s, z * sz);
            }
        }
        glEnd();
    }

    // ---- Universe gate ---------------------------------------------------------------------

    // A Universe gate is drawn from its own model (see UniverseGateModel), scaled to the gate:
    // across, so its outer edge is the ring's; through, as much as the standard gate's is, times
    // however much deeper the gate's ring is than the standard one's.
    static UniverseGateModel universeModel;
    static boolean universeModelFailed;
    // Display lists of the model, one for each of its parts in turn, for each size of gate
    static final Map<String, Integer> universeLists = new HashMap<>();
    // The metal each part of the model is drawn in, repeating across it every this many of the
    // model's units
    static final String[] universeMetals = {"ring", "chevron", "frame", "detail"};
    final static double universeMetalRepeat = 0.6;

    static UniverseGateModel universeModel() {
        if (universeModel == null && !universeModelFailed) {
            try (InputStream in = SGBaseTERenderer.class.getResourceAsStream("/assets/sgcraft/models/gate/universe_gate.txt")) {
                universeModel = UniverseGateModel.load(in);
            } catch (Exception e) {
                universeModelFailed = true;
                System.out.printf("SGCraft: Unable to load the Universe gate model: %s\n", e);
            }
        }
        return universeModel;
    }

    static double universeScale(GateShape shape) {
        return shape.ringOuterRadius / UniverseGateModel.OUTER_RADIUS;
    }

    static double universeDepthScale(GateShape shape) {
        return GateShape.STANDARD.ringOuterRadius / UniverseGateModel.OUTER_RADIUS * shape.depthScale();
    }

    // Where a Universe gate's top chevron is in the gate's frame: horizontal gates lying face up
    // have it the other way round
    static double universeTop(SGBaseTE te) {
        return te.gateOrientation == 2 ? -90 : 90;
    }

    // The gate's ring and chevrons, without its lights and symbols. Like the other gates, it has
    // seven chevrons, the bottom two left out, until it is given the chevron upgrade.
    void renderUniverseRing(SGBaseTE te, GateShape shape) {
        UniverseGateModel model = universeModel();
        if (model == null) {
            return;
        }
        int lists = universeLists(model, shape);
        glPushMatrix();
        glRotatef((float) (universeTop(te) - 90), 0, 0, 1);
        glColor3d(1, 1, 1);
        for (int part = 0; part < UniverseGateModel.PARTS; part++) {
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/universe/" + universeMetals[part] + ".png"));
            if (part != UniverseGateModel.CHEVRON) {
                glCallList(lists + part);
                continue;
            }
            // The chevrons the gate has, as renderChevrons places them: chevron i is the model's
            // (i - 4)th clockwise from the top
            int n = te.getNumChevrons(), i0 = n > 7 ? 0 : 1;
            for (int i = i0; i < i0 + n; i++) {
                int slot = ((i - 4) % UniverseGateModel.CHEVRONS + UniverseGateModel.CHEVRONS) % UniverseGateModel.CHEVRONS;
                glCallList(lists + UniverseGateModel.PARTS + slot);
            }
        }
        glPopMatrix();
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + textureFolder(te) + "/stargate.png"));
    }

    int universeLists(UniverseGateModel model, GateShape shape) {
        int steps = Math.max(180, shape.ringSegments * 4);
        String key = shape.ringOuterRadius + ":" + shape.ringDepth + ":" + steps;
        Integer lists = universeLists.get(key);
        if (lists != null) {
            return lists;
        }
        List<List<double[]>> parts = new ArrayList<>();
        for (int part = 0; part < UniverseGateModel.PARTS; part++) {
            parts.add(new ArrayList<>());
        }
        model.build((part, a, b, c, n) -> parts.get(part).add(new double[] {
                a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], n[0], n[1], n[2]}), steps);
        // The chevrons apart, one list each after the parts' (the chevron part's own left empty),
        // so that a gate can leave some out: by which of the model's, clockwise from the top,
        // each triangle is nearest
        for (int m = 0; m < UniverseGateModel.CHEVRONS; m++) {
            parts.add(new ArrayList<>());
        }
        for (double[] t : parts.get(UniverseGateModel.CHEVRON)) {
            double angle = Math.toDegrees(Math.atan2(t[1] + t[4] + t[7], t[0] + t[3] + t[6]));
            int m = (int) Math.round((90 - angle) / UniverseGateModel.SECTION_ANGLE);
            m = (m % UniverseGateModel.CHEVRONS + UniverseGateModel.CHEVRONS) % UniverseGateModel.CHEVRONS;
            parts.get(UniverseGateModel.PARTS + m).add(t);
        }
        parts.get(UniverseGateModel.CHEVRON).clear();
        double s = universeScale(shape), sz = universeDepthScale(shape), k = 1 / universeMetalRepeat;
        int base = GLAllocation.generateDisplayLists(parts.size());
        for (int part = 0; part < parts.size(); part++) {
            glNewList(base + part, GL_COMPILE);
            glBegin(GL_TRIANGLES);
            for (double[] t : parts.get(part)) {
                // Normals scale the other way to the corners
                double nx = t[9] / s, ny = t[10] / s, nz = t[11] / sz, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                glNormal3d(nx / nl, ny / nl, nz / nl);
                // The metal is laid flat on whichever of the gate's planes it faces most
                double ax = Math.abs(t[9]), ay = Math.abs(t[10]), az = Math.abs(t[11]);
                for (int v = 0; v < 3; v++) {
                    double x = t[3 * v], y = t[3 * v + 1], z = t[3 * v + 2];
                    if (az >= ax && az >= ay) {
                        glTexCoord2d(x * k, y * k);
                    } else if (ax >= ay) {
                        glTexCoord2d(y * k, z * k);
                    } else {
                        glTexCoord2d(x * k, z * k);
                    }
                    glVertex3d(x * s, y * s, z * sz);
                }
            }
            glEnd();
            glEndList();
        }
        universeLists.put(key, base);
        return base;
    }

    // The centre (degrees anticlockwise from the right, with the top chevron at 90) of a Universe
    // gate's symbol
    static double universeSymbolAngle(int symbol) {
        return 90 - SGBaseTE.universeSymbolOffset(symbol);
    }

    // How much darker a Universe gate's unlit symbols are than the panels they are cut into (a
    // fraction of the panel's brightness taken away), and the glow of its locked symbols
    final static double universeSymbolCut = 0.4;
    final static double[] universeGlowColour = {0.6, 0.75, 1.0};
    // How many times a locked symbol is drawn in white, for a bright core, and then how strong the
    // glow it gives off round it is (universe/glyphs_glow.png, from tools/UniverseGlyphGlow.java)
    final static int universeCoreLayers = 2;
    final static double universeGlowStrength = 1.6;

    // The inside edge of a Universe gate's arches, as measured with the model: (degrees either side
    // of the arch's centre, radius) from the bottom up to the point of the arch
    final static double[][] universeArchInside = {
        {2.716, 2.669}, {2.685, 2.778}, {2.364, 2.946}, {2.141, 3.022}, {1.624, 3.099},
        {1.272, 3.137}, {0.809, 3.168}, {0.480, 3.183}, {0.143, 3.189}, {0, 3.191}
    };

    // A Universe gate's symbols, as on the show, each on the panel filling its arch: cut into
    // the ring's face, and lit as the symbol locks, staying lit while the gate is open. Each glows
    // where it is cut.
    void renderUniverseSymbols(SGBaseTE te, GateShape shape) {
        glPushMatrix();
        glRotatef((float) (universeTop(te) - 90), 0, 0, 1);
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/universe/panel.png"));
        glColor3d(1, 1, 1);
        for (int symbol = 0; symbol < SGBaseTE.numRingSymbols; symbol++) {
            universePanel(shape, universeSymbolAngle(symbol));
        }
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/universe/glyphs.png"));
        glDisable(GL_LIGHTING);
        setLightingDisabled(true);
        glEnable(GL_BLEND);
        glDepthMask(false);
        // Cut: the panel made darker where the symbol is white, so that unlit it reads as cut in
        // rather than as dimly lit
        glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_COLOR);
        double cut = universeSymbolCut;
        glColor3d(cut, cut, cut);
        for (int symbol = 0; symbol < SGBaseTE.numRingSymbols; symbol++) {
            universeSymbol(shape, symbol, 0.0006);
        }
        // Lit: adding light where it is white, in white for a bright core, then in blue where its
        // blurred glow is, for the light it gives off round it
        int locked = te.isActive() ? Math.min(te.numEngagedChevrons, te.dialledAddress.length()) : 0;
        if (locked > 0) {
            glBlendFunc(GL_ONE, GL_ONE);
            for (int layer = 0; layer <= universeCoreLayers; layer++) {
                if (layer < universeCoreLayers) {
                    glColor3d(1, 1, 1);
                } else {
                    double g = universeGlowStrength;
                    bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/universe/glyphs_glow.png"));
                    glColor3d(universeGlowColour[0] * g, universeGlowColour[1] * g, universeGlowColour[2] * g);
                }
                for (int e = 0; e < locked; e++) {
                    int symbol = SGAddressing.charToSymbol(te.dialledAddress.charAt(e));
                    if (symbol >= 0) {
                        universeSymbol(shape, symbol, 0.0008 + 0.0004 * layer);
                    }
                }
            }
        }
        endGlow(te);
        glPopMatrix();
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + textureFolder(te) + "/stargate.png"));
    }

    // The panel filling an arch, centred at the given angle, just in front of the ring's face,
    // in its own metal
    void universePanel(GateShape shape, double centre) {
        double s = universeScale(shape), sz = universeDepthScale(shape), k = 1 / universeMetalRepeat;
        double[][] p = universeArchInside;
        glNormal3d(0, 0, 1);
        glBegin(GL_QUADS);
        for (int i = 0; i + 1 < p.length; i++) {
            // Anticlockwise seen from in front: bottom left, bottom right, top right, top left
            double[][] q = {{centre + p[i][0], p[i][1]}, {centre - p[i][0], p[i][1]},
                    {centre - p[i + 1][0], p[i + 1][1]}, {centre + p[i + 1][0], p[i + 1][1]}};
            for (double[] c : q) {
                double a = Math.toRadians(c[0]), x = c[1] * Math.cos(a), y = c[1] * Math.sin(a);
                glTexCoord2d(x * k, y * k);
                glVertex3d(x * s, y * s, (UniverseGateModel.faceZ(c[1]) + 0.0003) * sz);
            }
        }
        glEnd();
    }

    // The symbol's panel, a little in front of the ring's face (by the given model units), with the
    // symbol's cell of the glyph sheet over it. As on the model, the symbols are on the front only.
    // The sheet's cells are the panels measured with the model: GLYPH_HALF degrees either side of
    // the arch's centre, from GLYPH_INNER out to GLYPH_OUTER.
    final static double universeGlyphHalf = 2.0, universeGlyphInner = 2.7424, universeGlyphOuter = 3.1404;

    void universeSymbol(GateShape shape, int symbol, double lift) {
        double s = universeScale(shape), sz = universeDepthScale(shape);
        double centre = universeSymbolAngle(symbol), half = universeGlyphHalf;
        double cell = 1.0 / 6, u0 = symbol % 6 * cell, v0 = symbol / 6 * cell;
        double r0 = universeGlyphInner, r1 = universeGlyphOuter;
        double z0 = UniverseGateModel.faceZ(r0) + lift, z1 = UniverseGateModel.faceZ(r1) + lift;
        int pieces = 6;
        glNormal3d(0, 0, 1);
        glBegin(GL_QUADS);
        for (int i = 0; i < pieces; i++) {
            // Left to right across the symbol: anticlockwise to clockwise
            double fa = (double) i / pieces, fb = (double) (i + 1) / pieces;
            double aa = Math.toRadians(centre + half * (1 - 2 * fa));
            double ab = Math.toRadians(centre + half * (1 - 2 * fb));
            double ua = u0 + cell * fa, ub = u0 + cell * fb;
            // Anticlockwise seen from in front: bottom left, bottom right, top right, top left
            double[][] p = {
                    {r0 * Math.cos(aa), r0 * Math.sin(aa), z0, ua, v0 + cell},
                    {r0 * Math.cos(ab), r0 * Math.sin(ab), z0, ub, v0 + cell},
                    {r1 * Math.cos(ab), r1 * Math.sin(ab), z1, ub, v0},
                    {r1 * Math.cos(aa), r1 * Math.sin(aa), z1, ua, v0}};
            for (double[] q : p) {
                glTexCoord2d(q[3], q[4]);
                glVertex3d(q[0] * s, q[1] * s, q[2] * sz);
            }
        }
        glEnd();
    }

    // Additive drawing that glows whatever the light around the gate, as lit glyphs and lights do
    void beginGlow() {
        glDisable(GL_LIGHTING);
        setLightingDisabled(true);
        glEnable(GL_BLEND);
        glDepthMask(false);
        glBlendFunc(GL_ONE, GL_ONE);
    }

    void endGlow(SGBaseTE te) {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        if (!te.transparency) {
            glDisable(GL_BLEND);
        }
        glDepthMask(true);
        glColor4f(1, 1, 1, 1);
        glEnable(GL_LIGHTING);
        setLightingDisabled(false);
    }

    // How much darker a Universe gate's chevron lights are than the metal round them when unlit,
    // like dark glass; and lit, the colour of them and of the glow round them, as on the model: white
    final static double idleUniverseLightDarkening = 0.6;
    final static double[] universeLightColour = {1.0, 1.0, 1.0};
    final static double[] universeLightGlowColour = {0.35, 0.35, 0.35};
    // Where a Universe chevron's lights are: a mask, white where it glows, laid over the chevron
    // from CHEVRON_LIGHTS_HALF degrees anticlockwise of its centre (left) to as far clockwise
    // (right), and from CHEVRON_LIGHTS_OUTER (top) in to CHEVRON_LIGHTS_INNER (bottom); and its
    // glow, the same spread out. They are measured from the Destiny gate model by
    // tools/universe/chevmask.js and maskclean.js.
    final static double universeChevronLightsHalf = 17;
    final static double universeChevronLightsInner = 2.60, universeChevronLightsOuter = 3.40;
    // The lights are only on faces turned at least this much forward (the z of their normal), not
    // the steep sides of the chevron's steps
    final static double universeLightFacing = 0.5;

    // Draws a Universe gate's chevron's lights, on both faces: the chevron's own surface (see
    // UniverseGateModel.chevronSurface) again, with the light mask over it, added to what is
    // there, and lit, again with the glow round them; and first its painted studs (measured by
    // tools/universe/chevpaint.js and paintmask.js). The chevron itself is part of the gate's
    // model (see renderUniverseRing). Its centre is along x. The back is the front mirrored
    // through the ring, lights and all.
    void universeChevron(SGBaseTE te, GateShape shape, boolean engaged) {
        UniverseGateModel model = universeModel();
        if (model == null) {
            return;
        }
        // Drawn over the chevron's surface without fighting it
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(-1, -1);
        // Its painted bronze studs, lit like the metal they are on
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/universe/chevron_paint.png"));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glColor3d(1, 1, 1);
        universeChevronSurface(model, shape);
        // Its lights
        beginGlow();
        if (engaged) {
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/universe/chevron_glow.png"));
            glColor3d(universeLightGlowColour[0], universeLightGlowColour[1], universeLightGlowColour[2]);
            universeChevronSurface(model, shape);
            glColor3d(universeLightColour[0], universeLightColour[1], universeLightColour[2]);
        } else {
            // Unlit: darkening the chevron where the lights are
            glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_COLOR);
            double dark = idleUniverseLightDarkening;
            glColor3d(dark, dark, dark);
        }
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/universe/chevron_lights.png"));
        universeChevronSurface(model, shape);
        glDisable(GL_POLYGON_OFFSET_FILL);
        endGlow(te);
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + textureFolder(te) + "/stargate.png"));
    }

    // The forward faces of a Universe chevron, on both faces of the ring, with the light mask (or
    // its glow, whichever is bound) laid over them
    void universeChevronSurface(UniverseGateModel model, GateShape shape) {
        double s = universeScale(shape), sz = universeDepthScale(shape);
        double half = universeChevronLightsHalf;
        double r0 = universeChevronLightsInner, r1 = universeChevronLightsOuter;
        glBegin(GL_TRIANGLES);
        for (int face = -1; face <= 1; face += 2) {
            glNormal3d(0, 0, face);
            for (double[] t : model.chevronSurface()) {
                double ux = t[3] - t[0], uy = t[4] - t[1], uz = t[5] - t[2];
                double vx = t[6] - t[0], vy = t[7] - t[1], vz = t[8] - t[2];
                double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                if (nz < universeLightFacing * Math.sqrt(nx * nx + ny * ny + nz * nz)) {
                    continue;
                }
                for (int n = 0; n < 3; n++) {
                    // Anticlockwise seen from in front of the face
                    int k = face > 0 ? n : 2 - n;
                    double x = t[3 * k], y = t[3 * k + 1], z = t[3 * k + 2];
                    double d = -Math.toDegrees(Math.atan2(y, x)), r = Math.hypot(x, y);
                    glTexCoord2d((d + half) / (2 * half), (r1 - r) / (r1 - r0));
                    glVertex3d(x * s, y * s, z * face * sz);
                }
            }
        }
        glEnd();
    }

    void renderRing(GateShape shape, double r1, double r2, double depth, RingType type, double dz) {
        double z = depth / 2 + dz;
        double u = 0, du = 0, dv = 0;
        int n = shape.ringSegments;
        double[] c = shape.ringCos, s = shape.ringSin;
        // Ring and face tiles each span one segment of a 32-segment ring, so with more segments
        // each segment shows its share of a tile rather than a squashed whole one
        int k = n / GateShape.BASE_RING_SEGMENTS;
        double tw = 16.0 / k;
        double symbolWidth = ringSymbolTextureLength / n;
        glBegin(GL_QUADS);
        for (int i = 0; i < n; i++) {
            double ta = (i % k) * tw, tb = ta + tw;
            selectTile(TextureIndex.RING);
            switch (type) {
                case Outer:
                    glNormal3d(c[i], s[i], 0);
                    vertex(r2 * c[i], r2 * s[i], z, ta, 0);
                    vertex(r2 * c[i], r2 * s[i], -z, ta, 16);
                    vertex(r2 * c[i + 1], r2 * s[i + 1], -z, tb, 16);
                    vertex(r2 * c[i + 1], r2 * s[i + 1], z, tb, 0);
                    break;
                case Inner:
                    glNormal3d(-c[i], -s[i], 0);
                    vertex(r1 * c[i], r1 * s[i], -z, ta, 0);
                    vertex(r1 * c[i], r1 * s[i], z, ta, 16);
                    vertex(r1 * c[i + 1], r1 * s[i + 1], z, tb, 16);
                    vertex(r1 * c[i + 1], r1 * s[i + 1], -z, tb, 0);
                    break;
            }
            // Back
            glNormal3f(0, 0, -1);
            vertex(r1*c[i],   r1*s[i],   -z,   ta, 16);
            vertex(r1*c[i+1], r1*s[i+1], -z,   tb, 16);
            vertex(r2*c[i+1], r2*s[i+1], -z,   tb,  0);
            vertex(r2*c[i],   r2*s[i],   -z,   ta,  0);
            // Front
            glNormal3f(0, 0, 1);
            switch (type) {
                case Outer:
                    selectTile(TextureIndex.RING_FACE);
                    u = 16 - tb;
                    du = tw;
                    dv = 16;
                    break;
                case Inner:
                    selectTile(TextureIndex.RING_SYMBOL);
                    u = ringSymbolTextureLength - (i + 1) * symbolWidth;
                    du = symbolWidth;
                    dv = ringSymbolTextureHeight;
                    break;
            }
            vertex(r1*c[i],   r1*s[i],    z,   u+du, dv);
            vertex(r2*c[i],   r2*s[i],    z,   u+du,  0);
            vertex(r2*c[i+1], r2*s[i+1],  z,   u,     0);
            vertex(r1*c[i+1], r1*s[i+1],  z,   u,    dv);
        }

        glEnd();
    }
    
    void renderChevrons(SGBaseTE te, GateShape shape, float partialTicks) {
        int numChevrons = te.getNumChevrons();
        int i0 = numChevrons > 7 ? 0 : 1;
        int k = chevronSequenceRow(te);
        float a = te.angleBetweenChevrons();
        // A Pegasus gate being dialled lights its chevrons as the light filling its ring reaches them
        double fill = pegasusFill(te, partialTicks);
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
            // A Universe gate lights all its chevrons together as soon as it starts dialling
            boolean engaged = fill >= 0
                ? j < te.dialledAddress.length() && fillReaches(te, fill, chevronAngle(te, i, a))
                : te.getGateType() == GateType.UNIVERSE ? te.isActive() : te.chevronIsEngaged(j);
            if (dipping && j == te.numEngagedChevrons - 1) {
                engaged = i == 4 ? topLit : symbolLit;
            } else if (dipping && i == 4 && topLit && !symbolLit) {
                engaged = true;
            }
            renderChevronAtPosition(te, shape, i, a, engaged, i == 4 ? dip : 0);
        }
    }

    // How long (ticks) the top chevron takes to move down, stays down, and takes to move back up:
    // for each symbol but the last, within the pause between symbols, and for the last, which
    // takes longer, before the gate opens
    final static double[] chevronLockTicks = {6, 10, 6};
    final static double[] chevronFinalLockTicks = {4, 22, 3};

    // Ticks since this gate, dialling out, last locked a chevron, or a long time if it isn't
    double topChevronLockTime(SGBaseTE te, float partialTicks) {
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
    double chevronDip(double t, double[] timing) {
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
    int chevronPosition(SGBaseTE te, int row, int e) {
        return te.chevronPosition(row, e);
    }

    // The angle (degrees) at which renderChevronAtPosition draws the chevron at position i
    double chevronAngle(SGBaseTE te, int i, float a) {
        return 90 - (i - 4) * a;
    }

    // Which row of chevronEngagementSequences gives the order the chevrons lock in
    int chevronSequenceRow(SGBaseTE te) {
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

    // Render a chevron at the given position (0 to 8, with 4 being top dead centre)
    // with its light extended the given fraction of the way down to lock a symbol
    void renderChevronAtPosition(SGBaseTE gate, GateShape shape, int i, float a, boolean engaged, double slide) {
        glPushMatrix();

        glRotatef((float) chevronAngle(gate, i, a), 0, 0, 1);

        GateType type = gate.getGateType();
        if (type == GateType.UNIVERSE) {
            universeChevron(gate, shape, engaged);
        } else if (type == GateType.MILKY_WAY && milkyWayModel() != null) {
            milkyWayChevron(gate, shape, engaged, slide);
        } else if (gate.getGateType() == GateType.PEGASUS && pegasusModel() != null) {
            pegasusChevron(gate, shape, engaged);
        } else {
            // A Pegasus gate's top chevron is like the others, and stays put as it locks
            boolean master = i == 4 && type == GateType.MILKY_WAY;
            gateChevron(new GateChevron(shape, master ? masterChevronScale : 1, type == GateType.PEGASUS), engaged,
                    master ? masterChevronTravel * shape.detailScale * slide : 0);
        }
        glPopMatrix();
    }

    // How much wider the top chevron of a Milky Way gate is than the others
    final static double masterChevronScale = 1.15;

    // How far down the symbols on the symbol ring the V of a chevron reaches, as on the show:
    // halfway on a Milky Way gate, under a third of the way down the screens on a Pegasus gate
    final static double milkyWayChevronReach = 0.5;
    final static double pegasusChevronReach = 0.35;

    // The shape of a gate's chevron, as on the show. It is two parts: a light, a solid block
    // narrowing inwards from the ring's outer edge, standing a little proud of a metal outer
    // layer. The outer layer is a V whose arms run down either side of the light and join below it,
    // reaching down onto the symbols, with a row of slatted lights set in beside each arm. Locking
    // a symbol, a Milky Way gate's top chevron's outer layer slides down and back up, leaving the
    // light where it is. A Pegasus gate's chevrons are the same shape in its own colours. Distances
    // are outwards from the gate's centre (x) and either side of its middle (y).
    static class GateChevron {
        final boolean pegasus;
        final double width, top, lightTopX, lightBottom, slatBottom, bottom;
        final double wingTipX, wingTipY, wingTipThickness, wingBottom;
        final double armZ, lightZ, slatZ, backZ;

        GateChevron(GateShape shape, double scale, boolean pegasus) {
            this.pegasus = pegasus;
            double ring = shape.ringOuterRadius - shape.ringInnerRadius;
            double k = shape.detailScale;
            width = ring * 0.8 * scale;
            top = ringEdgeAt(shape, width / 2);
            double length = ring * 0.68;
            lightBottom = top - length * 0.64;
            slatBottom = top - length * 0.74;
            // The V reaches down onto the symbols: on a Milky Way gate, those inside its trim ring;
            // on a Pegasus gate, its glyph screens, which stop short of the ring's inner edge
            double symbolsInner = pegasus
                    ? shape.ringMidRadius - (shape.ringMidRadius - shape.ringInnerRadius) * pegasusGlyphBandV / ringSymbolTextureHeight
                    : shape.ringInnerRadius + innerTrimWidth * k;
            double reach = pegasus ? pegasusChevronReach : milkyWayChevronReach;
            bottom = shape.ringMidRadius - (shape.ringMidRadius - symbolsInner) * reach;
            // The wings run out along the ring's outer edge from the top of the slatted lights,
            // sloping down into the ring's face
            wingTipY = width * 1.3;
            wingTipX = ringEdgeAt(shape, wingTipY);
            wingTipThickness = length * 0.05;
            wingBottom = top - length * 0.2;
            // Its depth goes with the ring's thickness rather than its size
            double kz = shape.depthScale();
            backZ = shape.ringDepth / 2;
            armZ = backZ + chevronDepth * kz;
            lightZ = backZ + chevronDepth * kz * 1.3;
            // The light's top is flush with the ring's outer edge
            lightTopX = ringEdgeAt(shape, lightTop());
            slatZ = backZ + chevronDepth * kz * 0.45;
        }

        // Half widths: of the light at its top and bottom; of the outer layer at its top, at the
        // bottom of the light, and at its bottom; and of the slatted lights at their outer edge,
        // at the top and bottom
        double lightTop() { return width * 0.2; }
        double lightBase() { return width * 0.07; }
        double armTop() { return width * 0.33; }
        double armMid() { return width * 0.24; }
        double armBase() { return width * 0.15; }
        double slatTop() { return width * 0.5; }
        double slatBase() { return width * 0.36; }
        // Where the inner edge of the slatted lights, along the outside of the arm, ends
        double slatInnerBase() {
            return armTop() + (armMid() - armTop()) * (top - slatBottom) / (top - lightBottom);
        }
        // Half the width of the outer edge of the arms, the given distance out from the centre
        double armOuterAt(double x) {
            return x >= lightBottom
                ? armTop() + (armMid() - armTop()) * (top - x) / (top - lightBottom)
                : armMid() + (armBase() - armMid()) * (lightBottom - x) / (lightBottom - bottom);
        }
        // Half the width of the slatted lights' outer edge, the given distance out from the centre
        double slatOuterAt(double x) {
            return slatTop() + (slatBase() - slatTop()) * (top - x) / (top - slatBottom);
        }
    }

    // How bright an unlit chevron's light and slatted lights are
    final static double idleChevronLightBrightness = 0.45;
    final static double idlePegasusChevronLightBrightness = 0.25;
    final static double idleChevronSlatBrightness = 0.2;

    // Draws a gate's chevron (see GateChevron), its lights lit if engaged, with its
    // front outer layer moved the given distance down. As on the show, the outer layer is on both
    // faces of the ring, each with its own light; nothing of the chevron shows on the ring's outer
    // edge between them.
    void gateChevron(GateChevron c, boolean engaged, double drop) {
        glPushMatrix();
        glTranslated(-drop, 0, 0);
        chevronOuterLayer(c, engaged);
        glPopMatrix();
        chevronWings(c);

        // The back is the front mirrored through the ring, which turns its faces inside out
        glPushMatrix();
        glScaled(1, 1, -1);
        glFrontFace(GL_CW);
        chevronOuterLayer(c, engaged);
        chevronWings(c);
        glFrontFace(GL_CCW);
        glPopMatrix();

        chevronLight(c, engaged);
    }

    // Selects the metal a gate's chevron is made of. A Milky Way chevron uses the middle of the
    // chevron tile; that part of the Pegasus texture is plain flat grey, paler than the ring, so a
    // Pegasus chevron is made of the ring's own metal instead, as on the show.
    void selectChevronMetal(GateChevron c) {
        selectTile(c.pegasus ? TextureIndex.RING : TextureIndex.CHEVRON);
    }

    // Draws the parts of a gate's chevron that stay put while its outer layer moves, on
    // the front of the ring: the wings, and the sides of the recess the slatted lights are in
    void chevronWings(GateChevron c) {
        double at = c.armTop(), st = c.slatTop(), sb = c.slatBase(), si = c.slatInnerBase();
        glColor3d(1, 1, 1);
        selectChevronMetal(c);
        glBegin(GL_QUADS);
        // The sides and top of the slatted lights' recess
        for (int side = -1; side <= 1; side += 2) {
            quadFacing(new double[][] {{c.top, side * st, c.slatZ}, {c.slatBottom, side * sb, c.slatZ},
                    {c.slatBottom, side * sb, c.backZ}, {c.top, side * st, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 0, side, 0);
            quadFacing(new double[][] {{c.slatBottom, side * si, c.slatZ}, {c.slatBottom, side * sb, c.slatZ},
                    {c.slatBottom, side * sb, c.backZ}, {c.slatBottom, side * si, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, -1, 0, 0);
            quadFacing(new double[][] {{c.top, side * at, c.slatZ}, {c.top, side * st, c.slatZ},
                    {c.top, side * st, c.backZ}, {c.top, side * at, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 1, 0, 0);
        }
        // The wings at the top: slabs as high as the arms, running out along the ring's outer edge
        // from against the arm, over the top of the slatted lights, to a point
        double wy = c.wingTipY, wx = c.wingTipX, wb = c.wingBottom, sw = c.armOuterAt(wb);
        double tipZ = c.armZ;
        for (int side = -1; side <= 1; side += 2) {
            quadFacing(new double[][] {{wx, side * wy, tipZ}, {wx - c.wingTipThickness, side * wy, tipZ},
                    {wx - c.wingTipThickness, side * wy, c.backZ}, {wx, side * wy, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 0, side, 0);
            double[][] wing = {{c.top, side * at, c.armZ}, {wx, side * wy, tipZ},
                    {wx - c.wingTipThickness, side * wy, tipZ}, {wb, side * sw, c.armZ}};
            double[] n = faceNormal(wing[0], wing[1], wing[3]);
            quadFacing(wing, new double[][] {{6, 4}, {10, 4}, {10, 6}, {6, 12}}, n[0], n[1], n[2]);
            // Its edge along the ring's outer edge, its lower edge, and its inner edge against the arm,
            // which shows when the top chevron's arm slides down
            quadFacing(new double[][] {{c.top, side * at, c.armZ}, {wx, side * wy, tipZ},
                    {wx, side * wy, c.backZ}, {c.top, side * at, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 1, 0, 0);
            quadFacing(new double[][] {{wb, side * sw, c.armZ}, {wx - c.wingTipThickness, side * wy, tipZ},
                    {wx - c.wingTipThickness, side * wy, c.backZ}, {wb, side * sw, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, -1, 0, 0);
            quadFacing(new double[][] {{c.top, side * at, c.armZ}, {wb, side * sw, c.armZ},
                    {wb, side * sw, c.backZ}, {c.top, side * at, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 0, -side, 0);
        }
        glEnd();
    }

    // Draws the moving part of a gate chevron's outer layer, on the front of the ring:
    // the V and the slatted lights beside it
    void chevronOuterLayer(GateChevron c, boolean engaged) {
        double lt = c.lightTop(), lb = c.lightBase();
        double at = c.armTop(), am = c.armMid(), ab = c.armBase();
        double sb = c.slatBase(), si = c.slatInnerBase();

        // The V, in the plain middle of the chevron tile
        glColor3d(1, 1, 1);
        selectChevronMetal(c);
        glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) {
            prism(new double[] {c.top, c.top, c.lightBottom, c.lightBottom},
                    new double[] {side * lt, side * at, side * am, side * lb}, c.armZ, c.backZ, 6, 10);
        }
        prism(new double[] {c.lightBottom, c.lightBottom, c.bottom, c.bottom},
                new double[] {-am, am, ab, -ab}, c.armZ, c.backZ, 6, 10);
        glEnd();

        // The slatted lights: a row of bars stepping down the recess beside each arm, below the
        // wing, standing out of the recess's dark floor. Unlit they are plain metal; lit, their
        // fronts glow orange whatever the light around the gate.
        double wb = c.wingBottom, sw = c.slatOuterAt(wb);
        double barZ = c.slatZ + (c.armZ - c.slatZ) * 0.75;
        double pitch = (wb - c.slatBottom) / chevronSlats;
        double floor = idleChevronSlatBrightness;
        glColor3d(floor, floor, floor);
        glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) {
            quadFacing(new double[][] {{wb, side * c.armOuterAt(wb), c.slatZ}, {wb, side * sw, c.slatZ},
                    {c.slatBottom, side * sb, c.slatZ}, {c.slatBottom, side * si, c.slatZ}},
                    new double[][] {{6, 4}, {10, 4}, {10, 15}, {6, 15}}, 0, 0, 1);
        }
        glColor3d(1, 1, 1);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < chevronSlats; i++) {
                double xt = wb - (i + 0.2) * pitch, xb = xt - pitch * 0.6;
                // Its top and bottom
                quadFacing(new double[][] {{xt, side * c.armOuterAt(xt), barZ}, {xt, side * c.slatOuterAt(xt), barZ},
                        {xt, side * c.slatOuterAt(xt), c.slatZ}, {xt, side * c.armOuterAt(xt), c.slatZ}},
                        new double[][] {{6, 8}, {10, 8}, {10, 9}, {6, 9}}, 1, 0, 0);
                quadFacing(new double[][] {{xb, side * c.armOuterAt(xb), barZ}, {xb, side * c.slatOuterAt(xb), barZ},
                        {xb, side * c.slatOuterAt(xb), c.slatZ}, {xb, side * c.armOuterAt(xb), c.slatZ}},
                        new double[][] {{6, 8}, {10, 8}, {10, 9}, {6, 9}}, -1, 0, 0);
            }
        }
        glEnd();

        if (engaged) {
            selectTile(TextureIndex.CHEVRON_LIT);
            glDisable(GL_LIGHTING);
            setLightingDisabled(true);
        }
        glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < chevronSlats; i++) {
                double xt = wb - (i + 0.2) * pitch, xb = xt - pitch * 0.6;
                // The brightest band of the light texture when lit (orange on a Milky Way gate,
                // pale blue on a Pegasus gate), plain metal otherwise
                double vLit = c.pegasus ? 11 : 7;
                double u0 = engaged ? 0 : 6, u1 = engaged ? 16 : 10, v0 = engaged ? vLit : 6, v1 = engaged ? vLit + 2 : 10;
                quadFacing(new double[][] {{xt, side * c.armOuterAt(xt), barZ}, {xt, side * c.slatOuterAt(xt), barZ},
                        {xb, side * c.slatOuterAt(xb), barZ}, {xb, side * c.armOuterAt(xb), barZ}},
                        new double[][] {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}}, 0, 0, 1);
            }
        }
        glEnd();
        if (engaged) {
            glEnable(GL_LIGHTING);
            setLightingDisabled(false);
        }
    }

    // How many slatted lights step down each side of a gate's chevron
    final static int chevronSlats = 6;

    // Draws a gate chevron's light: a block standing out of each face of the ring,
    // its top flush with the ring's outer edge. It glows when lit whatever the light around the
    // gate. On a Milky Way gate the top of the light texture is its brightest band, and an unlit
    // light uses the dark red lower part. On a Pegasus gate the bottom is the brightest, and an
    // unlit light uses the deeper blue top, darkened so it looks dark like the ring.
    void chevronLight(GateChevron c, boolean engaged) {
        double lt = c.lightTop(), lb = c.lightBase();
        double x0 = c.lightTopX, x1 = c.lightBottom, z = c.lightZ;
        if (engaged) {
            glDisable(GL_LIGHTING);
            setLightingDisabled(true);
        } else {
            double idle = c.pegasus ? idlePegasusChevronLightBrightness : idleChevronLightBrightness;
            glColor3d(idle, idle, idle);
        }
        selectTile(TextureIndex.CHEVRON_LIT);
        double v0 = c.pegasus ? (engaged ? 8 : 0) : (engaged ? 2 : 9);
        double v1 = c.pegasus ? (engaged ? 16 : 8) : (engaged ? 10 : 16);
        glBegin(GL_QUADS);
        for (int face = -1; face <= 1; face += 2) {
            quadFacing(new double[][] {{x0, lt, face * z}, {x1, lb, face * z},
                    {x1, -lb, face * z}, {x0, -lt, face * z}},
                    new double[][] {{16, v0}, {12, v1}, {4, v1}, {0, v0}}, 0, 0, face);
        }
        glEnd();
        glColor3d(1, 1, 1);
        if (engaged) {
            glEnable(GL_LIGHTING);
            setLightingDisabled(false);
        }

        // Its inner end, sides and top, where it stands out of the ring's face, are the chevron's
        // metal: a light's texture stretched along them shows as a smeared streak
        selectChevronMetal(c);
        double[][] metal = {{6, 8}, {10, 8}, {10, 10}, {6, 10}};
        glBegin(GL_QUADS);
        for (int face = -1; face <= 1; face += 2) {
            double zf = face * z, zb = face * c.backZ;
            quadFacing(new double[][] {{x1, -lb, zf}, {x1, lb, zf}, {x1, lb, zb}, {x1, -lb, zb}},
                    metal, -1, 0, 0);
            for (int side = -1; side <= 1; side += 2) {
                quadFacing(new double[][] {{x0, side * lt, zf}, {x1, side * lb, zf},
                        {x1, side * lb, zb}, {x0, side * lt, zb}},
                        metal, 0, side, 0);
            }
            quadFacing(new double[][] {{x0, -lt, zf}, {x0, lt, zf}, {x0, lt, zb}, {x0, -lt, zb}},
                    metal, 1, 0, 0);
        }
        glEnd();
    }

    // The unit normal of the plane through three points, on the side facing the viewer (+z)
    static double[] faceNormal(double[] a, double[] b, double[] c) {
        double ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
        double bx = c[0] - a[0], by = c[1] - a[1], bz = c[2] - a[2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz) * (nz < 0 ? -1 : 1);
        return new double[] {nx / len, ny / len, nz / len};
    }

    // Draws a quad with the given corners and texture coordinates (within the current tile),
    // facing the given way: its corners are taken in whichever order makes it face that way.
    // Call inside glBegin(GL_QUADS).
    void quadFacing(double[][] p, double[][] uv, double nx, double ny, double nz) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        double bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
        double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        boolean reverse = cx * nx + cy * ny + cz * nz < 0;
        glNormal3d(nx, ny, nz);
        for (int n = 0; n < 4; n++) {
            int i = reverse ? 3 - n : n;
            vertex(p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1]);
        }
    }

    // How far (blocks, on a standard gate) the top chevron's outer layer slides down to lock a
    // symbol: a little further than on the show, so it shows at Minecraft's scale
    final static double masterChevronTravel = 0.09;

    // How far out from the gate's centre the ring's outer edge is, the given distance either side
    // of the middle of one of its sides
    static double ringEdgeAt(GateShape shape, double y) {
        double edge = shape.ringOuterRadius * Math.cos(Math.PI / shape.ringSegments);
        return Math.sqrt(edge * edge - y * y);
    }

    // A block standing on the gate's face, outlined by the given four corners (in either winding)
    // and running from depth zb out to depth zf, textured with the columns u0 to u1 of the current
    // tile. Call inside glBegin(GL_QUADS).
    void prism(double[] x, double[] y, double zf, double zb, double u0, double u1) {
        double area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += x[i] * y[j] - x[j] * y[i];
        }
        int d = area > 0 ? 1 : -1; // go round anticlockwise, so the front faces outwards
        glNormal3d(0, 0, 1);
        for (int n = 0; n < 4; n++) {
            int i = ((d * n) % 4 + 4) % 4;
            vertex(x[i], y[i], zf, n == 1 || n == 2 ? u1 : u0, n >= 2 ? 15 : 4);
        }
        for (int n = 0; n < 4; n++) {
            int i = ((d * n) % 4 + 4) % 4, j = ((d * (n + 1)) % 4 + 4) % 4;
            double ex = x[j] - x[i], ey = y[j] - y[i], len = Math.hypot(ex, ey);
            glNormal3d(ey / len, -ex / len, 0);
            vertex(x[i], y[i], zf, u0, 4);
            vertex(x[i], y[i], zb, u0, 6);
            vertex(x[j], y[j], zb, u1, 6);
            vertex(x[j], y[j], zf, u1, 4);
        }
    }

    protected static void setLightingDisabled(boolean off) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        if (off)
            glDisable(GL_TEXTURE_2D);
        else
            glEnable(GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    void renderEventHorizon(SGBaseTE te, float partialTicks) {
        if (SGCraft.useHDEventHorizionTexture) {
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/eventhorizonhd.png"));
        } else {
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/eventhorizon.png"));
        }
        GL11.glDisable(GL_LIGHTING);
        setLightingDisabled(true);
        glDisable(GL_CULL_FACE);
        glNormal3d(0, 0, 1);
        GateShape shape = te.getShape();
        double grid[][] = te.getEventHorizonGrid()[0];
        double rclip = shape.ehClipRadius * (te.irisIsClosed() ?  te.getIrisAperture(partialTicks) : 1.0);
        for (int i = 1; i < shape.ehGridRadialSize; i++) {
            glBegin(GL_QUAD_STRIP);
            for (int j = 0; j <= shape.ehGridPolarSize; j++) {
                ehVertex(shape, grid, i, j, rclip);
                ehVertex(shape, grid, i+1, j, rclip);
            }
            glEnd();
        }
        glBegin(GL_TRIANGLE_FAN);
        glTexCoord2d(0, 0);
        glVertex3d(0, 0, ehClip(grid[1][0] * shape.depthScale(), 0, rclip));
        for (int j = 0; j <= shape.ehGridPolarSize; j++)
            ehVertex(shape, grid, 1, j, rclip);
        glEnd();
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        GL11.glEnable(GL_LIGHTING);
        setLightingDisabled(false);
    }
    
    void ehVertex(GateShape shape, double[][] grid, int i, int j, double rclip) {
        double r = i * shape.ehBandWidth;
        double x = r * shape.ehCos[j];
        double y = r * shape.ehSin[j];
        double z = ehClip(grid[j+1][i] * shape.depthScale(), r, rclip); // ripples scale with the ring's thickness
        glTexCoord2d(x, y);
        glVertex3d(x, y, z);
    }
    
    double ehClip(double z, double r, double rclip) {
        if (r >= rclip)
            z = min(z, 0);
        return z;
    }       
    
    void renderIris(SGBaseTE te, double t) {
        bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/" + textureFolder(te) + "/iris.png"));
        double a = te.getIrisAperture(t);
        // The blades are sized for a standard gate; stretch them across a bigger opening but
        // keep them at the same depth, where the iris entity blocks things
        double s = te.getShape().ringInnerRadius / GateShape.STANDARD.ringInnerRadius;
        glPushMatrix();
        glScaled(s, s, 1);
        glNormal3f(0, 0, 1);
        glBegin(GL_TRIANGLES);
        for (int i = 0; i < numIrisBlades; i++) {
            renderIrisBlade(a, 360.0 * i / numIrisBlades);
        }
        glEnd();
        renderIrisHub(a, s);
        glPopMatrix();
    }

    // The shape of an iris blade (blocks, on a standard gate): how far out from the centre its
    // pivot is, its length and the length of its trailing edge, and its width at the pivot and
    // when closed
    final static double irisBladePivot = 2.31;
    final static double irisBladeLength = 2.40;
    final static double irisBladeEdge = 1.85;
    final static double irisBladeEndWidth = 0.16;
    final static double irisBladeWidth = 1.00;
    // How far (degrees) each blade swings about its pivot to open the iris, and how wide it is
    // then: far enough and narrow enough that, fully open, every blade is behind the ring, between
    // its inner and outer edges, all the way through the swing
    final static double irisBladeSwing = 62;
    final static double irisBladeOpenWidth = 0.03;
    // How finely each of a blade's triangles is cut, so that it can follow the swirl (see
    // irisSwirl)
    final static int irisBladeSteps = 8;

    // How wide a blade is when the iris is opened by a (0 to 1)
    static double irisBladeWidthAt(double a) {
        return irisBladeWidth - (irisBladeWidth - irisBladeOpenWidth) * a * a;
    }

    // As on the show, the whole iris swirls: everything is turned round the centre, more the
    // nearer it is to the middle, so the seams between blades curve. This many degrees at the
    // centre, none at the rim. All the blades are turned alike, so they can't cut through each
    // other.
    final static double irisSwirl = 40;

    static double irisSwirlAt(double dist) {
        return irisSwirl * Math.max(0, 1 - dist / GateShape.STANDARD.ringInnerRadius);
    }

    // Draws the blade at the given angle round the iris, opened by a. Call inside
    // glBegin(GL_TRIANGLES).
    void renderIrisBlade(double a, double angle) {
        double w1 = irisBladeLength, w2 = irisBladeEdge, h0 = irisBladeEndWidth, h1 = irisBladeWidth;
        double h = irisBladeWidthAt(a);
        double u = w2 / w1, v = h / h1, v0 = h0 / h1;
        double z0 = SGBaseTE.irisZPosition;
        double z1 = 0.01;
        // Its corners in its own frame as {x, y, z, u, v}: its point near the centre, the two
        // corners of its end at the pivot (tilted a little, which keeps overlapping blades in
        // order), and the two corners of its trailing edge
        double[] tip = {-w1, 0, z0, 0, 0}, edge = {-w1 + w2, h, z0, u, v}, tail = {-w1, h, z0, 0, v};
        double[] front1 = {0, 0, z0 + z1, 1, 0}, front2 = {0, h0, z0 + z1, 1, v0};
        double[] back1 = {0, 0, z0 - z1, 1, 0}, back2 = {0, h0, z0 - z1, 1, v0};
        // From its frame to the gate's: swung open about its pivot, then turned to its place
        double swing = Math.toRadians(-a * irisBladeSwing), turn = Math.toRadians(angle);
        double[] frame = {Math.cos(swing), Math.sin(swing), Math.cos(turn), Math.sin(turn)};
        irisTriangle(tip, front1, front2, frame);
        irisTriangle(tip, front2, edge, frame);
        irisTriangle(tip, edge, tail, frame);
        irisTriangle(tip, tail, edge, frame);
        irisTriangle(tip, edge, back2, frame);
        irisTriangle(tip, back2, back1, frame);
    }

    // Draws a triangle of a blade between corners given in its frame, cut into small pieces
    void irisTriangle(double[] p, double[] q, double[] s, double[] frame) {
        int n = irisBladeSteps;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n - i; j++) {
                irisVertex(p, q, s, i, j, frame);
                irisVertex(p, q, s, i + 1, j, frame);
                irisVertex(p, q, s, i, j + 1, frame);
                if (j < n - i - 1) {
                    irisVertex(p, q, s, i + 1, j, frame);
                    irisVertex(p, q, s, i + 1, j + 1, frame);
                    irisVertex(p, q, s, i, j + 1, frame);
                }
            }
        }
    }

    // The point of a blade's triangle i steps from p towards q and j steps towards s, placed in
    // the gate and swirled
    void irisVertex(double[] p, double[] q, double[] s, int i, int j, double[] frame) {
        double bq = (double) i / irisBladeSteps, bs = (double) j / irisBladeSteps, bp = 1 - bq - bs;
        double[] c = new double[5];
        for (int k = 0; k < 5; k++) {
            c[k] = p[k] * bp + q[k] * bq + s[k] * bs;
        }
        double x = irisBladePivot + c[0] * frame[0] - c[1] * frame[1];
        double y = c[0] * frame[1] + c[1] * frame[0];
        double gx = x * frame[2] - y * frame[3], gy = x * frame[3] + y * frame[2];
        double dist = Math.hypot(gx, gy), ang = Math.atan2(gy, gx) + Math.toRadians(irisSwirlAt(dist));
        glTexCoord2d(c[3], c[4]);
        glVertex3d(dist * Math.cos(ang), dist * Math.sin(ang), c[2]);
    }

    // As on the show, the middle of a closed iris stands out of it like a twisting tower:
    // narrow where it leaves the iris and flaring out to a wide top, where curved blades spiral
    // out from the centre, each lapping over the next and narrowing to a point. Down its sides,
    // the blades' edges twist round like a screw. It isn't a separate part: each of its blades is
    // the raised inner end of one of the main blades, and swings open with it. Sizes are in
    // blocks on a standard gate.
    final static int irisHubBlades = (int) numIrisBlades;
    // How far out from the centre the points of its top reach
    final static double irisHubRadius = 0.8;
    // How far its top stands out from the iris
    final static double irisHubTowerHeight = 0.15;
    // How wide it is where it leaves the iris, as a fraction of its top
    final static double irisHubBase = 0.3;
    // How far (degrees) its edges twist round between its top and the iris
    final static double irisHubTowerTwist = 60;
    // How much further its top's centre stands out than its points
    final static double irisHubHeight = 0.02;
    // How far each blade's leading edge stands above the blade it laps over, at the centre
    final static double irisHubStep = 0.02;
    // How far (degrees) each blade curves round between its point and the centre, on top of the
    // swirl of the whole iris
    final static double irisHubTwist = 50;
    // How many blade spacings each blade spans at its widest, near the centre
    final static double irisHubOverlap = 2.2;
    // How finely each blade is cut: along it, across its top, and down the tower's sides
    final static int irisHubSteps = 10, irisHubAcross = 4, irisHubDown = 8;

    // Draws the tower in the middle of the iris (see irisHubBlades), opened by a, on a gate the
    // given times as big as a standard one. As the iris opens, it opens out into a crown round
    // the edge of the hole, settling down into the iris as it goes (see hubPoint).
    void renderIrisHub(double a, double scale) {
        double rise = 1 - a;
        if (rise <= 0) {
            return;
        }
        glBegin(GL_QUADS);
        for (int b = 0; b < irisHubBlades; b++) {
            for (int i = 0; i < irisHubSteps; i++) {
                double r1 = irisHubRadius * i / irisHubSteps, r2 = irisHubRadius * (i + 1) / irisHubSteps;
                // Its top
                for (int j = 0; j < irisHubAcross; j++) {
                    double p1 = (double) j / irisHubAcross, p2 = (double) (j + 1) / irisHubAcross;
                    irisQuad(new double[][] {hubPoint(b, r1, p1, 0, a, rise, scale), hubPoint(b, r2, p1, 0, a, rise, scale),
                            hubPoint(b, r2, p2, 0, a, rise, scale), hubPoint(b, r1, p2, 0, a, rise, scale)},
                            new double[] {0, 0, 1});
                }
                // Its leading and trailing edges, twisting down the tower's sides to the iris
                for (int k = 0; k < irisHubDown; k++) {
                    double d1 = (double) k / irisHubDown, d2 = (double) (k + 1) / irisHubDown;
                    for (int edge = 0; edge <= 1; edge++) {
                        double[] q1 = hubPoint(b, r1, edge, d1, a, rise, scale), q2 = hubPoint(b, r2, edge, d1, a, rise, scale);
                        double[] q3 = hubPoint(b, r2, edge, d2, a, rise, scale), q4 = hubPoint(b, r1, edge, d2, a, rise, scale);
                        // Facing away from the blade, round the tower
                        double ang = Math.atan2(q1[1] + q2[1], q1[0] + q2[0]), side = edge == 0 ? 1 : -1;
                        irisQuad(new double[][] {q1, q2, q3, q4}, new double[] {side * Math.sin(ang), -side * Math.cos(ang), 0});
                    }
                }
            }
        }
        glEnd();
    }

    // A point on tower blade b: on its top, the given distance from the centre and fraction of
    // the way across from its leading edge; then the given fraction of the way down the tower's
    // side towards the iris. As the iris opens by a, the tower opens out with the hole in the
    // main blades into a crown of blades round its edge, as on the show, turning as the main
    // blades do and settling into the iris (it stands out the given fraction of its full height).
    // Fully open, the hole's edge is behind the ring. As {x, y, z, u, v}.
    static double[] hubPoint(int b, double rho, double across, double down, double a, double rise, double scale) {
        double spacing = 360.0 / irisHubBlades, widest = spacing * irisHubOverlap;
        double t = rho / irisHubRadius, f = 1 - t;
        // It narrows to a point at its outer end
        double phi = across * widest * (1 - t * t);
        double step = irisHubStep * (0.3 + 0.7 * f);
        double top = irisHubTowerHeight + irisHubHeight * f + step * (1 - phi / widest);
        double z = SGBaseTE.irisZPosition + scale * (0.005 + rise * top * (1 - down));
        // Narrow in the middle, flaring out to the top
        double up = 1 - down;
        double r = rho * (irisHubBase + (1 - irisHubBase) * up * up);
        // Each main blade's leading edge passes this close to the centre, at this angle round
        // from its pivot (see renderIrisBlade), so the hole's edge is there
        double swing = Math.toRadians(a * irisBladeSwing);
        double hole = irisBladePivot * Math.sin(swing);
        double dist = hole + r * rise;
        double at = Math.toRadians(b * spacing + irisHubTwist * f + phi + irisHubTowerTwist * down + irisSwirlAt(dist)) - swing;
        return new double[] {dist * Math.cos(at), dist * Math.sin(at), z, t, across + down};
    }

    // Draws a quad of the iris, corners given as {x, y, z, u, v}, facing roughly the given way.
    // Call inside glBegin(GL_QUADS).
    void irisQuad(double[][] p, double[] towards) {
        double ax = p[2][0] - p[0][0], ay = p[2][1] - p[0][1], az = p[2][2] - p[0][2];
        double bx = p[3][0] - p[1][0], by = p[3][1] - p[1][1], bz = p[3][2] - p[1][2];
        double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len == 0) {
            return;
        }
        boolean reverse = cx * towards[0] + cy * towards[1] + cz * towards[2] < 0;
        double sign = reverse ? -len : len;
        glNormal3d(cx / sign, cy / sign, cz / sign);
        for (int k = 0; k < 4; k++) {
            double[] q = p[reverse ? 3 - k : k];
            glTexCoord2d(q[3], q[4]);
            glVertex3d(q[0], q[1], q[2]);
        }
    }
    
    void selectTile(TextureIndex index) {
        u0 = (index.index % textureTilesWide) * (textureScaleU * 16);
        v0 = (index.index / textureTilesWide) * (textureScaleV * 16);
    }
    
    void vertex(double x, double y, double z, double u, double v) {
        glTexCoord2d(u0 + u * textureScaleU, v0 + v * textureScaleV);
        glVertex3d(x, y, z);
    }

}

enum TextureIndex {
    RING_FACE(1),
    RING(0),
    RING_SYMBOL(32),
    CHEVRON(3),
    CHEVRON_LIT(2);

    public final int index;

    TextureIndex(int index) {
        this.index = index;
    }
}

enum RingType {
    Inner, Outer
}

