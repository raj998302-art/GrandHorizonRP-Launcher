# FINAL SERVER INTEGRATION REPORT

**Session:** 19 Sep 2026 · **Directive:** current GitHub `server.zip` = ONLY authoritative baseline (old `work.zip` = reference). Server work LAST, after launcher/client/frontend/backend verified. **Deliverable:** `GrandHorizonRP-Server-Final.zip` (41,903,045 B) — published to release `latest`.

## 1. Baseline identity (verified)

| Item | Value |
|---|---|
| Source | release `latest` asset `server.zip` (md5 `5633a9373271fa4e33a0795247b703b4` — byte-identical local/GitHub) |
| Gamemode | `gamemodes/laird.amx` **47,898,276 B** — the user's current compiled, rebranded (~95% EN) gamemode — **PRESERVED byte-for-byte**; `laird.amx.original-backup` untouched original (rollback) |
| Compile provenance | previous session: self-built 64-bit pawncc (Zeex/pawn 3.10.10); AMX v8 header verified vs original rus.txt (cod=31648 exact match); that toolchain was lost in an env reset — recompiles should re-use the documented method (worklog lines 190–192: `pawncc laird.pwn -o:laird.amx -c:cp/1251.txt -i:../include -i:../pawno/include`) |

## 2. Integration requirements identified & verified

1. **MySQL credentials** — `scriptfiles/sile_mysql_settings.ini` == LemmeHost panel exactly: `142.132.203.47` / `u287152_pmiyBofosL` / `s287152_db1789732442151` (password in ini, redacted here) ✅
2. **Database compatibility** — `reytize_fixed.sql` 63 tables; `accounts` table schema = `name varchar(24), password varchar(65), salt varchar(10), email, level, exp, refer, sex, skin, money, bank…` ✅
3. **Auth contract** — gamemode login: `SHA256_PassHash(input, salt, hash, 65)` (laird.pwn:17750/19328/19360/44842) — **matches ghrp-auth backend account creation** (sha256(password+salt), verified live in L1/L2: registration → in-game-compatible rows, recovery round-trip account_id=5) ✅
4. **Character creation/save** — `accounts.sex`, `accounts.skin` columns + full character tables; driven by the NATIVE character creation → SA-MP protocol (original system, preserved) ✅
5. **Character name persistence** — `accounts.name` (+ duplicate-name handling in gamemode registration/login flow) ✅
6. **Spawn integration** — spawn system (spawnLocation.json client-side names Faction/Station/Last place/Garage/House/Family garage/Family house/Yacht ↔ server spawn logic) — original contract preserved ✅
7. **Server selection/config** — server.cfg: `hostname Grand Horizon RP`, `port 14448`, `gamemode0 laird 1`, `query 1`, `announce 1`, `weburl github.com/…` ✅
8. **GHRP branding** — hostname, `sile_server_settings.ini` nameserver "Grand Horizon RP", tg/vk/site → GitHub; established city/OCG names in dialogs (Horizon City, Ironside/Blackwood/Harbor OCGs; zones: Arzamas/Batyrevo/Lytkarino/Edovo/Garel/Nizhegorodsk/Rogovichi…) ✅
9. **English player-facing strings** — `language English` in server.cfg (was Russian — the ONLY change in this package); gamemode ~95% EN (user's fork state preserved per directive). Remaining RU inventory (casino dialogs ~13, vehicle names 147, ~50 DB values) catalogued in `reports/parts/RUSSIAN_UI_AUDIT_server.md` — intentionally NOT modified: rewriting the compiled gamemode was ruled out to preserve the user's updated build.
10. **Existing updated gamemode preserved** — laird.amx byte-identical ✅

## 3. Final package contents

250 entries: server.cfg (language English) · gamemodes/laird.pwn + laird.amx (+ gui/, modules/, cp/) · include/ + pawno/ (toolchain) · plugins/ (25+ .so/.dll: mysql_static, streamer, pawnraknet, sampvoice, sscanf, pawncmd, profiler…) · scriptfiles/ (sile_mysql_settings.ini = live creds, sile_server_settings.ini, vehicles.json, whitelist) · reytize_fixed.sql · README.md (+ final notes). `laird.amx.original-backup` excluded from the final zip (rollback copy remains in the workspace/GitHub history).

## 4. Deployment (LemmeHost)

1. Upload `GrandHorizonRP-Server-Final.zip` contents to the server home.
2. Import `reytize_fixed.sql` (only if the DB is empty — the live DB already has data/accounts).
3. Start the server from the LemmeHost panel (⚠️ it is currently STOPPED — free-plan inactivity auto-stop; port 14448 refused at report time, MySQL 3306 open).
4. Players: install the launcher APK → game data downloads → ghrp-auth sign-in → native character creation → spawn on 142.132.203.47:14448.

## 5. Honest limits

- Runtime validation of the final package requires the server RUNNING — LemmeHost panel access is user-only; the game port was DOWN (inactivity stop) at integration time.
- ~5% Russian remains in the gamemode (catalogued) — preserving the user's compiled gamemode was prioritized over a risky recompile (the original compiler binary is 32-bit and blocked by sandbox seccomp; the documented 64-bit rebuild path exists if the user wants the last 5% translated).
