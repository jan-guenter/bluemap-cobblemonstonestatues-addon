/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;

/** Transactional early-admission budget for retained format-2 GEO/PNG closure. */
final class ResourceAdmissionBudget {

    private long resourceBytes;
    private long structureUnits;

    void admitModel(
            int size, int bones, int cubes, int locators, int topologyNodes
    ) {
        requireItemSize(size);
        requireModelCounts(bones, cubes, locators, topologyNodes);
        long nextBytes = Math.addExact(resourceBytes, size);
        long nextStructure = Math.addExact(structureUnits, bones);
        nextStructure = Math.addExact(nextStructure, cubes);
        nextStructure = Math.addExact(nextStructure, locators);
        nextStructure = Math.addExact(nextStructure, topologyNodes);
        requireTotals(nextBytes, nextStructure);
        resourceBytes = nextBytes;
        structureUnits = nextStructure;
    }

    void requireResourceCapacity(int size) {
        requireItemSize(size);
        requireTotals(Math.addExact(resourceBytes, size), structureUnits);
    }

    void admitTexture(int size) {
        requireResourceCapacity(size);
        long nextBytes = Math.addExact(resourceBytes, size);
        requireTotals(nextBytes, structureUnits);
        resourceBytes = nextBytes;
    }

    long resourceBytes() {
        return resourceBytes;
    }

    long structureUnits() {
        return structureUnits;
    }

    private static void requireItemSize(int size) {
        if (size < 1 || size > FormatTwoBudgets.MAX_RESOURCE_BYTES) {
            throw new IllegalArgumentException("resource admission outside item budget");
        }
    }

    private static void requireModelCounts(
            int bones, int cubes, int locators, int topologyNodes
    ) {
        if (bones < 1 || bones > FormatTwoBudgets.MAX_MODEL_BONES
                || cubes < 0 || cubes > FormatTwoBudgets.MAX_MODEL_CUBES
                || locators < 0 || locators > FormatTwoBudgets.MAX_MODEL_LOCATORS
                || topologyNodes < 1
                || topologyNodes > FormatTwoBudgets.MAX_MODEL_TOPOLOGY_NODES) {
            throw new IllegalArgumentException("model structure admission outside item budget");
        }
    }

    private static void requireTotals(long bytes, long structure) {
        if (bytes > FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES
                || structure > FormatTwoBudgets.MAX_TOTAL_MODEL_STRUCTURE_UNITS) {
            throw new IllegalArgumentException("resource admission exceeds closure budget");
        }
    }
}
