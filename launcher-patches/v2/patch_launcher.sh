#!/usr/bin/env bash
# Grand Horizon RP launcher patch script - applies ALL smali/manifest/xml patches
set -e
SRC=/home/z/ghrp-work/launcher_src/launcher_src
API="https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@main/client-api/"
NEWPKG="com.grandhorizonrp.launcher"

cd "$SRC"

echo "=== 1. Settings.smali: 3 API URLs -> jsDelivr client-api ==="
python3 - <<PYEOF
import re
p = "smali_classes4/com/blackhub/bronline/launcher/Settings.smali"
s = open(p, encoding="utf-8").read()
urls = ["https://api.blackrussia.online/client/", "https://api.black-russia.com/client/", "https://api-backup111.blackrussia.online/client/"]
api = "https://cdn.jsdelivr.net/gh/raj998302-art/GrandHorizonRP-Launcher@main/client-api/"
n = 0
for u in urls:
    # const-string or const-string/jumbo variants
    for form in ['const-string v%d, "%s"', 'const-string/jumbo v%d, "%s"']:
        for reg in range(0, 16):
            old = form % (reg, u)
            if old in s:
                s = s.replace(old, form % (reg, api))
                n += 1
open(p, "w", encoding="utf-8").write(s)
print(f"Settings.smali: replaced {n} URL constants")
# verify
assert "blackrussia.online" not in s and "black-russia.com" not in s, "old URLs remain!"
print("verified: no old API URLs remain in Settings.smali")
PYEOF

echo "=== 2. Package rename com.br.top -> $NEWPKG ==="
sed -i "s/android:name=\"com.br.top.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION\"/android:name=\"${NEWPKG}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION\"/g" AndroidManifest.xml
sed -i "s/com.br.top.provider.HelpshiftContentProvider/${NEWPKG}.provider.HelpshiftContentProvider/g" AndroidManifest.xml
sed -i "s/com.br.top.appmetrica.preloadinfo.retail/${NEWPKG}.appmetrica.preloadinfo.retail/g" AndroidManifest.xml
sed -i "s/com.br.top.androidx-startup/${NEWPKG}.androidx-startup/g" AndroidManifest.xml
sed -i "s/com.br.top.adjust-lifecycle-provider/${NEWPKG}.adjust-lifecycle-provider/g" AndroidManifest.xml
sed -i "s/com.br.top.firebaseinitprovider/${NEWPKG}.firebaseinitprovider/g" AndroidManifest.xml
sed -i "s/android:authorities=\"com.br.top.provider\"/android:authorities=\"${NEWPKG}.provider\"/g" AndroidManifest.xml
sed -i "s/package=\"com.br.top\"/package=\"${NEWPKG}\"/" AndroidManifest.xml
grep -c "com.br.top" AndroidManifest.xml && echo "WARNING com.br.top still in manifest" || echo "manifest clean"

sed -i 's/\.field public static final APPLICATION_ID:Ljava\/lang\/String; = "com.br.top"/.field public static final APPLICATION_ID:Ljava\/lang\/String; = "'"${NEWPKG}"'"/' smali/com/blackhub/bronline/BuildConfig.smali

for f in "smali_classes4/com/blackhub/bronline/game/core/ABTestUtils\$Companion.smali" \
         "smali_classes4/com/blackhub/bronline/launcher/download/DownloadWorker\$doWork\$2.smali" \
         "smali_classes4/com/blackhub/bronline/launcher/viewmodel/MainActivityViewModel.smali" \
         "smali_classes4/com/blackhub/bronline/launcher/viewmodel/MainActivityViewModel\$fetchFeatureFlag\$2.smali"; do
  sed -i 's/const-string v\([0-9]*\), "com\.br\.top\/files"/const-string v\1, "'"${NEWPKG}"'\/files"/' "$f"
done

echo "--- StoreUpdateHelper self-update links -> GitHub ---"
sed -i 's|market://details?id=com.br.top|https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/GrandHorizonRP-Launcher.apk|' smali_classes4/com/blackhub/bronline/launcher/update/StoreUpdateHelper.smali
sed -i 's|https://play.google.com/store/apps/details?id=com.br.top|https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/GrandHorizonRP-Launcher.apk|' smali_classes4/com/blackhub/bronline/launcher/update/StoreUpdateHelper.smali

echo "=== 3. strings.xml rebrand (all locales) ==="
python3 - <<PYEOF
import glob, re
total = 0
for p in glob.glob("res/values*/strings.xml"):
    s = open(p, encoding="utf-8").read()
    orig = s
    s = s.replace("BLACK RUSSIA", "GRAND HORIZON RP")
    s = s.replace("Black Russia", "Grand Horizon RP")
    s = s.replace("Black russia", "Grand Horizon RP")
    s = s.replace("black russia", "grand horizon rp")
    s = s.replace("blackrussia", "grandhorizonrp")
    s = s.replace("BlackRussia", "GrandHorizonRP")
    s = s.replace("BLACKRUSSIA", "GRANDHORIZONRP")
    s = s.replace("ЧЕРНЫЙ РУССКИЙ", "GRAND HORIZON")
    s = s.replace("Черный русский", "Grand Horizon")
    s = s.replace("Black Rasiya", "Grand Horizon RP")
    if s != orig:
        open(p, "w", encoding="utf-8").write(s)
        cnt = sum(1 for _ in re.finditer("GRAND HORIZON|Grand Horizon|grandhorizonrp|GrandHorizonRP", s))
        print(f"{p}: rewritten ({cnt} GHRP refs)")
        total += 1
print(f"rebranded {total} strings.xml files")
PYEOF

echo "=== 4. DownloadWorker '/'->'.' URL flatten patch ==="
python3 - <<PYEOF
p = "smali_classes4/com/blackhub/bronline/launcher/download/DownloadWorker$downloadFile$2.smali"
s = open(p, encoding="utf-8").read()
if "invoke-virtual {p1}, Ljava/lang/String;->replace" not in s:
    # find the URL build: CURRENT_CDN_URL + path + name. Insert replace('/','.') on the path string.
    # The original sequence: ...getPath() -> v? ... StringBuilder append path
    marker = 'invoke-virtual {v5, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;'
    # p1 holds the path at append time. Insert replace before append:
    ins = ("invoke-virtual {p1, v6}, Ljava/lang/String;->replace(CC)Ljava/lang/String;\n"
           "    move-result-object p1\n    ")
    # v6 must be free register with '/' char... use a safer approach: const/16 with a high local
    # Actually use String.replace(CharSequence) style via chars:
    # const/16 v6, 0x2f ; '/'  then replace(C)C requires API... String.replace(char,char) exists since API 1 as replace(CC)
    if marker in s:
        s = s.replace(marker, "const/16 v6, 0x2f\n    const/16 v7, 0x2e\n    invoke-virtual {p1, v6, v7}, Ljava/lang/String;->replace(CC)Ljava/lang/String;\n    move-result-object p1\n    " + marker, 1)
        open(p, "w", encoding="utf-8").write(s)
        print("DownloadWorker: inserted replace('/','.') before path append")
    else:
        print("WARN: marker not found - check manually")
else:
    print("DownloadWorker already patched")
PYEOF

echo "=== 5. Final verification ==="
grep -rn "blackrussia.online\|black-russia.com" smali*/ res/values*/strings.xml 2>/dev/null | grep -v "^Binary" | head -5 || true
echo "DONE"
