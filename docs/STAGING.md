# Staging

> **Successful physical checkpoint:** the 284,840-byte helper completed all
> 7,869 routes at `2026-08-16 12:31:16 +02:00`. The canonical operator-local
> ZIP is 99,408,820 bytes with SHA-256
> `edc1b15d97267a28f9ef946a0c645e924b75322afb976aeeb220ad8c5056eefa`.
> Its 4,833 entries, 4,832 ATS4 states, six attestation roots, exact closure,
> and production Java loader verification all pass. Format-2 production code
> is enabled for the controlled resource-backed staging gate below.

> **Successful visual checkpoint:** on 2026-08-16 the exact 286,752-byte
> production JAR
> (`9800484109aa7e571f393aa96b069e6248186c35103b6e26fd9dad920e4efcd4`)
> completed resource-backed preflight after
> BlueMap's roots were ordered to match the client-attested winners. The
> gallery verified `8` cells with `0` failures. A forced fresh render showed
> Joltik, Bulbasaur, Pikachu, Hisuian Decidueye, and both material controls;
> the unknown master and orphan proxy remained invisible. The owner accepted
> the exact review result. This permits the bounded prerelease but not a
> production deployment.

> **Failed physical checkpoint; helper revoked:** the command-enabled ATS3
> helper was exactly `atmons-stone-pose-exporter-0.1.0-alpha.1.jar`, 278,486
> bytes, SHA-256
> `9b41f38e105ce94a8eb56179e9a5c939f953c84d171df1bc2001bdc0650f9728`.
> It was installed and executed only after the separately authorized local GO.
> The run aborted without output on its first work item because ATS3 lost a
> mirrored signed-zero normal bit. That identity and all earlier helper
> identities are revoked. The first command-enabled ATS4 helper was exactly
> 281,461 bytes with SHA-256
> `6c3590d3f3bb23689b95d88afcd677c3c50cbe876f0de90cdf7ee63d11f4fe38`.
> Its separately authorized run also aborted without output, on Archaludon's
> exact-zero-scale draw path with nonfinite public normals, so it is revoked.
> The first sanitizer-corrected ATS4 helper was exactly 285,871 bytes with
> SHA-256
> `9dbac38dda2e5ddadb5d75cad2b612a5112d4598478964bcc59d827c0ad40e08`.
> It progressed past the geometry failures, then aborted without output after
> at least 2,756 of 7,869 items because `NativeImage.read(byte[])` exhausted
> LWJGL's fixed 64 KiB `MemoryStack` on a compressed PNG; it is revoked. The
> streaming-decode helper was exactly 285,905 bytes with SHA-256
> `e775fa414d218e89a8d21ab89ff47417cca6627452459629520d90f9e1b197ae`.
> It reached 3,936 of 7,869 items before a redundant geometric-alignment
> heuristic rejected Mega Malamar's authentic `-0.001`-scale light plane, so
> it is revoked. The raw-normal-identity replacement is exactly 284,840 bytes
> with SHA-256
> `f35727ba2ce5c96abe085df36d5d02534b6a19e1f710eb4c145c0d7243c75b98`.
> Every failed helper identity remains revoked.

## Failed ATS3 export evidence

The controlled run started at `2026-08-14 08:02:57.602 +02:00`, reported
`0/7869`, and failed at `08:03:45.560` on `cobblemon:abomasnow`, form `Mega`,
aspect `mega`, requested pose `battle-idle`, pose index zero. Bridge and replay
agreed on 1,002 source quads, 622 retained quads, 380 exact-degenerate drops,
tint, and raw bounds, but their strict multiset SHA-256 values differed. Chat
reported `aborted without output`; no final ZIP, writer temporary, partial, or
export directory existed afterward.

A bounded exact-runtime probe localized the difference to eight mirrored
cubes in the 167-cube winning Mega Abomasnow model. The affected NORTH normals
differed only in raw X zero sign (`-0.0f` from runtime versus `+0.0f` from ATS3
replay). ATS4 records the four transformed mirror-zero-X non-X-face normals
explicitly; it does not loosen the multiset digest.

## Failed first ATS4 export evidence

The authorized corrected-normal run reached Archaludon Normal, empty aspects,
requested pose `battle-standing`, pose index zero, then failed at
`2026-08-16 06:52:30 +02:00` with `invalid pose-state down normal x`. It
aborted without output. The winning animation applies an exact zero scale axis
on cube-bearing paths. A bounded mapped-runtime reconstruction of the first
failing drawable node emitted 24 vertices for six zero-area quads at only two
distinct positions; every normal component was NaN (`0xffc00000`). A public
runtime `scale(1,1,0)` fixture reproduced the failure. The corrected helper
temporarily makes only nodes meeting the exact-zero-ancestry, finite-affine-
position, nonfinite-public-normal predicate effective `skipDraw` for the same
synchronous bridge render, then restores the original flags on every exit.

Controlled server-side staging uses the already accepted bundle; do not rerun
the exporter for this checkpoint:

1. Run `verifyPhysicalBundle` with the exact path and SHA below, then build and
   audit the format-2-enabled production JAR.
2. Keep the bundle in a private immutable upload directory outside BlueMap's
   pack directory, webroot, and every release artifact. Install the production
   add-on JAR in `config/bluemap/packs`. Because BlueMap scans its mods folder
   in an unspecified filesystem order, also place `.zip`-named symbolic links
   to the five original exact winner JARs there, reverse-sorted in descending
   attested priority: CCC compatibility (426), All the Mons (418), Z-A Mega
   (308), Mega Showdown (307), and Cobblemon (196). Do not copy or extract
   their contents. This makes BlueMap and the accepted client export select
   the same bytes without exposing the aliases to add-on or mod-JAR detection.
3. Configure the bundle path and SHA through exactly one mechanism below.
4. Verify the exact server mod roots before startup. This export has no
   client-only winning pack to mirror.
5. Start from a reviewed zero-replica disposable workload and require the full
   resource-backed replay preflight, generated-texture bake, and active runtime
   before rendering.
6. Load the existing eight-cell gallery, force a real rerender, and confirm the
   four representatives plus both material controls appear while the unknown
   master and orphan proxy remain invisible. This is representative visual
   evidence, not an exhaustive matrix.

Exporter failures, changing resource generation/fingerprint, empty geometry,
non-quad streams, bridge-versus-clean-room replay mismatch, inconsistent
substitute resolution, missing resources, and incomplete closure abort the
whole temporary export.

The accepted bundle is selected with:

```text
-DbluemapCobblemonStatues.poseBundle=/absolute/server/path/stone-pose-states-atmons-1.2.0.zip
-DbluemapCobblemonStatues.poseBundleSha256=edc1b15d97267a28f9ef946a0c645e924b75322afb976aeeb220ad8c5056eefa
```

No client-only winning resource pack needs mirroring for this export. Its
winners are vanilla plus the exact server-installed `allthemons`, `cobblemon`,
`mega_showdown`, Complete Cobblemon Collection compatibility, and `zamega`
mod packs.
