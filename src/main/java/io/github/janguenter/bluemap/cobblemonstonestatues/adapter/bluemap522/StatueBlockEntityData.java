/*
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * Adapted from the first-party BlueMap Botany Pots adapter scaffold at
 * v0.1.0-alpha.1 / f40eed6c1f7f30356bcdfabbc3e2a6455fec7884.
 * Modified in 2026 for the Cobblemon Stone Statues integration.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTName;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection;

/** BlueNBT DTO retaining only Stone Statues 1.1's top-level selection fields. */
public final class StatueBlockEntityData extends MCABlockEntity {

    @NBTName("SpeciesId")
    private String speciesId;
    @NBTName("FormId")
    private String formId;
    @NBTName("AspectsCsv")
    private String aspectsCsv;
    @NBTName("AnimationName")
    private String animationName;
    @NBTName("Scale")
    private String scale;
    @NBTName("Material")
    private String material;
    @NBTName("Rotation45")
    private int rotation45;
    @NBTName("StoneSeed")
    private long stoneSeed;

    public StatueBlockEntityData() {
    }

    public StatueSelection selection() {
        if (speciesId == null) {
            return new StatueSelection(
                    "cobblemon:bulbasaur", "Normal", "", "portrait",
                    StatueSelection.Scale.NORMAL, StatueSelection.Material.STONE,
                    0, 0L
            );
        }
        return new StatueSelection(
                speciesId,
                formId,
                aspectsCsv,
                animationName,
                StatueSelection.Scale.fromSerialized(scale),
                StatueSelection.Material.fromSerialized(material),
                rotation45,
                stoneSeed
        );
    }

    String retainedSpeciesId() {
        return speciesId;
    }

    String retainedFormId() {
        return formId;
    }

    String retainedAspectsCsv() {
        return aspectsCsv;
    }

    String retainedAnimationName() {
        return animationName;
    }

    String retainedScale() {
        return scale;
    }

    String retainedMaterial() {
        return material;
    }

    int retainedRotation45() {
        return rotation45;
    }

    long retainedStoneSeed() {
        return stoneSeed;
    }
}
