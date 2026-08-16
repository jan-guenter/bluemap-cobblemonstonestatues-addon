/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Bounds;
import io.github.janguenter.stoneposeexporter.CapturedMesh.Vec3;
import io.github.janguenter.stoneposeexporter.ExportRecords.Catalog;
import io.github.janguenter.stoneposeexporter.ExportRecords.Pose;
import io.github.janguenter.stoneposeexporter.ExportRecords.Texture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportRecordsMaterialBudgetTest {

    private static final Bounds BOUNDS = new Bounds(
            new Vec3(0F, 0F, 0F), new Vec3(1F, 1F, 1F)
    );

    @Test
    void repeatedPosesDedupeButFallbackAndSameShaDifferentResourcesRemainRoutes() {
        Texture first = texture(0, 1, 1, "a".repeat(64));
        Texture second = texture(1, 1, 1, "a".repeat(64));
        Pose firstPose = pose(first.resourceId(), 0xffffffff, "standing", 0);

        Catalog.MaterialBudget budget = Catalog.verifyMaterialBudgets(
                List.of(firstPose, firstPose),
                List.of(pose(second.resourceId(), 0xffffffff,
                        ExportRecords.FALLBACK_POSE, -1)),
                Map.of(first.resourceId(), first, second.resourceId(), second)
        );

        assertEquals(4, budget.variants());
        assertEquals(4L, budget.pixels());
    }

    @Test
    void routeAndPixelBoundariesAcceptExactAndRejectOneOver() {
        Map<String, Texture> routeTextures = new LinkedHashMap<>();
        List<Pose> routePoses = new ArrayList<>();
        int routePairs = FormatTwoBudgets.MAX_MATERIAL_VARIANTS
                / FormatTwoBudgets.MATERIAL_VARIANTS_PER_ROUTE;
        for (int index = 0; index < routePairs; index++) {
            Texture texture = texture(index, 1, 1, String.format("%064x", index + 1));
            routeTextures.put(texture.resourceId(), texture);
            routePoses.add(pose(texture.resourceId(), 0xffffffff, "standing", 0));
        }
        Catalog.MaterialBudget routeBoundary = Catalog.verifyMaterialBudgets(
                routePoses, List.of(), routeTextures
        );
        assertEquals(FormatTwoBudgets.MAX_MATERIAL_VARIANTS,
                routeBoundary.variants());

        routePoses.add(pose(
                routeTextures.values().iterator().next().resourceId(),
                0xfffffffe, "standing", 0
        ));
        assertThrows(IllegalArgumentException.class, () ->
                Catalog.verifyMaterialBudgets(routePoses, List.of(), routeTextures));

        Map<String, Texture> pixelTextures = new LinkedHashMap<>();
        List<Pose> pixelPoses = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            Texture texture = texture(index, 4_096, 4_096,
                    String.format("%064x", index + 1));
            pixelTextures.put(texture.resourceId(), texture);
            pixelPoses.add(pose(texture.resourceId(), 0xffffffff, "standing", 0));
        }
        Catalog.MaterialBudget pixelBoundary = Catalog.verifyMaterialBudgets(
                pixelPoses, List.of(), pixelTextures
        );
        assertEquals(FormatTwoBudgets.MAX_MATERIAL_VARIANT_PIXELS,
                pixelBoundary.pixels());

        Texture over = texture(16, 4_096, 4_096, "f".repeat(64));
        pixelTextures.put(over.resourceId(), over);
        pixelPoses.add(pose(over.resourceId(), 0xffffffff, "standing", 0));
        assertThrows(IllegalArgumentException.class, () ->
                Catalog.verifyMaterialBudgets(pixelPoses, List.of(), pixelTextures));
    }

    private static Texture texture(int index, int width, int height, String sha256) {
        return new Texture(
                "cobblemon:textures/pokemon/test" + index + ".png",
                "mod/cobblemon", 1, sha256, width, height
        );
    }

    private static Pose pose(
            String textureResourceId, int tint, String requestedPose, int poseIndex
    ) {
        return new Pose(
                "cobblemon:test", "Normal", "", requestedPose, poseIndex,
                "standing", 1F, "DIRECT", "DIRECT", "cobblemon:test.geo",
                "cobblemon:test", textureResourceId, "b".repeat(64),
                "c".repeat(64), 1, 1, 0, BOUNDS, tint
        );
    }
}
