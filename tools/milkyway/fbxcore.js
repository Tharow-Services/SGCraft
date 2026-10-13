// Reads a binary .fbx (version 7.x) into its node tree, and its meshes into prims: one per
// geometry and material, {name, model, pos, idx, uv}, positions in the file's own space with each
// model's transform applied. Run with jjs (Nashorn); loaded by the other scripts, with `file` set.
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var ByteBuffer = Java.type("java.nio.ByteBuffer");
var ByteOrder = Java.type("java.nio.ByteOrder");
var Inflater = Java.type("java.util.zip.Inflater");
var BufferedImage = Java.type("java.awt.image.BufferedImage");
var ImageIO = Java.type("javax.imageio.ImageIO");
var File = Java.type("java.io.File");
var JString = Java.type("java.lang.String");
var DoubleArray = Java.type("double[]");
var IntArray = Java.type("int[]");
var ByteArray = Java.type("byte[]");

var bytes = Files.readAllBytes(Paths.get(file));
var bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
var fbxVersion = bb.getInt(23);
var wide = fbxVersion >= 7500;

function readArray(type, pos) {
    var n = bb.getInt(pos), enc = bb.getInt(pos + 4), len = bb.getInt(pos + 8);
    var size = {f: 4, d: 8, l: 8, i: 4, b: 1}[type];
    var data;
    if (enc == 1) {
        var inf = new Inflater();
        inf.setInput(bytes, pos + 12, len);
        var raw = new ByteArray(n * size);
        var got = 0;
        while (got < raw.length) got += inf.inflate(raw, got, raw.length - got);
        inf.end();
        data = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
    } else {
        data = ByteBuffer.wrap(bytes, pos + 12, n * size).slice().order(ByteOrder.LITTLE_ENDIAN);
    }
    var res;
    if (type == "d" || type == "f") {
        res = new DoubleArray(n);
        for (var k = 0; k < n; k++) res[k] = type == "d" ? data.getDouble(k * 8) : data.getFloat(k * 4);
    } else {
        res = new IntArray(n);
        for (var k = 0; k < n; k++) res[k] = type == "l" ? data.getLong(k * 8) : type == "i" ? data.getInt(k * 4) : data.get(k);
    }
    return {value: res, next: pos + 12 + len};
}

function readNode(pos) {
    var end = wide ? bb.getLong(pos) : bb.getInt(pos);
    var nprops = wide ? bb.getLong(pos + 8) : bb.getInt(pos + 4);
    var head = wide ? 24 : 12;
    var nameLen = bb.get(pos + head) & 0xff;
    if (end == 0) return null;
    var name = String(new JString(bytes, pos + head + 1, nameLen, "ASCII"));
    var p = pos + head + 1 + nameLen, props = [];
    for (var i = 0; i < nprops; i++) {
        var t = String.fromCharCode(bb.get(p)); p++;
        switch (t) {
            case "Y": props.push(bb.getShort(p)); p += 2; break;
            case "C": props.push(bb.get(p) != 0); p += 1; break;
            case "I": props.push(bb.getInt(p)); p += 4; break;
            case "F": props.push(bb.getFloat(p)); p += 4; break;
            case "D": props.push(bb.getDouble(p)); p += 8; break;
            case "L": props.push(String(bb.getLong(p))); p += 8; break;
            case "S": case "R":
                var l = bb.getInt(p);
                props.push(t == "S" ? String(new JString(bytes, p + 4, l, "UTF-8")) : "<raw " + l + ">"); p += 4 + l; break;
            default:
                var a = readArray(t, p); props.push(a.value); p = a.next;
        }
    }
    var children = [];
    while (p < end) {
        var c = readNode(p);
        if (c == null) break;
        children.push(c.node);
        p = c.end;
    }
    return {node: {name: name, props: props, children: children}, end: end};
}

var root = [];
(function () {
    var p = 27;
    while (p < bytes.length - 200) {
        var c = readNode(p);
        if (c == null) break;
        root.push(c.node);
        p = c.end;
    }
})();

function child(n, name) {
    for (var i = 0; i < n.children.length; i++) if (n.children[i].name == name) return n.children[i];
    return null;
}
function top(name) {
    for (var i = 0; i < root.length; i++) if (root[i].name == name) return root[i];
    return null;
}
// A Properties70 value, or the default
function prop70(n, key, dflt) {
    var p = n && child(n, "Properties70");
    if (p) for (var i = 0; i < p.children.length; i++) {
        var q = p.children[i].props;
        if (q[0] == key) return q.slice(4);
    }
    return dflt;
}

var objects = {}, links = [];
top("Objects").children.forEach(function (o) {
    objects[o.props[0]] = o;
});
top("Connections").children.forEach(function (c) {
    links.push({kind: c.props[0], from: c.props[1], to: c.props[2], prop: c.props[3]});
});
function parentsOf(id) {
    return links.filter(function (l) { return l.from == id; }).map(function (l) { return l.to; });
}
function childrenOf(id) {
    return links.filter(function (l) { return l.to == id; }).map(function (l) { return l.from; });
}
function cleanName(o) { return String(o.props[1]).split("\u0000")[0]; }

function matMul(a, b) {
    var r = [];
    for (var i = 0; i < 4; i++) for (var j = 0; j < 4; j++) {
        var s = 0;
        for (var k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
        r.push(s);
    }
    return r;
}
function translate(v) { return [1, 0, 0, v[0], 0, 1, 0, v[1], 0, 0, 1, v[2], 0, 0, 0, 1]; }
function scale(v) { return [v[0], 0, 0, 0, 0, v[1], 0, 0, 0, 0, v[2], 0, 0, 0, 0, 1]; }
function rotXYZ(v) {
    // FBX's default order: X first, then Y, then Z (R = Rz Ry Rx)
    var x = v[0] * Math.PI / 180, y = v[1] * Math.PI / 180, z = v[2] * Math.PI / 180;
    var rx = [1, 0, 0, 0, 0, Math.cos(x), -Math.sin(x), 0, 0, Math.sin(x), Math.cos(x), 0, 0, 0, 0, 1];
    var ry = [Math.cos(y), 0, Math.sin(y), 0, 0, 1, 0, 0, -Math.sin(y), 0, Math.cos(y), 0, 0, 0, 0, 1];
    var rz = [Math.cos(z), -Math.sin(z), 0, 0, Math.sin(z), Math.cos(z), 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    return matMul(rz, matMul(ry, rx));
}
// A model's transform, from its own space to the file's
function modelMatrix(id) {
    var o = objects[id];
    if (!o || o.name != "Model") return [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    var m = matMul(translate(prop70(o, "Lcl Translation", [0, 0, 0])),
        matMul(rotXYZ(prop70(o, "PreRotation", [0, 0, 0])),
        matMul(rotXYZ(prop70(o, "Lcl Rotation", [0, 0, 0])), scale(prop70(o, "Lcl Scaling", [1, 1, 1])))));
    var up = parentsOf(id).filter(function (p) { return objects[p] && objects[p].name == "Model"; });
    return up.length ? matMul(modelMatrix(up[0]), m) : m;
}

var prims = [];
Object.keys(objects).forEach(function (id) {
    var g = objects[id];
    if (g.name != "Geometry" || !child(g, "PolygonVertexIndex")) return;
    var model = parentsOf(g.props[0]).filter(function (p) { return objects[p] && objects[p].name == "Model"; })[0];
    var mo = objects[model];
    var m = matMul(modelMatrix(model), matMul(translate(prop70(mo, "GeometricTranslation", [0, 0, 0])),
        matMul(rotXYZ(prop70(mo, "GeometricRotation", [0, 0, 0])), scale(prop70(mo, "GeometricScaling", [1, 1, 1])))));
    var mats = childrenOf(model).filter(function (c) { return objects[c] && objects[c].name == "Material"; })
        .map(function (c) { return cleanName(objects[c]); });
    var V = child(g, "Vertices").props[0], PI = child(g, "PolygonVertexIndex").props[0];
    var nv = V.length / 3, pos = new DoubleArray(V.length);
    for (var i = 0; i < nv; i++) {
        var x = V[3 * i], y = V[3 * i + 1], z = V[3 * i + 2];
        pos[3 * i] = m[0] * x + m[1] * y + m[2] * z + m[3];
        pos[3 * i + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
        pos[3 * i + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
    }
    // UVs, per polygon corner
    var uvNode = child(g, "LayerElementUV"), uv = null, uvIdx = null, uvByVertex = false;
    if (uvNode) {
        uv = child(uvNode, "UV").props[0];
        var ui = child(uvNode, "UVIndex");
        uvIdx = ui ? ui.props[0] : null;
        uvByVertex = child(uvNode, "MappingInformationType").props[0] == "ByVertice";
    }
    var lm = child(g, "LayerElementMaterial"), matIdx = lm ? child(lm, "Materials").props[0] : null;
    var allSame = !lm || child(lm, "MappingInformationType").props[0] == "AllSame";
    var byMat = {};
    var poly = [], polyNo = 0;
    for (var k = 0; k < PI.length; k++) {
        var v = PI[k], last = v < 0;
        if (last) v = ~v;
        poly.push([v, k]);
        if (last) {
            var mi = allSame ? (matIdx ? matIdx[0] : 0) : matIdx[polyNo];
            var b = byMat[mi] = byMat[mi] || {idx: [], uvs: []};
            for (var t = 1; t + 1 < poly.length; t++) {
                [poly[0], poly[t], poly[t + 1]].forEach(function (c) {
                    b.idx.push(c[0]);
                    if (uv) {
                        var u = uvByVertex ? c[0] : uvIdx ? uvIdx[c[1]] : c[1];
                        b.uvs.push(uv[2 * u], uv[2 * u + 1]);
                    }
                });
            }
            poly = [];
            polyNo++;
        }
    }
    Object.keys(byMat).forEach(function (mi) {
        prims.push({name: mats[mi] || ("material" + mi), model: cleanName(mo), pos: pos, idx: byMat[mi].idx, uv: byMat[mi].uvs});
    });
});
