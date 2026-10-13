// Renders the Milky Way gate reference model, or a region of it, straight on from the front (the
// side with the chevron lights, -z) or the back, lit from the upper left, optionally with its own
// textures, for looking at and measuring.
// jjs render.js -- <scratch> <fbx> <out.png> <size> [front|back] [cx cy halfWidth] [textures dir]
var file = arguments[1];
load(arguments[0] + "/fbxcore.js");
var outFile = arguments[2], S = +arguments[3], back = arguments[4] == "back";
var cx = arguments.length > 5 ? +arguments[5] : 0, cy = arguments.length > 6 ? +arguments[6] : 0;
var half = arguments.length > 7 ? +arguments[7] : 620;
var texDir = arguments.length > 8 ? arguments[8] : null;

var textures = {};
function texture(name) {
    if (!texDir) return null;
    var f = /^ChevronLight/.test(name) ? null : name == "Front" ? "Baked Stargate Front_BaseColor.png"
        : name == "Back" ? "Baked Stargate Back_BaseColor.png" : name + "_BaseColor.png";
    if (!f) return null;
    if (textures[f] === undefined) {
        var img = ImageIO.read(new File(texDir + "/" + f));
        textures[f] = {img: img, w: img.getWidth(), h: img.getHeight()};
    }
    return textures[f];
}

var zbuf = new DoubleArray(S * S), img = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
for (var i = 0; i < S * S; i++) zbuf[i] = -1e18;
var L = [-0.45, 0.55, 0.7], ll = Math.sqrt(L[0] * L[0] + L[1] * L[1] + L[2] * L[2]);
L = L.map(function (q) { return q / ll; });

prims.forEach(function (p) {
    var P = p.pos, I = p.idx, UV = p.uv, tex = texture(p.name), light = /^ChevronLight/.test(p.name);
    for (var t = 0; t < I.length; t += 3) {
        var sx = [], sy = [], sz = [], wv = [];
        for (var e = 0; e < 3; e++) {
            var k = I[t + e] * 3, x = P[k], y = P[k + 1], z = P[k + 2];
            // Seen from the front, +x is to the left; towards the viewer is up the depth
            var vx = back ? x : -x, depth = back ? z : -z;
            sx.push((vx - cx + half) / (2 * half) * S);
            sy.push((cy + half - y) / (2 * half) * S);
            sz.push(depth);
            wv.push([vx, y, depth]);
        }
        var ux = wv[1][0] - wv[0][0], uy = wv[1][1] - wv[0][1], uz = wv[1][2] - wv[0][2];
        var qx = wv[2][0] - wv[0][0], qy = wv[2][1] - wv[0][1], qz = wv[2][2] - wv[0][2];
        var nx = uy * qz - uz * qy, ny = uz * qx - ux * qz, nz = ux * qy - uy * qx, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-12) continue;
        nx /= nl; ny /= nl; nz /= nl;
        if (nz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        var shade = 0.25 + 0.75 * Math.max(0, nx * L[0] + ny * L[1] + nz * L[2]);
        var minx = Math.max(0, Math.floor(Math.min(sx[0], sx[1], sx[2]))), maxx = Math.min(S - 1, Math.ceil(Math.max(sx[0], sx[1], sx[2])));
        var miny = Math.max(0, Math.floor(Math.min(sy[0], sy[1], sy[2]))), maxy = Math.min(S - 1, Math.ceil(Math.max(sy[0], sy[1], sy[2])));
        if (minx > maxx || miny > maxy) continue;
        var den = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2]);
        if (Math.abs(den) < 1e-12) continue;
        for (var y = miny; y <= maxy; y++) for (var x = minx; x <= maxx; x++) {
            var X = x + 0.5, Y = y + 0.5;
            var w0 = ((sy[1] - sy[2]) * (X - sx[2]) + (sx[2] - sx[1]) * (Y - sy[2])) / den;
            var w1 = ((sy[2] - sy[0]) * (X - sx[2]) + (sx[0] - sx[2]) * (Y - sy[2])) / den;
            var w2 = 1 - w0 - w1;
            if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
            var z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2];
            if (z <= zbuf[y * S + x]) continue;
            zbuf[y * S + x] = z;
            var r = 200, g = 200, b = 200;
            if (light) { r = 255; g = 140; b = 40; }
            else if (tex && UV.length) {
                var u = w0 * UV[2 * t] + w1 * UV[2 * t + 2] + w2 * UV[2 * t + 4];
                var v = w0 * UV[2 * t + 1] + w1 * UV[2 * t + 3] + w2 * UV[2 * t + 5];
                var px = Math.floor((u - Math.floor(u)) * tex.w), py = Math.floor((1 - (v - Math.floor(v))) * tex.h);
                var c = tex.img.getRGB(Math.min(tex.w - 1, px), Math.min(tex.h - 1, py));
                r = (c >> 16) & 255; g = (c >> 8) & 255; b = c & 255;
            }
            img.setRGB(x, y, ((r * shade) | 0) << 16 | ((g * shade) | 0) << 8 | ((b * shade) | 0));
        }
    }
});
ImageIO.write(img, "png", new File(outFile));
print("wrote " + outFile);
