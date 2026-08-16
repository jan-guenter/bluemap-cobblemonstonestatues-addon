/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ResourceBlob;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ResolutionKind;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialTexturePlanTest {

    @TempDir
    Path temporary;

    @Test
    void duplicateBytesShareOutputsButRetainEveryRouteAndDecodeOnce() throws Exception {
        byte[] png = png(1, 1, 0xff4287f5);
        TextureIdentity first = texture("first", png, 1, 1);
        TextureIdentity second = texture("second", png, 1, 1);
        MaterialTexturePlan plan = MaterialTexturePlan.create(
                Map.of(first.resourceId(), first, second.resourceId(), second),
                List.of(
                        pose("one", "standing", 0, first.resourceId()),
                        pose("one", "standing", 0, first.resourceId())
                ),
                List.of(pose("two", "__atmons_impossible_pose__", -1,
                        second.resourceId()))
        );
        Map<String, ResourceBlob> blobs = Map.of(
                first.resourceId(), blob("first.png", png),
                second.resourceId(), blob("second.png", png)
        );
        AtomicInteger decodes = new AtomicInteger();

        MaterialTexturePlan.Generated generated = plan.generate(
                blobs, ignored -> false, value -> {
                    decodes.incrementAndGet();
                    return ImageIO.read(value.openStream());
                }
        );

        assertEquals(4, plan.variantCount());
        assertEquals(4L, plan.variantPixels());
        assertEquals(4, generated.keys().size());
        assertEquals(2, generated.textures().size());
        assertEquals(1, decodes.get());
    }

    @Test
    void distinctBytesNeverAliasAndExistingKeysRejectBeforeDecode() throws Exception {
        byte[] firstPng = png(1, 1, 0xff112233);
        byte[] secondPng = png(1, 1, 0xff334455);
        TextureIdentity first = texture("first", firstPng, 1, 1);
        TextureIdentity second = texture("second", secondPng, 1, 1);
        MaterialTexturePlan plan = MaterialTexturePlan.create(
                Map.of(first.resourceId(), first, second.resourceId(), second),
                List.of(
                        pose("one", "standing", 0, first.resourceId()),
                        pose("two", "standing", 0, second.resourceId())
                ),
                List.of()
        );
        Map<String, ResourceBlob> blobs = Map.of(
                first.resourceId(), blob("first.png", firstPng),
                second.resourceId(), blob("second.png", secondPng)
        );
        AtomicInteger decodes = new AtomicInteger();

        MaterialTexturePlan.Generated generated = plan.generate(
                blobs, ignored -> false, value -> {
                    decodes.incrementAndGet();
                    return ImageIO.read(value.openStream());
                }
        );
        assertEquals(4, generated.textures().size());
        assertEquals(2, decodes.get());

        AtomicInteger rejectedDecodes = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> plan.generate(
                blobs, ignored -> true, value -> {
                    rejectedDecodes.incrementAndGet();
                    return ImageIO.read(value.openStream());
                }
        ));
        assertEquals(0, rejectedDecodes.get());
    }

    @Test
    void pixelAndGeneratedStringBudgetsAcceptBoundaryAndRejectOneOver() {
        assertEquals(
                FormatTwoBudgets.MAX_GENERATED_TEXTURE_HEAP_BYTES / 2L,
                MaterialTexturePlan.checkedGeneratedCharacters(
                        0L, FormatTwoBudgets.MAX_GENERATED_TEXTURE_HEAP_BYTES / 2L
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                MaterialTexturePlan.checkedGeneratedCharacters(
                        FormatTwoBudgets.MAX_GENERATED_TEXTURE_HEAP_BYTES / 2L, 1L
                ));

        Map<String, TextureIdentity> exactTextures = new LinkedHashMap<>();
        List<PoseEntry> exactPoses = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            TextureIdentity texture = syntheticTexture(index, 4_096, 4_096);
            exactTextures.put(texture.resourceId(), texture);
            exactPoses.add(pose("species" + index, "standing", 0,
                    texture.resourceId()));
        }
        MaterialTexturePlan exact = MaterialTexturePlan.create(
                exactTextures, exactPoses, List.of()
        );
        assertEquals(FormatTwoBudgets.MAX_MATERIAL_VARIANT_PIXELS,
                exact.variantPixels());

        TextureIdentity over = syntheticTexture(16, 4_096, 4_096);
        exactTextures.put(over.resourceId(), over);
        exactPoses.add(pose("species16", "standing", 0, over.resourceId()));
        assertThrows(IllegalArgumentException.class, () ->
                MaterialTexturePlan.create(exactTextures, exactPoses, List.of()));
    }

    @Test
    void materialRouteCountAcceptsBoundaryAndRejectsOneOver() {
        TextureIdentity texture = syntheticTexture(0, 1, 1);
        List<PoseEntry> poses = new ArrayList<>();
        for (int tint = 0; tint < FormatTwoBudgets.MAX_MATERIAL_VARIANTS / 2; tint++) {
            poses.add(pose("species", "standing", 0, texture.resourceId(), tint));
        }
        MaterialTexturePlan exact = MaterialTexturePlan.create(
                Map.of(texture.resourceId(), texture), poses, List.of()
        );
        assertEquals(FormatTwoBudgets.MAX_MATERIAL_VARIANTS, exact.variantCount());

        poses.add(pose("species", "standing", 0, texture.resourceId(),
                FormatTwoBudgets.MAX_MATERIAL_VARIANTS / 2));
        assertThrows(IllegalArgumentException.class, () ->
                MaterialTexturePlan.create(
                        Map.of(texture.resourceId(), texture), poses, List.of()
                ));
    }

    private ResourceBlob blob(String name, byte[] raw) throws Exception {
        Path path = temporary.resolve(name);
        Files.write(path, raw);
        return ResourceBlob.read(path, raw.length);
    }

    private static PoseEntry pose(
            String species, String pose, int index, String textureResourceId
    ) {
        return pose(species, pose, index, textureResourceId, 0xfffefdfc);
    }

    private static PoseEntry pose(
            String species, String pose, int index, String textureResourceId, int tint
    ) {
        QuadMultisetDigest.Vec3 zero = new QuadMultisetDigest.Vec3(0F, 0F, 0F);
        return new PoseEntry(
                "cobblemon:" + species, "Normal", "", pose, index, null, 1F,
                ResolutionKind.DIRECT, ResolutionKind.DIRECT,
                "cobblemon:test.geo", "cobblemon:test", textureResourceId,
                "b".repeat(64), new QuadMultisetDigest.Result(
                        "a".repeat(64), 1, 1, 0, tint,
                        new QuadMultisetDigest.Bounds(zero, zero)
                )
        );
    }

    private static TextureIdentity texture(
            String name, byte[] raw, int width, int height
    ) {
        String resource = "cobblemon:textures/pokemon/" + name + ".png";
        return new TextureIdentity(
                resource, "assets/cobblemon/textures/pokemon/" + name + ".png",
                "mod/cobblemon", raw.length, sha256(raw), width, height
        );
    }

    private static TextureIdentity syntheticTexture(int index, int width, int height) {
        String name = "texture" + index;
        String resource = "cobblemon:textures/pokemon/" + name + ".png";
        return new TextureIdentity(
                resource, "assets/cobblemon/textures/pokemon/" + name + ".png",
                "mod/cobblemon", 1, String.format("%064x", index + 1), width, height
        );
    }

    private static byte[] png(int width, int height, int argb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static String sha256(byte[] raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
