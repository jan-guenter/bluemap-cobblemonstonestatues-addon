/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

/** Shared bounded validation for opaque resource-pack ledger identifiers. */
final class MetadataValidation {

    private static final int MAX_PACK_ID_LENGTH = 4_096;

    private MetadataValidation() {
    }

    static boolean safePackId(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_PACK_ID_LENGTH
                || value.indexOf('\\') >= 0) {
            return false;
        }
        for (String segment : value.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.FORMAT) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }
}
