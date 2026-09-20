# ORIGINAL CHARACTER SELECTION FLOW (Forensic Audit, Task 25-B)

> Traces auth-success → character selection/creation → server selection → spawn → game start,
> from smali + `files.gui.bpc` (game-data UI pack) + native `.so` RTTI/strings.
> **Extends** `repo/ORIGINAL_SCREEN_STATE_MACHINE.md` (which established the 88-screen
> packed-switch and WebView=auth-only). This document VERIFIES that work and adds:
> the complete id→fragment table, the canonical `ScreenId` constants, the *native* message-id
> space from `files.gui.bpc`, and the character-flow VM/command evidence. Reference only.

---

## 1. Verification of prior findings (all confirmed)

* `GUIManager.onFragmentChange(I,Bundle)` packed-switch spans **0x1..0x58 = 88 ids**
  (`GUIManager.smali:3058`, table `:3599-3688`). Confirmed by programmatic extraction.
* `WebViewAuthFragment` = **0x58**; `UpdateManagerFragment` = **0x56**; `MainFragment` = **0x55**;
  `LoaderFragment` = **0x54**; `InitializationFragment` = **0x53**. Confirmed (case labels below).
* WebView is auth-only. Confirmed: the only Java sender of message 0x58 is `AuthViewModel`
  (`saveTokenAndCloseWebView` / `closeWebView` / `handleAuthRedirect`); no other screen id
  touches a WebView.
* Character selection/creation and server selection have **NO Java fragments** — they are
  native NoesisGUI screens (see §4). The Java `game/gui/chooseserver/` package contains only
  `model/YoutuberAcc.smali` (server list decoration for promoted "youtuber" servers).

## 2. The complete 88-screen table (id → Java fragment; extracted from the packed-switch)

`GUIManager.smali` `onFragmentChange` — extracted by pairing the packed-switch table with the
`const-class` targets. "(native)" = no Java fragment; UI lives in the engine.

| id | Fragment / meaning | id | Fragment / meaning |
|---|---|---|---|
| 0x01 | PlatesGuiFragment (license plates) | 0x2E | GUIFractionSystem |
| 0x02 | GUIFuelFill | 0x2F | (native) |
| 0x03 | SCREEN_DINER (native) | 0x30 | (native) |
| 0x04 | SCREEN_HACK (native) | 0x31 | CraftGuiFragment |
| 0x05 | SCREEN_ROBBERY (native) | **0x32** | **GUISpawnLocation** |
| 0x06 | RentGuiFragment | 0x33 | (SCREEN_LINKS, native) |
| 0x07 | SCREEN_WIRES (native) | 0x34 | GUISocialNetworkLink |
| 0x08 | SCREEN_PIPES (native) | 0x35 | (native) |
| 0x09 | SCREEN_AUDIO (native) | 0x36 | (native) |
| 0x0A | SCREEN_WINDOW_SAMP (native SAMP dialog) | 0x37 | (native) |
| 0x0B | (native; SelectServer close msg, §3) | 0x38 | ElectricGuiFragment |
| 0x0C | (native) | 0x39 | CatchStreamerGUIFragment |
| 0x0D | GUINotificationNewStyle | 0x3A | GUIGasmanGame |
| 0x0E | MenuComposeGUIFragment | 0x3B | FishingGUIFragment |
| 0x0F | SCREEN_DANCE (native) | 0x3C | (native) |
| 0x10 | TaxiFragment | 0x3D | YotubePlayerFragment |
| 0x11 | TaxiOrderFragment | 0x3E | (native) |
| 0x12 | TaxiRatingFragment | 0x3F | InteractionWithNpcGUIFragment |
| 0x13 | (native) | 0x40 | HalloweenAwardGuiFragment |
| 0x14 | (native) | 0x41 | ActiveTaskGuiFragment |
| 0x15 | TaxiMapFragment | 0x42 | AdminToolsGuiFragment |
| 0x16 | GUIDonate | 0x43 | BrSimBannerComposeGUIFragment |
| 0x17 | SCREEN_SAWMILL (native) | 0x44 | UpgradeObjectEventGuiFragment |
| 0x18 | GUISmiEditor | 0x45 | GiftsGuiFragment |
| 0x19 | GUIPlayersList | 0x46 | PanelInfoGuiFragment |
| 0x1A | SCREEN_NEW_CAPTCHA (native) | 0x47 | CalendarGUIFragment |
| 0x1B | GUIRadialMenuForCar | 0x48 | RateAppComposeGUIFragment |
| 0x1C | GUITuning | 0x49 | CasesGUIFragment |
| 0x1D | (native) | 0x4A | BpRewardsGuiFragment |
| 0x1E | HolidayEventsGuiFragment | 0x4B | TanpinBannerGuiFragment |
| 0x1F | SCREEN_MINI_GAMES_HELPER (native) | 0x4C | VideoPlayerGuiFragment |
| 0x20 | (native) | 0x4D | MarketplaceGuiFragment |
| 0x21 | GUIUsersInventory | 0x4E | (native) |
| 0x22 | GUICarsTrunkOrCloset | 0x4F | ClickerGuiFragment |
| 0x23 | BlackPassBannerComposeGUIFragment | 0x50 | ChatGuiFragment |
| 0x24 | GUISocialInteraction | 0x51 | ModuleDialogGuiFragment |
| 0x25 | GUIDrivingSchool | 0x52 | RatingGuiFragment |
| **0x26** | **SCREEN_REGISTRATION (native)** | **0x53** | **InitializationFragment** |
| 0x27 | TutorialGuiFragment | **0x54** | **LoaderFragment** (update gate + dialogs) |
| 0x28 | GUIWoundSystem | **0x55** | **MainFragment** (lobby) |
| 0x29 | SCREEN_VIP_ACCOUNT (native) | **0x56** | **UpdateManagerFragment** |
| 0x2A | GUIEntertainmentSystem | 0x57 | (native; tutorial msgs ×16, §3) |
| 0x2B | GUIEntertainmentSystemFinalWindow | **0x58** | **WebViewAuthFragment (AUTH)** |
| 0x2C | (native) | 0x63 | INIT_ANALYTICS (message, not screen) |
| 0x2D | GUIFamilySystem | 0x64 | TECH_TOOLS profile-id message (§3) |

## 3. Canonical `ScreenId` constants + the *native* message-id space

### 3.1 Java constants (`game/core/constants/ScreenId.smali`, `.field` list)

Beyond the SCREEN_* table above: `COMMAND_AUTH_ID = 0xca (202)`, `INIT_ANALYTICS = 0x63`,
`INCOMPATIBLE_VERSION_ERROR = 0x67`, `UPDATE_MANAGER_UI = 0x65`, `UPDATE_MANAGER_ERROR = 0x66`.

### 3.2 Engine-side messages found in `files.gui.bpc` (978-entry UI pack; SendJson processors)

Each `.json/…` file is a UI→engine message script: `{"processor":"SendJson","screenId":N,"data":{…}}`
or `{"processor":"ShowGui","uri":"…xaml"}` or `{"processor":"ShowMenu","screens":[…]}`.
Census of all 108 SendJson ids (this is the *engine* message space, overlapping but not equal
to the Java fragment ids):

| id (hex) | used by (examples) | id (hex) | used by (examples) |
|---|---|---|---|
| 0x02 | GasStation/Open | 0x4A | BpRewards/Close |
| 0x0A | DialogWindow/Close ×7 | 0x54 | Launcher/Policy `{"o":1,"t":2}` |
| 0x0B | **SelectServer Open/Close** `{"o":1}` / `{"c":1}` | 0x57 | Tutorial ×16 |
| 0x1B | RadialMenu/Open | **0x58** | **SelectCharacter/BanDialog `{"BanDialog":1}`** |
| 0x1F | MiniGameHelpers/Fingerprint | 0x6C–0x7E | Gallery, Map(0x6F), Radio(0x70), Tablet(0x71), Subtitles, NewsHub, BossFight, PortJob, Leaderboard, Rally, FuelPouring, ValveRotation |
| 0x21/0x22 | Inventory Close | **0xCA** | **SelectCharacter Select/Close** `{"o":1,"l":1}` / `{"c":1}` |
| 0x26 | Registration ×7 (e.g. Auth2Required `{"o":1,"r":2}`, SelectGender `{"t":3,"cheats":1,"lk":[0,0]}`) | | |
| 0x32 | **SpawnLocation/AvailableLocations** `{"m":[0,1,2,3]}` | | |
| 0x41 | ActiveTask/Close; **Guest/GuestServerWarn** (see §5) | | |

Key readings: **COMMAND_AUTH_ID (202/0xCA) is the auth/character-selection command channel** —
the SelectCharacter screen talks to the engine on 202, not on a fragment id; `SelectServer` uses
native id 11 (0x0B); the ban dialog reuses 0x58; `SpawnLocation` selections use 0x32 with a
location mask `{"m":[…]}`.

## 4. The character selection / creation flow (end-to-end)

### 4.1 After auth success (engine side)

`Gui::Auth2Model` completes (`Auth2CompleteEvent` — RTTI `N3Gui18Auth2CompleteEventE`,
`util::Callback/Delegate<RKN3Gui18Auth2CompleteEvent>` consumers) → the engine shows the
**SelectCharacter** Noesis screen (XAML `SelectCharacter/SelectCharacter.xaml`, 19.7 KB in
`files.gui.bpc`), backed by `Gui::SelectCharacterVM` + `Gui::AccountCharactersVM` +
`Gui::SelectCharacterModel` (source dirs `BRClient/src/gui/screens/SelectCharacter/VM/…`).

### 4.2 Server & character data loading (HTTP from the engine)

* `Gui::SelectServerModel::LoadServersAsync()` — fetches the server list (engine HTTP stack:
  `CurlHttpProcessor`/`HttpApi`; URL from settings `apiUrl` — no hardcoded endpoint strings).
* `Gui::SelectServerModel::SetCurrentServer(ServerItem*)` — user picks a server.
* `Gui::SelectServerModel::RefreshCharactersForCurrentServer()` →
  `ProcessCharactersResponse(const char*)` — fetches the account's characters *for the chosen
  server* and fills `CharactersList`.
* `Gui::SelectCharacterModel::RemoveCharacterAsync(int,int)` /
  `CancelRemoveCharacterAsync(int,int)`; `CreateCharacterVM::{CreateCharacterAsync,RenameCharacterAsync}`
  (assert strings in the .so) — character CRUD is a **service API**, not peer packets.
* All RTTI: `ZN3Gui17SelectServerModel16LoadServersAsyncEv…`, `…SetCurrentServer…`,
  `…RefreshCharactersForCurrentServer…`, `…ProcessCharactersResponse…`,
  `ZN3Gui20SelectCharacterModel20RemoveCharacterAsyncEii…`, `N3Gui13CharacterItemE`,
  `N3Gui19AccountCharactersVME`, `ObservableCollectionImproved<CharacterItem>`.

### 4.3 The screens and their UI commands (from `files.gui.bpc` XAML bindings)

**SelectCharacter.xaml** (`SelectCharacterVM`):
`Account.Id`, `Account.Nick`, `Account.State`, `CharactersCount`, `CharactersList`,
`CurrentCharacter.{Balance,Family,Level}`, `CurrentServer.{IsAvailable,Logo,Name}`,
`CurrentNotify`, `IsPlayAvailable`, `RestrictionCounter`, `RestrictionTimeLeft`;
commands: **`PlayCmd`**, **`CreateCharacterCmd`**, **`SelectServerCmd`**, **`LogoutCmd`**,
**`CompleteGuestRegistrationCmd`**, `LinkCharacterCmd`, `RefreshCharactersCmd`, `CopyUidCmd`,
`OpenHelpshiftCmd/OpenSettingsCmd/OpenTelegramCmd/OpenVKCmd`.

**CreateCharacter.xaml** (`CreateCharacterVM`):
`CharacterName`, `CharacterSurname` (+`*Error` + `LostFocus`), `CharacterScenario`
(enum `BlackRussia.ECharacterScenario` / `CreateCharacterVM::EScenario`), `ServerName`,
`Account.Nick`, `IsActionEnabled`; commands: **`CreateCharacter`**, **`RenameCharacter`**, `GoBack`.
(CreateCharacterDone assert: "Null servers model" — after creation it re-reads the servers model.)

**AltSelectCharacter.xaml** (`AltRegistrationVM`, for the alternative registration path):
`CharacterName/Surname(+Error)`, `SelectServer`, `Server.{Logo,Name}`, `SignIn`, socials.

**Registration legacy screens** (`Registration/*.xaml`): `SignIn`, `SignUp`, `PasswordRecover`,
`PasswordRecoverCodeAccepted`, `ReferralName`, **`SelectGender`** (`IsFemale/IsMale/SetGenderCommand`),
**`SelectSkin`** (`PrevSkinCommand/NextSkinCommand/SetSkinCommand/HideCommand`),
`AutoLoginHint` — the old in-native registration flow, kept for fallback when
`IsAuth2Enabled` is false (string `IsAuth2Enabled` in .so) or `Registration/Auth2Required.json`
(`{"o":1,"r":2}`) redirects to the WebView flow.

**SelectServer.xaml** (`SelectServerVM`): `ServersList`, `CharactersList`, `ServersFilter`,
`IsServersFilteredEmpty`, per-item `Id/Name/Status/Color/Online{Current,Capacity,Progress}`;
commands `GoBack` (+ per-server select).

**SpawnLocation.xaml**: `Locations`, `SelectedLocationIndex`, `Name`, `Index`, `Number`,
`OnEnterClicked` — spawn point choice after Play.

### 4.4 3D character preview

`BRClient/src/core/CharacterSelectSceneController.cpp` (path string in .so) renders the
character in-engine during selection/creation; assert strings:
`"Json %s is not a valid config for CharacterSelectSceneController"` (the engine receives a
JSON config for the scene) and `"CharacterSelectSceneController::SwitchPedSkin: Scene is not
setup"` (skin switching = `SwitchPedSkin`). This is exactly GHRP's `GHEngine` character-preview
scene (`cpp/gh/game/GHEngine.cpp` createScene + skin carousel).

### 4.5 Transition to game start

1. `PlayCmd` → `SelectServerModel::PlayServerAsync(ServerItem*)` (RTTI) — engine connects to
   the chosen game server (SA-MP 0.3.7-R2 RakNet handshake — see worklog 23-A/B and
   `SERVER_CLIENT_PROTOCOL` research; the client .so contains both `RakPeerInterface`/
   `RakNet::BitStream` RTTI and 70 exported `enet_*` symbols).
2. Guest accounts get **GuestServerWarn** before connect:
   `.json/Guest/GuestServerWarn.json` → `SendJson screenId 65 (0x41)`,
   `{"o":1,"t":2,"h":"Внимание!","s":"Вы используете гостевой аккаунт. Ваши данные могут быть утеряны. Рекомендуем вам зарегистрироваться.","b":"Регистрация","bc":"0x00AA00"}`
   ("You are using a guest account. Your data may be lost. We recommend registering." with a
   "Регистрация" (Register) button).
3. On join, character/spawn selection → `SpawnLocation` screen (Java fragment **0x32**
   `GUISpawnLocation` for Android chrome + native XAML; engine message `{"m":[locations]}` on id 0x32).
4. Spawn → `JNIJSONTransport.onSpawn()` (:1806) fires `GUIManager.onSpawn()` (analytics +
   `getMarketBillingClientProductsAfterSpawn`), message 0x64 `{id: accountId}` →
   `USER_ACCOUNT_ID` persisted (`GUIManager.smali:3757-3776`). Gameplay HUD/screens take over.

### 4.6 Java-side knowledge of character state

Minimal by design: the engine pushes `playerCharacter` (`players_character` pref) and
`playerName` (`players_nick`) via `onSettingsJsonDataUpdate` — Java only mirrors them for
crashlytics/billing context. Character selection itself is invisible to Java (no fragment,
no bundle) apart from the SpawnLocation (0x32) fragment receiving the location JSON.

## 5. Guest-specific character flow details

* `Gui::GuestCharacterViewPolicy` vs `UserCharacterViewPolicy` (RTTI) — guests are pushed
  through a restricted variant of the character screens (typically: only create/complete
  registration, no multi-character management; `CharactersCount`/`RestrictionCounter` UI hints).
* `CompleteGuestRegistrationCmd` on SelectCharacter.xaml — one-tap "upgrade guest → registered"
  from the character screen (calls into Auth2Model link/registration endpoints).
* `GuestLevelCap` — server config capping guest progression.
* Guest server warning dialog (§4.5-2) before first connect.
* `allow_guest_auth` — feature flag gating the guest button on the auth page.

## 6. GHRP equivalents and status

| Original step | Grand Horizon | Status |
|---|---|---|
| Auth2Complete → SelectCharacter screen | `MainActivity` CHARACTER_CHECK → CHARACTER_READY / CHARACTER_REQUIRED | COMPLETE (launcher-level; native-GUI parity = M2) |
| `SelectServerModel::LoadServersAsync` (engine HTTP) | server list = GHRP server config (`LauncherConfig` + server panel) | COMPLETE (single server; multi-server UI = when we run >1) |
| `ProcessCharactersResponse` (per-server characters) | `GET /api/v2/character` (server-authoritative `accounts.sex/skin` + `launcher_char_created`) | COMPLETE (equivalent contract, REST instead of engine-HTTP) |
| `CreateCharacterVM::CreateCharacterAsync` (name+surname+scenario) | `POST /api/v2/character {sex, skin}` from the 3D preview panel | PARTIAL (GHRP: gender+skin; original also name/surname/scenario — names come from the SSO account in our design) |
| `CharacterSelectSceneController` (3D preview, `SwitchPedSkin`) | `GHEngineView` + `GHEngine::createScene` (skin carousel, drag-rotate, diagnostics) | COMPLETE at M1 (engine preview verified host-side 4/4) |
| `SelectGender`/`SelectSkin` XAML commands | gender toggle + skin carousel in Java panel | COMPLETE |
| `GuestServerWarn` dialog | (not yet shown) | MISSING (small: add a guest notice on Play; 1-line dialog) |
| `CompleteGuestRegistrationCmd` | "CHANGE CHARACTER / LOG OUT" chips + ghrp-auth upgrade path | PARTIAL (guest→registered upgrade flow exists on ghrp-auth; not surfaced as one tap) |
| SpawnLocation screen (0x32 + XAML + `{"m":[…]}`) | — (single spawn; server-side spawn selection via SA-MP SetSpawnInfo) | MISSING at UI level; planned with M2 join flow |
| `PlayServerAsync` → RakNet join | protocol cracked (worklog 23-A/B/24); C++ NetworkManager pending | IN PROGRESS (M2) |
| message 0x64 profile id | account_uuid persisted in session.json | COMPLETE |

## 7. Evidence index

* `smali_classes4/com/blackhub/bronline/game/GUIManager.smali` :3014/:3599-3688 (packed-switch),
  :3691 (onPacketIncoming special ids + type 1/2), :3757-3776 (0x64 → USER_ACCOUNT_ID)
* `smali_classes4/com/blackhub/bronline/game/core/constants/ScreenId.smali` (all constants incl. COMMAND_AUTH_ID=0xca)
* `smali_classes4/com/blackhub/bronline/game/gui/spawnlocation/GUISpawnLocation*` (Java side of 0x32)
* `files.gui.bpc` (gamedata UI pack, 978 entries): `.json/SelectCharacter/{Select,CreateCharacter,Close,BanDialog,LinkCharacter,Link2FA}.json`,
  `.json/SelectServer/{Open,Close}.json`, `.json/SpawnLocation/AvailableLocations.json`,
  `.json/Registration/{Auth2Required,SelectGender,SelectSkin}.json`,
  `.json/Guest/GuestServerWarn.json`; XAML: `SelectCharacter/SelectCharacter.xaml`,
  `SelectCharacter/CreateCharacter.xaml`, `SelectCharacter/AltSelectCharacter.xaml`,
  `SelectServer/SelectServer.xaml`, `SpawnLocation/SpawnLocation.xaml`, `Registration/*.xaml`
  (binding/command extraction in §4.3)
* `lib/armeabi-v7a/libblackrussia-client.so` strings/RTTI: `Gui::{SelectCharacterVM,CreateCharacterVM,
  SelectCharacterModel,SelectServerModel,AccountCharactersVM,CharacterItem,Auth2Model,Auth2CompleteEvent}`,
  `SelectServerModel::{LoadServersAsync,SetCurrentServer,RefreshCharactersForCurrentServer,
  ProcessCharactersResponse,PlayServerAsync,ShowErrorDialog}`,
  `SelectCharacterModel::{RemoveCharacterAsync,CancelRemoveCharacterAsync}`,
  `CreateCharacterVM::{CreateCharacterAsync,RenameCharacterAsync,CreateCharacterDone}`,
  `CharacterSelectSceneController::{SwitchPedSkin}`, `IsAuth2Enabled`, `ECharacterScenario`,
  `Gui::{Guest,User}CharacterViewPolicy`, `GuestServerViewPolicy`, `GuestLevelCap`, `allow_guest_auth`
