# Stone clean-room geometry reference

This isolated Java 21 reference implements the provenance-separated contract
in [SPEC.md](SPEC.md). It is not a workspace edit and has no deployment or
release behavior. The exact runtime profile accepts only finite array-form box
UV with integral binary32 origins and finite affine final matrices; alternate
per-face UV, fractional UV origins, non-finite values, and non-affine matrices
are explicitly outside the profile and rejected at its typed/JOML boundaries.

Files:

- `src/main/java/cleanroom/stone/BedrockBoxReplay.java`: dependency-neutral
  compiler, first-party validation, and raw canonicalizer.
- `src/joml/java/cleanroom/stone/JomlAdapters.java`: optional JOML 1.10.5
  adapter for the already-final position matrix.
- `src/test/java/cleanroom/stone/BedrockBoxReplayTest.java`: focused contract
  regressions, including signed inputs, post-transform double degeneracy,
  normal gating, raw-normal identity, and non-reversing canonicalization.
- `src/test/java/cleanroom/stone/RuntimeFixtureParityTest.java`: exact raw-f32
  comparison with the provenance-locked sanitized 13-case runtime fixture.

Run:

```bash
mkdir -p out
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

The parity test expects
`/tmp/stone-runtime-fixture/fixtures.ndjson` with SHA-256
`b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f`.
An alternate fixture path may be supplied as its first argument, but the hash
remains mandatory.

The Java files are MIT-licensed and carry SPDX, copyright, and clean-room
provenance headers suitable for mechanical integration.
