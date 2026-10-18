//------------------------------------------------------------------------------------------------
//
//   SG Craft - A Pegasus gate's glyph screens, and which of them are lit
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateType;
import gcewing.sg.util.SGState;

import java.util.Arrays;

import static gcewing.sg.client.renderer.GateRenderer.chevronAngle;
import static gcewing.sg.client.renderer.GateRenderer.chevronPosition;
import static gcewing.sg.client.renderer.GateRenderer.chevronSequenceRow;

// A Pegasus gate's glyph screens: one centred under each chevron, and the rest spread evenly
// between them about a glyph apart, so they line up with the chevrons however far apart those
// are. Angles (degrees) rise anticlockwise round the ring. Shared by PegasusGateRenderer and, for a
// Pegasus gate without its model, ClassicGateRenderer.
class PegasusScreens {

    // How many glyphs a Pegasus gate has, one to each screen round its ring
    final static int GLYPHS = 36;

    double[] angles, widths;

    int count() {
        return angles.length;
    }

    // The screen nearest the given angle
    int at(double angle) {
        int best = 0;
        double bestDistance = 360;
        for (int k = 0; k < angles.length; k++) {
            double d = Math.abs(((angle - angles[k]) % 360 + 540) % 360 - 180);
            if (d < bestDistance) {
                best = k;
                bestDistance = d;
            }
        }
        return best;
    }

    static PegasusScreens of(SGBaseTE te) {
        int n = te.getNumChevrons();
        int i0 = n > 7 ? 0 : 1;
        float a = te.angleBetweenChevrons();
        double[] chevrons = new double[n];
        for (int k = 0; k < n; k++) {
            chevrons[k] = (chevronAngle(te, i0 + k, a) % 360 + 360) % 360;
        }
        Arrays.sort(chevrons);
        int[] between = new int[n];
        int count = 0;
        for (int k = 0; k < n; k++) {
            double gap = k + 1 < n ? chevrons[k + 1] - chevrons[k] : chevrons[0] + 360 - chevrons[k];
            between[k] = Math.max(1, (int) Math.round(gap / SGBaseTE.ringSymbolAngle));
            count += between[k];
        }
        PegasusScreens screens = new PegasusScreens();
        screens.angles = new double[count];
        screens.widths = new double[count];
        int s = 0;
        for (int k = 0; k < n; k++) {
            double gap = k + 1 < n ? chevrons[k + 1] - chevrons[k] : chevrons[0] + 360 - chevrons[k];
            for (int t = 0; t < between[k]; t++, s++) {
                screens.angles[s] = chevrons[k] + gap * t / between[k];
                screens.widths[s] = gap / between[k];
            }
        }
        return screens;
    }

    // How far round (degrees, clockwise from the top) a Pegasus gate being dialled has lit its
    // ring, or -1 if it isn't one
    static double fill(SGBaseTE te, float partialTicks) {
        if (te.getGateType() != GateType.PEGASUS || te.isInitiator
                || te.state == SGState.Idle || te.state == SGState.Disconnecting) {
            return -1;
        }
        int length = te.dialledAddress.length();
        if (length > 0 && te.numEngagedChevrons >= length) {
            return 360;
        }
        if (te.pegasusFillEnd <= 0) {
            return 0;
        }
        double now = te.getWorld().getTotalWorldTime() + partialTicks;
        double p = (now - te.pegasusFillStart) / Math.max(1, te.pegasusFillEnd - te.pegasusFillStart);
        return 360 * Math.max(0, Math.min(1, p));
    }

    // Whether a ring lit that far round (see fill) has reached the given angle
    static boolean fillReaches(SGBaseTE te, double fill, double angle) {
        double top = chevronAngle(te, 4, te.angleBetweenChevrons());
        double d = ((top - angle) % 360 + 360) % 360;
        return (d < 1e-6 ? 360 : d) <= fill + 1e-6;
    }

    // Whether the chevron at position i, which locks j-th, is lit: a Pegasus gate being dialled
    // lights its chevrons as the light filling its ring reaches them
    static boolean chevronEngaged(SGBaseTE te, int i, int j, float a, float partialTicks) {
        double fill = fill(te, partialTicks);
        return fill >= 0
            ? j < te.dialledAddress.length() && fillReaches(te, fill, chevronAngle(te, i, a))
            : te.chevronIsEngaged(j);
    }

    // Which glyph is on the screen at the given angle: the glyphs go clockwise round the ring from
    // the one at the top, each a screen apart, in the order of the gate's symbol strip
    static int glyphAt(double angle) {
        int k = (int) Math.round((90 - angle) / SGBaseTE.ringSymbolAngle);
        return (k % GLYPHS + GLYPHS) % GLYPHS;
    }

    // Which of a Pegasus gate's screens are lit, or null if none are, as the show's gates do
    // (following the Carter Addon Pack's Atlantis gate). Only the glyphs light; the screens stay
    // dark behind them.
    //  - Dialling out, a single light steps round the ring a screen at a time to each chevron in
    //    turn (see SGBaseTE.pegasusLightTravel), and the glyph under each locked chevron stays lit.
    //    Dialled a symbol at a time with its chevrons locking as each is entered, the glyph under
    //    each lights as it locks (see GateRenderer.lockingOnDial).
    //  - Dialled into, the ring stays dark until the last few seconds of the dial, then fills
    //    with light clockwise from the top, lighting each chevron as it reaches it, so that the
    //    whole ring is lit as the last chevron locks (see fill).
    boolean[] lit(SGBaseTE te, float partialTicks) {
        int count = count();
        boolean[] lit = new boolean[count];
        boolean any = false;
        int row = chevronSequenceRow(te);
        float a = te.angleBetweenChevrons();
        int length = te.dialledAddress.length();
        int locked = Math.min(te.numEngagedChevrons, length);
        if (te.isInitiator || GateRenderer.lockingOnDial(te)) {
            for (int e = 0; e < locked; e++) {
                int i = chevronPosition(te, row, e);
                if (i >= 0) {
                    lit[at(chevronAngle(te, i, a))] = any = true;
                }
            }
            if (te.state == SGState.Dialling && locked < length) {
                int i = chevronPosition(te, row, locked);
                int previous = locked == 0 ? 4 : chevronPosition(te, row, locked - 1);
                double travel = te.targetRingAngle - te.startRingAngle;
                if (i >= 0 && previous >= 0 && travel != 0) {
                    double p = (te.interpolatedRingAngle(partialTicks) - te.startRingAngle) / travel;
                    p = Math.max(0, Math.min(1, p));
                    // The screens the light passes on its way, going as SGBaseTE.pegasusLightTravel
                    // does: the long way round, with an extra turn rather than a short hop
                    int to = at(chevronAngle(te, i, a));
                    int from = at(chevronAngle(te, previous, a));
                    int direction = travel > 0 ? 1 : -1;
                    int steps = ((direction * (to - from)) % count + count) % count;
                    if (steps == 0 || steps * 360.0 / count <= 240) {
                        steps += count;
                    }
                    int left = (int) Math.floor((1 - p) * steps + 1e-6);
                    lit[((to - direction * left) % count + count) % count] = any = true;
                }
            }
        } else {
            double fill = fill(te, partialTicks);
            if (fill > 0) {
                for (int k = 0; k < count; k++) {
                    if (fillReaches(te, fill, angles[k])) {
                        lit[k] = any = true;
                    }
                }
            }
        }
        return any ? lit : null;
    }
}
