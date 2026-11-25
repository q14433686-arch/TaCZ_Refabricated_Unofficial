package com.tacz.guns.test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Measure a hole in the visible ink before attributing its rim to the marker. Removing every
 * pixel near the etched reticle first can cut an intact one-pixel-wide rim in half. */
final class MarkerPixels {
    private MarkerPixels() {}

    record Ring(double cx, double cy, int rim, int changed, int distinct) {}
    private record Hole(int left, int top, int right, int bottom, BitSet rim) {
        double cx() { return (left + right) * .5; }
        double cy() { return (top + bottom) * .5; }
    }

    static boolean yellow(int rgb) {
        int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, b = rgb & 255;
        return r > 110 && g > 110 && Math.min(r, g) > 2 * b && Math.abs(r - g) < 48;
    }

    private static BitSet ink(BufferedImage frame, int x0, int y0, int size) {
        var result = new BitSet(size * size);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (yellow(frame.getRGB(x0 + x, y0 + y))) result.set(y * size + x);
        }
        return result;
    }

    // Flood four-connected non-yellow pixels. Only regions that cannot reach the edge are
    // holes; count bordering yellow pixels including the corners of the box.
    private static List<Hole> holes(BitSet ink, int size) {
        var result = new ArrayList<Hole>();
        var seen = (BitSet) ink.clone();
        int[] queue = new int[size * size];
        for (int start = seen.nextClearBit(0); start < queue.length; start = seen.nextClearBit(start + 1)) {
            int head = 0, tail = 0, left = size, right = -1, top = size, bottom = -1;
            var rim = new BitSet(queue.length);
            queue[tail++] = start;
            seen.set(start);
            while (head < tail) {
                int i = queue[head++], x = i % size, y = i / size;
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
                for (int yy = Math.max(0, y - 1); yy <= Math.min(size - 1, y + 1); yy++) {
                    for (int xx = Math.max(0, x - 1); xx <= Math.min(size - 1, x + 1); xx++) {
                        int j = yy * size + xx;
                        if (ink.get(j)) rim.set(j);
                        else if ((x == xx || y == yy) && !seen.get(j)) {
                            seen.set(j);
                            queue[tail++] = j;
                        }
                    }
                }
            }
            if (left > 0 && top > 0 && right < size - 1 && bottom < size - 1)
                result.add(new Hole(left, top, right, bottom, rim));
        }
        return result;
    }

    private static boolean nearInk(BitSet ink, int x, int y, int size) {
        for (int yy = Math.max(0, y - 1); yy <= Math.min(size - 1, y + 1); yy++)
            for (int xx = Math.max(0, x - 1); xx <= Math.min(size - 1, x + 1); xx++)
                if (ink.get(yy * size + xx)) return true;
        return false;
    }

    static Ring find(BufferedImage visible, BufferedImage hidden, int radius) {
        int size = 2 * radius + 1, x0 = visible.getWidth() / 2 - radius, y0 = visible.getHeight() / 2 - radius;
        var control = ink(hidden, x0, y0, size);
        var priorHoles = holes(control, size);
        Ring best = null;
        for (var hole : holes(ink(visible, x0, y0, size), size)) {
            if (priorHoles.stream().anyMatch(old -> Math.abs(old.cx() - hole.cx()) <= 1 && Math.abs(old.cy() - hole.cy()) <= 1)) continue;
            var added = (BitSet) hole.rim.clone();
            added.andNot(control);
            int distinct = 0;
            boolean left = false, right = false, top = false, bottom = false;
            for (int i = added.nextSetBit(0); i >= 0; i = added.nextSetBit(i + 1)) {
                int x = i % size, y = i / size;
                left |= x < hole.left; right |= x > hole.right;
                top |= y < hole.top; bottom |= y > hole.bottom;
                if (!nearInk(control, x, y, size)) distinct++;
            }
            // Four sides contributed new ink. At least six pixels remain distinct even after
            // allowing for a one-pixel shift in the background readout/reticle.
            if (left && right && top && bottom && distinct >= 6 && (best == null || distinct > best.distinct))
                best = new Ring(x0 + hole.cx(), y0 + hole.cy(), hole.rim.cardinality(), added.cardinality(), distinct);
        }
        return best;
    }

    /** Replay the central pixels from receiver job 138908998654, plus controls for false positives. */
    static void checkControls() {
        var exposed = rows(0x80, 0x3c0, 0x240, 0x240, 0x3c0);
        var hidden = rows(0x80, 0x80, 0, 0, 0);
        var ring = find(exposed, hidden, 7);
        if (ring == null || ring.rim != 12 || ring.changed != 11 || ring.distinct != 8 || find(exposed, exposed, 7) != null
                || find(rows(0x80, 0x3c0, 0x240, 0x240, 0x240), hidden, 7) != null // missing base
                || find(rows(0x80, 0x3c0, 0x3c0, 0x3c0, 0x3c0), hidden, 7) != null // solid
                || find(exposed, rows(0x40, 0x1e0, 0x120, 0x120, 0x1e0), 7) != null) // unchanged ring, shifted one pixel
            throw new AssertionError("XM157 framebuffer control mismatch: " + ring);
    }

    private static BufferedImage rows(int... bits) {
        var image = new BufferedImage(15, 15, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < bits.length; y++) for (int x = 0; x < 15; x++) {
            if ((bits[y] & (1 << (14 - x))) != 0) image.setRGB(x, y + 5, 0xffff00);
        }
        return image;
    }
}
