/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import kotlin.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/** Noninterfering observer for exact bytes consumed by Cobblemon's built-in GEO factory. */
public final class ModelResourceBindings {

    private static final int MAX_SUCCESSFUL_REGISTRATIONS =
            FormatTwoBudgets.MAX_MODEL_RESOURCES;
    private static final int MAX_MODEL_BYTES = FormatTwoBudgets.MAX_RESOURCE_BYTES;
    private static final long MAX_AGGREGATE_BYTES =
            FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES;
    private static final ModelBindingObserverCore<ResourceLocation, ResourceLocation, Bone>
            OBSERVER = new ModelBindingObserverCore<>(
                    MAX_SUCCESSFUL_REGISTRATIONS, MAX_MODEL_BYTES, MAX_AGGREGATE_BYTES,
                    MetadataValidation::safePackId,
                    (physicalId, logicalId) -> logicalId(physicalId).equals(logicalId)
            );

    private ModelResourceBindings() {
    }

    public static void beginReload() {
        OBSERVER.beginReload();
    }

    public static void beginBuiltInFactory(
            ResourceLocation physicalId, Resource resource
    ) {
        try {
            OBSERVER.beginFactory(physicalId, resource.sourcePackId());
        } catch (RuntimeException exception) {
            OBSERVER.invalidate();
        }
    }

    /** Records the exact byte array stock passes into its UTF-8 TexturedModel parser. */
    public static void observeFactoryBytes(byte[] raw) {
        OBSERVER.observeFactoryBytes(raw);
    }

    public static void finishBuiltInFactory(
            Pair<ResourceLocation, Bone> result
    ) {
        try {
            if (result == null) {
                OBSERVER.finishFactory(null, null);
            } else {
                OBSERVER.finishFactory(result.getFirst(), result.getSecond());
            }
        } catch (RuntimeException exception) {
            OBSERVER.invalidate();
        }
    }

    public static void completeReload() {
        OBSERVER.completeReload();
    }

    static synchronized Binding requireWinner(
            ResourceLocation logicalId, Bone expectedWrapper
    ) {
        OrderedModelBindingRegistry.Resolved<ResourceLocation> resolved =
                OBSERVER.resolve(logicalId, expectedWrapper);
        return new Binding(
                resolved.physicalId(), resolved.packId(), resolved.size(),
                resolved.sha256(), resolved.registrationOrdinal()
        );
    }

    static synchronized long generation() {
        return OBSERVER.generation();
    }

    private static ResourceLocation logicalId(ResourceLocation physicalId) {
        String path = physicalId.getPath();
        if (!path.endsWith(".geo.json")) {
            throw new IllegalArgumentException("built-in model resource is not GEO JSON");
        }
        int separator = path.lastIndexOf('/');
        String fileName = path.substring(separator + 1);
        int extension = fileName.lastIndexOf('.');
        if (extension < 1) {
            throw new IllegalArgumentException("built-in GEO lacks logical basename");
        }
        return ResourceLocation.fromNamespaceAndPath(
                physicalId.getNamespace(), fileName.substring(0, extension)
        );
    }

    record Binding(
            ResourceLocation physicalId,
            String packId,
            long size,
            String sha256,
            int registrationOrdinal
    ) {
        Binding {
            if (physicalId == null || !MetadataValidation.safePackId(packId)
                    || size < 1 || sha256 == null || !sha256.matches("[0-9a-f]{64}")
                    || registrationOrdinal < 1) {
                throw new IllegalArgumentException("invalid winning model binding");
            }
        }
    }

}
