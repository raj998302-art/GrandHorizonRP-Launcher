# Grand Horizon RP — Launcher & Game Data Infrastructure

This repository hosts **everything the Grand Horizon RP mobile launcher needs to run**:
the launcher APK (release asset), the client API (JSON configs), the complete game
data CDN (individual files served to the launcher's update system) and the SSO
authentication service (`ghrp-auth/`, deployed at https://ghrp-auth.vercel.app).

> Game: **Grand Horizon RP** (SA-MP based mobile RP) · Server: `142.132.203.47:14448`
> City: **Horizon City** · OCGs: **Ironside / Blackwood / Harbor** · Bank: **Horizon City Bank**

## 📱 Install (players)

1. Download the final launcher APK:
   https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/GrandHorizonRP-Launcher-Final.apk
   (sha256 `84b0feac7aa72c8a542b797c3e7934bc8b02422417faeb7554399ea5d8a7620c`, 117,794,283 B)
2. Install it (allow unknown sources; uninstall any older build first).
3. First open: splash → ~4.03 GB game data downloads into the app's external files
   dir → sign in / sign up through the built-in SSO → native character creation →
   play on `142.132.203.47:14448`.

## 🏗 Architecture

```
Launcher APK (com.grandhorizonrp.launcher, v16.102.14498 / versionCode 1529, targetSdk 36)
 ├── API  → https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@1ddc504b1698c37196cf8e54138e57cd3254caf1/client-api/
 │    ├── servers.json                          → server list (IP/port/name)
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
`tryGetPatchIndex(cdnConfig, fileRules, …)` downloads `patch_index.json`, matches files
by rule and computes the download plan. With `isFlavorWithLogo() = false` the site build
uses rules `["nologo.astc","astc","nologo","loader.video"]` which select **all 157 files
(4,034,899,131 bytes)**. Verified under QEMU with the real `libupdate-manager.so` +
`libblackrussia-client.so`: `countFiles=157`, `error 0x00000000`, `status 1`.

### Language
`initLanguageOnStartup` accepts `{ru, pt, en}` and defaults to **English**; the engine
loads `en/client-jsons.zip` from the game data (`uiLanguage` preference).

## ✅ Verification status (20 Sep 2026)

| Area | Status |
|---|---|
| SSO production (API + UI + Android.initToken bridge + DB) | **VERIFIED** end-to-end |
| Native CDN phase (patch_index download/parse/rules/size) | **VERIFIED** under QEMU with real native libs |
| APK build parity (CI = local, sha256 pinned) | **VERIFIED** |
| 26 native libs byte-identical to original | **VERIFIED** |
| No Black Russia endpoints / no secrets in APK | **VERIFIED** |
| Full 4.03 GB device download → extraction → engine start → login → character creation → spawn | **NEEDS REAL DEVICE** |

## 📋 Real-device A→Z test (for the owner)

Install `GrandHorizonRP-Launcher-Final.apk` (fresh install) and check:

A. Fresh install launches · B. GRAND HORIZON splash (no Black Russia flash) ·
C. No BR branding anywhere · D. English loads (no "no current language") ·
E. Update manager starts · F. Game data downloads (~4.03 GB) ·
G. No 0x0011001C / "Reconnecting to CDN 2.0" · H. All files install ·
I. SSO WebView opens ghrp-auth.vercel.app · J. Sign up (email → code → password) ·
K. START PLAY hands off to the native engine · L. Server selection (Grand Horizon RP) ·
M. Native character creation (male/female/skin/name) · N. Spawn in Horizon City ·
O. Reconnect works · P. Second launch does not re-download completed files.

Report the first failing letter to continue debugging.

## 🔧 Server

`GrandHorizonRP-Server-Final.zip` (release `latest`) — built from the current
`server.zip` baseline: compiled `laird.amx` preserved byte-for-byte, MySQL credentials
for `142.132.203.47:3306` verified, `language English`, hostname
`Grand Horizon RP`, port `14448`. The server is LIVE on LemmeHost.

## 📁 Repo layout

- `client-api/` — launcher API configs (served via jsDelivr, pinned commit)
- `ghrp-auth/` — SSO web app (Next.js, deploy to Vercel)
- `keystore/` — release signing key (ghrp alias)
- `tools/` — APK verification + CI scripts
- `launcher-patches/` — smali patch definitions
- `.github/workflows/` — deterministic APK build + verification pipeline
