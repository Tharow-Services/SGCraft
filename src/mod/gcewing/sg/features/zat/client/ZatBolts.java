package gcewing.sg.features.zat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

// The zat's bolts: thin, flickering blue-white lightning from the zat to whatever it hit, there for a moment
@SideOnly(Side.CLIENT)
public final class ZatBolts {

    // How long a bolt lasts (ticks), how far its zigzags stray from the straight line, and about how long each one is
    private static final int life = 6;
    private static final double wander = 0.045, step = 0.6;
    // How long (ticks) it takes to reach from the zat to what it hit
    private static final float travelTicks = 2;

    // Where the zat's tip is drawn: in first person, ahead of the eyes, to the side and below (in blocks along the view);
    // seen from outside, ahead of the shooter's body, to the side and up from their feet
    private static final double firstForward = 0.8, firstSide = 0.57, firstDown = 0.13;
    private static final double thirdForward = 0.6, thirdSide = 0.37, thirdUp = 0.85, sneakDrop = 0.2;

    private static final class Bolt {
        final int shooterId;
        final boolean leftHand;
        final Vec3d from, to;
        final long born; // world time
        final long seed;

        Bolt(int shooterId, boolean leftHand, Vec3d from, Vec3d to, long born, long seed) {
            this.shooterId = shooterId;
            this.leftHand = leftHand;
            this.from = from;
            this.to = to;
            this.born = born;
            this.seed = seed;
        }
    }

    private static final List<Bolt> bolts = new ArrayList<>();
    private static final Random random = new Random();
    private static World boltWorld;

    private ZatBolts() {}

    public static void add(int shooterId, boolean leftHand, Vec3d from, Vec3d to, boolean struck) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return;
        }
        if (world != boltWorld) {
            bolts.clear();
            boltWorld = world;
        }
        bolts.add(new Bolt(shooterId, leftHand, from, to, world.getTotalWorldTime(), random.nextLong()));
        if (struck) {
            // A few sparks where it struck, hanging there rather than flying off
            for (int i = 0; i < 5; i++) {
                world.spawnParticle(EnumParticleTypes.CRIT_MAGIC, to.x, to.y, to.z, 0, 0, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        Entity view = mc.getRenderViewEntity();
        if (bolts.isEmpty() || world == null || view == null) {
            return;
        }
        if (world != boltWorld) {
            bolts.clear();
            return;
        }
        float partial = event.getPartialTicks();
        long now = world.getTotalWorldTime();
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * partial;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * partial;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-cx, -cy, -cz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // adds light
        GlStateManager.depthMask(false);

        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
            Bolt bolt = it.next();
            float age = now - bolt.born + partial;
            if (age >= life || age < -1) {
                it.remove();
                continue;
            }
            float fade = 1 - Math.max(0, age) / life;
            // It shoots out from the zat to what it hit, then holds there, a new zigzag every tick so it crackles
            float reach = Math.min(1, Math.max(0, age) / travelTicks + 0.15F);
            Vec3d from = muzzle(mc, world, bolt, view, partial);
            Vec3d tip = from.add(bolt.to.subtract(from).scale(reach));
            Random shape = new Random(bolt.seed + now * 31);
            List<List<Vec3d>> strands = new ArrayList<>();
            strands.add(zigzag(from, tip, wander, shape));
            drawStrands(strands, 9F, 0.35F, 0.6F, 1.0F, 0.35F * fade); // its glow
            drawStrands(strands, 3.5F, 0.85F, 0.93F, 1.0F, 0.95F * fade); // its bright core
        }

        GL11.glLineWidth(1F);
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    // Where the bolt starts: the tip of the zat as this player sees it, following the shooter as they move, or where the
    // server said if the shooter isn't here
    private static Vec3d muzzle(Minecraft mc, World world, Bolt bolt, Entity view, float partial) {
        Entity shooter = world.getEntityByID(bolt.shooterId);
        if (!(shooter instanceof EntityLivingBase)) {
            return bolt.from;
        }
        double x = shooter.lastTickPosX + (shooter.posX - shooter.lastTickPosX) * partial;
        double y = shooter.lastTickPosY + (shooter.posY - shooter.lastTickPosY) * partial;
        double z = shooter.lastTickPosZ + (shooter.posZ - shooter.lastTickPosZ) * partial;
        double side = bolt.leftHand ? -1 : 1;
        if (shooter == view && mc.gameSettings.thirdPersonView == 0) {
            // Seen through their own eyes: along the view, to where the zat in their hand is drawn
            float pitch = shooter.prevRotationPitch + (shooter.rotationPitch - shooter.prevRotationPitch) * partial;
            float yaw = shooter.prevRotationYaw + (shooter.rotationYaw - shooter.prevRotationYaw) * partial;
            Vec3d forward = Vec3d.fromPitchYaw(pitch, yaw);
            Vec3d right = new Vec3d(-forward.z, 0, forward.x);
            right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
            Vec3d up = right.crossProduct(forward).normalize();
            Vec3d eye = new Vec3d(x, y + shooter.getEyeHeight(), z);
            return eye.add(forward.scale(firstForward)).add(right.scale(firstSide * side)).add(up.scale(-firstDown));
        }
        // Seen from outside: from their hand, the way their body faces
        EntityLivingBase body = (EntityLivingBase) shooter;
        float yaw = body.prevRenderYawOffset + (body.renderYawOffset - body.prevRenderYawOffset) * partial;
        Vec3d forward = Vec3d.fromPitchYaw(0, yaw);
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        double height = thirdUp - (shooter.isSneaking() ? sneakDrop : 0);
        return new Vec3d(x, y + height, z).add(forward.scale(thirdForward)).add(right.scale(thirdSide * side));
    }

    // Points along the line from a to b, each strayed a little to the side, most in the middle and none at the ends
    private static List<Vec3d> zigzag(Vec3d a, Vec3d b, double wander, Random random) {
        Vec3d along = b.subtract(a);
        double length = along.length();
        List<Vec3d> points = new ArrayList<>();
        points.add(a);
        if (length < 1e-3) {
            points.add(b);
            return points;
        }
        Vec3d dir = along.scale(1 / length);
        Vec3d side = dir.crossProduct(Math.abs(dir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0)).normalize();
        Vec3d up = dir.crossProduct(side).normalize();
        int n = Math.max(3, (int) Math.ceil(length / step));
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            double reach = wander * Math.min(1, Math.sin(Math.PI * t) * 3);
            points.add(a.add(along.scale(t)).add(side.scale(random.nextGaussian() * reach)).add(up.scale(random.nextGaussian() * reach)));
        }
        points.add(b);
        return points;
    }

    private static void drawStrands(List<List<Vec3d>> strands, float width, float r, float g, float b, float a) {
        GL11.glLineWidth(width);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        for (List<Vec3d> strand : strands) {
            buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (Vec3d p : strand) {
                buffer.pos(p.x, p.y, p.z).color(r, g, b, a).endVertex();
            }
            tessellator.draw();
        }
    }
}
