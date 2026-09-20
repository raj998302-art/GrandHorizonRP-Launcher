#!/bin/bash
# GHEngine native build — direct NDK clang build (documented in ENGINE_ARCHITECTURE.md §5).
# Produces libghengine.so for arm64-v8a and armeabi-v7a into app/src/main/jniLibs/.
#
# Inputs: ONLY this repository's C++ sources (launcher-gradle/app/src/main/cpp/).
# The original Black Russia engine binaries are NOT build inputs in any form.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$REPO_ROOT/launcher-gradle/app/src/main/cpp"
OUTDIR="$REPO_ROOT/launcher-gradle/app/src/main/jniLibs"
NDK="${NDK_ROOT:-${ANDROID_NDK_HOME:-/opt/android-ndk}}"
if [ ! -d "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin" ]; then
  # common local layout in this workspace
  for c in /home/z/ghrp-scratch/android-ndk-r27c "$HOME/android-ndk-r27c"; do
    [ -d "$c/toolchains/llvm/prebuilt/linux-x86_64/bin" ] && NDK="$c" && break
  done
fi
if [ ! -d "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin" ]; then
  echo "ERROR: NDK toolchain not found (set NDK_ROOT)" >&2; exit 1
fi
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

CFLAGS="-std=c++17 -O2 -fPIC -fvisibility=hidden -Wall -Wextra -Wno-unused-parameter \
 -DGL_GLEXT_PROTOTYPES=0 -I$SRC"

build_abi() {
  local ABI=$1 TRIPLE=$2
  local CC="$TC/$TRIPLE-clang"
  local CXX="$TC/$TRIPLE-clang++"
  local BUILD=$(mktemp -d)
  echo "== building $ABI =="
  local OBJS=()
  for f in \
    gh/assets/BpcArchive.cpp \
    gh/assets/BtxTexture.cpp \
    gh/assets/ModMesh.cpp \
    gh/assets/AniAnimation.cpp \
    gh/assets/AssetLibrary.cpp \
    gh/render/GlFuncs.cpp \
    gh/render/GLESGfx.cpp \
    gh/render/GHRenderer.cpp \
    gh/game/GHEngine.cpp \
    gh/net/GameClient.cpp \
    gh/jni/GHNative.cpp ; do
    o="$BUILD/$(echo "$f" | tr '/' '_').o"
    "$CXX" $CFLAGS -c "$SRC/$f" -o "$o"
    OBJS+=("$o")
  done
  mkdir -p "$OUTDIR/$ABI"
  "$CXX" -shared -static-libstdc++ -o "$OUTDIR/$ABI/libghengine.so" "${OBJS[@]}" \
    -llog -landroid -lEGL -lGLESv2 -lz \
    -Wl,--gc-sections -Wl,-z,max-page-size=16384 \
    -Wl,--build-id=sha1 \
    -Wl,-soname,libghengine.so
  "$TC/llvm-strip" "$OUTDIR/$ABI/libghengine.so"
  rm -rf "$BUILD"
  ls -la "$OUTDIR/$ABI/libghengine.so"
}

build_abi arm64-v8a aarch64-linux-android26
build_abi armeabi-v7a armv7a-linux-androideabi26
echo "== done =="
