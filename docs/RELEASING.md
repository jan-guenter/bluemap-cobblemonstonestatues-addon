# Releasing

Release only an owner-accepted renderer from a clean reviewed commit. The
prepared candidate is `0.1.0-alpha.2`; its four JAR identities are locked in
`provenance/release.json` and `docs/RELEASE.md`.

Initialize the exact toolkit and Adapter API gitlinks before running gates:

```bash
git submodule update --init --recursive -- \
  tooling/bluemap-addon-toolkit modules/bluemap-addon-adapter-api
```

1. Confirm the exact All the Mons, Minecraft, NeoForge, Java, BlueMap, and
   candidate-mod identities documented by this repository.
2. Run the repository's complete `check` and build gates with every required
   exact artifact property.
3. Verify the production and sources JAR boundaries, licenses, notices, and
   provenance. Do not bundle candidate-mod binaries, resources, source,
   galleries, worlds, logs, or credentials.
4. Confirm `addon_version`, the intended Maven coordinates, and every sealed
   release size and SHA-256 value.
5. Merge through a pull request. Create an immutable annotated tag exactly
   equal to `v<addon_version>` at the reviewed commit.
6. Let `.github/workflows/release.yml` publish. Compare downloaded assets and
   checksums with the accepted local artifacts before updating
   `bluemap-atmons`.

A tooling-only conventions change does not alter `addon_version` or an
existing tag. No publication step deploys to a production server.
