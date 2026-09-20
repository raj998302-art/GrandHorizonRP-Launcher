# Grand Horizon RP Launcher — Gradle Project

**This is the canonical launcher source.** 100% new-source Android application
(`com.grandhorizonrp.launcher`) built with a real Gradle + AGP pipeline.
The original Black Russia launcher is used ONLY as a behavioral/contract
reference — see [`../ORIGINAL_REFERENCE_SPEC.md`](../ORIGINAL_REFERENCE_SPEC.md).

## Structure

```
launcher-gradle/
├── settings.gradle              # Gradle 8.11.1 project settings
├── build.gradle                 # AGP 8.9.1 root plugin
├── gradle.properties
├── gradlew / gradle/wrapper/    # Gradle wrapper (reproducible builds)
└── app/
    ├── build.gradle             # applicationId com.grandhorizonrp.launcher,
    │                            # v1529 / 16.102.14498, minSdk 26, targetSdk 36
    ├── proguard-rules.pro       # keeps engine bridge classes
    └── src/main/
        ├── AndroidManifest.xml  # own manifest, single JNIActivity
        ├── java/com/grandhorizonrp/launcher/   # OUR app code
        │   (UpdateController, UpdateView, AuthController, Http, GHRPLog)
        ├── java/com/blackhub/bronline/         # ENGINE JNI BRIDGE ADAPTERS
        │   (JNILib, JNIJSONTransport, JNIActivity, GUIManager, AppLocalValues,
        │    JNIConfig, GameRender, SoftwareKeyboardBridge, App, Settings, …)
        │   — these class paths are REQUIRED by the native engine's FindClass
        │     lookups (its runtime ABI), implemented 100% by our own code
        ├── res/                 # GHRP branding, English strings, themes
        ├── assets/Fonts/        # engine-required fonts
        └── jniLibs/             # 13 native engine libraries × 2 ABIs
                                 # (binary dependency, gitignored — see below)
```

## Native engine libraries (binary dependency)

The 13 `.so` files per ABI are the proprietary game engine, integrated as a
**dependency** per the observed JNI contract (spec §13). They are byte-identical
to the original authorized distribution and are NOT committed to git.
CI fetches them from our `latest` release asset
`GrandHorizonRP-Launcher-v6-Scratch.apk` (own artifact). For local builds:

```bash
mkdir -p app/src/main/jniLibs && cd app/src/main/jniLibs
unzip -o /path/to/GrandHorizonRP-Launcher-v6-Scratch.apk 'lib/*'
mv lib/* . && rmdir lib
```

## Build

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Requirements: JDK 17+ (CI uses 21), Android SDK (AGP auto-provisions
platform/build-tools when licenses are accepted).

Signing uses `../keystore/ghrp-release.keystore` (alias `ghrp`); CI and local
defaults are equal to previous releases (upgrade-compatible certificate
`db509ae0…`).

## Verification

`python3 ../tools/verify_contract.py` — checks that every engine `Java_*`
symbol resolves in the dex (60/60), the full `JNIJSONTransport` callback
surface exists, and the APK structure is complete.
