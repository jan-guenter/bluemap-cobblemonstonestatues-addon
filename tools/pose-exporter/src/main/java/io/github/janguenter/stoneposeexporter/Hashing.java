/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Bounded SHA-256 helpers. */
final class Hashing {

    private Hashing() {
    }

    static String sha256(byte[] raw) {
        return HexFormat.of().formatHex(sha256Digest().digest(raw));
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static DigestResult sha256(InputStream input, long maximumBytes) throws IOException {
        if (maximumBytes < 1) {
            throw new IllegalArgumentException("invalid SHA-256 byte budget");
        }
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[64 * 1024];
        long size = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            size = Math.addExact(size, read);
            if (size > maximumBytes) {
                throw new IllegalArgumentException("hashed resource exceeds byte budget");
            }
            digest.update(buffer, 0, read);
        }
        if (size < 1) {
            throw new IllegalArgumentException("hashed resource is empty");
        }
        return new DigestResult(size, HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    record DigestResult(long size, String sha256) {
        DigestResult {
            if (size < 1 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid SHA-256 result");
            }
        }
    }
}
