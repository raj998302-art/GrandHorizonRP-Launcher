# GRAND HORIZON RP — ENGINE ARCHITECTURE (FROM-SCRATCH NATIVE ENGINE)

**Policy: REFERENCE ≠ DEPENDENCY.** The original Black Russia engine (`libblackrussia-client.so`
and its 12 companion libraries) is **never** a build input or runtime dependency of this project.
It was used only to *observe and document* behavior (file formats, obfuscation, shaders, flow).
Every subsystem below is independently implemented in this repository's own source tree.

- Engine name: **GHEngine**
- Binary: `libghengine.so` (built from `launcher-gradle/app/src/main/cpp/` — this repo's own C++ sources)
- Language: C++17, OpenGL ES 3.0, EGL 1.4, NDK r27c (clang 18)
- ABIs: `arm64-v8a`, `armeabi-v7a`
- Authoritative reference doc for the original's observable behavior: `ORIGINAL_REFERENCE_SPEC.md`

---

## 1. Reverse-engineered asset contracts (the "WHAT" — observed from the original)

These contracts were derived by *observation*: hex analysis of shipped game-data files,
structural validation against known geometry (e.g. a cylinder test mesh whose vertices must
lie on a circle), and disassembly of the original engine binary **for format documentation
only** (§1.4). No original code, tables or shaders are copied into GHEngine.

### 1.1 Container: `*.bpc` = ZIP (PK) archives
- Standard ZIP with per-entry DEFLATE; local-file-header sizes are valid (no data descriptors).
- Naming: `files.mesh.<group>.bpc`, `files.textures.<group>.astc.bpc` (+ `.tmb` preview sidecars),
  `files.audio.*`, `files.jsons.*` (plain ZIPs), `files.gui.bpc` (978 JSON UI definitions),
  `files.launcher.bpc` (launcher UI — superseded by our own Java UI).
- Contents observed: `*.mod` (mesh), `*.cls` (cluster metadata), `*.ani` (animation),
  `*.btx` (texture), JSON files.
- `files.common.bpc` is **not** a ZIP (high-entropy, custom/encrypted container — classified
  original-launcher-specific; not consumed by GHEngine; role reported in the comparison doc).

### 1.2 Mesh: `*.mod`
```
Header (28 bytes, plaintext):
  u32 magic     = 0xAB921033
  u32 dsize     = size of body (filesize == 28 + dsize, verified on 9 samples)
  u32 cnt       = number of leading 2048-byte body blocks that are encrypted
  5 × u32 reserved (zero)
  u32 hash      (per-file)

Body (dsize bytes):
  The first cnt×2048 bytes are encrypted with a custom XTEA-8 variant:
    key (4×u32) = derived from a 16-byte constant embedded in the ORIGINAL engine binary
                  via XOR 0x12913AFB then ROR 19 per word — derived key words include the
                  mesh magic itself (0xAB921033), confirming the derivation.
    Per 8-byte pair, 8 rounds, sum starts at Δ×8 (Δ = 0x9E3779B9), decrements per round:
      v1 -= (k2 + (v0<<4)) ^ (v0 + sum) ^ (k3 + (v0>>5))
      v0 -= (k0 + (v1<<4)) ^ (v1 + sum) ^ (k1 + (v1>>5))
      sum -= Δ
    (Reproduced independently in `cpp/gh/assets/ModCrypto.h`; validated by decrypting the
    shipped `zonecylb.mod` and recovering exact cylinder vertices at radius 0.5, height
    1.669, 36° segment steps, plus unit normals and embedded mesh names.)

Plaintext body structure (empirically mapped, SoA-leaning):
  0x000  submesh/material table records (contain 0x1400FFFF / 0x1803FFFF tag words)
  ~0x040 identity/bind matrices (4×4 float, row-major, scale 1.0)
  ~0x080 embedded mesh name (ASCII) + 0x1A terminator tag
  ...    further table records incl. counts/offsets
  mid    u16 index arrays (triangle lists, small ascending values, zero-terminated)
  ...    u32 index arrays for other LODs/submeshes
  later  float32 position arrays (x,y,z — validated by geometry)
  later  float32 normal arrays (unit length — validated)
  later  float32 UV arrays ([0..1] range)
GHEngine's parser (`ModMesh.h`) locates these blocks structurally (index-run detection +
float-distribution classification) and validates against per-file `.cls` bounds.
```

### 1.3 Cluster metadata: `*.cls`
`CLST` magic, u32 total size, 32-byte name, then float triples: mesh bounds
min(x,y,z), max(x,y,z) (validated: cylinder r=0.5 h=1.0), then optional transform data.

### 1.4 Texture: `*.btx`
4-byte prefix (observed 0) + **standard KTX v1** container:
ASTC-compressed (`GL_COMPRESSED_RGBA_ASTC_4x4_KHR` 0x93B4 and 6x6 0x93B7 observed,
with full mip chains; mip sizes match ASTC block math exactly — e.g. 512×512 ASTC 6x6
= ceil(512/6)² × 16 = 118336 B). Upload via `glCompressedTexImage2D`.
Devices without `GL_KHR_texture_compression_astc_ldr` are reported at startup
(minSdk 26 devices overwhelmingly support ASTC LDR).

### 1.5 Animation: `*.ani` (ANP3)
Plaintext magic `ANP3`, animation name (ASCII), internal track name, then a bone-track
table: bone names (classic 3ds-Max Biped hierarchy observed: `Root`, `Pelvis`, `Spine`,
`Spine1`, `Neck`, `Head`, `Bip01 L Clavicle`, …) at fixed track stride (936 B observed),
each track containing keyframe data for translation/rotation(quaternion)/scale.
GHEngine implements its own ANP3 reader + skeletal pose evaluation (§3.4).

### 1.6 Observed original render contract (behavioral, not copied)
- Skinning: 4 bone indices + 4 weights per vertex, standard weighted transform blend.
- Fog, shadow-projection and outline (cel) passes exist in the original's shader set —
  GHEngine implements its own equivalents with its own GLSL (§3.2).
- UI flow: character creation (male/female → skin/appearance) → confirm → spawn → HUD,
  driven in the original by `files.gui.bpc` JSONs; GHEngine's flow mirrors the states.

## 2. Engine module architecture

```
cpp/gh/
├── core/     GHLog.h            engine file logger (logs/engine.txt)
│             GHMath.h           vec2/3/4, mat4, quat — own implementation
├── assets/   ModCrypto.h        XTEA-8 mesh decryption (contract §1.2)
│             BpcArchive.h/.cpp  ZIP reader over system zlib (-lz), streaming local headers
│             BtxTexture.h/.cpp  KTX/ASTC → GL textures
│             ModMesh.h/.cpp     decrypt + structural mesh parse → GPU-ready arrays
│             AniAnimation.h/.cpp ANP3 parser + keyframe sampling
│             AssetLibrary.h/.cpp mounts files.*.bpc from the game-data dir, lookup API
├── render/   GHShaderLib.h      GHEngine's own GLSL sources
│             GLESGfx.h/.cpp     shader/buffer/texture helpers, GL state
│             GHRenderer.h/.cpp  EGL context, surface, camera, frame loop
├── game/     GHEngine.h/.cpp    scene graph, update/render, modes
│             CharacterScene.cpp character-creation 3D scene (real game meshes)
├── jni/      GHNative.cpp       JNI surface for Java (§4)
```

## 3. Rendering pipeline

### 3.1 Context
EGL 1.4 display/config (EGL_OPENGL_ES3_BIT, RGBA8888, 24+8 depth/stencil), GLES 3.0
context, dedicated render thread, `ANativeWindow` from Java `SurfaceHolder`.

### 3.2 Shading (own GLSL, written for GHEngine)
- `SKIN`: skinned mesh, 4-bone palette via `u_boneMatrices`, Lambert + half-Lambert
  hemisphere lighting, ASTC texture sampling, simple fog.
- `STATIC`: same pipeline without skinning (world/props).
- `FLAT`: untextured debug/prim pass (bounds, grid floor).

### 3.3 Frame
clear → sky gradient (fullscreen triangle) → opaque passes (STATIC, SKIN) →
ortho HUD pass (Java-driven UI stays in Android views; engine draws 3D-side HUD
crosshair/world markers only).

### 3.4 Characters
`ModMesh` (from `files.mesh.br_skins_01.bpc`) + `BtxTexture` (matching skin from
`files.textures.Characters.astc.bpc`) → skinned render with `AniAnimation` pose
(Stand/idle) when the mesh exposes weight data; static bind pose otherwise.
Character creation UI (Java) drives `nativeSetCharacter(skinIndex, …)`; the engine
reloads the mesh/texture pair live.

## 4. JNI surface (`com.grandhorizonrp.launcher.engine.GHNative`)
```
nativeInit(Surface, w, h) / nativeResize(w, h) / nativeStart() / nativeStop() / nativeDestroy()
nativeSetDataRoot(String path)          // game-data dir (from UpdateController)
nativeLoadScene(int mode)               // MODE_SPLASH / MODE_CHARACTER_CREATION / MODE_WORLD
nativeSetCharacter(int skinIndex)       // reload mesh+texture pair
nativeTouch(int action, float x, float y) // orbit/zoom for character view, look for world
nativeTick()                            // optional Java-driven pump (unused in release)
→ callbacks into Java: onEngineReady(), onSceneReady(int), onLoadError(String)
```

## 5. Build system
`tools/build-engine.sh` — direct NDK clang build (documented, reproducible):
per-TU compile (`aarch64-linux-android26-clang` / `armv7a-linux-androideabi26-clang`,
`-O2 -fvisibility=hidden -fPIC -Wall`, `-lz` system zlib, `-llog -landroid -lEGL -lGLESv2`),
link `libghengine.so`, `llvm-strip`, place into `app/src/main/jniLibs/<abi>/`.
AGP packages the result; `useLegacyPackaging true` keeps install-time extraction.
CI runs the same script before `gradlew assembleRelease`.

## 6. Milestones & honest scope
- **M1 (this drop):** engine core + asset pipeline + character scene rendering REAL
  game meshes/textures (from the shipped 4 GB data) with touch orbit — plus the
  launcher flow wiring. World streaming, SA-MP protocol, audio: architecture documented,
  implementation scheduled (see `ORIGINAL_VS_GRANDHORIZON_ENGINE.md` status table).
- Anti-tamper interplay: none — the original engine is absent, so none of its
  integrity checks can fire. (This was the root cause of the v5–v7 1–2 s splash crash.)
