/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.activation;

import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** One-owner encoded resource bytes with read-only stream/buffer views. */
public final class ResourceBlob {

    private final byte[] bytes;
    private final String sha256;

    private ResourceBlob(byte[] bytes) {
        this.bytes = bytes;
        this.sha256 = digest(bytes);
    }

    public static ResourceBlob read(Path path, int expectedSize) throws IOException {
        if (expectedSize < 1 || expectedSize > FormatTwoBudgets.MAX_RESOURCE_BYTES) {
            throw new IllegalArgumentException("resource blob size outside budget");
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] raw = input.readNBytes(expectedSize + 1);
            if (raw.length != expectedSize || input.read() != -1) {
                throw new IOException("resource changed while reading");
            }
            return new ResourceBlob(raw);
        }
    }

    public int size() {
        return bytes.length;
    }

    public String sha256() {
        return sha256;
    }

    public InputStream openStream() {
        return new ByteArrayInputStream(bytes);
    }

    public ByteBuffer readOnlyBuffer() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    private static String digest(byte[] raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
