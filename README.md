# Grand Horizon RP — Launcher & Game Data Infrastructure

This repository hosts **everything the Grand Horizon RP mobile launcher needs to run**:
the launcher APK (release asset), the client API (JSON configs), and the complete game
data CDN (individual files served to the launcher's update system).

> Game: **Grand Horizon RP** (SA-MP based mobile RP) · Server: `142.132.203.47:14448`
> City: **Horizon City** · OCGs: **Ironside / Blackwood / Harbor** · Bank: **Horizon City Bank**

## 📱 Install (players)

1. Download the launcher APK:
   https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/GrandHorizonRP-Launcher.apk
2. Install it (allow unknown sources).
3. Open **Grand Horizon RP** → the launcher downloads ~3.8 GB of game data into
   `Android/data/com.br.top/files/` → play.

## 🏗 Architecture

```
Launcher APK (com.br.top, Black Russia client base, rebranded)
 ├── API  → https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@main/client-api/
 │    ├── servers.json          → server list (IP/port/name) the client connects to
 │    ├── url-config.json       → CDN URL + social/donate links
 │    ├── hash.json             → manifest of all 159 game files (size + version marker)
 │    ├── app-config.json       → hides SIM/Tanpin buttons, no forced updates
 │    ├── update_manager_feature_flag.json → forces "hash_json" update system 1.0
 │    └── *.json                → all in-game GUI configs (rebranded)
 │
 └── CDN  → https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/gamedata/
      └── 159 flat assets: `files/mesh/X.bpc` → `files.mesh.X.bpc`
          (DownloadWorker URL patch replaces "/" with "." in the file path)
```

### Update system
The launcher uses the **hash.json (update system 1.0)** flow:
`GET {CDN}{path}{name}` → saved to `Android/data/com.br.top/files/{path}{name}`.
A file is re-downloaded only when its size differs from `hash.json` or the version
marker changes.

## 📁 Repository layout

| Path | Purpose |
|---|---|
| `client-api/` | The launcher/client API — served via jsDelivr (5–12 min CDN cache) |
| `launcher-patches/` | The 4 modified files of the decompiled launcher + rebuild notes |
| `keystore/ghrp-release.keystore` | Release signing key (alias `ghrp`, pass `GrandHorizon2026`) |

## 🔧 Rebuilding the launcher APK

```bash
# 1. Decompile the original launcher (apktool 2.10+, converts apktool.json → apktool.yml)
java -jar apktool.jar d launcher.apk -o launcher_src

# 2. Apply the patches from launcher-patches/ (see files for exact locations):
#    - Settings.smali        : API URLs → jsDelivr client-api
#    - DownloadWorker$downloadFile$2.smali : URL path "/"→"." (flat release assets)
#    - res/values/strings.xml + values-pt: BLACK RUSSIA → Grand Horizon RP

# 3. Build + sign
java -jar apktool.jar b launcher_src -o out.apk --use-aapt2
java -jar uber-apk-signer.jar -a out.apk --ks keystore/ghrp-release.keystore \
     --ksAlias ghrp --ksKeyPass GrandHorizon2026 --ksPass GrandHorizon2026
```

## 🎮 Server package

The ready-to-upload server zip (gamemode with Grand Horizon RP branding, Horizon City
renames, recompiled `laird.amx`, 63-table SQL) is built separately as `server.zip`.
MySQL credentials live in `scriptfiles/sile_mysql_settings.ini` on the game server.

## 🔄 Updating game data later

1. Modify/add files, upload them to the `gamedata` release (name = path with `/`→`.`).
2. Update `client-api/hash.json` (size + `date` marker), commit to `main`.
3. Purge jsDelivr: `curl https://purge.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@main/client-api/hash.json`
