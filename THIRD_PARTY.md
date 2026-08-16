# Third-party boundary

## Included MPL-covered modification

`BakedGeometryTopology.java` is a modification of Cobblemon 1.7.3
`TexturedModel.kt` from exact embedded commit
`37264aff0f6c90c9f9a79bbdfc712bd11929a38c`. Cobblemon Contributors license
that Source Code Form under MPL-2.0. The modified file retains attribution,
identifies the upstream path and changes, and is the only included
Cobblemon-derived Source Code Form. See `LICENSES.md`, the unmodified
`LICENSES/MPL-2.0.txt`, and `SOURCE-OFFER.md`.

## Included first-party LGPL adapter framework

The four production adapter files and one focused test listed in
`LICENSES.md` reuse the first-party BlueMap adapter framework from the
LGPL-2.1-only BlueMap Botany Pots Add-on `v0.1.0-alpha.1`, commit
`f40eed6c1f7f30356bcdfabbc3e2a6455fec7884`. They retain
`SPDX-License-Identifier: LGPL-2.1-only`. The production binary and matching
sources artifact carry the unmodified license text and the complete preferred
source needed to rebuild the adapter. The client helper contains none of these
files.

## Compile-only and operator-installed inputs

- BlueMap 5.22 and the exact Java 21/Minecraft 1.21.1 backport are compile-only
  dependencies licensed MIT.
- JOML 1.10.5 is an MIT-licensed compile-only dependency supplied by the
  pinned Minecraft 1.21.1 runtime. Its exact host JAR has SHA-256
  `cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a`.
  Neither artifact bundles JOML classes.
- The remainder of Cobblemon 1.7.3+1.21.1 program code is MPL-2.0 and stays an
  operator-installed runtime/compile-only input. It is not copied into either
  artifact.
- Cobblemon Pokémon assets carry per-asset terms. The four audited model
  license files contain Creative Commons legal text requiring attribution and
  noncommercial use, without an explicit title, version, URI, or ShareAlike
  label in those files. No Pokémon asset or derived mesh is redistributed.
- Cobblemon Stone Statues 1.1 is an exact runtime input. Its descriptor says
  All Rights Reserved while its embedded project license text says CC0; no
  Stone Statues program source or class is copied into this project.

## Operator-local data

The hybrid catalog contains identifiers, winning resource byte identities,
compact absolute runtime matrices, ordinary/mirror-zero-X normals, scalar
metadata, and verification digests captured through installed runtime APIs. It
contains no
final quad payload, model, texture, poser, or animation asset. It is generated
from the operator's installed artifacts, remains local, and is never packaged
in releases.

The removed prototype that mirrored proprietary client face/PoseStack
implementation details is not present in source or binary artifacts. The
replacement is the independently specified clean-room array-box-UV compiler
in `geometry/cleanroom/BedrockBoxReplay.java`, its narrow JOML adapter, and the
project-owned `CleanRoomGeometryCompiler` binding. Its specification and
13-case sanitized raw-f32 fixture are retained under
`provenance/cleanroom/stone-box-replay/`; the fixture is test evidence only and
is rejected from binary and sources artifacts. The helper command remains
source-enabled. The failed ATS3 binary and the first command-enabled ATS4
binary (281,461 bytes, SHA-256
`6c3590d3f3bb23689b95d88afcd677c3c50cbe876f0de90cdf7ee63d11f4fe38`)
are revoked. The final 284,840-byte ATS4 helper was used only after its
independent artifact audit and separate exact local physical-client GO. Its
complete operator-local bundle passed catalog, replay, ZIP, ATS4, closure, and
production-loader audits. Format-2 production code is enabled only for
controlled resource-backed staging; the bundle remains local and excluded
from every artifact.
