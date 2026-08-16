/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import com.google.gson.GsonBuilder;
import io.github.janguenter.stoneposeexporter.ExportRecords.Catalog;
import io.github.janguenter.stoneposeexporter.ExportRecords.PoseStateBlob;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministic STORED ZIP writer with one authoritative atomic output. */
final class DeterministicBundleWriter {

    static final String OUTPUT_NAME = "stone-pose-states-atmons-1.2.0.zip";
    private static final LocalDateTime CANONICAL_TIME =
            LocalDateTime.of(1980, 2, 1, 0, 0);
    private static final int MAX_MANIFEST_BYTES = 64 * 1024 * 1024;
    private static final long MAX_OUTPUT_BYTES = 512L * 1024L * 1024L;
    private static final FileOperations NIO = new NioFileOperations();

    private DeterministicBundleWriter() {
    }

    static Result write(Path gameDirectory, Catalog catalog) throws IOException {
        return write(gameDirectory, catalog, NIO, () -> { });
    }

    static Result write(
            Path gameDirectory, Catalog catalog, Runnable publicationGuard
    ) throws IOException {
        return write(gameDirectory, catalog, NIO, publicationGuard);
    }

    static Result write(
            Path gameDirectory, Catalog catalog, FileOperations operations
    ) throws IOException {
        return write(gameDirectory, catalog, operations, () -> { });
    }

    static Result write(
            Path gameDirectory,
            Catalog catalog,
            FileOperations operations,
            Runnable publicationGuard
    ) throws IOException {
        Path directory = gameDirectory.toAbsolutePath().normalize()
                .resolve("exports/atmons-stone-statues");
        Files.createDirectories(directory);
        Path target = directory.resolve(OUTPUT_NAME);
        Path temporary = Files.createTempFile(directory, '.' + OUTPUT_NAME + '.', ".tmp");
        boolean published = false;
        try {
            List<StatePayload> statePayloads = verifiedStatePayloads(catalog);
            byte[] manifest = (new GsonBuilder().disableHtmlEscaping().serializeNulls()
                    .setPrettyPrinting().create().toJson(catalog.manifest()) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            if (manifest.length < 1 || manifest.length > MAX_MANIFEST_BYTES) {
                throw new IllegalArgumentException("export manifest exceeds byte budget");
            }
            try (OutputStream output = Files.newOutputStream(
                    temporary, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            ); ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
                writeStored(zip, "manifest.json", manifest);
                for (StatePayload state : statePayloads) {
                    writeStored(zip, "pose-states/" + state.sha256() + ".bin", state.raw());
                }
            }
            operations.forceFile(temporary);
            long size = Files.size(temporary);
            if (size < 1 || size > MAX_OUTPUT_BYTES) {
                throw new IllegalArgumentException("export ZIP exceeds byte budget");
            }
            String sha256 = Hashing.sha256(temporary);
            publicationGuard.run();
            operations.atomicReplace(temporary, target);
            published = true;
            forceDirectoryBestEffort(operations, directory);
            return new Result(target, sha256, size);
        } finally {
            Files.deleteIfExists(temporary);
            if (!published && Files.exists(temporary)) {
                throw new IOException("failed to clean unpublished export temporary");
            }
        }
    }

    private static List<StatePayload> verifiedStatePayloads(Catalog catalog) {
        long stateBytes = 0L;
        List<StatePayload> payloads = new ArrayList<>(catalog.poseStates().size());
        for (PoseStateBlob state : catalog.poseStates().values().stream()
                .sorted(Comparator.comparing(PoseStateBlob::sha256)).toList()) {
            byte[] raw = state.verifiedRawForWrite();
            stateBytes = Math.addExact(stateBytes, raw.length);
            if (stateBytes > PoseStateStore.MAX_UNIQUE_BYTES) {
                throw new IllegalArgumentException("pose-state bytes exceed aggregate budget");
            }
            payloads.add(new StatePayload(state.sha256(), raw));
        }
        return List.copyOf(payloads);
    }

    private static void forceDirectoryBestEffort(
            FileOperations operations, Path directory
    ) {
        try {
            operations.forceDirectory(directory);
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // Directory fsync is unavailable on some Windows/DrvFS clients. The ZIP file
            // itself was fsynced before its atomic rename and remains authoritative.
        }
    }

    private static void writeStored(
            ZipOutputStream zip, String name, byte[] raw
    ) throws IOException {
        CRC32 checksum = new CRC32();
        checksum.update(raw);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(raw.length);
        entry.setCompressedSize(raw.length);
        entry.setCrc(checksum.getValue());
        entry.setTimeLocal(CANONICAL_TIME);
        entry.setExtra(null);
        entry.setComment(null);
        zip.putNextEntry(entry);
        zip.write(raw);
        zip.closeEntry();
    }

    interface FileOperations {
        void forceFile(Path path) throws IOException;

        void atomicReplace(Path source, Path target) throws IOException;

        void forceDirectory(Path directory) throws IOException;
    }

    private static final class NioFileOperations implements FileOperations {
        @Override
        public void forceFile(Path path) throws IOException {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                channel.force(true);
            }
        }

        @Override
        public void atomicReplace(Path source, Path target) throws IOException {
            try {
                Files.move(source, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "export filesystem does not support atomic replacement", exception
                );
            }
        }

        @Override
        public void forceDirectory(Path directory) throws IOException {
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            }
        }
    }

    record Result(Path path, String sha256, long size) {
    }

    private record StatePayload(String sha256, byte[] raw) {
    }
}
