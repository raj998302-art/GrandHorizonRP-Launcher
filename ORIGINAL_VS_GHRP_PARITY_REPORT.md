# ORIGINAL vs GHRP — PARITY REPORT (Final)

**Session:** 19 Sep 2026 · **Method:** layer-by-layer comparison of the ORIGINAL purchased launcher (`com.br.top` site build v16.102.14498/1529, apktool-M decode) vs `GrandHorizonRP-Launcher-Final.apk`.

## Parity ledger

| Layer | Original | GHRP Final | Parity |
|---|---|---|---|
| Native libraries (26: engine 28.7 MB, update-manager 9.8 MB, signer, bass, crypto/ssl, crashlytics×4, z-ng, graphics.path, datastore) | reference set | **byte-identical (sha256 × 26)** | **100%** |
| DEX classes | 64,524 | **64,524 (1:1)** | **100%** |
| Screen inventory | 78 ScreenId constants (58 fragment routes + 12 legacy dialogs) | **all 78 preserved** — no screen added/removed | **100%** |
| Character creation | NATIVE 0x26; SelectGender/SelectSkin/CreateCharacter/SelectCharacter/SpawnLocation; 767 skins (606 M / 161 F); br_tex_skins | **identical (native path untouched)** | **100%** |
| Auth handoff | WebViewAuthFragment 0x58 → SSO → `initToken({front_token})` → GUIManager(88) | **identical bridge**; SSO = 1:1 reproduction (PASETO v4.public, /api/v2/*) | **100%** (contract) |
| Update/download machinery | libupdate-manager.so + patch_index (159 files, xxhashct) + hash_json path | **identical** (same lib, same index structure, same feature flag) | **100%** |
| Game-data install flow | permissions → update manager → download → verify → native loads | **identical** (flow untouched) | **100%** |
| Server connection | SA-MP protocol → server list → spawn | **identical** (points at GHRP server) | **100%** (behavior) |
| Manifest components | all activities/services/receivers/providers | **all preserved** (authorities renamed to new package — Android requirement) | **100%** |
| versionCode / versionName / minSdk / targetSdk | 1529 / 16.102.14498 / 26 / 36 | **identical** | **100%** |
| Deeplinks | `bhgbrgame://` (resource-ref) | **identical** (resource preserved) | **100%** |
| Resource IDs (public.xml 14,565) | reference | **preserved** | **100%** |

## Intended deltas (the rebrand — 11 patch groups, all in `launcher-patches/v3/`)

| # | Delta | Reason |
|---|---|---|
| 1 | Package `com.br.top` → `com.grandhorizonrp.launcher` (+6 authorities, +1 permission) | user-mandated rebrand; internal `com.blackhub.bronline.*` identifiers PRESERVED (JNI symbols depend on them) |
| 2 | BuildConfig.APPLICATION_ID + 5 `…/files` path constants | data-dir follows package (Android requirement) |
| 3 | Settings API base ×3 → jsDelivr **pinned commit** `0a76d48b…`/client-api/ | GHRP config source (deterministic — @main resolution-lag immunity) |
| 4 | StoreUpdateHelper market/play links → GitHub release | GHRP distribution channel |
| 5 | DownloadWorker `'/'→'.'` URL flatten | release-asset naming (`files.jsons.en.client-jsons.zip`) |
| 6 | Branding tokens (BLACK RUSSIA → GRAND HORIZON RP) in all locales | rebrand |
| 7 | strings.xml default = FULL ENGLISH (1,429 strings; official-EN-based) | "full English player-facing" directive |
| 8 | `initLanguageOnStartup` {ru,pt} → {ru,pt,en}, default **en**; `getLocalizedResourcePath` en→`en/` | English-first product (engine has built-in en localization + gamedata ships en jsons) |
| 9 | BrSimBanner fallback URLs → GitHub | rebrand |
| 10 | client-api (CDN): 53 files FULL EN (13,902 strings) + Horizon City alignment | English + server-name consistency |
| 11 | gamedata `files.jsons.en.client-jsons.zip`: Yuzhny→Horizon City ×10 (+hash/patch_index size sync) | consistency with server-established city names |

## Runtime state graph comparison (launcher startup → game)

Every state transition in `ORIGINAL_RUNTIME_FLOW.md` §A–Z maps 1:1 in the final build:
startup providers → App.onCreate → JNIActivity → JNILib.init(1529) → native init → 0x53 Initialization → url-config (pinned CDN) → app-config (1529) → permissions → update manager (hash_json) → 0x56 → 0x55 MainFragment → play → 0x58 WebView (ghrp-auth) → initToken → native auth → **0x26 character creation (native, 767 skins)** → name → confirm → spawn → world → HUD → SA-MP connect (142.132.203.47:14448) — with original error/rollback/reconnect paths intact.

**Missing states/screens/events/APIs/assets: NONE.** The only behavioral differences are the 11 intended rebrand deltas above and the language default (en instead of ru).

## Assets

- All 26 native libs, all res/ IDs, assets/, kotlin/ — byte-identical.
- Gamedata: served from the GHRP GitHub release (original file set; en jsons patched for Horizon City + full-EN client-api on CDN).
- The 66 drawable-nodpi images with baked RU text flagged in the audit: unchanged (device-verified visual risk is low — most are covered by en locale resources; flagged honestly as the one known cosmetic gap if any RU raster appears on a screen).
