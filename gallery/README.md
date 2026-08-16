# Bounded Stone Statues gallery

This deterministic datapack contains exactly eight cells: four deliberately
different representative Pokémon poses/scales/rotations, vanilla stone and
gold controls, one unknown-species master, and one natural orphan proxy. It is
not a species-by-size or species-by-pose matrix.

Run `/function stone_statues_gallery:build`, then
`/function stone_statues_gallery:verify`. The orphan proxy is fixed at
`224 100 224`; the add-on must never route it. The two fallback cells are
expected to remain stock-invisible.

Build the deterministic staging ZIP with:

```bash
mkdir -p build/gallery
gallery/package.sh build/gallery/bluemap-cobblemonstonestatues-gallery.zip
```
