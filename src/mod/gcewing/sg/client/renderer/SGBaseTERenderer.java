//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate base tile entity renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.BaseGLUtils;
import gcewing.sg.BaseTileEntityRenderer;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.SGCraft;
import gcewing.sg.util.GateShape;
import gcewing.sg.util.GateType;
import gcewing.sg.util.SGState;
import gcewing.sg.Vector3;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

import static gcewing.sg.client.renderer.GateRenderer.setLightingDisabled;
import static gcewing.sg.client.renderer.GateRenderer.textureFolder;
import static java.lang.Math.min;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL;

public class SGBaseTERenderer extends BaseTileEntityRenderer {

    // As on the show, about 20 blades, each curling up at its inner end into one point of the
    // twisting star in the middle (see irisHubBlades)
    final static double numIrisBlades = 20;

    // How each type of gate draws its rings and chevrons
    final GateRenderer classicGate = new ClassicGateRenderer();
    final GateRenderer milkyWayGate = new MilkyWayGateRenderer();
    final GateRenderer pegasusGate = new PegasusGateRenderer();
    final GateRenderer universeGate = new UniverseGateRenderer();

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
    // view. Every gate rises (or, lying flat, reaches) out of its base's section, so would vanish
    // when looking up at it; a global renderer is drawn whenever its render bounding box is in
    // view instead. All gates are global so that changing a gate's type or size never leaves it
    // with the wrong kind of renderer.
    @Override
    public boolean isGlobalRenderer(TileEntity te) {
        return te instanceof SGBaseTE;
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
        gateRenderer(gate).render(gate, shape, partialTicks);
        if (gate.hasIrisUpgrade)
            renderIris(gate, partialTicks);
        if (gate.isConnected() && gate.state != SGState.SyncAwait) {
            renderEventHorizon(gate, partialTicks);
        }
    }

    // A Milky Way or Pegasus gate is drawn from its model, or as SGCraft always has without it
    GateRenderer gateRenderer(SGBaseTE gate) {
        switch (gate.getGateType()) {
            case UNIVERSE:
                return universeGate;
            case MILKY_WAY:
                return MilkyWayGateRenderer.available() ? milkyWayGate : classicGate;
            case PEGASUS:
                return PegasusGateRenderer.available() ? pegasusGate : classicGate;
            default:
                return classicGate;
        }
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

}
