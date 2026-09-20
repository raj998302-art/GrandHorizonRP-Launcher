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
 * Flow: splash -> game-data update -> SSO authentication -> server select ->
 * character creation (3D engine preview) -> play.
 */
public final class MainActivity extends Activity implements GHNative.Callback {
    private FrameLayout mRoot;
    private FrameLayout mOverlay;
    private GHEngineView mEngineView;
    private UpdateController mUpdateController;
    private String mAuthTokenJson = "";

    // Character creation state
    private boolean mFemale = false;
    private int mSkinIndex = 0;

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
    // Update flow (own Java updater)
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
                    public void run() { openAuth(); }
                });
            }
        });
        mUpdateController.start();
    }

    // ------------------------------------------------------------------
    // Engine lifecycle
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

    @Override
    public void onEngineEvent(final String method, final String json) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GHRPLog.i("engine event: " + method + " " + json);
                if ("onLoadError".equals(method) && mCharacterPanel != null) {
                    TextView err = mCharacterPanel.findViewById(0x2001);
                    if (err != null) err.setText("Engine: " + json);
                }
            }
        });
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
                mAuthTokenJson = tokenJson;
                GHRPLog.i("auth token received");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { showServerSelect(); }
                });
            }

            @Override
            public void onClose(String error) {
                GHRPLog.e("auth closed: " + error);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { showServerSelect(); }
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // Server select
    // ------------------------------------------------------------------
    private void showServerSelect() {
        LinearLayout panel = panelRoot();

        TextView heading = heading("SELECT SERVER");
        panel.addView(heading);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cd = new GradientDrawable();
        cd.setColor(0xFF151C2C);
        cd.setCornerRadius(dp(10));
        cd.setStroke(1, 0xFF2A3654);
        card.setBackground(cd);
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
            public void onClick(View v) { showCharacterCreation(); }
        });
        panel.addView(play, matchWrap());

        swapOverlay(scrollify(panel));
    }

    // ------------------------------------------------------------------
    // Character creation (3D preview + M/F + skin + name)
    // ------------------------------------------------------------------
    private LinearLayout mCharacterPanel;

    private void showCharacterCreation() {
        // Overlay becomes a translucent side panel; the engine renders behind it.
        mOverlay.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = panelRoot();
        mCharacterPanel = panel;

        panel.addView(heading("CREATE CHARACTER"));

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
            }
        });
        panel.addView(gender, glp);

        // Skin carousel
        final Button skin = new Button(this);
        skin.setTextSize(15);
        styleSecondary(skin);
        LinearLayout.LayoutParams slp = matchWrap();
        slp.bottomMargin = dp(10);
        skin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int n = GHNative.nativeCharacterCount();
                if (n <= 0) return;
                mSkinIndex = (mSkinIndex + 1) % n;
                applySkin();
            }
        });
        skin.setId(0x2002);
        panel.addView(skin, slp);

        // Name
        final EditText name = new EditText(this);
        name.setHint("Character name");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setTextColor(0xFFDDE6F5);
        name.setHintTextColor(0xFF5C6E92);
        name.setTextSize(15);
        GradientDrawable nd = new GradientDrawable();
        nd.setColor(0xFF10182A);
        nd.setCornerRadius(dp(8));
        nd.setStroke(1, 0xFF2A3654);
        name.setBackground(nd);
        name.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.addView(name, matchWrap());

        TextView err = new TextView(this);
        err.setId(0x2001);
        err.setTextColor(0xFFE06A6A);
        err.setTextSize(12);
        err.setPadding(0, dp(6), 0, 0);
        panel.addView(err, matchWrap());

        Button confirm = primaryButton("CONFIRM AND PLAY");
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) {
                    name.setError("Enter a name");
                    return;
                }
                confirmCharacter(n);
            }
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

    private void confirmCharacter(String name) {
        try {
            JSONObject charSel = new JSONObject();
            charSel.put("name", name);
            charSel.put("skin", GHNative.nativeCharacterName(mSkinIndex));
            charSel.put("gender", mFemale ? "female" : "male");
            charSel.put("auth", mAuthTokenJson.isEmpty() ? "{}"
                    : new JSONObject(mAuthTokenJson).optString("access_token", ""));
            GHRPLog.i("character confirmed: " + charSel);
        } catch (Throwable t) {
            GHRPLog.e("confirmCharacter json failed", t);
        }
        showPlayStatus(name);
    }

    // ------------------------------------------------------------------
    // Play status (honest M1 state: world/network module in development)
    // ------------------------------------------------------------------
    private void showPlayStatus(String name) {
        LinearLayout panel = panelRoot();
        panel.addView(heading("ENTERING " + LauncherConfig.SERVER_CITY));

        TextView welcome = new TextView(this);
        welcome.setText("Welcome, " + name + ".");
        welcome.setTextColor(0xFFDDE6F5);
        welcome.setTextSize(16);
        panel.addView(welcome, matchWrap());

        TextView status = new TextView(this);
        status.setText("Connecting to Grand Horizon RP (142.132.203.47:14448)…\n\n"
                + "Engine modules active: renderer, asset pipeline (mesh/textures/animation), "
                + "character system, touch input.\n\n"
                + "In development: world streaming, SA-MP server protocol, audio. "
                + "The launcher will receive these as engine updates.");
        status.setTextColor(0xFF8899BB);
        status.setTextSize(13);
        status.setLineSpacing(dp(2), 1f);
        panel.addView(status, matchWrap());

        Button back = primaryButton("BACK TO CHARACTER");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showCharacterCreation(); }
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
        // Character screen -> back to server select; otherwise keep flow.
        if (mCharacterPanel != null) {
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
