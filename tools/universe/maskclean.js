// Cleans the chevron light mask measured by chevmask.js and makes its glow. Keeps the lights the
// chevron is made of - its triangle and its bars - and drops any stray specks. Then writes the
// glow: the lights spread out and softened, for a halo round them.
// jjs maskclean.js -- <raw mask png> <lights png> <glow png>
var BufferedImage = Java.type("java.awt.image.BufferedImage"), ImageIO = Java.type("javax.imageio.ImageIO"), File = Java.type("java.io.File");
var src = ImageIO.read(new File(arguments[0])), W = src.getWidth(), H = src.getHeight();
var BAR = 300, ON = 100;
var v = new Float64Array(W * H);
for (var y = 0; y < H; y++) for (var x = 0; x < W; x++) v[y * W + x] = src.getRGB(x, y) & 0xff;
// Each lit patch, with anything touching it that is at all lit
var lab = new Int32Array(W * H), keep = [false], n = 0;
for (var i = 0; i < W * H; i++) {
    if (lab[i] || v[i] < ON) continue;
    n++;
    var stack = [i], members = [], maxx = 0;
    lab[i] = n;
    while (stack.length) {
        var p = stack.pop(), px = p % W, py = (p - px) / W;
        members.push(p); maxx = Math.max(maxx, px);
        for (var dy = -1; dy <= 1; dy++) for (var dx = -1; dx <= 1; dx++) {
            var qx = px + dx, qy = py + dy;
            if (qx < 0 || qy < 0 || qx >= W || qy >= H) continue;
            var q = qy * W + qx;
            if (!lab[q] && v[q] > 0) { lab[q] = n; stack.push(q); }
        }
    }
    var lit = members.filter(function (m) { return v[m] >= ON; }).length;
    keep.push(lit >= BAR);
}
var out = new Float64Array(W * H);
for (var i = 0; i < W * H; i++) out[i] = lab[i] && keep[lab[i]] ? v[i] : 0;
function write(a, name) {
    var img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    for (var i = 0; i < W * H; i++) {
        var g = Math.max(0, Math.min(255, Math.round(a[i])));
        img.setRGB(i % W, Math.floor(i / W), (g << 16) | (g << 8) | g);
    }
    ImageIO.write(img, "png", new File(name));
}
// The lights, their edges softened a little
function blur(a, sigma) {
    var r = Math.ceil(sigma * 3), k = [], sum = 0;
    for (var j = -r; j <= r; j++) { var w = Math.exp(-j * j / (2 * sigma * sigma)); k.push(w); sum += w; }
    k = k.map(function (w) { return w / sum; });
    var t = new Float64Array(W * H), o = new Float64Array(W * H);
    for (var y = 0; y < H; y++) for (var x = 0; x < W; x++) {
        var s = 0; for (var j = -r; j <= r; j++) { var xx = Math.min(W - 1, Math.max(0, x + j)); s += a[y * W + xx] * k[j + r]; } t[y * W + x] = s;
    }
    for (var y = 0; y < H; y++) for (var x = 0; x < W; x++) {
        var s = 0; for (var j = -r; j <= r; j++) { var yy = Math.min(H - 1, Math.max(0, y + j)); s += t[yy * W + x] * k[j + r]; } o[y * W + x] = s;
    }
    return o;
}
write(blur(out, 0.8), arguments[1]);
// The glow: spread well out, brought back up so its brightest is full
var glow = blur(out, 10), top = 0;
for (var i = 0; i < W * H; i++) top = Math.max(top, glow[i]);
for (var i = 0; i < W * H; i++) glow[i] = glow[i] / top * 255;
write(glow, arguments[2]);
print("patches " + n + ", kept " + keep.filter(function (k) { return k; }).length);
