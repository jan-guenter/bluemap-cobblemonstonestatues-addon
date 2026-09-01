/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap523;

import io.github.janguenter.bluemap.addon.adapter.api.bluemap523.BlueMapRuntimeCompatibility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerRuntimeCompatibilityTest {

    @Test
    void selectsOnlyTheFeatureBackportAndExactHostJoml() {
        assertTrue(BlueMapRuntimeCompatibility.matches(
                "5.22-feature.backport-5.23-stateless-java-web-server-46",
                "7e07f4e74ec1e92a6ead9aa1e66054af3e133aac"
        ));
        assertFalse(BlueMapRuntimeCompatibility.matches(
                "5.22-feature.backport-5.23-stateless-java-web-server-46",
                "0000000000000000000000000000000000000000"
        ));
        assertTrue(JomlRuntimeCompatibility.matchesCurrent());
    }
}
