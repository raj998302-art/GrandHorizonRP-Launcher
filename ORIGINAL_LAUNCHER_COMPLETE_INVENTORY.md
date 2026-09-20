# ORIGINAL LAUNCHER — COMPLETE INVENTORY (Reference Audit)

> Source: decoded/decompiled Black Russia launcher package (`orig-decode/launcher_src`, 928 MB).
> **STATUS: REFERENCE ONLY.** Nothing from this package is a build input, runtime
> dependency or fallback of Grand Horizon RP. This document is the engineering
> inventory of what the original implemented, used to derive behaviour contracts.

## 1. Package layout

| Path | Content |
|---|---|
| `AndroidManifest.xml` | single-activity architecture (`JNIActivity`), landscape, `extractNativeLibs` |
| `smali/` + `smali_classes2..7/` | decompiled Java/Kotlin (BR + Kotlin stdlib + androidx) |
| `lib/` (per-ABI) | original native engine `.so` (RakNet-era symbols, ENet transport, renderer) — **never packaged into GHRP** |
| `assets/` | embedded launcher assets (video fallbacks, configs) |
| `res/` | icons, drawables, layout fragments |
| `original/` | untouched originals kept by the decoder |

## 2. Java/Kotlin layers (behaviour contract)

### 2.1 Application / lifecycle
- `com.blackhub.bronline.BR` / `launcher.App` — process start, coroutine exception handlers, analytics wiring (AppMetrica/Adjust ids passed to WebView URL).
- `game.core.JNIActivity` — the single Activity hosting: `SurfaceView` (native render surface) + `jniactivity_fragment_container` + `jniactivity_notification_container`.
- Fragments are swapped by the **native engine** through `GUIManager.onFragmentChange(screenId, Bundle)`.

### 2.2 WebView auth layer (GHRP-equivalent: AuthController + ghrp-auth)
- `launcher.fragments.WebViewAuthFragment` — screenId **0x58 (88)**: engine sends `{auth_url}` JSON via `GUIManager.sendJsonData(0x58, json)`; fragment appends `sysinfo=android&ttclid&client_id&appmetrica_device_id&adjust_id`, opens the SSO site in a WebView.
- `WebAppInterface` (`@JavascriptInterface`, injected as **`Android`**): `initToken(jsonString)`, `closeWebview(errorString)`, `openHelpshift`, `startIdpAuth(url)`.
- On success the page calls `Android.initToken({front_token})` → `AuthViewModel.saveTokenAndCloseWebView` → engine message 0x58 → `closeGUI` → native flow continues. **The WebView is auth-only. It is closed immediately after handoff.**
- Launcher fragments (auth phase): `LoaderFragment`, `InitializationFragment`, `UpdateManagerFragment`, `MainFragment`, `WebViewAuthFragment`.

### 2.3 GUI / game fragments (native-driven)
`GUIManager.onFragmentChange` packed-switch maps **88 screen ids** to fragments —
server list, character selection UI chrome, chat, inventory, tuning, donate, taxi,
fractions, family system, spawn location, HUD notifications, players list, etc.
(see ORIGINAL_SCREEN_STATE_MACHINE.md). The heavy lifting (character render,
world, camera, HUD) is in the **native engine**; fragments host Android views
(compose/recycler) fed by engine data.

### 2.4 Launcher services
- Update manager (native side `loader`/patch logic + `UpdateManagerFragment` UI), remote configs, feature flags.
- Analytics, crash reporting, Helpshift support hook.

## 3. Native engine layer (what GHRP reimplements from scratch)

From prior reverse-engineering sessions (documented in ENGINE_ARCHITECTURE.md):
- Asset pipeline: `.bpc` ZIP containers, `.mod` XTEA-8-encrypted meshes
  (f32 SoA static / f16+edge-buffer character paths), `.btx` KTX1+ASTC 6x6
  textures, `.ani` ANP3 skeletal anims, `.cls` collision bounds.
- Renderer: EGL/GLES surface, character/world rendering, camera, lighting.
- Networking: ENet transport symbols in the client `.so`; server speaks
  SA-MP 0.3.7-R2 RakNet (see SERVER_CLIENT_PROTOCOL research, worklog 23-A/B).
- GUI: `files.gui.bpc` = 978 JSON UI definitions driving native screens.

## 4. What Grand Horizon RP implements independently (status)

| Original subsystem | GHRP implementation | Status |
|---|---|---|
| Splash / branding | Java splash, own art | DONE |
| Game-data update | Pure-Java UpdateController (patch_index + release CDN) | DONE |
| Auth WebView (0x58 contract) | AuthController + ghrp-auth (Vercel) + `Android.initToken` bridge | DONE (upgraded this session: full-bleed UI, session persistence) |
| Server select | Java panel + engine preview (original: native GUI fragment) | DONE at launcher level; native-GUI parity is M2 |
| Character selection / preview | Native 3D engine (GHEngineView + side panel) | DONE (renderer robustness + diagnostics this session) |
| Character persistence | Server-authoritative accounts.sex/skin + SessionStore | DONE this session (was the guest-persistence bug) |
| World rendering / streaming | — | M2 (planned) |
| Multiplayer rendering | — | M2 (protocol cracked, implementation pending) |
| HUD / audio / vehicles | — | M2+ |

## 5. Strict prohibitions honoured

- No original `.so` is packaged (APK forensic: only `libghengine.so` ×2 ABIs).
- No `com.blackhub.*` classes (dex contains only `com.grandhorizonrp.*`).
- No original launcher runtime code copied; only behaviour/format contracts derived.
