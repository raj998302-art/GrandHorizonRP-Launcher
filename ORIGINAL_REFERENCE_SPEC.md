# ORIGINAL REFERENCE SPEC — Black Russia Launcher (com.br.top / com.blackhub.bronline)
## Grand Horizon RP Scratch Launcher — Technical Reference

> **STATUS OF THIS DOCUMENT**: The original authorized Black Russia launcher APK
> (decoded at `launcher_src/`, sha256 of `launcher_src.zip` =
> `166b235ce65f4d82dfc625d7818c4dfe5229cddcf5d7756460aa19bd1d4d84c0`) is used
> **exclusively as behavioral/contract reference**. The Grand Horizon RP launcher
> (`com.grandhorizonrp.launcher`) is a **100% new-source Android project**.
> Nothing from the original APK is patched, recompiled or repackaged into the
> product. The only binary dependencies reused are the 13 proprietary native
> engine libraries (`lib/arm64-v8a/*.so`), integrated as a dependency per the
> JNI contract documented here (see §13).

---

## 1. APPLICATION IDENTITY (original reference)

| Property | Original | GHRP product |
|---|---|---|
| Package | `com.br.top` / appId `com.blackhub.bronline` | `com.grandhorizonrp.launcher` |
| versionCode | 1529 | 1529 (protocol parity) |
| versionName | 16.102.14498 | 16.102.14498 (protocol parity) |
| Entry activity | `com.blackhub.bronline.game.core.JNIActivity` | `com.blackhub.bronline.game.core.JNIActivity` (our class, engine-required path — see §6) |
| Theme (first frame) | `AppTheme.WithSplash` → `windowBackground=@drawable/bg_welcome_img` (BR-branded police-car scene) | `AppTheme.WithSplash` → GHRP `bg_welcome_img` (own artwork) |
| Theme (runtime) | `AppTheme.NoActionBar` → `total_black`, fullscreen, immersive | same contract, own colors |
| Orientation | sensorLandscape, singleTask | same |
| GL requirement | GLES 3.0 (`JNIGLSurfaceView` + `JNIConfigChooser` with EGL_KHR_surfaceless_context / EGL_RECORDABLE_ANDROID) | same |
| minSdk / targetSdk | 26 / 36 | same |
| Distribution | site flavor (`DistributionType.Site = 0`) | same |

Original manifest carried ~40 SDK components (Firebase Messaging, AppMetrica,
Adjust, Helpshift, RuStore, Play Billing, WorkManager, Room). **GHRP ships none
of these** — own-code replacements where a function is actually needed.

---

## 2. HIGH-LEVEL FLOW / SCREEN STATE GRAPH (original)

```
[Process start]
 └─ JNIActivity (single activity, fullscreen, GL)
     ├─ windowBackground = splash image (first frame, no code)
     ├─ InitializationFragment   (language init, storage mount, config sync)
     │    ├─ language = SettingsHelper "language" pref (ru default; GHRP: "en")
     │    ├─ STORAGE_ROOT = externalFilesDir + "/"   (seeded into engine)
     │    ├─ fetch url-config.json + app-config.json (CDN pin)
     │    │      → JNILib.onUrlConfigReceived(bytes) / onAppConfigReceived(bytes)
     │    └─ fetch update_manager_feature_flag.json
     ├─ LoaderFragment           (engine boot: loads launcher.bpc world)
     ├─ UpdateManagerFragment    (native Update-System-2.0 progress UI)
     │    ├─ progress + status strings + speed
     │    └─ errors → dialog → retry (4 attempts) → re-ask
     ├─ [engine XAML launcher]   (rendered by engine from launcher.bpc:
     │        video background, logo, PLAY button, server name, online counter)
     ├─ WebViewAuthFragment      (screenId 0x58 — engine-requested WebView)
     └─ [engine game]            (character creation → spawn → HUD)
```

Key architectural fact: **the visible "launcher UI" (post-update) is rendered by
the native engine** interpreting `launcher.bpc` (XAML/bytecode package). The
Android-side fragments only cover: initialization, update progress, and the
auth WebView. Everything else (character selection, appearance, skin, name,
spawn, HUD) is engine-rendered through `JNIGLSurfaceView`.

---

## 3. AUTHENTICATION STATE GRAPH (original)

```
[engine start screen] ── PLAY / LOGIN
   └─ engine opens screen 0x58 (WebViewAuthFragment)
        payload JSON: { "auth_url": "<registrationSite + query>" }
   └─ Java appends sysinfo params:
        sysinfo=android, ttclid, client_id, appmetrica_device_id, adjust_id
        (GHRP: sysinfo=android + client_id only — no trackers exist)
   └─ WebView loads auth_url (external SSO website)
        Login ─→ Sign Up ─→ Email ─→ 6-digit code ─→ Password ─→ Success
        Recovery: email ─→ code ─→ new password
        Guest: one-tap guest account
   └─ page calls window.Android.initToken('{"front_token":"..."}')
   └─ saveTokenAndCloseWebView():
         JNILib.sendJsonData(0x58, tokenJsonBytes)   ← SAME id both directions
         closeGUI (fragment dismissed)
   └─ engine Auth2 protocol (native, reads url-config itself):
         POST <characterService>/auth/token  grant_type=front_token
              → access_token + refresh_token
         SessionInit(front_token) → SessionConfirm(access_token)
         subsequent requests: AuthorizationV2_ header
   └─ [engine] server select → character flow
```

Error paths: `closeWebview(errorString)` (page → Java → engine error state),
`startIdpAuth(url)` (external IdP), `openHelpshift(json)` (support chat —
**GHRP: no-op, support is Discord**).

---

## 4. WEBVIEW CONTRACT (original)

- `WebViewAuthFragment` created by `GUIManager` when engine requests screen
  `0x58`; auth URL arrives in the open-payload JSON (`auth_url`).
- WebView settings: JS enabled, DOM storage, `WebAppInterface` injected under
  the JS name **`Android`**.
- Bridge methods (all `@JavascriptInterface`):

| Method | Signature (JS → Java) | Behavior |
|---|---|---|
| `initToken` | `Android.initToken(jsonString)` | jsonString = `{"front_token":"..."}` → forwarded to engine as message `0x58` |
| `closeWebview` | `Android.closeWebview(errorString)` | cancel/error path → engine gets error |
| `openHelpshift` | `Android.openHelpshift(jsonString)` | support chat (Helpshift SDK) |
| `startIdpAuth` | `Android.startIdpAuth(url)` | external IdP flow |

- Success handoff is **synchronous within the fragment lifecycle**: token →
  `sendJsonData(0x58, bytes)` → fragment closed → engine continues Auth2.

---

## 5. JNI CONTRACT — Java→Native declarations (original)

All native methods declared by the original Java layer (the engine exports
`Java_...` symbols for each; verified 121/121 signatures in v6):

### 5.1 `com.blackhub.bronline.game.core.JNILib` (50 natives)
Critical ones:
```
init(String pathToRes, String pathToSaves, int clientVersion(1529),
     int distributionType(0=Site), int buildType(5=Release))
initRender(int realW, int realH, int densityDpi, float density, int clientVersion)
onUrlConfigReceived(byte[])      onAppConfigReceived(byte[])
sendJsonData(int screenId, byte[] json)          // 0x58 auth handoff
onMultiTouchEvent(int[] actions, int[] ids, float[] xs, float[] ys, int[] meta,
                  int pointerCount, int pointerIndex)   // 8-int contract, 3 pointers
tryGetPatchIndex(String jsonHttpData, String fileRules, boolean isEnabledCheckResources,
                 int version, int candidateVersion, int downloadTimeout,
                 int connectionTimeout, int distributionType, boolean useBackupCdn,
                 boolean isDevModUpdateManager, boolean forceCheckResources) → String
tryDownloadResources(boolean isEnabledRecovery, int downloadSpeedLimit,
                 boolean isEnabledCheckResources, int downloadTimeout,
                 int connectionTimeout, boolean isEnabledSendingOfCDNMetric,
                 boolean forceCheckResources) → boolean
getAdditionDownloadPatchData() → String   // progress polling
loadPatchIndexBytes() / storePatchIndexBytes(byte[])  // Java-owned cache hook
getSerializedSettings() → String         // settings JSON → engine
onResume()/onPause()/onStop()/onDestroy()/onLowMemory()/onTrimMemory(int)
onKeyEvent(int keyCode, int action, int metaState)
onTabEvent(4×int[], 1×int)               // stylus/pen events
setSoftwareKeyboardVisible(boolean) ...
```

### 5.2 `JNIConfig` (11), `GameRender` (4), `SoftwareKeyboardBridge` (5), `HelpshiftManager` (1)
GUI/config surface used by the engine for GL setup, keyboard and support.

### 5.3 Load order (original)
```
System.loadLibrary("update-manager")     (static init of JNIActivity)
System.loadLibrary("blackrussia-client") (DT_NEEDED pulls: libbass, libssl,
                                          libcrypto, libcrashlytics*, libz-ng,
                                          libdatastore_shared_counter,
                                          libandroidx.graphics.path)
```

---

## 6. JNI CONTRACT — Native→Java (engine FindClass targets)

**Hard requirement**: the engine's native code resolves these exact classes:

| Class path (fixed by engine) | Members used |
|---|---|
| `com/blackhub/bronline/game/core/JNIJSONTransport` | **59 public static callbacks** — the entire engine→Java OS surface: dialogs (synchronous RPC with `CountDownLatch` await), state persistence (`state_monitor.sm`, `failure_flag`, `patch_index.json` cache read/write), media decoder, keyboard show/hide, vibration, clipboard, system info, GPU info, network type, locale, storage paths |
| `com/blackhub/bronline/game/core/AppLocalValues` (+ `Companion.getInstance()`) | `hasAppLocalValue/getAppLocalValue/setAppLocalValue` — engine-side key-value store |
| `com/blackhub/bronline/game/GUIManager` | fragment orchestration + `sendJsonData` routing |

> These class paths are the **engine's runtime ABI**, not branding. GHRP's
> scratch app provides its own implementations at these paths (package
> `com.blackhub.bronline.*` = adapter layer; the application's own package is
> `com.grandhorizonrp.launcher`). This is documented, deliberate, and the only
> way a foreign engine can be hosted.

`JNIActivity` seeds `AppLocalValues.STORAGE_ROOT = externalFilesDir + "/"`
before `JNILib.init(...)` — the engine derives all data paths from it
(`<STORAGE_ROOT>mesh/`, `textures/`, `audio/`, `resources/`, `jsons/`,
`launcher.bpc`, `common.bpc`, `gui.bpc`, saves under `filesDir`).

---

## 7. UPDATE / DOWNLOAD FLOW (original "Update System 2.0")

```
InitializationFragment
  ├─ GET url-config.json   (region entry: cdnUrl, cdnBackupUrl, apiUser/Pass)
  ├─ GET app-config.json
  ├─ GET update_manager_feature_flag.json
  │     └─ java defaults if missing: connectionTimeout=15000ms, downloadTimeout=1200000ms
  └─ UpdateManagerFragment
       ├─ httpData = {"cdn": cdnUrl, "backup_cdn": cdnBackupUrl,
       │              "username": apiUsername, "password": apiPassword}
       │     (original SITE flavor HARDCODED BR CDN basic-auth
       │      main/DzEI3O4VDpdc6KpcSfd3 → 401 on foreign CDNs — v5's fatal bug)
       ├─ fileRules = JSON array, site flavor: ["android.site","astc","with.logo",
       │              "with.logo.astc","loader.video"]
       ├─ result = tryGetPatchIndex(httpData, fileRules, ...)
       │     → native GETs <cdn>/patch_index.json (curl, TLS, FOLLOWLOCATION=1)
       │     → rule matching, size computation
       │     → result JSON keys: patch_index_status_key(1=ok),
       │        patch_index_size_key, patch_index_addition_size_after_apply,
       │        patch_index_error_key ("0x0011001C" = curl 28 timeout etc.)
       ├─ loop: tryDownloadResources(...) + getAdditionDownloadPatchData() polling
       │     → per-file download <cdn>/<link> → crc_xxhashct verify → extract to path
       └─ complete → LoaderFragment → engine start
```

Java owns the patch-index byte cache (`loadPatchIndexBytes/storePatchIndexBytes`
on `filesDir/patch_index.json`) — **GHRP pre-seeds it via Java TLS** (own
downloader) so the native phase starts from a verified index.

### 7.1 patch_index.json format (verified against native parser)
```json
{
  "android_version": 1529,
  "filesize": 4034899131,
  "link": "ghrp-v1",
  "hash_commit": "ghrp-v2-20260920",
  "forced_download_site": false,     (+ market/pc/ios/rustore)
  "ios_version": 0,
  "patches": [],
  "files": [
    { "crc_xxhashct": 4092278032,    // xxhash-based content check
      "filesize": 3106513,
      "link": "files.launcher.bpc",  // FLAT asset name on CDN (URL = cdn + "/" + link)
      "path": "launcher.bpc",        // LOCAL path under STORAGE_ROOT
      "rule_file": "loader.video" }  // one rule per file
  ]
}
```

### 7.2 File-rule semantics (site flavor)
| Rule | Meaning | Count (GHRP index) |
|---|---|---|
| `base` | required by all flavors | 61 |
| `astc` | ASTC-compressed textures (GLES3 devices) | 90 |
| `nologo` | mesh/textures WITHOUT BR-logo content | 1 |
| `nologo.astc` | ASTC variant of nologo set | 4 |
| `loader.video` | launcher.bpc (UI script package) | 1 |
| `with.logo` / `with.logo.astc` / `android.site` | BR-logo + site-launcher files | 0 — intentionally EXCLUDED (rebrand) |

GHRP `fileRules` (exact array passed to `tryGetPatchIndex`):
`["astc","nologo","nologo.astc","loader.video"]` → selects all 157 files.

### 7.3 Native error codes (engine → UI)
- `0x0011001C` — curl error 28 (operation timed out) — device symptom of CT=30ms or 401-retry loop
- `0x00110006` — resolver failure (harness artifact / DNS)
- `0x0011003C` — internal error (curl 60-class TLS in harness)
- `0x00000000` + status 1 — success

---

## 8. SERVER-SELECTION FLOW (original)

- Engine reads `servers.json` (via app-config/client-api): array of
  `{id, online, maxonline, color, x2, key, name, firstname, secondname, url, ip, port}`.
- Launcher screen (engine XAML) shows server list; selection stores server key;
  game connection = TCP `ip:port` (SA-MP protocol) after Auth2 session confirm.
- **GHRP**: single entry — `Grand Horizon RP | HORIZON CITY`, `142.132.203.47:14448`.

---

## 9. CHARACTER CREATION FLOW (engine-rendered — reference)

```
[male/female select] → [appearance: face/hair/eyes sliders]
→ [skin/model select] → [character name input]
→ [validation: unique name, charset First_Last] → [confirm]
→ [save: POST characterService] → [spawn location select]
→ [spawn → native world → HUD]
```
All steps are native engine screens (GUI JSON transport ↔ `JNIJSONTransport`
dialog RPC for name input). The Java layer only provides: keyboard bridge,
dialog host, GL surface, input forwarding. **No Android UI must be built for
these steps** — the engine renders them; our Java classes satisfy the callbacks.

---

## 10. UI DESIGN LANGUAGE (reference measurements)

- **Palette**: near-black background `#0d0e12` / `total_black`, glass-dark
  surfaces with subtle elevation, accent = warm orange→red gradient
  (`#FFC60000 → #FFFF6318`), gold `#FFC700` (server color).
- **Typography**: Montserrat family (SSO web) / Roboto-Regular + Muller Bold
  (engine XAML fonts, bundled at `assets/Fonts/`).
- **Splash**: full-bleed artwork as window background (zero-delay first frame),
  no text; launcher logo fades in over video background (engine XAML).
- **Buttons**: large rounded (12–16dp radius), gradient fill for primary CTA,
  uppercase, letter-spaced labels; disabled = 40% alpha.
- **Progress UI**: centered card, percentage + received/total MB + MB/s,
  thin rounded progress bar with gradient, status line above, retry dialog on
  error with EXACT error code text.
- **Landscape-first**: all screens designed sensorLandscape; WebView auth is
  the only portrait-tolerant surface (SFO is responsive both orientations).

---

## 11. REQUIRED ASSETS (GHRP product)

| Asset | Source | Notes |
|---|---|---|
| 13 × `lib/arm64-v8a/*.so` | engine binary dependency (original lib dir) | see §13 |
| `assets/Fonts/Roboto-Regular.ttf`, `muller_bold.ttf` | engine-required fonts | open/renamed fonts shipped with engine data |
| `bg_welcome_img` splash | GHRP own artwork | replaces BR police-car scene |
| App icons (all densities + adaptive) | GHRP own artwork | replaces BR bear |
| `launcher.bpc` (game-data, rule `loader.video`) | GHRP-rebranded XAML package (already in gamedata release) | engine launcher UI |
| 156 other game-data files | preserved engine data (byte-exact) | see §12 |

## 12. GAME DATA SEPARATION (gamedata release audit, 2026-09-20)

- Release `gamedata`: **159 individual assets (4.123 GB)** + same data as
  **3-part zips** (3 × 1.375 GB) + `patch_index.json` + feature flag + README.
- Launcher-coupled files (only 3, all root): `launcher.bpc` (engine launcher
  UI — GHRP-rebranded), `common.bpc`, `gui.bpc` (engine core scripts —
  generic, no BR branding).
- Language resources: `jsons/` (client-jsons.zip + `en/` + `pt-br/` variants)
  — engine dictionary files; `en` is a complete resource set.
- Everything else (`mesh/` 46 files 1.74GB, `textures/` 94 files 1.69GB,
  `resources/` 7 files 0.46GB, `audio/` 4 files 0.13GB) = pure engine/game
  content — **preserved byte-exact, no modification**.
- `nologo` rule set already excludes BR-logo car textures (4+1 files).
- patch_index regenerated for GHRP: 157 files, flat links, `hash_commit=ghrp-v2-20260920`.

---

## 13. DEPENDENCY REPORT (legal/technical)

| Dependency | Status |
|---|---|
| Native game engine (13 `.so`, arm64-v8a) | **Integrated as binary dependency per observed JNI contract** (user-owned distribution, `gamedata`/original authorized package). Not modified, not re-signed, loaded at runtime by our own Java code. |
| Android SDK / build-tools | Standard tooling (Gradle + AGP). |
| SSO website | Own implementation deployed at `https://ghrp-auth.vercel.app` (production-verified). |
| Game server | SA-MP protocol server `142.132.203.47:14448` (user-operated, LemmeHost). |
| MySQL | LemmeHost `s287152_db1789732442151` (accounts; SHA256(password+salt) parity with server AMX). |

**Known proprietary components and their justification** (mandate §11): the
`.so` engine binaries are the ONLY original-derived runtime components. Their
class-path requirements (`com.blackhub.bronline.*` FindClass targets) are
satisfied by our own adapter classes. No original Java/smali/resources are
compiled into the product.

---

## 14. REQUIRED APIs / ENDPOINTS (GHRP runtime config)

| Purpose | URL |
|---|---|
| Config CDN (pin) | `https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@<pin>/client-api/` |
| Game-data CDN | `https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/gamedata/` |
| SSO (registration/character) | `https://ghrp-auth.vercel.app` (+ `/api`) |
| Auth2 token endpoint | `https://ghrp-auth.vercel.app/api/v2/auth/token` (grants: password / guest / front_token) |
| Game server | `142.132.203.47:14448` |

Config files consumed: `url-config.json`, `app-config.json`,
`servers.json`, `update_manager_feature_flag.json` (CT=15000ms, DT=1200000ms),
`patch_index.json`, `hash.json`.

---

## 15. GHRP IMPLEMENTATION DEVIATIONS (product vs reference)

1. **Own update orchestration** (Java TLS, pre-seeded patch-index, no BR
   basic-auth anywhere) — eliminates the 0x0011001C failure class.
2. **English-only** player-facing text; engine language pref seeded `"en"`.
3. **No analytics/crash/billing SDKs** — own file logger
   (`files/logs/launcher.txt`, `crash.txt`).
4. **WebView SSO** to own Vercel deployment with same bridge contract.
5. **GHRP branding** on splash/icons; engine launcher XAML rebranded (launcher.bpc).
6. Real **Gradle project** (AGP) — reproducible source builds in CI.

*Extracted and verified from the authorized original decode
(`launcher_src/`), engine `.so` symbol tables, and QEMU harness execution logs
(see worklog Tasks 15–20). This spec is the single source of truth for the
scratch implementation.*
