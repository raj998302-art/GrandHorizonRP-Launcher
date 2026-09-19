# L5 — FINAL QA REPORT

**Session:** 19 Sep 2026 · **Subject:** GrandHorizonRP-Launcher-Final.apk (119,432,683 B, sha256 `4d5abe153dbeddf125d1150813c00a9daab8936c6f6df4fbc5fe6248967dc6f5`) + full-stack

## 1. Build matrix

| Artifact | Result |
|---|---|
| apktool 2.9.3 build (`--use-aapt2`, MT-Manager json→yml conversion) | ✅ SUCCESS, 5,160 entries |
| uber-apk-signer (ghrp-release.keystore) | ✅ zipalign verified, signature v2+v3 verified (CN=Grand Horizon RP) |
| verify_final.py — 34 checks | ✅ **ALL PASS** |
| Class parity vs original | ✅ **64,524 / 64,524** classes, 1:1 |
| Native libs vs original | ✅ **26/26 sha256-identical** |
| Binary manifest | ✅ package com.grandhorizonrp.launcher · versionCode 1529 · versionName 16.102.14498 · targetSdk 36 · minSdk 26 · gwpAsanMode INT_DEC −1 · JNIActivity/app class/all components |
| Stale refs | ✅ 0 × com.br.top in all dex/xml/arsc/json |
| Language | ✅ default strings.xml 100% EN (1,429 translated, 3 data-format keeps); engine en localization active (initLanguageOnStartup {ru,pt,en}, default en); client-api 53/53 files EN (13,902 strings); gamedata en client-jsons Horizon City pass |
| CDN bootstrap | ✅ pinned commit 0a76d48b… (3 URLs in classes4.dex, byte-verified) |

## 2. Static QA matrix (mapped to the user's mandatory test matrix)

| Area | Verifiable here | Result |
|---|---|---|
| Startup — cold/warm/first-install/second-launch | device-only | static-verified (dex/manifest/providers/signing); boot flow lines verified in smali; **device test pending** |
| Game data — empty/partial/complete/corrupted/existing/interrupted/resume | chain-verified | downloader = original libupdate-manager (libcurl FOLLOWLOCATION=1, range 206 ✅, retry "All CURL attempts", recovery flag on); patch_index/hash.json/self-consistent sizes ✅; **device test pending** |
| Authentication — guest/login/registration/OTP/password/recovery/invalid/expired | **E2E-verified** (L1/L2 via curl + agent-browser + live DB) | ✅ guest bridge `initToken({front_token})` captured; sign-in/recovery round-trips vs live LemmeHost MySQL (account_id=5); wrong-pass 401; production https://ghrp-auth.vercel.app healthy (db up) |
| Native engine — init/render/loading/WebView-handoff/server-selection | device-only | JNI surface + 78 screens + fragment routing + auth handoff verified in smali/binaries; natives byte-identical; **device test pending** |
| Character — male/female/skins/appearance/name/confirm/save/reconnect | device-only | NATIVE creation preserved (0x26; 767 skins 606M/161F; en l10n); server contract (accounts.name/sex/skin) verified; **device test pending** |
| Spawn — selection/loading/spawn/HUD/controls | device-only | spawnLocation.json EN verified 1:1 with launcher canon; **device test pending** |
| Server — list/connection/timeout/reconnect/unavailable/success | **live-probed** | ⚠️ game port 14448 REFUSED (LemmeHost free-server inactivity STOP — user must start from panel); MySQL 3306 OPEN, db up |
| Stability — rotate/background/resume/restart/low-memory/network-loss | device-only | original lifecycle code preserved (onNewIntent, deeplinks bhgbrgame, foreground download guard); **device test pending** |

## 3. Crash investigation status (the historical blocker)

- Previous build's black-screen crash: **native anti-tamper REFUTED by full binary audit** (0 kill/ptrace syscalls in 14.6 MB engine code; libsigner.so = Adjust fraud reader, no kill primitive; see L3_NATIVE_TAMPER_HUNT.md).
- The one **unintended deviation** of the failed build (targetSdk 34 vs 36) is FIXED in this build; gwpAsanMode encoding matches the original exactly.
- Remaining hypotheses (Java-SDK failure, pre-onCreate provider death, data-mount failure) are instrumented by the published diagnostic kit (release `diagnostics`: `GrandHorizonRP-Diagnostic.apk` — 8-stage marker provider with fsync; `GHRP-Test2-OriginalResigned.apk` — signature-only experiment).
- If the final APK still crashes on device: install the diagnostic APK, press the 8 buttons in order, export `ghrp-diag-report.txt` — the report pinpoints the failing stage.

## 4. Definition of done — current state

| Requirement | State |
|---|---|
| Original launcher audited | ✅ |
| L3 native engine (audit + rebuild + full English) | ✅ |
| L4 full integration (all live endpoints verified) | ✅ |
| Game data via GitHub release → Android data folder | ✅ chain + downloader verified (device execution pending) |
| Login/Register/Guest (web auth) | ✅ E2E live |
| Male/Female/Appearance/Name/Confirm (native) | ✅ preserved + contract verified (device execution pending) |
| Server connection + DB persistence | ⚠️ **requires LemmeHost server START (user action)** — currently auto-stopped |
| Final APK built + published | ✅ |
| Final server ZIP built + published | ✅ |
| **APK installed on real Android device, A→Z flow tested** | ❌ **NOT POSSIBLE IN SANDBOX — requires user's device** |

**Honest verdict:** everything achievable in this environment is done and verified. Task completion per the user's Definition of Done requires: (1) user starts the LemmeHost server, (2) user installs `GrandHorizonRP-Launcher-Final.apk` on a real device, (3) A→Z flow confirmation (or diagnostic report if anything fails).
