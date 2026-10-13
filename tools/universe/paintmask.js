// Turns the chevron's measured bronze paint (chevpaint.js) into its paint texture: our own bronze,
// each patch shaded as a rounded stud lit from above (paler at its top, darker at its bottom),
// opaque where it is painted and see-through elsewhere. Drops specks smaller than MIN pixels.
// jjs paintmask.js -- <raw mask png> <out png>
var BufferedImage = Java.type("java.awt.image.BufferedImage"), ImageIO = Java.type("javax.imageio.ImageIO"), File = Java.type("java.io.File");
var src = ImageIO.read(new File(arguments[0])), W = src.getWidth(), H = src.getHeight(), MIN = 60;
var v = new Float64Array(W * H);
for (var y = 0; y < H; y++) for (var x = 0; x < W; x++) v[y * W + x] = (src.getRGB(x, y) & 0xff) / 255;
var lab = new Int32Array(W * H), n = 0, parts = [null];
for (var i = 0; i < W * H; i++) {
    if (lab[i] || v[i] < 0.5) continue;
    n++;
    var stack = [i], members = [], miny = H, maxy = 0;
    lab[i] = n;
    while (stack.length) {
        var p = stack.pop(), px = p % W, py = (p - px) / W;
        members.push(p); miny = Math.min(miny, py); maxy = Math.max(maxy, py);
        for (var dy = -1; dy <= 1; dy++) for (var dx = -1; dx <= 1; dx++) {
            var qx = px + dx, qy = py + dy;
            if (qx < 0 || qy < 0 || qx >= W || qy >= H) continue;
            var q = qy * W + qx;
            if (!lab[q] && v[q] > 0) { lab[q] = n; stack.push(q); }
        }
    }
    parts.push({keep: members.length >= MIN, miny: miny, maxy: maxy});
}
var img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
var seed = 7;
function rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }
for (var y = 0; y < H; y++) for (var x = 0; x < W; x++) {
    var i = y * W + x, part = parts[lab[i]];
    if (!lab[i] || !part.keep) continue;
    // 0 at the stud's top, 1 at its bottom
    var t = part.maxy > part.miny ? (y - part.miny) / (part.maxy - part.miny) : 0.5;
    var light = 1.25 - 0.6 * t + (rnd() - 0.5) * 0.06;
    var r = Math.min(255, Math.round(170 * light)), g = Math.min(255, Math.round(118 * light)), b = Math.min(255, Math.round(66 * light));
    var a = Math.round(255 * Math.min(1, v[i]));
    img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
}
ImageIO.write(img, "png", new File(arguments[1]));
print("patches " + n + ", kept " + parts.filter(function (p) { return p && p.keep; }).length);
