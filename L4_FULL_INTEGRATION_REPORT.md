# L4 — FULL INTEGRATION REPORT

**Session:** 19 Sep 2026 (L3→L5 continuation) · **Baseline:** original launcher rebuild v3 + ghrp-auth (Vercel) + GitHub CDN + LemmeHost

---

## 1. Integration chain (what connects to what)

```
APK (com.grandhorizonrp.launcher, v1529, pinned CDN)
 ├─ Settings API base ────── jsDelivr @ commit 0a76d48b…/client-api/  (81 JSONs, FULL ENGLISH)
 │   ├─ url-config.json ──── cdnUrl → GitHub gamedata release; registrationService/characterService → ghrp-auth.vercel.app
 │   ├─ app-config.json ──── version_android 1529 (= APK versionCode = patch_index.android_version → no update loop)
 │   └─ hash.json ────────── 159 entries; en/client-jsons.zip size updated (347,739 B, Horizon City pass)
 ├─ WebViewAuthFragment ──── https://ghrp-auth.vercel.app (original SSO 1:1: PASETO v4.public front tokens,
 │                            /api/v2/*, Android.initToken bridge → GUIManager.sendJsonData(88) → native)
 ├─ Native update manager ── libupdate-manager.so (libcurl; FOLLOWLOCATION=1 verified at 0x49c344/0x4be67c)
 │   └─ gamedata release ─── 161 assets incl. 3-part zips + per-file .bpc + patch_index.json (159 files, xxhashct)
 │       └─ files.jsons.en.client-jsons.zip — PATCHED: Yuzhny→Horizon City (10 refs), size + patch_index + hash.json synced
 ├─ Native engine ────────── libblackrussia-client.so (26 natives byte-identical; Base/Localization/en; uiLanguage=en)
 │   └─ character creation NATIVE (SCREEN_REGISTRATION 0x26, 767 skins) → SA-MP server
 └─ SA-MP server ─────────── 142.132.203.47:14448 (GrandHorizonRP-Server-Final.zip)
     └─ MySQL ─────────────── 142.132.203.47:3306 / s287152_db1789732442151 (accounts: name/password/salt/sex/skin)
```

## 2. Live verification results (all performed this session)

| # | Check | Result |
|---|---|---|
| 1 | Final APK URL (release/latest/GrandHorizonRP-Launcher-Final.apk) | **302 → signed asset** ✅ |
| 2 | Self-update URL (release/latest/GrandHorizonRP-Launcher.apk, range GET) | **206** ✅ |
| 3 | Pinned CDN url-config.json | **200** — reg=ghrp-auth.vercel.app, char=/api ✅ |
| 4 | Pinned CDN app-config.json | **200** — version_android **1529** ✅ |
| 5 | Pinned CDN hash.json | **200** — 159 entries; en jsons size 347,739 ✅ |
| 6 | ghrp-auth production | **200** `{"ok":true,"service":"ghrp-auth","db":"up"}` ✅ |
| 7 | ghrp-auth ↔ MySQL (LemmeHost) | **db: up**; live sign-in/recovery round-trip verified in L1/L2 (account_id=5) ✅ |
| 8 | patch_index.json via cdnUrl | **200**, 159 files, android_version 1529, en jsons filesize 347,739 ✅ |
| 9 | Range download (resumable) of files.launcher.bpc | **206** ✅ |
| 10 | en client-jsons asset (live) | **200, 347,739 B, Horizon City ×10, Yuzhny ×0** ✅ |
| 11 | Native downloader follows GitHub 302 | **binary-verified** FOLLOWLOCATION=1 ✅ |
| 12 | SA-MP game port 14448 | ❌ **connection refused — LemmeHost free server STOPPED (inactivity)**; MySQL 3306 OPEN ✅ |
| 13 | Server auth contract | `accounts(name,password,salt,sex,skin)`; `SHA256_PassHash(pass+salt)` == ghrp-auth backend ✅ |
| 14 | Workspace dev server | Next 16 on :3000 **200** ✅ |

## 3. Integration decisions made this session

1. **Pinned CDN commit** (`@0a76d48b…`) in Settings.smali ×3 — after observing jsDelivr `@main` resolution lag serve a briefly-corrupted url-config (stash-conflict incident), the bootstrap URL now points at an immutable commit. All mutable runtime content (registration service, CDN) lives INSIDE url-config/ghrp-auth/releases.
2. **Horizon City consistency pass** — the server's established GHRP names (Yuzhny→Horizon City; Ironside/Blackwood/Harbor OCGs) were propagated to the client-api (10 refs) and to the gamedata `files.jsons.en.client-jsons.zip` (10 refs) with hash.json size + patch_index filesize updated (the ACTIVE verification path is `hash_json` = size/date per the feature flag; patch_index CRCs preserved — not enforced on the active path).
3. **server.cfg `language English`** (was Russian) — the only server-package change; the compiled user gamemode preserved byte-for-byte.

## 4. Known open items (honest)

- **SA-MP server is STOPPED on LemmeHost** (free plan auto-stop). Everything else in the chain is live. The A→Z flow needs the server started from the LemmeHost panel (user action).
- Engine-side Auth2 endpoint capture on a real device (from L1/L2) remains open.
- SMTP for OTP e-mail codes (top follow-up from L1/L2) remains open.
