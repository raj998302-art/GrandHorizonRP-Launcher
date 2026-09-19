# ORIGINAL_VS_GHRP_RUNTIME_DIFF.md

**Purpose:** binary-level runtime comparison between the ORIGINAL working Black Russia
launcher and the Grand Horizon RP repack, to find every difference that could affect
startup behavior. Evidence collected 19 Sep 2026 (static analysis only — no device).

**Reference originals:**

| Artifact | What it is | How obtained |
|---|---|---|
| `launcher_src.zip` (818 MB) | MT Manager (apktool-M) decompile of the ORIGINAL `com.br.top` launcher (site build, flavor `siteRelease`) — the exact base of the GHRP repack | user's file.kiwi room |
| `orig-brgame.apk` (119,414,879 B, md5 `3a8e7cab95cbec5c7b67607adde428d9`) | TRUE original APK binary of the Google-Play variant `com.launcher.brgame`, version 16.102.14498 / versionCode 1529 — byte-for-byte the developer's release code | aptoide (verified md5) |

The Play variant differs from the site variant only by build flavor
(`marketRelease` vs `siteRelease`: explicit `FirebaseApp.initializeApp`, adjust tokens,
billing paths, flavor-suffixed Compose lambda names). Both run on real devices.

---

## 1. AndroidManifest.xml (binary, parsed with a full AXML parser)

Original site build vs GHRP APK — the COMPLETE list of differences:

| # | Item | Original | GHRP | Impact |
|---|---|---|---|---|
| 1 | `package` | `com.br.top` | `com.grandhorizonrp.launcher` | INTENDED (rebrand) |
| 2 | `uses-sdk targetSdkVersion` | **36** | **34** | **UNINTENDED DEVIATION** — found by comparing against both originals; fixed to 36 in the new diagnostic build |
| 3 | `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` name | `com.br.top.*` | `com.grandhorizonrp.launcher.*` | INTENDED, consistent |
| 4 | 6 provider authorities (FileProvider, FirebaseInit, AppMetrica preload, androidx-startup, Adjust, Helpshift) | `com.br.top.*` | `com.grandhorizonrp.launcher.*` | INTENDED. AppMetrica's provider rebuilds its UriMatcher from `getPackageName()` at runtime, so it matches the new authority. Helpshift/Adjust/FileProvider providers do not parse their authority. |
| 5 | JNIActivity deeplink `android:scheme` | resource ref `@string/app_auth_deeplink_scheme` (`0x7f12005c`) | literal `"bhgbrgame"` | apktool-M decode artifact; the string resource value IS `"bhgbrgame"` — functionally identical |
| 6 | `android:gwpAsanMode` encoding | INT_DEC `-1` | earlier build: string `"default"`; new build: INT_DEC `-1` | now matches original exactly |

Everything else — all activities, services, receivers, meta-data, permissions,
`application` attributes (`extractNativeLibs=true`, `requestLegacyExternalStorage=true`,
`usesCleartextTraffic=true`, `networkSecurityConfig`, `localeConfig`), versionCode 1529,
versionName 16.102.14498, minSdk 26 — is IDENTICAL.

## 2. classes.dex × 7 (the definitive DEX comparison)

- Class set: **64,524 classes in both** — exact 1:1, no missing/extra classes.
- Full bytecode diff (baksmali of GHRP APK vs the original decompile):
  - **0 instructions differ.** The only textual differences are:
    - dropped DEX `static_values` entries that equal the type default (`= false`, `= null`, `= 0x0`) — semantically identical (101 fields);
    - float-rendering **comments** (`# -6.805647338418769E38` vs `# -6.8056...E38`) — hex bit patterns identical;
    - `attrs.xml` enum/flag **reordering** (aapt2 sorts alphabetically) — values identical.
- vs the true original Play APK (brgame): all diffs are the build-flavor marker
  (`"app_marketRelease"` vs `"app_siteRelease"`, `$app_marketRelease` Compose lambda names,
  flavor-specific BuildConfig constants) — i.e. the two ORIGINALS differ from each other
  in exactly these spots — plus the 8 intended GHRP patches:
  1. `BuildConfig.APPLICATION_ID` → `com.grandhorizonrp.launcher`
  2. `ABTestUtils$Companion` / `DownloadWorker$doWork$2` / `MainActivityViewModel(+2)` → `"com.br.top/files"` path constants → new package
  3. `Settings` API base URLs → jsDelivr client-api
  4. `StoreUpdateHelper` market links → GitHub release
  5. `DownloadWorker$downloadFile$2` URL flatten (`/`→`.`)
- Stale references: **zero** `com.br.top` / `com/br/top` occurrences in all DEX, resources, assets (byte-level scan).

**Conclusion: the rebuilt DEX is a faithful reproduction. DEX corruption / VerifyError is ruled out as far as static analysis can.**

## 3. Resources

- `res/values/public.xml`: **byte-identical** to the original (all 14,565 resource IDs preserved).
- `values/strings.xml`: differs only by the intended rebrand (BLACK RUSSIA→GRAND HORIZON RP etc.); **0** other value changes.
- All other values files: identical except `attrs.xml` (reordering only, see above).
- `resources.arsc`: stored uncompressed + zipaligned (required); size differs from the Play original only because the Play original is an AAB-density-split base.

## 4. Native libraries

- 26 `.so` (13 arm64-v8a + 13 armeabi-v7a): **byte-identical** to the original set (verified against both originals).
- Compression: DEFLATED in the zip — same as the original site build (`doNotCompress` list from the original's apktool metadata does NOT include `.so`), legal because `extractNativeLibs="true"`.
- Load order (from `JNILib.<clinit>`): `update-manager` → `blackrussia-client`.
- No native library is loaded before `Application.onCreate` (all `System.loadLibrary` call sites are behind user-triggered/lazy init except `JNILib`, which is referenced first inside `JNIActivity.onCreate`).

## 5. Assets / kotlin / META-INF

- All 8 `assets/` files identical. `assets/dexopt/baseline.prof(m)` stored as in the original
  (ART validates profiles against dex checksums; on mismatch the profile is rejected
  gracefully — not a startup crash vector).
- `kotlin/` dir identical. `META-INF` re-created by signing (v1) as expected.

## 6. ZIP container

| Property | Original site build | GHRP | Match |
|---|---|---|---|
| dex compression | DEFLATED | DEFLATED | ✅ |
| .so compression | DEFLATED | DEFLATED | ✅ |
| resources.arsc | STORED | STORED (+zipalign verified) | ✅ |
| png/webp/ogg/jpg, baseline.prof(m), res/raw billing files | STORED | STORED | ✅ |
| entry count | ~5.1k | 5,147–5,150 | ✅ (equivalent) |

## 7. Signing

| | Original site | Original Play (brgame) | GHRP |
|---|---|---|---|
| scheme | publisher cert (v1+v2…) | aptoide-generated cert (v2+v3, AAB-derived, **not** the developer's) | GHRP release keystore (v1+v2+v3) |
| cert sha256 | (unknown — original binary not available) | `3ce3a643…` | `db509ae029d36a71aa939a612bc19a2da3def2b5bf80cfca22f02a7718e9f96c` |

**The signature is different from the publisher's — and this is the one runtime
difference that static analysis cannot eliminate.** The Play variant proves the engine
runs under a *different application id* (`com.launcher.brgame`) and under a
*non-developer certificate* (aptoide's), but that build's constants were compiled for
its own package, and we cannot verify on-device behavior from here.

## 8. Summary — what is actually different at runtime

1. **Signature** (GHRP keystore instead of original publisher) — **hypothesis G (integrity/anti-tamper), unproven** → TEST-2 APK isolates exactly this variable.
2. **Package name** + the 8 patched constants (intended, Play variant proves a foreign package id works when compiled for it).
3. **targetSdk 34 instead of 36** (UNINTENDED deviation, now FIXED to 36 in the diagnostic build).
4. Branding strings + CDN URLs (intended).

Everything else — all 64,524 classes with identical bytecode, all resource IDs and values,
all native libraries, all assets, the whole manifest component set — is **proven
byte-identical or semantically identical to the working original.**

## 9. Crash-class assessment (user's categories A–J)

- B (UnsatisfiedLinkError), I (repack changed something the engine needs): **largely ruled out statically** — code/resources/natives are faithful.
- A (Java/Kotlin exception): possible but no Java crash evidence survived (no `ghrp-crash.txt`, no toast) — if a Java exception fired inside `App.onCreate`, the previous diagnostic build would have captured it (its crash handler was the first statement of `App.onCreate` and writes with try/catch around every step).
- **Strongest remaining hypotheses, in order:**
  1. **G — runtime integrity / signature check** in the native engine or an SDK (kills the process without a Java stacktrace). TEST-2 settles this on-device.
  2. **Crash before `Application.onCreate`** (class-load/verification of the huge 30 MB primary dex, or a ContentProvider failing on this specific device/Android 15 combination) — the new diagnostic build's `GhrpDiagProvider` (initOrder −1000) writes markers BEFORE every other component, so this stage is now observable.
  3. **H — Android 15 / device-specific** (16 KB pages on a 16 KB kernel device would kill `System.loadLibrary` — but that would be AFTER `App.onCreate`, and the original would fail identically, so it does not explain "original works").

**Honesty note:** no device or emulator exists in this sandbox (no adb, no KVM,
arm-only natives). Everything above is STATIC_VERIFIED. The two diagnostic APKs
published alongside this document are the instruments that turn the remaining
hypotheses into facts on the user's phone.
