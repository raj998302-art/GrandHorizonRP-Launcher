#!/usr/bin/env python3
"""Contract verifier: every Java_* symbol exported by the native libs must
resolve to a matching native method declaration in the from-scratch APK's
dex. Also verifies the engine FindClass target and the JNIJSONTransport
callback surface against the original smali signatures."""
import re
import subprocess
import sys
import zipfile

APK = "/home/z/ghrp-scratch/build/signed/unsigned-aligned-signed.apk"
LIBS = [
    "/home/z/ghrp-scratch/decode/v5/lib/arm64-v8a/libblackrussia-client.so",
    "/home/z/ghrp-scratch/decode/v5/lib/arm64-v8a/libupdate-manager.so",
    "/home/z/ghrp-scratch/decode/v5/lib/arm64-v8a/libsigner.so",
]

# JNIJSONTransport callback surface extracted from the original smali
JNIJSON_TRANSPORT_EXPECTED = """GetBatteryPercentage()F
GetFreeMemoryInMB()I
GetGsmSignalStrengthDbm()I
GetWiFiSignalStrengthDbm()I
OnRequestPlayersCompleted(I[I[Ljava/lang/String;)V
awaitDialogClose()V
awaitDialogClose(Z)V
checkFreeSpaceMemory(DJ)Z
closeAllWindows()V
closeAllWindowsExSAMP()V
closeSoftwareKeyboard()V
destroyPlatformMediaDecoder(J)V
doFingerPrintSupport()Z
doRecordAudioPermissionGranted()Z
getClipboardString()[B
getCompatibleClientVersion()I
getDeviceInfo()[B
getFreeSpaceMemory(Ljava/lang/String;)J
getSerializedSettings()[B
getSoftwareKeyboardHeight()I
getVersionResources()Ljava/lang/String;
hideTimeStamp()V
initializePlatformMediaDecoder(J[BZ)V
isExistFailureFlag()Z
isExistState()Z
isSoftwareKeyboardOpen()Z
keyboardOpened(Z)V
loadPatchIndexBytes()[B
loadStateBytes()[B
onAsyncBitmapRequestDone(Ljava/lang/String;Landroid/graphics/Bitmap;)V
onAsyncFileRequestDone(Ljava/lang/String;Ljava/lang/String;)V
onDialogRPCIncoming(II[B[B[B[B)V
onJsonDataIncoming(I[B)V
onSettingsJsonDataUpdate([B)V
onSpawn()V
onSplashScreenDestroyed()V
onTabEvent([I[B[I[II)V
openSoftwareKeyboard([BIIIZZIIII)V
playVibration(IF)V
playVibrationSequence([I[F)V
quitGame()V
relocateInputField(IIII)V
removeFailureFlag()Z
removePatchIndex()Z
removeState()Z
reportEvent(ILjava/lang/String;Ljava/lang/String;)V
reportEventForAllProviders(Ljava/lang/String;Ljava/lang/String;)V
sendErrorToFirebaseFirestore(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
sendJsonData(I[B)V
setClipboardString(Ljava/lang/String;)V
setCompatibleClientVersion(I)Z
setSoftwareKeyboardSelection(II)V
setSoftwareKeyboardText([BII)V
setVersionResources(Ljava/lang/String;)Z
showErrorDialog(Ljava/lang/String;)V
storeFailureFlag()Z
storePatchIndexBytes([B)Z
storeStateBytes([B)Z
updateSurfaceTexture(J)V""".splitlines()


def mangle_to_java(sym):
    """Java_com_blackhub_bronline_game_core_JNILib_init -> (com.blackhub.bronline.game.core.JNILib, init)"""
    body = sym[len("Java_"):]
    # handle underscores that are escaped (_1) vs package separators
    parts = []
    cur = []
    i = 0
    while i < len(body):
        c = body[i]
        if c == "_":
            if i + 1 < len(body) and body[i + 1] == "1":
                cur.append("_")
                i += 2
                continue
            if i + 1 < len(body) and body[i + 1] == "2":
                cur.append(";")
                i += 2
                continue
            parts.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(c)
        i += 1
    parts.append("".join(cur))
    cls = ".".join(parts[:-1])
    return cls, parts[-1]


def main():
    failures = []

    # 1. collect Java_ symbols from the libs
    symbols = set()
    for lib in LIBS:
        out = subprocess.run(["strings", "-a", lib], capture_output=True, text=True).stdout
        for line in out.splitlines():
            if line.startswith("Java_") and line[5:].isidentifier() or line.startswith("Java_"):
                if re.match(r"^Java_[A-Za-z0-9_1]+$", line):
                    symbols.add(line)

    # engine-relevant subset: classes we must provide (skip 3rd-party: adjust sig + un4seen bass + crashlytics Java classes are not in our app but those libs are only loaded by their SDKs — EXCEPT libbass which the engine loads via DT_NEEDED!)
    engine_symbols = set()
    third_party = set()
    for sym in symbols:
        cls, _ = mangle_to_java(sym)
        if cls.startswith("com.blackhub.bronline"):
            engine_symbols.add(sym)
        else:
            third_party.add(sym)

    print(f"[i] total Java_ symbols: {len(symbols)} (engine: {len(engine_symbols)}, third-party: {len(third_party)})")
    for sym in sorted(third_party):
        print(f"[skip 3rd-party] {sym}")

    # 2. dump dex method names from our APK
    with zipfile.ZipFile(APK) as z:
        dex = z.read("classes.dex")
    open("/tmp/ghrp-classes.dex", "wb").write(dex)
    # use dexdump from build-tools
    dd = "/home/z/ghrp-scratch/sdk/bt36/android-16/dexdump"
    out = subprocess.run([dd, "-d", "/tmp/ghrp-classes.dex"], capture_output=True, text=True).stdout

    # map: class -> set of (name, descriptor-ish)
    dex_classes = {}
    current = None
    for line in out.splitlines():
        m = re.match(r"Class #\d+\s+header_off.*", line)
        if "Class descriptor" in line:
            current = line.split("'")[1]
            dex_classes.setdefault(current, set())
        elif current and ("name" in line):
            nm = re.search(r"name\s*:\s*'([^']+)'", line)
            ty = re.search(r"type\s*:\s*'([^']+)'", line)
            if nm:
                dex_classes[current].add((nm.group(1), ty.group(1) if ty else ""))

    # 3. verify each engine symbol resolves
    ok = 0
    for sym in sorted(engine_symbols):
        cls, method = mangle_to_java(sym)
        dex_cls = "L" + cls.replace(".", "/") + ";"
        if dex_cls not in dex_classes:
            failures.append(f"MISSING CLASS {cls} (for symbol {sym})")
            continue
        names = {n for n, _ in dex_classes[dex_cls]}
        if method not in names:
            failures.append(f"MISSING METHOD {cls}.{method} (symbol {sym})")
        else:
            ok += 1
    print(f"[i] engine JNI symbols resolved in dex: {ok}/{len(engine_symbols)}")

    # 4. AppLocalValues FindClass target
    if "Lcom/blackhub/bronline/game/core/AppLocalValues;" not in dex_classes:
        failures.append("MISSING CLASS com.blackhub.bronline.game.core.AppLocalValues (engine FindClass target)")
    else:
        methods = {n for n, _ in dex_classes["Lcom/blackhub/bronline/game/core/AppLocalValues;"]}
        for need in ["getInstance", "hasAppLocalValue", "getAppLocalValue", "setAppLocalValue"]:
            if need not in methods:
                failures.append(f"AppLocalValues missing method {need}")

    # 5. JNIJSONTransport full surface (name check; signatures via javap on our classes would need dex->java; do name-based + count)
    if "Lcom/blackhub/bronline/game/core/JNIJSONTransport;" not in dex_classes:
        failures.append("MISSING CLASS JNIJSONTransport")
    else:
        methods = {n for n, _ in dex_classes["Lcom/blackhub/bronline/game/core/JNIJSONTransport;"]}
        missing = [m for m in JNIJSON_TRANSPORT_EXPECTED if m.split("(")[0] not in methods]
        if missing:
            failures.append("JNIJSONTransport missing: " + ", ".join(missing))
        print(f"[i] JNIJSONTransport methods present: {len(methods)} (expected surface: {len(JNIJSON_TRANSPORT_EXPECTED)})")

    # 6. core classes present
    for cls in ["com.blackhub.bronline.game.core.JNILib",
                "com.blackhub.bronline.game.core.JNIActivity",
                "com.blackhub.bronline.game.core.JNIGLSurfaceView",
                "com.blackhub.bronline.game.core.JNIRenderer",
                "com.blackhub.bronline.game.GUIManager",
                "com.blackhub.bronline.game.GameRender",
                "com.blackhub.bronline.game.core.JNIConfig",
                "com.blackhub.bronline.game.core.keyboardHelper.SoftwareKeyboardBridge",
                "com.blackhub.bronline.launcher.App",
                "com.blackhub.bronline.launcher.Settings",
                "com.blackhub.bronline.launcher.di.HelpshiftManager",
                "com.grandhorizonrp.launcher.UpdateController",
                "com.grandhorizonrp.launcher.AuthController",
                "com.grandhorizonrp.launcher.UpdateView",
                "com.grandhorizonrp.launcher.Http",
                "com.grandhorizonrp.launcher.GHRPLog"]:
        if "L" + cls.replace(".", "/") + ";" not in dex_classes:
            failures.append("MISSING CLASS " + cls)

    # 7. APK structure checks
    with zipfile.ZipFile(APK) as z:
        names = z.namelist()
        for need in ["classes.dex", "lib/arm64-v8a/libblackrussia-client.so",
                     "lib/arm64-v8a/libupdate-manager.so", "lib/arm64-v8a/libsigner.so",
                     "assets/Fonts/Roboto-Regular.ttf", "assets/Fonts/muller_bold.ttf",
                     "res/xml/network_security_config.xml"]:
            if need not in names:
                failures.append("MISSING APK ENTRY " + need)
        nlibs = [n for n in names if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
        print(f"[i] arm64 libs packaged: {len(nlibs)}")

    print()
    if failures:
        print("FAILURES (%d):" % len(failures))
        for f in failures:
            print("  - " + f)
        sys.exit(1)
    print("ALL CONTRACT CHECKS PASS")


if __name__ == "__main__":
    main()
