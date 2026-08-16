/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.profile;

import de.bluecolored.bluemap.core.util.Key;

import java.util.Map;

/** Exact ATM 1.2.0 artifact and block identities. */
public final class ExactProfile {

    /** Format 2 is enabled only for the exact, operator-configured ATM 1.2.0 profile. */
    public static final boolean FORMAT_2_READY = true;
    public static final String PROFILE_ID = "atmons-1.2.0-stone-statues-1.1-cobblemon-1.7.3";
    public static final String MINECRAFT_VERSION = "1.21.1";
    public static final String NEOFORGE_VERSION = "21.1.248";
    public static final int JAVA_FEATURE = 21;
    public static final String EXPORTER_VERSION = "0.1.0-alpha.1";
    public static final String EXPORTER_MOD_ID = "atmons_stone_pose_exporter";
    public static final long EXPORTER_SIZE = 284_840L;
    public static final String EXPORTER_SHA256 =
            "f35727ba2ce5c96abe085df36d5d02534b6a19e1f710eb4c145c0d7243c75b98";
    public static final int LIVE_SPECIES_COUNT = 1_027;
    public static final String STONE_SHA256 =
            "0fece8e5a988b660f2b608a316f88e2572290835fec1acf67d78329133e92f09";
    public static final long STONE_SIZE = 170_977L;
    public static final String COBBLEMON_SHA256 =
            "962d75df4fb649d94863a7a7d130d4d2b3de4da9b3cae4c44b1ce90f37ec0ed5";
    public static final long COBBLEMON_SIZE = 128_748_941L;
    public static final String COBBLEMON_COMMIT =
            "37264aff0f6c90c9f9a79bbdfc712bd11929a38c";
    public static final String BLOCK_ID = "cobblemonstonestatues:pokemon_statue";
    public static final Key BLOCK = Key.parse(BLOCK_ID);
    public static final Key PROXY = Key.parse("cobblemonstonestatues:statue_proxy");
    public static final Map<String, ExactModArtifactDetector.Identity> ARTIFACTS = Map.of(
            "cobblemonstonestatues", new ExactModArtifactDetector.Identity(
                    STONE_SHA256, STONE_SIZE
            ),
            "cobblemon", new ExactModArtifactDetector.Identity(
                    COBBLEMON_SHA256, COBBLEMON_SIZE
            )
    );

    private ExactProfile() {
    }
}
