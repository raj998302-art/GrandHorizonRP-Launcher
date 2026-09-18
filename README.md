# Grand Horizon RP — Official Android Launcher

Android launcher + game-data downloader for **Grand Horizon RP** — a Hindi
community mobile roleplay server (SA-MP based).

| Item | Value |
|---|---|
| Server | `142.132.203.47:14448` |
| App id | `com.grandhorizonrp.launcher` |
| Data location | `Android/data/com.grandhorizonrp.launcher/files/` |

## What the launcher does
1. **Live server status** — SA-MP UDP query shows online players.
2. **One-tap game data install** — downloads the clean game-data packages from
   this repo's `latest` GitHub release straight into the app's Android data
   folder. SHA-256 verified, resumable, split into packages under GitHub's
   2 GB per-asset limit.
3. **PLAY button** — launches the installed game client with the server
   address pre-filled (copyable as fallback).

## Build the APK (GitHub Actions)
Every push to `main` triggers `.github/workflows/build-apk.yml`:

1. Gradle assembles a **signed release APK** (keystore in `keystore/`)
2. The APK is published to the `latest` release as `GrandHorizonRP-Launcher.apk`
3. Stable download URL:

   `https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/GrandHorizonRP-Launcher.apk`

## Game data packages
Built from the original client dump with `tools/package-gamedata.py`:

| Package | Contents |
|---|---|
| `gamedata-core.zip` | `common.bpc`, `gui.bpc`, `launcher.bpc`, `jsons/` |
| `gamedata-audio.zip` | `audio/` |
| `gamedata-mesh.zip` | `mesh/` |
| `gamedata-textures.zip` | `textures/` |
| `gamedata-resources.zip` | `resources/` (images + videos) |

Unwanted files removed: internal `date_marker_*` markers, unused `jsons/pt-br`
locale.

Re-packaging after a game update:

    python3 tools/package-gamedata.py <path-to-files.zip> dist/ <version>
    # upload dist/gamedata-*.zip to the 'latest' release
    # merge dist/gameData-manifest.json into launcher-config.json
    # commit launcher-config.json (launcher reads it remotely, no APK rebuild needed)

## Runtime configuration
`launcher-config.json` (repo root) is fetched by the app on every start —
change server address, news text, package list or game client packages
**without rebuilding the APK**.

## Signing
Release keystore: `keystore/ghrp-release.keystore`
Alias `ghrp`, store & key password `GrandHorizon2026`.
Keep the same key for every future build so updates install over the old app.
