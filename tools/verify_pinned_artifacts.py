#!/usr/bin/env python3
"""Verify exact ATM 1.2.0 Stone Statues inputs without retaining them."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import zipfile


EXPECTED = {
    "stone": {
        "size": 170_977,
        "sha256": "0fece8e5a988b660f2b608a316f88e2572290835fec1acf67d78329133e92f09",
        "required": {
            "META-INF/neoforge.mods.toml",
            "com/jorgaomc/cobblemonstonestatues/compat/CobblemonStoneBridgeImpl.class",
        },
    },
    "cobblemon": {
        "size": 128_748_941,
        "sha256": "962d75df4fb649d94863a7a7d130d4d2b3de4da9b3cae4c44b1ce90f37ec0ed5",
        "required": {
            "META-INF/neoforge.mods.toml",
            "com/cobblemon/mod/common/CobblemonBuildDetails.class",
        },
    },
}

CLASS_IDENTITIES = {
    "com/cobblemon/mod/common/client/render/models/blockbench/pokemon/gen7/DecidueyeHisuianModel.class": (
        17_596,
        "9ee3143f6a4b37418f706f006aba852416b9eb89d2a3819766183f81081e3f8d",
    ),
    "com/cobblemon/mod/common/client/render/models/blockbench/animation/WingFlapIdleAnimation.class": (
        8_131,
        "48bd55714e03f61e0bbec08b5666b298634c1064168ae39179afbe994c0a14f6",
    ),
    "com/cobblemon/mod/common/client/render/models/blockbench/wavefunction/WaveFunctionKt.class": (
        11_534,
        "5e0346ed51833e6ba1d55e2c4682a69dd69dc8545dbe0368a5da5a5e1dc7660c",
    ),
}

REPRESENTATIVE_RESOURCES = {
    "data/cobblemon/species/generation1/bulbasaur.json": (3331, "8c47cef1da5be6bb332da1d76cc5b926070e50b50c629b2bd6f559a275eb00c8"),
    "data/cobblemon/species/generation1/pikachu.json": (6021, "63c005932031ec59066e41236d35b7cf2c02698d5328ff1f28c2f86f37bff0d4"),
    "data/cobblemon/species/generation5/joltik.json": (3149, "b1415136ac7708d4664bf83038fca9b6cae6059d54119618959006b82593687e"),
    "data/cobblemon/species/generation7/decidueye.json": (6226, "3236aecb2d0d4945843616658fce4aacaf4cb45fd48fb2c28e9e7bd7df12d3a7"),
    "assets/cobblemon/bedrock/pokemon/resolvers/0001_bulbasaur/0_bulbasaur_base.json": (313, "f83c4f959e1c2429173fb620423a144c93215b6216f679b13a660d7bb0dc6f5f"),
    "assets/cobblemon/bedrock/pokemon/resolvers/0025_pikachu/0_pikachu_base.json": (5992, "5786e850520491426addd8da9f6f5301b8e7cffa5adcdbee5711c20fc965a396"),
    "assets/cobblemon/bedrock/pokemon/resolvers/0595_joltik/0_joltik_base.json": (514, "15b863593b99c076e2685f3832f5e0c969c55c9e57eef23cffe6019c46879ef0"),
    "assets/cobblemon/bedrock/pokemon/resolvers/0724_decidueye/0_decidueye_base.json": (313, "45265d15727e443413165dfdcb1107ec803839dea82a62715aad572b660f6a1f"),
    "assets/cobblemon/bedrock/pokemon/resolvers/0724_decidueye/1_decidueye_hisuian.json": (364, "95439fb076a6cdd1a8d4b018cbd43183e5e36212604b301c9f76bfcf1bb30705"),
    "assets/cobblemon/bedrock/pokemon/posers/0001_bulbasaur/bulbasaur.json": (947, "8a105725d3bcf22686628fd59d990087f164279caea1d05151117dcd2858d2f0"),
    "assets/cobblemon/bedrock/pokemon/posers/0025_pikachu/pikachu.json": (2530, "42391fef5a270007874aacdd6d47a64e9e8533e3d6f8f3f99b08997e624851a7"),
    "assets/cobblemon/bedrock/pokemon/posers/0595_joltik/joltik.json": (708, "3faabdfc798a5f40476c087cc4e29ce1ca663bba025c259eb3ee70a4b611a068"),
    "assets/cobblemon/bedrock/pokemon/models/0001_bulbasaur/bulbasaur.geo.json": (7232, "dbd51f848eb7de2e984cbffe1e8f6247df895930947d33136bd2e8254d62840d"),
    "assets/cobblemon/bedrock/pokemon/models/0025_pikachu/pikachu_male.geo.json": (10856, "f8ea21f6821d49e8a358f05d43562312a0e018e883f1354aa1445d2a0b432c83"),
    "assets/cobblemon/bedrock/pokemon/models/0595_joltik/joltik.geo.json": (5988, "864070a78b13e9f8ce608210e3772d6d494d0c9e4621a8c631680368993c9d7f"),
    "assets/cobblemon/bedrock/pokemon/models/0724_decidueye/decidueye_hisuian.geo.json": (38759, "e748da043e24ce5dab844feb306b34f6f972277d684ff7a043dfbeed024d98a4"),
    "assets/cobblemon/bedrock/pokemon/animations/0001_bulbasaur/bulbasaur.animation.json": (18845, "b259d6ee0550680c60f856a4b2df07741edca6213139dcebe2d4d0dd067cbebc"),
    "assets/cobblemon/bedrock/pokemon/animations/0025_pikachu/pikachu.animation.json": (217435, "d9ca00604978f295ad312d358a06f2655c725b30ac3da73c3637ae160c543384"),
    "assets/cobblemon/bedrock/pokemon/animations/0595_joltik/joltik.animation.json": (2846, "c410dc3b64563bbf96713550e0ff655381f93644dbf307be1e803678e87c534a"),
    "assets/cobblemon/bedrock/pokemon/animations/0724_decidueye/decidueye.animation.json": (10089, "f9191a35bd9e6c5c3e92a711ce423b246ffa32049eb0e717cf43bd3e3560ffef"),
    "assets/cobblemon/textures/pokemon/0001_bulbasaur/bulbasaur.png": (1136, "85e2c259135415e58f03d4c5bf94dd37d358caa86313ec4eb99b6cf65d5a767c"),
    "assets/cobblemon/textures/pokemon/0025_pikachu/pikachu.png": (1405, "df0b0b2029e0cb51ace2fd7d65ce94fc6a7bf1a4681722bf20aa22edd2cc3c8e"),
    "assets/cobblemon/textures/pokemon/0595_joltik/joltik.png": (758, "7787a2d93e5f28c8f091153dc32001ba568a1dca23edb47225a9c7ce4baeb62e"),
    "assets/cobblemon/textures/pokemon/0724_decidueye/decidueye_hisuian.png": (6118, "7839e413148fd243de64dd2e259d805a2de27112b1cb4fd0fabd930b6f6c54f3"),
}


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def verify_file(label: str, path: Path) -> None:
    expected = EXPECTED[label]
    raw = path.read_bytes()
    assert len(raw) == expected["size"], f"{label} size mismatch"
    assert sha256(raw) == expected["sha256"], f"{label} SHA-256 mismatch"
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        assert expected["required"] <= names, f"{label} required entries missing"
        if label == "cobblemon":
            assert len(REPRESENTATIVE_RESOURCES) == 24
            assert sum(size for size, _ in REPRESENTATIVE_RESOURCES.values()) == 351_875
            for entry, identity in {**REPRESENTATIVE_RESOURCES, **CLASS_IDENTITIES}.items():
                data = archive.read(entry)
                assert (len(data), sha256(data)) == identity, f"entry drift: {entry}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stone-statues", required=True, type=Path)
    parser.add_argument("--cobblemon", required=True, type=Path)
    args = parser.parse_args()
    verify_file("stone", args.stone_statues)
    verify_file("cobblemon", args.cobblemon)
    print("Exact Stone Statues/Cobblemon artifacts and representative closure verified.")


if __name__ == "__main__":
    main()
