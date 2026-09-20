package com.grandhorizonrp.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.grandhorizonrp.launcher.engine.GHEngineView;
import com.grandhorizonrp.launcher.engine.GHNative;

import org.json.JSONObject;

import java.io.File;

/**
 * GRAND HORIZON RP launcher — main activity (own implementation).
 *
 * Launcher state machine (WebView = AUTH ONLY; everything else native/Java-side):
 *
 *   APP_START
 *     -> (game-data update, Java updater)
 *     -> SESSION_RESTORE  (SessionStore + backend verification)
 *          |-- no saved session ----------------> AUTHENTICATE  (WebView SSO)
 *          |-- guest: re-auth via guest_secret -> CHARACTER_CHECK
 *          |-- user: stored front_token valid --> CHARACTER_CHECK
 *          '-- invalid/expired -----------------> AUTHENTICATE
 *   AUTHENTICATE (WebView)
 *     -> token payload {front_token, guest_secret, account} -> save -> CHARACTER_CHECK
 *   CHARACTER_CHECK (GET /api/v2/character)
 *     |-- has_character = true ----> CHARACTER_READY  -> server select -> play
 *     '-- has_character = false ---> CHARACTER_REQUIRED -> creation (native 3D)
 *   CHARACTER CREATION (native engine preview + side panel)
 *     -> POST /api/v2/character {sex, skin} -> CHARACTER_READY -> play status
 *
 * Every transition is logged (GHRPLog) — see WEBVIEW_STATE_MACHINE.md.
 */
public final class MainActivity extends Activity implements GHNative.Callback {
    private FrameLayout mRoot;
    private FrameLayout mOverlay;
    private GHEngineView mEngineView;
    private UpdateController mUpdateController;

    // ---- session state ----
    private SessionStore.Session mSession;
    private boolean mCharacterReady = false;
    private JSONObject mCharacter = null;   // last known character JSON from the backend
    private boolean mRestoreInFlight = false;

    // ---- character creation state ----
    private boolean mFemale = false;
    private int mSkinIndex = 0;

    // ---- diagnostics strip ----
    private TextView mDiagView;
    private final StringBuilder mDiag = new StringBuilder();

    /**
     * Launcher preview index -> SA-MP skin id (documented mapping; all ids are
     * standard SA-MP ped ids 0..311). The native preview mesh is the BR art
     * asset; this id is what the gamemode receives in accounts.skin. The
     * in-game /reg dialog remains the final authority for gameplay looks.
     */
    private static final int[] SKIN_IDS_MALE = {26, 7, 46, 60, 72, 0, 170, 66};
    private static final int[] SKIN_IDS_FEMALE = {12, 13, 40, 55, 90, 192, 91, 216};

    private int sampSkinId() {
        int[] set = mFemale ? SKIN_IDS_FEMALE : SKIN_IDS_MALE;
        return set[mSkinIndex % set.length];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;
        GHRPLog.i("=== MainActivity.onCreate (GHRP from-scratch engine) ===");
        keepScreenOn();
        hideSystemUi();

        mRoot = new FrameLayout(this);
        mOverlay = new FrameLayout(this);
        mRoot.addView(mOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(mRoot);

        showSplash();
        startUpdateFlow();
    }

    // ------------------------------------------------------------------
    // Splash (own branding — zero original assets)
    // ------------------------------------------------------------------
    private void showSplash() {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B0F1A, 0xFF141B2E, 0xFF05070C});
        splash.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("GRAND HORIZON RP");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setLetterSpacing(0.12f);
        title.setTextColor(0xFFE8B24B);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        splash.addView(title);

        TextView sub = new TextView(this);
        sub.setText("HORIZON CITY");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setLetterSpacing(0.35f);
        sub.setTextColor(0xFF8899BB);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        splash.addView(sub, lp);

        swapOverlay(splash);
    }

    // ------------------------------------------------------------------
    // Update flow (own Java updater) — untouched, working
    // ------------------------------------------------------------------
    private void startUpdateFlow() {
        UpdateView updateView = new UpdateView(this, mOverlay);
        mUpdateController = new UpdateController(this, updateView);
        updateView.setController(mUpdateController);
        mUpdateController.setListener(new UpdateController.Listener() {
            @Override
            public void onUpdateComplete() {
                startEngine();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { beginSessionFlow(); }
                });
            }
        });
        mUpdateController.start();
    }

    // ------------------------------------------------------------------
    // Engine lifecycle (untouched, working)
    // ------------------------------------------------------------------
    private void startEngine() {
        if (mEngineView != null) return;
        File dataRoot = getExternalFilesDir(null);
        if (dataRoot == null) dataRoot = getFilesDir();
        //noinspection ResultOfMethodCallIgnored
        dataRoot.mkdirs();

        mEngineView = new GHEngineView(this);
        GHNative.nativeSetCallback(MainActivity.this);
        GHNative.nativeSetDataRoot(dataRoot.getAbsolutePath());
        GHNative.nativeLoadScene(1 /* character creation */);
        // Surface callback will call nativeInit when the surface is ready.
        mRoot.addView(mEngineView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Auth webview host (own overlay). */
    public static FrameLayout getOverlay() {
        return sInstance != null ? sInstance.mOverlay : null;
    }
    private static volatile MainActivity sInstance;

    // ------------------------------------------------------------------
    // Engine events -> on-screen diagnostics (device-side evidence)
    // ------------------------------------------------------------------
    @Override
    public void onEngineEvent(final String method, final String json) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GHRPLog.i("engine event: " + method + " " + json);
                if ("onSceneReady".equals(method)) {
                    diag("mesh: " + json);
                    Diagnostics.record("scene_ready", json);
                } else if ("onLoadError".equals(method)) {
                    diag("ERROR: " + json);
                    Diagnostics.record("load_error", json);
                    if (mCharacterPanel != null) {
                        TextView err = mCharacterPanel.findViewById(0x2001);
                        if (err != null) err.setText("Engine: " + json);
                    }
                } else if ("onAssetsScanned".equals(method)) {
                    diag("skins scanned: " + json);
                    Diagnostics.record("assets_scanned", json);
                } else if ("onEngineInfo".equals(method)) {
                    diag(json);
                    Diagnostics.record("engine_info", json);
                }
            }
        });
    }

    private void diag(String line) {
        if (line == null || line.isEmpty()) return;
        synchronized (mDiag) {
            if (mDiag.length() > 0) mDiag.append('\n');
            mDiag.append(line);
            if (mDiag.length() > 900) mDiag.delete(0, mDiag.length() - 900);
        }
        if (mDiagView != null) {
            mDiagView.setText(mDiag.toString());
        }
    }

    // ------------------------------------------------------------------
    // SESSION STATE MACHINE (guest persistence fix lives here)
    // ------------------------------------------------------------------
    private String apiBase() {
        String b = LauncherConfig.sRegistrationService;
        return (b == null || b.isEmpty()) ? "https://ghrp-auth.vercel.app" : b;
    }

    /** STATE: APP_START -> session resolution. */
    private void beginSessionFlow() {
        GHRPLog.i("[state] APP_START -> session resolution");
        mSession = SessionStore.load(this);
        if (mSession == null) {
            GHRPLog.i("[state] no saved session -> AUTHENTICATE (WebView)");
            openAuth();
            return;
        }
        GHRPLog.i("[state] SESSION_RESTORE (kind=" + mSession.kind
                + ", name=" + mSession.accountName + ")");
        runRestore(mSession);
    }

    /**
     * STATE: SESSION_RESTORE -> CHARACTER_CHECK.
     * Guest accounts re-auth with their stored secret (fresh 12h token);
     * registered accounts reuse the stored token and fall back to the
     * WebView if it expired.
     */
    private void runRestore(final SessionStore.Session s) {
        if (mRestoreInFlight) return;
        mRestoreInFlight = true;
        showRestoring();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String token = s.frontToken;

                if (s.isGuest() && !s.guestSecret.isEmpty()) {
                    // Fresh token via password grant (guest_secret contract).
                    try {
                        JSONObject body = new JSONObject();
                        body.put("grant_type", "password");
                        body.put("username", s.email);
                        body.put("password", s.guestSecret);
                        Http.JsonResp r = Http.postJson(apiBase() + "/api/v2/auth/token",
                                body.toString(), null, 20000);
                        if (r.isOk()) {
                            JSONObject d = new JSONObject(r.body);
                            token = d.optString("front_token", "");
                            if (!token.isEmpty()) {
                                SessionStore.updateToken(MainActivity.this, s, token);
                                GHRPLog.i("[state] guest re-auth ok (token refreshed)");
                            }
                        } else {
                            GHRPLog.w("[state] guest re-auth failed: HTTP " + r.code);
                        }
                    } catch (Throwable t) {
                        GHRPLog.e("guest re-auth error", t);
                    }
                }

                // CHARACTER_CHECK via the server (authoritative).
                final String fToken = token;
                Http.JsonResp cr = Http.getJson(apiBase() + "/api/v2/character", fToken, 20000);
                final Http.JsonResp fCr = cr;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRestoreInFlight = false;
                        if (fCr.isOk()) {
                            try {
                                JSONObject d = new JSONObject(fCr.body);
                                boolean has = d.optBoolean("has_character", false);
                                if (d.has("character")) mCharacter = d.getJSONObject("character");
                                if (mSession != null && !fToken.isEmpty()
                                        && !fToken.equals(mSession.frontToken)) {
                                    SessionStore.updateToken(MainActivity.this, mSession, fToken);
                                }
                                if (has) {
                                    GHRPLog.i("[state] CHARACTER_READY (restored)");
                                    mCharacterReady = true;
                                    showServerSelect();
                                } else {
                                    GHRPLog.i("[state] CHARACTER_REQUIRED (no character yet)");
                                    showCharacterCreation();
                                }
                            } catch (Throwable t) {
                                GHRPLog.e("character check parse failed", t);
                                showCharacterCreation();
                            }
                        } else if (fCr.code == 401 || fCr.code == 403 || fCr.code == 404) {
                            // Identity invalid on the server — drop and re-authenticate.
                            GHRPLog.i("[state] session invalid (HTTP " + fCr.code + ") -> AUTHENTICATE");
                            SessionStore.clear(MainActivity.this);
                            mSession = null;
                            openAuth();
                        } else {
                            // Network problem: keep the session, allow offline play path.
                            GHRPLog.w("[state] character check unavailable (HTTP " + fCr.code
                                    + ") — offering retry");
                            showRestoreFailed();
                        }
                    }
                });
            }
        }, "ghrp-restore").start();
    }

    /** Small "restoring session" panel (no WebView flash on restart). */
    private void showRestoring() {
        LinearLayout panel = panelRoot();
        panel.addView(heading("WELCOME BACK"));
        TextView t = new TextView(this);
        if (mSession != null) {
            t.setText(mSession.isGuest() ? "Restoring your guest session…" : "Restoring your session…");
        } else {
            t.setText("Restoring session…");
        }
        t.setTextColor(0xFFDDE6F5);
        t.setTextSize(15);
        panel.addView(t, matchWrap());

        TextView who = new TextView(this);
        who.setText(mSession != null ? mSession.accountName : "");
        who.setTextColor(0xFFE8B24B);
        who.setTextSize(20);
        who.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(8);
        panel.addView(who, lp);
        swapOverlay(scrollify(panel));
    }

    /** Restore could not reach the backend — retry / continue offline. */
    private void showRestoreFailed() {
        LinearLayout panel = panelRoot();
        panel.addView(heading("CONNECTION"));
        TextView t = new TextView(this);
        t.setText("Your saved session is safe, but the account server could not be reached.\n\n"
                + "Check your internet connection and retry.");
        t.setTextColor(0xFF8899BB);
        t.setTextSize(14);
        t.setLineSpacing(dp(2), 1f);
        panel.addView(t, matchWrap());

        Button retry = primaryButton("RETRY");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { runRestore(mSession); }
        });
        panel.addView(retry, matchWrap());

        Button fresh = primaryButton("SIGN IN AGAIN");
        fresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { openAuth(); }
        });
        panel.addView(fresh, matchWrap());
        swapOverlay(scrollify(panel));
    }

    // ------------------------------------------------------------------
    // Authentication (SSO WebView -> token via Android.initToken bridge)
    // ------------------------------------------------------------------
    private void openAuth() {
        String authUrl = LauncherConfig.sRegistrationService
                + "/?app=ghrp&lang=en&state=launcher";
        AuthController.open(this, authUrl, new AuthController.Listener() {
            @Override
            public void onToken(String tokenJson) {
                GHRPLog.i("[state] AUTHENTICATE -> token received");
                handleAuthToken(tokenJson);
            }

            @Override
            public void onClose(String error) {
                GHRPLog.e("[state] auth closed: " + error);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Closed without auth: fall back to session (if any) or retry.
                        if (mSession != null) runRestore(mSession);
                        else openAuth();
                    }
                });
            }
        });
    }

    /** WebView payload: {front_token, guest_secret?, account?} -> session store. */
    private void handleAuthToken(String tokenJson) {
        try {
            JSONObject o = new JSONObject(tokenJson);
            String frontToken = o.optString("front_token", "");
            if (frontToken.isEmpty()) {
                GHRPLog.e("auth token payload missing front_token");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { openAuth(); }
                });
                return;
            }
            String guestSecret = o.optString("guest_secret", "");
            JSONObject acc = o.optJSONObject("account");
            String name = acc != null ? acc.optString("name", "") : "";
            String email = acc != null ? acc.optString("email", "") : "";
            String kind = acc != null ? acc.optString("kind", "user") : "user";
            String uuid = acc != null ? acc.optString("account_uuid", "") : "";

            // DEFENSE IN DEPTH: if the WebView payload omitted the identity
            // fields (older page builds / guest flow without e-mail), decode
            // them straight from the front_token payload segment — the token
            // is "<base64url JSON>.<sig>" and carries the full identity.
            if (email.isEmpty() || name.isEmpty() || uuid.isEmpty()
                    || "user".equals(kind)) {
                JSONObject tok = decodeFrontToken(frontToken);
                if (tok != null) {
                    if (email.isEmpty()) email = tok.optString("email", "");
                    if (name.isEmpty()) name = tok.optString("name", "");
                    if (uuid.isEmpty()) uuid = tok.optString("account_uuid", "");
                    String tkind = tok.optString("kind", "");
                    if (!tkind.isEmpty()) kind = tkind;
                    GHRPLog.i("identity completed from token payload "
                            + "(kind=" + kind + ", email=" + (email.isEmpty() ? "?" : "present") + ")");
                }
            }

            mSession = new SessionStore.Session(kind, email, guestSecret, frontToken,
                    name, uuid, System.currentTimeMillis());
            SessionStore.save(this, mSession);
            GHRPLog.i("[state] AUTHENTICATED (kind=" + kind + ", name=" + name
                    + ", email=" + (email.isEmpty() ? "MISSING" : "ok") + ")");

            // -> CHARACTER_CHECK
            runOnUiThread(new Runnable() {
                @Override
                public void run() { runRestore(mSession); }
            });
        } catch (Throwable t) {
            GHRPLog.e("handleAuthToken failed", t);
            runOnUiThread(new Runnable() {
                @Override
                public void run() { openAuth(); }
            });
        }
    }

    /**
     * Decode the payload segment of a front_token
     * ("<base64url(json)>.<signature>") without any external library.
     * Returns null when not decodable.
     */
    private static JSONObject decodeFrontToken(String token) {
        try {
            int dot = token.indexOf('.');
            if (dot <= 0) return null;
            String seg = token.substring(0, dot);
            seg = seg.replace('-', '+').replace('_', '/');
            int pad = (-seg.length()) % 4;
            for (int i = 0; i < pad; i++) seg += '=';
            byte[] raw = android.util.Base64.decode(seg, android.util.Base64.DEFAULT);
            return new JSONObject(new String(raw, "UTF-8"));
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Server select (native flow — Java panel; WebView is auth-only)
    // ------------------------------------------------------------------
    private void showServerSelect() {
        LinearLayout panel = panelRoot();

        TextView heading = heading("SELECT SERVER");
        panel.addView(heading);

        // Character chip — proof that the session + character were restored.
        if (mCharacter != null || (mSession != null && !mSession.accountName.isEmpty())) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cd = new GradientDrawable();
            cd.setColor(0x26E8B24B);
            cd.setCornerRadius(dp(10));
            cd.setStroke(1, 0x66E8B24B);
            chip.setBackground(cd);
            chip.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams clp = matchWrap();
            clp.bottomMargin = dp(14);
            panel.addView(chip, clp);

            TextView k = new TextView(this);
            k.setText("PLAYING AS");
            k.setTextColor(0xFF8899BB);
            k.setTextSize(10);
            k.setLetterSpacing(0.2f);
            chip.addView(k);
            TextView v = new TextView(this);
            String nm = mSession != null ? mSession.accountName : "";
            String gender = "";
            if (mCharacter != null) {
                gender = mCharacter.optString("sex", "");
                if (!gender.isEmpty()) gender = " · " + gender.toUpperCase();
            }
            v.setText(nm + gender);
            v.setTextColor(0xFFE8B24B);
            v.setTextSize(17);
            v.setTypeface(Typeface.DEFAULT_BOLD);
            chip.addView(v);
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cdd = new GradientDrawable();
        cdd.setColor(0xFF151C2C);
        cdd.setCornerRadius(dp(10));
        cdd.setStroke(1, 0xFF2A3654);
        card.setBackground(cdd);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView name = new TextView(this);
        name.setText(LauncherConfig.SERVER_NAME);
        name.setTextColor(0xFFE8B24B);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);

        TextView city = new TextView(this);
        city.setText(LauncherConfig.SERVER_CITY);
        city.setTextColor(0xFF8899BB);
        city.setTextSize(13);
        city.setLetterSpacing(0.2f);
        card.addView(city);

        TextView addr = new TextView(this);
        addr.setText(LauncherConfig.SERVER_HOST + ":" + LauncherConfig.SERVER_PORT);
        addr.setTextColor(0xFF5C6E92);
        addr.setTextSize(12);
        card.addView(addr);
        panel.addView(card, matchWrap());

        Button play = primaryButton("PLAY ON " + LauncherConfig.SERVER_CITY);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showPlayStatus(); }
        });
        panel.addView(play, matchWrap());

        Button change = secondaryButton("CHANGE CHARACTER");
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GHRPLog.i("[state] user requested character change -> CHARACTER_REQUIRED");
                showCharacterCreation();
            }
        });
        panel.addView(change, matchWrap());

        Button logout = secondaryButton("LOG OUT / RESET ACCOUNT");
        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GHRPLog.i("[state] logout -> session cleared -> AUTHENTICATE");
                SessionStore.clear(MainActivity.this);
                mSession = null;
                mCharacter = null;
                mCharacterReady = false;
                openAuth();
            }
        });
        panel.addView(logout, matchWrap());

        swapOverlay(scrollify(panel));
    }

    // ------------------------------------------------------------------
    // Character creation (native 3D preview + side panel; saves to the server)
    // ------------------------------------------------------------------
    private LinearLayout mCharacterPanel;

    private void showCharacterCreation() {
        // Overlay becomes a translucent side panel; the engine renders behind it.
        mOverlay.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = panelRoot();
        mCharacterPanel = panel;

        panel.addView(heading("CREATE CHARACTER"));

        // Account name chip (server-authoritative name).
        if (mSession != null && !mSession.accountName.isEmpty()) {
            TextView who = new TextView(this);
            who.setText("Character name: " + mSession.accountName);
            who.setTextColor(0xFF8899BB);
            who.setTextSize(12);
            LinearLayout.LayoutParams lp = matchWrap();
            lp.bottomMargin = dp(10);
            panel.addView(who, lp);
        }

        // Gender toggle
        final Button gender = new Button(this);
        gender.setTextSize(15);
        LinearLayout.LayoutParams glp = matchWrap();
        glp.bottomMargin = dp(10);
        styleSecondary(gender);
        gender.setText("GENDER: MALE");
        gender.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFemale = !mFemale;
                gender.setText("GENDER: " + (mFemale ? "FEMALE" : "MALE"));
                mSkinIndex = 0;
                applySkin();
                updateSkinIdLabel();
            }
        });
        panel.addView(gender, glp);

        // Skin carousel
        final Button skin = new Button(this);
        skin.setTextSize(15);
        styleSecondary(skin);
        LinearLayout.LayoutParams slp = matchWrap();
        slp.bottomMargin = dp(6);
        skin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int n = GHNative.nativeCharacterCount();
                if (n <= 0) return;
                mSkinIndex = (mSkinIndex + 1) % n;
                applySkin();
                updateSkinIdLabel();
            }
        });
        skin.setId(0x2002);
        panel.addView(skin, slp);

        // Skin id label (what the server will store).
        final TextView skinId = new TextView(this);
        skinId.setId(0x2003);
        skinId.setTextColor(0xFF5C6E92);
        skinId.setTextSize(11);
        skinId.setPadding(0, 0, 0, dp(8));
        panel.addView(skinId, matchWrap());

        // Character name entry (required flow: preview -> NAME -> confirm ->
        // save). Pre-filled with the server-generated account name; the user
        // may keep it or personalize it. Validated here and server-side.
        TextView nameLabel = new TextView(this);
        nameLabel.setText("CHARACTER NAME");
        nameLabel.setTextColor(0xFF8899BB);
        nameLabel.setTextSize(11);
        nameLabel.setLetterSpacing(0.14f);
        panel.addView(nameLabel, matchWrap());

        final EditText nameInput = new EditText(this);
        nameInput.setId(0x2004);
        nameInput.setText(mSession != null ? mSession.accountName : "");
        nameInput.setTextColor(0xFFE8EEF8);
        nameInput.setHintTextColor(0xFF5C6E92);
        nameInput.setHint("Firstname_Lastname");
        nameInput.setTextSize(15);
        nameInput.setSingleLine(true);
        nameInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        GradientDrawable nbg = new GradientDrawable();
        nbg.setColor(0x331A2236);
        nbg.setStroke(dp(1), 0xFF2A3854);
        nbg.setCornerRadius(dp(8));
        nameInput.setBackground(nbg);
        nameInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams nlp = matchWrap();
        nlp.bottomMargin = dp(6);
        panel.addView(nameInput, nlp);

        TextView nameHint = new TextView(this);
        nameHint.setText("3–24 characters · letters, numbers, _ (e.g. John_Silver)");
        nameHint.setTextColor(0xFF5C6E92);
        nameHint.setTextSize(10);
        nameHint.setPadding(0, 0, 0, dp(8));
        panel.addView(nameHint, matchWrap());

        TextView err = new TextView(this);
        err.setId(0x2001);
        err.setTextColor(0xFFE06A6A);
        err.setTextSize(12);
        err.setPadding(0, dp(6), 0, 0);
        panel.addView(err, matchWrap());

        // Engine diagnostics strip (device evidence for the renderer pipeline).
        TextView diagTitle = new TextView(this);
        diagTitle.setText("ENGINE STATUS");
        diagTitle.setTextColor(0xFF5C6E92);
        diagTitle.setTextSize(10);
        diagTitle.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams dtlp = matchWrap();
        dtlp.topMargin = dp(12);
        panel.addView(diagTitle, dtlp);

        mDiagView = new TextView(this);
        mDiagView.setTextColor(0xFF7A8CAE);
        mDiagView.setTextSize(10);
        mDiagView.setTypeface(Typeface.MONOSPACE);
        mDiagView.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable dd = new GradientDrawable();
        dd.setColor(0xCC0A0E18);
        dd.setCornerRadius(dp(6));
        mDiagView.setBackground(dd);
        synchronized (mDiag) {
            mDiagView.setText(mDiag.toString());
        }
        panel.addView(mDiagView, matchWrap());

        Button confirm = primaryButton("CONFIRM AND PLAY");
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { confirmCharacter(); }
        });
        panel.addView(confirm, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("Drag on the character to rotate. Tap GENDER and SKIN to customize.");
        hint.setTextColor(0xFF5C6E92);
        hint.setTextSize(12);
        panel.addView(hint, matchWrap());

        // Semi-transparent panel background so the 3D character stays visible.
        GradientDrawable pd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF2060A12, 0xCC060A12, 0x00060A12});
        panel.setBackground(pd);
        panel.setPadding(dp(18), dp(24), dp(10), dp(24));

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.42f),
                ViewGroup.LayoutParams.MATCH_PARENT);
        mOverlay.removeAllViews();
        mOverlay.addView(panel, flp);

        applySkin();
        updateSkinIdLabel();
    }

    private void updateSkinIdLabel() {
        TextView t = mCharacterPanel == null ? null : (TextView) mCharacterPanel.findViewById(0x2003);
        if (t != null) {
            t.setText("Server skin id: " + sampSkinId()
                    + (mFemale ? " (female)" : " (male)"));
        }
    }

    private void applySkin() {
        GHNative.nativeSetCharacter(mSkinIndex);
        TextView err = mCharacterPanel == null ? null : mCharacterPanel.findViewById(0x2001);
        if (err != null) err.setText("");
        Button skin = mCharacterPanel == null ? null : (Button) mCharacterPanel.findViewById(0x2002);
        if (skin != null) {
            String n = GHNative.nativeCharacterName(mSkinIndex);
            skin.setText("SKIN: " + (n == null || n.isEmpty() ? ("#" + mSkinIndex) : n.toUpperCase()));
        }
    }

    /**
     * STATE: CHARACTER_REQUIRED -> POST /api/v2/character -> CHARACTER_READY.
     * The character (name/sex/skin) is saved SERVER-SIDE — not just locally.
     */
    private void confirmCharacter() {
        if (mSession == null || mSession.frontToken.isEmpty()) {
            if (mCharacterPanel != null) {
                TextView err = mCharacterPanel.findViewById(0x2001);
                if (err != null) err.setText("No session — sign in again.");
            }
            openAuth();
            return;
        }
        // Local name validation (server re-validates; SA-MP name rules).
        EditText nameInput = mCharacterPanel == null
                ? null : (EditText) mCharacterPanel.findViewById(0x2004);
        String chosenName = nameInput != null
                ? nameInput.getText().toString().trim() : "";
        if (!chosenName.isEmpty()
                && !chosenName.matches("[A-Za-z0-9_]{3,24}")) {
            TextView err = mCharacterPanel.findViewById(0x2001);
            if (err != null) err.setText("Name must be 3–24 characters "
                    + "(letters, numbers, underscore only).");
            return;
        }

        if (mCharacterPanel != null) {
            TextView err = mCharacterPanel.findViewById(0x2001);
            if (err != null) err.setText("Saving character…");
        }
        final String token = mSession.frontToken;
        final int sex = mFemale ? 1 : 0;
        final int skinId = sampSkinId();
        final String fName = chosenName;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("sex", sex);
                    body.put("skin", skinId);
                    if (!fName.isEmpty()) body.put("name", fName);
                    Http.JsonResp r = Http.postJson(apiBase() + "/api/v2/character",
                            body.toString(), token, 20000);
                    final Http.JsonResp fr = r;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (fr.isOk()) {
                                try {
                                    JSONObject d = new JSONObject(fr.body);
                                    if (d.has("character")) mCharacter = d.getJSONObject("character");
                                    // Keep the session's display name in sync
                                    // with the (possibly renamed) character.
                                    if (mCharacter != null) {
                                        String savedName = mCharacter.optString("name", "");
                                        if (!savedName.isEmpty() && mSession != null
                                                && !savedName.equals(mSession.accountName)) {
                                            SessionStore.rename(MainActivity.this, mSession, savedName);
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                                GHRPLog.i("[state] CHARACTER saved server-side (name="
                                        + fName + ", sex=" + sex + ", skin=" + skinId + ")");
                                mCharacterReady = true;
                                showPlayStatus();
                            } else {
                                GHRPLog.e("character save failed: HTTP " + fr.code);
                                if (mCharacterPanel != null) {
                                    TextView err = mCharacterPanel.findViewById(0x2001);
                                    if (err != null) {
                                        String why = fr.code == 409 ? "That name is already taken."
                                                : ("Could not save the character (server HTTP "
                                                + fr.code + "). Check your connection and retry.");
                                        err.setText(why);
                                    }
                                }
                            }
                        }
                    });
                } catch (Throwable t) {
                    GHRPLog.e("confirmCharacter failed", t);
                }
            }
        }, "ghrp-charsave").start();
    }

    // ------------------------------------------------------------------
    // Play status (honest M1 state: world/network module in development)
    // ------------------------------------------------------------------
    private void showPlayStatus() {
        LinearLayout panel = panelRoot();
        panel.addView(heading("ENTERING " + LauncherConfig.SERVER_CITY));

        TextView welcome = new TextView(this);
        String nm = mSession != null ? mSession.accountName : "player";
        welcome.setText("Welcome, " + nm + ".");
        welcome.setTextColor(0xFFDDE6F5);
        welcome.setTextSize(16);
        panel.addView(welcome, matchWrap());

        if (mCharacter != null) {
            TextView ch = new TextView(this);
            ch.setText("Character: " + mCharacter.optString("sex", "?")
                    + " · skin " + mCharacter.optInt("skin", 0)
                    + " — saved to your account.");
            ch.setTextColor(0xFF8899BB);
            ch.setTextSize(13);
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(4);
            panel.addView(ch, lp);
        }

        TextView status = new TextView(this);
        status.setText("Connecting to Grand Horizon RP (142.132.203.47:14448)…\n\n"
                + "Engine modules active: renderer, asset pipeline (mesh/textures/animation), "
                + "character system, touch input, session persistence.\n\n"
                + "In development: world streaming, SA-MP server protocol, audio. "
                + "The launcher will receive these as engine updates.");
        status.setTextColor(0xFF8899BB);
        status.setTextSize(13);
        status.setLineSpacing(dp(2), 1f);
        panel.addView(status, matchWrap());

        Button back = primaryButton("BACK");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showServerSelect(); }
        });
        panel.addView(back, matchWrap());

        swapOverlay(scrollify(panel));
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private LinearLayout panelRoot() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B0F1A, 0xFF141B2E, 0xFF05070C});
        p.setBackground(bg);
        p.setPadding(dp(24), dp(28), dp(24), dp(24));
        return p;
    }

    private TextView heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFE8B24B);
        t.setTextSize(20);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(18);
        t.setLayoutParams(lp);
        return t;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFF0B0F1A);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFE8B24B, 0xFFD69A2E});
        d.setCornerRadius(dp(10));
        b.setBackground(d);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(14);
        b.setLayoutParams(lp);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFDDE6F5);
        b.setTextSize(14);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF151C2C);
        d.setCornerRadius(dp(10));
        d.setStroke(1, 0xFF2A3654);
        b.setBackground(d);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void styleSecondary(Button b) {
        b.setTextColor(0xFFDDE6F5);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF151C2C);
        d.setCornerRadius(dp(10));
        d.setStroke(1, 0xFF2A3654);
        b.setBackground(d);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private ScrollView scrollify(View content) {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    private void swapOverlay(View v) {
        mOverlay.removeAllViews();
        mDiagView = null;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mOverlay.addView(v, lp);
        mOverlay.setBackgroundColor(0xFF0B0F1A);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mUpdateController != null) mUpdateController.cancel();
        try {
            if (mEngineView != null) GHNative.nativeStop();
        } catch (Throwable t) {
            GHRPLog.e("nativeStop failed", t);
        }
        if (sInstance == this) sInstance = null;
        GHRPLog.i("MainActivity.onDestroy");
        super.onDestroy();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        // Character screen -> back to server select (if a character exists);
        // otherwise keep the flow forward-only.
        if (mCharacterPanel != null && mCharacterReady) {
            mCharacterPanel = null;
            showServerSelect();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}
