//------------------------------------------------------------------------------------------------
//
//   SG Craft - The shape of a Pegasus (Atlantis) gate
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The shape of a Pegasus gate, as on the show, as groups of triangles (see pegasus_gate.txt, made
// by tools/pegasus/extract.js). It is in metres, the front towards +z, its chevrons at 90 degrees
// and every 40 degrees round from there. The fixed ring's faces and edges are given for the ninth of
// it round the top chevron, the inner ring's screens and the dividers between them for the one at
// the top, and the chevron for the top one; the rest is those, turned.
public class PegasusGateModel {

    public static final int CHEVRONS = 9, SCREENS = 36;
    public static final double SECTION_ANGLE = 360.0 / CHEVRONS, SCREEN_ANGLE = 360.0 / SCREENS;

    // A group's triangles: {ax, ay, az, au, av, bx, ..., cv}, corners anticlockwise seen from
    // outside
    final Map<String, List<double[]>> groups = new HashMap<>();
    public double outerRadius;
    // Where the screens are: from screenInner out to screenOuter, their surface screenZ0 forward
    // at the inside, rising by screenSlope for each metre out
    public double screenInner, screenOuter, screenZ0, screenSlope;

    public static PegasusGateModel load(InputStream in) throws IOException {
        PegasusGateModel model = new PegasusGateModel();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            List<double[]> corners = null, tris = null;
            while ((line = reader.readLine()) != null) {
                String[] f = line.trim().split("\\s+");
                switch (f[0]) {
                    case "g":
                        corners = new ArrayList<>();
                        tris = model.groups.computeIfAbsent(f[1], k -> new ArrayList<>());
                        break;
                    case "v":
                        corners.add(new double[] {Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]),
                                Double.parseDouble(f[4]), Double.parseDouble(f[5])});
                        break;
                    case "t":
                        double[] t = new double[15];
                        for (int k = 0; k < 3; k++) {
                            System.arraycopy(corners.get(Integer.parseInt(f[1 + k])), 0, t, 5 * k, 5);
                        }
                        tris.add(t);
                        break;
                }
            }
        }
        model.measure();
        return model;
    }

    // Faces meeting at more than this angle (degrees) are a crease, lit apart; less, and the
    // surface is smooth across them
    static final double CREASE = 40;

    // The normal at each corner of each of a group's triangles, {ax, ay, az, bx, ..., cz}: the
    // faces round that corner averaged, by their area, those across a crease left out
    public List<double[]> cornerNormals(String name) {
        List<double[]> normals = smoothNormals.get(name);
        if (normals != null) {
            return normals;
        }
        List<double[]> tris = group(name);
        Map<String, List<double[]>> round = new HashMap<>();
        List<double[]> faceNormals = new ArrayList<>();
        for (double[] t : tris) {
            double[] n = normal(t);
            double ux = t[5] - t[0], uy = t[6] - t[1], uz = t[7] - t[2];
            double vx = t[10] - t[0], vy = t[11] - t[1], vz = t[12] - t[2];
            double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
            double area = Math.sqrt(cx * cx + cy * cy + cz * cz) / 2;
            double[] weighted = {n[0], n[1], n[2], area};
            faceNormals.add(weighted);
            for (int k = 0; k < 3; k++) {
                round.computeIfAbsent(cornerKey(t, k), x -> new ArrayList<>()).add(weighted);
            }
        }
        double creaseCos = Math.cos(Math.toRadians(CREASE));
        normals = new ArrayList<>();
        for (int i = 0; i < tris.size(); i++) {
            double[] t = tris.get(i), own = faceNormals.get(i), out = new double[9];
            for (int k = 0; k < 3; k++) {
                double sx = 0, sy = 0, sz = 0;
                for (double[] n : round.get(cornerKey(t, k))) {
                    if (n[0] * own[0] + n[1] * own[1] + n[2] * own[2] >= creaseCos) {
                        sx += n[0] * n[3];
                        sy += n[1] * n[3];
                        sz += n[2] * n[3];
                    }
                }
                double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (l < 1e-15) {
                    sx = own[0];
                    sy = own[1];
                    sz = own[2];
                    l = 1;
                }
                out[3 * k] = sx / l;
                out[3 * k + 1] = sy / l;
                out[3 * k + 2] = sz / l;
            }
            normals.add(out);
        }
        smoothNormals.put(name, normals);
        return normals;
    }

    final Map<String, List<double[]>> smoothNormals = new HashMap<>();

    static String cornerKey(double[] t, int k) {
        return Math.round(t[5 * k] * 1e4) + "," + Math.round(t[5 * k + 1] * 1e4) + "," + Math.round(t[5 * k + 2] * 1e4);
    }

    void measure() {
        for (double[] t : group("front")) {
            for (int k = 0; k < 3; k++) {
                outerRadius = Math.max(outerRadius, Math.hypot(t[5 * k], t[5 * k + 1]));
            }
        }
        // The screens' front: a line through the corners of its forward faces, fitted
        double n = 0, sr = 0, sz = 0, srr = 0, srz = 0;
        screenInner = Double.MAX_VALUE;
        for (double[] t : group("screen")) {
            double[] nn = normal(t);
            if (nn[2] < 0.9) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                double r = Math.hypot(t[5 * k], t[5 * k + 1]), z = t[5 * k + 2];
                n++;
                sr += r;
                sz += z;
                srr += r * r;
                srz += r * z;
                screenInner = Math.min(screenInner, r);
                screenOuter = Math.max(screenOuter, r);
            }
        }
        if (n > 1) {
            screenSlope = (n * srz - sr * sz) / Math.max(1e-12, n * srr - sr * sr);
            // The line's height at the centre, then at the screens' inside
            double z0 = (sz - screenSlope * sr) / n;
            // The screens are slabs, with forward faces at their back as well as their front:
            // the line moved forward onto the front, through the furthest forward of them
            double lift = Double.NEGATIVE_INFINITY;
            for (double[] t : group("screen")) {
                for (int k = 0; k < 3; k++) {
                    lift = Math.max(lift, t[5 * k + 2] - (z0 + screenSlope * Math.hypot(t[5 * k], t[5 * k + 1])));
                }
            }
            screenZ0 = z0 + lift + screenSlope * screenInner;
        }
    }

    // The screens' surface, how far forward at the given radius
    public double screenZ(double r) {
        return screenZ0 + screenSlope * (r - screenInner);
    }

    public List<double[]> group(String name) {
        List<double[]> g = groups.get(name);
        return g != null ? g : new ArrayList<>();
    }

    // The unit normal of a triangle, the way its corners go round
    public static double[] normal(double[] t) {
        double ux = t[5] - t[0], uy = t[6] - t[1], uz = t[7] - t[2];
        double vx = t[10] - t[0], vy = t[11] - t[1], vz = t[12] - t[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-15 ? new double[] {0, 0, 1} : new double[] {nx / l, ny / l, nz / l};
    }
}
