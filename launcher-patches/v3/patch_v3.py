#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Grand Horizon RP launcher — v3 patch set (applied on PRISTINE original decode)
Base: /home/z/ghrp-build/launcher_src  (original com.br.top site build v16.102.14498 / 1529)

Patch set:
  1.  Settings.smali: 3 API base URLs -> jsDelivr client-api (repo CDN)
  2.  Manifest: package com.br.top -> com.grandhorizonrp.launcher (+ provider authorities)
  3.  BuildConfig.APPLICATION_ID -> com.grandhorizonrp.launcher
  4.  "com.br.top/files" path constants (4 files)
  5.  StoreUpdateHelper market links -> GitHub release
  6.  DownloadWorker URL path flatten ('/' -> '.')
  7.  Branding tokens in ALL values*/strings.xml (BLACK RUSSIA -> GRAND HORIZON RP)
  8.  FULL ENGLISH: apply 1,428 translations to res/values/strings.xml
  9.  UtilsKt.initLanguageOnStartup: supported set {ru,pt} -> {ru,pt,en}, default ru -> en
  10. UtilsKt.getLocalizedResourcePath: "en" -> "en/" branch
Every step verifies itself. No silent failures.
"""
import re, sys, glob, io

SRC = "/home/z/ghrp-build/launcher_src"
NEWPKG = "com.grandhorizonrp.launcher"
API = "https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@main/client-api/"
GH_REL = "https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/GrandHorizonRP-Launcher.apk"

def rd(p):
    return open(p, encoding="utf-8").read()
def wr(p, s):
    open(p, "w", encoding="utf-8").write(s)

ok = True
def check(cond, msg):
    global ok
    print(("  [OK] " if cond else "  [FAIL] ") + msg)
    if not cond: ok = False

# ---------- 1. Settings.smali API URLs ----------
print("== 1. Settings.smali API base URLs ==")
p = f"{SRC}/smali_classes4/com/blackhub/bronline/launcher/Settings.smali"
s = rd(p)
n = 0
for u in ["https://api.blackrussia.online/client/", "https://api.black-russia.com/client/",
          "https://api-backup111.blackrussia.online/client/"]:
    for form in ['const-string v%d, "%s"', 'const-string/jumbo v%d, "%s"']:
        for reg in range(0, 16):
            old = form % (reg, u)
            if old in s:
                s = s.replace(old, form % (reg, API))
                n += 1
wr(p, s)
check(n == 3, f"replaced {n}/3 URL constants")
check("blackrussia.online" not in s and "black-russia.com" not in s, "no old API URLs remain")

# ---------- 2. Manifest package rename ----------
print("== 2. Manifest package rename ==")
p = f"{SRC}/AndroidManifest.xml"
s = rd(p)
repl = [
    ('package="com.br.top"', f'package="{NEWPKG}"'),
    ('android:name="com.br.top.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"',
     f'android:name="{NEWPKG}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"'),
    ('com.br.top.provider.HelpshiftContentProvider', f'{NEWPKG}.provider.HelpshiftContentProvider'),
    ('com.br.top.appmetrica.preloadinfo.retail', f'{NEWPKG}.appmetrica.preloadinfo.retail'),
    ('com.br.top.androidx-startup', f'{NEWPKG}.androidx-startup'),
    ('com.br.top.adjust-lifecycle-provider', f'{NEWPKG}.adjust-lifecycle-provider'),
    ('com.br.top.firebaseinitprovider', f'{NEWPKG}.firebaseinitprovider'),
    ('android:authorities="com.br.top.provider"', f'android:authorities="{NEWPKG}.provider"'),
]
cnt = 0
for old, new in repl:
    if old in s:
        s = s.replace(old, new); cnt += 1
wr(p, s)
check(cnt == len(repl), f"manifest: applied {cnt}/{len(repl)} renames")
leftover = s.count("com.br.top")
check(leftover == 0, f"manifest has {leftover} leftover com.br.top refs")

# ---------- 3. BuildConfig.APPLICATION_ID ----------
print("== 3. BuildConfig.APPLICATION_ID ==")
p = f"{SRC}/smali/com/blackhub/bronline/BuildConfig.smali"
s = rd(p)
old = '.field public static final APPLICATION_ID:Ljava/lang/String; = "com.br.top"'
if old in s:
    s = s.replace(old, f'.field public static final APPLICATION_ID:Ljava/lang/String; = "{NEWPKG}"')
    wr(p, s)
    check(True, "APPLICATION_ID replaced")
else:
    check('"%s"' % NEWPKG in s, "APPLICATION_ID already replaced or pattern differs")

# ---------- 4. "com.br.top/files" path constants ----------
print("== 4. files path constants ==")
files4 = [
    "smali_classes4/com/blackhub/bronline/game/core/ABTestUtils$Companion.smali",
    "smali_classes4/com/blackhub/bronline/launcher/download/DownloadWorker$doWork$2.smali",
    "smali_classes4/com/blackhub/bronline/launcher/viewmodel/MainActivityViewModel.smali",
    "smali_classes4/com/blackhub/bronline/launcher/viewmodel/MainActivityViewModel$fetchFeatureFlag$2.smali",
]
tot = 0
for f in files4:
    p = f"{SRC}/{f}"
    s = rd(p)
    before = s.count('const-string')
    s2 = re.sub(r'const-string v(\d+), "com\.br\.top/files"', lambda m: f'const-string v{m.group(1)}, "{NEWPKG}/files"', s)
    c = len(re.findall(r'const-string v\d+, "' + re.escape(NEWPKG) + r'/files"', s2))
    wr(p, s2)
    tot += c
check(tot >= 4, f"path constants replaced in {tot} sites (expect >=4)")

# ---------- 5. StoreUpdateHelper self-update links ----------
print("== 5. StoreUpdateHelper update links ==")
p = f"{SRC}/smali_classes4/com/blackhub/bronline/launcher/update/StoreUpdateHelper.smali"
s = rd(p)
n1 = s.count("market://details?id=com.br.top")
s = s.replace("market://details?id=com.br.top", GH_REL)
n2 = s.count("https://play.google.com/store/apps/details?id=com.br.top")
s = s.replace("https://play.google.com/store/apps/details?id=com.br.top", GH_REL)
wr(p, s)
check(n1 + n2 >= 1, f"replaced {n1} market + {n2} play links")

# ---------- 6. DownloadWorker '/'->'.' URL flatten ----------
print("== 6. DownloadWorker URL flatten ==")
p = f"{SRC}/smali_classes4/com/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2.smali"
s = rd(p)
if "Ljava/lang/String;->replace(CC)Ljava/lang/String;" in s:
    check(True, "already flattened")
else:
    marker = 'invoke-virtual {v5, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;'
    if marker in s:
        ins = ("const/16 v6, 0x2f\n"
               "    const/16 v7, 0x2e\n"
               "    invoke-virtual {p1, v6, v7}, Ljava/lang/String;->replace(CC)Ljava/lang/String;\n"
               "    move-result-object p1\n    ")
        s = s.replace(marker, ins + marker, 1)
        wr(p, s)
        check(True, "flatten inserted")
    else:
        check(False, "append marker not found - manual review needed")

# ---------- 9. initLanguageOnStartup language patch ----------
print("== 9. UtilsKt.initLanguageOnStartup: en support + en default ==")
p = f"{SRC}/smali_classes4/com/blackhub/bronline/game/core/utils/UtilsKt.smali"
s = rd(p)
old_block = ('''    .line 796
    const-string v0, "pt"

    const-string v1, "ru"

    filled-new-array {v1, v0}, [Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lkotlin/collections/SetsKt;->setOf([Ljava/lang/Object;)Ljava/util/Set;

    move-result-object v0
''')
new_block = ('''    .line 796
    const-string v0, "pt"

    const-string v1, "ru"

    const-string v6, "en"

    filled-new-array {v1, v0, v6}, [Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lkotlin/collections/SetsKt;->setOf([Ljava/lang/Object;)Ljava/util/Set;

    move-result-object v0

    const-string v1, "en"
''')
if old_block in s:
    s = s.replace(old_block, new_block, 1)
    wr(p, s)
    check(True, "initLanguageOnStartup patched (set {ru,pt,en}, default en)")
else:
    check('filled-new-array {v1, v0, v6}' in s, "initLanguageOnStartup already patched")

# ---------- 10. getLocalizedResourcePath en branch ----------
print("== 10. UtilsKt.getLocalizedResourcePath: en -> en/ ==")
p = f"{SRC}/smali_classes4/com/blackhub/bronline/game/core/utils/UtilsKt.smali"
s = rd(p)
old_block = ('''    .line 857
    const-string v0, "pt"

    invoke-static {p0, v0}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const-string p0, "pt/"

    goto :goto_0

    :cond_0
    const-string p0, "ru/"

    .line 858
    :goto_0
''')
new_block = ('''    .line 857
    const-string v0, "en"

    invoke-static {p0, v0}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    const-string p0, "en/"

    goto :goto_0

    :cond_1
    const-string v0, "pt"

    invoke-static {p0, v0}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const-string p0, "pt/"

    goto :goto_0

    :cond_0
    const-string p0, "ru/"

    .line 858
    :goto_0
''')
if old_block in s:
    s = s.replace(old_block, new_block, 1)
    wr(p, s)
    check(True, "getLocalizedResourcePath patched (en/pt/ru branches)")
else:
    check('const-string p0, "en/"' in s, "getLocalizedResourcePath already patched")

# ---------- 8. FULL ENGLISH strings ----------
print("== 8. English strings.xml ==")
# load translations
T = {}
import os
tr_dir = "/home/z/audit-work/tr"
for f in sorted(glob.glob(f"{tr_dir}/tr*.py")):
    ns = {}
    exec(compile(open(f, encoding="utf-8").read(), f, "exec"), ns)
    T.update(ns["T"])
print(f"  loaded {len(T)} translations")
SPECIAL_KEEP = {"common_edittext_allowed_characters", "common_edittext_allowed_characters_for_admin_tools"}

def xml_escape_value(v):
    # v: python string from dict (may contain \n, ', ", & etc.)
    v = v.replace("&", "&amp;")
    v = v.replace("<", "&lt;").replace(">", "&gt;")
    v = v.replace("'", "\\'")
    v = v.replace('"', '\\"')
    v = v.replace("\n", "\\n")
    v = v.replace("\t", "\\t")
    return v

p = f"{SRC}/res/values/strings.xml"
s = rd(p)
entries = re.findall(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', s, re.S)
cyr = [(n, attrs, v) for n, attrs, v in entries if re.search(r"[А-Яа-яЁё]", v)]
applied = 0
for n, attrs, v in cyr:
    if n in SPECIAL_KEEP or n not in T:
        continue
    new_val = xml_escape_value(T[n])
    old_el = f'<string name="{n}"{attrs}>{v}</string>'
    new_el = f'<string name="{n}"{attrs}>{new_val}</string>'
    if old_el in s:
        s = s.replace(old_el, new_el, 1)
        applied += 1
wr(p, s)
check(applied >= 1420, f"applied {applied}/1428 translations")
# verify no cyrillic remains except special keeps
entries2 = re.findall(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', s, re.S)
left = [n for n, a, v in entries2 if re.search(r"[А-Яа-яЁё]", v) and n not in SPECIAL_KEEP]
check(len(left) == 0, f"cyrillic remaining (non-special): {len(left)} {left[:5]}")

# ---------- 7. Branding tokens (ALL locales) ----------
print("== 7. Branding tokens in all locales ==")
tot_files = 0
for path in glob.glob(f"{SRC}/res/values*/strings.xml"):
    s = rd(path)
    orig = s
    for old, new in [("BLACK RUSSIA", "GRAND HORIZON RP"), ("Black Russia", "Grand Horizon RP"),
                     ("Black russia", "Grand Horizon RP"), ("black russia", "Grand Horizon RP"),
                     ("blackrussia", "grandhorizonrp"), ("BlackRussia", "GrandHorizonRP"),
                     ("BLACKRUSSIA", "GRANDHORIZONRP"), ("ЧЕРНЫЙ РУССКИЙ", "GRAND HORIZON"),
                     ("Черный русский", "Grand Horizon"), ("Black Rasiya", "Grand Horizon RP")]:
        s = s.replace(old, new)
    if s != orig:
        wr(path, s); tot_files += 1
check(tot_files >= 1, f"rebranded {tot_files} strings.xml files")

# app_name sanity
s = rd(f"{SRC}/res/values/strings.xml")
m = re.search(r'<string name="app_name"[^>]*>(.*?)</string>', s)
print(f"  app_name = {m.group(1) if m else '??'}")
check(m and "Grand Horizon" in m.group(1), "app_name is Grand Horizon")

# ---------- final cross-checks ----------
print("== Final cross-checks ==")
s = rd(f"{SRC}/AndroidManifest.xml")
check('package="com.grandhorizonrp.launcher"' in s, "manifest package final")
# no stale brand refs in default strings
s = rd(f"{SRC}/res/values/strings.xml")
for bad in ["BLACK RUSSIA", "Black Russia", "ЧЕРНЫЙ", "Черный"]:
    check(bad not in s, f"no '{bad}' in default strings.xml")

print()
print("PATCH RESULT: " + ("ALL OK" if ok else "FAILURES PRESENT"))
sys.exit(0 if ok else 1)
