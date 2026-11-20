package com.tacz.guns.test;

import com.tacz.guns.GunMod;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Final, post-tonemap RGB, not vertex light or the emissive material name. The neutral
 * receiver, bare skin and unrelated ground ROIs are inside surfaces in the stock night frames. */
final class ReimaginedFrames {
    private static int channel(int rgb, int c) { return rgb >> (16 - c * 8) & 255; }

    private static int[] mean(BufferedImage image, int x1, int y1, int x2, int y2) {
        int[] sum = new int[3];
        int n = 0;
        for (int y = image.getHeight()*y1/100; y < image.getHeight()*y2/100; y++) {
            for (int x = image.getWidth()*x1/100; x < image.getWidth()*x2/100; x++) {
                int rgb = image.getRGB(x,y);
                for (int c=0; c<3; c++) sum[c] += channel(rgb,c);
                n++;
            }
        }
        for (int c=0; c<3; c++) sum[c] /= n;
        return sum;
    }

    static void exposure(BufferedImage off, BufferedImage body, BufferedImage stock, BufferedImage cooled, StringBuilder failures) {
        int[][] rois = {{62,71,65,74}, {49,67,51,70}, {32,61,36,65}};
        for (int i=0; i<rois.length; i++) {
            int[] r = rois[i], delta = new int[3];
            var a = mean(off,r[0],r[1],r[2],r[3]);
            var b = mean(body,r[0],r[1],r[2],r[3]);
            var after = mean(cooled,r[0],r[1],r[2],r[3]);
            for (int c=0; c<3; c++) delta[c] = b[c]-a[c];
            int min = Arrays.stream(delta).min().orElseThrow(), max = Arrays.stream(delta).max().orElseThrow();
            String name = new String[]{"metal", "skin", "ground"}[i];
            GunMod.LOGGER.info("PORT_REIMAGINED: {} ambient={} shot={} increment={} cooled={}",name,Arrays.toString(a),Arrays.toString(b),Arrays.toString(delta),Arrays.toString(after));
            // Do not require white skin or a desaturated base: light the existing albedo.
            boolean valid = switch (i) {
                case 0 -> min > 9 && max-min <= 5 + max*.35;
                case 1 -> min > 5 && delta[0] > delta[1]*1.15 && delta[1] > delta[2]+4;
                default -> max <= 12 && min >= -18;
            };
            for (int c=0; c<3; c++) valid &= Math.abs(after[c]-a[c]) <= 16;
            if (!valid) failures.append(' ').append(name).append("-exposure");
        }
        // Difference against the equally exposed body with only the locator suppressed.
        // Require a bright core on the *actual stock billboard*, not the synthetic texel quad.
        int changed=0, cores=0;
        for (int y=stock.getHeight()*46/100; y<stock.getHeight()*59/100; y++) {
            for (int x=stock.getWidth()*49/100; x<stock.getWidth()*59/100; x++) {
                int a=body.getRGB(x,y), b=stock.getRGB(x,y), inc=0, lo=255, hi=0;
                for (int c=0;c<3;c++) {
                    int v=channel(b,c); inc=Math.max(inc,v-channel(a,c)); lo=Math.min(lo,v); hi=Math.max(hi,v);
                }
                if (inc>55) {changed++; if (lo>165 && hi-lo<45) cores++;}
            }
        }
        GunMod.LOGGER.info("PORT_REIMAGINED: stock locator changed={} bright neutral core={}",changed,cores);
        if (cores<6) failures.append(" stock-core");
    }

    private ReimaginedFrames() {}
}
