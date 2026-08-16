/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentSnapshotJobTest {

    @TempDir
    Path temporary;

    @Test
    void nullCanonicalPathRetainsAndHashesTheRegularReportedSource() throws IOException {
        Path reported = Files.writeString(temporary.resolve("loaded-mod.jar"), "bytes");

        EnvironmentSnapshotJob.ResolvedCodeSource resolved =
                EnvironmentSnapshotJob.resolveRegularCodeSource(
                "loaded_mod", reported, ignored -> null
        );

        assertEquals(reported, resolved.path());
        assertTrue(Files.isRegularFile(resolved.path()));
        assertFalse(resolved.canonical());
    }

    @Test
    void absentOrNonFileReportedSourcesRemainFatalWithModContext() {
        IllegalArgumentException absent = assertThrows(
                IllegalArgumentException.class,
                () -> EnvironmentSnapshotJob.resolveRegularCodeSource(
                        "virtual_mod", null, ignored -> null
                )
        );
        assertTrue(absent.getMessage().contains("virtual_mod"));

        IllegalArgumentException directory = assertThrows(
                IllegalArgumentException.class,
                () -> EnvironmentSnapshotJob.resolveRegularCodeSource(
                        "directory_mod", temporary, ignored -> null
                )
        );
        assertTrue(directory.getMessage().contains("directory_mod"));
    }
}
