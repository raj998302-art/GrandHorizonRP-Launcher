# WEBVIEW vs NATIVE ARCHITECTURE — Verified Split

## 1. Rule (from the reference forensics — ORIGINAL_SCREEN_STATE_MACHINE.md)

```
WEBVIEW (auth ONLY):
  login, registration (email -> OTP -> password), recovery, guest auth,
  handoff via Android.initToken({front_token,...})

NATIVE / launcher-side (never WebView):
  game-data update, server selection, character selection & creation,
  character rendering (3D), confirmation, spawn, world, multiplayer,
  HUD, gameplay UI
```

The original implements exactly this: screenId 0x58 (`WebViewAuthFragment`) is
the **only** WebView screen out of 88 screens; the WebView is destroyed right
after `saveTokenAndCloseWebView`.

## 2. Grand Horizon audit (this session)

| Screen | Where it lives | Verdict |
|---|---|---|
| Splash | Java (`MainActivity.showSplash`) | OK (launcher-level, matches original) |
| Update | Java `UpdateController` + `UpdateView` | OK (original: UpdateManagerFragment + native patcher) |
| Sign in / Sign up / Recovery / Guest | **WebView only** (ghrp-auth.vercel.app in AuthController) | OK — matches 0x58 contract |
| Server select | Java panel (no WebView) | OK at launcher level; native-GUI parity deferred to M2 (documented, not hidden) |
| Character selection / creation | **Native 3D** (GHEngineView + translucent side panel) | OK — rendering is 100% native engine |
| Character confirmation | Java panel -> server API | OK |
| Spawn / world / HUD | pending M2 | documented gap |

**Findings:**
- Character selection is **NOT** implemented inside the WebView (no
  character-creation markup exists in `page.tsx`; the page only performs auth).
- Server selection is **NOT** inside the WebView.
- The WebView hands off via `Android.initToken` and is closed — it never
  becomes the game UI.

## 3. Bridge contract

```
AuthController injects  WebAppInterface-equivalent named "Android":
  Android.initToken(jsonString)   <- {front_token, guest_secret?, account?}
  Android.closeWebview(error)
URL params (matches original param set):
  sysinfo=android&client_id=<uuid>&ttclid=&appmetrica_device_id=&adjust_id=
  (+ email= prefill for re-login convenience — GHRP addition)
```

## 4. Known, honest deviations

1. Server/character screens are **Java overlay panels + native 3D viewport**,
   not engine-GUI fragments like the original's native UI system. Rendering is
   native either way; full native-GUI parity needs the gui.bpc UI runtime (M2+).
2. The character *name* comes from the server account (SA-MP login name
   contract), so the launcher creation panel intentionally has no free-text
   name field (the original asks for names inside the native/registration
   dialogs backed by the same account table).
