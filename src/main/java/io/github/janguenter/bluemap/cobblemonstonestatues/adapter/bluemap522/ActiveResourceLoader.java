/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ResourceBlob;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ModelIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Retains the first BlueMap-visible GEO and PNG bytes and verifies their identities. */
final class ActiveResourceLoader {

    private static final int MAX_ROOTS = 4_096;

    private ActiveResourceLoader() {
    }

    static Result load(
            ResourcePack resourcePack,
            Iterable<Path> roots,
            Map<String, ModelIdentity> models,
            Map<String, TextureIdentity> textures
    ) throws IOException, InterruptedException {
        return load(resourcePack, roots, models, textures, ResourceBlob::read);
    }

    static Result load(
            ResourcePack resourcePack,
            Iterable<Path> roots,
            Map<String, ModelIdentity> models,
            Map<String, TextureIdentity> textures,
            BlobReader reader
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(reader, "reader");
        Map<String, Request> requested = new LinkedHashMap<>();
        long requestedBytes = 0L;
        for (ModelIdentity identity : models.values()) {
            requestedBytes = Math.addExact(requestedBytes, identity.size());
            if (requested.putIfAbsent(identity.resourcePath(), new Request(
                    Kind.MODEL, identity.modelId(), identity.size(), identity.sha256()
            )) != null) {
                return Result.invalid("active-resource-path-collision");
            }
        }
        for (TextureIdentity identity : textures.values()) {
            requestedBytes = Math.addExact(requestedBytes, identity.size());
            if (requested.putIfAbsent(identity.resourcePath(), new Request(
                    Kind.TEXTURE, identity.resourceId(), identity.size(), identity.sha256()
            )) != null) {
                return Result.invalid("active-resource-path-collision");
            }
        }
        if (requestedBytes > FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES) {
            return Result.invalid("active-resource-byte-budget");
        }
        Map<String, Captured> firstByPath = new HashMap<>();
        int rootCount = 0;
        for (Path root : roots) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (++rootCount > MAX_ROOTS) {
                return Result.invalid("active-resource-root-budget");
            }
            resourcePack.loadResourcePath(root, activeRoot ->
                    collectFirst(activeRoot, requested, firstByPath, reader));
        }
        if (firstByPath.size() != requested.size()) {
            return Result.invalid("active-resource-roster-mismatch");
        }
        Map<String, ResourceBlob> retainedModels = new LinkedHashMap<>();
        Map<String, ResourceBlob> retainedTextures = new LinkedHashMap<>();
        for (Map.Entry<String, Request> entry : requested.entrySet()) {
            Captured captured = firstByPath.get(entry.getKey());
            Request request = entry.getValue();
            ResourceBlob blob = captured == null ? null : captured.blob();
            if (blob == null || blob.size() != request.size()
                    || !request.sha256().equals(blob.sha256())) {
                return Result.invalid(request.kind() == Kind.MODEL
                        ? "active-model-hash-drift" : "active-texture-hash-drift");
            }
            Map<String, ResourceBlob> target = request.kind() == Kind.MODEL
                    ? retainedModels : retainedTextures;
            target.put(request.logicalId(), blob);
        }
        return Result.success(retainedModels, retainedTextures);
    }

    private static void collectFirst(
            Path root,
            Map<String, Request> requested,
            Map<String, Captured> output,
            BlobReader reader
    ) throws IOException {
        for (Map.Entry<String, Request> entry : requested.entrySet()) {
            Path resource = root.resolve(entry.getKey());
            if (!Files.isRegularFile(resource)) {
                continue;
            }
            if (output.putIfAbsent(entry.getKey(), new Captured(null)) != null) {
                continue;
            }
            Request request = entry.getValue();
            if (Files.size(resource) != request.size()) {
                continue;
            }
            output.put(entry.getKey(), new Captured(reader.read(resource, request.size())));
        }
    }

    record Result(
            boolean valid,
            String reason,
            Map<String, ResourceBlob> models,
            Map<String, ResourceBlob> textures
    ) {
        Result {
            Objects.requireNonNull(reason, "reason");
            models = Map.copyOf(models);
            textures = Map.copyOf(textures);
            if (valid && (models.isEmpty() || textures.isEmpty())) {
                throw new IllegalArgumentException("valid active closure is empty");
            }
        }

        static Result success(
                Map<String, ResourceBlob> models,
                Map<String, ResourceBlob> textures
        ) {
            return new Result(true, "exact-active-resources", models, textures);
        }

        static Result invalid(String reason) {
            return new Result(false, reason, Map.of(), Map.of());
        }
    }

    private enum Kind {
        MODEL,
        TEXTURE
    }

    private record Request(Kind kind, String logicalId, int size, String sha256) {
    }

    private record Captured(ResourceBlob blob) {
    }

    @FunctionalInterface
    interface BlobReader {
        ResourceBlob read(Path path, int expectedSize) throws IOException;
    }
}
