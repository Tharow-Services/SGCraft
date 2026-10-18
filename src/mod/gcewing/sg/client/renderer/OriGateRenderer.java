//------------------------------------------------------------------------------------------------
//
//   SG Craft - Ori supergate renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.SGCraft;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateShape;
import gcewing.sg.util.OriGate;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

// The Ori supergate (SG-1, The Ark of Truth): a ring of separate, plated metal segments with gaps between them, their
// outer halves wrapped in a band of blue energy, like a shield, that joins them up. As the
// gate dials, the segments link one after another from the top, anticlockwise all the way round (see OriGate): a
// linked segment's metal lights up and its energy appears, and lightning crackles constantly across the gaps between
// linked segments' bare inner halves, and wildly where the next link is forming. Idle, it's dark metal with no energy at all.
class OriGateRenderer extends GateRenderer {

    // Each segment's share of its place round the ring; the rest is the gap the energy arcs across
    final static double segmentFill = 0.45;
    // How thick a segment is, of the ring's depth
    final static double segmentDepth = 0.5;
    // How far the raised spine on each face of the metal half stands out, of the ring's depth, and how wide it is
    final static double spineHeight = 0.06, spineWidth = 0.55;
    // How far the light housing at each segment's inner end reaches into the opening, of the ring's width
    final static double noseLength = 0.12;
    // Where each segment's metal ends, of the way out across the ring
    final static double metalLength = 0.5;
    // The energy band covers the outer half of each segment, standing a little proud of the metal all round
    final static double bandStart = 0.5, bandOverhang = 0.04, bandDepth = 1.3;

    final static float[] glowColour = {0.55F, 0.8F, 1.0F};
    // The energy half: its colour, and how bright it is unlinked and linked
    final static float[] energyColour = {0.35F, 0.65F, 1.0F}, energyRimColour = {0.75F, 0.9F, 1.0F};
    // How bright it is linked; unlinked, it isn't there at all
    final static float energyLinked = 0.55F;
    // Steps each piece of the energy band is curved in
    final static int energySteps = 4;
    final static float[] arcCore = {0.9F, 0.97F, 1.0F}, arcGlow = {0.35F, 0.6F, 1.0F};
    // Pieces each arc is made of, and how far it strays from a straight line, of its length
    final static int arcPieces = 9;
    final static double arcWander = 0.35;

    final static ResourceLocation texture = SGCraft.mod.resourceLocation("textures/tileentity/ori/stargate.png");

    private final Random random = new Random();

    // The metal; in the world, the light is drawn later, in Forge's second pass, after translucent blocks, which would
    // otherwise be drawn over it (see SGBaseTERenderer.renderGate). Elsewhere (the GDO's screen) it's all drawn at once.
    @Override
    void renderRings(SGBaseTE te, GateShape shape, float partialTicks) {
        int n = OriGate.segmentCount(shape);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        glColor4f(1, 1, 1, 1);
        glDisable(GL_CULL_FACE);
        for (int k = 0; k < n; k++) {
            renderSegment(shape, n, k);
        }
        glEnable(GL_CULL_FACE);
        if (MinecraftForgeClient.getRenderPass() != 0) {
            renderLight(te, shape, partialTicks);
        }
    }

    // The energy band, the lit metal and the lightning
    void renderLight(SGBaseTE te, GateShape shape, float partialTicks) {
        int n = OriGate.segmentCount(shape);
        double[] glow = OriGate.segmentGlow(te, n, partialTicks);
        double time = te.getWorld() != null ? te.getWorld().getTotalWorldTime() + partialTicks : 0;
        glDisable(GL_CULL_FACE);
        beginGlow();
        glDisable(GL_TEXTURE_2D);
        for (int k = 0; k < n; k++) {
            renderEnergy(shape, n, k, glow, time);
            if (glow[k] > 0) {
                renderSegmentGlow(shape, n, k, (float) glow[k], time);
            }
        }
        renderArcs(te, shape, n, glow, time);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_CULL_FACE);
        endGlow(te);
    }

    // Nothing to draw: the Ori gate has no chevrons
    @Override
    void renderChevrons(SGBaseTE te, GateShape shape, float partialTicks) {
    }

    @Override
    void renderChevron(SGBaseTE te, GateShape shape, int i, boolean engaged, double slide) {
    }

    // ---- Segments ----

    // Segment k is centred on the angle k / n of the way round from the top
    static double segmentAngle(int n, int k) {
        return Math.PI / 2 + 2 * Math.PI * k / n;
    }

    static double halfWidth(GateShape shape, int n) {
        return Math.PI * (shape.ringInnerRadius + OriGate.outerRadius(shape)) / 2 / n * segmentFill;
    }

    static double halfDepth(GateShape shape) {
        return shape.ringDepth * segmentDepth / 2;
    }

    // Where each segment's metal ends
    static double metalEnd(GateShape shape) {
        return shape.ringInnerRadius + (OriGate.outerRadius(shape) - shape.ringInnerRadius) * metalLength;
    }

    // Where the energy band starts and ends: over the outer half of the metal, and a little past its end
    static double bandInner(GateShape shape) {
        double r0 = shape.ringInnerRadius;
        return r0 + (metalEnd(shape) - r0) * bandStart;
    }

    static double bandOuter(GateShape shape) {
        double r0 = shape.ringInnerRadius, r1 = metalEnd(shape);
        return r1 + (r1 - r0) * bandOverhang;
    }

    // A point at radius r and angle a, s blocks across (anticlockwise) and z out of the face
    static double[] at(double r, double a, double s, double z) {
        double c = Math.cos(a), sn = Math.sin(a);
        return new double[] {r * c - s * sn, r * sn + s * c, z};
    }

    // The metal half: its body, the spine standing out of its front and back, and the light housing at its inner end
    void renderSegment(GateShape shape, int n, int k) {
        double a = segmentAngle(n, k);
        double r0 = shape.ringInnerRadius, rm = metalEnd(shape), width = OriGate.outerRadius(shape) - r0;
        double half = halfWidth(shape, n), d = halfDepth(shape);
        box(a, r0, rm, -half, half, -d, d);
        double spineHalf = half * spineWidth, spineZ = d + spineHeight * shape.ringDepth;
        box(a, r0 + width * 0.08, rm, -spineHalf, spineHalf, d, spineZ);
        box(a, r0 + width * 0.08, rm, -spineHalf, spineHalf, -spineZ, -d);
        box(a, r0 - width * noseLength, r0, -half * 0.7, half * 0.7, -d * 0.7, d * 0.7);
    }

    // A box in a segment's frame: from radius r0 to r1, s0 to s1 across, z0 to z1 out of the face; textured by size
    static void box(double a, double r0, double r1, double s0, double s1, double z0, double z1) {
        double[][] p = corners(a, r0, r1, s0, s1, z0, z1);
        double c = Math.cos(a), sn = Math.sin(a);
        glBegin(GL_QUADS);
        face(p[4], p[5], p[7], p[6], 0, 0, 1, r1 - r0, s1 - s0);          // front
        face(p[0], p[2], p[3], p[1], 0, 0, -1, s1 - s0, r1 - r0);         // back
        face(p[1], p[3], p[7], p[5], c, sn, 0, s1 - s0, z1 - z0);         // outer end
        face(p[0], p[4], p[6], p[2], -c, -sn, 0, z1 - z0, s1 - s0);       // inner end
        face(p[2], p[6], p[7], p[3], -sn, c, 0, z1 - z0, r1 - r0);        // anticlockwise side
        face(p[0], p[1], p[5], p[4], sn, -c, 0, r1 - r0, z1 - z0);        // clockwise side
        glEnd();
    }

    static double[][] corners(double a, double r0, double r1, double s0, double s1, double z0, double z1) {
        double[][] p = new double[8][];
        for (int i = 0; i < 8; i++) {
            p[i] = at((i & 1) == 0 ? r0 : r1, a, (i & 2) == 0 ? s0 : s1, (i & 4) == 0 ? z0 : z1);
        }
        return p;
    }

    // A quad with the texture's plating at about two blocks to a repeat
    static void face(double[] p0, double[] p1, double[] p2, double[] p3, double nx, double ny, double nz, double u, double v) {
        glNormal3d(nx, ny, nz);
        u /= 2;
        v /= 2;
        glTexCoord2d(0, 0);
        vertex(p0);
        glTexCoord2d(u, 0);
        vertex(p1);
        glTexCoord2d(u, v);
        vertex(p2);
        glTexCoord2d(0, v);
        vertex(p3);
    }

    // The segments' outer halves are wrapped in one band of see-through blue light round the ring, spreading as the
    // segments link: segment k's piece reaches halfway to each neighbour, fading out towards one that isn't linked,
    // and there's none at all where nothing is linked. Brightest where it meets the metal, and shimmering.
    void renderEnergy(GateShape shape, int n, int k, double[] glow, double time) {
        double g = glow[k];
        double before = Math.min(g, glow[(k + n - 1) % n]), after = Math.min(g, glow[(k + 1) % n]);
        if (g <= 0) {
            return;
        }
        double a = segmentAngle(n, k), step = Math.PI / n; // halfway to the next segment
        double rm = bandInner(shape), r1 = bandOuter(shape), d = halfDepth(shape) * bandDepth;
        double shimmer = 0.75 + 0.25 * Math.sin(time * 0.25 + k * 2.3) * Math.sin(time * 0.11 + k);
        glBegin(GL_QUADS);
        for (int i = 0; i < 2 * energySteps; i++) {
            double t0 = -1 + (double) i / energySteps, t1 = -1 + (double) (i + 1) / energySteps;
            float l0 = level(g, before, after, t0, shimmer), l1 = level(g, before, after, t1, shimmer);
            double a0 = a + t0 * step, a1 = a + t1 * step;
            for (int side = -1; side <= 1; side += 2) { // front and back faces
                double z = side * d;
                quad(at(rm, a0, 0, z), at(r1, a0, 0, z), at(r1, a1, 0, z), at(rm, a1, 0, z),
                    scaled(energyRimColour, l0), scaled(energyColour, l0 * 0.6F), scaled(energyColour, l1 * 0.6F), scaled(energyRimColour, l1));
            }
            // The outer edge, and the inner one where it shows across the gaps
            quad(at(r1, a0, 0, -d), at(r1, a0, 0, d), at(r1, a1, 0, d), at(r1, a1, 0, -d),
                scaled(energyColour, l0 * 0.6F), scaled(energyColour, l0 * 0.6F), scaled(energyColour, l1 * 0.6F), scaled(energyColour, l1 * 0.6F));
            quad(at(rm, a0, 0, -d), at(rm, a0, 0, d), at(rm, a1, 0, d), at(rm, a1, 0, -d),
                scaled(energyRimColour, l0 * 0.5F), scaled(energyRimColour, l0 * 0.5F), scaled(energyRimColour, l1 * 0.5F), scaled(energyRimColour, l1 * 0.5F));
        }
        glEnd();
    }

    // How bright the band is at t (-1, halfway back to the segment before, to 1, halfway on to the next): the segment's
    // own glow in the middle, going to what it shares with its neighbour at each end
    static float level(double g, double before, double after, double t, double shimmer) {
        double edge = t < 0 ? before : after;
        double f = Math.abs(t);
        return (float) ((g + (edge - g) * f) * energyLinked * shimmer);
    }

    static float[] scaled(float[] colour, float level) {
        return new float[] {colour[0] * level, colour[1] * level, colour[2] * level};
    }

    static void quad(double[] p0, double[] p1, double[] p2, double[] p3, float[] c0, float[] c1, float[] c2, float[] c3) {
        colour(c0);
        vertex(p0);
        colour(c1);
        vertex(p1);
        colour(c2);
        vertex(p2);
        colour(c3);
        vertex(p3);
    }

    static void colour(float[] c) {
        glColor4f(c[0], c[1], c[2], 1);
    }

    // A linked segment's metal: its spines glow along their length, brightest at the inner end, and its light housing
    // burns bright; it all pulses a little
    void renderSegmentGlow(GateShape shape, int n, int k, float glow, double time) {
        double a = segmentAngle(n, k);
        double r0 = shape.ringInnerRadius, rm = metalEnd(shape), width = OriGate.outerRadius(shape) - r0;
        double half = halfWidth(shape, n), d = halfDepth(shape);
        double spineHalf = half * spineWidth, spineZ = d + spineHeight * shape.ringDepth + 0.01;
        float g = glow * (float) (0.85 + 0.15 * Math.sin(time * 0.3 + k * 1.7));

        glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) {
            double z = side * spineZ;
            colour(scaled(glowColour, g));
            vertex(at(r0 + width * 0.08, a, -spineHalf * 0.5, z));
            vertex(at(r0 + width * 0.08, a, spineHalf * 0.5, z));
            colour(scaled(glowColour, g * 0.35F));
            vertex(at(rm, a, spineHalf * 0.5, z));
            vertex(at(rm, a, -spineHalf * 0.5, z));
        }
        double rn = r0 - width * noseLength - 0.01;
        colour(scaled(arcCore, g));
        vertex(at(rn, a, -half * 0.6, -d * 0.6));
        vertex(at(rn, a, -half * 0.6, d * 0.6));
        vertex(at(rn, a, half * 0.6, d * 0.6));
        vertex(at(rn, a, half * 0.6, -d * 0.6));
        glEnd();
    }

    static void vertex(double[] p) {
        glVertex3d(p[0], p[1], p[2]);
    }

    // ---- Energy arcing between segments ----

    // Lightning crackles constantly across the gaps between linked segments' inner halves, and wildly where the links
    // are forming. The bolts change shape every other tick.
    void renderArcs(SGBaseTE te, GateShape shape, int n, double[] glow, double time) {
        long flicker = (long) Math.floor(time / 2);
        for (int k = 0; k < n; k++) {
            int next = (k + 1) % n;
            double g0 = glow[k], g1 = glow[next];
            if (g0 <= 0 && g1 <= 0) {
                continue;
            }
            random.setSeed(flicker * 7919 + k * 104729L + te.getPos().hashCode());
            boolean forming = Math.abs(g0 - g1) > 0.001 || g0 < 1 || g1 < 1;
            float strength = (float) Math.max(g0, g1);
            int bolts = forming ? 3 : 1 + random.nextInt(2);
            for (int b = 0; b < bolts; b++) {
                renderArc(shape, n, k, next, strength);
            }
        }
        glLineWidth(1);
    }

    // A jagged bolt across the gap from segment k's metal to the next one's, on the inner half the energy band doesn't
    // cover, with a branch now and then
    void renderArc(GateShape shape, int n, int k, int next, float strength) {
        double r0 = shape.ringInnerRadius, rb = bandInner(shape);
        double r = r0 + (rb - r0) * (0.15 + 0.7 * random.nextDouble());
        double d = halfDepth(shape);
        double z = (random.nextDouble() * 2 - 1) * d;
        double half = halfWidth(shape, n);
        double[] from = at(r, segmentAngle(n, k), half, z);
        double[] to = at(r + (random.nextDouble() - 0.5) * (rb - r0) * 0.5, segmentAngle(n, next), -half,
            z + (random.nextDouble() - 0.5) * d);
        double[][] bolt = bolt(from, to);

        // A wide, faint glow under a thin bright core
        glLineWidth(6);
        colour(scaled(arcGlow, strength * 0.6F));
        line(bolt);
        glLineWidth(2);
        colour(scaled(arcCore, strength));
        line(bolt);

        if (random.nextDouble() < 0.5) {
            double[] start = bolt[1 + random.nextInt(arcPieces - 2)];
            double length = distance(from, to) * 0.5;
            double[] end = {start[0] + (random.nextDouble() - 0.5) * length, start[1] + (random.nextDouble() - 0.5) * length,
                start[2] + (random.nextDouble() - 0.5) * length * 0.5};
            glLineWidth(1.5F);
            colour(scaled(arcGlow, strength));
            line(bolt(start, end));
        }
    }

    double[][] bolt(double[] from, double[] to) {
        double[][] points = new double[arcPieces + 1][];
        double wander = distance(from, to) * arcWander;
        for (int i = 0; i <= arcPieces; i++) {
            double t = (double) i / arcPieces;
            double off = (i == 0 || i == arcPieces) ? 0 : wander * Math.sin(Math.PI * t);
            points[i] = new double[] {
                from[0] + (to[0] - from[0]) * t + (random.nextDouble() - 0.5) * off,
                from[1] + (to[1] - from[1]) * t + (random.nextDouble() - 0.5) * off,
                from[2] + (to[2] - from[2]) * t + (random.nextDouble() - 0.5) * off * 0.5};
        }
        return points;
    }

    static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static void line(double[][] points) {
        glBegin(GL_LINE_STRIP);
        for (double[] p : points) {
            vertex(p);
        }
        glEnd();
    }
}
