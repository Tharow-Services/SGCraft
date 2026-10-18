//------------------------------------------------------------------------------------------------
//
//   SG Craft - Pegasus stargate renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateShape;
import net.minecraft.client.renderer.GLAllocation;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

// A Pegasus gate is drawn from a model of the gate on the show (see PegasusGateModel), made into
// display lists for each size of gate, one for each of its groups
class PegasusGateRenderer extends GateRenderer {

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
    // The glow of its lit glyphs
    final static double pegasusGlowStrength = 1.0;
    final static double[] pegasusGlowColour = {0.3, 0.7, 1.0};

    static PegasusGateModel pegasusModel() {
        if (pegasusModel == null && !pegasusModelFailed) {
            try (InputStream in = PegasusGateRenderer.class.getResourceAsStream("/assets/sgcraft/models/gate/pegasus_gate.txt")) {
                pegasusModel = PegasusGateModel.load(in);
            } catch (Exception e) {
                pegasusModelFailed = true;
                System.out.printf("SGCraft: Unable to load the Pegasus gate model: %s\n", e);
            }
        }
        return pegasusModel;
    }

    // Whether the model is there to draw the gate from
    static boolean available() {
        return pegasusModel() != null;
    }

    static double pegasusScale(GateShape shape) {
        return shape.ringOuterRadius / pegasusModel.outerRadius;
    }

    static double pegasusDepthScale(GateShape shape) {
        return GateShape.STANDARD.ringOuterRadius / pegasusModel.outerRadius * shape.ringDepth / GateShape.STANDARD.ringDepth;
    }

    // Where the top chevron of the model is in the gate's frame (see
    // SGBaseTERenderer.renderStargate): at the top, however the gate stands
    static double modelTop(SGBaseTE te) {
        return 90;
    }

    @Override
    boolean chevronEngaged(SGBaseTE te, int i, int j, float a, float partialTicks) {
        return PegasusScreens.chevronEngaged(te, i, j, a, partialTicks);
    }

    // Its ring is thin at the bore, and the screens stand out in front of it, so blades swung
    // open into it would show. The iris sits just behind the bore's front edge instead, and is
    // cut off at the bore, so its blades go into the ring as they open.
    @Override
    double irisDepth(GateShape shape) {
        return pegasusModel().boreFront * pegasusDepthScale(shape) - SGBaseTERenderer.irisBladeTilt;
    }

    @Override
    double irisClipRadius(GateShape shape) {
        return pegasusModel().boreRadius * pegasusScale(shape);
    }

    // The gate's rings, fixed as on the show: the ring itself and the inner ring's screens, with
    // the glyphs on them lit as it dials (see PegasusScreens.lit)
    @Override
    void renderRings(SGBaseTE te, GateShape shape, float partialTicks) {
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
        bindStargateTexture(te);
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
            try (InputStream in = PegasusGateRenderer.class.getResourceAsStream("/assets/sgcraft/models/gate/pegasus_glyphs.txt")) {
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
        int base = GLAllocation.generateDisplayLists(PegasusScreens.GLYPHS);
        for (int g = 0; g < PegasusScreens.GLYPHS; g++) {
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
    // the gate lights it (see PegasusScreens.lit)
    void renderPegasusCrystalScreens(SGBaseTE te, GateShape shape, int lists, float partialTicks) {
        PegasusScreens screens = PegasusScreens.of(te);
        boolean[] lit = screens.lit(te, partialTicks);
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
                glCallList(glyphLists + pegasusReferenceGlyph[PegasusScreens.glyphAt(screens.angles[s])]);
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

    // Draws a Pegasus gate's chevron, its centre along x, its lights lit if engaged. Its chevrons
    // stay put as they lock.
    @Override
    void renderChevron(SGBaseTE te, GateShape shape, int i, boolean engaged, double slide) {
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
        bindStargateTexture(te);
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
}
