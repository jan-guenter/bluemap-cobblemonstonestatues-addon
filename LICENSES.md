# License map

Unless a source file carries a different SPDX identifier, this project's
first-party source and documentation are licensed under the MIT License in
[`LICENSE`](LICENSE).

The following modified Source Code Form is licensed under Mozilla Public
License 2.0 instead:

- `src/main/java/io/github/janguenter/bluemap/cobblemonstonestatues/geometry/`
  `BakedGeometryTopology.java`

That file identifies Cobblemon Contributors, the exact upstream commit and
path, and this project's modifications in its header. The unmodified MPL-2.0
text is in [`LICENSES/MPL-2.0.txt`](LICENSES/MPL-2.0.txt).

The following first-party BlueMap adapter files are licensed under GNU Lesser
General Public License 2.1 only:

- `src/main/java/io/github/janguenter/bluemap/cobblemonstonestatues/adapter/`
  `bluemap522/BlueMap522Adapter.java`
- `src/main/java/io/github/janguenter/bluemap/cobblemonstonestatues/adapter/`
  `bluemap522/StatueBlockEntityData.java`
- `src/main/java/io/github/janguenter/bluemap/cobblemonstonestatues/adapter/`
  `bluemap522/StoneStatuesRenderer.java`
- `src/main/java/io/github/janguenter/bluemap/cobblemonstonestatues/adapter/`
  `bluemap522/StoneStatuesResourceExtension.java`
- `src/test/java/io/github/janguenter/bluemap/cobblemonstonestatues/adapter/`
  `bluemap522/BlueMap522AdapterTest.java`

They preserve the license of the first-party adapter framework reused from
BlueMap Botany Pots Add-on `v0.1.0-alpha.1`, commit
`f40eed6c1f7f30356bcdfabbc3e2a6455fec7884`. The unmodified LGPL-2.1-only
text is in [`LICENSES/LGPL-2.1-only.txt`](LICENSES/LGPL-2.1-only.txt).

The production add-on and client-only helper are Larger Works containing both
MIT-covered files and the MPL-2.0-covered file. The production add-on also
contains the four LGPL-2.1-only adapter files listed above. The client-only
helper contains no LGPL-covered adapter file. Each binary and matching sources
artifact carries the license texts and notices required for the files it
contains, plus the source-code offer.
