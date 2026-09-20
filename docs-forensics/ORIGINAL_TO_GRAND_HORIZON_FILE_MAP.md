# ORIGINAL → GRAND HORIZON FILE MAP (Forensic Audit, Task 25-B)

> Source: `/home/z/ghrp-scratch/orig-decode/launcher_src/` (928 MB decoded APK of the original
> Black Russia launcher, flavor `site`, `APPLICATION_ID com.br.top`, `VERSION_CODE 1529 (0x5f9)`,
> `VERSION_NAME 16.102.14498`, git branch `android-release-16.102-1529-auth2-2`, commit `eb9c6720`).
> **Reference only — never a build input or runtime dependency of Grand Horizon.**
> All evidence paths below are relative to `launcher_src/` and were read read-only.

---

## 1. Top-level layout (sizes via `du -sh`)

| Path | Size | Content |
|---|---|---|
| `AndroidManifest.xml` | 28 K | single-activity app: `com.blackhub.bronline.game.core.JNIActivity` (MAIN/LAUNCHER, `sensorLandscape`, `singleTask`), deep link `bhgbrgame://auth` (`res/values/strings.xml:95-96`), GL ES 3.0 required, biometric/mic permissions, billing 8.0.0, Firebase (Crashlytics-NDK + Firestore), Rustore/VK providers, FileProvider `com.br.top.provider` |
| `smali/` … `smali_classes7/` | 777 MB | 7 dex images, 64,524 `.smali` files total (17,153 + 8,784 + 9,138 + 11,470 + 7,372 + 9,250 + 1,357) |
| `lib/{arm64-v8a,armeabi-v7a}` | 46 M / 33 M | 13 native libs each (see doc 4) |
| `assets/` | 440 K | `Fonts/{Roboto-Regular,muller_bold}.ttf`, `PublicSuffixDatabase.list`, `helpshift/{Helpcenter,Webchat}.js`, `adi-registration.properties`, `dexopt/baseline.prof(m)` |
| `res/` | 72 M | 3,629 XML + 3,935 drawables, ~45 config buckets (`layout-land`, `drawable-pt-nodpi`, …) |
| `kotlin/` | 112 K | kotlin metadata |
| `original/`, `unknown/`, `META-INF/` | — | decoder keeps + protobuf descriptors, META-INF module files |

## 2. Class census (all dexes)

| Package root | Classes | Role |
|---|---|---|
| `com/blackhub/bronline` | **8,816** (305 in `smali/` + 8,511 in `smali_classes4/`) | the launcher + game UI (ours to mirror) |
| `com/google/*` | 11,132 (firebase 2,259; gms 3,148; protobuf 462; gson 225) | Firebase/Crashlytics/Firestore, GMS auth+billing, protobuf, gson |
| `androidx` | 31,065 | framework compat + Compose + Room + WorkManager |
| `kotlin`/`kotlinx` | 3,162 | stdlib/coroutines |
| `ru/rustore` (+`ru/vk`) | 786 | Rustore billing/remote config/review, VK store |
| `com/adjust` | 301 | Adjust attribution |
| `com/helpshift` | 342 | support SDK |
| `com/airbnb` | 404 | Lottie |
| `com/caverock` (134) + `com/gcssloop` (12) | 146 | MPAndroidChart + color picker |
| `com/pierfrancescosoffritti` | 74 | YouTube player |
| `com/samsung` | 22 | Samsung SDK |
| `android`/`org`/`okhttp3`/`dagger` | ~910 | support + DI + HTTP |

### `com.blackhub.bronline` breakdown (class counts per package, `find -maxdepth 1`)

| Subpackage | Classes | Responsibility |
|---|---|---|
| `launcher/fragments` | 126 (incl. lambdas) | Loader / Initialization / UpdateManager / Main / WebViewAuth fragments |
| `launcher/viewmodel` | 51 | AuthViewModel, MainActivityViewModel (+ coroutine state machines) |
| `launcher/download` | 42 | WorkManager workers: UpdateManagerWorker, UpdateManagerSlotDownloaderWorker, DownloadWorker, foreground guard service + notifications |
| `launcher/update` | 48 | ExternalStorageChecker, MemorySpaceChecker, NetworkConnectionChecker, MarketAppUpdateHelper, UpdateApkHelper, FirestoreErrorSender, WorkManagerHelper |
| `launcher/database` | 40 | Room: LauncherDatabase (`MyFiles`), PurchaseDatabase (2 tables), DAOs |
| `launcher/network` | 21 | Api/Auth/PaymentApi (Retrofit), NetworkProvider (CDN + backup CDN + billing services), UserAgentInterceptor, FCM service, Server model |
| `launcher/di` | 102 | Dagger modules (App, Billing, Database, Dispatchers, Helpshift, Network…) |
| `launcher/data` / `model` | 7 | AuthUiState, MyFile, UpdateManager{CallbackData,ErrorCallbackData,UiProgress,SizeData}, FileInfoForDownloadItem |
| `launcher/webview` | 1 | **WebAppInterface** (the `Android` JS bridge) |
| `launcher/dialogs`, `logging` | 9 | update dialogs, file logging |
| `game/core` | 87 (root) | **JNIActivity** (4,979 lines), **JNILib** (native decls), **JNIJSONTransport** (3,387 lines, ~70 static JNI callbacks), JNIGLSurfaceView, JNIRenderer, JNIConfig(+Chooser), JNIExceptionReporter, JNIDevHubClient, SettingsHelper(+Companion 1,299 lines), AppLocalValues, ABTestUtils, BuildType, DistributionType, EStartupMode, FeatureFlag* |
| `game/core/*` subpkgs | constants 26, enums, model, network, preferences 3, resources, utils, viewmodel, keyboardHelper, extension | constants incl. **ScreenId**, **NativeSettingsKeys**, PatchIndexStatusJsonKeys, PathConstants, LinksConstants, FileFormats, FilePrefix; Preferences{,Repository,Impl}; JNIActivityViewModel (224 K) |
| `game/gui/*` | 60+ subpackages | one package per native screen (see doc 3 table) |
| `game/ui/*` | 46 subpackages | Compose UI implementations for the same screens |
| `game/common` | 50 | BaseFragment/BaseViewModel, LocalNotification, media player, mkloader, roundcornerprogressbar, colorpickerview, sensormanager, composemanager |
| `game/theme` | 6 | TypographyStyle (630 K smali!) + themes |
| `analytics` (in `smali/`) | ~30 | AnalyticEngine{Adjust,YandexAppMetrica}, Composite, DeviceInfoJNI, Firebase ids, ttclid storage |

## 3. Functional family map (original → Grand Horizon)

Runtime stages: **S**=splash/pre-native, **I**=init/config, **U**=update, **A**=auth, **L**=lobby/server+character, **G**=gameplay, **X**=cross-cutting.

| Original file/class | Responsibility | Dependencies | Stage | Grand Horizon equivalent | Status |
|---|---|---|---|---|---|
| `game/core/JNIActivity.smali` (4979 ln) | the ONE Activity: hosts `JNIGLSurfaceView` + `jniactivity_fragment_container` + `jniactivity_notification_container`; calls `JNILib.init(filesDir=external files dir, savesDir=getFilesDir(), 0x5f9, distribution, buildType)` (line 3881); back handling; insets; review flows | JNILib, GUIManager, both ViewModels | S,I | `MainActivity.java` (own single-activity flow, no engine-hosting parity needed at M1) | PARTIAL (by design — our engine is in-app, not fragment-driven) |
| `game/core/JNILib.smali` | `System.loadLibrary("update-manager")` then `("blackrussia-client")`; ~45 native methods (init, initRender, step, resize, pause/resume, multiTouchEvent, orientationChanged, networkConnectionChanged, sendJsonData, deliverNetworkEvents, onApp/UrlConfigReceived, tryGetPatchIndex, tryDownloadResources/NextSlot, cancelDownloadResources, getCurrent/TargetPatchVersion, getResourcesState, getJsonFromArchive, getBitmap/FileFromAssetsAsync, requestPlayers, sendChatMessage, getPlayerId/VehicleType, volume/mute, media hooks, sendUpdateSystemCodedMessage, …) | libupdate-manager.so + libblackrussia-client.so | I,U,G | `engine/GHNative.java` (own surface: init, setDataRoot, createScene, rotate, step, nativeGetEngineInfo) | PARTIAL (M1 surface; update moved to Java) |
| `game/core/JNIJSONTransport.smali` (3387 ln, ~70 static methods) | **native→Java callback surface**: onJsonDataIncoming(guiid,bytes), sendJsonData, onSettingsJsonDataUpdate, getSerializedSettings, {load,store,remove,isExist}StateBytes (`state_monitor.sm`), {…}PatchIndexBytes (`patch_index.json`), {…}FailureFlag (`failure_flag`), getDeviceInfo, getCompatibleClientVersion/set…, getVersionResources/set…, checkFreeSpaceMemory/getFreeSpaceMemory, keyboard APIs, clipboard, vibration, media decoder, onDialogRPCIncoming, onTabEvent, OnRequestPlayersCompleted, onSpawn, onSplashScreenDestroyed, quitGame, showErrorDialog, reportEvent(ForAllProviders), sendErrorToFirebaseFirestore, closeAllWindows(ExSAMP) | JNI registered from native (JNI_OnLoad) | X | `GHNative.java` + `GHEngineView.java` callbacks (onEngineInfo, onSceneReady) | PARTIAL |
| `game/GUIManager.smali` (4323 ln) | screen router: `onJsonDataIncoming`→`onPacketIncoming(id,json)` (special ids 0x63/0x64/0x65/0x66/0x67/0x54; type=1 open, type=2 close; `LauncherHelper.handleJson` for 0x54 dialogs) → `onFragmentChange(id,bundle)` packed-switch **0x1..0x58** (88 ids); `sendJsonData(id,json)`; `createGuiFromId` (packed-switch 0x7..0xa → BrDialogWires/BrDialogPipes/BrAudioDialog/BrDialogWindow + if-chain for 0x17 sawmill, 0x1a captcha, 0x29 vip, minigames); analytics hooks; `showingScreen`/`isOpenScreen` maps | JNIJSONTransport, fragments, analytics | X | `MainActivity.java` state machine + `ORIGINAL_SCREEN_STATE_MACHINE.md` | PARTIAL (launcher-level; 60+ game screens not needed until M2+) |
| `launcher/fragments/WebViewAuthFragment.smali` (1217 ln) | screenId **0x58**: reads `auth_url` from engine JSON bundle, `Uri.buildUpon().appendQueryParameter(sysinfo=android, ttclid, client_id, appmetrica_device_id, adjust_id)`, injects `WebAppInterface` as **`Android`** (`addJavascriptInterface`, line 519), WebViewClient, error dialogs | AuthViewModel, WebAppInterface, analytics providers | A | `AuthController.java` (full-screen WebView; own params) | COMPLETE (functionally) |
| `launcher/webview/WebAppInterface.smali` | `@JavascriptInterface` **initToken(jsonString)**, **closeWebview(errorString)**, **openHelpshift(jsonString)**, **startIdpAuth(url)** | AuthViewModel | A | `AuthController.java` inner JS bridge (`Android.initToken`) | COMPLETE |
| `launcher/viewmodel/AuthViewModel.smali` (1247 ln) | `saveTokenAndCloseWebView(json)`: `GUIManager.sendJsonData(0x58, JSONObject)` + `closeGUI({},0x58)` (line 1023-1105); `closeWebView(errorString)`: `{"r":1}` + base64url `body` decode → also 0x58; `handleAuthRedirect(intent)`: deep link `bhgbrgame://auth?*` → query params JSON → same 0x58 path; `openCustomTabs` (IDP), `openHelpshift` | GUIManager, HelpshiftManager, LocalNotification | A | `AuthController.java` + ghrp-auth `/api/v2` (token→session.json) | COMPLETE (equivalent contract; see doc 2) |
| `game/core/SettingsHelper(.smali + $Companion 1299 ln)` | **session restore**: `getSerializedSettings()[B` builds UTF-8 JSON from SharedPreferences `preferences` incl. `playerName, playerCharacterId, playerServerId, authAccessToken, authRefreshToken, authFlowType, isPolicyAccepted, abInstallationId` + graphics/audio/control settings + apiUrl/apiUserName/apiPassword/apiBackup*/apiUserAgent/telegram*/vk/discord/policy URLs | Preferences, BuildConfig, JNIActivity | I | `SessionStore.java` (session.json schema-gated) + `LauncherConfig.java` | COMPLETE (different store, same contract) |
| `game/core/preferences/Preferences.smali` | SharedPreferences file name **`preferences`**; keys: AUTH_ACCESS_TOKEN, AUTH_REFRESH_TOKEN, AUTH_FLOW_TYPE, AUTH_TOKEN_KEY(auth_token), USER_ACCOUNT_ID, USER_SERVER_ID, PLAYERS_CHARACTER, PLAYERS_NICK, PRIVACY, AB_INSTALLATION_ID, IS_NOT_FIRST_LAUNCH, RESOURCES_VERSION, COMPATIBLE_CLIENT_VERSION, ADJUST_ADID, TTCLID, EMAIL, install_source, DevHubServerAddress, sent purchase tokens | — | X | `SessionStore.java` + `LauncherConfig.java` | COMPLETE |
| `game/core/preferences/PreferencesRepository(.Impl)` | DI facade over Preferences (put/getString/Int/Float/Boolean, contains, purchase-token sent markers) | Dagger | X | (folded into SessionStore/LauncherConfig) | COMPLETE |
| `game/core/constants/NativeSettingsKeys.smali` | Gson DTO of the native settings JSON (camelCase): resolution…keyboardVersion, uiLanguage, region, playerName, playerCharacter, lastServer, **authAccessToken, authRefreshToken, authFlowType**, isPolicyAccepted, abInstallationId | gson | X | (contract documented in doc 2; our own schema) | COMPLETE (documented) |
| `launcher/viewmodel/MainActivityViewModel.smali` (244 K) | startup orchestration: getBaseLinks, getRawAppConfig, fetchFeatureFlag (retry), checkUpdate/checkUpdateForUpdateManager (→ `JNILib.tryGetPatchIndex(jsonHttpData{cdn,backup_cdn,username,password}, fileRules, …)` line 583), updateDB (Room), deleteUnusedEntriesFromDB, getSizeOfUpdateFromDB, setCurrentArchitectureFolder, checkAppVersion/isLauncherVersionActual, startNextSlotWorkManager, checkRustoreUpdate | NetworkProvider, LauncherDatabase, JNILib | I,U | `UpdateController.java` + `LauncherConfig.java` (feature-flag + patch-index JSON, own CDN) | COMPLETE (Java-native, no .so update manager) |
| `launcher/fragments/LoaderFragment.smali` (screenId 0x54) | update gate UI: progress bars, buttons, observes WorkManager, `deleteFilesNotFromListAndStartMainFragment`, sends 0x54 progress JSONs via LauncherHelper | MainActivityViewModel | U | `UpdateView.java` + `UpdateController.java` | COMPLETE (launcher-level) |
| `launcher/fragments/InitializationFragment.smali` (0x53) | init/status UI while native boots (observers on startup flows) | MainActivityViewModel | I | splash in `MainActivity.java` | COMPLETE |
| `launcher/fragments/UpdateManagerFragment.smali` (0x56) | newer update UI (slot download, sizes, errors) | MainActivityViewModel, workers | U | (merged into UpdateView) | COMPLETE |
| `launcher/fragments/MainFragment.smali` (0x55) | lobby: news/play buttons, profile chip | engine JSON | L | server-select panel in `MainActivity.java` | PARTIAL (native GUI parity = M2) |
| `launcher/download/*Worker*.smali` | WorkManager: `UpdateManagerWorker` → thread calls `JNILib.tryDownloadResources(isEnabledRecovery, downloadSpeedLimit, isEnabledCheckResources, downloadTimeout, connectionTimeout, isEnabledSendingOfCDNMetric, forceCheckResources)`; `UpdateManagerSlotDownloaderWorker` → `tryDownloadNextSlot(…, fileRules, …)`; DownloadWorker (APK); foreground service + notifications | JNILib, WorkManager | U | `UpdateController.java` (own downloader: patch-index diff + range-resume) | COMPLETE |
| `launcher/update/*` | space/network checks, store update helpers, Firestore error sender, WorkManagerHelper | — | U | space/network checks inline in UpdateController | COMPLETE |
| `launcher/database/LauncherDatabase.smali` + `MyFile` + `MyFileDao` | Room DB table `MyFiles(id, name, path, size, data, downloaded)` — tracks downloaded game files for diffing/cleanup | Room | U | UpdateController's patch-index map (JSON) | COMPLETE (different mechanism) |
| `launcher/database/PurchaseDatabase*` | Room: BillingPurchases + BillingRustorePurchases (analytics of purchases) | Room | G | — (no billing in GHRP) | N/A (deliberate) |
| `launcher/network/Api.smali`, `NetworkProvider.smali`, `UserAgentInterceptor` | Retrofit services for CDN config/feature flag/billing; UA `MOl9ISIvsVFgqqVgDIBpVmf` (BuildConfig line 33) | okhttp | I,U | `Http.java` + ghrp-auth `/api/v2` + GitHub release CDN | COMPLETE |
| `launcher/network/Server.smali`, `launcher/LauncherHelper.smali` | Server model (list from engine), launcher dialogs (ban reason, registration rules, network error, insufficient space, app update) + 0x54 dialog protocol (`{"t":id,"r":result}`) | GUIManager | A,L | server select + error panels in MainActivity | COMPLETE (launcher-level) |
| `game/core/JNIGLSurfaceView.smali` + `JNIRenderer.smali` + `JNIConfigChooser` | GLSurfaceView hosting the engine render thread; `JNILib.initRender(w,h,dpi,dpiScale,version)`, `step()` per frame; EGL config chooser | JNILib | G | `engine/GHEngineView.java` + `GHRenderer.cpp` (own render thread) | PARTIAL (M1: character scene only; world = M2) |
| `game/core/keyboardHelper/*` (SoftwareKeyboardHelper, KeyboardHeightProvider, SoftwareKeyboardBridge JNI) | soft keyboard bridge to engine (nativeCommitText/OnKeyDown/…) | JNI | G | — (engine-side IME not yet needed) | MISSING (M2+; not needed until text entry in-engine) |
| `game/core/JNIExceptionReporter`, `logging/*` | crash forwarding to Crashlytics; file logger (`initFileLogger`) | Firebase | X | `GHRPLog.java` + engine `GHLog` | PARTIAL (no crash upload by design) |
| `game/core/JNIDevHubClient.smali`, DevHubServerAddress pref | dev-mode gRPC-ish hub (127.0.0.1:50051) | — | dev | — | N/A |
| `analytics/*` (Adjust, AppMetrica, Firebase ids, ttclid) | attribution + device ids (fed to auth URL + engine 0x63) | SDKs | X | — none (privacy decision), ghrp-auth uses its own ids | N/A (deliberate; documented) |
| `launcher/di/HelpshiftManager.smali` (+`nativeInit` JNI export) | support chat | Helpshift SDK | X | — (no support SDK) | MISSING (deliberate) |
| `launcher/network/MyFirebaseMessagingService.smali` + `NotificationDismissedReceiver` | push notifications (FCM) | Firebase | X | — | MISSING (deliberate) |
| `game/gui/**` (60+ screen packages, see doc 3) | Android fragments/Compose UI for native screens | GUIManager | G | — (M2+ backlog per GRAND_HORIZON_ENGINE_GAP_ANALYSIS.md) | MISSING (planned M2+) |
| `game/theme/TypographyStyle.smali` (630 K) | Compose typography | Compose | G | own styles in MainActivity | N/A |
| `BR.smali` (Application) | process start | analytics | S | `GHRPApp.java` | COMPLETE |

### Native-bridge message contract (Java↔engine) — the part our engine must keep speaking

* Java→engine: `GUIManager.sendJsonData(id, json)` → `JNIJSONTransport.sendJsonData(id, utf8)` → `Java_…_JNILib_sendJsonData` (exported).
* engine→Java: `Java_…_JNIJSONTransport_onJsonDataIncoming`-equivalent via `JNI_OnLoad` RegisterNatives → `GUIManager.onPacketIncoming(id,json)` → open (`type:1`) / close (`type:2`) / special ids.
* Settings pull/push: `getSerializedSettings()[B` (pull on boot) and `onSettingsJsonDataUpdate([B)` (push, Gson → prefs).
* Persistent blobs the engine asks Java to store (all under `getFilesDir()`): **`state_monitor.sm`** (+`.tmp`), **`patch_index.json`**, **`failure_flag`**.

## 4. Top 30 largest `com.blackhub.bronline` classes (bytes, `find -printf %s`)

| Size | Class | What it is |
|---|---|---|
| 630,052 | `game/theme/TypographyStyle` | full Compose typography tables (generated) |
| 587,962 | `game/ui/marketplace/uiblock/MarketplaceBottomSheetKt` | marketplace bottom-sheet Compose UI |
| 376,214 | `game/ui/holidayevents/HolidayEventsContentKt` | holiday event screens |
| 365,844 | `launcher/di/DaggerApplicationComponent$ApplicationComponentImpl` | generated Dagger graph |
| 297,493 / 293,335 | `game/ui/marketplace/MarketplaceMainUiKt$…lambda$83$…ConstraintLayout$1/$5` | generated Compose layout lambdas |
| 291,117 | `game/common/resources/StringResourceCompose` | localized string table wrappers |
| 259,746 | `game/ui/cases/ui/CasesOpenOneCaseUiKt` | loot-case opening UI |
| 244,432 | `launcher/viewmodel/MainActivityViewModel` | **startup/update orchestration (see above)** |
| 241,446 | `game/gui/holidayevents/HolidayEventsUiState` | UI state model |
| 229,356 | `game/gui/craft/CraftViewModel` | crafting logic |
| 228,853 | `game/gui/donate/GUIDonate` | donate shop |
| 228,754 | `game/gui/cases/CasesUiState` | cases state |
| 224,384 | `game/core/viewmodel/JNIActivityViewModel` | billing/analytics glue for the activity |
| 224,312 / 219,587 / 217,417 | `game/ui/calendar/CalendarMainUiKt$…` | calendar screens |
| 216,550 | `game/gui/electric/view/FindProblemView` | electric minigame custom view |
| 215,066 | `game/ui/blackpass/BlackPassSplitActivatePremiumKt` | battle-pass premium UI |
| 212,679 | `game/ui/marketplace/MarketplaceMainUiKt` | marketplace |
| 211,767 | `game/gui/electric/ui/FindProblemFragment` | electric minigame |
| 204,251 | `game/ui/craft/uiblock/CraftAnimButtonsBlockKt` | craft buttons |
| 202,121 / 201,611 / 196,350 | `game/ui/craft/CraftMainUiKt$…` + main | crafting screens |
| 201,611 | `game/gui/inventory/UILayoutExchange` | inventory exchange |
| 198,536 / 198,084 | `game/ui/cases/CasesGuiKt` / `game/ui/taxiorder/TaxiOrderMainKt` | cases / taxi order |
| 194,570 | `game/ui/plates/PlatesGuiKt` | license plates |
| 192,384 | `game/ui/gifts/GiftsPurchaseUiKt` | gifts purchase |
| 190,255 | `game/gui/blackpass/BlackPassMainUIState` | battle pass state |
| 190,022 | `game/ui/upgradeobjectevent/UpgradeObjectEventMainKt$…` | object upgrade event |

**Reading:** the largest Java code is *gameplay chrome* (marketplace, cases, craft, blackpass, calendar) — none of it is needed for the A→Z launcher flow; the launcher-critical classes are the *small* ones (`WebAppInterface` 218 lines, `Preferences`, `JNILib`, `AuthViewModel`, `SettingsHelper$Companion`).

## 5. Update/download system (reference contract)

1. **Feature flag fetch** (Java, Retrofit): base links + app config + feature flag (`launcher/viewmodel/MainActivityViewModel`: `getBaseLinks`, `getRawAppConfig`, `fetchFeatureFlag` with retry).
2. **Patch index**: `MainActivityViewModel$checkUpdateForUpdateManager$1` builds `jsonHttpData` = JSON `{cdn, backup_cdn, username:"main", password:"DzEI3O4VDpdc6KpcSfd3"}` (BuildConfig CDN_*) + `fileRules` string, then native `JNILib.tryGetPatchIndex(jsonHttpData, fileRules, isEnabledCheckResources, version, candidateVersion, downloadTimeout, connectionTimeout, distributionType, useBackupCdn, isDevModUpdateManager, forceCheckResources)` → returns JSON with keys `patch_index_status_key`, `patch_index_size_key`, `patch_index_addition_size_after_apply`, `patch_index_error_key` (`PatchIndexStatusJsonKeys`; defaults: connection timeout 0x3a98=15 000 ms, download timeout 0x124f80=1 200 000 ms, speed limit 0x80000, delimiter 0x100000, confirm-size 0x19000).
3. **Persistence of patch state**: engine→Java callbacks `storePatchIndexBytes/loadPatchIndexBytes/removePatchIndex` (`patch_index.json` in `getFilesDir()`), `storeStateBytes/loadStateBytes/removeState/isExistState` (`state_monitor.sm` — resume marker), `storeFailureFlag/isExistFailureFlag/removeFailureFlag` (`failure_flag` — interrupted-update marker). Native side: `UpdateManagerSystemCallbacks::SetPatchIndexSavedCallback/SetStateMonitorSavedCallback/…` (imported by client .so from libupdate-manager.so).
4. **Download execution**: WorkManager `UpdateManagerWorker` (initial dataSync run) → background thread → `JNILib.tryDownloadResources(7 args)` or `tryDownloadNextSlot(7 args)`; slot-based resume; progress flows engine→Java via `UICallback` → `GUIManager.setUpdateManagerCallbackData` → `UpdateManagerFragment`/`LoaderFragment` UI; Java→engine progress via `LauncherHelper.sendDownloadProgress/Started/Complete` as message **0x54** JSON `{phase, Current, Target, Speed, Phase, UpdateInfo:[{name:"content",size}]}`.
5. **Rollback/backup**: native `BackupSystem`, `RollbackSystem`, `ClearBackupSystem`, `PatchFilesSystem{,2,3}` (symbols in libupdate-manager.so); Room `MyFiles` table tracks per-file state in Java for cleanup (`deleteUnusedEntriesFromDB`).
6. **Diff/patch format**: file-level diff via patch-index entries (path, crc `crc_xxhashct` BR-hash, filesize, link, `rule_file`) — no binary deltas; "patch" = re-download changed files, with slots to bound disk usage.

Grand Horizon replaces steps 2-4 with pure Java (`UpdateController`: patch_index.json diff vs local size/hash + GitHub release CDN + HTTP range resume) — contract-equivalent, no native code.

## 6. What is deliberately NOT mirrored

* Analytics/attribution stack (Adjust/AppMetrica/Firebase ids) — GHRP privacy stance.
* Crashlytics/Firestore error upload — GHRP logs locally only.
* Helpshift support bridge, FCM push, billing (Google + Rustore + VK), YouTube player, Lottie charts, Samsung SDK.
* All 60+ gameplay GUI packages — M2+ backlog (see `GRAND_HORIZON_ENGINE_GAP_ANALYSIS.md`).

These are **excluded by scope**, not by oversight; each maps to a native screen family documented in doc 3.
