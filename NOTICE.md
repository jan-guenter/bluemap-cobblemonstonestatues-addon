# Notices

Except for the MPL-2.0-covered file identified below, this project is
first-party work licensed under MIT or the file-level LGPL-2.1-only adapter
mapping below.

`BakedGeometryTopology.java` is a modified Source Code Form adapted from
Cobblemon's `TexturedModel.kt`, copyright 2023 Cobblemon Contributors, at exact
commit `37264aff0f6c90c9f9a79bbdfc712bd11929a38c` and path
`common/src/main/kotlin/com/cobblemon/mod/common/client/render/models/`
`blockbench/TexturedModel.kt`. It is distributed under Mozilla Public License
2.0. The modification replaces client `LayerDefinition` output with a strict,
order-neutral topology representation and runtime-state binding used by this
BlueMap integration.

Four production BlueMap adapter files and their focused adapter test preserve
LGPL-2.1-only from the first-party framework reused from BlueMap Botany Pots
Add-on `v0.1.0-alpha.1`, commit
`f40eed6c1f7f30356bcdfabbc3e2a6455fec7884`. The exact file roster is in
`LICENSES.md`. The client-only pose exporter does not compile or package those
adapter files.

The production add-on also source-compiles exactly four MIT classes from the
first-party BlueMap Add-on Adapter API `0.1.0-alpha.2`, commit
`e81f08bc4bfbf02d810ec8949a019130e2e61634`. Its standalone JAR is not bundled
or installed. Both production archives carry its exact MIT license under a
distinct `META-INF/LICENSE-bluemap-addon-adapter-api` path. The client-only
exporter keeps the exact frozen notices from its physically executed
`0.1.0-alpha.1` artifact.

See `LICENSES.md` for the file-level map, `LICENSES/MPL-2.0.txt` for the full
MPL license, `LICENSES/LGPL-2.1-only.txt` for the full LGPL license, and
`SOURCE-OFFER.md` for the version-matched corresponding source location.
Production binary and sources artifacts contain all applicable copies under
`META-INF`; helper artifacts contain the MIT/MPL materials applicable to them.

No Cobblemon or Stone Statues classes, Pokémon models, textures, animation or
poser files, resource packs, generated meshes, or operator-local export bundle
are distributed. The production add-on reads operator-installed resources;
the client helper observes the installed runtime and emits compact local
metadata only.
