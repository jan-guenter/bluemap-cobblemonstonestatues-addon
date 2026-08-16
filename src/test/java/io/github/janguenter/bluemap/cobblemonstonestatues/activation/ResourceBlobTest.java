/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.activation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceBlobTest {

    @TempDir
    Path temporary;

    @Test
    void exposesOnlyIndependentReadOnlyViewsOfItsOwnedBytes() throws Exception {
        byte[] raw = {1, 2, 3, 4};
        Path path = temporary.resolve("resource.bin");
        Files.write(path, raw);
        ResourceBlob blob = ResourceBlob.read(path, raw.length);

        ByteBuffer buffer = blob.readOnlyBuffer();
        assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 9));
        assertArrayEquals(raw, blob.openStream().readAllBytes());
        blob.openStream().read();
        assertArrayEquals(raw, blob.openStream().readAllBytes());
        assertFalse(Arrays.stream(ResourceBlob.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("raw")));
    }
}
