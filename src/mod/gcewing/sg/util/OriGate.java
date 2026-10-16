package gcewing.sg.util;

import gcewing.sg.tileentity.SGBaseTE;

// How an Ori supergate's segments link up as it dials: they light one after another from the top, anticlockwise (seen
// from the front) all the way round, each symbol dialled bringing in more, and the gate opens as the loop closes. Shared by its renderer and the crackle it makes as each link forms.
public final class OriGate {

    // How long (blocks, round the ring) a segment and the gap after it are, about, measured on the supergate's ring
    public static final double segmentPitch = 1.65;
    // How much bigger the Ori ring is drawn than the supergate's: its outer edge reaches this much further, its
    // inner edge staying against the event horizon
    public static final double outerScale = 1.15;

    public static double outerRadius(GateShape shape) {
        return shape.ringOuterRadius * outerScale;
    }

    private OriGate() {}

    public static int segmentCount(GateShape shape) {
        double mid = (shape.ringInnerRadius + shape.ringOuterRadius) / 2;
        return Math.max(22, (int) Math.round(2 * Math.PI * mid / segmentPitch));
    }

    // How far through dialling (0 to 1) the gate is: symbols locked, and how far through the next
    public static double dialProgress(SGBaseTE te, float partialTicks) {
        int length = te.dialledAddress == null ? 0 : te.dialledAddress.length();
        switch (te.state) {
            case Connected:
            case Transient:
            case SyncAwait:
            case EstablishingConnection:
            case Disconnecting:
                return 1;
            case Dialling:
            case InterDialling:
                if (length == 0) {
                    return 0;
                }
                return Math.min(1, (te.numEngagedChevrons + te.symbolDialProgress(partialTicks)) / length);
            default:
                // Idle, though it may be part-dialled a symbol at a time
                return length > 0 ? Math.min(1, (double) te.numEngagedChevrons / length) : 0;
        }
    }

    // How brightly (0 to 1) each of the n segments glows, segment 0 being at the top and the rest following it
    // anticlockwise. The last link holds half formed while the gate waits on the far one, and closes the loop as the
    // gate opens, its horizon then filling in.
    public static double[] segmentGlow(SGBaseTE te, int n, float partialTicks) {
        double reach;
        if (te.state == SGState.SyncAwait || te.state == SGState.EstablishingConnection) {
            reach = n - 0.5;
        } else if (te.state == SGState.Dialling || te.state == SGState.InterDialling || te.state == SGState.Idle) {
            reach = dialProgress(te, partialTicks) * (n - 0.5);
        } else {
            reach = dialProgress(te, partialTicks) * n;
        }
        double[] glow = new double[n];
        for (int k = 0; k < n; k++) {
            glow[k] = Math.max(0, Math.min(1, reach - k));
        }
        return glow;
    }

    // How many of the n segments are fully lit
    public static int linkedSegments(SGBaseTE te, int n) {
        int linked = 0;
        for (double glow : segmentGlow(te, n, 0)) {
            if (glow >= 1) {
                linked++;
            }
        }
        return linked;
    }
}
