#!/usr/bin/env python3
"""Build the Grand Horizon RP patch_index.json for the native update system 2.0.

- Uses the ORIGINAL Black Russia patch_index.json file entries (crc_xxhashct + filesize + path + rule_file)
  for the 159 files that exist on our gamedata release (all byte-identical, sizes verified).
- Rewrites `link` for each file to "<flat-asset-name>#" so that:
      download URL = CDN + link + "/" + path
                  = https://github.com/<owner>/<repo>/releases/download/gamedata/files.mesh.br_map_00.bpc#/mesh/br_map_00.bpc
  The URL fragment (#...) is stripped by libcurl, so GitHub serves the flat release asset.  :-)
- paths list kept EXACTLY as original (local layout = gamePath + "/" + path).
- patches: []  (fresh install => full download from "files"; afterwards date marker => skip)
"""
import json, urllib.request

ORIG = "/home/z/ghrp-work/orig_patch_index.json"
RELEASE = "/home/z/ghrp-work/gd_release.json"
OUT = "/home/z/ghrp-work/patch_index.json"

orig = json.load(open(ORIG))
rel = json.load(open(RELEASE))
ours = {a["name"]: a for a in rel["assets"] if a["name"].startswith("files.")}

files = []
total = 0
skipped = []
for f in orig["files"]:
    p = f["path"]
    asset = "files." + p.replace("/", ".")
    if asset not in ours:
        skipped.append(p)
        continue
    if ours[asset]["size"] != f["filesize"]:
        raise SystemExit(f"size mismatch for {p}: orig {f['filesize']} vs ours {ours[asset]['size']}")
    files.append({
        "crc_xxhashct": f["crc_xxhashct"],
        "filesize": f["filesize"],
        "link": asset + "#",
        "path": p,
        "rule_file": f.get("rule_file", "base"),
    })
    total += f["filesize"]

doc = {
    "android_version": 1529,
    "filesize": total,
    "link": "ghrp-v1",
    "hash_commit": "ghrp-v1-20260919",
    "forced_download_ios": False,
    "forced_download_market": False,
    "forced_download_pc": False,
    "forced_download_rustore": False,
    "forced_download_site": False,
    "ios_version": 0,
    "files": files,
    "patches": [],
}

json.dump(doc, open(OUT, "w"), indent=1)
print(f"files: {len(files)}  total payload: {total:,} bytes = {total/1e9:.3f} GB")
print(f"skipped (not on our CDN): {len(skipped)}")
for s in skipped[:10]:
    print("  -", s)
# sanity: verify every link asset really exists on release
missing = [f["path"] for f in files if f["link"][:-1] not in ours]
print("missing assets:", missing)
