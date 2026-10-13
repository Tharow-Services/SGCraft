// Simplifies a mesh by collapsing its edges one at a time, cheapest first, until it has no more
// than the given number of triangles (Garland and Heckbert's quadric error metric). Each corner
// keeps the sum of the planes of the triangles that met there, and an edge costs how far its
// corners, moved together, would stand off those planes; so flat stretches go first, and the
// sharp edges that give the shape stay. Its open edges, and the edges between its materials,
// are held by planes upright across them. Collapses that would turn a triangle over are
// skipped. Each triangle is [[x, y, z] x 3], with any other values on it (such as .name) kept.
function decimate(tris, target) {
    var PriorityQueue = Java.type("java.util.PriorityQueue");
    // Corners welded where they meet
    var pos = [], index = {}, faces = [];
    function weld(q) {
        var k = q[0].toFixed(6) + "," + q[1].toFixed(6) + "," + q[2].toFixed(6);
        if (index[k] === undefined) { index[k] = pos.length; pos.push([q[0], q[1], q[2]]); }
        return index[k];
    }
    tris.forEach(function (t) {
        var f = t.map(weld);
        if (f[0] == f[1] || f[1] == f[2] || f[0] == f[2]) return;
        var extra = {};
        for (var p in t) if (isNaN(p)) extra[p] = t[p];
        faces.push({v: f, alive: true, extra: extra, name: t.name || ""});
    });
    var n = pos.length, around = [];
    for (var i = 0; i < n; i++) around.push([]);
    faces.forEach(function (f, fi) { f.v.forEach(function (v) { around[v].push(fi); }); });
    function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
    function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
    function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    function normalOf(a, b, c) { return cross(sub(b, a), sub(c, a)); }
    // Quadrics, as the 10 values of a symmetric 4 x 4 matrix
    var Q = [];
    for (var i = 0; i < n; i++) Q.push([0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
    function addPlane(q, nx, ny, nz, d, w) {
        q[0] += w * nx * nx; q[1] += w * nx * ny; q[2] += w * nx * nz; q[3] += w * nx * d;
        q[4] += w * ny * ny; q[5] += w * ny * nz; q[6] += w * ny * d;
        q[7] += w * nz * nz; q[8] += w * nz * d; q[9] += w * d * d;
    }
    function errorAt(q, p) {
        var x = p[0], y = p[1], z = p[2];
        return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x + q[4] * y * y + 2 * q[5] * y * z
            + 2 * q[6] * y + q[7] * z * z + 2 * q[8] * z + q[9];
    }
    // Edges, by their corners, with the faces on them, to find open edges and material edges
    var edgeFaces = {};
    function ekey(a, b) { return a < b ? a + "_" + b : b + "_" + a; }
    faces.forEach(function (f, fi) {
        var nn = normalOf(pos[f.v[0]], pos[f.v[1]], pos[f.v[2]]), l = Math.sqrt(dot(nn, nn));
        if (l < 1e-15) return;
        var u = [nn[0] / l, nn[1] / l, nn[2] / l], d = -dot(u, pos[f.v[0]]);
        f.v.forEach(function (v) { addPlane(Q[v], u[0], u[1], u[2], d, l / 2); });
        for (var e = 0; e < 3; e++) {
            var k = ekey(f.v[e], f.v[(e + 1) % 3]);
            (edgeFaces[k] = edgeFaces[k] || []).push(fi);
        }
    });
    var HOLD = 1000;
    Object.keys(edgeFaces).forEach(function (k) {
        var fs = edgeFaces[k];
        var open = fs.length == 1, seam = fs.length > 1 && fs.some(function (fi) { return faces[fi].name != faces[fs[0]].name; });
        if (!open && !seam) return;
        var ab = k.split("_").map(Number), a = pos[ab[0]], b = pos[ab[1]], e = sub(b, a);
        fs.forEach(function (fi) {
            var f = faces[fi], nn = normalOf(pos[f.v[0]], pos[f.v[1]], pos[f.v[2]]), side = cross(e, nn), l = Math.sqrt(dot(side, side));
            if (l < 1e-15) return;
            var u = [side[0] / l, side[1] / l, side[2] / l], d = -dot(u, a), w = HOLD * dot(e, e);
            addPlane(Q[ab[0]], u[0], u[1], u[2], d, w);
            addPlane(Q[ab[1]], u[0], u[1], u[2], d, w);
        });
    });
    // Each corner's version, so that queued edges made stale by a collapse are skipped
    var version = [], alive = [];
    for (var i = 0; i < n; i++) { version.push(0); alive.push(true); }
    var queue = new PriorityQueue(1024, function (p, q) { return p[0] < q[0] ? -1 : p[0] > q[0] ? 1 : 0; });
    function sumQ(a, b) { var q = []; for (var i = 0; i < 10; i++) q.push(Q[a][i] + Q[b][i]); return q; }
    function best(a, b) {
        var q = sumQ(a, b), pa = pos[a], pb = pos[b], pm = [(pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2, (pa[2] + pb[2]) / 2];
        var ea = errorAt(q, pa), eb = errorAt(q, pb), em = errorAt(q, pm);
        return ea <= eb && ea <= em ? [ea, pa] : eb <= em ? [eb, pb] : [em, pm];
    }
    function push(a, b) {
        var r = best(a, b);
        queue.add([r[0], a, b, version[a], version[b]]);
    }
    Object.keys(edgeFaces).forEach(function (k) { var ab = k.split("_").map(Number); push(ab[0], ab[1]); });
    var count = faces.length;
    // Whether moving corner v to p turns over any of its faces (other than those on the edge)
    function flips(v, other, p) {
        for (var i = 0; i < around[v].length; i++) {
            var f = faces[around[v][i]];
            if (!f.alive || f.v.indexOf(other) >= 0) continue;
            var q = f.v.map(function (w) { return w == v ? p : pos[w]; });
            var before = normalOf(pos[f.v[0]], pos[f.v[1]], pos[f.v[2]]), after = normalOf(q[0], q[1], q[2]);
            var lb = Math.sqrt(dot(before, before)), la = Math.sqrt(dot(after, after));
            if (la < 1e-15 || dot(before, after) < 0.5 * lb * la) return true;
        }
        return false;
    }
    while (count > target && !queue.isEmpty()) {
        var e = queue.poll(), a = e[1], b = e[2];
        if (!alive[a] || !alive[b] || version[a] != e[3] || version[b] != e[4]) continue;
        var r = best(a, b), p = r[1];
        if (flips(a, b, p) || flips(b, a, p)) continue;
        // b goes into a
        pos[a] = [p[0], p[1], p[2]];
        Q[a] = sumQ(a, b);
        alive[b] = false;
        around[b].forEach(function (fi) {
            var f = faces[fi];
            if (!f.alive) return;
            if (f.v.indexOf(a) >= 0) { f.alive = false; count--; return; }
            f.v = f.v.map(function (w) { return w == b ? a : w; });
            around[a].push(fi);
        });
        around[a] = around[a].filter(function (fi) { return faces[fi].alive; });
        version[a]++;
        var near = {};
        around[a].forEach(function (fi) { faces[fi].v.forEach(function (w) { if (w != a) near[w] = 1; }); });
        Object.keys(near).forEach(function (w) { push(a, +w); });
    }
    var out = [];
    faces.forEach(function (f) {
        if (!f.alive) return;
        var t = f.v.map(function (v) { return pos[v].slice(); });
        for (var p in f.extra) t[p] = f.extra[p];
        out.push(t);
    });
    return out;
}
