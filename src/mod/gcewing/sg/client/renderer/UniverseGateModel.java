//------------------------------------------------------------------------------------------------
//
//   SG Craft - The shape of a Universe (Destiny) gate
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The shape of a Universe gate, as on the show, as triangles. It is in its own units: the ring's
// outer edge is OUTER_RADIUS from its centre and its faces are either side of z = 0. Its chevrons
// are at 90 degrees and every 40 degrees round from there, clockwise.
//
// The ring itself is a body turned from one profile (PROFILE). Everything standing out of it - the
// frames round the symbols' arches, the posts between them, the lattice along the rim, the pipes
// round the inside and the chevrons - is a set of faces, each an outline with a height at each
// corner (see universe_gate.txt), with sides dropping from its edges to the body. Those are given
// for a ninth of a turn on the front, a whole chevron with half the way to the next one either
// side of it, and the rest of the gate is that, turned, front and back.
public class UniverseGateModel {

    // The parts of the gate, which are drawn in their own metals
    public static final int BODY = 0, CHEVRON = 1, RELIEF = 2, DETAIL = 3, PARTS = 4;

    public static final double OUTER_RADIUS = 3.4111;
    public static final double INNER_RADIUS = 2.5188;
    // The angle from a chevron to the next, which the faces are given for
    public static final double SECTION_ANGLE = 40;
    public static final int CHEVRONS = 9;

    // The body's profile on the front, (radius, z), from the middle of the slot round the inside of
    // the ring where the event horizon sits, up its inner edge, out across its face to the outer
    // edge and down that to the middle of the groove round it. The back is its mirror.
    static final double[][] PROFILE = {
        {2.5810, 0.0}, {2.5810, 0.0114}, {2.5188, 0.0149}, {2.5188, 0.0771}, {2.5209, 0.0828},
        {2.5261, 0.0859}, {2.6479, 0.1080}, {2.6699, 0.1403}, {2.7414, 0.1482}, {3.1400, 0.1864},
        {3.2618, 0.1969}, {3.2618, 0.2139}, {3.2671, 0.2201}, {3.3290, 0.2314}, {3.3436, 0.2300},
        {3.3542, 0.2211}, {3.3581, 0.2070}, {3.3581, 0.1534}, {3.3870, 0.1176}, {3.3870, 0.0566},
        {3.4111, 0.0570}, {3.3854, 0.0035}, {3.3735, 0.0035}, {3.3735, 0.0}
    };
    // The part of the profile that faces forwards, from the inner edge to the outer
    static final int FACE_FROM = 3, FACE_TO = 20;

    // The face of the ring between the symbols' arches, which the arches' insides are flush with
    // and the symbols are on: a cone, z rising with radius
    public static double faceZ(double r) {
        return interpolate(PROFILE, 7, 10, r);
    }

    // How far forward the body's front is at the given radius; where it steps, the lower side
    static double bodyZ(double r) {
        return interpolate(PROFILE, FACE_FROM, FACE_TO, r);
    }

    static double interpolate(double[][] p, int from, int to, double r) {
        if (r <= p[from][0]) {
            return p[from][1];
        }
        double best = Double.NaN;
        for (int i = from; i < to; i++) {
            double r0 = p[i][0], r1 = p[i + 1][0];
            double lo = Math.min(r0, r1), hi = Math.max(r0, r1);
            if (r < lo || r > hi) {
                continue;
            }
            double z = hi - lo < 1e-9 ? Math.min(p[i][1], p[i + 1][1])
                    : p[i][1] + (p[i + 1][1] - p[i][1]) * (r - r0) / (r1 - r0);
            best = Double.isNaN(best) ? z : Math.min(best, z);
        }
        return Double.isNaN(best) ? p[to][1] : best;
    }

    // Receives the gate's triangles: corners anticlockwise seen from the side the normal points to
    public interface Sink {
        void triangle(int part, double[] a, double[] b, double[] c, double[] normal);
    }

    // A ninth of the gate's raised faces and their sides, in a frame where the chevron's centre
    // is along x, with half the way to the next chevron either side: {part, ax, ay, az, bx, ..., nx, ny, nz}
    final List<double[]> section = new ArrayList<>();

    public static UniverseGateModel load(InputStream in) throws IOException {
        List<Face> faces = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\\s+");
                Face face = new Face();
                face.part = f[0].equals("a") ? CHEVRON : f[0].equals("b") ? RELIEF : DETAIL;
                int n = (f.length - 1) / 3;
                face.points = new double[n][];
                for (int i = 0; i < n; i++) {
                    // The faces were measured on the side of the model without the symbols, which
                    // is the gate's back; the front is them seen from the other side, mirrored
                    // across the chevron's centre
                    double d = -Double.parseDouble(f[1 + 3 * i]), r = Double.parseDouble(f[2 + 3 * i]);
                    double a = Math.toRadians(-d);
                    face.points[i] = new double[] {r * Math.cos(a), r * Math.sin(a), Double.parseDouble(f[3 + 3 * i]), d, r};
                }
                faces.add(face);
            }
        }
        UniverseGateModel model = new UniverseGateModel();
        model.buildSection(faces);
        return model;
    }

    static class Face {
        int part;
        // Each corner: x, y, z, then degrees from the chevron's centre and radius
        double[][] points;
        // Its corners in threes, covering it
        List<int[]> triangles;
    }

    // The raised faces of the section
    final List<Face> faces = new ArrayList<>();

    void buildSection(List<Face> faces) {
        this.faces.addAll(faces);
        // Edges two faces share have no side
        Map<String, Integer> edgeUse = new HashMap<>();
        for (Face f : faces) {
            for (int i = 0; i < f.points.length; i++) {
                edgeUse.merge(edgeKey(f.points[i], f.points[(i + 1) % f.points.length]), 1, Integer::sum);
            }
        }
        for (Face f : faces) {
            double[][] p = f.points;
            boolean anticlockwise = signedArea(p) > 0;
            f.triangles = triangulate(p);
            for (int[] t : f.triangles) {
                double[] a = p[t[0]], b = p[t[1]], c = p[t[2]];
                // Each face is on the front, so faces forwards whichever way round its outline goes
                add(f.part, a, b, c, normal(a, b, c, 1));
            }
            for (int i = 0; i < p.length; i++) {
                double[] a = p[i], b = p[(i + 1) % p.length];
                if (edgeUse.get(edgeKey(a, b)) > 1 || onSectionEdge(a, b)) {
                    continue;
                }
                double baseA = bodyZ(a[4]), baseB = bodyZ(b[4]);
                if (a[2] <= baseA + 1e-5 && b[2] <= baseB + 1e-5) {
                    continue;
                }
                // Outwards from the face: to the right of its edges going anticlockwise
                double ex = b[0] - a[0], ey = b[1] - a[1], len = Math.hypot(ex, ey);
                if (len < 1e-9) {
                    continue;
                }
                double s = anticlockwise ? 1 : -1;
                double[] n = {s * ey / len, -s * ex / len, 0};
                double[] a0 = {a[0], a[1], Math.min(baseA, a[2])}, b0 = {b[0], b[1], Math.min(baseB, b[2])};
                addQuad(f.part, a0, b0, b, a, n);
            }
        }
    }

    static boolean onSectionEdge(double[] a, double[] b) {
        double h = SECTION_ANGLE / 2;
        return Math.abs(a[3] + h) < 1e-3 && Math.abs(b[3] + h) < 1e-3
                || Math.abs(a[3] - h) < 1e-3 && Math.abs(b[3] - h) < 1e-3;
    }

    static String edgeKey(double[] a, double[] b) {
        String ka = key(a), kb = key(b);
        return ka.compareTo(kb) < 0 ? ka + "|" + kb : kb + "|" + ka;
    }

    static String key(double[] p) {
        return Math.round(p[0] * 2e4) + "," + Math.round(p[1] * 2e4) + "," + Math.round(p[2] * 2e4);
    }

    static double signedArea(double[][] p) {
        double s = 0;
        for (int i = 0; i < p.length; i++) {
            double[] a = p[i], b = p[(i + 1) % p.length];
            s += a[0] * b[1] - b[0] * a[1];
        }
        return s / 2;
    }

    // The unit normal of a triangle, turned to face up (z) if up > 0, else down
    static double[] normal(double[] a, double[] b, double[] c, int up) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-15) {
            return new double[] {0, 0, 1};
        }
        double s = (nz >= 0) == (up > 0) ? 1 / l : -1 / l;
        return new double[] {nx * s, ny * s, nz * s};
    }

    // Keeps a triangle of the section, its corners put anticlockwise seen from where n points
    void add(int part, double[] a, double[] b, double[] c, double[] n) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        if (cx * n[0] + cy * n[1] + cz * n[2] < 0) {
            double[] t = b;
            b = c;
            c = t;
        }
        section.add(new double[] {part, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], n[0], n[1], n[2]});
    }

    void addQuad(int part, double[] a, double[] b, double[] c, double[] d, double[] n) {
        add(part, a, b, c, n);
        add(part, a, c, d, n);
    }

    // Splits a simple polygon, of corners (x, y, ...) in either winding, into triangles of its
    // corners, by cutting off ears
    static List<int[]> triangulate(double[][] p) {
        List<int[]> tris = new ArrayList<>();
        int n = p.length;
        List<Integer> v = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            v.add(i);
        }
        double s = signedArea(p) > 0 ? 1 : -1;
        int guard = 0;
        while (v.size() > 3 && guard++ < 10 * n * n) {
            boolean cut = false;
            for (int i = 0; i < v.size(); i++) {
                int i0 = v.get((i + v.size() - 1) % v.size()), i1 = v.get(i), i2 = v.get((i + 1) % v.size());
                double cross = (p[i1][0] - p[i0][0]) * (p[i2][1] - p[i0][1]) - (p[i1][1] - p[i0][1]) * (p[i2][0] - p[i0][0]);
                if (cross * s <= 1e-14) {
                    continue;
                }
                boolean empty = true;
                for (int j : v) {
                    if (j != i0 && j != i1 && j != i2 && inTriangle(p[j], p[i0], p[i1], p[i2])) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    tris.add(new int[] {i0, i1, i2});
                    v.remove(i);
                    cut = true;
                    break;
                }
            }
            if (!cut) {
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

    // Gives the whole gate to the sink: the body turned in the given number of steps round, and
    // the raised faces, a ninth at a time
    public void build(Sink sink, int bodySteps) {
        buildBody(sink, bodySteps);
        for (int g = 0; g < CHEVRONS; g++) {
            double turn = Math.toRadians(90 - g * SECTION_ANGLE);
            double cos = Math.cos(turn), sin = Math.sin(turn);
            for (int face = -1; face <= 1; face += 2) {
                for (double[] t : section) {
                    double[][] q = new double[3][];
                    for (int k = 0; k < 3; k++) {
                        // Mirrored through the ring onto the back as needed, then turned to the
                        // chevron
                        double x = t[1 + 3 * k], y = t[2 + 3 * k], z = t[3 + 3 * k] * face;
                        q[k] = new double[] {x * cos - y * sin, x * sin + y * cos, z};
                    }
                    double nx = t[10], ny = t[11], nz = t[12] * face;
                    double[] n = {nx * cos - ny * sin, nx * sin + ny * cos, nz};
                    // Mirroring turns the corners the other way round
                    if (face > 0) {
                        sink.triangle((int) t[0], q[0], q[1], q[2], n);
                    } else {
                        sink.triangle((int) t[0], q[0], q[2], q[1], n);
                    }
                }
            }
        }
    }

    void buildBody(Sink sink, int steps) {
        for (int face = -1; face <= 1; face += 2) {
            for (int i = 0; i + 1 < PROFILE.length; i++) {
                double r0 = PROFILE[i][0], z0 = PROFILE[i][1] * face, r1 = PROFILE[i + 1][0], z1 = PROFILE[i + 1][1] * face;
                double dr = r1 - r0, dz = z1 - z0, len = Math.hypot(dr, dz);
                if (len < 1e-9) {
                    continue;
                }
                // Outwards from the body: to the left going along the front's profile, so to the
                // right along the back's, which is mirrored
                double nr = -dz / len * face, nz = dr / len * face;
                for (int j = 0; j < steps; j++) {
                    double a0 = 2 * Math.PI * j / steps, a1 = 2 * Math.PI * (j + 1) / steps;
                    double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                    double am = (a0 + a1) / 2;
                    double[] n = {nr * Math.cos(am), nr * Math.sin(am), nz};
                    double[] p = {r0 * c0, r0 * s0, z0}, q = {r1 * c0, r1 * s0, z1};
                    double[] s = {r1 * c1, r1 * s1, z1}, t = {r0 * c1, r0 * s1, z0};
                    emitFacing(sink, BODY, p, q, s, n);
                    emitFacing(sink, BODY, p, s, t, n);
                }
            }
        }
    }

    // Gives a triangle to the sink with its corners in the order that makes it face along n
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

    // The forward faces of one chevron, as triangles {ax, ay, az, bx, ..., cz} in its frame, its
    // centre along x
    public List<double[]> chevronSurface() {
        if (chevronSurface == null) {
            chevronSurface = new ArrayList<>();
            for (double[] t : section) {
                if ((int) t[0] == CHEVRON && t[12] >= 0.05) {
                    chevronSurface.add(Arrays.copyOfRange(t, 1, 10));
                }
            }
        }
        return chevronSurface;
    }

    List<double[]> chevronSurface;
}
