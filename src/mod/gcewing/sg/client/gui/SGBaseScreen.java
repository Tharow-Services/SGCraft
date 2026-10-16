//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate base gui screen
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.gui;

import gcewing.sg.util.SGAddressing;
import gcewing.sg.container.SGBaseContainer;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.SGCraft;
import gcewing.sg.network.SupergatePowerNetworkHandler;
import gcewing.sg.util.TimeFormat;
import net.minecraft.entity.player.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import java.text.DecimalFormat;

public class SGBaseScreen extends SGScreen {

    static String screenTitle = "Stargate Address";
    static final int guiWidth = 256;
    int guiHeight = 208; //92;
    static final int fuelGaugeWidth = 16;
    static final int fuelGaugeHeight = 34;
    static final int fuelGaugeX = 214;
    static final int fuelGaugeY = 84;
    static final int fuelGaugeU = 0;
    static final int fuelGaugeV = 208;
    
    private SGBaseTE te;
    private String address;
    private String formattedAddress;
    private boolean addressValid;
    private EntityPlayer player;

    // A supergate's power, from the server (see SupergatePowerNetworkHandler); zpmsNeeded is 0 until it arrives
    private int zpms, zpmsNeeded;
    private double zpmEnergy, openCost, tickCost;
    private boolean noPower;
    private long lastPowerRequest = 0;
    private static final DecimalFormat whole = new DecimalFormat("###,###,###,###,##0");
    private static final DecimalFormat fraction = new DecimalFormat("###,###,##0.##");
    
    public static SGBaseScreen create(EntityPlayer player, World world, BlockPos pos) {
        SGBaseTE te = SGBaseTE.at(world, pos);
        if (te != null) {
            return new SGBaseScreen(player, te);
        } else {
            return null;
        }
    }

    private SGBaseScreen(EntityPlayer player, SGBaseTE te) {
        super(new SGBaseContainer(player, te), guiWidth, 208);
        this.te = te;
        this.player = player;
        this.te.markBlockForUpdate();
        getAddress();
        if (this.addressValid) {
            //System.out.printf("SGBaseScreen: Copying address %s to clipboard\n", formattedAddress);
            if (SGCraft.saveAddressToClipboard) {
                setClipboardString(this.formattedAddress);
            }
        }
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void drawBackgroundLayer() {
        if (SGBaseContainer.showsInventory(player, this.te)) {
            bindTexture(SGCraft.mod.resourceLocation("textures/gui/sg_gui.png"), 256, 256);
            guiHeight = 208;
        } else {
            guiHeight = 92;
            bindTexture(SGCraft.mod.resourceLocation("textures/gui/sg_gui_noinventory.png"), 256, 125);
        }
        drawTexturedRect(0, 0, guiWidth, guiHeight, 0, 0);
        int cx = this.xSize / 2;
        if (this.addressValid)
            if (this.te.displayGateAddress) {
                drawAddressSymbols(cx, 22, this.address);
            } else {
                drawCenteredString("... Gate Address Hidden ...", cx, 42);
            }
        setTextColor(0x004c66);
        drawCenteredString(this.screenTitle, cx, 8);
        if (this.te.displayGateAddress) {
            drawCenteredString(this.formattedAddress, cx, 72);
        }
        if (this.te.numCamouflageSlots > 0 && SGBaseContainer.showsInventory(player, this.te))
            drawCenteredString("Base Camouflage", 92, 92);
        if (this.te.isSupergate() && SGBaseContainer.showsInventory(player, this.te)) {
            drawZpmSlots();
        }
        if (this.te.isSupergate()) {
            drawSupergatePower();
        }
    }

    // A supergate's ZPM slots, beside the camouflage: the screen's texture has no frames for them, so they're drawn
    private void drawZpmSlots() {
        setTextColor(0x004c66);
        drawCenteredString("ZPMs", SGBaseContainer.zpmSlotsX + 26, 92);
        for (int i = 0; i < SGBaseTE.numZpmSlots; i++) {
            int x = SGBaseContainer.zpmSlotsX + i * 18 - 1, y = SGBaseContainer.zpmSlotsY - 1;
            setColor(0x373737);
            drawRect(x, y, 17, 1);
            drawRect(x, y, 1, 17);
            setColor(0xffffff);
            drawRect(x + 1, y + 17, 17, 1);
            drawRect(x + 17, y + 1, 1, 17);
            setColor(0x8b8b8b);
            drawRect(x + 1, y + 1, 16, 16);
        }
        resetColor();
    }

    // Below the screen: the ZPMs a supergate runs on, and what it costs to run, as the city shield's screen shows
    private void drawSupergatePower() {
        if (System.currentTimeMillis() > this.lastPowerRequest + 1000) {
            this.lastPowerRequest = System.currentTimeMillis();
            SupergatePowerNetworkHandler.requestPowerFromServer(this.te.getPos());
        }
        int top = this.guiHeight + 4, x = 10, line = 11;
        // A plain panel in the screen's colours: a dark edge round light grey
        setColor(0x373737);
        drawRect(0, top, guiWidth, 6 * line + 8);
        setColor(0xc6c6c6);
        drawRect(2, top + 2, guiWidth - 4, 6 * line + 4);
        resetColor();
        setTextColor(0x404040);
        drawCenteredString("Supergate Power", this.xSize / 2, top + 5);
        if (this.zpmsNeeded == 0) {
            drawString("...", x, top + 5 + line);
            return;
        }
        int y = top + 5 + line;
        if (this.noPower) {
            setTextColor(0x207020);
            drawString("No power required: it needs no ZPMs", x, y);
            return;
        }
        setTextColor(this.zpms >= this.zpmsNeeded ? 0x207020 : 0xa02020);
        drawString("ZPMs: " + this.zpms + " / " + this.zpmsNeeded + (this.zpms >= this.zpmsNeeded ? "" : "  - not enough to dial"), x, y);
        setTextColor(0x404040);
        drawString("ZPM energy: " + whole.format(this.zpmEnergy), x, y += line);
        drawString("To open: " + whole.format(this.openCost) + " (nearby; more further)", x, y += line);
        drawString("Held open: " + fraction.format(this.tickCost) + " per tick", x, y += line);
        String opens = this.openCost > 0 ? whole.format(Math.floor(this.zpmEnergy / this.openCost)) : "unlimited";
        String open = this.tickCost > 0 ? TimeFormat.runTime(this.zpmEnergy / this.tickCost) : "unlimited";
        drawString("Opens left: " + opens + "   Stays open: " + open, x, y += line);
    }

    public void setSupergatePower(BlockPos pos, int zpms, int zpmsNeeded, double zpmEnergy, double openCost, double tickCost,
            boolean noPower) {
        if (pos.equals(this.te.getPos())) {
            this.noPower = noPower;
            this.zpms = zpms;
            this.zpmsNeeded = zpmsNeeded;
            this.zpmEnergy = zpmEnergy;
            this.openCost = openCost;
            this.tickCost = tickCost;
        }
    }

    private void getAddress() {
        // Fix if a gate is constructed but the base block is removed, then replaced, the home address isn't getting properly set again.
        if (this.te.homeAddress != null) {
            this.address = this.te.homeAddress;
            this.formattedAddress = SGAddressing.formatAddress(this.address, "-", "-");
            this.addressValid = true;
        } else {
            this.address = "";
            this.formattedAddress = this.te.addressError;
            this.addressValid = false;
        }
    }
}
