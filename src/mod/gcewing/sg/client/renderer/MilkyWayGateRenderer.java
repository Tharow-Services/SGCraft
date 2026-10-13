//------------------------------------------------------------------------------------------------
//
//   SG Craft - Milky Way stargate renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateShape;
import net.minecraft.client.renderer.GLAllocation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

// A Milky Way gate is drawn from a model of the gate on the show (see MilkyWayGateModel), made
// into display lists for each size of gate: the fixed ring's body and detail, the symbol ring's
// cone and glyphs, and the chevron's metal and lights, each split by how its corners move as the
// top chevron locks a symbol
class MilkyWayGateRenderer extends GateRenderer {

    static MilkyWayGateModel milkyWayModel;
    static boolean milkyWayModelFailed;
    static final Map<String, Integer> milkyWayLists = new HashMap<>();
    // The metal each part of the model is drawn in (its lights are coloured glass, see
    // renderChevron), repeating across it every this many metres
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
            try (InputStream in = MilkyWayGateRenderer.class.getResourceAsStream("/assets/sgcraft/models/gate/milkyway_gate.txt")) {
                milkyWayModel = MilkyWayGateModel.load(in);
            } catch (Exception e) {
                milkyWayModelFailed = true;
                System.out.printf("SGCraft: Unable to load the Milky Way gate model: %s\n", e);
            }
        }
        return milkyWayModel;
    }

    // Whether the model is there to draw the gate from
    static boolean available() {
        return milkyWayModel() != null;
    }

    static double milkyWayScale(GateShape shape) {
        return shape.ringOuterRadius / MilkyWayGateModel.OUTER_RADIUS;
    }

    static double milkyWayDepthScale(GateShape shape) {
        return GateShape.STANDARD.ringOuterRadius / MilkyWayGateModel.OUTER_RADIUS * shape.depthScale();
    }

    // Where the top chevron of the model is in the gate's frame (see
    // SGBaseTERenderer.renderStargate): at the top, however the gate stands
    static double modelTop(SGBaseTE te) {
        return 90;
    }

    // The gate's rings: the fixed ring, and the symbol ring turned to its angle, with its point of
    // origin under the top chevron at rest
    @Override
    void renderRings(SGBaseTE te, GateShape shape, float partialTicks) {
        MilkyWayGateModel model = milkyWayModel();
        int lists = milkyWayLists(model, shape);
        glPushMatrix();
        glRotatef((float) (modelTop(te) - 90), 0, 0, 1);
        glColor3d(1, 1, 1);
        for (int part = MilkyWayGateModel.BODY; part <= MilkyWayGateModel.GLYPHS; part++) {
            if (part == MilkyWayGateModel.SYMBOL_RING) {
                glRotatef((float) te.interpolatedRingAngle(partialTicks), 0, 0, 1);
            }
            bindTexture("textures/tileentity/milkyway/" + milkyWayMetals[part] + ".png");
            glCallList(lists + part);
        }
        glPopMatrix();
        bindStargateTexture(te);
    }

    // Draws a Milky Way gate's chevron, its centre along x, its lights lit if engaged. The parts of
    // the top chevron that move as it locks a symbol are moved the given fraction of the way. As
    // on the show, a locked chevron lights its slatted lights and the top of its triangle, over
    // the ring's rim; the top chevron lights the whole triangle while it is locking a symbol.
    @Override
    void renderChevron(SGBaseTE te, GateShape shape, int i, boolean engaged, double slide) {
        MilkyWayGateModel model = milkyWayModel();
        int lists = milkyWayLists(model, shape), moves = model.moveCount();
        double s = milkyWayScale(shape), sz = milkyWayDepthScale(shape);
        boolean locking = engaged && slide > 0;
        glPushMatrix();
        // The model's chevron is at the top
        glRotatef(-90, 0, 0, 1);
        bindTexture("textures/tileentity/milkyway/chevron.png");
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
                    bindTexture("textures/tileentity/milkyway/" + glass + ".png");
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
        bindStargateTexture(te);
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
}
