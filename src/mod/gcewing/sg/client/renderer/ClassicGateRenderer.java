//------------------------------------------------------------------------------------------------
//
//   SG Craft - Stargate renderer for gates drawn without a model
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.client.renderer;

import gcewing.sg.tileentity.SGBaseTE;
import gcewing.sg.util.GateShape;
import gcewing.sg.util.GateType;

import static org.lwjgl.opengl.GL11.*;

// Draws a Milky Way or Pegasus gate from shapes of its own, on the gate type's stargate texture,
// as SGCraft always has: a fixed outer ring, an inner ring of symbols, and chevrons built up from
// blocks. Used when the gate type's model (see MilkyWayGateRenderer, PegasusGateRenderer) can't be
// loaded.
class ClassicGateRenderer extends GateRenderer {

    // Ring and event horizon sizes and detail come from the gate's GateShape
    final static double ringOverlap = 1/64.0;
    final static double ringZOffset = 0.0001;
    // How far a gate's chevrons stand proud of the ring's face
    final static double chevronDepth = 1/16.0;
    final static int textureTilesWide = 32;
    final static int textureTilesHigh = 2;
    final static double textureScaleU = 1.0/(textureTilesWide * 16);
    final static double textureScaleV = 1.0/(textureTilesHigh * 16);
    
    final static double ringSymbolTextureLength = 512.0; //27 * 8;
    final static double ringSymbolTextureHeight = 16.0; //12;
    // Where (texels along the symbol strip) the divider before the strip's first glyph is; it
    // falls just before the strip starts
    final static double ringGlyphStripStart = -0.41;
    // How far to turn a Milky Way gate's symbol ring so that at a ring angle of a whole number of
    // glyphs (SGBaseTE.ringGlyphAngle), that glyph is centred at the top. The strip runs clockwise
    // round the ring from the right.
    final static double ringGlyphRotation = 90 - 360 * (1
            - (ringGlyphStripStart + ringSymbolTextureLength / SGBaseTE.numRingGlyphs / 2) / ringSymbolTextureLength);

    double u0, v0;

    @Override
    void renderRings(SGBaseTE te, GateShape shape, float partialTicks) {
        // The rings are polygons, and the inner one turns, so the outer one overlaps it enough
        // to cover the corners of the inner one's polygon at any angle
        renderRing(shape, shape.ringMidRadius - ringOverlap * shape.detailScale, shape.ringOuterRadius, shape.ringDepth, RingType.Outer, ringZOffset * shape.detailScale);
        renderInnerRing(te, shape, partialTicks);
    }

    @Override
    boolean chevronEngaged(SGBaseTE te, int i, int j, float a, float partialTicks) {
        return PegasusScreens.chevronEngaged(te, i, j, a, partialTicks);
    }

    @Override
    void renderChevron(SGBaseTE te, GateShape shape, int i, boolean engaged, double slide) {
        // A Pegasus gate's top chevron is like the others, and stays put as it locks
        GateType type = te.getGateType();
        boolean master = i == 4 && type == GateType.MILKY_WAY;
        gateChevron(new GateChevron(shape, master ? masterChevronScale : 1, type == GateType.PEGASUS), engaged,
                master ? masterChevronTravel * shape.detailScale * slide : 0);
    }

    // A Milky Way gate's symbol ring turns to bring each symbol to the top. A Pegasus gate's ring of
    // glyph screens stays still and blank, and lights up as it dials (see renderPegasusGlyphs).
    void renderInnerRing(SGBaseTE te, GateShape shape, float partialTicks) {
        boolean pegasus = te.getGateType() == GateType.PEGASUS;
        double ringAngle = pegasus ? 0 : te.interpolatedRingAngle(partialTicks);
        // As on the show, a Milky Way gate's symbol ring turns inside a trim ring that doesn't
        double trim = pegasus ? 0 : innerTrimWidth * shape.detailScale;
        glPushMatrix();
        glRotatef((float)(ringAngle + (pegasus ? SGBaseTE.ringSymbolAngle / 2 : ringGlyphRotation)), 0, 0, 1);
        renderRing(shape, shape.ringInnerRadius + trim, shape.ringMidRadius, shape.ringDepth, RingType.Inner, 0);
        glPopMatrix();
        if (pegasus) {
            renderPegasusScreens(te, shape);
        }
        if (trim > 0) {
            // It overlaps the symbol ring, as the outer ring does, to cover the corners of the
            // symbol ring's polygon at any angle
            renderTrimRing(shape, shape.ringInnerRadius, shape.ringInnerRadius + trim + ringOverlap * shape.detailScale);
        }
        if (pegasus) {
            renderPegasusGlyphs(te, shape, partialTicks);
        }
    }

    // How wide (blocks, on a standard gate) a Milky Way gate's trim ring is, round the inside of
    // its symbol ring
    final static double innerTrimWidth = 1 / 16.0;

    // Draws the trim ring round the inside of a Milky Way gate's symbol ring, between the given
    // radii, with the ridged edge pattern from the bottom of the ring face tile
    void renderTrimRing(GateShape shape, double r1, double r2) {
        int n = shape.ringSegments;
        double[] c = shape.ringCos, s = shape.ringSin;
        // Just in front of the symbol ring where they overlap
        double z = shape.ringDepth / 2 + ringZOffset * shape.detailScale;
        int k = n / GateShape.BASE_RING_SEGMENTS;
        double tw = 16.0 / k;
        glBegin(GL_QUADS);
        for (int i = 0; i < n; i++) {
            double ta = (i % k) * tw, tb = ta + tw;
            // Its inside, facing into the opening
            selectTile(TextureIndex.RING);
            glNormal3d(-c[i], -s[i], 0);
            vertex(r1 * c[i], r1 * s[i], -z, ta, 0);
            vertex(r1 * c[i], r1 * s[i], z, ta, 16);
            vertex(r1 * c[i + 1], r1 * s[i + 1], z, tb, 16);
            vertex(r1 * c[i + 1], r1 * s[i + 1], -z, tb, 0);
            // Back and front
            selectTile(TextureIndex.RING_FACE);
            for (int side = -1; side <= 1; side += 2) {
                glNormal3f(0, 0, side);
                double[][] p = {{r1 * c[i], r1 * s[i]}, {r2 * c[i], r2 * s[i]},
                        {r2 * c[i + 1], r2 * s[i + 1]}, {r1 * c[i + 1], r1 * s[i + 1]}};
                double[][] uv = {{ta, 16}, {ta, 13}, {tb, 13}, {tb, 16}};
                for (int m = 0; m < 4; m++) {
                    int j = side > 0 ? m : 3 - m;
                    vertex(p[j][0], p[j][1], side * z, uv[j][0], uv[j][1]);
                }
            }
        }
        glEnd();
    }

    // The bottom of the symbol strip, below this, is the ring's bevelled edge, which never lights
    final static double pegasusGlyphBandV = 11;
    // The colour of the symbol strip behind the glyphs, which a blank glyph screen shows
    final static double[] pegasusScreenColour = {0.094, 0.12, 0.15};

    // The plain metal of a Pegasus gate's symbol ring: its dividers between screens, and its
    // inner edge
    final static double[] pegasusMetalColour = {0.36, 0.38, 0.42};
    final static double[] pegasusEdgeColour = {0.22, 0.23, 0.26};
    // How much of each screen's width (each side) a divider between screens takes, and how far
    // (blocks, on a standard gate) the dividers stand out of the ring's face
    final static double pegasusDividerWidth = 0.09;
    final static double pegasusDividerHeight = 0.012;

    // Draws a Pegasus gate's symbol ring as on the show: a ring of separate dark screens, one under
    // each chevron and the rest spread evenly between them (see PegasusScreens), with a raised
    // metal divider between each pair, inside a plain metal inner edge. The screens show nothing
    // until lit (see renderPegasusGlyphs). Drawn over the symbol ring's texture, stopping short of
    // the outer ring where it overlaps.
    void renderPegasusScreens(SGBaseTE te, GateShape shape) {
        PegasusScreens screens = PegasusScreens.of(te);
        double r1 = shape.ringInnerRadius, r2 = shape.ringMidRadius - ringOverlap * shape.detailScale;
        double rb = shape.ringMidRadius - (shape.ringMidRadius - r1) * pegasusGlyphBandV / ringSymbolTextureHeight;
        double k = shape.detailScale;
        double z = shape.ringDepth / 2 + 0.001 * k, zt = z + pegasusDividerHeight * k;
        int n = shape.ringSegments;
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_QUADS);
        for (int s = 0; s < screens.count(); s++) {
            double centre = screens.angles[s], half = screens.widths[s] / 2;
            double gap = screens.widths[s] * pegasusDividerWidth;
            // The screen
            glColor3d(pegasusScreenColour[0], pegasusScreenColour[1], pegasusScreenColour[2]);
            ringBand(rb, r2, centre - half + gap, centre + half - gap, z, n);
            // Its inner edge
            glColor3d(pegasusEdgeColour[0], pegasusEdgeColour[1], pegasusEdgeColour[2]);
            ringBand(r1, rb, centre - half, centre + half, z, n);
            // The divider after it, standing out of the ring's face, with its sides. Its floor
            // covers the symbol ring's texture, and its sides are drawn facing both ways, so that
            // nothing of the texture shows past it from any angle.
            double d0 = centre + half - gap, d1 = centre + half + gap;
            glColor3d(pegasusMetalColour[0], pegasusMetalColour[1], pegasusMetalColour[2]);
            ringBand(rb, r2, d0, d1, z, n);
            ringBand(rb, r2, d0, d1, zt, n);
            for (double d : new double[] {d0, d1}) {
                double a = Math.toRadians(d), side = d == d0 ? -1 : 1;
                double pr = polygonRadius(1, d, n);
                glNormal3d(-Math.sin(a) * side, Math.cos(a) * side, 0);
                double[][] p = {{rb * pr, z}, {r2 * pr, z}, {r2 * pr, zt}, {rb * pr, zt}};
                for (int m = 0; m < 8; m++) {
                    double[] q = p[m < 4 ? m : 7 - m];
                    glVertex3d(q[0] * Math.cos(a), q[0] * Math.sin(a), q[1]);
                }
            }
        }
        glEnd();
        glColor3d(1, 1, 1);
        glEnable(GL_TEXTURE_2D);
    }

    // A flat band of the ring's face between the given radii and angles (degrees), at depth z.
    // Its edges follow the ring's own polygon of the given number of segments rather than chords
    // of their own, so they meet the ring's edges exactly: chords cut across the polygon would
    // leave slivers of the symbol ring's texture showing, which a big gate makes plain to see.
    // Call inside glBegin(GL_QUADS).
    static void ringBand(double r1, double r2, double from, double to, double z, int segments) {
        glNormal3f(0, 0, 1);
        double step = 360.0 / segments;
        double a0 = from;
        while (a0 < to - 1e-9) {
            // To the next corner of the polygon, or the band's end
            double a1 = Math.min(to, (Math.floor(a0 / step + 1e-9) + 1) * step);
            double p0 = polygonRadius(1, a0, segments), p1 = polygonRadius(1, a1, segments);
            double c0 = Math.cos(Math.toRadians(a0)), s0 = Math.sin(Math.toRadians(a0));
            double c1 = Math.cos(Math.toRadians(a1)), s1 = Math.sin(Math.toRadians(a1));
            glVertex3d(r1 * p0 * c0, r1 * p0 * s0, z);
            glVertex3d(r2 * p0 * c0, r2 * p0 * s0, z);
            glVertex3d(r2 * p1 * c1, r2 * p1 * s1, z);
            glVertex3d(r1 * p1 * c1, r1 * p1 * s1, z);
            a0 = a1;
        }
    }

    // How far from the centre, at the given angle (degrees), the edge of a ring polygon of the
    // given number of segments and corner radius r is
    static double polygonRadius(double r, double angle, int segments) {
        double step = 360.0 / segments;
        double mid = (Math.floor(angle / step + 1e-9) + 0.5) * step;
        return r * Math.cos(Math.toRadians(step / 2)) / Math.cos(Math.toRadians(angle - mid));
    }

    // Lights a Pegasus gate's glyph screens as it dials (see PegasusScreens.lit)
    void renderPegasusGlyphs(SGBaseTE te, GateShape shape, float partialTicks) {
        PegasusScreens screens = PegasusScreens.of(te);
        boolean[] lit = screens.lit(te, partialTicks);
        if (lit == null) {
            return;
        }
        int count = screens.count();

        // Lit glyphs glow regardless of the light around the gate. The glyph sheet is the glyphs
        // alone on black, added on top of the screens: once in their own colour, then twice more
        // in blue for the glow.
        bindTexture("textures/tileentity/" + textureFolder(te) + "/glyphs.png");
        beginGlow();
        double k = PegasusGateRenderer.pegasusGlowStrength;
        double[] glow = PegasusGateRenderer.pegasusGlowColour;
        for (int layer = 1; layer <= 3; layer++) {
            if (layer == 1) {
                glColor4f(1, 1, 1, 1);
            } else {
                glColor4d(glow[0] * k, glow[1] * k, glow[2] * k, 1);
            }
            for (int s = 0; s < count; s++) {
                if (lit[s]) {
                    litGlyph(shape, screens.angles[s], screens.widths[s] * (1 - 2 * pegasusDividerWidth), PegasusScreens.glyphAt(screens.angles[s]),
                            pegasusGlyphBandV, layer);
                }
            }
        }
        endGlow(te);
        bindStargateTexture(te);
    }

    // The glyph sheet's glyphs are in cells of a square grid this many wide
    final static int pegasusGlyphGrid = 6;

    // Draws a glyph from the glyph sheet on the screen centred at the given angle (degrees) and
    // the given width, over the symbol strip down to the given row of it, in the current colour
    void litGlyph(GateShape shape, double centre, double width, int glyph, double bandV, int layer) {
        double cell = 1.0 / pegasusGlyphGrid;
        double u0 = (glyph % pegasusGlyphGrid) * cell, v0 = (glyph / pegasusGlyphGrid) * cell;
        double r1 = shape.ringInnerRadius, r2 = shape.ringMidRadius;
        double rb = r2 - (r2 - r1) * bandV / ringSymbolTextureHeight;
        double z = shape.ringDepth / 2 + 0.002 * layer * shape.detailScale, zb = z;
        int steps = 4;
        glBegin(GL_QUADS);
        glNormal3f(0, 0, 1);
        for (int s = 0; s < steps; s++) {
            double a0 = Math.toRadians(centre - width / 2 + width * s / steps);
            double a1 = Math.toRadians(centre - width / 2 + width * (s + 1) / steps);
            // Upright at the top of the ring: the glyph's left is anticlockwise, its top outwards
            double ua = u0 + cell * (1 - (double) s / steps), ub = u0 + cell * (1 - (double) (s + 1) / steps);
            glTexCoord2d(ua, v0 + cell);
            glVertex3d(rb * Math.cos(a0), rb * Math.sin(a0), zb);
            glTexCoord2d(ua, v0);
            glVertex3d(r2 * Math.cos(a0), r2 * Math.sin(a0), z);
            glTexCoord2d(ub, v0);
            glVertex3d(r2 * Math.cos(a1), r2 * Math.sin(a1), z);
            glTexCoord2d(ub, v0 + cell);
            glVertex3d(rb * Math.cos(a1), rb * Math.sin(a1), zb);
        }
        glEnd();
    }

    void renderRing(GateShape shape, double r1, double r2, double depth, RingType type, double dz) {
        double z = depth / 2 + dz;
        double u = 0, du = 0, dv = 0;
        int n = shape.ringSegments;
        double[] c = shape.ringCos, s = shape.ringSin;
        // Ring and face tiles each span one segment of a 32-segment ring, so with more segments
        // each segment shows its share of a tile rather than a squashed whole one
        int k = n / GateShape.BASE_RING_SEGMENTS;
        double tw = 16.0 / k;
        double symbolWidth = ringSymbolTextureLength / n;
        glBegin(GL_QUADS);
        for (int i = 0; i < n; i++) {
            double ta = (i % k) * tw, tb = ta + tw;
            selectTile(TextureIndex.RING);
            switch (type) {
                case Outer:
                    glNormal3d(c[i], s[i], 0);
                    vertex(r2 * c[i], r2 * s[i], z, ta, 0);
                    vertex(r2 * c[i], r2 * s[i], -z, ta, 16);
                    vertex(r2 * c[i + 1], r2 * s[i + 1], -z, tb, 16);
                    vertex(r2 * c[i + 1], r2 * s[i + 1], z, tb, 0);
                    break;
                case Inner:
                    glNormal3d(-c[i], -s[i], 0);
                    vertex(r1 * c[i], r1 * s[i], -z, ta, 0);
                    vertex(r1 * c[i], r1 * s[i], z, ta, 16);
                    vertex(r1 * c[i + 1], r1 * s[i + 1], z, tb, 16);
                    vertex(r1 * c[i + 1], r1 * s[i + 1], -z, tb, 0);
                    break;
            }
            // Back
            glNormal3f(0, 0, -1);
            vertex(r1*c[i],   r1*s[i],   -z,   ta, 16);
            vertex(r1*c[i+1], r1*s[i+1], -z,   tb, 16);
            vertex(r2*c[i+1], r2*s[i+1], -z,   tb,  0);
            vertex(r2*c[i],   r2*s[i],   -z,   ta,  0);
            // Front
            glNormal3f(0, 0, 1);
            switch (type) {
                case Outer:
                    selectTile(TextureIndex.RING_FACE);
                    u = 16 - tb;
                    du = tw;
                    dv = 16;
                    break;
                case Inner:
                    selectTile(TextureIndex.RING_SYMBOL);
                    u = ringSymbolTextureLength - (i + 1) * symbolWidth;
                    du = symbolWidth;
                    dv = ringSymbolTextureHeight;
                    break;
            }
            vertex(r1*c[i],   r1*s[i],    z,   u+du, dv);
            vertex(r2*c[i],   r2*s[i],    z,   u+du,  0);
            vertex(r2*c[i+1], r2*s[i+1],  z,   u,     0);
            vertex(r1*c[i+1], r1*s[i+1],  z,   u,    dv);
        }

        glEnd();
    }
    
    // How much wider the top chevron of a Milky Way gate is than the others
    final static double masterChevronScale = 1.15;

    // How far down the symbols on the symbol ring the V of a chevron reaches, as on the show:
    // halfway on a Milky Way gate, under a third of the way down the screens on a Pegasus gate
    final static double milkyWayChevronReach = 0.5;
    final static double pegasusChevronReach = 0.35;

    // The shape of a gate's chevron, as on the show. It is two parts: a light, a solid block
    // narrowing inwards from the ring's outer edge, standing a little proud of a metal outer
    // layer. The outer layer is a V whose arms run down either side of the light and join below it,
    // reaching down onto the symbols, with a row of slatted lights set in beside each arm. Locking
    // a symbol, a Milky Way gate's top chevron's outer layer slides down and back up, leaving the
    // light where it is. A Pegasus gate's chevrons are the same shape in its own colours. Distances
    // are outwards from the gate's centre (x) and either side of its middle (y).
    static class GateChevron {
        final boolean pegasus;
        final double width, top, lightTopX, lightBottom, slatBottom, bottom;
        final double wingTipX, wingTipY, wingTipThickness, wingBottom;
        final double armZ, lightZ, slatZ, backZ;

        GateChevron(GateShape shape, double scale, boolean pegasus) {
            this.pegasus = pegasus;
            double ring = shape.ringOuterRadius - shape.ringInnerRadius;
            double k = shape.detailScale;
            width = ring * 0.8 * scale;
            top = ringEdgeAt(shape, width / 2);
            double length = ring * 0.68;
            lightBottom = top - length * 0.64;
            slatBottom = top - length * 0.74;
            // The V reaches down onto the symbols: on a Milky Way gate, those inside its trim ring;
            // on a Pegasus gate, its glyph screens, which stop short of the ring's inner edge
            double symbolsInner = pegasus
                    ? shape.ringMidRadius - (shape.ringMidRadius - shape.ringInnerRadius) * pegasusGlyphBandV / ringSymbolTextureHeight
                    : shape.ringInnerRadius + innerTrimWidth * k;
            double reach = pegasus ? pegasusChevronReach : milkyWayChevronReach;
            bottom = shape.ringMidRadius - (shape.ringMidRadius - symbolsInner) * reach;
            // The wings run out along the ring's outer edge from the top of the slatted lights,
            // sloping down into the ring's face
            wingTipY = width * 1.3;
            wingTipX = ringEdgeAt(shape, wingTipY);
            wingTipThickness = length * 0.05;
            wingBottom = top - length * 0.2;
            // Its depth goes with the ring's thickness rather than its size
            double kz = shape.depthScale();
            backZ = shape.ringDepth / 2;
            armZ = backZ + chevronDepth * kz;
            lightZ = backZ + chevronDepth * kz * 1.3;
            // The light's top is flush with the ring's outer edge
            lightTopX = ringEdgeAt(shape, lightTop());
            slatZ = backZ + chevronDepth * kz * 0.45;
        }

        // Half widths: of the light at its top and bottom; of the outer layer at its top, at the
        // bottom of the light, and at its bottom; and of the slatted lights at their outer edge,
        // at the top and bottom
        double lightTop() { return width * 0.2; }
        double lightBase() { return width * 0.07; }
        double armTop() { return width * 0.33; }
        double armMid() { return width * 0.24; }
        double armBase() { return width * 0.15; }
        double slatTop() { return width * 0.5; }
        double slatBase() { return width * 0.36; }
        // Where the inner edge of the slatted lights, along the outside of the arm, ends
        double slatInnerBase() {
            return armTop() + (armMid() - armTop()) * (top - slatBottom) / (top - lightBottom);
        }
        // Half the width of the outer edge of the arms, the given distance out from the centre
        double armOuterAt(double x) {
            return x >= lightBottom
                ? armTop() + (armMid() - armTop()) * (top - x) / (top - lightBottom)
                : armMid() + (armBase() - armMid()) * (lightBottom - x) / (lightBottom - bottom);
        }
        // Half the width of the slatted lights' outer edge, the given distance out from the centre
        double slatOuterAt(double x) {
            return slatTop() + (slatBase() - slatTop()) * (top - x) / (top - slatBottom);
        }
    }

    // How bright an unlit chevron's light and slatted lights are
    final static double idleChevronLightBrightness = 0.45;
    final static double idlePegasusChevronLightBrightness = 0.25;
    final static double idleChevronSlatBrightness = 0.2;

    // Draws a gate's chevron (see GateChevron), its lights lit if engaged, with its
    // front outer layer moved the given distance down. As on the show, the outer layer is on both
    // faces of the ring, each with its own light; nothing of the chevron shows on the ring's outer
    // edge between them.
    void gateChevron(GateChevron c, boolean engaged, double drop) {
        glPushMatrix();
        glTranslated(-drop, 0, 0);
        chevronOuterLayer(c, engaged);
        glPopMatrix();
        chevronWings(c);

        // The back is the front mirrored through the ring, which turns its faces inside out
        glPushMatrix();
        glScaled(1, 1, -1);
        glFrontFace(GL_CW);
        chevronOuterLayer(c, engaged);
        chevronWings(c);
        glFrontFace(GL_CCW);
        glPopMatrix();

        chevronLight(c, engaged);
    }

    // Selects the metal a gate's chevron is made of. A Milky Way chevron uses the middle of the
    // chevron tile; that part of the Pegasus texture is plain flat grey, paler than the ring, so a
    // Pegasus chevron is made of the ring's own metal instead, as on the show.
    void selectChevronMetal(GateChevron c) {
        selectTile(c.pegasus ? TextureIndex.RING : TextureIndex.CHEVRON);
    }

    // Draws the parts of a gate's chevron that stay put while its outer layer moves, on
    // the front of the ring: the wings, and the sides of the recess the slatted lights are in
    void chevronWings(GateChevron c) {
        double at = c.armTop(), st = c.slatTop(), sb = c.slatBase(), si = c.slatInnerBase();
        glColor3d(1, 1, 1);
        selectChevronMetal(c);
        glBegin(GL_QUADS);
        // The sides and top of the slatted lights' recess
        for (int side = -1; side <= 1; side += 2) {
            quadFacing(new double[][] {{c.top, side * st, c.slatZ}, {c.slatBottom, side * sb, c.slatZ},
                    {c.slatBottom, side * sb, c.backZ}, {c.top, side * st, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 0, side, 0);
            quadFacing(new double[][] {{c.slatBottom, side * si, c.slatZ}, {c.slatBottom, side * sb, c.slatZ},
                    {c.slatBottom, side * sb, c.backZ}, {c.slatBottom, side * si, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, -1, 0, 0);
            quadFacing(new double[][] {{c.top, side * at, c.slatZ}, {c.top, side * st, c.slatZ},
                    {c.top, side * st, c.backZ}, {c.top, side * at, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 1, 0, 0);
        }
        // The wings at the top: slabs as high as the arms, running out along the ring's outer edge
        // from against the arm, over the top of the slatted lights, to a point
        double wy = c.wingTipY, wx = c.wingTipX, wb = c.wingBottom, sw = c.armOuterAt(wb);
        double tipZ = c.armZ;
        for (int side = -1; side <= 1; side += 2) {
            quadFacing(new double[][] {{wx, side * wy, tipZ}, {wx - c.wingTipThickness, side * wy, tipZ},
                    {wx - c.wingTipThickness, side * wy, c.backZ}, {wx, side * wy, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 0, side, 0);
            double[][] wing = {{c.top, side * at, c.armZ}, {wx, side * wy, tipZ},
                    {wx - c.wingTipThickness, side * wy, tipZ}, {wb, side * sw, c.armZ}};
            double[] n = faceNormal(wing[0], wing[1], wing[3]);
            quadFacing(wing, new double[][] {{6, 4}, {10, 4}, {10, 6}, {6, 12}}, n[0], n[1], n[2]);
            // Its edge along the ring's outer edge, its lower edge, and its inner edge against the arm,
            // which shows when the top chevron's arm slides down
            quadFacing(new double[][] {{c.top, side * at, c.armZ}, {wx, side * wy, tipZ},
                    {wx, side * wy, c.backZ}, {c.top, side * at, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 1, 0, 0);
            quadFacing(new double[][] {{wb, side * sw, c.armZ}, {wx - c.wingTipThickness, side * wy, tipZ},
                    {wx - c.wingTipThickness, side * wy, c.backZ}, {wb, side * sw, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, -1, 0, 0);
            quadFacing(new double[][] {{c.top, side * at, c.armZ}, {wb, side * sw, c.armZ},
                    {wb, side * sw, c.backZ}, {c.top, side * at, c.backZ}},
                    new double[][] {{6, 8}, {10, 8}, {10, 10}, {6, 10}}, 0, -side, 0);
        }
        glEnd();
    }

    // Draws the moving part of a gate chevron's outer layer, on the front of the ring:
    // the V and the slatted lights beside it
    void chevronOuterLayer(GateChevron c, boolean engaged) {
        double lt = c.lightTop(), lb = c.lightBase();
        double at = c.armTop(), am = c.armMid(), ab = c.armBase();
        double sb = c.slatBase(), si = c.slatInnerBase();

        // The V, in the plain middle of the chevron tile
        glColor3d(1, 1, 1);
        selectChevronMetal(c);
        glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) {
            prism(new double[] {c.top, c.top, c.lightBottom, c.lightBottom},
                    new double[] {side * lt, side * at, side * am, side * lb}, c.armZ, c.backZ, 6, 10);
        }
        prism(new double[] {c.lightBottom, c.lightBottom, c.bottom, c.bottom},
                new double[] {-am, am, ab, -ab}, c.armZ, c.backZ, 6, 10);
        glEnd();

        // The slatted lights: a row of bars stepping down the recess beside each arm, below the
        // wing, standing out of the recess's dark floor. Unlit they are plain metal; lit, their
        // fronts glow orange whatever the light around the gate.
        double wb = c.wingBottom, sw = c.slatOuterAt(wb);
        double barZ = c.slatZ + (c.armZ - c.slatZ) * 0.75;
        double pitch = (wb - c.slatBottom) / chevronSlats;
        double floor = idleChevronSlatBrightness;
        glColor3d(floor, floor, floor);
        glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) {
            quadFacing(new double[][] {{wb, side * c.armOuterAt(wb), c.slatZ}, {wb, side * sw, c.slatZ},
                    {c.slatBottom, side * sb, c.slatZ}, {c.slatBottom, side * si, c.slatZ}},
                    new double[][] {{6, 4}, {10, 4}, {10, 15}, {6, 15}}, 0, 0, 1);
        }
        glColor3d(1, 1, 1);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < chevronSlats; i++) {
                double xt = wb - (i + 0.2) * pitch, xb = xt - pitch * 0.6;
                // Its top and bottom
                quadFacing(new double[][] {{xt, side * c.armOuterAt(xt), barZ}, {xt, side * c.slatOuterAt(xt), barZ},
                        {xt, side * c.slatOuterAt(xt), c.slatZ}, {xt, side * c.armOuterAt(xt), c.slatZ}},
                        new double[][] {{6, 8}, {10, 8}, {10, 9}, {6, 9}}, 1, 0, 0);
                quadFacing(new double[][] {{xb, side * c.armOuterAt(xb), barZ}, {xb, side * c.slatOuterAt(xb), barZ},
                        {xb, side * c.slatOuterAt(xb), c.slatZ}, {xb, side * c.armOuterAt(xb), c.slatZ}},
                        new double[][] {{6, 8}, {10, 8}, {10, 9}, {6, 9}}, -1, 0, 0);
            }
        }
        glEnd();

        if (engaged) {
            selectTile(TextureIndex.CHEVRON_LIT);
            glDisable(GL_LIGHTING);
            setLightingDisabled(true);
        }
        glBegin(GL_QUADS);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < chevronSlats; i++) {
                double xt = wb - (i + 0.2) * pitch, xb = xt - pitch * 0.6;
                // The brightest band of the light texture when lit (orange on a Milky Way gate,
                // pale blue on a Pegasus gate), plain metal otherwise
                double vLit = c.pegasus ? 11 : 7;
                double u0 = engaged ? 0 : 6, u1 = engaged ? 16 : 10, v0 = engaged ? vLit : 6, v1 = engaged ? vLit + 2 : 10;
                quadFacing(new double[][] {{xt, side * c.armOuterAt(xt), barZ}, {xt, side * c.slatOuterAt(xt), barZ},
                        {xb, side * c.slatOuterAt(xb), barZ}, {xb, side * c.armOuterAt(xb), barZ}},
                        new double[][] {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}}, 0, 0, 1);
            }
        }
        glEnd();
        if (engaged) {
            glEnable(GL_LIGHTING);
            setLightingDisabled(false);
        }
    }

    // How many slatted lights step down each side of a gate's chevron
    final static int chevronSlats = 6;

    // Draws a gate chevron's light: a block standing out of each face of the ring,
    // its top flush with the ring's outer edge. It glows when lit whatever the light around the
    // gate. On a Milky Way gate the top of the light texture is its brightest band, and an unlit
    // light uses the dark red lower part. On a Pegasus gate the bottom is the brightest, and an
    // unlit light uses the deeper blue top, darkened so it looks dark like the ring.
    void chevronLight(GateChevron c, boolean engaged) {
        double lt = c.lightTop(), lb = c.lightBase();
        double x0 = c.lightTopX, x1 = c.lightBottom, z = c.lightZ;
        if (engaged) {
            glDisable(GL_LIGHTING);
            setLightingDisabled(true);
        } else {
            double idle = c.pegasus ? idlePegasusChevronLightBrightness : idleChevronLightBrightness;
            glColor3d(idle, idle, idle);
        }
        selectTile(TextureIndex.CHEVRON_LIT);
        double v0 = c.pegasus ? (engaged ? 8 : 0) : (engaged ? 2 : 9);
        double v1 = c.pegasus ? (engaged ? 16 : 8) : (engaged ? 10 : 16);
        glBegin(GL_QUADS);
        for (int face = -1; face <= 1; face += 2) {
            quadFacing(new double[][] {{x0, lt, face * z}, {x1, lb, face * z},
                    {x1, -lb, face * z}, {x0, -lt, face * z}},
                    new double[][] {{16, v0}, {12, v1}, {4, v1}, {0, v0}}, 0, 0, face);
        }
        glEnd();
        glColor3d(1, 1, 1);
        if (engaged) {
            glEnable(GL_LIGHTING);
            setLightingDisabled(false);
        }

        // Its inner end, sides and top, where it stands out of the ring's face, are the chevron's
        // metal: a light's texture stretched along them shows as a smeared streak
        selectChevronMetal(c);
        double[][] metal = {{6, 8}, {10, 8}, {10, 10}, {6, 10}};
        glBegin(GL_QUADS);
        for (int face = -1; face <= 1; face += 2) {
            double zf = face * z, zb = face * c.backZ;
            quadFacing(new double[][] {{x1, -lb, zf}, {x1, lb, zf}, {x1, lb, zb}, {x1, -lb, zb}},
                    metal, -1, 0, 0);
            for (int side = -1; side <= 1; side += 2) {
                quadFacing(new double[][] {{x0, side * lt, zf}, {x1, side * lb, zf},
                        {x1, side * lb, zb}, {x0, side * lt, zb}},
                        metal, 0, side, 0);
            }
            quadFacing(new double[][] {{x0, -lt, zf}, {x0, lt, zf}, {x0, lt, zb}, {x0, -lt, zb}},
                    metal, 1, 0, 0);
        }
        glEnd();
    }

    // The unit normal of the plane through three points, on the side facing the viewer (+z)
    static double[] faceNormal(double[] a, double[] b, double[] c) {
        double ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
        double bx = c[0] - a[0], by = c[1] - a[1], bz = c[2] - a[2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz) * (nz < 0 ? -1 : 1);
        return new double[] {nx / len, ny / len, nz / len};
    }

    // Draws a quad with the given corners and texture coordinates (within the current tile),
    // facing the given way: its corners are taken in whichever order makes it face that way.
    // Call inside glBegin(GL_QUADS).
    void quadFacing(double[][] p, double[][] uv, double nx, double ny, double nz) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        double bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
        double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        boolean reverse = cx * nx + cy * ny + cz * nz < 0;
        glNormal3d(nx, ny, nz);
        for (int n = 0; n < 4; n++) {
            int i = reverse ? 3 - n : n;
            vertex(p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1]);
        }
    }

    // How far (blocks, on a standard gate) the top chevron's outer layer slides down to lock a
    // symbol: a little further than on the show, so it shows at Minecraft's scale
    final static double masterChevronTravel = 0.09;

    // How far out from the gate's centre the ring's outer edge is, the given distance either side
    // of the middle of one of its sides
    static double ringEdgeAt(GateShape shape, double y) {
        double edge = shape.ringOuterRadius * Math.cos(Math.PI / shape.ringSegments);
        return Math.sqrt(edge * edge - y * y);
    }

    // A block standing on the gate's face, outlined by the given four corners (in either winding)
    // and running from depth zb out to depth zf, textured with the columns u0 to u1 of the current
    // tile. Call inside glBegin(GL_QUADS).
    void prism(double[] x, double[] y, double zf, double zb, double u0, double u1) {
        double area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += x[i] * y[j] - x[j] * y[i];
        }
        int d = area > 0 ? 1 : -1; // go round anticlockwise, so the front faces outwards
        glNormal3d(0, 0, 1);
        for (int n = 0; n < 4; n++) {
            int i = ((d * n) % 4 + 4) % 4;
            vertex(x[i], y[i], zf, n == 1 || n == 2 ? u1 : u0, n >= 2 ? 15 : 4);
        }
        for (int n = 0; n < 4; n++) {
            int i = ((d * n) % 4 + 4) % 4, j = ((d * (n + 1)) % 4 + 4) % 4;
            double ex = x[j] - x[i], ey = y[j] - y[i], len = Math.hypot(ex, ey);
            glNormal3d(ey / len, -ex / len, 0);
            vertex(x[i], y[i], zf, u0, 4);
            vertex(x[i], y[i], zb, u0, 6);
            vertex(x[j], y[j], zb, u1, 6);
            vertex(x[j], y[j], zf, u1, 4);
        }
    }

    void selectTile(TextureIndex index) {
        u0 = (index.index % textureTilesWide) * (textureScaleU * 16);
        v0 = (index.index / textureTilesWide) * (textureScaleV * 16);
    }
    
    void vertex(double x, double y, double z, double u, double v) {
        glTexCoord2d(u0 + u * textureScaleU, v0 + v * textureScaleV);
        glVertex3d(x, y, z);
    }

}

enum TextureIndex {
    RING_FACE(1),
    RING(0),
    RING_SYMBOL(32),
    CHEVRON(3),
    CHEVRON_LIT(2);

    public final int index;

    TextureIndex(int index) {
        this.index = index;
    }
}

enum RingType {
    Inner, Outer
}
