package gcewing.sg.features.rings.client;

import static org.lwjgl.opengl.GL11.*;

import gcewing.sg.features.rings.RingPlatformTE;
import gcewing.sg.features.rings.RingTransports;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

// A ring platform's five rings. At rest they lie on the floor round the platform (or flat under the ceiling). Set off,
// they rise one after another, the first highest, and stack round those inside (a ceiling platform's drop, the first to
// the floor); bands of blue-white light sweep through them as they transport; then they go back.
public class RingPlatformRenderer extends TileEntitySpecialRenderer<RingPlatformTE> {

    private static final ResourceLocation TOP = new ResourceLocation("sgcraft", "textures/blocks/ring_top.png");
    private static final ResourceLocation SIDE = new ResourceLocation("sgcraft", "textures/blocks/ring_side.png");

    private static final int rings = 5, segments = 36;
    // Each ring's size (blocks), and how far apart they stack
    private static final double outer = 2.45, inner = 2.05, height = 0.35, spacing = 0.5;
    // How far the resting rings stand out of the floor (or hang out of the ceiling): 5% of a block
    private static final double restLip = 0.05;
    // Ticks between rings setting off, and each one's travel; when they start back, and how long that takes
    // Going back, one at a time, the one nearest its resting place first (the last out), each taking returnTravel; all
    // back before the sound ends
    private static final int launchGap = 12, travel = 16, returnStart = RingTransports.transportTick + 8, returnGap = 5,
        returnTravel = 12;
    // The light: a glow building inside the rings once they're stacked, as the sound's energy builds, then the bands of the
    // transport round its moment
    private static final int glowStart = RingTransports.stackTicks + 6, columnStart = RingTransports.transportTick - 10,
        columnEnd = RingTransports.transportTick + 12, afterglowEnd = returnStart + 4; // (gone as the rings move off)

    @Override
    public void render(RingPlatformTE te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        boolean ceiling = te.isCeiling();
        long start = te.activeStart;
        double t = start >= 0 ? te.getWorld().getTotalWorldTime() - start + partialTicks : -1;
        if (t > RingTransports.doneTicks) {
            t = -1;
        }
        BlockPos pos = te.getPos();
        double floor = (start >= 0 ? te.floorY : te.floor()) - pos.getY();
        // At rest they lie sunk in the floor, their tops just above the platform block's (or in the ceiling, just below it)
        double rest = ceiling ? -restLip : 1 + restLip - height;

        // Lit as the floor inside the rings is
        BlockPos lightAt = new BlockPos(pos.getX(), (int) Math.floor(pos.getY() + (ceiling ? floor : 1)), pos.getZ());
        int light = te.getWorld().getCombinedLight(lightAt, 0);
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.disableCull(); // seen from inside and out

        if (t < 0) {
            drawRing(rest); // at rest they lie together: one shows
        } else {
            // In the column of light the rings shine as it does
            double column = columnStrength(t);
            if (column > 0) {
                int bx = light % 65536, by = light / 65536;
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) (bx + (240 - bx) * column), (float) (by + (240 - by) * column));
            }
            for (int k = 0; k < rings; k++) {
                // A floor platform's first ring goes highest; a ceiling platform's first lands lowest
                double stacked = floor + (ceiling ? k : rings - 1 - k) * spacing;
                double go = ease((t - k * launchGap) / travel);
                double back = ease((t - returnStart - (rings - 1 - k) * returnGap) / returnTravel);
                double out = go * (1 - back);
                drawRing(rest + (stacked - rest) * out);
            }
            if (t >= glowStart && t <= afterglowEnd) {
                drawLight(t, floor);
            }
        }

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    private static double ease(double f) {
        f = Math.max(0, Math.min(1, f));
        return f * f * (3 - 2 * f);
    }

    // One ring, its bottom at y: 36 segments, each with its own panel of the texture on top and bottom
    private void drawRing(double y) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        double y1 = y + height;

        bindTexture(TOP);
        buf.begin(GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            // Top, facing up (texture across the ring's width, along it round the ring)
            quad(buf, inner * c0, y1, inner * s0, 0, 0, inner * c1, y1, inner * s1, 1, 0,
                outer * c1, y1, outer * s1, 1, 1, outer * c0, y1, outer * s0, 0, 1, 1F);
            // Bottom, facing down
            quad(buf, outer * c0, y, outer * s0, 0, 1, outer * c1, y, outer * s1, 1, 1,
                inner * c1, y, inner * s1, 1, 0, inner * c0, y, inner * s0, 0, 0, 0.5F);
        }
        tess.draw();

        bindTexture(SIDE);
        buf.begin(GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            // Outside, and inside
            quad(buf, outer * c0, y, outer * s0, 0, 1, outer * c0, y1, outer * s0, 0, 0,
                outer * c1, y1, outer * s1, 1, 0, outer * c1, y, outer * s1, 1, 1, 0.8F);
            quad(buf, inner * c1, y, inner * s1, 0, 1, inner * c1, y1, inner * s1, 0, 0,
                inner * c0, y1, inner * s0, 1, 0, inner * c0, y, inner * s0, 1, 1, 0.65F);
        }
        tess.draw();
    }

    private static void quad(BufferBuilder buf, double x0, double y0, double z0, double u0, double v0,
            double x1, double y1, double z1, double u1, double v1, double x2, double y2, double z2, double u2, double v2,
            double x3, double y3, double z3, double u3, double v3, float shade) {
        buf.pos(x0, y0, z0).tex(u0, v0).color(shade, shade, shade, 1F).endVertex();
        buf.pos(x1, y1, z1).tex(u1, v1).color(shade, shade, shade, 1F).endVertex();
        buf.pos(x2, y2, z2).tex(u2, v2).color(shade, shade, shade, 1F).endVertex();
        buf.pos(x3, y3, z3).tex(u3, v3).color(shade, shade, shade, 1F).endVertex();
    }

    // How bright the column of light inside the rings is (0 to 1): swelling to the transport, then fading
    static double columnStrength(double t) {
        if (t < columnStart || t > columnEnd) {
            return 0;
        }
        int peak = RingTransports.transportTick;
        return t <= peak ? ease((t - columnStart) / (double) (peak - columnStart)) : 1 - ease((t - peak) / (double) (columnEnd - peak));
    }

    // The transport's light, as in the show: a white glow growing along the rings' inner edges as they charge; a column of
    // white light filling them at the transport, streaky at its edges; then a fading blue glow on their inner edges
    private void drawLight(double t, double floor) {
        int peak = RingTransports.transportTick;
        double column = columnStrength(t);
        // The edge glow: building once they're stacked, full through the column, then blue and fading
        double edge = t < columnStart ? ease((t - glowStart) / (double) (columnStart - glowStart)) : t <= peak ? 1
            : 1 - ease((t - peak) / (double) (afterglowEnd - peak));
        double blue = t > peak ? 1 - column : 0; // how far the edge glow has gone blue
        double top = floor + (rings - 1) * spacing + height;

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL_SMOOTH);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        // Along each ring's inner edge, and in the gaps between them: a band of light, brightest at its middle
        if (edge > 0) {
            float r = (float) (1 - 0.6 * blue), g = (float) (1 - 0.25 * blue), b = 1F;
            double rr = inner - 0.03;
            buf.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int k = 0; k < rings; k++) {
                double y0 = floor + k * spacing, y1 = y0 + spacing;
                float a = (float) (0.55 * edge);
                for (int i = 0; i < segments; i++) {
                    double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
                    double flicker = 0.75 + 0.25 * Math.sin(i * 2.3 + k * 1.7 + t * 0.8);
                    float am = (float) (a * flicker);
                    double ym = (y0 + y1) / 2;
                    // Lower half, rising to the middle; upper half, falling from it
                    buf.pos(rr * Math.cos(a0), y0, rr * Math.sin(a0)).color(r, g, b, 0F).endVertex();
                    buf.pos(rr * Math.cos(a1), y0, rr * Math.sin(a1)).color(r, g, b, 0F).endVertex();
                    buf.pos(rr * Math.cos(a1), ym, rr * Math.sin(a1)).color(r, g, b, am).endVertex();
                    buf.pos(rr * Math.cos(a0), ym, rr * Math.sin(a0)).color(r, g, b, am).endVertex();
                    buf.pos(rr * Math.cos(a0), ym, rr * Math.sin(a0)).color(r, g, b, am).endVertex();
                    buf.pos(rr * Math.cos(a1), ym, rr * Math.sin(a1)).color(r, g, b, am).endVertex();
                    buf.pos(rr * Math.cos(a1), y1, rr * Math.sin(a1)).color(r, g, b, 0F).endVertex();
                    buf.pos(rr * Math.cos(a0), y1, rr * Math.sin(a0)).color(r, g, b, 0F).endVertex();
                }
            }
            tess.draw();
        }

        // The column: nested walls of light filling the rings, warm white, streaked up and down, bright in the middle
        if (column > 0) {
            double mid = (floor + top) / 2;
            buf.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int layer = 0; layer < 5; layer++) {
                double rr = (inner - 0.08) * (0.25 + 0.75 * layer / 4.0);
                for (int i = 0; i < segments; i++) {
                    double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
                    double streak = 0.55 + 0.45 * Math.sin(i * 3.1 + layer * 2.2 + t * 1.3) * Math.sin(i * 1.7 - t * 0.7);
                    float am = (float) (0.32 * column * streak), ae = am * 0.35F;
                    buf.pos(rr * Math.cos(a0), floor, rr * Math.sin(a0)).color(1F, 0.95F, 0.9F, ae).endVertex();
                    buf.pos(rr * Math.cos(a1), floor, rr * Math.sin(a1)).color(1F, 0.95F, 0.9F, ae).endVertex();
                    buf.pos(rr * Math.cos(a1), mid, rr * Math.sin(a1)).color(1F, 0.96F, 0.92F, am).endVertex();
                    buf.pos(rr * Math.cos(a0), mid, rr * Math.sin(a0)).color(1F, 0.96F, 0.92F, am).endVertex();
                    buf.pos(rr * Math.cos(a0), mid, rr * Math.sin(a0)).color(1F, 0.96F, 0.92F, am).endVertex();
                    buf.pos(rr * Math.cos(a1), mid, rr * Math.sin(a1)).color(1F, 0.96F, 0.92F, am).endVertex();
                    buf.pos(rr * Math.cos(a1), top, rr * Math.sin(a1)).color(1F, 0.95F, 0.9F, ae).endVertex();
                    buf.pos(rr * Math.cos(a0), top, rr * Math.sin(a0)).color(1F, 0.95F, 0.9F, ae).endVertex();
                }
            }
            tess.draw();
        }

        GlStateManager.shadeModel(GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    @Override
    public boolean isGlobalRenderer(RingPlatformTE te) {
        return true; // its rings reach far beyond its block
    }
}
