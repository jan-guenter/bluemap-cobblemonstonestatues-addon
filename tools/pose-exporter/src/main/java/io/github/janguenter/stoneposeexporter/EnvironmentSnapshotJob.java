/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.stoneposeexporter.ExportRecords.CodeSource;
import io.github.janguenter.stoneposeexporter.ExportRecords.ResourcePack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Incremental full code-source and client-pack fingerprint snapshot. */
final class EnvironmentSnapshotJob implements AutoCloseable {

    private static final long MAX_TOTAL_CODE_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final long MAX_PACK_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_RESOURCE_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_MODS = 4_096;
    private static final int MAX_PACKS = 1_024;
    private static final int MAX_RESOURCES_PER_PACK = 100_000;

    private final List<ModSpec> mods;
    private final List<Path> uniqueCodePaths;
    private final List<PackResources> packs;
    private final Map<Path, FileIdentity> codeFiles = new HashMap<>();
    private final List<ResourcePack> resourcePacks = new ArrayList<>();
    private final byte[] buffer = new byte[64 * 1024];
    private int codeIndex;
    private int packIndex;
    private StreamState stream;
    private PackState packState;
    private long totalCodeBytes;
    private Snapshot result;

    EnvironmentSnapshotJob(Minecraft minecraft) throws IOException {
        mods = captureModSpecs();
        uniqueCodePaths = mods.stream().map(ModSpec::path).distinct()
                .sorted(Comparator.comparing(Path::toString)).toList();
        packs = minecraft.getResourceManager().listPacks().toList();
        if (packs.isEmpty() || packs.size() > MAX_PACKS) {
            throw new IllegalArgumentException("active resource-pack ledger outside budget");
        }
        Set<String> packIds = new HashSet<>();
        for (PackResources pack : packs) {
            String packId = pack.packId();
            if (packId == null || packId.isBlank() || packId.length() > 4_096
                    || packId.indexOf('\0') >= 0 || packId.indexOf('\u001f') >= 0
                    || packId.indexOf('\n') >= 0 || packId.indexOf('\r') >= 0
                    || !packIds.add(packId)) {
                throw new IllegalArgumentException("active resource-pack ID is unsafe/duplicate");
            }
        }
    }

    boolean advance(long byteBudget) throws IOException {
        if (result != null) {
            return true;
        }
        if (byteBudget < buffer.length) {
            throw new IllegalArgumentException("snapshot byte budget is too small");
        }
        long remaining = byteBudget;
        while (remaining > 0 && codeIndex < uniqueCodePaths.size()) {
            if (stream == null) {
                Path path = uniqueCodePaths.get(codeIndex);
                stream = StreamState.file(path);
            }
            int read = stream.read(buffer, remaining);
            if (read >= 0) {
                remaining -= read;
                continue;
            }
            FileIdentity identity = stream.finishFile();
            totalCodeBytes = Math.addExact(totalCodeBytes, identity.size());
            if (totalCodeBytes > MAX_TOTAL_CODE_BYTES) {
                throw new IllegalArgumentException("loaded-mod code sources exceed byte budget");
            }
            codeFiles.put(uniqueCodePaths.get(codeIndex), identity);
            stream = null;
            codeIndex++;
        }
        while (remaining > 0 && codeIndex == uniqueCodePaths.size()
                && packIndex < packs.size()) {
            if (packState == null) {
                packState = PackState.create(packs.get(packIndex), packIndex);
            }
            long before = remaining;
            remaining = packState.advance(buffer, remaining);
            if (packState.complete()) {
                resourcePacks.add(packState.finish());
                packState = null;
                packIndex++;
            }
            if (before == remaining && packState != null) {
                throw new IllegalStateException("resource-pack snapshot made no progress");
            }
        }
        if (codeIndex == uniqueCodePaths.size() && packIndex == packs.size()) {
            List<CodeSource> sources = new ArrayList<>(mods.size());
            for (ModSpec mod : mods) {
                FileIdentity identity = codeFiles.get(mod.path());
                sources.add(new CodeSource(
                        mod.modId(), mod.path().getFileName().toString(),
                        identity.size(), identity.sha256()
                ));
            }
            result = new Snapshot(sources, resourcePacks);
        }
        return result != null;
    }

    Snapshot result() {
        if (result == null) {
            throw new IllegalStateException("environment snapshot is incomplete");
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException exception) {
                failure = exception;
            }
        }
        if (packState != null) {
            try {
                packState.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static List<ModSpec> captureModSpecs() throws IOException {
        List<ModSpec> result = new ArrayList<>();
        Set<String> modIds = new HashSet<>();
        for (IModInfo info : ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId)).toList()) {
            var owningFile = info.getOwningFile().getFile();
            Path reported = owningFile.getFilePath();
            ResolvedCodeSource resolved = resolveRegularCodeSource(
                    info.getModId(), reported,
                    source -> source.toRealPath()
            );
            Path path = resolved.path();
            if (!resolved.canonical()) {
                LogUtils.getLogger().warn(
                        "Loaded mod {} code source {} reported by {} as {} returned no "
                                + "canonical path; hashing the regular reported source",
                        info.getModId(), path.getFileName(),
                        owningFile.getClass().getName(), path.getClass().getName()
                );
            }
            if (!Files.isRegularFile(path) || !modIds.add(info.getModId())
                    || !safeBaseName(path.getFileName().toString())) {
                throw new IllegalArgumentException("loaded mod code source is not a unique file");
            }
            result.add(new ModSpec(info.getModId(), path));
            if (result.size() > MAX_MODS) {
                throw new IllegalArgumentException("loaded-mod ledger outside budget");
            }
        }
        return List.copyOf(result);
    }

    static ResolvedCodeSource resolveRegularCodeSource(
            String modId, Path reported, RealPathResolver realPathResolver
    ) throws IOException {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("loaded mod has an invalid ID");
        }
        if (reported == null) {
            throw new IllegalArgumentException(
                    "loaded mod has no reported code-source path: " + modId
            );
        }
        Path canonical;
        try {
            canonical = realPathResolver.resolve(reported);
        } catch (IOException exception) {
            throw new IOException(
                    "loaded mod code-source canonicalization failed: " + modId,
                    exception
            );
        }
        Path selected = canonical == null ? reported : canonical;
        if (!Files.isRegularFile(selected)) {
            throw new IllegalArgumentException(
                    "loaded mod code source is not a regular file: " + modId
            );
        }
        return new ResolvedCodeSource(selected, canonical != null);
    }

    private static boolean safeBaseName(String value) {
        if (value == null || value.isBlank() || value.length() > 512
                || ".".equals(value) || "..".equals(value)
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            return false;
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

    record Snapshot(List<CodeSource> codeSources, List<ResourcePack> resourcePacks) {
        Snapshot {
            codeSources = List.copyOf(codeSources);
            resourcePacks = List.copyOf(resourcePacks);
        }
    }

    private record ModSpec(String modId, Path path) {
    }

    private record FileIdentity(long size, String sha256) {
    }

    record ResolvedCodeSource(Path path, boolean canonical) {
    }

    @FunctionalInterface
    interface RealPathResolver {
        Path resolve(Path path) throws IOException;
    }

    private static final class PackState implements AutoCloseable {
        private final PackResources pack;
        private final int order;
        private final List<ResourceSpec> resources;
        private final List<List<String>> rows = new ArrayList<>();
        private int resourceIndex;
        private StreamState stream;
        private long totalBytes;

        private PackState(PackResources pack, int order, List<ResourceSpec> resources) {
            this.pack = pack;
            this.order = order;
            this.resources = resources;
        }

        static PackState create(PackResources pack, int order) {
            Map<String, IoSupplier<InputStream>> resources = new TreeMap<>();
            IoSupplier<InputStream> metadata = pack.getRootResource("pack.mcmeta");
            if (metadata != null) {
                resources.put("pack.mcmeta", metadata);
            }
            for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES).stream()
                    .sorted().toList()) {
                pack.listResources(PackType.CLIENT_RESOURCES, namespace, "", (id, supplier) -> {
                    String key = resourceKey(id);
                    if (resources.put(key, supplier) != null
                            || resources.size() > MAX_RESOURCES_PER_PACK) {
                        throw new IllegalArgumentException(
                                "resource-pack roster collision/budget"
                        );
                    }
                });
            }
            List<ResourceSpec> specs = resources.entrySet().stream()
                    .map(entry -> new ResourceSpec(entry.getKey(), entry.getValue()))
                    .toList();
            return new PackState(pack, order, specs);
        }

        long advance(byte[] buffer, long budget) throws IOException {
            long remaining = budget;
            while (remaining > 0 && resourceIndex < resources.size()) {
                ResourceSpec resource = resources.get(resourceIndex);
                if (stream == null) {
                    stream = StreamState.resource(resource.supplier(), MAX_RESOURCE_BYTES);
                }
                int read = stream.read(buffer, remaining);
                if (read >= 0) {
                    remaining -= read;
                    continue;
                }
                FileIdentity identity = stream.finishFile();
                totalBytes = Math.addExact(totalBytes, identity.size());
                if (totalBytes > MAX_PACK_BYTES) {
                    throw new IllegalArgumentException("resource pack exceeds byte budget");
                }
                rows.add(List.of(
                        resource.name(), Long.toString(identity.size()), identity.sha256()
                ));
                stream = null;
                resourceIndex++;
            }
            return remaining;
        }

        boolean complete() {
            return resourceIndex == resources.size();
        }

        ResourcePack finish() {
            if (!complete()) {
                throw new IllegalStateException("resource pack fingerprint is incomplete");
            }
            List<List<String>> fingerprintRows = new ArrayList<>(rows.size() + 1);
            fingerprintRows.add(List.of(
                    "__pack__", Integer.toString(order), pack.packId(),
                    pack.getClass().getName()
            ));
            fingerprintRows.addAll(rows);
            return new ResourcePack(
                    order, pack.packId(), CatalogRoots.root("active-pack", fingerprintRows)
            );
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }

        private static String resourceKey(ResourceLocation id) {
            return "assets/" + id.getNamespace() + '/' + id.getPath();
        }
    }

    private record ResourceSpec(String name, IoSupplier<InputStream> supplier) {
    }

    private static final class StreamState implements AutoCloseable {
        private final InputStream input;
        private final MessageDigest digest = sha256();
        private final long maximum;
        private long size;
        private boolean finished;

        private StreamState(InputStream input, long maximum) {
            this.input = input;
            this.maximum = maximum;
        }

        static StreamState file(Path path) throws IOException {
            return new StreamState(Files.newInputStream(path), 2L * 1024L * 1024L * 1024L);
        }

        static StreamState resource(IoSupplier<InputStream> supplier, long maximum)
                throws IOException {
            return new StreamState(supplier.get(), maximum);
        }

        int read(byte[] buffer, long budget) throws IOException {
            int request = (int) Math.min(buffer.length, budget);
            int read = input.read(buffer, 0, request);
            if (read < 0) {
                finished = true;
                input.close();
                return -1;
            }
            if (read == 0) {
                throw new IOException("fingerprint stream returned no data");
            }
            size = Math.addExact(size, read);
            if (size > maximum) {
                throw new IllegalArgumentException("fingerprint stream exceeds byte budget");
            }
            digest.update(buffer, 0, read);
            return read;
        }

        FileIdentity finishFile() {
            if (!finished) {
                throw new IllegalStateException("fingerprint stream is not finished");
            }
            return new FileIdentity(size, HexFormat.of().formatHex(digest.digest()));
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        private static MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable", exception);
            }
        }
    }
}
