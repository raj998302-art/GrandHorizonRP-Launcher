# v3 patch set — ORIGINAL-based rebuild (19 Sep 2026, session L3)

Base: pristine apktool decode of the ORIGINAL purchased launcher (file.kiwi launcher_src.zip,
com.br.top site build v16.102.14498 / versionCode 1529, decoded at /tmp/audit/launcher_src).

Applied by `patch_v3.py` (all steps self-verifying):
1. Settings.smali: 3 API base URLs -> jsDelivr client-api
2. Manifest package com.br.top -> com.grandhorizonrp.launcher (+ 7 provider authority renames)
3. BuildConfig.APPLICATION_ID -> com.grandhorizonrp.launcher
4. "com.br.top/files" path constants -> new package (5 sites / 4 files)
5. StoreUpdateHelper market/play links -> GitHub release self-update URL
6. DownloadWorker URL path flatten ('/' -> '.') for CDN asset names
7. Branding tokens in all values*/strings.xml (BLACK RUSSIA -> GRAND HORIZON RP)
8. FULL ENGLISH: 1,428 translations applied to res/values/strings.xml
   (3 intentional keeps: 2 charset whitelists, 1 RU plate-format sample)
9. UtilsKt.initLanguageOnStartup: supported {ru,pt} -> {ru,pt,en}, default ru -> en
10. UtilsKt.getLocalizedResourcePath: en -> "en/" branch (parity with gamedata jsons/en/)
11. BrSimBanner fallback URLs -> GitHub repo

Build pipeline: apktool.json -> apktool.yml (see apktool.yml here) -> `apktool b --use-aapt2`
-> uber-apk-signer (keystore/ghrp-release.keystore, v2+v3, zipalign verified).

Key fixes vs the previously FAILED build:
- targetSdk 36 (was accidentally 34)
- gwpAsanMode INT_DEC -1 exact original encoding (verified in binary manifest)
- full-English Java strings + engine-level en language support
- zero stale com.br.top references; 64,524/64,524 classes 1:1; 26 native libs byte-identical

Verification: `verify_final.py` — 34 checks, ALL PASS.
Native anti-tamper hunt verdict: engine libs contain NO signature-kill primitive
(see /tmp/audit/reports/L3_NATIVE_TAMPER_HUNT.md + worklog L3-NATIVE-HUNT).
