/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.texture;

import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection.Material;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StoneTextureProcessorTest {

    @Test
    void appliesStoneAndGoldBoundaryRules() {
        assertArrayEquals(new int[]{95, 96, 100}, StoneTextureProcessor.stone(0.179D, 0.5D));
        assertArrayEquals(new int[]{132, 133, 137}, StoneTextureProcessor.stone(0.18D, 0.5D));
        assertArrayEquals(new int[]{132, 133, 137}, StoneTextureProcessor.stone(0.84D, 0.5D));
        assertArrayEquals(new int[]{150, 151, 155}, StoneTextureProcessor.stone(0.841D, 0.5D));
        assertArrayEquals(new int[]{96, 74, 12}, StoneTextureProcessor.gold(0.2D, 0D));
        assertArrayEquals(new int[]{214, 172, 30}, StoneTextureProcessor.gold(0.2D, 0.55D));
        assertArrayEquals(new int[]{249, 236, 82}, StoneTextureProcessor.gold(0.9D, 1D));
    }

    @Test
    void preservesTransparentZeroAndMultipliesUniformTint() {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x00ffffff);
        source.setRGB(1, 0, 0xffffffff);

        BufferedImage converted = StoneTextureProcessor.process(
                source, Material.GOLD_BLOCK, 0x80804020
        );

        assertEquals(0x00000000, converted.getRGB(0, 0));
        assertEquals(0x807c3b0a, converted.getRGB(1, 0));
    }
}
