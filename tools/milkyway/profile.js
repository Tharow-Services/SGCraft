// Prints the cross section of a part of the Milky Way gate reference model through its axis at
// each given angle, as chains of (r, z) points, to read the ring's profile from.
// jjs profile.js -- <scratch> <fbx> <part> <angle>...
var file = arguments[1];
load(arguments[0] + "/fbxcore.js");
load(arguments[0] + "/mwcore.js");
var tris = parts[arguments[2]];
function k(p) { return p[0].toFixed(4) + "," + p[1].toFixed(4); }
for (var i = 3; i < arguments.length; i++) {
    var segs = slice(tris, +arguments[i]);
    // Chain the segments end to end
    var at = {};
    segs.forEach(function (s, n) { [k(s[0]), k(s[1])].forEach(function (q) { (at[q] = at[q] || []).push(n); }); });
    var used = {}, chains = [];
    segs.forEach(function (s, n) {
        if (used[n]) return;
        used[n] = 1;
        var chain = [s[0], s[1]];
        for (var dir = 0; dir < 2; dir++) {
            var guard = 0;
            while (guard++ < 10000) {
                var end = dir ? chain[0] : chain[chain.length - 1];
                var next = (at[k(end)] || []).filter(function (m) { return !used[m]; })[0];
                if (next === undefined) break;
                used[next] = 1;
                var t = segs[next], o = k(t[0]) == k(end) ? t[1] : t[0];
                if (dir) chain.unshift(o); else chain.push(o);
            }
        }
        chains.push(chain);
    });
    print("angle " + arguments[i] + ": " + chains.length + " chains");
    chains.forEach(function (c) {
        if (c[0][0] > c[c.length - 1][0]) c.reverse();
        print("  " + c.map(function (q) { return "(" + q[0].toFixed(4) + " " + q[1].toFixed(4) + ")"; }).join(" "));
    });
}
