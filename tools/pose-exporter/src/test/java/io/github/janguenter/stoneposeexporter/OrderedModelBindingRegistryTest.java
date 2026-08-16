/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderedModelBindingRegistryTest {

    @Test
    void lastBuiltInFactoryCallWinsAndRequiresExactWrapperIdentity() {
        OrderedModelBindingRegistry<String, String, Object> registry =
                new OrderedModelBindingRegistry<>(2, 21L);
        Object first = new Object();
        Object winner = new Object();

        registry.begin();
        registry.record("cobblemon:joltik.geo", "pack-a:first/joltik.geo.json",
                "a", 10L, "a".repeat(64), first);
        registry.record(
                "cobblemon:joltik.geo", "pack-b:last/joltik.geo.json",
                "b", 11L, "b".repeat(64), winner
        );
        registry.complete();

        OrderedModelBindingRegistry.Resolved<String> resolved = registry.resolve(
                "cobblemon:joltik.geo", winner
        );
        assertEquals("pack-b:last/joltik.geo.json", resolved.physicalId());
        assertEquals("b", resolved.packId());
        assertEquals(11L, resolved.size());
        assertEquals("b".repeat(64), resolved.sha256());
        assertEquals(2, resolved.registrationOrdinal());
        assertEquals(1L, registry.generation());
        assertThrows(IllegalArgumentException.class, () ->
                registry.resolve("cobblemon:joltik.geo", first));
    }

    @Test
    void incompleteNestedAndOverBudgetCollectionsFailClosed() {
        OrderedModelBindingRegistry<String, String, Object> registry =
                new OrderedModelBindingRegistry<>(1, 1L);
        assertThrows(IllegalStateException.class, registry::complete);
        assertThrows(IllegalStateException.class, () -> registry.resolve("model", new Object()));
        registry.begin();
        assertThrows(IllegalStateException.class, registry::begin);
        registry.record("first", "first.geo.json", "pack", 1L, "a".repeat(64),
                new Object());
        assertThrows(IllegalArgumentException.class, () ->
                registry.record("second", "second.geo.json", "pack", 1L,
                        "b".repeat(64), new Object()));
    }

    @Test
    void customLaterOverwriteIsRejectedByIdentityEvenWithSameLogicalId() {
        OrderedModelBindingRegistry<String, String, Object> registry =
                new OrderedModelBindingRegistry<>(1, 1L);
        Object builtIn = new Object();
        Object customOverwrite = new Object();
        registry.begin();
        registry.record("model", "physical.geo.json", "pack", 1L,
                "a".repeat(64), builtIn);
        registry.complete();

        assertThrows(IllegalArgumentException.class, () ->
                registry.resolve("model", customOverwrite));
    }
}
