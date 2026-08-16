/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoseTintTest {

    @Test
    void correlatesExactTruncatingPosableModelWhiteInputTint() {
        int derived = PoseTint.fromWhiteInput(1F, 0.5F, 0.25F, 0.75F);
        assertEquals(0xFF7F3F, derived & 0xFFFFFF);
        assertEquals(0xBF, derived >>> 24);
        assertEquals(derived, PoseTint.requireAuthoritative(derived, derived));
        assertThrows(IllegalArgumentException.class,
                () -> PoseTint.requireAuthoritative(derived, derived ^ 1));
    }
}
