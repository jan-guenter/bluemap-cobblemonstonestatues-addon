/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceAdmissionBudgetTest {

    @Test
    void resourceBoundaryIsTransactional() {
        ResourceAdmissionBudget budget = new ResourceAdmissionBudget();
        int admissions = (int) (FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES
                / FormatTwoBudgets.MAX_RESOURCE_BYTES);
        for (int index = 0; index < admissions; index++) {
            budget.admitTexture(FormatTwoBudgets.MAX_RESOURCE_BYTES);
        }
        assertEquals(FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES, budget.resourceBytes());

        assertThrows(IllegalArgumentException.class, () ->
                budget.requireResourceCapacity(1));
        assertThrows(IllegalArgumentException.class, () -> budget.admitTexture(1));
        assertEquals(FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES, budget.resourceBytes());
        assertEquals(0L, budget.structureUnits());
    }

    @Test
    void structureAndItemFailuresDoNotMutateEitherCounter() {
        ResourceAdmissionBudget budget = new ResourceAdmissionBudget();
        for (int index = 0; index < 128; index++) {
            budget.admitModel(1, 1, 0, 0, 8_191);
        }
        assertThrows(IllegalArgumentException.class, () ->
                budget.admitModel(1, 1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                budget.admitTexture(FormatTwoBudgets.MAX_RESOURCE_BYTES + 1));

        assertEquals(128L, budget.resourceBytes());
        assertEquals(FormatTwoBudgets.MAX_TOTAL_MODEL_STRUCTURE_UNITS,
                budget.structureUnits());
    }

    @Test
    void perModelLimitsRejectBeforeMutation() {
        assertRejectedModel(FormatTwoBudgets.MAX_MODEL_BONES + 1, 0, 0, 1);
        assertRejectedModel(1, FormatTwoBudgets.MAX_MODEL_CUBES + 1, 0, 1);
        assertRejectedModel(1, 0, FormatTwoBudgets.MAX_MODEL_LOCATORS + 1, 1);
        assertRejectedModel(1, 0, 0,
                FormatTwoBudgets.MAX_MODEL_TOPOLOGY_NODES + 1);
    }

    private static void assertRejectedModel(
            int bones, int cubes, int locators, int topologyNodes
    ) {
        ResourceAdmissionBudget budget = new ResourceAdmissionBudget();
        assertThrows(IllegalArgumentException.class, () ->
                budget.admitModel(1, bones, cubes, locators, topologyNodes));
        assertEquals(0L, budget.resourceBytes());
        assertEquals(0L, budget.structureUnits());
    }
}
