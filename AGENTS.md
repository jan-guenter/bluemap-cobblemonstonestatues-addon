# Agent guide

This standalone repository implements only the BlueMap Cobblemon Stone Statues
add-on and its client-only operator-local exporter. Read the workspace root
`AGENTS.md` first.

- Preserve version `0.1.0-alpha.1` until a reviewed release task changes it.
- Pin the exact ATM 1.2.0 artifacts and BlueMap backport; fail closed on drift.
- Never package Cobblemon/Stone classes, models, textures, animations, poser
  files, generated posed meshes, the operator-local bundle, or resource packs.
- Production support is driven by one complete exporter bundle; partial export
  activation is forbidden.
- Route only `pokemon_statue`; never own `statue_proxy`.
- Keep visual/unit fixtures representative: exactly four valid statues, two
  controls, unknown master fallback, and orphan proxy fallback.
- Do not stage, commit, push, tag, publish, or deploy without explicit scope.

Validation:

```bash
gradle --no-daemon clean check build
gradle --no-daemon generatePomFileForAddonPublication
python3 tools/verify_pinned_artifacts.py --stone-statues <jar> --cobblemon <jar>
```
