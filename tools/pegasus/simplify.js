// Simplifies a mesh by clustering: every corner moves to the average of the corners in its cell
// of a grid of the given size, and triangles left with fewer than three corners go. Each
// triangle is [[x, y, z] x 3], with any other values on it (such as .name) kept.
function simplify(tris, cell) {
    var sum = {};
    function key(q) { return Math.floor(q[0] / cell) + "," + Math.floor(q[1] / cell) + "," + Math.floor(q[2] / cell); }
    tris.forEach(function (v) {
        v.forEach(function (q) {
            var k = key(q), s = sum[k] = sum[k] || [0, 0, 0, 0];
            s[0] += q[0]; s[1] += q[1]; s[2] += q[2]; s[3]++;
        });
    });
    var at = {};
    Object.keys(sum).forEach(function (k) { var s = sum[k]; at[k] = [s[0] / s[3], s[1] / s[3], s[2] / s[3]]; });
    var out = [], seen = {};
    tris.forEach(function (v) {
        var ks = v.map(key);
        if (ks[0] == ks[1] || ks[1] == ks[2] || ks[0] == ks[2]) return;
        // The same triangle from more than one source (such as both sides of a thin wall,
        // turned the same way) only once
        var id = ks.slice().sort().join("|") + "|" + (v.name || "");
        var winding = ks[0] < ks[1] ? 1 : 0;
        if (seen[id + winding]) return;
        seen[id + winding] = 1;
        var t = ks.map(function (k) { return at[k]; });
        for (var p in v) if (isNaN(p)) t[p] = v[p];
        out.push(t);
    });
    return out;
}
