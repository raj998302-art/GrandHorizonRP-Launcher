# L3 — NATIVE ENGINE AUDIT (Grand Horizon RP)

**Session date:** 19 Sep 2026 · **Task IDs:** L3-a…L3-e, L3-NATIVE-HUNT, L3-REBUILD
**Scope:** original launcher native engine + runtime flow, reverse-engineered from the ORIGINAL purchased launcher (`file.kiwi` launcher_src.zip = apktool-M decode of `com.br.top` site build v16.102.14498 / versionCode 1529, decoded tree at `/tmp/audit/launcher_src`, pristine working copy at `/home/z/ghrp-build/launcher_src`).

---

## 1. Original engine architecture (verified facts)

| Component | Identity / role | Evidence |
|---|---|---|
| `JNIActivity` (`com.blackhub.bronline.game.core.JNIActivity`) | MAIN/LAUNCHER activity — hosts the native engine | manifest; smali_classes4 |
| `JNILib` (`…game.core.JNILib`) | Java↔native bridge; `<clinit>` loads **libupdate-manager.so FIRST, then libblackrussia-client.so** | JNILib.smali clinit 13–18 |
| `libblackrussia-client.so` (28.7 MB arm64 / 20.7 MB v7a) | the game engine (render, Noesis GUI, SA-MP netcode, character creation, world) | 60 exported `Java_*` symbols incl. `GameRender_initGameRender`, `JNILib_*`, `JNIConfig_*` |
| `libupdate-manager.so` (9.8 MB) | download/patch machinery: statically-linked **libcurl 8.16.0 + OpenSSL 3.6 + nlohmann-json + xxHash**; `PatchIndexApiClient`, `DownloadFilesSystem`, `PatchFilesSystem(2/3)`, `UpdateManagerSystem`, `BackupSystem`, `RollbackSystem` | strings + symbol analysis |
| `libsigner.so` (1.1 MB) | **Adjust SDK fraud-signature collector** (OLLVM control-flow-flattened, XOR-obfuscated strings: `getPackageInfo`, `signatures`, `TracerPid:`, `/proc/self/maps`) — **no kill primitive** | disassembly; imports/syscall audit |
| engine localization | `Base/Localization/en/Global.xamlux`, `Base/Localization/en/Strings.xamlux`, `…/ru/…` — **EN localization is built into the engine**; engine reads `uiLanguage` key | strings in libblackrussia-client.so |
| data localization | gamedata ships `jsons/client-jsons.zip` (RU), `jsons/en/client-jsons.zip` (EN), `jsons/pt-br/client-jsons.zip` | files.zip listing; gamedata release assets |

### 1.1 Startup/runtime flow (A→Z, from smali, exact call sites)
1. Process start → providers (Adjust, AppMetrica preload, androidx-startup, Helpshift, FirebaseInit, FileProvider) → `App.onCreate` (smali:675).
2. `JNIActivity.onCreate` (smali:3194) → `JNILib.init(..., 0x5f9 /*=1529*/, ...)` (3879–3881) → loads update-manager + blackrussia-client.
3. Engine native init → `onNativeInitFinish` via packet `0x54 {"c":1}` → GUIManager.
4. First fragment observer shows **0x53 InitializationFragment** (lambda$setObservers$13: 1883–1934, showGUI 0x53 @1913).
5. `getBaseLinks` (2388) → `onUrlConfigReceived` (`$getBaseLinks$1`:484) fetches **url-config.json** via Retrofit from the Settings API base (jsDelivr client-api in GHRP) → regions, cdnUrl, registrationService…
6. `onAppConfigReceived` (`$getRawAppConfig$1`:140) → app-config.json → version contract (version_android 1529 == JNILib 0x5f9 == patch_index.android_version ⇒ no update loop).
7. InitializationFragment → `startPermissionRequestAndUpdateFiles` (1914) → `tryUpdateFiles` (1999) → update manager (`tryGetPatchIndex $checkUpdateForUpdateManager$1`:583) downloads/verifies gamedata per **patch_index.json** (crc_xxhashct per file) into `<external-files>/files` tree; progress via 0x65/0x66 callbacks; download foreground guard service.
8. `$lambda$8` (0x53→0x56) / `$lambda$9` (0x53→0x55) → **0x56 UpdateManagerFragment** → `finishUpdateManager` (773) `closeGUI(0x56)+showGUI(0x55)` (812–825) → **0x55 MainFragment** (homepage).
9. MainFragment → play → **0x58 WebViewAuthFragment** (auth_url from url-config `registrationService`; appends sysinfo/ttclid/client_id/appmetrica_device_id/adjust_id) → original-style SSO web app (GHRP: https://ghrp-auth.vercel.app, 1:1 reproduction, PASETO v4.public front tokens).
10. `WebAppInterface.initToken({front_token})` → `AuthViewModel.saveTokenAndCloseWebView` (1023) → `GUIManager.sendJsonData(88,{front_token})` + `closeGUI(88)` → **native engine continues auth** (engine-side Auth2 API).
11. **Character creation stays NATIVE** — SCREEN_REGISTRATION **0x26** + engine XAML screens (SelectGender/SelectSkin/CreateCharacter/SelectCharacter/SpawnLocation in gui.bpc; wire screens 38/202/50; 767 skin .mod models 606M/161F; br_tex_skins textures) — see CHARACTER_CREATION_ASSET_MAP.md.
12. Name entry/validation → server contract; confirmation → spawn selection → world load → HUD → SA-MP connection (142.132.203.47:14448).
13. `onPacketIncoming` (3691–3900) full dispatch verified (0x63 analytics / 0x64 login+USER_ACCOUNT_ID+billing / 0x65–0x66 update / 0x67 incompatible-version / 0x54 native-init / LauncherHelper.handleJson dialogs).
14. Reconnect/errors: DialogType enum (BanReason/Policy/InsufficientSpace/NetworkError/Rules/ApplicationUpdate/ContentUpdate/LauncherUpdate), MemorySpaceChecker, deeplinks `bhgbrgame://` (kept).

### 1.2 GUI routing (GUIManager)
- 79 ScreenId constants (78 semantic) — all 78 screens preserved in the rebuild (no screen removed/added).
- `onFragmentChange` packed-switch: 88 cases 0x1..0x58 → 58 Java fragments; legacy `createGuiFromId` 12 ISAMPGUI dialogs; `showGUI` special-cases {13,65,39} touch-cancel, 0x33 custom tab; `screensIsFragment` whitelist 57 ids (mapped-but-dead 0x40 HalloweenAward in this build — original behavior kept).

## 2. Anti-tamper hunt (hypothesis G) — **REFUTED for the crash**
Deep AArch64 binary analysis (custom ELF parser + annotated disassembler; full-report `/tmp/audit/reports/L3_NATIVE_TAMPER_HUNT.md`):
- `libblackrussia-client.so`: JNI_OnLoad @0x13cf6e8 = Crashlytics dlopen + logger only. **533 init funcs all benign; whole 14.6 MB .text contains 0 SVC, 0 kill/tgkill/ptrace; abort×1446 = libc++ asserts; raise×47 SIGTRAP gated behind default-0 .bss flag; exit×2 = OOM path.** Not packed (entropy 6.59/8, plaintext RTTI).
- `/proc/self/task/%d/comm` @0x2d0bb6 (xref 0x152a124) = **thread-name getter for logging** (no scan/compare). `/proc/self/exe|file` = whereami self-path resolution.
- No `AndroidManifest/META-INF/classes.dex/getPackageInfo/signatures/TracerPid` strings in any encoding in either engine lib.
- `libsigner.so` re-audited: contains the hidden signature/anti-debug READERS (Adjust fraud SDK, OLLVM) but **no kill primitive** — its 6 raw-syscall sites (openat/read/close/clock_gettime/gettimeofday/getpid) cannot kill the process.
- **Conclusion: the observed device crash (black screen 1–2 s) cannot be explained by native anti-tamper.** Remaining suspects (in order): Java-layer SDK failure handling, pre-`Application.onCreate` provider death, engine data-mount failure — instrumented by the already-published diagnostic kit (release `diagnostics`: `GrandHorizonRP-Diagnostic.apk` 8-stage marker provider + `GHRP-Test2-OriginalResigned.apk` signature experiment).

## 3. Downloader redirect verification (L4-critical)
`libupdate-manager.so` embeds libcurl 8.16 (vcpkg cross-build). PLT-resolved analysis (GOT 0x951a08 → stub 0x8b17a0) mapped **all 77 `curl_easy_setopt` call sites**; option histogram includes **CURLOPT_FOLLOWLOCATION(52)=1 set at 0x49c344 and 0x4be67c** → the native downloader **follows HTTP 302 redirects**. GitHub release asset URLs (which 302 to `release-assets.githubusercontent.com`) are therefore fully compatible with the original downloader. TIMEOUT_MS(155)/CONNECTTIMEOUT_MS(156) also set — matches `update_manager_feature_flag.json` connection_timeout:30.

## 4. Game-data installation contract (original, preserved)
- patch_index.json: 159 files, `android_version: 1529`, per-file `crc_xxhashct` (xxHash verified natively), `link` = release asset name (`#` suffix = original format).
- Update flow: PatchIndexApiClient → DownloadFilesSystem (multi-attempt, resumable) → PatchFilesSystem → BackupSystem/RollbackSystem → SetVersionResources. `is_enabled_recovery:true` in feature flag.
- Game data is **NOT embedded in the APK** (original behavior): downloaded to the app's external files tree, then loaded by the native engine from disk.
- Version triangle verified live: app-config.version_android = 1529 = patch_index.android_version = APK versionCode (no update loop).

## 5. Rebuild (L3-d) — what changed vs the original, and what did not

**UNCHANGED (byte-identical):** all 26 native libraries (sha256-verified), all 64,524 classes (1:1), all resource IDs (public.xml), all assets, manifest component set, deeplink scheme, engine screens/flow, character creation (native), server connection flow.

**Intended changes (11 patch groups, `launcher-patches/v3/patch_v3.py`, each self-verified):**
1. Settings API base ×3 → `https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@e3eab73760ce0b48b524b80b3aec402b3428c0f2/client-api/` (**pinned commit** — immune to jsDelivr @main resolution lag; decision after a real @main poisoning incident this session).
2. Manifest `package` → `com.grandhorizonrp.launcher` (+ 6 authority renames, 1 permission rename). Internal `com.blackhub.bronline` engine identifiers **preserved** (JNI native symbol names depend on them).
3. `BuildConfig.APPLICATION_ID` + 5 `"…/files"` path constants (data dir moves with the package — Android requirement).
4. `StoreUpdateHelper` self-update links → GitHub release.
5. `DownloadWorker` path flatten `'/'→'.'` (CDN asset naming).
6. Branding tokens (BLACK RUSSIA→GRAND HORIZON RP etc.) in all locales.
7. **Full English:** default `strings.xml` = 1,429 translated strings (audit-work/tr dictionaries; 3 data-format keeps), `common_enter_email_or_social` translated.
8. **Language support:** `initLanguageOnStartup` set {ru,pt}→{ru,pt,en}, default ru→en (engine has built-in `Base/Localization/en`; gamedata ships `jsons/en/client-jsons.zip`).
9. `getLocalizedResourcePath`: `en`→`"en/"` branch (only callers = legacy cases-hint images that don't exist in current gamedata — graceful fallback, zero behavioral risk).
10. BrSimBanner fallback URLs → GitHub.
11. **client-api (CDN content): all 53 Cyrillic files translated (13,902 strings)** — official-EN-localization-based wording + GHRP glossary; committed `e3eab73`.

**Fixes vs the previously FAILED build:** targetSdk 34→**36** (the one uncorrected deviation of the failed build), gwpAsanMode INT_DEC −1 encoding, pinned CDN, English everywhere, 0 stale refs.

## 6. Verification (all PASS — `launcher-patches/v3/verify_final.py`, 34 checks)
Binary-manifest AXML parse (package/versionCode 1529/versionName 16.102.14498/targetSdk 36/minSdk 26/application class/JNIActivity/download service/7 provider authorities/gwpAsanMode −1) · zip CRC + no duplicates (5,160 entries) · 0 stale `com.br.top` refs · 7 valid dex · 64,524 classes · pinned URL present · resources.arsc stored + GRAND HORIZON RP label + EN strings + RU counterpart absent · 26 natives sha256-identical · .so deflated/pngs stored · assets/kotlin present · APK Signing Block v2+v3 · zipalign verified.

## 7. Honest limits (device-testable only on real hardware)
- No Android device/emulator exists in this sandbox: runtime A→Z (splash → download → auth → native character creation → spawn → server connection) is **statically verified, not device-verified**. The published diagnostic kit settles any residual crash cause on-device with stage markers.
- Engine-side Auth2 endpoints are runtime-built (device capture TODO from L1/L2 remains).
- The previously observed crash remains unexplained by static analysis; strongest remaining hypotheses are instrumented by the diagnostic kit (see §2).
- Currency naming ("₽"/rubles in some official-EN strings) is a pending project-wide style decision (CURRENCY_TBD) — untouched to avoid inventing.
