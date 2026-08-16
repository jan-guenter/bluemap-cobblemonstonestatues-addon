/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Exact physical-client-only exporter entrypoint. */
@Mod(value = StonePoseExporterMod.MOD_ID, dist = Dist.CLIENT)
public final class StonePoseExporterMod {

    public static final String MOD_ID = "atmons_stone_pose_exporter";

    @SuppressWarnings("unused")
    private final StonePoseExportController controller;

    public StonePoseExporterMod(IEventBus modEventBus) {
        controller = new StonePoseExportController(modEventBus);
    }
}
