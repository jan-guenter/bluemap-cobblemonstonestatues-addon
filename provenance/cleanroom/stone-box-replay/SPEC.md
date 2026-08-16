# Clean-room Bedrock box-UV quad replay specification

Status: black-box-calibrated clean-room reference, 2026-08-14; integration
boundary addendum, 2026-08-16.

This document separates official schema statements, target-profile facts
observed through a sanitized public-runtime fixture stream, and first-party
validation policy. A fact is claimed only for the domain actually exercised.

## Scope and exclusion boundary

The exact-profile slice accepts one Bedrock-style cube with array-form box UV
and an integral binary32 UV origin, one finite already-final affine position
matrix, and six finite already-final cardinal normal vectors. It emits zero to
six ordered quads containing paired position/UV vertices and one copied normal.

It does not parse geometry JSON, resolve bone/cube mirror inheritance, compose
bone hierarchies, apply cube rotations or pivots, choose materials, reproduce
runtime face iteration, or support alternate object-form per-face UVs.

No Minecraft source/decompiled bytecode, Cobblemon `TexturedModel` source,
workspace replay compiler, or prior face-compiler tests were consulted. The
runtime probe source was explicitly out of bounds and was not read.

## Provenance

Official sources, accessed 2026-08-14:

1. Microsoft, [Schema Documentation - geometry:1.12.0](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/schemasreference/schemas/minecraftschema_geometry_1.12.0?view=minecraft-bedrock-stable).
   Primary source for `origin`, `size`, scalar `inflate`, `mirror`, array-form
   box `uv`, texture dimensions, and named face directions.
2. Microsoft, [Visuals Documentation - minecraft:geometry.v1.12.0](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/visualreference/geometry.v1.12.0?view=minecraft-bedrock-stable).
   Independent presentation of the same public field semantics.
3. Microsoft, [Entity Modeling and Animation](https://learn.microsoft.com/en-us/minecraft/creator/documents/entitymodelingandanimation?view=minecraft-bedrock-stable).
   Says Bedrock uses Box UV by default and gives whole-number/flooring authoring
   guidance, but does not define the executable cube net.
4. JOML, [Matrix4fc 1.10.5 API](https://joml-ci.github.io/JOML/apidocs/org/joml/Matrix4fc.html).
   `transformPosition` treats the vector as having homogeneous `w=1` and does
   not perform perspective divide.

Black-box evidence:

- Sanitized NDJSON: `/tmp/stone-runtime-fixture/fixtures.ndjson`
- SHA-256: `b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f`
- Shape: 13 distinct cases, each containing raw binary32 input, final matrix,
  six final cardinal normals, and 24 captured runtime vertices.
- Supplied provenance states that the data was generated headlessly through
  public runtime model-building/render APIs. This clean-room work consumed
  only the sanitized NDJSON, not the probe source.
- Optional JOML test artifact: Maven Central `org.joml:joml:1.10.5`, local
  SHA-256 `cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a`.

## Evidence labels

- `S-*`: stated by official Microsoft Bedrock documentation.
- `P-*`: observed target-profile behavior in the provenance-locked fixture.
- `V-*`: first-party compiler/verification policy, not a runtime claim.
- `C-*`: arbitrary convention whose effect is erased by canonical comparison.

## Officially documented facts

- **S-1 — axes/faces.** North is `-Z`, south `+Z`, east `+X`, west
  `-X`, up `+Y`, and down `-Y`. North/south span X/Y, east/west span
  Z/Y, and up/down span X/Z.
- **S-2 — cube fields.** `origin` is described as the unrotated lower corner;
  `size` extends from it in model-space units. The installed profile contains
  signed sizes, so P-rules preserve algebraic endpoint identity instead of
  imposing the schema's authoring intent.
- **S-3 — deformation.** Schema `inflate` grows a box additively in all
  directions. The installed runtime bridge supplies a per-axis deformation
  vector, represented by three `inflate_bits` words in the fixture.
- **S-4 — texture/UV.** Texture width/height are texel dimensions. Array-form
  `uv` contains two floats and is the upper-left start of box mapping. The
  schema separately defines an alternate object-form per-face representation.
- **S-5 — mirror.** Documentation says mirror acts along X and flips
  east/west faces, but does not provide an executable per-vertex algorithm.
- **S-6 — Box UV guidance.** The authoring guide mentions whole numbers and
  flooring. It does not specify which values/intermediates an implementation
  floors; P-7 below is stronger evidence for this exact profile.

## Black-box-backed target-profile rules

- **P-1 — binary32 identity.** Cube inputs, matrix elements, final normals,
  positions, and UVs are binary32. Replay geometry/UV arithmetic stays in
  Java `float`; comparison serializes raw float words.
- **P-2 — signed inputs.** Signed/zero size, positive/negative per-axis
  deformation, and negative UV coordinates are retained. No endpoint sorting,
  absolute value, or clamp occurs.
- **P-3 — ordered endpoints.** For each axis, in this Java-float operation
  order:

  ```text
  second = f32(origin + size)
  A      = f32(origin - deformation)
  B      = f32(second + deformation)
  ```

  Fixed endpoint roles matched positive/negative deformation on a negative-X
  size as well as per-axis negative deformation on positive sizes.
- **P-4 — position unit boundary.** Divide each box coordinate by `16.0f`
  before applying the already-final matrix. JOML 1.10.5
  `Matrix4fc.transformPosition` reproduced the captured position words exactly
  in all 12 finite-normal cases. Apply the matrix once; add no pivot, rotation,
  coordinate flip, or hierarchy transform.
- **P-5 — local ordered vertices.** With A/B retaining algebraic identity,
  use the following four-vertex winding. Runtime cyclic start and face
  iteration are ignored, but no reversal is allowed.

  | Face | Cardinal | Ordered local positions |
  | --- | --- | --- |
  | north | `-Z` | `(xB,yB,zA)`, `(xB,yA,zA)`, `(xA,yA,zA)`, `(xA,yB,zA)` |
  | south | `+Z` | `(xA,yB,zB)`, `(xA,yA,zB)`, `(xB,yA,zB)`, `(xB,yB,zB)` |
  | east | `+X` | `(xB,yB,zB)`, `(xB,yA,zB)`, `(xB,yA,zA)`, `(xB,yB,zA)` |
  | west | `-X` | `(xA,yB,zA)`, `(xA,yA,zA)`, `(xA,yA,zB)`, `(xA,yB,zB)` |
  | up | `+Y` | `(xA,yB,zA)`, `(xA,yB,zB)`, `(xB,yB,zB)`, `(xB,yB,zA)` |
  | down | `-Y` | `(xB,yA,zA)`, `(xB,yA,zB)`, `(xA,yA,zB)`, `(xA,yA,zA)` |

- **P-6 — box-UV rectangles.** Let signed `U,V` be the integer box-UV
  origin and signed `W,H,D = size.x,size.y,size.z`. Deformation does not alter
  UV spans. All sums are left-associated Java-float operations.

  | Face | Rectangle `(L,T,width,height)` when not mirrored |
  | --- | --- |
  | down | `(U + D, V, W, D)` |
  | up | `(U + D + W, V, W, D)` |
  | west | `(U, V + D, D, H)` |
  | north | `(U + D, V + D, W, H)` |
  | east | `(U + D + W, V + D, D, H)` |
  | south | `(U + D + D + W, V + D, W, H)` |

  `R=f32(L+width)` and `B=f32(T+height)`. L/R and T/B are algebraic
  labels, not sorted minima/maxima.
- **P-7 — UV association and fractional sizes.** Normalize each coordinate by
  float division with texture width/height. For the P-5 vertex sequence:

  ```text
  mirror=false, UP:         (L,B), (L,T), (R,T), (R,B)
  mirror=false, all others: (R,B), (R,T), (L,T), (L,B)
  ```

  A `(2.5,3.25,4.75)` size fixture retained fractional UV spans exactly; it
  did not floor them. Negative signed spans also remained signed.
- **P-8 — mirror.** Mirror preserves the canonical geometric winding and
  source-face normal. It horizontally reflects face-local U association. It
  also exchanges the east and west rectangle ownership before applying that
  reflection. Thus:

  ```text
  mirror=true, UP:         (R,B), (R,T), (L,T), (L,B)
  mirror=true, all others: (L,B), (L,T), (R,T), (R,B)
  ```

  For mirrored geometry, geometric east uses the non-mirrored west rectangle
  and geometric west uses the non-mirrored east rectangle.
- **P-9 — normals.** Select the already-final vector by semantic source face
  and copy its three float words unchanged. Do not transform, normalize, sign
  repair, or derive it from the quad. Negative-handedness evidence confirms
  that winding sign is not a repair signal.
- **P-10 — raw runtime degenerates.** The zero-X runtime stream contains all
  six candidates; V-2 filters four and retains two coincident nonzero Y/Z
  faces. Multiplicity is semantic.
- **P-11 — collapsed transform.** The collapsed-X capture contains NaN in all
  six final normals. The compiler/non-sanitized-frame boundary must fail
  closed; it must not hide those invalid normals by dropping a
  later-degenerate face.

P-11 remains the compiler-input boundary. The client integration has one
separate pre-draw sanitizer: when an exact local zero-scale component exists
on the traversed ModelPart ancestry, the already-final position matrix is
finite and affine, and a public ATS4 ordinary or mirror-zero-X transformed
normal component is nonfinite, that node is temporarily made effective
`skipDraw` before the observed bridge emits it. It therefore supplies neither
a runtime frame nor candidate quads to this compiler and is not a
later-degenerate-face drop. The temporary flag is restored after the complete
synchronous bridge action. Every non-suppressed frame remains subject to P-11
and V-1; nonfinite normals without exact-zero ancestry and finite invalid
normals fail closed. This integration policy is first-party behavior and is
not attributed to the unchanged 13-case fixture.

## First-party validation policy

- **V-1 — normal gate first.** Copy the selected normal, require finite
  components, accumulate its length in double, and require inclusive length
  `[0.5D,1.5D]` before any degeneracy decision.
- **V-2 — exact transformed degeneracy.** Build and transform all four
  positions. Promote each stored float coordinate to double before
  subtraction, then calculate:

  ```text
  c0 = (p1 - p0) cross (p2 - p0)  // triangle [0,1,2]
  c1 = (p3 - p2) cross (p0 - p2)  // triangle [2,3,0]
  ```

  Drop only if all three components of both crosses are exactly `== 0.0D`.
  Preserve those subtraction origins and that evaluation order; the
  algebraically equivalent expression `(p2-p0) cross (p3-p0)` is not the
  contract and can round differently at extreme finite binary32 magnitudes.
  No epsilon is used. A `1e-30f` cube is retained: its double area is nonzero
  even though a float cross would underflow.
- **V-3 — retained normal identity.** Do not derive, repair, or reject a
  stored normal from the direction of a cross computed from final binary32
  positions. After V-1 and V-2, retain the copied raw normal unchanged. The
  connected exporter requires the complete canonical quad multiset from the
  live bridge to equal the independently replayed multiset; production repeats
  that equality before activation. The identity includes every raw normal and
  position word, UV, winding, multiplicity, count, bound, and tint.

  This rule was corrected after the exact Mega Malamar standing animation
  exposed the conditioning flaw in a geometric cosine heuristic. Its
  `left_pink_light` and `right_pink_light` branches apply uniform scale
  `-0.001` to inflated zero-depth planes. One authentic retained triangle had
  cross length `8.333489239650309e-10`; final-position quantization changed the
  apparent normal cosine to `0.9964617793` even though the separately supplied
  runtime normal was finite with length `0.9999998763`. A tuned cosine or area
  tolerance would be scale-dependent; exact multiset equality is the
  authoritative parity check.

## C-independent ordering

The reference enumerates `NORTH,SOUTH,EAST,WEST,UP,DOWN`. This is C-1 only.
Runtime face iteration and cyclic start changed under mirror. Verification
erases both by cyclic canonicalization and multiset sorting.

## Java API

The dependency-neutral core is:

```java
enum Face { NORTH, SOUTH, EAST, WEST, UP, DOWN }
record Vec2(float u, float v) {}
record Vec3(float x, float y, float z) {}

record Cube(
    Vec3 origin,
    Vec3 size,
    Vec3 deformation,
    boolean mirror,
    Vec2 boxUv,
    int textureWidth,
    int textureHeight
) {}

@FunctionalInterface
interface AffinePositionTransform {
    Vec3 transformPosition(Vec3 matrixSpacePosition);
}

record Vertex(Vec3 position, Vec2 uv) {}
record Quad(Face sourceFace, List<Vertex> vertices, Vec3 normal) {}

List<Quad> compileRuntimeProfile(
    Cube cube,
    AffinePositionTransform finalPositionMatrix,
    Map<Face, Vec3> finalNormals
);
```

`compileRuntimeProfile` selects P-3, P-4, and P-6–P-8. The lower-level
`compile` retains explicit policy seams for research. The optional
`JomlAdapters.finalPositionMatrix(Matrix4fc)` snapshots the already-final
matrix, rejects any non-finite component or non-affine bottom row, and
delegates every position to JOML `transformPosition`.

`Cube` represents only array-form box UV. Object-form/per-face UV has no API
representation and must be rejected by an upstream parser. The exact-profile
constructor rejects non-finite cube scalars, non-positive texture dimensions,
and fractional UV origins. It intentionally accepts signed and zero sizes,
signed deformation, and negative integral UV origins.

## Canonical verification

Canonicalization operates on paired vertices, never separate position/UV
arrays:

1. Encode each vertex as big-endian raw float words
   `(x,y,z,u,v)` with `Float.floatToRawIntBits`.
2. Form exactly four cyclic rotations and choose the unsigned-byte
   lexicographic minimum. Never form a reversed sequence.
3. Append the three raw normal words. Source face and constant fixture color
   are outside the compared payload.
4. Sort canonical 92-byte quad records with multiplicity.
5. For the optional SHA-256 digest, prefix a big-endian 32-bit quad count and
   hash the sorted records.

The parity test verifies exact canonical-record equality, not digest alone.

## Fixture coverage and locked results

All noncollapsed cases match the compiler as exact raw-f32 canonical
multisets after V-filtering:

| Case | Primary discrimination | Retained quads | Canonical SHA-256 |
| --- | --- | ---: | --- |
| `asymmetric_uv` | net, face orientation, `/16`, matrix | 6 | `601499526bf7784bd10e3e7eb09150ab79432d272c7051cb2538359c63887ac6` |
| `mirror` | U reflection, east/west rectangle exchange | 6 | `63977c12246e027f0bbb386c44e1db91b1250c6fd3c8cda91a9d1f34060fdff6` |
| `signed_size` | unsorted negative X/Z spans | 6 | `a6755f7d4ea7cc1b1ab4b1fcf1b81088f85762f041b21058db773ed283c0018c` |
| `negative_inflate` | per-axis negative deformation | 6 | `d184c8922626973a23efdd2a0438cc87cfa171f483f3be321168a7a81824e93f` |
| `negative_fractional_uv` | negative integer UV, non-square texture | 6 | `d43d8964f99583af42947931e0a6fd3731197a0b75c515df6875fbbaf716c874` |
| `fractional_size_negative_uv` | no flooring of fractional size spans | 6 | `320fc334141894e63047dd0f2013cbc703c55c541b23584facb1d2cd764a307b` |
| `inflate_zero_control` | deformation control | 6 | `3ba03c5527c4a30d9dfd27e2909193ab620e3640ad0aceeb12236f48a61a5059` |
| `positive_uniform_inflate` | positive deformation, UV unchanged | 6 | `d240bb505d386796a2ee0c7db175babf93ecddf08f7b2187b745e2301c7d41c2` |
| `negative_size_x_positive_inflate` | fixed roles, signed size/+deformation | 6 | `5219570f950e4c6cdad58865a93ac515ae5a9c492ca7664fae612a2145988a78` |
| `negative_size_x_negative_inflate` | fixed roles, signed size/-deformation | 6 | `6655f004589e430194256f02746c184ce37aa25f1bbf4804221b0fad7e7985f0` |
| `zero_dimension` | post-transform exact filtering/multiplicity | 2 | `142ee742707ffebb3f78c6168cff784b84e8a3eab8eb20c89c72aaf96cb0b431` |
| `negative_handedness` | preserve winding and raw normals | 6 | `a7396c32abbe2ba59f4435f1d53f2c4e3af255dab1416d99744f97975ae9fc01` |
| `collapsed_x` | NaN final normals | fail closed | n/a |

## Remaining gaps

The evidence does not establish:

1. Fractional box-UV **origins**; the public fixture input used integer
   offsets. The exact-profile API rejects them. Fractional size spans are
   resolved and are not floored.
2. Nonzero deformation combined with negative Y or Z size. Fixed roles are
   directly proven for negative X with both deformation signs and separately
   for negative X/Z with zero deformation.
3. Signed-zero endpoint/deformation edge behavior in the runtime.
4. Overflow, infinities, or NaN cube inputs. The exact-profile reference
   rejects non-finite values and any operation producing them; collapsed
   captured normals are a required fail-closed case.
5. Perspective/non-affine matrices. The exact-profile JOML boundary rejects
   them and uses no perspective divide.
6. Object-form per-face UV, UV rotation, material selection, atlas-region
   transforms, half-texel policies outside this slice, or texture sampling.
   Object-form/per-face UV has no exact-profile API representation and must be
   rejected before compilation.
7. Runtime face iteration and cyclic start. They are intentionally irrelevant
   under the mandated verifier; winding is fully compared and resolved for
   the exercised cases.
8. Upstream inheritance of bone versus cube mirror. The slice accepts the
   already-effective boolean.

## Validation commands

From `/tmp/stone-cleanroom-geometry`:

```bash
javac --release 21 -Xlint:all -Werror \
  -cp lib/joml-1.10.5.jar -d out \
  src/main/java/cleanroom/stone/BedrockBoxReplay.java \
  src/joml/java/cleanroom/stone/JomlAdapters.java \
  src/test/java/cleanroom/stone/BedrockBoxReplayTest.java \
  src/test/java/cleanroom/stone/RuntimeFixtureParityTest.java

java -ea -cp out:lib/joml-1.10.5.jar \
  cleanroom.stone.BedrockBoxReplayTest
java -ea -cp out:lib/joml-1.10.5.jar \
  cleanroom.stone.RuntimeFixtureParityTest
```

The parity test first verifies the exact sanitized fixture SHA-256, then
compares all finite-normal cases as raw canonical multisets and asserts the
collapsed case fails closed.

## License

The isolated Java sources and tests carry `SPDX-License-Identifier: MIT` and
`Copyright (c) 2026 Jan Guenter`, together with a short clean-room provenance
header preserving the official-document and sanitized-fixture boundary.
