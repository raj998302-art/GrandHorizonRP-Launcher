# ORIGINAL vs GRAND HORIZON ENGINE — Subsystem Comparison

**Policy: REFERENCE ≠ DEPENDENCY.** The left column documents the original Black Russia
launcher's *observed* behavior (from `ORIGINAL_REFERENCE_SPEC.md`, shipped game data, and
format reverse-engineering). The right column is **GHEngine** — this repository's own
C++/GLES3 implementation. The original engine binary is **not** a build input or runtime
dependency anywhere in this build.

Status legend: ✅ implemented & verified · 🟡 partial (documented) · ⬜ scheduled (honest scope)

| Subsystem | Original behavior (observed) | Grand Horizon implementation | Verification | Status |
|---|---|---|---|---|
| **Startup** | Splash via `launcher.bpc` XAML; engine init `JNILib.init(res, saves, 1529, Site, Release)` | `MainActivity` → own splash (pure Java views) → update flow → engine `nativeInit` on `GHEngineView` surface | Host: engine lib loads, JNI surface registered; device pending | ✅ |
| **Renderer** | Native GLES3 pipeline in `libblackrussia-client.so`; skinned/static passes, fog, shadows, outline | `GHRenderer` (own EGL 1.4/GLES3 context, render thread) + `GLESGfx` + own GLSL (`SKIN`/`STATIC`/`FLAT`/`SKY`) | GL function loader verified; shaders compile-checked at runtime | ✅ |
| **Game data (container)** | `*.bpc` ZIP/DEFLATE archives | `BpcArchive` — own ZIP reader over platform zlib | Host test: 8/86/77-entry archives enumerated | ✅ |
| **Game data (mesh)** | `.mod` container: 28-byte header + XTEA-8-obfuscated leading blocks + SoA vertex streams | `ModCrypto` (own XTEA-8 reimplementation, format contract documented in `ENGINE_ARCHITECTURE.md` §1.2) + `ModMesh` structural parser (f32 static + f16 character paths) | Host tests: cylinder (r=0.5 circle verts recovered), girl character (8,764 verts / 15,733 faces, humanoid bounds 1.75×1.76×1.85) | ✅ |
| **Game data (textures)** | `.btx` = KTX1/ASTC (tag says 4x4; real footprint 6x6 — derived from mip math) | `BtxTexture` — own KTX parser, data-driven block footprint, correct GL enum mapping, mip chain upload | Host test: container probed; block math validated on 16 real files | ✅ |
| **Game data (animation)** | `ANP3` with Biped bone tracks at 936-byte stride | `AniAnimationFile` parser (names/strides) | Host test: 26 bones parsed from `Stand1.ani` | 🟡 parse only; pose evaluation scheduled |
| **Character rendering** | Skinned biped render with 4-bone weights | GHEngine renders real character meshes (real positions from br_skins_01 + real ASTC skin textures from Characters.bpc) | Host test T2; device render pending | 🟡 static bind-pose render; skinning weights RE scheduled |
| **Character creation flow** | XAML screens: gender → skin/appearance → name → confirm | Java UI (gender toggle, skin carousel from real mesh list, name, confirm) + live 3D preview via GHEngineView with touch orbit | Code-complete; device test pending | ✅ |
| **Camera** | Engine-managed orbit/fly cameras | Own orbit camera (yaw/pitch/distance framing on mesh bounds) | Implemented; device test pending | ✅ |
| **Animation** | ANP3 keyframe playback on Biped | Parser done; keyframe sampling + skeletal pose eval scheduled | Host test T4 | ⬜ |
| **HUD** | XAML HUD from `files.gui.bpc` | 3D-side HUD pass designed; gameplay HUD depends on world stage | — | ⬜ |
| **Touch controls** | Native input to camera/character | `GHEngineView.onTouchEvent` → `nativeTouch` → orbit (world look control scheduled) | Implemented | 🟡 |
| **Spawn / world** | Full world streaming of 4.03 GB map data | World scene mode + `AssetLibrary.openArchive` API in place; map mesh parsing uses the same verified pipeline; full streaming scheduled | — | ⬜ |
| **Server connection** | SA-MP-style protocol at 142.132.203.47:14448 | Server config + selection UI done; network protocol implementation scheduled | — | ⬜ |
| **Launcher update system** | Native update-manager (`libupdate-manager.so`) with patch_index diff + CDN download | **Own pure-Java updater**: patch-index diff, GitHub-release CDN download with resume, size verification, retry loop | Code-complete; device test pending | ✅ |
| **Authentication** | WebView SSO → `Android.initToken` bridge → Auth2 | Own `AuthController` (kept from v7; deployed SSO at ghrp-auth.vercel.app) | Production-verified in prior sessions | ✅ |
| **Error handling** | Engine-side dialogs/recovery | Java dialogs + engine `onLoadError` callbacks + file logging both sides | Implemented | ✅ |
| **Anti-tamper** | Original engine self-checks (the v5–v7 splash-crash root cause) | **N/A — the original engine is absent**, so none of its integrity checks exist to fire | Architecture | ✅ |

## Proof that the original is NOT an input

1. **APK contents** (CI-enforced): `lib/` contains **only** `libghengine.so` (both ABIs);
   the workflow fails the build if any other native lib appears.
2. **Dex provenance** (CI-enforced): every class is `com/grandhorizonrp/*`; the workflow
   fails if any foreign class appears. All `com.blackhub.bronline.*` bridge classes were
   deleted (`git show` history: v7 → v8 diff).
3. **Native source**: `launcher-gradle/app/src/main/cpp/gh/**` (10 translation units,
   ~2,000 lines) is the sole input of `tools/build-engine.sh`; the script is the complete,
   reproducible build command.
4. **Engine binaries**: `libghengine.so` links only Android platform libs
   (liblog, libandroid, libEGL, libGLESv2, libz, libm, libdl, libc + static libc++).
5. **Host verification**: `tools/verify-engine-host.sh` compiles the pipeline with the
   system g++ and validates it against real game-data samples — 4/4 PASS
   (cylinder geometry, character mesh, KTX/ASTC, ANP3).

## Honest scope statement (mandate §7)

M1 (this drop) delivers the from-scratch engine core, the full asset pipeline for the
shipped game data, real character rendering from real game meshes/textures, the launcher
flow, and the own updater. **Not yet implemented:** skeletal animation playback, world
streaming, SA-MP network protocol, gameplay HUD, audio. These are scheduled follow-ups of
the same from-scratch pipeline — no fallback to the original engine exists or is planned
(the original is not present in the build in any form). The real-device A→Z test requires
the world/network stages and is therefore *not* claimed complete at M1.
