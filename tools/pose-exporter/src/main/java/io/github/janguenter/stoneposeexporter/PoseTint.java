/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

/** Exact PosableModel white-input tint correlation; captured vertex tint remains authoritative. */
final class PoseTint {

    private PoseTint() {
    }

    static int fromWhiteInput(float red, float green, float blue, float alpha) {
        int alphaByte = (int) (alpha * 255F);
        int redByte = (int) (red * 255F);
        int greenByte = (int) (green * 255F);
        int blueByte = (int) (blue * 255F);
        return alphaByte << 24 | redByte << 16 | greenByte << 8 | blueByte;
    }

    static int requireAuthoritative(int derived, int authoritative) {
        if (derived != authoritative) {
            throw new IllegalArgumentException("captured authoritative tint differs");
        }
        return authoritative;
    }
}
