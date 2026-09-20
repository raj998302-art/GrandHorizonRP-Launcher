#!/bin/bash
# Host-side engine verification: compiles the engine asset pipeline with the system
# g++ and runs it against real game-data samples (from the public gamedata release).
# Validates: MOD decryption + mesh parse (cylinder known geometry + character mesh),
# KTX/ASTC texture probe, ANP3 animation parse.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
FMT="$WORK/fmt"
BASE="https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/gamedata"

mkdir -p "$FMT/ext"
for f in files.mesh.br_common.bpc files.mesh.br_anim.bpc files.textures.Characters.astc.bpc.tmb; do
  curl -fsSL --retry 3 -o "$FMT/$f" "$BASE/$f"
done

# Extract the girl character mesh (first ~6MB of br_skins_01 contains AK_Blcvtk_girl)
curl -fsSL --retry 3 -r 0-6291455 -o "$FMT/files.mesh.br_skins_01.bpc.part" "$BASE/files.mesh.br_skins_01.bpc"
python3 - "$FMT" << 'PYEOF'
import struct, sys, zipfile, zlib
fmt = sys.argv[1]
z = zipfile.ZipFile(f'{fmt}/files.mesh.br_common.bpc')
open(f'{fmt}/ext/zonecylb.mod', 'wb').write(z.read('zonecylb.mod'))
# stream-parse local headers of the partial archive
data = open(f'{fmt}/files.mesh.br_skins_01.bpc.part', 'rb').read()
pos = 0
while pos + 30 <= len(data):
    hdr = data[pos:pos+30]
    if hdr[:4] != b'PK\x03\x04': break
    crc, csize, usize, nlen, elen = struct.unpack('<IIIHH', hdr[14:30])
    name = data[pos+30:pos+30+nlen].decode()
    off = pos + 30 + nlen + elen
    if name == 'AK_Blcvtk_girl.mod' and off + csize <= len(data):
        raw = zlib.decompress(data[off:off+csize], -15)
        open(f'{fmt}/ext/AK_Blcvtk_girl.mod', 'wb').write(raw)
        print('extracted', name, len(raw))
        break
    pos = off + csize
PYEOF

CP="$ROOT/launcher-gradle/app/src/main/cpp"
g++ -std=c++17 -O2 -I "$HERE/hosttest/stubs" -I "$CP" -I "$CP/gh" \
    "$HERE/hosttest/test_main.cpp" "$HERE/hosttest/glstub.cpp" \
    "$CP/gh/assets/ModMesh.cpp" "$CP/gh/assets/BtxTexture.cpp" \
    "$CP/gh/assets/AniAnimation.cpp" "$CP/gh/assets/BpcArchive.cpp" \
    -lz -o "$WORK/enginetest"
"$WORK/enginetest" "$FMT"
