package gcewing.sg.features.pdd.client.gui;

import static gcewing.sg.tileentity.SGBaseTE.sendErrorMsg;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor3f;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glTexCoord2d;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glVertex3d;

import com.google.common.eventbus.Subscribe;
import gcewing.sg.SGCraft;
import gcewing.sg.features.pdd.AddressData;
import gcewing.sg.features.pdd.network.PddNetworkHandler;
import gcewing.sg.tileentity.DHDTE;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.tileentity.data.GateAccessData;
import gcewing.sg.util.GateUtil;
import gcewing.sg.util.SGAddressing;
import gcewing.sg.util.SGState;
import net.malisis.core.client.gui.Anchor;
import net.malisis.core.client.gui.BasicScreen;
import net.malisis.core.client.gui.MalisisGui;
import net.malisis.core.client.gui.component.UIComponent;
import net.malisis.core.client.gui.component.container.BasicContainer;
import net.malisis.core.client.gui.component.container.BasicForm;
import net.malisis.core.client.gui.component.container.BasicList;
import net.malisis.core.client.gui.component.decoration.UILabel;
import net.malisis.core.client.gui.component.decoration.UISeparator;
import net.malisis.core.client.gui.component.interaction.UIButton;
import net.malisis.core.client.gui.component.interaction.UICheckBox;
import net.malisis.core.client.gui.component.interaction.button.builder.UIButtonBuilder;
import net.malisis.core.renderer.font.FontOptions;
import net.malisis.core.util.FontColors;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;

public class PddScreen extends BasicScreen {
    private int lastUpdate = 0;
    private boolean unlockMouse = true;
    private boolean isAdmin;
    private BasicForm form;
    private BasicContainer<?> addressContainer;
    private UIButton addAddressButton, editAddressButton, deleteAddressButton, buttonReset, closeButton, buttonDial, buttonDisconnect;
    private UILabel localGateAddressLabel, gateStatusLabel, availableAddressesLabel, addressTextureLabel, userFeedbackLabel;
    private UISeparator valuesSeparator;
    private UICheckBox autoCloseChk;
    private BlockPos location;
    private BlockPos gatePos;
    private World world;
    private EntityPlayer player;
    private boolean delayedUpdate = true;
    private int digit = 0;
    private boolean dialling = false;
    private String enteredAddress = "";
    private String diallingAddress = "";
    private GState gstate = new GState();
    private SGBaseTE localGate = null;
    private boolean last = false;
    public boolean firstOpen = true;
    private List<AddressData> clonedList;
    private BasicList<AddressData> addressList;
    private long timer;

    public PddScreen(EntityPlayer player, World worldIn, BlockPos gatePos, boolean isAdmin) {
        this.player = player;
        this.isAdmin = true;
        this.world = worldIn;
        this.location = new BlockPos(player.posX, player.posY, player.posZ);
        this.gatePos = gatePos;
    }

    @Override
    public void construct() {
        long timer = System.currentTimeMillis();

        this.guiscreenBackground = false;
        Keyboard.enableRepeatEvents(true);
        TileEntity localGateTE = world.getTileEntity(gatePos);

        if (localGateTE instanceof SGBaseTE) {
            localGate = (SGBaseTE) localGateTE;
        }

        // Master Panel
        this.form = new BasicForm(this, 300, 225, TextFormatting.WHITE + I18n.format("sgcraft.gui.pdd.label.personalDialerDevice"));
        this.form.setMovable(true);
        this.form.setBackgroundAlpha(255);

        // ****************************************************************************************************************************
        // Address List Area
        // ****************************************************************************************************************************
        this.addressContainer = new BasicContainer(this, 295, 185);
        this.addressContainer.setPosition(0, 0, Anchor.LEFT | Anchor.MIDDLE);
        this.addressContainer.setBorder(FontColors.WHITE, 1, 185);
        this.addressContainer.setPadding(0, 3);
        this.addressContainer.setBackgroundAlpha(0);

        availableAddressesLabel = new UILabel(this, TextFormatting.WHITE + I18n.format("sgcraft.gui.pdd.label.availableAddresses")+":");
        availableAddressesLabel.setPosition(5, 1, Anchor.LEFT | Anchor.TOP);

        localGateAddressLabel = new UILabel(this, "gateAddress");
        localGateAddressLabel.setFontOptions(FontOptions.builder().from(FontColors.BLUE_FO).shadow(true).scale(1.0F).build());
        localGateAddressLabel.setPosition(-5, 1, Anchor.RIGHT | Anchor.TOP);

        gateStatusLabel = new UILabel(this, TextFormatting.WHITE + I18n.format("sgcraft.gui.pdd.label.dialing"));
        gateStatusLabel.setFontOptions(FontOptions.builder().from(FontColors.BLUE_FO).shadow(true).scale(1.8F).build());
        gateStatusLabel.setPosition(-5, 50, Anchor.CENTER | Anchor.TOP);
        gateStatusLabel.setVisible(true);

        addressTextureLabel = new UILabel(this, "... template ...");
        addressTextureLabel.setFontOptions(FontOptions.builder().from(FontColors.BLUE_FO).shadow(true).scale(1.8F).build());
        addressTextureLabel.setPosition(-5, 60, Anchor.CENTER | Anchor.TOP);
        addressTextureLabel.setVisible(false);

        valuesSeparator = new UISeparator(this);
        valuesSeparator.setSize(this.addressContainer.getWidth() - 55, 1);
        valuesSeparator.setPosition(0, 70, Anchor.TOP | Anchor.CENTER);

        this.addressList = new BasicList<>(this, UIComponent.INHERITED, this.addressContainer.getHeight() - 14);
        this.addressList.setPosition(0, BasicScreen.getPaddedY(availableAddressesLabel, 2));
        this.addressList.setItemComponentFactory(AddressItemComponent::new);
        this.addressList.setItemComponentSpacing(1);
        this.addressList.setPadding(2);
        this.addressList.setBorder(FontColors.WHITE, 1, 185);
        this.addressList.setBorders(FontColors.WHITE, 185, 0, 1, 0, 0);
        this.addressList.setName("List");
        this.addressList.register(this);

        this.addressContainer.add(availableAddressesLabel, localGateAddressLabel, gateStatusLabel, addressTextureLabel, this.addressList);

        // ****************************************************************************************************************************

        this.addAddressButton = new UIButtonBuilder(this)
            .text(TextFormatting.GREEN + "+")
            .onClick(() -> new PddEntryScreen(this, player, "Name Here", "Address Here", 10, 0, false, false,false, 1).display())
            .anchor(Anchor.BOTTOM | Anchor.LEFT)
            .position(0, 0)
            //.visible(this.canAdd)
            .build("button.add");

        this.deleteAddressButton = new UIButtonBuilder(this)
            .text(TextFormatting.RED + "-")
            .onClick(() -> {
                if (addressList.getSelectedItem() != null && !addressList.getSelectedItem().isLocked()) {
                    new PddEntryScreen(this, player, addressList.getSelectedItem().getName(), addressList.getSelectedItem().getAddress(), addressList.getSelectedItem().getIndex(), addressList.getSelectedItem().getUnid(), addressList.getSelectedItem().isLocked(), addressList.getSelectedItem().isAutoClose(), true,3).display();
                }
            })
            .anchor(Anchor.BOTTOM | Anchor.LEFT)
            .position(BasicScreen.getPaddedX(this.addAddressButton, 2), 0)
            //.visible(this.canRemove)
            .build("button.remove");

        this.editAddressButton = new UIButtonBuilder(this)
            .text(TextFormatting.YELLOW + "?")
            .onClick(() -> new PddEntryScreen(this, player, addressList.getSelectedItem().getName(), addressList.getSelectedItem().getAddress(), addressList.getSelectedItem().getIndex(), addressList.getSelectedItem().getUnid(), addressList.getSelectedItem().isLocked(), addressList.getSelectedItem().isAutoClose(),false,2).display())
            .anchor(Anchor.BOTTOM | Anchor.LEFT)
            .position(BasicScreen.getPaddedX(this.deleteAddressButton, 2), 0)
            //.visible(this.canModify)
            .build("button.details");

        this.autoCloseChk = new UICheckBox(this, TextFormatting.WHITE + "Auto Close");
        this.autoCloseChk.setAnchor(Anchor.BOTTOM | Anchor.LEFT);
        this.autoCloseChk.setPosition(BasicScreen.getPaddedX(this.editAddressButton, 2), -2);
        this.autoCloseChk.setEnabled(false);
        this.autoCloseChk.setChecked(false);

        buttonDial = new UIButtonBuilder(this)
            .width(40)
            .anchor(Anchor.BOTTOM | Anchor.CENTER)
            .text(TextFormatting.WHITE + I18n.format("sgcraft.gui.button.dialSelectedAddress"))
            .visible(false)
            .onClick(() -> {
                dial();
            })
            .build("button.dial");

        buttonReset = new UIButtonBuilder(this)
                .width(40)
                .anchor(Anchor.BOTTOM | Anchor.CENTER)
                .text(TextFormatting.WHITE + I18n.format("sgcraft.gui.button.reset"))
                .visible(false)
                .onClick(() -> {
                    resetGui();
                })
                .build("button.reset");

        buttonDisconnect = new UIButtonBuilder(this)
            .width(40)
            .anchor(Anchor.BOTTOM | Anchor.CENTER)
            .text(TextFormatting.WHITE + I18n.format("sgcraft.gui.button.disconnect"))
            .visible(false)
            .onClick(() -> {
                TileEntity localGate = GateUtil.locateLocalGate(this.world, this.location, SGCraft.toolSearchRange, false);
                if (!(localGate instanceof SGBaseTE)) {
                    return;
                } else if (localGate != null) {
                    PddNetworkHandler.sendPddInputToServer((SGBaseTE) localGate, 2, "", "");
                }
            })
            .build("button.disconnect");

        final UIButton buttonClose = new UIButtonBuilder(this)
            .width(40)
            .anchor(Anchor.BOTTOM | Anchor.RIGHT)
            .text(TextFormatting.WHITE + I18n.format("sgcraft.gui.button.close"))
            .onClick(this::close)
            .build("button.close");

        userFeedbackLabel = new UILabel(this, TextFormatting.WHITE + I18n.format("sgcraft.gui.pdd.label.warning"));
        userFeedbackLabel.setPosition(-15, -3, Anchor.CENTER | Anchor.BOTTOM);

        this.form.add(this.addressContainer, addAddressButton, editAddressButton, deleteAddressButton, autoCloseChk, buttonDial, buttonReset, buttonDisconnect, userFeedbackLabel, buttonClose);
        addToScreen(this.form);
        //this.readAddresses(player);
    }

    @Subscribe
    public void onListChange(BasicList.SelectEvent<GateAccessData> event) {
        //System.out.println("Ping");
        if (this.addressList.getSize() == 0) {
            this.deleteAddressButton.setEnabled(false);
            this.editAddressButton.setEnabled(false);
            this.buttonDial.setEnabled(false);
        } else {
            this.deleteAddressButton.setEnabled(true);
            this.editAddressButton.setEnabled(true);
            this.buttonDial.setEnabled(true);
        }
    }

    @Override
    public void update(int mouseX, int mouseY, float partialTick) {
        super.update(mouseX, mouseY, partialTick);

        if (unlockMouse && this.lastUpdate == 25) {
            Mouse.setGrabbed(false); // Force the mouse to be visible even though Mouse.isGrabbed() is false.  //#BugsUnited.
            unlockMouse = false; // Only unlock once per session.
        }

        this.refresh();
        this.detectChange();

        if (System.currentTimeMillis() > timer + 750) {
            this.checkDiallingStatus();
            timer = System.currentTimeMillis();
        }

        if (++this.lastUpdate > 125) {
            this.lastUpdate = 0;
        }
    }

    private void refresh() { // Method is checked every frame.
        if (localGate != null) {
            if (localGate.isMerged) {
                this.localGateAddressLabel.setText(SGAddressing.formatAddress(localGate.homeAddress, "-", "-"));
            } else {
                this.localGateAddressLabel.setText(TextFormatting.WHITE + I18n.format("sgcraft.gui.pdd.label.noLocalStargateFound"));
            }
            if (localGate != null) {

                if ((this.dialling || this.last || localGate.state == SGState.SyncAwait || localGate.state == SGState.Transient)) {
                    this.buttonDial.setVisible(false);
                    this.addressList.setVisible(false);
                    this.autoCloseChk.setVisible(false);
                    this.availableAddressesLabel.setVisible(false);
                    this.localGateAddressLabel.setVisible(false);
                    if (localGate.state == SGState.SyncAwait || localGate.state == SGState.Transient) {
                        if (this.autoCloseChk.isChecked()) {
                            this.close();
                        }
                        this.gateStatusLabel.setText(I18n.format("sgcraft.gui.pdd.label.establishing"));
                        gateStatusLabel.setFontOptions(FontOptions.builder().from(FontColors.GREEN_FO).shadow(true).scale(1.8F).build());
                        if (this.enteredAddress.isEmpty()) {
                            this.enteredAddress = localGate.dialledAddress;
                        }
                    }
                } else {

                    if (localGate.state == SGState.Idle && !localGate.errorState && !dialling) {
                        this.addressList.setVisible(true);
                        this.autoCloseChk.setVisible(true);
                        if (this.addressList.getSize() > 0 && this.addressList.getSelectedItem() != null && !this.addressList.getSelectedItem().getAddress().isEmpty()) {
                            if (this.addressList.getSelectedItem().getAddress().equalsIgnoreCase(this.localGateAddressLabel.getText())) {
                                this.buttonDial.setEnabled(false);
                            } else {
                                this.buttonDial.setEnabled(true);
                            }
                        }
                        this.buttonDial.setVisible(true);
                        this.localGateAddressLabel.setVisible(true);
                        this.availableAddressesLabel.setVisible(true);
                        this.buttonReset.setVisible(false);
                    }

                    if (localGate.state == SGState.Dialling) {
                        if (this.enteredAddress.isEmpty()) {
                            this.enteredAddress = localGate.dialledAddress;
                        }
                        if (this.diallingAddress.isEmpty()) {
                            this.diallingAddress = this.enteredAddress;
                        }

                        this.addressList.setVisible(false);
                        this.autoCloseChk.setVisible(false);
                        this.buttonDisconnect.setVisible(true);
                        this.gateStatusLabel.setFontOptions(FontOptions.builder().from(FontColors.BLUE_FO).shadow(true).scale(1.8F).build());
                        this.gateStatusLabel.setText(I18n.format("sgcraft.gui.pdd.label.dialing"));
                        this.buttonReset.setVisible(true);
                    }

                    if (localGate.state == SGState.Disconnecting) {
                        this.addressList.setVisible(false);
                        this.autoCloseChk.setVisible(false);
                        this.gateStatusLabel.setFontOptions(FontOptions.builder().from(FontColors.YELLOW_FO).shadow(true).scale(1.8F).build());
                        this.gateStatusLabel.setText(I18n.format("sgcraft.gui.pdd.label.disconnecting"));

                    }
                    if (localGate.state == SGState.Connected) {
                        if (this.enteredAddress.isEmpty()) {
                            this.enteredAddress = localGate.dialledAddress;
                        }
                        if (this.diallingAddress.isEmpty()) {
                            this.diallingAddress = this.enteredAddress;
                        }

                        this.addressList.setVisible(false);
                        this.buttonDisconnect.setVisible(true);
                        this.gateStatusLabel.setText(I18n.format("sgcraft.gui.pdd.label.connected"));
                        this.gateStatusLabel.setFontOptions(FontOptions.builder().from(FontColors.BLUE_FO).shadow(true).scale(1.8F).build());
                    }
                }

                if (localGate.errorState) {
                    this.addressList.setVisible(false);
                    this.buttonDisconnect.setVisible(true);
                    this.gateStatusLabel.setFontOptions(FontOptions.builder().from(FontColors.RED_FO).shadow(true).scale(1.8F).build());
                    this.gateStatusLabel.setText(I18n.format("sgcraft.gui.pdd.label.error"));
                    this.diallingAddress = localGate.dialledAddress; // Account for someone opening the GUI to a connected gate.
                    dialling = false;
                    this.buttonDial.setVisible(false);
                    this.buttonReset.setVisible(true);
                }

                if (!this.addressList.isVisible() && (!(localGate.state == SGState.Disconnecting)) && !this.gateStatusLabel.getText().equalsIgnoreCase("... Error ...")) {
                    this.drawAddressSymbols(getXStartLocation(diallingAddress), this.gateStatusLabel.screenY() + 40, this.enteredAddress);
                }

                if (localGate.isConnected() && localGate.state == SGState.Connected) {
                    this.last = false;
                    this.buttonDial.setVisible(false);
                    this.buttonReset.setVisible(false);
                    this.autoCloseChk.setVisible(false);
                    this.buttonDisconnect.setVisible(true);
                    this.buttonDisconnect.setEnabled(true);
                } else {
                    this.buttonDisconnect.setVisible(false);
                }
                this.addAddressButton.setVisible(this.addressList.isVisible());
                this.deleteAddressButton.setVisible(this.addressList.isVisible());
                this.editAddressButton.setVisible(this.addressList.isVisible());
                this.availableAddressesLabel.setVisible(this.addressList.isVisible());
                this.localGateAddressLabel.setVisible(this.addressList.isVisible());
                this.valuesSeparator.setVisible(!this.addressList.isVisible());
                this.userFeedbackLabel.setVisible(!this.buttonDial.isVisible() && !this.buttonReset.isVisible() && !this.buttonDisconnect.isVisible() && !(localGate.state == SGState.Disconnecting));
                this.gateStatusLabel.setVisible(!this.addressList.isVisible());

                if (this.addressList.getSize() > 0 && this.addressList.getSelectedItem() != null) {
                    this.autoCloseChk.setChecked(this.addressList.getSelectedItem().isAutoClose());
                    this.deleteAddressButton.setEnabled(!this.addressList.getSelectedItem().isLocked());
                }
            }
        }
    }

    private void dial() {
        timer = System.currentTimeMillis();
        if (this.addressList.getSize() > 0 && this.addressList.getSelectedItem() != null && !this.addressList.getSelectedItem().getAddress().isEmpty()) {
            if (this.addressList.getSelectedItem().getAddress().equalsIgnoreCase(this.localGateAddressLabel.getText())) {
                // Don't dial, user lacks common sense.
                return;
            }
        }
        if (this.addressList.getSize() > 0 && this.addressList.getSelectedItem() != null && !this.addressList.getSelectedItem().getAddress().isEmpty()) {
            this.resetGui(); //Reset before starting to account for half dialed sequences
            this.lastUpdate = 0;
            if (localGate.chevronsLockOnDial) {
                if (!isAdmin) {
                    dialSelectedAddressLockEachChevronIndividually(); // Progressive Dial Sequence
                } else {
                    // Todo: the below is a very stupid way to implement this...
                    dialSelectedAddress(); // Immediate dial address and lock all chevrons at one
                    // Note: this happens because gate.Connect() checks for "chevronsLockOnDial", if true, then immediately lock dialed address.
                }
            } else {
                dialSelectedAddress(); // Immediate Dial but display ring rotation.
                // This does NOT lock all chevrons at once because the TE value "chevronsLockOnDial" is false; and gate.Connect() knows.
                this.close();
            }
        }
    }

    private void resetGui() {
        // Note:  this is programmed specifically to wait for TE data to arrive to reset the clients gate.
        PddNetworkHandler.sendPddInputToServer(localGate, 4, "", "");
        localGate.immediateDialAddress = ""; //client side only variable for renderer.
        dialling = false;
        firstOpen = true;
        last = false;
        this.addressList.setVisible(true);
        this.buttonReset.setVisible(false);
        this.buttonDial.setVisible(true);
    }

    @Override
    protected void keyTyped(char keyChar, int keyCode) {
        super.keyTyped(keyChar, keyCode);
        this.lastUpdate = 0; // Reset the timer when key is typed.
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.lastUpdate = 0; // Reset the timer when mouse is pressed.
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // Can't stop the game otherwise the Sponge Scheduler also stops.
    }

// ******************************************************************************************************************************

    final static String symbolTextureFile = "symbols48.png";
    final static int symbolsPerRowInTexture = 10;
    final static int symbolWidthInTexture = 48;
    final static int symbolHeightInTexture = 48;
    final static int symbolTextureWidth = 512;
    final static int symbolTextureHeight = 256;
    final static int frameWidth = 236;
    final static int frameHeight = 44;
    final static int borderSize = 6;
    final static int cellSize = 24;

    private void detectChange() {
        final ItemStack stack = player.getHeldItemMainhand();
        NBTTagCompound compound = stack.getTagCompound();
        if (compound != null) {
            List<AddressData> comparedList = AddressData.getAddresses(compound);
            if (!comparedList.equals(clonedList)) {
                this.addressList.clearItems();
                this.clonedList = AddressData.getAddresses(compound);
                this.addressList.addItems(AddressData.getAddresses(compound));
            }
        }
    }

    private void dialRandom() {
        // Todo: figure out how to properly use this.
        final TileEntity localGateTE = GateUtil.locateLocalGate(this.world, this.location, SGCraft.toolSearchRange, false);
        if (localGateTE instanceof SGBaseTE) {
            localGate = (SGBaseTE) localGateTE;
        }
        if (localGate != null) {
            PddNetworkHandler.sendPddInputToServer(localGate, 3, localGate.homeAddress, ""); // Dials specified address based on Gates configuration, rotation vs. immediate dial.
        }
    }

    private void dialSelectedAddress() {
        TileEntity localGateTE = GateUtil.locateLocalGate(this.world, this.location, SGCraft.toolSearchRange, false);
        if (localGateTE instanceof SGBaseTE) {
            localGate = (SGBaseTE) localGateTE;
        }

       if (localGate != null) {
            if (this.addressList.getSelectedItem() != null && !this.addressList.getSelectedItem().getAddress().isEmpty()) {
                this.gateStatusLabel.setText(I18n.format("sgcraft.gui.pdd.label.dialing"));
                String destination = this.addressList.getSelectedItem().getAddress().toUpperCase().replaceAll("-", "");
                if (SGAddressing.inSameDimension(localGate.homeAddress,destination)) {
                    this.enteredAddress = destination.substring(0,7);
                } else {
                    this.enteredAddress = destination.substring(0,9);
                }
                PddNetworkHandler.sendPddInputToServer(localGate, 1, localGate.homeAddress ,destination); // Dials specified address based on Gates configuration, rotation vs. immediate dial.
            }
        }
    }

    private void dialSelectedAddressLockEachChevronIndividually() {
        final TileEntity localGateTE = GateUtil.locateLocalGate(this.world, this.location, SGCraft.toolSearchRange, false);
        if (localGateTE instanceof SGBaseTE) {
            localGate = (SGBaseTE) localGateTE;
        }

        if (localGate != null) {
            this.enteredAddress = "";
            this.gateStatusLabel.setFontOptions(FontOptions.builder().from(FontColors.BLUE_FO).shadow(true).scale(1.8F).build());
            this.gateStatusLabel.setText(I18n.format("sgcraft.gui.pdd.label.dialing"));
            this.dialling = true; // Allows checkDiallingStatus method to execute.
            this.digit = 0;
        }
    }

    private void checkDiallingStatus() { // Method is checked every 100 frames.
        if (localGate != null) {
            if (dialling && !localGate.errorState) {
                firstOpen = false;
                diallingAddress = this.addressList.getSelectedItem().getAddress().toUpperCase().replaceAll("-", "");

                if (SGAddressing.inSameDimension(localGate.homeAddress, diallingAddress)) {
                    diallingAddress = diallingAddress.substring(0,7);
                }

                if (diallingAddress.length() != 7 && diallingAddress.length() != 9) {
                    //SGBaseTE.sendGenericErrorMsg(player, "Invalid Address Specified.");
                    sendErrorMsg(player, "malformedAddress");
                    dialling = false;
                    return;
                }

                if (diallingAddress.length()  > localGate.getNumChevrons()) {
                    sendErrorMsg(player, "targetLackChevrons");
                    //SGBaseTE.sendGenericErrorMsg(player, "Gate does not have Chevron Upgrade.");
                    dialling = false;
                    return;
                }

                this.addressList.setVisible(false);
                TileEntity localGateTE = GateUtil.locateLocalGate(this.world, this.location, SGCraft.toolSearchRange, false);

                if (localGateTE instanceof SGBaseTE) {
                    SGBaseTE localGate = (SGBaseTE) localGateTE;
                    this.last = false;
                    localGate.immediateDialAddress = diallingAddress;
                    PddNetworkHandler.sendEnterSymbolToServer(localGate, diallingAddress, this.digit);
                    char currentSymbol = diallingAddress.charAt(digit);
                    enteredAddress += currentSymbol;
                    this.digit += 1;
                }
                if (this.digit >= diallingAddress.length()) {
                    this.dialling = false;
                    this.last = true;
                    this.digit = 0;
                }
            }
        }
    }

    public void stopDialing() {
        this.dialling = false;
    }

    private int getXStartLocation(String address) {
        if (address.length() == 7 || address.length() == 0) {
            return (this.addressTextureLabel.screenX() - 25) + (enteredAddress.length() * 12);
        } else {
            return  (this.addressTextureLabel.screenX() - 55) + (enteredAddress.length() * 12);
        }
    }

    private void drawAddressSymbols(int x, int y, String address) {
        int x0 = x - address.length() * cellSize / 2;
        int y0 = y + frameHeight / 2 - cellSize / 2;
        bindSGTexture(symbolTextureFile,
                symbolTextureWidth * cellSize / symbolWidthInTexture,
                symbolTextureHeight * cellSize / symbolHeightInTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        int n = address.length();
        for (int i = 0; i < n; i++) {
            int s = SGBaseTE.charToSymbol(address.charAt(i));
            int row = s / symbolsPerRowInTexture;
            int col = s % symbolsPerRowInTexture;

            drawTexturedRect(x0 + i * cellSize, y0, cellSize, cellSize,col * cellSize, row * cellSize);
        }
    }

    private void drawTexturedRect(double x, double y, double w, double h, double u, double v) {
        drawTexturedRect(x, y, w, h, u, v, w, h);
    }

    private void drawTexturedRect(double x, double y, double w, double h, double u, double v, double us, double vs) {
        drawTexturedRectUV(x, y, w, h, u * gstate.uscale, v * gstate.vscale, us * gstate.uscale, vs * gstate.vscale);
    }

    private void drawTexturedRectUV(double x, double y, double w, double h, double u, double v, double us, double vs) {
        glBegin(GL_QUADS);
        glColor3f(gstate.red, gstate.green, gstate.blue);
        glTexCoord2d(u, v+vs);
        glVertex3d(x, y+h, 1000); //zLevel
        glTexCoord2d(u+us, v+vs);
        glVertex3d(x+w, y+h, 1000); //zLevel
        glTexCoord2d(u+us, v);
        glVertex3d(x+w, y, 1000); //zLevel
        glTexCoord2d(u, v);
        glVertex3d(x, y, 1000);//zLevel
        glEnd();
    }

    private void bindSGTexture(String name, int usize, int vsize) {
        bindTexture(SGCraft.mod.resourceLocation("textures/gui/" + name), usize, vsize);
    }

    private void bindTexture(ResourceLocation rsrc, int usize, int vsize) {
        gstate.texture = rsrc;
        mc.getTextureManager().bindTexture(rsrc);
        gstate.uscale = 1.0 / usize;
        gstate.vscale = 1.0 / vsize;
    }

    private class AddressItemComponent extends BasicList.ItemComponent<AddressData> {

        private UILabel addressLabel, nameLabel;
        private BasicContainer lockedStatusContainer;

        public AddressItemComponent(final MalisisGui gui, final BasicList<AddressData> parent, final AddressData item) {
            super(gui, parent, item);
            this.setOnDoubleClickConsumer(i -> dial());
        }

        @Override
        protected void construct(final MalisisGui gui) {
            super.construct(gui);

            this.setHeight(28);
            this.setPadding(1);

            this.nameLabel = new UILabel(this.getGui(), TextFormatting.WHITE + this.item.getName());
            this.nameLabel.setPosition(4, 3);

            this.addressLabel = new UILabel(this.getGui(), TextFormatting.BLUE + this.item.getAddress());
            this.addressLabel.setPosition(4, BasicScreen.getPaddedY(this.nameLabel, 2));

            this.lockedStatusContainer = new BasicContainer(this.getGui(), 5, UIComponent.INHERITED);
            this.lockedStatusContainer.setPosition(0, 0, Anchor.MIDDLE | Anchor.RIGHT);

            this.update();

            this.add(this.nameLabel, this.addressLabel, this.lockedStatusContainer);
        }

        public void update() {
            this.lockedStatusContainer.setColor(this.item.isLocked() ? FontColors.GRAY : FontColors.GREEN);
        }
    }
}
