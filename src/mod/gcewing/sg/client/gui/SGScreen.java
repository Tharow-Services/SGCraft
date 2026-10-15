//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate gui base class
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.gui;

import static org.lwjgl.opengl.GL11.*;

import gcewing.sg.BaseContainer;
import gcewing.sg.BaseGui;
import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.SGCraft;
import net.minecraft.client.*;
import net.minecraft.inventory.*;

//------------------------------------------------------------------------------------------------

public class SGScreen extends BaseGui.Screen {

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

    double uscale, vscale;
    float red = 1F, green = 1F, blue = 1F;

    public SGScreen() {
        super(new BaseContainer(Minecraft.getMinecraft().displayWidth,Minecraft.getMinecraft().displayHeight));
    }

    public SGScreen(Container container, int width, int height) {
        super(container, width, height);
    }
    
    public void drawAddressSymbols(int x, int y, String address) {
        int x0 = x - address.length() * cellSize / 2;
        int y0 = y + frameHeight / 2 - cellSize / 2;
//      bindSGTexture("symbol_frame.png", 256, 64);
//      drawTexturedRect(x - frameWidth / 2, y, frameWidth, frameHeight, 0, 0);
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
            // Sample a texel inside the cell: with smooth filtering, its edge would pick up the neighbouring symbol
            double inset = cellSize / (double) symbolWidthInTexture;
            drawTexturedRect(x0 + i * cellSize, y0, cellSize, cellSize,
                col * cellSize + inset, row * cellSize + inset, cellSize - 2 * inset, cellSize - 2 * inset);
        }
    }
    
    public void drawAddressString(int x, int y, String address) {
        drawCenteredString(this.fontRenderer, address, x, y, 0xffffff);
    }
    
//  void drawAddressString(int x, int y, String address, String caret) {
//      drawCenteredString(this.fontRendererObj, padAddress(address, caret), x, y, 0xffffff);
//  }
    
//  String padAddress(String address, String caret) {
//      return SGAddressing.padAddress(address, caret, );
//  }
    
    void bindSGTexture(String name) {
        bindSGTexture(name, 1, 1);
    }
    
    void bindSGTexture(String name, int usize, int vsize) {
        bindTexture(SGCraft.mod.resourceLocation("textures/gui/" + name), usize, vsize);
    }
    
}
