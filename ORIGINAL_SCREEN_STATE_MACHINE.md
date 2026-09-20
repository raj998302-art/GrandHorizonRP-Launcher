# ORIGINAL SCREEN STATE MACHINE (Reference) + GHRP Equivalent

> Evidence: `smali_classes4/com/blackhub/bronline/game/GUIManager.smali`
> (`onFragmentChange(I, Bundle)` packed-switch 0x1..0x58) + launcher fragments +
> WebView auth trace (worklog Task 16). Reference only — nothing copied.

## 1. Original flow (evidence-backed)

```
SPLASH (native loader)
  -> INITIALIZATION (InitializationFragment)          [id in switch, launcher phase]
  -> UPDATE (UpdateManagerFragment)                   [game-data patcher, native+UI]
  -> WEB AUTH (WebViewAuthFragment, screenId 0x58)    [ONLY WebView screen]
       engine -> sendJsonData(0x58, {auth_url})
       page  -> Android.initToken({front_token})
       AuthViewModel.saveTokenAndCloseWebView -> message 0x58 back to engine
  -> MAIN / SERVER SELECT (MainFragment + native GUI) [server list, connect]
  -> CHARACTER SELECTION / CREATION (native GUI + native 3D renderer)
  -> SPAWN / GAMEPLAY (native world, 53+ game GUI fragments)
```

The 88 screen ids map (fragments verified in smali): WebViewAuth, UpdateManager,
Main, Loader, Initialization + game screens: RatingGui, ModuleDialog, Chat,
Clicker, Marketplace, VideoPlayer, TanpinBanner, BpRewards, Cases, RateApp,
Calendar, PanelInfo, Gifts, UpgradeObjectEvent, BrSimBanner, AdminTools,
ActiveTask, HalloweenAward, InteractionWithNpc, YouTubePlayer, Fishing,
GasmanGame, CatchStreamer, Electric, SocialNetworkLink, **SpawnLocation**,
Craft, **FractionSystem**, **FamilySystem**, EntertainmentSystem, WoundSystem,
TutorialHints, DrivingSchool, SocialInteraction, BlackPassBanner,
**CarsTrunkOrCloset**, **UsersInventory**, HolidayEvents, **Tuning**,
**RadialMenuForCar**, **PlayersList**, SmiEditor, **Donate**, TaxiMap,
TaxiRating, TaxiOrder, Taxi, **Menu**, Notification, Rent, FuelFill, Plates, …

**Conclusion (architecture proof):** the original uses the WebView **exclusively**
for authentication (screen 0x58). Server selection, character selection, spawn
selection and all gameplay UI are native engine screens. Character rendering,
rotation, camera and confirmation happen in the native renderer.

## 2. GHRP state machine (implemented this session)

States and transitions (all logged with `[state]` prefix in GHRPLog):

```
APP_START
  -> (UpdateController verifies game data)
  -> SESSION_RESTORE            SessionStore.load()
       |-- no session -------------------> AUTHENTICATE (WebView 0x58-equivalent)
       |-- session found:
            guest -> POST /api/v2/auth/token (password grant, guest_secret)
            user  -> reuse stored front_token
            -> CHARACTER_CHECK (GET /api/v2/character, Bearer)
                 |-- 200 has_character=true  -> CHARACTER_READY
                 |-- 200 has_character=false -> CHARACTER_REQUIRED
                 |-- 401/403/404            -> session invalid -> AUTHENTICATE
                 |-- network error          -> retry panel (session kept)
AUTHENTICATE (WebView)
  -> Android.initToken({front_token, guest_secret, account})
  -> SessionStore.save -> CHARACTER_CHECK
CHARACTER_REQUIRED
  -> CREATE CHARACTER (native 3D preview + panel: gender, skin carousel,
     drag-rotate; diagnostics strip shows engine evidence)
  -> POST /api/v2/character {sex, skin}   (server-authoritative save)
  -> CHARACTER_READY
CHARACTER_READY
  -> SERVER SELECT (Java panel; PLAY / CHANGE CHARACTER / LOG OUT)
  -> PLAY STATUS (engine modules + next milestones; M2 connect)
```

Restart matrix (device persistence contract):

| Scenario | Path | Result |
|---|---|---|
| Normal close + reopen | SESSION_RESTORE -> guest re-auth -> CHARACTER_CHECK | character restored, **no re-creation** |
| Force-stop + reopen | same (files flushed on save, atomic rename) | character restored |
| Process death | same | character restored |
| WebView cache cleared | irrelevant (storage = app-private files, not cache) | character restored |
| Log out / reset | SessionStore.clear + server `launcher_char_created` stays 1 until a new character is saved on the next account | creation re-appears |
| Server account deleted | CHARACTER_CHECK 401/404 -> clear -> WebView auth | creation re-appears (correct) |

## 3. Architectural rule (enforced)

```
WEBVIEW  = login / register / OTP / recovery / guest auth + handoff  (ONLY)
NATIVE/JAVA-SIDE (no WebView) = update, server select, character select
                                 (3D native renderer), spawn, gameplay UI
```
Grand Horizon matches the reference architecture. Verified: no character
creation or server selection exists inside any WebView page.
