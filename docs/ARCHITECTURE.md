# Architecture

> **Revoked checkpoint:** the posed-quad bundle described below documents the
> last format-1 implementation and is no longer an activation design. The
> approved replacement is the compact pose-state plus operator-installed GEO
> architecture in [HYBRID_POSE_SCHEMA.md](HYBRID_POSE_SCHEMA.md). Format 2 is
> enabled for controlled staging after its complete export and loader audit;
> format 1 is not a fallback. The remainder of this document is historical.

The client exporter runs only after an exact connected client's resource reload
and species sync. It enumerates Stone's own GUI choices and pose names, then
calls Stone 1.1's public `renderStonePokemon` bridge with identity transforms,
empty layer context, time zero, no quirks, and a capturing buffer. This makes
the exact upstream runtime—not this project—responsible for form/aspect
resolution, JSON/code poser behavior, animation, fallback choice, cleanup, and
form base scale.

Captured meshes already include form base scale. Production applies only the
block transform in this order: center translation, positive Y rotation in 45°
steps, `(1,-1,-1)` reflection, and the persisted enum scale. It converts the
verified active source PNG to stone/gold locally, then emits one outward
winding with ordinary BlueMap face light, cave filtering, top-only filtering,
map color, and out-of-cell bounds.

Activation is atomic: exact artifact pair, configured bundle outer digest,
hard-pinned exporter identity, exactly 1,027 derived species, bundle
schema/closure/hashes/count attestation, active winning source texture
hash/dimensions, synthetic dispatch, and BlueNBT retention must all pass. No
partial profile is published.

The exporter fingerprints all loaded-mod code sources and every ordered active
client resource pack before and after capture. Its start/end Stone census
includes every GUI choice and ordered pose key. It retains at most 384 MiB of
unique mesh bytes, emits at most a 512 MiB ZIP, fsyncs that temporary file, and
atomically renames the ZIP. Directory fsync is advisory for Windows/DrvFS; no
sidecar participates in publication or activation.

The exporter output is operator-local and nonredistributable. It contains
final numeric quads and identifiers but no source PNG, GEO, animation, poser,
model class, or preconverted texture.
