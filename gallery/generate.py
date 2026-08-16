#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate the intentionally bounded eight-cell Stone Statues gallery."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
FUNCTIONS = ROOT / "datapack/data/stone_statues_gallery/function"
ORIGIN_Y = 100


def selection(
    species: str,
    form: str,
    aspects: str,
    animation: str,
    scale: str,
    material: str,
    rotation: int,
) -> dict[str, object]:
    return {
        "SpeciesId": species,
        "FormId": form,
        "AspectsCsv": aspects,
        "AnimationName": animation,
        "Scale": scale,
        "Material": material,
        "Rotation45": rotation,
        "StoneSeed": 0,
    }


def cells() -> list[dict[str, object]]:
    result: list[dict[str, object]] = [
        {
            "id": "joltik-half-walking-stone-r0",
            "kind": "valid",
            "position": [208, ORIGIN_Y, 216],
            "block": "cobblemonstonestatues:pokemon_statue",
            "selection": selection(
                "cobblemon:joltik", "Normal", "", "walking",
                "HALF", "STONE", 0,
            ),
        },
        {
            "id": "bulbasaur-normal-sleep-stone-r1",
            "kind": "valid",
            "position": [216, ORIGIN_Y, 216],
            "block": "cobblemonstonestatues:pokemon_statue",
            "selection": selection(
                "cobblemon:bulbasaur", "Normal", "", "sleep",
                "NORMAL", "STONE", 1,
            ),
        },
        {
            "id": "pikachu-double-shoulder-left-gold-r3",
            "kind": "valid",
            "position": [224, ORIGIN_Y, 216],
            "block": "cobblemonstonestatues:pokemon_statue",
            "selection": selection(
                "cobblemon:pikachu", "Normal", "", "shoulder_left",
                "DOUBLE", "GOLD_BLOCK", 3,
            ),
        },
        {
            "id": "hisuian-decidueye-triple-fly-stone-r5",
            "kind": "valid",
            "position": [232, ORIGIN_Y, 216],
            "block": "cobblemonstonestatues:pokemon_statue",
            "selection": selection(
                "cobblemon:decidueye", "Hisui", "hisuian", "fly",
                "TRIPLE", "STONE", 5,
            ),
        },
        {
            "id": "stone-control",
            "kind": "control",
            "position": [208, ORIGIN_Y, 224],
            "block": "minecraft:stone",
        },
        {
            "id": "gold-control",
            "kind": "control",
            "position": [216, ORIGIN_Y, 224],
            "block": "minecraft:gold_block",
        },
        {
            "id": "orphan-statue-proxy",
            "kind": "fallback",
            "position": [224, ORIGIN_Y, 224],
            "block": "cobblemonstonestatues:statue_proxy",
            "expected": "stock-invisible; production never owns proxy",
        },
        {
            "id": "unknown-species-master",
            "kind": "fallback",
            "position": [232, ORIGIN_Y, 224],
            "block": "cobblemonstonestatues:pokemon_statue",
            "selection": selection(
                "cobblemon:atmons_unknown_species", "Normal", "", "portrait",
                "NORMAL", "STONE", 0,
            ),
            "expected": "stock-invisible; choice absent from attested export",
        },
    ]
    if len(result) != 8:
        raise AssertionError("gallery must remain exactly eight cells")
    valid = [cell for cell in result if cell["kind"] == "valid"]
    if len(valid) != 4 or len({cell["selection"]["Scale"] for cell in valid}) != 4:
        raise AssertionError("representative scale coverage changed")
    if result[6]["position"] != [224, 100, 224]:
        raise AssertionError("natural orphan-proxy fixture moved")
    return result


def snbt(value: object) -> str:
    if isinstance(value, str):
        escaped = value.replace("\\", "\\\\").replace('"', '\\"')
        return f'"{escaped}"'
    if isinstance(value, int):
        return str(value)
    raise TypeError(type(value))


def selection_snbt(values: dict[str, object]) -> str:
    ordered = (
        "SpeciesId", "FormId", "AspectsCsv", "AnimationName",
        "Scale", "Material", "Rotation45", "StoneSeed",
    )
    fields = []
    for key in ordered:
        value = values[key]
        if key == "StoneSeed":
            fields.append(f"{key}:{int(value)}L")
        else:
            fields.append(f"{key}:{snbt(value)}")
    return "{" + ",".join(fields) + "}"


def position(cell: dict[str, object]) -> str:
    return " ".join(str(value) for value in cell["position"])


def outputs() -> dict[Path, bytes]:
    roster = cells()
    document = {
        "schema": 1,
        "profile": "atmons-1.2.0-stone-statues-1.1-cobblemon-1.7.3",
        "logical_cells": 8,
        "valid_representatives": 4,
        "material_controls": 2,
        "stock_invisible_fallbacks": 2,
        "occupied_aabb": {"minimum": [208, 100, 216], "maximum": [232, 100, 224]},
        "camera": {"position": [220.5, 115, 249.5], "yaw": 180, "pitch": 22},
        "cells": roster,
    }
    case_json = (json.dumps(document, indent=2, sort_keys=True) + "\n").encode()
    rows = ["index\tid\tkind\tx\ty\tz\tblock\texpected"]
    for index, cell in enumerate(roster, start=1):
        x, y, z = cell["position"]
        rows.append("\t".join((
            str(index), str(cell["id"]), str(cell["kind"]),
            str(x), str(y), str(z), str(cell["block"]),
            str(cell.get("expected", "rendered representative/control")),
        )))

    build = [
        "# Generated by gallery/generate.py; do not hand-edit.",
        "forceload add 204 212 236 228",
        "fill 204 100 212 236 104 228 minecraft:air replace",
        "fill 204 99 212 236 99 228 minecraft:smooth_stone replace",
    ]
    verify = [
        "# Generated by gallery/generate.py; do not hand-edit.",
        "scoreboard objectives add stone_statues_gallery dummy",
        "scoreboard players set #checked stone_statues_gallery 0",
        "scoreboard players set #failures stone_statues_gallery 0",
    ]
    for index, cell in enumerate(roster, start=1):
        build.append(f"# {index:02d} {cell['id']}")
        build.append(f"setblock {position(cell)} {cell['block']} replace")
        values = cell.get("selection")
        if values is not None:
            build.append(f"data merge block {position(cell)} {selection_snbt(values)}")
        verify.append(
            f"execute unless block {position(cell)} {cell['block']} run "
            "scoreboard players add #failures stone_statues_gallery 1"
        )
        if values is not None:
            verify.append(
                f"execute unless data block {position(cell)} {selection_snbt(values)} run "
                "scoreboard players add #failures stone_statues_gallery 1"
            )
        verify.append("scoreboard players add #checked stone_statues_gallery 1")
    build.append(
        'tellraw @a [{"text":"Built the bounded eight-cell Stone Statues gallery.",'
        '"color":"green"}]'
    )
    verify.extend((
        "execute unless score #checked stone_statues_gallery matches 8 run "
        "scoreboard players add #failures stone_statues_gallery 1",
        "execute if score #failures stone_statues_gallery matches 0 run tellraw @a "
        '[{"text":"Stone Statues gallery verification passed: 8 cells.","color":"green"}]',
        "execute unless score #failures stone_statues_gallery matches 0 run tellraw @a "
        '[{"text":"Stone Statues gallery verification failed.","color":"red"}]',
    ))
    generated: dict[Path, bytes] = {
        ROOT / "cases.json": case_json,
        ROOT / "cases.tsv": ("\n".join(rows) + "\n").encode(),
        ROOT / "datapack/pack.mcmeta": (
            '{\n  "pack": {\n    "description": "ATM 1.2.0 Stone Statues '
            'bounded BlueMap gallery",\n    "pack_format": 48\n  }\n}\n'
        ).encode(),
        ROOT / "datapack/data/minecraft/tags/function/load.json": (
            '{\n  "values": [\n    "stone_statues_gallery:load"\n  ]\n}\n'
        ).encode(),
        FUNCTIONS / "build.mcfunction": ("\n".join(build) + "\n").encode(),
        FUNCTIONS / "verify.mcfunction": ("\n".join(verify) + "\n").encode(),
        FUNCTIONS / "clear.mcfunction": (
            "# Generated by gallery/generate.py; do not hand-edit.\n"
            "fill 204 99 212 236 104 228 minecraft:air replace\n"
        ).encode(),
        FUNCTIONS / "pose.mcfunction": (
            "# Generated by gallery/generate.py; do not hand-edit.\n"
            "tp @s 220.5 115 249.5 180 22\n"
            "gamemode spectator @s\n"
        ).encode(),
        FUNCTIONS / "release.mcfunction": (
            "# Generated by gallery/generate.py; do not hand-edit.\n"
            "forceload remove 204 212 236 228\n"
            "save-all flush\n"
        ).encode(),
        FUNCTIONS / "load.mcfunction": (
            'tellraw @a [{"text":"Stone Statues gallery ready: run '
            '/function stone_statues_gallery:build","color":"aqua"}]\n'
        ).encode(),
    }
    sums = []
    for path, raw in sorted(generated.items(), key=lambda item: item[0].as_posix()):
        sums.append(f"{hashlib.sha256(raw).hexdigest()}  {path.relative_to(ROOT)}")
    generated[ROOT / "SHA256SUMS"] = ("\n".join(sums) + "\n").encode()
    return generated


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for path, expected in outputs().items():
        if args.check:
            if not path.is_file() or path.read_bytes() != expected:
                raise SystemExit(f"generated gallery drift: {path.relative_to(ROOT)}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(expected)
    print("Verified deterministic eight-cell Stone Statues gallery.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
