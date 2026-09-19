#!/usr/bin/env python3
"""Assemble final APK with correct compression (deflate everything except doNotCompress types)."""
import zipfile, os, shutil

SRC = "/home/z/ghrp-work/launcher_src/launcher_src"
BASE = "/tmp/ghrp_base.apk"
OUT = "/home/z/ghrp-work/ghrp-launcher-unsigned.apk"

STORE_EXT = {".arsc", ".png", ".webp", ".ogg", ".mp3", ".wav", ".jpg", ".jpeg", ".prof", ".profm", ".binarypb"}
STORE_FILES = {
    "res/raw/root_r6.crt",
    "res/raw/com_android_billingclient_heterodyne_info",
    "res/raw/com_android_billingclient_registration_info.binarypb",
    "assets/dexopt/baseline.prof",
    "assets/dexopt/baseline.profm",
    "resources.arsc",
}

def compress_level(name):
    lname = name.lower()
    base = lname.rsplit("/", 1)[-1]
    if name in STORE_FILES or base in STORE_FILES:
        return zipfile.ZIP_STORED
    ext = os.path.splitext(lname)[1]
    if ext in STORE_EXT:
        return zipfile.ZIP_STORED
    return zipfile.ZIP_DEFLATED

count = 0
with zipfile.ZipFile(OUT, "w") as out:
    # 1. base apk (manifest, resources.arsc, res/*)
    with zipfile.ZipFile(BASE) as base:
        for info in base.infolist():
            lvl = compress_level(info.filename)
            out.writestr(info.filename, base.read(info.filename), compress_type=lvl)
            count += 1
    # 2. dex files
    for dex in sorted(os.listdir(f"{SRC}/build/apk")):
        if dex.endswith(".dex"):
            out.write(f"{SRC}/build/apk/{dex}", dex, compress_type=zipfile.ZIP_DEFLATED)
            count += 1
    # 3. assets / lib / kotlin
    for top in ("assets", "lib", "kotlin"):
        for root, _, files in os.walk(f"{SRC}/{top}"):
            for fn in files:
                full = os.path.join(root, fn)
                arc = os.path.relpath(full, SRC)
                out.write(full, arc, compress_type=compress_level(arc))
                count += 1
    # 4. unknown/ contents at original paths
    for root, _, files in os.walk(f"{SRC}/unknown"):
        for fn in files:
            full = os.path.join(root, fn)
            arc = os.path.relpath(full, f"{SRC}/unknown")
            out.write(full, arc, compress_type=compress_level(arc))
            count += 1

sz = os.path.getsize(OUT)
print(f"final APK: {sz:,} bytes ({sz/1e6:.1f} MB), {count} entries")
