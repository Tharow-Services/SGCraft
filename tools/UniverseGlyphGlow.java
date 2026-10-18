// Draws the glow round the Universe gate's lit symbols: its symbol sheet (tileentity/universe/
// glyphs.png, from tools/universe/glyphsheet.js) blurred, each cell on its own, so that drawn over a
// lit symbol it spreads light round it (see UniverseGateRenderer.renderUniverseSymbols). Run from the
// project root with a JDK:
//
//   javac -d build/tools tools/UniverseGlyphGlow.java && java -cp build/tools UniverseGlyphGlow
//
// It writes tileentity/universe/glyphs_glow.png beside the sheet.

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class UniverseGlyphGlow {

    static final String DIR = "src/resources/assets/sgcraft/textures/tileentity/universe/";
    static final int CELLS = 6;
    // How far the glow spreads, as a fraction of a cell's width, and how much brighter than the
    // blurred symbol it is drawn, so that it is strong near the symbol
    static final double SPREAD = 0.06, GAIN = 2.2;

    public static void main(String[] args) throws Exception {
        BufferedImage sheet = ImageIO.read(new File(DIR + "glyphs.png"));
        int w = sheet.getWidth(), h = sheet.getHeight(), cw = w / CELLS, ch = h / CELLS;
        double sigma = SPREAD * cw;
        int reach = (int) Math.ceil(3 * sigma);
        double[] kernel = new double[2 * reach + 1];
        double sum = 0;
        for (int i = -reach; i <= reach; i++) {
            kernel[i + reach] = Math.exp(-i * i / (2 * sigma * sigma));
            sum += kernel[i + reach];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        double[] v = new double[w * h], across = new double[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                v[y * w + x] = (sheet.getRGB(x, y) >> 8 & 255) / 255.0;
            }
        }
        // Blurred across then down, never reaching past the edge of the symbol's own cell
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int x0 = x / cw * cw;
                double s = 0;
                for (int k = -reach; k <= reach; k++) {
                    int xx = x + k;
                    if (xx >= x0 && xx < x0 + cw) {
                        s += v[y * w + xx] * kernel[k + reach];
                    }
                }
                across[y * w + x] = s;
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            int y0 = y / ch * ch;
            for (int x = 0; x < w; x++) {
                double s = 0;
                for (int k = -reach; k <= reach; k++) {
                    int yy = y + k;
                    if (yy >= y0 && yy < y0 + ch) {
                        s += across[yy * w + x] * kernel[k + reach];
                    }
                }
                int g = (int) Math.round(Math.min(1, s * GAIN) * 255);
                out.setRGB(x, y, g << 16 | g << 8 | g);
            }
        }
        ImageIO.write(out, "png", new File(DIR + "glyphs_glow.png"));
    }
}
