/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.catalog;

import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.Attestation;
import io.github.janguenter.bluemap.cobblemonstonestatues.profile.ExactProfile;

import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit operator-local gate for a complete physical-client format-2 export. */
public final class PhysicalBundleVerifier {

    private static final int EXPECTED_CHOICES = 1_457;
    private static final int EXPECTED_POSES = 7_869;
    private static final int EXPECTED_MODELS = 1_338;
    private static final int EXPECTED_STATES = 4_832;
    private static final int EXPECTED_TEXTURES = 1_366;

    private PhysicalBundleVerifier() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("expected absolute bundle path and SHA-256");
        }
        Path bundle = Path.of(arguments[0]);
        String expectedSha = arguments[1];
        PoseCatalog catalog = PoseCatalogLoader.loadVerified(
                bundle, expectedSha, ExactProfile.LIVE_SPECIES_COUNT
        );
        Attestation attestation = catalog.attestation();
        if (attestation.choiceCount() != EXPECTED_CHOICES
                || attestation.poseCount() != EXPECTED_POSES
                || attestation.modelCount() != EXPECTED_MODELS
                || attestation.poseStateCount() != EXPECTED_STATES
                || attestation.textureCount() != EXPECTED_TEXTURES
                || catalog.poses().size() + catalog.fallbacks().size() != EXPECTED_POSES) {
            throw new IllegalArgumentException("physical export count identity mismatch");
        }
        System.out.printf(
                "PHYSICAL_BUNDLE_VERIFIED path=%s size=%d sha=%s species=%d choices=%d "
                        + "poses=%d models=%d states=%d textures=%d%n",
                bundle.toRealPath(), Files.size(bundle), catalog.bundleSha256(),
                attestation.speciesCount(), attestation.choiceCount(),
                attestation.poseCount(), attestation.modelCount(),
                attestation.poseStateCount(), attestation.textureCount()
        );
    }
}
