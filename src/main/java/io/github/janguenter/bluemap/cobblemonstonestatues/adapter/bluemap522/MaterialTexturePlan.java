/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Key;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ResourceBlob;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection.Material;
import io.github.janguenter.bluemap.cobblemonstonestatues.texture.StoneTextureProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Bounded, deduplicated material plan built before any active resource is opened. */
final class MaterialTexturePlan {

    private final Map<String, SourcePlan> sources;
    private final Map<String, Key> generatedKeys;
    private final int variantCount;
    private final long variantPixels;

    private MaterialTexturePlan(
            Map<String, SourcePlan> sources,
            Map<String, Key> generatedKeys,
            int variantCount,
            long variantPixels
    ) {
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        this.generatedKeys = Collections.unmodifiableMap(new LinkedHashMap<>(generatedKeys));
        this.variantCount = variantCount;
        this.variantPixels = variantPixels;
    }

    static MaterialTexturePlan create(PoseCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        return create(
                catalog.textures(), catalog.poses().values(), catalog.fallbacks().values()
        );
    }

    static MaterialTexturePlan create(
            Map<String, TextureIdentity> textures,
            Iterable<PoseEntry> poses,
            Iterable<PoseEntry> fallbacks
    ) {
        Objects.requireNonNull(textures, "textures");
        if (Material.values().length != FormatTwoBudgets.MATERIAL_VARIANTS_PER_ROUTE) {
            throw new IllegalStateException("material enum differs from format-2 contract");
        }
        Map<PairKey, TextureIdentity> pairs = new LinkedHashMap<>();
        retainPairs(poses, textures, pairs);
        retainPairs(fallbacks, textures, pairs);

        Map<String, List<Variant>> grouped = new LinkedHashMap<>();
        Map<String, Key> generatedKeys = new LinkedHashMap<>();
        Map<OutputKey, Key> outputs = new LinkedHashMap<>();
        int variants = 0;
        long pixels = 0L;
        for (Map.Entry<PairKey, TextureIdentity> pair : pairs.entrySet()) {
            TextureIdentity source = pair.getValue();
            long sourcePixels = Math.multiplyExact(
                    (long) source.width(), (long) source.height()
            );
            for (Material material : Material.values()) {
                variants = Math.addExact(variants, 1);
                pixels = Math.addExact(pixels, sourcePixels);
                if (variants > FormatTwoBudgets.MAX_MATERIAL_VARIANTS
                        || pixels > FormatTwoBudgets.MAX_MATERIAL_VARIANT_PIXELS) {
                    throw new IllegalArgumentException("material texture plan exceeds budget");
                }
                String cacheKey = cacheKey(
                        material, source.resourceId(), pair.getKey().tintArgb()
                );
                Key key = generatedKey(material, source, pair.getKey().tintArgb());
                if (generatedKeys.putIfAbsent(cacheKey, key) != null) {
                    throw new IllegalArgumentException("duplicate material texture route");
                }
                OutputKey output = new OutputKey(
                        material, source.sha256(), pair.getKey().tintArgb()
                );
                Key previous = outputs.putIfAbsent(output, key);
                if (previous != null) {
                    if (!previous.equals(key)) {
                        throw new IllegalArgumentException("shared texture identity drift");
                    }
                    continue;
                }
                grouped.computeIfAbsent(source.resourceId(), ignored -> new ArrayList<>())
                        .add(new Variant(material, pair.getKey().tintArgb(), cacheKey, key));
            }
        }
        Map<String, SourcePlan> sources = new LinkedHashMap<>();
        grouped.forEach((resourceId, sourceVariants) -> sources.put(
                resourceId,
                new SourcePlan(textures.get(resourceId), sourceVariants)
        ));
        if (sources.isEmpty() || variants == 0) {
            throw new IllegalArgumentException("empty material texture plan");
        }
        return new MaterialTexturePlan(sources, generatedKeys, variants, pixels);
    }

    Generated generate(
            Map<String, ResourceBlob> blobs,
            Predicate<Key> existingKey
    ) throws IOException {
        return generate(blobs, existingKey, blob -> ImageIO.read(blob.openStream()));
    }

    Generated generate(
            Map<String, ResourceBlob> blobs,
            Predicate<Key> existingKey,
            ImageDecoder decoder
    ) throws IOException {
        Objects.requireNonNull(blobs, "blobs");
        Objects.requireNonNull(existingKey, "existingKey");
        Objects.requireNonNull(decoder, "decoder");
        for (Key key : new java.util.LinkedHashSet<>(generatedKeys.values())) {
            if (existingKey.test(key)) {
                throw new IllegalArgumentException("generated texture key collision");
            }
        }
        Map<Key, Texture> generated = new LinkedHashMap<>();
        long encodedCharacters = 0L;
        for (SourcePlan sourcePlan : sources.values()) {
            ResourceBlob blob = Objects.requireNonNull(
                    blobs.get(sourcePlan.identity().resourceId()),
                    "source texture closure is missing"
            );
            BufferedImage image = decoder.decode(blob);
            if (image == null) {
                throw new IllegalArgumentException("source texture decode mismatch");
            }
            try {
                if (image.getWidth() != sourcePlan.identity().width()
                        || image.getHeight() != sourcePlan.identity().height()) {
                    throw new IllegalArgumentException("source texture decode mismatch");
                }
                for (Variant variant : sourcePlan.variants()) {
                    if (generated.containsKey(variant.key())) {
                        throw new IllegalArgumentException("generated texture key collision");
                    }
                    BufferedImage converted = StoneTextureProcessor.process(
                            image, variant.material(), variant.tintArgb()
                    );
                    Texture texture = Texture.from(variant.key(), converted);
                    encodedCharacters = checkedGeneratedCharacters(
                            encodedCharacters, texture.getTexture().length()
                    );
                    generated.put(variant.key(), texture);
                }
            } finally {
                image.flush();
            }
        }
        return new Generated(generatedKeys, generated, encodedCharacters);
    }

    static long checkedGeneratedCharacters(long current, long added) {
        if (current < 0L || added < 0L) {
            throw new IllegalArgumentException("negative generated texture size");
        }
        long next = Math.addExact(current, added);
        if (Math.multiplyExact(2L, next)
                > FormatTwoBudgets.MAX_GENERATED_TEXTURE_HEAP_BYTES) {
            throw new IllegalArgumentException("generated texture heap exceeds budget");
        }
        return next;
    }

    int variantCount() {
        return variantCount;
    }

    long variantPixels() {
        return variantPixels;
    }

    private static void retainPairs(
            Iterable<PoseEntry> poses,
            Map<String, TextureIdentity> textures,
            Map<PairKey, TextureIdentity> output
    ) {
        for (PoseEntry pose : poses) {
            TextureIdentity source = textures.get(pose.textureResourceId());
            if (source == null) {
                throw new IllegalArgumentException("source texture closure is missing");
            }
            output.putIfAbsent(new PairKey(source.resourceId(), pose.tintArgb()), source);
        }
    }

    static String cacheKey(Material material, String resourceId, int tintArgb) {
        return material.profile() + '|' + resourceId + '|'
                + Integer.toUnsignedString(tintArgb);
    }

    private static Key generatedKey(
            Material material, TextureIdentity source, int tintArgb
    ) {
        String stem = material == Material.STONE ? "stone" : "gold";
        String hash = source.sha256() + '-' + String.format("%08x", tintArgb);
        return Key.parse("bluemap_cobblemonstonestatues:generated/" + stem + '/' + hash);
    }

    private record PairKey(String resourceId, int tintArgb) {
    }

    private record OutputKey(Material material, String sha256, int tintArgb) {
    }

    private record SourcePlan(TextureIdentity identity, List<Variant> variants) {
        private SourcePlan {
            Objects.requireNonNull(identity, "identity");
            variants = List.copyOf(variants);
        }
    }

    private record Variant(Material material, int tintArgb, String cacheKey, Key key) {
    }

    record Generated(
            Map<String, Key> keys,
            Map<Key, Texture> textures,
            long encodedCharacters
    ) {
        Generated {
            keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
            textures = Collections.unmodifiableMap(new LinkedHashMap<>(textures));
            if (keys.isEmpty() || textures.isEmpty()
                    || !textures.keySet().equals(new java.util.HashSet<>(keys.values()))) {
                throw new IllegalArgumentException("incomplete generated texture closure");
            }
        }
    }

    @FunctionalInterface
    interface ImageDecoder {
        BufferedImage decode(ResourceBlob blob) throws IOException;
    }
}
