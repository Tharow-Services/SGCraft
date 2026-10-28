package gcewing.sg.features.naquadahgenerator.client;

import static org.lwjgl.opengl.GL11.*;

import gcewing.sg.features.naquadahgenerator.NaquadahGeneratorBlock;
import gcewing.sg.features.naquadahgenerator.NaquadahGeneratorTE;
import gcewing.sg.features.naquadahgenerator.NaquadahGenerators;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

// A running Naquadah generator's lights: its crystal (red on a Mark I, blue on a Mark II), the white rod across its
// middle, and the ring of small lights round its dome, all glowing and gently pulsing. The parts themselves are in the
// block's model; this only lights them.
public class NaquadahGeneratorRenderer extends TileEntitySpecialRenderer<NaquadahGeneratorTE> {

    // Where the lit parts are on a north-facing generator, from its middle (as GeneratorObj.java builds the model)
    private static final double crystalY = 0.364, crystalZ = -0.141;
    private static final double rodY = 0.225, rodZ = -0.102, rodHalf = 0.075;
    private static final double lightsY = 0.363, lightsRadius = 0.124;
    private static final int lights = 16, segments = 16;

    @Override
    public void render(NaquadahGeneratorTE te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (!te.running) {
            return;
        }
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof NaquadahGeneratorBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(NaquadahGeneratorBlock.FACING);
        boolean markII = ((NaquadahGeneratorBlock) state.getBlock()).mark == NaquadahGenerators.Mark.II;
        double time = te.getWorld().getTotalWorldTime() + (double) partialTicks;
        float pulse = (float) (0.8 + 0.2 * Math.sin(time * 0.15));

        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        glPushMatrix();
        glTranslated(x + 0.5, y, z + 0.5);
        glRotatef(-facing.getHorizontalAngle() + 180, 0, 1, 0); // as the block model turns: north faces -z
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glDepthMask(false);
        glShadeModel(GL_SMOOTH);

        // The crystal
        float cr = markII ? 0.25F : 1F, cg = markII ? 0.55F : 0.15F, cb = markII ? 1F : 0.25F;
        disc(0, crystalY, crystalZ, 0, 0, -1, 0.09, cr, cg, cb, 0.5F * pulse);
        disc(0, crystalY, crystalZ, 0, 0, -1, 0.035, 1F, 0.6F + 0.4F * cg, 0.6F + 0.4F * cb, 0.7F * pulse);

        // The rod: a bright bar with a soft halo
        bar(rodHalf, 0.013, 0.95F * pulse);
        bar(rodHalf + 0.02, 0.04, 0.3F * pulse);

        // The ring of lights round the dome, leaving the crystal's place in front
        for (int i = 0; i < lights; i++) {
            double angle = 2 * Math.PI * (2 * i + 1) / (2 * lights);
            if (Math.abs(Math.sin(angle)) < 0.35 && Math.cos(angle) > 0) {
                continue;
            }
            double ox = -Math.sin(angle), oz = -Math.cos(angle);
            disc(ox * lightsRadius, lightsY, oz * lightsRadius, ox, 0, oz, 0.018, 0.55F, 0.8F, 1F, 0.85F * pulse);
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

    // A glow at (cx, cy, cz) facing out along (nx, 0, nz): solid in the middle, fading to nothing at its edge
    private static void disc(double cx, double cy, double cz, double nx, double ny, double nz, double radius,
            float r, float g, float b, float a) {
        // Across it: sideways (horizontal, at right angles to the way it faces) and up
        double sx = -nz, sz = nx;
        double ox = cx + nx * 0.004, oy = cy, oz = cz + nz * 0.004;
        glBegin(GL_TRIANGLE_FAN);
        glColor4f(r, g, b, a);
        glVertex3d(ox, oy, oz);
        glColor4f(r, g, b, 0);
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            double s = radius * Math.cos(angle), u = radius * Math.sin(angle);
            glVertex3d(ox + sx * s, oy + u, oz + sz * s);
        }
        glEnd();
    }

    // The rod's glow: white along its middle, fading to nothing above and below
    private static void bar(double halfLength, double halfHeight, float a) {
        double zf = rodZ - 0.004;
        glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) {
            glColor4f(1F, 1F, 0.95F, a);
            glVertex3d(-halfLength, rodY, zf);
            glVertex3d(halfLength, rodY, zf);
            glColor4f(1F, 1F, 0.95F, 0);
            glVertex3d(halfLength, rodY + side * halfHeight, zf);
            glVertex3d(-halfLength, rodY + side * halfHeight, zf);
        }
        glEnd();
    }
}
