# ORIGINAL NATIVE ENGINE STRUCTURE (Forensic Audit, Task 25-B)

> Inspects `orig-decode/launcher_src/lib/` (79 MB across 2 ABIs) with `nm -D`, `readelf -d`,
> `strings`, `file`. Focus ABI for deep analysis: **armeabi-v7a**. Reference only.
> Tool evidence commands: `nm -D --defined-only`, `nm -D -u`, `readelf -d`, `strings -a`.

---

## 1. Library inventory (per ABI)

| .so | arm64-v8a | armeabi-v7a | Role |
|---|---|---|---|
| **libblackrussia-client.so** | 28,657,728 | 20,750,296 | THE game engine + launcher-native logic (client) |
| **libupdate-manager.so** | 9,830,984 | 6,785,024 | resource patcher/downloader (own curl+TLS, rollback/backup) |
| libcrypto.so | 5,719,104 | 3,838,100 | OpenSSL/BoringSSL (for client .so https) |
| libssl.so | 1,021,096 | 880,708 | OpenSSL TLS |
| libcrashlytics-common.so | 864,616 | 534,164 | Firebase Crashlytics NDK |
| libsigner.so | 1,145,112 | 1,070,500 | **Adjust SDK signature** (`Java_com_adjust_sdk_sig_NativeLibHelper_{nSign,nOnResume}`) — attribution anti-fraud, not game code |
| libcrashlytics.so + handler + trampoline | ~458 K | ~262 K | Crashlytics |
| libbass.so | 323,368 | 239,268 | **BASS** audio library (un4seen) — engine audio backend |
| libz-ng.so | 119,536 | 93,600 | zlib-ng (fast zlib) |
| libandroidx.graphics.path.so | 10,096 | 7,252 | AndroidX path interpolator |
| libdatastore_shared_counter.so | 7,112 | 4,416 | Jetpack DataStore |
| **TOTAL** | 46 MB | 33 MB | loaded via `JNILib.<clinit>`: `loadLibrary("update-manager")` then `("blackrussia-client")` |

`file`: client = `ELF 32-bit LSB shared object, ARM, EABI5, dynamically linked, for Android 26,
built by NDK`; update-manager = `for Android 24`. Client compiler (ident strings):
**clang 19.0.1 (Android r530567e NDK), +pgo +bolt +lto** (two idents: pgo+bolt+lto+mlgo and
pgo−bolt variants) — a release-grade PGO/LTO build.

## 2. libblackrussia-client.so — linkage

`readelf -d` NEEDED: `libandroid.so, libOpenSLES.so, libz.so, liblog.so, libbass.so,
libEGL.so, libGLESv3.so, libupdate-manager.so, libjnigraphics.so, libssl.so, libcrypto.so,
libdl.so, libm.so, libc.so`.

Notable imports (`nm -D -u`):

* **Graphics**: 83 `gl*` functions (GLES3 core: shaders, VAO/FBO, compressed textures
  `glCompressedTexImage2D`, …) + `eglGetProcAddress`/`eglGetCurrentContext` (extensions).
* **Audio**: full **BASS** surface (`BASS_Init/StreamCreateFile/StreamCreateURL/RecordStart/
  ChannelSet3D*/Apply3D/Set3DPosition/…`) + **OpenSLES** (`SL_IID_ENGINE, SL_IID_PLAY,
  SL_IID_RECORD, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_ANDROIDCONFIGURATION`) + vendored
  **Opus 1.4** (58 source paths `vendor/opus/opus-1.4/…`) + **mpg123** (56 paths) + OpenAL
  extension strings (AL_SOFT_*, AL_EXT_* — an OpenAL layer exists in-engine).
* **Bitmaps**: `AndroidBitmap_lockPixels/unlockPixels` (+ libjnigraphics NEEDED) — engine pulls
  decoded bitmaps from Java (`onAsyncBitmapRequestDone`).
* **Crypto**: `RAND_bytes` (+ libssl/libcrypto).
* **HTTP**: 15 `curl_*` imports (easy+multi) — engine-side REST (Auth2Model, servers, characters).
* **Update system (from libupdate-manager.so)**: imports
  `UpdateManagerSystemApi::{GetInstance,PrepareWork,Work,DownloadNextSlot,RemoveLastCheckDateMarker,AddFileDownloadedNotification}`,
  `PatchIndexApiClient::Update`, `UpdateManagerSystemCallbacks::{AddUICallback,AddUIErrorCallback,
  SetLogCallback,SetErrorCodeCallback,SetEventSystemCallback,SetReportEventCallback,
  SetPatchIndex{Saved,Restore,Remove}Callback,SetStateMonitor{Saved,Restore,Remove,Exist}Callback,
  SetFailureFlag{Exist,Store,Remove}Callback,SetFreeSpace{Getter,Checker}Callback,
  SetHashCommit{Getter,Setter}Function,SetCompatibleClientVersionSetterFunction}` —
  **direct proof the Java `JNIJSONTransport` storage callbacks are wired here**.
* **Networking**: 70 exported `enet_*` C symbols (full ENet API:
  `enet_host_{create,connect,service,broadcast,flush,compress,…}`, `enet_packet_*`,
  `enet_list_*`, `enet_address_*`, `enet_crc`) **plus** SA-MP-era RakNet inside:
  RTTI `16RakPeerInterface`, `7RakPeer`, `N4util8CallbackIJRN6RakNet9BitStreamEEE`,
  `ReliabilityLayer`-adjacent strings — the game-protocol stack speaks RakNet (matching the
  SA-MP 0.3.7-R2 server), ENet is a second transport (likely launcher/voice/aux).

## 3. Exported JNI surface (60 `Java_…` symbols, `nm -D --defined-only`)

```
Java_com_blackhub_bronline_game_GameRender_{initGameRender,nativeRequestRender,nativeRequestRenderTexture,nativeRequestRenderTexturePlate}
Java_com_blackhub_bronline_game_core_JNIConfig_{nativeDestroy,nativeGameSettings,nativeGetChild,nativeGetFloat,nativeGetIntPacked,nativeGetString,nativeIsValid,nativeSetFloat,nativeSetInt,nativeSetString}
Java_com_blackhub_bronline_game_core_JNILib_{init,initRender,initFileLogger,step,resize,pauseEvent,resumeEvent,orientationChanged,networkConnectionChanged,multiTouchEvent,sendJsonData,deliverNetworkEvents,onAppConfigReceived,onUrlConfigReceived,onMediaOpened,onFrameDecoded,requestPlayers,sendChatMessage,getPlayerId,getPlayerVehicleType,getMutePlayer,setMutePlayer,getVolumePlayer,setVolumePlayer,getJsonFromArchive,getBitmapFromAssetsAsync,getFileFromAssetsAsync,isDonateAllowed,checkResourcesSubscribe(?),getCurrentPatchVersion,getTargetPatchVersion,getAdditionDownloadPatchData,tryGetPatchIndex,tryDownloadResources,tryDownloadNextSlot,cancelDownloadResources,removeLastCheckDateMarker,sendUpdateSystemCodedMessage,setDebugMenuVisible,toggleBloor,toggleDrawing2dStuff,сheckResources}   ← note the Cyrillic 'с' in сheckResources
Java_com_blackhub_bronline_game_core_keyboardHelper_SoftwareKeyboardBridge_{nativeCommitText,nativeOnKeyDown,nativeOnKeyUp,nativeOnKeyboardSizeChanged,nativeOnSetVisible}
Java_com_blackhub_bronline_launcher_di_HelpshiftManager_nativeInit
```

Plus `JNI_OnLoad` (T, addr 0x00e74ec1) — `JNIJSONTransport`'s ~70 static callbacks are bound
via **RegisterNatives at load time** (no `Java_…JNIJSONTransport_…` exports exist, and no
class-name strings remain — registered from code tables).

## 4. Subsystem coverage census

### 4.1 By engine source tree (unique `__FILE__` strings — 256 files under `BRClient/src/`)

| Dir | Files | Content (sample file names from strings) |
|---|---|---|
| `gui/` | 92 | `GuiManager.cpp`, `NoesisBackend.cpp`, `DeliverNetworkUiEvents.cpp`, `screens/` (51: Auth2, SelectCharacter, SelectServer, Registration, Hud, Map, Inventory, Tablet, RaceMode, Radio, Notifications, Freeroam, DamageIndicator, DialogWindows, PortJob, BossFight…), `converters/` 21, `components/` 8, `backend/` 5 |
| `librw/` | 21 | RenderWare-compatible layer (ClumpRead, pipelines, `rw/` 8 more: CustomBuildingRenderer/Pipeline, MemoryMgr, NodeName, RwHelper, imgui_impl_opengl3) |
| `network/` | 19 | `processors/{CurlHttpProcessor,HttpRequestProcessor}`, `requests/HttpApi`, RakNet/ENet transport |
| `core/` | 16 | `CharacterSelectSceneController.cpp`, LauncherMainLoop (in skel/), engine core |
| `renderer/` | 15 | Renderer, Shadows, WaterLevel/WaterCannon, SpecialFX, SnowEmitter, PlantMgr, ParticleMgr, ParaboloidEnvMap, ImFont, EntityPreview, Colorcycle, Chat, RenderSceneMode, speedometer/SpeedometerShapeHelper |
| `vehicles/` | 10 | Automobile, Bike, Boat, BoatFloater, Heli, QuadBike, Trailer, CarModComponent, HandlingMgr, Vehicle |
| `util/` | 9 | JsonStorage, StringUtils, ThrottleTimer, FreezeMonitor, BuildConfig, templates, BlockPool, MultiTypeStore, JsonUtils |
| `peds/` | 7 | WeaponManager, … (character/ped system) |
| `modelinfo/` | 5 | model metadata |
| `audio/` | 5 | `core/sampman.cpp` (SA-MP audio manager heritage), opus/mpg123 integration |
| `physics/` | 4 | dynamics/constraints (DistanceConstraint, SingleLinearAxisConstraintPart… — a real physics engine, Bullet-style layout) |
| `entities/`, `animation/` | 4+4 | entity system; anim assoc groups (`AnimBlendAssocGroup`) |
| `filesystem/` | 7 | packfile/asset IO |
| `weapons/` | 3 | Weapon, WeaponInfo, ProjectileInfo |
| `tdb/` | 2 | **TextureDatabase{,Runtime}** (texture pack manager) |
| `collision/`, `objects/`, `appConfig/`, `touchinterface/`, `tasks/`, `reports/`, `minigames/`, `skel/` | 2/2/2/1/1/1/1/5 | collision, world objects, app config, touch input, task scheduler, Firebase reporting, minigames, platform skeleton (android/HelpshiftBridge, crossplatform, LauncherMainLoop) |

### 4.2 By RTTI type census (3,038 unique mangled `N…E` names in .rodata)

| Family | RTTI hits | Notes |
|---|---|---|
| Gui (N3Gui…) | **1,056** (506 type names in `N3Gui…E` form + 550 mangled refs) | NoesisGUI VM layer — largest subsystem |
| Hud | 88 | HUD VMs (RadarVM, CrosshairVM, ChatVM…) |
| Chat | 70 | chat VMs/channels |
| Anim | 69 | animation association/blend classes |
| Player | 37 | player pool/control |
| Model | 133 | model info classes |
| Net | 28 | network messages |
| Physics | 29 | constraints/bodies |
| Object | 45 | world objects |
| Font | 49 | text/font stack |
| Input | 19 | touch/keyboard |
| Vehicle | 18 | vehicle classes |
| Stream | 25 | IO streams |
| Render | 15 | renderer classes |
| Texture | 14 | texture classes |
| Weapon | 12 | weapons |
| Audio | 5 (+BASS/OpenSLES/Opus/OpenAL surfaces) | |
| Collision | 6 | |
| World | 2 | (world state is C-side, not RTTI-heavy) |
| RakNet | 2 (RakPeerInterface, BitStream callbacks) | + 70 enet C exports |

Plus **215 Noesis `BlackRussia.*` UI types** (registered XAML ViewModels/converters/behaviors)
and **506 `Gui::` namespace types** — the UI alone is ~3× our whole engine's symbol budget.

### 4.3 Vendored third-party (from `vendor/` paths + linkage)

* **Opus 1.4** (58 files) — voice codec.
* **mpg123** (56 files) — mp3 decode (radio streams).
* **NoesisGUI** (+ `noesis-extensions`, 11 files) — XAML UI middleware (`https://noesisengine.com/trial`
  string = **TRIAL license tag in the shipped engine**).
* **EA STL (eastl)** — container/allocator library.
* **BASS** (external libbass.so) — audio output/3D audio.
* **ENet** — UDP reliable transport.
* **RakNet (SA-MP 0.3.7 variant)** — game protocol.
* **curl** (15 imports; full static copy with 90 exports inside libupdate-manager.so).
* **OpenSSL/BoringSSL** (libssl/libcrypto external + static copy inside update-manager:
  AES_*, X509/ADMISSIONS_* symbols).
* **nlohmann::json 3.11** (update-manager PatchIndex serialization).
* **zlib-ng** (libz-ng.so) + libz for the client.
* **imgui** (`rw/imgui_impl_opengl3.cpp`) — debug overlay UI.

## 5. libupdate-manager.so (the reference update engine)

* 13,447 exported symbols (10,313 T) — includes full static curl + OpenSSL + nlohmann json.
* Source tree: `tools/resource-patcher/resource_patcher/src/{update_manager,common}` (Windows CI
  path `C:/gitlab-runner/builds/JQb1zMz_7/…`), files: UpdateManagerSystem{,Callbacks,Api,Updater,
  StateMonitor,SlotDownloader}, PatchFilesSystem{,2,3}, DownloadFilesSystem, BackupSystem,
  RollbackSystem, ClearBackupSystem, HTTPFileDownloader, PatchIndexApiClient, Serializator,
  Filesystem, PercentCounter, UICallback.
* Patch model: `PatchIndex` (nlohmann json: per-file path/crc/filesize/link/rule) →
  DownloadFilesSystem (curl, speed-limited, slot-based) → PatchFilesSystem* (apply) →
  BackupSystem/RollbackSystem/ClearBackupSystem (atomic apply with rollback) →
  `state_monitor.sm` checkpoint + `failure_flag` + `patch_index.json` persistence via the
  Java callbacks (§2).
* CRC: `crc_xxhashct` (BR-proprietary xxhash variant — confirmed previously; see
  ENGINE_ARCHITECTURE.md, not needed by GHRP which uses size+TLS).

## 6. Original vs Grand Horizon `libghengine.so` (gap statement)

Our engine (stripped release, arm64): **511,952 bytes**, 720 dynsym exports of which **13 are
Java_ natives** (`GHNative_native{Init,Start,Stop,Resize,Touch,SetCharacter,LoadScene,
SetDataRoot,SetLogPath,SetCallback,GetEngineInfo,CharacterCount,CharacterName}`) + `JNI_OnLoad`;
NEEDED: `liblog, libandroid, libEGL, libGLESv2, libz, libm, libdl, libc` (static libc++, no ssl).

| Subsystem (original evidence) | Original scale | libghengine.so (M1) | Status |
|---|---|---|---|
| JNI surface | 60 exports + ~70 RegisterNatives callbacks | 13 exports | PARTIAL by design (launcher+preview only) |
| Renderer (librw + renderer/) | 21+15 files, 83 gl funcs, shadows/water/FX/particles | GLESGfx/GHRenderer/GHShaderLib (own GLSL, ~12 gl entry points via loader) | PARTIAL — character preview done; world/shadows/water/FX = MISSING (M2) |
| NoesisGUI VM layer | 506 Gui types + 215 XAML types + 978 files.gui.bpc entries | none (Java UI) | MISSING (M2+; our UI strategy = Android views + native HUD later) |
| Animation | 69 RTTI + AnimBlendAssocGroup | AniAnimation (ANP3 parse only; no playback/blending) | PARTIAL (parse done, playback M2) |
| Audio (BASS+OpenSLES+Opus+mpg123+OpenAL) | 5 files + 2 vendor codecs + BASS .so | none | MISSING (M2+) |
| Networking (RakNet+ENet, 19 network/ files) | full SA-MP client | none in .so yet (Python probe proven; C++ NetworkManager = M2) | MISSING (cracked, implementation pending) |
| Physics | 4+ files (constraints/dynamics) | none | MISSING (M2+) |
| Vehicles/peds/weapons/entities | 10+7+3+2 files | none | MISSING (M2+) |
| Filesystem/tdb (bpc/texture db) | 7+2 files | BpcArchive/BtxTexture/ModMesh/AssetLibrary (own) | PARTIAL (formats reverse-engineered; streaming = M2) |
| Update system | libupdate-manager.so (9.8 MB) | **replaced by pure-Java UpdateController** | COMPLETE (different mechanism, no native dep) |
| HTTP/REST (curl in-engine) | CurlHttpProcessor/HttpApi | Java `Http.java` (launcher) | COMPLETE at launcher level |
| Touch input | touchinterface + multiTouchEvent JNI | `nativeTouch` | COMPLETE (M1 scope) |
| IME bridge | SoftwareKeyboardBridge (5 JNI) | none | MISSING (needed when engine text input lands) |

**Scale takeaway:** the original engine is a ~21-29 MB (per ABI) PGO/LTO C++ game client —
Roughly 55× our current engine size, with the UI middleware alone (Noesis stack) accounting for
the single biggest RTTI family (1,056 Gui types). Our M1 engine intentionally covers only the
launcher-critical path (asset pipeline + character preview + GL renderer + JNI surface); every
missing family above is already tracked in `GRAND_HORIZON_ENGINE_GAP_ANALYSIS.md` and the M2+
plan (networking first — protocol fully cracked in worklog 23-A/B/24).

## 7. Evidence index

* `lib/armeabi-v7a/*` `ls -la`, `file`
* `readelf -d lib/armeabi-v7a/libblackrussia-client.so` (NEEDED list)
* `nm -D --defined-only lib/armeabi-v7a/libblackrussia-client.so` (1,246 syms; 60 Java_;
  `JNI_OnLoad` @ 0x00e74ec1; 70 `enet_*`)
* `nm -D -u lib/armeabi-v7a/libblackrussia-client.so` (638 imports; 83 gl*; BASS/SL/curl/UMS callbacks)
* `strings -a lib/armeabi-v7a/libblackrussia-client.so`: 256 `BRClient/src/…` __FILE__ paths,
  3,704 mangled names (3,038 `N…E` RTTI), 215 `BlackRussia.*` types, clang 19.0.1 idents,
  vendor/{opus(58),mpg123(56),noesis-extensions(11)} paths, `https://noesisengine.com/trial`
* `nm -D --defined-only lib/armeabi-v7a/libupdate-manager.so` (13,447 syms; 90 curl_*; AES/X509)
  + `readelf -d` (liblog/libm/libdl/libc only) + `strings` (resource_patcher paths,
  state_monitor.sm/failure_flag/patch_index messages)
* `nm -D --defined-only lib/armeabi-v7a/libsigner.so` (Adjust NativeLibHelper)
* Our engine: `repo/launcher-gradle/app/build/intermediates/stripped_native_libs/release/…/
  lib/{arm64-v8a,armeabi-v7a}/libghengine.so` (`nm -D`, `readelf -d`)
