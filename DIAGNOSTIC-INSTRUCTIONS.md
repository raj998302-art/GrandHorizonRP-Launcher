# Grand Horizon RP — Startup Crash Diagnosis Kit (19 Sep 2026)

You reported: the launcher installs, shows a black screen for 1–2 seconds, then
crashes — and the previous diagnostic build produced **no** `ghrp-boot.txt`,
no `ghrp-crash.txt`, no toasts. That means the process died **before the old
marker code ever ran** (or before its file writes became visible).

This kit contains two purpose-built APKs. Install and run them in the order
below and report what you see. NO random patches were applied to produce them —
both were built after a full binary-level original-vs-repack audit
(see `ORIGINAL_VS_GHRP_RUNTIME_DIFF.md`).

---

## APK 1 — `GrandHorizonRP-Diagnostic.apk` (install FIRST)

- Same package as the launcher (`com.grandhorizonrp.launcher`) → **uninstall the
  current launcher first** (the old one crashes anyway).
- It does **NOT** auto-start the game. You get a "Startup Diagnostic" screen
  with numbered buttons. The earliest possible Java code in the app
  (a ContentProvider with `initOrder -1000`, which runs BEFORE every other
  provider and BEFORE `Application.onCreate`) writes persistent markers
  **synchronously with fsync** — so the last marker in
  `Android/data/com.grandhorizonrp.launcher/files/ghrp-boot.txt` identifies the
  first failing stage even if the process is killed without any exception.
- **targetSdk is now 36** (the original's value — the previous builds had 34 by
  accident; this deviation is fixed in this build).

### What to do

1. Install, open it. If the diagnostic screen itself appears → **Java startup,
   manifest, resources and package are PROVEN OK** (that alone rules out half
   of the crash causes).
2. Press the buttons **in order**:
   - `1. Test Java Startup` — device info dump
   - `2. Test Data Directories` — external/internal dirs writable?
   - `3. Test Native Libraries (one by one)` — loads all 13 native libs one by
     one with markers; stops at the first failure and names it
   - `4. Test Engine JNI init` — runs the EXACT `JNILib.init(...)` call the
     game uses (this is where a native anti-tamper abort would happen)
   - `5. Test Renderer (EGL/GLES)` — GPU/EGL initialization test
   - `6. Test Update Manager / Network` — CDN reachability
   - `7. Launch Full Game (JNIActivity)` — starts the real launcher UI
   - `8. Export Diagnostic Report`
3. If ANY step crashes the app: reopen the diagnostic app — it reads the
   timeline and shows the last successful stage. Then press `8. Export
   Diagnostic Report`: the report is written to the app's files dirs AND to the
   **Downloads** folder (visible in any file manager, no Android/data tricks
   needed) and a share sheet opens so you can send it anywhere.

### What to report back

- The result lines shown on the diagnostic screen for each button.
- If a step crashed: which button, and the "Last successful stage" line.
- The content of `ghrp-diag-report.txt` (from Downloads / share sheet).
- If the app died before even the diagnostic screen appeared: say so — that
  means the crash is at process/class-load level (before ANY app code), which
  itself is decisive evidence.

---

## APK 2 — `GHRP-Test2-OriginalResigned.apk` (the signature experiment)

- Contains the **100% original game code**: all 7 DEX files and
  `resources.arsc` are **byte-identical** to the original Black Russia Play
  build (com.launcher.brgame, 16.102.14498), the 26 native libs are the
  original ones, the manifest is original — the ONLY changes are:
  1. signed with the **GHRP release keystore** instead of the original cert,
  2. `requiredSplitTypes` removed (technicality so the standalone APK installs),
  3. native libs injected (the download source ships them in a split).
- Different package (`com.launcher.brgame`) → installs **alongside** the
  diagnostic APK, nothing is overwritten. It still contacts the ORIGINAL
  Black Russia servers (it is the original app!) — it is a test instrument,
  **not** the Grand Horizon launcher, and must NOT be published as the final
  launcher.

### How to read the result

| Result | Meaning |
|---|---|
| Runs like the original (splash/download screen works) | **Re-signing is safe.** The crash is caused by something in our rebuild (package-rename constants, resources, targetSdk). The diagnostic APK (step 4) will show exactly which. |
| Crashes exactly like the GHRP launcher (black screen → dead) | **The engine (or an SDK) verifies the APK signature/certificate and kills the process.** This is the native anti-tamper scenario — we then need a legitimate compatibility strategy for the engine, not random patches. |
| Installs but fails differently | Report what happens — anything distinct is evidence. |

---

## Why no "fixed production APK" was published today

Per your rule: no guess builds. Static analysis proved every layer of the
current release is byte-faithful to the original (code, resources, natives,
assets) **except the signature and the targetSdk deviation (now fixed)**.
With no device in this sandbox, the failing subsystem cannot be identified
honestly without your test results above. Once you report them, the fix will
target exactly that subsystem, then go through CI (must be GREEN) and a new
versioned release with the full verification report.

## Files on the diagnostics release

| File | Purpose |
|---|---|
| `GrandHorizonRP-Diagnostic.apk` | diagnostic screen build (package `com.grandhorizonrp.launcher`) |
| `GHRP-Test2-OriginalResigned.apk` | signature-isolation experiment (original bytes + GHRP signature) |
| `ORIGINAL_VS_GHRP_RUNTIME_DIFF.md` | the full original-vs-repack audit |
| `DIAGNOSTIC-INSTRUCTIONS.md` | this file |
