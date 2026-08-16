/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Canonical length-framed roots consumed by the production loader. */
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
