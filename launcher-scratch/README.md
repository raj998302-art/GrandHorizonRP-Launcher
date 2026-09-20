# GHRP Launcher — FROM-SCRATCH BUILD (v6)

Own Java implementation of the Grand Horizon RP launcher. The original Black Russia
launcher is used ONLY as the reference for the exact flow, UI, and the native engine
JNI contracts. No smali patching, no rebrand of the original APK — 100% own code
(64 classes), compiled with ECJ/d8/aapt2, signed with the GHRP release keystore.

## Architecture
- `com.blackhub.bronline.*` — engine bridge layer (exact JNI contract, verified
  signature-for-signature against the original smali — see verify_contract.py):
  JNILib (50 natives), JNIJSONTransport (59 engine callbacks), GameRender,
  JNIConfig, SoftwareKeyboardBridge, AppLocalValues, HelpshiftManager declaration,
  GUIManager (engine message dispatch, screen 0x58 = WebView auth), JNIActivity
  (engine host), JNIGLSurfaceView/JNIRenderer (GL), App, Settings.
- `com.grandhorizonrp.launcher.*` — own glue: UpdateController (config sync →
  feature flag → patch-index pre-seed → tryGetPatchIndex → tryDownloadResources
  with progress + retries), AuthController (WebView SSO with Android.initToken
  bridge to ghrp-auth.vercel.app), UpdateView (launcher-design progress UI),
  Http (Java TLS stack), GHRPLog (file logging + crash capture).
- Native libs: unmodified engine set (libblackrussia-client.so + deps) — the
  game engine renders all in-game + launcher XAML GUI (rebranded launcher.bpc
  from the gamedata release).

## Critical fixes vs the patched v5 (device failure 0x0011001C root causes)
1. httpData no longer carries the original's hardcoded BR CDN basic-auth
   ("main"/"DzEI3O4VDpdc6KpcSfd3") — v5 sent those to github.com (401 →
   "Reconnecting to CDN 2.0" loop). GHRP passes the url-config's EMPTY credentials.
2. fileRules = ["astc","nologo","nologo.astc","loader.video"] (JSONArray string,
   same contract as the original getUpdateFileRules for ASTC no-logo flavor).
3. tryGetPatchIndex correct signature (String jsonHttpData, String fileRules,
   Z, I version=1529, I candidate, I downloadTimeout=1200000, I
   connectionTimeout=15000, I distType=0, Z useBackupCdn, Z isDevMod, Z force).
4. Patch index pre-seeded through the Java HTTP stack (system CAs) before the
   native phase — the Java-side cache loadPatchIndexBytes() serves.

## Build
Requires: JDK 11+, ecj.jar, Android build-tools 36 + platform 36, zip,
uber-apk-signer, ghrp-release.keystore. Native libs are copied from the
original APK's lib/ dirs (both ABIs, 13 libs each, byte-identical).

    bash build.sh          # -> build/signed/unsigned-aligned-signed.apk
    python3 verify_contract.py   # JNI contract verification

## Engine init contract (exact)
JNILib.init(pathToRes=<externalFilesDir>, pathToSaves=<filesDir>, 1529, 0 /*Site*/, 5 /*Release*/)
JNILib.initRender(realWidth, realHeight, densityDpi, density, 1529)
STORAGE_ROOT AppLocalValue = externalFilesDir + "/"

## Flows
Splash (theme bg_welcome_img) → engine init → config sync (jsDelivr pin
@1ddc504: url-config + app-config + feature flag) → update (native um downloads
4.03 GB game data from the GitHub gamedata release, progress UI) → engine XAML
start screen (rebranded launcher.bpc) → PLAY → engine opens screen 0x58 with
auth_url → WebView SSO (ghrp-auth.vercel.app, production-verified) →
Android.initToken({front_token}) → sendJsonData(0x58) → engine Auth2 →
server 142.132.203.47:14448 → character creation (native) → game.
