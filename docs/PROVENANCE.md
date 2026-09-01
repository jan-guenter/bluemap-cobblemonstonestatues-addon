# Provenance

The exact binary identities live in `provenance/upstreams.json` and the
packaged `exact-artifacts.json`. Cobblemon's embedded build commit is
`37264aff0f6c90c9f9a79bbdfc712bd11929a38c`; that exact object is available in
the local evidence checkout and is used only to audit the public API behavior.

Four inspected Pokémon model-license files are byte-identical and contain
Creative Commons legal text requiring attribution and noncommercial use. They
do not identify a title, version, URI, or ShareAlike label in the embedded
text, so this project records only those observed terms and redistributes no
asset or derived mesh.

Cobblemon Stone Statues' NeoForge descriptor says All Rights Reserved while its
embedded project license text says CC0. This project does not resolve that
conflict by copying its implementation; the exporter invokes the installed
public bridge and neither artifact contains Stone Statues program code.

`BakedGeometryTopology.java` is the sole included Cobblemon-derived Source
Code Form. It is an MPL-2.0 modification of exact commit `37264aff...` and
retains the exact upstream path, attribution, and modification notice in its
header.

The production BlueMap adapter layer preserves LGPL-2.1-only on the exact five
files listed in `LICENSES.md`. That framework came from the first-party
BlueMap Botany Pots Add-on release `v0.1.0-alpha.1`, commit
`f40eed6c1f7f30356bcdfabbc3e2a6455fec7884`, whose complete project license is
LGPL-2.1-only. The helper does not compile or package this adapter layer.

Four MIT adapter helpers compile from BlueMap Add-on Adapter API
`0.1.0-alpha.2`, commit `e81f08bc4bfbf02d810ec8949a019130e2e61634`, and
source tree `2f974c9bb2ba13888d69682f86f30f58922d30eb`. The add-on packages the
module license but not its JAR.

The replacement box compiler was produced behind a clean-room boundary from
the Microsoft Bedrock geometry schema/visual documentation and a sanitized
13-case raw-f32 runtime fixture. The normative local specification is
`provenance/cleanroom/stone-box-replay/SPEC.md`; the fixture SHA-256 is
`b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f`.
The ordinary test task always runs fixture parity. Neither fixture nor other
test evidence is packaged.
