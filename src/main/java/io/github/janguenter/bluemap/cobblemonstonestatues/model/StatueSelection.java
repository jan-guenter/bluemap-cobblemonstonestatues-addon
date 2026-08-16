/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.model;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Normalized persisted selection used to look up an exported time-zero pose. */
public record StatueSelection(
        String speciesId,
        String formId,
        String aspectsCsv,
        String animationName,
        Scale scale,
        Material material,
        int rotation45,
        long stoneSeed
) {

    public StatueSelection {
        speciesId = speciesId == null || speciesId.isBlank()
                ? "cobblemon:bulbasaur" : speciesId;
        formId = formId == null ? "" : formId;
        aspectsCsv = normalizeAspects(aspectsCsv);
        animationName = animationName == null ? "" : animationName.trim();
        scale = scale == null ? Scale.NORMAL : scale;
        material = material == null ? Material.STONE : material;
        rotation45 = Math.floorMod(rotation45, 8);
    }

    public String poseKey() {
        return speciesId + '\u001f' + formId + '\u001f' + aspectsCsv
                + '\u001f' + animationName;
    }

    public static String normalizeAspects(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        Set<String> values = new LinkedHashSet<>();
        for (String raw : source.split(",")) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values.stream().sorted().collect(java.util.stream.Collectors.joining(","));
    }

    public enum Scale {
        HALF(0.5D), NORMAL(1D), DOUBLE(2D), TRIPLE(3D);

        private final double factor;

        Scale(double factor) {
            this.factor = factor;
        }

        public double factor() {
            return factor;
        }

        public static Scale fromSerialized(String value) {
            if (value != null) {
                for (Scale scale : values()) {
                    if (scale.name().equalsIgnoreCase(value)) {
                        return scale;
                    }
                }
            }
            return NORMAL;
        }
    }

    public enum Material {
        STONE("stone_v1"), GOLD_BLOCK("gold_block_v1");

        private final String profile;

        Material(String profile) {
            this.profile = profile;
        }

        public String profile() {
            return profile;
        }

        public static Material fromSerialized(String value) {
            if (value != null) {
                String normalized = value.toUpperCase(Locale.ROOT);
                for (Material material : values()) {
                    if (material.name().equals(normalized)) {
                        return material;
                    }
                }
            }
            return STONE;
        }
    }
}
