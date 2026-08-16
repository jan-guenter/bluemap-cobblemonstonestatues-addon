/*
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Adapted from the first-party BlueMap Botany Pots adapter scaffold at
 * v0.1.0-alpha.1 / f40eed6c1f7f30356bcdfabbc3e2a6455fec7884.
 * Modified in 2026 for the Cobblemon Stone Statues integration.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.Keyed;
import de.bluecolored.bluemap.core.util.Registry;
import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.mca.MCAUtil;
import de.bluecolored.bluemap.core.world.mca.blockentity.BlockEntityType;
import de.bluecolored.bluenbt.NBTWriter;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.StoneStatuesRuntime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** BlueMap 5.22 internal ABI boundary. */
public final class BlueMap522Adapter {

    private static final StoneStatuesRuntime RUNTIME = StoneStatuesRuntime.INSTANCE;
    private static final Key RENDERER_KEY =
            Key.parse("bluemap_cobblemonstonestatues:exact_shape");
    private static final BlockRendererType RENDERER = new BlockRendererType.Impl(
            RENDERER_KEY,
            (pack, gallery, settings) -> new StoneStatuesRenderer(
                    pack, gallery, settings, RUNTIME
            )
    );
    private static final ResourcePack.Extension<StoneStatuesResourceExtension> EXTENSION =
            new StoneStatuesResourceExtensionType(RUNTIME);
    private static final BlockEntityType BLOCK_ENTITY = new BlockEntityType.Impl(
            Key.parse("cobblemonstonestatues:pokemon_statue"),
            StatueBlockEntityData.class
    );

    private BlueMap522Adapter() {
    }

    public static synchronized boolean install() {
        if (!canRegister(BlockRendererType.REGISTRY, RENDERER)
                || !canRegister(ResourcePack.Extension.REGISTRY, EXTENSION)
                || !canRegister(BlockEntityType.REGISTRY, BLOCK_ENTITY)) {
            RUNTIME.failPermanently("registry-collision");
            return false;
        }
        return registerAll(
                () -> RUNTIME.failPermanently("registry-collision"),
                () -> register(BlockRendererType.REGISTRY, RENDERER),
                () -> register(ResourcePack.Extension.REGISTRY, EXTENSION),
                () -> register(BlockEntityType.REGISTRY, BLOCK_ENTITY)
        );
    }

    static boolean registerAll(
            Runnable permanentFailure,
            BooleanSupplier... registrations
    ) {
        Objects.requireNonNull(permanentFailure, "permanentFailure");
        Objects.requireNonNull(registrations, "registrations");
        for (BooleanSupplier registration : registrations) {
            if (!Objects.requireNonNull(registration, "registration").getAsBoolean()) {
                permanentFailure.run();
                return false;
            }
        }
        return true;
    }

    static boolean isExpectedDispatch(Variant variant) {
        return variant != null
                && variant.getRenderer() == RENDERER
                && ResourcePack.MISSING_BLOCK_MODEL.equals(variant.getModel())
                && !variant.isTransformed()
                && !variant.isUvlock()
                && Double.compare(variant.getWeight(), 1D) == 0;
    }

    static boolean probeBlockEntityRetention() {
        try {
            BlockEntity parsed = MCAUtil.BLUENBT.read(
                    new ByteArrayInputStream(createProbeNbt()), BlockEntity.class
            );
            return parsed instanceof StatueBlockEntityData data
                    && BLOCK_ENTITY.getKey().equals(data.getId())
                    && data.getX() == 17 && data.getY() == -23 && data.getZ() == 41
                    && "cobblemon:pikachu".equals(data.retainedSpeciesId())
                    && "Normal".equals(data.retainedFormId())
                    && "female,shiny".equals(data.retainedAspectsCsv())
                    && "shoulder_left".equals(data.retainedAnimationName())
                    && "DOUBLE".equals(data.retainedScale())
                    && "GOLD_BLOCK".equals(data.retainedMaterial())
                    && data.retainedRotation45() == 3
                    && data.retainedStoneSeed() == 91L;
        } catch (IOException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static byte[] createProbeNbt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NBTWriter writer = new NBTWriter(bytes)) {
            writer.beginCompound();
            writer.name("id").value("cobblemonstonestatues:pokemon_statue");
            writer.name("x").value(17);
            writer.name("y").value(-23);
            writer.name("z").value(41);
            writer.name("SpeciesId").value("cobblemon:pikachu");
            writer.name("FormId").value("Normal");
            writer.name("AspectsCsv").value("female,shiny");
            writer.name("AnimationName").value("shoulder_left");
            writer.name("Scale").value("DOUBLE");
            writer.name("Material").value("GOLD_BLOCK");
            writer.name("Rotation45").value(3);
            writer.name("StoneSeed").value(91L);
            writer.endCompound();
        }
        return bytes.toByteArray();
    }

    private static <T extends Keyed> boolean canRegister(Registry<T> registry, T candidate) {
        T existing = registry.get(candidate.getKey());
        return existing == null || existing == candidate;
    }

    private static <T extends Keyed> boolean register(Registry<T> registry, T candidate) {
        T existing = registry.get(candidate.getKey());
        if (existing == null) {
            registry.register(candidate);
            existing = registry.get(candidate.getKey());
        }
        return existing == candidate;
    }
}
