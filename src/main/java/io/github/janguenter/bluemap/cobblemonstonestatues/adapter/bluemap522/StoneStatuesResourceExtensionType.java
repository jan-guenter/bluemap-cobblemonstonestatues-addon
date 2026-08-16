/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.util.Key;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.StoneStatuesRuntime;

/** Resource-pack extension factory registered before resource loading begins. */
final class StoneStatuesResourceExtensionType
        implements ResourcePack.Extension<StoneStatuesResourceExtension> {

    private static final Key KEY =
            Key.parse("bluemap_cobblemonstonestatues:exact_profile");
    private final StoneStatuesRuntime runtime;

    StoneStatuesResourceExtensionType(StoneStatuesRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public Key getKey() {
        return KEY;
    }

    @Override
    public StoneStatuesResourceExtension create(ResourcePack pack) {
        return new StoneStatuesResourceExtension(pack, runtime);
    }
}
