//------------------------------------------------------------------------------------------------
//
//   SG Craft - Size and layout of a stargate multiblock
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.util;

import gcewing.sg.Vector3;

// Everything that depends on how big a gate is should read it from here, so that a gate of a
// different size only needs a different GateShape.
//
// All positions are in the gate's local frame (see SGBaseTE.localToGlobalTransformation): the
// base block is at the origin at the bottom centre of the ring, x runs left to right, y runs up,
// and the event horizon lies in the z = 0 plane.

public final class GateShape {

    // Values in the pattern
    public static final int EMPTY = 0;
    public static final int RING = 1;
    public static final int CHEVRON = 2;

    // How far in front of and behind the event horizon entities are tracked each tick
    public static final double PORTAL_TRACKING_DEPTH = 1.5;

    // The ring textures are laid out for this many segments; a ring may use any multiple of it
    public static final int BASE_RING_SEGMENTS = 32;
    // The event horizon ripples are tuned for this many slices around the grid
    public static final int BASE_EH_POLAR_SIZE = 32;

    // Angles (degrees, anticlockwise from the right) at which the renderer draws the nine chevrons
    private static final double[] CHEVRON_ANGLES = {250, 210, 170, 130, 90, 50, 10, -30, -70};

    public static final GateShape STANDARD = new GateShape(
        new int[][] {
            {2, 1, 2, 1, 2},
            {1, 0, 0, 0, 1},
            {2, 0, 0, 0, 2},
            {1, 0, 0, 0, 1},
            {2, 1, 0, 1, 2},
        },
        2.0, 2.25, 2.5, 0.25, 32,
        5, 32,
        new Vector3(-1.5, 0.5, 0.5), new Vector3(1.5, 3.5, 5.5),
        new Vector3(-1.0, 0.5, 0.0), new Vector3(1.0, 2.5, 2.5),
        1.0, null);

    // Supergates come in sizes from 2 * MIN_SUPERGATE_RADIUS + 1 to 2 * MAX_SUPERGATE_RADIUS + 1
    // blocks across. Beyond the maximum, a flat gate needs more chunks loaded than one Forge ticket
    // allows (25), and the ring textures get blurrier than vanilla blocks at about 75 across.
    public static final int MIN_SUPERGATE_RADIUS = 4;
    public static final int MAX_SUPERGATE_RADIUS = 30;
    public static final int DEFAULT_SUPERGATE_RADIUS = 7;

    // Supergate shapes are made as needed and shared, so shapes can be compared with ==
    private static final GateShape[] supergates = new GateShape[MAX_SUPERGATE_RADIUS + 1];

    // The supergate whose ring is the given number of blocks from its centre to its base block
    public static synchronized GateShape supergate(int radius) {
        if (radius < MIN_SUPERGATE_RADIUS || radius > MAX_SUPERGATE_RADIUS) {
            throw new IllegalArgumentException("Supergate radius must be from "
                + MIN_SUPERGATE_RADIUS + " to " + MAX_SUPERGATE_RADIUS + ": " + radius);
        }
        if (supergates[radius] == null) {
            supergates[radius] = makeSupergate(radius);
        }
        return supergates[radius];
    }

    // A ring of blocks that follows the circle rather than a square. The rendered ring is a
    // standard one scaled up to fit, so its textures keep their proportions, and the event
    // horizon, kawoosh and details are scaled up to match, but it is only as thick as its blocks
    // (see SUPERGATE_RING_DEPTH). The ring and the event horizon get enough segments to keep each
    // one under about three quarters of a block long.
    private static GateShape makeSupergate(int r) {
        double k = (r + 0.5) / STANDARD.ringOuterRadius;
        int segments = BASE_RING_SEGMENTS * (int) Math.ceil(2 * Math.PI * (r + 0.5) / BASE_RING_SEGMENTS / 0.75);
        return new GateShape(
            circularPattern(r, CHEVRON_ANGLES),
            STANDARD.ringInnerRadius * k, STANDARD.ringMidRadius * k, STANDARD.ringOuterRadius * k,
            SUPERGATE_RING_DEPTH, segments,
            STANDARD.ehGridRadialSize, segments,
            new Vector3(-(r - 0.5), 0.5, 0.5), new Vector3(r - 0.5, 2 * r - 0.5, 2 * r - 0.5),
            new Vector3(-(r - 1), 0.5, 0.0), new Vector3(r - 1, 2 * r - 1.5, 2.5 * k - 1),
            k, "supergate");
    }

    // How thick (blocks) a supergate's rendered ring is: as thick as its blocks, whatever its size
    public static final double SUPERGATE_RING_DEPTH = 1.0;

    // How much thicker the rendered ring is than a standard gate's. Details standing out of its
    // faces, such as chevrons and event horizon ripples, scale with this rather than detailScale.
    public double depthScale() {
        return ringDepth / STANDARD.ringDepth;
    }

    public boolean isSupergate() {
        return textureName != null;
    }

    // The opening as it looks, which arriving entities are put in. A supergate's rendered ring is
    // thicker than its blocks, so its opening is the circle inside the rendered ring; a standard
    // gate's is the gap between its blocks. Heights are above the base block's centre.

    // The floor of the opening
    public double openingFloor() {
        return isSupergate() ? Math.max(apertureBottom, centreY - ringInnerRadius) : apertureBottom;
    }

    // The top of the opening
    public double openingTop() {
        return isSupergate() ? centreY + ringInnerRadius : apertureTop;
    }

    // Half the height of the opening
    public double openingRadius() {
        return (openingTop() - openingFloor()) / 2;
    }

    // Whether a point the given distance across the gate's face from its middle, and height above
    // the base block's centre, is in the opening rather than in front of the ring. A supergate's
    // rendered ring covers much of the square between its blocks, which entities are tracked in.
    public boolean inOpening(double x, double y) {
        if (!isSupergate()) {
            return true;
        }
        double dy = y - centreY;
        return x * x + dy * dy <= ringInnerRadius * ringInnerRadius;
    }

    // Half the width of the opening at the given height
    public double openingHalfWidthAt(double y) {
        if (!isSupergate()) {
            return apertureHalfWidth;
        }
        double dy = y - centreY;
        return Math.sqrt(Math.max(0, ringInnerRadius * ringInnerRadius - dy * dy));
    }

    // Rows from top to bottom; the base block is the centre of the bottom row
    private final int[][] pattern;

    public final int width;
    public final int height;
    public final int halfWidth;

    // Height of the centre of the ring above the base block
    public final double centreY;

    public final double ringInnerRadius;
    public final double ringMidRadius;
    public final double ringOuterRadius;
    public final double ringDepth;
    // The rendered rings are polygons with this many sides. More look smoother on a big gate.
    public final int ringSegments;
    public final double[] ringSin;
    public final double[] ringCos;

    // The open area inside the ring: half its width, and the heights of its floor (the top of
    // the base block) and ceiling. Entities in it are tracked for teleporting.
    public final double apertureHalfWidth;
    public final double apertureBottom;
    public final double apertureTop;
    // An entity only teleports if it ends up no further than this behind the event horizon
    public final double maxCrossingDepth;

    // Event horizon ripple simulation grid: rings from the centre out, and slices around. More
    // slices give a smoother edge without changing how the ripples move; more rings also give
    // finer ripples, but the opening transient takes longer to spread to the rim.
    public final int ehGridRadialSize;
    public final int ehGridPolarSize;
    // Scales the around-the-ring term of the ripple simulation so that ripples spread around the
    // horizon at the same speed whatever ehGridPolarSize is
    public final double ehAngularCoupling;
    // Steps the ripple simulation takes per tick. A fine grid pulls harder on each cell, and one
    // step per tick would overshoot and blow up, so it takes smaller steps instead.
    public final int ehSubsteps;

    // Ripple wave speed squared, in grid cells per tick
    public static final double EH_WAVE_SPEED_SQ = 0.03;
    public final double ehBandWidth;
    public final double ehClipRadius;
    public final double[] ehSin;
    public final double[] ehCos;

    // Region damaged by the opening vortex (kawoosh), and the region of blocks it destroys
    public final Vector3 kawooshDamageMin, kawooshDamageMax;
    public final Vector3 kawooshBlockMin, kawooshBlockMax;

    // Size of rendered details (chevrons, event horizon ripples, iris) relative to a standard gate
    public final double detailScale;
    // The name of a supergate shape, or null for the standard stargate. The rendered gate's
    // textures are its gate type's own, whatever its size.
    public final String textureName;

    public GateShape(int[][] pattern,
        double ringInnerRadius, double ringMidRadius, double ringOuterRadius, double ringDepth,
        int ringSegments,
        int ehGridRadialSize, int ehGridPolarSize,
        Vector3 kawooshDamageMin, Vector3 kawooshDamageMax,
        Vector3 kawooshBlockMin, Vector3 kawooshBlockMax,
        double detailScale, String textureName)
    {
        this.pattern = pattern;
        this.height = pattern.length;
        this.width = pattern[0].length;
        this.halfWidth = width / 2;
        this.centreY = (height - 1) / 2.0;
        this.ringInnerRadius = ringInnerRadius;
        this.ringMidRadius = ringMidRadius;
        this.ringOuterRadius = ringOuterRadius;
        this.ringDepth = ringDepth;
        if (ringSegments <= 0 || ringSegments % BASE_RING_SEGMENTS != 0) {
            throw new IllegalArgumentException("ringSegments must be a multiple of " + BASE_RING_SEGMENTS);
        }
        this.ringSegments = ringSegments;
        this.ringSin = new double[ringSegments + 1];
        this.ringCos = new double[ringSegments + 1];
        for (int i = 0; i <= ringSegments; i++) {
            double a = 2 * Math.PI * i / ringSegments;
            ringSin[i] = Math.sin(a);
            ringCos[i] = Math.cos(a);
        }
        this.apertureHalfWidth = halfWidth - 0.5;
        this.apertureBottom = 0.5;
        this.apertureTop = height - 1.5;
        this.maxCrossingDepth = height;
        this.ehGridRadialSize = ehGridRadialSize;
        this.ehGridPolarSize = ehGridPolarSize;
        double polarRatio = (double) ehGridPolarSize / BASE_EH_POLAR_SIZE;
        this.ehAngularCoupling = polarRatio * polarRatio;
        // The simulation is stable while dt^2 * c^2 * (4 * coupling + 4) stays under 4 at the
        // innermost ring; keep it under half that. A standard gate needs just one step.
        double maxStep = Math.sqrt(1 / (2 * EH_WAVE_SPEED_SQ * (ehAngularCoupling + 1)));
        this.ehSubsteps = Math.max(1, (int) Math.ceil(1 / maxStep));
        this.ehBandWidth = ringInnerRadius / ehGridRadialSize;
        this.ehClipRadius = ringOuterRadius;
        this.ehSin = new double[ehGridPolarSize + 1];
        this.ehCos = new double[ehGridPolarSize + 1];
        for (int i = 0; i <= ehGridPolarSize; i++) {
            double a = 2 * Math.PI * i / ehGridPolarSize;
            ehSin[i] = Math.sin(a);
            ehCos[i] = Math.cos(a);
        }
        this.kawooshDamageMin = kawooshDamageMin;
        this.kawooshDamageMax = kawooshDamageMax;
        this.kawooshBlockMin = kawooshBlockMin;
        this.kawooshBlockMax = kawooshBlockMax;
        this.detailScale = detailScale;
        this.textureName = textureName;
    }

    // A ring of blocks of the given radius (in blocks from its centre to the base block). Every
    // block the circle passes through is part of the ring, and the ring blocks nearest to each
    // chevron angle are chevrons.
    static int[][] circularPattern(int radius, double[] chevronAngles) {
        int size = 2 * radius + 1;
        int[][] pattern = new int[size][size];
        for (int y = 0; y < size; y++)
            for (int x = -radius; x <= radius; x++)
                if (circleCrossesCell(x, y - radius, radius))
                    pattern[size - 1 - y][radius + x] = RING;
        for (double angle : chevronAngles) {
            // Place left-hand chevrons as mirror images of right-hand ones, so that ties between
            // equally near blocks are broken the same way on both sides
            double a = Math.toRadians(angle);
            boolean mirror = Math.cos(a) < -1e-9;
            double px = round6(Math.abs(radius * Math.cos(a))), py = round6(radius * Math.sin(a));
            int bestX = 0, bestY = 0;
            double bestDist = Double.MAX_VALUE;
            for (int y = 0; y < size; y++)
                for (int x = -radius; x <= radius; x++)
                    if (pattern[size - 1 - y][radius + x] == RING) {
                        double d = Math.hypot(x - px, y - radius - py);
                        if (d < bestDist) {
                            bestDist = d;
                            bestX = x;
                            bestY = y;
                        }
                    }
            pattern[size - 1 - bestY][radius + (mirror ? -bestX : bestX)] = CHEVRON;
        }
        // The base block's own cell
        pattern[size - 1][radius] = EMPTY;
        return pattern;
    }

    private static double round6(double x) {
        return Math.round(x * 1e6) / 1e6;
    }

    // Whether a circle about the origin passes through the unit cell centred on (cx, cy)
    private static boolean circleCrossesCell(int cx, int cy, double r) {
        double nx = Math.max(Math.abs(cx) - 0.5, 0), ny = Math.max(Math.abs(cy) - 0.5, 0);
        double fx = Math.abs(cx) + 0.5, fy = Math.abs(cy) + 0.5;
        return Math.hypot(nx, ny) < r && Math.hypot(fx, fy) > r;
    }

    // What should be at local position (x, y) of a vertical gate, where x is in
    // -halfWidth..halfWidth and y is in 0..height-1. The base block itself is at (0, 0).
    public int cellAt(int x, int y) {
        return pattern[height - 1 - y][halfWidth + x];
    }

    // Whether a block centred at local (x, y) lies in line with the opening, and so in the path of
    // the kawoosh. Used with kawooshBlockMin/Max, which only bound the region.
    public boolean inKawooshBore(double x, double y) {
        // The opening is bounded by whichever is further out: the rendered ring or the blocks
        return Math.hypot(x, y - centreY) <= Math.max(ringInnerRadius, apertureHalfWidth) - 0.25;
    }

    // How far to search sideways from a block for blocks belonging to the same gate, in either
    // orientation. A horizontal gate extends height - 1 blocks out from its base.
    public int searchRadius() {
        return Math.max(halfWidth, height - 1) + 1;
    }

    // How far to search up from the base (or down from a ring block) for blocks of the same gate
    public int searchHeight() {
        return height - 1;
    }

}
