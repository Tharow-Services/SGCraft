//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate base tile entity renderer
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.*;
import gcewing.sg.block.SGBaseBlock;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.SGState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.Optional;

import static java.lang.Math.min;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL;

public class SGBaseTERenderer extends BaseTileEntityRenderer {

    final static int numRingSegments = 32;
    final static double ringInnerRadius = 2.0;
    final static double ringMidRadius = 2.25;
    final static double ringOuterRadius = 2.5;
    final static double ringDepth = 0.5;
    final static double ringOverlap = 1/64.0;
    final static double ringZOffset = 0.0001;
    final static double chevronInnerRadius = 2.25;
    final static double chevronOuterRadius = ringOuterRadius + 1/16.0;
    final static double chevronWidth = (chevronOuterRadius - chevronInnerRadius) * 1.5;
    final static double chevronDepth = 0.125;
    final static double chevronBorderWidth = chevronWidth / 6;
    final static double chevronMotionDistance = 1/8.0;
    
    final static int textureTilesWide = 32;
    final static int textureTilesHigh = 2;
    final static double textureScaleU = 1.0/(textureTilesWide * 16);
    final static double textureScaleV = 1.0/(textureTilesHigh * 16);
    
    final static double ringSymbolTextureLength = 512.0; //27 * 8;
    final static double ringSymbolTextureHeight = 16.0; //12;
    final static double ringSymbolSegmentWidth = ringSymbolTextureLength / numRingSegments;
    
    public final static int ehGridRadialSize = 5;
    public final static int ehGridPolarSize = numRingSegments;
    public final static double ehBandWidth = ringInnerRadius / ehGridRadialSize;
    
    final static double numIrisBlades = 12;
    
    static int chevronEngagementSequences[][] = {
        {9, 3, 4, 5, 6, 0, 1, 2, 9}, // 7 symbols (9 = never enganged)
        {7, 3, 4, 5, 8, 0, 1, 2, 6}  // 9 symbols
    };

    static double s[] = new double[numRingSegments + 1];
    static double c[] = new double[numRingSegments + 1];
    

    static {
        for (int i = 0; i <= numRingSegments; i++) {
            double a = 2 * Math.PI * i / numRingSegments;
            s[i] = Math.sin(a);
            c[i] = Math.cos(a);
        }
    }
    
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

            if (gate.gateOrientation == 1) {
                glTranslated(x + 0.5, y + 2.5, z + 0.5); // Vertical, facing SOUTH
            }

            if (gate.gateOrientation == 2) {
                if (gate.facingDirectionOfBase == 0) { // North
                    glTranslated(x + 0.5, y + 0.25, z + -1.5);
                    glRotatef(180, 0, 180, 180); // Horizontal, Face Up
                }
                if (gate.facingDirectionOfBase == 1) { // West
                    glTranslated(x + -1.5, y + 0.25, z + 0.5);
                    glRotatef(180, 180, 180, 0); // Horizontal, Face Up
                }
                if (gate.facingDirectionOfBase == 2) { // South
                    glTranslated(x + 0.5, y + 0.25, z + 2.5);
                    glRotatef(180, 0, -180, 180); // Horizontal, Face Up
                }
                if (gate.facingDirectionOfBase == 3) { // East
                    glTranslated(x + 2.5, y + 0.25, z + 0.5);
                    glRotatef(180, 180, -180, 0); // Horizontal, Face Up
                }
            }

            if (gate.gateOrientation == 3) {
                if (gate.facingDirectionOfBase == 0) { // North
                    glTranslated(x + 0.5, y + 0.25, z + -1.5);
                    glRotatef(180, 0, -180, 180); // Horizontal, Face Down
                }
                if (gate.facingDirectionOfBase == 1) { // West
                    glTranslated(x + -1.5, y + 0.25, z + 0.5);
                    glRotatef(180, 180, -180, 0); // Horizontal, Face Down

                }
                if (gate.facingDirectionOfBase == 2) { // South
                    glTranslated(x + 0.5, y + 0.25, z + 2.5);
                    glRotatef(180, 0, 180, 180); // Horizontal, Face Down
                }
                if (gate.facingDirectionOfBase == 3) { // East
                    glTranslated(x + 2.5, y + 0.25, z + 0.5);
                    glRotatef(180, 180, 180, 0); // Horizontal, Face Down
                }
            }

            renderStargate(gate, partialTicks);
            glDisable(GL_RESCALE_NORMAL);
            glPopMatrix();
        }
    }

    void renderStargate(SGBaseTE gate, float partialTicks) {
        BaseGLUtils.glMultMatrix(gate.localToGlobalTransformation(Vector3.zero));
        if (gate.gateType <= 1) {
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/milkyway/stargate.png"));
        } else {
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/pegasus/stargate.png"));
        }

        glNormal3f(0, 1, 0);
        renderRing(ringMidRadius - ringOverlap, ringOuterRadius, RingType.Outer, ringZOffset);
        renderInnerRing(gate, partialTicks);
        renderChevrons(gate);
        renderEventHorizonGrid(gate, partialTicks);
        if (gate.hasIrisUpgrade)
            renderIris(gate, partialTicks);
        if (gate.isConnected() && gate.state != SGState.SyncAwait) {
            renderEventHorizon(gate, partialTicks);
        }
    }

    void renderEventHorizonGrid(SGBaseTE gate, float partialTicks) {
        Trans3 t = gate.localToGlobalTransformation();
        Vector3 p0 = null;
        Vector3 p1 = null;

        if (gate.gateOrientation == 1) {
            p0 = new Vector3(-1.5, 0.5, -1.5);
            p1 = new Vector3(1.5, 3.5, 1.5);
        }

        if (gate.gateOrientation == 2 || gate.gateOrientation == 3) {
            p0 = new Vector3(1.5, -1.5, -0.5);
            p1 = new Vector3(-1.5, 1.5, -3.5);
        }
        AxisAlignedBB box = t.box(p0, p1);
        RenderGlobal.drawSelectionBoundingBox(ValkyrienUtils.getAABBInGlobal(box, gate.getWorld(), gate.getPos()), 255, 255, 255, 0);
    }
    
    void renderInnerRing(SGBaseTE te, float partialTicks) {
        glPushMatrix();
        glRotatef((float)(te.interpolatedRingAngle(partialTicks) + SGBaseTE.ringSymbolAngle / 2), 0, 0, 1);
        renderRing(ringInnerRadius, ringMidRadius, RingType.Inner, 0);

        glPopMatrix();
    }
        
    void renderRing(double r1, double r2, RingType type, double dz) {
        double z = ringDepth / 2 + dz;
        double u = 0, du = 0, dv = 0;
        glBegin(GL_QUADS);
        for (int i = 0; i < numRingSegments; i++) {
            selectTile(TextureIndex.RING);
            switch (type) {
                case Outer:
                    glNormal3d(c[i], s[i], 0);
                    vertex(r2 * c[i], r2 * s[i], z, 0, 0);
                    vertex(r2 * c[i], r2 * s[i], -z, 0, 16);
                    vertex(r2 * c[i + 1], r2 * s[i + 1], -z, 16, 16);
                    vertex(r2 * c[i + 1], r2 * s[i + 1], z, 16, 0);
                    break;
                case Inner:
                    glNormal3d(-c[i], -s[i], 0);
                    vertex(r1 * c[i], r1 * s[i], -z, 0, 0);
                    vertex(r1 * c[i], r1 * s[i], z, 0, 16);
                    vertex(r1 * c[i + 1], r1 * s[i + 1], z, 16, 16);
                    vertex(r1 * c[i + 1], r1 * s[i + 1], -z, 16, 0);
                    break;
            }
            // Back
            glNormal3f(0, 0, -1);
            vertex(r1*c[i],   r1*s[i],   -z,    0, 16);
            vertex(r1*c[i+1], r1*s[i+1], -z,   16, 16);
            vertex(r2*c[i+1], r2*s[i+1], -z,   16,  0);
            vertex(r2*c[i],   r2*s[i],   -z,    0,  0);
            // Front
            glNormal3f(0, 0, 1);
            switch (type) {
                case Outer:
                    selectTile(TextureIndex.RING_FACE);
                    u = 0;
                    du = 16;
                    dv = 16;
                    break;
                case Inner:
                    selectTile(TextureIndex.RING_SYMBOL);
                    u = ringSymbolTextureLength - (i + 1) * ringSymbolSegmentWidth;
                    du = ringSymbolSegmentWidth;
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
    
    void renderChevrons(SGBaseTE te) {
        int numChevrons = te.getNumChevrons();
        int i0 = numChevrons > 7 ? 0 : 1;
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
        float a = te.angleBetweenChevrons();
        for (int i = i0; i < i0 + numChevrons; i++) {
            int j = chevronEngagementSequences[k][i];
            boolean engaged = te.chevronIsEngaged(j);
            renderChevronAtPosition(te, i, a, engaged);
        }
    }

    // Render a chevron at the given position (0 to 8, with 4 being top dead centre)
    void renderChevronAtPosition(SGBaseTE gate, int i, float a, boolean engaged) {
        glPushMatrix();

        if (gate.gateOrientation == 1) {
            glRotatef(90 - (i - 4) * a, 0, 0, 1);
        }

        if (gate.gateOrientation == 2) {
            glRotatef(-90 - (i - 4) * a, 0, 0, 1);
        }

        if (gate.gateOrientation == 3) {
            glRotatef(90 - (i - 4) * a, 0, 0, 1);
        }

        chevron(engaged);
        glPopMatrix();
    }
    
    void chevron(boolean engaged) {
        double r1 = chevronInnerRadius;
        double r2 = chevronOuterRadius;
        double z2 = ringDepth / 2;
        double z1 = z2 + chevronDepth;
        double w1 = chevronBorderWidth;
        double w2 = w1 * 1.25;
        double x1 = r1, y1 = chevronWidth / 4;
        double x2 = r2, y2 = chevronWidth / 2;
        
        if (engaged)
            glTranslated(-chevronMotionDistance, 0, 0);
        glBegin(GL_QUADS);
        
        selectTile(TextureIndex.CHEVRON);
        
        // Face 1
        vertex(x2, y2, z1, 0, 2);
        vertex(x1, y1, z1, 0, 16);
        vertex(x1+w1, y1-w1, z1, 4, 12);
        vertex(x2, y2-w2, z1, 4, 2);
        
        // Side 1
        vertex(x2, y2, z1, 0, 0);
        vertex(x2, y2, z2, 0, 4);
        vertex(x1, y1, z2, 16, 4);
        vertex(x1, y1, z1, 16, 0);
        
        // End 1
        vertex(x2, y2, z1, 16, 0);
        vertex(x2, y2-w2, z1, 12, 0);
        vertex(x2, y2-w2, z2, 12, 4);
        vertex(x2, y2, z2, 16, 4);
        
        // Face 2
        vertex(x1+w1, y1-w1, z1, 4, 12);
        vertex(x1, y1, z1, 0, 16);
        vertex(x1, -y1, z1, 16, 16);
        vertex(x1+w1, -y1+w1, z1, 12, 12);
        
        // Side 2
        vertex(x1, y1, z1, 0, 0);
        vertex(x1, y1, z2, 0, 4);
        vertex(x1, -y1, z2, 16, 4);
        vertex(x1, -y1, z1, 16, 0);
        
        // Face 3
        vertex(x2, -y2+w2, z1, 12, 0);
        vertex(x1+w1, -y1+w1, z1, 12, 12);
        vertex(x1, -y1, z1, 16, 16);
        vertex(x2, -y2, z1, 16, 0);
        
        // Side 3
        vertex(x1, -y1, z1, 0, 0);
        vertex(x1, -y1, z2, 0, 4);
        vertex(x2, -y2, z2, 16, 4);
        vertex(x2, -y2, z1, 16, 0);
        
        // End 3
        vertex(x2, -y2, z1, 0, 0);
        vertex(x2, -y2, z2, 0, 4);
        vertex(x2, -y2+w2, z2, 4, 4);
        vertex(x2, -y2+w2, z1, 4, 0);
        
        // Back
        vertex(x2, -y2, z2, 0, 0);
        vertex(x1, -y1, z2, 0, 16);
        vertex(x1, y1, z2, 16, 16);
        vertex(x2, y2, z2, 16, 0);
        
        glEnd();

        selectTile(TextureIndex.CHEVRON_LIT);
        if (!engaged)
            glColor3d(0.5, 0.5, 0.5);
        else {
            glDisable(GL_LIGHTING);
            setLightingDisabled(true);
        }
        glBegin(GL_QUADS);

        // Face 4
        vertex(x2, y2-w2, z1, 0, 4);
        vertex(x1+w1, y1-w1, z1, 4, 16);
        vertex(x1+w1, 0, z1, 8, 16);
        vertex(x2, 0, z1, 8, 4);
        
        vertex(x2, 0, z1, 8, 4);
        vertex(x1+w1, 0, z1, 8, 16);
        vertex(x1+w1, -y1+w1, z1,  12, 16);
        vertex(x2, -y2+w2, z1, 16, 4);
        
        // End 4
        vertex(x2, y2-w2, z2, 0, 0);
        vertex(x2, y2-w2, z1, 0, 4);
        vertex(x2, -y2+w2, z1, 16, 4);
        vertex(x2, -y2+w2, z2, 16, 0);
        
        glColor3f(1, 1, 1);
        glEnd();
        glEnable(GL_LIGHTING);
        setLightingDisabled(false);
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
        double[][] grid = te.getEventHorizonGrid()[0];
        double rclip = 2.5 * (te.irisIsClosed() ?  te.getIrisAperture(partialTicks) : 1.0);
        for (int i = 1; i < ehGridRadialSize; i++) {
            glBegin(GL_QUAD_STRIP);
            for (int j = 0; j <= ehGridPolarSize; j++) {
                ehVertex(grid, i, j, rclip);
                ehVertex(grid, i+1, j, rclip);
            }
            glEnd();
        }
        glBegin(GL_TRIANGLE_FAN);
        glTexCoord2d(0, 0);
        glVertex3d(0, 0, ehClip(grid[1][0], 0, rclip));
        for (int j = 0; j <= ehGridPolarSize; j++)
            ehVertex(grid, 1, j, rclip);
        glEnd();
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        GL11.glEnable(GL_LIGHTING);
        setLightingDisabled(false);
    }
    
    void ehVertex(double[][] grid, int i, int j, double rclip) {
        double r = i * ehBandWidth;
        double x = r * c[j];
        double y = r * s[j];
        double z = ehClip(grid[j+1][i], r, rclip);
        glTexCoord2d(x, y);
        glVertex3d(x, y, z);
    }
    
    double ehClip(double z, double r, double rclip) {
        if (r >= rclip)
            z = min(z, 0);
        return z;
    }       
    
    void renderIris(SGBaseTE te, double t) {
        if (te.gateType <= 1) {
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/milkyway/iris.png"));
        } else {
            bindTexture(SGCraft.mod.resourceLocation("textures/tileentity/pegasus/iris.png"));
        }
        double a = 0.8 * te.getIrisAperture(t);
        for (int i = 0; i < numIrisBlades; i++) {
            glPushMatrix();
            glRotated(360.0 * i / numIrisBlades, 0.0, 0.0, 1.0);
            renderIrisBlade(te, a, t);
            glPopMatrix();
        }
    }
    
    void renderIrisBlade(SGBaseTE te, double a, double t) {
        double aa = a * 60;
        double r  = 2.31;
        double w1 = 2.40;
        double w2 = 1.85;
        double h0 = 0.16;
        double h1 = 1.00;
        double h = h1 - (h1 - h0) * a * a;
        double u = w2 / w1;
        double v = h / h1;
        double v0 = h0 / h1;
        double z0 = SGBaseTE.irisZPosition; //0.1;
        double z1 = 0.01;
        glPushMatrix();
        glTranslated(r, 0, 0);
        glRotated(-aa, 0, 0, 1);
        glBegin(GL_TRIANGLE_FAN);
        glTexCoord2d(0, 0); glVertex3d(-w1, 0, z0);
        glTexCoord2d(1, 0); glVertex3d(0, 0, z0 + z1);
        glTexCoord2d(1, v0); glVertex3d(0, h0, z0 + z1);
        glTexCoord2d(u, v); glVertex3d(-w1 + w2, h, z0);
        glTexCoord2d(0, v); glVertex3d(-w1, h, z0);
        glEnd();
        glBegin(GL_TRIANGLE_FAN);
        glTexCoord2d(0, 0); glVertex3d(-w1, 0, z0);
        glTexCoord2d(0, v); glVertex3d(-w1, h, z0);
        glTexCoord2d(u, v); glVertex3d(-w1 + w2, h, z0);
        glTexCoord2d(1, v0); glVertex3d(0, h0, z0 - z1);
        glTexCoord2d(1, 0); glVertex3d(0, 0, z0 - z1);
        glEnd();
        glPopMatrix();
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

