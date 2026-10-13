// Reads a Wavefront .obj, streaming it, into prims: one per material, {name, pos, idx}, the
// positions shared, faces split into triangles as fans. Run with jjs (Nashorn); loaded by the
// other scripts, with `file` set, and optionally `keep(name)` set to say which materials to read.
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var ByteBuffer = Java.type("java.nio.ByteBuffer");
var BufferedImage = Java.type("java.awt.image.BufferedImage");
var ImageIO = Java.type("javax.imageio.ImageIO");
var File = Java.type("java.io.File");
var DoubleArray = Java.type("double[]");
var IntArray = Java.type("int[]");
var BufferedReader = Java.type("java.io.BufferedReader");
var FileReader = Java.type("java.io.FileReader");

var pos = new DoubleArray(1 << 20), nPos = 0;
var byMaterial = {};
(function () {
    var reader = new BufferedReader(new FileReader(file), 1 << 20);
    var current = null, line;
    while ((line = reader.readLine()) != null) {
        var c0 = line.charAt(0);
        if (c0 == 'v' && line.charAt(1) == ' ') {
            var f = line.split(" ");
            if (nPos + 3 > pos.length) {
                var bigger = new DoubleArray(pos.length * 2);
                java.lang.System.arraycopy(pos, 0, bigger, 0, nPos);
                pos = bigger;
            }
            var k = 1;
            while (f[k] === "") k++;
            pos[nPos++] = +f[k]; pos[nPos++] = +f[k + 1]; pos[nPos++] = +f[k + 2];
        } else if (c0 == 'u' && line.startsWith("usemtl ")) {
            var name = String(line.substring(7)).trim();
            current = typeof keep == "function" && !keep(name) ? null : (byMaterial[name] = byMaterial[name] || {n: 0, idx: new IntArray(1 << 12)});
        } else if (c0 == 'f' && line.charAt(1) == ' ' && current) {
            var f = line.split(" "), ids = [];
            for (var i = 1; i < f.length; i++) {
                if (f[i] === "") continue;
                var s = f[i], slash = s.indexOf("/");
                var v = parseInt(slash < 0 ? s : s.substring(0, slash));
                ids.push(v < 0 ? nPos / 3 + v : v - 1);
            }
            for (var t = 1; t + 1 < ids.length; t++) {
                if (current.n + 3 > current.idx.length) {
                    var bigger = new IntArray(current.idx.length * 2);
                    java.lang.System.arraycopy(current.idx, 0, bigger, 0, current.n);
                    current.idx = bigger;
                }
                current.idx[current.n++] = ids[0]; current.idx[current.n++] = ids[t]; current.idx[current.n++] = ids[t + 1];
            }
        }
    }
    reader.close();
})();

var prims = Object.keys(byMaterial).map(function (name) {
    var m = byMaterial[name], idx = new IntArray(m.n);
    java.lang.System.arraycopy(m.idx, 0, idx, 0, m.n);
    return {name: name, model: name, pos: pos, idx: idx};
});
