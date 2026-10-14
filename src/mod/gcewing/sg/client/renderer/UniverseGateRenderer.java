//------------------------------------------------------------------------------------------------
//
//   SG Craft - Universe stargate renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateShape;
import gcewing.sg.util.SGAddressing;
import net.minecraft.client.renderer.GLAllocation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

// A Universe gate is drawn from its own model (see UniverseGateModel), scaled to the gate:
// across, so its outer edge is the ring's; through, as much as the standard gate's is, times
// however much deeper the gate's ring is than the standard one's.
class UniverseGateRenderer extends GateRenderer {

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
            try (InputStream in = UniverseGateRenderer.class.getResourceAsStream("/assets/sgcraft/models/gate/universe_gate.txt")) {
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

    // Where a Universe gate's top chevron is in the gate's frame (see
    // SGBaseTERenderer.renderStargate): at the top, however the gate stands
    static double universeTop(SGBaseTE te) {
        return 90;
    }

    // As on the show, a Universe gate turns as a whole, chevrons and all
    @Override
    void turnGate(SGBaseTE te, float partialTicks) {
        glRotatef((float) te.interpolatedRingAngle(partialTicks), 0, 0, 1);
    }

    // Its ring's face is only just in front of the blades near the bore, and there are gaps in
    // the lattice round its rim, so blades swung open into it would show. The iris sits just
    // behind the bore's front edge instead, and is cut off at the bore, so its blades go into the
    // ring as they open.
    @Override
    double irisDepth(GateShape shape) {
        return UniverseGateModel.PROFILE[UniverseGateModel.FACE_FROM][1] * universeDepthScale(shape) - SGBaseTERenderer.irisBladeTilt;
    }

    @Override
    double irisClipRadius(GateShape shape) {
        return UniverseGateModel.INNER_RADIUS * universeScale(shape);
    }

    // A Universe gate lights all its chevrons together as soon as it starts dialling, or, dialled
    // a symbol at a time, as soon as the first locks
    @Override
    boolean chevronEngaged(SGBaseTE te, int i, int j, float a, float partialTicks) {
        return te.isActive() || lockingOnDial(te);
    }

    @Override
    void renderRings(SGBaseTE te, GateShape shape, float partialTicks) {
        renderUniverseRing(te, shape);
        renderUniverseSymbols(te, shape);
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
            bindTexture("textures/tileentity/universe/" + universeMetals[part] + ".png");
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
        bindStargateTexture(te);
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
        bindTexture("textures/tileentity/universe/panel.png");
        glColor3d(1, 1, 1);
        for (int symbol = 0; symbol < SGBaseTE.numRingSymbols; symbol++) {
            universePanel(shape, universeSymbolAngle(symbol));
        }
        bindTexture("textures/tileentity/universe/glyphs.png");
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
        int locked = te.isActive() || lockingOnDial(te) ? Math.min(te.numEngagedChevrons, te.dialledAddress.length()) : 0;
        if (locked > 0) {
            glBlendFunc(GL_ONE, GL_ONE);
            for (int layer = 0; layer <= universeCoreLayers; layer++) {
                if (layer < universeCoreLayers) {
                    glColor3d(1, 1, 1);
                } else {
                    double g = universeGlowStrength;
                    bindTexture("textures/tileentity/universe/glyphs_glow.png");
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
        bindStargateTexture(te);
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
    @Override
    void renderChevron(SGBaseTE te, GateShape shape, int i, boolean engaged, double slide) {
        UniverseGateModel model = universeModel();
        if (model == null) {
            return;
        }
        // Drawn over the chevron's surface without fighting it
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(-1, -1);
        // Its painted bronze studs, lit like the metal they are on
        bindTexture("textures/tileentity/universe/chevron_paint.png");
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glColor3d(1, 1, 1);
        universeChevronSurface(model, shape);
        // Its lights
        beginGlow();
        if (engaged) {
            bindTexture("textures/tileentity/universe/chevron_glow.png");
            glColor3d(universeLightGlowColour[0], universeLightGlowColour[1], universeLightGlowColour[2]);
            universeChevronSurface(model, shape);
            glColor3d(universeLightColour[0], universeLightColour[1], universeLightColour[2]);
        } else {
            // Unlit: darkening the chevron where the lights are
            glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_COLOR);
            double dark = idleUniverseLightDarkening;
            glColor3d(dark, dark, dark);
        }
        bindTexture("textures/tileentity/universe/chevron_lights.png");
        universeChevronSurface(model, shape);
        glDisable(GL_POLYGON_OFFSET_FILL);
        endGlow(te);
        bindStargateTexture(te);
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
}
