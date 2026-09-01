# Release artifact boundary

The owner accepted the combined All the Mons 1.2.0 render on 2026-09-01.
The tag-gated release workflow is prepared for the immutable
`0.1.0-alpha.2` prerelease. Its GitHub Release asset allowlist contains exactly
these four version-matched JARs:

1. `bluemap-cobblemonstonestatues-addon-<version>.jar`
2. `bluemap-cobblemonstonestatues-addon-<version>-sources.jar`
3. `atmons-stone-pose-exporter-<version>.jar`
4. `atmons-stone-pose-exporter-<version>-sources.jar`

The production and helper binary/source pairs must use the same reviewed
version. POM/module metadata may be Maven-published but is not a GitHub Release
asset. Reports, manifests, operator bundles, pose-state blobs, resource packs,
third-party JARs, and test fixtures are never release assets.

The accepted four-JAR identities are:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Production JAR | 291,240 | `20108b9de728a92a91da458d4ba9aa91d0c83ea5fe65549a8b308c97a0c67617` |
| Production sources JAR | 122,460 | `24a953c56bb8b019ce21007d93982b0007209a8c1a06875b420b64d5e4ccedc8` |
| Client exporter JAR | 285,447 | `bdc09212215e463840f364c683b9bdc663968a11bf0aed665a515db4f71510af` |
| Client exporter sources JAR | 104,193 | `a5b1b95be96068e6428589824f834d6a846326ba624e826acb8ad1147c59470e` |

The two Maven modules are
`io.github.jan-guenter:bluemap-cobblemonstonestatues-addon:0.1.0-alpha.2`
and
`io.github.janguenter.stoneposeexporter:atmons-stone-pose-exporter:0.1.0-alpha.2`.
Each publishes its binary JAR, sources JAR, POM, and Gradle module metadata.
The helper module packages its own frozen `THIRD_PARTY.md`, byte-identical to
the notice in the physically executed and accepted helper. That artifact-time
notice records that production activation remained disabled until the catalog,
replay, audit, and staging gates passed; those gates subsequently passed. The
root notice and this release record describe the current production state.

The 278,486-byte command-enabled ATS3 helper with SHA-256
`9b41f38e105ce94a8eb56179e9a5c939f953c84d171df1bc2001bdc0650f9728`
was used only for its separately authorized local physical-client checkpoint.
It deterministically aborted without output because ATS3 did not preserve a
mirrored signed-zero normal bit, and it is revoked. The first 281,461-byte ATS4
helper is also revoked after its authorized run aborted without output on an
exact-zero-scale Archaludon draw path with nonfinite public normals. The
first sanitizer-corrected ATS4 helper was 285,871 bytes with SHA-256
`9dbac38dda2e5ddadb5d75cad2b612a5112d4598478964bcc59d827c0ad40e08`.
Its authorized run aborted without output when the byte-array PNG decoder
exhausted LWJGL's fixed 64 KiB `MemoryStack`, so it is revoked. The
streaming-decode helper was 285,905 bytes with SHA-256
`e775fa414d218e89a8d21ab89ff47417cca6627452459629520d90f9e1b197ae`.
Its authorized run reached 3,936 of 7,869 items before the redundant
geometric-alignment heuristic rejected Mega Malamar's authentic
`-0.001`-scale light plane, so it is revoked. The raw-normal-identity
replacement is 284,840 bytes with SHA-256
`f35727ba2ce5c96abe085df36d5d02534b6a19e1f710eb4c145c0d7243c75b98`.
Its 99,408,820-byte complete bundle has SHA-256
`edc1b15d97267a28f9ef946a0c645e924b75322afb976aeeb220ad8c5056eefa`
and passed the independent catalog and strict production-loader audits. The
production path then passed resource-backed preflight against the exact
original winning resource roots. The gallery verified eight cells with zero
fixture failures; all four representatives and both vanilla controls rendered,
while the unknown master and orphan proxy remained stock-invisible. The exact
review URL passed an independent browser sanity check and the owner accepted
the result. This authorizes the bounded prerelease, not production deployment.
