//------------------------------------------------------------------------------------------------
//
//   SG Craft - The shape of a Milky Way (SG-1) gate
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The shape of a Milky Way gate, as on the show, as triangles. It is in metres: the ring's outer
// edge is OUTER_RADIUS from its centre, its faces either side of z = 0, the front towards +z. Its
// chevrons are at 90 degrees and every 40 degrees round from there.
//
// It is three things, each drawn on its own: the fixed ring, the symbol ring turning in a channel
// round the front of it, and a chevron. The fixed ring is a body turned from one profile, with
// everything standing out of it or cut into it given as polygons for a ninth of it, round the top
// chevron; the rest is that, turned. Where something is cut into the body, cells of it are left out
// (see milkyway_gate.txt) and the polygons there are the body's own faces. The symbol ring is the
// front of a cone, with its glyphs and the dividers between them standing out of it, all round. The
// chevron is polygons, each corner moving one of a few ways as the top chevron locks a symbol.
public class MilkyWayGateModel {

    // The parts of the gate, which are drawn in their own metals
    public static final int BODY = 0, DETAIL = 1, SYMBOL_RING = 2, GLYPHS = 3, CHEVRON = 4, LIGHT = 5, PARTS = 6;

    public static final double OUTER_RADIUS = 6.0960;
    public static final int CHEVRONS = 9;
    public static final double SECTION_ANGLE = 360.0 / CHEVRONS;
    // The ninth of the fixed ring its detail is given for, and the columns it is cut into: the
    // model's own faces are flat, one every COLUMN degrees from there
    static final double SECTION_FROM = 70, COLUMN = 4;
    static final int COLUMNS = 10;

    double[][] staticProfile, ringProfile;
    final List<double[][]> staticFaces = new ArrayList<>(), ringFaces = new ArrayList<>();
    final List<double[][]> chevronFaces = new ArrayList<>(), lightFaces = new ArrayList<>(),
            lowerLightFaces = new ArrayList<>();
    // Cells of the fixed body left out: column * 1000 + profile segment
    final Set<Integer> cut = new HashSet<>();
    // How far each way of moving takes a corner of the top chevron when it is locked, indexed by
    // the way (0 is not moving)
    double[][] moves = new double[1][3];

    public static MilkyWayGateModel load(InputStream in) throws IOException {
        MilkyWayGateModel model = new MilkyWayGateModel();
        List<double[]> moveList = new ArrayList<>();
        moveList.add(new double[3]);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\\s+");
                switch (f[0]) {
                    case "p":
                        double[][] p = new double[(f.length - 2) / 2][];
                        for (int i = 0; i < p.length; i++) {
                            p[i] = new double[] {Double.parseDouble(f[2 + 2 * i]), Double.parseDouble(f[3 + 2 * i])};
                        }
                        if (f[1].equals("static")) {
                            model.staticProfile = p;
                        } else {
                            model.ringProfile = p;
                        }
                        break;
                    case "c":
                        model.cut.add(Integer.parseInt(f[1]) * 1000 + Integer.parseInt(f[2]));
                        break;
                    case "m":
                        int n = Integer.parseInt(f[1]);
                        while (moveList.size() <= n) {
                            moveList.add(new double[3]);
                        }
                        moveList.set(n, new double[] {Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4])});
                        break;
                    case "s":
                        model.staticFaces.add(corners(f, 3));
                        break;
                    case "r":
                        model.ringFaces.add(corners(f, 3));
                        break;
                    case "v":
                        model.chevronFaces.add(corners(f, 4));
                        break;
                    case "l":
                        model.lightFaces.add(corners(f, 4));
                        break;
                    case "t":
                        model.lowerLightFaces.add(corners(f, 4));
                        break;
                }
            }
        }
        model.moves = moveList.toArray(new double[0][]);
        return model;
    }

    // The bore, the inside of the fixed ring: how far it is from the centre
    public double boreRadius() {
        double r = Double.MAX_VALUE;
        for (double[] p : staticProfile) {
            r = Math.min(r, p[0]);
        }
        return r;
    }

    // How far forward the bore's front edge is
    public double boreFront() {
        double r = boreRadius(), z = 0;
        for (double[] p : staticProfile) {
            if (p[0] == r) {
                z = Math.max(z, p[1]);
            }
        }
        return z;
    }

    // The corners of a polygon line, each of the given number of values
    static double[][] corners(String[] f, int each) {
        double[][] p = new double[(f.length - 1) / each][];
        for (int i = 0; i < p.length; i++) {
            p[i] = new double[each];
            for (int k = 0; k < each; k++) {
                p[i][k] = Double.parseDouble(f[1 + each * i + k]);
            }
        }
        return p;
    }

    // Receives the gate's triangles: corners anticlockwise seen from the side the normal points
    // to. A chevron's corners carry how they move as a fourth value.
    public interface Sink {
        void triangle(int part, double[] a, double[] b, double[] c, double[] normal);
    }

    // The fixed ring: its body turned in the given number of steps to each COLUMN degrees, and
    // its detail, a ninth at a time
    public void buildFixed(Sink sink, int stepsPerColumn) {
        double[][] p = staticProfile;
        for (int g = 0; g < CHEVRONS; g++) {
            for (int j = 0; j < COLUMNS; j++) {
                for (int i = 0; i + 1 < p.length; i++) {
                    if (cut.contains(j * 1000 + i)) {
                        continue;
                    }
                    double a0 = SECTION_FROM + g * SECTION_ANGLE + j * COLUMN;
                    // Going along the profile, the body is on the left, so outwards is to the right
                    revolve(sink, BODY, p[i], p[i + 1], a0, a0 + COLUMN, stepsPerColumn, 1);
                }
            }
            double turn = Math.toRadians(g * SECTION_ANGLE);
            for (double[][] face : staticFaces) {
                polygon(sink, DETAIL, turned(face, turn));
            }
        }
    }

    // The symbol ring, at rest: the point of origin at the top and the rest of its glyphs
    // clockwise from there, one every 360 / 39 degrees. Its cone turned in the given number of
    // steps all round, and its glyphs and dividers.
    public void buildRing(Sink sink, int steps) {
        double[][] p = ringProfile;
        for (int i = 0; i + 1 < p.length; i++) {
            // The front of a cone, facing forwards: outwards is to the left going outwards
            revolve(sink, SYMBOL_RING, p[i], p[i + 1], 0, 360, steps, -1);
        }
        for (double[][] face : ringFaces) {
            polygon(sink, GLYPHS, face);
        }
    }

    // The pieces of the chevron: its metal; its lights, lit whenever it is locked; and the lower
    // part of its light's triangle, lit only as the top chevron locks a symbol
    public static final int METAL = 0, LIGHTS = 1, LOWER_LIGHT = 2, PIECES = 3;

    // The top chevron, one piece of it, only the corners that move the given way
    public void buildChevron(Sink sink, int piece, int move) {
        List<double[][]> faces = piece == METAL ? chevronFaces : piece == LIGHTS ? lightFaces : lowerLightFaces;
        for (double[][] face : faces) {
            if ((int) face[0][3] == move) {
                polygon(sink, piece == METAL ? CHEVRON : LIGHT, face);
            }
        }
    }

    public int moveCount() {
        return moves.length;
    }

    // How far the corners moving the given way go when the top chevron is locked
    public double[] move(int way) {
        return moves[way];
    }

    static double[][] turned(double[][] face, double turn) {
        double c = Math.cos(turn), s = Math.sin(turn);
        double[][] q = new double[face.length][];
        for (int i = 0; i < face.length; i++) {
            double[] f = face[i];
            q[i] = new double[] {f[0] * c - f[1] * s, f[0] * s + f[1] * c, f[2]};
        }
        return q;
    }

    // The surface swept by the profile segment from p to q, turned from degrees a0 to a1, facing
    // to the right of the way the segment goes in (r, z) if side is 1, else to the left
    static void revolve(Sink sink, int part, double[] p, double[] q, double a0, double a1, int steps, int side) {
        double dr = q[0] - p[0], dz = q[1] - p[1], len = Math.hypot(dr, dz);
        if (len < 1e-9) {
            return;
        }
        double nr = dz / len * side, nz = -dr / len * side;
        for (int k = 0; k < steps; k++) {
            double b0 = Math.toRadians(a0 + (a1 - a0) * k / steps), b1 = Math.toRadians(a0 + (a1 - a0) * (k + 1) / steps);
            double c0 = Math.cos(b0), s0 = Math.sin(b0), c1 = Math.cos(b1), s1 = Math.sin(b1), bm = (b0 + b1) / 2;
            double[] n = {nr * Math.cos(bm), nr * Math.sin(bm), nz};
            double[] w = {p[0] * c0, p[0] * s0, p[1]}, x = {q[0] * c0, q[0] * s0, q[1]};
            double[] y = {q[0] * c1, q[0] * s1, q[1]}, z = {p[0] * c1, p[0] * s1, p[1]};
            emitFacing(sink, part, w, x, y, n);
            emitFacing(sink, part, w, y, z, n);
        }
    }

    // A polygon's triangles, as its corners go round: it faces the way they go anticlockwise
    static void polygon(Sink sink, int part, double[][] p) {
        double[] n = newell(p);
        if (n == null) {
            return;
        }
        List<int[]> tris = triangulate(p, n);
        for (int[] t : tris) {
            sink.triangle(part, p[t[0]], p[t[1]], p[t[2]], n);
        }
    }

    // The unit normal of a polygon, the way its corners go round
    static double[] newell(double[][] p) {
        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < p.length; i++) {
            double[] a = p[i], b = p[(i + 1) % p.length];
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
        }
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-14 ? null : new double[] {nx / l, ny / l, nz / l};
    }

    // Splits a simple polygon into triangles of its corners, by cutting off ears, flat in the
    // plane it most faces, with its corners in their own order (as tools/milkyway/extract.js
    // checked it can be)
    static List<int[]> triangulate(double[][] p, double[] n) {
        double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        boolean alongZ = az >= ax && az >= ay, alongX = !alongZ && ax >= ay;
        int u = alongZ ? 0 : alongX ? 1 : 0, w = alongZ ? 1 : 2;
        boolean flip = (alongZ ? n[2] : alongX ? n[0] : -n[1]) < 0;
        double[][] q = new double[p.length][];
        for (int i = 0; i < p.length; i++) {
            q[i] = new double[] {p[i][u], flip ? -p[i][w] : p[i][w]};
        }
        List<int[]> tris = new ArrayList<>();
        List<Integer> v = new ArrayList<>();
        for (int i = 0; i < q.length; i++) {
            v.add(i);
        }
        int guard = 0;
        while (v.size() > 3 && guard++ < 10 * q.length * q.length) {
            boolean cutEar = false;
            for (int i = 0; i < v.size(); i++) {
                int i0 = v.get((i + v.size() - 1) % v.size()), i1 = v.get(i), i2 = v.get((i + 1) % v.size());
                double cross = (q[i1][0] - q[i0][0]) * (q[i2][1] - q[i0][1]) - (q[i1][1] - q[i0][1]) * (q[i2][0] - q[i0][0]);
                if (cross <= 1e-14) {
                    continue;
                }
                boolean empty = true;
                for (int j : v) {
                    if (j != i0 && j != i1 && j != i2 && inTriangle(q[j], q[i0], q[i1], q[i2])) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    tris.add(new int[] {i0, i1, i2});
                    v.remove(i);
                    cutEar = true;
                    break;
                }
            }
            if (!cutEar) {
                // Nothing left that is a clean ear: what is left is all but flat
                v.remove(0);
            }
        }
        if (v.size() == 3) {
            tris.add(new int[] {v.get(0), v.get(1), v.get(2)});
        }
        return tris;
    }

    static boolean inTriangle(double[] q, double[] a, double[] b, double[] c) {
        double d1 = side(q, a, b), d2 = side(q, b, c), d3 = side(q, c, a);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    static double side(double[] q, double[] a, double[] b) {
        return (q[0] - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (q[1] - b[1]);
    }

    static void emitFacing(Sink sink, int part, double[] a, double[] b, double[] c, double[] n) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        if (cx * n[0] + cy * n[1] + cz * n[2] >= 0) {
            sink.triangle(part, a, b, c, n);
        } else {
            sink.triangle(part, a, c, b, n);
        }
    }
}
