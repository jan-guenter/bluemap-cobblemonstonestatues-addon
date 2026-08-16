/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.catalog;

import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ModelIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseStateIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.QuadMultisetDigest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical length-framed roots mirrored exactly from exporter format 2. */
final class CatalogRoots {

    private static final byte[] MAGIC =
            "atmons-stone-root-v1".getBytes(StandardCharsets.US_ASCII);
    private static final Comparator<List<String>> ROW_ORDER = (first, second) -> {
        int shared = Math.min(first.size(), second.size());
        for (int index = 0; index < shared; index++) {
            int comparison = first.get(index).compareTo(second.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(first.size(), second.size());
    };

    private CatalogRoots() {
    }

    static String species(Map<String, PoseEntry> fallbacks) {
        Set<String> values = new HashSet<>();
        fallbacks.values().forEach(entry -> values.add(entry.speciesId()));
        return root("species", values.stream().map(List::of).toList());
    }

    static String choices(Map<String, PoseEntry> fallbacks) {
        Set<String> values = new HashSet<>();
        fallbacks.values().forEach(entry -> values.add(entry.choiceKey()));
        return root("choices", values.stream().map(List::of).toList());
    }

    static String poses(Map<String, PoseEntry> poses, Map<String, PoseEntry> fallbacks) {
        List<List<String>> rows = new ArrayList<>(poses.size() + fallbacks.size());
        poses.values().forEach(entry -> rows.add(poseRow("profile", entry)));
        fallbacks.values().forEach(entry -> rows.add(poseRow("fallback", entry)));
        return root("poses-v2", rows);
    }

    static String models(Map<String, ModelIdentity> models) {
        return root("models-v2", models.values().stream().map(identity -> List.of(
                identity.modelId(), identity.resourceId(), identity.resourcePath(),
                identity.winningPackId(), Integer.toString(identity.size()),
                identity.sha256(), identity.formatVersion(), identity.geometryIdentifier(),
                Integer.toString(identity.textureWidth()),
                Integer.toString(identity.textureHeight()), identity.topologySha256(),
                Integer.toString(identity.topologyNodeCount()),
                Integer.toString(identity.boneCount()), Integer.toString(identity.cubeCount()),
                Integer.toString(identity.locatorCount())
        )).toList());
    }

    static String poseStates(Map<String, PoseStateIdentity> states) {
        return root("pose-states-v2", states.values().stream().map(identity -> List.of(
                identity.sha256(), Integer.toString(identity.size()),
                identity.selectedRootPath(), Integer.toString(identity.nodeCount()),
                Integer.toUnsignedString(identity.tintArgb())
        )).toList());
    }

    static String textures(Map<String, TextureIdentity> textures) {
        return root("textures", textures.values().stream().map(identity -> List.of(
                identity.resourceId(), identity.resourcePath(), identity.winningPackId(),
                Integer.toString(identity.size()), identity.sha256(),
                Integer.toString(identity.width()), Integer.toString(identity.height())
        )).toList());
    }

    static String root(String domain, Collection<List<String>> sourceRows) {
        List<List<String>> rows = new ArrayList<>(sourceRows.size());
        sourceRows.forEach(row -> rows.add(List.copyOf(row)));
        rows.sort(ROW_ORDER);
        MessageDigest digest = sha256();
        updateBytes(digest, MAGIC);
        updateString(digest, domain);
        updateInt(digest, rows.size());
        for (List<String> row : rows) {
            updateInt(digest, row.size());
            for (String field : row) {
                updateString(digest, field);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<String> poseRow(String kind, PoseEntry entry) {
        QuadMultisetDigest.Result verification = entry.verification();
        QuadMultisetDigest.Bounds bounds = verification.bounds();
        List<String> row = new ArrayList<>(27);
        java.util.Collections.addAll(
                row, kind, entry.speciesId(), entry.formId(), entry.aspectsCsv(),
                entry.pose(), Integer.toString(entry.poseIndex()),
                entry.effectivePose() == null ? "0" : "1",
                entry.effectivePose() == null ? "" : entry.effectivePose(),
                Float.toHexString(entry.baseScale()), entry.geometryResolution().name(),
                entry.textureResolution().name(), entry.modelId(), entry.poserId(),
                entry.textureResourceId(), entry.poseStateSha256(), verification.sha256(),
                Integer.toString(verification.sourceQuadCount()),
                Integer.toString(verification.retainedQuadCount()),
                Integer.toString(verification.droppedDegenerateQuadCount()),
                coordinate(bounds.minimum().x()), coordinate(bounds.minimum().y()),
                coordinate(bounds.minimum().z()), coordinate(bounds.maximum().x()),
                coordinate(bounds.maximum().y()), coordinate(bounds.maximum().z()),
                Integer.toUnsignedString(verification.tintArgb())
        );
        return List.copyOf(row);
    }

    private static String coordinate(float value) {
        return Float.toHexString(value);
    }

    private static void updateString(MessageDigest digest, String value) {
        updateBytes(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateBytes(MessageDigest digest, byte[] value) {
        updateInt(digest, value.length);
        digest.update(value);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
