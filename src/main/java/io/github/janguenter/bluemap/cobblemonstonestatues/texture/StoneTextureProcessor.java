/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.texture;

import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection.Material;

import java.awt.image.BufferedImage;

/** Exact Stone Statues 1.1 pixel transform, independently implemented. */
public final class StoneTextureProcessor {

    private StoneTextureProcessor() {
    }

    public static BufferedImage process(
            BufferedImage source, Material material, int tintArgb
    ) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB
        );
        int tintA = tintArgb >>> 24 & 0xFF;
        int tintR = tintArgb >>> 16 & 0xFF;
        int tintG = tintArgb >>> 8 & 0xFF;
        int tintB = tintArgb & 0xFF;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24 & 0xFF;
                if (alpha == 0 || tintA == 0) {
                    result.setRGB(x, y, 0);
                    continue;
                }
                int red = argb >>> 16 & 0xFF;
                int green = argb >>> 8 & 0xFF;
                int blue = argb & 0xFF;
                double luma = (0.299D * red + 0.587D * green + 0.114D * blue) / 255D;
                double value = clamp((luma - 0.5D) * 1.18D + 0.5D, 0D, 1D);
                int transformed = material == Material.STONE
                        ? stonePacked(luma, value) : goldPacked(luma, value);
                int outA = alpha * tintA / 255;
                int outR = (transformed >>> 16 & 0xFF) * tintR / 255;
                int outG = (transformed >>> 8 & 0xFF) * tintG / 255;
                int outB = (transformed & 0xFF) * tintB / 255;
                result.setRGB(x, y, outA << 24 | outR << 16 | outG << 8 | outB);
            }
        }
        return result;
    }

    static int[] stone(double luma, double value) {
        return unpack(stonePacked(luma, value));
    }

    private static int stonePacked(double luma, double value) {
        int gray = (int) (44D + value * 172D);
        if (luma < 0.18D) {
            gray = (int) (gray * 0.72D);
        }
        if (luma > 0.84D) {
            gray = Math.min(232, gray + 18);
        }
        return pack(clamp(gray + 2), clamp(gray + 3), clamp(gray + 7));
    }

    static int[] gold(double luma, double value) {
        return unpack(goldPacked(luma, value));
    }

    private static int goldPacked(double luma, double value) {
        if (luma < 0.18D) {
            value *= 0.76D;
        }
        if (luma > 0.84D) {
            value = Math.min(1D, value + 0.08D);
        }
        if (value < 0.55D) {
            double t = value / 0.55D;
            return lerpPacked(t, 96, 74, 12, 214, 172, 30);
        }
        double t = (value - 0.55D) / 0.45D;
        return lerpPacked(t, 214, 172, 30, 249, 236, 82);
    }

    private static int lerpPacked(
            double t, int r1, int g1, int b1, int r2, int g2, int b2
    ) {
        return pack(
                (int) Math.round(r1 + (r2 - r1) * t),
                (int) Math.round(g1 + (g2 - g1) * t),
                (int) Math.round(b1 + (b2 - b1) * t)
        );
    }

    private static int pack(int red, int green, int blue) {
        return red << 16 | green << 8 | blue;
    }

    private static int[] unpack(int packed) {
        return new int[]{packed >>> 16 & 0xFF, packed >>> 8 & 0xFF, packed & 0xFF};
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
