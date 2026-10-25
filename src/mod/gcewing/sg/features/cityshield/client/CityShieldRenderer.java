package gcewing.sg.features.cityshield.client;

import static org.lwjgl.opengl.GL11.*;

import gcewing.sg.features.cityshield.CityShieldTE;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

// The city shield: a see-through, glowing ellipsoid round its generator, brightest where it's seen edge on, with a
// slow shimmer running over it. Drawn in Forge's second pass, after translucent blocks (see CityShieldTE).
public class CityShieldRenderer extends TileEntitySpecialRenderer<CityShieldTE> {

    // Bands from pole to pole, and slices round
    private static final int stacks = 32, slices = 64;
    private static final float[] colour = {0.30F, 0.58F, 0.95F}, rimColour = {0.75F, 0.9F, 1.0F};
    // How opaque it is face on (all of it, seen from inside), and edge on
    private static final float alpha = 0.22F, rimAlpha = 0.6F;
    // The bright edge as it spreads: how wide (radians of latitude) and how opaque
    private static final double edgeWidth = 0.12;
    private static final float edgeAlpha = 0.75F;

    @Override
    public boolean isGlobalRenderer(CityShieldTE te) {
        return true; // far bigger than the generator's section
    }

    @Override
    public void render(CityShieldTE te, double x, double y, double z, float partialTicks, int destroyStage, float a) {
        float fade = te.lastFade + (te.fade - te.lastFade) * partialTicks;
        if (fade <= 0) {
            return;
        }
        double time = te.getWorld().getTotalWorldTime() + (double) partialTicks;
        // The centre, from the camera
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;

        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        glPushMatrix();
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE); // seen from inside too
        glEnable(GL_BLEND);
        // Tints what's behind it rather than adding light, which a bright sky seen from inside would hardly show
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glShadeModel(GL_SMOOTH);

        // As on Atlantis, it spreads from the generator's level, a bright edge sweeping up over the top (and down under
        // the ground), and draws back the same way: it reaches edge (radians) above and below its middle
        double spread = fade * fade * (3 - 2 * fade);
        double edge = spread * Math.PI / 2;
        // The bright edge, dying away as the shield closes over the top
        double edgeGlow = Math.min(1, (1 - spread) * 5);
        for (int half = -1; half <= 1; half += 2) {
            for (int i = 0; i < stacks / 2; i++) {
                double lat0 = half * edge * i / (stacks / 2), lat1 = half * edge * (i + 1) / (stacks / 2);
                glBegin(GL_QUAD_STRIP);
                for (int j = 0; j <= slices; j++) {
                    vertex(te, cx, cy, cz, lat0, j, time, edge, edgeGlow);
                    vertex(te, cx, cy, cz, lat1, j, time, edge, edgeGlow);
                }
                glEnd();
            }
        }

        glShadeModel(GL_FLAT);
        glDepthMask(true);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_CULL_FACE);
        glEnable(GL_LIGHTING);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    // A point at latitude lat (-90 degrees, the bottom, to 90, the top) and the slice'th longitude
    private static void vertex(CityShieldTE te, double cx, double cy, double cz, double lat, int slice, double time, double edge, double edgeGlow) {
        double lon = 2 * Math.PI * slice / slices;
        double ux = Math.cos(lat) * Math.cos(lon), uy = Math.sin(lat), uz = Math.cos(lat) * Math.sin(lon);
        double up = lat >= 0 ? te.above : te.below; // the top half reaches above the generator, the bottom below
        double px = cx + ux * te.width, py = cy + uy * up, pz = cz + uz * te.width;

        // Edge on, the eye looks along the surface through more of it: brighter there
        double nx = ux / te.width, ny = uy / up, nz = uz / te.width;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz), vl = Math.sqrt(px * px + py * py + pz * pz);
        double facing = vl > 0 ? Math.abs((nx * px + ny * py + nz * pz) / (nl * vl)) : 1;
        double rim = Math.pow(1 - facing, 3);
        // Slow waves running up it and round it
        double shimmer = 0.5 + 0.25 * Math.sin(time * 0.05 - lat * 6) + 0.25 * Math.sin(time * 0.03 + lon * 5);

        // Near the spreading edge it glows almost white
        double band = edgeGlow * Math.exp(-Math.pow((edge - Math.abs(lat)) / edgeWidth, 2));
        double light = Math.max(rim, band);
        float r = (float) (colour[0] + (rimColour[0] - colour[0]) * light);
        float g = (float) (colour[1] + (rimColour[1] - colour[1]) * light);
        float b = (float) (colour[2] + (rimColour[2] - colour[2]) * light);
        float al = (float) Math.min(1, (alpha + (rimAlpha - alpha) * rim) * (0.6 + 0.4 * shimmer) + edgeAlpha * band);
        glColor4f(r, g, b, al);
        glVertex3d(px, py, pz);
    }
}
