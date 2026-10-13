// Reads a .glb and renders views of it for measuring. Run with jjs (Nashorn).
// jjs glb.js -- <file.glb> <outdir>
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var ByteBuffer = Java.type("java.nio.ByteBuffer");
var ByteOrder = Java.type("java.nio.ByteOrder");
var BufferedImage = Java.type("java.awt.image.BufferedImage");
var ImageIO = Java.type("javax.imageio.ImageIO");
var File = Java.type("java.io.File");
var JString = Java.type("java.lang.String");

var bytes = Files.readAllBytes(Paths.get(file));
var bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
var jsonLen = bb.getInt(12);
var json = JSON.parse(new JString(bytes, 20, jsonLen, "UTF-8"));
var binStart = 20 + jsonLen + 8;

function accessor(i) {
    var a = json.accessors[i], v = json.bufferViews[a.bufferView];
    var off = binStart + (v.byteOffset || 0) + (a.byteOffset || 0);
    var comps = {SCALAR: 1, VEC2: 2, VEC3: 3}[a.type];
    var res = new Array(a.count * comps);
    for (var k = 0; k < a.count * comps; k++) {
        if (a.componentType == 5126) res[k] = bb.getFloat(off + k * 4);
        else if (a.componentType == 5123) res[k] = bb.getShort(off + k * 2) & 0xffff;
        else res[k] = bb.getInt(off + k * 4);
    }
    return res;
}

var prims = json.meshes[0].primitives.map(function (p) {
    return {name: json.materials[p.material].name, pos: accessor(p.attributes.POSITION), idx: accessor(p.indices)};
});
prims.forEach(function (p) {
    var n = p.pos.length / 3, minz = 1e9, maxz = -1e9, minr = 1e9, maxr = 0;
    for (var i = 0; i < n; i++) {
        var x = p.pos[3 * i], y = p.pos[3 * i + 1], z = p.pos[3 * i + 2], r = Math.sqrt(x * x + y * y);
        minz = Math.min(minz, z); maxz = Math.max(maxz, z); minr = Math.min(minr, r); maxr = Math.max(maxr, r);
    }
    print(p.name + ": verts " + n + " tris " + p.idx.length / 3 + " r " + minr.toFixed(4) + ".." + maxr.toFixed(4) + " z " + minz.toFixed(4) + ".." + maxz.toFixed(4));
});

