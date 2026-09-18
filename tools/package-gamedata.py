#!/usr/bin/env python3
"""Grand Horizon RP - Game Data Packager.

Streams entries from the original client zip file into clean per-category
game-data packages that the Android launcher downloads from the GitHub
'latest' release.

Usage:
    python3 package-gamedata.py <source-files.zip> <output-dir> [version]

Unwanted entries removed automatically:
    - files/date_marker_*   (internal host markers)
    - files/jsons/pt-br/*   (unused Portuguese locale)
"""
import hashlib
import json
import os
import sys
import zipfile

RELEASE_BASE = "https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest"


def package_for(name):
    if name.startswith("files/jsons"):
        return "gamedata-core.zip"
    if name in ("files/common.bpc", "files/gui.bpc", "files/launcher.bpc"):
        return "gamedata-core.zip"
    if name.startswith("files/audio/"):
        return "gamedata-audio.zip"
    if name.startswith("files/mesh/"):
        return "gamedata-mesh.zip"
    if name.startswith("files/textures/"):
        return "gamedata-textures.zip"
    if name.startswith("files/resources/"):
        return "gamedata-resources.zip"
    return None


def is_unwanted(name):
    return ("date_marker" in name) or ("jsons/pt-br" in name)


def main():
    src_zip = sys.argv[1] if len(sys.argv) > 1 else "/home/z/Downloads/files.zip"
    out_dir = sys.argv[2] if len(sys.argv) > 2 else "dist"
    version = sys.argv[3] if len(sys.argv) > 3 else "1.0.0"
    os.makedirs(out_dir, exist_ok=True)

    src = zipfile.ZipFile(src_zip)
    zips = {}
    counts = {}

    for entry in src.infolist():
        name = entry.filename
        if name.endswith("/"):
            continue
        if is_unwanted(name):
            continue
        pkg = package_for(name)
        if pkg is None:
            continue
        if pkg not in zips:
            zips[pkg] = zipfile.ZipFile(os.path.join(out_dir, pkg), "w", zipfile.ZIP_STORED)
            counts[pkg] = 0
        with src.open(name) as fin, zips[pkg].open(name, "w") as fout:
            while True:
                chunk = fin.read(1024 * 1024)
                if not chunk:
                    break
                fout.write(chunk)
        counts[pkg] += 1

    parts = []
    for pkg in sorted(zips.keys()):
        zips[pkg].close()
        path = os.path.join(out_dir, pkg)
        size = os.path.getsize(path)
        h = hashlib.sha256()
        with open(path, "rb") as f:
            while True:
                chunk = f.read(1024 * 1024)
                if not chunk:
                    break
                h.update(chunk)
        parts.append({
            "name": pkg,
            "size": size,
            "sha256": h.hexdigest(),
            "url": f"{RELEASE_BASE}/{pkg}",
        })
        print(f"{pkg}: {size} bytes ({counts[pkg]} files)")

    manifest = {"version": version, "parts": parts}
    with open(os.path.join(out_dir, "gameData-manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print("Wrote gameData-manifest.json")


if __name__ == "__main__":
    main()
