# Hybrid pose-state architecture

This document freezes the replacement for the revoked posed-quad bundle. It
describes the exact All the Mons 1.2.0 profile. The complete client export and
strict production-loader audit have passed; resource-backed staging and the
bounded gallery remain required before release.

## Shipping boundary

The production add-on reads the operator-installed winning Cobblemon GEO and
PNG resources through BlueMap's active resource roots. It never packages a
Pokémon PNG, GEO, animation, poser, generated mesh, or client-exported
payload. The client helper writes an operator-local bundle containing only
resource identities, resolved selection metadata, compact absolute runtime
frames, pose-state blobs, and verification digests.

The old `meshes/<sha256>.bin` entries and production `MeshCodec` path are
revoked. The 278,486-byte command-enabled ATS3 helper is also revoked after its
authorized local run exposed mirrored signed-zero normal loss and aborted
without output. The first 281,461-byte ATS4 helper is also revoked: its
authorized run aborted without output on Archaludon when an exact-zero-scale
draw path produced nonfinite public normals. The corrected command-enabled
ATS4 helper at 285,871 bytes with SHA-256
`9dbac38dda2e5ddadb5d75cad2b612a5112d4598478964bcc59d827c0ad40e08`
is also revoked: its authorized run exhausted LWJGL's fixed 64 KiB
`MemoryStack` in the byte-array PNG decoder after at least 2,756 of 7,869
items. The 285,905-byte streaming-decode helper with SHA-256
`e775fa414d218e89a8d21ab89ff47417cca6627452459629520d90f9e1b197ae`
then reached 3,936 of 7,869 items before a redundant geometric-alignment
heuristic rejected Mega Malamar's authentic `-0.001`-scale light plane; it is
revoked. The raw-normal-identity replacement is pinned at 284,840 bytes with
SHA-256
`f35727ba2ce5c96abe085df36d5d02534b6a19e1f710eb4c145c0d7243c75b98`.
The resulting 99,408,820-byte operator-local bundle has SHA-256
`edc1b15d97267a28f9ef946a0c645e924b75322afb976aeeb220ad8c5056eefa`.
It contains 1,027 species, 1,457 choices, 6,412 named profiles, 1,457
fallbacks, 1,338 models, 4,832 pose states, and 1,366 textures. All 7,869
routes, six attestation roots, ATS4 records, and ZIP entries passed independent
and production-loader verification. Format-2 production code is enabled only
for controlled resource-backed staging; this is not a release authorization.

## Format 2 records

The canonical selection key remains
`speciesId + US + formId + US + sortedUniqueAspects + US + requestedPose`.
Unknown requested poses use one captured Stone-bridge fallback record for the
choice; unknown choices remain stock-invisible. A choice with no named pose is
still represented by its required fallback record.

Each model resource identity records:

- effective model ID;
- exact winning `assets/<namespace>/<path>.geo.json` resource ID, active pack
  ID, byte size, SHA-256, geometry identifier, and texture dimensions;
- an order-neutral topology digest over every named node path, parent path,
  default topology transform, and ordered box definitions;
- source bone, cube, locator, and generated-node counts.

The selected root is pose-state data, not model-global data. Each pose state is
content-addressed by canonical uncompressed format-4 bytes for exactly the
selected rendered-root induced subtree. Its first node is the selected root at
its absolute canonical path, `parentIndex=-1`, sibling ordinal zero. There is
no synthetic wrapper record. Selecting the wrapper `/` remains representable.
Every record contains:

- absolute escaped path, parent index, and captured sibling ordinal;
- `visible` and `skipDraw`;
- an optional runtime frame, present exactly when the node is effectively
  visible, does not skip its own draw, and owns at least one box;
- for that frame, all sixteen raw IEEE-754 float coefficients of the final
  affine position matrix, six raw transformed cardinal normals in
  `DOWN,UP,WEST,NORTH,EAST,SOUTH` order, and four additional transformed
  `DOWN,UP,NORTH,SOUTH` normals whose local X input is `-0.0f`;
- the model-wide final uniform ARGB tint observed by `CaptureBuffer`.

The pose catalog also keeps requested species/form/aspects, requested pose and
index, nullable actual `state.currentPose`, effective model/poser/texture IDs,
independent direct/substitute resolution outcomes, form base scale, and the
pose-state hash. Base scale is routing/attestation metadata only: Stone has
already applied it to every captured absolute matrix. Neither client replay,
production replay, nor the later block transform applies it again.

Each pose record carries a verification-only retained-quad multiset SHA-256,
source/retained/dropped exact-degenerate quad counts, raw retained bounds, and
tint. A canonical record stores the raw stored-normal and four position/UV
vertices; only cyclic vertex starts are equivalent. Winding is never reversed,
records are unsigned-byte sorted, and duplicates remain in the multiset. No
quad payload is stored in the bundle.

## Client capture invariant

The helper installs a client-only Mixin observer at `PosableModel.render` HEAD.
A non-inheritable single-use hook is armed around exactly one synchronous call
to Stone 1.1's public `renderStonePokemon` bridge. The observer therefore runs
after pose selection and animation but before root rendering and Stone's
`setDefault` cleanup. It must fire exactly once with the expected model,
resolver, pose stack, buffer, sentinels, render context, and base scale.

The observer identity-scans the effective resolver's exact baked wrapper to
derive the selected root's absolute path. It traverses the selected
`Bone.getChildren()` subtree in actual client order. Using balanced
push/`translateAndRotate`/pop calls on Stone's already-base-scaled `PoseStack`,
it snapshots final matrices, ordinary transformed cardinal normals, and the
four mirror-zero-X normals only for draw-bearing nodes. The latter preserve a
raw zero sign that Minecraft's mirrored cube path can carry through rotation.
Invisible subtrees are not traversed; `skipDraw` suppresses
only a node's boxes. Hidden, empty, and skip-draw nodes keep structural records
without unused frames. Entry pose-stack matrices and identity must be exactly
restored.

The traversal mirrors the public ModelPart contract. `isEmpty()` describes
only a node's own cubes. A visible own-empty node with children still enters
its transform and traverses those children; a visible own-empty leaf returns
before transforming; `skipDraw` omits only the node's own cubes while children
continue; and an invisible node returns before both transform and child
rendering. Exact-zero ancestry starts false at the selected root. Wrapper
ancestors used only to derive that root's path cannot mark or suppress the
selected subtree.

One deliberately narrow sanitizer applies before the observed node draw. The
traversal propagates whether any visited local ModelPart scale component is
exactly `== 0.0f`. If an originally drawable node on such a path has a finite
affine absolute position matrix but any public ATS4 ordinary or mirror-zero-X
transformed normal component is nonfinite, the hook records effective
`skipDraw=true`, records no runtime frame, and temporarily sets that exact
ModelPart to `skipDraw` for the remainder of the same synchronous bridge
render. Empty structural parents propagate the marker; visible empty leaves
retain ModelPart's ordinary early-return behavior. Every mutation is restored
after the complete bridge action, including exceptional exits. The sanitizer
does not infer singularity from a descendant matrix determinant, because
float rotation can obscure an inherited exact-zero scale.

This is an intentional omission of stock flattened planes whose positions are
finite but whose emitted normals are NaN. A mapped-runtime reconstruction of
the first failing Archaludon node emits 24 vertices for six zero-area quads at
only two distinct positions, with all normal components NaN (`0xffc00000`); a
public-runtime `scale(1,1,0)` fixture reproduces it. That bounded node probe is
not a second live-client capture and does not prove every singular plane would
be filtered later; the sanitizer deliberately omits any otherwise retainable
coincident plane meeting the same narrow predicate. Uniform `scale(0,0,0)`
with finite public normals remains an ordinary strict frame. Nonfinite normals
without exact-zero-scale ancestry, and finite normals that fail the
normal-length gate, still fail closed. Apart from this reversible per-node
sanitizer, the hook never cancels or replaces the ordinary bridge render.

For the same captured state, the helper parses the exact bytes that created the
winning built-in GEO wrapper, binds the selected subtree, and invokes the
separately audited clean-room array-box-UV compiler with the absolute frames.
It compares the complete multiset identity with the single bridge capture
before retaining the pose state. Random MoLang is evaluated only once; replay
checks that realized numeric result and never performs a second Stone render.

The built-in factory observer hashes the exact byte array consumed by
Cobblemon, records physical resource and pack IDs, and later requires an exact
re-read match plus wrapper identity. Any observer accounting fault invalidates
the export epoch without interfering with Cobblemon reload. Custom replacement
or stale identities fail closed; a custom registration that leaves the exact
same built-in Bone identity is accepted only when topology and replay also
match.

Cobblemon itself creates that byte array with `InputStream.readAllBytes()`
before the observer can reject it. The observer does not clone or retain an
over-budget array, and the helper refuses availability/output when one GEO is
over 2 MiB or the observed GEO closure is over 64 MiB, but it cannot honestly
claim pre-allocation rejection without changing stock reload behavior. The
exact five-JAR profile's largest GEO is 130,948 bytes.

## Clean-room compiler boundary

`geometry/cleanroom/BedrockBoxReplay.java` was produced behind a separate
clean-room boundary from the Microsoft Bedrock geometry schema/visual
documentation and a sanitized 13-case raw-f32 runtime fixture. Its narrow JOML
adapter applies each already-final affine matrix once with the exact host JOML
1.10.5 `transformPosition` operation. It does not reconstruct a client
PoseStack or local transforms and contains no copied Minecraft implementation
source.

The exact profile accepts finite array-form box UV only. The original 13-case
fixture locks endpoint deformation, mirror, UV net, transformed stored
normals, and the exact two-triangle degeneracy filter. A separate pinned
public-runtime mirrored-cube regression locks the ATS4 mirror-zero-X normal
selection and its raw signed-zero behavior; that rule is not attributed to the
unchanged 13-case fixture. Per-face/object UV,
fractional box-UV origins, projective matrices, nonfinite values, invalid
normal lengths, and over-budget geometry fail closed. A geometric alignment
heuristic is deliberately absent: exact bridge-versus-replay multiset equality
binds every raw normal and position word without rejecting quantization-limited
planes such as Mega Malamar's `-0.001`-scale lights. The tracked
fixture SHA-256 is
`b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f`
and ordinary root/helper tests run all 13 cases with no optional skip.

## Production invariant

Format-2 activation verifies the complete manifest and exact active winning
GEO/PNG closure from one-owner immutable blobs. Before route activation it
compiles every unique model-resource/pose-state pair, binds the topology,
compares the full multiset identity, converts into the bounded server model,
and then discards the preflight mesh. After material generation it retains only
verified topology and pose state—not source GEO/PNG bytes—and runtime lazily
recompiles those into a weighted single-flight cache.
Generated material closure includes named profiles and fallbacks. Runtime
publication occurs only after resource-extension bake has retained textures
and passed the BlueNBT probe.

The later statue center, enum size, Y rotation, and two-axis reflection are
outside the attested replay boundary. Form base scale is already inside the
captured matrices and is never repeated.

Any schema, resource, topology, numerical, digest, texture, or capacity error
rolls back the block atomically to Stone's stock-invisible result. A replay or
topology attestation failure disables the complete exact profile rather than
leaving other poses active. Capacity exhaustion still rolls back and
propagates so BlueMap can retry. There is no partial model or server-invented
substitution.

## Reuse and retirement

Retained components:

- exact artifact, environment, resource-pack, Stone GUI census, resolution,
  fallback, texture, and base-scale attestations;
- `CaptureBuffer`/`CapturedMesh` only as an in-memory bridge oracle;
- deterministic atomic ZIP publication and bounded diagnostics;
- BlueNBT routing, material conversion, `StatueModel`, outer block transforms,
  lighting/cave/top/map-color behavior, rollback, and the eight-cell gallery.

Retired components:

- `UniqueMeshStore`, `meshes/*.bin`, mesh roots, and `MeshCodec`;
- repeated bridge renders and stochastic byte-equality checks;
- fallback inference by mesh equality;
- local-transform/PoseStack reconstruction;
- texture-only active-resource loading.

## Bounded verification sequence

1. format-4 pose-state codec, matrix/ordinary/mirror-normal observer, topology
   binding, and limits;
2. exact Bedrock GEO parser/topology for effective 1.12.0 and 1.21.0 array-box
   schemas;
3. clean-room compiler and multiset digest against the tracked 13-case fixture
   plus bounded exact-artifact tests;
4. same-invocation exporter replay in the command-enabled controlled helper;
5. strict format-2 production preflight/activation and complete live export;
6. exactly four representative valid statues plus two material controls, one
   unknown master, and one orphan proxy in the visual gallery.

The fixture remains eight cells. No visual species/pose matrix is added.
