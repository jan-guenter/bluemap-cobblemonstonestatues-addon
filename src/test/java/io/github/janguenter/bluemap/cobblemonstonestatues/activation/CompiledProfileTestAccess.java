/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.activation;

import de.bluecolored.bluemap.core.util.Key;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.PoseEntry;

import java.util.Map;

/** Narrow cross-package test access to a profile with one precompiled cache entry. */
public final class CompiledProfileTestAccess {

    private CompiledProfileTestAccess() {
    }

    public static CompiledProfile cachedProfile(
            StoneStatuesRuntime.Attempt owner,
            ProfilePreflight.Result preflight,
            Map<String, Key> materialTextures,
            PoseEntry pose,
            ProfilePreflight.Geometry geometry
    ) {
        ProfilePreflight.VerifiedModel model = preflight.models().get(pose.modelId());
        CompiledProfile.CacheKey key = new CompiledProfile.CacheKey(
                pose.modelId(), model.identity().sha256(), pose.poseStateSha256()
        );
        CompiledProfile.GeometryCache cache = new CompiledProfile.GeometryCache(
                CompiledProfile.MAX_CACHE_ENTRIES, CompiledProfile.MAX_CACHE_QUADS
        );
        cache.get(key, () -> geometry, () -> { });
        return new CompiledProfile(owner, preflight, materialTextures, cache);
    }

    public static int cachedEntries(CompiledProfile profile) {
        return profile.cachedEntries();
    }
}
