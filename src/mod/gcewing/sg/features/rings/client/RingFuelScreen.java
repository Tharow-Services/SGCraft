package gcewing.sg.features.rings.client;

import gcewing.sg.SGCraft;
import gcewing.sg.client.gui.SGScreen;
import gcewing.sg.features.rings.RingFuelContainer;
import gcewing.sg.features.rings.RingPlatformTE;
import gcewing.sg.features.rings.RingTransports;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.text.DecimalFormat;

// A ring platform's fuel screen, without MalisisCore: its naquadah slots, its energy and what it adds up to. Drawn on the
// DHD's fuel screen, as its slots and gauge are where that has them.
public class RingFuelScreen extends SGScreen {

    private static final DecimalFormat whole = new DecimalFormat("###,###,###,##0");
    private static final int gaugeWidth = 16, gaugeHeight = 34, gaugeX = 214, gaugeY = 84, gaugeU = 0, gaugeV = 208;

    private final RingPlatformTE te;

    public static RingFuelScreen create(EntityPlayer player, World world, BlockPos pos) {
        RingPlatformTE te = RingTransports.platformAt(world, pos);
        return te != null ? new RingFuelScreen(player, te) : null;
    }

    public RingFuelScreen(EntityPlayer player, RingPlatformTE te) {
        super(new RingFuelContainer(player, te), RingFuelContainer.guiWidth, RingFuelContainer.guiHeight);
        this.te = te;
    }

    @Override
    protected void drawBackgroundLayer() {
        bindTexture(SGCraft.mod.resourceLocation("textures/gui/dhd_fuel_gui.png"), 256, 256);
        drawTexturedRect(0, 0, RingFuelContainer.guiWidth, RingFuelContainer.guiHeight, 0, 0);

        // Its energy, filling the gauge
        int level = (int) Math.min(gaugeHeight, (long) gaugeHeight * te.energy / Math.max(1, RingTransports.energyCapacity));
        GL11.glEnable(GL11.GL_BLEND);
        drawTexturedRect(gaugeX, gaugeY + gaugeHeight - level, gaugeWidth, level, gaugeU, gaugeV);
        GL11.glDisable(GL11.GL_BLEND);

        setTextColor(0x004c66);
        drawCenteredString(I18n.format("sgcraft.gui.rings.label.title"), xSize / 2, 8);
        drawRightAlignedString(I18n.format("sgcraft.gui.rings.label.energy") + ":", 125, 40);
        drawString(whole.format(te.energy) + " / " + whole.format(RingTransports.energyCapacity) + " FE", 130, 40);
        drawRightAlignedString(I18n.format("sgcraft.gui.rings.label.naquadah") + ":", 125, 50);
        drawString(whole.format(te.naquadahCount()) + " (" + whole.format((long) te.naquadahCount() * RingTransports.energyPerNaquadah) + " FE)", 130, 50);
        drawRightAlignedString(I18n.format("sgcraft.gui.rings.label.uses") + ":", 125, 60);
        drawString(whole.format(te.energyAvailable() / Math.max(1, RingTransports.energyPerUse)), 130, 60);
        drawRightAlignedString(I18n.format("sgcraft.gui.rings.label.linked") + ":", 125, 70);
        drawString(te.linked ? te.linkPos.getX() + ", " + te.linkPos.getY() + ", " + te.linkPos.getZ()
            : I18n.format("sgcraft.gui.rings.label.notLinked"), 130, 70);
        drawString(I18n.format("sgcraft.gui.rings.label.fuel"), 150, 96);
    }
}
