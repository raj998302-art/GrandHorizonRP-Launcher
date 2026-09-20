package com.grandhorizonrp.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

/**
 * WebView authentication — exact original flow (screen 0x58):
 *  - engine supplies auth_url (from url-config registrationService)
 *  - launcher appends sysinfo=android + client_id (+ ttclid/analytics ids — empty for GHRP)
 *  - page is loaded in a WebView with a JavascriptInterface named "Android"
 *  - Android.initToken({front_token}) -> forwarded to the engine as message 0x58
 *  - Android.closeWebview(error) -> closes the overlay (error -> dialog)
 */
public final class AuthController {
    public interface Listener {
        void onToken(String tokenJson);

        void onClose(String error);
    }

    private static final String AUTH_HOST = "ghrp-auth.vercel.app";

    private static volatile WebView sWebView;
    private static volatile Listener sListener;
    private static volatile boolean sOpen = false;

    private AuthController() {
    }

    public static void open(final Activity activity, final String authUrl, final Listener listener) {
        sListener = listener;
        final String url = buildUrl(activity, authUrl);
        GHRPLog.i("AuthController.open: " + url);
        activity.runOnUiThread(new Runnable() {
            @SuppressLint("SetJavaScriptEnabled")
            @Override
            public void run() {
                if (sOpen || sWebView != null) {
                    GHRPLog.w("auth already open");
                    return;
                }
                FrameLayout overlay = com.blackhub.bronline.game.core.JNIActivity.getOverlay();
                if (overlay == null) {
                    GHRPLog.e("no overlay — cannot show auth");
                    return;
                }
                sOpen = true;

                FrameLayout root = new FrameLayout(activity);
                root.setBackgroundColor(Color.parseColor("#0d0e12"));

                LinearLayout topBar = new LinearLayout(activity);
                topBar.setOrientation(LinearLayout.VERTICAL);
                topBar.setBackgroundColor(Color.parseColor("#14161c"));
                TextView title = new TextView(activity);
                title.setText("GRAND HORIZON RP — SIGN IN");
                title.setTextColor(Color.parseColor("#f2f3f5"));
                title.setTextSize(13);
                title.setLetterSpacing(0.08f);
                title.setPadding(dp(activity, 20), dp(activity, 14), dp(activity, 20), dp(activity, 14));
                topBar.addView(title, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                root.addView(topBar, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP));

                WebView web = new WebView(activity);
                sWebView = web;
                WebSettings settings = web.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setCacheMode(WebSettings.LOAD_DEFAULT);
                settings.setMediaPlaybackRequiresUserGesture(false);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                web.setBackgroundColor(Color.parseColor("#0d0e12"));
                web.setWebChromeClient(new WebChromeClient());
                web.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return false; // follow all navigation inside the webview
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        GHRPLog.i("auth page loaded: " + url);
                    }
                });
                web.addJavascriptInterface(new Bridge(), "Android");
                web.loadUrl(url);

                FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                webParams.topMargin = dp(activity, 46);
                root.addView(web, webParams);

                overlay.addView(root, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        });
    }

    public static void close(Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (sWebView != null) {
                    try {
                        sWebView.stopLoading();
                        sWebView.loadUrl("about:blank");
                        View parent = (View) sWebView.getParent();
                        if (parent != null && parent.getParent() instanceof ViewGroup) {
                            ((ViewGroup) parent.getParent()).removeView(parent);
                        }
                        sWebView.destroy();
                    } catch (Throwable t) {
                        GHRPLog.e("close webview failed", t);
                    }
                    sWebView = null;
                }
                sOpen = false;
            }
        });
    }

    // ------------------------------------------------------------------
    // URL construction (matches original WebViewAuthFragment param set)
    // ------------------------------------------------------------------
    private static String buildUrl(Activity activity, String authUrl) {
        SharedPreferences prefs = activity.getSharedPreferences("launcher", Activity.MODE_PRIVATE);
        String clientId = prefs.getString("client_id", "");
        if (clientId.isEmpty()) {
            clientId = UUID.randomUUID().toString();
            prefs.edit().putString("client_id", clientId).apply();
        }
        StringBuilder sb = new StringBuilder(authUrl);
        sb.append(authUrl.contains("?") ? '&' : '?');
        sb.append("sysinfo=android");
        sb.append("&client_id=").append(enc(clientId));
        sb.append("&ttclid=");
        sb.append("&appmetrica_device_id=");
        sb.append("&adjust_id=");
        return sb.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    // ------------------------------------------------------------------
    // JS bridge — contract of the original WebAppInterface
    // ------------------------------------------------------------------
    private static final class Bridge {
        @JavascriptInterface
        public void initToken(String jsonString) {
            GHRPLog.i("Android.initToken received (" + (jsonString == null ? 0 : jsonString.length()) + " chars)");
            Listener l = sListener;
            if (l != null && jsonString != null && !jsonString.trim().isEmpty()) {
                try {
                    // Normalize: ensure it is a JSON object string
                    String json = jsonString.trim();
                    if (!json.startsWith("{")) {
                        JSONObject o = new JSONObject();
                        o.put("front_token", json);
                        json = o.toString();
                    }
                    l.onToken(json);
                } catch (Throwable t) {
                    GHRPLog.e("initToken parse failed", t);
                }
            }
        }

        @JavascriptInterface
        public void closeWebview(String errorString) {
            GHRPLog.i("Android.closeWebview error=" + errorString);
            Listener l = sListener;
            if (l != null) {
                l.onClose(errorString);
            }
        }
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
