// Writes the triangles of SGCraft's Milky Way gate model as the mod builds it, for
// tools/milkyway/compare.js to set beside the reference: each {int part, then 9 floats, its
// corners}, big-endian, with the symbol ring at rest and the top chevron moved the given fraction
// of the way to locked.
// javac -cp build/classes/java/main -d build/tools tools/milkyway/DumpModel.java
// java -cp build/classes/java/main;build/tools DumpModel <milkyway_gate.txt> <out.bin> [locked]

import gcewing.sg.client.renderer.MilkyWayGateModel;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

public class DumpModel {

    public static void main(String[] args) throws Exception {
        MilkyWayGateModel model;
        try (FileInputStream in = new FileInputStream(args[0])) {
            model = MilkyWayGateModel.load(in);
        }
        double locked = args.length > 2 ? Double.parseDouble(args[2]) : 0;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[1])))) {
            double[] shift = new double[3], turn = new double[1];
            MilkyWayGateModel.Sink sink = (part, a, b, c, n) -> {
                try {
                    out.writeInt(part);
                    double co = Math.cos(turn[0]), si = Math.sin(turn[0]);
                    for (double[] p : new double[][] {a, b, c}) {
                        double x = p[0] + shift[0], y = p[1] + shift[1];
                        out.writeFloat((float) (x * co - y * si));
                        out.writeFloat((float) (x * si + y * co));
                        out.writeFloat((float) (p[2] + shift[2]));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            model.buildFixed(sink, 2);
            model.buildRing(sink, 180);
            // The chevrons, the top one moved
            for (int g = 0; g < MilkyWayGateModel.CHEVRONS; g++) {
                turn[0] = Math.toRadians(g * MilkyWayGateModel.SECTION_ANGLE);
                for (int piece = 0; piece < MilkyWayGateModel.PIECES; piece++) {
                    for (int m = 0; m < model.moveCount(); m++) {
                        double[] d = model.move(m);
                        for (int k = 0; k < 3; k++) {
                            shift[k] = g == 0 ? d[k] * locked : 0;
                        }
                        model.buildChevron(sink, piece, m);
                    }
                }
            }
        }
    }
}
