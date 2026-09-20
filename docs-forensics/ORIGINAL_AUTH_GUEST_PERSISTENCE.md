# ORIGINAL AUTH & GUEST PERSISTENCE (Forensic Audit, Task 25-B)

> **THE key document**: how the original Black Russia launcher keeps a user logged in
> across app restarts, how the WebView hands the token to the native engine, and how
> guest accounts work. Evidence = smali (with file + line anchors) + native `.so` strings.
> Reference only. All paths relative to `orig-decode/launcher_src/`.

---

## 0. Executive summary (the one-paragraph version)

The **native engine owns the session**, and the **Java side is its disk**. After the SSO
WebView produces a token, it is *not* stored by any Java auth code — it is shipped to the
engine as **message 0x58** (`GUIManager.sendJsonData(0x58, json)`). The engine validates it
against the SSO backend (`Auth2Model`), and whenever the engine wants the login to survive
restarts it calls back `JNIJSONTransport.onSettingsJsonDataUpdate(json)` whose
`authAccessToken`/`authRefreshToken`/`authFlowType`/`playerName`/`playerCharacter`/`lastServer`
fields `UtilsKt.saveSettingsInPreferences` writes into the **`preferences` SharedPreferences file**.
On the next cold start the engine pulls everything back with a single JNI call:
`JNIJSONTransport.getSerializedSettings()[B` (built by `SettingsHelper$Companion` from those same
prefs) and re-validates via `Auth2Model::InitiateAccessTokenCheck` / `InitiateRefreshTokenCheck`.
Guests are a *first-class account type in the engine* (`Gui::GuestAccount`, `EAccountState` =
`None/Guest/Registered`, `GuestLevelCap`, `GuestCharacterViewPolicy`, `GuestServerViewPolicy`,
`allow_guest_auth` flag) — created server-side by the SSO page, persisted exactly like password
accounts, and re-authenticated on restart through the same stored tokens.

---

## 1. The storage (what is on disk)

### 1.1 SharedPreferences file `preferences` (app-private)

Class: `smali_classes4/com/blackhub/bronline/game/core/preferences/Preferences.smali`
(static helper, file name constant `NAME = "preferences"`, line ~46 `const-string "preferences"`).

Auth/session-relevant keys declared in the same file (static fields):

| Key constant | Value | Type | Written by | Read by |
|---|---|---|---|---|
| `AUTH_ACCESS_TOKEN` | `"AUTH_ACCESS_TOKEN"` | String | `UtilsKt.saveSettingsInPreferences` (engine push) | `SettingsHelper$Companion.getSerializedSettings` (engine pull) |
| `AUTH_REFRESH_TOKEN` | `"AUTH_REFRESH_TOKEN"` | String | same | same |
| `AUTH_FLOW_TYPE` | `"AUTH_FLOW_TYPE"` | int | same | same |
| `AUTH_TOKEN_KEY` | `"auth_token"` | String | (legacy/unused in this build — only declared) | — |
| `USER_ACCOUNT_ID` | `"USER_ACCOUNT_ID"` | int | `GUIManager.onPacketIncoming` case 0x64 (profile id after login, GUIManager.smali:3774-3776) | billing (`JNIActivityViewModel` line 3495/4247), `UtilsKt` helpers |
| `USER_SERVER_ID` | `"USER_SERVER_ID"` | int | engine push (`lastServer`) | engine pull (`playerServerId`) |
| `PLAYERS_CHARACTER` | `"players_character"` | int | engine push (`playerCharacter`) | engine pull (`playerCharacterId`) |
| `PLAYERS_NICK` | `"players_nick"` | String | engine push (`playerName`) | engine pull (`playerName`) |
| `EMAIL` | `"EMAIL"` | String | (declared; used by helpshift metadata) | HelpshiftManager |
| `PRIVACY` | `"PRIVACY"` | int | policy accept | engine pull (`isPolicyAccepted`) |
| `AB_INSTALLATION_ID` | `"AB_INSTALLATION_ID"` | String | AB test | engine pull |
| `IS_NOT_FIRST_LAUNCH_KEY` | `"IS_NOT_FIRST_LAUNCH"` | bool | first-run logic | startup |
| `RESOURCES_VERSION` | `"RESOURCES_VERSION"` | String | update system (`setVersionResources`) | update checks |
| `COMPATIBLE_CLIENT_VERSION` | int | update system | server-compat gate |

### 1.2 Files in `getFilesDir()` (engine-owned, written via Java)

From `smali_classes4/com/blackhub/bronline/game/core/JNIJSONTransport.smali`:

| File | Methods (lines) | Meaning |
|---|---|---|
| `state_monitor.sm` (+ `.tmp`) | `storeStateBytes([B)Z` :3226ff, `loadStateBytes()[B` :1347ff, `removeState()Z` :2511ff, `isExistState()Z` :1157ff | binary blob the engine checkpoints (login/update progress); lets the engine resume mid-states after process death |
| `patch_index.json` | `storePatchIndexBytes([B)Z` :3226ff area, `loadPatchIndexBytes()[B` :1276ff, `removePatchIndex()Z` :2453ff | serialized `PatchIndex` (nlohmann-json, class in libupdate-manager.so) — the download plan |
| `failure_flag` | `storeFailureFlag`, `isExistFailureFlag` :1090ff, `removeFailureFlag` | marks an update that died half-applied → triggers restore/rollback on next start |

### 1.3 Room databases (NOT auth)

`launcher/database/`: `LauncherDatabase` (table `MyFiles(id,name,path,size,data,downloaded)` —
update bookkeeping) and `PurchaseDatabase` (billing analytics). **No credentials, tokens or
account rows live in SQLite.**

> There is no EncryptedSharedPreferences, no Keystore-wrapped token, no account database:
> tokens sit in plain `SharedPreferences("preferences")`, protected only by app sandboxing.

---

## 2. Cold-start restore (exactly who reads what)

```
JNIActivity.onCreate                                     (JNIActivity.smali:3194)
  ├─ JNILib.init(pathToRes, pathToSaves=getFilesDir(),
  │             version=0x5f9, distributionType, buildType)   (:3881)
  │    └─ native engine boot (libblackrussia-client.so)
  │         ├─ JNI call → JNIJSONTransport.getSerializedSettings()[B   (:828)
  │         │    └─ SettingsHelper$Companion.getSerializedSettings()   ($Companion.smali:60-1299)
  │         │         reads prefs: players_nick, players_character,
  │         │         USER_SERVER_ID, AUTH_ACCESS_TOKEN, AUTH_REFRESH_TOKEN,
  │         │         AUTH_FLOW_TYPE, PRIVACY, AB_INSTALLATION_ID
  │         │         (+ all graphics/audio/control settings, urls from BuildConfig)
  │         │         → org.json.JSONObject → UTF-8 bytes
  │         │         keys: playerName, playerCharacterId, playerServerId,
  │         │               authAccessToken, authRefreshToken, authFlowType,
  │         │               isPolicyAccepted, abInstallationId, … apiUrl/apiUserName/
  │         │               apiPassword/apiBackup*/apiUserAgent … ($Companion.smali:1196-1240)
  │         ├─ Auth2Model::InitiateAccessTokenCheck(token)   (RTTI: ZN3Gui10Auth2Model24InitiateAccessTokenCheckE…)
  │         ├─ Auth2Model::InitiateRefreshTokenCheck(token)  (RTTI: ZN3Gui10Auth2Model25InitiateRefreshTokenCheckE…)
  │         │    → SSO backend (CurlHttpProcessor → HttpApi) validates/refreshes
  │         │    → valid  ⇒ "logged in": engine goes to server list (screen flow, doc 3)
  │         │    → invalid⇒ engine opens AUTH screen: sendJsonData-equivalent →
  │         │               GUIManager.onJsonDataIncoming(0x58, {"type":1,"auth_url":…})
  │         └─ Gui::Auth2Model::SessionInit()/SessionConfirm()  (RTTI)
  └─ (UI side meanwhile) showGUI(SCREEN_INITIALIZATION=0x53)   (JNIActivity.smali:1916)
```

Validation evidence (native strings, `lib/armeabi-v7a/libblackrussia-client.so`):

* `"Auth2Model::RequestSessionInit - Client token or front token is empty, ask web team"`
* `"Auth2Model::SessionConfirm - Access token is empty, ask web team"`
* `"Auth2Model::HandleEventReceived: failed to decode base64 body"` / `… parse JSON body`
* RTTI lambdas: `ZN3Gui10Auth2Model11SessionInitEvE3$_0`, `ZN3Gui10Auth2Model14SessionConfirmEvE3$_0`,
  `ZN3Gui10Auth2Model24InitiateAccessTokenCheckEN5eastl10basic_string…`, `ZN3Gui10Auth2Model25InitiateRefreshTokenCheckE…`
* Account model RTTI: `N3Gui8IAccountE` (interface), `N3Gui12GuestAccountE`, `N3Gui15PasswordAccountE`,
  `N3Gui12TokenAccountE`, `N3Gui12AccountModelE`, `N3Gui13EAccountStateE` (boxed enum
  `BlackRussia.EAccountState`), states seen in string table: **`None`, `Guest`, `Registered`**.
* Logout family: `LogoutImpl(LogoutParams)`, `LogoutToLobby(EConfirm,…)`,
  `LogoutFromDevice(…)`, `LogoutFromAllDevices(…)` (RTTI lambdas).

**Java never decides "logged in".** The only Java-side login marker is `USER_ACCOUNT_ID`
(int, written when the engine reports the profile id, message 0x64) and it is used for
billing/analytics — not for gating any screen.

---

## 3. The WebView → native token handoff (message 0x58)

### 3.1 Opening the WebView (engine → Java)

1. Engine emits `onJsonDataIncoming(0x58, bytes)` (native→Java, `JNIJSONTransport.smali:1664`):
   bytes → `JSONObject` → `runOnUiThread(JNIJSONTransport$1)` → `GUIManager.onPacketIncoming(0x58, json)`
   (`JNIJSONTransport$1.smali:62-70`).
2. `onPacketIncoming` sees `json.type == 1` → `openingScreen(0x58, json)` (`GUIManager.smali:3691ff`)
   → `handleFragmentScreen` → `Useful.jsonStringToBundle(json, "json_object")`
   (`GUIManager.smali:1440-1530`) → `emitFragmentChange(0x58, bundle)` → `onFragmentChange`
   packed-switch case **0x58 → `WebViewAuthFragment`** (`GUIManager.smali:3058` switch, table at
   `:3599-3688`, case `:pswitch_1` at `:3064-3070`).

### 3.2 The WebView page (Java → SSO site)

`WebViewAuthFragment.smali`:

* reads `auth_url` from arguments (`:826`, constant in `WebViewAuthFragmentKt.smali:47` `ARG_AUTH_URL_KEY = "auth_url"`),
* builds the URL (`:865-1040`):
  `Uri.parse(auth_url).buildUpon()`
  `.appendQueryParameter("sysinfo","android")` (`:865`)
  `.appendQueryParameter("ttclid", …)` (`:912`, from `AnalyticsTtclidStorageProvider`)
  `.appendQueryParameter("client_id", …)` (`:927`)
  `.appendQueryParameter("appmetrica_device_id", …)` (`:964`)
  `.appendQueryParameter("adjust_id", …)` (`:994`)
  → `oauthWebView.loadUrl(url)` (`:358`),
* injects the JS bridge (`:513-519`):
  `webView.addJavascriptInterface(new WebAppInterface(authViewModel), "Android")`
* default SSO front: `BuildConfig.AUTH_URL = "https://reg-front.blackhub.team/"`
  (`smali/com/blackhub/bronline/BuildConfig.smali:39`).

### 3.3 The JavascriptInterface (page → Java)

`launcher/webview/WebAppInterface.smali` (218 lines, all methods annotated
`@JavascriptInterface`, class injected under the name **`Android`**):

| Method (line) | Parameter | Delegates to | Purpose |
|---|---|---|---|
| `initToken(String jsonString)` (:118) | token JSON from the page | `AuthViewModel.saveTokenAndCloseWebView` | **success handoff** |
| `closeWebview(String errorString)` (:84) | error JSON (base64url `body`) | `AuthViewModel.closeWebView` | failure/cancel handoff (also reaches the engine, see below) |
| `openHelpshift(String jsonString)` (:152) | config JSON | `AuthViewModel.openHelpshift` | support chat inside auth flow |
| `startIdpAuth(String url)` (:186) | external IdP url | `AuthViewModel.openCustomTabs` | Google/VK/etc. login via Custom Tabs |

### 3.4 Java → engine (the actual handoff)

`AuthViewModel.saveTokenAndCloseWebView(String jsonString)`
(`launcher/viewmodel/AuthViewModel.smali:1023-1105`):

```smali
const/16 p1, 0x58
invoke-virtual {v1, p1, v2}, Lcom/blackhub/bronline/game/GUIManager;->sendJsonData(ILorg/json/JSONObject;)V
…
invoke-virtual {v0, v1, p1}, Lcom/blackhub/bronline/game/GUIManager;->closeGUI(Lorg/json/JSONObject;I)V
```

i.e. **exactly two calls**:

1. `GUIManager.sendJsonData(0x58, new JSONObject(jsonString))`
   → `JNIJSONTransport.sendJsonData(0x58, json.toString().getBytes(UTF_8))`
   (`GUIManager.smali:4014-4078`) → native `Java_com_blackhub_bronline_game_core_JNILib_sendJsonData`
   (exported symbol, verified in `nm -D`).
2. `GUIManager.closeGUI(new JSONObject(), 0x58)` — closes the auth screen.

Perfect symmetry: **the screen id 0x58 (88, `SCREEN_AUTH`) is both the WebView screen and the
token message id** — engine opens 0x58 with `{auth_url}`, page answers on 0x58 with `{front_token,…}`.

Failure path (`AuthViewModel.closeWebView`, `:301-440`): builds `{"r":1}` and *also* decodes the
page's base64url `body` field and sends that JSON to the engine **as message 0x58** — so the
engine's `Auth2Model::HandleEventReceived` (base64+JSON decoder — see its assert strings) is the
single consumer of both success and error events.

IDP redirect path (`AuthViewModel.handleAuthRedirect(Intent)`, `:504-655`): manifest deep link
`bhgbrgame://auth` (`AndroidManifest.xml:222-231` + `res/values/strings.xml:95-96`) — if
`uri.host == "auth"`, all query parameters are collected into a `JSONObject` and passed to the
**same** `saveTokenAndCloseWebView` → message 0x58.

### 3.5 What the native side does with the token

Evidence from `libblackrussia-client.so` strings/RTTI (armeabi-v7a):

* `Auth2Model` (source `BRClient/src/gui/screens/Auth2/Auth2Model.cpp`) holds the account:
  * `RequestSessionInit` — asserts *“Client token or front token is empty, ask web team”* →
    it posts the **front_token** (plus a client token) to the SSO to open a session;
  * `SessionConfirm` — asserts *“Access token is empty, ask web team”* → exchanges/confirms the
    session and obtains the **access token**;
  * `InitiateAccessTokenCheck(token)` / `InitiateRefreshTokenCheck(token)` — validity checks
    (this is what runs on cold start with the persisted tokens);
  * `HandleEventReceived` — decodes base64+JSON events (the closeWebview error path);
  * `Link/LinkVerificationAsync/Unlink` (social linking), `InitiatePasswordChange`,
    `ShowAccountBlockedDialog(HttpJsonResponseError)`, `ShowNetworkDialog`,
    `LogoutImpl/LogoutToLobby/LogoutFromDevice/LogoutFromAllDevices`.
* Account objects: `IAccount` + `GuestAccount` / `PasswordAccount` / `TokenAccount`, exposed to
  the Noesis UI as `BlackRussia.Account` with an `EAccountState` (`None|Guest|Registered`) and a
  boolean property (RTTI `TypePropertyFunction<Gui::IAccount, bool>`) — the UI asks "is guest".
* HTTP: own curl stack (`CurlHttpProcessor`, `HttpRequestProcessor`, `HttpApi`,
  `src/network/requests/HttpApi.cpp`) — **the engine does its own REST, independent of Java**.
* JSON field names in native: `access_token`, `refresh_token`, `front_token`, `authFlowType`
  (string table) — matching the `NativeSettingsKeys` DTO and the settings JSON built in Java.

### 3.6 Persistence write-back (engine → Java prefs)

Whenever the engine wants settings (incl. tokens) to persist:

```
native → JNIJSONTransport.onSettingsJsonDataUpdate([B)      (JNIJSONTransport.smali:1753)
  → String(UTF_8) → Gson().fromJson(…, NativeSettingsKeys)  (camelCase DTO)
  → UtilsKt.saveSettingsInPreferences(prefsRepo, keys)      (UtilsKt.smali:3584-4260)
       playerName      → putString("players_nick")
       playerCharacter → putInteger("players_character")
       lastServer      → putInteger("USER_SERVER_ID")
       authAccessToken  → putString("AUTH_ACCESS_TOKEN")    (:4213-4215)
       authRefreshToken → putString("AUTH_REFRESH_TOKEN")   (:4218-4220)
       authFlowType     → putInteger("AUTH_FLOW_TYPE")      (:4223-4225)
       isPolicyAccepted → putInteger("PRIVACY")
       abInstallationId → putString("AB_INSTALLATION_ID")
       (+ graphics/audio/controls/uiLanguage/region…)
```

Delta semantics: every field is written only `if (value != null)` — the engine can update one
field without touching the rest; a field cannot be nulled through this channel (logout writes
empty strings / zeroes, or relies on `Preferences.clear(context,key)` which exists
(`Preferences.smali:90` `clear(Context,String)`) but has no auth call sites in Java).

Profile id: after a successful login the engine sends message **0x64 (100, `TECH_TOOLS` const)**
with `{"id": accountId}` → `GUIManager.onPacketIncoming` case 0x64 stores `USER_ACCOUNT_ID`
(`GUIManager.smali:3757-3776`) and reports the login to analytics — the Java-visible "you are
logged in" breadcrumb.

---

## 4. Guest accounts in the original

### 4.1 Guest is an account class, not a hack

* Engine RTTI: `N3Gui12GuestAccountE` (`Gui::GuestAccount`), sibling of `PasswordAccount` /
  `TokenAccount` under `IAccount`; Noesis type `BlackRussia.GuestAccount`.
* UI policies: `Gui::GuestCharacterViewPolicy` and `Gui::GuestServerViewPolicy` (vs
  `UserCharacterViewPolicy`) — guests see a restricted character/server UI
  (e.g. forced character creation, limited server list).
* `GuestLevelCap` (string) — server-driven level cap for guests.
* `allow_guest_auth` (string) — a config flag (feature flag / server config) gating whether
  guest login is offered at all.
* Donate: Java `gui/donate/network/DonateActionWithJSON.sendDonateIsNotAllowedForGuests()` —
  guests are blocked from purchasing (the engine notifies "not allowed for guests").

### 4.2 Guest creation flow

There is **no guest-account API in the Java launcher** (`rg -i guest` over all blackhub smali
only matches donate-guest strings). Guest creation happens **inside the SSO WebView page**
(`reg-front.blackhub.team`, "Play as guest" style button) — the web backend mints a guest
account and returns the **same token JSON** to `Android.initToken(jsonString)`. The engine then
represents it as `GuestAccount` (`EAccountState = Guest`, `authFlowType` presumably the enum
ordinal — `None/Guest/Registered` strings sit adjacent to the EAccountState RTTI in the string
table). Guest name generation is likewise server-side (web), not in the client binary
(no name-generator strings exist in the .so; `playerName` arrives via the account/profile).

### 4.3 Guest re-auth on restart

Identical to password accounts: the guest's `authAccessToken`/`authRefreshToken`/`authFlowType`
are pushed to prefs by `onSettingsJsonDataUpdate`, and on cold start
`getSerializedSettings()` returns them; `Auth2Model::InitiateAccessTokenCheck/InitiateRefreshTokenCheck`
re-validate. The refresh token is the long-lived credential for both account kinds — exactly the
device re-auth contract Grand Horizon implemented (`guest_secret` password-grant, see
`GUEST_PERSISTENCE_CONTRACT.md`).

### 4.4 Differences vs a registered account

| Aspect | Guest | Registered |
|---|---|---|
| Account class | `Gui::GuestAccount` | `Gui::PasswordAccount` (or `TokenAccount` for IdP) |
| `EAccountState` | `Guest` | `Registered` |
| Character view | `GuestCharacterViewPolicy` | `UserCharacterViewPolicy` |
| Server view | `GuestServerViewPolicy` | full list |
| Level cap | `GuestLevelCap` config | none |
| Donate | blocked (`sendDonateIsNotAllowedForGuests`) | allowed |
| Persistence | same prefs, same token checks | same |

---

## 5. Restart matrix (original behavior, derived from the above)

| Scenario | What happens |
|---|---|
| Cold start with valid tokens | `getSerializedSettings` → access/refresh check passes → engine straight to server list; WebView never shown |
| Cold start with expired access, valid refresh | refresh check → new access token → `onSettingsJsonDataUpdate` re-persists → logged in |
| Cold start with both invalid | engine opens screen 0x58 with fresh `auth_url` → WebView login again |
| Process death mid-update | `state_monitor.sm` + `failure_flag` + `patch_index.json` let the engine resume/rollback (update path, not auth) |
| App data cleared | everything gone (prefs + files are all in app-private storage) → first-launch flow |
| Logout (`LogoutImpl`) | engine clears account; prefs updated via settings push (empty strings); account id remains until next login overwrites |

## 6. Grand Horizon mapping (status)

| Original mechanism | GHRP implementation | Status |
|---|---|---|
| Token JSON via message 0x58 (screen==message id) | `Android.initToken({front_token, guest_secret, account{…}})` → `SessionStore.save` | COMPLETE (contract preserved, richer payload) |
| prefs `AUTH_ACCESS_TOKEN/AUTH_REFRESH_TOKEN/AUTH_FLOW_TYPE` | `session.json` {schema, kind, email, guest_secret, front_token, account_name, account_uuid, saved_at} (atomic tmp+rename, schema gate) | COMPLETE (app-private file instead of prefs — same sandbox guarantee) |
| `getSerializedSettings` on cold start | `SessionStore.load()` in `MainActivity` SESSION_RESTORE | COMPLETE |
| `InitiateAccessTokenCheck/RefreshTokenCheck` | guest → password-grant re-auth with `guest_secret`; user → stored token, then `GET /api/v2/character` 401/403/404 → clear+WebView | COMPLETE (server-authoritative validation) |
| Engine owns session, Java is disk | Java owns session (no native engine auth at M1) — architectural inversion, documented | BY DESIGN (M2 networking will move validation server-side like the original) |
| `USER_ACCOUNT_ID` (message 0x64) | account_uuid in session + server `accounts` table | COMPLETE (different id space) |
| Guest as account class + policies | server `kind` field + `launcher_char_created` marker; guest restrictions via ghrp-auth | COMPLETE at launcher level |
| `state_monitor.sm`/`failure_flag`/`patch_index.json` | UpdateController's local patch-index state + resume | COMPLETE (Java) |

## 7. Evidence index (exact locations)

* `smali_classes4/com/blackhub/bronline/launcher/viewmodel/AuthViewModel.smali`
  :1023 `saveTokenAndCloseWebView` (0x58 send + closeGUI) · :301 `closeWebView` (r:1 + base64 body → 0x58) · :504 `handleAuthRedirect` (deep link → 0x58) · :671 `openCustomTabs`
* `smali_classes4/com/blackhub/bronline/launcher/webview/WebAppInterface.smali`
  :84 `closeWebview` · :118 `initToken` · :152 `openHelpshift` · :186 `startIdpAuth`
* `smali_classes4/com/blackhub/bronline/launcher/fragments/WebViewAuthFragment.smali`
  :513-519 `addJavascriptInterface(…,"Android")` · :826 `auth_url` · :865/:912/:927/:964/:994 query params · :358 `loadUrl`
* `smali_classes4/com/blackhub/bronline/game/GUIManager.smali`
  :3691 `onPacketIncoming` (0x63/0x64/0x65/0x66/0x67/0x54 special ids; type 1/2 open/close) · :3774 `USER_ACCOUNT_ID` write · :4014 `sendJsonData` → `JNIJSONTransport.sendJsonData(I,[B)` · :3014/:3599-3688 `onFragmentChange` packed-switch 0x1..0x58
* `smali_classes4/com/blackhub/bronline/game/core/JNIJSONTransport.smali`
  :1664 `onJsonDataIncoming` · :1753 `onSettingsJsonDataUpdate` · :828 `getSerializedSettings` · :1276 `loadPatchIndexBytes` · :1347 `loadStateBytes` · :1090 `isExistFailureFlag` · :1157 `isExistState` · :3226/:3300 `storePatchIndexBytes`/`storeStateBytes`
* `smali_classes4/com/blackhub/bronline/game/core/SettingsHelper$Companion.smali`
  :60-1299 `getSerializedSettings` (prefs reads :744-796 incl. `AUTH_ACCESS_TOKEN` :772, `AUTH_REFRESH_TOKEN` :781, `AUTH_FLOW_TYPE` :790; JSON build :1196-1240 `playerName/playerCharacterId/playerServerId/authAccessToken/authRefreshToken/authFlowType/isPolicyAccepted/abInstallationId`)
* `smali_classes4/com/blackhub/bronline/game/core/constants/NativeSettingsKeys.smali`
  (Gson DTO field list: `authAccessToken`, `authRefreshToken`, `authFlowType`, `playerName`, `playerCharacter`, `lastServer`, …)
* `smali_classes4/com/blackhub/bronline/game/core/utils/UtilsKt.smali`
  :3584 `saveSettingsInPreferences` (token writes :4213-4225; null-guarded deltas)
* `smali_classes4/com/blackhub/bronline/game/core/preferences/Preferences.smali`
  (key constants; file name "preferences"; `clear(Context,String)` :90)
* `smali/com/blackhub/bronline/BuildConfig.smali` :39 `AUTH_URL = https://reg-front.blackhub.team/`
* `AndroidManifest.xml` :193-231 JNIActivity + deep link; `res/values/strings.xml` :95-96 `bhgbrgame://auth`
* Native (`lib/armeabi-v7a/libblackrussia-client.so`): strings/RTTI
  `Gui::Auth2Model` (SessionInit/SessionConfirm/InitiateAccessTokenCheck/InitiateRefreshTokenCheck/HandleEventReceived/LogoutImpl…),
  `Gui::{I,Guest,Password,Token}Account`, `EAccountState{None,Guest,Registered}`,
  `GuestLevelCap`, `allow_guest_auth`, `access_token/refresh_token/front_token/authFlowType`,
  `CurlHttpProcessor/HttpApi`; exported JNI `Java_com_blackhub_bronline_game_core_JNILib_sendJsonData`.
