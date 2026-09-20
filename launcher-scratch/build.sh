#!/bin/bash
# GHRP from-scratch launcher build (no Gradle — direct toolchain)
# ECJ -> d8 -> aapt2 -> package -> sign (uber-apk-signer: zipalign + v2/v3)
set -e
cd /home/z/ghrp-scratch

ANDROID_JAR=sdk/plat36/android-36/android.jar
BT=sdk/bt36/android-16

echo "=== [1/6] ECJ compile ==="
rm -rf build
mkdir -p build/obj build/dex build/gen
find app/src -name '*.java' > build/sources.txt
java -jar tools/ecj.jar \
    -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -proc:none -nowarn \
    -d build/obj \
    @build/sources.txt
echo "compiled: $(find build/obj -name '*.class' | wc -l) classes"

echo "=== [2/6] d8 dex ==="
find build/obj -name '*.class' > build/classes.txt
"$BT/d8" --release \
    --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output build/dex \
    $(cat build/classes.txt | tr '\n' ' ')
ls -la build/dex/

echo "=== [3/6] aapt2 compile resources ==="
"$BT/aapt2" compile --dir app/res -o build/res.zip

echo "=== [4/6] aapt2 link ==="
"$BT/aapt2" link -o build/base.apk \
    -I "$ANDROID_JAR" \
    --manifest app/AndroidManifest.xml \
    --min-sdk-version 26 \
    --target-sdk-version 36 \
    --version-code 1529 \
    --version-name 16.102.14498 \
    --auto-add-overlay \
    build/res.zip
ls -la build/base.apk

echo "=== [5/6] assemble APK ==="
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
# native libs + engine fonts (deflated — extractNativeLibs=true)
(cd app && zip -q -r ../build/unsigned.apk lib/ assets/)
echo "entries: $(unzip -l build/unsigned.apk | tail -1)"

echo "=== [6/6] sign ==="
rm -rf build/signed
java -jar repo/tools/uber-apk-signer.jar \
    -a build/unsigned.apk \
    --ks repo/keystore/ghrp-release.keystore \
    --ksAlias ghrp \
    --ksPass GrandHorizon2026 \
    --ksKeyPass GrandHorizon2026 \
    --out build/signed 2>&1 | tail -12
ls -la build/signed/
echo "=== BUILD COMPLETE ==="
