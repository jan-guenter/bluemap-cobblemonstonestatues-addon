/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatueSelectionTest {

    @Test
    void normalizesOnlyThePersistedFieldsStoneNormalizes() {
        StatueSelection selection = new StatueSelection(
                "cobblemon:pikachu", " Normal ", "female, shiny, female, ,",
                " shoulder_left ", null, null, -1, 91L
        );

        assertEquals("cobblemon:pikachu", selection.speciesId());
        assertEquals(" Normal ", selection.formId());
        assertEquals("female,shiny", selection.aspectsCsv());
        assertEquals("shoulder_left", selection.animationName());
        assertEquals(StatueSelection.Scale.NORMAL, selection.scale());
        assertEquals(StatueSelection.Material.STONE, selection.material());
        assertEquals(7, selection.rotation45());
        assertEquals(91L, selection.stoneSeed());
    }

    @Test
    void defaultsBlankSpeciesAndParsesBoundedEnums() {
        StatueSelection selection = new StatueSelection(
                " ", null, null, null,
                StatueSelection.Scale.fromSerialized("double"),
                StatueSelection.Material.fromSerialized("gold_block"), 11, 0L
        );

        assertEquals("cobblemon:bulbasaur", selection.speciesId());
        assertEquals("", selection.formId());
        assertEquals("", selection.aspectsCsv());
        assertEquals("", selection.animationName());
        assertEquals(StatueSelection.Scale.DOUBLE, selection.scale());
        assertEquals(StatueSelection.Material.GOLD_BLOCK, selection.material());
        assertEquals(3, selection.rotation45());
        assertEquals(StatueSelection.Scale.NORMAL,
                StatueSelection.Scale.fromSerialized("enormous"));
        assertEquals(StatueSelection.Material.STONE,
                StatueSelection.Material.fromSerialized("diamond"));
    }
}
