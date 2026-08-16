# Client-only hybrid pose-state exporter (ATS4 source checkpoint)

The 278,486-byte command-enabled ATS3 helper with SHA-256
`9b41f38e105ce94a8eb56179e9a5c939f953c84d171df1bc2001bdc0650f9728`
was installed and executed only for its authorized exact local checkpoint. It
aborted without output on the first Mega Abomasnow work item because ATS3 lost
a mirrored signed-zero normal bit. The first command-enabled ATS4 helper,
281,461 bytes with SHA-256
`6c3590d3f3bb23689b95d88afcd677c3c50cbe876f0de90cdf7ee63d11f4fe38`,
was also installed only after GO; it aborted without output on Archaludon when
an exact-zero-scale draw path produced nonfinite public normals. Both failed
identities and every earlier helper identity are revoked. The first
sanitizer-corrected ATS4 helper, 285,871 bytes with SHA-256
`9dbac38dda2e5ddadb5d75cad2b612a5112d4598478964bcc59d827c0ad40e08`,
is also revoked after its authorized run exhausted LWJGL's fixed 64 KiB
`MemoryStack` in the byte-array PNG decoder. The streaming-decode ATS4 helper,
285,905 bytes with SHA-256
`e775fa414d218e89a8d21ab89ff47417cca6627452459629520d90f9e1b197ae`,
is also revoked: its authorized run reached 3,936 of 7,869 items before a
redundant geometric-alignment heuristic rejected Mega Malamar's authentic
`-0.001`-scale light plane. The raw-normal-identity replacement is exactly
284,840 bytes with SHA-256
`f35727ba2ce5c96abe085df36d5d02534b6a19e1f710eb4c145c0d7243c75b98`.

The replacement observes one exact Stone 1.1 bridge render after pose
application, records only compact absolute draw-node matrices plus ordinary
and mirror-zero-X transformed normals, and uses the emitted quads only as an
in-memory verification oracle. The
deterministic ZIP holds no source PNG/GEO/animation/poser bytes and
no final quad blobs. The generated bundle remains operator-local, ignored by
Git, and nonredistributable.

This checkpoint contains the fail-closed single-use observer, exact-byte model
binding, compact format-2 pose-state/catalog writer, and clean-room GEO replay
comparison. Its complete 7,869-route physical-client export and strict
production-loader audit passed. Production code is enabled only for controlled
resource-backed staging; this helper is not a release or deployment artifact.

At render HEAD, one narrow reversible sanitizer handles a public-runtime edge:
an originally drawable node under an exact local zero-scale path is made
effective `skipDraw` only when its absolute position matrix is finite and
affine but a sampled ATS4 transformed normal is nonfinite. The flag remains in
effect through the same bridge render and is restored on every exit. This
deliberately omits flattened NaN-normal planes such as the exact Archaludon
case. The bounded mapped-runtime node reconstruction does not prove that every
singular plane would be filtered later, so the policy intentionally omits any
otherwise retainable coincident plane meeting the same narrow predicate. All
other invalid-normal states still fail closed.

The helper is a mixed-license Larger Work. Most files are MIT; the shared
`BakedGeometryTopology.java` is an MPL-2.0-covered Cobblemon modification. The
binary and matching `-sources.jar` contain the license map, both license texts,
notices, and corresponding-source offer from the repository root.

Ordinary `test` always runs the tracked 13-case sanitized raw-f32 clean-room
fixture at its pinned SHA-256. The fixture remains repository/test evidence and
is rejected from both helper artifacts.

The exact-byte model observer sees the array after Cobblemon's stock
`InputStream.readAllBytes()` call. It retains no extra copy and invalidates the
export epoch above 2 MiB per GEO or 64 MiB combined observed GEO bytes, but it
cannot prevent Cobblemon's original allocation without interfering with stock
resource reload. The exact profile's largest GEO is 130,948 bytes.

Build with exact compile-only inputs:

```bash
gradle --no-daemon \
  -PstoneStatuesJar=/path/to/cobblemonstonestatues-neoforge-1.1.jar \
  -PcobblemonJar=/path/to/Cobblemon-neoforge-1.7.3+1.21.1.jar \
  clean check build
```
