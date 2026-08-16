/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.catalog;

/** Evidence-grounded allocation bounds for the exact ATMons 1.2.0 hybrid profile. */
public final class FormatTwoBudgets {

    public static final int MAX_RESOURCE_BYTES = 2 * 1024 * 1024;
    public static final long MAX_ACTIVE_RESOURCE_BYTES = 64L * 1024L * 1024L;
    public static final int MAX_MODEL_RESOURCES = 8_192;
    public static final int MAX_MODEL_BONES = 1_024;
    public static final int MAX_MODEL_CUBES = 4_096;
    public static final int MAX_MODEL_LOCATORS = 1_024;
    public static final int MAX_MODEL_TOPOLOGY_NODES = 8_192;
    public static final int MAX_MODEL_DEPTH = 512;
    public static final long MAX_TOTAL_MODEL_STRUCTURE_UNITS = 1_048_576L;
    public static final int MAX_MATERIAL_VARIANTS = 16_384;
    public static final int MATERIAL_VARIANTS_PER_ROUTE = 2;
    public static final long MAX_MATERIAL_VARIANT_PIXELS = 512L * 1024L * 1024L;
    public static final long MAX_GENERATED_TEXTURE_HEAP_BYTES = 256L * 1024L * 1024L;

    private FormatTwoBudgets() {
    }
}
