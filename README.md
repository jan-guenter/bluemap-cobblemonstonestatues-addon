# BlueMap Cobblemon Stone Statues Add-on

This is a BlueMap 5.22 add-on for Cobblemon Stone Statues 1.1 in the exact All
the Mons 1.2.0 environment. Version `0.1.0-alpha.1` is the owner-accepted
bounded prerelease; it is not a generic compatibility or production-deployment
claim.

The hybrid renderer never redistributes Cobblemon's Pokémon models, textures,
animations, poser JSON, or code-only posers. A separate client-only exporter
asks the exact Stone Statues runtime bridge to resolve every GUI-reachable
species/form pose at time zero. Its compact operator-local ZIP records winning
GEO/PNG byte identities, absolute draw-node matrices, ordinary and
mirror-signed-zero normals, and retained-quad verification digests. It
contains no final quad blobs or source assets. BlueMap will parse the
operator-installed winning GEO/PNG resources
only after the complete bundle and every byte identity pass preflight.

At capture time, one reversible exact-zero-scale sanitizer makes an affected
draw node effective `skipDraw` only when its absolute position is finite but
its public transformed normals are nonfinite. This deliberately omits
flattened NaN-normal planes before both bridge observation and replay; every
other invalid-normal state remains fail-closed.

The exact live staging server reports 1,027 loaded species. Raw JAR counts are
not used as support claims; authoritative choice, pose, model, state, and
texture counts come from the connected exporter run and must be complete.

## Required runtime inputs

- BlueMap backport commit `9be321df995a1103808621d529eb72773e719d4d`
- Cobblemon Stone Statues 1.1, 170,977 bytes, SHA-256
  `0fece8e5a988b660f2b608a316f88e2572290835fec1acf67d78329133e92f09`
- Cobblemon 1.7.3+1.21.1, 128,748,941 bytes, SHA-256
  `962d75df4fb649d94863a7a7d130d4d2b3de4da9b3cae4c44b1ce90f37ec0ed5`
- the raw-normal-identity command-enabled ATS4 client helper, exactly 284,840 bytes,
  SHA-256
  `f35727ba2ce5c96abe085df36d5d02534b6a19e1f710eb4c145c0d7243c75b98`,
  after its independent artifact audit and separate physical-client GO
- an exporter bundle created against the exact connected client after resource
  reload and species synchronization
- every original winning client resource pack mirrored into BlueMap's resource
  roots when the exporter identifies a client-only GEO or texture winner

Set both `bluemapCobblemonStatues.poseBundle` and
`bluemapCobblemonStatues.poseBundleSha256` as JVM system properties, or their
documented environment-variable equivalents. Any mismatch keeps the invisible
stock behavior.

The 278,486-byte command-enabled ATS3 helper was installed and executed only
after its controlled physical-client GO. It deterministically aborted without
output on Mega Abomasnow because ATS3 lost a mirrored signed-zero normal bit.
The first 281,461-byte ATS4 helper was then installed and executed after its
own GO, but aborted without output on Archaludon because an exact-zero-scale
draw path produced nonfinite public normals. Both failed identities and the
93,116-byte, 278,192-byte, and 278,438-byte predecessors are revoked. The
285,871-byte corrected ATS4 helper progressed beyond those geometry failures,
but aborted without output after at least 2,756 of 7,869 items because
`NativeImage.read(byte[])` exhausted LWJGL's fixed 64 KiB `MemoryStack` on a
compressed PNG. That identity is revoked. The 285,905-byte streaming-decode
helper then reached 3,936 of 7,869 items before rejecting Mega Malamar's valid
`-0.001`-scale light plane with a redundant geometric-alignment heuristic; it
also aborted without output and is revoked. The 284,840-byte replacement
removes that ill-conditioned heuristic while retaining exact raw live-versus-
replay multiset equality, and passed its full helper gate. Its complete
physical-client export contains all 7,869 routes and passed the strict
production loader plus independent ZIP, ATS4, closure, and attestation-root
audits. The 99,408,820-byte operator-local bundle has SHA-256
`edc1b15d97267a28f9ef946a0c645e924b75322afb976aeeb220ad8c5056eefa`.
Format-2 production code passed its resource-backed preflight and bounded
eight-cell gallery review on 2026-08-16. The exact release artifact remains
fail-closed without that operator-local bundle and its winning resources.

## Build

```bash
gradle --no-daemon clean check build
gradle --no-daemon generatePomFileForAddonPublication
```

Build the separate client helper with its exact compile-only inputs:

```bash
gradle --no-daemon -p tools/pose-exporter \
  -PstoneStatuesJar=/path/to/cobblemonstonestatues-neoforge-1.1.jar \
  -PcobblemonJar=/path/to/Cobblemon-neoforge-1.7.3+1.21.1.jar \
  clean check build
```

The exact-input gate additionally requires:

```bash
gradle --no-daemon \
  -PstoneStatuesJar=/path/to/cobblemonstonestatues-neoforge-1.1.jar \
  -PcobblemonJar=/path/to/Cobblemon-neoforge-1.7.3+1.21.1.jar \
  verifyPinnedArtifacts
```

Verify the accepted operator-local physical export without packaging it:

```bash
gradle --no-daemon verifyPhysicalBundle \
  -PposeBundle=/absolute/path/stone-pose-states-atmons-1.2.0.zip \
  -PposeBundleSha256=edc1b15d97267a28f9ef946a0c645e924b75322afb976aeeb220ad8c5056eefa
```

The tag-gated release workflow rebuilds both modules twice, requires the four
owner-accepted JAR identities, publishes exactly those four JARs to the GitHub
prerelease, and publishes the production and exporter modules separately to
GitHub Packages. The operator bundle, gallery, reports, POMs, module metadata,
third-party resources, and fixtures are never GitHub Release assets.

The owner accepted the exact controlled gallery on 2026-08-16: all four
representative statues and both material controls rendered, while the unknown
master selection and orphan proxy remained invisible. This acceptance permits
the bounded prerelease; it does not authorize production deployment.

Both modules produce a version-matched `-sources.jar`. Binary and sources
artifacts include the applicable MIT, MPL-2.0, and LGPL-2.1-only texts, the
file-level mixed-license map, notices, and corresponding-source offer. The
client-only helper contains no LGPL adapter source. See
[`LICENSES.md`](LICENSES.md) for the exact file-level boundary.

## Support/fallback boundary

Only `cobblemonstonestatues:pokemon_statue` is routed. Proxy blocks, unknown
species/forms/aspects/poses, malformed state, custom Pokémon not present in the
attested exact export, any resource drift, and any partial render error remain
stock-invisible. Geometry capacity errors propagate after rollback so BlueMap
can retry with capacity growth.

The gallery is intentionally eight cells: four representative statues, two
vanilla material controls, one unknown master selection, and one natural
orphan proxy. It is not a combinatorial species/scale/rotation test matrix.
