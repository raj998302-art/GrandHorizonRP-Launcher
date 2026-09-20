# Grand Horizon RP — Launcher & Game Data Infrastructure

This repository hosts **everything the Grand Horizon RP mobile launcher needs to run**:
the **from-scratch launcher source** (`launcher-gradle/`), the client API (JSON configs),
the complete game data CDN (individual files served to the launcher's update system)
and the SSO authentication service (`ghrp-auth/`, deployed at https://ghrp-auth.vercel.app).

> Game: **Grand Horizon RP** (SA-MP based mobile RP) · Server: `142.132.203.47:14448`
> City: **Horizon City** · OCGs: **Ironside / Blackwood / Harbor** · Bank: **Horizon City Bank**

## 🚨 Architecture (hard rule)

The launcher is a **100% new-source Android project** (`launcher-gradle/`, package
`com.grandhorizonrp.launcher`) built with a real **Gradle + AGP** pipeline.
The original Black Russia launcher is **REFERENCE ONLY** — every contract it
imposes (JNI bridge, update system, WebView auth, GL hosting) is reimplemented
in our own Java code. The full reference contract is documented in
**[`ORIGINAL_REFERENCE_SPEC.md`](ORIGINAL_REFERENCE_SPEC.md)**.
The 13 proprietary native engine libraries are integrated as a **binary
dependency** per that spec (§13) — nothing from the original APK is patched,
recompiled or repackaged.

## 📱 Install (players)

1. Download the from-scratch launcher APK:
   https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/GrandHorizonRP-Launcher-v7-Gradle.apk
   (built by CI from `launcher-gradle/` — Gradle assembleRelease, v2+v3 signed)
2. Install it (allow unknown sources; uninstall any older build first).
3. First open: splash → ~4.03 GB game data downloads into the app's external files
   dir → sign in / sign up through the built-in SSO → native character creation →
   play on `142.132.203.47:14448`.

## 🏗 Architecture

```
launcher-gradle/  (com.grandhorizonrp.launcher, v16.102.14498 / versionCode 1529, targetSdk 36)
 ├── app/src/main/java/com/grandhorizonrp/launcher/   → OUR code (update, auth, http, logging)
 ├── app/src/main/java/com/blackhub/bronline/         → engine JNI bridge adapters (own code,
 │                                                      engine-required FindClass paths)
 ├── app/src/main/jniLibs/arm64-v8a + armeabi-v7a     → 13 native engine libs (dependency)
 ├── API  → https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@1ddc504b1698c37196cf8e54138e57cd3254caf1/client-api/
 │    ├── servers.json                          → server list (142.132.203.47:14448)
 │    ├── url-config.json                       → cdnUrl + registrationService/characterService → ghrp-auth.vercel.app
 │    ├── app-config.json                       → versions 1529, no forced updates
 │    ├── update_manager_feature_flag.json      → patch_index_json flow, connection_timeout 15000 ms,
 │    │                                            download_timeout 1,200,000 ms  (0x0011001C fix)
 │    └── *.json                                → all in-game GUI configs (English, rebranded)
 │
 ├── CDN  → https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/gamedata/
 │    ├── patch_index.json                      → 157 files, flat links (URL = cdn + "/" + link)
 │    ├── files.launcher.bpc                    → native launcher GUI (GRAND HORIZON splash)
 │    └── 156 game data assets                  → meshes / textures / audio / jsons
 │
 └── SSO  → https://ghrp-auth.vercel.app  (source: ghrp-auth/ in this repo)
      ├── page            → original-style WebView auth (email → 6-digit code → password → START PLAY)
      ├── /api/v2/...     → auth/token, registration/*, recovery/*, guest
      └── MySQL           → game accounts table, sha256(password+salt) — laird.amx compatible
```

### Update system (patch_index / "CDN 2.0")
Our `UpdateController` orchestrates the native `tryGetPatchIndex(cdnConfig, fileRules, …)`:
it first **pre-seeds the patch index via Java TLS** (own downloader, no BR basic-auth —
the credentials in url-config are intentionally empty), then lets the engine's update
manager parse and download. fileRules = `["nologo.astc","astc","nologo","loader.video"]`
which select **all 157 files (4,034,899,131 bytes)**. Verified under QEMU with the real
`libupdate-manager.so` + `libblackrussia-client.so`: `countFiles=157`, `error 0x00000000`,
`status 1`.

### Language
English only (`App` seeds language pref `"en"`); the engine loads `en/client-jsons.zip`
from the game data (`uiLanguage` preference).

## ✅ Verification status (20 Sep 2026)

| Area | Status |
|---|---|
| SSO production (API + UI + Android.initToken bridge + DB) | **VERIFIED** end-to-end |
| Native CDN phase (patch_index download/parse/rules/size) | **VERIFIED** under QEMU with real native libs |
| Engine JNI contract (60/60 symbols, 82 transport callbacks) | **VERIFIED** — `tools/verify_contract.py` |
| Gradle build (javac → dex → package → v2+v3 sign, zipalign) | **VERIFIED** locally + CI |
| No Black Russia endpoints / no secrets / no trackers in APK | **VERIFIED** (dex string audit) |
| Full 4.03 GB device download → extraction → engine start → login → character creation → spawn | **NEEDS REAL DEVICE** |

## 📋 Real-device A→Z test (for the owner)

Install `GrandHorizonRP-Launcher-v7-Gradle.apk` (fresh install) and check:

A. Fresh install launches · B. GRAND HORIZON splash (no Black Russia flash) ·
C. No BR branding anywhere · D. English loads (no "no current language") ·
E. Update manager starts · F. Game data downloads (~4.03 GB) ·
G. No 0x0011001C / "Reconnecting to CDN 2.0" · H. All files install ·
I. SSO WebView opens ghrp-auth.vercel.app · J. Sign up (email → code → password) ·
K. START PLAY hands off to the native engine · L. Server selection (Grand Horizon RP) ·
M. Native character creation (male/female/skin/name) · N. Spawn in Horizon City ·
O. Reconnect works · P. Second launch does not re-download completed files.

Logs on failure: `Android/data/com.grandhorizonrp.launcher/files/logs/launcher.txt`
(+ `crash.txt`). Report the first failing letter to continue debugging.

## 🔧 Server

`GrandHorizonRP-Server-Final.zip` (release `latest`) — built from the current
`server.zip` baseline: compiled `laird.amx` preserved byte-for-byte, MySQL credentials
for `142.132.203.47:3306` verified, `language English`, hostname
`Grand Horizon RP`, port `14448`. The server is LIVE on LemmeHost.

## 📁 Repo layout

- `launcher-gradle/` — **THE launcher source** (Gradle + AGP project, own Java code)
- `ORIGINAL_REFERENCE_SPEC.md` — full reference contract extracted from the original
- `client-api/` — launcher API configs (served via jsDelivr, pinned commit)
- `ghrp-auth/` — SSO web app (Next.js, deploy to Vercel)
- `keystore/` — release signing key (ghrp alias)
- `tools/` — APK verification + contract verification scripts
- `.github/workflows/build-gradle.yml` — Gradle build + verify + release pipeline
